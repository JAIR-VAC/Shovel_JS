package com.kve.common.mobrules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Velocidad de ataque del Mob Editor -> intervalo de la IA. */
class MobAttackSpeedTest {

    @Test
    void attacksPerSecondToTicks() {
        assertEquals(20, MobAttackSpeed.intervalTicks(1.0));
        assertEquals(10, MobAttackSpeed.intervalTicks(2.0));
        assertEquals(40, MobAttackSpeed.intervalTicks(0.5));
        assertEquals(1, MobAttackSpeed.intervalTicks(100.0)); // nunca 0
    }

    @Test
    void unsetOrInvalidMeansVanilla() {
        assertEquals(-1, MobAttackSpeed.intervalTicks(Double.NaN));
        assertEquals(-1, MobAttackSpeed.intervalTicks(0));
        assertEquals(-1, MobAttackSpeed.intervalTicks(-3));
    }
}
