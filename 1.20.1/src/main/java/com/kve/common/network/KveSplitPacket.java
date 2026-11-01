package com.kve.common.network;

import com.kve.CraftEditorMod;
import com.kve.common.permission.KvePermissionManager;
import com.kve.common.permission.KvePermissionRole;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Parte de un paquete de estructura grande. Forge 1.20.1 no divide paquetes: en un servidor dedicado
 * al servidor le llegan como mucho 32767 bytes y al cliente 1 MiB, y una estructura mediana ya pasa ese limite.
 * Las partes se juntan en orden y el paquete original se maneja como si hubiera llegado entero.
 */
public class KveSplitPacket {

    static final byte KIND_GENERATE = 0;
    static final byte KIND_SCAN = 1;
    static final byte KIND_REMOTE = 2;

    private static final int TO_SERVER_PART = 30_000;
    private static final int TO_CLIENT_PART = 1_000_000;
    private static final int MAX_TOTAL_BYTES = 64 * 1024 * 1024;
    private static final long STALE_MS = 60_000;

    private static int nextTransferId = 0;
    private static final Map<UUID, Transfer> SERVER_TRANSFERS = new HashMap<>();
    private static Transfer clientTransfer;

    public final int transferId;
    public final byte kind;
    public final int index;
    public final int total;
    public final byte[] data;

    public KveSplitPacket(int transferId, byte kind, int index, int total, byte[] data) {
        this.transferId = transferId;
        this.kind = kind;
        this.index = index;
        this.total = total;
        this.data = data;
    }

    public static void encode(KveSplitPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.transferId);
        buf.writeByte(msg.kind);
        buf.writeVarInt(msg.index);
        buf.writeVarInt(msg.total);
        buf.writeByteArray(msg.data);
    }

    public static KveSplitPacket decode(FriendlyByteBuf buf) {
        return new KveSplitPacket(buf.readVarInt(), buf.readByte(), buf.readVarInt(), buf.readVarInt(),
                buf.readByteArray(TO_CLIENT_PART));
    }

    public static void sendToServer(StructureWorldGenerateRequestPacket msg) {
        byte[] bytes = toBytes(b -> StructureWorldGenerateRequestPacket.encode(msg, b));
        if (bytes.length <= TO_SERVER_PART) {
            KVENetwork.CHANNEL.sendToServer(msg);
            return;
        }
        split(bytes, KIND_GENERATE, TO_SERVER_PART, KVENetwork.CHANNEL::sendToServer);
    }

    public static void reply(StructureWorldScanResponsePacket msg, NetworkEvent.Context ctx) {
        byte[] bytes = toBytes(b -> StructureWorldScanResponsePacket.encode(msg, b));
        if (bytes.length <= TO_CLIENT_PART) KVENetwork.CHANNEL.reply(msg, ctx);
        else split(bytes, KIND_SCAN, TO_CLIENT_PART, part -> KVENetwork.CHANNEL.reply(part, ctx));
    }

    public static void reply(StructureRemoteResponsePacket msg, NetworkEvent.Context ctx) {
        byte[] bytes = toBytes(b -> StructureRemoteResponsePacket.encode(msg, b));
        if (bytes.length <= TO_CLIENT_PART) KVENetwork.CHANNEL.reply(msg, ctx);
        else split(bytes, KIND_REMOTE, TO_CLIENT_PART, part -> KVENetwork.CHANNEL.reply(part, ctx));
    }

    private static byte[] toBytes(java.util.function.Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writer.accept(buf);
            byte[] out = new byte[buf.readableBytes()];
            buf.readBytes(out);
            return out;
        } finally {
            buf.release();
        }
    }

    private static void split(byte[] bytes, byte kind, int partSize, java.util.function.Consumer<KveSplitPacket> send) {
        int id;
        synchronized (KveSplitPacket.class) {
            id = nextTransferId++;
        }
        int total = (bytes.length + partSize - 1) / partSize;
        for (int i = 0; i < total; i++) {
            int from = i * partSize;
            int len = Math.min(partSize, bytes.length - from);
            byte[] part = new byte[len];
            System.arraycopy(bytes, from, part, 0, len);
            send.accept(new KveSplitPacket(id, kind, i, total, part));
        }
    }

    public static void handle(KveSplitPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        boolean toServer = ctx.getDirection().getReceptionSide().isServer();
        ctx.enqueueWork(() -> {
            if (toServer) receiveOnServer(msg, ctxSupplier);
            else receiveOnClient(msg, ctxSupplier);
        });
        ctx.setPacketHandled(true);
    }

    private static void receiveOnServer(KveSplitPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        ServerPlayer sender = ctxSupplier.get().getSender();
        if (sender == null || msg.kind != KIND_GENERATE) return;
        // solo los admin pueden generar; asi nadie mas llena memoria del servidor con partes
        if (KvePermissionManager.resolve(sender) != KvePermissionRole.ADMIN) {
            if (msg.index == 0) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Ignored split structure upload from non-admin {}",
                        sender.getGameProfile().getName());
            }
            return;
        }
        long now = System.currentTimeMillis();
        SERVER_TRANSFERS.values().removeIf(t -> now - t.lastPart > STALE_MS);
        UUID key = sender.getUUID();
        byte[] done = accept(SERVER_TRANSFERS.get(key), msg, TO_SERVER_PART, t -> SERVER_TRANSFERS.put(key, t),
                () -> SERVER_TRANSFERS.remove(key));
        if (done == null) return;
        dispatch(done, buf -> StructureWorldGenerateRequestPacket.handle(
                StructureWorldGenerateRequestPacket.decode(buf), ctxSupplier));
    }

    private static void receiveOnClient(KveSplitPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        if (msg.kind != KIND_SCAN && msg.kind != KIND_REMOTE) return;
        byte[] done = accept(clientTransfer, msg, TO_CLIENT_PART, t -> clientTransfer = t, () -> clientTransfer = null);
        if (done == null) return;
        if (msg.kind == KIND_SCAN) {
            dispatch(done, buf -> StructureWorldScanResponsePacket.handle(
                    StructureWorldScanResponsePacket.decode(buf), ctxSupplier));
        } else {
            dispatch(done, buf -> StructureRemoteResponsePacket.handle(
                    StructureRemoteResponsePacket.decode(buf), ctxSupplier));
        }
    }

    /** Suma la parte a su transferencia; devuelve los bytes completos al llegar la ultima, o null. */
    private static byte[] accept(Transfer current, KveSplitPacket msg, int partSize,
                                 java.util.function.Consumer<Transfer> store, Runnable drop) {
        if (msg.total <= 0 || msg.index < 0 || msg.index >= msg.total
                || (long) msg.total * partSize > MAX_TOTAL_BYTES) {
            drop.run();
            return null;
        }
        if (msg.index == 0) {
            current = new Transfer(msg.transferId, msg.kind, msg.total);
            store.accept(current);
        }
        // una parte suelta o fuera de orden invalida la transferencia entera
        if (current == null || current.id != msg.transferId || current.kind != msg.kind
                || current.total != msg.total || current.next != msg.index) {
            drop.run();
            return null;
        }
        current.bytes.write(msg.data, 0, msg.data.length);
        current.next++;
        current.lastPart = System.currentTimeMillis();
        if (current.next < current.total) return null;
        drop.run();
        return current.bytes.toByteArray();
    }

    private static void dispatch(byte[] bytes, java.util.function.Consumer<FriendlyByteBuf> handler) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            handler.accept(buf);
        } catch (RuntimeException e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not read split structure packet", e);
        }
    }

    private static final class Transfer {
        final int id;
        final byte kind;
        final int total;
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int next = 0;
        long lastPart = System.currentTimeMillis();

        Transfer(int id, byte kind, int total) {
            this.id = id;
            this.kind = kind;
            this.total = total;
        }
    }
}
