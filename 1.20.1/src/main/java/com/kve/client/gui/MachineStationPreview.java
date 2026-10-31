package com.kve.client.gui;

import com.kve.client.structure.OrbitCamera;
import com.kve.client.structure.StructureSceneRenderer;
import com.kve.common.structure.StructureVoxelGrid;
import com.kve.common.recipetype.RecipeTypeDescriptor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class MachineStationPreview {

    private MachineStationPreview() {}

    private static final Map<String, StructureVoxelGrid> GRID_CACHE = new HashMap<>();

    private static final OrbitCamera CAMERA = new OrbitCamera(3.2f, 1.5f, 40f, 35f);

    private static final Map<StructureVoxelGrid, float[]> FRAMING_CACHE = new java.util.IdentityHashMap<>();

    private static final Map<String, List<RelativeBlock>> MULTIBLOCK_OVERRIDES = new HashMap<>();
    static {
        MULTIBLOCK_OVERRIDES.put("create:mixing",
                List.of(new RelativeBlock(new BlockPos(0, -1, 0),
                        () -> blockState("create:basin"))));
        MULTIBLOCK_OVERRIDES.put("create:crushing",
                List.of(new RelativeBlock(new BlockPos(1, 0, 0),
                        () -> blockState("create:crushing_wheel"))));
        MULTIBLOCK_OVERRIDES.put("create:compacting",
                List.of(new RelativeBlock(new BlockPos(0, -1, 0),
                        () -> blockState("create:basin"))));
        MULTIBLOCK_OVERRIDES.put("create:splashing",
                List.of(new RelativeBlock(new BlockPos(0, 0, 1),
                        () -> net.minecraft.world.level.block.Blocks.WATER.defaultBlockState())));
        MULTIBLOCK_OVERRIDES.put("create:haunting",
                List.of(new RelativeBlock(new BlockPos(0, 0, 1),
                        () -> net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE.defaultBlockState())));

        List<RelativeBlock> arsPedestalRing = List.of(
                new RelativeBlock(new BlockPos(2, 0, 0), () -> blockState("ars_nouveau:arcane_pedestal")),
                new RelativeBlock(new BlockPos(-2, 0, 0), () -> blockState("ars_nouveau:arcane_pedestal")),
                new RelativeBlock(new BlockPos(0, 0, 2), () -> blockState("ars_nouveau:arcane_pedestal")),
                new RelativeBlock(new BlockPos(0, 0, -2), () -> blockState("ars_nouveau:arcane_pedestal")));
        MULTIBLOCK_OVERRIDES.put("ars_nouveau:enchanting_apparatus", arsPedestalRing);
        MULTIBLOCK_OVERRIDES.put("ars_nouveau:enchantment", arsPedestalRing);
        MULTIBLOCK_OVERRIDES.put("ars_nouveau:imbuement", arsPedestalRing);

        MULTIBLOCK_OVERRIDES.put("farmersdelight:cooking",
                List.of(new RelativeBlock(new BlockPos(0, -1, 0), () ->
                        net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.CampfireBlock.LIT, true))));
    }

    /**
     * Renders the hero preview for {@code descriptor} into the given GUI
     * rectangle, or does nothing and returns {@code false} if this
     * recipe type has no resolvable station block - the caller
     * ({@code MachineRecipeEditScreen}) is expected to fall back to the
     * old flat {@code g.renderItem()} icon in that case, so nothing
     * regresses for types this hasn't been rolled out to yet.
     */
    public static boolean render(GuiGraphics g, RecipeTypeDescriptor descriptor, int x, int y, int w, int h) {
        if (descriptor == null || w <= 0 || h <= 0) return false;
        StructureVoxelGrid grid = resolveGrid(descriptor);
        if (grid == null) return renderItemFallback(g, descriptor, x, y, w, h);
        float[] fit = FRAMING_CACHE.computeIfAbsent(grid, MachineStationPreview::fitCamera);
        CAMERA.setTarget(new org.joml.Vector3f(fit[0], fit[1], fit[2]));
        CAMERA.setDistance(fit[3]);
        // Giro lento (una vuelta cada 16 s) para que la estacion
        // se lea como un bloque 3D y no como un icono; estructuras grandes
        // (pedestales, basin + prensa) se entienden mejor al verlas girar.
        float turn = (float) ((System.currentTimeMillis() % 16_000L) / 16_000.0 * Math.PI * 2.0);
        CAMERA.setYawPitch(0.7f + turn, 0.5f);
        StructureSceneRenderer.render(x, y, w, h, grid, CAMERA, null, false);
        return true;
    }

    private static float[] fitCamera(StructureVoxelGrid grid) {
        BlockPos[] b = grid.computeBounds();
        if (b == null) return new float[]{0.5f, 0.5f, 0.5f, 3.2f};
        float minX = b[0].getX(), minY = b[0].getY(), minZ = b[0].getZ();
        float maxX = b[1].getX() + 1, maxY = b[1].getY() + 1, maxZ = b[1].getZ() + 1;
        float cx = (minX + maxX) / 2f, cy = (minY + maxY) / 2f, cz = (minZ + maxZ) / 2f;
        float hx = (maxX - minX) / 2f, hy = (maxY - minY) / 2f, hz = (maxZ - minZ) / 2f;
        float radius = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
        float halfFov = (float) Math.toRadians(CAMERA.fovDegrees() / 2f);
        float distance = radius / (float) Math.sin(halfFov) * 1.08f;
        return new float[]{cx, cy, cz, distance};
    }

    private static boolean renderItemFallback(GuiGraphics g, RecipeTypeDescriptor descriptor, int x, int y, int w, int h) {
        if (!descriptor.hasStationItem()) return false;
        ResourceLocation id = ResourceLocation.tryParse(descriptor.stationItem);
        Item item = id == null ? null : BuiltInRegistries.ITEM.get(id);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return false;
        int size = Math.min(w, h);
        float scale = Math.max(1f, size / 16f * 0.85f);
        g.pose().pushPose();
        g.pose().translate(x + w / 2f - 8 * scale, y + h / 2f - 8 * scale, 0);
        g.pose().scale(scale, scale, 1f);
        try { g.renderItem(new net.minecraft.world.item.ItemStack(item), 0, 0); } catch (Throwable ignored) {}
        g.pose().popPose();
        return true;
    }

    private static StructureVoxelGrid resolveGrid(RecipeTypeDescriptor descriptor) {
        String key = descriptor.recipeSerializer;
        if (key == null || key.isEmpty()) return null;
        if (GRID_CACHE.containsKey(key)) return GRID_CACHE.get(key);

        StructureVoxelGrid grid = buildGrid(descriptor);
        GRID_CACHE.put(key, grid);
        return grid;
    }

    private static StructureVoxelGrid buildGrid(RecipeTypeDescriptor descriptor) {
        BlockState primary = resolveStationBlockState(descriptor);
        if (primary == null) return null;

        StructureVoxelGrid grid = new StructureVoxelGrid();
        grid.setBlock(BlockPos.ZERO, primary);

        if (!descriptor.stationStructure.isEmpty()) {
            for (RecipeTypeDescriptor.StationBlock sb : descriptor.stationStructure) {
                BlockState st = blockStateOfBlock(sb.block);
                if (st == null) continue;
                if (sb.state != null && !sb.state.isBlank()) st = applyProperties(st, sb.state);
                grid.setBlock(new BlockPos(sb.x, sb.y, sb.z), st);
            }
            return grid;
        }
        List<RelativeBlock> extra = MULTIBLOCK_OVERRIDES.get(descriptor.recipeSerializer);
        if (extra != null) {
            for (RelativeBlock rb : extra) {
                BlockState extraState = rb.state.get();
                if (extraState != null) grid.setBlock(rb.offset, extraState);
            }
        }
        return grid;
    }

    private static BlockState resolveStationBlockState(RecipeTypeDescriptor descriptor) {
        if (!descriptor.hasStationItem()) return null;
        BlockState state = blockState(descriptor.stationItem);
        if (state == null) return null;
        if (state.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE) return null;
        if (descriptor.stationBlockState != null && !descriptor.stationBlockState.isBlank()) {
            state = applyProperties(state, descriptor.stationBlockState);
        }
        return state;
    }

    private static BlockState blockStateOfBlock(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return blockState(blockId);
        return BuiltInRegistries.BLOCK.get(id).defaultBlockState();
    }

    private static BlockState blockState(String itemId) {
        ResourceLocation id;
        try {
            id = new ResourceLocation(itemId);
        } catch (Exception e) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (!(item instanceof BlockItem blockItem)) return null;
        return blockItem.getBlock().defaultBlockState();
    }

    private static BlockState applyProperties(BlockState state, String spec) {
        for (String pair : spec.split(",")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String propName = pair.substring(0, eq).trim();
            String valueStr = pair.substring(eq + 1).trim();
            Property<?> prop = state.getBlock().getStateDefinition().getProperty(propName);
            if (prop != null) state = setPropertyByName(state, prop, valueStr);
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState setPropertyByName(BlockState state, Property<T> prop, String valueStr) {
        Optional<T> value = prop.getValue(valueStr);
        return value.map(v -> state.setValue(prop, v)).orElse(state);
    }

    /** Drops every cached grid - call if station block resolution ever needs to be forced fresh (e.g. after a recipe_types JSON reload). Mirrors {@link StructureSceneRenderer#resetCache()}'s role for the structure editor. */
    public static void resetCache() {
        GRID_CACHE.clear();
        FRAMING_CACHE.clear();
    }

    private record RelativeBlock(BlockPos offset, Supplier<BlockState> state) {}
}
