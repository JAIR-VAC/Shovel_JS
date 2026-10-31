package com.kve.common.persist;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class PackSnapshotManager {

    private PackSnapshotManager() {}

    public static final String DIR_NAME = "shovel_js_snapshots";
    public static final int MAX_SNAPSHOTS = 30;

    static final List<String> DIRS = List.of(
            "kubejs/server_scripts/shovel_js", "kubejs/startup_scripts/shovel_js", "kubejs/client_scripts/shovel_js",
            "kubejs/data/shovel_js", "kubejs/assets/shovel_js", "config/shovel_js");

    static final List<String> CONFIG_DIRS = List.of("kubejs/config", "config");

    public record Info(String fileName, long sizeBytes, long modifiedMs) {}

    static boolean isKveConfigName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.equals("shovel_js_backups") || n.equals("shovel_js_linemaps") || n.endsWith(".tmp")) return false;
        return n.startsWith("shovel_js") || n.startsWith("shovel_js_state");
    }

    /**
     * Los nombres visibles (reglas del pack) se escriben en
     * kubejs/assets/&lt;mod&gt;/lang/&lt;idioma&gt;.json, fuera de kubejs/assets/kve.
     * Esos archivos de idioma tambien entran (se pisan al restaurar; nunca se
     * borran otros assets del pack).
     */
    static boolean isLangFile(String e) {
        return e.matches("kubejs/assets/[a-z0-9_.-]+/lang/[a-z0-9_]+\\.json");
    }

    static boolean isAllowedEntry(String entry) {
        String e = entry.replace('\\', '/');
        if (e.startsWith("/") || e.contains("..") || e.contains(":")) return false;
        for (String d : DIRS) if (e.startsWith(d + "/")) return true;
        if (isLangFile(e)) return true;
        for (String c : CONFIG_DIRS) {
            if (e.startsWith(c + "/")) {
                String rest = e.substring(c.length() + 1);
                String first = rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest;
                if (!DIRS.contains(c + "/" + first) && isKveConfigName(first)) return true;
            }
        }
        return false;
    }

    public static Path dir(Path gameDir) { return gameDir.resolve(DIR_NAME); }

    /** Archivos (rutas relativas con "/") que entrarian en una instantanea ahora mismo. */
    public static List<String> collect(Path gameDir) throws IOException {
        List<String> out = new ArrayList<>();
        for (String d : DIRS) addTree(gameDir, gameDir.resolve(d), out);
        for (String c : CONFIG_DIRS) {
            Path cd = gameDir.resolve(c);
            if (!Files.isDirectory(cd)) continue;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(cd)) {
                for (Path p : ds) {
                    String rel = rel(gameDir, p);
                    if (DIRS.contains(rel) || !isKveConfigName(p.getFileName().toString())) continue;
                    if (Files.isDirectory(p)) addTree(gameDir, p, out);
                    else out.add(rel);
                }
            }
        }
        Path assets = gameDir.resolve("kubejs/assets");
        if (Files.isDirectory(assets)) {
            try (DirectoryStream<Path> ns = Files.newDirectoryStream(assets)) {
                for (Path n : ns) {
                    Path lang = n.resolve("lang");
                    if (!Files.isDirectory(lang) || rel(gameDir, n).equals("kubejs/assets/shovel_js")) continue;
                    try (DirectoryStream<Path> files = Files.newDirectoryStream(lang, "*.json")) {
                        for (Path f : files) out.add(rel(gameDir, f));
                    }
                }
            }
        }
        out.removeIf(r -> !isAllowedEntry(r));
        out.sort(String::compareTo);
        return out;
    }

    /** Diferencias entre una instantanea y el estado actual (rutas relativas). */
    public record Diff(List<String> added, List<String> removed, List<String> changed) {
        public boolean isEmpty() { return added.isEmpty() && removed.isEmpty() && changed.isEmpty(); }
    }

    public static Diff compare(Path gameDir, String fileName) throws IOException {
        Path zipPath = resolveSnapshot(gameDir, fileName);
        List<String> now = collect(gameDir);
        Set<String> nowSet = new HashSet<>(now);
        Set<String> inZip = new HashSet<>();
        List<String> added = new ArrayList<>(), removed = new ArrayList<>(), changed = new ArrayList<>();
        try (ZipFile zf = new ZipFile(zipPath.toFile())) {
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory() || !isAllowedEntry(e.getName())) continue;
                inZip.add(e.getName());
                if (!nowSet.contains(e.getName())) { removed.add(e.getName()); continue; }
                byte[] old;
                try (InputStream in = zf.getInputStream(e)) { old = in.readAllBytes(); }
                if (!Arrays.equals(old, Files.readAllBytes(gameDir.resolve(e.getName())))) changed.add(e.getName());
            }
        }
        for (String f : now) if (!inZip.contains(f)) added.add(f);
        added.sort(String::compareTo);
        removed.sort(String::compareTo);
        changed.sort(String::compareTo);
        return new Diff(added, removed, changed);
    }

    /**
     * Diferencias entre DOS puntos cualesquiera - dos instantaneas, o
     * una instantanea y el estado actual ({@code newer} = null). {@link #compare}
     * solo comparaba contra el estado actual.
     */
    public static Diff compareSnapshots(Path gameDir, String older, String newer) throws IOException {
        Map<String, byte[]> a = contents(gameDir, older);
        Map<String, byte[]> b = contents(gameDir, newer);
        List<String> added = new ArrayList<>(), removed = new ArrayList<>(), changed = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : b.entrySet()) {
            byte[] old = a.get(e.getKey());
            if (old == null) added.add(e.getKey());
            else if (!Arrays.equals(old, e.getValue())) changed.add(e.getKey());
        }
        for (String k : a.keySet()) if (!b.containsKey(k)) removed.add(k);
        added.sort(String::compareTo);
        removed.sort(String::compareTo);
        changed.sort(String::compareTo);
        return new Diff(added, removed, changed);
    }

    /** Texto de {@code entry} en una instantanea (o en el estado actual si {@code snapshot} es null); null si no esta. */
    public static String readText(Path gameDir, String snapshot, String entry) throws IOException {
        if (!isAllowedEntry(entry)) return null;
        if (snapshot == null) {
            Path f = gameDir.resolve(entry);
            return Files.isRegularFile(f) ? Files.readString(f, java.nio.charset.StandardCharsets.UTF_8) : null;
        }
        try (ZipFile zf = new ZipFile(resolveSnapshot(gameDir, snapshot).toFile())) {
            ZipEntry e = zf.getEntry(entry);
            if (e == null || e.isDirectory()) return null;
            try (InputStream in = zf.getInputStream(e)) { return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
        }
    }

    private static Map<String, byte[]> contents(Path gameDir, String snapshot) throws IOException {
        Map<String, byte[]> out = new java.util.TreeMap<>();
        if (snapshot == null) {
            for (String f : collect(gameDir)) out.put(f, Files.readAllBytes(gameDir.resolve(f)));
            return out;
        }
        try (ZipFile zf = new ZipFile(resolveSnapshot(gameDir, snapshot).toFile())) {
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory() || !isAllowedEntry(e.getName())) continue;
                try (InputStream in = zf.getInputStream(e)) { out.put(e.getName(), in.readAllBytes()); }
            }
        }
        return out;
    }

    private static void addTree(Path gameDir, Path root, List<String> out) throws IOException {
        if (!Files.isDirectory(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!file.getFileName().toString().endsWith(".tmp")) out.add(rel(gameDir, file));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String rel(Path gameDir, Path p) {
        return gameDir.relativize(p).toString().replace('\\', '/');
    }

    static String sanitizeLabel(String label) {
        if (label == null) return "";
        String s = label.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-").replaceAll("-{2,}", "-");
        s = s.replaceAll("^-|-$", "");
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    /** Crea una instantanea y devuelve el zip. Borra las mas viejas por encima de {@link #MAX_SNAPSHOTS}. */
    public static Path create(Path gameDir, String label) throws IOException {
        return create(gameDir, label, null);
    }

    static Path create(Path gameDir, String label, String keep) throws IOException {
        List<String> files = collect(gameDir);
        Path dir = dir(gameDir);
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String lab = sanitizeLabel(label);
        Path zip = dir.resolve("shovel_js-" + stamp + (lab.isEmpty() ? "" : "-" + lab) + ".zip");
        int n = 1;
        while (Files.exists(zip)) zip = dir.resolve("shovel_js-" + stamp + "-" + (++n) + (lab.isEmpty() ? "" : "-" + lab) + ".zip");
        Path tmp = zip.resolveSibling(zip.getFileName() + ".tmp");
        try (OutputStream os = Files.newOutputStream(tmp); ZipOutputStream zos = new ZipOutputStream(os)) {
            for (String f : files) {
                zos.putNextEntry(new ZipEntry(f));
                Files.copy(gameDir.resolve(f), zos);
                zos.closeEntry();
            }
        }
        Files.move(tmp, zip, StandardCopyOption.REPLACE_EXISTING);
        prune(gameDir, keep);
        return zip;
    }

    /** Instantaneas existentes, la mas nueva primero. */
    public static List<Info> list(Path gameDir) {
        List<Info> out = new ArrayList<>();
        Path dir = dir(gameDir);
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().endsWith(".zip")).forEach(p -> {
                try { out.add(new Info(p.getFileName().toString(), Files.size(p), Files.getLastModifiedTime(p).toMillis())); }
                catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
        out.sort(Comparator.comparingLong(Info::modifiedMs).reversed().thenComparing(Info::fileName));
        return out;
    }

    /**
     * Vuelve UN archivo del pack a como estaba en la instantanea.
     * Antes de tocar nada crea una instantanea de seguridad del estado actual,
     * asi cualquier cambio se puede deshacer. Solo archivos que maneja Shovel JS.
     * Devuelve false si el archivo no existe en la instantanea (no se toca nada).
     */
    public static boolean restoreFile(Path gameDir, String snapshot, String entry) throws IOException {
        if (!isAllowedEntry(entry)) throw new IOException("archivo fuera de lo que maneja Shovel JS: " + entry);
        String old = readText(gameDir, snapshot, entry);
        if (old == null) return false;
        create(gameDir, "antes-de-restaurar-archivo", null);
        Path target = gameDir.resolve(entry).normalize();
        if (!target.startsWith(gameDir.normalize())) throw new IOException("ruta fuera del juego: " + entry);
        Files.createDirectories(target.getParent());
        Files.writeString(target, old, java.nio.charset.StandardCharsets.UTF_8);
        return true;
    }

    private static void prune(Path gameDir, String keep) throws IOException {
        List<Info> all = list(gameDir);
        for (int i = MAX_SNAPSHOTS; i < all.size(); i++) {
            String name = all.get(i).fileName();
            if (name.equals(keep)) continue;
            Files.deleteIfExists(dir(gameDir).resolve(name));
        }
    }

    private static Path resolveSnapshot(Path gameDir, String fileName) throws IOException {
        if (fileName == null || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..") || !fileName.endsWith(".zip")) {
            throw new IOException("nombre de instantanea invalido: " + fileName);
        }
        Path p = dir(gameDir).resolve(fileName);
        if (!Files.isRegularFile(p)) throw new IOException("no existe: " + fileName);
        return p;
    }

    /**
     * Restaura {@code fileName()}: primero guarda el estado actual como
     * "antes-de-restaurar", vacia las carpetas de KVE y extrae el zip.
     * Devuelve cuantos archivos se restauraron.
     */
    public static int restore(Path gameDir, String fileName) throws IOException {
        Path zipPath = resolveSnapshot(gameDir, fileName);
        create(gameDir, "antes-de-restaurar", fileName);
        if (!Files.isRegularFile(zipPath)) throw new IOException("no existe: " + fileName);
        for (String d : DIRS) deleteTree(gameDir.resolve(d));
        int n = 0;
        try (ZipFile zf = new ZipFile(zipPath.toFile())) {
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory() || !isAllowedEntry(e.getName())) continue;
                Path target = gameDir.resolve(e.getName()).normalize();
                if (!target.startsWith(gameDir.normalize())) continue;
                Files.createDirectories(target.getParent());
                try (InputStream in = zf.getInputStream(e)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                n++;
            }
        }
        return n;
    }

    public static void delete(Path gameDir, String fileName) throws IOException {
        Files.deleteIfExists(resolveSnapshot(gameDir, fileName));
    }

    /** Cantidad de archivos dentro de una instantanea (para mostrarla antes de restaurar). */
    public static int countEntries(Path gameDir, String fileName) {
        try (ZipFile zf = new ZipFile(resolveSnapshot(gameDir, fileName).toFile())) {
            return zf.size();
        } catch (IOException e) {
            return -1;
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> s = Files.walk(root)) {
            List<Path> all = s.sorted(Comparator.reverseOrder()).toList();
            for (Path p : all) Files.deleteIfExists(p);
        }
    }

    public static Path gameDir() { return Paths.get("").toAbsolutePath(); }
}
