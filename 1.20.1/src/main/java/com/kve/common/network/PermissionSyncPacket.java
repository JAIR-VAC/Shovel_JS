package com.kve.common.network;

import com.kve.common.permission.KvePermissionRole;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> Client. Carries the role the server just resolved for this
 * player (see {@code KvePermissionManager#resolve}). The client caches it
 * in {@code ClientPermissionState} and uses it to gate opening the editor
 * GUI and every mutating action inside it.
 */
public class PermissionSyncPacket {

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

    public static void handle(PermissionSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientPacketApply.apply(msg)));
        ctx.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientPacketApply {
        static void apply(PermissionSyncPacket msg) {
            com.kve.client.permission.ClientPermissionState.onSync(
                    KvePermissionRole.parse(msg.role, KvePermissionRole.NONE));
        }
    }
}
