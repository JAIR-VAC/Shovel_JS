package com.kve.client.gui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/** Motor de retinte en HSL y paleta propia del tema BRONZE. */
class KveGuiStyleColorTest {

    @AfterEach
    void resetTheme() {
        KveGuiStyle.applyTheme(KveGuiStyle.Theme.CYAN);
    }

    private static float luminance(int argb) {
        return (0.2126f * ((argb >> 16) & 0xFF) + 0.7152f * ((argb >> 8) & 0xFF) + 0.0722f * (argb & 0xFF)) / 255f;
    }

    @Test
    void hslRoundTripKeepsTheColor() throws Exception {
        Method toHsl = KveGuiStyle.class.getDeclaredMethod("toHsl", int.class, int.class, int.class);
        Method fromHsl = KveGuiStyle.class.getDeclaredMethod("fromHsl", float.class, float.class, float.class);
        toHsl.setAccessible(true);
        fromHsl.setAccessible(true);
        int[] samples = {0x000000, 0xFFFFFF, 0x808080, 0xFF0000, 0x00FF00, 0x0000FF, 0xE8C96A, 0x080C1A, 0xCCDDFF, 0x7F77DD, 0xC8834F};
        for (int rgb : samples) {
            float[] hsl = (float[]) toHsl.invoke(null, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            int back = (int) fromHsl.invoke(null, hsl[0], hsl[1], hsl[2]);
            for (int shift : new int[]{16, 8, 0}) {
                assertTrue(Math.abs(((rgb >> shift) & 0xFF) - ((back >> shift) & 0xFF)) <= 1,
                        String.format("%06X volvio como %06X", rgb, back));
            }
        }
    }

    @Test
    void cyanIsTheUntintedDefault() {
        KveGuiStyle.applyTheme(KveGuiStyle.Theme.CYAN);
        assertEquals(0xF0080C1A, KveGuiStyle.BG_PANEL);
        assertEquals(0xFFCCDDFF, KveGuiStyle.TEXT_PRIMARY);
    }

    @Test
    void retintKeepsDarkBackgroundsDarkAndLightTextLight() {
        KveGuiStyle.applyTheme(KveGuiStyle.Theme.GREEN);
        int panel = KveGuiStyle.BG_PANEL;
        assertNotEquals(0xF0080C1A, panel, "el tema verde retiñe el panel");
        assertEquals(0xF0, panel >>> 24, "el alfa se conserva");
        assertTrue(luminance(panel) < 0.10f, "el panel sigue oscuro");
        assertTrue(luminance(KveGuiStyle.TEXT_PRIMARY) > 0.70f, "el texto sigue claro");
        int g = (panel >> 8) & 0xFF, b = panel & 0xFF;
        assertTrue(g >= b, "el panel tira al verde del acento, no al azul de la base");
    }

    @Test
    void bronzeUsesItsOwnPaletteAndDerivesScrollbars() {
        KveGuiStyle.applyTheme(KveGuiStyle.Theme.BRONZE);
        assertEquals(0xF2221611, KveGuiStyle.BG_PANEL);
        assertEquals(KveGuiStyle.BORDER_B, KveGuiStyle.SCROLL_THUMB);
        assertEquals(KveGuiStyle.BG_FIELD, KveGuiStyle.SCROLL_TRACK);
        assertEquals(0xFFEFB45A, KveGuiStyle.ACCENT_GOLD);
    }
}
