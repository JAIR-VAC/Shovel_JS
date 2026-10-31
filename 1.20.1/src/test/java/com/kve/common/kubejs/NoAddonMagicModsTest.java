package com.kve.common.kubejs;

import com.kve.common.recipe.RecipeData;
import com.kve.common.recipetype.KveAddonFlags;
import com.kve.common.recipetype.RecipeTypeDescriptorRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Segunda tanda "sin addons de KubeJS": Blood Magic, Ars Nouveau
 * y Goety exportan su JSON nativo. Cada forma se verifico contra el bytecode
 * del serializer real en extrasources/:
 * BloodMagic-1.18-1.20.1-3.3.8-50 (Constants$JSON, *RecipeSerializer),
 * ars_nouveau-1.20.1-4.12.7 (*Recipe$Serializer, CasterTomeData$Serializer),
 * goety-2.5.58.1 (*Recipe$Serializer, *RecipeSerializer).
 */
class NoAddonMagicModsTest {

    private static final String[] ADDONS = {"kubejs-blood-magic", "kubejs-ars-nouveau", "kubejs-goety"};

    @BeforeEach
    void setUp() {
        RecipeTypeDescriptorRegistry.reload();
        RecipeTypeDescriptorRegistry.setModLoadedCheckForTests(m -> true);
        for (String a : ADDONS) KveAddonFlags.setEnabled(a, true);
    }

    @AfterEach
    void tearDown() {
        for (String a : ADDONS) KveAddonFlags.setEnabled(a, false);
    }

    private static RecipeData.MachineSlot item(net.minecraft.world.item.Item i, int count, double chance) {
        RecipeData.MachineSlot s = new RecipeData.MachineSlot();
        s.type = RecipeData.MachineSlotType.ITEM;
        s.item = new ItemStack(i, count);
        s.chance = chance;
        return s;
    }

    private static RecipeData.MachineSlot item(net.minecraft.world.item.Item i) {
        return item(i, 1, 1.0);
    }

    private static RecipeData.MachineSlot tag(String tag) {
        RecipeData.MachineSlot s = new RecipeData.MachineSlot();
        s.type = RecipeData.MachineSlotType.TAG;
        s.tagId = tag;
        s.tagCount = 1;
        return s;
    }

    private static RecipeData.MachineSlot fluid(String id, int amount) {
        RecipeData.MachineSlot s = new RecipeData.MachineSlot();
        s.type = RecipeData.MachineSlotType.FLUID;
        s.fluidId = id;
        s.fluidAmount = amount;
        return s;
    }

    private static RecipeData machine(String type, String extra) {
        RecipeData r = new RecipeData();
        r.recipeId = "t_" + type.replace(':', '_');
        r.type = RecipeData.RecipeType.CUSTOM_MACHINE;
        r.customTypeId = type;
        r.customExtraJson = extra;
        return r;
    }

    private static String nativeJs(RecipeData r) {
        String js = KubeJSExporter.previewRecipeJS(r);
        assertTrue(js.contains("event.custom({"), js);
        assertTrue(js.contains("\"type\": \"" + r.customTypeId + "\""), js);
        assertFalse(js.contains("event.recipes"), "no debe depender del addon:\n" + js);
        return js;
    }

    @Test
    void bloodAltarWritesAllFourRequiredInts() {
        RecipeData r = machine("bloodmagic:altar",
                "\"upgradeLevel\": 2, \"altarSyphon\": 1000, \"consumptionRate\": 5, \"drainRate\": 5");
        r.machineInputs.add(item(Items.GHAST_TEAR));
        r.machineOutputs.add(item(Items.DIAMOND));
        String js = nativeJs(r);
        assertTrue(js.contains("\"input\": {\n"), js);
        assertTrue(js.contains("\"output\": {\n"), js);
        for (String k : new String[]{"\"upgradeLevel\": 2", "\"altarSyphon\": 1000", "\"consumptionRate\": 5", "\"drainRate\": 5"}) {
            assertTrue(js.contains(k), k + "\n" + js);
        }
    }

    @Test
    void soulForgeNumbersItsInputsLikeTheJar() {
        RecipeData r = machine("bloodmagic:soulforge", "\"minimumDrain\": 80.0, \"drain\": 2.5");
        r.machineInputs.add(tag("#forge:sand"));
        r.machineInputs.add(item(Items.CHARCOAL));
        r.machineInputs.add(item(Items.BRICK));
        r.machineOutputs.add(item(Items.TNT, 6, 1.0));
        String js = nativeJs(r);
        assertTrue(js.contains("\"input0\""), js);
        assertTrue(js.contains("\"tag\": \"forge:sand\""), "tag sin '#':\n" + js);
        assertTrue(js.contains("\"input2\""), js);
        assertFalse(js.contains("\"input3\""), "slot vacio = clave omitida:\n" + js);
        assertTrue(js.contains("\"count\": 6"), js);
        assertTrue(js.contains("\"drain\": 2.5"), js);
    }

    @Test
    void arcWritesToolFluidsAddedOutputAndInputSize() {
        RecipeData r = machine("bloodmagic:arc", "\"consumeIngredient\": false");
        r.machineInputs.add(item(Items.SAND, 8, 1.0));
        r.machineInputs.add(tag("#minecraft:shovels"));
        r.machineInputs.add(fluid("minecraft:water", 200));
        r.machineOutputs.add(item(Items.CLAY_BALL));
        r.machineOutputs.add(item(Items.FLINT, 1, 0.5));
        String js = nativeJs(r);
        assertTrue(js.contains("\"inputsize\": 8"), js);
        assertTrue(js.contains("\"tool\": {\n"), js);
        assertTrue(js.contains("\"tag\": \"minecraft:shovels\""), js);
        assertTrue(js.contains("\"inputFluid\": {"), js);
        assertTrue(js.contains("\"amount\": 200"), js);
        assertTrue(js.contains("\"addedoutput\": ["), js);
        assertTrue(js.contains("\"chance\": 0.5"), js);
        assertTrue(js.contains("\"mainchance\": 0.0"), js);
        assertTrue(js.contains("\"consumeingredient\": false"), "clave en minusculas como Constants$JSON:\n" + js);
        assertFalse(js.contains("\"outputFluid\""), js);
    }

    @Test
    void arcStillReadsTheOldToolFieldWhenTheToolSlotIsEmpty() {
        RecipeData r = machine("bloodmagic:arc", "\"tool\": \"#bloodmagic:arc/hydrate\"");
        r.machineInputs.add(item(Items.TERRACOTTA));
        r.machineOutputs.add(item(Items.CLAY));
        String js = nativeJs(r);
        assertTrue(js.contains("\"tag\": \"bloodmagic:arc/hydrate\""), js);
        assertTrue(js.contains("\"consumeingredient\": true"), "default del campo:\n" + js);
    }

    @Test
    void arcPotionIsNoLongerUnsupported() {
        assertFalse(RecipeTypeDescriptorRegistry.find("bloodmagic:arc_potion").orElseThrow().unsupported);
        RecipeData r = machine("bloodmagic:arc_potion", "");
        r.machineInputs.add(item(Items.ARROW, 8, 1.0));
        r.machineInputs.add(item(Items.POTION));
        r.machineOutputs.add(item(Items.TIPPED_ARROW, 8, 1.0));
        String js = nativeJs(r);
        assertTrue(js.contains("\"inputsize\": 8"), js);
        assertFalse(js.contains("\"effect\""), "effect/level/duration no existen en el serializer:\n" + js);
    }

    @Test
    void alchemyArrayUsesLowercaseKeysAndFirstTwoIngredients() {
        RecipeData r = machine("bloodmagic:array", "");
        r.machineInputs.add(item(Items.REDSTONE));
        r.machineInputs.add(item(Items.STONE));
        r.machineOutputs.add(item(Items.COMPASS));
        String js = nativeJs(r);
        assertTrue(js.contains("\"baseinput\""), js);
        assertTrue(js.contains("\"addedinput\""), js);
        assertTrue(js.indexOf("minecraft:redstone") < js.indexOf("minecraft:stone\""), js);
        assertFalse(js.contains("\"texture\""), "textura vacia = omitida:\n" + js);
    }

    @Test
    void glyphUsesItemIdOutputAndWrappedInputs() {
        RecipeData r = machine("ars_nouveau:glyph", "\"exp\": 160");
        r.machineInputs.add(item(Items.TNT));
        r.machineInputs.add(tag("#forge:gems"));
        r.machineOutputs.add(item(Items.PAPER));
        String js = nativeJs(r);
        assertTrue(js.contains("\"output\": \"minecraft:paper\""), "GlyphRecipe lee output con getAsItem (id plano):\n" + js);
        assertTrue(js.contains("\"count\": 1"), js);
        assertTrue(js.contains("\"exp\": 160"), js);
        assertTrue(js.contains("\"inputItems\": ["), js);
        assertTrue(js.contains("\"tag\": \"forge:gems\""), "sin '#' dentro de un Ingredient:\n" + js);
        assertFalse(js.contains("'#forge:gems'"), js);
    }

    @Test
    void glyphRecipeIdIsTheGlyphItemIdSoItReplacesTheOriginal() {
        RecipeData r = machine("ars_nouveau:glyph", "\"exp\": 160");
        r.machineInputs.add(item(Items.ENDER_PEARL));
        r.machineOutputs.add(item(Items.PAPER));
        String js = nativeJs(r);
        assertTrue(js.contains(").id('minecraft:paper');"), js);
        assertFalse(js.contains("shovel_js:"), js);
    }

    @Test
    void imbuementSeparatesTheCenterItemFromThePedestals() {
        RecipeData r = machine("ars_nouveau:imbuement", "\"sourceCost\": 2000");
        r.machineInputs.add(tag("#forge:gems/amethyst"));
        r.machineInputs.add(item(Items.SUGAR));
        r.machineInputs.add(item(Items.MILK_BUCKET));
        r.machineOutputs.add(item(Items.PAPER));
        String js = nativeJs(r);
        assertTrue(js.contains("\"input\": {"), js);
        assertTrue(js.contains("\"source\": 2000"), js);
        String pedestals = js.substring(js.indexOf("\"pedestalItems\""));
        assertTrue(pedestals.contains("minecraft:sugar") && pedestals.contains("minecraft:milk_bucket"), js);
        assertFalse(pedestals.contains("forge:gems/amethyst"), "el item central no es un pedestal:\n" + js);
    }

    @Test
    void imbuementWithoutPedestalsStillWritesTheRequiredArray() {
        RecipeData r = machine("ars_nouveau:imbuement", "");
        r.machineInputs.add(item(Items.AMETHYST_SHARD));
        r.machineOutputs.add(item(Items.PAPER));
        assertTrue(nativeJs(r).contains("\"pedestalItems\": []"));
    }

    @Test
    void apparatusWrapsTheReagentInAnArray() {
        RecipeData r = machine("ars_nouveau:enchanting_apparatus", "\"reagent\": \"minecraft:golden_helmet\"");
        r.machineInputs.add(item(Items.GLASS_BOTTLE));
        r.machineOutputs.add(item(Items.DIAMOND_HELMET));
        String js = nativeJs(r);
        assertTrue(js.contains("\"reagent\": ["), js);
        assertTrue(js.contains("minecraft:golden_helmet"), js);
        assertTrue(js.contains("\"sourceCost\": 1000"), js);
    }

    @Test
    void crushResultsAlwaysCarryAChance() {
        RecipeData r = machine("ars_nouveau:crush", "");
        r.machineInputs.add(item(Items.CORNFLOWER));
        r.machineOutputs.add(item(Items.BLUE_DYE, 2, 1.0));
        r.machineOutputs.add(item(Items.BONE_MEAL, 1, 0.25));
        String js = nativeJs(r);
        assertTrue(js.contains("\"chance\": 1.0"), js);
        assertTrue(js.contains("\"chance\": 0.25"), js);
        assertTrue(js.contains("\"skip_block_place\": false"), js);
    }

    @Test
    void casterTomeSplitsTheSpellIntoAList() {
        RecipeData r = machine("ars_nouveau:caster_tome",
                "\"tomeName\": \"Test\", \"spellGlyphs\": \"ars_nouveau:glyph_projectile, ars_nouveau:glyph_harm\"");
        String js = nativeJs(r);
        assertTrue(js.contains("\"spell\": ["), js);
        assertTrue(js.contains("\"ars_nouveau:glyph_harm\""), js);
        assertTrue(js.contains("\"type\": \"ars_nouveau:constant\""), js);
        assertTrue(js.contains("\"red\": 255"), js);
    }

    @Test
    void ritualUsesTheJarKeysAndDropsEmptyEntityBlocks() {
        RecipeData r = machine("goety:ritual",
                "\"ritualType\": \"goety:craft\", \"activationItem\": \"minecraft:ender_pearl\", "
                        + "\"craftType\": \"forge\", \"entityToSacrificeTag\": \"goety:cats\", "
                        + "\"entityToSacrificeDisplayName\": \"entity.minecraft.cat\"");
        r.machineInputs.add(item(Items.STRING));
        r.machineInputs.add(tag("#forge:gems/lapis"));
        r.machineOutputs.add(item(Items.LEAD));
        String js = nativeJs(r);
        assertTrue(js.contains("\"ritual_type\": \"goety:craft\""), js);
        assertTrue(js.contains("\"activation_item\": {"), js);
        assertTrue(js.contains("\"ingredients\": ["), js);
        assertTrue(js.contains("\"result\": {"), js);
        assertTrue(js.contains("\"entity_to_sacrifice\": {"), js);
        assertTrue(js.contains("\"display_name\": \"entity.minecraft.cat\""), js);
        assertFalse(js.contains("\"entity_to_convert\""), "bloque vacio = omitido:\n" + js);
        assertFalse(js.contains("\"structure_to_locate\""), js);
        assertFalse(js.contains("activationItem"), "nada de claves camelCase del addon:\n" + js);
    }

    /** goety-2.5.58.1 CauldronRecipe$Serializer: ingredients[], result, take_with (opcional), levelLeft/soulCost/color. */
    @Test
    void goetyCauldronExportsNativeJson() {
        RecipeData r = machine("goety:cauldron", "\"soulCost\": 1000, \"levelLeft\": 1");
        r.machineInputs.add(item(Items.SCUTE));
        r.machineInputs.add(item(Items.GLASS_BOTTLE));
        r.machineInputs.add(item(Items.SHULKER_SHELL));
        r.machineOutputs.add(item(Items.POTION));
        String js = nativeJs(r);
        assertTrue(js.contains("\"take_with\": {"), js);
        assertTrue(js.contains("\"minecraft:glass_bottle\""), js);
        assertTrue(js.contains("\"levelLeft\": 1"), js);
        assertTrue(js.contains("\"soulCost\": 1000"), js);
        assertTrue(js.contains("\"color\": 4159204"), js);
        int ing = js.indexOf("\"ingredients\"");
        int take = js.indexOf("\"take_with\"");
        String ingredients = js.substring(ing, js.indexOf(']', ing));
        assertFalse(ingredients.contains("glass_bottle"), "take_with no va en ingredients:\n" + js);
        assertTrue(ingredients.contains("minecraft:scute") && ingredients.contains("minecraft:shulker_shell"), js);
        assertTrue(take > 0);
    }

    @Test
    void goetyNoOutputTypesAreFlagged() {
        for (String t : new String[]{"goety:brewing", "goety:soul_absorber_recipes", "goety:cauldron_sus"}) {
            assertTrue(RecipeTypeDescriptorRegistry.find(t).orElseThrow().noOutput, t);
        }
    }

    @Test
    void soulAbsorberUsesTheLowercaseCookingTimeKey() {
        RecipeData r = machine("goety:soul_absorber_recipes", "\"cookingTime\": 50, \"soulIncrease\": 5");
        r.machineInputs.add(item(Items.GHAST_TEAR));
        String js = nativeJs(r);
        assertTrue(js.contains("\"cookingtime\": 50"), js);
        assertTrue(js.contains("\"soulIncrease\": 5"), js);
    }

    @Test
    void pulverizeTakesTheResultFromTheOutputSlot() {
        RecipeData r = machine("goety:pulverize", "");
        r.machineInputs.add(item(Items.AMETHYST_BLOCK));
        r.machineOutputs.add(item(Items.AMETHYST_SHARD, 4, 1.0));
        String js = nativeJs(r);
        assertTrue(js.contains("\"item_result\": {"), js);
        assertTrue(js.contains("\"count\": 4"), js);
    }

    @Test
    void brewingNestsTheEntityFilterOnlyWhenSet() {
        RecipeData r = machine("goety:brewing", "\"effectId\": \"minecraft:speed\"");
        r.machineInputs.add(item(Items.SUGAR));
        String js = nativeJs(r);
        assertTrue(js.contains("\"effect\": \"minecraft:speed\""), js);
        assertTrue(js.contains("\"capacityExtra\": 0"), js);
        assertFalse(js.contains("\"entity\""), js);
    }

    @Test
    void everyMigratedTypeSaysNoAddonIsNeeded() {
        for (String t : new String[]{
                "bloodmagic:altar", "bloodmagic:alchemytable", "bloodmagic:soulforge", "bloodmagic:arc",
                "bloodmagic:arc_potion", "bloodmagic:array",
                "ars_nouveau:enchanting_apparatus", "ars_nouveau:enchantment", "ars_nouveau:glyph",
                "ars_nouveau:imbuement", "ars_nouveau:crush", "ars_nouveau:caster_tome",
                "goety:ritual", "goety:brazier", "goety:cursed_infuser_recipes", "goety:brewing",
                "goety:pulverize", "goety:soul_absorber_recipes"}) {
            var d = RecipeTypeDescriptorRegistry.find(t).orElseThrow();
            assertTrue(d.hasRawJson(), t);
            assertFalse(RecipeTypeDescriptorRegistry.shouldUseDedicatedCall(d), t);
            assertFalse(RecipeTypeDescriptorRegistry.addonFlagIsOnlyBlocker(d), t);
        }
    }
}
