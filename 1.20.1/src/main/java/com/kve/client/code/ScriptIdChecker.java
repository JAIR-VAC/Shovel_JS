package com.kve.client.code;

import com.kve.common.code.IdSuggest;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class ScriptIdChecker {
    private ScriptIdChecker() {}

    public enum Kind { OK, TYPO, MOD_MISSING }

    public record Result(Kind kind, String suggestion) {
        static final Result OK = new Result(Kind.OK, null);
    }

    private static List<String> suggestionPool;
    private static final Map<String, Result> CACHE = new HashMap<>();
    private static java.util.Set<String> datapackNamespaces;

    /** Resultado para {@code id}. Ante cualquier duda (registros sin cargar, error) devuelve OK: un falso error es peor que ninguno. */
    public static Result check(String id) {
        Result cached = CACHE.get(id);
        if (cached != null) return cached;
        Result r = compute(id);
        if (!BuiltInRegistries.ITEM.keySet().isEmpty()) CACHE.put(id, r);
        return r;
    }

    private static Result compute(String id) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null || BuiltInRegistries.ITEM.keySet().isEmpty()) return Result.OK;
            if (exists(rl)) return Result.OK;
            String ns = rl.getNamespace();
            if (!isKnownNamespace(ns)) return new Result(Kind.MOD_MISSING, ns);
            String near = IdSuggest.nearest(id, suggestionPool());
            return near == null ? Result.OK : new Result(Kind.TYPO, near);
        } catch (Throwable t) {
            return Result.OK;
        }
    }

    /** Olvida lo resuelto (al abrir un archivo o pulsar Validar: puede haber cambiado el mundo, recetas, etc.). */
    public static void clearCache() {
        CACHE.clear();
        datapackNamespaces = null;
    }

    private static boolean exists(ResourceLocation rl) {
        for (Registry<?> reg : BuiltInRegistries.REGISTRY) {
            if (inRegistry(reg, rl)) return true;
        }
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        RegistryAccess access = server != null ? server.registryAccess() : mc.level != null ? mc.level.registryAccess() : null;
        if (access != null) {
            for (RegistryAccess.RegistryEntry<?> e : (Iterable<RegistryAccess.RegistryEntry<?>>) access.registries()::iterator) {
                if (inRegistry(e.value(), rl)) return true;
            }
        }
        if (mc.level != null && mc.level.getRecipeManager().byKey(rl).isPresent()) return true;
        if (mc.player != null && mc.player.connection.getAdvancements().getAdvancements().get(rl) != null) return true;
        if (server != null) {
            if (server.getRecipeManager().byKey(rl).isPresent()) return true;
            if (server.getAdvancements().getAdvancement(rl) != null) return true;
            if (server.getLootData().getElement(net.minecraft.world.level.storage.loot.LootDataType.TABLE, rl) != null) return true;
            if (server.getLootData().getElement(net.minecraft.world.level.storage.loot.LootDataType.PREDICATE, rl) != null) return true;
            if (server.getFunctions().get(rl).isPresent()) return true;
        }
        return false;
    }

    private static <T> boolean inRegistry(Registry<T> reg, ResourceLocation rl) {
        try {
            if (reg.containsKey(rl)) return true;
            return reg.getTag(TagKey.create(reg.key(), rl)).isPresent();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isKnownNamespace(String ns) {
        switch (ns) {
            case "minecraft", "forge", "c", "kubejs", "shovel_js", "realms" -> { return true; }
            default -> { }
        }
        if (ModList.get() != null && ModList.get().isLoaded(ns)) return true;
        if (datapackNamespaces == null) {
            datapackNamespaces = new java.util.HashSet<>();
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) mc.level.getRecipeManager().getRecipeIds().forEach(r -> datapackNamespaces.add(r.getNamespace()));
        }
        return datapackNamespaces.contains(ns);
    }

    private static List<String> suggestionPool() {
        if (suggestionPool != null) return suggestionPool;
        List<String> out = new ArrayList<>();
        Map<String, Boolean> seen = new HashMap<>();
        for (Registry<?> reg : List.<Registry<?>>of(BuiltInRegistries.ITEM, BuiltInRegistries.BLOCK, BuiltInRegistries.FLUID,
                BuiltInRegistries.ENTITY_TYPE, BuiltInRegistries.MOB_EFFECT, BuiltInRegistries.ENCHANTMENT)) {
            for (ResourceLocation k : reg.keySet()) {
                String s = k.toString();
                if (seen.put(s, Boolean.TRUE) == null) out.add(s);
            }
        }
        suggestionPool = out;
        return out;
    }
}
