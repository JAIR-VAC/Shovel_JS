package com.kve.common.kubejs;

import com.kve.common.recipe.RecipeData;
import com.kve.common.recipetype.RecipeTypeDescriptor;
import com.kve.common.recipetype.RecipeTypeDescriptorRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScriptWriteGuardCoverageTest {

    @BeforeEach
    void noModsLoaded() {
        RecipeTypeDescriptorRegistry.setModLoadedCheckForTests(m -> false);
        RecipeTypeDescriptorRegistry.reload();
    }

    @AfterEach
    void restore() {
        RecipeTypeDescriptorRegistry.setModLoadedCheckForTests(null);
    }

    private static RecipeData.MachineSlot item(ItemStack s) {
        RecipeData.MachineSlot m = new RecipeData.MachineSlot();
        m.item = s;
        return m;
    }

    private static RecipeData.MachineSlot fluid(String id) {
        RecipeData.MachineSlot m = new RecipeData.MachineSlot();
        m.type = RecipeData.MachineSlotType.FLUID;
        m.fluidId = id;
        m.fluidAmount = 250;
        return m;
    }

    @Test
    void everyCatalogTypeExportsWellFormedScript() {
        List<String> bad = new ArrayList<>();
        int checked = 0;
        for (RecipeTypeDescriptor d : RecipeTypeDescriptorRegistry.all()) {
            RecipeData r = new RecipeData();
            r.recipeId = "guard_test";
            r.customTypeId = d.recipeSerializer;
            r.customExtraJson = "{\"note\": \"it's a \\\"test\\\"\"}";
            if (d.hasSlotLayout()) {
                r.type = RecipeData.RecipeType.CUSTOM_MACHINE;
                for (RecipeTypeDescriptor.SlotDef s : d.slotLayout.slots) {
                    RecipeData.MachineSlot slot = "fluid".equalsIgnoreCase(s.type) ? fluid("minecraft:water") : item(new ItemStack(Items.IRON_INGOT));
                    (s.isOutput() ? r.machineOutputs : r.machineInputs).add(slot);
                }
            } else {
                r.type = RecipeData.RecipeType.CUSTOM_JSON;
                r.shapelessIngredients = new ArrayList<>(List.of(new ItemStack(Items.IRON_INGOT)));
                r.output = new ItemStack(Items.GOLD_INGOT);
            }
            String js;
            try {
                js = KubeJSExporter.previewRecipeJS(r);
            } catch (RuntimeException ex) {
                bad.add(d.recipeSerializer + " -> excepcion: " + ex);
                continue;
            }
            String wrapped = "ServerEvents.recipes(event => {\n" + js + "\n})\n";
            if (!ScriptWriteGuard.looksWellFormed(wrapped)) bad.add(d.recipeSerializer + ":\n" + js);
            checked++;
        }
        assertTrue(checked > 100, "Sanity: se esperaban mas de 100 tipos, hubo " + checked);
        assertTrue(bad.isEmpty(), "Scripts que la guarda rechazaria:\n" + String.join("\n\n", bad));
    }

    @Test
    void guardStillRejectsBrokenScripts() {
        assertFalse(ScriptWriteGuard.looksWellFormed("event.custom({\"a\": 'x)"));
        assertFalse(ScriptWriteGuard.looksWellFormed("ServerEvents.tags('item', event => {"));
        assertTrue(ScriptWriteGuard.looksWellFormed("// it's fine\nconsole.log(`a ${'b'}`)"));
    }
}
