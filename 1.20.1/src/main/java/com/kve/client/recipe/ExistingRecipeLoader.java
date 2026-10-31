package com.kve.client.recipe;

import com.kve.common.recipe.RecipeData;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class ExistingRecipeLoader {
    private ExistingRecipeLoader() {}

    private static List<String> repairing = null;

    public static RecipeData toRecipeData(ResourceLocation id, Recipe<?> recipe, ItemStack output) {
        repairing = new ArrayList<>();
        try {
            RecipeData d = toRecipeDataInner(id, recipe, output);
            d.repairedTags = repairing;
            return d;
        } finally {
            repairing = null;
        }
    }

    private static RecipeData toRecipeDataInner(ResourceLocation id, Recipe<?> recipe, ItemStack output) {
        RecipeData d = new RecipeData();
        d.output = output.copy();
        d.recipeId = id.getPath().replace('/', '_');

        ResourceLocation serializerId = net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER
                .getKey(recipe.getSerializer());
        String serializer = serializerId != null ? serializerId.toString() : "";
        if (recipe instanceof ShapedRecipe shaped
                && com.kve.common.kubejs.KubeJSExporter.isPositionalCustomType(serializer)) {
            d.type = RecipeData.RecipeType.CUSTOM_JSON;
            d.customTypeId = serializer;
            d.gridWidth = shaped.getWidth();
            d.gridHeight = shaped.getHeight();
            d.ingredients = new ArrayList<>();
            for (Ingredient ing : shaped.getIngredients()) d.ingredients.add(firstMatch(ing));
            while (d.ingredients.size() < d.gridWidth * d.gridHeight) d.ingredients.add(ItemStack.EMPTY);
            var desc = com.kve.common.recipetype.RecipeTypeDescriptorRegistry.find(serializer);
            if (desc.isPresent() && !desc.get().extraFields.isEmpty()) tryFillFromRawJson(id, d);
        } else if (recipe instanceof ShapedRecipe shaped) {
            d.type = RecipeData.RecipeType.VANILLA_SHAPED;
            d.gridWidth  = shaped.getWidth();
            d.gridHeight = shaped.getHeight();
            d.ingredients = new ArrayList<>();
            NonNullList<Ingredient> ings = shaped.getIngredients();
            for (Ingredient ing : ings) d.ingredients.add(firstMatch(ing));
            while (d.ingredients.size() < d.gridWidth * d.gridHeight) d.ingredients.add(ItemStack.EMPTY);
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            d.type = RecipeData.RecipeType.VANILLA_SHAPELESS;
            d.shapelessIngredients = new ArrayList<>();
            for (Ingredient ing : shapeless.getIngredients()) {
                ItemStack s = firstMatch(ing);
                if (!s.isEmpty()) d.shapelessIngredients.add(s);
            }
        } else {
            d.type = RecipeData.RecipeType.CUSTOM_JSON;
            // Same registry mismatch as RecipeTypePickerScreen: getType()
            // is almost always one of vanilla's ~7 RecipeType buckets, so looking it up in RECIPE_TYPE either
            // resolved to "minecraft:crafting" or, if the mod's Recipe
            // subclass returns a RecipeType instance that was never actually
            // registered, silently to null - leaving customTypeId blank,
            // which is why the type picker showed "(elige un tipo de
            // receta)" and the station icon fell back to a plain crafting
            // table instead of resolving the mod's real station. The
            // RecipeSerializer, by contrast, MUST be registered (the
            // RecipeManager couldn't have loaded this recipe otherwise), and
            // its id is what a recipe JSON's "type" field - and this mod's
            // own customTypeId - actually mean.
            ResourceLocation typeId = net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER
                    .getKey(recipe.getSerializer());
            d.customTypeId = typeId != null ? typeId.toString() : "";
            boolean isSmithing = isSmithingCustomTypeId(d.customTypeId);
            if (isSmithing) {
                d.gridWidth = 3; d.gridHeight = 1;
                d.ingredients = new ArrayList<>();
                for (int i = 0; i < 3; i++) d.ingredients.add(ItemStack.EMPTY);
            } else {
                d.shapelessIngredients = new ArrayList<>();
                try {
                    for (Ingredient ing : recipe.getIngredients()) {
                        ItemStack s = firstMatch(ing);
                        if (!s.isEmpty()) d.shapelessIngredients.add(s);
                    }
                } catch (Exception ignored) {}
            }

            boolean needsRawJson = isSmithing
                    ? d.ingredients.stream().allMatch(ItemStack::isEmpty)
                    : d.shapelessIngredients.isEmpty();
            var descriptorForExtra = com.kve.common.recipetype.RecipeTypeDescriptorRegistry.find(d.customTypeId);
            boolean needsExtraFields = descriptorForExtra.isPresent() && !descriptorForExtra.get().extraFields.isEmpty();
            // Los fluidos y quimicos nunca salen de getIngredients(): con slotLayout siempre hay que leer el JSON
            boolean hasLayout = descriptorForExtra.isPresent() && descriptorForExtra.get().hasSlotLayout();
            if (needsRawJson || needsExtraFields || hasLayout) {
                tryFillFromRawJson(id, d);
            }
        }
        return d;
    }

    private static ItemStack firstMatch(Ingredient ing) {
        if (ing == null || ing.isEmpty()) return ItemStack.EMPTY;
        try {
            List<ItemStack> fromJson = new ArrayList<>(1);
            addJsonIcon(ing.toJson(), fromJson);
            if (!fromJson.isEmpty()) return fromJson.get(0);
        } catch (Exception ignored) {
        }
        ItemStack[] items = ing.getItems();
        if (items.length == 0) return ItemStack.EMPTY;
        return items[0].copy();
    }

    private static boolean isSmithingCustomTypeId(String customTypeId) {
        return customTypeId != null && customTypeId.toLowerCase().contains("smithing");
    }

    private static void tryFillFromRawJson(ResourceLocation id, RecipeData d) {
        try {
            ResourceLocation path = new ResourceLocation(id.getNamespace(), "recipes/" + id.getPath() + ".json");
            net.minecraft.server.MinecraftServer integrated =
                    net.minecraft.client.Minecraft.getInstance().getSingleplayerServer();
            net.minecraft.server.packs.resources.ResourceManager rm = integrated != null
                    ? integrated.getResourceManager()
                    : net.minecraft.client.Minecraft.getInstance().getResourceManager();
            java.util.Optional<net.minecraft.server.packs.resources.Resource> res = rm.getResource(path);
            if (res.isEmpty()) return;

            com.google.gson.JsonObject root;
            try (java.io.InputStreamReader r = new java.io.InputStreamReader(
                    res.get().open(), java.nio.charset.StandardCharsets.UTF_8)) {
                root = com.google.gson.JsonParser.parseReader(r).getAsJsonObject();
            }

            if (isSmithingCustomTypeId(d.customTypeId)) {
                List<ItemStack> slot = new ArrayList<>(1);
                String[] keys = {"template", "base", "addition"};
                for (int i = 0; i < keys.length && i < d.ingredients.size(); i++) {
                    if (!root.has(keys[i])) continue;
                    slot.clear();
                    addJsonIcon(root.get(keys[i]), slot);
                    if (!slot.isEmpty()) d.ingredients.set(i, slot.get(0));
                }
                fillExtraJsonFromRawRecipe(root, d);
                return;
            }

            List<ItemStack> found = new ArrayList<>();
            var descriptor = com.kve.common.recipetype.RecipeTypeDescriptorRegistry.find(d.customTypeId).orElse(null);
            for (com.google.gson.JsonElement el : com.kve.common.recipe.RawRecipeInputs.inputs(root, descriptor)) {
                addJsonIcon(el, found);
            }

            if (!found.isEmpty() && d.shapelessIngredients.isEmpty()) d.shapelessIngredients = found;

            fillExtraJsonFromRawRecipe(root, d);
            fillMachineSlots(root, d, descriptor);
        } catch (Exception ignored) {
        }
    }

    private static void fillExtraJsonFromRawRecipe(com.google.gson.JsonObject root, RecipeData d) {
        if (d.customTypeId == null || d.customTypeId.isBlank()) return;
        var found = com.kve.common.recipetype.RecipeTypeDescriptorRegistry.find(d.customTypeId);
        if (found.isEmpty() || found.get().extraFields.isEmpty()) return;

        com.google.gson.JsonObject extra = com.kve.common.recipe.RawRecipeInputs.extraFields(root, found.get());
        if (extra.size() > 0) d.customExtraJson = extra.toString();
    }

    private static void fillMachineSlots(com.google.gson.JsonObject root, RecipeData d,
                                         com.kve.common.recipetype.RecipeTypeDescriptor desc) {
        if (desc == null || !desc.hasSlotLayout() || desc.hasRitualView()) return;
        List<RecipeData.MachineSlot> ins = machineSlots(root, desc, false);
        List<RecipeData.MachineSlot> outs = machineSlots(root, desc, true);
        if (outs.stream().noneMatch(s -> s.type == RecipeData.MachineSlotType.ITEM && !s.item.isEmpty())
                && d.output != null && !d.output.isEmpty()) {
            int i = 0;
            for (var def : desc.slotLayout.slots) {
                if (!def.isOutput() || def.repeatable) continue;
                if (!"fluid".equalsIgnoreCase(def.type)) {
                    while (outs.size() <= i) outs.add(new RecipeData.MachineSlot());
                    if (isEmptySlot(outs.get(i))) outs.set(i, itemSlot(d.output.copy()));
                    break;
                }
                i++;
            }
        }
        trimTrailingEmpty(ins);
        trimTrailingEmpty(outs);
        if (ins.isEmpty() && outs.isEmpty()) return;
        d.machineInputs = ins;
        d.machineOutputs = outs;
    }

    private static List<RecipeData.MachineSlot> machineSlots(com.google.gson.JsonObject root,
                                                             com.kve.common.recipetype.RecipeTypeDescriptor desc, boolean outputs) {
        List<com.kve.common.recipetype.RecipeTypeDescriptor.SlotDef> defs = new ArrayList<>();
        for (var s : desc.slotLayout.slots) if (s.isOutput() == outputs) defs.add(s);
        List<List<com.google.gson.JsonElement>> els = com.kve.common.recipe.RawRecipeInputs.slotElements(root, desc, outputs);
        List<RecipeData.MachineSlot> fixed = new ArrayList<>(), tail = new ArrayList<>();
        for (int i = 0; i < defs.size() && i < els.size(); i++) {
            var def = defs.get(i);
            if (def.repeatable) {
                for (com.google.gson.JsonElement el : els.get(i)) {
                    RecipeData.MachineSlot s = toMachineSlot(el, def);
                    if (s != null) tail.add(s);
                }
                continue;
            }
            RecipeData.MachineSlot s = els.get(i).isEmpty() ? null : toMachineSlot(els.get(i).get(0), def);
            if (s == null) {
                s = new RecipeData.MachineSlot();
                if ("fluid".equalsIgnoreCase(def.type)) s.type = RecipeData.MachineSlotType.FLUID;
            }
            fixed.add(s);
        }
        fixed.addAll(tail);
        return fixed;
    }

    /** Un elemento JSON de la receta como slot del editor de maquina; null si no se puede leer. */
    private static RecipeData.MachineSlot toMachineSlot(com.google.gson.JsonElement el,
                                                        com.kve.common.recipetype.RecipeTypeDescriptor.SlotDef def) {
        if (el == null || el.isJsonNull()) return null;
        boolean fluidDef = "fluid".equalsIgnoreCase(def.type);
        if (el.isJsonArray()) {
            for (com.google.gson.JsonElement sub : el.getAsJsonArray()) {
                RecipeData.MachineSlot s = toMachineSlot(sub, def);
                if (s != null) return s;
            }
            return null;
        }
        if (el.isJsonPrimitive()) {
            String id = el.getAsString();
            if (fluidDef) {
                RecipeData.MachineSlot s = new RecipeData.MachineSlot();
                s.type = RecipeData.MachineSlotType.FLUID;
                s.fluidId = id;
                return s;
            }
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            if (id.startsWith("#")) o.addProperty("tag", id.substring(1)); else o.addProperty("item", id);
            el = o;
        }
        if (!el.isJsonObject()) return null;
        com.google.gson.JsonObject o = el.getAsJsonObject();
        if (fluidDef || (def.type == null && com.kve.common.recipe.RawRecipeInputs.isFluidJson(o))) {
            String id = null;
            for (String k : new String[]{"fluid", "FluidName", "gas", "slurry", "pigment", "infuse_type", "chemical"}) {
                if (o.has(k) && o.get(k).isJsonPrimitive()) { id = o.get(k).getAsString(); break; }
                if (o.has(k) && o.get(k).isJsonObject()) return toMachineSlot(o.get(k), def);
            }
            if (id == null && o.has("id") && o.get("id").isJsonPrimitive()) {
                id = o.get("id").getAsString();
            }
            if (id == null) {
                for (String k : new String[]{"fluidTag", "tag"}) {
                    if (o.has(k) && o.get(k).isJsonPrimitive()) { id = "#" + o.get(k).getAsString(); break; }
                }
            }
            if (id == null && o.has("ingredient")) {
                RecipeData.MachineSlot inner = toMachineSlot(o.get("ingredient"), def);
                if (inner != null && inner.type == RecipeData.MachineSlotType.FLUID && o.has("amount")) {
                    try { inner.fluidAmount = Math.max(1, o.get("amount").getAsInt()); } catch (Exception ignored) {}
                }
                return inner;
            }
            if (id == null || id.isBlank()) return null;
            RecipeData.MachineSlot s = new RecipeData.MachineSlot();
            s.type = RecipeData.MachineSlotType.FLUID;
            s.fluidId = id;
            for (String k : new String[]{"amount", "Amount"}) {
                try {
                    if (o.has(k) && o.get(k).isJsonPrimitive()) { s.fluidAmount = Math.max(1, o.get(k).getAsInt()); break; }
                } catch (Exception ignored) {}
            }
            readChance(o, s);
            return s;
        }
        List<ItemStack> one = new ArrayList<>(1);
        addJsonIcon(o, one);
        if (one.isEmpty()) return null;
        RecipeData.MachineSlot s = itemSlot(one.get(0));
        readChance(o, s);
        return s;
    }

    private static RecipeData.MachineSlot itemSlot(ItemStack stack) {
        RecipeData.MachineSlot s = new RecipeData.MachineSlot();
        String tag = com.kve.common.recipe.TagIngredient.tagOf(stack);
        if (tag != null) {
            s.type = RecipeData.MachineSlotType.TAG;
            s.tagId = tag;
            s.tagCount = Math.max(1, stack.getCount());
        } else {
            s.item = stack;
        }
        return s;
    }

    private static void readChance(com.google.gson.JsonObject o, RecipeData.MachineSlot s) {
        try {
            if (o.has("chance") && o.get("chance").isJsonPrimitive()) s.chance = o.get("chance").getAsDouble();
        } catch (Exception ignored) {}
    }

    private static boolean isEmptySlot(RecipeData.MachineSlot s) {
        return switch (s.type) {
            case ITEM -> s.item == null || s.item.isEmpty();
            case FLUID -> s.fluidId == null || s.fluidId.isBlank();
            case TAG -> s.tagId == null || s.tagId.isBlank();
        };
    }

    private static void trimTrailingEmpty(List<RecipeData.MachineSlot> list) {
        while (!list.isEmpty() && isEmptySlot(list.get(list.size() - 1))) list.remove(list.size() - 1);
    }

    private static int jsonCount(com.google.gson.JsonObject o) {
        for (String k : new String[]{"count", "Count", "amount"}) {
            try {
                if (o.has(k) && o.get(k).isJsonPrimitive()) return Math.max(1, o.get(k).getAsInt());
            } catch (Exception ignored) {}
        }
        return 1;
    }

    private static void addJsonIcon(com.google.gson.JsonElement el, List<ItemStack> out) {
        try {
            if (el == null || el.isJsonNull()) return;
            if (el.isJsonArray()) {
                for (com.google.gson.JsonElement sub : el.getAsJsonArray()) {
                    int before = out.size();
                    addJsonIcon(sub, out);
                    if (out.size() > before) return;
                }
                return;
            }
            if (!el.isJsonObject()) return;
            com.google.gson.JsonObject o = el.getAsJsonObject();

            String itemId = null;
            int count = jsonCount(o);
            if (o.has("item")) {
                com.google.gson.JsonElement itemEl = o.get("item");
                if (itemEl.isJsonPrimitive()) {
                    itemId = itemEl.getAsString();
                } else {
                    addJsonIcon(itemEl, out);
                    return;
                }
            } else if (o.has("tag") && o.get("tag").isJsonPrimitive()) {
                String tagId = o.get("tag").getAsString();
                if (com.kve.common.recipe.TagIngredient.members(tagId).isEmpty()) {
                    List<net.minecraft.world.item.Item> lenient = lenientTagMembers(tagId);
                    if (!lenient.isEmpty()) {
                        out.add(new ItemStack(lenient.get(0), count));
                        if (repairing != null) {
                            repairing.add("#" + tagId + " -> " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(lenient.get(0)));
                        }
                        return;
                    }
                }
                ItemStack tagStack = com.kve.common.recipe.TagIngredient.of(tagId, null);
                tagStack.setCount(count);
                out.add(tagStack);
                return;
            }
            if (itemId == null && o.has("ingredient")) {
                List<ItemStack> inner = new ArrayList<>(1);
                addJsonIcon(o.get("ingredient"), inner);
                if (!inner.isEmpty()) {
                    ItemStack s = inner.get(0);
                    if (count > 1) s.setCount(count);
                    out.add(s);
                }
                return;
            }
            if (itemId == null) return;
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getOptional(new ResourceLocation(itemId));
            if (item.isEmpty()) return;
            ItemStack stack = new ItemStack(item.get(), count);
            if (o.has("nbt")) {
                com.google.gson.JsonElement nbt = o.get("nbt");
                String snbt = nbt.isJsonPrimitive() ? nbt.getAsString() : nbt.toString();
                try { stack.setTag(net.minecraft.nbt.TagParser.parseTag(snbt)); } catch (Exception ignored) {}
            }
            out.add(stack);
        } catch (Exception ignored) {}
    }

    /**
     * Items de un tag de items leyendo sus JSON (todos los datapacks/mods, en
     * orden) e IGNORANDO las referencias que faltan - o sea, lo que el tag
     * tendria si un mod no lo hubiera roto. Primero usa el tag cargado si
     * tiene items. Necesita el servidor integrado (singleplayer/LAN): en un
     * servidor dedicado el cliente no ve los datapacks y devuelve vacio.
     */
    public static List<net.minecraft.world.item.Item> lenientTagMembers(String tagId) {
        java.util.LinkedHashSet<net.minecraft.world.item.Item> acc = new java.util.LinkedHashSet<>();
        lenientTag(tagId, new java.util.HashSet<>(), 0, acc);
        return new ArrayList<>(acc);
    }

    private static void lenientTag(String tagId, java.util.Set<String> seen, int depth,
                                   java.util.Set<net.minecraft.world.item.Item> acc) {
        String id = tagId.startsWith("#") ? tagId.substring(1) : tagId;
        if (depth > 8 || !seen.add(id)) return;
        List<net.minecraft.world.item.Item> loaded = com.kve.common.recipe.TagIngredient.members(id);
        if (!loaded.isEmpty()) { acc.addAll(loaded); return; }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        net.minecraft.server.MinecraftServer server = net.minecraft.client.Minecraft.getInstance().getSingleplayerServer();
        if (rl == null || server == null) return;
        ResourceLocation file = new ResourceLocation(rl.getNamespace(), "tags/items/" + rl.getPath() + ".json");
        java.util.LinkedHashSet<net.minecraft.world.item.Item> own = new java.util.LinkedHashSet<>();
        for (net.minecraft.server.packs.resources.Resource res : server.getResourceManager().getResourceStack(file)) {
            try (java.io.Reader r = new java.io.InputStreamReader(res.open(), java.nio.charset.StandardCharsets.UTF_8)) {
                com.google.gson.JsonObject o = com.google.gson.JsonParser.parseReader(r).getAsJsonObject();
                if (o.has("replace") && o.get("replace").getAsBoolean()) own.clear();
                if (!o.has("values") || !o.get("values").isJsonArray()) continue;
                for (com.google.gson.JsonElement v : o.getAsJsonArray("values")) {
                    String ref = v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString();
                    if (ref.startsWith("#")) {
                        lenientTag(ref, seen, depth + 1, own);
                    } else {
                        ResourceLocation irl = ResourceLocation.tryParse(ref);
                        if (irl != null) net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(irl).ifPresent(own::add);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        acc.addAll(own);
    }
}
