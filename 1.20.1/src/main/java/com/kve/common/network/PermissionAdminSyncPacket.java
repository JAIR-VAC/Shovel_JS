package com.kve.common.network;

import com.kve.common.permission.KvePermissionRole;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server -> Client. Answers {@link PermissionAdminRequestPacket} with every currently-online player's resolved role, plus the config's fallback settings for display. */
public class PermissionAdminSyncPacket {

    public static final class Entry {
        public final String name;
        public final KvePermissionRole role;
        public Entry(String name, KvePermissionRole role) { this.name = name; this.role = role; }
    }

    public final List<Entry> entries;
    public final int requireOpLevel;
    public final KvePermissionRole opRole;
    public final KvePermissionRole defaultRole;

    public PermissionAdminSyncPacket(List<Entry> entries, int requireOpLevel, KvePermissionRole opRole, KvePermissionRole defaultRole) {
        this.entries = entries;
        this.requireOpLevel = requireOpLevel;
        this.opRole = opRole;
        this.defaultRole = defaultRole;
    }

    public static void encode(PermissionAdminSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entries.size());
        for (Entry e : msg.entries) {
            buf.writeUtf(e.name, 32);
            buf.writeUtf(e.role.name(), 16);
        }
        buf.writeVarInt(msg.requireOpLevel);
        buf.writeUtf(msg.opRole.name(), 16);
        buf.writeUtf(msg.defaultRole.name(), 16);
    }

    public static PermissionAdminSyncPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String name = buf.readUtf(32);
            KvePermissionRole role = KvePermissionRole.parse(buf.readUtf(16), KvePermissionRole.NONE);
            entries.add(new Entry(name, role));
        }
        int requireOpLevel = buf.readVarInt();
        KvePermissionRole opRole = KvePermissionRole.parse(buf.readUtf(16), KvePermissionRole.ADMIN);
        KvePermissionRole defaultRole = KvePermissionRole.parse(buf.readUtf(16), KvePermissionRole.NONE);
        return new PermissionAdminSyncPacket(entries, requireOpLevel, opRole, defaultRole);
    }

    public static void handle(PermissionAdminSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientPacketApply.apply(msg)));
        ctx.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientPacketApply {
        static void apply(PermissionAdminSyncPacket msg) {
            com.kve.client.permission.ClientPermissionAdminState.onSync(msg);
        }
    }
}
