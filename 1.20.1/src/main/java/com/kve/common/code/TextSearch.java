package com.kve.common.code;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Motor de "Buscar y reemplazar" del editor de codigo.
 * <p>
 * Puro Java, sin Minecraft, para poder testearlo aparte. Trabaja linea por
 * linea sobre la misma {@code List<String>} que usa el editor: una
 * coincidencia nunca cruza un salto de linea (igual que la busqueda que ya
 * existia), y un reemplazo nunca introduce saltos de linea nuevos.
 */
public final class TextSearch {

    private TextSearch() {}

    /** Opciones de busqueda. */
    public record Options(boolean matchCase, boolean wholeWord, boolean regex) {
        public static final Options DEFAULT = new Options(false, false, false);
    }

    /** Una coincidencia: linea y rango de columnas [start, end). */
    public record Match(int line, int start, int end) {}

    public static Pattern compile(String query, Options opts) {
        String body = opts.regex() ? query : Pattern.quote(query);
        if (opts.wholeWord()) body = "(?<![\\w$])(?:" + body + ")(?![\\w$])";
        int flags = opts.matchCase() ? 0 : (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return Pattern.compile(body, flags);
    }

    /**
     * Siguiente coincidencia estrictamente despues de (fromLine, fromCol),
     * dando la vuelta al principio del archivo si hace falta. Null si no hay
     * ninguna. Las coincidencias vacias (p.ej. regex {@code a*}) se ignoran
     * para no quedar clavado en la misma posicion.
     */
    public static Match findNext(List<String> lines, int fromLine, int fromCol, Pattern p) {
        int n = lines.size();
        if (n == 0) return null;
        fromLine = Math.max(0, Math.min(n - 1, fromLine));
        for (int step = 0; step <= n; step++) {
            int li = (fromLine + step) % n;
            String s = lines.get(li);
            int start = (step == 0) ? Math.min(s.length(), Math.max(0, fromCol + 1)) : 0;
            if (step == n) {
                Match m = firstIn(s, li, 0, p, Math.max(0, fromCol + 1));
                return m;
            }
            Match m = firstIn(s, li, start, p, Integer.MAX_VALUE);
            if (m != null) return m;
        }
        return null;
    }

    /** Coincidencia anterior a (fromLine, fromCol), con vuelta al final del archivo. */
    public static Match findPrevious(List<String> lines, int fromLine, int fromCol, Pattern p) {
        int n = lines.size();
        if (n == 0) return null;
        fromLine = Math.max(0, Math.min(n - 1, fromLine));
        for (int step = 0; step <= n; step++) {
            int li = Math.floorMod(fromLine - step, n);
            String s = lines.get(li);
            int limit = (step == 0) ? fromCol : Integer.MAX_VALUE;
            if (step == n) return lastIn(s, li, p, Integer.MAX_VALUE, fromCol - 1);
            Match m = lastIn(s, li, p, limit, -1);
            if (m != null) return m;
        }
        return null;
    }

    private static Match firstIn(String s, int li, int from, Pattern p, int maxStartExclusive) {
        Matcher m = p.matcher(s);
        int pos = from;
        while (pos <= s.length() && m.find(pos)) {
            if (m.start() >= maxStartExclusive) return null;
            if (m.end() > m.start()) return new Match(li, m.start(), m.end());
            pos = m.end() + 1;
        }
        return null;
    }

    private static Match lastIn(String s, int li, Pattern p, int maxStartExclusive, int minStartExclusive) {
        Matcher m = p.matcher(s);
        Match last = null;
        while (m.find()) {
            if (m.start() >= maxStartExclusive) break;
            if (m.end() > m.start() && m.start() > minStartExclusive) last = new Match(li, m.start(), m.end());
        }
        return last;
    }

    /** Cuantas coincidencias hay en total. */
    public static int count(List<String> lines, Pattern p) {
        int c = 0;
        for (String s : lines) {
            Matcher m = p.matcher(s);
            while (m.find()) if (m.end() > m.start()) c++;
        }
        return c;
    }

    /**
     * Texto que reemplaza a {@code match}. Con regex se respetan referencias
     * {@code $1}; sin regex el reemplazo es literal.
     */
    public static String replacementFor(String line, Match match, Pattern p, String replacement, Options opts) {
        if (!opts.regex()) return replacement;
        Matcher m = p.matcher(line);
        if (m.find(match.start()) && m.start() == match.start()) {
            StringBuffer sb = new StringBuffer();
            m.appendReplacement(sb, replacement);
            return sb.substring(match.start());
        }
        return replacement;
    }

    /** Reemplaza una sola coincidencia en su linea y devuelve la columna donde termina el texto insertado. */
    public static int replaceOne(List<String> lines, Match match, Pattern p, String replacement, Options opts) {
        String line = lines.get(match.line());
        String rep = replacementFor(line, match, p, replacement, opts);
        lines.set(match.line(), line.substring(0, match.start()) + rep + line.substring(match.end()));
        return match.start() + rep.length();
    }

    /** Reemplaza todas las coincidencias. Devuelve cuantas se reemplazaron. */
    public static int replaceAll(List<String> lines, Pattern p, String replacement, Options opts) {
        String rep = opts.regex() ? replacement : Matcher.quoteReplacement(replacement);
        int total = 0;
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            Matcher m = p.matcher(s);
            StringBuilder sb = new StringBuilder();
            int c = 0;
            while (m.find()) {
                if (m.end() == m.start()) continue;
                m.appendReplacement(sb, rep);
                c++;
            }
            if (c > 0) {
                m.appendTail(sb);
                lines.set(i, sb.toString());
                total += c;
            }
        }
        return total;
    }

    /** Para mensajes: una etiqueta corta de las opciones activas. */
    public static String describe(Options o) {
        StringBuilder sb = new StringBuilder();
        if (o.matchCase()) sb.append("Aa ");
        if (o.wholeWord()) sb.append("ab ");
        if (o.regex()) sb.append(".* ");
        return sb.toString().trim().toLowerCase(Locale.ROOT);
    }
}
