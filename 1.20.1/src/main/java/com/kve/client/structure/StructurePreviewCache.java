package com.kve.client.structure;

import com.kve.common.structure.StructureVoxelGrid;

import com.kve.common.structure.StructureNbtIO;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public final class StructurePreviewCache {

    private static final StructureVoxelGrid FAILED = new StructureVoxelGrid();
    private static final StructureVoxelGrid PENDING = new StructureVoxelGrid();

    private final Map<String, StructureVoxelGrid> cache = new HashMap<>();

    public StructureVoxelGrid get(String id) {
        if (id == null || id.isBlank() || !id.contains(":")) return null;
        StructureVoxelGrid cached = cache.get(id);
        if (cached != null) return (cached == FAILED || cached == PENDING) ? null : cached;

        ResourceLocation rl;
        try {
            rl = new ResourceLocation(id);
        } catch (Exception ex) {
            cache.put(id, FAILED);
            return null;
        }
        try {
            StructureNbtIO.LoadResult result = StructureNbtIO.loadEffective(rl);
            cache.put(id, result.grid);
            return result.grid;
        } catch (Exception ex) {
            if (isConnectedToRemoteServer()) {
                cache.put(id, PENDING);
                StructureRemoteLoadState.request(rl,
                        result -> cache.put(id, result.grid),
                        failedId -> cache.put(id, FAILED));
            } else {
                cache.put(id, FAILED);
            }
            return null;
        }
    }

    private static boolean isConnectedToRemoteServer() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getSingleplayerServer() == null && mc.getConnection() != null;
    }

    public boolean hasAttempted(String id) {
        if (id == null) return false;
        StructureVoxelGrid cached = cache.get(id);
        return cached != null && cached != PENDING;
    }

    public void clear() { cache.clear(); }
}
