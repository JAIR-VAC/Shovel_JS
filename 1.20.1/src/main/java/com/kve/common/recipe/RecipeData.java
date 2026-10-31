package com.kve.common.recipe;

import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class RecipeData {

    public enum RecipeType {
        VANILLA_SHAPED,
        VANILLA_SHAPELESS,
        EC_TABLE_SHAPED,
        EC_TABLE_SHAPELESS,
        EC_ENDER_SHAPED,
        EC_ENDER_SHAPELESS,
        EC_FLUX_SHAPED,
        EC_FLUX_SHAPELESS,
        EC_COMBINATION,
        CUSTOM_JSON,
        CUSTOM_MACHINE
    }

    public enum ECTableTier { BASIC, ADVANCED, ELITE, ULTIMATE }

    public enum MachineSlotType { ITEM, FLUID, TAG }

    /** One input or output slot of a {@link RecipeType#CUSTOM_MACHINE} recipe. Only the fields relevant to {@link #type} are used when exporting. */
    public static class MachineSlot {
        public MachineSlotType type = MachineSlotType.ITEM;

        public ItemStack item = ItemStack.EMPTY;

        public String fluidId = "";
        public int fluidAmount = 1000;

        public String tagId = "";
        public int tagCount = 1;

        public int ringSlot = -1;

        /**
         * Drop chance for OUTPUT slots only, 0.0-1.0 (clamped the same way
         * as {@link RecipeData#secondaryOutputChance} - see
         * {@code KubeJSExporter#clampChance}). 1.0 (the default) means
         * "always drops, no bonus-output semantics" and is intentionally
         * never emitted in exported JS/JSON (see
         * {@code KubeJSExporter#machineSlotFragment}) so every existing
         * recipe/export/golden-test that predates this field round-trips
         * byte-for-byte unchanged - only a slot a player has actually
         * dialed below 100% starts emitting a {@code chance} key. Ignored
         * on input slots (nothing reads it there); kept on the shared
         * class rather than a separate output-only type because
         * {@link MachineSlot} is already used interchangeably for both
         * roles throughout {@code MachineRecipeEditScreen} and the
         * exporters, and splitting it would be a much larger, riskier
         * change than this field addition.
         */
        public double chance = 1.0;

        public MachineSlot copy() {
            MachineSlot c = new MachineSlot();
            c.type = type;
            c.item = item.copy();
            c.fluidId = fluidId; c.fluidAmount = fluidAmount;
            c.tagId = tagId; c.tagCount = tagCount;
            c.ringSlot = ringSlot;
            c.chance = chance;
            return c;
        }
    }

    public String recipeId = "";

    /**
     * Tags rotos (sin items cargados) que ExistingRecipeLoader
     * reemplazo por un item real al abrir la receta ("#forge:gems/diamond ->
     * minecraft:diamond"). Solo para avisarle al jugador; no se guarda.
     */
    public transient java.util.List<String> repairedTags = new java.util.ArrayList<>();
    public boolean enabled = true;
    public String  category = "";
    /** Notas libres del equipo: se exportan como comentarios "// nota:" encima de la receta. */
    public String  notes = "";
    public RecipeType type = RecipeType.VANILLA_SHAPED;
    public ECTableTier ecTier = ECTableTier.BASIC;

    public List<ItemStack> ingredients = new ArrayList<>();
    public int gridWidth  = 3;
    public int gridHeight = 3;

    public List<ItemStack> shapelessIngredients = new ArrayList<>();

    public ItemStack output = ItemStack.EMPTY;

    public long ecPowerRequired = 1_000_000L;
    public int  ecCraftingTime  = 200;

    public ItemStack combinationCenter = ItemStack.EMPTY;
    public List<ItemStack> combinationPedestals = new ArrayList<>();
    public long combinationRfCost = 50_000L;
    public long combinationRfRate = 1_000L;

    public String customTypeId    = "";
    public String customExtraJson = "";

    /**
     * Optional bonus output with a drop chance, emitted as
     * {@code secondaryOutput}/{@code secondaryChance} in the generated raw
     * JSON recipe (matching the naming already used by the Thermal/
     * Immersive Engineering {@code RecipeTypeDescriptor}s). Only meaningful
     * for {@link RecipeType#CUSTOM_JSON} - real vanilla/EC crafting recipes
     * genuinely cannot hand back a second, different item, so this is never
     * read for any other type.
     */
    public ItemStack secondaryOutput = ItemStack.EMPTY;
    public double    secondaryOutputChance = 0.5;

    /** Typed slots for {@link RecipeType#CUSTOM_MACHINE}; unused by every other type. */
    public List<MachineSlot> machineInputs  = new ArrayList<>();
    public List<MachineSlot> machineOutputs = new ArrayList<>();
}