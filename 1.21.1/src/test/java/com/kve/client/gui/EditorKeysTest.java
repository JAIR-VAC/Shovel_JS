package com.kve.client.gui;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sesion {@link EditorKeys} es la cola comun de keyPressed() de
 * los editores. Si {@code swallow()} vuelve a devolver siempre true, los
 * atajos del shell (Ctrl+Enter, Ctrl+Tab...) vuelven a quedar muertos en las
 * tabs de edicion; si {@code isFocusTab()} acepta Ctrl+Tab, el cambio de tab
 * del shell dejaria de funcionar. Logica pura (sin tipos de Minecraft).
 */
class EditorKeysTest {

    private static final int CTRL  = GLFW.GLFW_MOD_CONTROL;
    private static final int SHIFT = GLFW.GLFW_MOD_SHIFT;

    @Test
    void plainTabAndShiftTabAreFocusNavigation() {
        assertTrue(EditorKeys.isFocusTab(GLFW.GLFW_KEY_TAB, 0));
        assertTrue(EditorKeys.isFocusTab(GLFW.GLFW_KEY_TAB, SHIFT));
    }

    @Test
    void ctrlTabBelongsToTheShell() {
        assertFalse(EditorKeys.isFocusTab(GLFW.GLFW_KEY_TAB, CTRL));
        assertFalse(EditorKeys.isFocusTab(GLFW.GLFW_KEY_TAB, CTRL | SHIFT));
    }

    @Test
    void otherKeysAreNotFocusTab() {
        assertFalse(EditorKeys.isFocusTab(GLFW.GLFW_KEY_ENTER, 0));
        assertFalse(EditorKeys.isFocusTab(GLFW.GLFW_KEY_S, 0));
    }

    @Test
    void saveNeedsCtrlAndS() {
        assertTrue(EditorKeys.isSave(GLFW.GLFW_KEY_S, CTRL));
        assertTrue(EditorKeys.isSave(GLFW.GLFW_KEY_S, CTRL | SHIFT));
        assertFalse(EditorKeys.isSave(GLFW.GLFW_KEY_S, 0));
        assertFalse(EditorKeys.isSave(GLFW.GLFW_KEY_S, SHIFT));
        assertFalse(EditorKeys.isSave(GLFW.GLFW_KEY_D, CTRL));
    }

    @Test
    void ctrlChordsPassThroughPlainKeysAreSwallowed() {
        assertFalse(EditorKeys.swallow(CTRL));
        assertFalse(EditorKeys.swallow(CTRL | SHIFT));
        assertTrue(EditorKeys.swallow(0));
        assertTrue(EditorKeys.swallow(SHIFT));
    }
}
