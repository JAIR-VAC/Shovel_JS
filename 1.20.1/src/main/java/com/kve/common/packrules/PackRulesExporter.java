package com.kve.common.packrules;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kve.CraftEditorMod;
import com.kve.common.kubejs.KveScriptPaths;
import com.kve.common.kubejs.ScriptWriteGuard;
import com.kve.common.util.JsStrings;
import com.kve.common.util.KveNumbers;
import com.kve.common.util.SafeFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Salida de {@link PackRules}:
 * <ul>
 *   <li>{@code server_scripts/kve/pack/rules.js} - compostables, spawns y
 *       gamerules via {@code ShovelAPI.*} (los aplica {@link PackRulesRuntime} en Java);</li>
 *   <li>{@code client_scripts/kve/pack/recipe_viewer_info.js} - paginas de
 *       informacion con {@code JEIEvents.information} / {@code REIEvents.information}
 *       (firmas verificadas en las fuentes de KubeJS 2001.6.5);</li>
 *   <li>{@code kubejs/assets/<mod>/lang/<idioma>.json} - nombres visibles. KubeJS
 *       carga esos archivos por encima de los de los mods; se usa el namespace
 *       del propio item para que la clave gane sobre la del mod.</li>
 * </ul>
 */
public final class PackRulesExporter {
    private PackRulesExporter() {}

    public static final String HEADER = "// Shovel JS - Reglas del pack (AUTO-GENERATED)\n";

    public static String buildServerScript(PackRules r) {
        StringBuilder sb = new StringBuilder(HEADER);
        sb.append("// Compostables, spawns bloqueados y gamerules por defecto. Las aplica Java\n");
        sb.append("// (sin codigo por evento); se vuelven a registrar en cada /reload.\n\n");
        sb.append("ShovelAPI.clearPackRules()\n");
        for (PackRules.Compostable c : r.compostables) {
            call(sb, "setCompostable(" + JsStrings.quote(c.itemId) + ", " + KveNumbers.trimmed(c.chance, 3) + ")", c.itemId);
        }
        for (PackRules.SpawnBlock s : r.spawnBlocks) {
            call(sb, "blockSpawn(" + JsStrings.quote(s.entityId) + ", " + JsStrings.quote(String.join(",", s.dimensions))
                    + ", " + s.naturalOnly + ")", s.entityId);
        }
        for (PackRules.GameRuleDefault g : r.gameRules) {
            call(sb, "defaultGameRule(" + JsStrings.quote(g.rule) + ", " + JsStrings.quote(g.value) + ")", g.rule);
        }
        return sb.toString();
    }

    private static void call(StringBuilder sb, String call, String label) {
        sb.append("try { ShovelAPI.").append(call).append(" } catch (ex) { console.error('[Shovel JS] regla del pack ")
          .append(JsStrings.escape(label)).append(": ' + ex) }\n");
    }

    public static String buildClientInfoScript(PackRules r) {
        StringBuilder sb = new StringBuilder(HEADER);
        sb.append("// Paginas de \"Informacion\" en JEI / REI. client_scripts: reinicia el cliente para verlas.\n\n");
        if (r.infoPages.isEmpty()) {
            sb.append("// (sin paginas de informacion)\n");
            return sb.toString();
        }
        sb.append("if (typeof JEIEvents !== 'undefined') {\n");
        sb.append("  JEIEvents.information(event => {\n");
        for (PackRules.InfoPage p : r.infoPages) {
            sb.append("    try { event.addItem(").append(JsStrings.quote(p.itemId)).append(", ").append(lines(p))
              .append(") } catch (ex) { console.error('[Shovel JS] info JEI ").append(JsStrings.escape(p.itemId)).append(": ' + ex) }\n");
        }
        sb.append("  })\n}\n\n");
        sb.append("if (typeof REIEvents !== 'undefined') {\n");
        sb.append("  REIEvents.information(event => {\n");
        for (PackRules.InfoPage p : r.infoPages) {
            String title = p.title.isBlank() ? p.itemId : p.title;
            sb.append("    try { event.addItem(").append(JsStrings.quote(p.itemId)).append(", ").append(JsStrings.quote(title))
              .append(", ").append(lines(p)).append(") } catch (ex) { console.error('[Shovel JS] info REI ")
              .append(JsStrings.escape(p.itemId)).append(": ' + ex) }\n");
        }
        sb.append("  })\n}\n");
        return sb.toString();
    }

    private static String lines(PackRules.InfoPage p) {
        List<String> q = new ArrayList<>();
        for (String l : p.lines) if (!l.isBlank()) q.add(JsStrings.quote(l));
        return "[" + String.join(", ", q) + "]";
    }

    static String namespaceOf(String id) {
        int i = id.indexOf(':');
        return i > 0 ? id.substring(0, i) : "minecraft";
    }

    static Map<Path, Map<String, String>> langEntries(PackRules r) {
        Map<Path, Map<String, String>> out = new LinkedHashMap<>();
        for (PackRules.Rename n : r.renames) {
            Path file = KveScriptPaths.langFile(namespaceOf(n.targetId), PackRules.normalizeLocale(n.locale));
            out.computeIfAbsent(file, f -> new TreeMap<>()).put(n.langKey, n.name);
        }
        return out;
    }

    static JsonObject mergeLang(JsonObject existing, Set<String> previouslyOwned, Map<String, String> now) {
        JsonObject out = existing == null ? new JsonObject() : existing.deepCopy();
        for (String k : previouslyOwned) if (!now.containsKey(k)) out.remove(k);
        now.forEach(out::addProperty);
        return out;
    }

    public static List<String> commit(PackRules rules) {
        List<String> warnings = new ArrayList<>();
        PackRules previous = PackRules.current();
        try {
            PackRules.save(PackRules.storeFile(), rules);
        } catch (IOException ex) {
            warnings.add("No se pudo guardar " + PackRules.storeFile() + ": " + ex.getMessage());
        }
        if (!ScriptWriteGuard.writeIfValid(KveScriptPaths.packRulesServerFile(), buildServerScript(rules), "reglas del pack")) {
            warnings.add("rules.js no se escribio (ver log)");
        }
        if (!ScriptWriteGuard.writeIfValid(KveScriptPaths.packRulesInfoFileClient(), buildClientInfoScript(rules), "info JEI/REI")) {
            warnings.add("recipe_viewer_info.js no se escribio (ver log)");
        }
        warnings.addAll(writeLangFiles(langEntries(previous), langEntries(rules)));
        PackRules.setCurrent(rules.copy());
        return warnings;
    }

    static List<String> writeLangFiles(Map<Path, Map<String, String>> before, Map<Path, Map<String, String>> after) {
        List<String> warnings = new ArrayList<>();
        Set<Path> files = new java.util.LinkedHashSet<>(before.keySet());
        files.addAll(after.keySet());
        for (Path file : files) {
            Set<String> owned = before.getOrDefault(file, Map.of()).keySet();
            Map<String, String> now = after.getOrDefault(file, Map.of());
            try {
                JsonObject existing = Files.isRegularFile(file)
                        ? JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject() : null;
                JsonObject merged = mergeLang(existing, owned, now);
                if (merged.size() == 0 && existing != null) Files.deleteIfExists(file);
                else if (merged.size() > 0) SafeFiles.writeAtomic(file, new GsonBuilder().setPrettyPrinting().create().toJson(merged) + "\n");
            } catch (Exception ex) {
                CraftEditorMod.LOGGER.error("[Shovel JS] Nombres: no se pudo escribir {}: {}", file, ex.toString());
                warnings.add("No se pudo escribir " + file.getFileName() + ": " + ex.getMessage());
            }
        }
        return warnings;
    }
}
