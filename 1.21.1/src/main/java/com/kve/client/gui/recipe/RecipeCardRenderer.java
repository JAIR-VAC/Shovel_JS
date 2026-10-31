package com.kve.client.gui.recipe;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.SafeItemRender;

import com.kve.common.Lang;
import com.kve.common.recipe.RecipeData;
import com.kve.common.recipe.TagIngredient;
import com.kve.common.recipetype.RecipeTypeDescriptor;
import com.kve.common.recipetype.RecipeTypeDescriptorRegistry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Vista enmarcada de una receta para paneles laterales (conflictos, selector de recetas):
 * la estacion en 3D arriba, como el editor de maquinas, y debajo la receta con su forma
 * y el resultado. Solo dibuja; devuelve el item bajo el mouse para el tooltip.
 */
@OnlyIn(Dist.CLIENT)
public final class RecipeCardRenderer {

    private RecipeCardRenderer() {}

    private static final int SLOT = 18;

    /** La mesa de crafteo no tiene descriptor propio; este solo sirve para el render 3D. */
    private static final RecipeTypeDescriptor CRAFTING = new RecipeTypeDescriptor();
    static {
        CRAFTING.recipeSerializer = "minecraft:crafting_shaped";
        CRAFTING.stationItem = "minecraft:crafting_table";
    }

    public static RecipeTypeDescriptor descriptorOf(RecipeData d) {
        if (d.customTypeId != null && !d.customTypeId.isBlank()) {
            RecipeTypeDescriptor found = RecipeTypeDescriptorRegistry.find(d.customTypeId).orElse(null);
            if (found != null) return found;
        }
        return d.type == RecipeData.RecipeType.CUSTOM_JSON || d.type == RecipeData.RecipeType.CUSTOM_MACHINE ? null : CRAFTING;
    }

    public static String typeLabel(RecipeData d) {
        return switch (d.type) {
            case VANILLA_SHAPED -> Lang.get("gui.kve.recipe.type.shaped");
            case VANILLA_SHAPELESS -> Lang.get("gui.kve.recipe.type.shapeless");
            case CUSTOM_JSON, CUSTOM_MACHINE -> {
                RecipeTypeDescriptor desc = descriptorOf(d);
                yield desc != null ? desc.effectiveDisplayName() : d.customTypeId;
            }
            default -> d.type.name().toLowerCase(java.util.Locale.ROOT);
        };
    }

    /** Icono de un slot de maquina: el item, un miembro del tag o el balde del fluido. */
    public static ItemStack machineIcon(RecipeData.MachineSlot s) {
        if (s == null) return ItemStack.EMPTY;
        switch (s.type) {
            case ITEM: return s.item == null ? ItemStack.EMPTY : s.item;
            case TAG: {
                if (s.tagId == null || s.tagId.isBlank()) return ItemStack.EMPTY;
                List<net.minecraft.world.item.Item> m = TagIngredient.members(s.tagId);
                return TagIngredient.of(s.tagId, m.isEmpty() ? Items.BARRIER : m.get(0));
            }
            case FLUID: {
                if (s.fluidId == null || s.fluidId.isBlank()) return ItemStack.EMPTY;
                ResourceLocation id = ResourceLocation.tryParse(s.fluidId);
                var fluid = id == null ? null : BuiltInRegistries.FLUID.get(id);
                ItemStack bucket = fluid == null ? ItemStack.EMPTY : new ItemStack(fluid.getBucket());
                return bucket.isEmpty() ? new ItemStack(Items.WATER_BUCKET) : bucket;
            }
            default: return ItemStack.EMPTY;
        }
    }

    /** Una celda por ranura fija de la estacion (vacias incluidas) y una por cada extra que haya. */
    private static List<ItemStack> machineIcons(List<RecipeData.MachineSlot> slots, RecipeTypeDescriptor desc, boolean outputs) {
        int fixed = 0;
        if (desc != null && desc.hasSlotLayout()) {
            for (RecipeTypeDescriptor.SlotDef d : desc.slotLayout.slots) if (d.isValid() && d.isOutput() == outputs && !d.repeatable) fixed++;
        }
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < Math.max(fixed, slots.size()); i++) out.add(i < slots.size() ? machineIcon(slots.get(i)) : ItemStack.EMPTY);
        return out;
    }

    /** Celdas de la mesa: las recetas chicas se ven en el 3x3 completo y las sin forma con sus huecos libres. */
    private static List<ItemStack> craftingCells(RecipeData d, boolean shaped) {
        List<ItemStack> grid = new ArrayList<>();
        for (int i = 0; i < 9; i++) grid.add(ItemStack.EMPTY);
        if (shaped) {
            int w = d.gridWidth, h = d.gridHeight;
            if (w <= 0 || h <= 0 || w > 3 || h > 3 || d.ingredients.size() != w * h) return d.ingredients;
            for (int r = 0; r < h; r++) for (int c = 0; c < w; c++) grid.set(r * 3 + c, d.ingredients.get(r * w + c));
        } else {
            if (d.shapelessIngredients.size() > 9) return d.shapelessIngredients;
            for (int i = 0; i < d.shapelessIngredients.size(); i++) grid.set(i, d.shapelessIngredients.get(i));
        }
        return grid;
    }

    /**
     * Dibuja la tarjeta en (x, y, w, h). {@code d} null dibuja el marco con un aviso.
     * @return el item bajo el mouse, o EMPTY
     */
    public static ItemStack draw(GuiGraphics g, Font font, RecipeData d, int x, int y, int w, int h, int mx, int my) {
        g.fill(x, y, x + w, y + h, 0xFF0C1422);
        g.renderOutline(x, y, w, h, KveGuiStyle.BORDER_FIELD);
        g.renderOutline(x + 1, y + 1, w - 2, h - 2, 0xFF16263A);
        int pad = 6, tw = w - pad * 2;
        if (d == null) {
            int ty = y + h / 2 - 4;
            for (String l : KveGuiStyle.wrapTextSafe(font, Lang.get("gui.kve.recipe.card.empty"), tw)) {
                KveGuiStyle.drawCenteredTextSafe(g, l, x + w / 2, ty, KveGuiStyle.TEXT_MUTED);
                ty += font.lineHeight;
            }
            return ItemStack.EMPTY;
        }
        KveGuiStyle.drawTextSafe(g, d.recipeId == null || d.recipeId.isBlank() ? "-" : d.recipeId, x + pad, y + 5, tw, KveGuiStyle.ACCENT_GOLD);
        KveGuiStyle.drawTextSafe(g, typeLabel(d), x + pad, y + 15, tw, KveGuiStyle.TEXT_MUTED);

        // estacion en 3D, el mismo "hero" que usa el editor de maquinas
        int heroY = y + 27, heroH = Math.max(40, Math.min(90, h / 3));
        g.fill(x + pad, heroY, x + pad + tw, heroY + heroH, 0xFF101C2E);
        g.renderOutline(x + pad, heroY, tw, heroH, KveGuiStyle.BORDER_SUBTLE);
        RecipeTypeDescriptor desc = descriptorOf(d);
        boolean drawn = false;
        try { drawn = desc != null && MachineStationPreview.render(g, desc, x + pad + 1, heroY + 1, tw - 2, heroH - 2); }
        catch (Throwable ignored) { }
        if (!drawn) {
            ItemStack big = d.output != null && !d.output.isEmpty() ? d.output : new ItemStack(Items.CRAFTING_TABLE);
            g.pose().pushPose();
            g.pose().translate(x + pad + tw / 2f - 16, heroY + heroH / 2f - 16, 0);
            g.pose().scale(2f, 2f, 1f);
            SafeItemRender.render(g, big, 0, 0);
            g.pose().popPose();
        }

        // receta: ingredientes -> resultado
        List<ItemStack> ins;
        int cols;
        switch (d.type) {
            case VANILLA_SHAPED -> {
                ins = craftingCells(d, true);
                cols = ins == d.ingredients ? Math.max(1, d.gridWidth) : 3;
            }
            case VANILLA_SHAPELESS -> { ins = craftingCells(d, false); cols = 3; }
            case EC_TABLE_SHAPED, EC_ENDER_SHAPED, EC_FLUX_SHAPED -> { ins = d.ingredients; cols = Math.max(1, d.gridWidth); }
            case CUSTOM_JSON -> {
                boolean grid = !d.ingredients.isEmpty() && d.ingredients.size() == d.gridWidth * d.gridHeight;
                ins = grid ? d.ingredients : new ArrayList<>(d.shapelessIngredients);
                if (!grid) while (ins.size() < 3) ins.add(ItemStack.EMPTY);
                cols = grid ? Math.max(1, d.gridWidth) : 3;
            }
            case CUSTOM_MACHINE -> { ins = machineIcons(d.machineInputs, descriptorOf(d), false); cols = ins.size() > 4 ? 3 : 2; }
            case EC_COMBINATION -> {
                ins = new ArrayList<>();
                if (d.combinationCenter != null && !d.combinationCenter.isEmpty()) ins.add(d.combinationCenter);
                for (ItemStack p : d.combinationPedestals) if (p != null && !p.isEmpty()) ins.add(p);
                cols = 4;
            }
            default -> { ins = d.shapelessIngredients; cols = 3; }
        }
        List<ItemStack> outs = d.type == RecipeData.RecipeType.CUSTOM_MACHINE ? machineIcons(d.machineOutputs, descriptorOf(d), true) : List.of(d.output);
        if (outs.isEmpty()) outs = List.of(d.output == null ? ItemStack.EMPTY : d.output);
        int maxCols = Math.max(1, (tw - 40) / SLOT);
        cols = Math.max(1, Math.min(cols, maxCols));
        int rows = Math.max(1, (ins.size() + cols - 1) / cols);
        int gridW = cols * SLOT, gridH = rows * SLOT;
        int outH = outs.size() * (SLOT + 2);
        int top = heroY + heroH + 8;
        int bodyH = Math.max(gridH, outH);
        int startX = x + pad + Math.max(0, (tw - gridW - 22 - SLOT) / 2);
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < ins.size(); i++) {
            int sx = startX + (i % cols) * SLOT, sy = top + (bodyH - gridH) / 2 + (i / cols) * SLOT;
            if (sy + SLOT > y + h - 4) break;
            RecipeSlotRenderer.drawSlot(g, font, sx, sy, SLOT, ins.get(i), mx, my);
            if (hit(mx, my, sx, sy) && ins.get(i) != null && !ins.get(i).isEmpty()) hovered = ins.get(i);
        }
        int ax = startX + gridW + 6;
        g.drawString(font, "→", ax, top + bodyH / 2 - 4, KveGuiStyle.ACCENT_GOLD, false);
        int ox = ax + 16;
        for (int i = 0; i < outs.size(); i++) {
            int oy = top + (bodyH - outH) / 2 + i * (SLOT + 2);
            if (oy + SLOT > y + h - 4) break;
            g.fill(ox - 1, oy - 1, ox + SLOT + 1, oy + SLOT + 1, 0xFF2A4A6A);
            RecipeSlotRenderer.drawSlot(g, font, ox, oy, SLOT, outs.get(i), mx, my);
            if (hit(mx, my, ox, oy) && !outs.get(i).isEmpty()) hovered = outs.get(i);
        }
        int ty = top + bodyH + 6;
        if (!d.enabled && ty + 9 < y + h) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.status.disabled"), x + pad, ty, tw, KveGuiStyle.TEXT_WARN);
        }
        return hovered;
    }

    private static boolean hit(int mx, int my, int sx, int sy) { return mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT; }
}
