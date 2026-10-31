package com.kve.common.customitem;

import com.kve.CraftEditorMod;
import com.kve.common.config.KVEConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * "Raíz de navegación: NO dar acceso al filesystem completo del
 * usuario ... Anclar a una carpeta fija ... con `..` deshabilitado y
 * resolución de rutas siempre vía Path.normalize() + chequeo
 * startsWith(root) antes de leer nada."
 * <p>
 * Deliberately has no dependency on any client/Minecraft type so it can be
 * unit-tested with plain JUnit + a temp directory - the actual security
 * property this class exists for is exactly the kind of thing that should have a real,
 * fast, no-Forge-bootstrap test around it rather than only being exercised
 * by hand in-game.
 */
public final class ImportRoot {

    private ImportRoot() {}

    /** Files.walk depth cap - a flat "drop your PNGs here" folder doesn't need unbounded recursion, and this bounds worst-case directory-walk cost on a folder the player fully controls. */
    private static final int MAX_DEPTH = 4;

    /** Folder the in-game browser is anchored to - {@link KVEConfig#filePickerImportDir}, relative to the game/run directory (same base kubejs/ and config/ already use). */
    public static Path root() {
        String dir = KVEConfig.filePickerImportDir;
        if (dir == null || dir.isBlank()) dir = "shovel_js_import";
        return Paths.get(dir);
    }

    public static void ensureExists() {
        try {
            Files.createDirectories(root());
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not create import folder {}: {}", root(), ex.getMessage());
        }
    }

    /**
     * Resolves {@code relativeOrAbsolute} (as picked from a
     * {@link #listPngFiles()} entry, or typed/pasted by the player) against
     * {@code root}, and returns it ONLY if the normalized result is still
     * inside {@code root}. This is the actual traversal guard: an input
     * like {@code "../../config/kve.properties"} or an absolute path
     * pointing anywhere else normalizes outside root and comes back empty
     * instead of ever reaching a {@code Files} call.
     */
    public static Optional<Path> resolveSafe(Path root, String relativeOrAbsolute) {
        if (root == null || relativeOrAbsolute == null || relativeOrAbsolute.isBlank()) return Optional.empty();
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path candidate;
        try {
            candidate = Paths.get(relativeOrAbsolute);
        } catch (Exception ex) {
            return Optional.empty();
        }
        Path resolved = candidate.isAbsolute() ? candidate : root.resolve(candidate);
        Path normalized = resolved.toAbsolutePath().normalize();
        if (!normalized.startsWith(normalizedRoot)) return Optional.empty();
        return Optional.of(normalized);
    }

    /**
     * Every {@code *.png} under {@code root} (depth-capped), as
     * root-relative display strings using forward slashes on every OS.
     * Returns an empty list (never throws) if {@code root} doesn't exist
     * yet or can't be listed - the caller/screen shows its own "no files
     * yet" hint for that case.
     */
    public static List<String> listPngFiles(Path root) {
        List<String> out = new ArrayList<>();
        if (root == null) return out;
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot)) return out;
        try (Stream<Path> walk = Files.walk(normalizedRoot, MAX_DEPTH)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .forEach(p -> out.add(normalizedRoot.relativize(p).toString().replace('\\', '/')));
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not list import folder {}: {}", normalizedRoot, ex.getMessage());
        }
        Collections.sort(out);
        return out;
    }
}
