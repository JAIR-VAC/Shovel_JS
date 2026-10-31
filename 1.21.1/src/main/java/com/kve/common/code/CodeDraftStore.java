package com.kve.common.code;

import com.kve.common.util.SafeFiles;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Autoguardado de borradores del editor de codigo.
 * Mientras una pestaña tiene
 * cambios sin guardar, su texto se copia cada pocos segundos a
 * {@code config/kve/code_drafts/}; si el juego se cierra o crashea antes de
 * guardar, al volver a abrir ese archivo el editor ofrece recuperarlo.
 * <p>
 * Nunca toca el script real: el borrador vive aparte y se borra en cuanto
 * el archivo se guarda o se descartan los cambios. Un archivo por script,
 * nombrado por un hash de su ruta (las rutas pueden tener caracteres que no
 * sirven como nombre de archivo).
 */
public final class CodeDraftStore {

    private static final Gson GSON = new Gson();

    private final Path dir;

    public CodeDraftStore(Path dir) {
        this.dir = dir;
    }

    /** Un borrador recuperado: el texto y cuando se guardo (epoch ms). */
    public record Draft(String text, long savedAtMillis) {}

    private Path fileFor(Path script) {
        String key = script.toAbsolutePath().normalize().toString();
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(key.getBytes(StandardCharsets.UTF_8));
            return dir.resolve(HexFormat.of().formatHex(h) + ".json");
        } catch (Exception ex) {
            return dir.resolve(Integer.toHexString(key.hashCode()) + ".json");
        }
    }

    /** Guarda (o reemplaza) el borrador de {@code script}. Escritura atomica via archivo temporal. */
    public void save(Path script, String text) {
        try {
            Files.createDirectories(dir);
            JsonObject o = new JsonObject();
            o.addProperty("path", script.toAbsolutePath().normalize().toString());
            o.addProperty("savedAt", System.currentTimeMillis());
            o.addProperty("text", text);
            Path target = fileFor(script);
            SafeFiles.writeAtomic(target, GSON.toJson(o));
        } catch (IOException ignored) {
        }
    }

    /** Borrador de {@code script}, si existe y es legible. */
    public Optional<Draft> load(Path script) {
        Path f = fileFor(script);
        if (!Files.isRegularFile(f)) return Optional.empty();
        try {
            JsonObject o = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
            if (o == null || !o.has("text")) return Optional.empty();
            long at = o.has("savedAt") ? o.get("savedAt").getAsLong() : 0L;
            return Optional.of(new Draft(o.get("text").getAsString(), at));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    /** Borra el borrador de {@code script} (tras guardar o descartar). */
    public void clear(Path script) {
        try {
            Files.deleteIfExists(fileFor(script));
        } catch (IOException ignored) {}
    }
}
