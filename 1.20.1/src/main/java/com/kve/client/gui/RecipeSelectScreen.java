package com.kve.client.gui;

import com.kve.common.Lang;
import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.recipe.RecipeData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Elegir varias recetas tuyas para abrirlas a la vez en {@link RecipeMultiEditScreen}. */
@OnlyIn(Dist.CLIENT)
public class RecipeSelectScreen extends Screen {

    private static final int ROW_H = 14, CARD_W = 190, CHECK = 9;

    private final Screen parent;
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<RecipeData> shown = new ArrayList<>();
    private String query = "";
    private String previewId;
    private int L, T, W, H, scroll;

    public RecipeSelectScreen(Screen parent) {
        super(Lang.c("gui.kve.recipe.multi.pick_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        W = Math.min(width - 16, 600);
        H = Math.min(height - 16, 320);
        L = (width - W) / 2;
        T = (height - H) / 2;
        clearWidgets();
        KveScaledEditBox search = new KveScaledEditBox(font, listX(), T + KveGuiStyle.TITLEBAR_H + 4, listW(), 12, Lang.c("gui.kve.hub.search"));
        search.setHint(Lang.c("gui.kve.hub.search"));
        search.setValue(query);
        search.setResponder(s -> { query = s; refilter(); });
        addRenderableWidget(search);
        refilter();
        int by = T + H - KveGuiStyle.PAD - KveGuiStyle.BTN_H;
        String closeL = Lang.get("gui.kve.btn.close");
        int closeX = L + W - KveGuiStyle.PAD - KveGuiStyle.btnWidth(closeL);
        addRenderableWidget(KveGuiStyle.smartBtn(closeX, by, 0, closeL, b -> onClose()));
        String lbl = Lang.get("gui.kve.conflict.edit_many", selected.size());
        KvePlateButton edit = KveGuiStyle.smartBtn(closeX - 6 - KveGuiStyle.btnWidth(lbl), by, 0, lbl, b -> {
            List<String> ids = new ArrayList<>(selected);
            minecraft.setScreen(parent);
            RecipeMultiEditScreen.open(parent, ids);
        }).withPrimary(true);
        edit.active = selected.size() >= 2;
        addRenderableWidget(edit);
    }

    private void refilter() {
        shown.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (RecipeData r : KubeJSExporter.getAddedRecipes()) {
            String out = r.output == null || r.output.isEmpty() ? "" : r.output.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (q.isEmpty() || r.recipeId.toLowerCase(Locale.ROOT).contains(q) || out.contains(q)) shown.add(r);
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, shown.size() - visibleRows())));
    }

    private int listX() { return L + KveGuiStyle.PAD; }
    private int listW() { return W - KveGuiStyle.PAD * 2 - CARD_W - 8; }
    private int listTop() { return T + KveGuiStyle.TITLEBAR_H + 20; }
    private int visibleRows() { return (T + H - KveGuiStyle.PAD - KveGuiStyle.BTN_H - 6 - listTop()) / ROW_H; }

    private RecipeData rowAt(double mx, double my) {
        if (mx < listX() || mx >= listX() + listW() || my < listTop()) return null;
        int i = (int) ((my - listTop()) / ROW_H);
        if (i >= visibleRows()) return null;
        i += scroll;
        return i < shown.size() ? shown.get(i) : null;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, W, H, getTitle().getString());
        int x = listX(), w = listW(), top = listTop(), vis = visibleRows();
        g.fill(x - 2, top - 2, x + w, top + vis * ROW_H + 1, KveGuiStyle.BG_FIELD);
        RecipeData hov = rowAt(mx, my);
        for (int i = 0; i < vis && i + scroll < shown.size(); i++) {
            RecipeData r = shown.get(i + scroll);
            int ry = top + i * ROW_H;
            boolean on = selected.contains(r.recipeId);
            if (r == hov || r.recipeId.equals(previewId)) g.fill(x - 2, ry, x + w, ry + ROW_H, r == hov ? KveGuiStyle.BORDER_FIELD : 0xFF1E3550);
            g.fill(x, ry + 2, x + CHECK, ry + 2 + CHECK, 0xFF0A1220);
            g.renderOutline(x, ry + 2, CHECK, CHECK, r == hov ? KveGuiStyle.ACCENT_GOLD : KveGuiStyle.BORDER_FIELD);
            if (on) g.fill(x + 2, ry + 4, x + CHECK - 2, ry + CHECK, KveGuiStyle.TEXT_OK);
            if (r.output != null && !r.output.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(x + CHECK + 3, ry + 1, 0);
                g.pose().scale(0.75f, 0.75f, 1f);
                SafeItemRender.render(g, r.output, 0, 0);
                g.pose().popPose();
            }
            KveGuiStyle.drawTextSafe(g, r.recipeId, x + CHECK + 18, ry + 3, w - CHECK - 22, on ? KveGuiStyle.TEXT_OK : KveGuiStyle.TEXT_PRIMARY);
        }
        if (shown.isEmpty()) KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.common.none"), x + 2, top + 2, w, KveGuiStyle.TEXT_MUTED);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.multi.pick_hint"), x, T + H - KveGuiStyle.PAD - 10, w - 4, KveGuiStyle.TEXT_MUTED);
        RecipeData card = hov != null ? hov : find(previewId);
        int cx = L + W - KveGuiStyle.PAD - CARD_W, cy = T + KveGuiStyle.TITLEBAR_H + 4;
        ItemStack cardHover = RecipeCardRenderer.draw(g, font, card, cx, cy, CARD_W, T + H - KveGuiStyle.PAD - KveGuiStyle.BTN_H - 6 - cy, mx, my);
        super.render(g, mx, my, pt);
        if (!cardHover.isEmpty()) g.renderTooltip(font, cardHover, mx, my);
    }

    private RecipeData find(String id) {
        if (id == null) return null;
        for (RecipeData r : shown) if (r.recipeId.equals(id)) return r;
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;
        RecipeData r = btn == 0 ? rowAt(mx, my) : null;
        if (r == null) return false;
        previewId = r.recipeId;
        if (!selected.remove(r.recipeId)) selected.add(r.recipeId);
        init();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, shown.size() - visibleRows()), scroll - (int) Math.signum(delta) * 3));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
