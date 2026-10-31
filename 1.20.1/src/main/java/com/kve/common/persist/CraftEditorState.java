package com.kve.common.persist;

import com.kve.CraftEditorMod;
import com.kve.common.config.KVEConfig;
import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.kubejs.KubeJSItemAttrExporter;
import com.kve.common.kubejs.KubeJSBlockAttrExporter;
import com.kve.common.kubejs.KubeJSMobExporter;
import com.kve.common.kubejs.TagExporter;
import com.kve.common.kubejs.LootTableExporter;
import com.kve.common.recipe.RecipeData;
import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CraftEditorState {

    private static final Path STATE_FILE =
            Paths.get("kubejs", "config", "shovel_js_state.json");
    private static final Path TEMP_FILE  =
            Paths.get("kubejs", "config", "shovel_js_state.tmp");
    private static final Path BACKUP_DIR =
            Paths.get("kubejs", "config", "shovel_js_backups");
    private static final Path EXPORT_FILE =
            Paths.get("kubejs", "config", "shovel_js_export.json");
    private static final Path IMPORT_FILE =
            Paths.get("kubejs", "config", "shovel_js_import.json");
    private static final Path IMPORT_FILE_JS =
            Paths.get("kubejs", "config", "shovel_js_import.js");

    private static Path resolveImportFile() {
        if (Files.exists(IMPORT_FILE))    return IMPORT_FILE;
        if (Files.exists(IMPORT_FILE_JS)) return IMPORT_FILE_JS;
        return null;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static boolean loaded    = false;
    private static boolean dirty     = false;
    private static long    lastMtime = 0L;
    private static boolean sizeWarned = false;

    private static final Set<String> FAVORITE_ITEMS   = new LinkedHashSet<>();
    private static final Set<String> FAVORITE_MOBS    = new LinkedHashSet<>();
    private static final Set<String> FAVORITE_RECIPES = new LinkedHashSet<>();
    private static final Set<String> FAVORITE_BLOCKS  = new LinkedHashSet<>();
    private static final Set<String> FAVORITE_LOOT    = new LinkedHashSet<>();
    private static final Set<String> FAVORITE_TAGS    = new LinkedHashSet<>();

    /**
     * Contador que sube con cada mutacion de cualquiera de los 5 sets de
     * favoritos de arriba (y con cada {@link #load()}, que los repuebla
     * enteros desde disco).
     * <p>
     * Perf: las grillas de ItemAttributeScreen/BlockAttributeScreen/
     * MobEditorScreen re-filtran y RE-ORDENAN su lista completa en cada
     * rebuild de widgets - y un rebuild se dispara en cada evento de
     * mouseDragged del grip de {@link com.kve.client.gui.ResizeHandle}.
     * El unico dato externo del que depende ese orden es "que esta
     * marcado como favorito"; comparar este entero contra el de la ultima
     * vez les permite saltarse el trabajo entero cuando nada cambio, sin
     * tener que copiar o hashear los sets.
     */
    private static int favoritesEpoch = 0;

    /** Ver {@link #favoritesEpoch()}. */
    public static int favoritesEpoch() { return favoritesEpoch; }

    public static boolean isFavoriteItem(String id)   { return FAVORITE_ITEMS.contains(id); }
    public static boolean isFavoriteMob(String id)    { return FAVORITE_MOBS.contains(id); }
    public static boolean isFavoriteRecipe(String id) { return id != null && FAVORITE_RECIPES.contains(id); }
    public static boolean isFavoriteBlock(String id)  { return id != null && FAVORITE_BLOCKS.contains(id); }
    public static boolean isFavoriteLoot(String key)  { return key != null && FAVORITE_LOOT.contains(key); }
    public static boolean isFavoriteTag(String key)   { return key != null && FAVORITE_TAGS.contains(key); }

    public static void toggleFavoriteItem(String id) {
        if (id == null || id.isEmpty()) return;
        if (!FAVORITE_ITEMS.remove(id)) FAVORITE_ITEMS.add(id);
        favoritesEpoch++;
        save();
    }

    public static void toggleFavoriteMob(String id) {
        if (id == null || id.isEmpty()) return;
        if (!FAVORITE_MOBS.remove(id)) FAVORITE_MOBS.add(id);
        favoritesEpoch++;
        save();
    }

    public static void toggleFavoriteRecipe(String id) {
        if (id == null || id.isEmpty()) return;
        if (!FAVORITE_RECIPES.remove(id)) FAVORITE_RECIPES.add(id);
        favoritesEpoch++;
        save();
    }

    public static void toggleFavoriteBlock(String id) {
        if (id == null || id.isEmpty()) return;
        if (!FAVORITE_BLOCKS.remove(id)) FAVORITE_BLOCKS.add(id);
        favoritesEpoch++;
        save();
    }

    public static void toggleFavoriteLoot(String key) {
        if (key == null || key.isEmpty()) return;
        if (!FAVORITE_LOOT.remove(key)) FAVORITE_LOOT.add(key);
        favoritesEpoch++;
        save();
    }

    public static void toggleFavoriteTag(String key) {
        if (key == null || key.isEmpty()) return;
        if (!FAVORITE_TAGS.remove(key)) FAVORITE_TAGS.add(key);
        favoritesEpoch++;
        save();
    }

    public static List<String> getFavoriteItems()   { return new ArrayList<>(FAVORITE_ITEMS); }
    public static List<String> getFavoriteMobs()    { return new ArrayList<>(FAVORITE_MOBS); }
    public static List<String> getFavoriteRecipes() { return new ArrayList<>(FAVORITE_RECIPES); }
    public static List<String> getFavoriteBlocks()  { return new ArrayList<>(FAVORITE_BLOCKS); }
    public static List<String> getFavoriteLoot()    { return new ArrayList<>(FAVORITE_LOOT); }

    /**
     * Every distinct, non-blank {@code category} value currently used
     * across the custom recipe list, in first-seen order. Powers the
     * category filter chips in {@code CraftEditorScreen}; categories are
     * free text per recipe ({@link RecipeData#category}) - there is no
     * separate registry of "known" categories to maintain.
     */
    public static List<String> getRecipeCategories() {
        LinkedHashSet<String> cats = new LinkedHashSet<>();
        for (RecipeData r : KubeJSExporter.getAddedRecipes()) {
            if (r.category != null && !r.category.isBlank()) cats.add(r.category);
        }
        return new ArrayList<>(cats);
    }

    private static final long MTIME_CHECK_INTERVAL_MS = 250L;

    private static long lastMtimeCheckAt = 0L;

    public static synchronized void loadIfNeeded() {
        if (loaded) {
            long now = System.currentTimeMillis();
            if (now - lastMtimeCheckAt < MTIME_CHECK_INTERVAL_MS) return;
            lastMtimeCheckAt = now;
            try {
                long mtime = Files.exists(STATE_FILE)
                        ? Files.getLastModifiedTime(STATE_FILE).toMillis() : 0;
                if (mtime == lastMtime) return;
            } catch (IOException ignored) { return; }
        }
        load();
    }

    public static synchronized void forceReload() {
        loaded = false;
        sizeWarned = false;
        load();
    }

    /**
     * Every call that mutates or exports/imports the shared state goes
     * through this check first. {@code com.kve.client.permission.ClientPermissionState}
     * holds the role last negotiated with the server over the network (see
     * that class + {@code KvePermissionManager}); on a dedicated server the
     * default is fail-closed (NONE) until the server confirms otherwise.
     * Singleplayer/LAN is unaffected (always resolves to full access), so
     * this is a no-op behavior-wise for the common case.
     */
    public static boolean canEditNow() {
        return com.kve.common.permission.EditPermission.canEdit();
    }

    private static boolean requireEdit(String action) {
        if (com.kve.common.permission.EditPermission.canEdit()) return true;
        CraftEditorMod.LOGGER.warn("[Shovel JS] Blocked '{}': player lacks edit permission on this server.", action);
        return false;
    }

    public static void save() {
        if (!requireEdit("save")) return;
        dirty = true;
        flushAsync();
    }

    public static void saveNow() {
        java.util.concurrent.Future<?> confirmation;
        synchronized (CraftEditorState.class) {
            if (!dirty) return;
            final String snapshot;
            try {
                snapshot = GSON.toJson(buildJson());
            } catch (Exception e) {
                CraftEditorMod.LOGGER.error("[Shovel JS] Snapshot failed: {}", e.getMessage());
                return;
            }
            checkFileSize(snapshot);
            latestSnapshot.set(snapshot);
            dirty = false;

            if (writerActive.compareAndSet(false, true)) {
                confirmation = SAVE_EXECUTOR.submit(CraftEditorState::drainAndWrite);
            } else {
                confirmation = SAVE_EXECUTOR.submit(() -> {});
            }
        }
        try {
            confirmation.get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            CraftEditorMod.LOGGER.error("[Shovel JS] saveNow() could not confirm the write completed: {}", e.getMessage());
        }
    }

    private static void load() {
        loaded = true;
        favoritesEpoch++;
        KubeJSExporter.clearAll();
        KubeJSMobExporter.clearAll();
        KubeJSItemAttrExporter.clearAll();
        KubeJSBlockAttrExporter.clearAll();
        TagExporter.clearAll();
        LootTableExporter.clearAll();
        com.kve.common.structure.StructureFrequencyExporter.clearAll();
        FAVORITE_ITEMS.clear();
        FAVORITE_MOBS.clear();
        FAVORITE_RECIPES.clear();
        FAVORITE_BLOCKS.clear();
        FAVORITE_LOOT.clear();
        FAVORITE_TAGS.clear();

        if (!Files.exists(STATE_FILE)) {
            CraftEditorMod.LOGGER.info("[Shovel JS] No state file found, starting fresh.");
            lastMtime = 0;
            return;
        }
        try {
            lastMtime = Files.getLastModifiedTime(STATE_FILE).toMillis();
            String json = Files.readString(STATE_FILE, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            loadRecipes(root);
            loadRemovedRecipes(root);
            loadMobs(root);
            loadItems(root);
            loadBlocks(root);
            loadTags(root);
            loadLootTables(root);
            loadStructureFrequency(root);
            loadFavorites(root);
            markAllApplied();
            CraftEditorMod.LOGGER.info("[Shovel JS] State loaded from {}", STATE_FILE);
        } catch (Exception e) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Failed to load state: {}", e.getMessage());
        }
    }

    /**
     * Lo cargado del disco (o importado) cuenta como aplicado: el estado solo guarda lo aplicado,
     * nunca borradores pendientes, para que un Aplicar de otra pestania no los vuelva permanentes.
     */
    private static void markAllApplied() {
        KubeJSExporter.markAppliedAdded();
        KubeJSExporter.markAppliedRemoved();
        KubeJSMobExporter.markApplied();
        KubeJSItemAttrExporter.markApplied();
        KubeJSBlockAttrExporter.markApplied();
        TagExporter.markApplied();
        LootTableExporter.markApplied();
        com.kve.common.structure.StructureFrequencyExporter.markApplied();
    }

    private static void loadFavorites(JsonObject root) {
        if (!root.has("favorites")) return;
        JsonObject fav = root.getAsJsonObject("favorites");
        if (fav.has("items"))
            for (JsonElement el : fav.getAsJsonArray("items")) FAVORITE_ITEMS.add(el.getAsString());
        if (fav.has("mobs"))
            for (JsonElement el : fav.getAsJsonArray("mobs")) FAVORITE_MOBS.add(el.getAsString());
        if (fav.has("recipes"))
            for (JsonElement el : fav.getAsJsonArray("recipes")) FAVORITE_RECIPES.add(el.getAsString());
        if (fav.has("blocks"))
            for (JsonElement el : fav.getAsJsonArray("blocks")) FAVORITE_BLOCKS.add(el.getAsString());
        if (fav.has("loot"))
            for (JsonElement el : fav.getAsJsonArray("loot")) FAVORITE_LOOT.add(el.getAsString());
        if (fav.has("tags"))
            for (JsonElement el : fav.getAsJsonArray("tags")) FAVORITE_TAGS.add(el.getAsString());
    }

    private static JsonObject saveFavorites() {
        JsonObject fav = new JsonObject();
        JsonArray items = new JsonArray();
        for (String id : FAVORITE_ITEMS) items.add(id);
        JsonArray mobs = new JsonArray();
        for (String id : FAVORITE_MOBS) mobs.add(id);
        JsonArray recipes = new JsonArray();
        for (String id : FAVORITE_RECIPES) recipes.add(id);
        JsonArray blocks = new JsonArray();
        for (String id : FAVORITE_BLOCKS) blocks.add(id);
        JsonArray loot = new JsonArray();
        for (String id : FAVORITE_LOOT) loot.add(id);
        fav.add("items", items);
        fav.add("mobs", mobs);
        fav.add("recipes", recipes);
        fav.add("blocks", blocks);
        fav.add("loot", loot);
        JsonArray tags = new JsonArray();
        for (String id : FAVORITE_TAGS) tags.add(id);
        fav.add("tags", tags);
        return fav;
    }

    /**
     * Single dedicated background thread for all async saves. Using the
     * shared ForkJoinPool (as CompletableFuture.runAsync does by default)
     * meant a burst of save() calls could have two of its Runnables
     * actually running at once - cancel(false) on the previous
     * CompletableFuture only prevents ITS completion callbacks from
     * firing, it does NOT stop a plain Runnable that's already executing,
     * since there's no interruption point in it. Two threads racing to
     * Files.writeString(TEMP_FILE, ...) then Files.move(TEMP_FILE,
     * STATE_FILE) at the same time could corrupt TEMP_FILE (interleaved
     * writes) or let an older snapshot win the move over a newer one.
     * A single-thread executor makes that interleaving impossible: only
     * one save is ever physically writing at a time.
     */
    private static final java.util.concurrent.ExecutorService SAVE_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Shovel JS-AsyncSave");
                t.setDaemon(true);
                return t;
            });
    private static final java.util.concurrent.atomic.AtomicReference<String> latestSnapshot =
            new java.util.concurrent.atomic.AtomicReference<>();
    /** True while the single save thread is in its drain loop, so flushAsync() knows whether it needs to (re)start that loop or can just hand off the new snapshot to the loop already running. */
    private static final java.util.concurrent.atomic.AtomicBoolean writerActive =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private static void flushAsync() {
        final String snapshot;
        try {
            JsonObject root = buildJson();
            snapshot = GSON.toJson(root);
        } catch (Exception e) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Snapshot failed: {}", e.getMessage());
            return;
        }

        checkFileSize(snapshot);
        latestSnapshot.set(snapshot);

        if (writerActive.compareAndSet(false, true)) {
            SAVE_EXECUTOR.execute(CraftEditorState::drainAndWrite);
        }
    }

    private static void drainAndWrite() {
        try {
            String snap;
            while ((snap = latestSnapshot.getAndSet(null)) != null) {
                try {
                    Files.createDirectories(STATE_FILE.getParent());
                    rotateBackup();
                    Files.writeString(TEMP_FILE, snap, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    com.kve.common.util.SafeFiles.moveReplacing(TEMP_FILE, STATE_FILE);
                    synchronized (CraftEditorState.class) {
                        dirty = false;
                        try { lastMtime = Files.getLastModifiedTime(STATE_FILE).toMillis(); }
                        catch (IOException ignored) {}
                    }
                } catch (IOException ex) {
                    CraftEditorMod.LOGGER.error("[Shovel JS] Async save failed: {}", ex.getMessage());
                }
            }
        } finally {
            writerActive.set(false);
            if (latestSnapshot.get() != null && writerActive.compareAndSet(false, true)) {
                SAVE_EXECUTOR.execute(CraftEditorState::drainAndWrite);
            }
        }
    }

    private static void writeToFile() {
        try {
            String json = GSON.toJson(buildJson());
            checkFileSize(json);
            Files.createDirectories(STATE_FILE.getParent());
            rotateBackup();
            Files.writeString(TEMP_FILE, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            com.kve.common.util.SafeFiles.moveReplacing(TEMP_FILE, STATE_FILE);
            try { lastMtime = Files.getLastModifiedTime(STATE_FILE).toMillis(); }
            catch (IOException ignored) {}
        } catch (Exception e) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Sync save failed: {}", e.getMessage());
        }
    }

    private static void checkFileSize(String content) {
        int kb = content.getBytes(StandardCharsets.UTF_8).length / 1024;
        if (kb >= KVEConfig.warnFileSizeKB && !sizeWarned) {
            sizeWarned = true;
            CraftEditorMod.LOGGER.warn(
                    "[Shovel JS] shovel_js_state.json is {}KB (threshold {}KB). Consider removing unused entries.",
                    kb, KVEConfig.warnFileSizeKB);
        } else if (kb < KVEConfig.warnFileSizeKB) {
            sizeWarned = false;
        }
    }

    private static void rotateBackup() {
        int count = KVEConfig.backupCount;
        if (count <= 0 || !Files.exists(STATE_FILE)) return;
        try {
            Files.createDirectories(BACKUP_DIR);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path backup = BACKUP_DIR.resolve("shovel_js_state_" + stamp + ".json");
            Files.copy(STATE_FILE, backup, StandardCopyOption.REPLACE_EXISTING);

            List<Path> backups = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(BACKUP_DIR, "shovel_js_state_*.json")) {
                for (Path p : ds) backups.add(p);
            }
            backups.sort(Comparator.comparing(p -> p.getFileName().toString()));
            while (backups.size() > count) {
                Files.deleteIfExists(backups.remove(0));
            }
        } catch (IOException e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Backup rotation failed: {}", e.getMessage());
        }
    }

    public static List<String> listBackups() {
        List<String> names = new ArrayList<>();
        try {
            if (!Files.isDirectory(BACKUP_DIR)) return names;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(BACKUP_DIR, "shovel_js_state_*.json")) {
                for (Path p : ds) names.add(p.getFileName().toString());
            }
            names.sort(Collections.reverseOrder());
        } catch (IOException ignored) {}
        return names;
    }

    public static boolean restoreBackup(String fileName) {
        Path backup = BACKUP_DIR.resolve(fileName);
        if (!Files.exists(backup)) return false;
        try {
            Files.copy(backup, STATE_FILE, StandardCopyOption.REPLACE_EXISTING);
            forceReload();
            return true;
        } catch (IOException e) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Restore failed: {}", e.getMessage());
            return false;
        }
    }

    public static void ensureIoDir() {
        try {
            Files.createDirectories(EXPORT_FILE.getParent());
        } catch (IOException e) {
            CraftEditorMod.LOGGER.error("[Shovel JS] No se pudo crear la carpeta de import/export: {}", e.getMessage());
        }
    }

    public static boolean exportAll() {
        try {
            ensureIoDir();
            saveNow();
            if (!Files.exists(STATE_FILE)) writeToFile();
            Files.copy(STATE_FILE, EXPORT_FILE, StandardCopyOption.REPLACE_EXISTING);
            CraftEditorMod.LOGGER.info("[Shovel JS] Exported to {}", EXPORT_FILE.toAbsolutePath());
            return true;
        } catch (IOException e) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Export failed: {}", e.getMessage());
            return false;
        }
    }

    public static Path getExportPath() { return EXPORT_FILE.toAbsolutePath(); }
    public static Path getImportPath() {
        Path found = resolveImportFile();
        return (found != null ? found : IMPORT_FILE).toAbsolutePath();
    }
    public static boolean importFileExists() { ensureIoDir(); return resolveImportFile() != null; }

    public static boolean importAll() {
        if (!requireEdit("import")) return false;
        ensureIoDir();
        Path file = resolveImportFile();
        if (file == null) return false;
        return importFrom(file);
    }

    public static boolean importFrom(Path file) {
        if (!requireEdit("import")) return false;
        if (file == null || !Files.exists(file)) return false;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            loadRecipes(root);
            loadRemovedRecipes(root);
            loadMobs(root);
            loadItems(root);
            loadBlocks(root);
            loadTags(root);
            loadLootTables(root);
            loadStructureFrequency(root);
            markAllApplied();
            save();
            CraftEditorMod.LOGGER.info("[Shovel JS] Imported from {}", file.toAbsolutePath());
            return true;
        } catch (Exception e) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Import failed: {}", e.getMessage());
            return false;
        }
    }

    /** Selectable domains for the unified export/scan screen (KVE-craft-import-export-unify). ALL keeps the historic single-blob behaviour ({@link #exportAll()}). */
    public enum ExportDomain { ALL, RECIPES, MOBS, ITEMS, BLOCKS, TAGS, LOOT }

    /**
     * Writes ONLY the selected domain's slice of state to {@link #EXPORT_FILE},
     * instead of the whole state blob - answers "que tipo de script exportar
     * (mob, craft, etc)" from the unified interface. Reuses the same
     * saveXxx() builders {@link #buildJson()} already calls, so the produced
     * JSON round-trips through {@link #importFrom()} unchanged (every loader
     * skips keys it doesn't find).
     */
    public static boolean exportDomain(ExportDomain domain) {
        if (domain == null || domain == ExportDomain.ALL) return exportAll();
        try {
            ensureIoDir();
            saveNow();
            JsonObject root = new JsonObject();
            switch (domain) {
                case RECIPES -> {
                    root.add("recipes", saveRecipes());
                    root.add("removedRecipes", saveRemovedRecipes());
                }
                case MOBS   -> root.add("mobs", saveMobs());
                case ITEMS  -> root.add("items", saveItems());
                case BLOCKS -> root.add("blocks", saveBlocks());
                case TAGS   -> root.add("tags", saveTags());
                case LOOT   -> root.add("lootTables", saveLootTables());
                default -> { }
            }
            String json = GSON.toJson(root);
            Files.writeString(EXPORT_FILE, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            CraftEditorMod.LOGGER.info("[Shovel JS] Exported domain {} to {}", domain, EXPORT_FILE.toAbsolutePath());
            return true;
        } catch (Exception e) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Domain export failed: {}", e.getMessage());
            return false;
        }
    }

    private static JsonObject buildJson() {
        JsonObject root = new JsonObject();
        root.add("recipes",        saveRecipes());
        root.add("removedRecipes", saveRemovedRecipes());
        root.add("mobs",           saveMobs());
        root.add("items",          saveItems());
        root.add("blocks",         saveBlocks());
        root.add("tags",           saveTags());
        root.add("lootTables",     saveLootTables());
        root.add("structureFrequency", saveStructureFrequency());
        root.add("favorites",      saveFavorites());
        return root;
    }

    private static void loadStructureFrequency(JsonObject root) {
        if (!root.has("structureFrequency")) return;
        for (JsonElement el : root.getAsJsonArray("structureFrequency")) {
            try {
                JsonObject o = el.getAsJsonObject();
                com.kve.common.structure.StructureFrequencyExporter.StructureSetOverride ov =
                        new com.kve.common.structure.StructureFrequencyExporter.StructureSetOverride();
                ov.structureSetId   = str(o, "structureSetId", "");
                if (ov.structureSetId.isEmpty()) continue;
                ov.enabled          = !o.has("enabled") || bool2(o, "enabled", true);
                ov.spacingOverride  = o.has("spacingOverride") ? o.get("spacingOverride").getAsInt() : -1;
                ov.separationOverride = o.has("separationOverride") ? o.get("separationOverride").getAsInt() : -1;
                if (o.has("bannedStructures")) {
                    for (JsonElement bel : o.getAsJsonArray("bannedStructures")) ov.bannedStructures.add(bel.getAsString());
                }
                com.kve.common.structure.StructureFrequencyExporter.loadEntry(ov);
            } catch (Exception ex) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped structure frequency override: {}", ex.getMessage());
            }
        }
    }

    private static JsonArray saveStructureFrequency() {
        JsonArray arr = new JsonArray();
        for (com.kve.common.structure.StructureFrequencyExporter.StructureSetOverride ov
                : com.kve.common.structure.StructureFrequencyExporter.getAppliedEntries()) {
            JsonObject o = new JsonObject();
            o.addProperty("structureSetId",     ov.structureSetId);
            o.addProperty("enabled",            ov.enabled);
            o.addProperty("spacingOverride",    ov.spacingOverride);
            o.addProperty("separationOverride", ov.separationOverride);
            JsonArray banned = new JsonArray();
            for (String s : ov.bannedStructures) banned.add(s);
            o.add("bannedStructures", banned);
            arr.add(o);
        }
        return arr;
    }

    private static void loadRecipes(JsonObject root) {
        if (!root.has("recipes")) return;
        for (JsonElement el : root.getAsJsonArray("recipes")) {
            try {
                KubeJSExporter.loadRecipe(recipeFromJson(el.getAsJsonObject()));
            } catch (Exception e) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped recipe: {}", e.getMessage());
            }
        }
    }

    /**
     * Parses a single recipe entry from JSON. Extracted from the old inline
     * loop body so it can also be reused by {@link RecipeSubsetIO} for
     * partial (per-project) import/export without duplicating field names.
     */
    public static RecipeData recipeFromJson(JsonObject o) {
        RecipeData r = new RecipeData();
        r.recipeId   = str(o, "recipeId", "unknown");
        r.enabled    = !o.has("enabled") || bool2(o, "enabled", true);
        r.category   = str(o, "category", "");
        r.notes      = str(o, "notes", "");
        r.type       = enumVal(o, "type", RecipeData.RecipeType.class, RecipeData.RecipeType.VANILLA_SHAPED);
        r.ecTier     = enumVal(o, "ecTier", RecipeData.ECTableTier.class, RecipeData.ECTableTier.BASIC);
        r.gridWidth  = oi(o, "gridWidth");
        r.gridHeight = oi(o, "gridHeight");
        r.output     = o.has("output") ? stackFromJson(o.get("output")) : ItemStack.EMPTY;
        r.ecPowerRequired  = o.has("ecPower") ? o.get("ecPower").getAsLong()  : 1_000_000L;
        r.ecCraftingTime   = o.has("ecTime")  ? o.get("ecTime").getAsInt()    : 200;
        r.combinationRfCost= o.has("rfCost")  ? o.get("rfCost").getAsLong()   : 50_000L;
        r.combinationRfRate= o.has("rfRate")  ? o.get("rfRate").getAsLong()   : 1_000L;
        r.customTypeId     = str(o, "customTypeId", "");
        r.customExtraJson  = str(o, "customExtraJson", "");

        if (o.has("ingredients"))
            r.ingredients = stackListFromJson(o.getAsJsonArray("ingredients"));
        if (o.has("shapeless"))
            r.shapelessIngredients = stackListFromJson(o.getAsJsonArray("shapeless"));
        if (o.has("combCenter"))
            r.combinationCenter = stackFromJson(o.get("combCenter"));
        if (o.has("combPedestals"))
            r.combinationPedestals = stackListFromJson(o.getAsJsonArray("combPedestals"));
        if (o.has("machineInputs"))
            r.machineInputs = machineSlotListFromJson(o.getAsJsonArray("machineInputs"));
        if (o.has("machineOutputs"))
            r.machineOutputs = machineSlotListFromJson(o.getAsJsonArray("machineOutputs"));
        return r;
    }

    private static JsonArray saveRecipes() {
        JsonArray arr = new JsonArray();
        for (RecipeData r : KubeJSExporter.getAppliedAddedRecipes()) arr.add(recipeToJson(r));
        return arr;
    }

    /** Serializes a single recipe to JSON. See {@link #recipeFromJson(JsonObject)}. */
    public static JsonObject recipeToJson(RecipeData r) {
        JsonObject o = new JsonObject();
        o.addProperty("recipeId",   r.recipeId);
        o.addProperty("enabled",    r.enabled);
        o.addProperty("category",   r.category);
        if (!r.notes.isEmpty()) o.addProperty("notes", r.notes);
        o.addProperty("type",       r.type.name());
        o.addProperty("ecTier",     r.ecTier.name());
        o.addProperty("gridWidth",  r.gridWidth);
        o.addProperty("gridHeight", r.gridHeight);
        o.add("output",     stackToJson(r.output));
        o.addProperty("ecPower",    r.ecPowerRequired);
        o.addProperty("ecTime",     r.ecCraftingTime);
        o.addProperty("rfCost",     r.combinationRfCost);
        o.addProperty("rfRate",     r.combinationRfRate);
        o.addProperty("customTypeId",    r.customTypeId);
        o.addProperty("customExtraJson", r.customExtraJson);
        o.add("ingredients",  toStackArray(r.ingredients));
        o.add("shapeless",    toStackArray(r.shapelessIngredients));
        o.add("combCenter", stackToJson(r.combinationCenter));
        o.add("combPedestals", toStackArray(r.combinationPedestals));
        o.add("machineInputs",  toMachineSlotArray(r.machineInputs));
        o.add("machineOutputs", toMachineSlotArray(r.machineOutputs));
        return o;
    }

    private static void loadRemovedRecipes(JsonObject root) {
        if (!root.has("removedRecipes")) return;
        for (JsonElement el : root.getAsJsonArray("removedRecipes"))
            KubeJSExporter.loadRemovedRecipe(el.getAsString());
    }

    private static JsonArray saveRemovedRecipes() {
        JsonArray arr = new JsonArray();
        for (String id : KubeJSExporter.getAppliedRemovedRecipes()) arr.add(id);
        return arr;
    }

    private static void loadMobs(JsonObject root) {
        if (!root.has("mobs")) return;
        for (JsonElement el : root.getAsJsonArray("mobs")) {
            try {
                JsonObject o = el.getAsJsonObject();
                KubeJSMobExporter.MobEntry e = new KubeJSMobExporter.MobEntry();
                e.entityId        = str(o, "entityId", "");
                if (e.entityId.isEmpty()) continue;
                e.enabled          = !o.has("enabled") || bool2(o, "enabled", true);
                e.category         = str(o, "category", "");
                e.health          = odNaN(o, "health");
                e.armor           = odNaN(o, "armor");
                e.armorToughness  = odNaN(o, "armorToughness");
                e.attackDamage    = odNaN(o, "attackDamage");
                e.attackSpeed     = odNaN(o, "attackSpeed");
                e.movementSpeed   = odNaN(o, "movementSpeed");
                e.knockbackResist = odNaN(o, "knockbackResist");
                e.followRange     = odNaN(o, "followRange");
                e.xpDrop          = o.has("xpDrop") ? o.get("xpDrop").getAsInt() : -1;
                e.regenPerSecond  = odNaN(o, "regenPerSecond");
                e.damageTakenMult = odNaN(o, "damageTakenMult");
                e.damageDealtMult = odNaN(o, "damageDealtMult");
                e.customName        = str(o, "customName", "");
                e.customNameSet     = bool(o, "customNameSet");
                e.customNameVisible = bool(o, "customNameVisible");
                e.scriptOverride    = str(o, "scriptOverride", "");

                if (o.has("equipment")) {
                    for (JsonElement qel : o.getAsJsonArray("equipment")) {
                        try {
                            JsonObject qo = qel.getAsJsonObject();
                            String slot = str(qo, "slot", "");
                            if (slot.isEmpty()) continue;
                            e.equipment.add(new KubeJSMobExporter.EquipmentItem(
                                    slot, str(qo, "itemId", ""),
                                    qo.has("dropChance") ? qo.get("dropChance").getAsDouble() : 0.0,
                                    str(qo, "nbt", ""),
                                    qo.has("spawnChance") ? qo.get("spawnChance").getAsDouble() : 1.0));
                        } catch (Exception ex) {
                            CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped mob equipment: {}", ex.getMessage());
                        }
                    }
                }

                if (o.has("equipmentBuilds")) {
                    for (JsonElement bel : o.getAsJsonArray("equipmentBuilds")) {
                        try {
                            JsonObject bo = bel.getAsJsonObject();
                            KubeJSMobExporter.EquipmentBuild build = new KubeJSMobExporter.EquipmentBuild();
                            build.name    = str(bo, "name", "");
                            build.weight  = bo.has("weight") ? bo.get("weight").getAsDouble() : 1.0;
                            build.enabled = !bo.has("enabled") || bool2(bo, "enabled", true);
                            if (bo.has("equipment")) {
                                for (JsonElement qel : bo.getAsJsonArray("equipment")) {
                                    try {
                                        JsonObject qo = qel.getAsJsonObject();
                                        String slot = str(qo, "slot", "");
                                        if (slot.isEmpty()) continue;
                                        build.equipment.add(new KubeJSMobExporter.EquipmentItem(
                                                slot, str(qo, "itemId", ""),
                                                qo.has("dropChance") ? qo.get("dropChance").getAsDouble() : 0.0,
                                                str(qo, "nbt", ""),
                                                qo.has("spawnChance") ? qo.get("spawnChance").getAsDouble() : 1.0));
                                    } catch (Exception ex) {
                                        CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped mob equipment build item: {}", ex.getMessage());
                                    }
                                }
                            }
                            e.equipmentBuilds.add(build);
                        } catch (Exception ex) {
                            CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped mob equipment build: {}", ex.getMessage());
                        }
                    }
                }

                if (o.has("attributeMods")) {
                    for (JsonElement mel : o.getAsJsonArray("attributeMods")) {
                        try {
                            JsonObject mo = mel.getAsJsonObject();
                            String attrId = str(mo, "attrId", "");
                            if (attrId.isEmpty()) continue;
                            e.attributeMods.add(new KubeJSMobExporter.AttrMod(
                                    attrId,
                                    od(mo, "value"),
                                    str(mo, "dimension", ""),
                                    mo.has("probability") ? mo.get("probability").getAsDouble() : 1.0));
                        } catch (Exception ex) {
                            CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped mob attr: {}", ex.getMessage());
                        }
                    }
                }
                if (o.has("attackEffects")) {
                    for (JsonElement fxel : o.getAsJsonArray("attackEffects")) {
                        try {
                            JsonObject fxo = fxel.getAsJsonObject();
                            String effectId = str(fxo, "effectId", "");
                            if (effectId.isEmpty()) continue;
                            int durationTicks = fxo.has("durationTicks") ? fxo.get("durationTicks").getAsInt() : 100;
                            int amplifier     = fxo.has("amplifier")     ? fxo.get("amplifier").getAsInt()     : 0;
                            float chance      = fxo.has("chance")        ? fxo.get("chance").getAsFloat()      : 1.0f;
                            e.attackEffects.add(new KubeJSItemAttrExporter.FoodEffect(effectId, durationTicks, amplifier, chance));
                        } catch (Exception ex) {
                            CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped mob attack effect: {}", ex.getMessage());
                        }
                    }
                }
                if (o.has("allies")) {
                    for (JsonElement alel : o.getAsJsonArray("allies")) {
                        try {
                            String allyId = alel.getAsString();
                            if (allyId.isEmpty()) continue;
                            e.allies.add(allyId);
                        } catch (Exception ex) {
                            CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped mob ally: {}", ex.getMessage());
                        }
                    }
                }
                if (o.has("behaviorFlags")) {
                    for (JsonElement bfel : o.getAsJsonArray("behaviorFlags")) {
                        try {
                            String flag = bfel.getAsString();
                            if (flag.isEmpty()) continue;
                            e.behaviorFlags.add(flag);
                        } catch (Exception ex) {
                            CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped mob behavior flag: {}", ex.getMessage());
                        }
                    }
                }

                KubeJSMobExporter.loadEntry(e);
            } catch (Exception ex) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped mob: {}", ex.getMessage());
            }
        }
    }

    private static void loadTags(JsonObject root) {
        if (!root.has("tags")) return;
        for (JsonElement el : root.getAsJsonArray("tags")) {
            try {
                JsonObject o = el.getAsJsonObject();
                TagExporter.TagEntry e = new TagExporter.TagEntry();
                String typeStr = str(o, "regType", "ITEM");
                try { e.regType = TagExporter.RegistryType.valueOf(typeStr); }
                catch (Exception ex) { e.regType = TagExporter.RegistryType.ITEM; }
                e.tagId = str(o, "tagId", "");
                if (e.tagId.isEmpty()) continue;
                e.enabled  = !o.has("enabled") || bool2(o, "enabled", true);
                e.replace  = bool2(o, "replace", false);
                e.category = str(o, "category", "");
                if (o.has("added"))
                    for (JsonElement s : o.getAsJsonArray("added")) e.added.add(s.getAsString());
                if (o.has("removed"))
                    for (JsonElement s : o.getAsJsonArray("removed")) e.removed.add(s.getAsString());
                if (!e.replace && e.added.removeIf(e.removed::contains)) {
                    CraftEditorMod.LOGGER.warn(
                            "[Shovel JS] Tag '{}': id(s) en added Y removed a la vez (estado inconsistente) - resuelto a favor de removed", e.tagId);
                }
                TagExporter.loadEntry(e);
            } catch (Exception ex) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped tag: {}", ex.getMessage());
            }
        }
    }

    private static JsonArray saveTags() {
        JsonArray arr = new JsonArray();
        for (TagExporter.TagEntry e : TagExporter.getAppliedEntries()) {
            JsonObject o = new JsonObject();
            o.addProperty("regType",  e.regType.name());
            o.addProperty("tagId",    e.tagId);
            o.addProperty("enabled",  e.enabled);
            o.addProperty("replace",  e.replace);
            o.addProperty("category", e.category);
            JsonArray added = new JsonArray();
            for (String s : e.added) added.add(s);
            o.add("added", added);
            JsonArray removed = new JsonArray();
            for (String s : e.removed) removed.add(s);
            o.add("removed", removed);
            arr.add(o);
        }
        return arr;
    }

    private static void loadLootTables(JsonObject root) {
        if (!root.has("lootTables")) return;
        for (JsonElement el : root.getAsJsonArray("lootTables")) {
            try {
                JsonObject o = el.getAsJsonObject();
                LootTableExporter.LootTableEntry e = new LootTableExporter.LootTableEntry();
                if (o.has("kind") && o.has("event")) {
                    try { e.kind = LootTableExporter.TargetKind.valueOf(str(o, "kind", "ENTITY")); }
                    catch (Exception ex) { e.kind = LootTableExporter.TargetKind.ENTITY; }
                    try { e.event = LootTableExporter.TriggerEvent.valueOf(str(o, "event", "DEATH")); }
                    catch (Exception ex) { e.event = LootTableExporter.TriggerEvent.DEATH; }
                } else {
                    switch (str(o, "targetType", "ENTITY")) {
                        case "BLOCK" -> { e.kind = LootTableExporter.TargetKind.BLOCK; e.event = LootTableExporter.TriggerEvent.BREAK; }
                        case "BLOCK_INTERACT" -> { e.kind = LootTableExporter.TargetKind.BLOCK; e.event = LootTableExporter.TriggerEvent.INTERACT; }
                        default -> { e.kind = LootTableExporter.TargetKind.ENTITY; e.event = LootTableExporter.TriggerEvent.DEATH; }
                    }
                }
                e.targetId = str(o, "targetId", "");
                if (e.targetId.isEmpty()) continue;
                e.enabled  = !o.has("enabled") || bool2(o, "enabled", true);
                e.category = str(o, "category", "");
                if (o.has("pools")) {
                    for (JsonElement pel : o.getAsJsonArray("pools")) {
                        JsonObject po = pel.getAsJsonObject();
                        LootTableExporter.LootPoolData pool = new LootTableExporter.LootPoolData();
                        pool.rolls = po.has("rolls") ? po.get("rolls").getAsInt() : 1;
                        String condStr = str(po, "condition", "NONE");
                        try { pool.condition = LootTableExporter.Condition.valueOf(condStr); }
                        catch (Exception ex) { pool.condition = LootTableExporter.Condition.NONE; }
                        pool.chance = po.has("chance") ? po.get("chance").getAsDouble() : 0.5;
                        pool.lootingMultiplier = po.has("lootingMultiplier") ? po.get("lootingMultiplier").getAsDouble() : 0.02;
                        if (po.has("extraConditions") && po.get("extraConditions").isJsonArray()) pool.extraConditions = po.getAsJsonArray("extraConditions");
                        if (po.has("entries")) {
                            for (JsonElement eel : po.getAsJsonArray("entries")) {
                                JsonObject eo = eel.getAsJsonObject();
                                LootTableExporter.LootEntryData entry = new LootTableExporter.LootEntryData();
                                entry.itemId   = str(eo, "itemId", "");
                                entry.weight   = eo.has("weight")   ? eo.get("weight").getAsInt()   : 1;
                                entry.minCount = eo.has("minCount") ? eo.get("minCount").getAsInt() : 1;
                                entry.maxCount = eo.has("maxCount") ? eo.get("maxCount").getAsInt() : 1;
                                if (eo.has("conditions") && eo.get("conditions").isJsonArray()) entry.conditions = eo.getAsJsonArray("conditions");
                                if (eo.has("functions")) {
                                    for (JsonElement fel : eo.getAsJsonArray("functions")) {
                                        JsonObject fo = fel.getAsJsonObject();
                                        LootTableExporter.LootFunctionData fn = new LootTableExporter.LootFunctionData();
                                        String fTypeStr = str(fo, "type", "ENCHANT_WITH_LEVELS");
                                        try { fn.type = LootTableExporter.FunctionType.valueOf(fTypeStr); }
                                        catch (Exception ex) { fn.type = LootTableExporter.FunctionType.ENCHANT_WITH_LEVELS; }
                                        fn.levelsMin = fo.has("levelsMin") ? fo.get("levelsMin").getAsInt() : 10;
                                        fn.levelsMax = fo.has("levelsMax") ? fo.get("levelsMax").getAsInt() : 30;
                                        fn.treasure  = bool2(fo, "treasure", false);
                                        fn.nbt       = str(fo, "nbt", "");
                                        entry.functions.add(fn);
                                    }
                                }
                                if (!entry.itemId.isEmpty()) pool.entries.add(entry);
                            }
                        }
                        e.pools.add(pool);
                    }
                }
                LootTableExporter.loadEntry(e);
            } catch (Exception ex) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped loot table: {}", ex.getMessage());
            }
        }
    }

    private static JsonArray saveLootTables() {
        JsonArray arr = new JsonArray();
        for (LootTableExporter.LootTableEntry e : LootTableExporter.getAppliedEntries()) {
            JsonObject o = new JsonObject();
            o.addProperty("kind",       e.kind.name());
            o.addProperty("event",      e.event.name());
            o.addProperty("targetId",   e.targetId);
            o.addProperty("enabled",    e.enabled);
            o.addProperty("category",   e.category);
            JsonArray pools = new JsonArray();
            for (LootTableExporter.LootPoolData pool : e.pools) {
                JsonObject po = new JsonObject();
                po.addProperty("rolls", pool.rolls);
                po.addProperty("condition", pool.condition.name());
                po.addProperty("chance", pool.chance);
                po.addProperty("lootingMultiplier", pool.lootingMultiplier);
                if (pool.extraConditions != null && !pool.extraConditions.isEmpty()) po.add("extraConditions", pool.extraConditions.deepCopy());
                JsonArray entries = new JsonArray();
                for (LootTableExporter.LootEntryData entry : pool.entries) {
                    JsonObject eo = new JsonObject();
                    eo.addProperty("itemId",   entry.itemId);
                    eo.addProperty("weight",   entry.weight);
                    eo.addProperty("minCount", entry.minCount);
                    eo.addProperty("maxCount", entry.maxCount);
                    if (entry.conditions != null && !entry.conditions.isEmpty()) eo.add("conditions", entry.conditions.deepCopy());
                    JsonArray functions = new JsonArray();
                    for (LootTableExporter.LootFunctionData fn : entry.functions) {
                        JsonObject fo = new JsonObject();
                        fo.addProperty("type", fn.type.name());
                        fo.addProperty("levelsMin", fn.levelsMin);
                        fo.addProperty("levelsMax", fn.levelsMax);
                        fo.addProperty("treasure", fn.treasure);
                        fo.addProperty("nbt", fn.nbt);
                        functions.add(fo);
                    }
                    eo.add("functions", functions);
                    entries.add(eo);
                }
                po.add("entries", entries);
                pools.add(po);
            }
            o.add("pools", pools);
            arr.add(o);
        }
        return arr;
    }

    private static JsonArray saveMobs() {
        JsonArray arr = new JsonArray();
        for (KubeJSMobExporter.MobEntry e : KubeJSMobExporter.getAppliedEntries()) {
            JsonObject o = new JsonObject();
            o.addProperty("entityId",          e.entityId);
            o.addProperty("enabled",           e.enabled);
            o.addProperty("category",          e.category);
            addD(o, "health",          e.health);
            addD(o, "armor",           e.armor);
            addD(o, "armorToughness",  e.armorToughness);
            addD(o, "attackDamage",    e.attackDamage);
            addD(o, "attackSpeed",     e.attackSpeed);
            addD(o, "movementSpeed",   e.movementSpeed);
            addD(o, "knockbackResist", e.knockbackResist);
            addD(o, "followRange",     e.followRange);
            if (e.xpDrop >= 0) o.addProperty("xpDrop", e.xpDrop);
            addD(o, "regenPerSecond",  e.regenPerSecond);
            addD(o, "damageTakenMult", e.damageTakenMult);
            addD(o, "damageDealtMult", e.damageDealtMult);
            o.addProperty("customName",        e.customName);
            o.addProperty("customNameSet",     e.customNameSet);
            o.addProperty("customNameVisible", e.customNameVisible);
            o.addProperty("scriptOverride",    e.scriptOverride);
            JsonArray equip = new JsonArray();
            for (KubeJSMobExporter.EquipmentItem eq : e.equipment) {
                JsonObject qo = new JsonObject();
                qo.addProperty("slot",        eq.slot);
                qo.addProperty("itemId",      eq.itemId);
                qo.addProperty("dropChance",  eq.dropChance);
                qo.addProperty("spawnChance", eq.spawnChance);
                qo.addProperty("nbt",         eq.nbt);
                equip.add(qo);
            }
            o.add("equipment", equip);
            JsonArray builds = new JsonArray();
            for (KubeJSMobExporter.EquipmentBuild b : e.equipmentBuilds) {
                JsonObject bo = new JsonObject();
                bo.addProperty("name",    b.name);
                bo.addProperty("weight",  b.weight);
                bo.addProperty("enabled", b.enabled);
                JsonArray bEquip = new JsonArray();
                for (KubeJSMobExporter.EquipmentItem eq : b.equipment) {
                    JsonObject qo = new JsonObject();
                    qo.addProperty("slot",       eq.slot);
                    qo.addProperty("itemId",     eq.itemId);
                    qo.addProperty("dropChance", eq.dropChance);
                    qo.addProperty("spawnChance", eq.spawnChance);
                    qo.addProperty("nbt",        eq.nbt);
                    bEquip.add(qo);
                }
                bo.add("equipment", bEquip);
                builds.add(bo);
            }
            o.add("equipmentBuilds", builds);
            JsonArray mods = new JsonArray();
            for (KubeJSMobExporter.AttrMod m : e.attributeMods) {
                JsonObject mo = new JsonObject();
                mo.addProperty("attrId",      m.attrId);
                mo.addProperty("value",       m.value);
                mo.addProperty("dimension",   m.dimension);
                mo.addProperty("probability", m.probability);
                mods.add(mo);
            }
            o.add("attributeMods", mods);
            JsonArray atkFx = new JsonArray();
            for (KubeJSItemAttrExporter.FoodEffect fx : e.attackEffects) {
                JsonObject fxo = new JsonObject();
                fxo.addProperty("effectId",      fx.effectId);
                fxo.addProperty("durationTicks", fx.durationTicks);
                fxo.addProperty("amplifier",     fx.amplifier);
                fxo.addProperty("chance",        fx.chance);
                atkFx.add(fxo);
            }
            o.add("attackEffects", atkFx);
            JsonArray allies = new JsonArray();
            for (String allyId : e.allies) {
                allies.add(allyId);
            }
            o.add("allies", allies);
            JsonArray behaviorFlags = new JsonArray();
            for (String flag : e.behaviorFlags) {
                behaviorFlags.add(flag);
            }
            o.add("behaviorFlags", behaviorFlags);
            arr.add(o);
        }
        return arr;
    }

    private static void loadItems(JsonObject root) {
        if (!root.has("items")) return;
        for (JsonElement el : root.getAsJsonArray("items")) {
            try {
                JsonObject o = el.getAsJsonObject();
                KubeJSItemAttrExporter.ItemEntry e = new KubeJSItemAttrExporter.ItemEntry();
                e.itemId         = str(o, "itemId", "");
                if (e.itemId.isEmpty()) continue;
                e.enabled        = !o.has("enabled") || bool2(o, "enabled", true);
                e.category       = str(o, "category", "");
                e.maxDamage      = oi(o, "maxDamage");
                e.maxStackSize   = oi(o, "maxStackSize");
                e.burnTime       = oi(o, "burnTime");
                e.attackDamage   = odNaN(o, "attackDamage");
                e.attackSpeed    = odNaN(o, "attackSpeed");
                e.armor          = odNaN(o, "armor");
                e.armorToughness = odNaN(o, "armorToughness");
                e.movementSpeed  = odNaN(o, "movementSpeed");
                e.attackDamageOp   = opOf(str(o, "attackDamageOp",   "ADDITION"));
                e.attackSpeedOp    = opOf(str(o, "attackSpeedOp",    "ADDITION"));
                e.armorOp          = opOf(str(o, "armorOp",          "ADDITION"));
                e.armorToughnessOp = opOf(str(o, "armorToughnessOp", "ADDITION"));
                e.movementSpeedOp  = opOf(str(o, "movementSpeedOp",  "ADDITION"));
                e.fireResistant    = bool(o, "fireResistant");
                e.fireResistantSet = o.has("fireResistant");
                e.rarity           = str(o, "rarity", "");
                e.hideFromCreativeTab   = bool(o, "hideFromCreativeTab");
                e.hideFromRecipeViewers = bool(o, "hideFromRecipeViewers");
                e.hideFromLoot          = bool(o, "hideFromLoot");

                if (o.has("attributes")) {
                    for (JsonElement ael : o.getAsJsonArray("attributes")) {
                        try {
                            JsonObject ao = ael.getAsJsonObject();
                            String attrId = str(ao, "id", "");
                            if (attrId.isEmpty()) continue;
                            double val = od(ao, "value");
                            KubeJSItemAttrExporter.Op op = ao.has("op")
                                    ? KubeJSItemAttrExporter.Op.fromJs(ao.get("op").getAsString())
                                    : KubeJSItemAttrExporter.Op.ADDITION;
                            e.setAttribute(attrId, val, op);
                        } catch (Exception ex) {
                            CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped item attr: {}", ex.getMessage());
                        }
                    }
                }

                if (o.has("removedAttributes") && o.get("removedAttributes").isJsonArray()) {
                    for (JsonElement rel : o.getAsJsonArray("removedAttributes")) {
                        try {
                            String rid = rel.getAsString();
                            if (rid != null && !rid.isEmpty()) e.removedAttributes.add(rid);
                        } catch (Exception ex) {
                            CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped removed item attr: {}", ex.getMessage());
                        }
                    }
                }
                if (o.has("curioAttributes") && o.get("curioAttributes").isJsonArray()) {
                    for (JsonElement cel : o.getAsJsonArray("curioAttributes")) {
                        try {
                            JsonObject co = cel.getAsJsonObject();
                            String attrId = str(co, "id", "");
                            if (attrId.isEmpty()) continue;
                            KubeJSItemAttrExporter.Op op = co.has("op")
                                    ? KubeJSItemAttrExporter.Op.fromJs(co.get("op").getAsString())
                                    : KubeJSItemAttrExporter.Op.ADDITION;
                            e.curioAttributes.add(new KubeJSItemAttrExporter.SlotAttrMod(attrId, od(co, "value"), op, str(co, "slot", "")));
                        } catch (Exception ex) {
                            CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped curio attr: {}", ex.getMessage());
                        }
                    }
                }
                if (o.has("curioRemoved") && o.get("curioRemoved").isJsonArray()) {
                    for (JsonElement rel : o.getAsJsonArray("curioRemoved")) {
                        try {
                            String key = rel.getAsString();
                            if (key != null && key.contains("|")) e.curioRemoved.add(key);
                        } catch (Exception ex) {
                            CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped removed curio attr: {}", ex.getMessage());
                        }
                    }
                }

                if (o.has("addModeAttributes") && o.get("addModeAttributes").isJsonArray()) {
                    for (JsonElement ael : o.getAsJsonArray("addModeAttributes")) {
                        try {
                            String aid = ael.getAsString();
                            if (aid != null && !aid.isEmpty()) e.addModeAttributes.add(aid);
                        } catch (Exception ex) {
                            CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped add-mode item attr: {}", ex.getMessage());
                        }
                    }
                }

                if (o.has("food") && o.get("food").isJsonObject()) {
                    try {
                        JsonObject fo = o.getAsJsonObject("food");
                        KubeJSItemAttrExporter.FoodProps fp = new KubeJSItemAttrExporter.FoodProps();
                        fp.nutrition     = fo.has("nutrition")     ? fo.get("nutrition").getAsInt()       : fp.nutrition;
                        fp.saturationMod = fo.has("saturationMod") ? fo.get("saturationMod").getAsFloat() : fp.saturationMod;
                        fp.alwaysEdible  = bool(fo, "alwaysEdible");
                        fp.fastFood      = bool(fo, "fastFood");
                        fp.meat          = bool(fo, "meat");
                        if (fo.has("effects")) {
                            for (JsonElement fxel : fo.getAsJsonArray("effects")) {
                                try {
                                    JsonObject fxo = fxel.getAsJsonObject();
                                    String effectId = str(fxo, "effectId", "");
                                    if (effectId.isEmpty()) continue;
                                    int durationTicks = fxo.has("durationTicks") ? fxo.get("durationTicks").getAsInt() : 100;
                                    int amplifier     = fxo.has("amplifier")     ? fxo.get("amplifier").getAsInt()     : 0;
                                    float chance      = fxo.has("chance")        ? fxo.get("chance").getAsFloat()      : 1.0f;
                                    fp.effects.add(new KubeJSItemAttrExporter.FoodEffect(effectId, durationTicks, amplifier, chance));
                                } catch (Exception ex) {
                                    CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped food effect: {}", ex.getMessage());
                                }
                            }
                        }
                        e.food = fp;
                    } catch (Exception ex) {
                        CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped item food properties: {}", ex.getMessage());
                    }
                }

                if (o.has("tooltip")) {
                    for (JsonElement tel : o.getAsJsonArray("tooltip")) {
                        try { e.tooltip.add(tel.getAsString()); }
                        catch (Exception ex) { CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped tooltip line: {}", ex.getMessage()); }
                    }
                }

                if (o.has("combat") && o.get("combat").isJsonObject()) {
                    try {
                        JsonObject co = o.getAsJsonObject("combat");
                        KubeJSItemAttrExporter.CombatProps cp = new KubeJSItemAttrExporter.CombatProps();
                        cp.lifestealPct     = co.has("lifestealPct")     ? co.get("lifestealPct").getAsDouble()  : 0;
                        cp.igniteSeconds    = co.has("igniteSeconds")    ? co.get("igniteSeconds").getAsInt()    : 0;
                        cp.useCooldownTicks = co.has("useCooldownTicks") ? co.get("useCooldownTicks").getAsInt() : 0;
                        readEffects(co, "hitEffects", cp.hitEffects);
                        readEffects(co, "passiveEffects", cp.passiveEffects);
                        if (!cp.isEmpty()) e.combat = cp;
                    } catch (Exception ex) {
                        CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped item combat properties: {}", ex.getMessage());
                    }
                }

                KubeJSItemAttrExporter.loadEntry(e);
            } catch (Exception ex) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped item: {}", ex.getMessage());
            }
        }
    }

    private static JsonArray saveItems() {
        JsonArray arr = new JsonArray();
        for (KubeJSItemAttrExporter.ItemEntry e : KubeJSItemAttrExporter.getAppliedEntries()) {
            JsonObject o = new JsonObject();
            o.addProperty("itemId",         e.itemId);
            o.addProperty("enabled",        e.enabled);
            o.addProperty("category",       e.category);
            o.addProperty("maxDamage",      e.maxDamage);
            o.addProperty("maxStackSize",   e.maxStackSize);
            o.addProperty("burnTime",       e.burnTime);
            addD(o, "attackDamage",   e.attackDamage);
            addD(o, "attackSpeed",    e.attackSpeed);
            addD(o, "armor",          e.armor);
            addD(o, "armorToughness", e.armorToughness);
            addD(o, "movementSpeed",  e.movementSpeed);
            if (!Double.isNaN(e.attackDamage))   o.addProperty("attackDamageOp",   e.attackDamageOp.name());
            if (!Double.isNaN(e.attackSpeed))    o.addProperty("attackSpeedOp",    e.attackSpeedOp.name());
            if (!Double.isNaN(e.armor))          o.addProperty("armorOp",          e.armorOp.name());
            if (!Double.isNaN(e.armorToughness)) o.addProperty("armorToughnessOp", e.armorToughnessOp.name());
            if (!Double.isNaN(e.movementSpeed))  o.addProperty("movementSpeedOp",  e.movementSpeedOp.name());
            if (e.fireResistantSet) o.addProperty("fireResistant", e.fireResistant);
            o.addProperty("rarity",         e.rarity);
            if (e.hideFromCreativeTab)   o.addProperty("hideFromCreativeTab", true);
            if (e.hideFromRecipeViewers) o.addProperty("hideFromRecipeViewers", true);
            if (e.hideFromLoot)          o.addProperty("hideFromLoot", true);
            JsonArray attrs = new JsonArray();
            for (KubeJSItemAttrExporter.AttrMod m : e.attributes.values()) {
                JsonObject ao = new JsonObject();
                ao.addProperty("id",    m.attrId);
                ao.addProperty("value", m.value);
                ao.addProperty("op",    m.op.js);
                attrs.add(ao);
            }
            o.add("attributes", attrs);
            // Solo si hay algo que quitar - el JSON de items que nunca
            // borraron un atributo por defecto queda identico al de antes.
            if (!e.removedAttributes.isEmpty()) {
                JsonArray removed = new JsonArray();
                for (String rid : e.removedAttributes) removed.add(rid);
                o.add("removedAttributes", removed);
            }
            if (!e.curioAttributes.isEmpty()) {
                JsonArray curio = new JsonArray();
                for (KubeJSItemAttrExporter.SlotAttrMod m : e.curioAttributes) {
                    JsonObject co = new JsonObject();
                    co.addProperty("id",    m.attrId);
                    co.addProperty("value", m.value);
                    co.addProperty("op",    m.op.js);
                    co.addProperty("slot",  m.slot);
                    curio.add(co);
                }
                o.add("curioAttributes", curio);
            }
            if (!e.curioRemoved.isEmpty()) {
                JsonArray removed = new JsonArray();
                for (String key : e.curioRemoved) removed.add(key);
                o.add("curioRemoved", removed);
            }
            if (!e.addModeAttributes.isEmpty()) {
                JsonArray addMode = new JsonArray();
                for (String aid : e.addModeAttributes) addMode.add(aid);
                o.add("addModeAttributes", addMode);
            }
            if (e.food != null) {
                JsonObject fo = new JsonObject();
                fo.addProperty("nutrition",     e.food.nutrition);
                fo.addProperty("saturationMod", e.food.saturationMod);
                fo.addProperty("alwaysEdible",  e.food.alwaysEdible);
                fo.addProperty("fastFood",      e.food.fastFood);
                fo.addProperty("meat",          e.food.meat);
                JsonArray fx = new JsonArray();
                for (KubeJSItemAttrExporter.FoodEffect fe : e.food.effects) {
                    JsonObject feo = new JsonObject();
                    feo.addProperty("effectId",      fe.effectId);
                    feo.addProperty("durationTicks", fe.durationTicks);
                    feo.addProperty("amplifier",     fe.amplifier);
                    feo.addProperty("chance",        fe.chance);
                    fx.add(feo);
                }
                fo.add("effects", fx);
                o.add("food", fo);
            }
            if (!e.tooltip.isEmpty()) {
                JsonArray tt = new JsonArray();
                for (String line : e.tooltip) tt.add(line);
                o.add("tooltip", tt);
            }
            if (e.combat != null && !e.combat.isEmpty()) {
                JsonObject co = new JsonObject();
                co.addProperty("lifestealPct",     e.combat.lifestealPct);
                co.addProperty("igniteSeconds",    e.combat.igniteSeconds);
                co.addProperty("useCooldownTicks", e.combat.useCooldownTicks);
                co.add("hitEffects",     writeEffects(e.combat.hitEffects));
                co.add("passiveEffects", writeEffects(e.combat.passiveEffects));
                o.add("combat", co);
            }
            arr.add(o);
        }
        return arr;
    }

    private static void loadBlocks(JsonObject root) {
        if (!root.has("blocks")) return;
        for (JsonElement el : root.getAsJsonArray("blocks")) {
            try {
                JsonObject o = el.getAsJsonObject();
                KubeJSBlockAttrExporter.BlockEntry e = new KubeJSBlockAttrExporter.BlockEntry();
                e.blockId = str(o, "blockId", "");
                if (e.blockId.isEmpty()) continue;
                e.enabled             = !o.has("enabled") || bool2(o, "enabled", true);
                e.category             = str(o, "category", "");
                e.destroySpeed         = od(o, "destroySpeed");
                e.explosionResistance  = od(o, "explosionResistance");
                e.lightEmission        = oi(o, "lightEmission");
                e.friction              = od(o, "friction");
                e.speedFactor = o.has("speedFactor") ? od(o, "speedFactor") : -1;
                e.jumpFactor  = o.has("jumpFactor") ? od(o, "jumpFactor") : -1;
                e.soundType   = str(o, "soundType", "");
                e.hasCollision    = o.has("hasCollision") ? o.get("hasCollision").getAsBoolean() : null;
                e.randomlyTicking = o.has("randomlyTicking") ? o.get("randomlyTicking").getAsBoolean() : null;
                e.requiresTool    = bool(o, "requiresTool");
                e.requiresToolSet = o.has("requiresTool");
                if (o.has("harvestTools")) {
                    for (JsonElement hel : o.getAsJsonArray("harvestTools")) {
                        e.harvestTools.add(hel.getAsString());
                    }
                }
                if (o.has("harvestToolItems")) {
                    for (JsonElement hel : o.getAsJsonArray("harvestToolItems")) {
                        e.harvestToolItems.add(hel.getAsString());
                    }
                }
                e.harvestLevel = oi(o, "harvestLevel");
                e.requireExactHarvestTool = bool(o, "requireExactHarvestTool");
                if (o.has("tooltip")) {
                    for (JsonElement tel : o.getAsJsonArray("tooltip")) {
                        try { e.tooltip.add(tel.getAsString()); }
                        catch (Exception ex) { CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped block tooltip line: {}", ex.getMessage()); }
                    }
                }
                KubeJSBlockAttrExporter.loadEntry(e);
            } catch (Exception ex) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped block: {}", ex.getMessage());
            }
        }
    }

    private static JsonArray saveBlocks() {
        JsonArray arr = new JsonArray();
        for (KubeJSBlockAttrExporter.BlockEntry e : KubeJSBlockAttrExporter.getAppliedEntries()) {
            JsonObject o = new JsonObject();
            o.addProperty("blockId",             e.blockId);
            o.addProperty("enabled",             e.enabled);
            o.addProperty("category",            e.category);
            o.addProperty("destroySpeed",        e.destroySpeed);
            o.addProperty("explosionResistance", e.explosionResistance);
            o.addProperty("lightEmission",       e.lightEmission);
            o.addProperty("friction",            e.friction);
            if (e.speedFactor >= 0) o.addProperty("speedFactor", e.speedFactor);
            if (e.jumpFactor >= 0) o.addProperty("jumpFactor", e.jumpFactor);
            if (e.soundType != null && !e.soundType.isBlank()) o.addProperty("soundType", e.soundType);
            if (e.hasCollision != null) o.addProperty("hasCollision", e.hasCollision);
            if (e.randomlyTicking != null) o.addProperty("randomlyTicking", e.randomlyTicking);
            if (e.requiresToolSet) o.addProperty("requiresTool", e.requiresTool);
            if (e.harvestTools != null && !e.harvestTools.isEmpty()) {
                JsonArray ht = new JsonArray();
                for (String t : e.harvestTools) ht.add(t);
                o.add("harvestTools", ht);
            }
            if (e.harvestToolItems != null && !e.harvestToolItems.isEmpty()) {
                JsonArray hti = new JsonArray();
                for (String t : e.harvestToolItems) hti.add(t);
                o.add("harvestToolItems", hti);
            }
            if (e.harvestLevel >= 0) o.addProperty("harvestLevel", e.harvestLevel);
            if (e.requireExactHarvestTool) o.addProperty("requireExactHarvestTool", true);
            if (e.tooltip != null && !e.tooltip.isEmpty()) {
                JsonArray tt = new JsonArray();
                for (String line : e.tooltip) tt.add(line);
                o.add("tooltip", tt);
            }
            arr.add(o);
        }
        return arr;
    }

    private static double od(JsonObject o, String k) {
        try { return o.has(k) ? o.get(k).getAsDouble() : -1; } catch (Exception e) { return -1; }
    }

    private static double odNaN(JsonObject o, String k) {
        try { return o.has(k) ? o.get(k).getAsDouble() : Double.NaN; } catch (Exception e) { return Double.NaN; }
    }

    // Companero de escritura de odNaN(): Gson tira IllegalArgumentException
    // al toJson() cualquier double NaN/Infinity - o.addProperty(k, NaN)
    // rompia el guardado COMPLETO en cuanto un solo item tuviera un stat
    // core sin cambiar (el caso normal). Omitir la clave por completo
    // cuando esta "sin cambio" evita el crash Y es lo que odNaN() ya
    // espera de vuelta (o.has(k)==false -> NaN) - round-trip real.
    private static void addD(JsonObject o, String k, double v) {
        if (!Double.isNaN(v)) o.addProperty(k, v);
    }

    private static KubeJSItemAttrExporter.Op opOf(String name) {
        try { return KubeJSItemAttrExporter.Op.valueOf(name); } catch (Exception ex) { return KubeJSItemAttrExporter.Op.ADDITION; }
    }

    private static int oi(JsonObject o, String k) {
        try { return o.has(k) ? o.get(k).getAsInt() : -1; } catch (Exception e) { return -1; }
    }

    private static void readEffects(JsonObject o, String key, List<KubeJSItemAttrExporter.FoodEffect> out) {
        if (!o.has(key)) return;
        for (JsonElement fxel : o.getAsJsonArray(key)) {
            try {
                JsonObject fxo = fxel.getAsJsonObject();
                String effectId = str(fxo, "effectId", "");
                if (effectId.isEmpty()) continue;
                int durationTicks = fxo.has("durationTicks") ? fxo.get("durationTicks").getAsInt() : 100;
                int amplifier     = fxo.has("amplifier")     ? fxo.get("amplifier").getAsInt()     : 0;
                float chance      = fxo.has("chance")        ? fxo.get("chance").getAsFloat()      : 1.0f;
                out.add(new KubeJSItemAttrExporter.FoodEffect(effectId, durationTicks, amplifier, chance));
            } catch (Exception ex) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped {} entry: {}", key, ex.getMessage());
            }
        }
    }

    private static JsonArray writeEffects(List<KubeJSItemAttrExporter.FoodEffect> effects) {
        JsonArray fx = new JsonArray();
        for (KubeJSItemAttrExporter.FoodEffect fe : effects) {
            JsonObject feo = new JsonObject();
            feo.addProperty("effectId",      fe.effectId);
            feo.addProperty("durationTicks", fe.durationTicks);
            feo.addProperty("amplifier",     fe.amplifier);
            feo.addProperty("chance",        fe.chance);
            fx.add(feo);
        }
        return fx;
    }

    private static String str(JsonObject o, String k, String def) {
        try { return o.has(k) ? o.get(k).getAsString() : def; } catch (Exception e) { return def; }
    }

    private static boolean bool(JsonObject o, String k) {
        try { return o.has(k) && o.get(k).getAsBoolean(); } catch (Exception e) { return false; }
    }

    private static boolean bool2(JsonObject o, String k, boolean def) {
        try { return o.has(k) ? o.get(k).getAsBoolean() : def; } catch (Exception e) { return def; }
    }

    private static <E extends Enum<E>> E enumVal(JsonObject o, String k, Class<E> cls, E def) {
        try { return o.has(k) ? Enum.valueOf(cls, o.get(k).getAsString()) : def; }
        catch (Exception e) { return def; }
    }

    private static JsonObject stackToJson(ItemStack s) {
        JsonObject o = new JsonObject();
        if (s == null || s.isEmpty()) {
            o.addProperty("id", "minecraft:air");
            return o;
        }
        ResourceLocation k = BuiltInRegistries.ITEM.getKey(s.getItem());
        o.addProperty("id", k != null ? k.toString() : "minecraft:air");
        if (s.getCount() != 1) o.addProperty("count", s.getCount());
        if (s.hasTag()) o.addProperty("nbt", Objects.requireNonNull(s.getTag()).toString());
        return o;
    }

    /**
     * Reads back an ItemStack from either the new {id, count, nbt} object
     * format or the legacy plain-string format (just the item id) that
     * earlier versions of this mod wrote, for backward compatibility with
     * existing crafteditor_state.json files.
     */
    private static ItemStack stackFromJson(JsonElement el) {
        if (el == null || el.isJsonNull()) return ItemStack.EMPTY;

        String id;
        int count = 1;
        String nbt = null;

        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            id = str(o, "id", "minecraft:air");
            count = Math.max(1, oi(o, "count") > 0 ? oi(o, "count") : 1);
            nbt = o.has("nbt") ? o.get("nbt").getAsString() : null;
        } else {
            id = el.getAsString();
        }

        if (id == null || id.isEmpty() || id.equals("minecraft:air")) return ItemStack.EMPTY;
        try {
            var item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
            if (item == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(item, count);
            if (nbt != null && !nbt.isEmpty()) {
                try {
                    stack.setTag(net.minecraft.nbt.TagParser.parseTag(nbt));
                } catch (Exception nbtEx) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] Could not parse NBT for {}: {}", id, nbtEx.getMessage());
                }
            }
            return stack;
        } catch (Exception e) { return ItemStack.EMPTY; }
    }

    private static ItemStack stackFromJson(String id) {
        if (id == null) return ItemStack.EMPTY;
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        return stackFromJson((JsonElement) o);
    }

    private static JsonArray toMachineSlotArray(List<RecipeData.MachineSlot> slots) {
        JsonArray a = new JsonArray();
        for (RecipeData.MachineSlot s : slots) a.add(machineSlotToJson(s));
        return a;
    }

    private static JsonObject machineSlotToJson(RecipeData.MachineSlot s) {
        JsonObject o = new JsonObject();
        o.addProperty("type", s.type.name());
        o.add("item", stackToJson(s.item));
        o.addProperty("fluidId", s.fluidId);
        o.addProperty("fluidAmount", s.fluidAmount);
        o.addProperty("tagId", s.tagId);
        o.addProperty("tagCount", s.tagCount);
        o.addProperty("ringSlot", s.ringSlot);
        o.addProperty("chance", s.chance);
        return o;
    }

    private static List<RecipeData.MachineSlot> machineSlotListFromJson(JsonArray arr) {
        List<RecipeData.MachineSlot> list = new ArrayList<>();
        for (JsonElement el : arr) {
            try {
                JsonObject o = el.getAsJsonObject();
                RecipeData.MachineSlot s = new RecipeData.MachineSlot();
                s.type = enumVal(o, "type", RecipeData.MachineSlotType.class, RecipeData.MachineSlotType.ITEM);
                s.item = o.has("item") ? stackFromJson(o.get("item")) : ItemStack.EMPTY;
                s.fluidId = str(o, "fluidId", "");
                s.fluidAmount = o.has("fluidAmount") && oi(o, "fluidAmount") > 0 ? oi(o, "fluidAmount") : 1000;
                s.tagId = str(o, "tagId", "");
                s.tagCount = o.has("tagCount") && oi(o, "tagCount") > 0 ? oi(o, "tagCount") : 1;
                s.ringSlot = oi(o, "ringSlot");
                double savedChance = od(o, "chance");
                s.chance = savedChance >= 0.0 ? Math.max(0.0, Math.min(1.0, savedChance)) : 1.0;
                list.add(s);
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    private static JsonArray toStackArray(List<ItemStack> stacks) {
        JsonArray a = new JsonArray();
        for (ItemStack s : stacks) a.add(stackToJson(s));
        return a;
    }

    private static List<ItemStack> stackListFromJson(JsonArray arr) {
        List<ItemStack> list = new ArrayList<>();
        for (JsonElement el : arr) list.add(stackFromJson(el));
        return list;
    }
}