package com.kve.client.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

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
    public record Group(List<RecipeHolder<CraftingRecipe>> recipes, List<ItemStack> sampleGrid) {}

    private record Info(RecipeHolder<CraftingRecipe> recipe, List<ItemStack> grid, int filled, Set<Item> possible) {}

    /** Todos los grupos en conflicto de la mesa de crafteo. */
    public static List<Group> findConflicts(RecipeManager mgr, Level level, RegistryAccess ra) {
        List<Info> infos = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> r : mgr.getAllRecipesFor(RecipeType.CRAFTING)) {
            Info info = describe(r);
            if (info != null) infos.add(info);
        }
        Map<Integer, List<Info>> byFilled = new HashMap<>();
        for (Info i : infos) byFilled.computeIfAbsent(i.filled(), k -> new ArrayList<>()).add(i);

        Map<RecipeHolder<CraftingRecipe>, RecipeHolder<CraftingRecipe>> parent = new HashMap<>();
        Map<RecipeHolder<CraftingRecipe>, List<ItemStack>> gridOf = new HashMap<>();
        GridContainer container = new GridContainer();
        for (Info a : infos) {
            container.set(a.grid());
            for (Info b : byFilled.getOrDefault(a.filled(), List.of())) {
                if (b == a || !b.possible().containsAll(itemsOf(a.grid()))) continue;
                boolean matches;
                try {
                    matches = b.recipe().value().matches(container.input(), level);
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

        Map<RecipeHolder<CraftingRecipe>, List<RecipeHolder<CraftingRecipe>>> groups = new LinkedHashMap<>();
        for (RecipeHolder<CraftingRecipe> r : parent.keySet()) groups.computeIfAbsent(find(parent, r), k -> new ArrayList<>()).add(r);
        List<Group> out = new ArrayList<>();
        for (Map.Entry<RecipeHolder<CraftingRecipe>, List<RecipeHolder<CraftingRecipe>>> e : groups.entrySet()) {
            List<RecipeHolder<CraftingRecipe>> members = e.getValue();
            if (members.size() < 2 || allSameResult(members, ra)) continue;
            members.sort((x, y) -> x.id().compareTo(y.id()));
            out.add(new Group(members, gridOf.getOrDefault(members.get(0), List.of())));
        }
        out.sort((x, y) -> x.recipes().get(0).id().compareTo(y.recipes().get(0).id()));
        return out;
    }

    /**
     * Recetas existentes que tambien aceptarian {@code grid9} (3x3, fila por
     * fila; vacios = {@link ItemStack#EMPTY}) y dan otro resultado que
     * {@code result}. Se ignora {@code excludeId} (la propia receta que se
     * esta editando, si ya existia).
     */
    public static List<RecipeHolder<CraftingRecipe>> conflictsWithGrid(RecipeManager mgr, Level level, RegistryAccess ra,
                                                         List<ItemStack> grid9, ItemStack result, ResourceLocation excludeId) {
        List<RecipeHolder<CraftingRecipe>> out = new ArrayList<>();
        if (grid9.stream().allMatch(ItemStack::isEmpty)) return out;
        GridContainer container = new GridContainer();
        container.set(grid9);
        for (RecipeHolder<CraftingRecipe> r : mgr.getAllRecipesFor(RecipeType.CRAFTING)) {
            if (r.value().isSpecial() || r.id().equals(excludeId)) continue;
            try {
                if (!r.value().matches(container.input(), level)) continue;
                ItemStack other = r.value().getResultItem(ra);
                if (!sameResult(other, result)) out.add(r);
            } catch (Throwable ignored) {}
        }
        return out;
    }

    private static Info describe(RecipeHolder<CraftingRecipe> holder) {
        CraftingRecipe r = holder.value();
        if (r.isSpecial()) return null;
        NonNullList<Ingredient> ings;
        try { ings = r.getIngredients(); } catch (Throwable t) { return null; }
        if (ings == null || ings.isEmpty()) return null;
        int w, h;
        if (r instanceof ShapedRecipe shaped) {
            w = shaped.getWidth();
            h = shaped.getHeight();
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
        return filled == 0 ? null : new Info(holder, grid, filled, possible);
    }

    private static Set<Item> itemsOf(List<ItemStack> grid) {
        Set<Item> s = new HashSet<>();
        for (ItemStack st : grid) if (!st.isEmpty()) s.add(st.getItem());
        return s;
    }

    private static boolean allSameResult(List<RecipeHolder<CraftingRecipe>> rs, RegistryAccess ra) {
        ItemStack first = safeResult(rs.get(0), ra);
        for (int i = 1; i < rs.size(); i++) if (!sameResult(first, safeResult(rs.get(i), ra))) return false;
        return true;
    }

    private static ItemStack safeResult(RecipeHolder<CraftingRecipe> r, RegistryAccess ra) {
        try { return r.value().getResultItem(ra); } catch (Throwable t) { return ItemStack.EMPTY; }
    }

    private static boolean sameResult(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameComponents(a, b) && a.getCount() == b.getCount();
    }

    private static RecipeHolder<CraftingRecipe> find(Map<RecipeHolder<CraftingRecipe>, RecipeHolder<CraftingRecipe>> parent, RecipeHolder<CraftingRecipe> r) {
        parent.putIfAbsent(r, r);
        RecipeHolder<CraftingRecipe> p = parent.get(r);
        if (p != r) { p = find(parent, p); parent.put(r, p); }
        return p;
    }

    private static void union(Map<RecipeHolder<CraftingRecipe>, RecipeHolder<CraftingRecipe>> parent, RecipeHolder<CraftingRecipe> a, RecipeHolder<CraftingRecipe> b) {
        RecipeHolder<CraftingRecipe> ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) parent.put(ra, rb);
    }

    private static final class GridContainer {
        private final List<ItemStack> items = new ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        private CraftingInput input = CraftingInput.EMPTY;

        void set(List<ItemStack> grid) {
            for (int i = 0; i < 9; i++) items.set(i, i < grid.size() ? grid.get(i) : ItemStack.EMPTY);
            input = CraftingInput.of(3, 3, items);
        }

        CraftingInput input() { return input; }
    }
}
