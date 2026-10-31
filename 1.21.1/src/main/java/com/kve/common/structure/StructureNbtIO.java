package com.kve.common.structure;

import com.kve.CraftEditorMod;
import com.kve.common.structure.StructureVoxelGrid;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class StructureNbtIO {

    private StructureNbtIO() {}

    private static final int FALLBACK_DATA_VERSION = 3465;

    /**
     * Resolves the on-disk path for a structure id, matching I/O
     * section: {@code kubejs/data/<ns>/structures/<path>.nbt}. Relative to
     * the game's working directory, same convention already used by
     * {@code CraftEditorState} elsewhere in this mod.
     */
    public static Path resolvePath(ResourceLocation id) {
        Path root = Paths.get("kubejs", "data");
        return com.kve.common.security.KvePathGuard.resolveResource(
                root, id.getNamespace(), id.getPath(), ".nbt", "structure");
    }

    /**
     * Builds the same "size/palette/blocks/entities/DataVersion" tag shape
     * {@link #save} writes to disk, WITHOUT touching the filesystem or
     * calling {@link StructureVoxelGrid#normalizeOrigin()} - callers that
     * need a normalized origin (like {@code save} itself) must do that
     * first. Pulled out of {@code save} so the "generar en el mundo"
     * world-build flow ({@code StructureWorldGenerateRequestPacket}) can
     * serialize the CURRENT in-editor grid (including edits not yet
     * Applied to disk) to send over the network for the server to place
     * as real blocks - it needs exactly this tag shape since
     * {@code StructureWorldIO#place} reads it with the same
     * {@code NbtUtils.readBlockState()}/palette-index logic {@link #fromTag()}
     * already uses locally.
     */
    public static CompoundTag toTag(StructureVoxelGrid grid) {
        int[] size = grid.computeSize();
        if (size == null) size = new int[]{0, 0, 0};

        Map<BlockState, Integer> paletteIndex = new LinkedHashMap<>();
        ListTag paletteTag = new ListTag();
        ListTag blocksTag = new ListTag();

        for (Map.Entry<BlockPos, BlockState> e : grid.cellsView().entrySet()) {
            BlockState state = e.getValue();
            Integer idx = paletteIndex.get(state);
            if (idx == null) {
                idx = paletteTag.size();
                paletteIndex.put(state, idx);
                paletteTag.add(NbtUtils.writeBlockState(state));
            }
            BlockPos p = e.getKey();
            CompoundTag blockTag = new CompoundTag();
            blockTag.put("pos", intList(p.getX(), p.getY(), p.getZ()));
            blockTag.putInt("state", idx);
            CompoundTag te = grid.getBlockEntityNbt(p);
            if (te != null) blockTag.put("nbt", te.copy());
            blocksTag.add(blockTag);
        }

        ListTag entitiesTag = new ListTag();
        for (CompoundTag entity : grid.scannedEntitiesView()) {
            entitiesTag.add(entity.copy());
        }

        CompoundTag root = new CompoundTag();
        root.put("size", intList(size[0], size[1], size[2]));
        root.put("palette", paletteTag);
        root.put("blocks", blocksTag);
        root.put("entities", entitiesTag);
        root.putInt("DataVersion", currentDataVersion());
        root.putBoolean("shovel_js_air_explicit", true);
        if (grid.getScanOrigin() != null) {
            BlockPos so = grid.getScanOrigin();
            root.put("shovel_js_scan_origin", intList(so.getX(), so.getY(), so.getZ()));
            root.putString("shovel_js_scan_dim", grid.getScanDimension());
        }
        if (!"NONE".equals(grid.getTerrainAdjustment())) {
            root.putString("shovel_js_terrain_adjustment", grid.getTerrainAdjustment());
        }
        ListTag spawnCapsTag = new ListTag();
        for (Map.Entry<BlockPos, StructureSpawnCap> e : grid.spawnCapsView().entrySet()) {
            BlockPos p = e.getKey();
            StructureSpawnCap cap = e.getValue();
            CompoundTag capTag = new CompoundTag();
            capTag.put("pos", intList(p.getX(), p.getY(), p.getZ()));
            capTag.putString("entity", cap.entityId);
            capTag.putInt("minSpawnDelayTicks", cap.minSpawnDelayTicks);
            capTag.putInt("maxSpawnDelayTicks", cap.maxSpawnDelayTicks);
            capTag.putInt("spawnCount", cap.spawnCount);
            capTag.putInt("maxNearbyEntities", cap.maxNearbyEntities);
            capTag.putInt("requiredPlayerRange", cap.requiredPlayerRange);
            capTag.putInt("spawnRange", cap.spawnRange);
            capTag.putInt("minLightLevel", cap.minLightLevel);
            capTag.putInt("maxLightLevel", cap.maxLightLevel);
            capTag.putBoolean("blockOnPeaceful", cap.blockOnPeaceful);
            capTag.putBoolean("blockInDaylight", cap.blockInDaylight);
            spawnCapsTag.add(capTag);
        }
        root.put("shovel_js_spawn_caps", spawnCapsTag);
        return root;
    }

    public static void save(StructureVoxelGrid grid, ResourceLocation id) throws IOException {
        grid.normalizeOrigin();
        if (grid.computeSize() == null) {
            throw new IOException("La estructura esta vacia; no hay nada que guardar.");
        }
        CompoundTag root = toTag(grid);

        Path path = resolvePath(id);
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp.toFile())) {
            NbtIo.writeCompressed(root, fos);
            try {
                fos.getFD().sync();
            } catch (IOException syncEx) {
                CraftEditorMod.LOGGER.warn(
                        "[Shovel JS] fsync fallo al guardar {} (los datos ya estan escritos, se continua igual): {}",
                        id, syncEx.getMessage());
            }
        }
        try {
            Files.move(tmp, path,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            // Backstop: algunos filesystems/configuraciones no soportan
            // atomic move (ej. tmp y destino en volumenes distintos, poco
            // probable aca porque resolveSibling() los deja en el mismo
            // directorio, pero mejor no asumir en produccion). Sin la flag
            // vuelve al comportamiento anterior - sigue siendo mejor que
            // nada, y con el fsync de arriba al menos el contenido en si ya
            // esta completo en disco antes de este segundo intento.
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        grid.markClean();
        CraftEditorMod.LOGGER.info("[Shovel JS] Estructura guardada: {} ({} celdas) -> {}",
                id, grid.cellCount(), path.toAbsolutePath());

        try {
            StructureUsageIndex.updateForStructure(id, grid);
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Estructura {} guardada OK, pero fallo la actualizacion del indice de uso: {}", id, ex.getMessage());
        }
    }

    public static StructureVoxelGrid load(ResourceLocation id) throws IOException {
        Path path = resolvePath(id);
        if (!Files.exists(path)) {
            throw new IOException("No existe el archivo de estructura: " + path.toAbsolutePath());
        }
        CompoundTag root;
        try (InputStream in = Files.newInputStream(path)) {
            root = NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        }
        return fromTag(root, id);
    }

    /** Result of {@link #loadEffective()}: the loaded grid plus where it came from. */
    public static final class LoadResult {
        public final StructureVoxelGrid grid;
        public final boolean fromOverride;

        LoadResult(StructureVoxelGrid grid, boolean fromOverride) {
            this.grid = grid;
            this.fromOverride = fromOverride;
        }
    }

    public static LoadResult loadEffective(ResourceLocation id) throws IOException {
        if (exists(id)) {
            return new LoadResult(load(id), true);
        }
        CompoundTag root = readFromGameResources(id);
        if (root == null) {
            throw new IOException("No hay override ni recurso original para " + id
                    + " (si es de un mod/datapack, hace falta un mundo cargado en este cliente para leerlo).");
        }
        return new LoadResult(fromTag(root, id), false);
    }

    public static LoadResult fromRemoteBytes(byte[] compressed, ResourceLocation id) throws IOException {
        CompoundTag root;
        try (InputStream in = new java.io.ByteArrayInputStream(compressed)) {
            root = NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        }
        return new LoadResult(fromTag(root, id), false);
    }

    private static CompoundTag readFromGameResources(ResourceLocation id) {
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        ResourceLocation resPath = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "structure/" + id.getPath() + ".nbt");
        try {
            var resOpt = server.getResourceManager().getResource(resPath);
            if (resOpt.isEmpty()) return null;
            try (InputStream in = resOpt.get().open()) {
                return NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            }
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] No se pudo leer el recurso de estructura original {}: {}", id, ex.getMessage());
            return null;
        }
    }

    /**
     * Server-side counterpart to {@link #readFromGameResources()}: reads the
     * same {@code data/<ns>/structures/<path>.nbt} resource, but via an
     * already-known {@code MinecraftServer} (a dedicated server has one,
     * unlike a remote client) rather than the client's integrated server.
     * This is what answers a {@code StructureRemoteRequestPacket} - see
     * {@link #fromRemoteBytes()} for the client-side half of that round
     * trip. Returns null (never throws) if the resource doesn't exist or
     * can't be read; the packet handler turns that into a "not found"
     * response rather than an error.
     */
    public static byte[] readRawBytesFromServer(net.minecraft.server.MinecraftServer server, ResourceLocation id) {
        if (server == null) return null;
        ResourceLocation resPath = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "structure/" + id.getPath() + ".nbt");
        try {
            var resOpt = server.getResourceManager().getResource(resPath);
            if (resOpt.isEmpty()) return null;
            try (InputStream in = resOpt.get().open()) {
                return in.readAllBytes();
            }
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] No se pudo leer el recurso de estructura (remoto) {}: {}", id, ex.getMessage());
            return null;
        }
    }

    public static List<ResourceLocation> listAvailableIds() {
        TreeMap<String, ResourceLocation> byName = new TreeMap<>();
        for (ResourceLocation id : scanOverrideIds()) byName.put(id.toString(), id);

        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            try {
                Map<ResourceLocation, ?> found = server.getResourceManager()
                        .listResources("structure", path -> path.getPath().endsWith(".nbt"));
                for (ResourceLocation key : found.keySet()) {
                    ResourceLocation id = idFromResourcePath(key);
                    if (id != null) byName.putIfAbsent(id.toString(), id);
                }
            } catch (Exception ex) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] No se pudo listar estructuras del juego: {}", ex.getMessage());
            }
        }
        return new ArrayList<>(byName.values());
    }

    private static ResourceLocation idFromResourcePath(ResourceLocation key) {
        String path = key.getPath();
        String prefix = "structure/";
        String suffix = ".nbt";
        if (!path.startsWith(prefix) || !path.endsWith(suffix)) return null;
        String idPath = path.substring(prefix.length(), path.length() - suffix.length());
        try {
            return ResourceLocation.fromNamespaceAndPath(key.getNamespace(), idPath);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Public wrapper around {@link #scanOverrideIds()}, used by
     * {@link StructureUsageIndex}'s (re)build pass to enumerate every
     * structure it needs to scan once, up front - rather than that class
     * duplicating the same {@code kubejs/data/**}-walking logic. Deliberately
     * scoped to KVE-managed overrides only (never vanilla/mod/datapack
     * resources reachable only via a loaded world's resource manager, see
     * {@link #readFromGameResources()}) - same "KVE's own entries, not the
     * whole modpack's registries" scope every other domain's usage-search
     * already keeps to (see {@code IdUsageIndex}'s class doc).
     */
    public static List<ResourceLocation> listOverrideIds() {
        return scanOverrideIds();
    }

    private static List<ResourceLocation> scanOverrideIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        Path dataRoot = Paths.get("kubejs", "data");
        if (!Files.isDirectory(dataRoot)) return ids;
        try (var nsStream = Files.list(dataRoot)) {
            for (Path nsDir : (Iterable<Path>) nsStream::iterator) {
                if (!Files.isDirectory(nsDir)) continue;
                String ns = nsDir.getFileName().toString();
                Path structDir = nsDir.resolve("structure");
                if (!Files.isDirectory(structDir)) continue;
                try (var files = Files.walk(structDir)) {
                    for (Path f : (Iterable<Path>) files::iterator) {
                        if (!Files.isRegularFile(f) || !f.toString().endsWith(".nbt")) continue;
                        String rel = structDir.relativize(f).toString().replace('\\', '/');
                        String idPath = rel.substring(0, rel.length() - ".nbt".length());
                        try {
                            ids.add(ResourceLocation.fromNamespaceAndPath(ns, idPath));
                        } catch (Exception ignored) {
                        }
                    }
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return ids;
    }

    /**
     * "Acción restaurar = borrar el.nbt en kubejs + recarga
     * recursos.": deletes the override file so the id falls back to whatever
     * the game would otherwise use. The recarga (resource/datapack reload)
     * itself is outside this method's reach from client code - callers must
     * still tell the player to run {@code /reload} (or rejoin the world) for
     * a running server to pick the change up; this only prepares the file
     * change that reload will pick up.
     */
    public static boolean restore(ResourceLocation id) throws IOException {
        Path path = resolvePath(id);
        if (!Files.exists(path)) return false;
        Files.delete(path);
        CraftEditorMod.LOGGER.info("[Shovel JS] Override de estructura eliminado: {} -> restaura comportamiento original tras /reload.", id);
        StructureUsageIndex.removeStructure(id);
        return true;
    }

    /** True if {@code id} currently has a kubejs override on disk (i.e. the game is using the KVE-edited version right now). */
    public static boolean isOverride(ResourceLocation id) {
        return exists(id);
    }

    /**
     * Public wrapper around {@link #fromTag()} for the world-build scan flow
     * ({@code StructureWorldScanResponsePacket}): parses a
     * {@code StructureWorldIO#scan}-produced tag (same shape {@link #toTag()}
     * writes) into a throwaway grid whose id is just a placeholder - the
     * caller (screen's scan handler) only ever pulls CELLS back out via
     * {@link StructureVoxelGrid#copyCellsFrom}, which ignores id entirely,
     * so what's stamped here is never shown to the player.
     */
    public static StructureVoxelGrid fromScanTag(CompoundTag root) {
        return fromTag(root, ResourceLocation.fromNamespaceAndPath("shovel_js", "world_scan_result"));
    }

    static StructureVoxelGrid fromTag(CompoundTag root, ResourceLocation id) {
        StructureVoxelGrid grid = new StructureVoxelGrid();
        grid.setId(id.toString());

        ListTag paletteTag = root.getList("palette", 10 );
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        HolderGetter<net.minecraft.world.level.block.Block> blockLookup = blockLookup();
        for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag entry = paletteTag.getCompound(i);
            palette.add(NbtUtils.readBlockState(blockLookup, entry));
        }

        ListTag blocksTag = root.getList("blocks", 10);
        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag blockEntry = blocksTag.getCompound(i);
            ListTag posTag = blockEntry.getList("pos", 3 );
            BlockPos pos = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
            int stateIdx = blockEntry.getInt("state");
            if (stateIdx >= 0 && stateIdx < palette.size()) {
                grid.setBlock(pos, palette.get(stateIdx));
                if (blockEntry.contains("nbt", 10 )) {
                    grid.setBlockEntityNbt(pos, blockEntry.getCompound("nbt").copy());
                }
            }
        }

        ListTag entitiesTag = root.getList("entities", 10 );
        List<CompoundTag> scannedEntities = new ArrayList<>(entitiesTag.size());
        for (int i = 0; i < entitiesTag.size(); i++) {
            scannedEntities.add(entitiesTag.getCompound(i).copy());
        }
        grid.setScannedEntities(scannedEntities);

        Map<BlockPos, StructureSpawnCap> spawnCaps = new HashMap<>();
        ListTag spawnCapsTag = root.getList("shovel_js_spawn_caps", 10 );
        for (int i = 0; i < spawnCapsTag.size(); i++) {
            CompoundTag capTag = spawnCapsTag.getCompound(i);
            ListTag posTag = capTag.getList("pos", 3 );
            BlockPos pos = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
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
            spawnCaps.put(pos, cap);
        }
        grid.setSpawnCaps(spawnCaps);

        if (root.contains("shovel_js_scan_origin", 9 )) {
            ListTag soTag = root.getList("shovel_js_scan_origin", 3 );
            BlockPos scanOrigin = new BlockPos(soTag.getInt(0), soTag.getInt(1), soTag.getInt(2));
            grid.setScanOrigin(scanOrigin, root.getString("shovel_js_scan_dim"));
        }
        grid.setTerrainAdjustment(root.getString("shovel_js_terrain_adjustment"));

        grid.markClean();
        return grid;
    }

    private static HolderGetter<net.minecraft.world.level.block.Block> blockLookup() {
        return BuiltInRegistries.BLOCK.asLookup();
    }

    private static ListTag intList(int x, int y, int z) {
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(x));
        list.add(IntTag.valueOf(y));
        list.add(IntTag.valueOf(z));
        return list;
    }

    private static int currentDataVersion() {
        try {
            return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        } catch (Throwable t) {
            return FALLBACK_DATA_VERSION;
        }
    }

    public static boolean exists(ResourceLocation id) {
        return Files.exists(resolvePath(id));
    }
}
