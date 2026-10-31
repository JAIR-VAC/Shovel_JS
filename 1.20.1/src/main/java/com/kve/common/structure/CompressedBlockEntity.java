package com.kve.common.structure;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Holder inmutable de UN {@code CompoundTag} de block-entity (contenido de
 * cofre, config de spawner, texto de cartel, ...), guardado en memoria ya
 * comprimido con gzip en vez del {@link CompoundTag} vivo.
 * <p>
 * Motivación real: {@link StructureVoxelGrid#blockEntityNbt} es un mapa
 * {@code BlockPos -> nbt} sin límite de tamaño - una estructura con miles
 * de cofres/spawners (ej. una dungeon grande escaneada entera) puede tener
 * miles de entradas vivas al mismo tiempo en memoria del cliente, cada una
 * con su propio árbol de objetos {@code CompoundTag}/{@code ListTag}/etc.
 * Un cofre lleno de items con NBT (encantamientos, nombres custom) puede
 * pesar varios KB como objetos Java sueltos; comprimido con gzip (mismo
 * códec que ya usa {@code NbtIo.writeCompressed()} para guardar el .nbt a
 * disco) baja eso a una fracción, a costa de (des)comprimir en cada
 * lectura/escritura.
 * <p>
 * Esa ganancia SOLO es real con miles de entradas simultáneas - con una
 * estructura chica (unos pocos cofres) el overhead de gzip por-entrada
 * (cabecera + costo de CPU) no vale la pena frente al ahorro de memoria,
 * que ahí es insignificante. No hay un umbral "N o más" en esta clase a
 * propósito: comprimir SIEMPRE (sin importar el conteo) es lo único que no
 * requiere que {@link StructureVoxelGrid} sepa de antemano cuántas
 * entradas va a terminar teniendo una sesión de edición en curso, y el
 * costo de CPU de gzip sobre un CompoundTag de unos pocos KB es
 * imperceptible comparado con el resto de lo que ya hace un frame de
 * render/edición.
 */
public final class CompressedBlockEntity {

    private final byte[] gzipped;

    private CompressedBlockEntity(byte[] gzipped) {
        this.gzipped = gzipped;
    }

    /** Comprime {@code tag} (nunca null - los llamadores ya manejan "sin nbt" removiendo la entrada del mapa, no guardando un tag vacío). */
    public static CompressedBlockEntity of(CompoundTag tag) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(64);
        try {
            NbtIo.writeCompressed(tag, baos);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new CompressedBlockEntity(baos.toByteArray());
    }

    /** Descomprime y devuelve una instancia NUEVA del tag - cada llamada decodifica de cero, ningún caller comparte la instancia devuelta con otro. */
    public CompoundTag get() {
        try {
            return NbtIo.readCompressed(new ByteArrayInputStream(gzipped));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Tamaño real en bytes ya comprimido - expuesto solo para diagnostico/logging, no usado por la lógica de edición. */
    public int compressedSizeBytes() {
        return gzipped.length;
    }
}
