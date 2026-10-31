package com.kve.client.structure;

import com.kve.common.structure.StructureVoxelGrid;

import com.kve.testsupport.MinecraftBootstrapExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El editor de voxeles debe crecer/achicarse como el modo
 * mundo al colocar/romper, y el deshacer debe aguantar mucho mas que 20 pasos.
 */
@ExtendWith(MinecraftBootstrapExtension.class)
class StructureVoxelGridEditTest {

    @Test
    void breakingAnEdgeBlockShrinksTheBox() {
        StructureVoxelGrid g = new StructureVoxelGrid();
        g.setBlock(new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState());
        g.setBlock(new BlockPos(1, 0, 0), Blocks.STONE.defaultBlockState());
        g.setBlock(new BlockPos(5, 0, 0), Blocks.STONE.defaultBlockState());
        assertEquals(6, g.computeSize()[0]);
        g.breakCell(new BlockPos(5, 0, 0));
        assertEquals(2, g.computeSize()[0], "romper el bloque del borde debe achicar la caja");
    }

    @Test
    void breakingAnInteriorBlockKeepsTheCarvedAir() {
        StructureVoxelGrid g = new StructureVoxelGrid();
        for (int x = 0; x < 3; x++) g.setBlock(new BlockPos(x, 0, 0), Blocks.STONE.defaultBlockState());
        g.breakCell(new BlockPos(1, 0, 0));
        assertTrue(g.cellsView().containsKey(new BlockPos(1, 0, 0)), "el hueco interior queda tallado como aire");
        assertTrue(g.cellsView().get(new BlockPos(1, 0, 0)).isAir());
        assertEquals(3, g.computeSize()[0]);
    }

    @Test
    void undoKeepsFarMoreThanTwentySteps() {
        StructureVoxelGrid g = new StructureVoxelGrid();
        for (int i = 0; i < 300; i++) {
            BlockPos p = new BlockPos(i, 0, 0);
            g.pushUndoForCell(p);
            g.setBlock(p, Blocks.STONE.defaultBlockState());
        }
        assertEquals(300, g.undoDepth());
        for (int i = 0; i < 300; i++) g.undo();
        assertTrue(g.isEmpty(), "300 deshacer deben volver al estado inicial");
    }

    @Test
    void aBrushStrokeIsASingleUndoStep() {
        StructureVoxelGrid g = new StructureVoxelGrid();
        g.setBlock(new BlockPos(0, 0, 0), Blocks.DIRT.defaultBlockState());
        g.clearUndoHistory();
        g.pushUndoForCell(new BlockPos(0, 1, 0));
        g.setBlock(new BlockPos(0, 1, 0), Blocks.STONE.defaultBlockState());
        for (int y = 2; y < 10; y++) {
            g.extendLastUndoForCells(List.of(new BlockPos(0, y, 0)));
            g.setBlock(new BlockPos(0, y, 0), Blocks.STONE.defaultBlockState());
        }
        assertEquals(1, g.undoDepth());
        g.undo();
        assertEquals(1, g.cellCount(), "un deshacer quita el trazo entero");
        g.redo();
        assertEquals(10, g.cellCount());
    }
}
