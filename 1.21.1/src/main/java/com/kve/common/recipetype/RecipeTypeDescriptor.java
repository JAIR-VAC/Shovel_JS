package com.kve.common.recipetype;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes ONE recipe serializer/type ("recipeSerializer", e.g.
 * "mekanism:crushing" or "create:mixing") beyond the generic
 * ingredient/output shape KVE already understands for CUSTOM_JSON and
 * CUSTOM_MACHINE recipes:
 *
 *   - {@link #extraFields}: the extra fields this recipe type actually has
 *     (name, type, default value, human label), so the editor can show the
 *     player what's available instead of them having to know the addon's
 *     JSON schema by heart.
 *   - {@link #dedicatedCall}: if the addon that owns this recipe type
 *     exposes its own KubeJS builder function (e.g.
 *     {@code event.recipes.mekanism.crushing(...)}), this describes how to
 *     call it, so KVE can emit that INSTEAD of the generic
 *     {@code event.custom({...})} raw-JSON call when the addon is
 *     actually installed - matching how a human scripter would write it
 *     by hand, and getting the extra validation/ergonomics the addon's own
 *     builder provides.
 *
 * Instances of this class are pure data, parsed from JSON by
 * {@link RecipeTypeDescriptorRegistry} - nothing here is hardcoded per
 * mod, so adding support for a new mod's recipe type is just dropping in
 * a new JSON file (see that class's docs for the file format and folder
 * layout), the same spirit as the kubejs-more-recipe-types addon.
 */
public class RecipeTypeDescriptor {

    /** The recipe serializer/type id this describes, e.g. "mekanism:crushing". Matches RecipeData#customTypeId. */
    public String recipeSerializer = "";

    /** Human-friendly name shown in pickers, e.g. "Mekanism - Crushing". Falls back to recipeSerializer if blank. */
    public String displayName = "";

    /** Optional: which mod namespace/modid this belongs to, for grouping in pickers. Defaults to the namespace part of recipeSerializer if blank. */
    public String modId = "";

    /**
     * Optional explicit item id (e.g. {@code "create:spout"}) of the real
     * crafting station this recipe type belongs to, shown as the small
     * icon in {@code CraftEditorScreen} (see {@code drawStationIcon}/
     * {@code stationIcon}) instead of always falling back to the vanilla
     * crafting table or to {@code CraftEditorScreen}'s own keyword-guessing
     * heuristic (derives a keyword from the recipe type's path and
     * searches that mod's item registry for a matching id - works for
     * mods that name their station after their machine, but silently
     * falls back to the crafting table for anything that doesn't, e.g.
     * Create's "filling" recipe type is made by the "spout" block, which
     * shares no substring with "filling").
     * <p>
     * Same additive/non-breaking contract as {@link #gridWidth}/
     * {@link #slotLayout}: blank (the default) means "not specified" and
     * {@code CraftEditorScreen} falls back to the heuristic exactly as if
     * this field didn't exist. Set it whenever a recipe type's real
     * station doesn't share a keyword with the recipe type's own id -
     * this is the ViScriptRecipe-style explicit approach: instead of
     * guessing, the pack just says what the station is.
     */
    public String stationItem = "";

    /** Whether {@link #stationItem} was actually given a value. */
    public boolean hasStationItem() { return stationItem != null && !stationItem.isBlank(); }

    public String stationBlockState = "";

    public List<StationBlock> stationStructure = new ArrayList<>();

    /** Un bloque de {@link #stationStructure}. */
    public static class StationBlock {
        public int x, y, z;
        public String block = "";
        public String state = "";
    }

    /**
     * set true for a
     * recipe type whose real shape is confirmed (against the mod's own
     * jar/source, not guessed) to NOT fit KVE's generic
     * ingredient(s)/output raw-JSON model (customJson/customMachineJson
     * in KubeJSExporter) AND has no dedicated hand-written export method
     * either - e.g. it needs 3+ levels of nesting, an array of sub-recipes,
     * or slot data KVE's editor doesn't even have a way to enter yet. This
     * is the explicit opt-out asks for instead of ever letting
     * such a type fall through to the generic branch silently: once true,
     * {@link com.kve.common.kubejs.KubeJSExporter} refuses to export any
     * recipe of this type at all (raw JSON OR dedicated call) and emits a
     * loud comment explaining why instead, so a broken recipe never ships
     * quietly. Default false preserves every existing descriptor's
     * behavior unchanged.
     */
    public boolean unsupported = false;

    /** Human-readable reason shown in the export warning when {@link #unsupported}, e.g. what real shape the codec actually needs and why KVE can't emit it yet. Shown verbatim, so keep it short and specific. */
    public String unsupportedReason = "";

    /** Whether {@link #unsupported} is set. Named for readability at call sites, mirrors {@link #hasStationItem()}/{@link #hasCustomGrid()}. */
    public boolean isUnsupported() { return unsupported; }

    /**
     * (mods populares): el tipo no produce ningun ITEM - repara el
     * item de entrada (aether:repairing), prohibe colocarlo (placement bans)
     * o solo cambia datos (apotheosis:spawner_modifier/reforging). Sin esto
     * CraftEditorScreen exigia un item de salida para guardar y esas recetas
     * eran imposibles de crear desde el editor. Default false.
     */
    public boolean noOutput = false;

    public boolean noIngredients = false;

    /**
     * The mod looks its recipe up BY ID, and that id must equal
     * the output item's id. Ars Nouveau's Scribe's Table does this: its
     * UnlockGlyphButton calls {@code RecipeManager.byKey(spellPart.getRegistryName)}.
     * A glyph recipe saved as {@code crafteditor:<x>} therefore loads fine but
     * is never used. When true, the exporter writes {@code.id('<output id>')},
     * and because KubeJS adds its recipes after the originals, the mod's own
     * recipe is replaced. Default false.
     */
    public boolean idFromOutput = false;

    public List<ExtraField> extraFields = new ArrayList<>();

    public DedicatedCall dedicatedCall = null;

    public com.google.gson.JsonObject rawJson = null;

    /** Whether {@link #rawJson} was declared (and parsed as an object). */
    public boolean hasRawJson() { return rawJson != null && rawJson.size() > 0; }

    /**
     * Tipos que KVE exporta en el formato nativo del mod con un builder Java
     * propio (no una plantilla rawJson): no necesitan el addon de KubeJS.
     * projecte:emc_value ni siquiera es una receta - va a un archivo del
     * datapack (ver ProjectEEmcExporter).
     */
    public static final java.util.Set<String> NATIVE_JAVA_TYPES = java.util.Set.of(
            "create:mechanical_crafting", "create:sequenced_assembly",
            "twilightforest:uncrafting", "projecte:emc_value", "projecte:world_transmutation");

    /** Se exporta nativo (plantilla rawJson o builder Java propio), sin depender de un addon de KubeJS. */
    public boolean hasNativeExport() { return hasRawJson() || NATIVE_JAVA_TYPES.contains(recipeSerializer); }

    public SlotLayout slotLayout = null;

    public int gridWidth = 0;
    public int gridHeight = 0;

    /** Whether both {@link #gridWidth} and {@link #gridHeight} were given a usable (positive) size. */
    public boolean hasCustomGrid() { return gridWidth > 0 && gridHeight > 0; }

    /**
     * True if this type declares a real {@link SlotLayout} (parsed and
     * validated - see {@code RecipeTypeDescriptorRegistry#parseSlotLayout}).
     * A type with this set is meant for the dedicated typed-slot machine
     * editor ({@code MachineRecipeEditScreen}), not the generic
     * ItemStack pattern grid ({@code CraftEditorScreen}) - even if it
     * also happens to set {@link #gridWidth}/{@link #gridHeight}, those
     * two are unrelated systems and a type should really only rely on
     * one of them.
     */
    public boolean hasSlotLayout() { return slotLayout != null; }

    /**
     * Optional alternate presentation on top of (not instead of) the
     * type's normal editor - {@link #slotLayout} for
     * {@code MachineRecipeEditScreen},
     * or the plain shaped grid for {@code CraftEditorScreen}. Blank (the
     * default) means "not specified"; the two values KVE currently
     * understands are {@code "ritual_ring"} (center item + ring of
     * offerings + params panel + output, for ritual/pedestal/altar-style
     * recipe types like Goety rituals or Ars Nouveau's Enchanting
     * Apparatus - see {@link #isRitualView()}) and {@code "node_grid"}
     * (pannable/zoomable blueprint canvas for an open-ended NxN shaped
     * pattern like Create's Mechanical Crafter - see
     * {@link #isNodeGridView()}). An unknown value is treated the same as
     * blank - see {@code RecipeTypeDescriptorRegistry#parseEditorView} -
     * so a typo can only lose the alternate view, never break the type.
     */
    public String editorView = "";

    /** True if {@link #editorView} is the ritual_ring view (MachineRecipeEditScreen-side, needs {@link #slotLayout}). */
    public boolean isRitualView() { return "ritual_ring".equalsIgnoreCase(editorView); }

    /** Same check, named for readability at call sites that just want to know "should I use the ritual canvas". */
    public boolean hasRitualView() { return isRitualView(); }

    public boolean isNodeGridView() { return "node_grid".equalsIgnoreCase(editorView); }

    /**
     * Settings for the ritual_ring view (only meaningful when
     * {@link #hasRitualView()}). Never null when {@link #isRitualView()}
     * is true - the registry always fills in defaults - so callers don't
     * need a null-check beyond gating on {@link #hasRitualView()} first.
     */
    public RitualViewSettings ritualView = null;

    public String effectiveDisplayName() {
        return displayName != null && !displayName.isBlank() ? displayName : recipeSerializer;
    }

    public String effectiveModId() {
        if (modId != null && !modId.isBlank()) return modId;
        int i = recipeSerializer == null ? -1 : recipeSerializer.indexOf(':');
        return i > 0 ? recipeSerializer.substring(0, i) : "";
    }

    /** One extra field a recipe of this type has, beyond the generic ingredient(s)/output KVE already handles. */
    public static class ExtraField {
        public String key = "";
        /** One of: int, float, double, long, boolean, string, item, fluid_id. Informational for the editor UI; not enforced here. Describes ONE element's type - see {@link #repeatable}. */
        public String type = "string";
        /** Default value serialized as a string (e.g. "100", "0.5", "true", "minecraft:iron_ingot"); the GUI parses it per {@link #type}. When {@link #repeatable} is true, this is the default for a single element (the skeleton seeds a one-entry list with it). */
        public String defaultValue = "";
        /** Human label shown next to the field, e.g. "Processing Time (ticks)". Falls back to key if blank. */
        public String label = "";
        /** True if a recipe of this type is invalid without this field being set. Informational only (KVE doesn't hard-block export on it). */
        public boolean required = false;
        public boolean repeatable = false;

        /**
         * Para un campo que guarda un quimico de Mekanism (gas,
         * infuse_type, pigment, slurry; pipe-separated), igual que
         * {@link SlotDef#chemical}. Blank = no es un quimico. Lo usa
         * {@code SemanticValidator} para buscarlo en el registro de quimicos y
         * no en el de fluidos de Forge.
         */
        public String chemical = "";

        public java.util.List<String> options = java.util.List.of();

        /** Whether {@link #options} has at least one suggested value - gates whether the editor shows a cycle button for this field. */
        public boolean hasOptions() { return options != null && !options.isEmpty(); }

        public String effectiveLabel() {
            return label != null && !label.isBlank() ? label : key;
        }

        /** Whether this field's values are quoted as JSON strings (vs. emitted as bare numeric/boolean literals) based on {@link #type}. */
        public boolean isStringType() {
            return type == null || switch (type) {
                case "int", "float", "double", "long", "boolean" -> false;
                default -> true;
            };
        }

        private String elementLiteral() {
            String val = defaultValue == null ? "" : defaultValue;
            if (!isStringType()) return val.isEmpty() ? "0" : val;
            return "\"" + val + "\"";
        }

        /** The JSON literal to seed the "extra JSON" skeleton with for this field: a single value, or a one-element array if {@link #repeatable}. */
        public String skeletonValueLiteral() {
            return repeatable ? "[ " + elementLiteral() + " ]" : elementLiteral();
        }
    }

    /** Describes the addon's own dedicated KubeJS event builder for this recipe type, preferred over raw JSON when available. */
    public static class DedicatedCall {
        /**
         * Forge modid that must be loaded for this call to be valid, e.g.
         * "mekanism". Checked automatically via ModList before ever being
         * used - this is for cases where the mod being loaded is BY ITSELF
         * sufficient proof the call exists (the dedicated call lives inside
         * that same mod jar, or a jar we're confident is always paired
         * with it).
         */
        public String requiresMod = "";
        /**
         * A human-readable label for something that CANNOT be verified via
         * ModList - either because it's a separate KubeJS addon jar whose
         * exact modid isn't confirmed, or (very commonly) because it isn't
         * a Forge mod at all: several community recipe-type collections
         * (e.g. kubejs-more-recipe-types) are KJSPKG script packages, just
         *.js files dropped into kubejs/, with no modid to check. There is
         * no reliable runtime signal for these, so KVE never assumes one
         * is present - the player/pack maker opts a label in explicitly
         * once via {@code kubejs/config/kve_addon_flags.json}
         * ({@link com.kve.common.recipetype.KveAddonFlags}), defaulted to
         * off. A descriptor may set requiresMod, requiresAddon, both, or
         * (rare) neither of them as blank, in which case its dedicated
         * call is never used.
         */
        public String requiresAddon = "";
        public String argsTemplate = "";
        /**
         * Optional per-extra-field call fragment templates, keyed by the
         * extra field's `key`, e.g. {"processingTime": ".processingTime({value})"}.
         * Only fields BOTH present on the recipe's customExtraJson AND
         * listed here get appended (in {@link #extraFields} order) to
         * build {extra} in {@link #argsTemplate}.
         */
        public java.util.Map<String, String> extraFieldCallTemplates = new java.util.LinkedHashMap<>();
        /** If true (default), KVE emits this dedicated call instead of raw JSON whenever its gate(s) pass. If false, it's purely descriptive/documentation and raw JSON is always used. */
        public boolean preferOverRawJson = true;
        public boolean skipAutoId = false;
    }

    /** A custom, addon-shaped slot grid for {@link #slotLayout}. Pure data - just an ordered list of {@link SlotDef}s; all the actual rendering/hit-testing lives in {@code MachineRecipeEditScreen}. */
    public static class SlotLayout {
        public List<SlotDef> slots = new ArrayList<>();

        public boolean unified = false;

        /**
         * follow-up: when true,
         * outputs share the same row/origin as inputs (exactly like
         * {@link #unified} does), but WITHOUT turning on the
         * pannable/zoomable blueprint canvas skin {@link #unified} also
         * enables ({@code isStationCanvas} in {@code
         * MachineRecipeEditScreen} stays keyed only to {@link #unified},
         * never to this field). Suppresses the "Inputs"/"Outputs" text
         * labels entirely too - for a simple archetypal one-input/
         * one-output machine like {@code create:pressing}, the goal is a
         * single plain row - input slot, hero render, arrow, output slot,
         * no labels - matching the real mod's own GUI instead of KVE's generic two-stacked-boxes
         * look. {@link #arrow} should always be set explicitly alongside
         * this (there's no vertical gap to auto-center an arrow in
         * anymore, since both sections now occupy the same row).
         * <p>
         * False (the default) keeps every existing layout's behavior
         * unchanged, same additive contract as every other field here.
         * Ignored (treated as false) when {@link #unified} is also true -
         * {@code unified} already implies a shared origin, just with the
         * canvas skin on top.
         */
        public boolean sameRow = false;

        /**
         * (imagen de referencia de {@code
         * farmersdelight:cooking}): puramente decorativo -
         * dibuja un pequeño ícono de fuego bajo el hero de {@link
         * MachineStationPreview} para estaciones que necesitan una fuente
         * de calor externa para funcionar (Cooking Pot sobre fogata, no
         * sobre cualquier suelo). No afecta ningún dato de receta ni
         * validación - solo comunica visualmente un requisito real del
         * bloque real. False (el default) no dibuja nada, igual contrato
         * aditivo que el resto de estos campos.
         */
        public boolean heatSourceHint = false;

        /**
         * De la sesion anterior): un slot de icono
         * puramente ligado a un {@code extraField} de tipo string libre -
         * no a un {@link SlotDef} de verdad, porque el item no participa
         * de {@code ingredient}/{@code results} de la receta (va aparte,
         * como {@code container} de {@code farmersdelight:cooking}: el
         * contenedor que el resultado necesita, ej. un bowl). Null (el
         * default) no dibuja nada - mismo contrato aditivo que el resto de
         * estos campos. Cuando esta seteado, {@code MachineRecipeEditScreen}
         * dibuja un slot de item interactivo (click para tipear/pegar el
         * id, click derecho para vaciar) leyendo y escribiendo el valor
         * crudo de {@link ExtraField#key} == {@link #extraFieldKey} dentro
         * del JSON extra de la receta - no requiere que el extraField
         * declarado en el tipo sea de tipo "item", cualquier string sirve
         * (el mismo valor que ya se exporta tal cual a KubeJS).
         */
        public ContainerFieldSlot containerFieldSlot = null;

        /**
         * Optional decorative progress arrow (vanilla furnace-style),
         * purely visual - never interactive, never affects slot indices.
         * Null (the default) draws nothing, same as if this field didn't
         * exist.
         */
        public Arrow arrow = null;

        /**
         * canvas de estaciones: tanques del canvas cuyo
         * valor NO es un {@link SlotDef} sino un par de {@link ExtraField}s
         * (id + cantidad) - mismo patron que {@link #containerFieldSlot},
         * generalizado a fluidos/quimicos. Existe porque varios tipos ya
         * guardaban su gas/fluido como extra fields (ej. Mekanism
         * compressing: "inputGas"/"inputGasAmount") que sus plantillas de
         * llamada dedicada referencian via {field:KEY} - mover ese dato a un
         * slot hubiera cambiado lo que esas plantillas emiten. Asi el canvas
         * muestra el tanque real de la maquina (click = elegir, rueda =
         * cantidad) sin tocar el modelo de datos ni las plantillas. Vacio
         * por defecto.
         */
        public List<FieldTank> fieldTanks = new ArrayList<>();

        /**
         * canvas de estaciones: barra de energia
         * decorativa en la posicion real de la GUI de la maquina (Mekanism/
         * Powah/Industrial Foregoing), para que una estacion electrica se
         * reconozca como tal. Si {@link EnergyBar#field} apunta a un extra
         * field, el tooltip muestra su valor actual; si no, aclara que el
         * consumo lo define la maquina y no la receta. Null = no se dibuja.
         */
        public EnergyBar energyBar = null;

        public List<DecorItem> decorItems = new ArrayList<>();

        /**
         * canvas: textos cortos fijos en el canvas (ej.
         * "Combustible", "Bioma frio", "Clic der.: copiar") para explicar la
         * estacion real sin salir del editor. Solo en el canvas de estacion.
         */
        public List<CanvasNote> notes = new ArrayList<>();

        /** Solo hay slots de entrada (valido unicamente para tipos {@link RecipeTypeDescriptor#noOutput}: combustibles, prohibiciones, suelos...). */
        public boolean hasOnlyInputs() {
            if (slots.isEmpty()) return false;
            for (SlotDef s : slots) if (s.isOutput()) return false;
            return true;
        }

        /** A layout is only useful if it actually defines both sides of a recipe - one with only inputs (or only outputs) would leave the editor with no way to add the other, which is strictly worse than the generic grid it would otherwise replace. */
        public boolean hasAtLeastOneOfEachRole() {
            boolean hasInput = false, hasOutput = false;
            for (SlotDef s : slots) {
                if (s.isOutput()) hasOutput = true; else hasInput = true;
            }
            return hasInput && hasOutput;
        }
    }

    /**
     * A single decorative progress-arrow, positioned in the same coordinate
     * space as {@link SlotDef#x}/{@link SlotDef#y} (section-relative when
     * {@link SlotLayout#unified} is false, canvas-relative when true).
     * Modeled on vanilla's furnace arrow: a short static indicator that
     * visually connects "goes in here" to "comes out there", which is
     * present in nearly every processing-machine GUI (Create's Crushing
     * Wheels, Millstone, Mechanical Press, Farmer's Delight's Cutting
     * Board, etc.) but had no equivalent in KVE's abstract slot grid.
     */
    public static class Arrow {
        public int x = 0;
        public int y = 0;
        /** "right" or "down" - which way the arrow points. Anything else defaults to "right". */
        public String direction = "right";
    }

    /** See {@link SlotLayout#fieldTanks} javadoc. Positions use the same coordinate space as {@link SlotDef#x}/{@link SlotDef#y}. */
    public static class FieldTank {
        public int x = 0;
        public int y = 0;
        /** Tank height in px (width is always one slot). 0 = the editor's default tank height. */
        public int h = 0;
        /** Key of the {@link ExtraField} holding the fluid/chemical id. Required - a tank without it is dropped by the registry. */
        public String idField = "";
        /** Optional key of the {@link ExtraField} holding the amount (mB for fluids). Blank = the tank has no editable amount. */
        public String amountField = "";
        /** Same meaning as {@link SlotDef#chemical}: blank = Forge fluid, otherwise Mekanism chemical registry name(s) ("gas", "infuse_type", "pigment", "slurry", pipe-separated). */
        public String chemical = "";
        /** "input" or "output" - only affects color/tooltip wording, never data. */
        public String role = "input";
        /** Short caption for the tooltip, e.g. "Gas de entrada". Falls back to {@link #idField}. */
        public String label = "";

        public boolean isOutput() { return "output".equalsIgnoreCase(role); }
        public boolean hasAmountField() { return amountField != null && !amountField.isBlank(); }
    }

    /** See {@link SlotLayout#decorItems} javadoc. Same coordinate space as {@link SlotDef#x}/{@link SlotDef#y}. */
    public static class DecorItem {
        public int x = 0;
        public int y = 0;
        /** Item id to show (e.g. "aether:ambrosium_shard"). An id that doesn't resolve (mod not installed) draws an empty locked slot. */
        public String item = "";
        /** Tooltip caption, e.g. "Combustible del Altar". Falls back to the item's own name. */
        public String label = "";
    }

    /** See {@link SlotLayout#notes} javadoc. */
    public static class CanvasNote {
        public int x = 0;
        public int y = 0;
        public String text = "";
        /** ARGB color; 0 = the editor's muted label color. */
        public int color = 0;
    }

    /** See {@link SlotLayout#energyBar} javadoc. */
    public static class EnergyBar {
        public int x = 0;
        public int y = 0;
        /** Bar size in px. Vertical when h >= w (Mekanism/Powah style), horizontal otherwise (Mekanism "horizontal power bar"). */
        public int w = 6;
        public int h = 54;
        /** Optional extra field key holding the recipe's own energy value (e.g. Powah "energy"). Blank = machine-defined consumption. */
        public String field = "";
        /** Unit shown in the tooltip ("FE", "J"...). */
        public String unit = "FE";

        public boolean hasField() { return field != null && !field.isBlank(); }
    }

    /** See {@link SlotLayout#containerFieldSlot} javadoc. */
    public static class ContainerFieldSlot {
        public int x = 0;
        public int y = 0;
        /** Key of the {@link ExtraField} this slot reads/writes (e.g. "container"). Required - a slot with a blank/missing key is dropped by the registry, same tolerant-parsing contract as everything else in this file. */
        public String extraFieldKey = "";
        /** Short label drawn next to the slot (e.g. "Container"). Falls back to {@link #extraFieldKey} if blank. */
        public String label = "";
    }

    /**
     * Settings block for {@link #editorView} = {@code "ritual_ring"}
     * Pure data, parsed
     * tolerantly by the registry - a malformed field just falls back to
     * its own default rather than invalidating the whole block.
     */
    public static class RitualViewSettings {
        /** Cap on how many offering slots the ring shows/accepts. Clamped 1..16 by the registry; defaults to 8. */
        public int maxOfferings = 8;
        /**
         * If non-blank, the center item is edited as this {@link ExtraField}
         * (must be an item-typed field on this descriptor) instead of a
         * slot from {@link #slotLayout} - e.g. Goety's "activationItem" or
         * Ars Nouveau's "reagent". Takes priority over {@link #centerSlotId}
         * when both are set.
         */
        public String centerField = "";
        /**
         * If {@link #centerField} is blank, the center item instead comes
         * from the {@link SlotDef} in {@link #slotLayout} with this id
         * (the rest of that role's inputs become the ring). Ignored when
         * {@link #centerField} is set.
         */
        public String centerSlotId = "";
        public boolean ringFromInputs = true;
        /**
         * Posiciones fijas de las ofrendas, {x, y} en px respecto del centro, en el
         * MISMO orden en que el mod ubica sus ingredientes (copiadas de su vista JEI).
         * Vacio = circulo uniforme de {@link #maxOfferings}. Si hay posiciones, el
         * maximo de ofrendas es la cantidad de posiciones y el ingrediente i va a la i.
         */
        public java.util.List<int[]> positions = new java.util.ArrayList<>();

        public boolean hasPositions() { return positions != null && !positions.isEmpty(); }
        public boolean hasCenterField() { return centerField != null && !centerField.isBlank(); }
        public boolean hasCenterSlotId() { return centerSlotId != null && !centerSlotId.isBlank(); }
    }

    /**
     * One slot position in a {@link SlotLayout}. Positions ({@link #x},
     * {@link #y}) are pixel offsets from the top-left of that slot's own
     * section (the input section or the output section - the two stay
     * visually separated exactly like KVE's generic grid, only the
     * placement of slots within each section is customizable), NOT
     * absolute screen coordinates - so a layout keeps rendering correctly
     * regardless of where the editor window itself is positioned/resized.
     * When the owning {@link SlotLayout#unified} is true, both offsets are
     * instead relative to the single shared canvas origin.
     */
    public static class SlotDef {
        public String id = "";
        /** "input" or "output" - which section (and which of {@code machineInputs}/{@code machineOutputs}) this slot belongs to. Case-insensitive; anything else makes the whole slot invalid. */
        public String role = "input";
        /** "item", "fluid", or "tag" - the slot type a fresh value picked at this position gets. Purely a default/hint (matching how {@link ExtraField#type} is informational): the player can still right-click-clear and refill a slot with any type via the normal pickers. */
        public String type = "item";
        /** Pixel X offset from the section's left edge (same origin the generic grid's column 0 uses). */
        public int x = 0;
        /** Pixel Y offset from the section's top edge (below the "Inputs"/"Outputs" label, same origin the generic grid's row 0 uses). */
        public int y = 0;
        /** True if a recipe of this type is invalid without this slot filled. Informational only, same as {@link ExtraField#required} - KVE doesn't hard-block export on it. */
        public boolean required = false;
        public boolean repeatable = false;
        /**
         * True if this position should render as a tall vertical fluid
         * tank (mimicking Create/Mekanism/Farmer's Delight-style tank
         * bars) instead of the standard 18x18 item-slot square. Purely
         * visual - the underlying value is still a normal fluid slot, same
         * click/scroll/clear behavior as any other slot, just drawn taller
         * and narrower so a layout that has a real fluid tank (Create's
         * Spout/Mixer) actually looks like one instead of showing a
         * generic square with a fluid-colored border. Only makes sense
         * paired with {@code "type": "fluid"}; ignored for item/tag slots.
         */
        public boolean tank = false;

        /**
         * fluidos: para un slot {@code "fluid"}
         * que en el mod real NO es un fluido de Forge sino un quimico de
         * Mekanism - el nombre del registro de Mekanism ("gas",
         * "infuse_type", "pigment", "slurry"; confirmados en
         * mekanism.api.MekanismAPI del jar real), o varios separados por
         * "|" cuando la maquina acepta mas de uno (ej. el Chemical
         * Crystallizer toma gas O slurry). Antes estos slots abrian el
         * selector de fluidos de Forge, que no lista ningun gas: era
         * imposible elegir "mekanism:hydrogen" desde la GUI. Tambien decide
         * la clave del JSON crudo ({@code {"gas": id}} vs
         * {@code {"slurry": id}}). Blank (default) = fluido normal.
         */
        public String chemical = "";

        /** Optional tank height override in px (only for {@link #tank} slots), so a layout can match the real gauge size (e.g. Mekanism's 18x60 standard gauge). 0 = the editor's default. */
        public int h = 0;

        /** Optional short caption shown in this slot's tooltip (also while empty), e.g. "Gas izquierdo". Blank = none. */
        public String label = "";

        public int maxCount = 0;

        public boolean isChemical() { return chemical != null && !chemical.isBlank(); }

        public boolean isOutput() { return "output".equalsIgnoreCase(role); }

        /** True if this slot definition is well-formed enough to render: a real role and non-negative coordinates. Called by the registry before accepting a parsed slotLayout - a single bad entry doesn't need to nuke the whole layout, but it does get dropped. */
        public boolean isValid() {
            return role != null && (role.equalsIgnoreCase("input") || role.equalsIgnoreCase("output")) && x >= 0 && y >= 0;
        }

        public String effectiveId(int fallbackIndex) {
            return id != null && !id.isBlank() ? id : (role + "_" + fallbackIndex);
        }
    }
}
