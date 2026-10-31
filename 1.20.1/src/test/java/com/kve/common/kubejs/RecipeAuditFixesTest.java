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

import static org.junit.jupiter.api.Assertions.*;

class RecipeAuditFixesTest {

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
        r.recipeId = "audit_test";
        r.type = RecipeData.RecipeType.CUSTOM_MACHINE;
        r.customTypeId = type;
        return r;
    }

    private static RecipeData.MachineSlot item(ItemStack s) {
        RecipeData.MachineSlot m = new RecipeData.MachineSlot();
        m.item = s;
        return m;
    }

    private static JsonObject exported(RecipeData r) {
        String js = KubeJSExporter.previewRecipeJS(r);
        int start = js.indexOf("event.custom(");
        int end = js.lastIndexOf(").id('shovel_js:");
        assertTrue(start >= 0 && end > start, "no event.custom(...) in:\n" + js);
        return JsonParser.parseString(js.substring(start + "event.custom(".length(), end)).getAsJsonObject();
    }

    @Test
    void farmersDelightCookingUsesItemStackShapeForResultAndContainer() {
        RecipeData r = machine("farmersdelight:cooking");
        r.machineInputs.add(item(new ItemStack(Items.APPLE, 2)));
        r.machineInputs.add(item(new ItemStack(Items.SUGAR)));
        r.machineOutputs.add(item(new ItemStack(Items.HONEY_BOTTLE)));
        r.customExtraJson = "\"container\": \"minecraft:glass_bottle\"";
        JsonObject o = exported(r);
        assertEquals(JsonParser.parseString("[{\"item\":\"minecraft:apple\"},{\"item\":\"minecraft:apple\"},{\"item\":\"minecraft:sugar\"}]"),
                o.get("ingredients"));
        assertEquals(JsonParser.parseString("{\"item\":\"minecraft:honey_bottle\"}"), o.get("result"));
        assertEquals(JsonParser.parseString("{\"item\":\"minecraft:glass_bottle\"}"), o.get("container"));
        assertEquals(200, o.get("cookingtime").getAsInt());
    }

    @Test
    void createMillingWritesProcessingTime() {
        RecipeData r = machine("create:milling");
        r.machineInputs.add(item(new ItemStack(Items.WHEAT)));
        r.machineOutputs.add(item(new ItemStack(Items.SUGAR)));
        assertEquals(100, exported(r).get("processingTime").getAsInt());
    }

    @Test
    void addonOnlyTypeWithoutTheAddonExplainsInsteadOfExportingBrokenJson() {
        RecipeData r = machine("totemic:ceremony");
        r.machineInputs.add(item(new ItemStack(Items.IRON_INGOT)));
        r.machineOutputs.add(item(new ItemStack(Items.IRON_BLOCK)));
        String js = KubeJSExporter.previewRecipeJS(r);
        assertTrue(js.contains("NO EXPORTADO"), js);
        assertTrue(js.contains("kubejs-"), js);
        assertFalse(js.contains("event.custom("), js);
    }

    private static RecipeData.MachineSlot fluid(String id, int amount) {
        RecipeData.MachineSlot m = new RecipeData.MachineSlot();
        m.type = RecipeData.MachineSlotType.FLUID;
        m.fluidId = id;
        m.fluidAmount = amount;
        return m;
    }

    @Test
    void flowingFluidsAreExportedAsTheirSource() {
        assertEquals("minecraft:water", KubeJSExporter.sourceFluidId("minecraft:flowing_water"));
        assertEquals("minecraft:lava", KubeJSExporter.sourceFluidId("minecraft:flowing_lava"));
        assertEquals("#minecraft:water", KubeJSExporter.sourceFluidId("#minecraft:water"));
        assertEquals("mekanism:hydrogen", KubeJSExporter.sourceFluidId("mekanism:hydrogen"));
        RecipeData r = machine("irons_spellbooks:alchemist_cauldron_fill");
        r.machineInputs.add(item(new ItemStack(Items.GLASS_BOTTLE)));
        r.machineInputs.add(fluid("minecraft:flowing_water", 250));
        r.machineOutputs.add(item(new ItemStack(Items.POTION)));
        assertTrue(exported(r).toString().contains("\"minecraft:water\""), exported(r).toString());
    }

    @Test
    void ironsPotionBaseFluidCarriesThePotion() {
        RecipeData r = machine("irons_spellbooks:alchemist_cauldron_brew");
        r.machineInputs.add(fluid("irons_spellbooks:potion", 1000));
        r.machineInputs.add(item(new ItemStack(Items.ENDER_PEARL)));
        r.machineOutputs.add(item(new ItemStack(Items.STONE)));
        r.machineOutputs.add(fluid("minecraft:water", 250));
        String noPotion = KubeJSExporter.previewRecipeJS(r);
        assertTrue(noPotion.contains("NO EXPORTADO"), "sin pocion no debe exportar:" + System.lineSeparator() + noPotion);
        r.customExtraJson = "\"basePotion\": \"minecraft:strong_healing\"";
        JsonObject base = exported(r).getAsJsonObject("base_fluid");
        assertTrue(base.toString().contains("minecraft:strong_healing"), base.toString());
    }

    @Test
    void apotheosisNumericStatChangeAlwaysHasMinAndMax() {
        RecipeData r = new RecipeData();
        r.recipeId = "audit_test";
        r.type = RecipeData.RecipeType.CUSTOM_JSON;
        r.customTypeId = "apotheosis:spawner_modifier";
        r.shapelessIngredients = new java.util.ArrayList<>(java.util.List.of(new ItemStack(Items.SUGAR)));
        r.output = ItemStack.EMPTY;
        JsonObject stat = exported(r).getAsJsonArray("stat_changes").get(0).getAsJsonObject();
        assertEquals(-1, stat.get("min").getAsInt());
        assertEquals(-1, stat.get("max").getAsInt());
    }

    @Test
    void botaniaPetalApothecaryUsesASeedReagent() {
        RecipeData r = machine("botania:petal_apothecary");
        r.machineInputs.add(item(new ItemStack(Items.POPPY)));
        r.machineOutputs.add(item(new ItemStack(Items.DANDELION)));
        JsonObject o = exported(r);
        assertEquals(JsonParser.parseString("{\"tag\":\"botania:seed_apothecary_reagent\"}"), o.get("reagent"));
    }

    @Test
    void thermalBottlerMixesItemAndFluidIngredients() {
        RecipeData r = machine("thermal:bottler");
        r.machineInputs.add(item(new ItemStack(Items.WHITE_CONCRETE_POWDER, 2)));
        r.machineOutputs.add(item(new ItemStack(Items.WHITE_CONCRETE)));
        JsonObject o = exported(r);
        assertEquals(JsonParser.parseString("[{\"item\":\"minecraft:white_concrete_powder\",\"count\":2},{\"fluid\":\"minecraft:water\",\"amount\":1000}]"),
                o.get("ingredients"));
    }

    @Test
    void singleItemCreateMachineNeverExpandsAStack() {
        RecipeData r = machine("create:crushing");
        r.machineInputs.add(item(new ItemStack(Items.COBBLESTONE, 3)));
        r.machineOutputs.add(item(new ItemStack(Items.GRAVEL)));
        assertEquals(1, exported(r).getAsJsonArray("ingredients").size());
    }

    @Test
    void stacksStillExpandWhenTheyFitTheMachine() {
        RecipeData r = machine("farmersdelight:cooking");
        r.machineInputs.add(item(new ItemStack(Items.APPLE, 3)));
        r.machineInputs.add(item(new ItemStack(Items.SUGAR)));
        r.machineOutputs.add(item(new ItemStack(Items.HONEY_BOTTLE)));
        assertEquals(4, exported(r).getAsJsonArray("ingredients").size());
    }

    @Test
    void stacksThatDoNotFitTheMachineAreNotExpanded() {
        RecipeData r = machine("farmersdelight:cooking");
        r.machineInputs.add(item(new ItemStack(Items.APPLE, 3)));
        r.machineInputs.add(item(new ItemStack(Items.SUGAR, 3)));
        r.machineInputs.add(item(new ItemStack(Items.WHEAT, 3)));
        r.machineOutputs.add(item(new ItemStack(Items.HONEY_BOTTLE)));
        assertEquals(3, exported(r).getAsJsonArray("ingredients").size());
    }
}
