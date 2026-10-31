package com.kve.mixin;

import com.kve.common.mobrules.MobAttackSpeed;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Velocidad de ataque del Mob Editor para mobs cuerpo a cuerpo (ver
 * {@link MobAttackSpeed}). {@code getAttackInterval()} es el cooldown entre
 * golpes (20 ticks fijos en vanilla); verificado en el jar de Forge
 * 1.20.1-47.2.0: {@code protected int getAttackInterval()} y
 * {@code protected final PathfinderMob mob}.
 */
@Mixin(MeleeAttackGoal.class)
public abstract class MeleeAttackGoalMixin {

    @Shadow @Final protected PathfinderMob mob;

    @Inject(method = "getAttackInterval", at = @At("HEAD"), cancellable = true)
    private void kve$attackInterval(CallbackInfoReturnable<Integer> cir) {
        int t = MobAttackSpeed.configuredInterval(mob);
        if (t > 0) cir.setReturnValue(t);
    }
}
