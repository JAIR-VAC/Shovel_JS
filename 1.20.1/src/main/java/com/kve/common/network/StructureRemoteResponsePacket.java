package com.kve.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class StructureRemoteResponsePacket {

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

    public static void handle(StructureRemoteResponsePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientPacketApply.apply(msg)));
        ctx.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientPacketApply {
        static void apply(StructureRemoteResponsePacket msg) {
            com.kve.client.structure.StructureRemoteLoadState.onResponse(msg.requestId, msg.id, msg.data);
        }
    }
}
