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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class StructureWorldGenerateRequestPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StructureWorldGenerateRequestPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(CraftEditorMod.MODID, "structure_world_generate_request"));

    public static final StreamCodec<FriendlyByteBuf, StructureWorldGenerateRequestPacket> STREAM_CODEC =
            KvePayloadCodecs.of(StructureWorldGenerateRequestPacket::encode, StructureWorldGenerateRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public final int requestId;
    public final BlockPos origin;
    public final CompoundTag structureTag;
    /**
     * True cuando el cliente ya tiene una sesion armada (re-editar in-place
     * una estructura existente, ver {@code StructureWorldBuildSession#isEnabled}
     * en {@code StructureEditorScreen#doWorldGenerate}) - ver
     * {@link StructureWorldIO#place(ServerLevel, BlockPos, CompoundTag, boolean)}
     * para el porque. False = colocar copia nueva anclada al crosshair, sin
     * sesion (shell/ray-cast normal).
     */
    public final boolean inPlace;

    public StructureWorldGenerateRequestPacket(int requestId, BlockPos origin, CompoundTag structureTag, boolean inPlace) {
        this.requestId = requestId;
        this.origin = origin;
        this.structureTag = structureTag;
        this.inPlace = inPlace;
    }

    private static final int MAX_PAYLOAD_BYTES = 64 * 1024 * 1024;

    public static void encode(StructureWorldGenerateRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.requestId);
        buf.writeBlockPos(msg.origin);
        buf.writeBoolean(msg.inPlace);
        byte[] compressed;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(msg.structureTag, out);
            compressed = out.toByteArray();
        } catch (IOException e) {
            compressed = new byte[0];
        }
        buf.writeVarInt(compressed.length);
        buf.writeBytes(compressed);
    }

    public static StructureWorldGenerateRequestPacket decode(FriendlyByteBuf buf) {
        int requestId = buf.readVarInt();
        BlockPos origin = buf.readBlockPos();
        boolean inPlace = buf.readBoolean();
        int len = buf.readVarInt();
        if (len < 0 || len > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Structure generate payload too large or invalid: " + len);
        }
        byte[] compressed = new byte[len];
        buf.readBytes(compressed);
        CompoundTag tag;
        try {
            tag = len == 0 ? new CompoundTag() : NbtIo.readCompressed(new ByteArrayInputStream(compressed), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            tag = new CompoundTag();
        }
        return new StructureWorldGenerateRequestPacket(requestId, origin, tag != null ? tag : new CompoundTag(), inPlace);
    }

    public static void handle(StructureWorldGenerateRequestPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) ctx.player();
            if (sender == null) return;

            if (KvePermissionManager.resolve(sender) != KvePermissionRole.ADMIN) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Ignored world-generate request from non-admin {}", sender.getGameProfile().getName());
                ctx.reply(StructureWorldGenerateResponsePacket.failure(
                        msg.requestId, "gui.kve.perm.denied"));
                return;
            }

            int[] size = readSize(msg.structureTag);
            if (size == null) {
                ctx.reply(StructureWorldGenerateResponsePacket.failure(
                        msg.requestId, "gui.kve.structure.empty"));
                return;
            }
            if (size[0] > KVEConfig.structureEditorMaxEdge || size[1] > KVEConfig.structureEditorMaxEdge
                    || size[2] > KVEConfig.structureEditorMaxEdge) {
                ctx.reply(StructureWorldGenerateResponsePacket.failure(
                        msg.requestId, "gui.kve.structure.world.too_large"));
                return;
            }

            ServerLevel level = sender.serverLevel();
            StructureWorldIO.PlaceResult result = StructureWorldIO.placeAndReport(level, msg.origin, msg.structureTag, msg.inPlace);
            int placed = result.placed();
            CraftEditorMod.LOGGER.info("[Shovel JS] {} generated {} real blocks at {} in {}",
                    sender.getGameProfile().getName(), placed, result.origin(), level.dimension().location());
            ctx.reply(StructureWorldGenerateResponsePacket.success(
                    msg.requestId, result.origin(), size[0], size[1], size[2], placed));
        });
    }

    private static int[] readSize(CompoundTag tag) {
        if (!tag.contains("size", 9 )) return null;
        var sizeTag = tag.getList("size", 3 );
        if (sizeTag.size() != 3) return null;
        int x = sizeTag.getInt(0), y = sizeTag.getInt(1), z = sizeTag.getInt(2);
        if (x <= 0 || y <= 0 || z <= 0) return null;
        return new int[]{x, y, z};
    }
}
