package com.kve.client.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.crafting.IShapedRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detector de conflictos entre recetas de mesa de crafteo: dos
 * Recetas estan en conflicto cuando la MISMA grilla de ingredientes sirve
 * para las dos y dan resultados distintos - en el juego solo una gana y la
 * otra queda inalcanzable, algo que no se nota hasta que alguien la busca.
 * <p>
 * En vez de comparar ingredientes a mano (tags, simetria, recetas de mods
 * con su propia logica de coincidencia...), se arma una grilla
 * representativa de cada receta (el primer item de cada ingrediente) y se
 * le pregunta a las OTRAS recetas con su propio {@code matches()} - la
 * misma comprobacion que hace la mesa de crafteo real. Para que no sea
 * O(n²) sobre miles de recetas, solo se compara contra recetas con la misma
 * cantidad de ingredientes y cuyos items posibles incluyen todos los de la
 * grilla.
 * <p>
 * Limitacion conocida: se prueba una grilla por receta (la de los primeros
 * items de cada ingrediente), asi que dos recetas que solo chocan con una
 * variante concreta de un tag pueden no detectarse.
 */
@OnlyIn(Dist.CLIENT)
public final class RecipeConflictFinder {

    private RecipeConflictFinder() {}

    /** Un grupo de recetas que aceptan la misma grilla y no dan todas lo mismo. */
    public record Group(List<CraftingRecipe> recipes, List<ItemStack> sampleGrid) {}

    private record Info(CraftingRecipe recipe, List<ItemStack> grid, int filled, Set<Item> possible) {}

    /** Todos los grupos en conflicto de la mesa de crafteo. */
    public static List<Group> findConflicts(RecipeManager mgr, Level level, RegistryAccess ra) {
        List<Info> infos = new ArrayList<>();
        for (CraftingRecipe r : mgr.getAllRecipesFor(RecipeType.CRAFTING)) {
            Info info = describe(r);
            if (info != null) infos.add(info);
        }
        Map<Integer, List<Info>> byFilled = new HashMap<>();
        for (Info i : infos) byFilled.computeIfAbsent(i.filled(), k -> new ArrayList<>()).add(i);

        Map<CraftingRecipe, CraftingRecipe> parent = new HashMap<>();
        Map<CraftingRecipe, List<ItemStack>> gridOf = new HashMap<>();
        GridContainer container = new GridContainer();
        for (Info a : infos) {
            container.set(a.grid());
            for (Info b : byFilled.getOrDefault(a.filled(), List.of())) {
                if (b == a || !b.possible().containsAll(itemsOf(a.grid()))) continue;
                boolean matches;
                try {
                    matches = b.recipe().matches(container, level);
                } catch (Throwable t) {
                    matches = false;
                }
                if (matches) {
                    union(parent, a.recipe(), b.recipe());
                    gridOf.putIfAbsent(a.recipe(), a.grid());
                    gridOf.putIfAbsent(b.recipe(), a.grid());
                }
            }
        }

        Map<CraftingRecipe, List<CraftingRecipe>> groups = new LinkedHashMap<>();
        for (CraftingRecipe r : parent.keySet()) groups.computeIfAbsent(find(parent, r), k -> new ArrayList<>()).add(r);
        List<Group> out = new ArrayList<>();
        for (Map.Entry<CraftingRecipe, List<CraftingRecipe>> e : groups.entrySet()) {
            List<CraftingRecipe> members = e.getValue();
            if (members.size() < 2 || allSameResult(members, ra)) continue;
            members.sort((x, y) -> x.getId().compareTo(y.getId()));
            out.add(new Group(members, gridOf.getOrDefault(members.get(0), List.of())));
        }
        out.sort((x, y) -> x.recipes().get(0).getId().compareTo(y.recipes().get(0).getId()));
        return out;
    }

    /**
     * Recetas existentes que tambien aceptarian {@code grid9} (3x3, fila por
     * fila; vacios = {@link ItemStack#EMPTY}) y dan otro resultado que
     * {@code result}. Se ignora {@code excludeId} (la propia receta que se
     * esta editando, si ya existia).
     */
    public static List<CraftingRecipe> conflictsWithGrid(RecipeManager mgr, Level level, RegistryAccess ra,
                                                         List<ItemStack> grid9, ItemStack result, ResourceLocation excludeId) {
        List<CraftingRecipe> out = new ArrayList<>();
        if (grid9.stream().allMatch(ItemStack::isEmpty)) return out;
        GridContainer container = new GridContainer();
        container.set(grid9);
        for (CraftingRecipe r : mgr.getAllRecipesFor(RecipeType.CRAFTING)) {
            if (r.isSpecial() || r.getId().equals(excludeId)) continue;
            try {
                if (!r.matches(container, level)) continue;
                ItemStack other = r.getResultItem(ra);
                if (!sameResult(other, result)) out.add(r);
            } catch (Throwable ignored) {}
        }
        return out;
    }

    private static Info describe(CraftingRecipe r) {
        if (r.isSpecial()) return null;
        NonNullList<Ingredient> ings;
        try { ings = r.getIngredients(); } catch (Throwable t) { return null; }
        if (ings == null || ings.isEmpty()) return null;
        int w, h;
        if (r instanceof IShapedRecipe<?> shaped) {
            w = shaped.getRecipeWidth();
            h = shaped.getRecipeHeight();
            if (w > 3 || h > 3 || w * h != ings.size()) return null;
        } else {
            if (ings.size() > 9) return null;
            w = 3;
            h = (ings.size() + 2) / 3;
        }
        List<ItemStack> grid = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) grid.add(ItemStack.EMPTY);
        Set<Item> possible = new HashSet<>();
        int filled = 0;
        for (int i = 0; i < ings.size(); i++) {
            Ingredient ing = ings.get(i);
            if (ing.isEmpty()) continue;
            ItemStack[] options = ing.getItems();
            if (options.length == 0) return null; // tag vacio: la receta no se puede hacer
            for (ItemStack s : options) possible.add(s.getItem());
            int col = i % w, row = i / w;
            grid.set(row * 3 + col, options[0].copyWithCount(1));
            filled++;
        }
        return filled == 0 ? null : new Info(r, grid, filled, possible);
    }

    private static Set<Item> itemsOf(List<ItemStack> grid) {
        Set<Item> s = new HashSet<>();
        for (ItemStack st : grid) if (!st.isEmpty()) s.add(st.getItem());
        return s;
    }

    private static boolean allSameResult(List<CraftingRecipe> rs, RegistryAccess ra) {
        ItemStack first = safeResult(rs.get(0), ra);
        for (int i = 1; i < rs.size(); i++) if (!sameResult(first, safeResult(rs.get(i), ra))) return false;
        return true;
    }

    private static ItemStack safeResult(CraftingRecipe r, RegistryAccess ra) {
        try { return r.getResultItem(ra); } catch (Throwable t) { return ItemStack.EMPTY; }
    }

    private static boolean sameResult(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameTags(a, b) && a.getCount() == b.getCount();
    }

    private static CraftingRecipe find(Map<CraftingRecipe, CraftingRecipe> parent, CraftingRecipe r) {
        parent.putIfAbsent(r, r);
        CraftingRecipe p = parent.get(r);
        if (p != r) { p = find(parent, p); parent.put(r, p); }
        return p;
    }

    private static void union(Map<CraftingRecipe, CraftingRecipe> parent, CraftingRecipe a, CraftingRecipe b) {
        CraftingRecipe ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) parent.put(ra, rb);
    }

    /** Grilla 3x3 de solo lectura para {@code Recipe#matches} - nunca pertenece a un menu real. */
    private static final class GridContainer implements CraftingContainer {
        private final NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);

        void set(List<ItemStack> grid) {
            for (int i = 0; i < 9; i++) items.set(i, i < grid.size() ? grid.get(i) : ItemStack.EMPTY);
        }

        @Override public int getWidth() { return 3; }
        @Override public int getHeight() { return 3; }
        @Override public List<ItemStack> getItems() { return List.copyOf(items); }
        @Override public int getContainerSize() { return 9; }
        @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
        @Override public ItemStack getItem(int slot) { return slot >= 0 && slot < 9 ? items.get(slot) : ItemStack.EMPTY; }
        @Override public ItemStack removeItem(int slot, int amount) { return ItemStack.EMPTY; }
        @Override public ItemStack removeItemNoUpdate(int slot) { return ItemStack.EMPTY; }
        @Override public void setItem(int slot, ItemStack stack) { if (slot >= 0 && slot < 9) items.set(slot, stack); }
        @Override public void setChanged() {}
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clearContent() { items.replaceAll(s -> ItemStack.EMPTY); }
        @Override public void fillStackedContents(StackedContents contents) {
            for (ItemStack s : items) contents.accountSimpleStack(s);
        }
    }
}
