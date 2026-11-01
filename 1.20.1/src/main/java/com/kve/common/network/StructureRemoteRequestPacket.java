package com.kve.common.network;

import com.kve.common.structure.StructureNbtIO;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> Server. Sent by {@code StructureEditorScreen#doLoad} only as a
 * fallback, when {@link StructureNbtIO#loadEffective} already failed
 * locally - i.e. there's no kubejs override AND the client has no
 * integrated server to read {@code PackType.SERVER_DATA} from itself
 * (a real remote/dedicated server connection). The server DOES have that
 * resource manager, so it reads the raw bytes on our behalf and answers
 * with a {@link StructureRemoteResponsePacket}.
 * <p>
 * Carries no permission check on purpose: this only ever reads a
 * structure resource (same as opening the editor's "Cargar" button
 * locally would), never writes anything, so it's gated the same
 * (not at all) as {@link StructureNbtIO#loadEffective} already is.
 */
public class StructureRemoteRequestPacket {

    /** Echoed back in the response so a stale/superseded reply on the client can be told apart from the one it's actually waiting for. */
    public final int requestId;
    public final String id;

    public StructureRemoteRequestPacket(int requestId, String id) {
        this.requestId = requestId;
        this.id = id;
    }

    public static void encode(StructureRemoteRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.requestId);
        buf.writeUtf(msg.id, 256);
    }

    public static StructureRemoteRequestPacket decode(FriendlyByteBuf buf) {
        return new StructureRemoteRequestPacket(buf.readVarInt(), buf.readUtf(256));
    }

    public static void handle(StructureRemoteRequestPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;
            MinecraftServer server = sender.getServer();

            // como los demas paquetes de estructuras: sin permiso se responde vacio (el cliente no queda esperando)
            if (!com.kve.common.permission.KvePermissionManager.resolve(sender).atLeast(com.kve.common.permission.KvePermissionRole.VIEW)) {
                KVENetwork.CHANNEL.reply(new StructureRemoteResponsePacket(msg.requestId, msg.id, null), ctx);
                return;
            }

            byte[] data = null;
            try {
                ResourceLocation id = new ResourceLocation(msg.id);
                data = StructureNbtIO.readRawBytesFromServer(server, id);
            } catch (RuntimeException ignored) {
            }
            KveSplitPacket.reply(new StructureRemoteResponsePacket(msg.requestId, msg.id, data), ctx);
        });
        ctx.setPacketHandled(true);
    }
}
