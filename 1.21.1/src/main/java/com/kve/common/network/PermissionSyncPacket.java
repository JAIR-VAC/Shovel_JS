package com.kve.common.network;

import com.kve.CraftEditorMod;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import com.kve.common.permission.KvePermissionRole;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;


/**
 * Server -> Client. Carries the role the server just resolved for this
 * player (see {@code KvePermissionManager#resolve}). The client caches it
 * in {@code ClientPermissionState} and uses it to gate opening the editor
 * GUI and every mutating action inside it.
 */
public class PermissionSyncPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PermissionSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(CraftEditorMod.MODID, "permission_sync"));

    public static final StreamCodec<FriendlyByteBuf, PermissionSyncPacket> STREAM_CODEC =
            KvePayloadCodecs.of(PermissionSyncPacket::encode, PermissionSyncPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public final String role;

    public PermissionSyncPacket(KvePermissionRole role) {
        this.role = role.name();
    }

    private PermissionSyncPacket(String role) {
        this.role = role;
    }

    public static void encode(PermissionSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.role, 16);
    }

    public static PermissionSyncPacket decode(FriendlyByteBuf buf) {
        return new PermissionSyncPacket(buf.readUtf(16));
    }

    public static void handle(PermissionSyncPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPacketApply.apply(msg));
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientPacketApply {
        static void apply(PermissionSyncPacket msg) {
            com.kve.client.permission.ClientPermissionState.onSync(
                    KvePermissionRole.parse(msg.role, KvePermissionRole.NONE));
        }
    }
}
