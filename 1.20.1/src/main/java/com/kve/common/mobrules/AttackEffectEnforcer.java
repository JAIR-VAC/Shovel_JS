package com.kve.common.mobrules;

import com.kve.CraftEditorMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;

public final class AttackEffectEnforcer {

    private AttackEffectEnforcer() {}

    /** One-shot warning per bad effect id per server run, so a typo'd effectId logs once instead of spamming on every single hit. */
    private static final Set<String> WARNED_BAD_EFFECT_IDS = new HashSet<>();

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!AttackEffectRequirements.anyRegistered()) return;

        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;

        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof LivingEntity)) return;

        String attackerTypeId = MobTypeIds.idOf(attacker);
        if (attackerTypeId == null) return;

        var rules = AttackEffectRequirements.rulesFor(attackerTypeId);
        if (rules.isEmpty()) return;

        for (AttackEffectRule rule : rules) {
            if (rule.chance < 1.0f && victim.getRandom().nextFloat() >= rule.chance) continue;

            ResourceLocation effectRl = ResourceLocation.tryParse(rule.effectId);
            MobEffect effect = effectRl == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(effectRl);
            if (effect == null) {
                if (WARNED_BAD_EFFECT_IDS.add(attackerTypeId + "->" + rule.effectId)) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] attack effect: efecto desconocido '{}' configurado para '{}', se omite",
                            rule.effectId, attackerTypeId);
                }
                continue;
            }
            victim.addEffect(new MobEffectInstance(effect, rule.durationTicks, rule.amplifier));
        }
    }
}
