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

class NoAddonMigrationTest {

    @BeforeEach
    void setUp() {
        RecipeTypeDescriptorRegistry.reload();
        RecipeTypeDescriptorRegistry.setModLoadedCheckForTests(m -> true);
        KveAddonFlags.setEnabled("kubejs-create", true);
        KveAddonFlags.setEnabled("kubejs-farmers-delight", true);
    }

    @AfterEach
    void tearDown() {
        KveAddonFlags.setEnabled("kubejs-create", false);
        KveAddonFlags.setEnabled("kubejs-farmers-delight", false);
    }

    private static RecipeData.MachineSlot item(net.minecraft.world.item.Item i, int count, double chance) {
        RecipeData.MachineSlot s = new RecipeData.MachineSlot();
        s.type = RecipeData.MachineSlotType.ITEM;
        s.item = new ItemStack(i, count);
        s.chance = chance;
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

    @Test
    void createProcessingTypesExportTheirNativeShapeWithChances() {
        for (String type : new String[]{"create:crushing", "create:milling", "create:cutting",
                "create:sandpaper_polishing", "create:splashing", "create:haunting"}) {
            RecipeData r = machine(type, type.equals("create:crushing") ? "\"processingTime\": 250" : "");
            r.machineInputs.add(item(Items.GRAVEL, 1, 1.0));
            r.machineOutputs.add(item(Items.FLINT, 2, 1.0));
            r.machineOutputs.add(item(Items.IRON_NUGGET, 1, 0.5));
            String js = KubeJSExporter.previewRecipeJS(r);
            assertTrue(js.contains("event.custom({"), type + "\n" + js);
            assertTrue(js.contains("\"type\": \"" + type + "\""), js);
            assertTrue(js.contains("\"ingredients\": ["), js);
            assertTrue(js.contains("\"results\": ["), js);
            assertTrue(js.contains("\"count\": 2"), js);
            assertTrue(js.contains("\"chance\": 0.5"), "salidas con probabilidad como en el jar:\n" + js);
            assertFalse(js.contains("event.recipes"), "sin addon:\n" + js);
        }
        RecipeData crush = machine("create:crushing", "\"processingTime\": 250");
        crush.machineInputs.add(item(Items.GRAVEL, 1, 1.0));
        crush.machineOutputs.add(item(Items.FLINT, 1, 1.0));
        assertTrue(KubeJSExporter.previewRecipeJS(crush).contains("\"processing_time\": 250"));
    }

    @Test
    void createDeployingKeepsBaseAndHeldItemInOrder() {
        RecipeData r = machine("create:deploying", "\"keepHeldItem\": true");
        r.machineInputs.add(item(Items.STICK, 1, 1.0));
        r.machineInputs.add(item(Items.IRON_AXE, 1, 1.0));
        r.machineOutputs.add(item(Items.STRIPPED_OAK_LOG, 1, 1.0));
        String js = KubeJSExporter.previewRecipeJS(r);
        assertTrue(js.indexOf("minecraft:stick") < js.indexOf("minecraft:iron_axe"), "base primero, item del deployer segundo:\n" + js);
        assertTrue(js.contains("\"keep_held_item\": true"), "Create 6 (1.21.1) usa snake_case:\n" + js);
        assertFalse(js.contains("event.recipes"), js);
    }

    @Test
    void farmersDelightCuttingUsesToolIngredientAndResultArray() {
        RecipeData r = machine("farmersdelight:cutting", "\"tool\": \"#forge:tools/knives\"");
        r.machineInputs.add(item(Items.CAKE, 1, 1.0));
        r.machineOutputs.add(item(Items.BREAD, 7, 1.0));
        String js = KubeJSExporter.previewRecipeJS(r);
        assertTrue(js.contains("\"type\": \"farmersdelight:cutting\""), js);
        assertTrue(js.contains("\"tag\": \"forge:tools/knives\""), js);
        assertTrue(js.contains("\"result\": ["), "FD usa 'result' (lista), no 'results':\n" + js);
        assertFalse(js.contains("\"sound\""), "sonido vacio = clave omitida (es opcional en el serializer):\n" + js);
        assertFalse(js.contains("event.recipes"), js);
    }

    @Test
    void theStatusLineTellsTheUserNoAddonIsNeeded() {
        assertTrue(RecipeTypeDescriptorRegistry.dedicatedCallStatusLine("create:milling").contains("no necesita addon"));
        assertFalse(RecipeTypeDescriptorRegistry.addonFlagIsOnlyBlocker(RecipeTypeDescriptorRegistry.find("create:milling").orElseThrow()),
                "ya no se ofrece 'Habilitar addon' para un tipo nativo");
    }
}
