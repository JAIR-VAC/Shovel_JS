package com.kve.common.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

public final class KvePathGuard {

    private KvePathGuard() {}

    private static final Pattern DRIVE_PREFIX = Pattern.compile("^[A-Za-z]:.*");

    private static final Pattern WINDOWS_RESERVED = Pattern.compile(
            "^(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?$", Pattern.CASE_INSENSITIVE);

    /**
     * true si {@code s} sirve como UN solo componente de ruta: no vacio, ni
     * "." ni "..", sin separadores ('/' o '\\'), sin ':' (unidad/ADS de
     * NTFS), sin NUL/control, sin espacio o punto al final (Windows los
     * descarta en silencio, y "a." == "a" ya cruza la validacion de
     * "a..") y sin nombre reservado de dispositivo.
     */
    public static boolean isSafeSegment(String s) {
        if (s == null || s.isEmpty()) return false;
        if (s.equals(".") || s.equals("..")) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '/' || c == '\\' || c == ':' || c < 0x20 || c == 0x7F) return false;
        }
        char last = s.charAt(s.length() - 1);
        if (last == '.' || last == ' ') return false;
        if (s.charAt(0) == ' ') return false;
        return !WINDOWS_RESERVED.matcher(s).matches();
    }

    public static boolean isSafeRelativePath(String p) {
        if (p == null || p.isEmpty()) return false;
        if (p.charAt(0) == '/' || DRIVE_PREFIX.matcher(p).matches()) return false;
        for (String seg : p.split("/", -1)) {
            if (!isSafeSegment(seg)) return false;
        }
        return true;
    }

    /** Un id {@code namespace:path} (ya separado) es seguro si el namespace es UN segmento seguro y el path es una ruta relativa segura. */
    public static boolean isSafeResourceId(String namespace, String path) {
        return isSafeSegment(namespace) && isSafeRelativePath(path);
    }

    public static Path resolveContained(Path root, String... parts) {
        if (root == null) throw new IllegalArgumentException("root nulo");
        Path resolved = root;
        for (String part : parts) {
            if (!isSafeRelativePath(part)) {
                throw new IllegalArgumentException("Segmento de ruta invalido: '" + part + "'");
            }
            try {
                resolved = resolved.resolve(part);
            } catch (InvalidPathException ex) {
                throw new IllegalArgumentException("Segmento de ruta invalido: '" + part + "'");
            }
        }
        if (!isContained(root, resolved)) {
            throw new IllegalArgumentException("La ruta escapa de la carpeta permitida: '" + String.join("/", parts) + "'");
        }
        return resolved;
    }

    public static Path resolveResource(Path root, String namespace, String path, String suffix, String... middle) {
        if (!isSafeSegment(namespace)) {
            throw new IllegalArgumentException("Namespace invalido: '" + namespace + "'");
        }
        if (!isSafeRelativePath(path)) {
            throw new IllegalArgumentException("Path invalido: '" + path + "'");
        }
        Path resolved = root.resolve(namespace);
        for (String m : middle) resolved = resolved.resolve(m);
        try {
            resolved = resolved.resolve(path + (suffix == null ? "" : suffix));
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("Path invalido: '" + path + "'");
        }
        if (!isContained(root, resolved)) {
            throw new IllegalArgumentException("La ruta escapa de la carpeta permitida: '" + namespace + ":" + path + "'");
        }
        return resolved;
    }

    /**
     * true si {@code candidate} esta dentro de {@code root} (o es
     * {@code root}). Dos capas:
     * <ol>
     *   <li><b>Lexica</b>: ambas rutas a absoluto + normalize; el candidato
     *       debe {@code startsWith(root)}. Frena "..", rutas absolutas, etc.</li>
     *   <li><b>Fisica</b> (symlinks): el ancestro MAS PROFUNDO del candidato
     *       que ya exista en disco se resuelve con {@code toRealPath()} y
     *       debe seguir dentro del {@code toRealPath()} de root. Frena un
     *       enlace simbolico/junction dentro de kubejs/ que apunte afuera.
     *       Si {@code root} todavia no existe, esta capa se omite (no hay
     *       nada dentro que pueda apuntar afuera).</li>
     * </ol>
     */
    public static boolean isContained(Path root, Path candidate) {
        if (root == null || candidate == null) return false;
        try {
            Path absRoot = root.toAbsolutePath().normalize();
            Path absCand = candidate.toAbsolutePath().normalize();
            if (!absCand.startsWith(absRoot)) return false;

            if (!Files.exists(absRoot)) return true;
            Path realRoot = absRoot.toRealPath();
            Path probe = absCand;
            while (probe != null && !Files.exists(probe, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                probe = probe.getParent();
            }
            if (probe == null) return true;
            Path realProbe = probe.toRealPath();
            return realProbe.startsWith(realRoot);
        } catch (IOException | InvalidPathException | SecurityException ex) {
            return false;
        }
    }

    /**
     * Borra {@code recordedPath} SOLO si, una vez resuelto contra el cwd del
     * juego, cae dentro de {@code root} y es un archivo regular (nunca un
     * directorio, nunca un symlink a otro lado). Reemplaza al viejo
     * {@code Files.deleteIfExists(Paths.get(old))} de los cuatro
     * exportadores basados en manifest.
     *
     * @return true si se borro algo; false si la ruta fue rechazada, no
     *         existia o no era un archivo regular. Un rechazo NO lanza
     *         excepcion: un manifest hostil no debe romper el Aplicar del
     *         resto de entradas (se loguea desde el llamador si quiere).
     */
    public static boolean deleteRecordedFile(Path root, String recordedPath) throws IOException {
        if (recordedPath == null || recordedPath.isBlank()) return false;
        Path candidate;
        try {
            candidate = java.nio.file.Paths.get(recordedPath);
        } catch (InvalidPathException ex) {
            return false;
        }
        if (!isContained(root, candidate)) return false;
        Path abs = candidate.toAbsolutePath().normalize();
        if (!Files.isRegularFile(abs, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return false;
        return Files.deleteIfExists(abs);
    }

    /** Extension en minusculas sin el punto ("" si no hay). Util para listas blancas de extension. */
    public static String extensionOf(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
