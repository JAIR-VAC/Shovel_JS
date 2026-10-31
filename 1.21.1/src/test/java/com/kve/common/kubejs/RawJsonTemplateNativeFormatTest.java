package com.kve.common.kubejs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kve.common.recipe.RecipeData;
import com.kve.common.recipetype.RecipeTypeDescriptorRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RawJsonTemplateNativeFormatTest {

    @BeforeEach
    void noModsLoaded() {
        RecipeTypeDescriptorRegistry.setModLoadedCheckForTests(m -> false);
        RecipeTypeDescriptorRegistry.reload();
    }

    @AfterEach
    void restore() {
        RecipeTypeDescriptorRegistry.setModLoadedCheckForTests(null);
    }

    private static RecipeData machine(String type) {
        RecipeData r = new RecipeData();
        r.recipeId = "native_test";
        r.type = RecipeData.RecipeType.CUSTOM_MACHINE;
        r.customTypeId = type;
        return r;
    }

    private static RecipeData.MachineSlot item(ItemStack s) {
        RecipeData.MachineSlot m = new RecipeData.MachineSlot();
        m.item = s;
        return m;
    }

    private static RecipeData.MachineSlot tag(String id, int count) {
        RecipeData.MachineSlot m = new RecipeData.MachineSlot();
        m.type = RecipeData.MachineSlotType.TAG;
        m.tagId = id;
        m.tagCount = count;
        return m;
    }

    private static RecipeData.MachineSlot fluid(String id, int amount) {
        RecipeData.MachineSlot m = new RecipeData.MachineSlot();
        m.type = RecipeData.MachineSlotType.FLUID;
        m.fluidId = id;
        m.fluidAmount = amount;
        return m;
    }

    private static RecipeData.MachineSlot gap() {
        return new RecipeData.MachineSlot();
    }

    private static JsonObject exported(RecipeData r) {
        String js = KubeJSExporter.previewRecipeJS(r);
        int start = js.indexOf("event.custom(");
        int end = js.lastIndexOf(").id('shovel_js:");
        assertTrue(start >= 0 && end > start, "no event.custom(...) in:\n" + js);
        assertTrue(js.contains("JSON nativo"), "expected the native-template path, got:\n" + js);
        return JsonParser.parseString(js.substring(start + "event.custom(".length(), end)).getAsJsonObject();
    }

    private static void assertNative(String realJarJson, JsonObject actual) {
        JsonObject expected = JsonParser.parseString(realJarJson).getAsJsonObject();
        assertEquals(expected, actual, "\nexpected (jar): " + expected + "\nactual:         " + actual);
    }

    private static void assertNativeSubset(String realJarJson, JsonObject actual) {
        JsonObject expected = JsonParser.parseString(realJarJson).getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : expected.entrySet()) {
            assertEquals(e.getValue(), actual.get(e.getKey()), "key '" + e.getKey() + "'\nexpected (jar): " + expected + "\nactual:         " + actual);
        }
    }

    @Test
    void mekanismCrushingMatchesJar() {
        RecipeData r = machine("mekanism:crushing");
        r.machineInputs.add(item(new ItemStack(Items.MUSIC_DISC_5)));
        r.machineOutputs.add(item(new ItemStack(Items.DISC_FRAGMENT_5, 9)));
        assertNative("{\"type\":\"mekanism:crushing\",\"input\":{\"count\":1,\"item\":\"minecraft:music_disc_5\"},\"output\":{\"id\":\"minecraft:disc_fragment_5\",\"count\":9}}", exported(r));
    }

    @Test
    void mekanismCombiningMatchesJar() {
        RecipeData r = machine("mekanism:combining");
        r.machineInputs.add(tag("c:dyes/black", 1));
        r.machineInputs.add(tag("c:dyes/white", 2));
        r.machineOutputs.add(item(new ItemStack(Items.LIGHT_GRAY_DYE, 6)));
        assertNative("{\"type\":\"mekanism:combining\",\"main_input\":{\"count\":1,\"tag\":\"c:dyes/black\"},\"extra_input\":{\"count\":2,\"tag\":\"c:dyes/white\"},\"output\":{\"id\":\"minecraft:light_gray_dye\",\"count\":6}}", exported(r));
    }

    @Test
    void mekanismInjectingGasTagFromFieldTankMatchesJar() {
        RecipeData r = machine("mekanism:injecting");
        r.machineInputs.add(tag("c:ingots/brick", 1));
        r.machineOutputs.add(item(new ItemStack(Items.CLAY_BALL)));
        r.customExtraJson = "\"inputGas\": \"#mekanism:water_vapor\", \"inputGasAmount\": 1";
        assertNative("{\"type\":\"mekanism:injecting\",\"chemical_input\":{\"amount\":1,\"tag\":\"mekanism:water_vapor\"},\"item_input\":{\"count\":1,\"tag\":\"c:ingots/brick\"},\"output\":{\"id\":\"minecraft:clay_ball\",\"count\":1},\"per_tick_usage\":true}", exported(r));
    }

    @Test
    void mekanismPurifyingGasIdMatchesJar() {
        RecipeData r = machine("mekanism:purifying");
        r.machineInputs.add(tag("c:ores/copper", 1));
        r.machineOutputs.add(item(new ItemStack(Items.RAW_COPPER, 3)));
        r.customExtraJson = "\"inputGas\": \"mekanism:oxygen\", \"inputGasAmount\": 1";
        assertNative("{\"type\":\"mekanism:purifying\",\"chemical_input\":{\"amount\":1,\"chemical\":\"mekanism:oxygen\"},\"item_input\":{\"count\":1,\"tag\":\"c:ores/copper\"},\"output\":{\"id\":\"minecraft:raw_copper\",\"count\":3},\"per_tick_usage\":true}", exported(r));
    }

    @Test
    void mekanismMetallurgicInfusingMatchesJar() {
        RecipeData r = machine("mekanism:metallurgic_infusing");
        r.machineInputs.add(item(new ItemStack(Items.BLACKSTONE)));
        r.machineOutputs.add(item(new ItemStack(Items.GILDED_BLACKSTONE)));
        r.customExtraJson = "\"infusionInput\": \"#mekanism:gold\", \"infusionAmount\": 100";
        assertNative("{\"type\":\"mekanism:metallurgic_infusing\",\"chemical_input\":{\"amount\":100,\"tag\":\"mekanism:gold\"},\"item_input\":{\"count\":1,\"item\":\"minecraft:blackstone\"},\"output\":{\"id\":\"minecraft:gilded_blackstone\",\"count\":1},\"per_tick_usage\":false}", exported(r));
    }

    @Test
    void mekanismChemicalInfusingMatchesJar() {
        RecipeData r = machine("mekanism:chemical_infusing");
        r.machineInputs.add(fluid("mekanism:hydrogen", 1));
        r.machineInputs.add(fluid("mekanism:chlorine", 1));
        r.machineOutputs.add(fluid("mekanism:hydrogen_chloride", 1));
        assertNative("{\"type\":\"mekanism:chemical_infusing\",\"left_input\":{\"amount\":1,\"chemical\":\"mekanism:hydrogen\"},\"right_input\":{\"amount\":1,\"chemical\":\"mekanism:chlorine\"},\"output\":{\"amount\":1,\"id\":\"mekanism:hydrogen_chloride\"}}", exported(r));
    }

    @Test
    void mekanismDissolutionBoxedSlurryOutputMatchesJar() {
        RecipeData r = machine("mekanism:dissolution");
        r.machineInputs.add(tag("c:ores/copper", 1));
        r.machineInputs.add(fluid("mekanism:sulfuric_acid", 1));
        r.machineOutputs.add(fluid("mekanism:dirty_copper", 1000));
        assertNative("{\"type\":\"mekanism:dissolution\",\"item_input\":{\"count\":1,\"tag\":\"c:ores/copper\"},\"chemical_input\":{\"amount\":1,\"chemical\":\"mekanism:sulfuric_acid\"},\"output\":{\"amount\":1000,\"id\":\"mekanism:dirty_copper\"},\"per_tick_usage\":true}", exported(r));
    }

    @Test
    void mekanismCrystallizingSlurryMatchesJar() {
        RecipeData r = machine("mekanism:crystallizing");
        r.machineInputs.add(fluid("mekanism:clean_copper", 200));
        r.machineOutputs.add(item(new ItemStack(Items.AMETHYST_SHARD)));
        assertNative("{\"type\":\"mekanism:crystallizing\",\"input\":{\"amount\":200,\"chemical\":\"mekanism:clean_copper\"},\"output\":{\"id\":\"minecraft:amethyst_shard\",\"count\":1}}", exported(r));
    }

    @Test
    void mekanismRotaryBothDirectionsMatchesJar() {
        RecipeData r = machine("mekanism:rotary");
        r.machineInputs.add(fluid("#c:brine", 1));
        r.machineInputs.add(fluid("mekanism:brine", 1));
        r.machineOutputs.add(fluid("mekanism:brine", 1));
        r.machineOutputs.add(fluid("mekanism:brine", 1));
        assertNative("{\"type\":\"mekanism:rotary\",\"fluid_input\":{\"amount\":1,\"tag\":\"c:brine\"},\"chemical_output\":{\"amount\":1,\"id\":\"mekanism:brine\"},\"chemical_input\":{\"amount\":1,\"chemical\":\"mekanism:brine\"},\"fluid_output\":{\"amount\":1,\"id\":\"mekanism:brine\"}}", exported(r));
    }

    @Test
    void mekanismSeparatingMatchesJar() {
        RecipeData r = machine("mekanism:separating");
        r.machineInputs.add(fluid("#c:brine", 10));
        r.machineOutputs.add(fluid("mekanism:sodium", 1));
        r.machineOutputs.add(fluid("mekanism:chlorine", 1));
        r.customExtraJson = "\"energyMultiplier\": \"\"";
        assertNative("{\"type\":\"mekanism:separating\",\"input\":{\"amount\":10,\"tag\":\"c:brine\"},\"left_chemical_output\":{\"amount\":1,\"id\":\"mekanism:sodium\"},\"right_chemical_output\":{\"amount\":1,\"id\":\"mekanism:chlorine\"}}", exported(r));
    }

    @Test
    void mekanismReactionMatchesJar() {
        RecipeData r = machine("mekanism:reaction");
        r.machineInputs.add(tag("c:dusts/fluorite", 1));
        r.machineInputs.add(fluid("#minecraft:water", 1000));
        r.machineInputs.add(fluid("mekanism:plutonium", 1000));
        r.machineOutputs.add(item(new ItemStack(Items.NETHER_STAR)));
        r.machineOutputs.add(fluid("mekanism:spent_nuclear_waste", 1000));
        r.customExtraJson = "\"processingTicks\": 100, \"energyPerTick\": 5";
        JsonObject out = exported(r);
        assertNativeSubset("{\"type\":\"mekanism:reaction\",\"item_input\":{\"count\":1,\"tag\":\"c:dusts/fluorite\"},\"fluid_input\":{\"amount\":1000,\"tag\":\"minecraft:water\"},\"chemical_input\":{\"amount\":1000,\"chemical\":\"mekanism:plutonium\"},\"energy_required\":5,\"duration\":100,\"item_output\":{\"id\":\"minecraft:nether_star\",\"count\":1},\"chemical_output\":{\"amount\":1000,\"id\":\"mekanism:spent_nuclear_waste\"}}", out);
        assertEquals(5, out.get("energy_required").getAsInt());
    }

    @Test
    void mekanismSawingSecondaryOutputUsesSlotChance() {
        // data/mekanism/recipe/compat/biomesoplenty/sawing/boat/dead.json shape + JsonConstants secondaryOutput/secondaryChance
        RecipeData r = machine("mekanism:sawing");
        r.machineInputs.add(item(new ItemStack(Items.OAK_BOAT)));
        r.machineOutputs.add(item(new ItemStack(Items.OAK_PLANKS, 5)));
        assertNative("{\"type\":\"mekanism:sawing\",\"input\":{\"count\":1,\"item\":\"minecraft:oak_boat\"},\"main_output\":{\"id\":\"minecraft:oak_planks\",\"count\":5}}", exported(r));

        RecipeData.MachineSlot sawdust = item(new ItemStack(Items.STICK));
        sawdust.chance = 0.25;
        r.machineOutputs.add(sawdust);
        JsonObject out = exported(r);
        assertEquals(JsonParser.parseString("{\"count\":1,\"id\":\"minecraft:stick\"}"), out.get("secondary_output"));
        assertEquals(0.25, out.get("secondary_chance").getAsDouble(), 1e-9);
    }

    @Test
    void mekanismEvaporatingMatchesJar() {
        RecipeData r = machine("mekanism:evaporating");
        r.machineInputs.add(fluid("#minecraft:water", 10));
        r.machineOutputs.add(fluid("mekanism:brine", 1));
        assertNative("{\"type\":\"mekanism:evaporating\",\"input\":{\"amount\":10,\"tag\":\"minecraft:water\"},\"output\":{\"amount\":1,\"id\":\"mekanism:brine\"}}", exported(r));
    }

    @Test
    void mekanismEnergyConversionFromPlainGridMatchesJar() {
        RecipeData r = new RecipeData();
        r.recipeId = "native_test";
        r.type = RecipeData.RecipeType.CUSTOM_JSON;
        r.customTypeId = "mekanism:energy_conversion";
        r.shapelessIngredients.add(new ItemStack(Items.REDSTONE));
        r.customExtraJson = "\"energyAmount\": 10000";
        assertNative("{\"type\":\"mekanism:energy_conversion\",\"input\":{\"count\":1,\"item\":\"minecraft:redstone\"},\"output\":10000}", exported(r));
    }

    @Test
    void powahEnergizingExpandsStackCountLikeTheJar() {
        RecipeData r = machine("powah:energizing");
        r.machineInputs.add(item(new ItemStack(Items.BLAZE_POWDER, 4)));
        r.machineOutputs.add(item(new ItemStack(Items.BLAZE_ROD)));
        r.customExtraJson = "\"energy\": 120000";
        assertNative("{\"type\":\"powah:energizing\",\"ingredients\":[{\"item\":\"minecraft:blaze_powder\"},{\"item\":\"minecraft:blaze_powder\"},{\"item\":\"minecraft:blaze_powder\"},{\"item\":\"minecraft:blaze_powder\"}],\"energy\":120000,\"result\":{\"id\":\"minecraft:blaze_rod\",\"count\":1}}", exported(r));
    }

    @Test
    void industrialForegoingDissolutionChamberMatchesJar() {
        RecipeData r = machine("industrialforegoing:dissolution_chamber");
        r.machineInputs.add(item(new ItemStack(Items.GLASS_PANE)));
        r.machineOutputs.add(item(new ItemStack(Items.SLIME_BALL)));
        r.customExtraJson = "\"inputFluid\": \"industrialforegoing:pink_slime\", \"inputFluidAmount\": 300, \"time\": 200, "
                + "\"outputFluid\": \"minecraft:water\", \"outputFluidAmount\": 150";
        assertNative("{\"type\":\"industrialforegoing:dissolution_chamber\",\"input\":[{\"item\":\"minecraft:glass_pane\"}],\"inputFluid\":{\"amount\":300,\"fluid\":\"industrialforegoing:pink_slime\"},\"output\":{\"id\":\"minecraft:slime_ball\",\"count\":1},\"outputFluid\":{\"amount\":150,\"id\":\"minecraft:water\"},\"processingTime\":200}", exported(r));
    }

    @Test
    void industrialForegoingDissolutionKeepsRingPositionsWithGaps() {
        // Ring slot 1 empty, slot 2 filled: the gap must not shift anything, and must not be exported.
        RecipeData r = machine("industrialforegoing:dissolution_chamber");
        r.machineInputs.add(gap());
        r.machineInputs.add(item(new ItemStack(Items.IRON_INGOT)));
        r.machineOutputs.add(item(new ItemStack(Items.IRON_BLOCK)));
        r.customExtraJson = "\"inputFluid\": \"minecraft:water\", \"time\": 100";
        String js = KubeJSExporter.previewRecipeJS(r);
        assertTrue(js.contains("NO EXPORTADO"), js);
        assertTrue(js.contains("Entrada 1"), js);
        assertFalse(js.contains("event.custom"), js);
    }

    @Test
    void industrialForegoingFluidExtractorMatchesJar() {
        RecipeData r = new RecipeData();
        r.recipeId = "native_test";
        r.type = RecipeData.RecipeType.CUSTOM_JSON;
        r.customTypeId = "industrialforegoing:fluid_extractor";
        r.shapelessIngredients.add(new ItemStack(Items.ACACIA_LOG));
        r.customExtraJson = "\"outputBlock\": \"minecraft:stripped_acacia_log\", \"breakChance\": 0.01, "
                + "\"outputFluid\": \"industrialforegoing:latex\", \"outputFluidAmount\": 4";
        assertNative("{\"type\":\"industrialforegoing:fluid_extractor\",\"input\":{\"item\":\"minecraft:acacia_log\"},\"result\":{\"Name\":\"minecraft:stripped_acacia_log\"},\"breakChance\":0.01,\"output\":{\"amount\":4,\"id\":\"industrialforegoing:latex\"},\"defaultRecipe\":false}", exported(r));
    }

    @Test
    void industrialForegoingLaserDrillFluidMatchesJar() {
        RecipeData r = new RecipeData();
        r.recipeId = "native_test";
        r.type = RecipeData.RecipeType.CUSTOM_JSON;
        r.customTypeId = "industrialforegoing:laser_drill_fluid";
        r.customExtraJson = "\"catalyst\": \"industrialforegoing:laser_lens10\", \"rarity\": \"\", \"entity\": \"minecraft:wither\", "
                + "\"outputFluid\": \"industrialforegoing:ether_gas\", \"outputFluidAmount\": 10";
        assertNative("{\"type\":\"industrialforegoing:laser_drill_fluid\",\"catalyst\":{\"item\":\"industrialforegoing:laser_lens10\"},\"entity_data\":{\"data\":{},\"display\":\"\",\"entity\":{\"type\":\"minecraft:wither\"}},\"output\":{\"amount\":10,\"fluid\":\"industrialforegoing:ether_gas\"},\"rarity\":[{\"biome_filter\":{\"blacklist\":[],\"whitelist\":[]},\"depth_max\":256,\"depth_min\":-64,\"dimension_filter\":{\"blacklist\":[],\"whitelist\":[]},\"weight\":8}]}", exported(r));
    }

    @Test
    void industrialForegoingStoneworkGenerateUsesRealKeyNames() {
        RecipeData r = new RecipeData();
        r.recipeId = "native_test";
        r.type = RecipeData.RecipeType.CUSTOM_JSON;
        r.customTypeId = "industrialforegoing:stonework_generate";
        r.output = new ItemStack(Items.ANDESITE);
        r.customExtraJson = "\"waterNeeded\": 300, \"lavaNeeded\": 300, \"waterConsumed\": 300, \"lavaConsumed\": 300";
        assertNative("{\"type\":\"industrialforegoing:stonework_generate\",\"output\":{\"id\":\"minecraft:andesite\",\"count\":1},\"waterNeed\":300,\"lavaNeed\":300,\"waterConsume\":300,\"lavaConsume\":300}", exported(r));
    }

    @Test
    void createFillingMatchesJar() {
        RecipeData r = machine("create:filling");
        r.machineInputs.add(item(new ItemStack(Items.GLASS_BOTTLE)));
        r.machineOutputs.add(item(new ItemStack(Items.HONEY_BOTTLE)));
        r.customExtraJson = "\"fluidId\": \"minecraft:lava\", \"fluidAmount\": 250";
        assertNative("{\"type\":\"create:filling\",\"ingredients\":[{\"item\":\"minecraft:glass_bottle\"},{\"type\":\"neoforge:single\",\"amount\":250,\"fluid\":\"minecraft:lava\"}],\"results\":[{\"id\":\"minecraft:honey_bottle\"}]}", exported(r));
    }

    @Test
    void createEmptyingMatchesJar() {
        RecipeData r = machine("create:emptying");
        r.machineInputs.add(item(new ItemStack(Items.HONEY_BOTTLE)));
        r.machineOutputs.add(item(new ItemStack(Items.GLASS_BOTTLE)));
        r.customExtraJson = "\"fluidOutputId\": \"create:tea\", \"fluidOutputAmount\": 250";
        assertNative("{\"type\":\"create:emptying\",\"ingredients\":[{\"item\":\"minecraft:honey_bottle\"}],\"results\":[{\"id\":\"minecraft:glass_bottle\"},{\"id\":\"create:tea\",\"amount\":250}]}", exported(r));
    }

    @Test
    void createMixingWithFluidTagAndGapsMatchesJar() {
        RecipeData r = machine("create:mixing");
        r.machineInputs.add(item(new ItemStack(Items.SUGAR)));
        r.machineInputs.add(item(new ItemStack(Items.COCOA_BEANS)));
        r.machineInputs.add(gap());
        r.machineInputs.add(fluid("#c:milk", 250));
        r.machineOutputs.add(gap());
        r.machineOutputs.add(fluid("create:chocolate", 250));
        r.customExtraJson = "\"heatRequirement\": \"heated\"";
        assertNative("{\"type\":\"create:mixing\",\"ingredients\":[{\"item\":\"minecraft:sugar\"},{\"item\":\"minecraft:cocoa_beans\"},{\"type\":\"neoforge:tag\",\"amount\":250,\"tag\":\"c:milk\"}],\"results\":[{\"id\":\"create:chocolate\",\"amount\":250}],\"heat_requirement\":\"heated\"}", exported(r));
    }

    @Test
    void tconstructAlloyIsNoLongerUnsupportedAndMatchesJar() {
        RecipeData r = machine("tconstruct:alloy");
        r.machineInputs.add(fluid("#c:molten_copper", 90));
        r.machineInputs.add(fluid("#tconstruct:molten_amethyst", 100));
        r.machineOutputs.add(fluid("#c:molten_amethyst_bronze", 90));
        r.customExtraJson = "\"temperature\": 820";
        assertNative("{\"type\":\"tconstruct:alloy\",\"inputs\":[{\"amount\":90,\"tag\":\"c:molten_copper\"},"
                + "{\"amount\":100,\"tag\":\"tconstruct:molten_amethyst\"}],\"result\":{\"amount\":90,\"tag\":\"c:molten_amethyst_bronze\"},"
                + "\"temperature\":820}", exported(r));
    }

    @Test
    void tconstructMeltingMatchesJar() {
        RecipeData r = machine("tconstruct:melting");
        r.machineInputs.add(tag("c:ingots/iron", 1));
        r.machineOutputs.add(fluid("#c:molten_iron", 90));
        r.customExtraJson = "\"temperature\": 800, \"time\": 60";
        assertNative("{\"type\":\"tconstruct:melting\",\"ingredient\":{\"tag\":\"c:ingots/iron\"},"
                + "\"result\":{\"amount\":90,\"tag\":\"c:molten_iron\"},\"temperature\":800,\"time\":60}", exported(r));
    }

    @Test
    void tconstructCastingTableWithCastMatchesJar() {
        RecipeData r = machine("tconstruct:casting_table");
        r.machineInputs.add(fluid("#c:molten_gold", 90));
        r.machineInputs.add(item(new ItemStack(Items.BOOK)));
        r.machineOutputs.add(item(new ItemStack(Items.ENCHANTED_BOOK)));
        r.customExtraJson = "\"cooling_time\": 57, \"cast_consumed\": true";
        assertNativeSubset("{\"type\":\"tconstruct:casting_table\",\"cast\":{\"item\":\"minecraft:book\"},\"cast_consumed\":true,"
                + "\"cooling_time\":57,\"fluid\":{\"amount\":90,\"tag\":\"c:molten_gold\"},\"result\":\"minecraft:enchanted_book\"}", exported(r));
    }

    @Test
    void missingRequiredSlotIsNotExportedSilently() {
        RecipeData r = machine("mekanism:chemical_infusing");
        r.machineInputs.add(fluid("mekanism:hydrogen", 1));
        r.machineOutputs.add(fluid("mekanism:hydrogen_chloride", 1));
        String js = KubeJSExporter.previewRecipeJS(r);
        assertTrue(js.contains("NO EXPORTADO"), js);
        assertTrue(js.contains("Gas derecho"), js);
        assertFalse(js.contains("event.custom"), js);
    }

    @Test
    void undeclaredExtraKeysPassThrough() {
        RecipeData r = machine("mekanism:crushing");
        r.machineInputs.add(item(new ItemStack(Items.COBBLESTONE)));
        r.machineOutputs.add(item(new ItemStack(Items.GRAVEL)));
        r.customExtraJson = "\"conditions\": [{\"type\": \"c:mod_loaded\", \"modid\": \"mekanism\"}]";
        JsonObject out = exported(r);
        assertTrue(out.has("conditions"), out.toString());
        assertEquals("mekanism:crushing", out.get("type").getAsString());
    }
}
