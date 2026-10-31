package com.kve.common.trade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kve.CraftEditorMod;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public final class TradeTypeDescriptorRegistry {

    private TradeTypeDescriptorRegistry() {}

    public static final Path USER_DIR = Paths.get("kubejs", "data", "shovel_js", "trade_types");
    private static final String BUNDLED_RESOURCE_ROOT = "/data/shovel_js/trade_types/";
    private static final String[] BUNDLED_FILES = {
            "guardvillagers.json",
            "ribbits.json",
    };

    private static final Gson GSON = new Gson();
    private static final Map<String, TradeTypeDescriptor> BY_ENTITY_ID = new LinkedHashMap<>();
    private static boolean loaded = false;

    private static Predicate<String> modLoadedCheck = TradeTypeDescriptorRegistry::isModLoadedViaForge;

    private static boolean isModLoadedViaForge(String modid) {
        try { return ModList.get().isLoaded(modid); }
        catch (Exception e) { return false; }
    }

    public static void setModLoadedCheckForTests(Predicate<String> check) {
        modLoadedCheck = check != null ? check : TradeTypeDescriptorRegistry::isModLoadedViaForge;
    }

    public static synchronized void loadIfNeeded() {
        if (loaded) return;
        reload();
    }

    public static synchronized void reload() {
        loaded = true;
        BY_ENTITY_ID.clear();
        int bundledCount = loadBundled();
        int userCount = loadUserFiles();
        CraftEditorMod.LOGGER.info("[Shovel JS] Trade type descriptors loaded: {} bundled, {} user/pack, {} total (after overrides).",
                bundledCount, userCount, BY_ENTITY_ID.size());
    }

    /** Test-only: drop back to the unloaded state so a test can seed {@link #BY_ENTITY_ID} itself via {@link #registerForTests()} without the real bundled/user files interfering. */
    public static synchronized void resetForTests() {
        loaded = false;
        BY_ENTITY_ID.clear();
    }

    /** Test-only: register one descriptor directly, bypassing the JSON loader entirely. Marks the registry as already-loaded so {@link #loadIfNeeded()} won't overwrite it with the real bundled/user files. */
    public static synchronized void registerForTests(TradeTypeDescriptor d) {
        loaded = true;
        if (d != null && d.entityTypeId != null && !d.entityTypeId.isBlank()) BY_ENTITY_ID.put(d.entityTypeId, d);
    }

    private static int loadBundled() {
        int count = 0;
        for (String fileName : BUNDLED_FILES) {
            String resourcePath = BUNDLED_RESOURCE_ROOT + fileName;
            try (InputStream in = TradeTypeDescriptorRegistry.class.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] Bundled trade type descriptor missing from jar: {}", resourcePath);
                    continue;
                }
                try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    count += parseAndRegister(JsonParser.parseReader(r), "bundled:" + fileName);
                }
            } catch (Exception e) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Failed to load bundled trade type descriptor {}: {}", resourcePath, e.getMessage());
            }
        }
        return count;
    }

    private static int loadUserFiles() {
        int count = 0;
        if (!Files.isDirectory(USER_DIR)) {
            writeExampleFile();
            return 0;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(USER_DIR, "*.json")) {
            for (Path p : ds) {
                try {
                    String json = Files.readString(p, StandardCharsets.UTF_8);
                    count += parseAndRegister(JsonParser.parseString(json), p.getFileName().toString());
                } catch (Exception e) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] Failed to load trade type descriptor {}: {}", p.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not scan {}: {}", USER_DIR, e.getMessage());
        }
        return count;
    }

    private static int parseAndRegister(JsonElement root, String sourceLabel) {
        int count = 0;
        if (root.isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray()) {
                if (registerOne(el, sourceLabel)) count++;
            }
        } else if (root.isJsonObject()) {
            if (registerOne(root, sourceLabel)) count++;
        }
        return count;
    }

    private static boolean registerOne(JsonElement el, String sourceLabel) {
        if (!el.isJsonObject()) return false;
        try {
            TradeTypeDescriptor d = GSON.fromJson(el, TradeTypeDescriptor.class);
            if (d == null || d.entityTypeId == null || d.entityTypeId.isBlank()) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Trade type descriptor in {} missing entityTypeId, skipped.", sourceLabel);
                return false;
            }
            BY_ENTITY_ID.put(d.entityTypeId, d);
            return true;
        } catch (Exception e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Malformed trade type descriptor in {}: {}", sourceLabel, e.getMessage());
            return false;
        }
    }

    private static void writeExampleFile() {
        try {
            Files.createDirectories(USER_DIR);
            TradeTypeDescriptor example = new TradeTypeDescriptor();
            example.entityTypeId = "examplemod:example_merchant";
            example.displayName = "Example Merchant (ExampleMod)";
            example.requiresMod = "examplemod";
            example.abstractVillagerCompatible = true;
            JsonArray arr = new JsonArray();
            arr.add(toJsonForExample(example));
            Files.writeString(USER_DIR.resolve("_example.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(arr),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            CraftEditorMod.LOGGER.info("[Shovel JS] Wrote example trade type descriptor to {}", USER_DIR.resolve("_example.json"));
        } catch (IOException e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not write example trade type descriptor: {}", e.getMessage());
        }
    }

    private static JsonObject toJsonForExample(TradeTypeDescriptor d) {
        JsonObject o = new JsonObject();
        o.addProperty("entityTypeId", d.entityTypeId);
        o.addProperty("displayName", d.displayName);
        o.addProperty("requiresMod", d.requiresMod);
        o.addProperty("abstractVillagerCompatible", d.abstractVillagerCompatible);
        if (d.professionGetter != null && !d.professionGetter.isBlank()) o.addProperty("professionGetter", d.professionGetter);
        if (d.professions != null && !d.professions.isEmpty()) {
            JsonArray profs = new JsonArray();
            for (String p : d.professions) profs.add(p);
            o.add("professions", profs);
        }
        return o;
    }

    public static Optional<TradeTypeDescriptor> find(String entityTypeId) {
        loadIfNeeded();
        if (entityTypeId == null || entityTypeId.isBlank()) return Optional.empty();
        return Optional.ofNullable(BY_ENTITY_ID.get(entityTypeId));
    }

    public static Collection<TradeTypeDescriptor> all() {
        loadIfNeeded();
        return Collections.unmodifiableCollection(BY_ENTITY_ID.values());
    }

    private static boolean requiredModLoaded(TradeTypeDescriptor d) {
        return d.requiresMod == null || d.requiresMod.isBlank() || modLoadedCheck.test(d.requiresMod);
    }

    public static boolean isNativeCompatible(String entityTypeId) {
        var found = find(entityTypeId);
        if (found.isPresent()) return found.get().abstractVillagerCompatible && requiredModLoaded(found.get());
        return MerchantTypeDetector.detect(entityTypeId) == MerchantTypeDetector.Kind.VILLAGER_LIKE;
    }

    /**
     * Clasificacion efectiva para el Trade Editor/backend nativo: descriptor
     * abstractVillagerCompatible -> VILLAGER_LIKE; descriptor cuyo mod no
     * esta cargado -> NOT_MERCHANT; en otro caso, lo que diga el detector
     * sobre una instancia real.
     */
    public static MerchantTypeDetector.Kind merchantKind(String entityTypeId) {
        var found = find(entityTypeId);
        if (found.isPresent()) {
            if (!requiredModLoaded(found.get())) return MerchantTypeDetector.Kind.NOT_MERCHANT;
            if (found.get().abstractVillagerCompatible) return MerchantTypeDetector.Kind.VILLAGER_LIKE;
        }
        MerchantTypeDetector.Kind k = MerchantTypeDetector.detect(entityTypeId);
        if (found.isPresent() && k == MerchantTypeDetector.Kind.UNKNOWN) return MerchantTypeDetector.Kind.MERCHANT;
        return k;
    }

    /** Getter de profesion propio del mod declarado en el descriptor ("" si no hay). */
    public static String professionGetter(String entityTypeId) {
        return find(entityTypeId).map(d -> d.professionGetter == null ? "" : d.professionGetter).orElse("");
    }

    /** Oficios propios del mod para el picker: los del descriptor + los observados en el mundo cargado. */
    public static java.util.List<String> knownModdedProfessions(String entityTypeId) {
        var d = find(entityTypeId);
        return MerchantTypeDetector.knownProfessions(entityTypeId,
                d.map(x -> x.professions).orElse(null), professionGetter(entityTypeId));
    }

    public static synchronized boolean saveUserDescriptor(TradeTypeDescriptor d) {
        if (d == null || d.entityTypeId == null || d.entityTypeId.isBlank()) return false;
        loadIfNeeded();
        try {
            Files.createDirectories(USER_DIR);
            String fileName = d.entityTypeId.replaceAll("[^a-zA-Z0-9_.-]", "_") + ".json";
            Files.writeString(USER_DIR.resolve(fileName),
                    new GsonBuilder().setPrettyPrinting().create().toJson(toJsonForExample(d)),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not save trade type descriptor for {}: {}", d.entityTypeId, e.getMessage());
            return false;
        }
        BY_ENTITY_ID.put(d.entityTypeId, d);
        return true;
    }
}
