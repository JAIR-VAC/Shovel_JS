package com.kve.client.recipe;

import com.kve.common.recipe.RecipeData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Icono y nombre de la estacion de crafteo de un tipo de receta (mesa de
 * crafteo, horno, "create:crusher"...). Salio de CraftEditorScreen
 * lo usan el editor y RecipeBrowserScreen, y no tiene nada
 * de pantalla. Cacheado - ver {@link #STATION_ICON_CACHE}.
 */
@OnlyIn(Dist.CLIENT)
public final class StationResolver {
    private StationResolver() {}

    private static final java.util.Map<String, Item> KNOWN_VANILLA_STATIONS = java.util.Map.of(
            "minecraft:smelting", Items.FURNACE,
            "minecraft:smoking", Items.SMOKER,
            "minecraft:blasting", Items.BLAST_FURNACE,
            "minecraft:campfire_cooking", Items.CAMPFIRE,
            "minecraft:stonecutting", Items.STONECUTTER,
            "minecraft:smithing_transform", Items.SMITHING_TABLE,
            "minecraft:smithing_trim", Items.SMITHING_TABLE
    );

    private static final java.util.Map<String, ItemStack> STATION_ICON_CACHE = new java.util.HashMap<>();

    private static final java.util.Map<String, String> STATION_NAME_CACHE = new java.util.HashMap<>();

    private static String stationNamesLanguage = null;

    private static String stationCacheKey(RecipeData.RecipeType type, String customTypeId) {
        return type.name() + '\u0000' + (customTypeId == null ? "" : customTypeId);
    }

    public static ItemStack resolveStationIcon(RecipeData.RecipeType type, String customTypeId) {
        String key = stationCacheKey(type, customTypeId);
        ItemStack cached = STATION_ICON_CACHE.get(key);
        if (cached != null) return cached;
        ItemStack resolved = resolveStationIconUncached(type, customTypeId);
        STATION_ICON_CACHE.put(key, resolved);
        return resolved;
    }

    private static ItemStack resolveStationIconUncached(RecipeData.RecipeType type, String customTypeId) {
        String namespace, keyword;
        switch (type) {
            case VANILLA_SHAPED, VANILLA_SHAPELESS -> { return new ItemStack(Items.CRAFTING_TABLE); }
            case EC_TABLE_SHAPED, EC_TABLE_SHAPELESS -> { namespace = "extendedcrafting"; keyword = "table"; }
            case EC_ENDER_SHAPED, EC_ENDER_SHAPELESS -> { namespace = "extendedcrafting"; keyword = "ender"; }
            case EC_FLUX_SHAPED, EC_FLUX_SHAPELESS  -> { namespace = "extendedcrafting"; keyword = "flux"; }
            case EC_COMBINATION -> { namespace = "extendedcrafting"; keyword = "singularity"; }
            case CUSTOM_JSON, CUSTOM_MACHINE -> {
                if (customTypeId == null || customTypeId.isEmpty()) return new ItemStack(Items.CRAFTING_TABLE);
                var explicitStation = explicitStationIcon(customTypeId);
                if (explicitStation != null) return explicitStation;
                Item knownVanilla = KNOWN_VANILLA_STATIONS.get(customTypeId);
                if (knownVanilla != null) return new ItemStack(knownVanilla);
                String[] nk = namespaceAndKeyword(customTypeId);
                namespace = nk[0];
                keyword = nk[1];
            }
            default -> { return new ItemStack(Items.CRAFTING_TABLE); }
        }
        ItemStack found = findItemByNamespaceAndKeyword(namespace, keyword);
        return found != null ? found : new ItemStack(Items.CRAFTING_TABLE);
    }

    /**
     * Real display name of the station a recipe type is crafted with -
     * companion to {@link #resolveStationIcon()}, used by
     * {@link RecipeBrowserScreen} to replace the generic "other" bucket
     * label with the actual station (e.g. "Crushing Table" instead of the
     * literal word "other"). Prefers the pack's own
     * {@link com.kve.common.recipetype.RecipeTypeDescriptor#effectiveDisplayName()}
     * when a descriptor is configured for this type (authoritative, not a
     * guess); otherwise falls back to the hover name of whatever item
     * {@link #resolveStationIcon()} resolved (still a real, existing item's
     * real name - never the word "other").
     */
    public static String resolveStationName(RecipeData.RecipeType type, String customTypeId) {
        // Perf: ver STATION_ICON_CACHE. Ademas del escaneo de registry que
        // ese cache ya evita, getHoverName().getString() construye y aplana
        // un Component traducible - y esto corre por fila visible por frame
        // en RecipeBrowserScreen.
        String current = null;
        try {
            var mc = Minecraft.getInstance();
            if (mc != null) current = mc.getLanguageManager().getSelected();
        } catch (Exception ignored) {}
        if (!java.util.Objects.equals(current, stationNamesLanguage)) {
            STATION_NAME_CACHE.clear();
            stationNamesLanguage = current;
        }
        String key = stationCacheKey(type, customTypeId);
        String cached = STATION_NAME_CACHE.get(key);
        if (cached != null) return cached;

        String resolved;
        if ((type == RecipeData.RecipeType.CUSTOM_JSON || type == RecipeData.RecipeType.CUSTOM_MACHINE)
                && customTypeId != null && !customTypeId.isEmpty()
                && com.kve.common.recipetype.RecipeTypeDescriptorRegistry.find(customTypeId).isPresent()) {
            resolved = com.kve.common.recipetype.RecipeTypeDescriptorRegistry.find(customTypeId).get().effectiveDisplayName();
        } else {
            resolved = resolveStationIcon(type, customTypeId).getHoverName().getString();
        }
        STATION_NAME_CACHE.put(key, resolved);
        return resolved;
    }

    private static ItemStack explicitStationIcon(String recipeTypeId) {
        var descriptor = com.kve.common.recipetype.RecipeTypeDescriptorRegistry.find(recipeTypeId).orElse(null);
        if (descriptor == null || !descriptor.hasStationItem()) return null;
        ResourceLocation rl = ResourceLocation.tryParse(descriptor.stationItem);
        if (rl == null) return null;
        Item item = BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
        return item != null && item != Items.AIR ? new ItemStack(item) : null;
    }

    private static ItemStack findItemByNamespaceAndKeyword(String namespace, String keyword) {
        if (namespace == null || namespace.isEmpty() || keyword == null || keyword.isEmpty()) return null;
        ItemStack found = findItemByNamespaceAndKeywordExact(namespace, keyword.toLowerCase());
        if (found != null) return found;
        String[] words = keyword.toLowerCase().split("_");
        java.util.Arrays.sort(words, (a, b) -> b.length() - a.length());
        for (String w : words) {
            if (w.length() < 4) continue;
            found = findItemByNamespaceAndKeywordExact(namespace, w);
            if (found != null) return found;
        }
        return null;
    }

    private static ItemStack findItemByNamespaceAndKeywordExact(String namespace, String kw) {
        ItemStack bestMatch = null;
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
            if (rl == null || !rl.getNamespace().equals(namespace)) continue;
            String path = rl.getPath().toLowerCase();
            if (path.contains(kw)) {
                if (path.contains("table") || path.contains("station") || path.contains("machine"))
                    return new ItemStack(item);
                if (bestMatch == null) bestMatch = new ItemStack(item);
            }
        }
        return bestMatch;
    }

    /**
     * {namespace, palabra clave} para buscar la estacion de un tipo de receta
     * de mod: "create:crushing" -> {"create", "crush"}. Se quita la cola
     * "_jei"/"_emi"/"_display"/... (serializers que solo existen para que el
     * visor muestre la receta, ej. "trinity_starforge_jei") y el sufijo
     * verbal ("ing"/"ery"/"er"/"s"), que nunca aparecen en el id del bloque.
     */
    public static String[] namespaceAndKeyword(String customTypeId) {
        int idx = customTypeId.indexOf(':');
        String namespace = idx > 0 ? customTypeId.substring(0, idx) : "";
        String path = idx > 0 ? customTypeId.substring(idx + 1) : customTypeId;
        if (path.contains("/")) path = path.substring(path.lastIndexOf('/') + 1);
        path = path.replaceAll("_(jei|emi|rei|display|viewer)$", "");
        return new String[]{namespace, path.replaceAll("(ing|ery|er|s)$", "")};
    }
}
