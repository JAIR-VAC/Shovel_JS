package com.kve.common.network;

import com.kve.CraftEditorMod;
import com.kve.common.config.KVEConfig;
import com.kve.common.permission.KvePermissionManager;
import com.kve.common.permission.KvePermissionRole;
import com.kve.common.structure.StructureWorldIO;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class StructureWorldScanRequestPacket {

    public final int requestId;
    public final BlockPos origin;
    public final int sizeX, sizeY, sizeZ;
    /** Opt-in "incluir mobs al escanear" toggle, see {@link StructureWorldIO#scan(ServerLevel, BlockPos, int, int, int, boolean)}. */
    public final boolean includeMobs;
    public final boolean includeAir;
    public final boolean requireSealed;

    public StructureWorldScanRequestPacket(int requestId, BlockPos origin, int sizeX, int sizeY, int sizeZ, boolean includeMobs) {
        this(requestId, origin, sizeX, sizeY, sizeZ, includeMobs, false, false);
    }

    public StructureWorldScanRequestPacket(int requestId, BlockPos origin, int sizeX, int sizeY, int sizeZ,
                                            boolean includeMobs, boolean includeAir) {
        this(requestId, origin, sizeX, sizeY, sizeZ, includeMobs, includeAir, false);
    }

    public StructureWorldScanRequestPacket(int requestId, BlockPos origin, int sizeX, int sizeY, int sizeZ,
                                            boolean includeMobs, boolean includeAir, boolean requireSealed) {
        this.requestId = requestId;
        this.origin = origin;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.includeMobs = includeMobs;
        this.includeAir = includeAir;
        this.requireSealed = requireSealed;
    }

    public static void encode(StructureWorldScanRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.requestId);
        buf.writeBlockPos(msg.origin);
        buf.writeVarInt(msg.sizeX);
        buf.writeVarInt(msg.sizeY);
        buf.writeVarInt(msg.sizeZ);
        buf.writeBoolean(msg.includeMobs);
        buf.writeBoolean(msg.includeAir);
        buf.writeBoolean(msg.requireSealed);
    }

    public static StructureWorldScanRequestPacket decode(FriendlyByteBuf buf) {
        int requestId = buf.readVarInt();
        BlockPos origin = buf.readBlockPos();
        int sizeX = buf.readVarInt();
        int sizeY = buf.readVarInt();
        int sizeZ = buf.readVarInt();
        boolean includeMobs = buf.readBoolean();
        boolean includeAir = buf.readBoolean();
        boolean requireSealed = buf.readBoolean();
        return new StructureWorldScanRequestPacket(requestId, origin, sizeX, sizeY, sizeZ, includeMobs, includeAir, requireSealed);
    }

    public static void handle(StructureWorldScanRequestPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;

            if (KvePermissionManager.resolve(sender) != KvePermissionRole.ADMIN) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Ignored world-scan request from non-admin {}", sender.getGameProfile().getName());
                KVENetwork.CHANNEL.reply(StructureWorldScanResponsePacket.failure(
                        msg.requestId, "gui.kve.perm.denied"), ctx);
                return;
            }

            if (msg.sizeX <= 0 || msg.sizeY <= 0 || msg.sizeZ <= 0
                    || msg.sizeX > KVEConfig.structureEditorMaxEdge
                    || msg.sizeY > KVEConfig.structureEditorMaxEdge
                    || msg.sizeZ > KVEConfig.structureEditorMaxEdge) {
                KVENetwork.CHANNEL.reply(StructureWorldScanResponsePacket.failure(
                        msg.requestId, "gui.kve.structure.world.too_large"), ctx);
                return;
            }

            ServerLevel level = sender.serverLevel();

            if (msg.requireSealed && !StructureWorldIO.isBorderSealed(level, msg.origin, msg.sizeX, msg.sizeY, msg.sizeZ)) {
                KVENetwork.CHANNEL.reply(StructureWorldScanResponsePacket.failure(
                        msg.requestId, "gui.kve.structure.world.border_not_sealed"), ctx);
                return;
            }

            StructureWorldIO.ScanResult result = StructureWorldIO.scan(level, msg.origin, msg.sizeX, msg.sizeY, msg.sizeZ, msg.includeMobs, msg.includeAir);
            if (result.tag == null) {
                KVENetwork.CHANNEL.reply(StructureWorldScanResponsePacket.empty(msg.requestId), ctx);
                return;
            }

            CraftEditorMod.LOGGER.info("[Shovel JS] {} scanned world region at {} ({}x{}x{}) in {}",
                    sender.getGameProfile().getName(), msg.origin, msg.sizeX, msg.sizeY, msg.sizeZ,
                    level.dimension().location());
            KveSplitPacket.reply(StructureWorldScanResponsePacket.success(
                    msg.requestId, result.tag, result.occupiedMin, result.occupiedMax), ctx);
        });
        ctx.setPacketHandled(true);
    }
}
