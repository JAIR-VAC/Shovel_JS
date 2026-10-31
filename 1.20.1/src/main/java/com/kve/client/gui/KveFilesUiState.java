package com.kve.client.gui;

import com.google.gson.*;
import com.kve.CraftEditorMod;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class KveFilesUiState {
    private KveFilesUiState() {}

    private static final Path FILE = Paths.get("kubejs", "config", "shovel_js_files_ui.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Set<String> PINNED = new LinkedHashSet<>();
    private static String lastSearch = "";
    private static boolean moreActionsExpanded = false;
    private static boolean loaded = false;

    /** Copia en orden de insercion de los pines guardados - nunca la referencia interna, para que el caller (CodeEditorScreen#pinned) sea libre de mutar su propio set sin pisar el cache de esta clase. */
    public static synchronized Set<Path> loadPinned() {
        loadIfNeeded();
        Set<Path> out = new LinkedHashSet<>();
        for (String s : PINNED) {
            try { out.add(Paths.get(s)); } catch (Exception ignored) {}
        }
        return out;
    }

    /** Reemplaza el set completo guardado y persiste - CodeEditorScreen llama esto cada vez que `pinned` cambia (toggle, borrado/rename de archivo), mismo criterio "escribir el estado completo" que {@code KveAddonFlags#setEnabled} usa para un flag individual. */
    public static synchronized void savePinned(Set<Path> pinned) {
        loadIfNeeded();
        PINNED.clear();
        for (Path p : pinned) PINNED.add(p.toString().replace('\\', '/'));
        save();
    }

    /** Ultimo texto tipeado en el searchBox de la lista de archivos, vacio si nunca se guardo nada (o si el jugador lo dejo vacio la ultima vez). */
    public static synchronized String loadLastSearch() {
        loadIfNeeded();
        return lastSearch;
    }

    public static synchronized void saveLastSearch(String search) {
        loadIfNeeded();
        lastSearch = search == null ? "" : search;
        save();
    }

    /** Si el toolbar "Mas opciones" estaba desplegado la ultima vez que se cerro esta pantalla. */
    public static synchronized boolean loadMoreActionsExpanded() {
        loadIfNeeded();
        return moreActionsExpanded;
    }

    public static synchronized void saveMoreActionsExpanded(boolean expanded) {
        loadIfNeeded();
        moreActionsExpanded = expanded;
        save();
    }

    private static void loadIfNeeded() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(FILE)) return;
        try {
            String json = Files.readString(FILE, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(json);
            if (root.isJsonArray()) {
                readPinnedArray(root.getAsJsonArray());
                return;
            }
            JsonObject obj = root.getAsJsonObject();
            if (obj.has("pinned")) readPinnedArray(obj.getAsJsonArray("pinned"));
            if (obj.has("lastSearch")) lastSearch = obj.get("lastSearch").getAsString();
            if (obj.has("moreActionsExpanded")) moreActionsExpanded = obj.get("moreActionsExpanded").getAsBoolean();
        } catch (Exception e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not read {}: {}", FILE, e.getMessage());
        }
    }

    private static void readPinnedArray(JsonArray arr) {
        for (JsonElement el : arr) {
            try { PINNED.add(el.getAsString()); } catch (Exception ex) {
                CraftEditorMod.LOGGER.debug("[Shovel JS] entrada invalida en {}, se ignora: {}", FILE, ex.toString());
            }
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject obj = new JsonObject();
            JsonArray arr = new JsonArray();
            for (String s : PINNED) arr.add(s);
            obj.add("pinned", arr);
            obj.addProperty("lastSearch", lastSearch);
            obj.addProperty("moreActionsExpanded", moreActionsExpanded);
            Files.writeString(FILE, GSON.toJson(obj), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not write {}: {}", FILE, e.getMessage());
        }
    }
}
