package com.kve.common.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class IdPattern {
    private IdPattern() {}

    public static List<String> split(String patterns) {
        List<String> out = new ArrayList<>();
        if (patterns == null) return out;
        for (String p : patterns.split("[\\s,]+")) if (!p.isBlank()) out.add(p.trim().toLowerCase(Locale.ROOT));
        return out;
    }

    /** true si {@code id} cumple UN patron (sin '#' ni '!'). */
    public static boolean matches(String id, String pattern) {
        String target = pattern.indexOf(':') >= 0 ? id : id.substring(id.indexOf(':') + 1);
        return toRegex(pattern).matcher(target.toLowerCase(Locale.ROOT)).matches();
    }

    /**
     * Ids de {@code universe} seleccionados por {@code patterns}, en el orden
     * de {@code universe}. {@code tagMembers} resuelve "#tag" a sus ids (puede
     * devolver vacio si no conoce el tag).
     */
    public static List<String> select(Collection<String> universe, String patterns, Function<String, Collection<String>> tagMembers) {
        List<String> include = new ArrayList<>();
        List<String> exclude = new ArrayList<>();
        Set<String> fromTags = new LinkedHashSet<>();
        for (String p : split(patterns)) {
            if (p.startsWith("!")) exclude.add(p.substring(1));
            else if (p.startsWith("#")) fromTags.addAll(tagMembers.apply(p.substring(1)));
            else include.add(p);
        }
        List<String> out = new ArrayList<>();
        for (String id : universe) {
            boolean in = fromTags.contains(id) || include.stream().anyMatch(p -> matches(id, p));
            if (in && exclude.stream().noneMatch(p -> matches(id, p))) out.add(id);
        }
        return out;
    }

    private static Pattern toRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                default -> sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(sb.toString());
    }
}
