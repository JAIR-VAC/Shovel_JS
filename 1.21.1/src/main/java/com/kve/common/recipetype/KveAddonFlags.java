package com.kve.common.recipetype;

import com.google.gson.*;
import com.kve.CraftEditorMod;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Backs {@link RecipeTypeDescriptor.DedicatedCall#requiresAddon}.
 *
 * Some recipe-type addons can't be verified through Forge's {@code ModList}
 * at all - the most common case being KJSPKG packages (e.g.
 * kubejs-more-recipe-types): those are plain .js files copied into
 * kubejs/, not a mod jar, so they never register a modid anywhere KVE could
 * check. Others technically ARE separate mod jars, but this catalog isn't
 * confident enough about their exact modid string to gate on it
 * automatically (getting that wrong either silently never fires the
 * dedicated call - safe - or, worse, could match an unrelated mod that
 * happens to share the guessed id).
 *
 * Rather than guess, every {@code requiresAddon} label is OFF by default
 * and the player/pack maker flips it on once, after actually confirming
 * the addon is installed, in:
 *
 *   kubejs/config/kve_addon_flags.json
 *   { "kubejs-more-recipe-types": true, "kubejs-mekanism": false, ... }
 *
 * The file is regenerated (adding any newly-seen labels, defaulted to
 * false) every time the descriptor catalog reloads, so a modpack maker
 * always sees the full list of labels currently in use without having to
 * read Java source to find them.
 */
public final class KveAddonFlags {
    private KveAddonFlags() {}

    private static final Path FILE = Paths.get("kubejs", "config", "shovel_js_addon_flags.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, Boolean> FLAGS = new LinkedHashMap<>();
    private static boolean loaded = false;

    public static synchronized boolean isEnabled(String addonLabel) {
        if (addonLabel == null || addonLabel.isBlank()) return false;
        loadIfNeeded();
        return FLAGS.getOrDefault(addonLabel, false);
    }

    /**
     * Flips a single flag and persists it immediately. This is what lets
     * the "Tipo conocido... habilita X" hint in CraftEditorScreen /
     * MachineRecipeEditScreen offer an in-game toggle instead of telling
     * the player to go hand-edit kve_addon_flags.json - the whole point of
     * KVE is not needing a text editor, so a config file with no in-game
     * way to change it was a gap in that promise.
     */
    public static synchronized void setEnabled(String addonLabel, boolean value) {
        if (addonLabel == null || addonLabel.isBlank()) return;
        loadIfNeeded();
        FLAGS.put(addonLabel, value);
        save();
    }

    /** Called by the descriptor registry after loading every descriptor, so any label used by a
     *  bundled/user JSON file that isn't in the flags file yet gets added (defaulted to false)
     *  instead of silently having no way to ever be turned on. */
    public static synchronized void ensureKnown(Collection<String> addonLabels) {
        loadIfNeeded();
        boolean changed = false;
        for (String label : addonLabels) {
            if (label == null || label.isBlank()) continue;
            if (!FLAGS.containsKey(label)) { FLAGS.put(label, false); changed = true; }
        }
        if (changed) save();
    }

    private static void loadIfNeeded() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(FILE)) { save(); return; }
        try {
            String json = Files.readString(FILE, StandardCharsets.UTF_8);
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                try { FLAGS.put(e.getKey(), e.getValue().getAsBoolean()); } catch (Exception ex) {
                    CraftEditorMod.LOGGER.debug("[Shovel JS] flag invalido '{}' en {}, se ignora: {}", e.getKey(), FILE, ex.toString());
                }
            }
        } catch (Exception e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not read {}: {}", FILE, e.getMessage());
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject o = new JsonObject();
            for (Map.Entry<String, Boolean> e : FLAGS.entrySet()) o.addProperty(e.getKey(), e.getValue());
            Files.writeString(FILE, GSON.toJson(o), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not write {}: {}", FILE, e.getMessage());
        }
    }
}
