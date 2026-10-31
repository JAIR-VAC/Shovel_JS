package com.kve.client.gui;

import com.kve.CraftEditorMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public final class SafeItemRender {

    private SafeItemRender() {}

    private static final Set<String> BROKEN = Collections.synchronizedSet(new HashSet<>());

    /** Dibuja {@code stack} en (x, y) con su tamano normal de 16px. Devuelve false si no se pudo (y se dibujo el marcador). */
    public static boolean render(GuiGraphics g, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return true;
        String id = idOf(stack);
        if (BROKEN.contains(id)) { drawMarker(g, x, y); return false; }
        try {
            g.renderItem(stack, x, y);
            return true;
        } catch (Throwable t) {
            fail(id, t);
            drawMarker(g, x, y);
            return false;
        }
    }

    /** Igual que {@link #render} pero para la decoracion (contador, durabilidad) - si falla se omite sin marcador. */
    public static void decorations(GuiGraphics g, net.minecraft.client.gui.Font font, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        String id = idOf(stack);
        if (BROKEN.contains(id)) return;
        try {
            g.renderItemDecorations(font, stack, x, y, null);
        } catch (Throwable t) {
            fail(id, t);
        }
    }

    /** Item sin contexto de stack (iconos de pantalla): mismo criterio que {@link #render}. */
    public static boolean render(GuiGraphics g, Item item, int x, int y) {
        if (item == null) return true;
        return render(g, new ItemStack(item), x, y);
    }

    /** true si este item ya se marco como roto (lo usa el panel de diagnostico). */
    public static boolean isBroken(String itemId) { return BROKEN.contains(itemId); }

    /** Ids rotos de esta sesion, para mostrar en Diagnostico. */
    public static Set<String> brokenIds() { return Set.copyOf(BROKEN); }

    /** Solo tests / reinicio de sesion. */
    public static void clearForTests() { BROKEN.clear(); }

    private static void fail(String id, Throwable t) {
        if (!BROKEN.add(id)) return;
        String mod = id.contains(":") ? id.substring(0, id.indexOf(':')) : "?";
        CraftEditorMod.LOGGER.error("[Shovel JS] El item '{}' (mod '{}') no se pudo dibujar y se muestra como marcador. "
                + "Si el crash o el error viene de ahi, el culpable es el mod '{}', no Shovel JS. Causa: {}",
                id, mod, mod, t.toString());
    }

    private static void drawMarker(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 16, y + 16, KveGuiStyle.th(0xFF3A1820));
        KveGuiStyle.drawTextSafe(g, "?", x + 5, y + 4, 12, 0xFFFF6B6B);
    }

    private static String idOf(ItemStack stack) {
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "?:unknown" : key.toString();
    }
}
