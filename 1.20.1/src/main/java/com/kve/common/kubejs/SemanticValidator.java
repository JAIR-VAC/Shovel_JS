package com.kve.common.kubejs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kve.common.recipetype.ChemicalRegistryHelper;
import com.kve.common.recipetype.RecipeTypeDescriptor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class SemanticValidator {
    private SemanticValidator() {}

    private static String msg(String key, Object... args) {
        try {
            return com.kve.common.Lang.get(key, args);
        } catch (Throwable t) {
            if (args == null || args.length == 0) return key;
            return key + " " + java.util.Arrays.toString(args);
        }
    }

    private static final Pattern NAMESPACE_PATH = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** True if the item registry actually has entries in it right now - false very early in loading, or in a plain unit test with no game bootstrap at all. */
    public static boolean itemRegistryReady() {
        try { return !BuiltInRegistries.ITEM.keySet().isEmpty(); }
        catch (Throwable t) { return false; }
    }

    public static boolean fluidRegistryReady() {
        try { return !BuiltInRegistries.FLUID.keySet().isEmpty(); }
        catch (Throwable t) { return false; }
    }

    public static boolean entityRegistryReady() {
        try { return !BuiltInRegistries.ENTITY_TYPE.keySet().isEmpty(); }
        catch (Throwable t) { return false; }
    }

    /** Bare {@code namespace:path} check, no registry lookup - used for tag ids, recipe/serializer type ids, and anything else that's a resource location but isn't backed by one single registry. */
    public static List<String> validateNamespacedIdFormat(String id, String label) {
        List<String> errors = new ArrayList<>();
        if (id == null || id.isBlank()) { errors.add(msg("gui.kve.validation.id_empty", label)); return errors; }
        String bare = id.startsWith("#") ? id.substring(1) : id;
        if (!NAMESPACE_PATH.matcher(bare).matches()) {
            errors.add(msg("gui.kve.validation.bad_format", label, id));
        }
        return errors;
    }

    public static List<String> validateItemId(String id, String label) {
        return validateRegistryId(id, label, msg("gui.kve.validation.kind.item"), SemanticValidator::itemRegistryReady,
                rl -> BuiltInRegistries.ITEM.containsKey(rl));
    }

    public static List<String> validateFluidId(String id, String label) {
        return validateRegistryId(id, label, msg("gui.kve.validation.kind.fluid"), SemanticValidator::fluidRegistryReady,
                rl -> BuiltInRegistries.FLUID.containsKey(rl));
    }

    /**
     * Quimicos de Mekanism (gas, slurry, pigment, infuse_type) que un descriptor
     * declara en sus slots y tanques. Vacio = el descriptor solo usa fluidos de Forge.
     */
    public static List<String> chemicalKindsOf(RecipeTypeDescriptor descriptor) {
        List<String> kinds = new ArrayList<>();
        if (descriptor == null || descriptor.slotLayout == null) return kinds;
        for (RecipeTypeDescriptor.SlotDef s : descriptor.slotLayout.slots) addKinds(kinds, s.chemical);
        for (RecipeTypeDescriptor.FieldTank tank : descriptor.slotLayout.fieldTanks) addKinds(kinds, tank.chemical);
        return kinds;
    }

    private static void addKinds(List<String> into, String spec) {
        for (String k : ChemicalRegistryHelper.parseKinds(spec)) if (!into.contains(k)) into.add(k);
    }

    /**
     * Un id que puede ser un fluido de Forge o un quimico de Mekanism (gas,
     * slurry...). {@code kinds} son los quimicos que el descriptor declara para
     * esa receta; con lista vacia solo se busca en fluidos. Un quimico de
     * Mekanism NO es un fluido de Forge (plutonio, desechos nucleares, slurries),
     * por eso buscarlo solo en fluidos marcaba recetas validas como inexistentes.
     */
    public static List<String> validateFluidOrChemicalId(String id, String label, List<String> kinds) {
        List<String> errors = new ArrayList<>();
        if (id == null || id.isBlank()) { errors.add(msg("gui.kve.validation.id_empty", label)); return errors; }
        if (!NAMESPACE_PATH.matcher(id).matches()) {
            errors.add(msg("gui.kve.validation.bad_format", label, id));
            return errors;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) { errors.add(msg("gui.kve.validation.bad_format", label, id)); return errors; }
        List<String> known = new ArrayList<>();
        for (String k : kinds) if (ChemicalRegistryHelper.available(k)) known.add(k);
        boolean fluidsReady = fluidRegistryReady();
        if (fluidsReady && BuiltInRegistries.FLUID.containsKey(rl)) return errors;
        for (String k : known) if (ChemicalRegistryHelper.contains(k, id)) return errors;
        if (com.kve.common.search.DeclaredIds.isDeclared(id)) return errors;
        if (!fluidsReady && known.isEmpty()) return errors;
        String kindLabel = kinds.isEmpty() ? msg("gui.kve.validation.kind.fluid") : msg("gui.kve.validation.kind.chemical");
        errors.add(msg("gui.kve.validation.not_found", label, kindLabel, id));
        return errors;
    }

    public static List<String> validateEntityId(String id, String label) {
        return validateRegistryId(id, label, msg("gui.kve.validation.kind.entity"), SemanticValidator::entityRegistryReady,
                rl -> BuiltInRegistries.ENTITY_TYPE.containsKey(rl));
    }

    public static boolean blockRegistryReady() {
        try { return !BuiltInRegistries.BLOCK.keySet().isEmpty(); }
        catch (Throwable t) { return false; }
    }

    public static List<String> validateBlockId(String id, String label) {
        return validateRegistryId(id, label, msg("gui.kve.validation.kind.block"), SemanticValidator::blockRegistryReady,
                rl -> BuiltInRegistries.BLOCK.containsKey(rl));
    }

    /** {@code MobEffect} is a Forge-registered registry (not one of the vanilla {@code BuiltInRegistries}), so it's checked against {@code ForgeRegistries.MOB_EFFECTS} instead of the {@code BuiltInRegistries}-based helper the other id kinds share. */
    public static boolean effectRegistryReady() {
        try { return !net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getEntries().isEmpty(); }
        catch (Throwable t) { return false; }
    }

    public static List<String> validateEffectId(String id, String label) {
        List<String> errors = new ArrayList<>();
        if (id == null || id.isBlank()) { errors.add(msg("gui.kve.validation.id_empty", label)); return errors; }
        if (!NAMESPACE_PATH.matcher(id).matches()) {
            errors.add(msg("gui.kve.validation.bad_format", label, id));
            return errors;
        }
        if (!effectRegistryReady()) return errors;
        try {
            ResourceLocation rl = new ResourceLocation(id);
            if (!net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.containsKey(rl)) {
                errors.add(msg("gui.kve.validation.effect_not_found", label, id));
            }
        } catch (Exception ex) {
            errors.add(msg("gui.kve.validation.invalid_id", label, id, ex.getMessage()));
        }
        return errors;
    }

    public static List<String> validateLootTableId(String id, String label) {
        List<String> errors = new ArrayList<>();
        if (id == null || id.isBlank()) { errors.add(msg("gui.kve.validation.id_empty", label)); return errors; }
        errors.addAll(validateNamespacedIdFormat(id, label));
        if (!errors.isEmpty()) return errors;
        List<String> known;
        try {
            known = LootTableExporter.listAllLootTableIds();
        } catch (Throwable t) {
            return errors;
        }
        if (known.isEmpty()) return errors;
        if (!known.contains(id)) {
            errors.add(msg("gui.kve.validation.loot_table_not_found", label, id));
        }
        return errors;
    }

    private interface ReadyCheck { boolean ready(); }
    private interface ContainsCheck { boolean contains(ResourceLocation rl); }

    private static List<String> validateRegistryId(String id, String label, String kindLabel,
            ReadyCheck ready, ContainsCheck contains) {
        List<String> errors = new ArrayList<>();
        if (id == null || id.isBlank()) { errors.add(msg("gui.kve.validation.id_empty", label)); return errors; }
        if (!NAMESPACE_PATH.matcher(id).matches()) {
            errors.add(msg("gui.kve.validation.bad_format", label, id));
            return errors;
        }
        if (!ready.ready()) return errors;
        try {
            ResourceLocation rl = new ResourceLocation(id);
            if (!contains.contains(rl) && !com.kve.common.search.DeclaredIds.isDeclared(id)) {
                errors.add(msg("gui.kve.validation.not_found", label, kindLabel, id));
            }
        } catch (Exception ex) {
            errors.add(msg("gui.kve.validation.invalid_id", label, id, ex.getMessage()));
        }
        return errors;
    }

    public static List<String> validateTagId(String tagId, String label) {
        return validateNamespacedIdFormat(tagId, label);
    }

    /** Parses {@code snbt} with the game's own {@link TagParser}. A blank/null value is not an error - it just means "no NBT", which is always valid. */
    public static List<String> validateNbt(String snbt, String label) {
        List<String> errors = new ArrayList<>();
        if (snbt == null || snbt.isBlank()) return errors;
        try {
            TagParser.parseTag(snbt);
        } catch (Exception ex) {
            errors.add(msg("gui.kve.validation.nbt_invalid", label, ex.getMessage()));
        }
        return errors;
    }

    /**
     * Validates a recipe's freeform "extra JSON" box: must parse as a JSON
     * object, and, if {@code descriptor} is non-null (i.e. this recipe's
     * type is a KNOWN type with a declared field schema), every field the
     * descriptor declares that's actually present in the JSON must match
     * its declared type. An unknown recipe type (no descriptor) only gets
     * the generic "is it a JSON object" check - there's no schema to check
     * field types against.
     */
    public static List<String> validateExtraJson(String extraJson, RecipeTypeDescriptor descriptor, String label) {
        List<String> errors = new ArrayList<>();
        if (extraJson == null || extraJson.isBlank()) return errors;
        String toParse = extraJson.trim().startsWith("{") ? extraJson.trim() : "{" + extraJson.trim() + "}";
        JsonElement el;
        try {
            el = JsonParser.parseString(toParse);
        } catch (Exception ex) {
            errors.add(msg("gui.kve.validation.json_invalid", label, ex.getMessage()));
            return errors;
        }
        if (!el.isJsonObject()) { errors.add(msg("gui.kve.validation.not_json_object", label)); return errors; }
        if (descriptor == null) return errors;

        JsonObject obj = el.getAsJsonObject();
        for (RecipeTypeDescriptor.ExtraField f : descriptor.extraFields) {
            if (f.key == null || f.key.isBlank() || !obj.has(f.key)) continue;
            JsonElement v = obj.get(f.key);
            if (f.repeatable) {
                if (!v.isJsonArray()) {
                    errors.add(msg("gui.kve.validation.field_list", label, f.key));
                    continue;
                }
                for (JsonElement item : v.getAsJsonArray()) errors.addAll(validateExtraFieldValue(item, f, label));
            } else {
                errors.addAll(validateExtraFieldValue(v, f, label));
            }
        }
        return errors;
    }

    private static List<String> validateExtraFieldValue(JsonElement v, RecipeTypeDescriptor.ExtraField f, String label) {
        List<String> errors = new ArrayList<>();
        String type = f.type == null ? "string" : f.type;
        switch (type) {
            case "int", "long" -> {
                if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber())
                    errors.add(msg("gui.kve.validation.field_int", label, f.key));
            }
            case "float", "double" -> {
                if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber())
                    errors.add(msg("gui.kve.validation.field_number", label, f.key));
            }
            case "boolean" -> {
                if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean())
                    errors.add(msg("gui.kve.validation.field_boolean", label, f.key));
            }
            case "item" -> {
                if (!v.isJsonPrimitive()) errors.add(msg("gui.kve.validation.field_item", label, f.key));
                else errors.addAll(validateItemId(v.getAsString(), label + "." + f.key));
            }
            case "fluid_id" -> {
                if (!v.isJsonPrimitive()) errors.add(msg("gui.kve.validation.field_fluid", label, f.key));
                else errors.addAll(validateFluidOrChemicalId(v.getAsString(), label + "." + f.key,
                        ChemicalRegistryHelper.parseKinds(f.chemical)));
            }
            default -> {
                if (!v.isJsonPrimitive()) errors.add(msg("gui.kve.validation.field_string", label, f.key));
                else if (!ChemicalRegistryHelper.parseKinds(f.chemical).isEmpty())
                    errors.addAll(validateFluidOrChemicalId(v.getAsString(), label + "." + f.key,
                            ChemicalRegistryHelper.parseKinds(f.chemical)));
            }
        }
        return errors;
    }
}
