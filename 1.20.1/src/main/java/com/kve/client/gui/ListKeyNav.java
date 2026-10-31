package com.kve.client.gui;

import org.lwjgl.glfw.GLFW;

public final class ListKeyNav {

    private int cursor = -1;
    private boolean keyboardDriven = false;

    /** Indice seleccionado por teclado en la lista FILTRADA, o -1 si el cursor esta inactivo. */
    public int cursor() { return cursor; }

    public boolean active() { return cursor >= 0; }

    /** True si {@code index} es la fila marcada por teclado - para el resaltado en render(). */
    public boolean isCursor(int index) { return cursor >= 0 && cursor == index; }

    /** True si hay que dibujar el tooltip junto a la celda marcada: cursor activo Y movido por teclado (no por un click). */
    public boolean showCursorTooltip() { return cursor >= 0 && keyboardDriven; }

    /** Desactiva el cursor (query nueva, lista recargada, etc.). */
    public void reset() { cursor = -1; keyboardDriven = false; }

    /** Deja el cursor dentro de la lista actual (o inactivo si quedo afuera). */
    public void clamp(int size) {
        if (cursor >= size) cursor = size - 1;
        if (cursor < 0) cursor = -1;
    }

    /** ENTER (principal o del pad numerico). */
    public static boolean isConfirm(int key) {
        return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
    }

    /**
     * Mueve el cursor y arrastra el scroll para que siga visible.
     *
     * @param size        tamaño de la lista filtrada
     * @param cols        columnas de la grilla (1 para una lista vertical)
     * @param visibleRows filas visibles a la vez
     * @param bar         scrollbar de la screen (puede ser null)
     * @return true si la tecla se consumio (la screen debe devolver true)
     */
    public boolean keyPressed(int key, int size, int cols, int visibleRows, ScrollBar bar) {
        if (size <= 0) return false;
        int c = Math.max(1, cols);
        boolean consumed = keyPressedInner(key, size, c, visibleRows, bar);
        if (consumed && cursor >= 0) keyboardDriven = true;
        return consumed;
    }

    private boolean keyPressedInner(int key, int size, int c, int visibleRows, ScrollBar bar) {

        switch (key) {
            case GLFW.GLFW_KEY_DOWN -> { move(cursor < 0 ? firstVisible(bar, c) : cursor + c, size, c, visibleRows, bar); return true; }
            case GLFW.GLFW_KEY_UP -> {
                if (cursor < 0) { move(firstVisible(bar, c), size, c, visibleRows, bar); return true; }
                if (cursor - c < 0) { reset(); return true; }
                move(cursor - c, size, c, visibleRows, bar);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> { move(cursor < 0 ? firstVisible(bar, c) : cursor + c * Math.max(1, visibleRows), size, c, visibleRows, bar); return true; }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                if (cursor < 0) return false;
                move(cursor - c * Math.max(1, visibleRows), size, c, visibleRows, bar);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> { if (cursor < 0) return false; move(cursor + 1, size, c, visibleRows, bar); return true; }
            case GLFW.GLFW_KEY_LEFT  -> { if (cursor < 0) return false; move(cursor - 1, size, c, visibleRows, bar); return true; }
            case GLFW.GLFW_KEY_HOME  -> { if (cursor < 0) return false; move(0, size, c, visibleRows, bar); return true; }
            case GLFW.GLFW_KEY_END   -> { if (cursor < 0) return false; move(size - 1, size, c, visibleRows, bar); return true; }
            default -> { return false; }
        }
    }

    /** Pone el cursor en {@code index} (por ejemplo tras un click) y lo deja visible. */
    public void setCursor(int index, int size, int cols, int visibleRows, ScrollBar bar) {
        move(index, size, Math.max(1, cols), visibleRows, bar);
        keyboardDriven = false;
    }

    private int firstVisible(ScrollBar bar, int cols) {
        return bar == null ? 0 : bar.getScroll() * cols;
    }

    private void move(int target, int size, int cols, int visibleRows, ScrollBar bar) {
        cursor = Math.max(0, Math.min(size - 1, target));
        if (bar == null || visibleRows <= 0) return;
        int row = cursor / cols;
        int scroll = bar.getScroll();
        if (row < scroll) bar.setScroll(row);
        else if (row >= scroll + visibleRows) bar.setScroll(row - visibleRows + 1);
    }
}
