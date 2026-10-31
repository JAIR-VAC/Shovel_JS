package com.kve.client.gui.ritual;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RitualRingLayoutTest {

    @Test
    void spreadDistributesLoadedOfferingsEvenly() {
        assertEquals(0, RitualRingLayout.spreadSlot(0, 4, 8));
        assertEquals(2, RitualRingLayout.spreadSlot(1, 4, 8));
        assertEquals(4, RitualRingLayout.spreadSlot(2, 4, 8));
        assertEquals(6, RitualRingLayout.spreadSlot(3, 4, 8));
        assertEquals(7, RitualRingLayout.spreadSlot(7, 8, 8));
    }

    @Test
    void customPositionsFollowDescriptorOrderAndDoNotOverlap() {
        List<int[]> goety = List.of(new int[]{0, -30}, new int[]{30, 0}, new int[]{0, 30},
                new int[]{-30, 0}, new int[]{15, -30});
        List<RitualRingLayout.Offering> pos = RitualRingLayout.computeCustomPositions(100, 100, goety);
        assertEquals(5, pos.size());
        assertTrue(pos.get(0).y < 100 && Math.abs(pos.get(0).x + 9 - 100) <= 1, "ingrediente 0 arriba");
        assertTrue(pos.get(1).x > 100, "ingrediente 1 a la derecha");
        assertTrue(pos.get(2).y > 100, "ingrediente 2 abajo");
        assertTrue(pos.get(3).x + RitualRingLayout.OFFERING_SLOT < 100, "ingrediente 3 a la izquierda");
        assertTrue(pos.get(4).x - pos.get(0).x >= RitualRingLayout.OFFERING_SLOT);
    }
}
