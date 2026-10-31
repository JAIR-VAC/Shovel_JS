package com.kve.client.gui;

public final class SearchFilter {
    private SearchFilter() {}

    /** Full match: checks the id (expects "namespace:path") and a display name. */
    public static boolean matches(String id, String displayName, String rawQuery) {
        return compile(rawQuery).matches(id, displayName);
    }

    /** Convenience overload for pickers that only search by id (no separate display name). */
    public static boolean matches(String id, String rawQuery) {
        return compile(rawQuery).matches(id, null);
    }

    /**
     * Compiled, immutable form of a query string. Cached: compiling the
     * same raw text twice in a row (i.e. once per entry during a refilter)
     * returns the same instance instead of re-parsing.
     */
    public static Query compile(String rawQuery) {
        String raw = rawQuery == null ? "" : rawQuery;
        Query cached = CACHE;
        if (cached != null && cached.raw.equals(raw)) return cached;
        Query built = new Query(raw);
        CACHE = built;
        return built;
    }

    /**
     * Single-entry cache. Written from the client render thread only in
     * practice; a race can only ever cost a redundant re-parse because
     * {@link Query} is immutable and published as one reference (never as
     * a raw/terms pair that could go out of sync).
     */
    private static volatile Query CACHE = null;

    /** What a single term of a query asks for. */
    public enum Kind {
        TEXT,
        NAMESPACE,
        TAG
    }

    public static final class Token {
        private final Kind kind;
        private final String raw;
        private final String norm;
        private final boolean negated;

        Token(Kind kind, String raw, String norm, boolean negated) {
            this.kind = kind; this.raw = raw; this.norm = norm; this.negated = negated;
        }

        public Kind kind()      { return kind; }
        /** Lowercased term as typed, separators intact (e.g. {@code minecraft:swords} for a {@code #tag}). */
        public String raw()     { return raw; }
        /** Lowercased term with separators stripped - what the matchers compare against. */
        public String norm()    { return norm; }
        public boolean negated(){ return negated; }
    }

    private static final Token[] NO_TOKENS = new Token[0];

    /** Parses a raw search box value into terms. Never null; empty when the box is blank. */
    public static java.util.List<Token> tokenize(String rawQuery) {
        return java.util.List.of(parse(rawQuery));
    }

    /** A parsed query, reusable across every entry of a list. */
    public static final class Query {
        private final String raw;
        private final Token[] tokens;

        private Query(String raw) {
            this.raw = raw;
            this.tokens = parse(raw);
        }

        /** True when the box is blank (or only punctuation) - every row passes. */
        public boolean isEmpty() { return tokens.length == 0; }

        public java.util.List<Token> tokens() { return java.util.List.of(tokens); }

        /**
         * @param id "namespace:path" shaped when possible (may be null)
         * @param displayName translated name (may be null)
         */
        public boolean matches(String id, String displayName) {
            for (Token t : tokens) {
                boolean hit = tokenMatches(id, displayName, t);
                if (hit == t.negated) return false;
            }
            return true;
        }
    }

    /**
     * Does {@code id}/{@code displayName} satisfy this single term? Ignores
     * {@link Token#negated()} - the caller decides what a hit means (see
     * {@link Query#matches}), which is what lets VirtualListWidget reuse
     * this for both its positive and its negative passes.
     */
    public static boolean tokenMatches(String id, String displayName, Token t) {
        if (t.kind == Kind.NAMESPACE) return namespaceContains(id, t.norm);
        return containsNormalized(id, t.norm) || containsNormalized(displayName, t.norm);
    }

    public static int scoreToken(String id, String displayName, Token t) {
        if (t.negated || t.norm.isEmpty()) return 0;
        String path = pathOf(id);
        if (equalsNormalized(path, t.norm))     return 1000;
        if (startsWithNormalized(path, t.norm)) return 600;
        if (startsWithNormalized(displayName, t.norm)) return 400;
        if (containsNormalized(path, t.norm))   return 200;
        return 100;
    }

    private static Token[] parse(String raw) {
        String q = raw == null ? "" : raw.trim();
        if (q.isEmpty()) return NO_TOKENS;

        java.util.List<Token> out = new java.util.ArrayList<>(4);
        int i = 0;
        int n = q.length();
        while (i < n) {
            while (i < n && q.charAt(i) == ' ') i++;
            if (i >= n) break;

            boolean negated = false;
            if (q.charAt(i) == '-' && i + 1 < n && q.charAt(i + 1) != ' ') { negated = true; i++; }

            Kind kind = Kind.TEXT;
            if (i < n && (q.charAt(i) == '@' || q.charAt(i) == '#')) {
                kind = q.charAt(i) == '@' ? Kind.NAMESPACE : Kind.TAG;
                i++;
            }

            StringBuilder rawSb  = new StringBuilder(16);
            if (i < n && q.charAt(i) == '"') {
                i++;
                while (i < n && q.charAt(i) != '"') rawSb.append(q.charAt(i++));
                if (i < n) i++;
            } else {
                while (i < n && q.charAt(i) != ' ') rawSb.append(q.charAt(i++));
            }

            String rawTerm = rawSb.toString().toLowerCase(java.util.Locale.ROOT);
            String norm    = normalizeAll(rawTerm);
            if (!norm.isEmpty()) out.add(new Token(kind, rawTerm, norm, negated));
        }
        return out.isEmpty() ? NO_TOKENS : out.toArray(new Token[0]);
    }

    private static String normalizeAll(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char k = normalize(s.charAt(i));
            if (k != 0) sb.append(k);
        }
        return sb.toString();
    }

    /**
     * ASCII lookup table for {@link #normalize}: separators map to 0,
     * letters to lowercase, everything else to itself. A table instead of a
     * chain of {@code if}s because this runs once per character of every
     * id/name of the list on every keystroke - the branchy version measured
     * ~3x slower than the JDK's own {@code contains} on the miss path,
     * which would have made this rewrite a perf REGRESSION for the exact
     * case that dominates typing (a partially typed word matching nothing
     * yet).
     */
    private static final char[] NORM_ASCII = new char[128];
    static {
        for (char c = 0; c < 128; c++) NORM_ASCII[c] = (c >= 'A' && c <= 'Z') ? (char) (c + 32) : c;
        for (char c : new char[]{'_', '-', '.', '/', ':', ' ', '\'', ',', '(', ')'}) NORM_ASCII[c] = 0;
    }

    private static char normalize(char c) {
        return c < 128 ? NORM_ASCII[c] : Character.toLowerCase(c);
    }

    /**
     * Case- and separator-insensitive {@code contains}, without allocating
     * <p>
     * Two passes on purpose: {@link String#regionMatches(boolean, int, String, int, int)}
     * first (JDK-fast, covers every plain hit like {@code sword} in
     * {@code iron_sword}), and only if that misses AND the haystack
     * actually carries a separator does it fall back to the char-by-char
     * normalized walk that lets {@code ironsword} match {@code iron_sword}.
     * {@code needle} must already be normalized and non-empty.
     */
    public static boolean containsNormalized(String haystack, String needle) {
        if (haystack == null || haystack.isEmpty() || needle.isEmpty()) return false;
        int hn = haystack.length(), nn = needle.length();

        char first = needle.charAt(0);
        char firstUpper = Character.toUpperCase(first);
        int last = hn - nn;
        for (int i = 0; i <= last; i++) {
            char h = haystack.charAt(i);
            if (h != first && h != firstUpper) continue;
            if (nn == 1 || haystack.regionMatches(true, i + 1, needle, 1, nn - 1)) return true;
        }
        if (nn >= hn) return false;

        boolean hasSeparator = false;
        for (int i = 0; i < hn; i++) {
            if (normalize(haystack.charAt(i)) == 0) { hasSeparator = true; break; }
        }
        if (!hasSeparator) return false;

        for (int start = 0; start <= hn - nn; start++) {
            char c0 = normalize(haystack.charAt(start));
            if (c0 != first) continue;
            int j = 1, i = start + 1;
            while (i < hn && j < nn) {
                char c = normalize(haystack.charAt(i++));
                if (c == 0) continue;
                if (c != needle.charAt(j)) break;
                j++;
            }
            if (j == nn) return true;
        }
        return false;
    }

    public static boolean startsWithNormalized(String haystack, String needle) {
        if (haystack == null || needle.isEmpty()) return false;
        int j = 0, i = 0, hn = haystack.length(), nn = needle.length();
        while (i < hn && j < nn) {
            char c = normalize(haystack.charAt(i++));
            if (c == 0) continue;
            if (c != needle.charAt(j)) return false;
            j++;
        }
        return j == nn;
    }

    public static boolean equalsNormalized(String haystack, String needle) {
        if (haystack == null) return false;
        int j = 0, hn = haystack.length(), nn = needle.length();
        for (int i = 0; i < hn; i++) {
            char c = normalize(haystack.charAt(i));
            if (c == 0) continue;
            if (j >= nn || c != needle.charAt(j)) return false;
            j++;
        }
        return j == nn;
    }

    private static boolean namespaceContains(String id, String needle) {
        if (id == null) return false;
        int colon = id.indexOf(':');
        String ns = colon >= 0 ? id.substring(0, colon) : id;
        return containsNormalized(ns, needle);
    }

    private static String pathOf(String id) {
        if (id == null) return null;
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

}
