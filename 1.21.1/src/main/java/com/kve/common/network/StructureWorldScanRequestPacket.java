package com.kve.common.network;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.kve.CraftEditorMod;
import com.kve.common.config.KVEConfig;
import com.kve.common.permission.KvePermissionManager;
import com.kve.common.permission.KvePermissionRole;
import com.kve.common.structure.StructureWorldIO;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public class StructureWorldScanRequestPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StructureWorldScanRequestPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(CraftEditorMod.MODID, "structure_world_scan_request"));

    public static final StreamCodec<FriendlyByteBuf, StructureWorldScanRequestPacket> STREAM_CODEC =
            KvePayloadCodecs.of(StructureWorldScanRequestPacket::encode, StructureWorldScanRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

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

    public static void handle(StructureWorldScanRequestPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) ctx.player();
            if (sender == null) return;

            if (KvePermissionManager.resolve(sender) != KvePermissionRole.ADMIN) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Ignored world-scan request from non-admin {}", sender.getGameProfile().getName());
                ctx.reply(StructureWorldScanResponsePacket.failure(
                        msg.requestId, "gui.kve.perm.denied"));
                return;
            }

            if (msg.sizeX <= 0 || msg.sizeY <= 0 || msg.sizeZ <= 0
                    || msg.sizeX > KVEConfig.structureEditorMaxEdge
                    || msg.sizeY > KVEConfig.structureEditorMaxEdge
                    || msg.sizeZ > KVEConfig.structureEditorMaxEdge) {
                ctx.reply(StructureWorldScanResponsePacket.failure(
                        msg.requestId, "gui.kve.structure.world.too_large"));
                return;
            }

            ServerLevel level = sender.serverLevel();

            if (msg.requireSealed && !StructureWorldIO.isBorderSealed(level, msg.origin, msg.sizeX, msg.sizeY, msg.sizeZ)) {
                ctx.reply(StructureWorldScanResponsePacket.failure(
                        msg.requestId, "gui.kve.structure.world.border_not_sealed"));
                return;
            }

            StructureWorldIO.ScanResult result = StructureWorldIO.scan(level, msg.origin, msg.sizeX, msg.sizeY, msg.sizeZ, msg.includeMobs, msg.includeAir);
            if (result.tag == null) {
                ctx.reply(StructureWorldScanResponsePacket.empty(msg.requestId));
                return;
            }

            CraftEditorMod.LOGGER.info("[Shovel JS] {} scanned world region at {} ({}x{}x{}) in {}",
                    sender.getGameProfile().getName(), msg.origin, msg.sizeX, msg.sizeY, msg.sizeZ,
                    level.dimension().location());
            ctx.reply(StructureWorldScanResponsePacket.success(
                    msg.requestId, result.tag, result.occupiedMin, result.occupiedMax));
        });
    }
}
