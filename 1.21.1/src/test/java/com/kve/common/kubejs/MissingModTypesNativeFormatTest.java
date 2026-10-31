package com.kve.common.kubejs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kve.common.recipe.RecipeData;
import com.kve.common.recipetype.RecipeTypeDescriptorRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tipos de receta que usan los jars de extrasources/ y que el
 * catalogo no tenia (IE bottling/squeezer/mixer/refinery..., Botany Pots
 * block_derived_*, Ars rituales, Ad Astra estacion espacial) y las salidas
 * secundarias de IE que se perdian. Mismo criterio que
 * {@link PopularModsNativeFormatTest}: se arma la receta real del jar
 * (ruta en cada test) con items vanilla y se compara la FORMA.
 */
class MissingModTypesNativeFormatTest {

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

    private static RecipeData grid(String type, ItemStack... ingredients) {
        RecipeData r = new RecipeData();
        r.recipeId = "native_test";
        r.type = RecipeData.RecipeType.CUSTOM_JSON;
        r.customTypeId = type;
        r.shapelessIngredients = new ArrayList<>(List.of(ingredients));
        r.output = ItemStack.EMPTY;
        return r;
    }

    private static RecipeData.MachineSlot item(ItemStack s) {
        RecipeData.MachineSlot m = new RecipeData.MachineSlot();
        m.item = s;
        return m;
    }

    private static RecipeData.MachineSlot item(ItemStack s, double chance) {
        RecipeData.MachineSlot m = item(s);
        m.chance = chance;
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

    private static JsonObject exported(RecipeData r) {
        String js = KubeJSExporter.previewRecipeJS(r);
        int start = js.indexOf("event.custom(");
        int end = js.lastIndexOf(").id('shovel_js:");
        assertTrue(start >= 0 && end > start, "no event.custom(...) in:\n" + js);
        return JsonParser.parseString(js.substring(start + "event.custom(".length(), end)).getAsJsonObject();
    }

    private static void assertJson(String expected, JsonObject actual) {
        JsonObject e = JsonParser.parseString(expected).getAsJsonObject();
        assertEquals(e, actual, "\nexpected: " + e + "\nactual:   " + actual);
    }

    @Test
    void ieBottlingMatchesJar() {
        RecipeData r = machine("immersiveengineering:bottling_machine");
        r.machineInputs.add(item(new ItemStack(Items.BLACK_CONCRETE_POWDER)));
        r.machineInputs.add(fluid("#minecraft:water", 125));
        r.machineOutputs.add(item(new ItemStack(Items.BLACK_CONCRETE)));
        assertJson("{\"type\":\"immersiveengineering:bottling_machine\",\"inputs\":[{\"item\":\"minecraft:black_concrete_powder\"}],"
                + "\"fluid\":{\"amount\":125,\"tag\":\"minecraft:water\"},\"results\":[{\"id\":\"minecraft:black_concrete\",\"count\":1}]}", exported(r));
    }

    @Test
    void ieSqueezerMatchesJar() {
        RecipeData r = machine("immersiveengineering:squeezer");
        r.machineInputs.add(item(new ItemStack(Items.WHEAT_SEEDS)));
        r.machineOutputs.add(fluid("minecraft:water", 80));
        r.customExtraJson = "{\"energy\": 6400}";
        assertJson("{\"type\":\"immersiveengineering:squeezer\",\"input\":{\"item\":\"minecraft:wheat_seeds\"},"
                + "\"fluid\":{\"amount\":80,\"id\":\"minecraft:water\"},\"energy\":6400}", exported(r));
    }

    @Test
    void ieMixerMatchesJar() {
        RecipeData r = machine("immersiveengineering:mixer");
        r.machineInputs.add(fluid("#minecraft:water", 500));
        r.machineInputs.add(tag("c:sands", 2));
        r.machineInputs.add(tag("c:gravels", 1));
        r.machineOutputs.add(fluid("minecraft:lava", 500));
        r.customExtraJson = "{\"energy\": 3200}";
        assertJson("{\"type\":\"immersiveengineering:mixer\",\"fluid\":{\"amount\":500,\"tag\":\"minecraft:water\"},"
                + "\"inputs\":[{\"basePredicate\":{\"tag\":\"c:sands\"},\"count\":2},{\"tag\":\"c:gravels\"}],"
                + "\"result\":{\"amount\":500,\"id\":\"minecraft:lava\"},\"energy\":3200}", exported(r));
    }

    @Test
    void ieRefineryMatchesJar() {
        RecipeData r = machine("immersiveengineering:refinery");
        r.machineInputs.add(fluid("#c:plantoil", 8));
        r.machineInputs.add(fluid("#c:ethanol", 8));
        r.machineInputs.add(tag("c:dusts/saltpeter", 1));
        r.machineOutputs.add(fluid("minecraft:water", 16));
        r.customExtraJson = "{\"energy\": 80}";
        assertJson("{\"type\":\"immersiveengineering:refinery\",\"input0\":{\"amount\":8,\"tag\":\"c:plantoil\"},"
                + "\"input1\":{\"amount\":8,\"tag\":\"c:ethanol\"},\"catalyst\":{\"tag\":\"c:dusts/saltpeter\"},"
                + "\"result\":{\"amount\":16,\"id\":\"minecraft:water\"},\"energy\":80}", exported(r));
    }

    @Test
    void ieCrusherKeepsSecondaries() {
        RecipeData r = machine("immersiveengineering:crusher");
        r.machineInputs.add(item(new ItemStack(Items.COBBLESTONE)));
        r.machineOutputs.add(item(new ItemStack(Items.GRAVEL)));
        r.machineOutputs.add(item(new ItemStack(Items.FLINT), 0.5));
        r.customExtraJson = "{\"energy\": 1600}";
        assertJson("{\"type\":\"immersiveengineering:crusher\",\"input\":{\"item\":\"minecraft:cobblestone\"},"
                + "\"result\":{\"id\":\"minecraft:gravel\",\"count\":1},\"energy\":1600,"
                + "\"secondaries\":[{\"chance\":0.5,\"output\":{\"id\":\"minecraft:flint\",\"count\":1}}]}", exported(r));
    }

    @Test
    void ieSawmillAlwaysWritesSecondaryLists() {
        RecipeData r = machine("immersiveengineering:sawmill");
        r.machineInputs.add(item(new ItemStack(Items.ACACIA_LOG)));
        r.machineOutputs.add(item(new ItemStack(Items.ACACIA_PLANKS, 6)));
        r.machineOutputs.add(tag("c:dusts/wood", 1));
        r.customExtraJson = "{\"energy\": 1600, \"stripped\": \"minecraft:stripped_acacia_log\", \"strippingSecondaries\": \"#c:dusts/wood\"}";
        assertJson("{\"type\":\"immersiveengineering:sawmill\",\"input\":{\"item\":\"minecraft:acacia_log\"},"
                + "\"result\":{\"id\":\"minecraft:acacia_planks\",\"count\":6},\"stripped\":{\"id\":\"minecraft:stripped_acacia_log\"},"
                + "\"energy\":1600,\"secondaryOutputs\":[{\"tag\":\"c:dusts/wood\"}],\"strippingSecondaries\":[{\"tag\":\"c:dusts/wood\"}]}", exported(r));
    }

    @Test
    void ieMineralMixMatchesJar() {
        RecipeData r = machine("immersiveengineering:mineral_mix");
        r.machineOutputs.add(tag("c:gems/diamond", 1));
        r.machineOutputs.get(0).chance = 0.2;
        r.machineOutputs.add(item(new ItemStack(Items.CLAY), 0.4));
        r.customExtraJson = "{\"biomes\": \"minecraft:is_overworld; minecraft:is_river\", \"weight\": 15, \"fail_chance\": 0.2,"
                + " \"spoils\": \"minecraft:gravel@0.6, minecraft:cobblestone@0.4\"}";
        assertJson("{\"type\":\"immersiveengineering:mineral_mix\","
                + "\"biome_predicates\":[[\"minecraft:is_overworld\"],[\"minecraft:is_river\"]],\"fail_chance\":0.2,"
                + "\"ores\":[{\"chance\":0.2,\"output\":{\"tag\":\"c:gems/diamond\"}},{\"chance\":0.4,\"output\":{\"id\":\"minecraft:clay\",\"count\":1}}],"
                + "\"spoils\":[{\"chance\":0.6,\"output\":{\"id\":\"minecraft:gravel\"}},{\"chance\":0.4,\"output\":{\"id\":\"minecraft:cobblestone\"}}],"
                + "\"weight\":15}", exported(r));
    }

    @Test
    void ieThermoelectricMatchesJar() {
        RecipeData r = grid("immersiveengineering:thermoelectric_source", new ItemStack(Items.BLUE_ICE));
        r.customExtraJson = "{\"tempKelvin\": 200}";
        assertJson("{\"type\":\"immersiveengineering:thermoelectric_source\",\"singleBlock\":\"minecraft:blue_ice\",\"tempKelvin\":200}", exported(r));
    }

    @Test
    void botanyPotsBlockDerivedCropDefaultsBlockToSeed() {
        RecipeData r = grid("botanypots:block_derived_crop", new ItemStack(Items.POPPY));
        assertJson("{\"type\":\"botanypots:block_derived_crop\",\"block\":\"minecraft:poppy\",\"input\":{\"item\":\"minecraft:poppy\"}}", exported(r));
    }

    @Test
    void botanyPotsInteractionMatchesJar() {
        RecipeData r = machine("botanypots:pot_interaction");
        r.machineInputs.add(tag("minecraft:hoes", 1));
        r.machineInputs.add(item(new ItemStack(Items.COARSE_DIRT)));
        r.machineOutputs.add(item(new ItemStack(Items.DIRT)));
        r.customExtraJson = "{\"sound\": \"minecraft:item.hoe.till\"}";
        assertJson("{\"type\":\"botanypots:pot_interaction\",\"held_item\":{\"tag\":\"minecraft:hoes\"},\"soil_item\":{\"item\":\"minecraft:coarse_dirt\"},"
                + "\"new_soil\":{\"id\":\"minecraft:dirt\",\"count\":1},\"sound_effect\":{\"id\":\"minecraft:item.hoe.till\",\"category\":\"BLOCKS\"}}", exported(r));
    }

    @Test
    void arsSummonRitualMatchesJar() {
        RecipeData r = machine("ars_nouveau:summon_ritual");
        r.machineInputs.add(item(new ItemStack(Items.AMETHYST_SHARD)));
        r.machineOutputs.add(item(new ItemStack(Items.BAT_SPAWN_EGG)));
        r.customExtraJson = "{\"count\": 5}";
        assertJson("{\"type\":\"ars_nouveau:summon_ritual\",\"augment\":{\"item\":\"minecraft:amethyst_shard\"},\"count\":5,"
                + "\"mobs\":[{\"mob\":\"minecraft:bat\",\"weight\":1}],\"source\":{\"mobSource\":\"MOB_LIST\"}}", exported(r));
    }

    @Test
    void arsBuddingConversionUsesBareIds() {
        RecipeData r = machine("ars_nouveau:budding_conversion");
        r.machineInputs.add(item(new ItemStack(Items.AMETHYST_BLOCK)));
        r.machineOutputs.add(item(new ItemStack(Items.BUDDING_AMETHYST)));
        assertJson("{\"type\":\"ars_nouveau:budding_conversion\",\"input\":\"minecraft:amethyst_block\",\"result\":\"minecraft:budding_amethyst\"}", exported(r));
    }

    @Test
    void adAstraSpaceStationMatchesJar() {
        RecipeData r = grid("ad_astra:space_station_recipe");
        r.type = RecipeData.RecipeType.CUSTOM_MACHINE;
        r.shapelessIngredients = null;
        r.machineInputs.add(tag("c:plates/iron", 64));
        r.machineInputs.add(item(new ItemStack(Items.IRON_INGOT, 32)));
        assertJson("{\"type\":\"ad_astra:space_station_recipe\",\"dimension\":\"ad_astra:earth_orbit\","
                + "\"ingredients\":[{\"count\":64,\"ingredient\":{\"tag\":\"c:plates/iron\"}},{\"count\":32,\"ingredient\":{\"item\":\"minecraft:iron_ingot\"}}],"
                + "\"structure\":\"ad_astra:space_station\"}", exported(r));
    }

    @Test
    void botaniaPetalApothecaryMatchesDatagen() {
        RecipeData r = machine("botania:petal_apothecary");
        r.machineInputs.add(tag("botania:petals/lime", 2));
        r.machineInputs.add(item(new ItemStack(Items.REDSTONE)));
        r.machineOutputs.add(item(new ItemStack(Items.POPPY)));
        r.customExtraJson = "{\"reagent\": \"#botania:seed_apothecary_reagent\"}";
        assertJson("{\"type\":\"botania:petal_apothecary\",\"ingredients\":[{\"tag\":\"botania:petals/lime\"},{\"tag\":\"botania:petals/lime\"},{\"item\":\"minecraft:redstone\"}],"
                + "\"output\":{\"id\":\"minecraft:poppy\",\"count\":1},\"reagent\":{\"tag\":\"botania:seed_apothecary_reagent\"}}", exported(r));
    }

    @Test
    void botaniaRunicAltarAlwaysHasCatalysts() {
        RecipeData r = machine("botania:runic_altar");
        r.machineInputs.add(item(new ItemStack(Items.FEATHER)));
        r.machineOutputs.add(item(new ItemStack(Items.STRING, 2)));
        r.customExtraJson = "{\"mana\": 5200, \"reagent\": \"minecraft:stone\"}";
        assertJson("{\"type\":\"botania:runic_altar\",\"catalysts\":[],\"ingredients\":[{\"item\":\"minecraft:feather\"}],\"mana\":5200,"
                + "\"output\":{\"id\":\"minecraft:string\",\"count\":2},\"reagent\":{\"item\":\"minecraft:stone\"}}", exported(r));
    }

    @Test
    void botaniaManaInfusionCatalystIsStateIngredient() {
        RecipeData r = machine("botania:mana_infusion");
        r.machineInputs.add(item(new ItemStack(Items.POPPY)));
        r.machineOutputs.add(item(new ItemStack(Items.DANDELION)));
        r.customExtraJson = "{\"mana\": 2500, \"catalyst\": \"minecraft:diamond_block\"}";
        assertJson("{\"type\":\"botania:mana_infusion\",\"catalyst\":{\"type\":\"botania:block\",\"block\":\"minecraft:diamond_block\"},"
                + "\"input\":{\"item\":\"minecraft:poppy\"},\"mana\":2500,\"output\":{\"id\":\"minecraft:dandelion\",\"count\":1}}", exported(r));
    }

    @Test
    void botaniaMarimorphosisTagKeepsHash() {
        RecipeData r = machine("botania:marimorphosis");
        r.machineInputs.add(tag("minecraft:base_stone_overworld", 1));
        r.machineOutputs.add(item(new ItemStack(Items.CALCITE)));
        r.customExtraJson = "{\"mana\": 12, \"weight\": 1, \"biome_bonus_tag\": \"minecraft:is_ocean\", \"biome_bonus_weight\": 11}";
        assertJson("{\"type\":\"botania:marimorphosis\",\"biome_bonus_tag\":\"minecraft:is_ocean\",\"biome_bonus_weight\":11,\"cooldown\":0,"
                + "\"input\":{\"type\":\"botania:tag\",\"tag\":\"#minecraft:base_stone_overworld\"},\"mana\":12,"
                + "\"output\":{\"type\":\"botania:block\",\"block\":\"minecraft:calcite\"},\"weight\":1}", exported(r));
    }

    @Test
    void apotheosisSizedUpgradeMatchesJar() {
        RecipeData r = machine("apotheosis:sized_upgrade_recipe");
        r.machineInputs.add(item(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)));
        r.machineInputs.add(item(new ItemStack(Items.CHAINMAIL_BOOTS)));
        r.machineInputs.add(tag("c:ingots/iron", 4));
        r.machineOutputs.add(item(new ItemStack(Items.IRON_BOOTS)));
        assertJson("{\"type\":\"apotheosis:sized_upgrade_recipe\",\"addition\":{\"count\":4,\"tag\":\"c:ingots/iron\"},"
                + "\"base\":{\"item\":\"minecraft:chainmail_boots\"},\"result\":{\"id\":\"minecraft:iron_boots\",\"count\":1},"
                + "\"template\":{\"item\":\"minecraft:netherite_upgrade_smithing_template\"}}", exported(r));
    }

    @Test
    void apotheosisPurityUpgradeMatchesJar() {
        RecipeData r = machine("apotheosis:purity_upgrade");
        r.machineInputs.add(item(new ItemStack(Items.GLOWSTONE_DUST, 3)));
        r.machineInputs.add(item(new ItemStack(Items.IRON_NUGGET, 9)));
        r.machineInputs.add(item(new ItemStack(Items.AMETHYST_SHARD, 1)));
        r.customExtraJson = "{\"purity\": \"chipped\"}";
        assertJson("{\"type\":\"apotheosis:purity_upgrade\",\"left\":[{\"count\":3,\"item\":\"minecraft:glowstone_dust\"}],\"purity\":\"chipped\","
                + "\"right\":[{\"count\":9,\"item\":\"minecraft:iron_nugget\"},{\"count\":1,\"item\":\"minecraft:amethyst_shard\"}]}", exported(r));
    }

    @Test
    void arsArmorUpgradeMatchesJar() {
        RecipeData r = machine("ars_nouveau:armor_upgrade");
        r.machineInputs.add(tag("c:rods/blaze", 1));
        r.machineInputs.add(tag("c:rods/blaze", 1));
        r.customExtraJson = "{\"tier\": 1, \"sourceCost\": 2500}";
        assertJson("{\"type\":\"ars_nouveau:armor_upgrade\",\"pedestalItems\":[{\"tag\":\"c:rods/blaze\"},{\"tag\":\"c:rods/blaze\"}],"
                + "\"sourceCost\":2500,\"tier\":1}", exported(r));
    }

    @Test
    void inputOnlyMachineRecipeValidates() {
        RecipeData r = machine("aether:item_placement_ban");
        r.machineInputs.add(item(new ItemStack(Items.FLINT_AND_STEEL)));
        assertTrue(KubeJSExporter.validateRecipe(r).isEmpty(), KubeJSExporter.validateRecipe(r).toString());
        assertEquals(JsonParser.parseString("{\"item\":\"minecraft:flint_and_steel\"}"), exported(r).get("ingredient"));
    }
}
