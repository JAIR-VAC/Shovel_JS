package com.kve.client.gui.settings;

import com.kve.CraftEditorMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@OnlyIn(Dist.CLIENT)
public final class KveClientSettings {
    private KveClientSettings() {}

    private static final Path FILE = Paths.get("config", "shovel_js", "client_settings.properties");

    public static final String CONFIRM_DISCARDS = "confirm_discards";
    public static final String REVIEW_BEFORE_APPLY = "review_before_apply";
    public static final String AUTO_RELOAD          = "auto_reload";
    /** Con el editor abierto, no dibujar el mundo detras del Studio. Activado por defecto. */
    public static final String EDITOR_POWER_SAVER   = "editor_power_saver";
    public static final String EDITOR_FPS_LIMIT     = "editor_fps_limit";
    public static final String SHOW_WAS_HINT        = "show_was_hint";
    public static final String INLINE_ICONS         = "inline_icons";


    public static final String QUEUE_PENDING = "queue_pending";


    public static final String HIDE_INVENTORY_BUTTON = "hide_inventory_button";

    /** Oculta el boton "KVE" que KveJeiPlugin/KveEmiPlugin dibujan junto a cada receta. Default false (visible). */
    public static final String HIDE_VIEWER_BUTTON = "hide_viewer_button";

    /** Eventos pasa a ser una funcion beta - oculta (pestania y menu) hasta prenderla en Ajustes. Default false. */
    public static final String BETA_EVENTS = "beta_events";

    public static boolean betaEventsEnabled() { return getOrDefault(BETA_EVENTS, false); }

    /** Perfil elegido en la bienvenida (ver {@link UserPersona}); vacio = sin elegir. */
    public static final String PERSONA = "persona";

    private static Map<String, String> cache = null;

    private static Map<String, String> map() {
        if (cache == null) load();
        return cache;
    }

    /** Returns the saved flag for {@code key}, or {@code fallback} if nothing was saved (first launch, or the entry predates this key). */
    public static boolean getOrDefault(String key, boolean fallback) {
        String v = map().get(key);
        return v != null ? Boolean.parseBoolean(v.trim()) : fallback;
    }

    /** Valor de texto guardado para {@code key} (ej. {@link #PERSONA}), o {@code fallback}. */
    public static String getString(String key, String fallback) {
        String v = map().get(key);
        return v != null ? v : fallback;
    }

    public static void putString(String key, String value) {
        map().put(key, value == null ? "" : value);
        save();
    }

    /** Saves {@code value} for {@code key} and flushes immediately - same "fine to call from a button handler, not from render()" rule as PanelLayoutStore#put. */
    public static void put(String key, boolean value) {
        map().put(key, String.valueOf(value));
        save();
    }

    private static void load() {
        cache = new HashMap<>();
        if (!Files.exists(FILE)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not read client_settings.properties: {}", e.getMessage());
            return;
        }
        for (String name : p.stringPropertyNames()) {
            cache.put(name, p.getProperty(name).trim());
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Properties p = new Properties();
            for (Map.Entry<String, String> e : map().entrySet()) {
                p.setProperty(e.getKey(), e.getValue());
            }
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                p.store(w, "Shovel JS - Client-only Workflow preferences (see KveClientSettings/KveSettingsScreen). Safe to delete, everything falls back to its default.");
            }
        } catch (IOException e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not save client_settings.properties: {}", e.getMessage());
        }
    }
}
