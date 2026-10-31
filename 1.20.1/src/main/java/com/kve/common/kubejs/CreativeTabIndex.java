package com.kve.common.kubejs;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class CreativeTabIndex {

    private static final Map<Item, ResourceLocation> ITEM_TO_TAB = new HashMap<>();

    @SubscribeEvent
    public static void onBuildContents(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tabId = event.getTabKey().location();
        for (Map.Entry<ItemStack, ?> entry : event.getEntries()) {
            ITEM_TO_TAB.putIfAbsent(entry.getKey().getItem(), tabId);
        }
    }

    /** @return el id de la tab real que contiene {@code item}, o {@code null} si todavía no se observó en ninguna. */
    public static ResourceLocation tabIdFor(Item item) {
        return item == null ? null : ITEM_TO_TAB.get(item);
    }

    /** Overload por id de item (string), lo que {@code KubeJSItemAttrExporter} maneja - resuelve contra {@link BuiltInRegistries#ITEM}. */
    public static ResourceLocation tabIdFor(String itemId) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return null;
        return tabIdFor(BuiltInRegistries.ITEM.get(rl));
    }
}
