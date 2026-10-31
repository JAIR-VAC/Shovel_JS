package com.kve.common.network;

import com.kve.common.permission.KvePermissionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> Server. Sent on login, and whenever the client wants to
 * re-check its permission (e.g. the player clicks a "Refresh permissions"
 * button after an admin changed kve_permissions.json without a relog).
 * Carries no payload; the server just looks up the sender.
 */
public class PermissionRequestPacket {

    public PermissionRequestPacket() {}

    public static void encode(PermissionRequestPacket msg, FriendlyByteBuf buf) {}

    public static PermissionRequestPacket decode(FriendlyByteBuf buf) {
        return new PermissionRequestPacket();
    }

    public static void handle(PermissionRequestPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            KVENetwork.sendPermission(player, KvePermissionManager.resolve(player));
        });
        ctx.setPacketHandled(true);
    }
}
