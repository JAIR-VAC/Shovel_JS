package com.kve.common.kubejs;

import com.google.gson.*;
import com.kve.CraftEditorMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class FaultTracker {
    private FaultTracker() {}

    private static final Path DIR = Paths.get("kubejs", "config", "shovel_js_linemaps");
    private static final Gson GSON = new GsonBuilder().create();

    public static final class Recorder {
        private final String fileName;
        private final List<int[]> boundaries = new ArrayList<>();
        private final List<String> ids = new ArrayList<>();

        public Recorder(String fileName) { this.fileName = fileName; }

        /** Call once per entry, with the line number range its own generated
         *  block occupies in the file (1-indexed, inclusive on both ends). */
        public void addRange(int startLine, int endLine, String entryId) {
            boundaries.add(new int[]{startLine, endLine});
            ids.add(entryId);
        }

        public void save() {
            try {
                Files.createDirectories(DIR);
                JsonArray arr = new JsonArray();
                for (int i = 0; i < boundaries.size(); i++) {
                    JsonObject o = new JsonObject();
                    o.addProperty("start", boundaries.get(i)[0]);
                    o.addProperty("end",   boundaries.get(i)[1]);
                    o.addProperty("id",    ids.get(i));
                    arr.add(o);
                }
                Files.writeString(DIR.resolve(fileName + ".map.json"), GSON.toJson(arr),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException ex) {
                CraftEditorMod.LOGGER.error("[Shovel JS] Failed to save line map for {}: {}", fileName, ex.getMessage());
            }
        }
    }

    /** Counts how many '\n' characters are in `sb` right now - i.e. the
     *  1-indexed line number that the NEXT character appended will land on. */
    public static int currentLine(CharSequence sb) {
        int n = 1;
        for (int i = 0; i < sb.length(); i++) if (sb.charAt(i) == '\n') n++;
        return n;
    }

    public static Optional<String> findEntryForLine(String fileName, int lineNumber) {
        Path p = DIR.resolve(fileName + ".map.json");
        if (!Files.exists(p)) return Optional.empty();
        try {
            String json = Files.readString(p, StandardCharsets.UTF_8);
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                int start = o.get("start").getAsInt(), end = o.get("end").getAsInt();
                if (lineNumber >= start && lineNumber <= end) return Optional.of(o.get("id").getAsString());
            }
        } catch (Exception ignored) {
            // A missing/corrupt map just means we can't attribute this one -
            // it's a nice-to-have, never something that should crash the watcher.
        }
        return Optional.empty();
    }
}
