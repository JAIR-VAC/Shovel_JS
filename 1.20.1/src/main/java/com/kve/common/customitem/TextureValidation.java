package com.kve.common.customitem;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * <p>
 * Deliberately only depends on the JDK (no Minecraft types), same reason
 * {@link ImportRoot} avoids them - {@link #validateFile()} and
 * {@link #readDimensions()} are plain, fast, real-file checks a unit test
 * can exercise directly with a temp file. The GPU-side texture upload
 * (NativeImage/DynamicTexture) stays in the client screen, which is the
 * only part of this feature that actually needs Minecraft's renderer.
 */
public final class TextureValidation {

    private TextureValidation() {}

    public static final long MAX_BYTES = 2L * 1024 * 1024;

    /** Blocking checks (extension + size) - a file that fails these is refused outright, not just warned about. */
    public static List<String> validateFile(Path p) {
        List<String> errors = new ArrayList<>();
        if (p == null) {
            errors.add(msg("gui.kve.itemcreator.picker.not_png"));
            return errors;
        }
        String name = p.getFileName() == null ? "" : p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".png")) {
            errors.add(msg("gui.kve.itemcreator.picker.not_png"));
        }
        try {
            if (Files.exists(p) && Files.size(p) > MAX_BYTES) {
                errors.add(msg("gui.kve.itemcreator.picker.too_large"));
            }
        } catch (IOException ignored) {
        }
        return errors;
    }

    /** Pixel dimensions via plain ImageIO (no GPU/Minecraft involved) - empty if the file isn't a readable image. */
    public static Optional<int[]> readDimensions(Path p) {
        if (p == null) return Optional.empty();
        try (InputStream in = Files.newInputStream(p)) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) return Optional.empty();
            return Optional.of(new int[]{img.getWidth(), img.getHeight()});
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    /** Non-blocking sizing check - estudio: "avisar si no es 16x16/32x32/64x64 porque puede verse borroso, no bloquear". */
    public static boolean isRecommendedSize(int width, int height) {
        return width == height && (width == 16 || width == 32 || width == 64);
    }

    private static String msg(String key) {
        try { return com.kve.common.Lang.get(key); } catch (Throwable t) { return key; }
    }
}
