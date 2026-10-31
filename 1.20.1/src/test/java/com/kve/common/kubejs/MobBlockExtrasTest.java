package com.kve.common.kubejs;

import net.minecraft.world.level.block.SoundType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class MobBlockExtrasTest {

    @Test
    void everyOfferedSoundTypeIsARealSoundTypeFieldSoKubeJsDoesNotSilentlyMuteTheBlock() throws Exception {
        for (String name : KubeJSBlockAttrExporter.SOUND_TYPES) {
            Field f = SoundType.class.getField(name.toUpperCase(Locale.ROOT));
            assertTrue(Modifier.isStatic(f.getModifiers()) && f.getType() == SoundType.class, name);
        }
    }

    @Test
    void physicsFieldsExportAsExplicitSetterCallsAndOnlyWhenSet() {
        KubeJSBlockAttrExporter.BlockEntry e = new KubeJSBlockAttrExporter.BlockEntry();
        e.blockId = "minecraft:stone";
        String none = KubeJSBlockAttrExporter.previewBlockJS(e);
        assertFalse(none.contains("setSpeedFactor") || none.contains("setSoundType") || none.contains("setHasCollision"));

        e.speedFactor = 0.4; e.jumpFactor = 0.5; e.soundType = "wool"; e.hasCollision = false; e.randomlyTicking = true;
        String js = KubeJSBlockAttrExporter.previewBlockJS(e);
        assertTrue(js.contains("block.setSpeedFactor(0.4)"), js);
        assertTrue(js.contains("block.setJumpFactor(0.5)"), js);
        assertTrue(js.contains("block.setSoundType('wool')"), js);
        assertTrue(js.contains("block.setHasCollision(false)"), js);
        assertTrue(js.contains("block.setIsRandomlyTicking(true)"), js);
        assertTrue(e.hasPhysicsTweaks());
    }

    @Test
    void physicsFieldsSurviveCopyAndAreValidated() {
        KubeJSBlockAttrExporter.BlockEntry e = new KubeJSBlockAttrExporter.BlockEntry();
        e.blockId = "minecraft:stone";
        e.speedFactor = 0.4; e.soundType = "glass"; e.hasCollision = false;
        KubeJSBlockAttrExporter.BlockEntry c = e.copy();
        assertEquals(0.4, c.speedFactor);
        assertEquals("glass", c.soundType);
        assertEquals(Boolean.FALSE, c.hasCollision);
        assertNull(c.randomlyTicking);

        e.soundType = "not_a_sound";
        e.jumpFactor = 9;
        var errs = KubeJSBlockAttrExporter.validateBlock(e);
        assertTrue(errs.stream().anyMatch(s -> s.contains("soundType")), errs.toString());
        assertTrue(errs.stream().anyMatch(s -> s.contains("jumpFactor")), errs.toString());
    }

    @Test
    void combatFieldsSurviveCopyAndAreRangeChecked() {
        KubeJSMobExporter.MobEntry e = new KubeJSMobExporter.MobEntry();
        e.entityId = "minecraft:zombie";
        assertFalse(e.hasCombatTweaks());
        e.xpDrop = 50; e.regenPerSecond = 1; e.damageTakenMult = 0.5; e.damageDealtMult = 2;
        KubeJSMobExporter.MobEntry c = e.copy();
        assertEquals(50, c.xpDrop);
        assertEquals(1, c.regenPerSecond);
        assertEquals(0.5, c.damageTakenMult);
        assertEquals(2, c.damageDealtMult);
        assertTrue(KubeJSMobExporter.validateCombat(e).isEmpty());

        e.damageTakenMult = -1; e.regenPerSecond = 5000;
        assertEquals(2, KubeJSMobExporter.validateCombat(e).size());
    }

    @Test
    void newImmunityFlagsAreOfferedAndAttitudeFlagsAreExclusive() {
        var flags = java.util.List.of(KubeJSMobExporter.BEHAVIOR_FLAGS);
        for (String f : new String[]{"FIRE_IMMUNE", "FALL_IMMUNE", "DROWN_IMMUNE", "EXPLOSION_IMMUNE", "PROJECTILE_IMMUNE", "NO_KNOCKBACK"}) {
            assertTrue(flags.contains(f), f);
        }
        assertEquals(java.util.Set.of("HOSTILE", "NEUTRAL", "PASSIVE"), KubeJSMobExporter.EXCLUSIVE_ATTITUDE_FLAGS);
    }
}
