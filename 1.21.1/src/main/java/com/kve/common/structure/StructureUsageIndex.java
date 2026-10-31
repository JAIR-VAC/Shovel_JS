package com.kve.common.structure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kve.CraftEditorMod;
import com.kve.common.structure.StructureVoxelGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StructureUsageIndex {

    private StructureUsageIndex() {}

    /** One reference to the searched id found inside a saved structure. */
    public record Hit(String structureId, String refId, String field) {}

    private record RefEntry(String id, String field) {}

    private static final Path CACHE_FILE = Paths.get("kubejs", "config", "shovel_js_structure_usage_index.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, List<RefEntry>> BY_STRUCTURE = new ConcurrentHashMap<>();

    private static final AtomicBoolean LOADED = new AtomicBoolean(false);
    private static final AtomicBoolean BUILDING = new AtomicBoolean(false);

    /**
     * Single background thread for both the initial full rebuild and every
     * incremental disk write below - same rationale as
     * {@code CraftEditorState.SAVE_EXECUTOR}: only one thread ever touches
     * {@link #CACHE_FILE}, so two writes can never interleave and corrupt it.
     */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Shovel JS-StructureUsageIndex");
        t.setDaemon(true);
        return t;
    });

    /**
     * Loads the on-disk cache (if any) into memory the first time this is
     * called, then kicks off a one-time background rebuild to catch drift.
     * Safe and cheap to call from every {@link #search} - after the first
     * call it's just an {@code AtomicBoolean} check.
     */
    public static void ensureLoaded() {
        if (LOADED.compareAndSet(false, true)) {
            loadCacheFromDisk();
            rebuildAsync();
        }
    }

    /** True while the one-time background rebuild (or a later forced one) is still running - {@code IdUsageScreen} can use this to show a "still indexing structures..." hint instead of implying an empty result set is final. */
    public static boolean isBuilding() {
        return BUILDING.get();
    }

    public static void forceRebuild() {
        LOADED.set(true);
        rebuildAsync();
    }

    /**
     * Case-insensitive substring search across every structure this index
     * currently knows about. Mirrors {@code IdUsageIndex.search}'s own
     * "empty query -> no results" rule for the same reason (a screen that
     * dumps everything before the player finishes typing is worse than
     * showing nothing yet).
     */
    public static List<Hit> search(String rawNeedle) {
        ensureLoaded();
        String needle = normalize(rawNeedle);
        List<Hit> hits = new ArrayList<>();
        if (needle.isEmpty()) return hits;
        for (Map.Entry<String, List<RefEntry>> e : BY_STRUCTURE.entrySet()) {
            for (RefEntry ref : e.getValue()) {
                if (ref.id().contains(needle)) hits.add(new Hit(e.getKey(), ref.id(), ref.field()));
            }
        }
        hits.sort(Comparator.comparing(Hit::structureId).thenComparing(Hit::field));
        return hits;
    }

    /**
     * Incremental update for ONE structure, called by {@link StructureNbtIO#save}
     * right after it finishes writing that structure's {@code .nbt} file.
     * Takes the grid that was just saved directly (it's already fully in
     * memory at that call site) instead of re-reading the file back off
     * disk - cheaper, and avoids a save/index race against a concurrent
     * read of the file we just wrote.
     */
    public static void updateForStructure(ResourceLocation id, StructureVoxelGrid grid) {
        loadCacheQuietly();
        String key = id.toString();
        List<RefEntry> refs = scanGrid(grid);
        if (refs.isEmpty()) BY_STRUCTURE.remove(key);
        else BY_STRUCTURE.put(key, refs);
        persistAsync();
    }

    /** Drops one structure's entries entirely - called by {@link StructureNbtIO#restore} right after it deletes that structure's override file. */
    public static void removeStructure(ResourceLocation id) {
        loadCacheQuietly();
        if (BY_STRUCTURE.remove(id.toString()) != null) persistAsync();
    }

    public static void rebuildSync() {
        BUILDING.set(true);
        try {
            doRebuild();
        } finally {
            BUILDING.set(false);
        }
    }

    private static void rebuildAsync() {
        if (!BUILDING.compareAndSet(false, true)) return;
        EXECUTOR.execute(() -> {
            try {
                doRebuild();
            } catch (Exception ex) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Fallo al reconstruir el indice de uso de estructuras: {}", ex.getMessage());
            } finally {
                BUILDING.set(false);
            }
        });
    }

    private static void doRebuild() {
        List<ResourceLocation> ids;
        try {
            ids = StructureNbtIO.listOverrideIds();
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] No se pudieron listar las estructuras para el indice: {}", ex.getMessage());
            return;
        }
        Map<String, List<RefEntry>> fresh = new LinkedHashMap<>();
        for (ResourceLocation id : ids) {
            try {
                StructureVoxelGrid grid = StructureNbtIO.load(id);
                List<RefEntry> refs = scanGrid(grid);
                if (!refs.isEmpty()) fresh.put(id.toString(), refs);
            } catch (IOException ex) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] No se pudo indexar la estructura {}: {}", id, ex.getMessage());
            }
        }
        BY_STRUCTURE.clear();
        BY_STRUCTURE.putAll(fresh);
        persist();
        CraftEditorMod.LOGGER.info("[Shovel JS] Indice de uso de estructuras reconstruido: {} estructura(s).", fresh.size());
    }

    private static List<RefEntry> scanGrid(StructureVoxelGrid grid) {
        java.util.LinkedHashSet<RefEntry> refs = new java.util.LinkedHashSet<>();
        for (Map.Entry<BlockPos, BlockState> e : grid.cellsView().entrySet()) {
            if (e.getValue().isAir()) continue;
            String blockId = idOf(e.getValue());
            if (blockId != null) refs.add(new RefEntry(blockId, "block"));

            CompoundTag te = grid.getBlockEntityNbt(e.getKey());
            if (te != null && te.contains("Items", 9 )) {
                ListTag items = te.getList("Items", 10 );
                for (int i = 0; i < items.size(); i++) {
                    CompoundTag stack = items.getCompound(i);
                    if (stack.contains("id", 8 )) {
                        String itemId = normalize(stack.getString("id"));
                        if (!itemId.isEmpty()) refs.add(new RefEntry(itemId, "block entity item"));
                    }
                }
            }
        }
        List<RefEntry> list = new ArrayList<>(refs);
        list.sort(Comparator.comparing(RefEntry::id));
        return list;
    }

    private static String idOf(BlockState state) {
        if (state == null) return null;
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key == null ? null : normalize(key.toString());
    }

    /** Loads {@link #CACHE_FILE} into {@link #BY_STRUCTURE} if present, without kicking off a rebuild - used from the incremental update paths, which must not recursively trigger {@link #ensureLoaded()}'s own rebuild. */
    private static void loadCacheQuietly() {
        if (LOADED.compareAndSet(false, true)) {
            loadCacheFromDisk();
        }
    }

    private static void loadCacheFromDisk() {
        if (!Files.exists(CACHE_FILE)) return;
        try {
            String json = Files.readString(CACHE_FILE, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> e : root.entrySet()) {
                List<RefEntry> refs = new ArrayList<>();
                for (com.google.gson.JsonElement el : e.getValue().getAsJsonArray()) {
                    JsonObject o = el.getAsJsonObject();
                    refs.add(new RefEntry(o.get("id").getAsString(), o.get("field").getAsString()));
                }
                BY_STRUCTURE.put(e.getKey(), refs);
            }
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] No se pudo leer el cache del indice de estructuras ({}) - se reconstruira desde cero.", ex.getMessage());
        }
    }

    private static void persistAsync() {
        EXECUTOR.execute(StructureUsageIndex::persist);
    }

    /** Runs on {@link #EXECUTOR} only (either directly, from an incremental update, or as the tail of {@link #doRebuild()}) - never called from the render thread. */
    private static void persist() {
        try {
            JsonObject root = new JsonObject();
            for (Map.Entry<String, List<RefEntry>> e : BY_STRUCTURE.entrySet()) {
                JsonArray arr = new JsonArray();
                for (RefEntry ref : e.getValue()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("id", ref.id());
                    o.addProperty("field", ref.field());
                    arr.add(o);
                }
                root.add(e.getKey(), arr);
            }
            Files.createDirectories(CACHE_FILE.getParent());
            Path tmp = CACHE_FILE.resolveSibling(CACHE_FILE.getFileName().toString() + ".tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, CACHE_FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] No se pudo guardar el cache del indice de estructuras: {}", ex.getMessage());
        }
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
