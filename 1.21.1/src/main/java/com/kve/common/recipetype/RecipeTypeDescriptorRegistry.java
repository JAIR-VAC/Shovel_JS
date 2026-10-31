package com.kve.common.recipetype;

import com.google.gson.*;
import com.kve.CraftEditorMod;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Loads and holds every known {@link RecipeTypeDescriptor}, keyed by
 * {@code recipeSerializer} id (the same string used as
 * {@code RecipeData#customTypeId}).
 *
 * Two sources are merged, exactly like a datapack overlay:
 *   1. Bundled defaults shipped inside this mod's jar, at
 *      {@code /data/kve/recipe_types/*.json} (classpath resources) - a
 *      small starter set for a few very common addons (see the bundled
 *      files for the actual list), so KVE is useful out of the box.
 *   2. User/pack-provided files under
 *      {@code kubejs/data/kve/recipe_types/*.json} on disk - anyone can
 *      add support for another mod's recipe type by dropping in a new
 *      JSON file with the schema documented on {@link RecipeTypeDescriptor},
 *      no Java/compiling required, in the same spirit as the
 *      kubejs-more-recipe-types KubeJS addon. Entries on disk OVERRIDE a
 *      bundled entry with the same recipeSerializer id, so a modpack can
 *      also use this to correct/extend a bundled descriptor.
 *
 * Each file may contain either a single descriptor object, or a JSON
 * array of descriptor objects.
 */
public final class RecipeTypeDescriptorRegistry {

    private RecipeTypeDescriptorRegistry() {}

    public static final Path USER_DIR = Paths.get("kubejs", "data", "shovel_js", "recipe_types");
    private static final String BUNDLED_RESOURCE_ROOT = "/data/shovel_js/recipe_types/";
    private static final String[] BUNDLED_FILES = {
            "minecraft.json",
            "mekanism.json",
            "create.json",
            "farmersdelight.json",
            "tconstruct.json",
            "immersiveengineering.json",
            "thermal.json",
            "arsnouveau.json",
            "goety.json",
            "bloodmagic.json",
            "botania.json",
            "botanypots.json",
            "industrialforegoing.json",
            "powah.json",
            "projecte.json",
            "garnished.json",
            "totemic.json",
            "twilightforest.json",
            "aether.json",
            "deep_aether.json",
            "cataclysm.json",
            "apotheosis.json",
            "ad_astra.json",
            "draconicevolution.json",
            "irons_spellbooks.json",
            "vinery.json",
            "refurbished_furniture.json",
            "simpleplanes.json",
            "celestisynth.json",
    };

    private static final Gson GSON = new Gson();
    private static volatile Map<String, RecipeTypeDescriptor> BY_SERIALIZER = new LinkedHashMap<>();
    /**
     * copia cruda (deep-copied) del
     * {@link JsonObject} tal cual se parseo para cada recipeSerializer,
     * bundled o de usuario. Guardada aparte del {@link RecipeTypeDescriptor}
     * tipado porque {@link #saveStationItemOverride()} necesita re-escribir
     * TODOS los campos originales
     * para no perder datos al crear/actualizar un override de usuario -
     * ver el javadoc de esa clase (los archivos de USER_DIR reemplazan la
     * entrada entera por id, no hacen deep-merge).
     */
    private static volatile Map<String, JsonObject> RAW_BY_SERIALIZER = new LinkedHashMap<>();
    private static Map<String, RecipeTypeDescriptor> buildingBy;
    private static Map<String, JsonObject> buildingRaw;
    private static boolean loaded = false;

    private static java.util.function.Predicate<String> modLoadedCheck = RecipeTypeDescriptorRegistry::isModLoadedViaForge;

    private static boolean isModLoadedViaForge(String modid) {
        try { return ModList.get().isLoaded(modid); }
        catch (Exception e) { return false; }
    }

    /**
     * Test-only hook: overrides how {@link #shouldUseDedicatedCall()} and
     * {@link #addonFlagIsOnlyBlocker()} answer "is this Forge mod loaded",
     * bypassing {@link ModList} entirely. Pass {@code null} to restore the
     * real ModList-backed check. Production code (the actual mod running
     * in-game) never calls this - only tests should.
     */
    public static void setModLoadedCheckForTests(java.util.function.Predicate<String> check) {
        modLoadedCheck = check != null ? check : RecipeTypeDescriptorRegistry::isModLoadedViaForge;
    }

    public static boolean isModLoadedViaForgePublic(String modId) {
        if (modId == null || modId.isEmpty()) return true;
        return modLoadedCheck.test(modId);
    }

    /**
     * Nombre "de display" real del mod segun Forge (ej. "mekanism" ->
     * "Mekanism"), para la cabecera de cada seccion del picker. Devuelve
     * null si el mod no esta cargado o ModList no esta disponible (tests
     * JUnit sin bootstrap de Forge) - el llamador cae entonces a
     * capitalizar el modid a mano.
     */
    public static String prettyModNamePublic(String modId) {
        try {
            return ModList.get().getModContainerById(modId)
                    .map(c -> c.getModInfo().getDisplayName())
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    public static synchronized void loadIfNeeded() {
        if (loaded) return;
        reload();
    }

    public static synchronized void reload() {
        loaded = true;
        buildingBy = new LinkedHashMap<>();
        buildingRaw = new LinkedHashMap<>();
        int bundledCount = loadBundled();
        int userCount = loadUserFiles();
        BY_SERIALIZER = buildingBy;
        RAW_BY_SERIALIZER = buildingRaw;
        buildingBy = null;
        buildingRaw = null;
        Set<String> addonLabels = new LinkedHashSet<>();
        for (RecipeTypeDescriptor d : BY_SERIALIZER.values()) {
            if (d.dedicatedCall != null && d.dedicatedCall.requiresAddon != null && !d.dedicatedCall.requiresAddon.isBlank()) {
                addonLabels.add(d.dedicatedCall.requiresAddon);
            }
        }
        KveAddonFlags.ensureKnown(addonLabels);
        CraftEditorMod.LOGGER.info("[Shovel JS] Recipe type descriptors loaded: {} bundled, {} user/pack, {} total (after overrides).",
                bundledCount, userCount, BY_SERIALIZER.size());
    }

    private static int loadBundled() {
        int count = 0;
        for (String fileName : BUNDLED_FILES) {
            String resourcePath = BUNDLED_RESOURCE_ROOT + fileName;
            try (InputStream in = RecipeTypeDescriptorRegistry.class.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] Bundled recipe type descriptor missing from jar: {}", resourcePath);
                    continue;
                }
                try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    count += parseAndRegister(JsonParser.parseReader(r), "bundled:" + fileName);
                }
            } catch (Exception e) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Failed to load bundled recipe type descriptor {}: {}", resourcePath, e.getMessage());
            }
        }
        return count;
    }

    private static int loadUserFiles() {
        int count = 0;
        if (!Files.isDirectory(USER_DIR)) {
            writeExampleFile();
            return 0;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(USER_DIR, "*.json")) {
            for (Path p : ds) {
                try {
                    String json = Files.readString(p, StandardCharsets.UTF_8);
                    count += parseAndRegister(JsonParser.parseString(json), p.getFileName().toString());
                } catch (Exception e) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] Failed to load recipe type descriptor {}: {}", p.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not scan {}: {}", USER_DIR, e.getMessage());
        }
        return count;
    }

    private static int parseAndRegister(JsonElement root, String sourceLabel) {
        int count = 0;
        if (root.isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray()) {
                if (registerOne(el, sourceLabel)) count++;
            }
        } else if (root.isJsonObject()) {
            if (registerOne(root, sourceLabel)) count++;
        }
        return count;
    }

    private static boolean registerOne(JsonElement el, String sourceLabel) {
        try {
            RecipeTypeDescriptor d = fromJson(el.getAsJsonObject());
            if (d.recipeSerializer == null || d.recipeSerializer.isBlank()) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped a recipe type descriptor in {} with no recipeSerializer.", sourceLabel);
                return false;
            }
            buildingBy.put(d.recipeSerializer, d);
            buildingRaw.put(d.recipeSerializer, el.getAsJsonObject().deepCopy());
            return true;
        } catch (Exception e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Malformed recipe type descriptor in {}: {}", sourceLabel, e.getMessage());
            return false;
        }
    }

    static RecipeTypeDescriptor fromJson(JsonObject o) {
        RecipeTypeDescriptor d = new RecipeTypeDescriptor();
        d.recipeSerializer = str(o, "recipeSerializer", "");
        d.displayName       = str(o, "displayName", "");
        d.modId             = str(o, "modId", "");
        d.stationItem       = str(o, "stationItem", "");
        d.unsupported       = o.has("unsupported") && o.get("unsupported").getAsBoolean();
        d.unsupportedReason = str(o, "unsupportedReason", "");
        d.noOutput          = o.has("noOutput") && o.get("noOutput").getAsBoolean();
        d.noIngredients     = o.has("noIngredients") && o.get("noIngredients").getAsBoolean();
        d.idFromOutput      = o.has("idFromOutput") && o.get("idFromOutput").getAsBoolean();

        if (o.has("extraFields")) {
            for (JsonElement fel : o.getAsJsonArray("extraFields")) {
                JsonObject fo = fel.getAsJsonObject();
                RecipeTypeDescriptor.ExtraField f = new RecipeTypeDescriptor.ExtraField();
                f.key          = str(fo, "key", "");
                f.type         = str(fo, "type", "string");
                f.defaultValue = str(fo, "default", "");
                f.label        = str(fo, "label", "");
                f.required     = fo.has("required") && fo.get("required").getAsBoolean();
                f.repeatable   = fo.has("repeatable") && fo.get("repeatable").getAsBoolean();
                if (fo.has("options")) {
                    java.util.List<String> opts = new java.util.ArrayList<>();
                    for (JsonElement oe : fo.getAsJsonArray("options")) {
                        String v = oe.getAsString();
                        if (v != null && !v.isBlank()) opts.add(v);
                    }
                    if (!opts.isEmpty()) f.options = opts;
                }
                if (!f.key.isBlank()) d.extraFields.add(f);
            }
        }

        if (o.has("dedicatedCall")) {
            JsonObject co = o.getAsJsonObject("dedicatedCall");
            RecipeTypeDescriptor.DedicatedCall c = new RecipeTypeDescriptor.DedicatedCall();
            c.requiresMod   = str(co, "requiresMod", "");
            c.requiresAddon = str(co, "requiresAddon", "");
            c.argsTemplate  = str(co, "argsTemplate", "");
            c.preferOverRawJson = !co.has("preferOverRawJson") || co.get("preferOverRawJson").getAsBoolean();
            c.skipAutoId = co.has("skipAutoId") && co.get("skipAutoId").getAsBoolean();
            if (co.has("extraFieldCallTemplates")) {
                JsonObject t = co.getAsJsonObject("extraFieldCallTemplates");
                for (Map.Entry<String, JsonElement> e : t.entrySet())
                    c.extraFieldCallTemplates.put(e.getKey(), e.getValue().getAsString());
            }
            if ((!c.requiresMod.isBlank() || !c.requiresAddon.isBlank()) && !c.argsTemplate.isBlank()) d.dedicatedCall = c;
        }

        if (o.has("rawJson")) {
            if (o.get("rawJson").isJsonObject()) d.rawJson = o.getAsJsonObject("rawJson").deepCopy();
            else CraftEditorMod.LOGGER.warn("[Shovel JS] Ignored non-object \"rawJson\" for {}.", d.recipeSerializer);
        }

        d.slotLayout = parseSlotLayout(o, d.recipeSerializer);
        if (d.slotLayout != null && !d.slotLayout.hasAtLeastOneOfEachRole() && !d.noOutput) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Ignored slotLayout for {}: only input slots, but the type is not noOutput.", d.recipeSerializer);
            d.slotLayout = null;
        }
        d.stationStructure = parseStationStructure(o, d.recipeSerializer);
        d.gridWidth  = parseGridDim(o, "gridWidth", d.recipeSerializer);
        d.gridHeight = parseGridDim(o, "gridHeight", d.recipeSerializer);
        d.editorView = parseEditorView(o, d.recipeSerializer);
        d.ritualView = d.isRitualView() ? parseRitualView(o, d.recipeSerializer) : null;
        return d;
    }

    private static final Set<String> KNOWN_EDITOR_VIEWS = Set.of("ritual_ring", "node_grid");

    private static String parseEditorView(JsonObject o, String recipeSerializer) {
        String v = str(o, "editorView", "");
        if (v.isBlank()) return "";
        if (!KNOWN_EDITOR_VIEWS.contains(v.toLowerCase(Locale.ROOT))) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Recipe type descriptor {} has an unknown \"editorView\" ({}) - ignoring, falling back to the normal editor.", recipeSerializer, v);
            return "";
        }
        return v.toLowerCase(Locale.ROOT);
    }

    private static RecipeTypeDescriptor.RitualViewSettings parseRitualView(JsonObject o, String recipeSerializer) {
        RecipeTypeDescriptor.RitualViewSettings rv = new RecipeTypeDescriptor.RitualViewSettings();
        if (!o.has("ritualView")) return rv;
        try {
            JsonObject ro = o.getAsJsonObject("ritualView");
            if (ro.has("maxOfferings")) {
                try {
                    int m = ro.get("maxOfferings").getAsInt();
                    rv.maxOfferings = Math.max(1, Math.min(16, m));
                } catch (Exception e) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] Recipe type descriptor {} has a non-numeric ritualView.maxOfferings - using default 8.", recipeSerializer);
                }
            }
            rv.centerField   = str(ro, "centerField", "");
            rv.centerSlotId  = str(ro, "centerSlotId", "");
            rv.ringFromInputs = !ro.has("ringFromInputs") || ro.get("ringFromInputs").getAsBoolean();
            if (ro.has("positions") && ro.get("positions").isJsonArray()) {
                for (com.google.gson.JsonElement pe : ro.getAsJsonArray("positions")) {
                    if (!pe.isJsonArray() || pe.getAsJsonArray().size() < 2) continue;
                    rv.positions.add(new int[]{pe.getAsJsonArray().get(0).getAsInt(), pe.getAsJsonArray().get(1).getAsInt()});
                    if (rv.positions.size() >= 16) break;
                }
                if (rv.hasPositions()) rv.maxOfferings = rv.positions.size();
            }
        } catch (Exception e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Malformed ritualView for {}, using defaults: {}", recipeSerializer, e.getMessage());
        }
        return rv;
    }

    private static int parseGridDim(JsonObject o, String key, String recipeSerializer) {
        if (!o.has(key)) return 0;
        JsonElement el = o.get(key);
        try {
            if (!el.isJsonPrimitive()) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Recipe type descriptor {} has a non-numeric \"{}\" ({}) - falling back to the generic grid dims.",
                        recipeSerializer, key, el);
                return 0;
            }
            int v = el.getAsInt();
            if (v <= 0) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Recipe type descriptor {} has \"{}\" = {} (must be > 0) - falling back to the generic grid dims.",
                        recipeSerializer, key, v);
                return 0;
            }
            return Math.min(v, 32);
        } catch (Exception e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Recipe type descriptor {} has an unparsable \"{}\" ({}): {} - falling back to the generic grid dims.",
                    recipeSerializer, key, el, e.getMessage());
            return 0;
        }
    }

    /**
     * Parses the optional {@code "slotLayout"} block. Returns {@code null}
     * (never throws) whenever the block is missing, malformed, or doesn't
     * describe at least one input and one output slot - in every one of
     * those cases {@code MachineRecipeEditScreen} falls back to its
     * existing generic grid exactly as if this feature didn't exist, so a
     * bad/typo'd pack JSON can only lose the custom layout, never break
     * the recipe type or crash the editor.
     */
    private static List<RecipeTypeDescriptor.StationBlock> parseStationStructure(JsonObject o, String recipeSerializer) {
        List<RecipeTypeDescriptor.StationBlock> out = new ArrayList<>();
        if (!o.has("stationStructure") || !o.get("stationStructure").isJsonArray()) return out;
        for (JsonElement el : o.getAsJsonArray("stationStructure")) {
            try {
                JsonObject b = el.getAsJsonObject();
                JsonArray pos = b.getAsJsonArray("pos");
                RecipeTypeDescriptor.StationBlock sb = new RecipeTypeDescriptor.StationBlock();
                sb.x = pos.get(0).getAsInt();
                sb.y = pos.get(1).getAsInt();
                sb.z = pos.get(2).getAsInt();
                sb.block = str(b, "block", "");
                sb.state = str(b, "state", "");
                if (sb.block.contains(":") && (sb.x != 0 || sb.y != 0 || sb.z != 0)) out.add(sb);
            } catch (Exception e) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Ignored malformed stationStructure entry for {}: {}", recipeSerializer, e.getMessage());
            }
        }
        return out;
    }

    private static RecipeTypeDescriptor.SlotLayout parseSlotLayout(JsonObject o, String recipeSerializer) {
        if (!o.has("slotLayout")) return null;
        try {
            JsonObject lo = o.getAsJsonObject("slotLayout");
            RecipeTypeDescriptor.SlotLayout layout = new RecipeTypeDescriptor.SlotLayout();
            if (lo.has("slots")) {
                int i = 0;
                for (JsonElement sel : lo.getAsJsonArray("slots")) {
                    JsonObject so = sel.getAsJsonObject();
                    RecipeTypeDescriptor.SlotDef sd = new RecipeTypeDescriptor.SlotDef();
                    sd.id         = str(so, "id", "");
                    sd.role       = str(so, "role", "input");
                    sd.type       = str(so, "type", "item");
                    sd.x          = so.has("x") ? so.get("x").getAsInt() : 0;
                    sd.y          = so.has("y") ? so.get("y").getAsInt() : 0;
                    sd.required   = so.has("required") && so.get("required").getAsBoolean();
                    sd.repeatable = so.has("repeatable") && so.get("repeatable").getAsBoolean();
                    sd.tank       = so.has("tank") && so.get("tank").getAsBoolean();
                    sd.chemical   = str(so, "chemical", "");
                    sd.h          = so.has("h") ? Math.max(0, so.get("h").getAsInt()) : 0;
                    sd.label      = str(so, "label", "");
                    if (sd.isValid()) {
                        sd.id = sd.effectiveId(i);
                        layout.slots.add(sd);
                    } else {
                        CraftEditorMod.LOGGER.warn("[Shovel JS] Dropped invalid slotLayout slot #{} for {}: bad role/x/y.", i, recipeSerializer);
                    }
                    i++;
                }
            }
            layout.unified = lo.has("unified") && lo.get("unified").getAsBoolean();
            layout.sameRow = lo.has("sameRow") && lo.get("sameRow").getAsBoolean();
            layout.heatSourceHint = lo.has("heatSourceHint") && lo.get("heatSourceHint").getAsBoolean();
            if (lo.has("arrow")) {
                try {
                    JsonObject ao = lo.getAsJsonObject("arrow");
                    RecipeTypeDescriptor.Arrow arrow = new RecipeTypeDescriptor.Arrow();
                    arrow.x = ao.has("x") ? ao.get("x").getAsInt() : 0;
                    arrow.y = ao.has("y") ? ao.get("y").getAsInt() : 0;
                    arrow.direction = str(ao, "direction", "right");
                    layout.arrow = arrow;
                } catch (Exception e) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] Ignored malformed slotLayout.arrow for {}: {}", recipeSerializer, e.getMessage());
                }
            }
            if (lo.has("containerFieldSlot")) {
                try {
                    JsonObject co = lo.getAsJsonObject("containerFieldSlot");
                    RecipeTypeDescriptor.ContainerFieldSlot cfs = new RecipeTypeDescriptor.ContainerFieldSlot();
                    cfs.x = co.has("x") ? co.get("x").getAsInt() : 0;
                    cfs.y = co.has("y") ? co.get("y").getAsInt() : 0;
                    cfs.extraFieldKey = str(co, "extraFieldKey", "");
                    cfs.label = str(co, "label", "");
                    if (!cfs.extraFieldKey.isBlank()) layout.containerFieldSlot = cfs;
                    else CraftEditorMod.LOGGER.warn("[Shovel JS] Ignored slotLayout.containerFieldSlot for {}: missing extraFieldKey.", recipeSerializer);
                } catch (Exception e) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] Ignored malformed slotLayout.containerFieldSlot for {}: {}", recipeSerializer, e.getMessage());
                }
            }
            if (lo.has("fieldTanks")) {
                try {
                    for (JsonElement tel : lo.getAsJsonArray("fieldTanks")) {
                        JsonObject to = tel.getAsJsonObject();
                        RecipeTypeDescriptor.FieldTank ft = new RecipeTypeDescriptor.FieldTank();
                        ft.x = to.has("x") ? to.get("x").getAsInt() : 0;
                        ft.y = to.has("y") ? to.get("y").getAsInt() : 0;
                        ft.h = to.has("h") ? Math.max(0, to.get("h").getAsInt()) : 0;
                        ft.idField = str(to, "idField", "");
                        ft.amountField = str(to, "amountField", "");
                        ft.chemical = str(to, "chemical", "");
                        ft.role = str(to, "role", "input");
                        ft.label = str(to, "label", "");
                        if (!ft.idField.isBlank() && ft.x >= 0 && ft.y >= 0) layout.fieldTanks.add(ft);
                        else CraftEditorMod.LOGGER.warn("[Shovel JS] Dropped a slotLayout.fieldTanks entry for {}: missing idField or negative x/y.", recipeSerializer);
                    }
                } catch (Exception e) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] Ignored malformed slotLayout.fieldTanks for {}: {}", recipeSerializer, e.getMessage());
                }
            }
            if (lo.has("energyBar")) {
                try {
                    JsonObject eo = lo.getAsJsonObject("energyBar");
                    RecipeTypeDescriptor.EnergyBar eb = new RecipeTypeDescriptor.EnergyBar();
                    eb.x = eo.has("x") ? eo.get("x").getAsInt() : 0;
                    eb.y = eo.has("y") ? eo.get("y").getAsInt() : 0;
                    eb.w = eo.has("w") ? Math.max(2, eo.get("w").getAsInt()) : eb.w;
                    eb.h = eo.has("h") ? Math.max(2, eo.get("h").getAsInt()) : eb.h;
                    eb.field = str(eo, "field", "");
                    eb.unit = str(eo, "unit", "FE");
                    layout.energyBar = eb;
                } catch (Exception e) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] Ignored malformed slotLayout.energyBar for {}: {}", recipeSerializer, e.getMessage());
                }
            }
            if (lo.has("decorItems")) {
                try {
                    for (JsonElement del : lo.getAsJsonArray("decorItems")) {
                        JsonObject dob = del.getAsJsonObject();
                        RecipeTypeDescriptor.DecorItem di = new RecipeTypeDescriptor.DecorItem();
                        di.x = dob.has("x") ? dob.get("x").getAsInt() : 0;
                        di.y = dob.has("y") ? dob.get("y").getAsInt() : 0;
                        di.item = str(dob, "item", "");
                        di.label = str(dob, "label", "");
                        if (!di.item.isBlank() && di.x >= 0 && di.y >= 0) layout.decorItems.add(di);
                        else CraftEditorMod.LOGGER.warn("[Shovel JS] Dropped a slotLayout.decorItems entry for {}: missing item or negative x/y.", recipeSerializer);
                    }
                } catch (Exception e) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] Ignored malformed slotLayout.decorItems for {}: {}", recipeSerializer, e.getMessage());
                }
            }
            if (lo.has("notes")) {
                try {
                    for (JsonElement nel : lo.getAsJsonArray("notes")) {
                        JsonObject no = nel.getAsJsonObject();
                        RecipeTypeDescriptor.CanvasNote cn = new RecipeTypeDescriptor.CanvasNote();
                        cn.x = no.has("x") ? no.get("x").getAsInt() : 0;
                        cn.y = no.has("y") ? no.get("y").getAsInt() : 0;
                        cn.text = str(no, "text", "");
                        String col = str(no, "color", "");
                        if (!col.isBlank()) {
                            try { cn.color = (int) Long.parseLong(col.startsWith("#") ? col.substring(1) : col, 16) | 0xFF000000; }
                            catch (NumberFormatException ignored) {}
                        }
                        if (!cn.text.isBlank() && cn.x >= 0 && cn.y >= 0) layout.notes.add(cn);
                    }
                } catch (Exception e) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] Ignored malformed slotLayout.notes for {}: {}", recipeSerializer, e.getMessage());
                }
            }
            if (!layout.hasAtLeastOneOfEachRole() && !layout.hasOnlyInputs()) {
                if (!layout.slots.isEmpty()) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] Ignored slotLayout for {}: needs at least one input and one output slot. Falling back to the generic grid.", recipeSerializer);
                }
                return null;
            }
            return layout;
        } catch (Exception e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Malformed slotLayout for {}, falling back to the generic grid: {}", recipeSerializer, e.getMessage());
            return null;
        }
    }

    private static String str(JsonObject o, String k, String def) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def; }
        catch (Exception e) { return def; }
    }

    private static void writeExampleFile() {
        try {
            Files.createDirectories(USER_DIR);
            RecipeTypeDescriptor example = new RecipeTypeDescriptor();
            example.recipeSerializer = "examplemod:example_type";
            example.displayName = "Example Mod - Example Type";
            RecipeTypeDescriptor.ExtraField f1 = new RecipeTypeDescriptor.ExtraField();
            f1.key = "processingTime"; f1.type = "int"; f1.defaultValue = "100"; f1.label = "Processing Time (ticks)";
            example.extraFields.add(f1);
            RecipeTypeDescriptor.ExtraField f2 = new RecipeTypeDescriptor.ExtraField();
            f2.key = "extraCatalysts"; f2.type = "item"; f2.defaultValue = "minecraft:redstone";
            f2.label = "Extra Catalysts"; f2.repeatable = true;
            example.extraFields.add(f2);
            RecipeTypeDescriptor.DedicatedCall call = new RecipeTypeDescriptor.DedicatedCall();
            call.requiresAddon = "some-kubejs-addon-i-confirmed-i-have";
            call.argsTemplate = "event.recipes.examplemod.example_type({output}, {input}){extra}";
            call.extraFieldCallTemplates.put("processingTime", ".processingTime({value})");
            call.extraFieldCallTemplates.put("extraCatalysts", ".catalyst({value})");
            example.dedicatedCall = call;
            example.gridWidth = 4;
            example.gridHeight = 4;
            example.stationItem = "examplemod:example_station";
            RecipeTypeDescriptor.SlotLayout layout = new RecipeTypeDescriptor.SlotLayout();
            layout.unified = true;
            RecipeTypeDescriptor.SlotDef in1 = new RecipeTypeDescriptor.SlotDef();
            in1.id = "main_input"; in1.role = "input"; in1.type = "item"; in1.x = 0; in1.y = 0; in1.required = true;
            RecipeTypeDescriptor.SlotDef in2 = new RecipeTypeDescriptor.SlotDef();
            in2.id = "fluid_input"; in2.role = "input"; in2.type = "fluid"; in2.x = 0; in2.y = 24; in2.tank = true;
            RecipeTypeDescriptor.SlotDef out1 = new RecipeTypeDescriptor.SlotDef();
            out1.id = "output_item"; out1.role = "output"; out1.type = "item"; out1.x = 60; out1.y = 0; out1.required = true;
            layout.slots.add(in1); layout.slots.add(in2); layout.slots.add(out1);
            RecipeTypeDescriptor.Arrow arrow = new RecipeTypeDescriptor.Arrow();
            arrow.x = 38; arrow.y = 0; arrow.direction = "right";
            layout.arrow = arrow;
            example.slotLayout = layout;

            JsonArray arr = new JsonArray();
            arr.add(toJsonForExample(example));
            Files.writeString(USER_DIR.resolve("_example.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(arr),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            CraftEditorMod.LOGGER.info("[Shovel JS] Wrote example recipe type descriptor to {}", USER_DIR.resolve("_example.json"));
        } catch (IOException e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not write example recipe type descriptor: {}", e.getMessage());
        }
    }

    private static JsonObject toJsonForExample(RecipeTypeDescriptor d) {
        JsonObject o = new JsonObject();
        o.addProperty("recipeSerializer", d.recipeSerializer);
        o.addProperty("displayName", d.displayName);
        if (d.hasStationItem()) o.addProperty("stationItem", d.stationItem);
        JsonArray fields = new JsonArray();
        for (RecipeTypeDescriptor.ExtraField f : d.extraFields) {
            JsonObject fo = new JsonObject();
            fo.addProperty("key", f.key);
            fo.addProperty("type", f.type);
            fo.addProperty("default", f.defaultValue);
            fo.addProperty("label", f.label);
            if (f.repeatable) fo.addProperty("repeatable", true);
            if (f.hasOptions()) {
                JsonArray opts = new JsonArray();
                for (String v : f.options) opts.add(v);
                fo.add("options", opts);
            }
            fields.add(fo);
        }
        o.add("extraFields", fields);
        if (d.dedicatedCall != null) {            JsonObject co = new JsonObject();
            co.addProperty("requiresMod", d.dedicatedCall.requiresMod);
            co.addProperty("requiresAddon", d.dedicatedCall.requiresAddon);
            co.addProperty("argsTemplate", d.dedicatedCall.argsTemplate);
            co.addProperty("preferOverRawJson", d.dedicatedCall.preferOverRawJson);
            if (d.dedicatedCall.skipAutoId) co.addProperty("skipAutoId", true);
            JsonObject t = new JsonObject();
            for (Map.Entry<String, String> e : d.dedicatedCall.extraFieldCallTemplates.entrySet())
                t.addProperty(e.getKey(), e.getValue());
            co.add("extraFieldCallTemplates", t);
            o.add("dedicatedCall", co);
        }
        if (d.hasCustomGrid()) {
            o.addProperty("gridWidth", d.gridWidth);
            o.addProperty("gridHeight", d.gridHeight);
        }
        if (d.slotLayout != null) {
            JsonObject lo = new JsonObject();
            JsonArray slotsArr = new JsonArray();
            for (RecipeTypeDescriptor.SlotDef sd : d.slotLayout.slots) {
                JsonObject so = new JsonObject();
                so.addProperty("id", sd.id);
                so.addProperty("role", sd.role);
                so.addProperty("type", sd.type);
                so.addProperty("x", sd.x);
                so.addProperty("y", sd.y);
                if (sd.required) so.addProperty("required", true);
                if (sd.repeatable) so.addProperty("repeatable", true);
                if (sd.tank) so.addProperty("tank", true);
                slotsArr.add(so);
            }
            lo.add("slots", slotsArr);
            if (d.slotLayout.unified) lo.addProperty("unified", true);
            if (d.slotLayout.arrow != null) {
                JsonObject ao = new JsonObject();
                ao.addProperty("x", d.slotLayout.arrow.x);
                ao.addProperty("y", d.slotLayout.arrow.y);
                ao.addProperty("direction", d.slotLayout.arrow.direction);
                lo.add("arrow", ao);
            }
            o.add("slotLayout", lo);
        }
        return o;
    }

    public static synchronized boolean saveStationItemOverride(String recipeSerializer, String newStationItem) {
        loadIfNeeded();
        if (recipeSerializer == null || recipeSerializer.isBlank()
                || newStationItem == null || newStationItem.isBlank()) return false;
        JsonObject raw = RAW_BY_SERIALIZER.get(recipeSerializer);
        if (raw == null) return false;
        JsonObject patched = raw.deepCopy();
        patched.addProperty("stationItem", newStationItem);

        Path target = USER_DIR.resolve(sanitizeFileName(namespaceOf(recipeSerializer)) + ".json");
        try {
            Files.createDirectories(USER_DIR);
            JsonArray arr = new JsonArray();
            if (Files.isRegularFile(target)) {
                JsonElement existing = JsonParser.parseString(Files.readString(target, StandardCharsets.UTF_8));
                if (existing.isJsonArray()) {
                    arr = existing.getAsJsonArray();
                } else if (existing.isJsonObject()) {
                    arr.add(existing.getAsJsonObject());
                }
            }
            boolean replaced = false;
            for (int i = 0; i < arr.size(); i++) {
                JsonElement el = arr.get(i);
                if (el.isJsonObject() && recipeSerializer.equals(str(el.getAsJsonObject(), "recipeSerializer", ""))) {
                    arr.set(i, patched);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) arr.add(patched);

            Files.writeString(target,
                    new GsonBuilder().setPrettyPrinting().create().toJson(arr),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            CraftEditorMod.LOGGER.info("[Shovel JS] Station picker: stationItem={} guardado para {} -> {}",
                    newStationItem, recipeSerializer, target);
        } catch (Exception e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Station picker: no se pudo guardar {} para {}: {}",
                    newStationItem, recipeSerializer, e.getMessage());
            return false;
        }
        reload();
        return true;
    }

    private static String namespaceOf(String recipeSerializer) {
        int i = recipeSerializer.indexOf(':');
        return i > 0 ? recipeSerializer.substring(0, i) : recipeSerializer;
    }

    private static String sanitizeFileName(String modId) {
        return (modId == null || modId.isBlank()) ? "unknown" : modId.replaceAll("[^a-z0-9_\\-]", "_");
    }

    public static Optional<RecipeTypeDescriptor> find(String recipeSerializer) {
        loadIfNeeded();
        if (recipeSerializer == null) return Optional.empty();
        return Optional.ofNullable(BY_SERIALIZER.get(recipeSerializer));
    }

    public static Collection<RecipeTypeDescriptor> all() {
        loadIfNeeded();
        return Collections.unmodifiableCollection(BY_SERIALIZER.values());
    }

    private static final Map<String, String> KNOWN_ADDON_MODIDS = Map.of(
            "kubejs-goety", "kubejs_goety"
    );

    private static boolean addonEnabled(String addon) {
        String modid = KNOWN_ADDON_MODIDS.get(addon);
        if (modid != null && modLoadedCheck.test(modid)) return true;
        return KveAddonFlags.isEnabled(addon);
    }

    /** True if this descriptor's dedicated call should be used right now: every gate it specifies passes, and it opts in. A descriptor with neither requiresMod nor requiresAddon set never qualifies - there'd be nothing gating it. */
    public static boolean shouldUseDedicatedCall(RecipeTypeDescriptor d) {
        if (d == null || d.dedicatedCall == null || !d.dedicatedCall.preferOverRawJson) return false;
        if (d.hasNativeExport()) return false;
        String mod = d.dedicatedCall.requiresMod;
        String addon = d.dedicatedCall.requiresAddon;
        if ((mod == null || mod.isBlank()) && (addon == null || addon.isBlank())) return false;
        if (mod != null && !mod.isBlank() && !modLoadedCheck.test(mod)) return false;
        if (addon != null && !addon.isBlank() && !addonEnabled(addon)) return false;
        return true;
    }

    public static boolean addonFlagIsOnlyBlocker(RecipeTypeDescriptor d) {
        if (d == null || d.dedicatedCall == null || !d.dedicatedCall.preferOverRawJson) return false;
        if (d.hasNativeExport()) return false;
        String addon = d.dedicatedCall.requiresAddon;
        if (addon == null || addon.isBlank() || addonEnabled(addon)) return false;
        String mod = d.dedicatedCall.requiresMod;
        if (mod != null && !mod.isBlank() && !modLoadedCheck.test(mod)) return false;
        return true;
    }

    /** The Forge modid a descriptor's native raw JSON belongs to: the dedicated call's requiresMod when set, else the descriptor's own mod id/namespace. */
    public static String nativeModId(RecipeTypeDescriptor d) {
        if (d == null) return "";
        if (d.dedicatedCall != null && d.dedicatedCall.requiresMod != null && !d.dedicatedCall.requiresMod.isBlank()) {
            return d.dedicatedCall.requiresMod;
        }
        return d.effectiveModId();
    }

    /** True if the mod owning this descriptor's native recipe type isn't loaded - the only thing a verified {@code rawJson} template can't compensate for. Uses the same test-safe {@link #modLoadedCheck} as the dedicated-call gates. */
    public static boolean isNativeModMissing(RecipeTypeDescriptor d) {
        String mod = nativeModId(d);
        return mod != null && !mod.isBlank() && !modLoadedCheck.test(mod);
    }

    public static boolean isRequiredModMissing(RecipeTypeDescriptor d) {
        if (d == null || d.dedicatedCall == null) return false;
        String mod = d.dedicatedCall.requiresMod;
        return mod != null && !mod.isBlank() && !modLoadedCheck.test(mod);
    }

    /**
     * Short one-line GUI status for "what will actually get written for
     * this recipe type right now" - a dedicated builder call, or the raw
     * {@code event.custom} fallback - computed fresh every call so the
     * editor screens can show it live (recomputed every frame) instead of
     * only as a one-off message when the type is first picked. Point 4 of
     * the. Returns "" if there's no descriptor for this id at all -
     * callers should simply not draw anything in that case.
     */
    public static String dedicatedCallStatusLine(String recipeTypeId) {
        var found = find(recipeTypeId);
        if (found.isEmpty()) return "";
        RecipeTypeDescriptor d = found.get();
        if (shouldUseDedicatedCall(d)) {
            return "\u2713 Se guardara con la llamada dedicada de " + d.effectiveDisplayName() + ".";
        }
        if (d.hasRawJson()) {
            if (isNativeModMissing(d)) {
                return "\u26a0 Falta el mod \"" + nativeModId(d) + "\" - la receta se guarda en su formato nativo, pero no cargara sin el mod.";
            }
            return "\u2713 Se guardara con el JSON nativo de " + nativeModId(d) + " (verificado contra el jar real; no necesita addon de KubeJS).";
        }
        if (d.dedicatedCall != null) {
            if (addonFlagIsOnlyBlocker(d)) {
                return "\u2139 Se guardara como JSON crudo - activa el addon (boton \"Habilitar\") para usar la llamada dedicada.";
            }
            String addon = d.dedicatedCall.requiresAddon;
            String mod = d.dedicatedCall.requiresMod;
            boolean addonMissing = addon != null && !addon.isBlank() && !addonEnabled(addon);
            boolean modMissing = isRequiredModMissing(d);
            if (modMissing && addonMissing) {
                return "\u26a0 Falta el mod \"" + mod + "\" y el addon de KubeJS \"" + addon
                        + "\" - sin ambos, esta receta se guarda como JSON crudo y puede no craftear.";
            }
            if (modMissing) {
                return "\u26a0 Falta el mod \"" + mod + "\" - sin el, esta receta se guarda como JSON crudo y puede no craftear.";
            }
            if (addonMissing) {
                return "\u26a0 Falta el addon de KubeJS \"" + addon + "\" - sin el, esta receta se guarda como JSON crudo y puede no craftear.";
            }
            return "\u2139 Se guardara como JSON crudo (event.custom) - llamada dedicada disponible pero no activa.";
        }
        return "";
    }
}
