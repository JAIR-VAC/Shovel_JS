package com.kve.common.network;

import com.kve.common.permission.KvePermissionManager;
import com.kve.common.permission.KvePermissionRole;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PermissionAdminRequestPacket {

    public PermissionAdminRequestPacket() {}

    public static void encode(PermissionAdminRequestPacket msg, FriendlyByteBuf buf) {}

    public static PermissionAdminRequestPacket decode(FriendlyByteBuf buf) {
        return new PermissionAdminRequestPacket();
    }

    public static void handle(PermissionAdminRequestPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;
            if (KvePermissionManager.resolve(sender) != KvePermissionRole.ADMIN) return;

            MinecraftServer server = sender.getServer();
            if (server == null) return;

            List<PermissionAdminSyncPacket.Entry> entries = new ArrayList<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                String name = p.getGameProfile().getName();
                entries.add(new PermissionAdminSyncPacket.Entry(name, KvePermissionManager.resolve(p)));
            }
            KVENetwork.CHANNEL.reply(new PermissionAdminSyncPacket(
                    entries, KvePermissionManager.getRequireOpLevel(),
                    KvePermissionManager.getOpRole(), KvePermissionManager.getDefaultRole()), ctx);
        });
        ctx.setPacketHandled(true);
    }
}
