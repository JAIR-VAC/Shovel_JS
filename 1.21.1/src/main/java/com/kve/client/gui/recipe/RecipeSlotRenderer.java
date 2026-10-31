package com.kve.client.gui.recipe;

import com.kve.client.gui.SafeItemRender;
import com.kve.client.gui.KveGuiStyle;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class RecipeSlotRenderer {
    private RecipeSlotRenderer() {}

    static void drawSlot(GuiGraphics g, Font font, int x, int y, int sz, ItemStack stack, int mx, int my) {
        drawSlot(g, font, x, y, sz, stack, mx, my, false);
    }

    static void drawSlot(GuiGraphics g, Font font, int x, int y, int sz, ItemStack stack, int mx, int my, boolean invalid) {
        boolean hov = mx >= x && mx < x + sz && my >= y && my < y + sz;
        g.fill(x, y, x + sz, y + sz, hov ? 0xFF1A3045 : KveGuiStyle.th(0xFF080C18));
        int col = invalid ? (hov ? 0xFFFF7766 : 0xFFCC4433) : hov ? KveGuiStyle.th(0xFF60A8F0) : KveGuiStyle.BORDER_SUBTLE;
        g.hLine(x, x + sz - 1, y, col); g.hLine(x, x + sz - 1, y + sz - 1, col);
        g.vLine(x, y, y + sz - 1, col); g.vLine(x + sz - 1, y, y + sz - 1, col);
        if (stack != null && !stack.isEmpty()) {
            int ox2 = x + Math.max(0, (sz - 16) / 2 + 1);
            int oy2 = y + Math.max(0, (sz - 16) / 2 + 1);
            String tag = com.kve.common.recipe.TagIngredient.tagOf(stack);
            ItemStack shown = tag != null ? cycleTagMember(tag, stack) : stack;
            try {
                SafeItemRender.render(g, shown, ox2, oy2);
                if (tag == null) g.renderItemDecorations(font, stack, ox2, oy2, null);
            } catch (Throwable ignored) {
                g.fill(ox2, oy2, ox2 + 16, oy2 + 16, KveGuiStyle.th(0xFF334455));
            }
            if (tag != null) drawTagMarker(g, font, x, y, sz, com.kve.common.recipe.TagIngredient.members(tag).isEmpty());
            else drawNbtHotspot(g, font, x, y, sz, stack, mx, my);
        }
    }

    static ItemStack cycleTagMember(String tag, ItemStack fallback) {
        var members = com.kve.common.recipe.TagIngredient.members(tag);
        if (members.isEmpty()) return fallback;
        int i = (int) ((System.currentTimeMillis() / 1000) % members.size());
        return new ItemStack(members.get(i));
    }

    private static void drawTagMarker(GuiGraphics g, Font font, int x, int y, int sz, boolean empty) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(x + 1, y + sz - 8, x + 7, y + sz - 1, 0xCC000000);
        // Rojo = el tag no tiene items (roto o vacio), ver el tooltip del slot.
        g.drawString(font, "#", x + 1, y + sz - 9, empty ? 0xFFFF6B6B : KveGuiStyle.ACCENT_GOLD, false);
        g.pose().popPose();
    }

    static void drawNbtHotspot(GuiGraphics g, Font font, int x, int y, int sz, ItemStack stack, int mx, int my) {
        int hs = Math.max(6, Math.min(8, sz / 2));
        int hx = x + sz - hs, hy = y;
        boolean hasNbt = com.kve.common.util.ItemExtraDataCodec.hasMeaningfulExtraData(stack);
        boolean hoverBadge = hitsNbtBadge(x, y, sz, mx, my);
        boolean overSlot = mx >= x && mx < x + sz && my >= y && my < y + sz;
        // Sin datos y sin el mouse encima no se dibuja nada; con datos, solo un punto que no tapa el icono.
        // El boton "N" para abrir el editor de NBT aparece al pasar el mouse por el slot.
        if (!hasNbt && !overSlot) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        if (!overSlot) {
            g.fill(x + sz - 4, y + 1, x + sz - 1, y + 4, 0xFF3FD07A);
        } else {
            g.fill(hx, hy, hx + hs, hy + hs, hasNbt ? 0xE01F8A44 : 0xC0203040);
            if (hoverBadge) {
                int hitSz = nbtBadgeHitSize(sz);
                int zx = x + sz - hitSz, zy = y;
                g.hLine(zx, zx + hitSz - 1, zy, 0xFFFFFFFF);
                g.hLine(zx, zx + hitSz - 1, zy + hitSz - 1, 0xFFFFFFFF);
                g.vLine(zx, zy, zy + hitSz - 1, 0xFFFFFFFF);
                g.vLine(zx + hitSz - 1, zy, zy + hitSz - 1, 0xFFFFFFFF);
            }
            if (hs >= 7) g.drawString(font, "N", hx + 1, hy, hasNbt ? 0xFF102010 : 0xFFAABBCC, false);
        }
        g.pose().popPose();
    }

    static boolean hitsNbtBadge(int x, int y, int sz, double mx, double my) {
        int hitSz = nbtBadgeHitSize(sz);
        int hx = x + sz - hitSz, hy = y;
        return mx >= hx && mx < hx + hitSz && my >= hy && my < hy + hitSz;
    }

    private static int nbtBadgeHitSize(int sz) {
        return Math.min(sz, Math.max(10, sz / 2));
    }
}
