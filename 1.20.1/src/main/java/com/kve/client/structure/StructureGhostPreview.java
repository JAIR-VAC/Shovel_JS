package com.kve.client.structure;

import com.kve.common.structure.StructureVoxelGrid;

import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * <p>
 * Small, deliberately static piece of shared client state, not a widget or
 * a screen: it just remembers "is the ghost armed", "which grid should it
 * draw", and "where in the world is it anchored", so it survives closing
 * and reopening {@link StructureEditorScreen} (a player arms the ghost,
 * closes the editor to walk around and look at the spot, presses the
 * anchor keybind while looking at a block, and keeps the editor closed the
 * whole time). {@link StructureGhostPreviewRenderer} reads this every
 * frame; {@link StructureEditorScreen} and {@link com.kve.client.keybind.KeyTickHandler}
 * write to it.
 * <p>
 * {@link #setGrid()} intentionally stores the SAME live {@link StructureVoxelGrid}
 * instance the editor is mutating, not a snapshot - so editing the
 * structure while the ghost is armed (even with the editor screen closed
 * and reopened) is reflected in the world preview immediately, the same
 * way {@link StructureSceneRenderer}'s GUI viewport already tracks the
 * live grid rather than a copy. Nothing here ever mutates the grid itself.
 */
@OnlyIn(Dist.CLIENT)
public final class StructureGhostPreview {

    private StructureGhostPreview() {}

    private static boolean enabled = false;
    private static StructureVoxelGrid grid = null;
    private static BlockPos anchor = null;

    public static boolean isEnabled() {
        return enabled && grid != null;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static StructureVoxelGrid getGrid() {
        return grid;
    }

    public static void setGrid(StructureVoxelGrid g) {
        grid = g;
    }

    public static BlockPos getAnchor() {
        return anchor;
    }

    public static void setAnchor(BlockPos pos) {
        if (pos != null && grid != null) {
            grid.normalizeOrigin();
        }
        anchor = pos == null ? null : pos.immutable();
    }

    public static boolean hasAnchor() {
        return anchor != null;
    }

    /** Fully resets - called when a different structure is loaded into the editor, since a stale ghost of the PREVIOUS structure at some world position would be actively misleading rather than just outdated. */
    public static void clear() {
        enabled = false;
        grid = null;
        anchor = null;
    }
}
