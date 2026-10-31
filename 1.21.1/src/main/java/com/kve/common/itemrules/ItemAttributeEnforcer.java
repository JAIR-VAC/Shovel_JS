package com.kve.common.itemrules;

import com.kve.common.kubejs.KubeJSItemAttrExporter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.core.registries.BuiltInRegistries;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ItemAttributeEnforcer {

    private ItemAttributeEnforcer() {}

    @SubscribeEvent
    public static void onItemAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || ItemTooltipEnforcer.isPreview(stack)) return;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) return;

        KubeJSItemAttrExporter.ItemEntry e = KubeJSItemAttrExporter.getAppliedEntry(ItemIds.idOf(stack.getItem()));
        if (e == null || !e.enabled || !KubeJSItemAttrExporter.hasAttributeEdits(e)) {
            applyCustomItemModifiers(event, stack, itemId);
            return;
        }

        EquipmentSlot slot = ItemIds.naturalSlot(stack);
        net.minecraft.world.entity.EquipmentSlotGroup group = net.minecraft.world.entity.EquipmentSlotGroup.bySlot(slot);

        for (KubeJSItemAttrExporter.AttrMod m : KubeJSItemAttrExporter.effectiveAttributeMods(e)) {
            net.minecraft.core.Holder<Attribute> attr = attribute(m.attrId);
            if (attr == null) continue;
            boolean addMode = e.isAddMode(m.attrId);
            ResourceLocation id = idFor(itemId.toString(), m.attrId, slot, addMode);
            if (addMode) {
                event.removeModifier(attr, id);
            } else {
                event.removeAllModifiersFor(attr);
            }
            event.addModifier(attr, new AttributeModifier(id, m.value, m.op.vanilla()), group);
        }
        for (String removed : KubeJSItemAttrExporter.effectiveRemovedAttributes(e)) {
            net.minecraft.core.Holder<Attribute> attr = attribute(removed);
            if (attr != null) event.removeAllModifiersFor(attr);
        }
    }

    private static void applyCustomItemModifiers(ItemAttributeModifierEvent event, ItemStack stack, ResourceLocation itemId) {
        com.kve.common.customitem.CustomItemState.loadIfNeededThrottled(1000L);
        com.kve.common.customitem.CustomItemDraft d = com.kve.common.customitem.CustomItemState.getByFullId(itemId.toString());
        if (d != null && !d.attributeModifiers.isEmpty()) {
            EquipmentSlot slot = ItemIds.naturalSlot(stack);
            var group = net.minecraft.world.entity.EquipmentSlotGroup.bySlot(slot);
            for (KubeJSItemAttrExporter.AttrMod m : d.attributeModifiers.values()) {
                if (m.attrId == null || m.attrId.isEmpty()) continue;
                net.minecraft.core.Holder<Attribute> attr = attribute(m.attrId);
                if (attr == null) continue;
                event.removeAllModifiersFor(attr);
                event.addModifier(attr, new AttributeModifier(idFor(itemId.toString(), m.attrId, slot), m.value, m.op.vanilla()), group);
            }
        }
    }

    static net.minecraft.core.Holder<Attribute> attribute(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : BuiltInRegistries.ATTRIBUTE.getHolder(rl).orElse(null);
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, ResourceLocation> ID_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    static ResourceLocation idFor(String itemId, String attrId, EquipmentSlot slot) {
        return idFor(itemId, attrId, slot, false);
    }

    /** {@code addMode}: en "Sumar" el modifier va AL LADO del base vanilla, asi que nunca puede reusar su id. */
    static ResourceLocation idFor(String itemId, String attrId, EquipmentSlot slot, boolean addMode) {
        if (!addMode && attrId.equals("minecraft:generic.attack_damage")) return net.minecraft.world.item.Item.BASE_ATTACK_DAMAGE_ID;
        if (!addMode && attrId.equals("minecraft:generic.attack_speed")) return net.minecraft.world.item.Item.BASE_ATTACK_SPEED_ID;
        String key = "shovel_js:" + itemId + ":" + attrId + ":" + slot.getName();
        return ID_CACHE.computeIfAbsent(key, k ->
                ResourceLocation.fromNamespaceAndPath("shovel_js", "item_attr/" + UUID.nameUUIDFromBytes(k.getBytes(StandardCharsets.UTF_8))));
    }
}
