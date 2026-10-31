package com.kve.common.structure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kve.CraftEditorMod;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-{@code structure_set} spawn frequency overrides ("frecuencia y
 * baneo" feature on the structure editor): lets the player raise/lower a
 * vanilla/modded structure_set's {@code spacing}/{@code separation}, or ban
 * one or more of its member structures entirely, without hand-writing a
 * datapack.
 * <p>
 * This works at {@code structure_set} granularity because that's where
 * Minecraft actually stores spacing/separation (see
 * {@code net.minecraft.world.level.levelgen.structure.StructurePlacement}) -
 * a single set commonly groups several structures together (e.g. vanilla's
 * {@code minecraft:villages} holds every village variant), so one override
 * can affect more than one structure by design, and per-structure "ban" is
 * layered on top via {@link StructureSetOverride#bannedStructures} rather
 * than being its own top-level concept.
 * <p>
 * Exported the same way {@link com.kve.common.kubejs.TagExporter} exports
 * tag replace-files: a full-replace JSON written straight into
 * {@code kubejs/data/<ns>/worldgen/structure_set/<path>.json} (a low
 * priority datapack overlay KubeJS already serves - same mechanism this
 * mod already relies on for tags and structure NBT, see
 * {@link StructureNbtIO#resolvePath()}), plus a manifest so a since-removed
 * override cleans its own file up on the next write instead of leaving a
 * stale one behind.
 * <p>
 * The starting point for each override is the LIVE structure_set object
 * from the running world's registry, re-encoded via its own vanilla codec
 * ({@link StructureSet#DIRECT_CODEC}) rather than hand-built JSON - this is
 * the only way to preserve whatever a modded structure_set's placement
 * actually contains (locate_offset, exclusion_zone, a non-random_spread
 * placement type, etc.) instead of silently dropping fields this class
 * doesn't know about.
 */
public final class StructureFrequencyExporter {

    private StructureFrequencyExporter() {}

    /** -1 means "leave this field as the live registry has it". */
    public static class StructureSetOverride {
        public String structureSetId;
        /** false = the whole set is pushed out to practically never generate (see {@link #DISABLE_SPACING}), regardless of spacingOverride/separationOverride below. */
        public boolean enabled = true;
        public int spacingOverride = -1;
        public int separationOverride = -1;
        /** Structure ids (members of this set) to drop from the set's "structures" list entirely. */
        public Set<String> bannedStructures = new LinkedHashSet<>();

        public StructureSetOverride() {}

        public StructureSetOverride copy() {
            StructureSetOverride c = new StructureSetOverride();
            c.structureSetId = structureSetId;
            c.enabled = enabled;
            c.spacingOverride = spacingOverride;
            c.separationOverride = separationOverride;
            c.bannedStructures = new LinkedHashSet<>(bannedStructures);
            return c;
        }

        /** Whether this override actually changes anything - an all-default entry is skipped on write/list, same "don't write a no-op file" reasoning TagExporter uses for empty replace values. */
        public boolean isNoOp() {
            return enabled && spacingOverride < 0 && separationOverride < 0 && bannedStructures.isEmpty();
        }
    }

    // Large enough that, in practice, no player will ever walk far enough
    // to see the set generate again - the same "huge spacing" trick
    // community datapacks use to disable a structure_set without emptying
    // its (possibly codec-required-non-empty) "structures" array.
    //
    // MUST stay within RandomSpreadStructurePlacement's codec range
    // [0:4096] for both fields (with separation < spacing) - the old
    // 1_000_000/999_999 values were never actually valid and blew up
    // RegistryDataLoader with "Value 999999 outside of range [0:4096]"
    // the moment a world was (re)loaded with that override on disk,
    // crashing on CreateWorldScreen/SelectWorldScreen before the player
    // ever got in. 4096/4095 is the largest value the codec accepts and
    // is still effectively "never" for a random-spread structure set.
    private static final int DISABLE_SPACING = 4096;
    private static final int DISABLE_SEPARATION = DISABLE_SPACING - 1;

    private static final List<String> LAST_WRITE_WARNINGS = new ArrayList<>();

    public static List<String> getLastWriteWarnings() { return new ArrayList<>(LAST_WRITE_WARNINGS); }

    private static final Map<String, StructureSetOverride> ENTRIES = new LinkedHashMap<>();

    /**
     * Lo ultimo aplicado (o cargado al arrancar): lo que se guarda en el estado y lo que leen
     * los enforcers. Los borradores pendientes viven solo en ENTRIES hasta Aplicar.
     */
    private static volatile Map<String, StructureSetOverride> APPLIED = Map.of();

    public static Collection<StructureSetOverride> getAppliedEntries() { return APPLIED.values(); }
    public static StructureSetOverride getAppliedEntry(String id) { return APPLIED.get(id); }
    public static boolean hasAppliedEntries() { return !APPLIED.isEmpty(); }

    /** Toma la foto de ENTRIES como aplicada; se llama al escribir el script y al cargar el estado. */
    public static void markApplied() {
        Map<String, StructureSetOverride> m = new LinkedHashMap<>();
        for (Map.Entry<String, StructureSetOverride> e : ENTRIES.entrySet()) m.put(e.getKey(), e.getValue().copy());
        APPLIED = java.util.Collections.unmodifiableMap(m);
    }

    public static Collection<StructureSetOverride> getEntries() { return Collections.unmodifiableCollection(ENTRIES.values()); }
    public static StructureSetOverride getEntry(String structureSetId) { return ENTRIES.get(structureSetId); }
    /** Used only by CraftEditorState while restoring from disk - does not write anything. */
    public static void loadEntry(StructureSetOverride ov) { if (ov.structureSetId != null) ENTRIES.put(ov.structureSetId, ov); }
    public static void clearAll() { ENTRIES.clear(); APPLIED = Map.of(); }

    /**
     * Stages/updates an override in memory only - no disk write, no state
     * persist. Mirrors {@link com.kve.common.kubejs.KubeJSExporter#stageRecipe}.
     * Returns the previous entry for this id (or {@code null} if there
     * wasn't one), so the caller can hand it to
     * {@link #discardStagedOverride()} if the queued change is later
     * discarded instead of applied.
     */
    public static StructureSetOverride stageOverride(StructureSetOverride ov) {
        StructureSetOverride previous = ENTRIES.get(ov.structureSetId);
        if (ov.isNoOp()) ENTRIES.remove(ov.structureSetId);
        else ENTRIES.put(ov.structureSetId, ov);
        return previous;
    }

    /** Undoes a {@link #stageOverride()}: restores whatever was staged before (removes the entry entirely if there was nothing). */
    public static void discardStagedOverride(String structureSetId, StructureSetOverride previousOrNull) {
        if (previousOrNull != null) ENTRIES.put(structureSetId, previousOrNull);
        else ENTRIES.remove(structureSetId);
    }

    public static List<String> commitAll(RegistryAccess access) {
        markApplied();
        List<String> warnings = writeAll(access, new ArrayList<>(ENTRIES.values()));
        com.kve.common.persist.CraftEditorState.save();
        return warnings;
    }

    private static Path dataDir() { return Paths.get("kubejs", "data"); }
    private static Path manifestFile() { return dataDir().resolve(".shovel_js_structure_freq_manifest.json"); }

    private static Path resolvePath(ResourceLocation id) {
        return com.kve.common.security.KvePathGuard.resolveResource(
                dataDir(), id.getNamespace(), id.getPath(), ".json", "worldgen", "structure_set");
    }

    public static List<String> listAllStructureSetIds(RegistryAccess access) {
        List<String> out = new ArrayList<>();
        try {
            RegistryAccess resolved = resolveAccess(access);
            var registry = resolved.registryOrThrow(Registries.STRUCTURE_SET);
            registry.keySet().forEach(k -> out.add(k.toString()));
            CraftEditorMod.LOGGER.debug("[Shovel JS] listAllStructureSetIds: resolvedAccess={} registrySize={} ids={}",
                    resolved == access ? "client" : "integrated-server-fallback", registry.keySet().size(), out.size());
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] listAllStructureSetIds failed: {}", ex.toString(), ex);
        }
        java.util.Collections.sort(out);
        return out;
    }

    static RegistryAccess resolveAccess(RegistryAccess access) {
        try {
            if (!access.registryOrThrow(Registries.STRUCTURE_SET).keySet().isEmpty()) return access;
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] resolveAccess: client access has no STRUCTURE_SET registry ({}), falling back to integrated server", ex.toString());
        }
        try {
            net.minecraft.server.MinecraftServer sp = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (sp == null) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] resolveAccess: getSingleplayerServer() returned null - not a locally-hosted world from this client's point of view");
            } else {
                return sp.registryAccess();
            }
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] resolveAccess: integrated server fallback failed: {}", ex.toString(), ex);
        }
        return access;
    }

    /** Structure ids that are members of the given structure_set, read back off the live, codec-re-encoded object - so this always matches exactly what {@link #writeAll()} would otherwise be filtering. Empty (not null) if the id doesn't resolve. */
    public static List<String> listMemberStructures(RegistryAccess access, String structureSetId) {
        List<String> out = new ArrayList<>();
        JsonObject encoded = encodeLive(access, structureSetId);
        if (encoded == null || !encoded.has("structures")) return out;
        for (JsonElement el : encoded.getAsJsonArray("structures")) {
            try {
                JsonObject o = el.getAsJsonObject();
                if (o.has("structure")) out.add(o.get("structure").getAsString());
            } catch (Exception ex) {
                CraftEditorMod.LOGGER.debug("[Shovel JS] elemento invalido en structures de '{}', se ignora: {}", structureSetId, ex.toString());
            }
        }
        return out;
    }

    /** Espaciado y separacion vivos del set ({@code random_spread}), o null si su ubicacion no los usa (anillos, etc.). */
    public static int[] liveSpread(RegistryAccess access, String structureSetId) {
        JsonObject encoded = encodeLive(access, structureSetId);
        if (encoded == null || !encoded.has("placement") || !encoded.get("placement").isJsonObject()) return null;
        JsonObject pl = encoded.getAsJsonObject("placement");
        if (!pl.has("spacing") || !pl.has("separation")) return null;
        try { return new int[]{pl.get("spacing").getAsInt(), pl.get("separation").getAsInt()}; }
        catch (RuntimeException ex) { return null; }
    }

    private static JsonObject encodeLive(RegistryAccess access, String structureSetId) {
        ResourceLocation rl = ResourceLocation.tryParse(structureSetId);
        if (rl == null) return null;
        try {
            RegistryAccess resolved = resolveAccess(access);
            var registry = resolved.registryOrThrow(Registries.STRUCTURE_SET);
            StructureSet value = registry.get(ResourceKey.create(Registries.STRUCTURE_SET, rl));
            if (value == null) return null;
            DataResult<JsonElement> result = StructureSet.DIRECT_CODEC
                    .encodeStart(RegistryOps.create(JsonOps.INSTANCE, resolved), value);
            JsonElement el = result.result().orElse(null);
            return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] No se pudo leer structure_set {}: {}", structureSetId, ex.getMessage());
            return null;
        }
    }

    /**
     * Writes one override file per non-no-op entry in {@code overrides},
     * deletes files for entries removed since the last write, and returns
     * any per-entry warnings (e.g. "this set's placement has no spacing
     * field to ban via"). Requires a loaded world (needs the live registry
     * to start from) - callers should check {@code minecraft.level != null}
     * first, same as every other registry-backed screen in this codebase.
     */
    public static List<String> writeAll(RegistryAccess access, List<StructureSetOverride> overrides) {
        LAST_WRITE_WARNINGS.clear();
        Set<String> previouslyWritten = readManifest();
        Set<String> nowWritten = new LinkedHashSet<>();

        for (StructureSetOverride ov : overrides) {
            if (ov.structureSetId == null || ov.structureSetId.isBlank() || ov.isNoOp()) continue;
            ResourceLocation rl = ResourceLocation.tryParse(ov.structureSetId);
            if (rl == null) {
                warn("Id de structure_set invalido: '" + ov.structureSetId + "' - omitido.");
                continue;
            }
            JsonObject encoded = encodeLive(access, ov.structureSetId);
            if (encoded == null) {
                warn("No se encontro (o no se pudo leer) el structure_set '" + ov.structureSetId + "' - omitido.");
                continue;
            }
            try {
                applyOverride(encoded, ov);
                Path path = resolvePath(rl);
                Files.createDirectories(path.getParent());
                Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
                String pretty = new GsonBuilder().setPrettyPrinting().create().toJson(encoded);
                Files.writeString(tmp, pretty, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                nowWritten.add(path.toString());
            } catch (Exception ex) {
                warn("Fallo al escribir override para '" + ov.structureSetId + "': " + ex.getMessage());
                CraftEditorMod.LOGGER.error("[Shovel JS] Structure frequency write failed for {}: {}", ov.structureSetId, ex.getMessage());
            }
        }

        for (String old : previouslyWritten) {
            if (!nowWritten.contains(old)) {
                try {
                    if (!com.kve.common.security.KvePathGuard.deleteRecordedFile(dataDir(), old)) {
                        CraftEditorMod.LOGGER.debug("[Shovel JS] entrada de manifest rechazada o ya inexistente: '{}'", old);
                    }
                } catch (IOException ex) {
                    CraftEditorMod.LOGGER.debug("[Shovel JS] no se pudo borrar el archivo viejo '{}': {}", old, ex.toString());
                }
            }
        }
        writeManifest(nowWritten);
        return getLastWriteWarnings();
    }

    /**
     * Mutates the already-encoded structure_set JSON in place: applies the
     * spacing/separation override (or the "disable" trick when the whole
     * set is banned), and drops banned member structures from the
     * "structures" array. If banning would leave that array empty (or the
     * set's placement has no spacing field to disable via, e.g. vanilla's
     * concentric_rings-placed strongholds), falls back to warning instead
     * of writing something that would either error or silently do nothing.
     */
    private static void applyOverride(JsonObject encoded, StructureSetOverride ov) {
        JsonObject placement = encoded.has("placement") && encoded.get("placement").isJsonObject()
                ? encoded.getAsJsonObject("placement") : null;
        boolean hasSpacingField = placement != null && placement.has("spacing") && placement.has("separation");

        if (!ov.enabled) {
            if (hasSpacingField) {
                placement.addProperty("spacing", DISABLE_SPACING);
                placement.addProperty("separation", DISABLE_SEPARATION);
            } else {
                warn("'" + ov.structureSetId + "': su placement no tiene spacing/separation (no es random_spread) - no se puede banear el set entero por esta via.");
            }
            return;
        }

        if (!ov.bannedStructures.isEmpty() && encoded.has("structures") && encoded.get("structures").isJsonArray()) {
            JsonArray structures = encoded.getAsJsonArray("structures");
            JsonArray kept = new JsonArray();
            for (JsonElement el : structures) {
                String id = el.isJsonObject() && el.getAsJsonObject().has("structure")
                        ? el.getAsJsonObject().get("structure").getAsString() : null;
                if (id == null || !ov.bannedStructures.contains(id)) kept.add(el);
            }
            if (kept.size() == 0) {
                if (hasSpacingField) {
                    placement.addProperty("spacing", DISABLE_SPACING);
                    placement.addProperty("separation", DISABLE_SEPARATION);
                } else {
                    warn("'" + ov.structureSetId + "': banear todos sus miembros dejaria 'structures' vacio y su placement no tiene spacing para compensar - omitido.");
                }
            } else {
                encoded.add("structures", kept);
            }
        }

        if (hasSpacingField) {
            if (ov.spacingOverride >= 0) placement.addProperty("spacing", ov.spacingOverride);
            if (ov.separationOverride >= 0) placement.addProperty("separation", ov.separationOverride);
        } else if (ov.spacingOverride >= 0 || ov.separationOverride >= 0) {
            warn("'" + ov.structureSetId + "': su placement no tiene spacing/separation (no es random_spread) - override de frecuencia ignorado.");
        }
    }

    private static void warn(String msg) {
        LAST_WRITE_WARNINGS.add(msg);
        CraftEditorMod.LOGGER.warn("[Shovel JS] {}", msg);
    }

    private static Set<String> readManifest() {
        try {
            if (!Files.exists(manifestFile())) return new LinkedHashSet<>();
            String json = Files.readString(manifestFile(), StandardCharsets.UTF_8);
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            Set<String> out = new LinkedHashSet<>();
            for (JsonElement el : arr) out.add(el.getAsString());
            return out;
        } catch (Exception ex) {
            return new LinkedHashSet<>();
        }
    }

    private static void writeManifest(Set<String> paths) {
        try {
            Files.createDirectories(dataDir());
            JsonArray arr = new JsonArray();
            for (String p : paths) arr.add(p);
            Files.writeString(manifestFile(), new Gson().toJson(arr), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Structure frequency manifest write failed: {}", ex.getMessage());
        }
    }
}
