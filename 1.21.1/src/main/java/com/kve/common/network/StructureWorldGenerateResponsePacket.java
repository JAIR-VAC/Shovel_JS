package com.kve.common.network;

import com.kve.CraftEditorMod;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;


/** Server -&gt; Client. Answers {@link StructureWorldGenerateRequestPacket}. */
public class StructureWorldGenerateResponsePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StructureWorldGenerateResponsePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(CraftEditorMod.MODID, "structure_world_generate_response"));

    public static final StreamCodec<FriendlyByteBuf, StructureWorldGenerateResponsePacket> STREAM_CODEC =
            KvePayloadCodecs.of(StructureWorldGenerateResponsePacket::encode, StructureWorldGenerateResponsePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public final int requestId;
    public final boolean success;
    /** Only meaningful when {@link #success} - the placed structure's own bounding box, for arming the client's world-build session. */
    public final BlockPos origin;
    public final int sizeX, sizeY, sizeZ, placedCount;
    /** Lang key for the flash message on failure; empty on success (the screen uses its own success text). */
    public final String failureKey;

    private StructureWorldGenerateResponsePacket(int requestId, boolean success, BlockPos origin,
                                                   int sizeX, int sizeY, int sizeZ, int placedCount, String failureKey) {
        this.requestId = requestId;
        this.success = success;
        this.origin = origin;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.placedCount = placedCount;
        this.failureKey = failureKey;
    }

    public static StructureWorldGenerateResponsePacket success(int requestId, BlockPos origin, int sizeX, int sizeY, int sizeZ, int placedCount) {
        return new StructureWorldGenerateResponsePacket(requestId, true, origin, sizeX, sizeY, sizeZ, placedCount, "");
    }

    public static StructureWorldGenerateResponsePacket failure(int requestId, String failureKey) {
        return new StructureWorldGenerateResponsePacket(requestId, false, BlockPos.ZERO, 0, 0, 0, 0, failureKey);
    }

    public static void encode(StructureWorldGenerateResponsePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.requestId);
        buf.writeBoolean(msg.success);
        buf.writeBlockPos(msg.origin);
        buf.writeVarInt(msg.sizeX);
        buf.writeVarInt(msg.sizeY);
        buf.writeVarInt(msg.sizeZ);
        buf.writeVarInt(msg.placedCount);
        buf.writeUtf(msg.failureKey, 64);
    }

    public static StructureWorldGenerateResponsePacket decode(FriendlyByteBuf buf) {
        int requestId = buf.readVarInt();
        boolean success = buf.readBoolean();
        BlockPos origin = buf.readBlockPos();
        int sx = buf.readVarInt(), sy = buf.readVarInt(), sz = buf.readVarInt();
        int placed = buf.readVarInt();
        String failureKey = buf.readUtf(64);
        return new StructureWorldGenerateResponsePacket(requestId, success, origin, sx, sy, sz, placed, failureKey);
    }

    public static void handle(StructureWorldGenerateResponsePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPacketApply.apply(msg));
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientPacketApply {
        static void apply(StructureWorldGenerateResponsePacket msg) {
            com.kve.client.structure.StructureWorldBuildSession.onGenerateResponse(msg);
        }
    }
}
