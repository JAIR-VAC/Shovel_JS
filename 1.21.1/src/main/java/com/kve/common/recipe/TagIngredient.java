package com.kve.common.recipe;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Ingrediente "cualquier item del tag" en la grilla del editor de recetas
 * La grilla guarda {@link ItemStack}s, asi que un tag se
 * representa como un stack de un item del tag (para dibujarlo) con la marca
 * NBT {@value #NBT_KEY} = id del tag. El exporter la reconoce y emite
 * {@code '#forge:ingots/copper'} (KubeJS) o {@code {tag: '...'}} (JSON) en
 * vez del item; guardar, deshacer, copiar y pegar la conservan sin tocar el
 * modelo de datos.
 */
public final class TagIngredient {
    private TagIngredient() {}

    public static final String NBT_KEY = "shovel_js_tag";

    /** Stack marcado como tag. {@code representative} es solo para dibujar (se usa el primer miembro si es null). */
    public static ItemStack of(String tagId, Item representative) {
        String id = tagId.startsWith("#") ? tagId.substring(1) : tagId;
        Item shown = representative;
        if (shown == null || shown == Items.AIR) {
            List<Item> m = members(id);
            shown = m.isEmpty() ? Items.BARRIER : m.get(0);
        }
        ItemStack s = new ItemStack(shown);
        CompoundTag tag = new CompoundTag();
        tag.putString(NBT_KEY, id);
        com.kve.common.util.ItemExtraDataCodec.setCustomData(s, tag);
        return s;
    }

    public static String tagOf(ItemStack s) {
        if (s == null || s.isEmpty()) return null;
        CompoundTag t = com.kve.common.util.ItemExtraDataCodec.customData(s);
        return t.contains(NBT_KEY) ? t.getString(NBT_KEY) : null;
    }

    public static boolean isTag(ItemStack s) {
        return tagOf(s) != null;
    }

    /** true si el unico NBT del stack es la marca de tag (no hay NBT "real" que exportar). */
    public static boolean onlyMarker(CompoundTag t) {
        return t != null && t.size() == 1 && t.contains(NBT_KEY);
    }

    /** Items del tag segun el registro cargado (vacio sin mundo / tag inexistente). */
    public static List<Item> members(String tagId) {
        if (tagId == null) return List.of();
        long now = System.currentTimeMillis();
        CachedMembers c = MEMBERS.get(tagId);
        if (c != null && now - c.at() < MEMBERS_TTL_MS) return c.items();
        ResourceLocation rl = ResourceLocation.tryParse(tagId.startsWith("#") ? tagId.substring(1) : tagId);
        List<Item> items = rl == null ? List.of() : BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, rl))
                .map(set -> set.stream().map(Holder::value).toList())
                .orElse(List.of());
        if (MEMBERS.size() > 512) MEMBERS.clear();
        MEMBERS.put(tagId, new CachedMembers(items, now));
        return items;
    }

    /**
     * Las pantallas piden los miembros de un tag en cada fotograma (rotar el icono, marcar el
     * tag vacio, el tooltip). Se guardan unos segundos: un /reload que cambia tags se ve enseguida
     * y no se rearma la lista cientos de veces por segundo.
     */
    private record CachedMembers(List<Item> items, long at) {}

    private static final long MEMBERS_TTL_MS = 3000L;
    private static final java.util.Map<String, CachedMembers> MEMBERS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Todos los tags de items cargados ahora mismo, ordenados (modo "Tag" del selector de items). */
    public static List<String> allTags() {
        return BuiltInRegistries.ITEM.getTagNames().map(k -> k.location().toString()).sorted().toList();
    }

    /** Tags de items a los que pertenece {@code item}, ordenados (para elegir uno al convertir un slot). */
    public static List<String> tagsOf(Item item) {
        return item.builtInRegistryHolder().tags().map(k -> k.location().toString()).sorted().toList();
    }
}
