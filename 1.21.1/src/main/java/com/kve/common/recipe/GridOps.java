package com.kve.common.recipe;

import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/**
 * Transformaciones de la grilla del editor de recetas: espejar,
 * girar, desplazar, recortar y rellenar. Todas devuelven una grilla NUEVA
 * (la de entrada no se toca, asi el deshacer guarda la anterior tal cual) y
 * nunca pierden items en silencio: si una operacion tiraria algo fuera de la
 * grilla, devuelve null.
 */
public final class GridOps {
    private GridOps() {}

    /** Grilla de {@code w} x {@code h}, fila por fila. */
    public record Grid(ItemStack[] slots, int w, int h) {
        public ItemStack at(int col, int row) { return slots[row * w + col]; }

        public static Grid of(ItemStack[] slots, int w, int h) {
            ItemStack[] copy = new ItemStack[w * h];
            for (int i = 0; i < copy.length; i++) copy[i] = i < slots.length && slots[i] != null ? slots[i] : ItemStack.EMPTY;
            return new Grid(copy, w, h);
        }

        static Grid empty(int w, int h) {
            ItemStack[] s = new ItemStack[w * h];
            Arrays.fill(s, ItemStack.EMPTY);
            return new Grid(s, w, h);
        }

        public boolean isEmpty() {
            for (ItemStack s : slots) if (!s.isEmpty()) return false;
            return true;
        }
    }

    public static Grid mirrorHorizontal(Grid g) {
        Grid out = Grid.empty(g.w, g.h);
        for (int r = 0; r < g.h; r++) for (int c = 0; c < g.w; c++) out.slots[r * g.w + (g.w - 1 - c)] = g.at(c, r);
        return out;
    }

    public static Grid mirrorVertical(Grid g) {
        Grid out = Grid.empty(g.w, g.h);
        for (int r = 0; r < g.h; r++) for (int c = 0; c < g.w; c++) out.slots[(g.h - 1 - r) * g.w + c] = g.at(c, r);
        return out;
    }

    /** Gira 90° en sentido horario. Una grilla no cuadrada cambia de forma (w x h -> h x w). */
    public static Grid rotateClockwise(Grid g) {
        Grid out = Grid.empty(g.h, g.w);
        for (int r = 0; r < g.h; r++) for (int c = 0; c < g.w; c++) out.slots[c * g.h + (g.h - 1 - r)] = g.at(c, r);
        return out;
    }

    public static Grid shift(Grid g, int dx, int dy) {
        Grid out = Grid.empty(g.w, g.h);
        for (int r = 0; r < g.h; r++) for (int c = 0; c < g.w; c++) {
            ItemStack s = g.at(c, r);
            if (s.isEmpty()) continue;
            int nc = c + dx, nr = r + dy;
            if (nc < 0 || nr < 0 || nc >= g.w || nr >= g.h) return null;
            out.slots[nr * g.w + nc] = s;
        }
        return out;
    }

    /** {minCol, minRow, maxCol, maxRow} de lo ocupado, o null si esta vacia. */
    public static int[] bounds(Grid g) {
        int minC = Integer.MAX_VALUE, minR = Integer.MAX_VALUE, maxC = -1, maxR = -1;
        for (int r = 0; r < g.h; r++) for (int c = 0; c < g.w; c++) {
            if (g.at(c, r).isEmpty()) continue;
            minC = Math.min(minC, c); minR = Math.min(minR, r);
            maxC = Math.max(maxC, c); maxR = Math.max(maxR, r);
        }
        return maxC < 0 ? null : new int[]{minC, minR, maxC, maxR};
    }

    /**
     * Lleva el contenido a la esquina superior izquierda (quita filas/columnas
     * vacias de arriba y de la izquierda) sin cambiar el tamaño de la grilla:
     * el patron exportado queda justo, sin espacios que obliguen a poner los
     * items en una posicion exacta de la mesa.
     */
    public static Grid trimToCorner(Grid g) {
        int[] b = bounds(g);
        if (b == null) return g;
        return shift(g, -b[0], -b[1]);
    }

    /** Pone {@code stack} en cada casillero vacio. */
    public static Grid fillEmpty(Grid g, ItemStack stack) {
        Grid out = Grid.of(g.slots, g.w, g.h);
        for (int i = 0; i < out.slots.length; i++) if (out.slots[i].isEmpty()) out.slots[i] = stack.copyWithCount(1);
        return out;
    }

    /** Pone {@code stack} en el borde (anillo exterior) y deja el centro como estaba. */
    public static Grid fillBorder(Grid g, ItemStack stack) {
        Grid out = Grid.of(g.slots, g.w, g.h);
        for (int r = 0; r < g.h; r++) for (int c = 0; c < g.w; c++) {
            if (r == 0 || c == 0 || r == g.h - 1 || c == g.w - 1) out.slots[r * g.w + c] = stack.copyWithCount(1);
        }
        return out;
    }
}
