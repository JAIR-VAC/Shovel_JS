package com.kve.client.recipe;

import com.kve.client.gui.MiniItemIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Lectura de las recetas cargadas en el cliente, por id de receta o por item
 * de salida. La usa la pestana "Quitar": {@code event.remove({id})} necesita
 * el id REAL de la receta, que casi nunca coincide con el del item
 * (minecraft:birch_slab sí, pero minecraft:dried_kelp_from_smelting no).
 */
@OnlyIn(Dist.CLIENT)
public final class RecipeOutputLookup {

    private RecipeOutputLookup() {}

    private static RecipeManager manager() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : mc.level.getRecipeManager();
    }

    private static Recipe<?> byId(String recipeId) {
        RecipeManager rm = manager();
        ResourceLocation rl = recipeId == null ? null : ResourceLocation.tryParse(recipeId);
        return rm == null || rl == null ? null : rm.byKey(rl).map(RecipeHolder::value).orElse(null);
    }

    private static ItemStack resultOf(Recipe<?> r) {
        Minecraft mc = Minecraft.getInstance();
        if (r == null || mc.level == null) return ItemStack.EMPTY;
        try {
            ItemStack s = r.getResultItem(mc.level.registryAccess());
            return s == null ? ItemStack.EMPTY : s;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    public static Item outputOf(String recipeId) {
        ItemStack s = resultOf(byId(recipeId));
        if (!s.isEmpty()) {
            remember(recipeId, s.getItem());
            return s.getItem();
        }
        Item known = MiniItemIcon.item(memory().get(recipeId));
        if (known != null) return known;
        return guessFromId(recipeId);
    }

    static Item guessFromId(String recipeId) {
        Item direct = MiniItemIcon.item(recipeId);
        if (direct != null || recipeId == null) return direct;
        ResourceLocation rl = ResourceLocation.tryParse(recipeId);
        if (rl == null) return null;
        String path = rl.getPath();
        String last = path.substring(path.lastIndexOf('/') + 1);
        for (String candidate : new String[]{last, last.replaceAll("_from_.*$", ""),
                last.replaceAll("_(smelting|blasting|smoking|campfire_cooking|stonecutting|smithing|crafting|shaped|shapeless)$", "")}) {
            for (String ns : new String[]{rl.getNamespace(), "minecraft"}) {
                Item it = MiniItemIcon.item(ns + ":" + candidate);
                if (it != null) return it;
            }
        }
        return null;
    }

    private static final java.nio.file.Path MEMORY_FILE =
            com.kve.common.kubejs.KveScriptPaths.CONFIG_ROOT.resolve("shovel_js_recipe_outputs.json");
    private static java.util.Map<String, String> memory;

    private static java.util.Map<String, String> memory() {
        if (memory != null) return memory;
        memory = new java.util.TreeMap<>();
        try {
            if (java.nio.file.Files.exists(MEMORY_FILE)) {
                com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(
                        java.nio.file.Files.readString(MEMORY_FILE)).getAsJsonObject();
                for (var en : o.entrySet()) memory.put(en.getKey(), en.getValue().getAsString());
            }
        } catch (Exception ignored) {
        }
        return memory;
    }

    private static void remember(String recipeId, Item item) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        if (recipeId == null || key == null) return;
        String value = key.toString();
        if (value.equals(memory().put(recipeId, value))) return; // ya lo sabia: no tocar disco
        try {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            memory().forEach(o::addProperty);
            java.nio.file.Files.createDirectories(MEMORY_FILE.getParent());
            com.kve.common.util.SafeFiles.writeAtomic(MEMORY_FILE, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(o));
        } catch (Exception ignored) {
        }
    }

    /** Tipo de la receta ("minecraft:smelting"...), o "" si no esta cargada. */
    public static String typeOf(String recipeId) {
        Recipe<?> r = byId(recipeId);
        if (r == null) return "";
        ResourceLocation key = BuiltInRegistries.RECIPE_TYPE.getKey(r.getType());
        return key == null ? "" : key.toString();
    }

    /** Ids de todas las recetas cargadas cuya salida es {@code item}, ordenados. */
    public static List<String> idsProducing(Item item) {
        List<String> out = new ArrayList<>();
        RecipeManager rm = manager();
        if (rm == null || item == null) return out;
        for (RecipeHolder<?> rHolder : rm.getRecipes()) {
            Recipe<?> r = rHolder.value();
            if ("shovel_js".equals(rHolder.id().getNamespace())) continue;
            ItemStack s = resultOf(r);
            if (!s.isEmpty() && s.getItem() == item) out.add(rHolder.id().toString());
        }
        out.sort(String::compareTo);
        return out;
    }
}
