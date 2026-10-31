package com.kve.client.worldgen;

import com.google.gson.JsonElement;
import com.kve.CraftEditorMod;
import com.kve.common.worldgen.WorldGenEntry;
import com.kve.common.worldgen.WorldGenExporter;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@OnlyIn(Dist.CLIENT)
public final class WorldGenTestPlacer {

    private WorldGenTestPlacer() {}

    /** Resultado de una prueba. {@code error} != null = no se hizo nada. */
    public record Result(int features, int changedBlocks, String error) {}

    private record Change(BlockPos pos, BlockState before) {}

    private static List<Change> lastChanges = null;
    private static ResourceKey<Level> lastDimension = null;

    private static final int RADIUS = 1;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    public static boolean canUndo() { return lastChanges != null && !lastChanges.isEmpty(); }

    /** Coloca lo que se puede probar de {@code e} en el chunk del jugador. */
    public static CompletableFuture<Result> placeAtPlayer(WorldGenEntry e) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return CompletableFuture.completedFuture(new Result(0, 0, "no_local"));
        List<WorldGenExporter.TestFeature> features = WorldGenExporter.testFeatures(e);
        if (features.isEmpty()) return CompletableFuture.completedFuture(new Result(0, 0, "nothing"));
        ResourceKey<Level> dim = mc.player.level().dimension();
        ChunkPos center = mc.player.chunkPosition();
        return CompletableFuture.supplyAsync(() -> place(server, dim, center, features), server);
    }

    /** Vuelve cada bloque de la ultima prueba a como estaba. Devuelve cuantos. */
    public static CompletableFuture<Integer> undo() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || !canUndo()) return CompletableFuture.completedFuture(0);
        List<Change> changes = lastChanges;
        ResourceKey<Level> dim = lastDimension;
        return CompletableFuture.supplyAsync(() -> {
            ServerLevel level = server.getLevel(dim);
            if (level == null) return 0;
            for (Change c : changes) level.setBlock(c.pos(), c.before(), FLAGS);
            lastChanges = null;
            return changes.size();
        }, server);
    }

    private static Result place(MinecraftServer server, ResourceKey<Level> dim, ChunkPos center, List<WorldGenExporter.TestFeature> features) {
        ServerLevel level = server.getLevel(dim);
        if (level == null) return new Result(0, 0, "no_level");
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (!level.hasChunk(center.x + dx, center.z + dz)) return new Result(0, 0, "not_loaded");
            }
        }

        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        List<PlacedFeature> parsed = new ArrayList<>();
        for (WorldGenExporter.TestFeature f : features) {
            var res = PlacedFeature.DIRECT_CODEC.parse(ops, f.placed());
            if (res.result().isPresent()) {
                parsed.add(res.result().get());
            } else {
                String msg = res.error().map(err -> err.message()).orElse("?");
                CraftEditorMod.LOGGER.warn("[Shovel JS] Prueba de world gen: '{}' no se pudo armar: {}", f.label(), msg);
                return new Result(0, 0, f.label() + ": " + msg);
            }
        }

        int minY = level.getMinBuildHeight();
        int height = level.getHeight();
        List<BlockState[]> before = snapshot(level, center, minY, height);

        RandomSource random = RandomSource.create();
        BlockPos origin = new BlockPos(center.getMinBlockX(), minY, center.getMinBlockZ());
        var generator = level.getChunkSource().getGenerator();
        for (PlacedFeature pf : parsed) {
            try {
                pf.place(level, generator, random, origin);
            } catch (Exception ex) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Prueba de world gen: fallo al colocar: {}", ex.toString());
            }
        }

        List<Change> changes = applyChanges(level, center, minY, height, before);
        lastChanges = changes;
        lastDimension = dim;
        return new Result(parsed.size(), changes.size(), null);
    }

    private static List<BlockState[]> snapshot(ServerLevel level, ChunkPos center, int minY, int height) {
        List<BlockState[]> out = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                LevelChunk chunk = level.getChunk(center.x + dx, center.z + dz);
                BlockState[] states = new BlockState[16 * 16 * height];
                int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
                int i = 0;
                for (int y = 0; y < height; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            states[i++] = chunk.getBlockState(p.set(bx + x, minY + y, bz + z));
                        }
                    }
                }
                out.add(states);
            }
        }
        return out;
    }

    private static List<Change> applyChanges(ServerLevel level, ChunkPos center, int minY, int height, List<BlockState[]> before) {
        List<Change> changes = new ArrayList<>();
        int idx = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                LevelChunk chunk = level.getChunk(center.x + dx, center.z + dz);
                BlockState[] states = before.get(idx++);
                int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
                int i = 0;
                for (int y = 0; y < height; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState old = states[i++];
                            p.set(bx + x, minY + y, bz + z);
                            BlockState now = chunk.getBlockState(p);
                            if (now == old) continue;
                            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(p.getY()));
                            section.setBlockState(x, p.getY() & 15, z, old, false);
                            level.setBlock(p, now, FLAGS);
                            changes.add(new Change(p.immutable(), old));
                        }
                    }
                }
            }
        }
        return changes;
    }
}
