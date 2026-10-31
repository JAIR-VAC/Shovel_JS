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
 * Tipos de receta de TConstruct, Ars Nouveau y Blood Magic que
 * usan los jars de extrasources/ y que el catalogo no tenia (o exportaba sin
 * una clave). Mismo criterio que {@link PopularModsNativeFormatTest}: se arma
 * la receta real del jar (ruta en cada test) con items vanilla y se compara
 * la FORMA.
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
    void tconstructMeltingKeepsByproducts() {
        // data/tconstruct/recipes/compat/ceramics/... (melting con "byproducts")
        RecipeData r = machine("tconstruct:melting");
        r.machineInputs.add(item(new ItemStack(Items.TERRACOTTA)));
        r.machineOutputs.add(fluid("minecraft:lava", 250));
        r.machineOutputs.add(fluid("minecraft:water", 50));
        r.customExtraJson = "{\"temperature\": 450, \"time\": 48}";
        JsonObject j = exported(r);
        assertEquals(JsonParser.parseString("[{\"amount\":50,\"fluid\":\"minecraft:water\"}]"), j.get("byproducts"), j.toString());
        assertEquals(JsonParser.parseString("{\"amount\":250,\"fluid\":\"minecraft:lava\"}"), j.get("result"), j.toString());
    }

    @Test
    void tconstructEntityMeltingMatchesJar() {
        RecipeData r = machine("tconstruct:entity_melting");
        r.machineInputs.add(tag("forge:bees", 1));
        r.machineOutputs.add(fluid("#forge:honey", 25));
        r.customExtraJson = "{\"damage\": 2}";
        assertJson("{\"type\":\"tconstruct:entity_melting\",\"damage\":2,\"entity\":{\"tag\":\"forge:bees\"},"
                + "\"result\":{\"amount\":25,\"tag\":\"forge:honey\"}}", exported(r));
    }

    @Test
    void tconstructSeveringMatchesJar() {
        RecipeData r = machine("tconstruct:severing");
        r.machineInputs.add(item(new ItemStack(Items.BLAZE_SPAWN_EGG)));
        r.machineOutputs.add(item(new ItemStack(Items.SKELETON_SKULL)));
        r.customExtraJson = "{\"per_level_chance\": 0.05, \"looting_bonus\": 0.01}";
        JsonObject j = exported(r);
        assertEquals(JsonParser.parseString("{\"type\":\"minecraft:blaze\"}"), j.get("entity"), j.toString());
        assertEquals(0.05, j.get("per_level_chance").getAsDouble(), 1e-9);
        assertEquals(0.01, j.get("looting_bonus").getAsDouble(), 1e-9);
        assertTrue(j.has("result"), j.toString());
    }

    @Test
    void tconstructMeltingFuelMatchesJar() {
        RecipeData r = grid("tconstruct:melting_fuel");
        r.customExtraJson = "{\"fluid\": \"minecraft:lava\", \"amount\": 50, \"duration\": 150, \"temperature\": 1500, \"rate\": 15}";
        assertJson("{\"type\":\"tconstruct:melting_fuel\",\"duration\":150,\"fluid\":{\"amount\":50,\"fluid\":\"minecraft:lava\"},"
                + "\"rate\":15,\"temperature\":1500}", exported(r));
    }

    @Test
    void tconstructMoldingMatchesJar() {
        RecipeData r = machine("tconstruct:molding_table");
        r.machineInputs.add(tag("minecraft:sand", 1));
        r.machineInputs.add(item(new ItemStack(Items.IRON_AXE)));
        r.machineOutputs.add(item(new ItemStack(Items.RED_SAND)));
        assertJson("{\"type\":\"tconstruct:molding_table\",\"material\":{\"tag\":\"minecraft:sand\"},"
                + "\"pattern\":{\"item\":\"minecraft:iron_axe\"},\"result\":\"minecraft:red_sand\"}", exported(r));
    }

    @Test
    void arsSummonRitualUsesStringSource() {
        RecipeData r = machine("ars_nouveau:summon_ritual");
        r.machineInputs.add(item(new ItemStack(Items.AMETHYST_SHARD)));
        r.machineOutputs.add(item(new ItemStack(Items.BAT_SPAWN_EGG)));
        r.customExtraJson = "{\"count\": 5}";
        assertJson("{\"type\":\"ars_nouveau:summon_ritual\",\"augment\":{\"item\":\"minecraft:amethyst_shard\"},\"count\":5,"
                + "\"mobs\":[{\"mob\":\"minecraft:bat\",\"weight\":1}],\"source\":\"MOB_LIST\"}", exported(r));
    }

    @Test
    void arsArmorUpgradeWrapsPedestals() {
        RecipeData r = machine("ars_nouveau:armor_upgrade");
        r.machineInputs.add(tag("forge:rods/blaze", 1));
        r.machineInputs.add(tag("forge:rods/blaze", 1));
        r.customExtraJson = "{\"tier\": 1, \"sourceCost\": 2500}";
        assertJson("{\"type\":\"ars_nouveau:armor_upgrade\",\"pedestalItems\":[{\"item\":{\"tag\":\"forge:rods/blaze\"}},{\"item\":{\"tag\":\"forge:rods/blaze\"}}],"
                + "\"sourceCost\":2500,\"tier\":1}", exported(r));
    }

    @Test
    void inputOnlyMachineRecipeValidates() {
        RecipeData r = machine("tconstruct:table_filling");
        r.machineInputs.add(item(new ItemStack(Items.BUCKET)));
        r.customExtraJson = "{\"fluid_amount\": 1000}";
        assertTrue(KubeJSExporter.validateRecipe(r).isEmpty(), KubeJSExporter.validateRecipe(r).toString());
        assertEquals("minecraft:bucket", exported(r).get("container").getAsString());
    }
}
