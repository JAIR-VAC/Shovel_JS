package com.kve.common.recipe;

import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.recipetype.RecipeTypeDescriptorRegistry;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.Locale;

public final class RecipeGridRules {
    private RecipeGridRules() {}

    /** Tope del resize manual "+/-" - mismo que EC_TABLE ULTIMATE (9x9). */
    public static final int MANUAL_GRID_MIN = 1;
    public static final int MANUAL_GRID_MAX = 9;

    public static boolean isCustomMod(RecipeData.RecipeType t) { return t == RecipeData.RecipeType.CUSTOM_JSON; }

    public static boolean isCombo(RecipeData.RecipeType t) { return t == RecipeData.RecipeType.EC_COMBINATION; }

    public static boolean isEcTableType(RecipeData.RecipeType t) {
        return t == RecipeData.RecipeType.EC_TABLE_SHAPED || t == RecipeData.RecipeType.EC_TABLE_SHAPELESS;
    }

    public static boolean isEcType(RecipeData.RecipeType t) {
        return t == RecipeData.RecipeType.EC_TABLE_SHAPED
            || t == RecipeData.RecipeType.EC_TABLE_SHAPELESS
            || t == RecipeData.RecipeType.EC_ENDER_SHAPED
            || t == RecipeData.RecipeType.EC_ENDER_SHAPELESS
            || t == RecipeData.RecipeType.EC_FLUX_SHAPED
            || t == RecipeData.RecipeType.EC_FLUX_SHAPELESS
            || t == RecipeData.RecipeType.EC_COMBINATION;
    }

    /**
     * CUSTOM_JSON cuyos casilleros son POSICIONALES (smithing:
     * template/base/addition; create:mechanical_crafting y
     * twilightforest:uncrafting: pattern/key). Guardarlos como shapeless
     * compactaba los vacios y el item "saltaba" de casillero al reabrir, y
     * quedaba asi en el script. La lista vive en
     * {@link KubeJSExporter#isPositionalCustomType()} (compartida con la validacion).
     */
    public static boolean isPositionalCustomJson(RecipeData.RecipeType t, String customTypeId) {
        if (t != RecipeData.RecipeType.CUSTOM_JSON) return false;
        return KubeJSExporter.isPositionalCustomType(lower(customTypeId));
    }

    public static boolean isShapeless(RecipeData.RecipeType t, String customTypeId) {
        return t == RecipeData.RecipeType.VANILLA_SHAPELESS
            || t == RecipeData.RecipeType.EC_TABLE_SHAPELESS
            || t == RecipeData.RecipeType.EC_ENDER_SHAPELESS
            || t == RecipeData.RecipeType.EC_FLUX_SHAPELESS
            || (t == RecipeData.RecipeType.CUSTOM_JSON && !isPositionalCustomJson(t, customTypeId));
    }

    public static int[] fixedCustomDims(String customTypeId) {
        var descriptor = RecipeTypeDescriptorRegistry.find(customTypeId);
        if (descriptor.isPresent() && descriptor.get().hasCustomGrid()) {
            return new int[]{descriptor.get().gridWidth, descriptor.get().gridHeight};
        }
        String t = lower(customTypeId);
        if (t.contains("stonecutting")) return new int[]{1, 1};
        if (t.contains("smelting") || t.contains("blasting")
                || t.contains("smoking") || t.contains("campfire")) return new int[]{1, 1};
        if (t.contains("smithing")) return new int[]{3, 1};
        return null;
    }

    /** CUSTOM_JSON sin tamano fijo (ej. create:mechanical_crafting, hasta 9x9): muestra los botones +/- de filas/columnas. */
    public static boolean isVariableGridCustomJson(RecipeData.RecipeType t, String customTypeId) {
        return t == RecipeData.RecipeType.CUSTOM_JSON && fixedCustomDims(customTypeId) == null;
    }

    public static int[] dims(RecipeData.RecipeType type, RecipeData.ECTableTier tier, String customTypeId,
                             RecipeData editing, int maxCols) {
        return switch (type) {
            case VANILLA_SHAPED, VANILLA_SHAPELESS -> new int[]{3, 3};
            case EC_TABLE_SHAPED, EC_TABLE_SHAPELESS -> switch (tier) {
                case BASIC    -> new int[]{3, 3};
                case ADVANCED -> new int[]{5, 5};
                case ELITE    -> new int[]{7, 7};
                case ULTIMATE -> new int[]{9, 9};
            };
            case EC_ENDER_SHAPED, EC_ENDER_SHAPELESS,
                 EC_FLUX_SHAPED,  EC_FLUX_SHAPELESS -> new int[]{3, 3};
            case EC_COMBINATION -> new int[]{0, 0};
            case CUSTOM_JSON -> customJsonDims(customTypeId, editing, maxCols);
            // CUSTOM_MACHINE se edita en MachineRecipeEditScreen, nunca en esta grilla.
            case CUSTOM_MACHINE -> new int[]{0, 0};
        };
    }

    static int[] customJsonDims(String customTypeId, RecipeData editing, int maxCols) {
        int[] fixed = fixedCustomDims(customTypeId);
        if (fixed != null) return fixed;
        int[] hint = existingRecipeGridHint(customTypeId, KubeJSExporter.getAddedRecipes(), editing, maxCols);
        return hint != null ? hint : new int[]{3, 3};
    }

    /**
     * Forma de la primera receta CUSTOM_JSON guardada del mismo tipo: shaped
     * (se detecta por {@code ingredients} no vacio - gridWidth/gridHeight
     * solos no sirven, RecipeData los inicializa en 3/3 incluso en shapeless)
     * o shapeless de mas de 9 ingredientes. null si no hay ninguna.
     */
    public static int[] existingRecipeGridHint(String customTypeId, Iterable<RecipeData> recipes,
                                               RecipeData exclude, int maxCols) {
        if (customTypeId == null || customTypeId.isBlank()) return null;
        for (RecipeData r : recipes) {
            if (r == exclude) continue;
            if (r.type != RecipeData.RecipeType.CUSTOM_JSON) continue;
            if (!customTypeId.equals(r.customTypeId)) continue;
            if (r.ingredients != null && !r.ingredients.isEmpty() && r.gridWidth > 0 && r.gridHeight > 0) {
                return new int[]{r.gridWidth, r.gridHeight};
            }
            if (r.shapelessIngredients != null && r.shapelessIngredients.size() > 9) {
                return shapelessGridDims(r.shapelessIngredients.size(), maxCols);
            }
        }
        return null;
    }

    public static int[] shapelessGridDims(int count, int maxCols) {
        if (count <= maxCols) return new int[]{count, 1};
        int cols = Math.max(1, maxCols);
        int rows = (count + cols - 1) / cols;
        return new int[]{cols, rows};
    }

    /**
     * Resize manual +/- conservando el contenido por fila/columna. Devuelve
     * null si achicar dejaria afuera algun casillero con item (nunca se
     * pierden items en silencio). Los tamanos se recortan a 1..9.
     */
    public static ItemStack[] resizeGrid(ItemStack[] slots, int oldW, int oldH, int newW, int newH) {
        newW = clampManual(newW);
        newH = clampManual(newH);
        if (newW < oldW || newH < oldH) {
            for (int row = 0; row < oldH; row++) {
                for (int col = 0; col < oldW; col++) {
                    boolean outside = col >= newW || row >= newH;
                    if (outside && !slots[row * oldW + col].isEmpty()) return null;
                }
            }
        }
        ItemStack[] out = new ItemStack[Math.max(1, newW * newH)];
        Arrays.fill(out, ItemStack.EMPTY);
        for (int row = 0; row < Math.min(oldH, newH); row++) {
            for (int col = 0; col < Math.min(oldW, newW); col++) {
                out[row * newW + col] = slots[row * oldW + col];
            }
        }
        return out;
    }

    /**
     * La grilla del editor como la 3x3 de una mesa de crafteo vanilla, para
     * buscar conflictos con recetas existentes (RecipeConflictFinder): shaped
     * conserva fila/columna, shapeless se compacta en orden. Cantidades a 1.
     * null si no es crafteo vanilla o la grilla es mas grande que 3x3.
     */
    public static java.util.List<ItemStack> craftingGrid9(RecipeData.RecipeType type, ItemStack[] slots, int w, int h) {
        if (type != RecipeData.RecipeType.VANILLA_SHAPED && type != RecipeData.RecipeType.VANILLA_SHAPELESS) return null;
        if (w > 3 || h > 3) return null;
        java.util.List<ItemStack> grid9 = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) grid9.add(ItemStack.EMPTY);
        if (type == RecipeData.RecipeType.VANILLA_SHAPED) {
            for (int r = 0; r < h; r++)
                for (int c = 0; c < w; c++) {
                    ItemStack st = slots[r * w + c];
                    if (st != null && !st.isEmpty()) grid9.set(r * 3 + c, st.copyWithCount(1));
                }
        } else {
            int k = 0;
            for (ItemStack st : slots) if (st != null && !st.isEmpty() && k < 9) grid9.set(k++, st.copyWithCount(1));
        }
        return grid9;
    }

    public static int clampManual(int v) {
        return Math.max(MANUAL_GRID_MIN, Math.min(MANUAL_GRID_MAX, v));
    }

    /**
     * True si una receta YA GUARDADA debe abrirse en MachineRecipeEditScreen
     * (slots tipados / vista ritual) y no en la grilla simple: su tipo tiene
     * slotLayout. Sin esto, una receta cuyo tipo gano slotLayout despues de
     * guardarse (ej. goety:ritual) se reabria para siempre como grilla plana.
     */
    public static boolean shouldRouteToMachineEditor(RecipeData d) {
        if (d.type == RecipeData.RecipeType.CUSTOM_MACHINE) return false;
        if (d.customTypeId == null || d.customTypeId.isBlank()) return false;
        var found = RecipeTypeDescriptorRegistry.find(d.customTypeId);
        return found.isPresent() && found.get().hasSlotLayout();
    }

    /**
     * Conversion unica para {@link #shouldRouteToMachineEditor()}: aplana la
     * grilla y las salidas a machineInputs/machineOutputs (fila por fila,
     * sin vacios) para que el editor de maquina muestre lo que el jugador ya
     * habia puesto. Muta {@code d}. No corre si ya hay datos de maquina; la
     * asignacion a slots con nombre no esta garantizada.
     */
    public static void migrateForMachineEditor(RecipeData d) {
        if (!d.machineInputs.isEmpty() || !d.machineOutputs.isEmpty()) return;
        for (ItemStack s : d.ingredients) addMachineSlot(d.machineInputs, s);
        for (ItemStack s : d.shapelessIngredients) addMachineSlot(d.machineInputs, s);
        addMachineSlot(d.machineOutputs, d.output);
        addMachineSlot(d.machineOutputs, d.secondaryOutput);
    }

    private static void addMachineSlot(java.util.List<RecipeData.MachineSlot> into, ItemStack s) {
        if (s == null || s.isEmpty()) return;
        RecipeData.MachineSlot slot = new RecipeData.MachineSlot();
        String tag = TagIngredient.tagOf(s);
        if (tag != null) {
            slot.type = RecipeData.MachineSlotType.TAG;
            slot.tagId = tag;
            slot.tagCount = Math.max(1, s.getCount());
        } else {
            slot.item = s.copy();
        }
        into.add(slot);
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
