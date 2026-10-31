package com.kve.common.network;

import com.kve.CraftEditorMod;
import com.kve.common.permission.KvePermissionManager;
import com.kve.common.permission.KvePermissionRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class WorldGenLabTeleportPacket {

    /** Id de la dimension de prueba; vacio = volver al punto de partida guardado. */
    public final String dimensionId;

    public WorldGenLabTeleportPacket(String dimensionId) {
        this.dimensionId = dimensionId == null ? "" : dimensionId;
    }

    private record ReturnPoint(ResourceKey<Level> dimension, double x, double y, double z, float yRot, float xRot) {}

    private static final Map<UUID, ReturnPoint> RETURN_POINTS = new HashMap<>();

    public static void encode(WorldGenLabTeleportPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.dimensionId, 256);
    }

    public static WorldGenLabTeleportPacket decode(FriendlyByteBuf buf) {
        return new WorldGenLabTeleportPacket(buf.readUtf(256));
    }

    public static void handle(WorldGenLabTeleportPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (KvePermissionManager.resolve(player) != KvePermissionRole.ADMIN) {
                player.sendSystemMessage(Component.translatable("gui.kve.perm.denied"));
                return;
            }
            if (msg.dimensionId.isEmpty()) goBack(player);
            else goToLab(player, msg.dimensionId);
        });
        ctx.setPacketHandled(true);
    }

    private static void goToLab(ServerPlayer player, String dimensionId) {
        ResourceLocation rl = ResourceLocation.tryParse(dimensionId);
        if (rl == null || !rl.getNamespace().equals("shovel_js") || !rl.getPath().startsWith("worldgen_preview_")) return;
        ServerLevel target = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
        if (target == null) {
            player.sendSystemMessage(Component.translatable("gui.kve.worldgen.preset.goto_missing", dimensionId));
            return;
        }
        int x = player.getBlockX(), z = player.getBlockZ();
        player.sendSystemMessage(Component.translatable("gui.kve.worldgen.preset.goto_generating"));
        target.getChunkSource().getChunkFuture(x >> 4, z >> 4, ChunkStatus.FULL, true)
                .thenAcceptAsync(result -> arrive(player, target, dimensionId, x, z), player.server);
    }

    private static void arrive(ServerPlayer player, ServerLevel target, String dimensionId, int x, int z) {
        if (player.isRemoved() || player.hasDisconnected()) return;
        if (player.level() != target) {
            RETURN_POINTS.put(player.getUUID(), new ReturnPoint(player.level().dimension(),
                    player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
        }
        int y = target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= target.getMinBuildHeight()) {
            y = target.getSeaLevel() + 1;
            target.setBlockAndUpdate(new BlockPos(x, y - 1, z), Blocks.GLASS.defaultBlockState());
        }
        player.teleportTo(target, x + 0.5, y, z + 0.5, player.getYRot(), player.getXRot());
        CraftEditorMod.LOGGER.info("[Shovel JS] {} went to worldgen lab dimension {}", player.getGameProfile().getName(), dimensionId);
        player.sendSystemMessage(Component.translatable("gui.kve.worldgen.preset.goto_done", dimensionId));
    }

    private static void goBack(ServerPlayer player) {
        ReturnPoint p = RETURN_POINTS.remove(player.getUUID());
        ServerLevel level = p != null ? player.server.getLevel(p.dimension()) : null;
        if (level == null) {
            level = player.server.overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
            player.teleportTo(level, spawn.getX() + 0.5, y, spawn.getZ() + 0.5, player.getYRot(), player.getXRot());
        } else {
            player.teleportTo(level, p.x(), p.y(), p.z(), p.yRot(), p.xRot());
        }
        player.sendSystemMessage(Component.translatable("gui.kve.worldgen.preset.back_done"));
    }
}
