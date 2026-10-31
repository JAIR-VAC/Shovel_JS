package com.kve.common.kubejs;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cubre {@link ItemAttrMath}: la conversion campo-de-texto <-> modifier
 * crudo de los stats core de ItemAttributeScreen. Java puro, sin bootstrap.
 * Golden real detras de esto: la velocidad de ataque de una espada vanilla
 * es {@code -2.4F} (= -2.4000000953674316 como double) y con la comparacion
 * cruda vieja 7 de las 11 velocidades float vanilla se exportaban como
 * "editadas" sin que el jugador las tocara.
 */
class ItemAttrMathTest {

    private static double throughTheField(double raw, boolean additive, double base) {
        String text = ItemAttrMath.fmt(ItemAttrMath.toEffective(raw, additive, base));
        return ItemAttrMath.fromEffective(Double.parseDouble(text), additive, base);
    }

    @Test
    void vanillaFloatAttackSpeedsAreNotFlaggedAsEdited() {
        float[] speeds = {-2.4F, -2.8F, -3.0F, -3.1F, -3.2F, -1.0F, -2.0F, -2.9F, -2.6F, -2.5F, -3.3F};
        for (float f : speeds) {
            double raw = f;
            assertTrue(ItemAttrMath.sameAsBaseline(throughTheField(raw, true, ItemAttrMath.ATTACK_SPEED_BASE),
                    raw, true, ItemAttrMath.ATTACK_SPEED_BASE), "speed " + f);
        }
    }

    @Test
    void oldRawComparisonWasReallyBroken() {
        double raw = -2.4F;
        assertNotEquals(raw, throughTheField(raw, true, ItemAttrMath.ATTACK_SPEED_BASE));
    }

    @Test
    void realEditsAreStillDetected() {
        double raw = -2.4F;
        double edited = ItemAttrMath.fromEffective(1.7, true, ItemAttrMath.ATTACK_SPEED_BASE);
        assertFalse(ItemAttrMath.sameAsBaseline(edited, raw, true, ItemAttrMath.ATTACK_SPEED_BASE));
        double tiny = ItemAttrMath.fromEffective(1.601, true, ItemAttrMath.ATTACK_SPEED_BASE);
        assertFalse(ItemAttrMath.sameAsBaseline(tiny, raw, true, ItemAttrMath.ATTACK_SPEED_BASE));
    }

    @Test
    void attackDamageShownAsFinalValue() {
        assertEquals("4", ItemAttrMath.fmt(ItemAttrMath.toEffective(3.0, true, ItemAttrMath.ATTACK_DAMAGE_BASE)));
        assertEquals(3.0, ItemAttrMath.fromEffective(4.0, true, ItemAttrMath.ATTACK_DAMAGE_BASE));
        assertEquals(-0.5, ItemAttrMath.fromEffective(0.5, true, ItemAttrMath.ATTACK_DAMAGE_BASE));
    }

    @Test
    void nanIsNoData() {
        assertTrue(ItemAttrMath.sameAsBaseline(Double.NaN, Double.NaN, true, 1.0));
        assertFalse(ItemAttrMath.sameAsBaseline(Double.NaN, 3.0, true, 1.0));
        assertFalse(ItemAttrMath.sameAsBaseline(3.0, Double.NaN, true, 1.0));
        assertTrue(Double.isNaN(ItemAttrMath.fromEffective(Double.NaN, true, 4.0)));
    }

    @Test
    void nonAdditiveOpsAreNotConverted() {
        assertEquals(0.2, ItemAttrMath.toEffective(0.2, false, 4.0));
        assertEquals(0.2, ItemAttrMath.fromEffective(0.2, false, 4.0));
        double raw = 0.2F;
        assertTrue(ItemAttrMath.sameAsBaseline(throughTheField(raw, false, 4.0), raw, false, 4.0));
    }

    @Test
    void fmtIgnoresDefaultLocale() {
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("es", "ES"));
            assertEquals("1.500", ItemAttrMath.fmt(1.5));
        } finally {
            Locale.setDefault(old);
        }
    }
}
