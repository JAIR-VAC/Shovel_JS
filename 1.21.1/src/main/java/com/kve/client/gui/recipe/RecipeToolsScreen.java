package com.kve.client.gui.recipe;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;

import com.kve.common.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.function.Function;

/**
 * el navegador de recetas ya tenia 2 herramientas sueltas
 * (conflictos, quitar en masa) en su fila de botones y sumaban 3 mas - en vez
 * de desbordar esa fila, un solo boton "Herramientas" abre esta lista, cada
 * una con una linea que explica para que sirve.
 */
@OnlyIn(Dist.CLIENT)
public class RecipeToolsScreen extends Screen {

    private record Tool(String key, Function<Screen, Screen> open) {}

    private static final List<Tool> TOOLS = List.of(
            new Tool("replace", p -> new RecipeReplaceScreen(p, false)),
            new Tool("unify", p -> new RecipeReplaceScreen(p, true)),
            new Tool("uncraftable", UncraftableItemsScreen::new),
            new Tool("conflicts", RecipeConflictsScreen::new),
            new Tool("bulk", RecipeBulkRemoveScreen::new));

    private static final int W = 320;
    private static final int ROW = 30;

    private final Screen parent;
    private int cx, cy, h;

    public RecipeToolsScreen(Screen parent) {
        super(Component.literal(Lang.get("gui.kve.recipe.tools.title")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        h = KveGuiStyle.TITLEBAR_H + 8 + TOOLS.size() * ROW + 26;
        cx = (width - W) / 2;
        cy = (height - h) / 2;
        int y = cy + KveGuiStyle.TITLEBAR_H + 6;
        for (Tool t : TOOLS) {
            addRenderableWidget(new KvePlateButton(cx + KveGuiStyle.PAD, y, 120, 14,
                    Component.literal(Lang.get("gui.kve.recipe.tools." + t.key())), b -> minecraft.setScreen(t.open().apply(this))));
            y += ROW;
        }
        addRenderableWidget(new KvePlateButton(cx + W - KveGuiStyle.PAD - 70, cy + h - KveGuiStyle.PAD - 14, 70, 14,
                Component.literal(Lang.get("gui.kve.btn.back")), b -> onClose()));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, h, getTitle().getString());
        int y = cy + KveGuiStyle.TITLEBAR_H + 6;
        int descX = cx + KveGuiStyle.PAD + 126;
        int descW = W - KveGuiStyle.PAD * 2 - 126;
        int hover = rowAt(mx, my);
        for (int i = 0; i < TOOLS.size(); i++) {
            Tool t = TOOLS.get(i);
            if (i == hover) g.fill(cx + KveGuiStyle.PAD - 2, y - 2, cx + W - KveGuiStyle.PAD, y + ROW - 4, KveGuiStyle.BORDER_FIELD);
            List<String> lines = KveGuiStyle.wrapTextSafe(font, Lang.get("gui.kve.recipe.tools." + t.key() + ".desc"), descW);
            for (int li = 0; li < Math.min(2, lines.size()); li++) {
                KveGuiStyle.drawTextSafe(g, lines.get(li), descX, y + 1 + li * 10, descW, KveGuiStyle.TEXT_MUTED);
            }
            y += ROW;
        }
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    private int rowAt(double mx, double my) {
        int top = cy + KveGuiStyle.TITLEBAR_H + 4;
        if (mx < cx + KveGuiStyle.PAD || mx >= cx + W - KveGuiStyle.PAD || my < top) return -1;
        int i = (int) ((my - top) / ROW);
        return i < TOOLS.size() ? i : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;
        int i = rowAt(mx, my);
        if (i >= 0 && btn == 0) { minecraft.setScreen(TOOLS.get(i).open().apply(this)); return true; }
        return false;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
