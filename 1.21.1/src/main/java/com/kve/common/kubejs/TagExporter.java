package com.kve.common.kubejs;

import com.google.gson.*;
import com.kve.CraftEditorMod;
import com.kve.common.persist.CraftEditorState;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Handles adding/removing entries to/from tags (existing or brand new),
 * for items, blocks, entity types, and fluids, plus a "replace" mode that
 * fully overwrites a tag's contents.
 *
 * Two different mechanisms are used on purpose:
 *  - add/remove (default mode) is written as a KubeJS startup script
 *    (ServerEvents.tags), because "remove a specific entry from a tag"
 *    isn't something the vanilla datapack tag format can express on its
 *    own - it requires the scripting layer to merge tags then strip
 *    entries at runtime.
 *  - replace mode is written as a plain vanilla tag JSON file with
 *    "replace": true, because that IS a native datapack feature and
 *    doing it that way is 100% reliable regardless of KubeJS version,
 *    instead of guessing at a scripting method that may not exist.
 */
public class TagExporter {

    public enum RegistryType {
        ITEM("item", "item"), BLOCK("block", "block"),
        ENTITY_TYPE("entity_type", "entity_type"), FLUID("fluid", "fluid");

        public final String kubejsId;
        public final String folder;
        RegistryType(String kubejsId, String folder) { this.kubejsId = kubejsId; this.folder = folder; }

        public RegistryType next() {
            RegistryType[] v = values();
            return v[(ordinal() + 1) % v.length];
        }

        public static RegistryType fromKubejsId(String kubejsId) {
            for (RegistryType t : values()) if (t.kubejsId.equals(kubejsId)) return t;
            return null;
        }
    }

    public static class TagEntry {
        public RegistryType regType = RegistryType.ITEM;
        public String tagId = "";
        public boolean enabled = true;
        public boolean replace = false;
        public String category = "";
        public List<String> added   = new ArrayList<>();
        public List<String> removed = new ArrayList<>();

        public String key() { return regType.name() + "|" + tagId; }

        public TagEntry copy() {
            TagEntry c = new TagEntry();
            c.regType = regType; c.tagId = tagId; c.enabled = enabled; c.replace = replace; c.category = category;
            c.added = new ArrayList<>(added);
            c.removed = new ArrayList<>(removed);
            return c;
        }
    }

    private static final List<String> LAST_WRITE_WARNINGS = new ArrayList<>();

    public static List<String> getLastWriteWarnings() { return Collections.unmodifiableList(LAST_WRITE_WARNINGS); }

    private static final Map<String, TagEntry> ENTRIES = new LinkedHashMap<>();

    /**
     * Lo ultimo aplicado (o cargado al arrancar): lo que se guarda en el estado y lo que leen
     * los enforcers. Los borradores pendientes viven solo en ENTRIES hasta Aplicar.
     */
    private static volatile Map<String, TagEntry> APPLIED = Map.of();

    public static Collection<TagEntry> getAppliedEntries() { return APPLIED.values(); }
    public static TagEntry getAppliedEntry(String id) { return APPLIED.get(id); }
    public static boolean hasAppliedEntries() { return !APPLIED.isEmpty(); }

    /** Toma la foto de ENTRIES como aplicada; se llama al escribir el script y al cargar el estado. */
    public static void markApplied() {
        Map<String, TagEntry> m = new LinkedHashMap<>();
        for (Map.Entry<String, TagEntry> e : ENTRIES.entrySet()) m.put(e.getKey(), e.getValue().copy());
        APPLIED = java.util.Collections.unmodifiableMap(m);
    }
    private static final Map<String, Deque<TagEntry>> HISTORY =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Deque<TagEntry>> eldest) {
                    return size() > Math.max(1, com.kve.common.config.KVEConfig.undoHistoryMaxKeys);
                }
            };

    /** Stages a tag into ENTRIES (in-memory only, no script/state write). */
    public static void stageTag(TagEntry e) {
        TagEntry previous = ENTRIES.get(e.key());
        if (previous != null) pushHistory(e.key(), previous);
        ENTRIES.put(e.key(), e);
    }

    /** Undoes a stageTag(): drops the staged entry and restores whatever was there before (null if it was new). */
    public static void discardStagedTag(String key, TagEntry previousOrNull) {
        if (previousOrNull != null) ENTRIES.put(key, previousOrNull);
        else ENTRIES.remove(key);
    }

    /** Stages a tag deletion (in-memory only). */
    public static void stageDeleteTag(String key) {
        ENTRIES.remove(key);
        HISTORY.remove(key);
    }

    /** Stages an undo (in-memory only, no script/state write) - see stageTag(). Returns the restored entry, or null if there's nothing to undo. */
    public static TagEntry stageUndo(String key) {
        Deque<TagEntry> h = HISTORY.get(key);
        if (h == null || h.isEmpty()) return null;
        TagEntry restored = h.removeFirst();
        ENTRIES.put(key, restored);
        return restored;
    }

    /** Undoes a stageUndo(): pushes the restored entry back onto the undo history and restores whatever ENTRIES had before the undo (null if it was new). */
    public static void discardStagedUndo(String key, TagEntry restored, TagEntry beforeUndoOrNull) {
        Deque<TagEntry> h = HISTORY.computeIfAbsent(key, k -> new ArrayDeque<>());
        h.addFirst(restored);
        if (beforeUndoOrNull != null) ENTRIES.put(key, beforeUndoOrNull);
        else ENTRIES.remove(key);
    }

    /** Writes the currently staged ENTRIES to disk (KubeJS script + KVE state). Only called from a PendingChange's onApply. */
    public static void commitTags() {
        writeAll();
        CraftEditorState.save();
    }

    private static void pushHistory(String key, TagEntry snapshot) {
        int max = com.kve.common.config.KVEConfig.undoHistorySize;
        if (max <= 0) return;
        Deque<TagEntry> h = HISTORY.computeIfAbsent(key, k -> new ArrayDeque<>());
        h.addFirst(snapshot.copy());
        while (h.size() > max) h.removeLast();
    }

    public static boolean canUndo(String key) {
        Deque<TagEntry> h = HISTORY.get(key);
        return h != null && !h.isEmpty();
    }

    public static TagEntry undo(String key) {
        Deque<TagEntry> h = HISTORY.get(key);
        if (h == null || h.isEmpty()) return null;
        TagEntry previous = h.removeFirst();
        ENTRIES.put(key, previous);
        writeAll();
        CraftEditorState.save();
        return previous;
    }

    public static Collection<TagEntry> getEntries()  { return Collections.unmodifiableCollection(ENTRIES.values()); }
    public static TagEntry             getEntry(String key) { return ENTRIES.get(key); }
    public static void clearAll() { ENTRIES.clear(); APPLIED = Map.of(); HISTORY.clear(); }
    public static void loadEntry(TagEntry e) { ENTRIES.put(e.key(), e); }

    /**
     * A short curated list of commonly used tags per registry type, offered
     * in the "Common Tags" picker so people don't have to remember exact
     * ids for the tags they reach for most.
     */
    public static List<String> commonTags(RegistryType type) {
        return switch (type) {
            case ITEM -> List.of(
                    "c:ores", "c:ingots", "c:nuggets", "c:dusts", "c:gems",
                    "c:rods", "c:storage_blocks", "c:tools",
                    "minecraft:tools", "minecraft:planks", "minecraft:logs", "minecraft:saplings",
                    "minecraft:flowers", "minecraft:beds", "minecraft:wool", "minecraft:music_discs",
                    "c:crops", "c:seeds", "c:foods");
            case BLOCK -> List.of(
                    "minecraft:logs", "minecraft:planks", "minecraft:leaves", "minecraft:beds",
                    "minecraft:wool", "c:ores", "c:stones", "c:storage_blocks",
                    "minecraft:mineable/pickaxe", "minecraft:mineable/axe", "minecraft:mineable/shovel");
            case ENTITY_TYPE -> List.of(
                    "minecraft:skeletons", "minecraft:raiders", "minecraft:beehive_inhabitors",
                    "minecraft:impact_projectiles", "minecraft:zombies", "minecraft:arthropod");
            case FLUID -> List.of("minecraft:water", "minecraft:lava", "c:water", "c:lava");
        };
    }

    /**
     * Basic validation: needs a namespaced tag id and at least one add/remove
     * entry (or, in replace mode, at least one value), otherwise the
     * generated file/script would be a silent no-op.
     */
    public static List<String> validateTag(TagEntry e) {
        List<String> errors = new ArrayList<>();
        if (e.tagId == null || e.tagId.isBlank()) {
            errors.add(com.kve.common.Lang.get("gui.kve.tag.err.empty_id"));
        } else if (!e.tagId.contains(":") || !e.tagId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            errors.add(com.kve.common.Lang.get("gui.kve.tag.err.bad_id_format"));
        }
        if (e.replace) {
            if (e.added == null || e.added.isEmpty())
                errors.add(com.kve.common.Lang.get("gui.kve.tag.err.replace_needs_value"));
        } else {
            boolean anyAdd = e.added != null && !e.added.isEmpty();
            boolean anyRemove = e.removed != null && !e.removed.isEmpty();
            if (!anyAdd && !anyRemove) errors.add(com.kve.common.Lang.get("gui.kve.tag.err.need_add_or_remove"));
        }

        // Semantic layer: each value must at least look like a valid id for
        // e.regType, and (when the matching registry is actually populated)
        // must exist in it. See SemanticValidator for the graceful-
        // degradation rule when no registry is available to check against.
        if (e.added != null) for (String v : e.added) errors.addAll(semanticValueErrors(e.regType, v));
        if (e.removed != null) for (String v : e.removed) errors.addAll(semanticValueErrors(e.regType, v));

        return errors;
    }

    private static List<String> semanticValueErrors(RegistryType type, String value) {
        return switch (type) {
            case ITEM -> SemanticValidator.validateItemId(value, "Valor");
            case BLOCK -> SemanticValidator.validateBlockId(value, "Valor");
            case FLUID -> SemanticValidator.validateFluidId(value, "Valor");
            case ENTITY_TYPE -> SemanticValidator.validateEntityId(value, "Valor");
        };
    }

    private static Path scriptDir() { return KveScriptPaths.tagsDir(); }
    private static Path dataDir()   { return Paths.get("kubejs", "data"); }
    private static Path manifestFile() { return dataDir().resolve(".shovel_js_tags_manifest.json"); }

    private static Path replacePathFor(TagEntry e) {
        ResourceLocation rl;
        try { rl = ResourceLocation.parse(e.tagId); } catch (Exception ex) { return null; }
        try {
            return com.kve.common.security.KvePathGuard.resolveResource(
                    dataDir(), rl.getNamespace(), rl.getPath(), ".json", "tags", e.regType.folder);
        } catch (IllegalArgumentException ex) {
            String warning = "Tag id invalido (" + ex.getMessage() + "): " + e.tagId;
            LAST_WRITE_WARNINGS.add(warning);
            CraftEditorMod.LOGGER.warn("[Shovel JS] {}", warning);
            return null;
        }
    }

    private static void writeAll() {
        LAST_WRITE_WARNINGS.clear();
        writeReplaceFiles();
        writeScript();
    }

    /** Filters {@code values} down to the ones that pass {@link #semanticValueErrors()} for {@code type}, warning (and logging) about every one that's dropped instead of ever letting a bad value ride along with the good ones into the same generated add()/remove()/replace-file. */
    private static List<String> filterValid(RegistryType type, List<String> values, String tagId) {
        List<String> ok = new ArrayList<>();
        if (values == null) return ok;
        for (String v : values) {
            List<String> errs = semanticValueErrors(type, v);
            if (errs.isEmpty()) {
                ok.add(v);
            } else {
                String warning = "Tag '" + tagId + "': valor '" + v + "' omitido - " + String.join("; ", errs);
                LAST_WRITE_WARNINGS.add(warning);
                CraftEditorMod.LOGGER.warn("[Shovel JS] {}", warning);
            }
        }
        return ok;
    }

    private static void writeReplaceFiles() {
        Set<String> previouslyWritten = readManifest();
        Set<String> nowWritten = new LinkedHashSet<>();

        for (TagEntry e : ENTRIES.values()) {
            if (!e.enabled || !e.replace) continue;
            Path p = replacePathFor(e);
            if (p == null) continue;
            List<String> validValues = filterValid(e.regType, e.added, e.tagId);
            if (validValues.isEmpty()) {
                continue;
            }
            try {
                Files.createDirectories(p.getParent());
                JsonObject json = new JsonObject();
                json.addProperty("replace", true);
                JsonArray values = new JsonArray();
                for (String id : validValues) values.add(id);
                json.add("values", values);
                com.kve.common.util.SafeFiles.writeAtomic(p, new GsonBuilder().setPrettyPrinting().create().toJson(json));
                nowWritten.add(p.toString());
            } catch (IOException ex) {
                CraftEditorMod.LOGGER.error("[Shovel JS] Tag replace-file write failed for {}: {}", e.tagId, ex.getMessage());
            }
        }

        for (String old : previouslyWritten) {
            if (!nowWritten.contains(old)) {
                try { com.kve.common.security.KvePathGuard.deleteRecordedFile(dataDir(), old); } catch (IOException ignored) {}
            }
        }
        writeManifest(nowWritten);
    }

    public static java.util.List<String> getManifestPaths() {
        return new java.util.ArrayList<>(readManifest());
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
            com.kve.common.util.SafeFiles.writeAtomic(manifestFile(), new Gson().toJson(arr));
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Tag manifest write failed: {}", ex.getMessage());
        }
    }

    private static void writeScript() {
        markApplied();
        try { Files.createDirectories(scriptDir()); } catch (IOException ex) { return; }

        Map<RegistryType, List<TagEntry>> byType = new EnumMap<>(RegistryType.class);
        for (TagEntry e : ENTRIES.values()) {
            if (!e.enabled || e.replace) continue;
            byType.computeIfAbsent(e.regType, k -> new ArrayList<>()).add(e);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("// Shovel JS - Tags (AUTO-GENERATED)\n");
        sb.append("// Apply with: /reload\n");
        sb.append("// Note: tags with \"Replace\" mode are NOT here - they are written as\n");
        sb.append("// raw datapack files under kubejs/data/.../tags/... instead.\n\n");

        for (TagEntry e : ENTRIES.values()) {
            if (e.enabled) continue;
            sb.append("// DISABLED: ").append(e.tagId).append(" (").append(e.regType.kubejsId).append(")\n");
        }
        if (!ENTRIES.isEmpty()) sb.append("\n");

        for (RegistryType type : RegistryType.values()) {
            List<TagEntry> list = byType.get(type);
            if (list == null || list.isEmpty()) continue;

            sb.append("ServerEvents.tags('").append(type.kubejsId).append("', event => {\n");
            for (TagEntry e : list) {
                appendTagEntry(sb, e, "  ");
                sb.append("\n");
            }
            sb.append("})\n\n");
        }

        ScriptWriteGuard.writeIfValid(KveScriptPaths.tagsFile(), sb.toString(), "shovel_js_tags");
    }

    /** Writes one tag's event.add(...)/event.remove(...) calls (add/remove mode only - replace mode has no JS form, see previewTagJS()). Only values that pass the semantic guard are emitted - a bad value is dropped (with a warning, see filterValid()) instead of riding along into the same call as the good ones. */
    private static void appendTagEntry(StringBuilder sb, TagEntry e, String indent) {
        List<String> added = filterValid(e.regType, e.added, e.tagId);
        List<String> removed = filterValid(e.regType, e.removed, e.tagId);
        if (!e.category.isEmpty()) sb.append(indent).append("// category: ").append(e.category).append("\n");
        if (!added.isEmpty()) {
            sb.append(indent).append("event.add('").append(e.tagId).append("',\n");
            for (int i = 0; i < added.size(); i++) {
                sb.append(indent).append("  '").append(added.get(i)).append("'");
                sb.append(i < added.size() - 1 ? ",\n" : "\n");
            }
            sb.append(indent).append(")\n");
        }
        if (!removed.isEmpty()) {
            sb.append(indent).append("event.remove('").append(e.tagId).append("',\n");
            for (int i = 0; i < removed.size(); i++) {
                sb.append(indent).append("  '").append(removed.get(i)).append("'");
                sb.append(i < removed.size() - 1 ? ",\n" : "\n");
            }
            sb.append(indent).append(")\n");
        }
    }

    /**
     * Read-only preview of exactly what saving this tag entry would emit,
     * without touching disk or the ENTRIES map. Replace-mode entries don't
     * have a JS form at all (see the class-level note on why) so those get
     * the plain datapack JSON instead, with a comment noting where it'd
     * actually be written. Same pattern as
     * {@code KubeJSExporter#previewRecipeJS}.
     */
    public static String previewTagJS(TagEntry e) {
        if (e == null) return "// (sin datos de tag)";
        try {
            if (e.replace) {
                JsonObject json = new JsonObject();
                json.addProperty("replace", true);
                JsonArray values = new JsonArray();
                for (String id : e.added) values.add(id);
                json.add("values", values);
                Path p = replacePathFor(e);
                String pretty = new GsonBuilder().setPrettyPrinting().create().toJson(json);
                return "// " + (p == null ? "(id de tag invalido)" : p.toString()) + "\n" + pretty;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("ServerEvents.tags('").append(e.regType.kubejsId).append("', event => {\n");
            appendTagEntry(sb, e, "  ");
            sb.append("})\n");
            return sb.toString();
        } catch (Exception ex) {
            return "// ERROR generando preview: " + ex.getMessage();
        }
    }
}
