package com.kve.common.kubejs;

import com.google.gson.JsonObject;
import com.kve.common.recipe.RecipeData;
import com.kve.common.recipetype.KveAddonFlags;
import com.kve.common.recipetype.RecipeTypeDescriptorRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tercera tanda "sin addons de KubeJS":
 * <ul>
 *   <li>create:item_application - misma forma que deploying (ItemApplicationRecipe
 *       lee ingredients/results/keepHeldItem), verificada contra
 *       data/create/recipes/item_application/*.json de create-1.20.1-6.0.8.</li>
 *   <li>projecte:emc_value - archivo de datapack pe_custom_conversions
 *       (FixedValuesDeserializer: values.before / values.after) de ProjectE-1.20.1-PE1.0.1.</li>
 * </ul>
 */
class NoAddonBatch3Test {

    private static final String[] ADDONS = {"kubejs-create", "kubejs-projecte"};

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

    private static RecipeData.MachineSlot item(net.minecraft.world.item.Item i) {
        RecipeData.MachineSlot s = new RecipeData.MachineSlot();
        s.type = RecipeData.MachineSlotType.ITEM;
        s.item = new ItemStack(i);
        return s;
    }

    private static RecipeData recipe(String type, String extra) {
        RecipeData r = new RecipeData();
        r.recipeId = "t_" + type.replace(':', '_');
        r.type = RecipeData.RecipeType.CUSTOM_MACHINE;
        r.customTypeId = type;
        r.customExtraJson = extra;
        return r;
    }

    @Test
    void itemApplicationExportsCreatesNativeShape() {
        RecipeData r = recipe("create:item_application", "");
        r.machineInputs.add(item(Items.STRIPPED_OAK_LOG));
        r.machineInputs.add(item(Items.IRON_INGOT));
        r.machineOutputs.add(item(Items.IRON_BLOCK));
        String js = KubeJSExporter.previewRecipeJS(r);
        assertTrue(js.contains("event.custom({"), js);
        assertTrue(js.contains("\"type\": \"create:item_application\""), js);
        assertTrue(js.contains("\"ingredients\": ["), js);
        assertTrue(js.contains("\"item\": \"minecraft:stripped_oak_log\""), js);
        assertTrue(js.contains("\"item\": \"minecraft:iron_ingot\""), js);
        assertTrue(js.contains("\"results\": ["), js);
        assertFalse(js.contains("event.recipes"), js);
        assertFalse(js.contains("\"inputs\""), "nada de la rama generica:\n" + js);
    }

    @Test
    void emcValueGoesToTheDatapackFileNotToAScript() {
        RecipeData r = recipe("projecte:emc_value", "\"item\": \"minecraft:diamond\", \"emc\": \"9000\", \"setBefore\": \"true\"");
        String js = KubeJSExporter.previewRecipeJS(r);
        assertFalse(js.contains("ProjectEEvents"), "no debe depender de kubejs-projecte:\n" + js);
        assertTrue(js.contains("pe_custom_conversions"), js);

        RecipeData after = recipe("projecte:emc_value", "\"item\": \"#forge:ingots/tin\", \"emc\": 300");
        RecipeData disabled = recipe("projecte:emc_value", "\"item\": \"minecraft:dirt\", \"emc\": 5");
        disabled.enabled = false;
        JsonObject json = ProjectEEmcExporter.buildJson(List.of(r, after, disabled));
        assertNotNull(json);
        JsonObject values = json.getAsJsonObject("values");
        assertEquals(9000L, values.getAsJsonObject("before").get("minecraft:diamond").getAsLong());
        assertEquals(300L, values.getAsJsonObject("after").get("#forge:ingots/tin").getAsLong());
        assertFalse(values.getAsJsonObject("after").has("minecraft:dirt"));
    }

    @Test
    void emcBelowOneIsRejectedLikeProjectEDoes() {
        RecipeData r = recipe("projecte:emc_value", "\"item\": \"minecraft:diamond\", \"emc\": 0");
        assertTrue(KubeJSExporter.previewRecipeJS(r).contains("NO EXPORTADO"));
        assertNull(ProjectEEmcExporter.buildJson(List.of(r)));
    }

    @Test
    void javaBuiltNativeTypesDoNotAskForAnAddon() {
        var d = RecipeTypeDescriptorRegistry.find("projecte:emc_value").orElseThrow();
        assertTrue(d.hasNativeExport());
        assertFalse(RecipeTypeDescriptorRegistry.shouldUseDedicatedCall(d));
        assertFalse(RecipeTypeDescriptorRegistry.addonFlagIsOnlyBlocker(d));
    }
}
