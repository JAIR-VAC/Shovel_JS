package com.kve.common.recipetype;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class RecipeTypeSlotLayoutStaticQaTest {

    private static final String[] BUNDLED_FILES = {
            "minecraft.json", "mekanism.json", "create.json", "farmersdelight.json", "tconstruct.json",
            "immersiveengineering.json", "thermal.json", "arsnouveau.json", "goety.json",
            "bloodmagic.json", "botania.json", "botanypots.json", "industrialforegoing.json",
            "powah.json", "projecte.json", "garnished.json", "totemic.json",
            "twilightforest.json", "aether.json", "deep_aether.json", "cataclysm.json", "apotheosis.json",
            "ad_astra.json", "draconicevolution.json", "irons_spellbooks.json", "vinery.json",
            "refurbished_furniture.json", "simpleplanes.json", "celestisynth.json",
    };
    private static final String RESOURCE_ROOT = "/data/shovel_js/recipe_types/";

    private static final Pattern INPUT_TOKEN =
            Pattern.compile("\\{(input|inputs|machineInput|machineInputs" +
                    "|machineInputTuple|machineInputTuple2|machineInputsTuple)\\}");
    private static final Pattern OUTPUT_TOKEN =
            Pattern.compile("\\{(output|outputs|machineOutput|machineOutputs" +
                    "|machineOutputTuple|machineOutputTuple2|machineOutputsTuple)\\}");

    @AfterEach
    void cleanup() {
        RecipeTypeDescriptorRegistry.setModLoadedCheckForTests(null);
    }

    private Set<String> idsWithSlotLayoutInRawJson() throws IOException {
        Gson gson = new Gson();
        Set<String> ids = new LinkedHashSet<>();
        for (String file : BUNDLED_FILES) {
            try (InputStream in = RecipeTypeDescriptorRegistry.class.getResourceAsStream(RESOURCE_ROOT + file)) {
                assertNotNull(in, "Missing bundled resource: " + file);
                JsonElement root = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonElement.class);
                JsonArray arr = root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
                if (root.isJsonObject()) arr.add(root);
                for (JsonElement el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    if (o.has("slotLayout")) {
                        String id = o.has("recipeSerializer") ? o.get("recipeSerializer").getAsString() : null;
                        if (id != null && !id.isBlank()) ids.add(id);
                    }
                }
            }
        }
        return ids;
    }

    @Test
    void everyDeclaredSlotLayoutSurvivesRegistryValidation() throws IOException {
        RecipeTypeDescriptorRegistry.reload();
        Set<String> declared = idsWithSlotLayoutInRawJson();
        assertFalse(declared.isEmpty(), "Sanity check: expected at least one bundled type with a slotLayout.");

        for (String id : declared) {
            RecipeTypeDescriptor d = RecipeTypeDescriptorRegistry.find(id).orElse(null);
            assertNotNull(d, "recipeSerializer disappeared entirely after reload(): " + id);
            assertTrue(d.hasSlotLayout(),
                    "'" + id + "' declares slotLayout in its JSON but the registry dropped it " +
                    "(malformed block, or it failed hasAtLeastOneOfEachRole()) - check the logs " +
                    "parseSlotLayout() would have emitted.");
            assertTrue(d.slotLayout.hasAtLeastOneOfEachRole() || (d.noOutput && d.slotLayout.hasOnlyInputs()),
                    "'" + id + "' has a slotLayout with only one role - needs both an input and an output slot.");
        }
    }

    @Test
    void sameRowFlagMatchesRawJson() throws IOException {
        RecipeTypeDescriptorRegistry.reload();
        Gson gson = new Gson();
        int checked = 0;
        for (String file : BUNDLED_FILES) {
            try (InputStream in = RecipeTypeDescriptorRegistry.class.getResourceAsStream(RESOURCE_ROOT + file)) {
                assertNotNull(in, "Missing bundled resource: " + file);
                JsonElement root = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonElement.class);
                JsonArray arr = root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
                if (root.isJsonObject()) arr.add(root);
                for (JsonElement el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    if (!o.has("slotLayout")) continue;
                    JsonObject lo = o.getAsJsonObject("slotLayout");
                    if (!lo.has("sameRow")) continue;
                    boolean expected = lo.get("sameRow").getAsBoolean();
                    String id = o.get("recipeSerializer").getAsString();
                    RecipeTypeDescriptor d = RecipeTypeDescriptorRegistry.find(id).orElse(null);
                    assertNotNull(d, "recipeSerializer disappeared after reload(): " + id);
                    assertNotNull(d.slotLayout, "'" + id + "' lost its slotLayout entirely: " + id);
                    assertEquals(expected, d.slotLayout.sameRow,
                            "'" + id + "' declares sameRow=" + expected + " in " + file +
                            " but the parsed descriptor has sameRow=" + d.slotLayout.sameRow);
                    checked++;
                }
            }
        }
        assertTrue(checked > 0, "Sanity check: expected at least one bundled entry declaring \"sameRow\".");
    }

    @Test
    void heatSourceHintFlagMatchesRawJson() throws IOException {
        RecipeTypeDescriptorRegistry.reload();
        Gson gson = new Gson();
        int checked = 0;
        for (String file : BUNDLED_FILES) {
            try (InputStream in = RecipeTypeDescriptorRegistry.class.getResourceAsStream(RESOURCE_ROOT + file)) {
                assertNotNull(in, "Missing bundled resource: " + file);
                JsonElement root = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonElement.class);
                JsonArray arr = root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
                if (root.isJsonObject()) arr.add(root);
                for (JsonElement el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    if (!o.has("slotLayout")) continue;
                    JsonObject lo = o.getAsJsonObject("slotLayout");
                    if (!lo.has("heatSourceHint")) continue;
                    boolean expected = lo.get("heatSourceHint").getAsBoolean();
                    String id = o.get("recipeSerializer").getAsString();
                    RecipeTypeDescriptor d = RecipeTypeDescriptorRegistry.find(id).orElse(null);
                    assertNotNull(d, "recipeSerializer disappeared after reload(): " + id);
                    assertNotNull(d.slotLayout, "'" + id + "' lost its slotLayout entirely: " + id);
                    assertEquals(expected, d.slotLayout.heatSourceHint,
                            "'" + id + "' declares heatSourceHint=" + expected + " in " + file +
                            " but the parsed descriptor has heatSourceHint=" + d.slotLayout.heatSourceHint);
                    checked++;
                }
            }
        }
        assertTrue(checked > 0, "Sanity check: expected at least one bundled entry declaring \"heatSourceHint\" (farmersdelight:cooking).");
    }

    @Test
    void decorItemsAndNotesMatchRawJson() throws IOException {
        RecipeTypeDescriptorRegistry.reload();
        Gson gson = new Gson();
        int checked = 0;
        for (String file : BUNDLED_FILES) {
            try (InputStream in = RecipeTypeDescriptorRegistry.class.getResourceAsStream(RESOURCE_ROOT + file)) {
                assertNotNull(in, "Missing bundled resource: " + file);
                JsonElement root = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonElement.class);
                JsonArray arr = root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
                if (root.isJsonObject()) arr.add(root);
                for (JsonElement el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    if (!o.has("slotLayout")) continue;
                    JsonObject lo = o.getAsJsonObject("slotLayout");
                    if (!lo.has("decorItems") && !lo.has("notes")) continue;
                    String id = o.get("recipeSerializer").getAsString();
                    RecipeTypeDescriptor d = RecipeTypeDescriptorRegistry.find(id).orElse(null);
                    assertNotNull(d, "recipeSerializer disappeared after reload(): " + id);
                    assertNotNull(d.slotLayout, "'" + id + "' lost its slotLayout entirely");
                    int decl = lo.has("decorItems") ? lo.getAsJsonArray("decorItems").size() : 0;
                    int notes = lo.has("notes") ? lo.getAsJsonArray("notes").size() : 0;
                    assertEquals(decl, d.slotLayout.decorItems.size(), "'" + id + "' decorItems parsed != declared in " + file);
                    assertEquals(notes, d.slotLayout.notes.size(), "'" + id + "' notes parsed != declared in " + file);
                    assertTrue(decl == 0 || d.slotLayout.unified, "'" + id + "' decorItems only render on a unified (station canvas) layout");
                    checked++;
                }
            }
        }
        assertTrue(checked > 0, "Sanity check: expected at least one bundled entry declaring decorItems/notes.");
    }

    @Test
    void containerFieldSlotIsParsed() {
        RecipeTypeDescriptorRegistry.reload();
        RecipeTypeDescriptor d = RecipeTypeDescriptorRegistry.find("farmersdelight:cooking").orElse(null);
        assertNotNull(d);
        assertNotNull(d.slotLayout);
        assertNotNull(d.slotLayout.containerFieldSlot, "farmersdelight:cooking declares containerFieldSlot but it wasn't parsed");
        assertEquals("container", d.slotLayout.containerFieldSlot.extraFieldKey);
    }

    private static final Set<String> SLOT_FORMATS = Set.of("ingredient", "item_stack", "item_id", "mek_item",
            "fluid_ingredient", "fluid_stack", "chemical", "boxed_chemical", "create_ingredient", "create_result",
            "if_fluid", "amount", "chance",
            "block", "id_stack", "forge_fluid", "astra_fluid", "counted_ingredient", "de_catalyst",
            "entity_id", "astra_fluid_ingredient", "apoth_salvage",
            "wrapped_ingredient", "ars_crush_result", "bm_added_output",
            "irons_fluid",
            "ie_ingredient", "ie_output", "bp_drop", "thermal_ingredient", "thermal_output",
            "tc_entity", "weighted_entity");
    private static final Set<String> FIELD_FORMATS = Set.of("", "ingredient", "item_stack", "if_fluid",
            "chemical_ingredient", "create_fluid", "block_ingredient", "list",
            "ie_output", "state_ingredient", "thermal_fluid", "fluid_in");

    private static void collectPlaceholders(JsonElement e, java.util.List<String> out) {
        if (e == null || e.isJsonNull()) return;
        if (e.isJsonObject()) { for (var en : e.getAsJsonObject().entrySet()) collectPlaceholders(en.getValue(), out); return; }
        if (e.isJsonArray()) { for (JsonElement el : e.getAsJsonArray()) collectPlaceholders(el, out); return; }
        if (e.getAsJsonPrimitive().isString() && e.getAsString().startsWith("$")) out.add(e.getAsString());
    }

    @Test
    void rawJsonPlaceholdersReferenceRealSlotsAndFields() {
        RecipeTypeDescriptorRegistry.reload();
        int checked = 0;
        for (RecipeTypeDescriptor d : RecipeTypeDescriptorRegistry.all()) {
            if (!d.hasRawJson()) continue;
            java.util.List<String> phs = new java.util.ArrayList<>();
            collectPlaceholders(d.rawJson, phs);
            for (String raw : new java.util.ArrayList<>(phs)) {
                int alt = raw.indexOf("||");
                if (alt >= 0 && raw.startsWith("$", alt + 2)) phs.add(raw.substring(alt + 2));
            }
            Set<String> slotIds = new LinkedHashSet<>();
            if (d.slotLayout != null) for (RecipeTypeDescriptor.SlotDef s : d.slotLayout.slots) slotIds.add(s.id);
            Set<String> fieldKeys = new LinkedHashSet<>();
            for (RecipeTypeDescriptor.ExtraField f : d.extraFields) fieldKeys.add(f.key);
            for (String raw : phs) {
                String ph = raw.contains("||") ? raw.substring(0, raw.indexOf("||")) : raw;
                String[] p = ph.substring(1).split(":");
                String where = "'" + d.recipeSerializer + "' rawJson placeholder " + raw;
                switch (p[0]) {
                    case "slot" -> {
                        assertTrue(slotIds.contains(p[1]), where + ": unknown slot id");
                        assertTrue(SLOT_FORMATS.contains(p.length > 2 ? p[2] : "ingredient"), where + ": unknown slot format");
                    }
                    case "slotKind" -> assertTrue(slotIds.contains(p[1]), where + ": unknown slot id");
                    case "inputs", "outputs", "input", "output" -> {
                        assertTrue(SLOT_FORMATS.contains(p.length > 1 ? p[1] : "ingredient"), where + ": unknown slot format");
                        if (p.length > 2) assertTrue(slotIds.contains(p[2]), where + ": excluded slot id not in slotLayout");
                    }
                    case "inputAt", "outputAt" -> {
                        assertTrue(p.length > 1 && p[1].matches("\\d+"), where + ": index must be a non-negative integer");
                        assertTrue(SLOT_FORMATS.contains(p.length > 2 ? p[2] : "ingredient"), where + ": unknown slot format");
                    }
                    case "field" -> {
                        assertTrue(fieldKeys.contains(p[1]), where + ": field not declared in extraFields");
                        assertTrue(FIELD_FORMATS.contains(p.length > 2 ? p[2] : ""), where + ": unknown field format");
                        String amountKey = p.length > 2 && p[2].equals("chemical_ingredient") ? (p.length > 4 ? p[4] : null)
                                : p.length > 3 && !p[2].isEmpty() ? p[3] : null;
                        if (amountKey != null && !amountKey.isBlank()) {
                            assertTrue(fieldKeys.contains(amountKey), where + ": amount field not declared in extraFields");
                        }
                    }
                    case "recipeId" -> { }
                    default -> fail(where + ": unknown placeholder kind");
                }
            }
            if (d.slotLayout != null) {
                for (RecipeTypeDescriptor.FieldTank t : d.slotLayout.fieldTanks) {
                    assertTrue(fieldKeys.contains(t.idField), "'" + d.recipeSerializer + "' fieldTank idField not declared: " + t.idField);
                    if (t.hasAmountField()) assertTrue(fieldKeys.contains(t.amountField), "'" + d.recipeSerializer + "' fieldTank amountField not declared: " + t.amountField);
                }
                if (d.slotLayout.energyBar != null && d.slotLayout.energyBar.hasField()) {
                    assertTrue(fieldKeys.contains(d.slotLayout.energyBar.field), "'" + d.recipeSerializer + "' energyBar field not declared");
                }
            }
            checked++;
        }
        assertTrue(checked >= 40, "Sanity check: expected the fluid/electric catalogs to carry rawJson templates, found " + checked);
    }

    @Test
    void slotLayoutRolesMatchDedicatedCallPlaceholders() {
        RecipeTypeDescriptorRegistry.reload();

        for (RecipeTypeDescriptor d : RecipeTypeDescriptorRegistry.all()) {
            if (!d.hasSlotLayout() || d.dedicatedCall == null) continue;
            String tmpl = d.dedicatedCall.argsTemplate;
            if (tmpl == null || tmpl.isBlank()) continue;

            boolean layoutHasInput = d.slotLayout.slots.stream().anyMatch(s -> !s.isOutput());
            boolean layoutHasOutput = d.slotLayout.slots.stream().anyMatch(RecipeTypeDescriptor.SlotDef::isOutput);
            boolean templateHasInput = INPUT_TOKEN.matcher(tmpl).find();
            boolean templateHasOutput = OUTPUT_TOKEN.matcher(tmpl).find();

            if (layoutHasInput) {
                assertTrue(templateHasInput,
                        "'" + d.recipeSerializer + "' slotLayout has an input slot but its dedicatedCall " +
                        "argsTemplate has no {input}/{inputs}/{machineInput(s)} token: " + tmpl +
                        " - this is exactly the CAT-C mistake Fase 4 found and fixed by hand " +
                        "(see Shovel JS-recipe-grid-compat-spec.txt, 'Correcciones encontradas').");
            }
            if (layoutHasOutput) {
                assertTrue(templateHasOutput,
                        "'" + d.recipeSerializer + "' slotLayout has an output slot but its dedicatedCall " +
                        "argsTemplate has no {output}/{outputs}/{machineOutput(s)} token: " + tmpl);
            }
        }
    }
}
