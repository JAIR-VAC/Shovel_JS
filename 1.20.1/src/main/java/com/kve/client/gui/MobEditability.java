package com.kve.client.gui;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class MobEditability {

    private MobEditability() {}

    static boolean isEditableMob(ClientLevel level, EntityType<?> et) {
        try {
            return et.create(level) instanceof LivingEntity;
        } catch (Exception ex) {
            return true;
        }
    }
}
