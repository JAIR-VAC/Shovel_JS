package com.kve.client.gui;

import com.kve.common.kubejs.TagExporter;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Icono que va rotando para un tag de la lista del editor de tags: los items que quedan en el tag
 * (los del juego, mas los agregados, menos los quitados). Bloques con su item, fluidos con su cubo
 * y entidades con su huevo. Las listas se guardan unos segundos; no se rearman en cada fotograma.
 */
public final class TagPreview {

    private TagPreview() {}

    /** Cada cuanto cambia el icono: lento, para que se alcance a ver cada uno. */
    private static final long CYCLE_MS = 1500L;
    private static final long CACHE_MS = 5000L;
    private static final int MAX_ITEMS = 64;

    private static final Map<String, List<Item>> CACHE = new HashMap<>();
    private static long cacheAt;

    /** Item a dibujar ahora para {@code e}, o null si el tag no tiene nada que mostrar. */
    public static Item current(TagExporter.TagEntry e) {
        List<Item> items = items(e);
        if (items.isEmpty()) return null;
        return items.get((int) ((System.currentTimeMillis() / CYCLE_MS) % items.size()));
    }

    public static List<Item> items(TagExporter.TagEntry e) {
        long now = System.currentTimeMillis();
        if (now - cacheAt > CACHE_MS) { CACHE.clear(); cacheAt = now; }
        String key = e.key() + "|" + e.replace + "|" + e.added.hashCode() + "|" + e.removed.hashCode();
        return CACHE.computeIfAbsent(key, k -> build(e));
    }

    private static List<Item> build(TagExporter.TagEntry e) {
        Set<Item> out = new LinkedHashSet<>();
        for (String id : e.added) add(out, e.regType, id);
        if (!e.replace) add(out, e.regType, "#" + e.tagId);
        Set<Item> removed = new LinkedHashSet<>();
        for (String id : e.removed) add(removed, e.regType, id);
        out.removeAll(removed);
        out.remove(Items.AIR);
        List<Item> list = new ArrayList<>(out);
        return list.size() > MAX_ITEMS ? new ArrayList<>(list.subList(0, MAX_ITEMS)) : list;
    }

    /** Agrega el item de {@code id}, o los de todo el tag si empieza con '#'. */
    private static void add(Set<Item> out, TagExporter.RegistryType type, String id) {
        if (id == null || id.isBlank()) return;
        boolean tag = id.startsWith("#");
        ResourceLocation rl = ResourceLocation.tryParse(tag ? id.substring(1) : id);
        if (rl == null) return;
        switch (type) {
            case ITEM -> collect(out, BuiltInRegistries.ITEM, Registries.ITEM, rl, tag, it -> it);
            case BLOCK -> collect(out, BuiltInRegistries.BLOCK, Registries.BLOCK, rl, tag, Block::asItem);
            case FLUID -> collect(out, BuiltInRegistries.FLUID, Registries.FLUID, rl, tag, Fluid::getBucket);
            case ENTITY_TYPE -> collect(out, BuiltInRegistries.ENTITY_TYPE, Registries.ENTITY_TYPE, rl, tag, SpawnEggItem::byId);
        }
    }

    private static <T> void collect(Set<Item> out, Registry<T> reg, ResourceKey<? extends Registry<T>> key,
                                    ResourceLocation rl, boolean tag, Function<T, Item> toItem) {
        try {
            if (tag) {
                reg.getTag(TagKey.create(key, rl)).ifPresent(set -> {
                    for (Holder<T> h : set) {
                        if (out.size() >= MAX_ITEMS) break;
                        Item it = toItem.apply(h.value());
                        if (it != null && it != Items.AIR) out.add(it);
                    }
                });
            } else {
                reg.getOptional(rl).map(toItem).filter(it -> it != Items.AIR).ifPresent(out::add);
            }
        } catch (RuntimeException ignored) {
            // un id raro de un mod no debe romper el dibujo de la lista
        }
    }
}
