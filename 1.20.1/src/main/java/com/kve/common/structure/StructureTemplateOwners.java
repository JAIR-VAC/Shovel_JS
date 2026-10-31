package com.kve.common.structure;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class StructureTemplateOwners {

    private StructureTemplateOwners() {}

    /** Un structure_set que contiene una estructura que usa la plantilla. */
    public record Match(String structureSetId, String structureId) {}

    private static final Map<String, List<Match>> CACHE = new ConcurrentHashMap<>();
    private static RegistryAccess cacheAccess = null;

    public static List<Match> find(RegistryAccess access, String templateId) {
        ResourceLocation tpl = ResourceLocation.tryParse(templateId == null ? "" : templateId.trim());
        if (tpl == null || access == null) return List.of();
        if (cacheAccess != access) { CACHE.clear(); cacheAccess = access; }
        return CACHE.computeIfAbsent(tpl.toString(), k -> compute(access, tpl));
    }

    private static List<Match> compute(RegistryAccess access, ResourceLocation tpl) {
        String templateId = tpl.toString();
        List<Match> out = new ArrayList<>();
        try {
            RegistryAccess ra = StructureFrequencyExporter.resolveAccess(access);
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, ra);

            Set<String> poolsWithTemplate = new HashSet<>();
            try {
                var pools = ra.registryOrThrow(Registries.TEMPLATE_POOL);
                for (var e : pools.entrySet()) {
                    JsonElement j = StructureTemplatePool.DIRECT_CODEC.encodeStart(ops, e.getValue()).result().orElse(null);
                    if (j != null && mentions(j, templateId)) poolsWithTemplate.add(e.getKey().location().toString());
                }
            } catch (Exception ignored) {
            }

            Set<String> structures = new LinkedHashSet<>();
            var registry = ra.registryOrThrow(Registries.STRUCTURE);
            String firstSeg = tpl.getPath().contains("/") ? tpl.getPath().substring(0, tpl.getPath().indexOf('/')) : tpl.getPath();
            for (var e : registry.entrySet()) {
                ResourceLocation sl = e.getKey().location();
                String sid = sl.toString();
                if (sid.equals(templateId)) { structures.add(sid); continue; }
                Structure s = e.getValue();
                if (s instanceof JigsawStructure) {
                    if (poolsWithTemplate.isEmpty()) continue;
                    String start = startPool(ops, s);
                    if (start == null) continue;
                    if (poolsWithTemplate.contains(start)) { structures.add(sid); continue; }
                    String family = family(start);
                    if (family == null) continue;
                    for (String p : poolsWithTemplate) {
                        if (p.startsWith(family + "/")) { structures.add(sid); break; }
                    }
                } else if (sl.getNamespace().equals(tpl.getNamespace())
                        && (sl.getPath().equals(firstSeg) || sl.getPath().startsWith(firstSeg + "_"))) {
                    structures.add(sid);
                }
            }
            if (structures.isEmpty()) return List.of();

            for (String setId : StructureFrequencyExporter.listAllStructureSetIds(access)) {
                for (String member : StructureFrequencyExporter.listMemberStructures(access, setId)) {
                    if (structures.contains(member)) out.add(new Match(setId, member));
                }
            }
        } catch (Exception ignored) {
        }
        return List.copyOf(out);
    }

    private static String startPool(RegistryOps<JsonElement> ops, Structure s) {
        try {
            JsonElement j = Structure.DIRECT_CODEC.encodeStart(ops, s).result().orElse(null);
            if (j instanceof JsonObject o && o.has("start_pool") && o.get("start_pool").isJsonPrimitive()) {
                return o.get("start_pool").getAsString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String family(String poolId) {
        int slash = poolId.lastIndexOf('/');
        return slash > poolId.indexOf(':') ? poolId.substring(0, slash) : null;
    }

    private static boolean mentions(JsonElement j, String id) {
        if (j == null || j.isJsonNull()) return false;
        if (j.isJsonPrimitive()) return j.getAsJsonPrimitive().isString() && id.equals(j.getAsString());
        if (j.isJsonArray()) {
            for (JsonElement x : j.getAsJsonArray()) if (mentions(x, id)) return true;
            return false;
        }
        for (var e : j.getAsJsonObject().entrySet()) if (mentions(e.getValue(), id)) return true;
        return false;
    }
}
