package com.kve.common.mobrules;

import net.neoforged.neoforge.event.tick.EntityTickEvent;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.Set;

public final class EnemyEnforcer {

    private EnemyEnforcer() {}

    private static final int SCAN_INTERVAL_TICKS = 10;

    private static final double DEFAULT_SEARCH_RADIUS = 16.0;
    private static final double MAX_SEARCH_RADIUS = 32.0;

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Pre event) {
        if (!EnemyRequirements.anyRegistered()) return;

        if (!(event.getEntity() instanceof LivingEntity le)) return;
        if (!(le instanceof Mob mob)) return;
        if (mob.level().isClientSide()) return;

        if (mob.tickCount % SCAN_INTERVAL_TICKS != 0) return;
        if (mob.getTarget() != null && mob.getTarget().isAlive()) return;

        String mobTypeId = MobTypeIds.idOf(mob);
        if (mobTypeId == null) return;

        Set<String> enemyTypeIds = EnemyRequirements.enemiesOf(mobTypeId);
        if (enemyTypeIds.isEmpty()) return;

        double radius = mob.getAttributes().hasAttribute(Attributes.FOLLOW_RANGE)
                ? Math.min(mob.getAttributeValue(Attributes.FOLLOW_RANGE), MAX_SEARCH_RADIUS)
                : DEFAULT_SEARCH_RADIUS;

        LivingEntity nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (LivingEntity candidate : mob.level().getEntitiesOfClass(
                LivingEntity.class, mob.getBoundingBox().inflate(radius))) {
            if (candidate == mob || !candidate.isAlive()) continue;

            String candidateTypeId = MobTypeIds.idOf(candidate);
            if (candidateTypeId == null || !enemyTypeIds.contains(candidateTypeId)) continue;

            if (!mob.hasLineOfSight(candidate)) continue;

            double distSq = mob.distanceToSqr(candidate);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = candidate;
            }
        }

        if (nearest != null) {
            mob.setTarget(nearest);
        }
    }
}
