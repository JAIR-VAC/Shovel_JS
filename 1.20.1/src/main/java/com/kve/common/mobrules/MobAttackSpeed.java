package com.kve.common.mobrules;

import com.kve.common.kubejs.KubeJSMobExporter;
import net.minecraft.world.entity.LivingEntity;

/**
 * Velocidad de ataque del Mob Editor. {@code generic.attack_speed} solo lo usa el jugador: la IA de
 * los mobs ataca con un intervalo fijo ({@code MeleeAttackGoal} = 20 ticks,
 * {@code RangedBowAttackGoal} = el que le pasa el mob), asi que poner el
 * atributo no cambiaba nada. Cuerpo a cuerpo: mixin de MeleeAttackGoal;
 * arco: {@link #applyToRangedGoals()} (API publica).
 */
public final class MobAttackSpeed {
    private MobAttackSpeed() {}

    /** Ataques por segundo -> ticks entre ataques (minimo 1); -1 si no hay valor. */
    public static int intervalTicks(double attacksPerSecond) {
        if (Double.isNaN(attacksPerSecond) || attacksPerSecond <= 0) return -1;
        return Math.max(1, (int) Math.round(20.0 / attacksPerSecond));
    }

    static void applyToRangedGoals(net.minecraft.world.entity.Mob mob, double attacksPerSecond) {
        int t = intervalTicks(attacksPerSecond);
        if (t <= 0) return;
        for (var wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof net.minecraft.world.entity.ai.goal.RangedBowAttackGoal<?> bow) bow.setMinAttackInterval(t);
        }
    }

    /** Intervalo configurado en el Mob Editor para este mob, o -1 si no tiene. */
    public static int configuredInterval(LivingEntity living) {
        KubeJSMobExporter.MobEntry e = MobTypeIds.entryFor(living);
        return e == null ? -1 : intervalTicks(e.attackSpeed);
    }
}
