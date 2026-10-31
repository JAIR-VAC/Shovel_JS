package com.kve.common.persist;

import com.kve.CraftEditorMod;
import com.kve.common.kubejs.KubeJSMobExporter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Plantillas de mob reusables. A
 * diferencia de {@code duplicateMob()} (copia el draft actual a UN mob
 * elegido en el momento, sin persistir nada) esto guarda un set de
 * propiedades con nombre en disco, para aplicarlo despues sobre cualquier
 * mob sin repetir la edicion manual cada vez.
 * <p>
 * Reusa {@link KubeJSMobExporter.MobEntry} tal cual como el "cuerpo" de la
 * plantilla - MobEditorScreen ya tiene {@code buildDraftMobEntry()} (arma un
 * MobEntry desde el draft actual) y {@code loadEntry(MobEntry)} (aplica un
 * MobEntry al draft SIN tocar entityId/selectedType), asi que aplicar una
 * plantilla a un mob distinto del que la origino ya funciona gratis por como
 * esta separado ese codigo - no hizo falta un formato nuevo.
 * <p>
 * Persistencia simple y sincronica (a proposito, sin el pipeline
 * async/debounced de {@link CraftEditorState}): guardar una plantilla es una
 * accion explicita y poco frecuente del jugador (click de boton), no algo
 * que se dispare por frame o por cada tecla, asi que no hace falta ese
 * nivel de complejidad aca.
 */
public class MobTemplateStore {

    private static final Path FILE = Paths.get("kubejs", "config", "shovel_js_mob_templates.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
            .serializeSpecialFloatingPointValues().create();

    public static class MobTemplate {
        public String name;
        public KubeJSMobExporter.MobEntry data;
    }

    private static List<MobTemplate> templates = null;

    private static void ensureLoaded() {
        if (templates != null) return;
        templates = new ArrayList<>();
        if (!Files.exists(FILE)) return;
        try {
            String json = Files.readString(FILE, StandardCharsets.UTF_8);
            List<MobTemplate> parsed = GSON.fromJson(json, new TypeToken<List<MobTemplate>>() {}.getType());
            if (parsed != null) {
                for (MobTemplate t : parsed) {
                    if (t != null && t.name != null && t.data != null) templates.add(t);
                }
            }
        } catch (Exception e) {
            CraftEditorMod.LOGGER.error("[Shovel JS] No se pudo leer shovel_js_mob_templates.json: {}", e.getMessage());
        }
    }

    private static void writeToDisk() {
        try {
            Path dir = FILE.getParent();
            if (dir != null) Files.createDirectories(dir);
            Files.writeString(FILE, GSON.toJson(templates), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CraftEditorMod.LOGGER.error("[Shovel JS] No se pudo guardar shovel_js_mob_templates.json: {}", e.getMessage());
        }
    }

    /** Lista de solo lectura, ordenada como esta en disco (orden de creacion). */
    public static synchronized List<MobTemplate> getAll() {
        ensureLoaded();
        return new ArrayList<>(templates);
    }

    public static synchronized boolean exists(String name) {
        ensureLoaded();
        for (MobTemplate t : templates) if (t.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    /** Crea o sobreescribe (mismo nombre, sin distinguir mayus/minus) la plantilla dada. entityId de la copia guardada se limpia - la plantilla es de propiedades, no de un mob especifico, y aplicarla nunca lo usa igual (ver loadEntry). */
    public static synchronized void saveOrReplace(String name, KubeJSMobExporter.MobEntry entry) {
        ensureLoaded();
        KubeJSMobExporter.MobEntry copy = entry.copy();
        copy.entityId = "";
        templates.removeIf(t -> t.name.equalsIgnoreCase(name));
        MobTemplate t = new MobTemplate();
        t.name = name;
        t.data = copy;
        templates.add(t);
        writeToDisk();
    }

    public static synchronized void delete(String name) {
        ensureLoaded();
        if (templates.removeIf(t -> t.name.equalsIgnoreCase(name))) writeToDisk();
    }
}
