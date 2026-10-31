package com.kve.common.network;

import com.kve.CraftEditorMod;
import com.kve.common.permission.KvePermissionManager;
import com.kve.common.permission.KvePermissionRole;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PermissionSetRolePacket {

    public final String targetName;
    public final String role;

    public PermissionSetRolePacket(String targetName, String role) {
        this.targetName = targetName;
        this.role = role;
    }

    public static void encode(PermissionSetRolePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.targetName, 32);
        buf.writeUtf(msg.role, 16);
    }

    public static PermissionSetRolePacket decode(FriendlyByteBuf buf) {
        return new PermissionSetRolePacket(buf.readUtf(32), buf.readUtf(16));
    }

    public static void handle(PermissionSetRolePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;
            if (KvePermissionManager.resolve(sender) != KvePermissionRole.ADMIN) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Ignored permission change from non-admin {}", sender.getGameProfile().getName());
                return;
            }

            MinecraftServer server = sender.getServer();
            if (server == null || msg.targetName == null || msg.targetName.isBlank()) return;

            if ("CLEAR".equals(msg.role)) {
                KvePermissionManager.clearPlayerRole(msg.targetName);
            } else {
                KvePermissionManager.setPlayerRole(msg.targetName, KvePermissionRole.parse(msg.role, KvePermissionRole.NONE));
            }

            ServerPlayer target = server.getPlayerList().getPlayerByName(msg.targetName);
            if (target != null) KVENetwork.sendPermission(target, KvePermissionManager.resolve(target));

            List<PermissionAdminSyncPacket.Entry> entries = new ArrayList<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                entries.add(new PermissionAdminSyncPacket.Entry(p.getGameProfile().getName(), KvePermissionManager.resolve(p)));
            }
            KVENetwork.CHANNEL.reply(new PermissionAdminSyncPacket(
                    entries, KvePermissionManager.getRequireOpLevel(),
                    KvePermissionManager.getOpRole(), KvePermissionManager.getDefaultRole()), ctx);
        });
        ctx.setPacketHandled(true);
    }
}
