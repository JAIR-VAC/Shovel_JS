package com.kve.common.network;

import com.kve.CraftEditorMod;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class StructureWorldScanResponsePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StructureWorldScanResponsePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(CraftEditorMod.MODID, "structure_world_scan_response"));

    public static final StreamCodec<FriendlyByteBuf, StructureWorldScanResponsePacket> STREAM_CODEC =
            KvePayloadCodecs.of(StructureWorldScanResponsePacket::encode, StructureWorldScanResponsePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static final int OUTCOME_SUCCESS = 0;
    public static final int OUTCOME_EMPTY = 1;
    public static final int OUTCOME_FAILURE = 2;

    public final int requestId;
    public final int outcome;
    public final CompoundTag tag;
    public final BlockPos occupiedMin, occupiedMax;
    public final String failureKey;

    private StructureWorldScanResponsePacket(int requestId, int outcome, CompoundTag tag,
                                              BlockPos occupiedMin, BlockPos occupiedMax, String failureKey) {
        this.requestId = requestId;
        this.outcome = outcome;
        this.tag = tag;
        this.occupiedMin = occupiedMin;
        this.occupiedMax = occupiedMax;
        this.failureKey = failureKey;
    }

    public static StructureWorldScanResponsePacket success(int requestId, CompoundTag tag, BlockPos occupiedMin, BlockPos occupiedMax) {
        return new StructureWorldScanResponsePacket(requestId, OUTCOME_SUCCESS, tag, occupiedMin, occupiedMax, "");
    }

    public static StructureWorldScanResponsePacket empty(int requestId) {
        return new StructureWorldScanResponsePacket(requestId, OUTCOME_EMPTY, new CompoundTag(), BlockPos.ZERO, BlockPos.ZERO, "");
    }

    public static StructureWorldScanResponsePacket failure(int requestId, String failureKey) {
        return new StructureWorldScanResponsePacket(requestId, OUTCOME_FAILURE, new CompoundTag(), BlockPos.ZERO, BlockPos.ZERO, failureKey);
    }

    private static final int MAX_PAYLOAD_BYTES = 64 * 1024 * 1024;

    public static void encode(StructureWorldScanResponsePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.requestId);
        buf.writeVarInt(msg.outcome);
        byte[] compressed;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(msg.tag, out);
            compressed = out.toByteArray();
        } catch (IOException e) {
            compressed = new byte[0];
        }
        buf.writeVarInt(compressed.length);
        buf.writeBytes(compressed);
        buf.writeBlockPos(msg.occupiedMin);
        buf.writeBlockPos(msg.occupiedMax);
        buf.writeUtf(msg.failureKey, 64);
    }

    public static StructureWorldScanResponsePacket decode(FriendlyByteBuf buf) {
        int requestId = buf.readVarInt();
        int outcome = buf.readVarInt();
        int len = buf.readVarInt();
        if (len < 0 || len > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Structure scan payload too large or invalid: " + len);
        }
        byte[] compressed = new byte[len];
        buf.readBytes(compressed);
        CompoundTag tag;
        try {
            tag = len == 0 ? new CompoundTag() : NbtIo.readCompressed(new ByteArrayInputStream(compressed), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            tag = new CompoundTag();
        }
        BlockPos min = buf.readBlockPos();
        BlockPos max = buf.readBlockPos();
        String failureKey = buf.readUtf(64);
        return new StructureWorldScanResponsePacket(requestId, outcome, tag != null ? tag : new CompoundTag(), min, max, failureKey);
    }

    public static void handle(StructureWorldScanResponsePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPacketApply.apply(msg));
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientPacketApply {
        static void apply(StructureWorldScanResponsePacket msg) {
            com.kve.client.structure.StructureWorldBuildSession.onScanResponse(msg);
        }
    }
}
