package com.kve.common.worldgen;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorldGenTestFeaturesTest {

    private static WorldGenEntry rule() {
        WorldGenEntry e = new WorldGenEntry();
        e.id = "shovel_js:test_rule";
        e.biomeIds.add("#minecraft:is_overworld");
        WorldGenEntry.OreVein v = new WorldGenEntry.OreVein();
        v.veinId = "v1";
        v.oreBlock = "minecraft:diamond_ore";
        v.deepslateOreBlock = "minecraft:deepslate_diamond_ore";
        v.rarity = 9;
        e.oreVeins.add(v);
        WorldGenEntry.Patch p = new WorldGenEntry.Patch();
        p.patchId = "p1";
        p.block = "minecraft:poppy";
        e.patches.add(p);
        return e;
    }

    @Test
    void testFeaturesDecodeWithTheRealCodec() {
        List<WorldGenExporter.TestFeature> features = WorldGenExporter.testFeatures(rule());
        assertEquals(2, features.size());
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, VanillaRegistries.createLookup());
        for (WorldGenExporter.TestFeature f : features) {
            var parsed = PlacedFeature.DIRECT_CODEC.parse(ops, f.placed());
            assertTrue(parsed.error().isEmpty(), f.label() + ": " + parsed.error().map(Object::toString).orElse("") + "\n" + f.placed());
        }
    }

    @Test
    void biomeFilterAndRarityAreStrippedForTheTest() {
        for (WorldGenExporter.TestFeature f : WorldGenExporter.testFeatures(rule())) {
            String json = f.placed().toString();
            assertFalse(json.contains("minecraft:biome\""), "el chunk del jugador puede no ser de los biomas de la regla: " + json);
            assertFalse(json.contains("rarity_filter"), "una veta rara casi nunca saldria en la prueba: " + json);
            assertTrue(f.placed().get("feature").isJsonObject(), "el configured feature va dentro: no esta en el registro todavia");
        }
    }

    @Test
    void ruleWithOnlyBiomeExtrasHasNothingToTest() {
        WorldGenEntry e = new WorldGenEntry();
        e.id = "shovel_js:only_spawns";
        e.removedFeatures.add("minecraft:ore_diamond");
        assertTrue(WorldGenExporter.testFeatures(e).isEmpty());
    }
}
