package com.kve.common.mobrules;

/**
 * One "attack effect" entry - the effect applied to whatever a configured
 * mob hits in melee. Deliberately field-shaped the same as
 * {@code KubeJSItemAttrExporter.FoodEffect} (effectId/durationTicks/
 * amplifier/chance) so {@code KubeJSMobExporter} can build one of these
 * straight from a {@code MobEntry#attackEffects} entry without any field
 * remapping.
 */
public final class AttackEffectRule {

    public final String effectId;
    public final int durationTicks;
    public final int amplifier;
    public final float chance;

    public AttackEffectRule(String effectId, int durationTicks, int amplifier, float chance) {
        this.effectId = effectId;
        this.durationTicks = durationTicks;
        this.amplifier = amplifier;
        this.chance = chance;
    }
}
