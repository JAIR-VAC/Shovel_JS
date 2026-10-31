package com.kve.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * - decisión de diseño completa en
 * docs/.
 * <p>
 * Los 11 packets de KVE ya tienen su propio par {@code encode(T, FriendlyByteBuf)} /
 * {@code decode(FriendlyByteBuf): T} escrito a mano, con validaciones y casos
 * borde (caps de tamaño, fallback ante IOException) que no son expresables
 * como una cadena de combinators de {@link StreamCodec#composite}. Este
 * adaptador deja que 2.2/2.3 conviertan cada packet a
 * {@code CustomPacketPayload} reusando ese mismo par tal cual, en vez de
 * reescribir la lógica de framing/compresión de cada uno.
 * <p>
 * Uso (ver el molde completo en el doc de diseño, §3):
 * <pre>{@code
 * public static final StreamCodec<FriendlyByteBuf, MiPacket> STREAM_CODEC =
 *         KvePayloadCodecs.of(MiPacket::encode, MiPacket::decode);
 * }</pre>
 */
public final class KvePayloadCodecs {

    private KvePayloadCodecs() {}

    /** Misma firma que los {@code encode} estáticos existentes: (valor, buffer). */
    @FunctionalInterface
    public interface LegacyEncoder<T> {
        void encode(T value, FriendlyByteBuf buf);
    }

    /** Misma firma que los {@code decode} estáticos existentes: (buffer) -> valor. */
    @FunctionalInterface
    public interface LegacyDecoder<T> {
        T decode(FriendlyByteBuf buf);
    }

    /**
     * Adapta un par encode/decode con la firma "vieja" (valor primero,
     * buffer segundo - la que ya tienen los 11 packets) a
     * {@link StreamCodec}, que espera (buffer, valor) en ese orden.
     * No cambia comportamiento: es un envoltorio de una línea sobre el
     * mismo código que ya corría bajo SimpleChannel.
     */
    public static <T> StreamCodec<FriendlyByteBuf, T> of(LegacyEncoder<T> encoder, LegacyDecoder<T> decoder) {
        return StreamCodec.of(
                (buf, value) -> encoder.encode(value, buf),
                decoder::decode);
    }
}
