package com.kve.client.gui.recipe;

import com.kve.common.Lang;
import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.recipe.RecipeData;
import com.kve.common.recipe.TagIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
final class RecipeIndex {

    /** Un ingrediente agrupado: lo que se muestra (con la cantidad), el tag si es tag, y cuantas otras opciones tiene. */
    static final class Ing {
        final ItemStack shown;
        final String tag;
        final int alternatives;
        Ing(ItemStack shown, String tag, int alternatives) { this.shown = shown; this.tag = tag; this.alternatives = alternatives; }
    }

    /** Una receta del grafo: del juego ({@link #recipe}) o un borrador del editor sin aplicar ({@link #draft}). */
    static final class Entry {
        final RecipeHolder<?> recipe;
        final RecipeData draft;
        final ResourceLocation id;
        final ItemStack result;
        final String typeLabel;
        final ItemStack station;
        List<Ing> inputs;

        private Entry(RecipeHolder<?> recipe, RecipeData draft, ResourceLocation id, ItemStack result, String typeLabel, ItemStack station) {
            this.recipe = recipe; this.draft = draft; this.id = id; this.result = result; this.typeLabel = typeLabel; this.station = station;
        }

        boolean pending() { return draft != null; }
    }

    private final RecipeManager manager;
    private final int managerSize;
    private final Object managerFirst;
    private final int draftSignature;
    private final Map<Item, List<Entry>> byOutput = new HashMap<>();
    private final Map<Item, List<Entry>> byInput = new HashMap<>();

    private static RecipeIndex cache;

    private RecipeIndex(RecipeManager m, int size, Object first, int draftSig) {
        manager = m; managerSize = size; managerFirst = first; draftSignature = draftSig;
    }

    private static long lastGameCheck;
    private static final long GAME_CHECK_MS = 1000;

    static RecipeIndex get() {
        Minecraft mc = Minecraft.getInstance();
        RecipeManager mgr = mc.getConnection() != null ? mc.getConnection().getRecipeManager()
                : mc.level != null ? mc.level.getRecipeManager() : null;
        int draftSig = draftSignature();
        long now = System.currentTimeMillis();
        if (cache != null && cache.manager == mgr && cache.draftSignature == draftSig && now - lastGameCheck < GAME_CHECK_MS) {
            return cache;
        }
        lastGameCheck = now;
        Collection<RecipeHolder<?>> recipes = mgr == null ? List.of() : mgr.getRecipes();
        Object first = recipes.isEmpty() ? null : recipes.iterator().next();
        if (cache != null && cache.manager == mgr && cache.managerSize == recipes.size()
                && cache.managerFirst == first && cache.draftSignature == draftSig) {
            return cache;
        }
        RecipeIndex idx = new RecipeIndex(mgr, recipes.size(), first, draftSig);
        idx.build(mc, recipes);
        cache = idx;
        return idx;
    }

    private static int draftSignature() {
        int h = 17;
        for (RecipeData d : KubeJSExporter.getAddedRecipes()) h = h * 31 + System.identityHashCode(d) + (d.enabled ? 1 : 0);
        for (String r : KubeJSExporter.getRemovedRecipes()) h = h * 31 + r.hashCode();
        return h;
    }

    private void build(Minecraft mc, Collection<RecipeHolder<?>> recipes) {
        RegistryAccess access = mc.getConnection() != null ? mc.getConnection().registryAccess()
                : mc.level != null ? mc.level.registryAccess() : RegistryAccess.EMPTY;
        Set<String> overridden = new HashSet<>(KubeJSExporter.getRemovedRecipes());
        List<Entry> drafts = new ArrayList<>();
        for (RecipeData d : KubeJSExporter.getAddedRecipes()) {
            if (d == null || d.recipeId == null || d.recipeId.isBlank()) continue;
            String gameId = "shovel_js:" + d.recipeId.replaceAll("[^a-zA-Z0-9_./]", "_");
            overridden.add(gameId);
            if (!d.enabled || d.output == null || d.output.isEmpty()) continue;
            ResourceLocation id = ResourceLocation.tryParse(gameId);
            if (id == null) continue;
            drafts.add(new Entry(null, d, id, d.output.copy(), draftTypeLabel(d), draftStation(d)));
        }
        for (RecipeHolder<?> r : recipes) {
            ResourceLocation id = r.id();
            if (overridden.contains(id.toString())) continue;
            ItemStack out = ItemStack.EMPTY;
            try { out = r.value().getResultItem(access); } catch (Throwable ignored) {}
            if (out == null) out = ItemStack.EMPTY;
            ItemStack station = ItemStack.EMPTY;
            try { station = r.value().getToastSymbol(); } catch (Throwable ignored) {}
            Entry e = new Entry(r, null, id, out, serializerLabel(r), station);
            if (!out.isEmpty()) byOutput.computeIfAbsent(out.getItem(), k -> new ArrayList<>()).add(e);
            try {
                Set<Item> seen = new HashSet<>();
                for (Ingredient ing : r.value().getIngredients()) {
                    for (ItemStack s : ing.getItems()) {
                        if (!s.isEmpty() && seen.add(s.getItem())) byInput.computeIfAbsent(s.getItem(), k -> new ArrayList<>()).add(e);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        for (Entry e : drafts) {
            byOutput.computeIfAbsent(e.result.getItem(), k -> new ArrayList<>()).add(0, e);
            Set<Item> seen = new HashSet<>();
            for (Ing ing : inputsOf(e)) if (seen.add(ing.shown.getItem())) byInput.computeIfAbsent(ing.shown.getItem(), k -> new ArrayList<>()).add(0, e);
        }
    }

    List<Entry> producing(Item item) { return byOutput.getOrDefault(item, List.of()); }

    List<Entry> using(Item item) { return byInput.getOrDefault(item, List.of()); }

    List<Ing> inputsOf(Entry e) {
        if (e.inputs != null) return e.inputs;
        Map<String, Ing> grouped = new LinkedHashMap<>();
        if (e.draft != null) {
            addData(grouped, e.draft);
        } else {
            List<Ingredient> ings;
            try { ings = e.recipe.value().getIngredients(); } catch (Throwable t) { ings = List.of(); }
            boolean any = false;
            for (Ingredient ing : ings) {
                if (ing == null || ing.isEmpty()) continue;
                ItemStack[] items = ing.getItems();
                if (items.length == 0) continue;
                any = true;
                String tag = tagOf(ing);
                ItemStack shown = items[0].copyWithCount(1);
                add(grouped, tag != null ? "#" + tag : key(shown), shown, tag, tag != null ? 0 : items.length - 1, 1);
            }
            if (!any) {
                try {
                    addData(grouped, com.kve.client.recipe.ExistingRecipeLoader.toRecipeData(e.id, e.recipe.value(), e.result));
                } catch (Throwable ignored) {
                }
            }
        }
        e.inputs = new ArrayList<>(grouped.values());
        return e.inputs;
    }

    private static void addData(Map<String, Ing> grouped, RecipeData d) {
        List<ItemStack> raw = new ArrayList<>();
        if (d.ingredients != null) raw.addAll(d.ingredients);
        if (d.shapelessIngredients != null) raw.addAll(d.shapelessIngredients);
        if (d.combinationCenter != null && !d.combinationCenter.isEmpty()) raw.add(d.combinationCenter);
        if (d.combinationPedestals != null) raw.addAll(d.combinationPedestals);
        if (d.machineInputs != null) {
            for (RecipeData.MachineSlot m : d.machineInputs) {
                if (m.type == RecipeData.MachineSlotType.ITEM) raw.add(m.item);
                else if (m.type == RecipeData.MachineSlotType.TAG) raw.add(TagIngredient.of(m.tagId, null).copyWithCount(Math.max(1, m.tagCount)));
            }
        }
        for (ItemStack s : raw) {
            if (s == null || s.isEmpty()) continue;
            String tag = TagIngredient.tagOf(s);
            ItemStack shown = new ItemStack(s.getItem());
            add(grouped, tag != null ? "#" + tag : key(shown), shown, tag, 0, Math.max(1, s.getCount()));
        }
    }

    private static void add(Map<String, Ing> grouped, String key, ItemStack shown, String tag, int alts, int count) {
        Ing prev = grouped.get(key);
        if (prev == null) grouped.put(key, new Ing(shown.copyWithCount(count), tag, alts));
        else prev.shown.grow(count);
    }

    private static String key(ItemStack s) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
        return id == null ? "?" : id.toString();
    }

    private static String tagOf(Ingredient ing) {
        try {
            var json = Ingredient.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, ing).result().orElse(null);
            if (json != null && json.isJsonObject() && json.getAsJsonObject().has("tag")) {
                return json.getAsJsonObject().get("tag").getAsString();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String serializerLabel(RecipeHolder<?> r) {
        ResourceLocation id = null;
        try { id = BuiltInRegistries.RECIPE_SERIALIZER.getKey(r.value().getSerializer()); } catch (Throwable ignored) {}
        if (id == null) return "?";
        return id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
    }

    private static String draftTypeLabel(RecipeData d) {
        if (d.customTypeId != null && !d.customTypeId.isBlank()) {
            return d.customTypeId.startsWith("minecraft:") ? d.customTypeId.substring("minecraft:".length()) : d.customTypeId;
        }
        return d.type.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static ItemStack draftStation(RecipeData d) {
        try {
            if (d.customTypeId != null && !d.customTypeId.isBlank()) {
                var desc = com.kve.common.recipetype.RecipeTypeDescriptorRegistry.find(d.customTypeId);
                if (desc.isPresent() && desc.get().hasStationItem()) {
                    ResourceLocation rl = ResourceLocation.tryParse(desc.get().stationItem);
                    if (rl != null) {
                        Item it = BuiltInRegistries.ITEM.get(rl);
                        if (it != Items.AIR) return new ItemStack(it);
                    }
                }
            }
        } catch (Throwable ignored) {}
        return new ItemStack(Items.CRAFTING_TABLE);
    }

    /** Copia editable de una receta del grafo y el id original a quitar al guardarla (null si ya es propia). */
    record Editable(RecipeData data, String removal) {}

    Editable editable(Entry e) {
        try {
            if (e.draft != null) return new Editable(copyOf(e.draft), null);
            RecipeData data = com.kve.client.recipe.ExistingRecipeLoader.toRecipeData(e.id, e.recipe.value(), e.result);
            return new Editable(data, e.id.getNamespace().equals("shovel_js") ? null : e.id.toString());
        } catch (Throwable t) {
            return null;
        }
    }

    /** Copia campo a campo: el borrador en cola no se toca hasta Guardar. */
    static RecipeData copyOf(RecipeData src) {
        RecipeData out = new RecipeData();
        for (java.lang.reflect.Field f : RecipeData.class.getFields()) {
            int m = f.getModifiers();
            if (java.lang.reflect.Modifier.isStatic(m) || java.lang.reflect.Modifier.isFinal(m)) continue;
            try { f.set(out, copyValue(f.get(src))); } catch (IllegalAccessException ignored) { }
        }
        return out;
    }

    private static Object copyValue(Object v) {
        if (v instanceof ItemStack s) return s.copy();
        if (v instanceof RecipeData.MachineSlot s) return s.copy();
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object o : l) out.add(copyValue(o));
            return out;
        }
        return v;
    }

    String openInEditor(Screen parent, Entry e) {
        RecipeData data;
        String removal;
        if (e.draft != null) {
            data = e.draft;
            removal = null;
        } else {
            try {
                data = com.kve.client.recipe.ExistingRecipeLoader.toRecipeData(e.id, e.recipe.value(), e.result);
            } catch (Throwable t) {
                return Lang.get("gui.kve.recipe.tree.edit_failed", e.id.toString());
            }
            removal = e.id.getNamespace().equals("shovel_js") ? null : e.id.toString();
        }
        com.kve.client.gui.studio.KveStudioShell.openRecipe(parent, data, removal);
        return null;
    }
}
