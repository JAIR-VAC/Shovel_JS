package com.kve.client.gui.tag;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KveScaledEditBox;

import com.kve.common.Lang;
import com.kve.common.recipe.TagIngredient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Elegir un tag de items para un casillero de la receta: lista
 * los tags a los que pertenece el item del casillero ("usar cualquier
 * lingote de cobre" en vez de solo el de un mod). Buscador arriba; cada fila
 * muestra cuantos items tiene el tag.
 */
@OnlyIn(Dist.CLIENT)
public class TagChoiceScreen extends Screen {

    private static final int W = 320;
    private static final int H = 240;
    private static final int ROW_H = 12;
    private static final int ROWS = 14;

    private final Screen parent;
    private final List<String> all;
    private final Consumer<String> onPick;
    private final List<String> shown = new ArrayList<>();
    private String query = "";
    private int scroll = 0;
    private int cx, cy;

    public TagChoiceScreen(Screen parent, Item item, Consumer<String> onPick) {
        super(Component.literal(Lang.get("gui.kve.recipe.tagpick.title")));
        this.parent = parent;
        this.all = TagIngredient.tagsOf(item);
        this.onPick = onPick;
        filter();
    }

    private void filter() {
        shown.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (String t : all) if (q.isEmpty() || t.contains(q)) shown.add(t);
        scroll = 0;
    }

    @Override
    protected void init() {
        cx = (width - W) / 2;
        cy = (height - H) / 2;
        KveScaledEditBox box = new KveScaledEditBox(font, cx + KveGuiStyle.PAD, cy + KveGuiStyle.TITLEBAR_H + 4, W - KveGuiStyle.PAD * 2, 12,
                Component.literal(Lang.get("gui.kve.recipe.tagpick.search")));
        box.setHint(Component.literal(Lang.get("gui.kve.recipe.tagpick.search")));
        box.setValue(query);
        box.setResponder(v -> { query = v; filter(); });
        addRenderableWidget(box);
        setInitialFocus(box);
        KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(cx + KveGuiStyle.PAD, cy + H - KveGuiStyle.PAD - 16, cx + W - KveGuiStyle.PAD);
        addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.cancel"), b -> onClose()));
    }

    private int top() { return cy + KveGuiStyle.TITLEBAR_H + 22; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, H, getTitle().getString());
        int x = cx + KveGuiStyle.PAD;
        int w = W - KveGuiStyle.PAD * 2;
        int ly = top();
        g.fill(x - 2, ly - 1, x + w, ly + ROWS * ROW_H + 1, KveGuiStyle.BG_FIELD);
        if (shown.isEmpty()) KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.tagpick.none"), x, ly + 2, w, KveGuiStyle.TEXT_MUTED);
        for (int i = 0; i < ROWS && i + scroll < shown.size(); i++) {
            String t = shown.get(i + scroll);
            int ry = ly + i * ROW_H;
            boolean hov = mx >= x && mx < x + w && my >= ry && my < ry + ROW_H;
            if (hov) g.fill(x - 2, ry, x + w, ry + ROW_H, KveGuiStyle.BORDER_FIELD);
            KveGuiStyle.drawTextSafe(g, "#" + t, x, ry + 2, w - 60, KveGuiStyle.TEXT_PRIMARY);
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.tagpick.count", TagIngredient.members(t).size()),
                    x + w - 58, ry + 2, 56, KveGuiStyle.TEXT_MUTED);
        }
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int x = cx + KveGuiStyle.PAD;
        int ly = top();
        if (btn == 0 && mx >= x && mx < cx + W - KveGuiStyle.PAD && my >= ly && my < ly + ROWS * ROW_H) {
            int i = (int) ((my - ly) / ROW_H) + scroll;
            if (i < shown.size()) {
                minecraft.setScreen(parent);
                onPick.accept(shown.get(i));
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, shown.size() - ROWS), scroll + (delta > 0 ? -3 : 3)));
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
