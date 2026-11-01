package com.kve.common.itemrules;

import com.kve.common.kubejs.KubeJSItemAttrExporter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ItemAttributeEnforcer {

    private ItemAttributeEnforcer() {
    }

    @SubscribeEvent
    public static void onItemAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || KubeJSItemAttrExporter.getEntries().isEmpty() || ItemTooltipEnforcer.isPreview(stack))
            return;
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null)
            return;

        KubeJSItemAttrExporter.ItemEntry e = KubeJSItemAttrExporter.getEntry(ItemIds.idOf(stack.getItem()));
        if (e == null || !e.enabled || !KubeJSItemAttrExporter.hasAttributeEdits(e))
            return;

        EquipmentSlot slot = LivingEntity.getEquipmentSlotForItem(stack);
        if (event.getSlotType() != slot)
            return;

        for (KubeJSItemAttrExporter.AttrMod m : KubeJSItemAttrExporter.effectiveAttributeMods(e)) {
            Attribute attr = attribute(m.attrId);
            if (attr == null)
                continue;
            boolean addMode = e.isAddMode(m.attrId);
            UUID id = uuidFor(itemId.toString(), m.attrId, slot, addMode);
            if (addMode) {
                event.getModifiers().get(attr).stream().filter(old -> id.equals(old.getId())).toList()
                        .forEach(old -> event.removeModifier(attr, old));
            } else {
                event.removeAttribute(attr);
            }
            event.addModifier(attr, new AttributeModifier(id,
                    "shovel_js_attr", m.value, AttributeModifier.Operation.valueOf(m.op.name())));
        }
        for (String removed : KubeJSItemAttrExporter.effectiveRemovedAttributes(e)) {
            Attribute attr = attribute(removed);
            if (attr != null)
                event.removeAttribute(attr);
        }
    }

    static Attribute attribute(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : ForgeRegistries.ATTRIBUTES.getValue(rl);
    }

    
    static UUID uuidFor(String itemId, String attrId, EquipmentSlot slot, boolean addMode) {
        if (!addMode && attrId.equals("minecraft:generic.attack_damage"))
            return VanillaUuids.ATTACK_DAMAGE;
        if (!addMode && attrId.equals("minecraft:generic.attack_speed"))
            return VanillaUuids.ATTACK_SPEED;
        return UUID_CACHE.computeIfAbsent("shovel_js:" + itemId + ":" + attrId + ":" + slot.getName(),
                k -> UUID.nameUUIDFromBytes(k.getBytes(StandardCharsets.UTF_8)));
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, UUID> UUID_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Los UUID base son {@code protected static} en Item: una subclase los puede
     * leer sin reflexion. Nunca se instancia.
     */
    private abstract static class VanillaUuids extends net.minecraft.world.item.Item {
        static final UUID ATTACK_DAMAGE = BASE_ATTACK_DAMAGE_UUID;
        static final UUID ATTACK_SPEED = BASE_ATTACK_SPEED_UUID;

        private VanillaUuids() {
            super(new Properties());
        }
    }
}
