package com.kve.common.packtools;

import com.google.gson.JsonParser;
import com.kve.common.kubejs.FaultTracker;
import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.kubejs.ScriptWriteGuard;
import com.kve.common.recipe.RecipeData;
import com.kve.common.search.PackDiagnostics;
import com.kve.common.worldgen.WorldGenEntry;
import com.kve.common.worldgen.WorldGenExporter;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class PackCheck {

    private PackCheck() {}

    public enum Severity { ERROR, WARN }

    /** {@code area}: scripts / recipes / data / kubejs / ids / worldgen. {@code where}: archivo o entrada. */
    public record Issue(Severity severity, String area, String where, String key, List<String> args) {}

    private static final String[] SCOPES = {"startup", "server", "client"};
    private static final int LOG_TAIL_BYTES = 512 * 1024;
    private static final int MAX_LOG_ERRORS = 40;
    private static final Pattern JS_LINE = Pattern.compile("([\\w.\\-]+\\.js)[:#](\\d+)");
    private static final Pattern LOG_ERROR = Pattern.compile("\\[(?:ERROR|ERR\\s*)]|\\bERROR\\b|Exception");

    public static List<Issue> runAll(Path gameDir) {
        List<Issue> out = new ArrayList<>(scanState());
        out.addAll(scanFiles(gameDir));
        out.sort(Comparator.comparing(Issue::severity));
        return out;
    }

    /** Solo disco (testeable sin registros): scripts generados, datapack y logs de KubeJS. */
    public static List<Issue> scanFiles(Path gameDir) {
        List<Issue> out = new ArrayList<>();
        for (String scope : SCOPES) scanScripts(gameDir, gameDir.resolve("kubejs").resolve(scope + "_scripts").resolve("shovel_js"), out);
        scanDatapack(gameDir, gameDir.resolve("kubejs").resolve("data"), out);
        for (String scope : SCOPES) scanLog(gameDir.resolve("logs").resolve("kubejs").resolve(scope + ".log"), scope, out);
        return out;
    }

    private static void scanScripts(Path gameDir, Path dir, List<Issue> out) {
        for (Path f : walk(dir, ".js")) {
            String rel = rel(gameDir, f);
            String text;
            try { text = Files.readString(f, StandardCharsets.UTF_8); } catch (IOException e) { continue; }
            if (!ScriptWriteGuard.looksWellFormed(text)) {
                out.add(new Issue(Severity.ERROR, "scripts", rel, "gui.kve.check.script_syntax", List.of()));
            }
            for (String line : text.split("\n")) {
                int i = line.indexOf("NO EXPORTADO:");
                if (i >= 0) {
                    out.add(new Issue(Severity.WARN, "recipes", rel, "gui.kve.check.not_exported",
                            List.of(line.substring(i + "NO EXPORTADO:".length()).replace("=", "").trim())));
                }
            }
        }
    }

    private static void scanDatapack(Path gameDir, Path dir, List<Issue> out) {
        for (Path f : walk(dir, ".json")) {
            try {
                JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                out.add(new Issue(Severity.ERROR, "data", rel(gameDir, f), "gui.kve.check.bad_json",
                        List.of(msg.length() > 160 ? msg.substring(0, 160) + "..." : msg)));
            }
        }
    }

    static void scanLog(Path log, String scope, List<Issue> out) {
        if (!Files.isRegularFile(log)) return;
        Set<String> seen = new LinkedHashSet<>();
        for (String line : tail(log)) {
            if (!LOG_ERROR.matcher(line).find() || line.contains("[WARN")) continue;
            String clean = line.trim();
            if (clean.isEmpty() || !seen.add(clean)) continue;
            String where = scope;
            Matcher m = JS_LINE.matcher(clean);
            if (m.find()) {
                where = m.group(1) + ":" + m.group(2);
                try {
                    Optional<String> entry = FaultTracker.findEntryForLine(m.group(1), Integer.parseInt(m.group(2)));
                    if (entry.isPresent()) where = where + " (" + entry.get() + ")";
                } catch (NumberFormatException ignored) {}
            }
            out.add(new Issue(Severity.ERROR, "kubejs", where, "gui.kve.check.kubejs_error",
                    List.of(clean.length() > 200 ? clean.substring(0, 200) + "..." : clean)));
            if (seen.size() >= MAX_LOG_ERRORS) break;
        }
    }

    private static List<String> tail(Path f) {
        try (RandomAccessFile raf = new RandomAccessFile(f.toFile(), "r")) {
            long len = raf.length();
            long start = Math.max(0, len - LOG_TAIL_BYTES);
            raf.seek(start);
            byte[] buf = new byte[(int) (len - start)];
            raf.readFully(buf);
            String s = new String(buf, StandardCharsets.UTF_8);
            if (start > 0) s = s.substring(Math.max(0, s.indexOf('\n') + 1));
            return List.of(s.split("\r?\n"));
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Lo que vive en memoria del editor: ids rotos, recetas y reglas de world gen invalidas. */
    public static List<Issue> scanState() {
        List<Issue> out = new ArrayList<>();
        try {
            for (PackDiagnostics.Problem p : PackDiagnostics.scanProject()) {
                var r = p.ref();
                out.add(new Issue(p.modMissing() ? Severity.WARN : Severity.ERROR, "ids", r.entryId(),
                        p.modMissing() ? "gui.kve.check.mod_missing" : "gui.kve.check.broken_id", List.of(r.field(), r.id())));
            }
        } catch (RuntimeException ignored) {
        }
        for (RecipeData r : KubeJSExporter.getAddedRecipes()) {
            if (!r.enabled) continue;
            List<String> errs = KubeJSExporter.validateRecipe(r);
            if (!errs.isEmpty()) out.add(new Issue(Severity.WARN, "recipes", r.recipeId, "gui.kve.check.recipe_invalid", List.of(errs.get(0))));
        }
        for (WorldGenEntry e : WorldGenExporter.getEntries()) {
            if (!e.enabled) continue;
            List<String> errs = e.validate();
            if (!errs.isEmpty()) out.add(new Issue(Severity.WARN, "worldgen", e.id, "gui.kve.check.worldgen_invalid", List.of(errs.get(0))));
        }
        return out;
    }

    private static List<Path> walk(Path dir, String ext) {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(ext)).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static String rel(Path gameDir, Path p) {
        return gameDir.relativize(p).toString().replace('\\', '/');
    }
}
