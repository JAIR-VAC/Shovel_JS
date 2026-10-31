package com.kve.common.network;

import com.kve.CraftEditorMod;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.kve.common.permission.KvePermissionManager;
import com.kve.common.permission.KvePermissionRole;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public class PermissionAdminRequestPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PermissionAdminRequestPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(CraftEditorMod.MODID, "permission_admin_request"));

    public static final StreamCodec<FriendlyByteBuf, PermissionAdminRequestPacket> STREAM_CODEC =
            KvePayloadCodecs.of(PermissionAdminRequestPacket::encode, PermissionAdminRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public PermissionAdminRequestPacket() {}

    public static void encode(PermissionAdminRequestPacket msg, FriendlyByteBuf buf) {}

    public static PermissionAdminRequestPacket decode(FriendlyByteBuf buf) {
        return new PermissionAdminRequestPacket();
    }

    public static void handle(PermissionAdminRequestPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) ctx.player();
            if (sender == null) return;
            if (KvePermissionManager.resolve(sender) != KvePermissionRole.ADMIN) return;

            MinecraftServer server = sender.getServer();
            if (server == null) return;

            List<PermissionAdminSyncPacket.Entry> entries = new ArrayList<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                String name = p.getGameProfile().getName();
                entries.add(new PermissionAdminSyncPacket.Entry(name, KvePermissionManager.resolve(p)));
            }
            ctx.reply(new PermissionAdminSyncPacket(
                    entries, KvePermissionManager.getRequireOpLevel(),
                    KvePermissionManager.getOpRole(), KvePermissionManager.getDefaultRole()));
        });
    }
}
