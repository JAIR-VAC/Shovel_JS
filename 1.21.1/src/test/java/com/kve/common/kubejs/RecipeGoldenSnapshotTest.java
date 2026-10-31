package com.kve.common.kubejs;

import com.kve.common.recipe.RecipeData;
import com.kve.common.recipetype.KveAddonFlags;
import com.kve.common.recipetype.RecipeTypeDescriptorRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecipeGoldenSnapshotTest {

    @BeforeEach
    void simulateCreateAndMekanismLoaded() {
        RecipeTypeDescriptorRegistry.setModLoadedCheckForTests(Set.of("create", "mekanism")::contains);
    }

    @AfterEach
    void restoreRealModListCheck() {
        RecipeTypeDescriptorRegistry.setModLoadedCheckForTests(null);
    }

    @Test
    void createMixingUsesNativeJsonEvenWhenTheAddonFlagIsOn() {
        KveAddonFlags.setEnabled("kubejs-create", true);

        RecipeData r = new RecipeData();
        r.recipeId = "mix_slime";
        r.type = RecipeData.RecipeType.CUSTOM_JSON;
        r.customTypeId = "create:mixing";
        r.output = new ItemStack(Items.SLIME_BALL);
        r.shapelessIngredients.add(new ItemStack(Items.CLAY_BALL));
        r.shapelessIngredients.add(new ItemStack(Items.WATER_BUCKET));

        String js = KubeJSExporter.previewRecipeJS(r);
        assertTrue(js.contains("\"type\": \"create:mixing\""), js);
        assertTrue(js.contains("\"id\": \"minecraft:slime_ball\""), js);
        assertFalse(js.contains("event.recipes."), "no debe depender del addon:\n" + js);
    }

    @Test
    void createCrushingUsesNativeJsonEvenWhenTheAddonFlagIsOn() {
        KveAddonFlags.setEnabled("kubejs-create", true);

        RecipeData r = new RecipeData();
        r.recipeId = "crush_gravel";
        r.type = RecipeData.RecipeType.CUSTOM_JSON;
        r.customTypeId = "create:crushing";
        r.output = new ItemStack(Items.FLINT);
        r.shapelessIngredients.add(new ItemStack(Items.GRAVEL));

        String js = KubeJSExporter.previewRecipeJS(r);
        assertTrue(js.contains("\"type\": \"create:crushing\""), js);
        assertTrue(js.contains("\"processing_time\": 100"), js);
        assertTrue(js.contains("\"item\": \"minecraft:gravel\""), js);
        assertFalse(js.contains("event.recipes."), "no debe depender del addon:\n" + js);
    }

    @Test
    void createFillingComposesTheNativeFluidIngredientFromTwoExtraFields() {
        KveAddonFlags.setEnabled("kubejs-create", true);

        RecipeData r = new RecipeData();
        r.recipeId = "fill_bucket";
        r.type = RecipeData.RecipeType.CUSTOM_JSON;
        r.customTypeId = "create:filling";
        r.output = new ItemStack(Items.LAVA_BUCKET);
        r.shapelessIngredients.add(new ItemStack(Items.BUCKET));
        r.customExtraJson = "\"fluidId\": \"minecraft:lava\", \"fluidAmount\": 1000";

        String js = KubeJSExporter.previewRecipeJS(r);
        assertTrue(js.contains("\"fluid\": \"minecraft:lava\""), js);
        assertTrue(js.contains("\"amount\": 1000"), js);
        assertFalse(js.contains("event.recipes."), "no debe depender del addon:\n" + js);
    }

    @Test
    void createFillingFallsBackToRawJsonWhenTheRequiredExtraFieldIsMissing() {
        KveAddonFlags.setEnabled("kubejs-create", true);

        RecipeData r = new RecipeData();
        r.recipeId = "fill_bucket_no_fluid";
        r.type = RecipeData.RecipeType.CUSTOM_JSON;
        r.customTypeId = "create:filling";
        r.output = new ItemStack(Items.LAVA_BUCKET);
        r.shapelessIngredients.add(new ItemStack(Items.BUCKET));
        // No customExtraJson set at all - {field:fluidId} has nothing to
        // substitute, so the dedicated call must not fire (never emit a
        // call with a missing argument).

        String js = KubeJSExporter.previewRecipeJS(r);

        assertTrue(js.contains("event.custom({"),
                "with no fluidId/fluidAmount set, the generic raw-JSON call should be used:\n" + js);
        assertFalse(js.contains("event.recipes.createFilling"));
    }

    @Test
    void createHauntingAlwaysUsesRawJsonSinceKubeJSCreateHasNoBuilderForIt() {
        // Regression guard: an earlier version of this descriptor
        // (incorrectly) claimed a "event.recipes.create.haunting(...)"
        // dedicated call existed. KubeJS Create's own supported-types list
        // never included Haunting, so that call would have failed at
        // script-load time in-game. Even with the addon flag on, this must
        // always fall back to raw JSON.
        KveAddonFlags.setEnabled("kubejs-create", true);

        RecipeData r = new RecipeData();
        r.recipeId = "haunt_test";
        r.type = RecipeData.RecipeType.CUSTOM_JSON;
        r.customTypeId = "create:haunting";
        r.output = new ItemStack(Items.SOUL_SAND);
        r.shapelessIngredients.add(new ItemStack(Items.SAND));

        String js = KubeJSExporter.previewRecipeJS(r);
        assertTrue(js.contains("event.custom({"), js);
        assertTrue(js.contains("\"type\": \"create:haunting\""), js);
        assertTrue(js.contains("\"id\": \"minecraft:soul_sand\""), js);
        assertFalse(js.contains("event.recipes.create"), "no debe depender del addon:\n" + js);
    }

    @Test
    void mekanismCrushingUsesNativeJsonEvenWhenTheAddonFlagIsOn() {
        KveAddonFlags.setEnabled("kubejs-mekanism", true);

        RecipeData r = new RecipeData();
        r.recipeId = "crush_raw_iron";
        r.type = RecipeData.RecipeType.CUSTOM_JSON;
        r.customTypeId = "mekanism:crushing";
        r.output = new ItemStack(Items.IRON_INGOT);
        r.shapelessIngredients.add(new ItemStack(Items.RAW_IRON));

        String js = KubeJSExporter.previewRecipeJS(r);
        assertTrue(js.contains("\"type\": \"mekanism:crushing\""), js);
        assertTrue(js.contains("\"item\": \"minecraft:raw_iron\""), js);
        assertFalse(js.contains("event.recipes.mekanism"), "no debe depender del addon:\n" + js);
    }

    @Test
    void mekanismCrushingFallsBackToRawJsonWhenTheAddonFlagIsOff() {
        KveAddonFlags.setEnabled("kubejs-mekanism", false);

        RecipeData r = new RecipeData();
        r.recipeId = "crush_raw_gold";
        r.type = RecipeData.RecipeType.CUSTOM_JSON;
        r.customTypeId = "mekanism:crushing";
        r.output = new ItemStack(Items.GOLD_INGOT);
        r.shapelessIngredients.add(new ItemStack(Items.RAW_GOLD));

        String js = KubeJSExporter.previewRecipeJS(r);

        assertTrue(js.contains("event.custom({"),
                "with the addon flag off, a raw-JSON call should be used:\n" + js);
        assertTrue(js.contains("\"type\": \"mekanism:crushing\""), js);
        assertTrue(js.contains("\"input\": {\n      \"count\": 1,\n      \"item\": \"minecraft:raw_gold\""), js);
        assertTrue(js.contains("\"output\": {\n      \"id\": \"minecraft:gold_ingot\""), js);
        assertTrue(js.contains(".id('shovel_js:crush_raw_gold');"), js);
        assertFalse(js.contains("event.recipes.mekanism.crushing"));
    }

    @Test
    void mekanismCrushingFallsBackToRawJsonWhenMekanismItselfIsntLoadedEvenIfTheAddonFlagIsOn() {
        RecipeTypeDescriptorRegistry.setModLoadedCheckForTests(modid -> false);
        KveAddonFlags.setEnabled("kubejs-mekanism", true);

        RecipeData r = new RecipeData();
        r.recipeId = "crush_raw_copper";
        r.type = RecipeData.RecipeType.CUSTOM_JSON;
        r.customTypeId = "mekanism:crushing";
        r.output = new ItemStack(Items.COPPER_INGOT);
        r.shapelessIngredients.add(new ItemStack(Items.RAW_COPPER));

        String js = KubeJSExporter.previewRecipeJS(r);

        assertTrue(js.contains("event.custom({"),
                "with the base mod not loaded, raw JSON should be used even though the addon flag is on:\n" + js);
        assertFalse(js.contains("event.recipes.mekanism.crushing"));
    }

    @Test
    void garnishedDyeBlowingExportsNativeJsonWithoutADuration() {
        KveAddonFlags.setEnabled("kubejs-garnished", true);
        RecipeTypeDescriptorRegistry.setModLoadedCheckForTests(m -> m.equals("garnished"));

        RecipeData r = new RecipeData();
        r.recipeId = "ruby_from_emerald";
        r.type = RecipeData.RecipeType.CUSTOM_JSON;
        r.customTypeId = "garnished:red_dye_blowing";
        r.output = new ItemStack(Items.REDSTONE);
        r.shapelessIngredients.add(new ItemStack(Items.EMERALD));

        String js = KubeJSExporter.previewRecipeJS(r);

        assertTrue(js.contains("\"item\": \"minecraft:emerald\""), js);
        assertTrue(js.contains("\"id\": \"minecraft:redstone\""), js);
        assertFalse(js.contains("processingTime"), js);
        assertFalse(js.contains("event.recipes.garnished"), js);
    }

    @Test
    void immersiveEngineeringSawmillExportsNativeJsonEvenWithTheAddon() {
        KveAddonFlags.setEnabled("kubejs-immersiveengineering", true);
        RecipeTypeDescriptorRegistry.setModLoadedCheckForTests(m -> m.equals("immersiveengineering"));

        RecipeData r = new RecipeData();
        r.recipeId = "plank_test";
        r.type = RecipeData.RecipeType.CUSTOM_JSON;
        r.customTypeId = "immersiveengineering:sawmill";
        r.output = new ItemStack(Items.OAK_PLANKS);
        r.shapelessIngredients.add(new ItemStack(Items.OAK_LOG));

        String js = KubeJSExporter.previewRecipeJS(r);

        assertTrue(js.contains("\"type\": \"immersiveengineering:sawmill\""), js);
        assertTrue(js.contains("\"item\": \"minecraft:oak_log\""), js);
        assertTrue(js.contains("\"id\": \"minecraft:oak_planks\""), js);
        assertTrue(js.contains("\"energy\": 2000"), js);
        assertFalse(js.contains("event.recipes.immersiveengineering"), "native JSON must win over the addon call:\n" + js);
    }

    @Test
    void bloodMagicArcFallsBackToRawJsonWhenTheRequiredToolFieldIsBlank() {
        // Hardening guard for the {field:KEY} substitution itself: "tool"
        // is required, but the catalog's own UI-prefilled default for it
        // is "" (blank) - a present-but-empty key must be treated the same
        // as a missing one, or this would silently emit tool: ''.
        KveAddonFlags.setEnabled("kubejs-blood-magic", true);
        RecipeTypeDescriptorRegistry.setModLoadedCheckForTests(m -> m.equals("bloodmagic"));

        RecipeData r = new RecipeData();
        r.recipeId = "arc_test";
        r.type = RecipeData.RecipeType.CUSTOM_JSON;
        r.customTypeId = "bloodmagic:arc";
        r.output = new ItemStack(Items.NETHERITE_INGOT);
        r.shapelessIngredients.add(new ItemStack(Items.IRON_INGOT));
        r.customExtraJson = "\"tool\": \"\", \"consumeIngredient\": true";

        String js = KubeJSExporter.previewRecipeJS(r);

        assertTrue(js.contains("event.custom({"),
                "a blank required 'tool' must fall back to raw JSON, not emit tool: '':\n" + js);
        assertFalse(js.contains("event.recipes.bloodmagic.arc"));
    }

    @Test
    void bloodMagicArcExportsNativeJsonEvenWithTheAddonAndAFilledTool() {
        KveAddonFlags.setEnabled("kubejs-blood-magic", true);
        RecipeTypeDescriptorRegistry.setModLoadedCheckForTests(m -> m.equals("bloodmagic"));

        RecipeData r = new RecipeData();
        r.recipeId = "arc_test_2";
        r.type = RecipeData.RecipeType.CUSTOM_JSON;
        r.customTypeId = "bloodmagic:arc";
        r.output = new ItemStack(Items.NETHERITE_INGOT);
        r.shapelessIngredients.add(new ItemStack(Items.IRON_INGOT));
        r.customExtraJson = "\"tool\": \"minecraft:iron_pickaxe\", \"consumeIngredient\": true";

        String js = KubeJSExporter.previewRecipeJS(r);

        assertTrue(js.contains("event.custom({"), js);
        assertFalse(js.contains("event.recipes.bloodmagic.arc"), js);
        assertTrue(js.contains("\"item\": \"minecraft:iron_pickaxe\""), "tool tomado del campo viejo:\n" + js);
        assertTrue(js.contains("\"consumeingredient\": true"), js);
    }

    @Test
    void mekanismCentrifugingUsesNativeGasJsonEvenWithTheAddonOn() {
        KveAddonFlags.setEnabled("kubejs-more-recipe-types", true);
        RecipeTypeDescriptorRegistry.setModLoadedCheckForTests(m -> m.equals("mekanism"));

        RecipeData r = new RecipeData();
        r.recipeId = "centrifuge_test";
        r.type = RecipeData.RecipeType.CUSTOM_MACHINE;
        r.customTypeId = "mekanism:centrifuging";

        RecipeData.MachineSlot gasIn = new RecipeData.MachineSlot();
        gasIn.type = RecipeData.MachineSlotType.FLUID;
        gasIn.fluidId = "mekanism:lithium";
        gasIn.fluidAmount = 100;
        r.machineInputs.add(gasIn);

        RecipeData.MachineSlot gasOut = new RecipeData.MachineSlot();
        gasOut.type = RecipeData.MachineSlotType.FLUID;
        gasOut.fluidId = "mekanism:deuterium";
        gasOut.fluidAmount = 100;
        r.machineOutputs.add(gasOut);

        String js = KubeJSExporter.previewRecipeJS(r);
        assertTrue(js.contains("\"type\": \"mekanism:centrifuging\""), js);
        assertTrue(js.contains("\"chemical\": \"mekanism:lithium\""), js);
        assertTrue(js.contains("\"id\": \"mekanism:deuterium\""), js);
        assertFalse(js.contains("global.mrt"), "no debe depender del addon:\n" + js);
    }

    @Test
    void mekanismWashingPutsEachFluidInItsOwnNativeKey() {
        // Regression guard for the Nth-valid-fluid-slot ordinal: washing
        // has TWO fluid inputs (clean fluid + dirty slurry) that must land
        // in the right argument slot, not just "first valid slot" reused
        // twice.
        KveAddonFlags.setEnabled("kubejs-more-recipe-types", true);
        RecipeTypeDescriptorRegistry.setModLoadedCheckForTests(m -> m.equals("mekanism"));

        RecipeData r = new RecipeData();
        r.recipeId = "washing_test";
        r.type = RecipeData.RecipeType.CUSTOM_MACHINE;
        r.customTypeId = "mekanism:washing";

        RecipeData.MachineSlot fluidIn = new RecipeData.MachineSlot();
        fluidIn.type = RecipeData.MachineSlotType.FLUID;
        fluidIn.fluidId = "minecraft:water";
        fluidIn.fluidAmount = 5;
        r.machineInputs.add(fluidIn);

        RecipeData.MachineSlot dirtySlurry = new RecipeData.MachineSlot();
        dirtySlurry.type = RecipeData.MachineSlotType.FLUID;
        dirtySlurry.fluidId = "mekanism:dirty_slurry";
        dirtySlurry.fluidAmount = 1;
        r.machineInputs.add(dirtySlurry);

        RecipeData.MachineSlot cleanSlurry = new RecipeData.MachineSlot();
        cleanSlurry.type = RecipeData.MachineSlotType.FLUID;
        cleanSlurry.fluidId = "mekanism:clean_slurry";
        cleanSlurry.fluidAmount = 1;
        r.machineOutputs.add(cleanSlurry);

        String js = KubeJSExporter.previewRecipeJS(r);
        assertTrue(js.contains("\"fluid_input\": {"), js);
        assertTrue(js.contains("\"fluid\": \"minecraft:water\""), js);
        assertTrue(js.contains("\"chemical\": \"mekanism:dirty_slurry\""), js);
        assertTrue(js.contains("\"id\": \"mekanism:clean_slurry\""), js);
        assertFalse(js.contains("global.mrt"), "no debe depender del addon:\n" + js);
    }

    @Test
    void mekanismReactionMixesItemFluidAndGasInItsNativeKeys() {
        KveAddonFlags.setEnabled("kubejs-more-recipe-types", true);
        RecipeTypeDescriptorRegistry.setModLoadedCheckForTests(m -> m.equals("mekanism"));

        RecipeData r = new RecipeData();
        r.recipeId = "reaction_test";
        r.type = RecipeData.RecipeType.CUSTOM_MACHINE;
        r.customTypeId = "mekanism:reaction";
        r.customExtraJson = "\"processingTicks\": 100, \"energyPerTick\": 10";

        RecipeData.MachineSlot itemIn = new RecipeData.MachineSlot();
        itemIn.type = RecipeData.MachineSlotType.ITEM;
        itemIn.item = new ItemStack(Items.REDSTONE);
        r.machineInputs.add(itemIn);

        RecipeData.MachineSlot fluidIn = new RecipeData.MachineSlot();
        fluidIn.type = RecipeData.MachineSlotType.FLUID;
        fluidIn.fluidId = "minecraft:water";
        fluidIn.fluidAmount = 10;
        r.machineInputs.add(fluidIn);

        RecipeData.MachineSlot gasIn = new RecipeData.MachineSlot();
        gasIn.type = RecipeData.MachineSlotType.FLUID;
        gasIn.fluidId = "mekanism:hydrogen_chloride";
        gasIn.fluidAmount = 1;
        r.machineInputs.add(gasIn);

        RecipeData.MachineSlot itemOut = new RecipeData.MachineSlot();
        itemOut.type = RecipeData.MachineSlotType.ITEM;
        itemOut.item = new ItemStack(Items.GLOWSTONE_DUST);
        r.machineOutputs.add(itemOut);

        RecipeData.MachineSlot gasOut = new RecipeData.MachineSlot();
        gasOut.type = RecipeData.MachineSlotType.FLUID;
        gasOut.fluidId = "mekanism:sodium";
        gasOut.fluidAmount = 1;
        r.machineOutputs.add(gasOut);

        String js = KubeJSExporter.previewRecipeJS(r);
        assertTrue(js.contains("\"item_input\": {"), js);
        assertTrue(js.contains("\"chemical_input\": {"), js);
        assertTrue(js.contains("\"item_output\": {"), js);
        assertTrue(js.contains("\"chemical_output\": {"), js);
        assertFalse(js.contains("global.mrt"), "no debe depender del addon:\n" + js);
    }

    private static String itemToJsForTest(ItemStack s) {
        return "{ item: '" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()) + "' }";
    }
}
