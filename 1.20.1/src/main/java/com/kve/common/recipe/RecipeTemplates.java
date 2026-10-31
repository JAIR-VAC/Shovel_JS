package com.kve.common.recipe;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Patrones tipicos de crafteo para no armarlos casillero por casillero
 * bloque de almacenamiento, herramientas, armadura,
 * construccion. En el patron, 'M' = material principal, 'S' = palo (o el
 * segundo material que elija el jugador), ' ' = vacio. Mismos patrones que
 * las recetas vanilla equivalentes.
 */
public final class RecipeTemplates {
    private RecipeTemplates() {}

    /** @param outputCount cantidad que da la receta vanilla equivalente (sugerencia para la salida) */
    public record Template(String id, String[] rows, int outputCount, boolean usesSecond) {
        public int width() { return rows[0].length(); }
        public int height() { return rows.length; }
    }

    public static final List<Template> ALL = List.of(
            t("storage_3x3", 1, "MMM", "MMM", "MMM"),
            t("storage_2x2", 1, "MM", "MM"),
            t("ring", 1, "MMM", "M M", "MMM"),
            t("pickaxe", 1, "MMM", " S ", " S "),
            t("axe", 1, "MM", "MS", " S"),
            t("shovel", 1, "M", "S", "S"),
            t("sword", 1, "M", "M", "S"),
            t("hoe", 1, "MM", " S", " S"),
            t("helmet", 1, "MMM", "M M"),
            t("chestplate", 1, "M M", "MMM", "MMM"),
            t("leggings", 1, "MMM", "M M", "M M"),
            t("boots", 1, "M M", "M M"),
            t("stairs", 4, "M  ", "MM ", "MMM"),
            t("slab", 6, "MMM"),
            t("wall", 6, "MMM", "MMM"),
            t("door", 3, "MM", "MM", "MM"),
            t("trapdoor", 2, "MMM", "MMM"),
            t("fence", 3, "MSM", "MSM"),
            t("pressure_plate", 1, "MM"),
            t("center_with_ring", 1, "MMM", "MSM", "MMM"));

    private static Template t(String id, int count, String... rows) {
        boolean second = String.join("", rows).indexOf('S') >= 0;
        return new Template(id, rows, count, second);
    }

    public static Template byId(String id) {
        return ALL.stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
    }

    /**
     * La grilla del patron dentro de una de {@code gridW} x {@code gridH}
     * (esquina superior izquierda); null si no entra.
     */
    public static GridOps.Grid apply(Template t, ItemStack material, ItemStack second, int gridW, int gridH) {
        if (t.width() > gridW || t.height() > gridH) return null;
        GridOps.Grid g = GridOps.Grid.empty(gridW, gridH);
        for (int r = 0; r < t.height(); r++) {
            for (int c = 0; c < t.width(); c++) {
                char ch = t.rows()[r].charAt(c);
                ItemStack s = ch == 'M' ? material : ch == 'S' ? second : ItemStack.EMPTY;
                g.slots()[r * gridW + c] = s == null || s.isEmpty() ? ItemStack.EMPTY : s.copyWithCount(1);
            }
        }
        return g;
    }
}
