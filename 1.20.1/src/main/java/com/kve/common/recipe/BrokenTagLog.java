package com.kve.common.recipe;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BrokenTagLog {
    private BrokenTagLog() {}

    private static final Pattern LINE = Pattern.compile("Couldn't load tag (\\S+) as it is missing following references: (.*)$");
    private static final long RECHECK_MS = 60_000;

    private static Map<String, String> broken = Map.of();
    private static long checkedAt = 0;
    private static long lastSize = -1;

    /** Referencias faltantes (y el jar que las agrega) si el tag no cargo; null si el log no dice nada. */
    public static synchronized String reason(String tagId) {
        if (tagId == null || tagId.isBlank()) return null;
        String id = tagId.startsWith("#") ? tagId.substring(1) : tagId;
        refreshIfStale();
        return broken.get(id);
    }

    private static void refreshIfStale() {
        long now = System.currentTimeMillis();
        if (now - checkedAt < RECHECK_MS && checkedAt != 0) return;
        checkedAt = now;
        Path log = Paths.get("logs", "latest.log");
        try {
            if (!Files.isRegularFile(log)) return;
            long size = Files.size(log);
            if (size == lastSize) return;
            lastSize = size;
            Map<String, String> found = new HashMap<>();
            try (BufferedReader r = Files.newBufferedReader(log, StandardCharsets.UTF_8)) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!line.contains("Couldn't load tag")) continue;
                    Matcher m = LINE.matcher(line);
                    if (!m.find()) continue;
                    String refs = m.group(2).trim();
                    if (refs.length() > 160) refs = refs.substring(0, 157) + "...";
                    found.putIfAbsent(m.group(1), refs);
                }
            }
            broken = found;
        } catch (Exception ignored) {
            // log ilegible (codificacion rara, bloqueado): sin diagnostico, nunca un error
        }
    }
}
