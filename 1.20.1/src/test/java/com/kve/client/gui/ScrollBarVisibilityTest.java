package com.kve.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Una barra de scroll sin contenido de sobra no captura el raton (la logica de visibilidad). */
class ScrollBarVisibilityTest {

    @Test
    void noOverflowMeansNoScrollAndNoClickCapture() {
        ScrollBar bar = new ScrollBar(0, 0, 100, 5, 10);
        assertEquals(0, bar.maxScroll());
        assertFalse(bar.mouseClicked(2, 50, 0), "una barra vacia no debe capturar el clic");
    }

    @Test
    void overflowKeepsScrollingAndClicks() {
        ScrollBar bar = new ScrollBar(0, 0, 100, 30, 10);
        assertEquals(20, bar.maxScroll());
    }
}
