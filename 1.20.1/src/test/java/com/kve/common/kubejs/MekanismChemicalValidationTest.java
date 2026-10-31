package com.kve.common.kubejs;

import com.kve.common.recipetype.ChemicalRegistryHelper;
import com.kve.common.recipetype.RecipeTypeDescriptor;
import com.kve.common.recipetype.RecipeTypeDescriptorRegistry;
import com.kve.common.recipe.RecipeData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Los quimicos de Mekanism (gas, slurry...) se validan contra SU registro, no
 * contra el de fluidos de Forge. Los registros se simulan: estos tests no
 * necesitan Mekanism instalado.
 */
class MekanismChemicalValidationTest {

    @BeforeEach
    void fakeMekanismChemicals() {
        ChemicalRegistryHelper.setFakeRegistryForTests(Map.of(
                "gas", Set.of("mekanism:plutonium", "mekanism:hydrogen_chloride"),
                "infuse_type", Set.of("mekanism:redstone")));
    }

    @AfterEach
    void restore() {
        ChemicalRegistryHelper.setFakeRegistryForTests(null);
    }

    @Test
    void gasIsAcceptedFromTheChemicalRegistry() {
        assertTrue(SemanticValidator.validateFluidOrChemicalId("mekanism:plutonium", "Input", List.of("gas")).isEmpty());
    }

    @Test
    void unknownChemicalIsStillRejected() {
        List<String> errs = SemanticValidator.validateFluidOrChemicalId("mekanism:no_existe", "Input", List.of("gas"));
        assertEquals(1, errs.size(), errs.toString());
    }

    @Test
    void gasIdIsNotAcceptedAsInfusionTypeWhenTheDescriptorSaysGas() {
        assertFalse(SemanticValidator.validateFluidOrChemicalId("mekanism:redstone", "Input", List.of("gas")).isEmpty());
        assertTrue(SemanticValidator.validateFluidOrChemicalId("mekanism:redstone", "Input", List.of("gas", "infuse_type")).isEmpty());
    }

    @Test
    void chemicalKindsComeFromTheDescriptorNotFromTheRecipe() {
        RecipeTypeDescriptor injecting = RecipeTypeDescriptorRegistry.find("mekanism:injecting").orElseThrow();
        assertEquals(List.of("gas"), SemanticValidator.chemicalKindsOf(injecting));
    }

    @Test
    void injectingRecipeWithPlutoniumGasPassesValidation() {
        RecipeData r = new RecipeData();
        r.recipeId = "inject_plutonium";
        r.type = RecipeData.RecipeType.CUSTOM_MACHINE;
        r.customTypeId = "mekanism:injecting";
        RecipeData.MachineSlot in = new RecipeData.MachineSlot();
        in.type = RecipeData.MachineSlotType.ITEM;
        in.item = new ItemStack(Items.DIRT);
        r.machineInputs.add(in);
        RecipeData.MachineSlot gas = new RecipeData.MachineSlot();
        gas.type = RecipeData.MachineSlotType.FLUID;
        gas.fluidId = "mekanism:plutonium";
        gas.fluidAmount = 1;
        r.machineInputs.add(gas);
        RecipeData.MachineSlot out = new RecipeData.MachineSlot();
        out.item = new ItemStack(Items.AMETHYST_SHARD);
        r.machineOutputs.add(out);
        r.customExtraJson = "\"inputGas\": \"mekanism:plutonium\", \"inputGasAmount\": 1";
        List<String> errs = KubeJSExporter.validateRecipe(r);
        assertTrue(errs.stream().noneMatch(e -> e.contains("plutonium")), errs.toString());
    }
}
