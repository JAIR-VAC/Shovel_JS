package com.kve.client.gui;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sesion {@link ListKeyNav} pasa de 2 pickers a las grillas de
 * Item/Block Attributes, donde el foco tambien puede estar en campos
 * numericos - el reparto de teclas con el texto es el contrato que no debe
 * romperse. Sin scrollbar (bar=null): Java puro, sin cliente.
 */
class ListKeyNavTest {

    private static final int COLS = 7, ROWS = 12, SIZE = 100;

    private static boolean press(ListKeyNav n, int key) {
        return n.keyPressed(key, SIZE, COLS, ROWS, null);
    }

    @Test
    void startsInactiveAndDownActivatesFirstCell() {
        ListKeyNav n = new ListKeyNav();
        assertFalse(n.active());
        assertTrue(press(n, GLFW.GLFW_KEY_DOWN));
        assertEquals(0, n.cursor());
    }

    @Test
    void leftRightHomeEndBelongToTheTextBoxWhileInactive() {
        ListKeyNav n = new ListKeyNav();
        assertFalse(press(n, GLFW.GLFW_KEY_LEFT));
        assertFalse(press(n, GLFW.GLFW_KEY_RIGHT));
        assertFalse(press(n, GLFW.GLFW_KEY_HOME));
        assertFalse(press(n, GLFW.GLFW_KEY_END));
        assertFalse(n.active());
    }

    @Test
    void movesByRowsAndColumnsOnceActive() {
        ListKeyNav n = new ListKeyNav();
        press(n, GLFW.GLFW_KEY_DOWN);
        press(n, GLFW.GLFW_KEY_DOWN);
        assertEquals(COLS, n.cursor());
        assertTrue(press(n, GLFW.GLFW_KEY_RIGHT));
        assertEquals(COLS + 1, n.cursor());
        assertTrue(press(n, GLFW.GLFW_KEY_UP));
        assertEquals(1, n.cursor());
    }

    @Test
    void upFromFirstRowReturnsToTheSearchBox() {
        ListKeyNav n = new ListKeyNav();
        press(n, GLFW.GLFW_KEY_DOWN);
        assertTrue(press(n, GLFW.GLFW_KEY_UP));
        assertFalse(n.active());
    }

    @Test
    void cursorNeverLeavesTheList() {
        ListKeyNav n = new ListKeyNav();
        press(n, GLFW.GLFW_KEY_DOWN);
        press(n, GLFW.GLFW_KEY_END);
        assertEquals(SIZE - 1, n.cursor());
        press(n, GLFW.GLFW_KEY_DOWN);
        assertEquals(SIZE - 1, n.cursor());
        n.clamp(10);
        assertEquals(9, n.cursor());
    }

    @Test
    void emptyListConsumesNothing() {
        ListKeyNav n = new ListKeyNav();
        assertFalse(n.keyPressed(GLFW.GLFW_KEY_DOWN, 0, COLS, ROWS, null));
        assertFalse(n.active());
    }

    @Test
    void enterIsConfirmKeyOnly() {
        assertTrue(ListKeyNav.isConfirm(GLFW.GLFW_KEY_ENTER));
        assertTrue(ListKeyNav.isConfirm(GLFW.GLFW_KEY_KP_ENTER));
        assertFalse(ListKeyNav.isConfirm(GLFW.GLFW_KEY_TAB));
    }

    @Test
    void resetDeactivates() {
        ListKeyNav n = new ListKeyNav();
        press(n, GLFW.GLFW_KEY_DOWN);
        n.reset();
        assertFalse(n.active());
        assertEquals(-1, n.cursor());
    }

    @Test
    void clickMovesCursorWithoutShowingCursorTooltip() {
        ListKeyNav n = new ListKeyNav();
        n.setCursor(10, SIZE, COLS, ROWS, null);
        assertTrue(n.active());
        assertFalse(n.showCursorTooltip());
    }

    @Test
    void arrowKeysShowCursorTooltipUntilNextClick() {
        ListKeyNav n = new ListKeyNav();
        press(n, GLFW.GLFW_KEY_DOWN);
        assertTrue(n.showCursorTooltip());
        n.setCursor(3, SIZE, COLS, ROWS, null);
        assertFalse(n.showCursorTooltip());
        press(n, GLFW.GLFW_KEY_RIGHT);
        assertTrue(n.showCursorTooltip());
        n.reset();
        assertFalse(n.showCursorTooltip());
    }
}
