package com.kve.common.search;

import com.kve.common.customitem.CustomBlockDraft;
import com.kve.common.customitem.CustomBlockState;
import com.kve.common.customitem.CustomItemDraft;
import com.kve.common.customitem.CustomItemState;
import com.kve.common.kubejs.KveScriptPaths;
import com.kve.common.kubejs.TagExporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Ids que el propio proyecto declara y que por eso NO estan (todavia) en los
 * registros del juego: items/bloques creados con el creador de items, lo que
 * registra cualquier {@code event.create('...')} de startup_scripts (existe
 * recien despues de reiniciar), ids de receta ({@code .id('crafteditor:...')})
 * y tags creados en el editor de tags. El validador de Archivos y el
 * diagnostico de ids rotos los marcaban como "desconocidos".
 */
public final class DeclaredIds {
    private DeclaredIds() {}

    private static final Set<String> OWN_NAMESPACES = Set.of("shovel_js", "kubejs");

    private static final Pattern CREATE = Pattern.compile("\\.create\\(\\s*['\"]([a-z0-9_.\\-]+(?::[a-z0-9_./\\-]+)?)['\"]");
    private static final Pattern RECIPE_ID = Pattern.compile("\\.id\\(\\s*['\"]([a-z0-9_.\\-]+:[a-z0-9_./\\-]+)['\"]");

    public static boolean isOwnNamespace(String id) {
        if (id == null) return false;
        String s = id.startsWith("#") ? id.substring(1) : id;
        int c = s.indexOf(':');
        return c > 0 && OWN_NAMESPACES.contains(s.substring(0, c));
    }

    /** Ids declarados dentro de un script (sin namespace = {@code kubejs:}, como hace KubeJS). */
    public static Set<String> scanScript(String js) {
        Set<String> out = new HashSet<>();
        if (js == null || js.isEmpty()) return out;
        Matcher m = CREATE.matcher(js);
        while (m.find()) out.add(normalize(m.group(1)));
        m = RECIPE_ID.matcher(js);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /**
     * true si el id esta en {@code localScript} (el archivo que se esta
     * editando) o en el resto del proyecto (ver {@link #projectIds()}).
     */
    public static boolean isDeclared(String id, Set<String> localScript) {
        if (id == null || id.isEmpty()) return false;
        String s = id.startsWith("#") ? id.substring(1) : id;
        if (isOwnNamespace(s)) return true;
        if (localScript != null && localScript.contains(s)) return true;
        return projectIds().contains(s);
    }

    public static boolean isDeclared(String id) { return isDeclared(id, null); }

    private static final long TTL_MS = 5000;
    private static Set<String> cache = Collections.emptySet();
    private static long cacheAt = 0;

    public static synchronized Set<String> projectIds() {
        long now = System.currentTimeMillis();
        if (now - cacheAt < TTL_MS) return cache;
        Set<String> out = new HashSet<>();
        try {
            CustomItemState.loadIfNeeded();
            for (CustomItemDraft d : CustomItemState.getDrafts()) addDraft(out, d.path());
        } catch (Throwable ignored) { }
        try {
            CustomBlockState.loadIfNeeded();
            for (CustomBlockDraft d : CustomBlockState.getDrafts()) addDraft(out, d.path());
        } catch (Throwable ignored) { }
        try {
            for (TagExporter.TagEntry e : TagExporter.getEntries()) {
                if (e.tagId != null && !e.tagId.isEmpty()) out.add(e.tagId.startsWith("#") ? e.tagId.substring(1) : e.tagId);
            }
        } catch (Throwable ignored) { }
        scanTree(out, KveScriptPaths.STARTUP_ROOT.getParent());
        scanTree(out, KveScriptPaths.SERVER_ROOT.getParent());
        cache = Collections.unmodifiableSet(out);
        cacheAt = now;
        return cache;
    }

    /** Fuerza el recalculo en la proxima consulta (despues de guardar un item/tag). */
    public static synchronized void invalidate() { cacheAt = 0; }

    private static void addDraft(Set<String> out, String path) {
        if (path == null || path.isEmpty()) return;
        out.add("kubejs:" + path);
        out.add("shovel_js:" + path);
    }

    private static void scanTree(Set<String> out, Path root) {
        if (root == null || !Files.isDirectory(root)) return;
        try (Stream<Path> files = Files.walk(root, 8)) {
            files.filter(p -> p.toString().endsWith(".js")).limit(2000).forEach(p -> {
                try {
                    if (Files.size(p) > 2_000_000) return;
                    out.addAll(scanScript(Files.readString(p)));
                } catch (IOException | RuntimeException ignored) { }
            });
        } catch (IOException | RuntimeException ignored) { }
    }

    private static String normalize(String raw) {
        String s = raw.toLowerCase(Locale.ROOT);
        return s.indexOf(':') >= 0 ? s : "kubejs:" + s;
    }
}
