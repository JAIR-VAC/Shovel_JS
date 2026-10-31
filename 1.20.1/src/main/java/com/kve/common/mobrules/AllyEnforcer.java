package com.kve.common.mobrules;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class AllyEnforcer {

    private AllyEnforcer() {}

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!AllyRequirements.anyRegistered()) return;

        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        LivingEntity newTarget = event.getNewTarget();
        if (newTarget == null) return;

        String entityTypeId = MobTypeIds.idOf(entity);
        String targetTypeId = MobTypeIds.idOf(newTarget);
        if (entityTypeId == null || targetTypeId == null) return;

        if (!AllyRequirements.areAllies(entityTypeId, targetTypeId)) return;

        event.setNewTarget(null);
    }

    /**
     * Safety net for anything that still results in an attack roll against
     * an ally despite {@link #onChangeTarget()} (see class javadoc) - e.g. a
     * player manually attacking, or modded AI/goals that bypass vanilla's
     * target-selector goals entirely. Hooks {@link LivingAttackEvent}
     * rather than {@code LivingHurtEvent}: it fires BEFORE damage/knockback
     * is computed and is cancelable, so canceling it here means the attack
     * never happens at all (no damage, no knockback, no hurt sound) instead
     * of merely zeroing out damage after the fact.
     */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!AllyRequirements.anyRegistered()) return;

        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;

        Entity attackerEntity = event.getSource().getEntity();
        if (!(attackerEntity instanceof LivingEntity attacker)) return;

        String attackerTypeId = MobTypeIds.idOf(attacker);
        String victimTypeId = MobTypeIds.idOf(victim);
        if (attackerTypeId == null || victimTypeId == null) return;

        if (!AllyRequirements.areAllies(attackerTypeId, victimTypeId)) return;

        event.setCanceled(true);

        if (attacker instanceof Mob attackerMob && attackerMob.getTarget() == victim) {
            attackerMob.setTarget(null);
        }
        if (victim instanceof Mob victimMob && victimMob.getTarget() == attacker) {
            victimMob.setTarget(null);
        }
    }
}
