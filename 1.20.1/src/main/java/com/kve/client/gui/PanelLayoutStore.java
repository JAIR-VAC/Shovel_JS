package com.kve.client.gui;

import com.kve.CraftEditorMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

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
public final class PanelLayoutStore {
    private PanelLayoutStore() {}

    private static final Path FILE = Paths.get("config", "shovel_js", "panel_layout.properties");

    private static Map<String, Integer> cache = null;

    private static Map<String, Integer> map() {
        if (cache == null) load();
        return cache;
    }

    /** Returns the saved width for {@code key}, or {@code fallback} if nothing was saved (first launch, or the entry predates this key). */
    public static int getOrDefault(String key, int fallback) {
        Integer v = map().get(key);
        return v != null ? v : fallback;
    }

    /** Saves {@code width} for {@code key} and flushes immediately - see class doc for why this is fine to call from a mouse-release handler but not from render(). */
    public static void put(String key, int width) {
        map().put(key, width);
        save();
    }

    private static void load() {
        cache = new HashMap<>();
        if (!Files.exists(FILE)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not read panel_layout.properties: {}", e.getMessage());
            return;
        }
        for (String name : p.stringPropertyNames()) {
            try {
                cache.put(name, Integer.parseInt(p.getProperty(name).trim()));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Properties p = new Properties();
            for (Map.Entry<String, Integer> e : map().entrySet()) {
                p.setProperty(e.getKey(), String.valueOf(e.getValue()));
            }
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                p.store(w, "Shovel JS - Persisted resizable-panel widths (see PanelLayoutStore). Safe to delete, screens fall back to their default widths.");
            }
        } catch (IOException e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not save panel_layout.properties: {}", e.getMessage());
        }
    }
}
