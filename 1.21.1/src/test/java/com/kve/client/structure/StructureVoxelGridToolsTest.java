package com.kve.client.structure;

import com.kve.common.structure.StructureVoxelGrid;
import com.kve.testsupport.MinecraftBootstrapExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Reemplazar, copiar/cortar/pegar region, vista por capas y lista de materiales del editor de estructuras. */
@ExtendWith(MinecraftBootstrapExtension.class)
class StructureVoxelGridToolsTest {

    @Test
    void replaceKeepsSharedProperties() {
        StructureVoxelGrid g = new StructureVoxelGrid();
        BlockPos p = new BlockPos(0, 0, 0);
        g.setBlock(p, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        List<BlockPos> targets = g.cellsOfBlock(Blocks.OAK_STAIRS, null, null);
        assertEquals(1, g.replaceCells(targets, Blocks.SPRUCE_STAIRS.defaultBlockState()));
        assertEquals(Blocks.SPRUCE_STAIRS, g.get(p).getBlock());
        assertEquals(Direction.EAST, g.get(p).getValue(StairBlock.FACING), "la orientacion se conserva al reemplazar");
    }

    @Test
    void replaceCanBeLimitedToABoxAndUndone() {
        StructureVoxelGrid g = new StructureVoxelGrid();
        for (int x = 0; x < 5; x++) g.setBlock(new BlockPos(x, 0, 0), Blocks.STONE.defaultBlockState());
        List<BlockPos> targets = g.cellsOfBlock(Blocks.STONE, new BlockPos(0, 0, 0), new BlockPos(1, 0, 0));
        assertEquals(2, targets.size());
        g.pushUndoForCells(targets);
        g.replaceCells(targets, Blocks.DIRT.defaultBlockState());
        assertEquals(Blocks.DIRT, g.get(new BlockPos(1, 0, 0)).getBlock());
        assertEquals(Blocks.STONE, g.get(new BlockPos(2, 0, 0)).getBlock(), "fuera de la caja no se toca");
        g.undo();
        assertEquals(Blocks.STONE, g.get(new BlockPos(0, 0, 0)).getBlock());
    }

    @Test
    void copyAndPasteKeepsBlocksAndBlockEntityData() {
        StructureVoxelGrid g = new StructureVoxelGrid();
        g.setBlock(new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState());
        g.setBlock(new BlockPos(1, 0, 0), Blocks.CHEST.defaultBlockState());
        CompoundTag tag = new CompoundTag();
        tag.putString("CustomName", "\"botin\"");
        g.setBlockEntityNbt(new BlockPos(1, 0, 0), tag);

        StructureVoxelGrid.Clip clip = g.copyRegion(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0));
        assertEquals(2, clip.cells().size());
        assertEquals(2, clip.sizeX());

        BlockPos origin = new BlockPos(0, 2, 0);
        assertEquals(2, g.pasteClip(clip, origin));
        assertEquals(Blocks.STONE, g.get(new BlockPos(0, 2, 0)).getBlock());
        assertEquals(Blocks.CHEST, g.get(new BlockPos(1, 2, 0)).getBlock());
        assertNotNull(g.getBlockEntityNbt(new BlockPos(1, 2, 0)), "el contenido del cofre viaja con el pegado");
        assertEquals("\"botin\"", g.getBlockEntityNbt(new BlockPos(1, 2, 0)).getString("CustomName"));
    }

    @Test
    void cutRemovesTheRegionAndUndoBringsItBack() {
        StructureVoxelGrid g = new StructureVoxelGrid();
        for (int x = 0; x < 3; x++) g.setBlock(new BlockPos(x, 0, 0), Blocks.STONE.defaultBlockState());
        List<BlockPos> cells = g.cellsInBox(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0));
        g.pushUndoForCells(cells);
        assertEquals(2, g.removeCells(cells));
        assertEquals(1, g.cellCount());
        g.undo();
        assertEquals(3, g.cellCount());
    }

    @Test
    void layerCutHidesUpperCellsWithoutTouchingData() {
        StructureVoxelGrid g = new StructureVoxelGrid();
        for (int y = 0; y < 4; y++) g.setBlock(new BlockPos(0, y, 0), Blocks.STONE.defaultBlockState());
        g.markClean();
        g.setViewMaxY(1);
        assertTrue(g.visibleCellPositions().stream().allMatch(p -> p.getY() <= 1));
        assertTrue(g.isAirOrHidden(new BlockPos(0, 2, 0)), "el raycast atraviesa las capas ocultas");
        assertFalse(g.isAirOrHidden(new BlockPos(0, 1, 0)));
        assertEquals(4, g.cellCount(), "el corte no borra nada");
        assertFalse(g.isDirty(), "cambiar la vista no marca la estructura como modificada");
        g.clearLayerCut();
        assertFalse(g.hasLayerCut());
        assertEquals(4, g.visibleCellPositions().size());
    }

    @Test
    void materialCountsAreSortedByAmount() {
        StructureVoxelGrid g = new StructureVoxelGrid();
        for (int x = 0; x < 3; x++) g.setBlock(new BlockPos(x, 0, 0), Blocks.STONE.defaultBlockState());
        g.setBlock(new BlockPos(0, 1, 0), Blocks.DIRT.defaultBlockState());
        g.breakCell(new BlockPos(1, 0, 0)); // queda como aire tallado: no cuenta
        List<Map.Entry<Block, Integer>> counts = g.materialCounts();
        assertEquals(Blocks.STONE, counts.get(0).getKey());
        assertEquals(2, counts.get(0).getValue());
        assertEquals(Blocks.DIRT, counts.get(1).getKey());
        assertEquals(2, counts.size(), "el aire no aparece en la lista");
    }
}
