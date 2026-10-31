package com.kve.common.itemrules;

import com.kve.CraftEditorMod;
import com.kve.common.kubejs.KubeJSItemAttrExporter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;

public final class ItemCombatEnforcer {

    private ItemCombatEnforcer() {}

    private static final int PASSIVE_INTERVAL = 20;
    private static final int PASSIVE_MIN_DURATION = 60;
    private static final int NIGHT_VISION_MIN_DURATION = 260;

    private static final Set<String> WARNED_BAD_EFFECT_IDS = new HashSet<>();

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        LivingEntity attacker = meleeAttacker(event.getSource().getEntity(), event.getSource().getDirectEntity());
        if (attacker == null || attacker == target) return;

        KubeJSItemAttrExporter.CombatProps c = combatOf(attacker.getMainHandItem());
        if (c == null) return;

        for (KubeJSItemAttrExporter.FoodEffect fx : c.hitEffects) {
            if (fx.chance < 1.0f && attacker.getRandom().nextFloat() >= fx.chance) continue;
            MobEffect effect = effect(fx.effectId);
            if (effect == null) continue;
            target.addEffect(new MobEffectInstance(effect, Math.max(1, fx.durationTicks), Math.max(0, fx.amplifier)), attacker);
        }
        if (c.igniteSeconds > 0 && !target.fireImmune()) {
            target.setSecondsOnFire(c.igniteSeconds);
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        LivingEntity attacker = meleeAttacker(event.getSource().getEntity(), event.getSource().getDirectEntity());
        if (attacker == null || attacker == target) return;

        KubeJSItemAttrExporter.CombatProps c = combatOf(attacker.getMainHandItem());
        if (c == null || c.lifestealPct <= 0) return;

        // No se puede robar mas vida de la que el objetivo tenia.
        float dealt = Math.min(event.getAmount(), target.getHealth());
        float heal = (float) (dealt * Math.min(100, c.lifestealPct) / 100.0);
        if (heal > 0) attacker.heal(heal);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide() || player.tickCount % PASSIVE_INTERVAL != 0) return;
        if (!KubeJSItemAttrExporter.hasAppliedEntries()) return;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            KubeJSItemAttrExporter.CombatProps c = combatOf(stack);
            if (c == null || c.passiveEffects.isEmpty()) continue;
            if (!activeInSlot(stack, slot)) continue;

            for (KubeJSItemAttrExporter.FoodEffect fx : c.passiveEffects) {
                MobEffect effect = effect(fx.effectId);
                if (effect == null) continue;
                int min = effect == MobEffects.NIGHT_VISION ? NIGHT_VISION_MIN_DURATION : PASSIVE_MIN_DURATION;
                int duration = Math.max(min, fx.durationTicks);
                player.addEffect(new MobEffectInstance(effect, duration, Math.max(0, fx.amplifier), true, false, true));
            }
        }
    }

    private static boolean activeInSlot(ItemStack stack, EquipmentSlot slot) {
        EquipmentSlot natural = LivingEntity.getEquipmentSlotForItem(stack);
        if (natural.getType() == EquipmentSlot.Type.ARMOR) return slot == natural;
        return slot.getType() == EquipmentSlot.Type.HAND;
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        ItemStack stack = event.getItemStack();
        KubeJSItemAttrExporter.CombatProps c = combatOf(stack);
        if (c == null || c.useCooldownTicks <= 0) return;
        if (player.getCooldowns().isOnCooldown(stack.getItem())) return;
        player.getCooldowns().addCooldown(stack.getItem(), c.useCooldownTicks);
    }

    private static LivingEntity meleeAttacker(Entity source, Entity direct) {
        if (!(source instanceof LivingEntity living)) return null;
        if (direct != null && direct != source) return null;
        return living;
    }

    private static KubeJSItemAttrExporter.CombatProps combatOf(ItemStack stack) {
        KubeJSItemAttrExporter.ItemEntry e = ItemIds.entryFor(stack);
        if (e == null || e.combat == null || e.combat.isEmpty()) return null;
        return e.combat;
    }

    private static MobEffect effect(String effectId) {
        if (effectId == null || effectId.isEmpty()) return null;
        ResourceLocation rl = ResourceLocation.tryParse(effectId);
        MobEffect effect = rl == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(rl);
        if (effect == null && WARNED_BAD_EFFECT_IDS.add(effectId)) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] item combat: efecto desconocido '{}', se omite", effectId);
        }
        return effect;
    }
}
