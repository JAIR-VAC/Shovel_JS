package com.kve.common.kubejs;

import com.kve.common.util.SafeFiles;
import com.kve.common.util.JsStrings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kve.CraftEditorMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class RecipeReplacementExporter {

    private RecipeReplacementExporter() {}

    public enum Kind { INPUT, OUTPUT, UNIFY }

    /**
     * @param modFilter() "" = todas las recetas; "create" = solo las de ese mod; "!create" = todas menos las de ese mod.
     * @param extraItems() UNIFY: los demas miembros del tag (se aceptan como el tag en ingredientes y, si {@code hideOthers()}, se ocultan).
     */
    public record Rule(String id, Kind kind, String match, String replacement, String modFilter,
                       List<String> extraItems, boolean hideOthers) {
        public Rule {
            modFilter = modFilter == null ? "" : modFilter.trim();
            extraItems = extraItems == null ? List.of() : List.copyOf(extraItems);
        }
    }

    public static final Path STORE = Paths.get("kubejs", "config", "shovel_js_recipe_replacements.json");

    public static Path serverScript() { return KveScriptPaths.SERVER_ROOT.resolve("replacements.js"); }

    public static Path clientHideScript() { return KveScriptPaths.CLIENT_ROOT.resolve("unify_hide.js"); }

    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.\\-]+:[a-z0-9_./\\-]+");
    private static final Pattern MOD_ID = Pattern.compile("!?[a-z0-9_.\\-]+");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final List<Rule> RULES = new ArrayList<>();
    private static boolean loaded = false;

    public static boolean isItemId(String s) { return s != null && ITEM_ID.matcher(s).matches(); }

    public static boolean isTagRef(String s) { return s != null && s.startsWith("#") && ITEM_ID.matcher(s.substring(1)).matches(); }

    /** Lista de problemas (vacia = regla valida). Mensajes cortos, se muestran tal cual en el editor. */
    public static List<String> validate(Rule r) {
        List<String> errors = new ArrayList<>();
        if (r == null) { errors.add("regla vacia"); return errors; }
        switch (r.kind()) {
            case INPUT -> {
                if (!isItemId(r.match()) && !isTagRef(r.match())) errors.add("'Buscar' debe ser un id (mod:item) o un #tag");
                if (!isItemId(r.replacement()) && !isTagRef(r.replacement())) errors.add("'Reemplazar por' debe ser un id o un #tag");
            }
            case OUTPUT -> {
                if (!isItemId(r.match()) && !isTagRef(r.match())) errors.add("'Buscar' debe ser un id (mod:item) o un #tag");
                if (!isItemId(r.replacement())) errors.add("un resultado solo puede ser un item concreto (no un #tag)");
            }
            case UNIFY -> {
                if (!isTagRef(r.match())) errors.add("para unificar, 'Buscar' debe ser un #tag");
                if (!isItemId(r.replacement())) errors.add("elige el item preferido del tag");
                for (String e : r.extraItems()) if (!isItemId(e)) errors.add("miembro invalido: " + e);
            }
        }
        if (r.match() != null && r.match().equals(r.replacement())) errors.add("'Buscar' y 'Reemplazar por' son iguales");
        if (!r.modFilter().isEmpty() && !MOD_ID.matcher(r.modFilter()).matches()) errors.add("filtro de mod invalido (usa 'mod' o '!mod')");
        return errors;
    }

    /** Descripcion en una linea para listas, historial y notas de version. */
    public static String describe(Rule r) {
        String scope = r.modFilter().isEmpty() ? "" : r.modFilter().startsWith("!")
                ? " (salvo recetas de " + r.modFilter().substring(1) + ")"
                : " (solo recetas de " + r.modFilter() + ")";
        return switch (r.kind()) {
            case INPUT -> "Ingrediente " + r.match() + " -> " + r.replacement() + scope;
            case OUTPUT -> "Resultado " + r.match() + " -> " + r.replacement() + scope;
            case UNIFY -> "Unificar " + r.match() + " en " + r.replacement() + scope
                    + (r.hideOthers() && !r.extraItems().isEmpty() ? " (oculta " + r.extraItems().size() + " en JEI/REI)" : "");
        };
    }

    public static synchronized List<Rule> rules() {
        ensureLoaded();
        return List.copyOf(RULES);
    }

    /** Reemplaza TODAS las reglas, las guarda y reescribe los dos scripts. Llamado desde el onApply del cambio pendiente. */
    public static synchronized void commit(List<Rule> newRules) {
        ensureLoaded();
        RULES.clear();
        RULES.addAll(newRules);
        save();
        writeScripts();
    }

    static synchronized void resetForTests(List<Rule> rules) {
        RULES.clear();
        RULES.addAll(rules);
        loaded = true;
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(STORE)) return;
        try {
            JsonArray arr = JsonParser.parseString(Files.readString(STORE, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                List<String> extra = new ArrayList<>();
                if (o.has("extraItems")) for (JsonElement e : o.getAsJsonArray("extraItems")) extra.add(e.getAsString());
                Rule r = new Rule(o.get("id").getAsString(), Kind.valueOf(o.get("kind").getAsString()),
                        o.get("match").getAsString(), o.get("replacement").getAsString(),
                        o.has("modFilter") ? o.get("modFilter").getAsString() : "", extra,
                        o.has("hideOthers") && o.get("hideOthers").getAsBoolean());
                if (validate(r).isEmpty()) RULES.add(r);
                else CraftEditorMod.LOGGER.warn("[Shovel JS] Ignored invalid recipe replacement rule {}: {}", r.id(), validate(r));
            }
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Failed to load {}: {}", STORE, ex.getMessage());
        }
    }

    private static void save() {
        JsonArray arr = new JsonArray();
        for (Rule r : RULES) {
            JsonObject o = new JsonObject();
            o.addProperty("id", r.id());
            o.addProperty("kind", r.kind().name());
            o.addProperty("match", r.match());
            o.addProperty("replacement", r.replacement());
            if (!r.modFilter().isEmpty()) o.addProperty("modFilter", r.modFilter());
            if (!r.extraItems().isEmpty()) {
                JsonArray ex = new JsonArray();
                r.extraItems().forEach(ex::add);
                o.add("extraItems", ex);
            }
            if (r.hideOthers()) o.addProperty("hideOthers", true);
            arr.add(o);
        }
        writeAtomic(STORE, GSON.toJson(arr));
    }

    private static void writeScripts() {
        try { Files.createDirectories(KveScriptPaths.SERVER_ROOT); } catch (IOException ignored) {}
        writeAtomic(serverScript(), buildServerScript(RULES));
        try { Files.createDirectories(KveScriptPaths.CLIENT_ROOT); } catch (IOException ignored) {}
        writeAtomic(clientHideScript(), buildClientHideScript(RULES));
    }

    private static void writeAtomic(Path file, String content) {
        try {
            SafeFiles.writeAtomic(file, content);
            CraftEditorMod.LOGGER.info("[Shovel JS] Wrote: {}", file.toAbsolutePath());
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Write failed {}: {}", file, ex.getMessage());
        }
    }

    static String filterJs(String modFilter) {
        if (modFilter == null || modFilter.isBlank()) return "{}";
        String m = modFilter.trim().toLowerCase(Locale.ROOT);
        return m.startsWith("!") ? "{ not: { mod: " + JsStrings.quote(m.substring(1)) + " } }" : "{ mod: " + JsStrings.quote(m) + " }";
    }

    public static String buildServerScript(List<Rule> rules) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Shovel JS - Reemplazos globales y unificacion de recetas (AUTO-GENERATED)\n");
        sb.append("// Editar desde Shovel JS: Recetas > Herramientas > Reemplazo global / Unificar tag.\n");
        sb.append("// Cada regla va en su propio try/catch: una rota no frena a las demas.\n\n");
        if (rules.isEmpty()) {
            sb.append("// (sin reglas)\n");
            return sb.toString();
        }
        sb.append("ServerEvents.recipes(event => {\n");
        for (Rule r : rules) {
            if (!validate(r).isEmpty()) continue;
            String f = filterJs(r.modFilter());
            sb.append("  // [").append(r.id()).append("] ").append(describe(r)).append("\n");
            sb.append("  try {\n");
            switch (r.kind()) {
                case INPUT -> sb.append("    event.replaceInput(").append(f).append(", ").append(JsStrings.quote(r.match())).append(", ").append(JsStrings.quote(r.replacement())).append(")\n");
                case OUTPUT -> sb.append("    event.replaceOutput(").append(f).append(", ").append(JsStrings.quote(r.match())).append(", ").append(JsStrings.quote(r.replacement())).append(")\n");
                case UNIFY -> {
                    sb.append("    event.replaceOutput(").append(f).append(", ").append(JsStrings.quote(r.match())).append(", ").append(JsStrings.quote(r.replacement())).append(")\n");
                    for (String other : r.extraItems()) {
                        if (other.equals(r.replacement())) continue;
                        sb.append("    event.replaceInput(").append(f).append(", ").append(JsStrings.quote(other)).append(", ").append(JsStrings.quote(r.match())).append(")\n");
                    }
                }
            }
            sb.append("  } catch (ex) { console.error('[Shovel JS] Regla de reemplazo ").append(r.id()).append(" fallo: ' + ex) }\n");
        }
        sb.append("})\n");
        return sb.toString();
    }

    /** Items a ocultar por las reglas UNIFY con hideOthers() (nunca el preferido), en el mismo formato de script ya probado por KubeJSItemAttrExporter. */
    public static String buildClientHideScript(List<Rule> rules) {
        LinkedHashSet<String> hide = new LinkedHashSet<>();
        for (Rule r : rules) {
            if (r.kind() != Kind.UNIFY || !r.hideOthers() || !validate(r).isEmpty()) continue;
            for (String other : r.extraItems()) if (!other.equals(r.replacement())) hide.add(other);
        }
        return KubeJSItemAttrExporter.buildHideViewersScript(new ArrayList<>(hide))
                .replace("// Shovel JS - Hide from Recipe Viewers (AUTO-GENERATED)",
                        "// Shovel JS - Unificacion: miembros no preferidos ocultos de JEI/REI (AUTO-GENERATED)");
    }

    /** Id corto y unico para una regla nueva. */
    public static String newRuleId(List<Rule> existing) {
        int n = existing.size() + 1;
        LinkedHashSet<String> used = new LinkedHashSet<>();
        for (Rule r : existing) used.add(r.id());
        while (used.contains("r" + n)) n++;
        return "r" + n;
    }
}
