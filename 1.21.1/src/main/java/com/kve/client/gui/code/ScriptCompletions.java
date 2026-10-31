package com.kve.client.gui.code;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Autocompletado con contexto para el editor de codigo, sin
 * depender de otro mod ni de VSCode. Logica pura, sin registries: {@link CodeEditorScreen} le pasa las
 * lineas y los ids, asi se puede testear sin Minecraft.
 * <p>
 * Lo que mas ayuda es saber que metodos tiene {@code event}
 * segun el evento en el que esta el cursor. {@link #enclosingEvent()} encuentra
 * la llamada {@code XxxEvents.yyy(} cuyo parentesis sigue abierto en el cursor,
 * y {@link #eventMembers()} devuelve los metodos de ese evento. Solo se listan
 * llamadas que el editor ya usaba como snippets verificados, mas los getters
 * comunes de los eventos de entidad/jugador/servidor.
 */
public final class ScriptCompletions {

    private ScriptCompletions() {}

    private static final Pattern EVENT_CALL = Pattern.compile("\\b([A-Z][A-Za-z]*Events)\\.([A-Za-z]+)\\s*\\(");

    private static final int MAX_LOOKBACK_LINES = 400;

    private static final Map<String, List<String>> MEMBERS = new LinkedHashMap<>();
    static {
        MEMBERS.put("StartupEvents.registry", List.of(
                "event.create('id')"));
        MEMBERS.put("ServerEvents.recipes", List.of(
                "event.shaped('id', ['XXX', 'XXX', 'XXX'], { X: 'minecraft:stick' })",
                "event.shapeless('id', ['minecraft:stick'])",
                "event.smelting('id', 'minecraft:iron_ingot', 'minecraft:iron_ore')",
                "event.remove({ output: 'minecraft:stick' })",
                "event.custom({ type: 'minecraft:crafting_shaped' })",
                "event.replaceInput({ input: 'minecraft:stick' }, 'minecraft:stick', '#minecraft:saplings')",
                "event.replaceOutput({ output: 'minecraft:stick' }, 'minecraft:stick', 'minecraft:blaze_rod')"));
        MEMBERS.put("ServerEvents.tags", List.of(
                "event.add('c:ingots/copper', 'minecraft:iron_ingot')",
                "event.remove('c:ingots/copper', 'minecraft:iron_ingot')",
                "event.removeAll('c:ingots/copper')"));
        MEMBERS.put("ItemEvents.modification", List.of(
                "event.modify('minecraft:diamond', item => { })"));
        MEMBERS.put("BlockEvents.modification", List.of(
                "event.modify('minecraft:stone', block => { })"));
    }

    private static final List<String> ENTITY_MEMBERS = List.of("event.entity", "event.level", "event.server");
    private static final List<String> PLAYER_MEMBERS = List.of("event.player", "event.level", "event.server");
    private static final List<String> SERVER_MEMBERS = List.of("event.server");

    /**
     * Evento ({@code "ServerEvents.recipes"}...) cuyo callback contiene la
     * posicion {@code (line, col)}, o null. Toma la llamada mas cercana hacia
     * arriba cuyo parentesis de apertura sigue sin cerrar en el cursor, asi un
     * evento ya cerrado mas arriba no se confunde con el actual.
     */
    public static String enclosingEvent(List<String> lines, int line, int col) {
        if (lines == null || line < 0 || line >= lines.size()) return null;
        int first = Math.max(0, line - MAX_LOOKBACK_LINES);
        StringBuilder text = new StringBuilder();
        for (int i = first; i < line; i++) text.append(lines.get(i)).append('\n');
        String cur = lines.get(line);
        text.append(cur, 0, Math.max(0, Math.min(col, cur.length())));
        String before = text.toString();

        String found = null;
        int foundAt = -1;
        Matcher m = EVENT_CALL.matcher(before);
        while (m.find()) {
            if (parenDepth(before, m.end() - 1) > 0 && m.start() > foundAt) {
                found = m.group(1) + "." + m.group(2);
                foundAt = m.start();
            }
        }
        return found;
    }

    static int parenDepth(String s, int from) {
        int depth = 0;
        char quote = 0;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\') { i++; continue; }
                if (c == quote) quote = 0;
                else if (c == '\n' && quote != '`') quote = 0;
                continue;
            }
            if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                int nl = s.indexOf('\n', i);
                if (nl < 0) break;
                i = nl;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') quote = c;
            else if (c == '(') depth++;
            else if (c == ')') depth--;
        }
        return depth;
    }

    /** Metodos/propiedades de {@code event} dentro de ese evento; vacio si no se conoce. */
    public static List<String> eventMembers(String event) {
        if (event == null) return List.of();
        List<String> known = MEMBERS.get(event);
        if (known != null) return known;
        if (event.startsWith("EntityEvents.")) return ENTITY_MEMBERS;
        if (event.startsWith("PlayerEvents.")) return PLAYER_MEMBERS;
        if (event.startsWith("ServerEvents.")) return SERVER_MEMBERS;
        return List.of();
    }

    /** True dentro de {@code ServerEvents.tags}: ahi el primer argumento es un tag id sin '#'. */
    public static boolean isTagEvent(String event) {
        return "ServerEvents.tags".equals(event);
    }

    public static void addMatching(List<String> out, Iterable<String> ids, String needle, String prefix, int cap) {
        if (ids == null || cap <= 0) return;
        String n = needle == null ? "" : needle.toLowerCase(Locale.ROOT);
        int found = 0;
        for (String id : ids) {
            if (found >= cap) break;
            if (!id.toLowerCase(Locale.ROOT).contains(n)) continue;
            String s = prefix + id;
            if (out.contains(s)) continue;
            out.add(s);
            found++;
        }
    }

    /** Filtra {@code candidates} por {@code needle} (contiene, sin mayusculas). */
    public static List<String> filter(List<String> candidates, String needle) {
        String n = needle == null ? "" : needle.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String c : candidates) if (n.isEmpty() || c.toLowerCase(Locale.ROOT).contains(n)) out.add(c);
        return out;
    }
}
