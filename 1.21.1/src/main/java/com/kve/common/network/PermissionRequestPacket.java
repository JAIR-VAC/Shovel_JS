package com.kve.common.network;

import com.kve.CraftEditorMod;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.kve.common.permission.KvePermissionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;


/**
 * Client -> Server. Sent on login, and whenever the client wants to
 * re-check its permission (e.g. the player clicks a "Refresh permissions"
 * button after an admin changed kve_permissions.json without a relog).
 * Carries no payload; the server just looks up the sender.
 */
public class PermissionRequestPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PermissionRequestPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(CraftEditorMod.MODID, "permission_request"));

    public static final StreamCodec<FriendlyByteBuf, PermissionRequestPacket> STREAM_CODEC =
            KvePayloadCodecs.of(PermissionRequestPacket::encode, PermissionRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public PermissionRequestPacket() {}

    public static void encode(PermissionRequestPacket msg, FriendlyByteBuf buf) {}

    public static PermissionRequestPacket decode(FriendlyByteBuf buf) {
        return new PermissionRequestPacket();
    }

    public static void handle(PermissionRequestPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;
            KVENetwork.sendPermission(player, KvePermissionManager.resolve(player));
        });
    }
}
