package com.kve.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The world-mutating
 * counterpart to {@link StructureNbtIO} (which only ever reads/writes a
 * grid to/from a local.nbt FILE, never a real {@code Level}). This class
 * is the only place in KVE that calls {@code ServerLevel#setBlock()} for
 * structure content, and it is ONLY ever invoked from server-side packet
 * handlers ({@code StructureWorldGenerateRequestPacket}/
 * {@code StructureWorldScanRequestPacket}) that have already verified the
 * sender is ADMIN (via {@code KvePermissionManager}) - this class itself
 * does no permission checking, same division of responsibility
 * {@code PermissionSetRolePacket} already uses (packet handler checks,
 * then delegates to a plain data-layer method that trusts its caller).
 * <p>
 * {@link #place} and {@link #scan} both use the exact same
 * "size/palette/blocks/entities/DataVersion" tag shape {@link StructureNbtIO#toTag}
 * writes and {@link StructureNbtIO#fromScanTag} reads, so a structure can
 * round-trip grid -&gt; place -&gt; (player edits with a pickaxe) -&gt; scan -&gt;
 * grid without this class needing to know anything about
 * {@code StructureVoxelGrid} itself - it only ever touches the tag shape
 * and the world.
 */
public final class StructureWorldIO {

    private StructureWorldIO() {}

    private static final String STATUE_MARKER_KEY = "shovel_js_structure_statue";

    /**
     * Places every cell of {@code root} (a {@link StructureNbtIO#toTag}
     * shaped tag) into {@code level} as real blocks, offset by
     * {@code origin} - {@code root}'s "pos" entries are local (0-based,
     * same convention {@link com.kve.common.structure.StructureVoxelGrid#normalizeOrigin()}
     * guarantees), so the real world position of each cell is
     * {@code origin + pos}. Any preserved block-entity nbt ("nbt" child of
     * a block entry - chest contents, spawner config, sign text...) is
     * reattached right after the block itself is set, same as
     * {@code StructureNbtIO#fromTag} does for the in-memory grid.
     *
     * @return how many cells were actually placed (skips a cell silently
     * if its palette index is out of range - a malformed/truncated tag
     * shouldn't half-crash a placement already in progress)
     */
    public static int place(ServerLevel level, BlockPos origin, CompoundTag root) {
        return place(level, origin, root, false);
    }

    public static int place(ServerLevel level, BlockPos origin, CompoundTag root, boolean inPlace) {
        return placeAndReport(level, origin, root, inPlace).placed();
    }

    /** Cuantos bloques se colocaron y en que origin quedo la estructura de verdad. */
    public record PlaceResult(int placed, BlockPos origin) {}

    /**
     * Igual que {@link #place(ServerLevel, BlockPos, CompoundTag, boolean)}, pero
 * devuelve tambien el origin final: con "Enterrar" (BURY) no es el, la
     * estructura baja (o sube) hasta el terreno. El editor lo necesita para que
     * su sesion y el fantasma queden donde quedaron los bloques.
     */
    public static PlaceResult placeAndReport(ServerLevel level, BlockPos origin, CompoundTag root, boolean inPlace) {
        ListTag paletteTag = root.getList("palette", 10 );
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        HolderGetter<Block> blockLookup = BuiltInRegistries.BLOCK.asLookup();
        for (int i = 0; i < paletteTag.size(); i++) {
            palette.add(NbtUtils.readBlockState(blockLookup, paletteTag.getCompound(i)));
        }

        ListTag blocksTag = root.getList("blocks", 10);
        int placed = 0;

        StructureTerrainAdjustment adjustment = StructureTerrainAdjustment.parse(root.getString("shovel_js_terrain_adjustment"));
        Map<Long, Integer> footprintBottomLocalY = new HashMap<>();
        BlockPos placeOrigin = origin;
        TerrainSample terrainSample = null;
        if (adjustment != StructureTerrainAdjustment.NONE) {
            for (int i = 0; i < blocksTag.size(); i++) {
                CompoundTag e = blocksTag.getCompound(i);
                ListTag posTag = e.getList("pos", 3);
                if (posTag.size() < 3) continue;
                int stateIdx = e.getInt("state");
                if (stateIdx < 0 || stateIdx >= palette.size()) continue;
                if (palette.get(stateIdx).is(Blocks.STRUCTURE_VOID)) continue;
                int lx = posTag.getInt(0), ly = posTag.getInt(1), lz = posTag.getInt(2);
                long key = columnKey(lx, lz);
                footprintBottomLocalY.merge(key, ly, Math::min);
            }
            terrainSample = sampleTerrain(level, origin, footprintBottomLocalY, adjustment);
            if (adjustment == StructureTerrainAdjustment.BURY && !terrainSample.groundTopWorldY.isEmpty()) {
                int minDiff = Integer.MAX_VALUE;
                for (Map.Entry<Long, Integer> col : footprintBottomLocalY.entrySet()) {
                    Integer groundY = terrainSample.groundTopWorldY.get(col.getKey());
                    if (groundY == null) continue;
                    int structureBottomWorldY = origin.getY() + col.getValue();
                    int diff = (groundY + 1) - structureBottomWorldY;
                    if (diff < minDiff) minDiff = diff;
                }
                if (minDiff != Integer.MAX_VALUE && minDiff != 0) {
                    placeOrigin = origin.offset(0, minDiff, 0);
                }
            }
        }
        final BlockPos origin0 = placeOrigin;

        List<BlockPos> placedPositions = new ArrayList<>();
        List<BlockState> placedStates = new ArrayList<>();

        BlockPos oldMin = null;
        BlockPos oldMax = null;
        BlockPos remapDelta = BlockPos.ZERO;
        if (root.contains("shovel_js_scan_origin", 9 )) {
            ListTag originTag = root.getList("shovel_js_scan_origin", 3 );
            ListTag sizeTagForRemap = root.getList("size", 3 );
            if (originTag.size() >= 3 && sizeTagForRemap.size() >= 3) {
                oldMin = new BlockPos(originTag.getInt(0), originTag.getInt(1), originTag.getInt(2));
                oldMax = oldMin.offset(sizeTagForRemap.getInt(0) - 1, sizeTagForRemap.getInt(1) - 1, sizeTagForRemap.getInt(2) - 1);
                remapDelta = new BlockPos(origin0.getX() - oldMin.getX(), origin0.getY() - oldMin.getY(), origin0.getZ() - oldMin.getZ());
            }
        }

        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag blockEntry = blocksTag.getCompound(i);
            ListTag posTag = blockEntry.getList("pos", 3 );
            if (posTag.size() < 3) continue;
            int stateIdx = blockEntry.getInt("state");
            if (stateIdx < 0 || stateIdx >= palette.size()) continue;

            BlockState state = palette.get(stateIdx);
            if (state.is(net.minecraft.world.level.block.Blocks.STRUCTURE_VOID)) continue;

            BlockPos worldPos = origin0.offset(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
            level.setBlock(worldPos, state, Block.UPDATE_CLIENTS);
            placed++;
            placedPositions.add(worldPos);
            placedStates.add(state);

            if (blockEntry.contains("nbt", 10 )) {
                BlockEntity be = level.getBlockEntity(worldPos);
                if (be != null) {
                    CompoundTag teTag = blockEntry.getCompound("nbt").copy();
                    if (oldMin != null) remapAbsolutePositions(teTag, oldMin, oldMax, remapDelta);
                    teTag.putInt("x", worldPos.getX());
                    teTag.putInt("y", worldPos.getY());
                    teTag.putInt("z", worldPos.getZ());
                    be.load(teTag);
                }
            }
        }

        if (adjustment == StructureTerrainAdjustment.BEARD_THIN
                || adjustment == StructureTerrainAdjustment.BEARD_BOX
                || adjustment == StructureTerrainAdjustment.ENCAPSULATE
                || adjustment == StructureTerrainAdjustment.BIOME_BLEND) {
            fillTerrainGaps(level, origin0, footprintBottomLocalY, terrainSample, adjustment, placedPositions, placedStates);
        }

        // Segunda pasada: ahora que TODA la estructura (soporte incluido)
        // ya esta en el mundo, recien avisar a los vecinos de cada celda -
        // un bloque roto/modded que reviente aca nunca debe tirar abajo el
        // resto de la actualizacion de la estructura ya colocada.
        for (int i = 0; i < placedPositions.size(); i++) {
            try {
                level.blockUpdated(placedPositions.get(i), placedStates.get(i).getBlock());
            } catch (Exception ex) {
                // one broken/mod block's neighbor update must never abort the rest
            }
        }

        placeEntitiesAndSpawnCaps(level, origin0, root);

        return new PlaceResult(placed, origin0);
    }

    private static void placeEntitiesAndSpawnCaps(ServerLevel level, BlockPos origin, CompoundTag root) {
        ListTag entitiesTag = root.getList("entities", 10 );
        for (int i = 0; i < entitiesTag.size(); i++) {
            CompoundTag entry = entitiesTag.getCompound(i);
            if (!entry.contains("nbt", 10 )) continue;
            ListTag posTag = entry.getList("pos", 6 );
            if (posTag.size() < 3) continue;
            double relX = posTag.getDouble(0), relY = posTag.getDouble(1), relZ = posTag.getDouble(2);

            CompoundTag entityData = entry.getCompound("nbt").copy();
            entityData.remove("UUID");

            try {
                Entity entity = EntityType.loadEntityRecursive(entityData, level, e -> {
                    e.moveTo(origin.getX() + relX, origin.getY() + relY, origin.getZ() + relZ,
                            e.getYRot(), e.getXRot());
                    return e;
                });
                if (entity == null) continue;

                if (entity instanceof Mob mobEntity) {
                    entity.getPersistentData().putBoolean(STATUE_MARKER_KEY, true);
                    ListTag sizeTag = root.getList("size", 3 );
                    double sizeY = sizeTag.size() >= 2 ? sizeTag.getInt(1) : 8;
                    AABB nearby = new AABB(
                            entity.getX() - 1.5, entity.getY() - sizeY - 2, entity.getZ() - 1.5,
                            entity.getX() + 1.5, entity.getY() + sizeY + 2, entity.getZ() + 1.5);
                    for (Mob old : level.getEntitiesOfClass(Mob.class, nearby,
                            m -> !m.isRemoved() && m != mobEntity && m.getType() == entity.getType()
                                    && (m.isNoAi() || m.getPersistentData().getBoolean(STATUE_MARKER_KEY)))) {
                        old.discard();
                    }
                }

                level.addFreshEntity(entity);
            } catch (Exception ex) {
                // A broken/foreign-mod entity must never take down the whole placement.
            }
        }

        ListTag spawnCapsTag = root.getList("shovel_js_spawn_caps", 10 );
        for (int i = 0; i < spawnCapsTag.size(); i++) {
            CompoundTag capTag = spawnCapsTag.getCompound(i);
            ListTag posTag = capTag.getList("pos", 3 );
            if (posTag.size() < 3) continue;
            BlockPos worldPos = origin.offset(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));

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
            StructureSpawnCapRuntime.register(level, worldPos, cap);
        }
    }

    public static final class ScanResult {
        public final CompoundTag tag;
        public final BlockPos occupiedMin;
        public final BlockPos occupiedMax;

        ScanResult(CompoundTag tag, BlockPos occupiedMin, BlockPos occupiedMax) {
            this.tag = tag;
            this.occupiedMin = occupiedMin;
            this.occupiedMax = occupiedMax;
        }
    }

    /**
     * Reads every non-air block in the inclusive world-space box
     * {@code [origin, origin + (sizeX-1, sizeY-1, sizeZ-1)]} back out of
     * {@code level}, trims to the tightest bounding box actually occupied
     * (shrink-to-fit - a player who broke blocks near an edge since the
     * last scan gets a smaller structure back, not a box padded with air
     * from the old, larger bounds), and returns it in the same tag shape
     * {@link StructureNbtIO#toTag} produces, re-based so the trimmed
     * region's own minimum corner is local {@code (0,0,0)} - matching
     * {@code StructureVoxelGrid#normalizeOrigin()}'s convention, since
     * this tag is fed straight into {@link StructureNbtIO#fromScanTag}
     * client-side, which expects exactly that.
     */
    public static boolean isBorderSealed(ServerLevel level, BlockPos origin, int sizeX, int sizeY, int sizeZ) {
        int padSizeX = sizeX + 2, padSizeY = sizeY + 2, padSizeZ = sizeZ + 2;
        BlockPos padOrigin = origin.offset(-1, -1, -1);

        Set<Long> visited = new HashSet<>();
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();

        for (int x = 0; x < padSizeX; x++) {
            for (int y = 0; y < padSizeY; y++) {
                for (int z = 0; z < padSizeZ; z++) {
                    boolean onPadShell = x == 0 || x == padSizeX - 1 || y == 0 || y == padSizeY - 1
                            || z == 0 || z == padSizeZ - 1;
                    if (!onPadShell) continue;
                    seedIfAir(level, padOrigin, x, y, z, visited, queue);
                }
            }
        }

        int[] dx = {1, -1, 0, 0, 0, 0};
        int[] dy = {0, 0, 1, -1, 0, 0};
        int[] dz = {0, 0, 0, 0, 1, -1};
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            boolean insideOriginalBox = cur[0] >= 1 && cur[0] <= sizeX
                    && cur[1] >= 1 && cur[1] <= sizeY
                    && cur[2] >= 1 && cur[2] <= sizeZ;
            if (insideOriginalBox) return false;
            for (int d = 0; d < 6; d++) {
                int nx = cur[0] + dx[d], ny = cur[1] + dy[d], nz = cur[2] + dz[d];
                if (nx < 0 || nx >= padSizeX || ny < 0 || ny >= padSizeY || nz < 0 || nz >= padSizeZ) continue;
                seedIfAir(level, padOrigin, nx, ny, nz, visited, queue);
            }
        }
        return true;
    }

    private static void seedIfAir(ServerLevel level, BlockPos padOrigin, int x, int y, int z,
                                   Set<Long> visited, java.util.ArrayDeque<int[]> queue) {
        long key = (((long) x) << 40) | (((long) y) << 20) | (long) z;
        if (!visited.add(key)) return;
        if (!level.getBlockState(padOrigin.offset(x, y, z)).isAir()) return;
        queue.add(new int[]{x, y, z});
    }

    public static ScanResult scan(ServerLevel level, BlockPos origin, int sizeX, int sizeY, int sizeZ) {
        return scan(level, origin, sizeX, sizeY, sizeZ, true);
    }

    public static ScanResult scan(ServerLevel level, BlockPos origin, int sizeX, int sizeY, int sizeZ,
                                   boolean includeMobs, boolean includeAir) {
        return scanInternal(level, origin, sizeX, sizeY, sizeZ, includeMobs, includeAir);
    }

    public static ScanResult scan(ServerLevel level, BlockPos origin, int sizeX, int sizeY, int sizeZ, boolean includeMobs) {
        return scanInternal(level, origin, sizeX, sizeY, sizeZ, includeMobs, true);
    }

    private static ScanResult scanInternal(ServerLevel level, BlockPos origin, int sizeX, int sizeY, int sizeZ,
                                            boolean includeMobs, boolean includeAir) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    BlockPos p = origin.offset(x, y, z);
                    if (level.getBlockState(p).isAir()) continue;
                    minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
                }
            }
        }
        if (minX > maxX) return new ScanResult(null, null, null);

        Map<BlockState, Integer> paletteIndex = new LinkedHashMap<>();
        ListTag paletteTag = new ListTag();
        ListTag blocksTag = new ListTag();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos worldPos = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(worldPos);
                    if (!includeAir && state.isAir()) continue;
                    int lx = x - minX, ly = y - minY, lz = z - minZ;
                    Integer idx = paletteIndex.get(state);
                    if (idx == null) {
                        idx = paletteTag.size();
                        paletteIndex.put(state, idx);
                        paletteTag.add(NbtUtils.writeBlockState(state));
                    }
                    CompoundTag blockTag = new CompoundTag();
                    blockTag.put("pos", intList(lx, ly, lz));
                    blockTag.putInt("state", idx);

                    BlockEntity be = level.getBlockEntity(worldPos);
                    if (be != null) {
                        blockTag.put("nbt", be.saveWithFullMetadata());
                    }
                    blocksTag.add(blockTag);
                }
            }
        }

        ListTag entitiesTag = new ListTag();
        if (includeMobs) {
            AABB box = new AABB(
                    origin.getX() + minX, origin.getY() + minY, origin.getZ() + minZ,
                    origin.getX() + maxX + 1, origin.getY() + maxY + 1, origin.getZ() + maxZ + 1);
            List<Mob> mobs = level.getEntitiesOfClass(Mob.class, box, m -> !m.isRemoved());
            for (Mob mob : mobs) {
                double relX = mob.getX() - (origin.getX() + minX);
                double relY = mob.getY() - (origin.getY() + minY);
                double relZ = mob.getZ() - (origin.getZ() + minZ);

                CompoundTag entry = new CompoundTag();
                ListTag posTag = new ListTag();
                posTag.add(DoubleTag.valueOf(relX));
                posTag.add(DoubleTag.valueOf(relY));
                posTag.add(DoubleTag.valueOf(relZ));
                entry.put("pos", posTag);
                entry.put("blockPos", intList(
                        Mth.floor(relX), Mth.floor(relY), Mth.floor(relZ)));

                CompoundTag entityData = new CompoundTag();
                mob.saveWithoutId(entityData);
                entityData.putString("id", EntityType.getKey(mob.getType()).toString());
                entry.put("nbt", entityData);
                entitiesTag.add(entry);
            }
        }

        CompoundTag root = new CompoundTag();
        root.put("size", intList(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1));
        root.put("palette", paletteTag);
        root.put("blocks", blocksTag);
        root.put("entities", entitiesTag);
        root.putBoolean("shovel_js_air_explicit", true);

        BlockPos worldMin = origin.offset(minX, minY, minZ);
        BlockPos worldMax = origin.offset(maxX, maxY, maxZ);
        root.put("shovel_js_scan_origin", intList(worldMin.getX(), worldMin.getY(), worldMin.getZ()));
        root.putString("shovel_js_scan_dim", level.dimension().location().toString());
        return new ScanResult(root, worldMin, worldMax);
    }

    private static void remapAbsolutePositions(net.minecraft.nbt.Tag tag, BlockPos oldMin, BlockPos oldMax, BlockPos delta) {
        if (tag instanceof CompoundTag ct) {
            if (ct.contains("x", 3 ) && ct.contains("y", 3) && ct.contains("z", 3)) {
                int x = ct.getInt("x"), y = ct.getInt("y"), z = ct.getInt("z");
                if (withinBounds(x, y, z, oldMin, oldMax)) {
                    ct.putInt("x", x + delta.getX());
                    ct.putInt("y", y + delta.getY());
                    ct.putInt("z", z + delta.getZ());
                }
            }
            for (String key : ct.getAllKeys()) {
                remapAbsolutePositions(ct.get(key), oldMin, oldMax, delta);
            }
        } else if (tag instanceof ListTag lt) {
            for (int i = 0; i < lt.size(); i++) {
                remapAbsolutePositions(lt.get(i), oldMin, oldMax, delta);
            }
        } else if (tag instanceof net.minecraft.nbt.IntArrayTag iat) {
            int[] arr = iat.getAsIntArray();
            if (arr.length == 3 && withinBounds(arr[0], arr[1], arr[2], oldMin, oldMax)) {
                arr[0] += delta.getX();
                arr[1] += delta.getY();
                arr[2] += delta.getZ();
            }
        }
    }

    private static boolean withinBounds(int x, int y, int z, BlockPos min, BlockPos max) {
        return x >= min.getX() && x <= max.getX()
                && y >= min.getY() && y <= max.getY()
                && z >= min.getZ() && z <= max.getZ();
    }

    private static ListTag intList(int x, int y, int z) {
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(x));
        list.add(IntTag.valueOf(y));
        list.add(IntTag.valueOf(z));
        return list;
    }

    private static long columnKey(int x, int z) {
        return (((long) x) << 32) | (z & 0xFFFFFFFFL);
    }

    private static int decodeX(long key) { return (int) (key >> 32); }
    private static int decodeZ(long key) { return (int) (key & 0xFFFFFFFFL); }

    private static final class TerrainSample {
        final Map<Long, Integer> groundTopWorldY = new HashMap<>();
        final Map<Long, BlockState> fillerState = new HashMap<>();
        /** columna -> decoracion de superficie que habia justo arriba del terreno (pasto alto/flor/nieve) - solo se llena en BIOME_BLEND, para re-colocarla despues del relleno en vez de perderla. */
        final Map<Long, BlockState> decorationState = new HashMap<>();
        final Set<Long> ringColumns = new HashSet<>();
    }

    /**
     * Muestrea el mundo real UNA sola vez, antes de tocar nada. Para
     * BURY esto le da a {@link #place} la altura de terreno bajo cada
     * columna de la huella (para calcular cuanto bajar el origin). Para
     * BEARD_ / ENCAPSULATE / BIOME_BLEND ademas guarda que bloque usar de
     * relleno - en BIOME_BLEND ese relleno es el mas comun de una
     * vecindad 3x3 (evita agarrar un bloque aislado raro como filler) y
     * ademas guarda la decoracion de superficie para no perderla.
     */
    private static TerrainSample sampleTerrain(ServerLevel level, BlockPos origin, Map<Long, Integer> footprintBottomLocalY, StructureTerrainAdjustment adjustment) {
        TerrainSample sample = new TerrainSample();
        boolean wantRing = adjustment == StructureTerrainAdjustment.ENCAPSULATE || adjustment == StructureTerrainAdjustment.BIOME_BLEND;
        Set<Long> toSample = new HashSet<>(footprintBottomLocalY.keySet());
        if (wantRing) {
            for (long key : footprintBottomLocalY.keySet()) {
                int lx = decodeX(key), lz = decodeZ(key);
                long[] neighbors = {columnKey(lx - 1, lz), columnKey(lx + 1, lz), columnKey(lx, lz - 1), columnKey(lx, lz + 1)};
                for (long nk : neighbors) {
                    if (!footprintBottomLocalY.containsKey(nk) && toSample.add(nk)) {
                        sample.ringColumns.add(nk);
                    }
                }
            }
        }

        boolean blend = adjustment == StructureTerrainAdjustment.BIOME_BLEND;
        for (long key : toSample) {
            int lx = decodeX(key), lz = decodeZ(key);
            int worldX = origin.getX() + lx;
            int worldZ = origin.getZ() + lz;
            int groundTopY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ) - 1;
            BlockPos groundPos = new BlockPos(worldX, groundTopY, worldZ);
            BlockState filler = level.getBlockState(groundPos);
            if (filler.isAir()) filler = Blocks.DIRT.defaultBlockState();

            if (blend) {
                Map<BlockState, Integer> counts = new HashMap<>();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int nx = worldX + dx, nz = worldZ + dz;
                        int ny = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz) - 1;
                        BlockState s = level.getBlockState(new BlockPos(nx, ny, nz));
                        if (!s.isAir()) counts.merge(s, 1, Integer::sum);
                    }
                }
                BlockState majority = filler;
                int best = 0;
                for (Map.Entry<BlockState, Integer> e : counts.entrySet()) {
                    if (e.getValue() > best) { best = e.getValue(); majority = e.getKey(); }
                }
                filler = majority;

                BlockState above = level.getBlockState(groundPos.above());
                if (!above.isAir()) sample.decorationState.put(key, above);
            }

            sample.groundTopWorldY.put(key, groundTopY);
            sample.fillerState.put(key, filler);
        }
        return sample;
    }

    /**
     * Rellena, columna por columna, el hueco entre la base de la
     * estructura y el terreno real - solo huecos GENUINOS (nunca pisa un
     * bloque que ya este ahi, ver {@link #fillColumn()}). Corre despues de
     * colocar la estructura, usando alturas/bloques ya muestreados por
     * {@link #sampleTerrain()} antes de que la estructura tocara el mundo.
     */
    private static void fillTerrainGaps(ServerLevel level, BlockPos origin0, Map<Long, Integer> footprintBottomLocalY,
                                         TerrainSample sample, StructureTerrainAdjustment adjustment,
                                         List<BlockPos> placedPositions, List<BlockState> placedStates) {
        if (sample == null || footprintBottomLocalY.isEmpty()) return;
        boolean thinOnly = adjustment == StructureTerrainAdjustment.BEARD_THIN;

        for (Map.Entry<Long, Integer> col : footprintBottomLocalY.entrySet()) {
            long key = col.getKey();
            Integer groundTopY = sample.groundTopWorldY.get(key);
            if (groundTopY == null) continue;
            int worldX = origin0.getX() + decodeX(key);
            int worldZ = origin0.getZ() + decodeZ(key);
            int structureBottomWorldY = origin0.getY() + col.getValue();
            fillColumn(level, worldX, worldZ, groundTopY, structureBottomWorldY, sample.fillerState.get(key), thinOnly, placedPositions, placedStates);
        }

        if (adjustment == StructureTerrainAdjustment.ENCAPSULATE || adjustment == StructureTerrainAdjustment.BIOME_BLEND) {
            int structureBaseLocalY = Integer.MAX_VALUE;
            for (int v : footprintBottomLocalY.values()) structureBaseLocalY = Math.min(structureBaseLocalY, v);
            int structureBottomWorldY = origin0.getY() + structureBaseLocalY;

            for (long key : sample.ringColumns) {
                Integer groundTopY = sample.groundTopWorldY.get(key);
                if (groundTopY == null) continue;
                int worldX = origin0.getX() + decodeX(key);
                int worldZ = origin0.getZ() + decodeZ(key);
                fillColumn(level, worldX, worldZ, groundTopY, structureBottomWorldY, sample.fillerState.get(key), false, placedPositions, placedStates);

                BlockState decoration = sample.decorationState.get(key);
                if (decoration != null) {
                    BlockPos decPos = new BlockPos(worldX, structureBottomWorldY, worldZ);
                    if (level.getBlockState(decPos).isAir()) {
                        level.setBlock(decPos, decoration, Block.UPDATE_CLIENTS);
                        placedPositions.add(decPos);
                        placedStates.add(decoration);
                    }
                }
            }
        }
    }

    private static void fillColumn(ServerLevel level, int worldX, int worldZ, int groundTopY, int structureBottomWorldY,
                                    BlockState filler, boolean thinOnly, List<BlockPos> placedPositions, List<BlockState> placedStates) {
        if (filler == null) return;
        int gapTop = structureBottomWorldY - 1;
        int gapBottom = thinOnly ? gapTop : groundTopY + 1;
        if (gapTop < gapBottom) return;
        for (int y = gapBottom; y <= gapTop; y++) {
            BlockPos p = new BlockPos(worldX, y, worldZ);
            if (!level.getBlockState(p).isAir()) continue;
            level.setBlock(p, filler, Block.UPDATE_CLIENTS);
            placedPositions.add(p);
            placedStates.add(filler);
        }
    }
}
