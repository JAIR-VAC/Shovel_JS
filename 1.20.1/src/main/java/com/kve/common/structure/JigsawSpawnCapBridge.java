package com.kve.common.structure;

import com.kve.CraftEditorMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import net.minecraft.server.MinecraftServer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class JigsawSpawnCapBridge {

    private JigsawSpawnCapBridge() {}

    public static void onPiecePlaced(ServerLevel level, ResourceLocation structureId,
                                      BlockPos anchorPos, StructurePlaceSettings settings) {
        if (structureId == null || level == null) return;

        List<CompoundTag> caps = capsFor(level.getServer(), structureId);
        if (caps.isEmpty()) return;

        List<BlockPos> worldPositions = new ArrayList<>(caps.size());
        List<StructureSpawnCap> parsed = new ArrayList<>(caps.size());
        for (CompoundTag capTag : caps) {
            ListTag posTag = capTag.getList("pos", 3 );
            if (posTag.size() < 3) continue;
            BlockPos localPos = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
            worldPositions.add(anchorPos.offset(transformLocal(localPos, settings)).immutable());
            parsed.add(parseCap(capTag));
        }
        if (parsed.isEmpty()) return;

        Runnable task = () -> {
            for (int i = 0; i < parsed.size(); i++) {
                StructureSpawnCap cap = parsed.get(i);
                BlockPos worldPos = worldPositions.get(i);
                if (StructureSpawnCapRuntime.register(level, worldPos, cap)) {
                    CraftEditorMod.LOGGER.info("[Shovel JS] spawn cap registrado (generacion natural): entity={} en {} (estructura {})",
                            cap.entityId, worldPos, structureId);
                }
            }
        };
        MinecraftServer server = level.getServer();
        if (server.isSameThread()) task.run();
        else server.execute(task);
    }

    /**
     * Cache de los tags {@code kve_spawn_caps} por estructura: postProcess/
     * place se llaman una vez POR CHUNK por pieza, y sin esto cada llamada
     * re-leia y descomprimia el .nbt completo (incluidas todas las piezas
     * vanilla sin markers). Lista vacia = "esta estructura no tiene markers".
     * Se limpia en cada recarga de datapacks ({@link #clearCache()}) para que
     * un .nbt re-guardado desde el editor se relea.
     */
    private static final Map<ResourceLocation, List<CompoundTag>> CACHE = new ConcurrentHashMap<>();

    public static void clearCache() {
        CACHE.clear();
    }

    private static List<CompoundTag> capsFor(MinecraftServer server, ResourceLocation structureId) {
        return CACHE.computeIfAbsent(structureId, id -> {
            byte[] raw = StructureNbtIO.readRawBytesFromServer(server, id);
            if (raw == null) return List.of();
            CompoundTag root;
            try (InputStream in = new ByteArrayInputStream(raw)) {
                root = NbtIo.readCompressed(in);
            } catch (IOException | RuntimeException ex) {
                // Nunca debe tumbar la generacion del mundo por esto.
                CraftEditorMod.LOGGER.warn("[Shovel JS] No se pudo releer .nbt crudo de {} para spawn caps: {}", id, ex.getMessage());
                return List.of();
            }
            ListTag spawnCapsTag = root.getList("shovel_js_spawn_caps", 10 );
            if (spawnCapsTag.isEmpty()) return List.of();
            List<CompoundTag> out = new ArrayList<>(spawnCapsTag.size());
            for (int i = 0; i < spawnCapsTag.size(); i++) out.add(spawnCapsTag.getCompound(i).copy());
            return List.copyOf(out);
        });
    }

    private static StructureSpawnCap parseCap(CompoundTag capTag) {
        StructureSpawnCap cap = new StructureSpawnCap();
        cap.entityId = capTag.getString("entity");
        cap.minSpawnDelayTicks = capTag.getInt("minSpawnDelayTicks");
        cap.maxSpawnDelayTicks = capTag.getInt("maxSpawnDelayTicks");
        cap.spawnCount = capTag.getInt("spawnCount");
        cap.maxNearbyEntities = capTag.getInt("maxNearbyEntities");
        cap.requiredPlayerRange = capTag.getInt("requiredPlayerRange");
        cap.spawnRange = capTag.getInt("spawnRange");
        cap.minLightLevel = capTag.getInt("minLightLevel");
        cap.maxLightLevel = capTag.getInt("maxLightLevel");
        cap.blockOnPeaceful = capTag.getBoolean("blockOnPeaceful");
        cap.blockInDaylight = capTag.getBoolean("blockInDaylight");
        return cap;
    }

    private static BlockPos transformLocal(BlockPos localPos, StructurePlaceSettings settings) {
        return StructureTemplate.transform(localPos, settings.getMirror(), settings.getRotation(), settings.getRotationPivot());
    }
}
