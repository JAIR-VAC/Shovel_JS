package com.kve.common.kubejs;

import com.kve.common.recipetype.RecipeTypeDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SemanticValidatorTest {

    @Test
    void registryReadyChecksReflectTheRegistriesActualState() {
        assertEquals(!net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().isEmpty(),
                SemanticValidator.itemRegistryReady());
        assertEquals(!net.minecraft.core.registries.BuiltInRegistries.FLUID.keySet().isEmpty(),
                SemanticValidator.fluidRegistryReady());
        assertEquals(!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet().isEmpty(),
                SemanticValidator.entityRegistryReady());
        assertEquals(!net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet().isEmpty(),
                SemanticValidator.blockRegistryReady());
    }

    @Test
    void wellFormedItemIdPassesWhenRegistryIsNotReady() {
        List<String> errors = SemanticValidator.validateItemId("minecraft:diamond", "Output");
        assertTrue(errors.isEmpty(), "format is fine and the registry can't be checked, so this should pass: " + errors);
    }

    @Test
    void blankItemIdFails() {
        List<String> errors = SemanticValidator.validateItemId("", "Output");
        assertFalse(errors.isEmpty());
    }

    @Test
    void malformedItemIdFailsFormatCheckRegardlessOfRegistry() {
        assertFalse(SemanticValidator.validateItemId("not_namespaced", "Output").isEmpty());
        assertFalse(SemanticValidator.validateItemId("Weird:CaseId", "Output").isEmpty());
        assertFalse(SemanticValidator.validateItemId("minecraft:has space", "Output").isEmpty());
    }

    @Test
    void tagIdAcceptsOptionalHashPrefix() {
        assertTrue(SemanticValidator.validateTagId("forge:ingots", "Tag").isEmpty());
        assertTrue(SemanticValidator.validateTagId("#forge:ingots", "Tag").isEmpty());
        assertFalse(SemanticValidator.validateTagId("#not a tag", "Tag").isEmpty());
        assertFalse(SemanticValidator.validateTagId("", "Tag").isEmpty());
    }

    @Test
    void blankNbtIsNotAnError() {
        assertTrue(SemanticValidator.validateNbt(null, "NBT").isEmpty());
        assertTrue(SemanticValidator.validateNbt("", "NBT").isEmpty());
        assertTrue(SemanticValidator.validateNbt("   ", "NBT").isEmpty());
    }

    @Test
    void wellFormedNbtParses() {
        assertTrue(SemanticValidator.validateNbt("{Damage:0,display:{Name:'\"Test\"'}}", "NBT").isEmpty());
    }

    @Test
    void malformedNbtFails() {
        List<String> errors = SemanticValidator.validateNbt("{this is not valid nbt", "NBT");
        assertFalse(errors.isEmpty());
    }

    @Test
    void blankExtraJsonIsNotAnError() {
        assertTrue(SemanticValidator.validateExtraJson(null, null, "Extra JSON").isEmpty());
        assertTrue(SemanticValidator.validateExtraJson("", null, "Extra JSON").isEmpty());
    }

    @Test
    void extraJsonMustBeAnObject() {
        List<String> errors = SemanticValidator.validateExtraJson("[1, 2, 3]", null, "Extra JSON");
        assertFalse(errors.isEmpty());
    }

    @Test
    void extraJsonAcceptsBareFieldsWithoutOuterBraces() {
        List<String> errors = SemanticValidator.validateExtraJson("\"processingTime\": 100", null, "Extra JSON");
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test
    void malformedExtraJsonFails() {
        List<String> errors = SemanticValidator.validateExtraJson("{ not: valid, json", null, "Extra JSON");
        assertFalse(errors.isEmpty());
    }

    @Test
    void unknownRecipeTypeOnlyGetsTheGenericObjectCheck() {
        List<String> errors = SemanticValidator.validateExtraJson("{ \"anything\": \"goes\" }", null, "Extra JSON");
        assertTrue(errors.isEmpty());
    }

    @Test
    void knownRecipeTypeChecksDeclaredFieldTypes() {
        RecipeTypeDescriptor d = new RecipeTypeDescriptor();
        RecipeTypeDescriptor.ExtraField f = new RecipeTypeDescriptor.ExtraField();
        f.key = "processingTime";
        f.type = "int";
        d.extraFields.add(f);

        assertTrue(SemanticValidator.validateExtraJson("{ \"processingTime\": 100 }", d, "Extra JSON").isEmpty());

        List<String> errors = SemanticValidator.validateExtraJson("{ \"processingTime\": \"soon\" }", d, "Extra JSON");
        assertFalse(errors.isEmpty(), "a string where an int is declared should fail");
    }

    @Test
    void knownRecipeTypeChecksBooleanFields() {
        RecipeTypeDescriptor d = new RecipeTypeDescriptor();
        RecipeTypeDescriptor.ExtraField f = new RecipeTypeDescriptor.ExtraField();
        f.key = "keepHeldItem";
        f.type = "boolean";
        d.extraFields.add(f);

        assertTrue(SemanticValidator.validateExtraJson("{ \"keepHeldItem\": true }", d, "Extra JSON").isEmpty());
        assertFalse(SemanticValidator.validateExtraJson("{ \"keepHeldItem\": \"yes\" }", d, "Extra JSON").isEmpty());
    }

    @Test
    void repeatableFieldMustBeAnArray() {
        RecipeTypeDescriptor d = new RecipeTypeDescriptor();
        RecipeTypeDescriptor.ExtraField f = new RecipeTypeDescriptor.ExtraField();
        f.key = "extraCatalysts";
        f.type = "item";
        f.repeatable = true;
        d.extraFields.add(f);

        assertTrue(SemanticValidator.validateExtraJson(
                "{ \"extraCatalysts\": [\"minecraft:redstone\", \"minecraft:glowstone_dust\"] }", d, "Extra JSON").isEmpty());

        List<String> errors = SemanticValidator.validateExtraJson(
                "{ \"extraCatalysts\": \"minecraft:redstone\" }", d, "Extra JSON");
        assertFalse(errors.isEmpty(), "a scalar where a repeatable field expects a list should fail");
    }

    @Test
    void fieldsAbsentFromTheJsonAreNotEnforcedHere() {
        RecipeTypeDescriptor d = new RecipeTypeDescriptor();
        RecipeTypeDescriptor.ExtraField f = new RecipeTypeDescriptor.ExtraField();
        f.key = "processingTime";
        f.type = "int";
        f.required = true;
        d.extraFields.add(f);

        assertTrue(SemanticValidator.validateExtraJson("{}", d, "Extra JSON").isEmpty());
    }
}
