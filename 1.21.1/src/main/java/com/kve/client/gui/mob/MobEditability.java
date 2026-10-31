package com.kve.client.gui.mob;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class MobEditability {

    private MobEditability() {}

    public static boolean isEditableMob(ClientLevel level, EntityType<?> et) {
        try {
            return et.create(level) instanceof LivingEntity;
        } catch (Exception ex) {
            return true;
        }
    }
}
