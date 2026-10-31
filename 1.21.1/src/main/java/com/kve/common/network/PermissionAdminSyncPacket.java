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

import java.util.ArrayList;
import java.util.List;

/** Server -> Client. Answers {@link PermissionAdminRequestPacket} with every currently-online player's resolved role, plus the config's fallback settings for display. */
public class PermissionAdminSyncPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PermissionAdminSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(CraftEditorMod.MODID, "permission_admin_sync"));

    public static final StreamCodec<FriendlyByteBuf, PermissionAdminSyncPacket> STREAM_CODEC =
            KvePayloadCodecs.of(PermissionAdminSyncPacket::encode, PermissionAdminSyncPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

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

    public static void handle(PermissionAdminSyncPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPacketApply.apply(msg));
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientPacketApply {
        static void apply(PermissionAdminSyncPacket msg) {
            com.kve.client.permission.ClientPermissionAdminState.onSync(msg);
        }
    }
}
