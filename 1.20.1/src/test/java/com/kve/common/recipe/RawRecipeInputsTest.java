package com.kve.common.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kve.common.recipetype.RecipeTypeDescriptor;
import com.kve.common.recipetype.RecipeTypeDescriptorRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lectura de recetas existentes de otros mods desde su JSON crudo
 * Los JSON son copias literales de los jars de extrasources/.
 */
class RawRecipeInputsTest {

    @BeforeEach
    void setUp() {
        RecipeTypeDescriptorRegistry.reload();
    }

    private static JsonObject json(String s) { return JsonParser.parseString(s).getAsJsonObject(); }

    private static RecipeTypeDescriptor desc(String type) {
        return RecipeTypeDescriptorRegistry.find(type).orElseThrow();
    }

    private static String itemOf(JsonElement e) {
        JsonObject o = e.getAsJsonObject();
        return o.has("item") ? o.get("item").getAsString() : "#" + o.get("tag").getAsString();
    }

    @Test
    void cataclysmCrabMeatSingleObjectIngredientIsRead() {
        JsonObject root = json("{\"type\":\"cataclysm:amethyst_bless\",\"ingredients\":{\"item\":\"cataclysm:amethyst_crab_meat\"},"
                + "\"result\":{\"item\":\"cataclysm:blessed_amethyst_crab_meat\"},\"time\":120}");
        List<JsonElement> in = RawRecipeInputs.inputs(root, desc("cataclysm:amethyst_bless"));
        assertEquals(1, in.size());
        assertEquals("cataclysm:amethyst_crab_meat", itemOf(in.get(0)));
        assertEquals(120, RawRecipeInputs.extraFields(root, desc("cataclysm:amethyst_bless")).get("time").getAsInt());
    }

    @Test
    void singleObjectIngredientsWithoutDescriptorIsRead() {
        JsonObject root = json("{\"ingredients\":{\"item\":\"minecraft:stick\"}}");
        List<JsonElement> in = RawRecipeInputs.inputs(root, null);
        assertEquals(1, in.size());
    }

    @Test
    void singularKeyArrayIsAlternativesOfOneSlot() {
        JsonObject root = json("{\"ingredient\":[{\"item\":\"minecraft:oak_log\"},{\"item\":\"minecraft:birch_log\"}]}");
        assertEquals(1, RawRecipeInputs.inputs(root, null).size());
    }

    @Test
    void weaponFusionUsesTemplateKeysInSlotOrder() {
        JsonObject root = json("{\"type\":\"cataclysm:weapon_fusion\",\"base\":{\"item\":\"minecraft:diamond_sword\"},"
                + "\"addition\":{\"item\":\"minecraft:nether_star\"},\"result\":{\"item\":\"minecraft:stick\"}}");
        List<JsonElement> in = RawRecipeInputs.inputs(root, desc("cataclysm:weapon_fusion"));
        assertEquals(2, in.size());
        assertEquals("minecraft:diamond_sword", itemOf(in.get(0)));
        assertEquals("minecraft:nether_star", itemOf(in.get(1)));
    }

    @Test
    void goetyCauldronPutsTakeWithInItsOwnSlot() {
        JsonObject root = json("{\"type\":\"goety:cauldron\",\"soulCost\":0,\"color\":16439971,\"take_with\":{\"tag\":\"forge:string\"},"
                + "\"ingredients\":[{\"item\":\"minecraft:honeycomb\"},{\"item\":\"minecraft:honeycomb\"}],\"result\":{\"item\":\"minecraft:candle\"}}");
        List<JsonElement> in = RawRecipeInputs.inputs(root, desc("goety:cauldron"));
        assertEquals(3, in.size());
        assertEquals("minecraft:honeycomb", itemOf(in.get(0)));
        assertEquals("#forge:string", itemOf(in.get(1)));
        assertEquals("minecraft:honeycomb", itemOf(in.get(2)));
        JsonObject extra = RawRecipeInputs.extraFields(root, desc("goety:cauldron"));
        assertEquals(16439971, extra.get("color").getAsInt());
    }

    @Test
    void goetyRitualFieldsAreReadFromSnakeCaseKeys() {
        JsonObject root = json("{\"type\":\"goety:ritual\",\"ritual_type\":\"goety:craft\",\"activation_item\":{\"item\":\"goety:wild_crown\"},"
                + "\"craftType\":\"deep\",\"soulCost\":1,\"duration\":10,"
                + "\"entity_to_sacrifice\":{\"tag\":\"goety:cats\",\"display_name\":\"entity.minecraft.cat\"},"
                + "\"ingredients\":[{\"tag\":\"forge:dusts/prismarine\"},{\"tag\":\"forge:gems/prismarine\"}],"
                + "\"result\":{\"item\":\"goety:abyss_crown\"}}");
        JsonObject extra = RawRecipeInputs.extraFields(root, desc("goety:ritual"));
        assertEquals("goety:craft", extra.get("ritualType").getAsString());
        assertEquals("goety:wild_crown", extra.get("activationItem").getAsString());
        assertEquals("deep", extra.get("craftType").getAsString());
        assertEquals(10, extra.get("duration").getAsInt());
        assertEquals("goety:cats", extra.get("entityToSacrificeTag").getAsString());
        assertEquals(2, RawRecipeInputs.inputs(root, desc("goety:ritual")).size());
    }

    @Test
    void tagIngredientBecomesHashId() {
        assertEquals("#forge:string", RawRecipeInputs.plain(json("{\"tag\":\"forge:string\"}"), "ingredient").getAsString());
    }

    @Test
    void farmersDelightCuttingToolAlternativesArePreserved() {
        JsonObject root = json("{\"type\":\"farmersdelight:cutting\",\"ingredients\":[{\"item\":\"minecraft:acacia_log\"}],"
                + "\"result\":[{\"item\":\"minecraft:stripped_acacia_log\"}],\"sound\":\"minecraft:item.axe.strip\","
                + "\"tool\":[{\"type\":\"farmersdelight:tool_action\",\"action\":\"axe_strip\"},{\"tag\":\"minecraft:axes\"}]}");
        JsonObject extra = RawRecipeInputs.extraFields(root, desc("farmersdelight:cutting"));
        assertTrue(extra.has("tool"), "el campo tool se perdia y se exportaba el cuchillo por defecto");
        assertEquals(root.get("tool"), JsonParser.parseString(extra.get("tool").getAsString()));
        assertEquals("minecraft:item.axe.strip", extra.get("sound").getAsString());
    }

    @Test
    void singleAlternativeArrayIsStillPlainId() {
        assertEquals("#minecraft:axes", RawRecipeInputs.plain(JsonParser.parseString("[{\"tag\":\"minecraft:axes\"}]"), "").getAsString());
    }

    /** irons_spellbooks-1.20.1-3.16.3: alchemist_cauldron/fill_water_bucket.json - "sound" y "mustFitAll" se perdian. */
    @Test
    void ironsCauldronSoundAndMustFitAllAreRead() {
        JsonObject root = json("{\"type\":\"irons_spellbooks:alchemist_cauldron_fill\",\"fluid\":{\"Amount\":1000,\"FluidName\":\"minecraft:water\"},"
                + "\"input\":{\"item\":\"minecraft:water_bucket\"},\"mustFitAll\":false,\"result\":{\"count\":1,\"item\":\"minecraft:bucket\"},"
                + "\"sound\":\"minecraft:item.bucket.empty\"}");
        JsonObject extra = RawRecipeInputs.extraFields(root, desc("irons_spellbooks:alchemist_cauldron_fill"));
        assertEquals("minecraft:item.bucket.empty", extra.get("sound").getAsString());
        assertFalse(extra.get("mustFitAll").getAsBoolean());
    }

    @Test
    void mekanismRotaryFluidAndGasGoToTheirSlots() {
        JsonObject root = json("{\"type\":\"mekanism:rotary\",\"fluidInput\":{\"amount\":1,\"tag\":\"minecraft:water\"},"
                + "\"gasOutput\":{\"amount\":1,\"gas\":\"mekanism:water_vapor\"},\"gasInput\":{\"amount\":1,\"gas\":\"mekanism:water_vapor\"},"
                + "\"fluidOutput\":{\"amount\":1,\"fluid\":\"minecraft:water\"}}");
        List<List<JsonElement>> in = RawRecipeInputs.slotElements(root, desc("mekanism:rotary"), false);
        assertEquals(2, in.size());
        assertEquals("minecraft:water", in.get(0).get(0).getAsJsonObject().get("tag").getAsString());
        assertEquals("mekanism:water_vapor", in.get(1).get(0).getAsJsonObject().get("gas").getAsString());
        List<List<JsonElement>> out = RawRecipeInputs.slotElements(root, desc("mekanism:rotary"), true);
        assertEquals("mekanism:water_vapor", out.get(0).get(0).getAsJsonObject().get("gas").getAsString());
        assertEquals("minecraft:water", out.get(1).get(0).getAsJsonObject().get("fluid").getAsString());
    }

    @Test
    void createMixingSplitsItemsAndFluids() {
        JsonObject root = json("{\"type\":\"create:mixing\",\"ingredients\":[{\"tag\":\"forge:sugar\"},"
                + "{\"fluid\":\"minecraft:milk\",\"amount\":250},{\"item\":\"minecraft:cocoa_beans\"}],"
                + "\"results\":[{\"fluid\":\"create:chocolate\",\"amount\":250}],\"heatRequirement\":\"heated\"}");
        RecipeTypeDescriptor d = desc("create:mixing");
        List<List<JsonElement>> in = RawRecipeInputs.slotElements(root, d, false);
        int fluidSlot = -1;
        List<RecipeTypeDescriptor.SlotDef> defs = d.slotLayout.slots.stream().filter(s -> !s.isOutput()).toList();
        for (int i = 0; i < defs.size(); i++) if ("fluid".equalsIgnoreCase(defs.get(i).type)) fluidSlot = i;
        assertEquals("minecraft:milk", in.get(fluidSlot).get(0).getAsJsonObject().get("fluid").getAsString());
        assertEquals("#forge:sugar", itemOf(in.get(0).get(0)));
        assertEquals("minecraft:cocoa_beans", itemOf(in.get(1).get(0)));
        List<List<JsonElement>> out = RawRecipeInputs.slotElements(root, d, true);
        assertTrue(out.stream().anyMatch(l -> !l.isEmpty() && RawRecipeInputs.isFluidJson(l.get(0))), "la salida de fluido se perdia");
    }

    @Test
    void mekanismCompressingReadsGasAmountField() {
        JsonObject root = json("{\"type\":\"mekanism:compressing\",\"chemicalInput\":{\"amount\":2,\"gas\":\"mekanism:osmium\"},"
                + "\"itemInput\":{\"ingredient\":{\"tag\":\"forge:ingots/osmium\"}},\"output\":{\"item\":\"mekanism:ingot_refined_obsidian\"}}");
        JsonObject extra = RawRecipeInputs.extraFields(root, desc("mekanism:compressing"));
        assertEquals("mekanism:osmium", extra.get("inputGas").getAsString());
        assertEquals(2, extra.get("inputGasAmount").getAsInt());
    }
}
