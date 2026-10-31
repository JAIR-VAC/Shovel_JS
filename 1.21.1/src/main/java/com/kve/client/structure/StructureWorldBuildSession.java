package com.kve.client.structure;

import com.kve.common.structure.StructureVoxelGrid;

import com.kve.common.network.KVENetwork;
import com.kve.common.network.StructureWorldGenerateResponsePacket;
import com.kve.common.network.StructureWorldScanRequestPacket;
import com.kve.common.network.StructureWorldScanResponsePacket;
import com.kve.common.structure.StructureNbtIO;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public final class StructureWorldBuildSession {

    private StructureWorldBuildSession() {}

    private static boolean enabled = false;
    private static BlockPos origin = null;
    private static int sizeX = 0, sizeY = 0, sizeZ = 0;

    private static final LongOpenHashSet placedPositions = new LongOpenHashSet();

    static final int MAX_GROW_DISTANCE = 48;

    private static BlockPos generateAnchor = null;

    public static boolean isEnabled() { return enabled && origin != null; }
    public static BlockPos getOrigin() { return origin; }
    public static BlockPos getGenerateAnchor() { return generateAnchor != null ? generateAnchor : origin; }
    public static int[] getSize() { return new int[]{sizeX, sizeY, sizeZ}; }

    /** Whether {@code pos} falls inside the currently tracked box. False (not a crash) if the session isn't armed. */
    public static boolean contains(BlockPos pos) {
        if (!isEnabled()) return false;
        int px = pos.getX(), py = pos.getY(), pz = pos.getZ();
        return px >= origin.getX() && px <= origin.getX() + sizeX - 1
                && py >= origin.getY() && py <= origin.getY() + sizeY - 1
                && pz >= origin.getZ() && pz <= origin.getZ() + sizeZ - 1;
    }

    private static void start(BlockPos newOrigin, int x, int y, int z) {
        origin = newOrigin.immutable();
        generateAnchor = origin;
        sizeX = Math.max(1, x);
        sizeY = Math.max(1, y);
        sizeZ = Math.max(1, z);
        enabled = true;
    }

    /** Actualiza solo el bounding box derivado ({@link #origin}/size) - a diferencia de {@link #start}, nunca toca {@link #generateAnchor} (ver su doc: solo un Generate/Scan explicito lo mueve, no el seguimiento pasivo de colocar/romper). */
    private static void setBounds(BlockPos newOrigin, int x, int y, int z) {
        origin = newOrigin.immutable();
        sizeX = Math.max(1, x);
        sizeY = Math.max(1, y);
        sizeZ = Math.max(1, z);
    }

    /** Called from the "Cancelar" flow, or when a different structure is loaded into the editor - a stale box anchored at the PREVIOUS structure's world position would be actively misleading, same reasoning as {@link StructureGhostPreview#clear()}. */
    public static void clear() {
        enabled = false;
        origin = null;
        generateAnchor = null;
        sizeX = sizeY = sizeZ = 0;
        placedPositions.clear();
        pendingScanId = -1;
        pendingScanCallback = null;
    }

    /** Vuelca {@code grid} (contenido local, ver {@link StructureVoxelGrid#cellsView()}) a {@link #placedPositions} en espacio de mundo, relativo a {@code worldOrigin} - usado al armar la sesion desde un Generate o un Scan exitoso (ver {@link #start}'s callers), nunca desde seguimiento pasivo. Reemplaza cualquier contenido previo. */
    private static void seedPlaced(StructureVoxelGrid grid, BlockPos worldOrigin) {
        placedPositions.clear();
        if (grid == null) return;
        for (BlockPos local : grid.cellsView().keySet()) {
            placedPositions.add(worldOrigin.offset(local).asLong());
        }
    }

    public static void onBlockPlaced(BlockPos worldPos) {
        if (!isEnabled()) return;
        int minX = origin.getX(), minY = origin.getY(), minZ = origin.getZ();
        int maxX = minX + sizeX - 1, maxY = minY + sizeY - 1, maxZ = minZ + sizeZ - 1;
        int px = worldPos.getX(), py = worldPos.getY(), pz = worldPos.getZ();
        int gap = Math.max(Math.max(Math.max(minX - px, px - maxX), Math.max(minY - py, py - maxY)), Math.max(minZ - pz, pz - maxZ));
        if (gap > MAX_GROW_DISTANCE) return;
        placedPositions.add(worldPos.asLong());
        if (px >= minX && px <= maxX && py >= minY && py <= maxY && pz >= minZ && pz <= maxZ) return;

        int newMinX = Math.min(minX, px), newMinY = Math.min(minY, py), newMinZ = Math.min(minZ, pz);
        int newMaxX = Math.max(maxX, px), newMaxY = Math.max(maxY, py), newMaxZ = Math.max(maxZ, pz);
        setBounds(new BlockPos(newMinX, newMinY, newMinZ),
                newMaxX - newMinX + 1, newMaxY - newMinY + 1, newMaxZ - newMinZ + 1);
    }

    public static void onBlockBroken(BlockPos worldPos) {
        if (!isEnabled()) return;
        if (!placedPositions.remove(worldPos.asLong())) return;

        if (placedPositions.isEmpty()) {
            clear();
            return;
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        var it = placedPositions.iterator();
        while (it.hasNext()) {
            BlockPos p = BlockPos.of(it.nextLong());
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        setBounds(new BlockPos(minX, minY, minZ), maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
    }

    private static int nextRequestId = 1;
    private static int pendingGenerateId = -1;
    private static BiConsumer<Integer, String> pendingGenerateCallback;
    private static StructureVoxelGrid pendingGenerateGrid;
    private static BlockPos pendingGenerateOrigin;
    private static int lastGenerateShiftY = 0;

    public static int lastGenerateShiftY() { return lastGenerateShiftY; }

    /**
     * Sends a {@code StructureWorldGenerateRequestPacket} for the grid's
     * current content and arms the session on success. {@code onDone} is
     * called with a non-null failure key on error (permission denied, too
     * large, empty grid), or null on success.
     * <p>
     * {@code inPlace} viaja tal cual hasta {@code StructureWorldIO#place} -
     * true = ya hay sesion armada (re-generar in-place, mismo origin de
     * donde se escaneo, ver {@link #isEnabled()}), false = colocar copia
     * nueva anclada al crosshair, sin sesion.
     */
    public static void requestGenerate(StructureVoxelGrid grid, BlockPos origin, boolean inPlace, Consumer<String> onDone) {
        int requestId = nextRequestId++;
        pendingGenerateId = requestId;
        pendingGenerateCallback = (placed, failureKey) -> onDone.accept(failureKey);
        pendingGenerateGrid = grid;
        pendingGenerateOrigin = origin.immutable();
        CompoundTag tag = StructureNbtIO.toTag(grid);
        KVENetwork.requestWorldGenerate(requestId, origin, tag, inPlace);
    }

    /** Called by {@code StructureWorldGenerateResponsePacket#handle} on the client thread. */
    public static void onGenerateResponse(StructureWorldGenerateResponsePacket msg) {
        if (msg.requestId != pendingGenerateId) return;
        BiConsumer<Integer, String> callback = pendingGenerateCallback;
        StructureVoxelGrid grid = pendingGenerateGrid;
        pendingGenerateId = -1;
        pendingGenerateCallback = null;
        BlockPos requested = pendingGenerateOrigin;
        pendingGenerateGrid = null;
        pendingGenerateOrigin = null;

        lastGenerateShiftY = 0;
        if (msg.success) {
            if (requested != null) {
                lastGenerateShiftY = msg.origin.getY() - requested.getY();
                if (lastGenerateShiftY != 0) moveGhostWithStructure(requested, msg.sizeX, msg.sizeY, msg.sizeZ, lastGenerateShiftY);
            }
            start(msg.origin, msg.sizeX, msg.sizeY, msg.sizeZ);
            seedPlaced(grid, msg.origin);
        }
        if (callback != null) callback.accept(msg.placedCount, msg.success ? null : msg.failureKey);
    }

    private static void moveGhostWithStructure(BlockPos requested, int sx, int sy, int sz, int dy) {
        BlockPos anchor = StructureGhostPreview.getAnchor();
        if (anchor == null) return;
        boolean insideRequestedBox = anchor.getX() >= requested.getX() && anchor.getX() < requested.getX() + sx
                && anchor.getY() >= requested.getY() && anchor.getY() < requested.getY() + sy
                && anchor.getZ() >= requested.getZ() && anchor.getZ() < requested.getZ() + sz;
        if (insideRequestedBox) StructureGhostPreview.setAnchor(anchor.offset(0, dy, 0));
    }

    private static int pendingScanId = -1;
    private static Consumer<StructureWorldScanResponsePacket> pendingScanCallback;

    /**
     * Sends a {@code StructureWorldScanRequestPacket} for the session's
     * CURRENT box. {@code onDone} receives the raw response packet so the
     * caller (the editor screen) can branch on
     * {@link StructureWorldScanResponsePacket#outcome} itself - success
     * (merge into the grid), empty (nothing to merge, not an error), or
     * failure (flash the reason).
     */
    public static void requestScan(boolean includeMobs, boolean includeAir, Consumer<StructureWorldScanResponsePacket> onDone) {
        requestScan(includeMobs, includeAir, false, onDone);
    }

    public static void requestScan(boolean includeMobs, boolean includeAir, boolean requireSealed, Consumer<StructureWorldScanResponsePacket> onDone) {
        if (!isEnabled()) return;
        int requestId = nextRequestId++;
        pendingScanId = requestId;
        pendingScanCallback = onDone;
        KVENetwork.requestWorldScan(requestId, origin, sizeX, sizeY, sizeZ, includeMobs, includeAir, requireSealed);
    }

    /** Called by {@code StructureWorldScanResponsePacket#handle} on the client thread. */
    public static void onScanResponse(StructureWorldScanResponsePacket msg) {
        if (msg.requestId != pendingScanId) return;
        Consumer<StructureWorldScanResponsePacket> callback = pendingScanCallback;
        pendingScanId = -1;
        pendingScanCallback = null;

        if (msg.outcome == StructureWorldScanResponsePacket.OUTCOME_SUCCESS) {
            int x = msg.occupiedMax.getX() - msg.occupiedMin.getX() + 1;
            int y = msg.occupiedMax.getY() - msg.occupiedMin.getY() + 1;
            int z = msg.occupiedMax.getZ() - msg.occupiedMin.getZ() + 1;
            start(msg.occupiedMin, x, y, z);
            seedPlaced(StructureNbtIO.fromScanTag(msg.tag), msg.occupiedMin);
        } else if (msg.outcome == StructureWorldScanResponsePacket.OUTCOME_EMPTY) {
            clear();
        }
        if (callback != null) callback.accept(msg);
    }
}
