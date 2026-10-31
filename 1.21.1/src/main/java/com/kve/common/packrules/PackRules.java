package com.kve.common.packrules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kve.CraftEditorMod;
import com.kve.common.kubejs.KveScriptPaths;
import com.kve.common.util.SafeFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * "Reglas del pack": ajustes chicos que un modpacker suele necesitar y que
 * no son de ningun editor en particular:
 * <ul>
 *   <li>{@link Rename} - nombre visible de un item/bloque (archivo de idioma en kubejs/assets);</li>
 *   <li>{@link InfoPage} - pagina "Informacion" de un item en JEI/REI;</li>
 *   <li>{@link Compostable} - que se pueda (o no) compostar y con que chance;</li>
 *   <li>{@link SpawnBlock} - que un mob no aparezca de forma natural (opcionalmente solo en ciertas dimensiones);</li>
 *   <li>{@link GameRuleDefault} - gamerules con las que arranca cada mundo nuevo del pack.</li>
 * </ul>
 * Estado del editor en {@code kubejs/config/kve_pack_rules.json}; la salida
 * la escribe {@link PackRulesExporter}.
 */
public final class PackRules {

    public static final class Rename {
        public String targetId = "";
        public boolean block = false;
        /** Clave real de idioma ({@code Item#getDescriptionId()}), la resuelve el editor al elegir el item. */
        public String langKey = "";
        public String locale = "en_us";
        public String name = "";
    }

    public static final class InfoPage {
        public String itemId = "";
        /** Titulo que muestra REI (JEI no tiene titulo propio). */
        public String title = "";
        public List<String> lines = new ArrayList<>();
    }

    public static final class Compostable {
        public String itemId = "";
        /** 0..1; 0 = sacarlo de la lista de compostables. */
        public float chance = 0.65f;
    }

    public static final class SpawnBlock {
        public String entityId = "";
        /** Vacio = todas las dimensiones. */
        public List<String> dimensions = new ArrayList<>();
        /** true = solo spawn natural/de chunk; false = tambien spawners, huevos y comandos. */
        public boolean naturalOnly = true;
    }

    public static final class GameRuleDefault {
        public String rule = "";
        public String value = "";
    }

    public List<Rename> renames = new ArrayList<>();
    public List<InfoPage> infoPages = new ArrayList<>();
    public List<Compostable> compostables = new ArrayList<>();
    public List<SpawnBlock> spawnBlocks = new ArrayList<>();
    public List<GameRuleDefault> gameRules = new ArrayList<>();

    public boolean isEmpty() {
        return renames.isEmpty() && infoPages.isEmpty() && compostables.isEmpty() && spawnBlocks.isEmpty() && gameRules.isEmpty();
    }

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern LOCALE = Pattern.compile("[a-z]{2,3}_[a-z]{2,3}");
    private static final Pattern RULE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    /** Problemas legibles (en espanol, como el resto de validaciones de exporters); vacio = se puede guardar. */
    public List<String> validate() {
        List<String> p = new ArrayList<>();
        for (Rename r : renames) {
            if (!ID.matcher(r.targetId).matches()) p.add("Nombre: id invalido '" + r.targetId + "'");
            if (r.langKey.isBlank()) p.add("Nombre: falta la clave de idioma de " + r.targetId);
            if (!LOCALE.matcher(r.locale).matches()) p.add("Nombre: idioma invalido '" + r.locale + "' (ej. en_us, es_es)");
            if (r.name.isBlank()) p.add("Nombre: " + r.targetId + " no tiene nombre");
        }
        for (InfoPage i : infoPages) {
            if (!ID.matcher(i.itemId).matches()) p.add("Info: id invalido '" + i.itemId + "'");
            if (i.lines.stream().allMatch(String::isBlank)) p.add("Info: " + i.itemId + " no tiene texto");
        }
        for (Compostable c : compostables) {
            if (!ID.matcher(c.itemId).matches()) p.add("Compost: id invalido '" + c.itemId + "'");
            if (!(c.chance >= 0f && c.chance <= 1f)) p.add("Compost: la chance de " + c.itemId + " debe ir de 0 a 1");
        }
        for (SpawnBlock s : spawnBlocks) {
            if (!ID.matcher(s.entityId).matches()) p.add("Spawn: id invalido '" + s.entityId + "'");
            for (String d : s.dimensions) if (!ID.matcher(d).matches()) p.add("Spawn: dimension invalida '" + d + "'");
        }
        for (GameRuleDefault g : gameRules) {
            if (!RULE.matcher(g.rule).matches()) p.add("Gamerule: nombre invalido '" + g.rule + "'");
            if (!g.value.equalsIgnoreCase("true") && !g.value.equalsIgnoreCase("false") && !g.value.matches("-?\\d+")) {
                p.add("Gamerule: " + g.rule + " necesita true/false o un numero");
            }
        }
        return p;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        JsonArray a = new JsonArray();
        for (Rename r : renames) {
            JsonObject j = new JsonObject();
            j.addProperty("target", r.targetId); j.addProperty("block", r.block);
            j.addProperty("key", r.langKey); j.addProperty("locale", r.locale); j.addProperty("name", r.name);
            a.add(j);
        }
        o.add("renames", a);
        a = new JsonArray();
        for (InfoPage i : infoPages) {
            JsonObject j = new JsonObject();
            j.addProperty("item", i.itemId); j.addProperty("title", i.title);
            JsonArray lines = new JsonArray();
            i.lines.forEach(lines::add);
            j.add("lines", lines);
            a.add(j);
        }
        o.add("info", a);
        a = new JsonArray();
        for (Compostable c : compostables) {
            JsonObject j = new JsonObject();
            j.addProperty("item", c.itemId); j.addProperty("chance", c.chance);
            a.add(j);
        }
        o.add("compost", a);
        a = new JsonArray();
        for (SpawnBlock s : spawnBlocks) {
            JsonObject j = new JsonObject();
            j.addProperty("entity", s.entityId); j.addProperty("naturalOnly", s.naturalOnly);
            JsonArray dims = new JsonArray();
            s.dimensions.forEach(dims::add);
            j.add("dimensions", dims);
            a.add(j);
        }
        o.add("spawnBlocks", a);
        a = new JsonArray();
        for (GameRuleDefault g : gameRules) {
            JsonObject j = new JsonObject();
            j.addProperty("rule", g.rule); j.addProperty("value", g.value);
            a.add(j);
        }
        o.add("gameRules", a);
        return o;
    }

    public static PackRules fromJson(JsonObject o) {
        PackRules r = new PackRules();
        for (JsonElement el : arr(o, "renames")) {
            JsonObject j = el.getAsJsonObject();
            Rename n = new Rename();
            n.targetId = str(j, "target"); n.block = j.has("block") && j.get("block").getAsBoolean();
            n.langKey = str(j, "key"); n.locale = str(j, "locale").isEmpty() ? "en_us" : str(j, "locale"); n.name = str(j, "name");
            r.renames.add(n);
        }
        for (JsonElement el : arr(o, "info")) {
            JsonObject j = el.getAsJsonObject();
            InfoPage i = new InfoPage();
            i.itemId = str(j, "item"); i.title = str(j, "title");
            for (JsonElement l : arr(j, "lines")) i.lines.add(l.getAsString());
            r.infoPages.add(i);
        }
        for (JsonElement el : arr(o, "compost")) {
            JsonObject j = el.getAsJsonObject();
            Compostable c = new Compostable();
            c.itemId = str(j, "item"); c.chance = j.has("chance") ? j.get("chance").getAsFloat() : 0.65f;
            r.compostables.add(c);
        }
        for (JsonElement el : arr(o, "spawnBlocks")) {
            JsonObject j = el.getAsJsonObject();
            SpawnBlock s = new SpawnBlock();
            s.entityId = str(j, "entity"); s.naturalOnly = !j.has("naturalOnly") || j.get("naturalOnly").getAsBoolean();
            for (JsonElement d : arr(j, "dimensions")) s.dimensions.add(d.getAsString());
            r.spawnBlocks.add(s);
        }
        for (JsonElement el : arr(o, "gameRules")) {
            JsonObject j = el.getAsJsonObject();
            GameRuleDefault g = new GameRuleDefault();
            g.rule = str(j, "rule"); g.value = str(j, "value");
            r.gameRules.add(g);
        }
        return r;
    }

    public PackRules copy() {
        return fromJson(toJson());
    }

    private static Iterable<JsonElement> arr(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString().trim() : "";
    }

    public static Path storeFile() {
        return KveScriptPaths.CONFIG_ROOT.resolve("shovel_js_pack_rules.json");
    }

    private static PackRules current;

    /** Reglas guardadas (lo ultimo Aplicado); se leen del disco la primera vez. */
    public static synchronized PackRules current() {
        if (current == null) current = load(storeFile());
        return current;
    }

    /** Solo para PackRulesExporter#commit y tests. */
    public static synchronized void setCurrent(PackRules rules) {
        current = rules;
    }

    static PackRules load(Path file) {
        if (!Files.isRegularFile(file)) return new PackRules();
        try {
            return fromJson(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] No se pudo leer {}: {}", file, ex.toString());
            return new PackRules();
        }
    }

    static void save(Path file, PackRules rules) throws IOException {
        SafeFiles.writeAtomic(file, GSON.toJson(rules.toJson()) + "\n");
    }

    /** "en_us" en minusculas con guion bajo, tal como lo espera un archivo de idioma. */
    public static String normalizeLocale(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
