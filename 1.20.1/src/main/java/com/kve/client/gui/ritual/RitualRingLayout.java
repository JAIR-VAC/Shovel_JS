package com.kve.client.gui.ritual;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-geometry helper for the "ritual_ring" editor view
 * positions N
 * "offering" slots evenly around a center point, plus hit-testing against
 * those positions and the center/output slots.
 * <p>
 * Deliberately has zero dependency on any mod's classes (Minecraft,
 * Forge, RecipeData, etc.) - it only knows about pixel rectangles - so it
 * can be unit-tested in plain JUnit and reused by any screen that wants a
 * ring layout, not just {@code MachineRecipeEditScreen}.
 */
public final class RitualRingLayout {
    private RitualRingLayout() {}

    /** Vanilla-ish slot size, matches {@code MachineRecipeEditScreen.SLOT}. */
    public static final int OFFERING_SLOT = 18;
    public static final int CENTER_SLOT = 18;
    /** Pixel distance from the center point to each offering slot's own center. */
    public static final int RING_RADIUS = 40;

    /** Clamps a descriptor's {@code ritualView.maxOfferings} into a sane renderable range. */
    public static int clampMaxOfferings(int requested) {
        return Math.max(1, Math.min(16, requested));
    }

    /**
     * One offering position: top-left (x, y) of its {@value #OFFERING_SLOT}px
     * square, in the same coordinate space as {@code centerX}/{@code centerY}.
     */
    public static final class Offering {
        public final int x, y;
        public Offering(int x, int y) { this.x = x; this.y = y; }
    }

    /**
     * Computes {@code n} offering positions evenly spaced in a circle
     * around (centerX, centerY), first slot straight up, going clockwise -
     * exactly the formula
     * {@code n} is clamped to at least 1 so callers never have to special-
     * case "zero offerings" themselves.
     */
    public static List<Offering> computeRingPositions(int centerX, int centerY, int n) {
        int count = Math.max(1, n);
        List<Offering> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = -Math.PI / 2.0 + (2.0 * Math.PI * i) / count;
            int x = (int) Math.round(centerX + RING_RADIUS * Math.cos(angle) - OFFERING_SLOT / 2.0);
            int y = (int) Math.round(centerY + RING_RADIUS * Math.sin(angle) - OFFERING_SLOT / 2.0);
            out.add(new Offering(x, y));
        }
        return out;
    }

    /**
     * Posiciones FIJAS declaradas por el descriptor en vez
     * del circulo uniforme. Goety, por ejemplo, pone el ingrediente 0 arriba, el 1
     * a la derecha, el 2 abajo, el 3 a la izquierda y recien despues las
     * diagonales - llenar un circulo en sentido horario no se parece en nada.
     * Los offsets de JEI estan pensados para slots de 16px y pueden quedar a 15px
     * entre si; se escalan lo justo para que dos slots de {@value #OFFERING_SLOT}px
     * no se pisen.
     */
    public static List<Offering> computeCustomPositions(int centerX, int centerY, List<int[]> offsets) {
        List<Offering> out = new ArrayList<>(offsets.size());
        double scale = customPositionScale(offsets);
        for (int[] o : offsets) {
            int x = (int) Math.round(centerX + o[0] * scale - OFFERING_SLOT / 2.0);
            int y = (int) Math.round(centerY + o[1] * scale - OFFERING_SLOT / 2.0);
            out.add(new Offering(x, y));
        }
        return out;
    }

    static double customPositionScale(List<int[]> offsets) {
        int minGap = Integer.MAX_VALUE;
        for (int i = 0; i < offsets.size(); i++) {
            for (int j = i + 1; j < offsets.size(); j++) {
                int gap = Math.max(Math.abs(offsets.get(i)[0] - offsets.get(j)[0]), Math.abs(offsets.get(i)[1] - offsets.get(j)[1]));
                if (gap > 0) minGap = Math.min(minGap, gap);
            }
        }
        if (minGap == Integer.MAX_VALUE) return 1.0;
        return Math.max(1.0, (OFFERING_SLOT + 2.0) / minGap);
    }

    /**
     * Posicion del anillo para la ofrenda {@code i} de {@code n} al abrir una receta
     * que no trae posiciones guardadas (receta de otro mod): repartidas de forma
     * uniforme, como las dibuja JEI (Botania, Ars Nouveau...), en vez de amontonadas
     * en las primeras posiciones en sentido horario.
     */
    public static int spreadSlot(int i, int n, int max) {
        if (n <= 0 || max <= 0) return 0;
        if (n >= max) return Math.min(i, max - 1);
        return (int) Math.round((double) i * max / n) % max;
    }

    /** True if (mx, my) is inside a {@code size}x{@code size} square at (x, y). */
    public static boolean hits(double mx, double my, int x, int y, int size) {
        return mx >= x && mx < x + size && my >= y && my < y + size;
    }

    /**
     * Hit-tests (mx, my) against a precomputed set of offering positions.
     * Returns the index of the offering under the cursor, or -1. Callers
     * pass exactly as many positions as they're currently rendering
     * (existing offerings + the trailing "add" slot, if any) - this class
     * doesn't know or care which index means "add a new one".
     */
    public static int hitTestRing(double mx, double my, List<Offering> positions) {
        for (int i = 0; i < positions.size(); i++) {
            Offering o = positions.get(i);
            if (hits(mx, my, o.x, o.y, OFFERING_SLOT)) return i;
        }
        return -1;
    }

    /** Hit-tests (mx, my) against the center slot at (centerX, centerY) (top-left, {@value #CENTER_SLOT}px square). */
    public static boolean hitTestCenter(double mx, double my, int centerX, int centerY) {
        return hits(mx, my, centerX, centerY, CENTER_SLOT);
    }
}
