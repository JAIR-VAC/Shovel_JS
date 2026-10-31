package com.kve.common.network;

import com.kve.CraftEditorMod;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public class StructureRemoteResponsePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StructureRemoteResponsePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(CraftEditorMod.MODID, "structure_remote_response"));

    public static final StreamCodec<FriendlyByteBuf, StructureRemoteResponsePacket> STREAM_CODEC =
            KvePayloadCodecs.of(StructureRemoteResponsePacket::encode, StructureRemoteResponsePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public final int requestId;
    public final String id;
    /** Raw compressed structure NBT bytes, or null if not found on the server. */
    public final byte[] data;

    public StructureRemoteResponsePacket(int requestId, String id, byte[] data) {
        this.requestId = requestId;
        this.id = id;
        this.data = data;
    }

    public static void encode(StructureRemoteResponsePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.requestId);
        buf.writeUtf(msg.id, 256);
        boolean found = msg.data != null;
        buf.writeBoolean(found);
        if (found) {
            buf.writeVarInt(msg.data.length);
            buf.writeBytes(msg.data);
        }
    }

    private static final int MAX_PAYLOAD_BYTES = 64 * 1024 * 1024;

    public static StructureRemoteResponsePacket decode(FriendlyByteBuf buf) {
        int requestId = buf.readVarInt();
        String id = buf.readUtf(256);
        boolean found = buf.readBoolean();
        byte[] data = null;
        if (found) {
            int len = buf.readVarInt();
            if (len < 0 || len > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Structure payload too large or invalid: " + len);
            }
            data = new byte[len];
            buf.readBytes(data);
        }
        return new StructureRemoteResponsePacket(requestId, id, data);
    }

    public static void handle(StructureRemoteResponsePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPacketApply.apply(msg));
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientPacketApply {
        static void apply(StructureRemoteResponsePacket msg) {
            com.kve.client.structure.StructureRemoteLoadState.onResponse(msg.requestId, msg.id, msg.data);
        }
    }
}
