package com.kve.common.mobrules;

import com.kve.common.kubejs.KubeJSMobExporter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Set;

public final class MobCombatEnforcer {

    private MobCombatEnforcer() {}

    static KubeJSMobExporter.MobEntry entryFor(Entity entity) {
        return MobTypeIds.entryFor(entity);
    }

    static String immunityFlagFor(DamageSource src) {
        if (src.is(DamageTypeTags.IS_FIRE)) return "FIRE_IMMUNE";
        if (src.is(DamageTypeTags.IS_FALL)) return "FALL_IMMUNE";
        if (src.is(DamageTypeTags.IS_DROWNING)) return "DROWN_IMMUNE";
        if (src.is(DamageTypeTags.IS_EXPLOSION)) return "EXPLOSION_IMMUNE";
        if (src.is(DamageTypeTags.IS_PROJECTILE)) return "PROJECTILE_IMMUNE";
        return null;
    }

    static boolean isImmune(Set<String> flags, DamageSource src) {
        if (flags == null || flags.isEmpty()) return false;
        String f = immunityFlagFor(src);
        return f != null && flags.contains(f);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity living = event.getEntity();
        if (living.level().isClientSide()) return;
        KubeJSMobExporter.MobEntry e = entryFor(living);
        if (e == null) return;
        if (isImmune(e.behaviorFlags, event.getSource())) {
            event.setCanceled(true);
            if (e.behaviorFlags.contains("FIRE_IMMUNE") && living.isOnFire()) living.clearFire();
        }
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        KubeJSMobExporter.MobEntry e = entryFor(event.getEntity());
        if (e != null && e.behaviorFlags.contains("NO_KNOCKBACK")) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        float amount = event.getAmount();
        KubeJSMobExporter.MobEntry victim = entryFor(event.getEntity());
        if (victim != null && !Double.isNaN(victim.damageTakenMult)) amount *= (float) victim.damageTakenMult;
        Entity attacker = event.getSource().getEntity();
        if (attacker == null && event.getSource().getDirectEntity() instanceof Projectile p) attacker = p.getOwner();
        KubeJSMobExporter.MobEntry dealer = attacker == null ? null : entryFor(attacker);
        if (dealer != null && !Double.isNaN(dealer.damageDealtMult)) amount *= (float) dealer.damageDealtMult;
        if (amount != event.getAmount()) event.setAmount(Math.max(0f, amount));
    }

    @SubscribeEvent
    public static void onXpDrop(LivingExperienceDropEvent event) {
        KubeJSMobExporter.MobEntry e = entryFor(event.getEntity());
        if (e != null && e.xpDrop >= 0) event.setDroppedExperience(e.xpDrop);
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        if (!KubeJSMobExporter.hasAppliedEntries()) return;
        LivingEntity living = event.getEntity();
        if (living.tickCount % 20 != 0 || living.level().isClientSide()) return;
        KubeJSMobExporter.MobEntry e = entryFor(living);
        if (e == null) return;
        if (!Double.isNaN(e.regenPerSecond) && e.regenPerSecond > 0 && living.isAlive()
                && living.getHealth() < living.getMaxHealth()) {
            living.heal((float) e.regenPerSecond);
        }
        if (e.behaviorFlags.contains("FIRE_IMMUNE") && living.isOnFire()) living.clearFire();
    }
}
