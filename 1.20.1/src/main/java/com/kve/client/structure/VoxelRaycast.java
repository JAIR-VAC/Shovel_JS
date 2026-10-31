package com.kve.client.structure;

import com.kve.common.structure.StructureVoxelGrid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

public final class VoxelRaycast {

    private VoxelRaycast() {}

    public static final class Hit {
        public final BlockPos cell;
        public final Direction face;
        public final BlockPos adjacent;

        Hit(BlockPos cell, Direction face) {
            this.cell = cell;
            this.face = face;
            this.adjacent = cell.relative(face);
        }
    }

    /**
     * @param maxDistance how far along the ray to search before giving up
     * @param padCells    how far beyond the structure's current bounds to keep
     *                    stepping, so a click "in front of" an otherwise empty
     *                    grid can still resolve to a sensible first-block position
     */
    public static Hit cast(StructureVoxelGrid grid, Vector3f origin, Vector3f dir, float maxDistance, int padCells) {
        if (dir.lengthSquared() < 1e-12f) return null;
        Vector3f d = new Vector3f(dir).normalize();

        int x = (int) Math.floor(origin.x);
        int y = (int) Math.floor(origin.y);
        int z = (int) Math.floor(origin.z);

        int stepX = sign(d.x), stepY = sign(d.y), stepZ = sign(d.z);
        float tMaxX = axisTMax(origin.x, d.x, x);
        float tMaxY = axisTMax(origin.y, d.y, y);
        float tMaxZ = axisTMax(origin.z, d.z, z);
        float tDeltaX = d.x != 0 ? Math.abs(1f / d.x) : Float.POSITIVE_INFINITY;
        float tDeltaY = d.y != 0 ? Math.abs(1f / d.y) : Float.POSITIVE_INFINITY;
        float tDeltaZ = d.z != 0 ? Math.abs(1f / d.z) : Float.POSITIVE_INFINITY;

        BlockPos[] bounds = grid.computeBounds();
        Direction lastFace = null;
        float traveled = 0f;

        int maxSteps = (int) (maxDistance * 3) + 64;
        for (int i = 0; i < maxSteps && traveled <= maxDistance; i++) {
            BlockPos cur = new BlockPos(x, y, z);
            if (!grid.isAirOrHidden(cur) && lastFace != null) {
                return new Hit(cur, lastFace);
            }
            if (!grid.isAirOrHidden(cur) && lastFace == null) {
                return new Hit(cur, Direction.UP);
            }
            if (bounds != null && outsidePadded(cur, bounds, padCells)) {
                if (traveled > 0.5f) break;
            }

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) { x += stepX; traveled = tMaxX; tMaxX += tDeltaX; lastFace = stepX > 0 ? Direction.WEST : Direction.EAST; }
                else { z += stepZ; traveled = tMaxZ; tMaxZ += tDeltaZ; lastFace = stepZ > 0 ? Direction.NORTH : Direction.SOUTH; }
            } else {
                if (tMaxY < tMaxZ) { y += stepY; traveled = tMaxY; tMaxY += tDeltaY; lastFace = stepY > 0 ? Direction.DOWN : Direction.UP; }
                else { z += stepZ; traveled = tMaxZ; tMaxZ += tDeltaZ; lastFace = stepZ > 0 ? Direction.NORTH : Direction.SOUTH; }
            }
        }
        return null;
    }

    /**
     * Fallback used when {@link #cast} finds nothing (empty grid, or click
     * missed everything): intersects the ray with the y=0 ground plane, so
     * the very first block of a brand-new structure can still be placed by
     * clicking in the viewport.
     */
    public static BlockPos castGroundPlane(Vector3f origin, Vector3f dir, int halfExtent) {
        Vector3f d = new Vector3f(dir).normalize();
        if (Math.abs(d.y) < 1e-6f) return null;
        float t = -origin.y / d.y;
        if (t < 0) return null;
        int x = (int) Math.floor(origin.x + d.x * t);
        int z = (int) Math.floor(origin.z + d.z * t);
        if (Math.abs(x) > halfExtent || Math.abs(z) > halfExtent) return null;
        return new BlockPos(x, 0, z);
    }

    private static boolean outsidePadded(BlockPos p, BlockPos[] bounds, int pad) {
        return p.getX() < bounds[0].getX() - pad || p.getX() > bounds[1].getX() + pad
                || p.getY() < bounds[0].getY() - pad || p.getY() > bounds[1].getY() + pad
                || p.getZ() < bounds[0].getZ() - pad || p.getZ() > bounds[1].getZ() + pad;
    }

    private static int sign(float v) { return v > 0 ? 1 : (v < 0 ? -1 : 0); }

    private static float axisTMax(float origin, float dir, int cell) {
        if (dir == 0) return Float.POSITIVE_INFINITY;
        float boundary = dir > 0 ? cell + 1 : cell;
        return (boundary - origin) / dir;
    }
}
