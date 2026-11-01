package com.kve.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import com.kve.common.structure.StructureSpawnCap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory data model for the Structure Voxel Editor. Deliberately NOT a {@code StructureTemplate} in memory -
 * a plain sparse map is simpler to mutate one cell at a time from GUI
 * clicks, and is only converted to/from the vanilla structure NBT shape at
 * the I/O boundary (see {@link com.kve.common.structure.StructureNbtIO}).
 * <p>
 * Positions are stored in "local" space: {@code BlockPos.ZERO} is one
 * corner of the structure's bounding box, matching the "Origin:
 * BlockPos.ZERO local; export offsets relative origin". Local space is not
 * re-normalized after every edit (that would renumber cells out from under
 * an in-progress edit) - normalization only happens explicitly, via
 * {@link #normalizeOrigin()}, which callers invoke before save/rotate.
 */
public final class StructureVoxelGrid {

    private final Map<BlockPos, BlockState> cells = new HashMap<>();

    private final Map<BlockPos, CompressedBlockEntity> blockEntityNbt = new HashMap<>();

    private final Map<BlockPos, StructureSpawnCap> spawnCaps = new HashMap<>();

    private final List<CompoundTag> scannedEntities = new ArrayList<>();

    private String id = "";

    private BlockPos scanOrigin;
    private String scanDimension;

    private String terrainAdjustment = "NONE";

    private boolean dirty = false;

    /**
     * Bumped on every mutation that could change what's visible or what a
     * cached render object was built from ({@link #setBlock()}, {@link #setAir()},
     * {@link #clear}, {@link #normalizeOrigin()}, {@link #rotate90},
     * {@link #copyFrom()}, {@link #setBlockEntityNbt()}). Purely a cheap "did
     * anything change since last frame" signal for perf caches - both
     * {@link #visibleCellPositions()} below and {@code StructureSceneRenderer}'s
     * block-entity-renderer instance cache key off it, so an unedited
     * structure (the overwhelming common case: the player is orbiting the
     * camera, not editing every frame) costs nothing extra to redraw no
     * matter how large it is.
     */
    private int version = 0;

    private int visibleCacheVersion = -1;

    private int boundsCacheVersion = -1;
    private BlockPos[] boundsCache;
    private List<BlockPos> visibleCellsCache = Collections.emptyList();

    private static final int MAX_UNDO = 1000;
    private static final int MAX_UNDO_CELLS = 4_000_000;

    private final Deque<Snapshot> undoStack = new ArrayDeque<>();

    private final Deque<Snapshot> redoStack = new ArrayDeque<>();

    /**
     * Pre-edit state of exactly ONE position, captured by {@link #pushUndoForCell()}/
     * {@link #pushUndoForCells()} - enough to fully restore that single cell
     * (block, preserved block-entity nbt, spawn cap marker) without touching
     * anything else. Every field null means the position wasn't part of the
     * design at all before the edit (undoing must {@code removeCell()}-equivalent
     * it, not merely set it to air).
     */
    private static final class CellPatchEntry {
        final BlockState state;
        final CompressedBlockEntity te;
        final StructureSpawnCap spawnCap;

        CellPatchEntry(BlockState state, CompressedBlockEntity te, StructureSpawnCap spawnCap) {
            this.state = state;
            this.te = te;
            this.spawnCap = spawnCap;
        }
    }

    private static final class Snapshot {
        final Map<BlockPos, BlockState> fullCells;
        final Map<BlockPos, CompressedBlockEntity> fullBlockEntityNbt;
        final Map<BlockPos, StructureSpawnCap> fullSpawnCaps;
        final Map<BlockPos, CellPatchEntry> scopedPatch;
        final String id;
        final boolean dirty;
        /** Solo en las completas: girar, reflejar y recentrar mueven tambien los mobs y el origen del escaneo. */
        List<CompoundTag> fullScannedEntities;
        BlockPos fullScanOrigin;
        String fullScanDimension;

        private Snapshot(Map<BlockPos, BlockState> fullCells, Map<BlockPos, CompressedBlockEntity> fullBlockEntityNbt,
                          Map<BlockPos, StructureSpawnCap> fullSpawnCaps, Map<BlockPos, CellPatchEntry> scopedPatch,
                          String id, boolean dirty) {
            this.fullCells = fullCells;
            this.fullBlockEntityNbt = fullBlockEntityNbt;
            this.fullSpawnCaps = fullSpawnCaps;
            this.scopedPatch = scopedPatch;
            this.id = id;
            this.dirty = dirty;
        }

        boolean isScoped() {
            return scopedPatch != null;
        }
    }

    private Snapshot fullSnapshot() {
        Snapshot s = new Snapshot(new HashMap<>(cells), new HashMap<>(blockEntityNbt), new HashMap<>(spawnCaps), null, id, dirty);
        s.fullScannedEntities = new ArrayList<>(scannedEntities.size());
        for (CompoundTag e : scannedEntities) s.fullScannedEntities.add(e.copy());
        s.fullScanOrigin = scanOrigin;
        s.fullScanDimension = scanDimension;
        return s;
    }

    private Snapshot scopedSnapshot(java.util.Collection<BlockPos> positions) {
        Map<BlockPos, CellPatchEntry> patch = new HashMap<>(positions.size());
        for (BlockPos p : positions) {
            BlockPos key = p.immutable();
            patch.put(key, new CellPatchEntry(cells.get(key), blockEntityNbt.get(key), spawnCaps.get(key)));
        }
        return new Snapshot(null, null, null, patch, id, dirty);
    }

    private void restore(Snapshot s) {
        if (s.isScoped()) {
            for (Map.Entry<BlockPos, CellPatchEntry> e : s.scopedPatch.entrySet()) {
                BlockPos pos = e.getKey();
                CellPatchEntry patch = e.getValue();
                if (patch.state == null) cells.remove(pos); else cells.put(pos, patch.state);
                if (patch.te == null) blockEntityNbt.remove(pos); else blockEntityNbt.put(pos, patch.te);
                if (patch.spawnCap == null) spawnCaps.remove(pos); else spawnCaps.put(pos, patch.spawnCap);
            }
        } else {
            cells.clear();
            cells.putAll(s.fullCells);
            blockEntityNbt.clear();
            blockEntityNbt.putAll(s.fullBlockEntityNbt);
            spawnCaps.clear();
            spawnCaps.putAll(s.fullSpawnCaps);
            if (s.fullScannedEntities != null) {
                scannedEntities.clear();
                for (CompoundTag e : s.fullScannedEntities) scannedEntities.add(e.copy());
                scanOrigin = s.fullScanOrigin;
                scanDimension = s.fullScanDimension;
            }
        }
        id = s.id;
        dirty = s.dirty;
    }

    private Snapshot inverseOf(Snapshot s) {
        return s.isScoped() ? scopedSnapshot(s.scopedPatch.keySet()) : fullSnapshot();
    }

    /**
     * Records the current state on the undo stack, dropping the oldest
     * entry once the 20-step cap is reached. Callers invoke this
     * immediately before any edit action that touches an unbounded or
     * unknown set of cells (clear, rotate, replacing the whole grid from a
     * scan) - never after, since by then the "before" state is already
     * gone. For an edit that only ever touches one or a few known
     * positions (paint, break, cycle facing, edit one nbt, place/remove one
     * spawn cap marker), prefer {@link #pushUndoForCell()}/
     * {@link #pushUndoForCells()} instead - same undo/redo behavior, far
     * cheaper on a large structure.
     */
    public void pushUndo() {
        undoStack.addLast(fullSnapshot());
        trim(undoStack);
        redoStack.clear();
    }

    /** Scoped form of {@link #pushUndo()} for an edit about to touch exactly one position - see {@link Snapshot}'s "Scoped" doc. */
    public void pushUndoForCell(BlockPos pos) {
        pushUndoForCells(java.util.Collections.singletonList(pos));
    }

    /** Scoped form of {@link #pushUndo()} for an edit about to touch exactly {@code positions} (and nothing else) - see {@link Snapshot}'s "Scoped" doc. */
    public void pushUndoForCells(java.util.Collection<BlockPos> positions) {
        undoStack.addLast(scopedSnapshot(positions));
        trim(undoStack);
        redoStack.clear();
    }

    /**
     * Suma {@code positions} al paso de deshacer MAS RECIENTE en vez de abrir
     * uno nuevo - un trazo entero de brocha se deshace de una vez, no bloque
     * por bloque. Solo captura el estado previo de las posiciones que ese paso
     * todavia no cubre (las ya cubiertas guardan su estado de ANTES del trazo).
     * Si el ultimo paso es completo (o no hay ninguno), abre uno puntual nuevo.
     */
    public void extendLastUndoForCells(java.util.Collection<BlockPos> positions) {
        Snapshot last = undoStack.peekLast();
        if (last == null || !last.isScoped()) {
            pushUndoForCells(positions);
            return;
        }
        for (BlockPos p : positions) {
            BlockPos key = p.immutable();
            if (!last.scopedPatch.containsKey(key)) {
                last.scopedPatch.put(key, new CellPatchEntry(cells.get(key), blockEntityNbt.get(key), spawnCaps.get(key)));
            }
        }
        redoStack.clear();
    }

    private static int cost(Snapshot s) {
        return s.isScoped() ? s.scopedPatch.size() : s.fullCells.size();
    }

    private static void trim(Deque<Snapshot> stack) {
        while (stack.size() > MAX_UNDO) stack.removeFirst();
        long total = 0;
        for (Snapshot s : stack) total += cost(s);
        while (stack.size() > 1 && total > MAX_UNDO_CELLS) total -= cost(stack.removeFirst());
    }

    /** Numero de pasos que se pueden deshacer ahora mismo (para el tooltip del boton). */
    public int undoDepth() {
        return undoStack.size();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /** Restores the most recent {@link #pushUndo()}/{@link #pushUndoForCell()} snapshot, if any. No-op (not an error) if the stack is empty. */
    public void undo() {
        if (undoStack.isEmpty()) return;
        Snapshot s = undoStack.removeLast();
        redoStack.addLast(inverseOf(s));
        trim(redoStack);
        restore(s);
        version++;
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        Snapshot s = redoStack.removeLast();
        undoStack.addLast(inverseOf(s));
        trim(undoStack);
        restore(s);
        version++;
    }

    /** Drops the whole undo/redo history - called on Load, since a freshly-loaded structure's history has nothing to do with whatever was being edited before it. */
    public void clearUndoHistory() {
        undoStack.clear();
        redoStack.clear();
    }

    public void setBlock(BlockPos pos, BlockState state) {
        if (state == null) {
            removeCell(pos);
            return;
        }
        BlockPos key = pos.immutable();
        cells.put(key, state);
        blockEntityNbt.remove(key);
        dirty = true;
        version++;
    }

    public void rotateBlockInPlace(BlockPos pos, BlockState newState) {
        if (newState == null || newState.isAir()) { setAir(pos); return; }
        BlockPos key = pos.immutable();
        if (!cells.containsKey(key)) return;
        cells.put(key, newState);
        dirty = true;
        version++;
    }

    /**
     * "Breaks" the block at {@code pos} - marks it EXPLICIT air, still part
     * of the design (see {@link #cells}'s field doc). This is what the
     * editor's RMB/break tool calls: breaking a wall inside an underground
     * structure should carve an opening that stays carved when the
     * structure is placed elsewhere, not silently forget that position ever
     * had bounds. Equivalent to {@code setBlock(pos, Blocks.AIR.defaultBlockState())}.
     * For the one case that genuinely needs "undo, this position was never
     * touched" (reverting an over-sized placement attempt, see
     * {@code StructureEditorScreen}'s max-edge guard), use {@link #removeCell()}
     * instead - it, unlike this method, doesn't count toward the structure's
     * bounds.
     */
    public void setAir(BlockPos pos) {
        setBlock(pos, Blocks.AIR.defaultBlockState());
    }

    public void breakCell(BlockPos pos) {
        setAir(pos);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
            if (e.getValue().isAir()) continue;
            BlockPos p = e.getKey();
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        boolean outside = minX == Integer.MAX_VALUE
                || pos.getX() < minX || pos.getX() > maxX
                || pos.getY() < minY || pos.getY() > maxY
                || pos.getZ() < minZ || pos.getZ() > maxZ;
        if (outside) removeCell(pos.immutable());
    }

    public void removeCell(BlockPos pos) {
        blockEntityNbt.remove(pos);
        if (cells.remove(pos) != null) {
            dirty = true;
            version++;
        }
    }

    /** Never returns null - unset cells (and cells explicitly marked air) return air, matching the real world. */
    public BlockState get(BlockPos pos) {
        BlockState s = cells.get(pos);
        return s != null ? s : Blocks.AIR.defaultBlockState();
    }

    /** True whether {@code pos} was never touched OR was explicitly carved to air - either way, nothing renders/occupies there. */
    public boolean isAir(BlockPos pos) {
        BlockState s = cells.get(pos);
        return s == null || s.isAir();
    }

    public boolean isEmpty() {
        return cells.isEmpty();
    }

    public int cellCount() {
        return cells.size();
    }

    /**
     * Bloques reales de la estructura, sin el aire explicito que deja borrar una celda
     * ({@link #cellCount()} si lo cuenta).
     */
    public int blockCount() {
        int n = 0;
        for (BlockState s : cells.values()) if (!s.isAir()) n++;
        return n;
    }

    public Map<BlockPos, BlockState> cellsView() {
        return cells;
    }

    /** Bumped on every mutation - see the field doc for what this backs. */
    public int version() {
        return version;
    }

    /**
     * Positions actually worth drawing: cells whose all 6 neighbors are
     * present and occlude ({@link BlockState#canOcclude()} - the same signal
     * the vanilla chunk mesher uses for face culling) are skipped entirely,
     * since every face of such a cell is guaranteed hidden inside the solid
     * volume around it. This is coarser than real per-face culling (a
     * partially-occluded cell still renders in full), but for the actual
     * worst case here - a filled solid interior, which is exactly the
     * scenario large structures hit - it cuts the number of
     * {@code renderSingleBlock}/block-entity-renderer calls per frame from
     * O(volume) to roughly O(surface area), which is the optimization that
     * matters: interior cell count grows with the cube of the edge length,
     * surface cells only with the square.
     * <p>
     * Recomputed only when {@link #version} has moved past the version this
     * was last built for - an unedited structure (the common case while
     * orbiting the camera) reuses the same list every frame at zero extra
     * cost.
     */
    public List<BlockPos> visibleCellPositions() {
        if (visibleCacheVersion == version) return visibleCellsCache;
        List<BlockPos> visible = new ArrayList<>(cells.size());
        for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
            if (e.getValue().isAir()) continue;
            BlockPos p = e.getKey();
            if (p.getY() > viewMaxY) continue;
            if (!isFullyInterior(p)) visible.add(p);
        }
        visibleCellsCache = visible;
        visibleCacheVersion = version;
        return visible;
    }

    private boolean isFullyInterior(BlockPos p) {
        return occludes(p.above()) && occludes(p.below())
                && occludes(p.north()) && occludes(p.south())
                && occludes(p.east()) && occludes(p.west());
    }

    private boolean occludes(BlockPos p) {
        if (p.getY() > viewMaxY) return false; // oculta por el corte de capas: la cara de abajo queda a la vista
        BlockState s = cells.get(p);
        if (s == null || !s.canOcclude()) return false;
        try {
            VoxelShape shape = s.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            return Block.isShapeFullBlock(shape);
        } catch (Exception ex) {
            // A mod block whose shape lookup assumes a real level/position must
            // never crash the visibility bake - fall back to "doesn't fully
            // occlude" (safe: renders the cell instead of risking a hole).
            return false;
        }
    }

    /** Vista por capas: las celdas con Y mayor que esto no se dibujan ni se pueden apuntar. No cambia los datos. */
    private int viewMaxY = Integer.MAX_VALUE;

    public int viewMaxY() { return viewMaxY; }

    public boolean hasLayerCut() { return viewMaxY != Integer.MAX_VALUE; }

    /** Cambia el corte de capas. Sube {@link #version} para que el render se rehaga, sin marcar la estructura como modificada. */
    public void setViewMaxY(int y) {
        if (viewMaxY == y) return;
        viewMaxY = y;
        version++;
    }

    public void clearLayerCut() { setViewMaxY(Integer.MAX_VALUE); }

    /** Para el raycast del editor: aire, o una celda escondida por el corte de capas. */
    public boolean isAirOrHidden(BlockPos pos) {
        return pos.getY() > viewMaxY || isAir(pos);
    }

    /**
     * Cantidad de cada bloque (sin aire), de mayor a menor. Los bloques de dos celdas (puertas,
     * plantas altas, camas) cuentan una vez: la mitad de arriba y la cabecera no son otro bloque.
     */
    public List<Map.Entry<Block, Integer>> materialCounts() {
        Map<Block, Integer> counts = new HashMap<>();
        for (BlockState s : cells.values()) {
            if (s.isAir() || isSecondHalf(s)) continue;
            counts.merge(s.getBlock(), 1, Integer::sum);
        }
        List<Map.Entry<Block, Integer>> out = new ArrayList<>(counts.entrySet());
        out.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return out;
    }

    private static boolean isSecondHalf(BlockState s) {
        if (s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                && s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) return true;
        return s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART)
                && s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD;
    }

    /** Celdas de {@code block}; con {@code a}/{@code b} no nulos, solo las de esa caja (inclusive). */
    public List<BlockPos> cellsOfBlock(Block block, BlockPos a, BlockPos b) {
        List<BlockPos> out = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
            if (e.getValue().getBlock() != block) continue;
            if (a != null && b != null && !inBox(e.getKey(), a, b)) continue;
            out.add(e.getKey());
        }
        return out;
    }

    /**
     * Pone {@code to} en cada celda de {@code positions}, conservando las propiedades que
     * compartan los dos bloques (orientacion de escaleras, mitad de losas...).
     * El caller hace {@link #pushUndoForCells} antes.
     */
    public int replaceCells(List<BlockPos> positions, BlockState to) {
        int n = 0;
        for (BlockPos p : positions) {
            BlockState from = cells.get(p);
            if (from == null) continue;
            if (to.isAir()) setAir(p);
            else setBlock(p, copySharedProperties(from, to));
            n++;
        }
        return n;
    }

    private static BlockState copySharedProperties(BlockState from, BlockState to) {
        BlockState out = to;
        for (Property<?> prop : from.getProperties()) out = copyProperty(from, out, prop);
        return out;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(BlockState from, BlockState to, Property<T> prop) {
        Property<?> target = to.getBlock().getStateDefinition().getProperty(prop.getName());
        if (target == null || !target.getValueClass().equals(prop.getValueClass())) return to;
        @SuppressWarnings("unchecked") Property<T> typed = (Property<T>) target;
        T value = from.getValue(prop);
        return typed.getPossibleValues().contains(value) ? to.setValue(typed, value) : to;
    }

    private static boolean inBox(BlockPos p, BlockPos a, BlockPos b) {
        return p.getX() >= Math.min(a.getX(), b.getX()) && p.getX() <= Math.max(a.getX(), b.getX())
                && p.getY() >= Math.min(a.getY(), b.getY()) && p.getY() <= Math.max(a.getY(), b.getY())
                && p.getZ() >= Math.min(a.getZ(), b.getZ()) && p.getZ() <= Math.max(a.getZ(), b.getZ());
    }

    /** Region copiada: celdas (incluido el aire explicito) relativas a su esquina minima, con su NBT. */
    public record Clip(Map<BlockPos, BlockState> cells, Map<BlockPos, CompoundTag> nbt, int sizeX, int sizeY, int sizeZ) {
        public boolean isEmpty() { return cells.isEmpty(); }
    }

    public Clip copyRegion(BlockPos a, BlockPos b) {
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        Map<BlockPos, BlockState> copied = new HashMap<>();
        Map<BlockPos, CompoundTag> copiedNbt = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
            if (!inBox(e.getKey(), a, b)) continue;
            BlockPos rel = e.getKey().subtract(min);
            copied.put(rel, e.getValue());
            CompoundTag tag = getBlockEntityNbt(e.getKey());
            if (tag != null) copiedNbt.put(rel, tag.copy());
        }
        return new Clip(copied, copiedNbt,
                Math.abs(a.getX() - b.getX()) + 1, Math.abs(a.getY() - b.getY()) + 1, Math.abs(a.getZ() - b.getZ()) + 1);
    }

    /** Celdas ocupadas dentro de la caja (inclusive). */
    public List<BlockPos> cellsInBox(BlockPos a, BlockPos b) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : cells.keySet()) if (inBox(p, a, b)) out.add(p);
        return out;
    }

    /** Posiciones que un pegado en {@code origin} va a tocar (para el deshacer acotado). */
    public List<BlockPos> clipTargets(Clip clip, BlockPos origin) {
        List<BlockPos> out = new ArrayList<>(clip.cells().size());
        for (BlockPos rel : clip.cells().keySet()) out.add(origin.offset(rel));
        return out;
    }

    public int pasteClip(Clip clip, BlockPos origin) {
        for (Map.Entry<BlockPos, BlockState> e : clip.cells().entrySet()) {
            BlockPos p = origin.offset(e.getKey());
            if (e.getValue().isAir()) setAir(p);
            else setBlock(p, e.getValue());
            CompoundTag tag = clip.nbt().get(e.getKey());
            if (tag != null) setBlockEntityNbt(p, tag.copy());
        }
        return clip.cells().size();
    }

    public int removeCells(List<BlockPos> positions) {
        for (BlockPos p : positions) removeCell(p);
        return positions.size();
    }

    public void clear() {
        if (!cells.isEmpty()) {
            cells.clear();
            blockEntityNbt.clear();
            spawnCaps.clear();
            dirty = true;
            version++;
        }
        scanOrigin = null;
        scanDimension = null;
    }

    public BlockPos getScanOrigin() { return scanOrigin; }

    /** Dimension id (e.g. "minecraft:overworld") the scan origin above is in, or null alongside a null origin. */
    public String getScanDimension() { return scanDimension; }

    /** Called by {@code StructureNbtIO#fromTag} when the loaded tag carries scan provenance. Does not mark the grid dirty - provenance is metadata, not an edit. */
    public void setScanOrigin(BlockPos origin, String dimension) {
        this.scanOrigin = origin;
        this.scanDimension = dimension;
    }

    /** enum name() de {@code StructureTerrainAdjustment}, "NONE" por defecto. */
    public String getTerrainAdjustment() { return terrainAdjustment; }

    /** Llamado por el toggle del editor (marca dirty) y por {@code StructureNbtIO#fromTag} al cargar (fromTag ya llama markClean() despues, ver su doc). */
    public void setTerrainAdjustment(String mode) {
        String safe = (mode == null || mode.isEmpty()) ? "NONE" : mode;
        if (!safe.equals(terrainAdjustment)) {
            terrainAdjustment = safe;
            dirty = true;
            version++;
        }
    }

    /** Read-only view of the currently held scanned-mob tags (opaque - see {@link #scannedEntities} field doc). */
    public List<CompoundTag> scannedEntitiesView() {
        return Collections.unmodifiableList(scannedEntities);
    }

    /** Replaces the held scanned-mob tags wholesale - called by {@code StructureNbtIO} on load, same pass-through role {@link #setBlockEntityNbt()} plays for TE nbt. */
    public void setScannedEntities(List<CompoundTag> entities) {
        scannedEntities.clear();
        scannedEntities.addAll(entities);
        dirty = true;
        version++;
    }

    /** Preserved block-entity NBT for a cell, or null if that cell has none (the common case). */
    public CompoundTag getBlockEntityNbt(BlockPos pos) {
        CompressedBlockEntity c = blockEntityNbt.get(pos);
        return c == null ? null : c.get();
    }

    /**
     * Attaches (or, with a null nbt, clears) block-entity NBT for a cell.
     * Called by {@code StructureNbtIO} while loading (id load flow marks
     * clean right after, so this alone doesn't leave a just-loaded
     * structure looking dirty), and by
     * {@code StructureBlockEntityNbtScreen}'s save callback when the
     * player hand-edits a chest/spawner/sign's nbt from the GUI - that
     * second caller DOES need {@link #dirty} set, since it's a real
     * player edit that should be reflected by Save/the "*" dirty marker
     * same as any block placement is. Setting it unconditionally here is
     * harmless for the load path too (whatever calls this during load
     * already calls {@link #markClean()} right after via copyFrom()/the
     * screen's applyLoadResult).
     */
    public void setBlockEntityNbt(BlockPos pos, CompoundTag nbt) {
        BlockPos key = pos.immutable();
        if (nbt == null) blockEntityNbt.remove(key);
        else blockEntityNbt.put(key, CompressedBlockEntity.of(nbt));
        dirty = true;
        version++;
    }

    public boolean hasBlockEntityNbt(BlockPos pos) {
        return blockEntityNbt.containsKey(pos);
    }

    /** The spawn cap marker at a cell, or null if that cell has none (the common case). */
    public StructureSpawnCap getSpawnCap(BlockPos pos) {
        return spawnCaps.get(pos);
    }

    public boolean hasSpawnCap(BlockPos pos) {
        return spawnCaps.containsKey(pos);
    }

    /** Places (or replaces) a spawn cap marker at a cell - the structure editor's SPAWN_CAP tool LMB action. */
    public void setSpawnCap(BlockPos pos, StructureSpawnCap cap) {
        spawnCaps.put(pos.immutable(), cap);
        dirty = true;
        version++;
    }

    /** Removes the spawn cap marker at a cell, if any - the structure editor's SPAWN_CAP tool RMB action. No-op if that cell has none. */
    public void removeSpawnCap(BlockPos pos) {
        if (spawnCaps.remove(pos) != null) {
            dirty = true;
            version++;
        }
    }

    /** Read-only view of every placed spawn cap, keyed by local position - consumed by {@code StructureSceneRenderer} for the marker outline and by {@code StructureNbtIO} for save/load. */
    public Map<BlockPos, StructureSpawnCap> spawnCapsView() {
        return Collections.unmodifiableMap(spawnCaps);
    }

    /** Bulk-replaces every spawn cap marker - used by {@code StructureNbtIO} on load, same pass-through role {@link #setScannedEntities()} plays for scanned mobs. */
    public void setSpawnCaps(Map<BlockPos, StructureSpawnCap> caps) {
        spawnCaps.clear();
        spawnCaps.putAll(caps);
        dirty = true;
        version++;
    }

    /**
     * Bulk-replaces this grid's cells, preserved TE nbt, scanned mob
     * entries, and id with another grid's - used by the load flow so a
     * freshly-loaded grid's data (including any TE nbt/scanned mobs)
     * transfers over without a manual cell-by-cell loop that would
     * otherwise drop it.
     */
    public void copyFrom(StructureVoxelGrid other) {
        cells.clear();
        cells.putAll(other.cells);
        blockEntityNbt.clear();
        blockEntityNbt.putAll(other.blockEntityNbt);
        scannedEntities.clear();
        scannedEntities.addAll(other.scannedEntities);
        spawnCaps.clear();
        spawnCaps.putAll(other.spawnCaps);
        id = other.id;
        scanOrigin = other.scanOrigin;
        scanDimension = other.scanDimension;
        terrainAdjustment = other.terrainAdjustment;
        dirty = false;
        version++;
    }

    public void copyCellsFrom(StructureVoxelGrid other) {
        cells.clear();
        cells.putAll(other.cells);
        blockEntityNbt.clear();
        blockEntityNbt.putAll(other.blockEntityNbt);
        scannedEntities.clear();
        scannedEntities.addAll(other.scannedEntities);
        scanOrigin = other.scanOrigin;
        scanDimension = other.scanDimension;
        dirty = true;
        version++;
    }

    /**
     * Inclusive [min,max] bounding box of occupied cells, or null if empty.
     * <p>
     * Cached the same way {@link #visibleCellPositions()} is (keyed off
     * {@link #version}): this is called every frame the structure editor is
     * open - once per {@code VoxelRaycast.cast} (mouse-hover picking) and
     * once more from the right panel's size readout - not just on edits.
     * Uncached, that's a full O(cell count) scan of the grid twice per
     * frame just to move the mouse or orbit the camera, which barely
     * registers for a small structure but becomes real, measurable frame
     * time on a large one - exactly the structures raising
     * {@code structure_editor_max_edge} makes more common. An unedited
     * structure now reuses the same two {@link BlockPos} instances every
     * frame at zero extra cost, same as the visible-cells cache already did.
     */
    public BlockPos[] computeBounds() {
        if (cells.isEmpty()) return null;
        if (boundsCacheVersion == version) return boundsCache;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : cells.keySet()) {
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        boundsCache = new BlockPos[]{ new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ) };
        boundsCacheVersion = version;
        return boundsCache;
    }

    /** Size in blocks along each axis (1x1x1 minimum for a single occupied cell), or null if empty. */
    public int[] computeSize() {
        BlockPos[] b = computeBounds();
        if (b == null) return null;
        return new int[]{
                b[1].getX() - b[0].getX() + 1,
                b[1].getY() - b[0].getY() + 1,
                b[1].getZ() - b[0].getZ() + 1
        };
    }

    /** True if the current bounding box exceeds maxEdge on any axis. Empty grid never exceeds. */
    public boolean exceedsMaxEdge(int maxEdge) {
        int[] size = computeSize();
        if (size == null) return false;
        return size[0] > maxEdge || size[1] > maxEdge || size[2] > maxEdge;
    }

    /**
     * Pre-flight guard: true if the union of the current
     * bounds and the box spanned by {@code extraA}/{@code extraB}
     * (inclusive, corners in any order) would exceed {@code maxEdge} on
     * any axis - WITHOUT mutating anything. Lets {@code StructureEditorScreen}'s
     * box/line tools reject an oversized bulk fill up front, the same way
     * single-block placement already rejects one cell that would push the
     * grid over the limit, instead of committing a possibly-large edit and
     * then having to unwind it.
     */
    public boolean wouldExceedMaxEdge(BlockPos extraA, BlockPos extraB, int maxEdge) {
        int minX = Math.min(extraA.getX(), extraB.getX());
        int minY = Math.min(extraA.getY(), extraB.getY());
        int minZ = Math.min(extraA.getZ(), extraB.getZ());
        int maxX = Math.max(extraA.getX(), extraB.getX());
        int maxY = Math.max(extraA.getY(), extraB.getY());
        int maxZ = Math.max(extraA.getZ(), extraB.getZ());
        BlockPos[] cur = computeBounds();
        if (cur != null) {
            minX = Math.min(minX, cur[0].getX()); minY = Math.min(minY, cur[0].getY()); minZ = Math.min(minZ, cur[0].getZ());
            maxX = Math.max(maxX, cur[1].getX()); maxY = Math.max(maxY, cur[1].getY()); maxZ = Math.max(maxZ, cur[1].getZ());
        }
        int sx = maxX - minX + 1, sy = maxY - minY + 1, sz = maxZ - minZ + 1;
        return sx > maxEdge || sy > maxEdge || sz > maxEdge;
    }

    public int fillBox(BlockPos a, BlockPos b, BlockState state, boolean hollow) {
        if (state == null) return 0;
        int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY()), maxY = Math.max(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());
        boolean air = state.isAir();
        int touched = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (hollow && x != minX && x != maxX && y != minY && y != maxY && z != minZ && z != maxZ) continue;
                    BlockPos p = new BlockPos(x, y, z);
                    if (air) setAir(p); else setBlock(p, state);
                    touched++;
                }
            }
        }
        return touched;
    }

    /**
     * Fills the axis-aligned box spanned by {@code a}/{@code b} (inclusive,
     * corners in any order) with a lattice pattern rather than a solid fill:
     * only cells sitting on a horizontal {@code spacing}-cell grid (measured
     * from the box's min X/Z corner) are touched, at every Y layer in the
     * box - a column at each grid intersection, spanning the full height of
     * the box. Meant for support-pillar/lattice layouts without hand-placing every pillar. Same
     * undo/size-guard discipline as {@link #fillBox()} - the caller's job.
     *
     * @param spacing distance in cells between grid lines, minimum 1 (1
     * degenerates to a solid fill, same as {@link #fillBox()}
     *                 with hollow=false)
     * @return how many cells were touched
     */
    public int fillGrid(BlockPos a, BlockPos b, BlockState state, int spacing) {
        if (state == null) return 0;
        spacing = Math.max(1, spacing);
        int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY()), maxY = Math.max(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());
        boolean air = state.isAir();
        int touched = 0;
        for (int x = minX; x <= maxX; x++) {
            if ((x - minX) % spacing != 0) continue;
            for (int z = minZ; z <= maxZ; z++) {
                if ((z - minZ) % spacing != 0) continue;
                for (int y = minY; y <= maxY; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (air) setAir(p); else setBlock(p, state);
                    touched++;
                }
            }
        }
        return touched;
    }

    /**
     * Fills a single-cell-wide 3D line of cells from {@code a} to
     * {@code b} (inclusive) with {@code state}, using a driving-axis
     * Bresenham walk (all three axes stepped in lock-step against the
     * longest one) so the line has no gaps regardless of its slope - for
     * beams, wires, fence runs, etc. without clicking every cell by hand.
     * Same undo/size-guard discipline as {@link #fillBox()} - the caller's
     * job, not this method's.
     *
     * @return how many cells were touched
     */
    public int fillLine(BlockPos a, BlockPos b, BlockState state) {
        if (state == null) return 0;
        boolean air = state.isAir();
        int x = a.getX(), y = a.getY(), z = a.getZ();
        int x1 = b.getX(), y1 = b.getY(), z1 = b.getZ();
        int dx = Math.abs(x1 - x), dy = Math.abs(y1 - y), dz = Math.abs(z1 - z);
        int sx = x < x1 ? 1 : -1, sy = y < y1 ? 1 : -1, sz = z < z1 ? 1 : -1;
        int dm = Math.max(dx, Math.max(dy, dz));
        if (dm == 0) {
            BlockPos p = new BlockPos(x, y, z);
            if (air) setAir(p); else setBlock(p, state);
            return 1;
        }
        int errX = dm / 2, errY = dm / 2, errZ = dm / 2;
        int touched = 0;
        for (int i = 0; i <= dm; i++) {
            BlockPos p = new BlockPos(x, y, z);
            if (air) setAir(p); else setBlock(p, state);
            touched++;
            errX -= dx; errY -= dy; errZ -= dz;
            if (errX < 0) { x += sx; errX += dm; }
            if (errY < 0) { y += sy; errY += dm; }
            if (errZ < 0) { z += sz; errZ += dm; }
        }
        return touched;
    }

    /**
     * Shifts every cell so the bounding box's minimum corner sits at
     * BlockPos.ZERO. Called before save and
     * before rotate (so rotation happens around a known-local box).
     */
    public void normalizeOrigin() {
        BlockPos[] b = computeBounds();
        if (b == null) return;
        BlockPos min = b[0];
        if (min.getX() == 0 && min.getY() == 0 && min.getZ() == 0) return;
        Map<BlockPos, BlockState> shifted = new HashMap<>(cells.size());
        Map<BlockPos, CompressedBlockEntity> shiftedNbt = new HashMap<>(blockEntityNbt.size());
        for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
            BlockPos oldPos = e.getKey();
            BlockPos newPos = oldPos.subtract(min);
            shifted.put(newPos, e.getValue());
            CompressedBlockEntity te = blockEntityNbt.get(oldPos);
            if (te != null) shiftedNbt.put(newPos, CompressedBlockEntity.of(withPos(te.get(), newPos)));
        }
        cells.clear();
        cells.putAll(shifted);
        blockEntityNbt.clear();
        blockEntityNbt.putAll(shiftedNbt);
        if (!spawnCaps.isEmpty()) {
            Map<BlockPos, StructureSpawnCap> shiftedCaps = new HashMap<>(spawnCaps.size());
            for (Map.Entry<BlockPos, StructureSpawnCap> e : spawnCaps.entrySet()) {
                shiftedCaps.put(e.getKey().subtract(min), e.getValue());
            }
            spawnCaps.clear();
            spawnCaps.putAll(shiftedCaps);
        }
        if (scanOrigin != null) scanOrigin = scanOrigin.offset(min);
        if (!scannedEntities.isEmpty()) {
            List<CompoundTag> shiftedEntities = new ArrayList<>(scannedEntities.size());
            for (CompoundTag entry : scannedEntities) {
                shiftedEntities.add(shiftScannedEntity(entry, min));
            }
            scannedEntities.clear();
            scannedEntities.addAll(shiftedEntities);
        }
        dirty = true;
        version++;
    }

    private static CompoundTag shiftScannedEntity(CompoundTag entry, BlockPos min) {
        CompoundTag copy = entry.copy();
        if (copy.contains("pos", 9 )) {
            ListTag posTag = copy.getList("pos", 6 );
            if (posTag.size() >= 3) {
                double relX = posTag.getDouble(0) - min.getX();
                double relY = posTag.getDouble(1) - min.getY();
                double relZ = posTag.getDouble(2) - min.getZ();
                ListTag newPos = new ListTag();
                newPos.add(DoubleTag.valueOf(relX));
                newPos.add(DoubleTag.valueOf(relY));
                newPos.add(DoubleTag.valueOf(relZ));
                copy.put("pos", newPos);
            }
        }
        if (copy.contains("blockPos", 9 )) {
            ListTag bp = copy.getList("blockPos", 3 );
            if (bp.size() >= 3) {
                ListTag newBp = new ListTag();
                newBp.add(IntTag.valueOf(bp.getInt(0) - min.getX()));
                newBp.add(IntTag.valueOf(bp.getInt(1) - min.getY()));
                newBp.add(IntTag.valueOf(bp.getInt(2) - min.getZ()));
                copy.put("blockPos", newBp);
            }
        }
        return copy;
    }

    private static CompoundTag withPos(CompoundTag te, BlockPos pos) {
        CompoundTag copy = te.copy();
        copy.putInt("x", pos.getX());
        copy.putInt("y", pos.getY());
        copy.putInt("z", pos.getZ());
        return copy;
    }

    /**
     * Rotates the entire structure 90 degrees clockwise around the Y axis,
     * in place. Individual block states are rotated too (facing/axis
     * properties), via vanilla {@link BlockState#rotate(Rotation)}, so
     * e.g. a furnace keeps facing a sensible direction after the turn.
     */
    public void rotate90() {
        normalizeOrigin();
        int[] size = computeSize();
        if (size == null) return;
        int sizeX = size[0], sizeZ = size[2];
        Map<BlockPos, BlockState> rotated = new HashMap<>(cells.size());
        Map<BlockPos, CompressedBlockEntity> rotatedNbt = new HashMap<>(blockEntityNbt.size());
        for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
            BlockPos p = e.getKey();
            BlockPos newPos = new BlockPos(sizeZ - 1 - p.getZ(), p.getY(), p.getX());
            rotated.put(newPos, fixupMultiCellOffsets(e.getValue().rotate(Rotation.CLOCKWISE_90), MultiCellTransform.ROTATE_CW_90));
            CompressedBlockEntity te = blockEntityNbt.get(p);
            if (te != null) rotatedNbt.put(newPos, CompressedBlockEntity.of(withPos(te.get(), newPos)));
        }
        cells.clear();
        cells.putAll(rotated);
        blockEntityNbt.clear();
        blockEntityNbt.putAll(rotatedNbt);
        if (!spawnCaps.isEmpty()) {
            Map<BlockPos, StructureSpawnCap> rotatedCaps = new HashMap<>(spawnCaps.size());
            for (Map.Entry<BlockPos, StructureSpawnCap> e : spawnCaps.entrySet()) {
                BlockPos p = e.getKey();
                rotatedCaps.put(new BlockPos(sizeZ - 1 - p.getZ(), p.getY(), p.getX()), e.getValue());
            }
            spawnCaps.clear();
            spawnCaps.putAll(rotatedCaps);
        }
        transformScannedEntities(sizeX, sizeZ, MultiCellTransform.ROTATE_CW_90);
        dirty = true;
        version++;
        assert sizeX >= 0;
    }

    /** Mirrors the entire structure across the X axis (left/right swap), in place. Block states are mirrored too (a stair's facing, a door's hinge, etc.) via vanilla {@link BlockState#mirror(Mirror)}, same as {@link #rotate90()} does for rotation. */
    public void flipX() {
        flip(true);
    }

    /** Mirrors the entire structure across the Z axis (front/back swap), in place. See {@link #flipX()}. */
    public void flipZ() {
        flip(false);
    }

    private void flip(boolean alongX) {
        normalizeOrigin();
        int[] size = computeSize();
        if (size == null) return;
        int sizeX = size[0], sizeZ = size[2];
        // alongX invierte la X de las posiciones; en Minecraft FRONT_BACK invierte X y LEFT_RIGHT invierte Z
        Mirror mirror = alongX ? Mirror.FRONT_BACK : Mirror.LEFT_RIGHT;
        Map<BlockPos, BlockState> flipped = new HashMap<>(cells.size());
        Map<BlockPos, CompressedBlockEntity> flippedNbt = new HashMap<>(blockEntityNbt.size());
        for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
            BlockPos p = e.getKey();
            BlockPos newPos = mirrorPos(p, alongX, sizeX, sizeZ);
            flipped.put(newPos, fixupMultiCellOffsets(e.getValue().mirror(mirror), alongX ? MultiCellTransform.MIRROR_X : MultiCellTransform.MIRROR_Z));
            CompressedBlockEntity te = blockEntityNbt.get(p);
            if (te != null) flippedNbt.put(newPos, CompressedBlockEntity.of(withPos(te.get(), newPos)));
        }
        cells.clear();
        cells.putAll(flipped);
        blockEntityNbt.clear();
        blockEntityNbt.putAll(flippedNbt);
        if (!spawnCaps.isEmpty()) {
            Map<BlockPos, StructureSpawnCap> flippedCaps = new HashMap<>(spawnCaps.size());
            for (Map.Entry<BlockPos, StructureSpawnCap> e : spawnCaps.entrySet()) {
                flippedCaps.put(mirrorPos(e.getKey(), alongX, sizeX, sizeZ), e.getValue());
            }
            spawnCaps.clear();
            spawnCaps.putAll(flippedCaps);
        }
        transformScannedEntities(sizeX, sizeZ, alongX ? MultiCellTransform.MIRROR_X : MultiCellTransform.MIRROR_Z);
        dirty = true;
        version++;
    }

    /** Mueve los mobs escaneados igual que las celdas: posicion, celda y hacia donde miran (como Entity#rotate/#mirror). */
    private void transformScannedEntities(int sizeX, int sizeZ, MultiCellTransform transform) {
        if (scannedEntities.isEmpty()) return;
        List<CompoundTag> moved = new ArrayList<>(scannedEntities.size());
        for (CompoundTag entry : scannedEntities) {
            CompoundTag copy = entry.copy();
            if (copy.contains("pos", 9)) {
                ListTag posTag = copy.getList("pos", 6);
                if (posTag.size() >= 3) {
                    double x = posTag.getDouble(0), y = posTag.getDouble(1), z = posTag.getDouble(2);
                    double nx = x, nz = z;
                    switch (transform) {
                        case ROTATE_CW_90 -> { nx = sizeZ - z; nz = x; }
                        case MIRROR_X -> nx = sizeX - x;
                        case MIRROR_Z -> nz = sizeZ - z;
                    }
                    ListTag newPos = new ListTag();
                    newPos.add(DoubleTag.valueOf(nx));
                    newPos.add(DoubleTag.valueOf(y));
                    newPos.add(DoubleTag.valueOf(nz));
                    copy.put("pos", newPos);
                    copy.put("blockPos", intListOf((int) Math.floor(nx), (int) Math.floor(y), (int) Math.floor(nz)));
                }
            }
            if (copy.contains("nbt", 10)) {
                CompoundTag data = copy.getCompound("nbt");
                ListTag rot = data.getList("Rotation", 5);
                if (rot.size() >= 2) {
                    float yaw = rot.getFloat(0);
                    float newYaw = switch (transform) {
                        case ROTATE_CW_90 -> yaw + 90f;
                        case MIRROR_X -> -yaw;
                        case MIRROR_Z -> 180f - yaw;
                    };
                    ListTag newRot = new ListTag();
                    newRot.add(net.minecraft.nbt.FloatTag.valueOf(newYaw));
                    newRot.add(net.minecraft.nbt.FloatTag.valueOf(rot.getFloat(1)));
                    data.put("Rotation", newRot);
                }
            }
            moved.add(copy);
        }
        scannedEntities.clear();
        scannedEntities.addAll(moved);
    }

    private static ListTag intListOf(int a, int b, int c) {
        ListTag t = new ListTag();
        t.add(IntTag.valueOf(a));
        t.add(IntTag.valueOf(b));
        t.add(IntTag.valueOf(c));
        return t;
    }

    private static BlockPos mirrorPos(BlockPos p, boolean alongX, int sizeX, int sizeZ) {
        return alongX ? new BlockPos(sizeX - 1 - p.getX(), p.getY(), p.getZ())
                       : new BlockPos(p.getX(), p.getY(), sizeZ - 1 - p.getZ());
    }

    private enum MultiCellTransform { ROTATE_CW_90, MIRROR_X, MIRROR_Z }

    private static BlockState fixupMultiCellOffsets(BlockState state, MultiCellTransform transform) {
        Block block = state.getBlock();
        Property<?> xProp = block.getStateDefinition().getProperty("x_offset");
        Property<?> yProp = block.getStateDefinition().getProperty("y_offset");
        Property<?> zProp = block.getStateDefinition().getProperty("z_offset");
        if (!(xProp instanceof IntegerProperty) || !(yProp instanceof IntegerProperty) || !(zProp instanceof IntegerProperty)) {
            return state;
        }
        IntegerProperty xOffsetProp = (IntegerProperty) xProp;
        IntegerProperty yOffsetProp = (IntegerProperty) yProp;
        IntegerProperty zOffsetProp = (IntegerProperty) zProp;
        int xSize, zSize;
        try {
            Field xSizeField = block.getClass().getField("xSize");
            Field zSizeField = block.getClass().getField("zSize");
            xSize = xSizeField.getInt(block);
            zSize = zSizeField.getInt(block);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return state;
        }
        int ox = state.getValue(xOffsetProp);
        int oy = state.getValue(yOffsetProp);
        int oz = state.getValue(zOffsetProp);
        int nx = ox, ny = oy, nz = oz;
        switch (transform) {
            case ROTATE_CW_90 -> { nx = zSize - 1 - oz; nz = ox; }
            case MIRROR_X -> nx = xSize - 1 - ox;
            case MIRROR_Z -> nz = zSize - 1 - oz;
        }
        return state.setValue(xOffsetProp, nx).setValue(yOffsetProp, ny).setValue(zOffsetProp, nz);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id == null ? "" : id; }

    public boolean isDirty() { return dirty; }
    public void markClean() { dirty = false; }
    public void markDirty() { dirty = true; }

    /** One entry: when something happened, and a short description of what. */
    public static final class HistoryEntry {
        public final long timestamp;
        public final String description;

        HistoryEntry(long timestamp, String description) {
            this.timestamp = timestamp;
            this.description = description;
        }
    }

    private static final int MAX_HISTORY = 500;

    private final Deque<HistoryEntry> history = new ArrayDeque<>();

    /** Appends one entry to the change log, dropping the oldest once {@link #MAX_HISTORY} is reached. Callers log AFTER an action completes (unlike {@link #pushUndo()}, which is called before), since the description usually needs the result (e.g. which block ended up where). */
    public void recordHistory(String description) {
        if (description == null || description.isEmpty()) return;
        if (history.size() >= MAX_HISTORY) history.removeFirst();
        history.addLast(new HistoryEntry(System.currentTimeMillis(), description));
    }

    /** Newest-first snapshot of the log, safe for a caller to iterate while more entries are recorded later (it's a copy, not a live view). */
    public List<HistoryEntry> historyView() {
        List<HistoryEntry> out = new ArrayList<>(history);
        Collections.reverse(out);
        return out;
    }

    public void clearHistoryLog() {
        history.clear();
    }
}
