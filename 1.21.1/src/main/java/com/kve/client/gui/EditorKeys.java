package com.kve.client.gui;

import org.lwjgl.glfw.GLFW;

public final class EditorKeys {

    private EditorKeys() {}

    private static boolean ctrl(int mods) {
        return (mods & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    /** Tab o Shift+Tab SIN Ctrl (Ctrl+Tab es del shell: cambiar de tab). */
    public static boolean isFocusTab(int key, int mods) {
        return key == GLFW.GLFW_KEY_TAB && !ctrl(mods);
    }

    /** Ctrl+S (Guardar) - mismo atajo que ya tienen CraftEditor/TagEditor/Files Editor. */
    public static boolean isSave(int key, int mods) {
        return key == GLFW.GLFW_KEY_S && ctrl(mods);
    }

    /**
     * Valor de retorno para el final de {@code keyPressed()}: {@code true}
     * (tecla consumida) salvo que lleve Ctrl, que se deja pasar al shell.
     */
    public static boolean swallow(int mods) {
        return !ctrl(mods);
    }
}
