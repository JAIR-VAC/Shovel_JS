package com.kve.common.structure;

import com.kve.CraftEditorMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class StructureSpawnCapRuntime {

    private StructureSpawnCapRuntime() {}

    private static final class Active {
        final BlockPos pos;
        final StructureSpawnCap cap;
        final EntityType<?> entityType;
        int ticksUntilNext;
        String lastBlockReason = null;

        Active(BlockPos pos, StructureSpawnCap cap, EntityType<?> entityType) {
            this.pos = pos;
            this.cap = cap;
            this.entityType = entityType;
            // El primer intento sale enseguida (1-3 s) en vez de
            // esperar el delay completo (10-40 s por defecto), que se leia
            // como "no funciona" al probarlo.
            this.ticksUntilNext = 20 + ThreadLocalRandom.current().nextInt(41);
        }
    }

    private static final Map<ResourceKey<Level>, List<Active>> ACTIVE = new HashMap<>();

    private static final Map<ResourceKey<Level>, Set<Long>> KNOWN_POS = new HashMap<>();

    /**
     * Registra un marker en una posicion de MUNDO absoluta (ya offseteada
     * por el origin de colocacion - el caller es responsable de esa
     * conversion, ver {@link StructureWorldIO#place}). No-op silencioso si
     * {@code cap.entityId} esta vacio o no corresponde a ningun
     * {@link EntityType} registrado (mismo criterio permisivo que el resto
     * de este mod usa para un id invalido en dato guardado por el
     * usuario - loguea y sigue, no revienta la colocacion completa), o si
     * esa posicion en esa dimension ya estaba registrada (evita duplicar
     * spawn rate al re-colocar la misma estructura, o al re-hidratar algo
     * que {@link #onLevelLoad()} ya cargo).
     */
    /** @return true si el marker quedo registrado ahora (false: invalido o ya estaba registrado en esa posicion). */
    public static boolean register(ServerLevel level, BlockPos worldPos, StructureSpawnCap cap) {
        return register(level, worldPos, cap, true);
    }

    private static boolean register(ServerLevel level, BlockPos worldPos, StructureSpawnCap cap, boolean persist) {
        if (cap == null || cap.entityId == null || cap.entityId.isBlank()) return false;
        ResourceLocation entityRl = ResourceLocation.tryParse(cap.entityId);
        EntityType<?> type = entityRl == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(entityRl).orElse(null);
        if (type == null) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Spawn cap con entityId invalido '{}' en {} - ignorado.", cap.entityId, worldPos);
            return false;
        }

        ResourceKey<Level> dim = level.dimension();
        Set<Long> known = KNOWN_POS.computeIfAbsent(dim, k -> new HashSet<>());
        BlockPos immutablePos = worldPos.immutable();
        if (!known.add(immutablePos.asLong())) return false;

        ACTIVE.computeIfAbsent(dim, k -> new ArrayList<>()).add(new Active(immutablePos, cap, type));
        if (persist) {
            StructureSpawnCapSavedData.get(level).add(immutablePos, cap);
        }
        return true;
    }

    @SubscribeEvent
    public static void onDatapackSync(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) JigsawSpawnCapBridge.clearCache();
    }

    @SubscribeEvent
    public static void onServerStarting(net.neoforged.neoforge.event.server.ServerStartingEvent event) {
        JigsawSpawnCapBridge.clearCache();
    }

    /**
     * Re-hidrata {@link #ACTIVE}/{@link #KNOWN_POS} desde
     * {@link StructureSpawnCapSavedData} cuando una dimension carga - asi
     * un restart de server no apaga los spawn caps ya colocados (ver
     * "Persistencia" en el javadoc de la clase). {@code persist=false} en
     * la llamada interna a {@link #register} evita re-escribir al
     * SavedData lo que se acaba de LEER de ahi mismo.
     */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        List<StructureSpawnCapSavedData.PersistedMarker> persisted =
                StructureSpawnCapSavedData.get(serverLevel).markersView();
        for (StructureSpawnCapSavedData.PersistedMarker m : persisted) {
            register(serverLevel, m.pos, m.cap, false);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        ResourceKey<Level> dim = serverLevel.dimension();
        ACTIVE.remove(dim);
        KNOWN_POS.remove(dim);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        List<Active> markers = ACTIVE.get(serverLevel.dimension());
        if (markers == null || markers.isEmpty()) return;

        for (Active active : markers) {
            if (--active.ticksUntilNext > 0) continue;
            active.ticksUntilNext = rollDelay(active.cap);
            attemptSpawn(serverLevel, active);
        }
    }

    private static int rollDelay(StructureSpawnCap cap) {
        int min = Math.min(cap.minSpawnDelayTicks, cap.maxSpawnDelayTicks);
        int max = Math.max(cap.minSpawnDelayTicks, cap.maxSpawnDelayTicks);
        if (max <= min) return Math.max(1, min);
        return min + ThreadLocalRandom.current().nextInt(max - min + 1);
    }

    private static void attemptSpawn(ServerLevel level, Active active) {
        StructureSpawnCap cap = active.cap;
        BlockPos pos = active.pos;

        if (cap.blockOnPeaceful && level.getDifficulty() == Difficulty.PEACEFUL) { blocked(active, "dificultad pacifica"); return; }
        if (cap.blockInDaylight && level.isDay()) { blocked(active, "es de dia"); return; }

        if (!level.hasNearbyAlivePlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, cap.requiredPlayerRange)) {
            blocked(active, "ningun jugador a menos de " + cap.requiredPlayerRange + " bloques");
            return;
        }
        // No tocar chunks sin cargar (el jugador cerca ya casi lo garantiza).
        if (!level.isLoaded(pos)) return;

        int spawnRange = Math.max(0, cap.spawnRange);
        AABB nearbyBox = new AABB(pos).inflate(spawnRange);
        long nearbyCount = level.getEntitiesOfClass(Entity.class, nearbyBox,
                e -> active.entityType.equals(e.getType())).size();
        if (nearbyCount >= cap.maxNearbyEntities) { blocked(active, "ya hay " + nearbyCount + " cerca (tope " + cap.maxNearbyEntities + ")"); return; }

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int toSpawn = Math.max(0, cap.spawnCount);
        int spawned = 0;
        String lastFail = "sin espacio libre con piso cerca del marker";
        for (int i = 0; i < toSpawn; i++) {
            Entity entity = active.entityType.create(level);
            if (entity == null) { lastFail = "la entidad no se pudo crear"; break; }
            boolean placed = false;
            for (int attempt = 0; attempt < 12 && !placed; attempt++) {
                int dx = attempt == 0 ? 0 : rand.nextInt(spawnRange * 2 + 1) - spawnRange;
                int dz = attempt == 0 ? 0 : rand.nextInt(spawnRange * 2 + 1) - spawnRange;
                for (int dy = 1; dy >= -2 && !placed; dy--) {
                    BlockPos feet = pos.offset(dx, 1 + dy, dz);
                    if (!level.isLoaded(feet)) continue;
                    entity.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, rand.nextFloat() * 360F, 0F);
                    if (!level.noCollision(entity, entity.getBoundingBox())) continue;
                    BlockPos below = feet.below();
                    boolean hasFloor = !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
                    if (!hasFloor && !entity.isNoGravity()) continue;
                    int light = level.getMaxLocalRawBrightness(feet);
                    if (light < cap.minLightLevel || light > cap.maxLightLevel) {
                        lastFail = "luz " + light + " fuera del rango " + cap.minLightLevel + "-" + cap.maxLightLevel;
                        continue;
                    }
                    placed = true;
                }
            }
            if (!placed) { entity.discard(); continue; }
            if (entity instanceof Mob mob) {
                net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(mob, level,
                        level.getCurrentDifficultyAt(entity.blockPosition()), MobSpawnType.STRUCTURE, null);
            }
            if (level.addFreshEntity(entity)) spawned++;
        }
        if (spawned == 0 && toSpawn > 0) blocked(active, lastFail);
        else active.lastBlockReason = null;
    }

    private static void blocked(Active active, String reason) {
        if (reason.equals(active.lastBlockReason)) return;
        active.lastBlockReason = reason;
        CraftEditorMod.LOGGER.info("[Shovel JS] spawn cap {} en {}: no spawnea ({}).", active.cap.entityId, active.pos, reason);
    }
}
