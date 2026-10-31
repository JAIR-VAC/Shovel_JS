package com.kve.client.gui;

import com.kve.client.gui.mob.MobEditability;
import com.kve.client.gui.mob.MobEditorScreen;
import com.kve.client.gui.mob.MobPickerScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class RegistryIndex {

    private RegistryIndex() {}

    private static List<Item> items;
    private static int itemsBuiltForSize = -1;

    private static List<Block> blocks;
    private static int blocksBuiltForSize = -1;

    /**
     * Todos los items menos {@code AIR}, vanilla primero. Lista inmutable
     * y COMPARTIDA: los llamadores la recorren, nunca la ordenan ni la
     * modifican (para eso copian a su propia {@code filtered}).
     */
    public static List<Item> items() {
        int size = BuiltInRegistries.ITEM.size();
        if (items == null || itemsBuiltForSize != size) {
            List<Item> vanilla = new ArrayList<>(), modded = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                ResourceLocation k = BuiltInRegistries.ITEM.getKey(item);
                if (k == null) continue;
                (k.getNamespace().equals("minecraft") ? vanilla : modded).add(item);
            }
            List<Item> all = new ArrayList<>(vanilla.size() + modded.size());
            all.addAll(vanilla);
            all.addAll(modded);
            items = List.copyOf(all);
            itemsBuiltForSize = size;
        }
        return items;
    }

    /** Todos los bloques menos {@code AIR}, vanilla primero. Mismo contrato que {@link #items()}. */
    public static List<Block> blocks() {
        int size = BuiltInRegistries.BLOCK.size();
        if (blocks == null || blocksBuiltForSize != size) {
            List<Block> vanilla = new ArrayList<>(), modded = new ArrayList<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block == Blocks.AIR) continue;
                ResourceLocation k = BuiltInRegistries.BLOCK.getKey(block);
                if (k == null) continue;
                (k.getNamespace().equals("minecraft") ? vanilla : modded).add(block);
            }
            List<Block> all = new ArrayList<>(vanilla.size() + modded.size());
            all.addAll(vanilla);
            all.addAll(modded);
            blocks = List.copyOf(all);
            blocksBuiltForSize = size;
        }
        return blocks;
    }

    private static List<EntityType<?>> editableMobTypes;
    private static int editableMobTypesBuiltForSize = -1;

    public static List<EntityType<?>> editableMobTypes() {
        int size = BuiltInRegistries.ENTITY_TYPE.size();
        if (editableMobTypes == null || editableMobTypesBuiltForSize != size) {
            net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
            List<EntityType<?>> vanilla = new ArrayList<>(), modded = new ArrayList<>();
            for (EntityType<?> et : BuiltInRegistries.ENTITY_TYPE) {
                if (!MobEditability.isEditableMob(level, et)) continue;
                ResourceLocation k = BuiltInRegistries.ENTITY_TYPE.getKey(et);
                if (k == null) continue;
                (k.getNamespace().equals("minecraft") ? vanilla : modded).add(et);
            }
            List<EntityType<?>> all = new ArrayList<>(vanilla.size() + modded.size());
            all.addAll(vanilla);
            all.addAll(modded);
            editableMobTypes = List.copyOf(all);
            editableMobTypesBuiltForSize = size;
        }
        return editableMobTypes;
    }

    private static final Map<Item, String>          ITEM_IDS  = new IdentityHashMap<>();
    private static final Map<Block, String>         BLOCK_IDS = new IdentityHashMap<>();
    private static final Map<EntityType<?>, String> MOB_IDS   = new IdentityHashMap<>();

    /** {@code "namespace:path"} del item, resuelto una sola vez. Cadena vacia si el item no esta registrado. */
    public static String id(Item item) {
        if (item == null) return "";
        String cached = ITEM_IDS.get(item);
        if (cached != null) return cached;
        ResourceLocation k = BuiltInRegistries.ITEM.getKey(item);
        String v = k != null ? k.toString() : "";
        ITEM_IDS.put(item, v);
        return v;
    }

    /** {@code "namespace:path"} del bloque, resuelto una sola vez. */
    public static String id(Block block) {
        if (block == null) return "";
        String cached = BLOCK_IDS.get(block);
        if (cached != null) return cached;
        ResourceLocation k = BuiltInRegistries.BLOCK.getKey(block);
        String v = k != null ? k.toString() : "";
        BLOCK_IDS.put(block, v);
        return v;
    }

    /** {@code "namespace:path"} del tipo de entidad, resuelto una sola vez. */
    public static String id(EntityType<?> type) {
        if (type == null) return "";
        String cached = MOB_IDS.get(type);
        if (cached != null) return cached;
        ResourceLocation k = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        String v = k != null ? k.toString() : "";
        MOB_IDS.put(type, v);
        return v;
    }

    private static final Map<Item, String>          ITEM_NAMES  = new IdentityHashMap<>();
    private static final Map<Block, String>         BLOCK_NAMES = new IdentityHashMap<>();
    private static final Map<EntityType<?>, String> MOB_NAMES   = new IdentityHashMap<>();

    private static String namesLanguage = null;

    /**
     * Tira los nombres cacheados si el jugador cambio de idioma desde la
     * ultima vez. Es un getter + un {@code equals} (igual que
     * {@link com.kve.common.Lang#get(String)}), no re-resuelve nada, asi
     * que llamarlo por entrada no reintroduce el costo que esto evita.
     */
    private static void checkLanguage() {
        String current;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            current = mc.getLanguageManager().getSelected();
        } catch (Exception ignored) {
            return;
        }
        if (!java.util.Objects.equals(current, namesLanguage)) {
            ITEM_NAMES.clear();
            BLOCK_NAMES.clear();
            MOB_NAMES.clear();
            namesLanguage = current;
        }
    }

    /** Nombre visible traducido del item. Esta es LA llamada cara que se cachea ({@code getDescription().getString()} aplana un Component entero). */
    public static String name(Item item) {
        if (item == null) return "";
        checkLanguage();
        String cached = ITEM_NAMES.get(item);
        if (cached != null) return cached;
        String v;
        try { v = item.getDescription().getString(); } catch (Throwable t) { v = id(item); }
        ITEM_NAMES.put(item, v);
        return v;
    }

    /** Nombre visible traducido del bloque. */
    public static String name(Block block) {
        if (block == null) return "";
        checkLanguage();
        String cached = BLOCK_NAMES.get(block);
        if (cached != null) return cached;
        String v;
        try { v = block.getName().getString(); } catch (Throwable t) { v = id(block); }
        BLOCK_NAMES.put(block, v);
        return v;
    }

    /** Nombre visible traducido del tipo de entidad. */
    public static String name(EntityType<?> type) {
        if (type == null) return "";
        checkLanguage();
        String cached = MOB_NAMES.get(type);
        if (cached != null) return cached;
        String v;
        try { v = type.getDescription().getString(); } catch (Throwable t) { v = id(type); }
        MOB_NAMES.put(type, v);
        return v;
    }

    private static final Map<Item, ItemStack>  ITEM_STACKS = new IdentityHashMap<>();
    private static final Map<Block, ItemStack> BLOCK_ICONS = new IdentityHashMap<>();

    /**
     * Stack de 1 unidad COMPARTIDO para dibujar este item en una grilla.
     * Solo lectura - ver el javadoc de la clase antes de usarlo para algo
     * que no sea render/tooltip.
     */
    public static ItemStack stack(Item item) {
        if (item == null) return ItemStack.EMPTY;
        ItemStack cached = ITEM_STACKS.get(item);
        if (cached != null) return cached;
        ItemStack v = new ItemStack(item);
        ITEM_STACKS.put(item, v);
        return v;
    }

    public static ItemStack blockIcon(Block block) {
        if (block == null) return ItemStack.EMPTY;
        ItemStack cached = BLOCK_ICONS.get(block);
        if (cached != null) return cached;
        Item item = block.asItem();
        ItemStack v = (item != null && item != Items.AIR) ? new ItemStack(item) : ItemStack.EMPTY;
        BLOCK_ICONS.put(block, v);
        return v;
    }
}
