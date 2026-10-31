package com.kve.common.code;

import java.util.List;

/**
 * Operaciones de texto puras (sin Minecraft) para el editor de codigo de
 * Files - seleccion, pares automaticos, corchete pareja, indentacion en
 * bloque. Separadas de {@code CodeEditorScreen} para poder testearlas, mismo
 * criterio que {@link JsFormatter}/{@link TextSearch}. Idea tomada de KubeJS
 * Studio y de cualquier editor moderno: el editor de KVE no
 * tenia modelo de seleccion ni deshacer.
 * <p>
 * Posiciones = (linea, columna) sobre una {@code List<String>} de lineas sin
 * '\n', igual que {@code EditorTab#lines}.
 */
public final class CodeEditOps {

    private CodeEditOps() {}

    public record Pos(int line, int col) implements Comparable<Pos> {
        @Override public int compareTo(Pos o) {
            return line != o.line ? Integer.compare(line, o.line) : Integer.compare(col, o.col);
        }
        public static Pos min(Pos a, Pos b) { return a.compareTo(b) <= 0 ? a : b; }
        public static Pos max(Pos a, Pos b) { return a.compareTo(b) >= 0 ? a : b; }
    }

    /** Recorta una posicion al texto actual (las lineas pueden haber cambiado). */
    public static Pos clamp(List<String> lines, Pos p) {
        int line = Math.max(0, Math.min(lines.size() - 1, p.line()));
        int col = Math.max(0, Math.min(lines.get(line).length(), p.col()));
        return new Pos(line, col);
    }

    /** Texto entre a y b (en cualquier orden), con '\n' entre lineas. */
    public static String textBetween(List<String> lines, Pos a, Pos b) {
        Pos s = clamp(lines, Pos.min(a, b)), e = clamp(lines, Pos.max(a, b));
        if (s.line() == e.line()) return lines.get(s.line()).substring(s.col(), e.col());
        StringBuilder sb = new StringBuilder(lines.get(s.line()).substring(s.col()));
        for (int i = s.line() + 1; i < e.line(); i++) sb.append('\n').append(lines.get(i));
        sb.append('\n').append(lines.get(e.line()), 0, e.col());
        return sb.toString();
    }

    /** Borra el rango [a,b) y devuelve la posicion donde queda el cursor (el inicio). */
    public static Pos deleteRange(List<String> lines, Pos a, Pos b) {
        Pos s = clamp(lines, Pos.min(a, b)), e = clamp(lines, Pos.max(a, b));
        String head = lines.get(s.line()).substring(0, s.col());
        String tail = lines.get(e.line()).substring(e.col());
        for (int i = e.line(); i > s.line(); i--) lines.remove(i);
        lines.set(s.line(), head + tail);
        return s;
    }

    /** Inserta texto (puede tener '\n') en p y devuelve la posicion final del cursor. */
    public static Pos insert(List<String> lines, Pos p, String text) {
        p = clamp(lines, p);
        String cur = lines.get(p.line());
        String head = cur.substring(0, p.col()), tail = cur.substring(p.col());
        String[] parts = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        if (parts.length == 1) {
            lines.set(p.line(), head + parts[0] + tail);
            return new Pos(p.line(), p.col() + parts[0].length());
        }
        lines.set(p.line(), head + parts[0]);
        for (int i = 1; i < parts.length - 1; i++) lines.add(p.line() + i, parts[i]);
        int lastIdx = p.line() + parts.length - 1;
        lines.add(lastIdx, parts[parts.length - 1] + tail);
        return new Pos(lastIdx, parts[parts.length - 1].length());
    }

    /** Cierre que corresponde a un caracter de apertura, o 0 si no es uno. */
    public static char closerFor(char open) {
        return switch (open) {
            case '(' -> ')';
            case '[' -> ']';
            case '{' -> '}';
            case '\'' -> '\'';
            case '"' -> '"';
            case '`' -> '`';
            default -> 0;
        };
    }

    public static boolean isCloser(char c) { return c == ')' || c == ']' || c == '}' || c == '\'' || c == '"' || c == '`'; }

    /**
     * Decide si al tipear {@code open} en {@code col} conviene insertar el par.
     * Para comillas solo si no se esta en medio de una palabra (evita
     * "don't" -> "don''t") y si el caracter siguiente no es alfanumerico; para
     * brackets, si lo que sigue es fin de linea, espacio o un cierre.
     */
    public static boolean shouldAutoPair(String line, int col, char open) {
        if (closerFor(open) == 0) return false;
        char next = col < line.length() ? line.charAt(col) : 0;
        boolean nextOk = next == 0 || Character.isWhitespace(next) || next == ')' || next == ']' || next == '}' || next == ',' || next == ';';
        if (open == '\'' || open == '"' || open == '`') {
            char prev = col > 0 ? line.charAt(col - 1) : 0;
            if (Character.isLetterOrDigit(prev) || prev == '_' || prev == '\\') return false;
            if (insideString(line, col)) return false;
        }
        return nextOk;
    }

    /** True si tipear {@code ch} en col deberia solo "saltar" el cierre que ya esta ahi. */
    public static boolean shouldOvertype(String line, int col, char ch) {
        return isCloser(ch) && col < line.length() && line.charAt(col) == ch;
    }

    /** True si el cursor esta entre un par vacio, p.ej. "(|)" - Backspace borra ambos. */
    public static boolean betweenEmptyPair(String line, int col) {
        if (col <= 0 || col >= line.length()) return false;
        char o = line.charAt(col - 1), c = line.charAt(col);
        return closerFor(o) != 0 && closerFor(o) == c;
    }

    /** Aproximacion por linea: hay un string abierto antes de col? (ignora comillas escapadas). */
    public static boolean insideString(String line, int col) {
        char q = 0;
        for (int i = 0; i < Math.min(col, line.length()); i++) {
            char c = line.charAt(i);
            if (c == '\\') { i++; continue; }
            if (q == 0) {
                if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') return false;
                if (c == '\'' || c == '"' || c == '`') q = c;
            } else if (c == q) q = 0;
        }
        return q != 0;
    }

    /**
     * Posicion del corchete que empareja con el que esta justo en {@code at}
     * (o justo antes del cursor), o null. Ignora corchetes dentro de strings
     * y comentarios de linea (aproximacion por linea, suficiente para
     * resaltar - mismo criterio que el chequeo de balance del editor).
     */
    public static Pos matchingBracket(List<String> lines, Pos cursor) {
        Pos at = null;
        String line = lines.get(cursor.line());
        if (cursor.col() < line.length() && isBracket(line.charAt(cursor.col())) && !inCodeNoise(line, cursor.col())) at = cursor;
        else if (cursor.col() > 0 && isBracket(line.charAt(cursor.col() - 1)) && !inCodeNoise(line, cursor.col() - 1)) at = new Pos(cursor.line(), cursor.col() - 1);
        if (at == null) return null;
        char c = lines.get(at.line()).charAt(at.col());
        boolean forward = c == '(' || c == '[' || c == '{';
        char open = forward ? c : openerFor(c), close = forward ? closerFor(c) : c;
        int depth = 0;
        int li = at.line(), ci = at.col();
        int scanned = 0;
        while (li >= 0 && li < lines.size() && scanned < 20000) {
            String l = lines.get(li);
            if (ci >= 0 && ci < l.length() && !inCodeNoise(l, ci)) {
                char ch = l.charAt(ci);
                if (ch == open) depth += forward ? 1 : -1;
                else if (ch == close) depth += forward ? -1 : 1;
                if (depth == 0) return new Pos(li, ci);
            }
            scanned++;
            if (forward) {
                ci++;
                if (ci >= l.length()) { li++; ci = 0; while (li < lines.size() && lines.get(li).isEmpty()) li++; }
            } else {
                ci--;
                if (ci < 0) { li--; while (li >= 0 && lines.get(li).isEmpty()) li--; if (li >= 0) ci = lines.get(li).length() - 1; }
            }
        }
        return null;
    }

    private static boolean isBracket(char c) { return c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}'; }

    private static char openerFor(char close) {
        return switch (close) { case ')' -> '('; case ']' -> '['; case '}' -> '{'; default -> 0; };
    }

    private static boolean inCodeNoise(String line, int col) {
        if (insideString(line, col)) return true;
        int cm = commentStart(line);
        return cm >= 0 && col >= cm;
    }

    private static int commentStart(String line) {
        char q = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\') { i++; continue; }
            if (q == 0) {
                if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') return i;
                if (c == '\'' || c == '"' || c == '`') q = c;
            } else if (c == q) q = 0;
        }
        return -1;
    }

    /** Agrega {@code unit} al inicio de cada linea del rango [from,to] (lineas vacias incluidas no, para no dejar basura). */
    public static void indentLines(List<String> lines, int from, int to, String unit) {
        for (int i = Math.max(0, from); i <= Math.min(lines.size() - 1, to); i++) {
            if (!lines.get(i).isEmpty()) lines.set(i, unit + lines.get(i));
        }
    }

    /** Quita hasta {@code width} espacios del inicio de cada linea del rango; devuelve cuantos quito de cada una. */
    public static int[] dedentLines(List<String> lines, int from, int to, int width) {
        int lo = Math.max(0, from), hi = Math.min(lines.size() - 1, to);
        int[] removed = new int[Math.max(0, hi - lo + 1)];
        for (int i = lo; i <= hi; i++) {
            String l = lines.get(i);
            int n = 0;
            while (n < width && n < l.length() && l.charAt(n) == ' ') n++;
            if (n > 0) lines.set(i, l.substring(n));
            removed[i - lo] = n;
        }
        return removed;
    }
}
