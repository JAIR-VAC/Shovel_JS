package com.kve.client.recipe;

import com.kve.common.recipe.TagIngredient;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Ultimos items elegidos en el editor de recetas, para la
 * paleta de "recientes": se ponen en la grilla con un clic sin volver a
 * abrir el selector. Vive mientras dure la sesion del juego.
 */
public final class RecentItems {
    private RecentItems() {}

    public static final int MAX = 9;
    private static final List<ItemStack> ITEMS = new ArrayList<>();

    /** Lo pone primero (sin repetir el mismo item/tag); cantidad 1. */
    public static synchronized void add(ItemStack s) {
        if (s == null || s.isEmpty()) return;
        ItemStack one = s.copyWithCount(1);
        ITEMS.removeIf(x -> same(x, one));
        ITEMS.add(0, one);
        while (ITEMS.size() > MAX) ITEMS.remove(ITEMS.size() - 1);
    }

    public static synchronized void remove(int index) {
        if (index >= 0 && index < ITEMS.size()) ITEMS.remove(index);
    }

    public static synchronized List<ItemStack> get() {
        return List.copyOf(ITEMS);
    }

    /** Mismo item y NBT, o mismo tag si es un ingrediente-tag. */
    public static boolean same(ItemStack a, ItemStack b) {
        String ta = TagIngredient.tagOf(a), tb = TagIngredient.tagOf(b);
        if (ta != null || tb != null) return ta != null && ta.equals(tb);
        return ItemStack.isSameItemSameTags(a, b);
    }
}
