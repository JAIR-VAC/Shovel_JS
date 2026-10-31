package com.kve.common.mobrules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Set;

public final class MobBehaviorFlagsEnforcer {

    private MobBehaviorFlagsEnforcer() {}

    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final double DEFAULT_SEARCH_RADIUS = 16.0;
    private static final double MAX_SEARCH_RADIUS = 32.0;

    private static final double MELEE_REACH = 3.0;
    private static final double RANGED_MAX_DISTANCE = 12.0;
    private static final int RANGED_ATTACK_COOLDOWN_TICKS = 40;
    private static final float RANGED_ATTACK_DAMAGE = 3.0F;

    private static final int GRIEF_COOLDOWN_TICKS = 60;
    private static final int MELEE_COOLDOWN_TICKS = 20;
    private static final float FALLBACK_MELEE_DAMAGE = 2.0F;
    private static final String NBT_MELEE_CD = "shovel_js_melee_cd";
    private static final String NBT_RANGED_CD = "shovel_js_ranged_cd";
    private static final String NBT_GRIEF_CD = "shovel_js_grief_cd";

    private static String typeIdOf(Entity e) {
        return MobTypeIds.idOf(e);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!MobBehaviorTypeFlags.anyRegistered()) return;

        LivingEntity le = event.getEntity();
        if (!(le instanceof Mob mob)) return;
        Level level = mob.level();
        if (level.isClientSide()) return;

        String typeId = typeIdOf(mob);
        if (typeId == null) return;
        Set<String> flags = MobBehaviorTypeFlags.flagsOf(typeId);
        if (flags.isEmpty()) return;

        boolean passive = flags.contains("PASSIVE");
        if (passive && mob.getTarget() != null) mob.setTarget(null);

        if (mob.tickCount % SCAN_INTERVAL_TICKS == 0) {
            double radius = mob.getAttributes().hasAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)
                    ? Math.min(mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE), MAX_SEARCH_RADIUS)
                    : DEFAULT_SEARCH_RADIUS;

            if (!passive && flags.contains("HOSTILE")
                    && (mob.getTarget() == null || !mob.getTarget().isAlive())) {
                Player nearest = findNearestVisiblePlayer(mob, radius);
                if (nearest != null) mob.setTarget(nearest);
            }

            if (flags.contains("FLEES_PLAYER")) {
                Player nearest = findNearestVisiblePlayer(mob, radius);
                if (nearest != null) fleeFrom(mob, nearest);
            }

            if (flags.contains("GRIEFS")) {
                tryGrief(mob, level);
            }
        }

        if (!passive && flags.contains("RANGED_ATTACK")) {
            tryRangedAttack(mob);
        }

        if (!passive && (flags.contains("HOSTILE") || flags.contains("NEUTRAL"))) {
            tryFallbackMelee(mob);
        }
    }

    private static void tryFallbackMelee(Mob mob) {
        LivingEntity target = mob.getTarget();
        if (!(target instanceof Player player) || !player.isAlive() || player.isCreative() || player.isSpectator()) return;
        if (hasOwnAttackAi(mob)) return;

        CompoundTag data = mob.getPersistentData();
        int cd = data.getInt(NBT_MELEE_CD);
        if (cd > 0) data.putInt(NBT_MELEE_CD, cd - 1);

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (mob.tickCount % SCAN_INTERVAL_TICKS == 0) {
            mob.getNavigation().moveTo(target, 1.2D);
        }
        double reach = mob.getBbWidth() * 2.0F * mob.getBbWidth() * 2.0F + target.getBbWidth();
        if (cd <= 0 && mob.distanceToSqr(target) <= Math.max(reach, MELEE_REACH * MELEE_REACH) && mob.hasLineOfSight(target)) {
            mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (mob.getAttributes().hasAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) {
                mob.doHurtTarget(target);
            } else {
                target.hurt(mob.damageSources().mobAttack(mob), FALLBACK_MELEE_DAMAGE);
            }
            data.putInt(NBT_MELEE_CD, MELEE_COOLDOWN_TICKS);
        }
    }

    private static boolean hasOwnAttackAi(Mob mob) {
        if (mob instanceof net.minecraft.world.entity.monster.Enemy) return true;
        for (var wrapped : mob.goalSelector.getAvailableGoals()) {
            var goal = wrapped.getGoal();
            if (goal instanceof net.minecraft.world.entity.ai.goal.MeleeAttackGoal
                    || goal instanceof net.minecraft.world.entity.ai.goal.RangedAttackGoal
                    || goal instanceof net.minecraft.world.entity.ai.goal.RangedBowAttackGoal<?>
                    || goal instanceof net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal<?>) {
                return true;
            }
        }
        return false;
    }

    /** NEUTRAL: solo reacciona cuando lo golpean - no busca nada por su cuenta, a diferencia de HOSTILE. PASSIVE gana si por algún motivo un mob tiene los dos flags juntos. */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!MobBehaviorTypeFlags.anyRegistered()) return;

        LivingEntity victim = event.getEntity();
        if (!(victim instanceof Mob mob)) return;
        if (mob.level().isClientSide()) return;

        String typeId = typeIdOf(mob);
        if (typeId == null) return;
        if (MobBehaviorTypeFlags.has(typeId, "PASSIVE")) return;
        if (!MobBehaviorTypeFlags.has(typeId, "NEUTRAL")) return;

        Entity src = event.getSource().getEntity();
        if (src instanceof Player player) mob.setTarget(player);
    }

    /** Mismo mecanismo que {@link AllyEnforcer#onChangeTarget()} - PASSIVE nunca deja que un target LLEGUE a asignarse, sin importar de dónde venga el intento (vanilla, otro mod, o incluso HOSTILE/NEUTRAL de este mismo enforcer si quedaran mal configurados juntos). */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!MobBehaviorTypeFlags.anyRegistered()) return;

        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        String typeId = typeIdOf(entity);
        if (typeId == null) return;
        if (MobBehaviorTypeFlags.has(typeId, "PASSIVE")) {
            event.setNewTarget(null);
        }
    }

    private static Player findNearestVisiblePlayer(Mob mob, double radius) {
        Player nearest = mob.level().getNearestPlayer(mob, radius);
        if (nearest == null || !nearest.isAlive() || nearest.isSpectator() || nearest.isCreative()) return null;
        if (!mob.hasLineOfSight(nearest)) return null;
        return nearest;
    }

    private static void fleeFrom(Mob mob, Player player) {
        Vec3 away = mob.position().subtract(player.position());
        if (away.lengthSqr() < 1.0E-4) {
            away = new Vec3(mob.getRandom().nextDouble() - 0.5, 0, mob.getRandom().nextDouble() - 0.5);
        }
        Vec3 dest = mob.position().add(away.normalize().scale(10));
        mob.getNavigation().moveTo(dest.x, dest.y, dest.z, 1.15D);
    }

    private static void tryGrief(Mob mob, Level level) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;

        CompoundTag data = mob.getPersistentData();
        int cd = data.getInt(NBT_GRIEF_CD);
        if (cd > 0) {
            data.putInt(NBT_GRIEF_CD, Math.max(0, cd - SCAN_INTERVAL_TICKS));
            return;
        }

        BlockPos target = mob.blockPosition().relative(mob.getDirection());
        BlockState state = level.getBlockState(target);
        if (state.isAir()) return;
        if (state.getDestroySpeed(level, target) < 0) return;

        level.destroyBlock(target, true, mob);
        data.putInt(NBT_GRIEF_CD, GRIEF_COOLDOWN_TICKS);
    }

    private static void tryRangedAttack(Mob mob) {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return;

        double distSq = mob.distanceToSqr(target);
        if (distSq < MELEE_REACH * MELEE_REACH || distSq > RANGED_MAX_DISTANCE * RANGED_MAX_DISTANCE) return;
        if (!mob.hasLineOfSight(target)) return;

        CompoundTag data = mob.getPersistentData();
        int cd = data.getInt(NBT_RANGED_CD);
        if (cd > 0) {
            data.putInt(NBT_RANGED_CD, cd - 1);
            return;
        }

        mob.swing(InteractionHand.MAIN_HAND);
        target.hurt(mob.level().damageSources().mobAttack(mob), RANGED_ATTACK_DAMAGE);
        data.putInt(NBT_RANGED_CD, RANGED_ATTACK_COOLDOWN_TICKS);
    }
}
