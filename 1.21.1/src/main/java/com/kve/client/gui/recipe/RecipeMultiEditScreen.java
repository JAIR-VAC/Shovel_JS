package com.kve.client.gui.recipe;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.DiffPreviewScreen;

import com.kve.client.gui.studio.KveTabHost;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.client.permission.ClientPermissionState;
import com.kve.common.Lang;
import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.recipe.RecipeData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Varias recetas abiertas a la vez, cada una en su propio editor completo (las mismas
 * herramientas de siempre), lado a lado para compararlas y editarlas. Entran tantos
 * editores como permita el ancho; arriba se elige que receta va en cada mitad.
 */
@OnlyIn(Dist.CLIENT)
public class RecipeMultiEditScreen extends Screen implements KveTabHost {

    private static final int TOP_H = 20, BOTTOM_H = 22, GAP = 6, MAX_PANES = 3;

    private final Screen parent;
    private final List<CraftEditorScreen> editors = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private final int[] shown = new int[MAX_PANES];
    private int panes = 1;
    private int focusPane;
    private int paneW;
    private int lastPending = -1;
    private static final int ARROW_W = 14;
    private int chipScroll, chipScrollMax;

    public RecipeMultiEditScreen(Screen parent, List<RecipeData> recipes) {
        super(Lang.c("gui.kve.recipe.multi.title"));
        this.parent = parent;
        for (RecipeData r : recipes) {
            CraftEditorScreen e = new CraftEditorScreen(KubeJSExporter.copyRecipe(r), null);
            e.withReturnScreen(this);
            editors.add(e);
            labels.add(r.recipeId == null || r.recipeId.isBlank() ? "?" : r.recipeId);
        }
        for (int i = 0; i < MAX_PANES; i++) shown[i] = Math.min(i, Math.max(0, editors.size() - 1));
    }

    /** Abre las recetas tuyas con esos ids; con una sola va directo al editor normal. */
    public static void open(Screen parent, List<String> recipeIds) {
        List<RecipeData> found = new ArrayList<>();
        for (String id : recipeIds) {
            for (RecipeData r : KubeJSExporter.getAddedRecipes()) if (r.recipeId.equals(id)) { found.add(r); break; }
        }
        if (found.isEmpty()) return;
        if (found.size() == 1) {
            com.kve.client.gui.studio.KveStudioShell.openRecipe(parent, KubeJSExporter.copyRecipe(found.get(0)), null);
            return;
        }
        net.minecraft.client.Minecraft.getInstance().setScreen(new RecipeMultiEditScreen(parent, found));
    }

    private int paneX(int i) { return 4 + i * (paneW + GAP); }
    private int paneH() { return height - TOP_H - BOTTOM_H; }

    @Override
    protected void init() {
        clearWidgets();
        int avail = width - 8;
        panes = 1;
        for (int n = Math.min(MAX_PANES, editors.size()); n >= 1; n--) {
            int w = (avail - GAP * (n - 1)) / n;
            boolean fits = true;
            for (int i = 0; i < n && fits; i++) {
                CraftEditorScreen e = editors.get(shown[i]);
                e.init(4 + i * (w + GAP), TOP_H, w, paneH());
                if (e.minHostedWidth() > w) fits = false;
            }
            if (fits || n == 1) { panes = n; break; }
        }
        // en una sola mitad no puede haber la misma receta repetida
        for (int i = 1; i < panes; i++) {
            for (int j = 0; j < i; j++) if (shown[i] == shown[j]) shown[i] = nextFree(i);
        }
        focusPane = Math.min(focusPane, panes - 1);
        paneW = (avail - GAP * (panes - 1)) / panes;
        for (int i = 0; i < panes; i++) editors.get(shown[i]).init(paneX(i), TOP_H, paneW, paneH());
        buildBars();
    }

    private int nextFree(int pane) {
        for (int k = 0; k < editors.size(); k++) {
            boolean used = false;
            for (int j = 0; j < pane; j++) if (shown[j] == k) used = true;
            if (!used) return k;
        }
        return shown[pane];
    }

    private void buildBars() {
        // con muchas recetas la barra se desplaza: flechas a los lados y rueda del mouse encima
        int chipY = 3, maxChip = 140;
        int[] widths = new int[editors.size()];
        int total = 0;
        for (int k = 0; k < editors.size(); k++) {
            widths[k] = Math.min(maxChip, KveGuiStyle.btnWidth(chipLabel(k)));
            total += widths[k] + 2;
        }
        boolean overflow = total > width - 8;
        int x0 = overflow ? 4 + ARROW_W + 2 : 4, x1 = overflow ? width - 4 - ARROW_W - 2 : width - 4;
        chipScrollMax = Math.max(0, total - (x1 - x0));
        chipScroll = Math.max(0, Math.min(chipScroll, chipScrollMax));
        int x = x0 - chipScroll;
        for (int k = 0; k < editors.size(); k++) {
            final int idx = k;
            int w = widths[k];
            if (x >= x0 && x + w <= x1) {
                KvePlateButton chip = new KvePlateButton(x, chipY, w, 14, Component.literal(chipLabel(k)), b -> showInFocused(idx)).withSelected(paneOf(k) >= 0);
                chip.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c("gui.kve.recipe.multi.chip_tooltip", labels.get(k))));
                addRenderableWidget(chip);
            }
            x += w + 2;
        }
        if (overflow) {
            KvePlateButton left = new KvePlateButton(4, chipY, ARROW_W, 14, Component.literal("◀"), b -> scrollChips(-120));
            left.active = chipScroll > 0;
            addRenderableWidget(left);
            KvePlateButton right = new KvePlateButton(width - 4 - ARROW_W, chipY, ARROW_W, 14, Component.literal("▶"), b -> scrollChips(120));
            right.active = chipScroll < chipScrollMax;
            addRenderableWidget(right);
        }
        int by = height - BOTTOM_H + 4;
        String closeL = Lang.get("gui.kve.btn.close");
        int closeX = width - 4 - KveGuiStyle.btnWidth(closeL);
        addRenderableWidget(KveGuiStyle.smartBtn(closeX, by, 0, closeL, b -> onClose()));
        int pend = PendingChangeManager.size();
        lastPending = pend;
        String applyL = Lang.get("gui.kve.pending.apply") + (pend > 0 ? " (" + pend + ")" : "");
        int applyX = closeX - 4 - KveGuiStyle.btnWidth(applyL);
        KvePlateButton apply = KveGuiStyle.smartBtn(applyX, by, 0, applyL, b ->
                com.kve.client.gui.KveToast.applied(PendingChangeManager.applyDomain(PendingDomain.RECIPES))).withPrimary(true);
        apply.active = pend > 0 && ClientPermissionState.canApply();
        addRenderableWidget(apply);
        String diffL = Lang.get("gui.kve.recipe.multi.review");
        int diffX = applyX - 4 - KveGuiStyle.btnWidth(diffL);
        KvePlateButton diff = KveGuiStyle.smartBtn(diffX, by, 0, diffL, b -> minecraft.setScreen(new DiffPreviewScreen(this,
                PendingChangeManager.snapshot(), () -> com.kve.client.gui.KveToast.applied(PendingChangeManager.applyAll()))));
        diff.active = pend > 0;
        addRenderableWidget(diff);
    }

    private String chipLabel(int k) {
        int pane = paneOf(k);
        return (pane >= 0 ? (pane + 1) + ": " : "") + labels.get(k);
    }

    private void scrollChips(int dx) {
        chipScroll = Math.max(0, Math.min(chipScrollMax, chipScroll + dx));
        init();
    }

    private int paneOf(int editor) {
        for (int i = 0; i < panes; i++) if (shown[i] == editor) return i;
        return -1;
    }

    /** Pone esa receta en la mitad con foco; si ya estaba en la otra, se intercambian. */
    private void showInFocused(int editor) {
        int other = paneOf(editor);
        if (other >= 0) shown[other] = shown[focusPane];
        shown[focusPane] = editor;
        init();
    }

    private int paneAt(double mx, double my) {
        if (my < TOP_H || my >= height - BOTTOM_H) return -1;
        for (int i = 0; i < panes; i++) if (mx >= paneX(i) && mx < paneX(i) + paneW) return i;
        return -1;
    }

    private CraftEditorScreen focused() { return editors.get(shown[focusPane]); }

    @Override
    public void tick() {
        for (int i = 0; i < panes; i++) editors.get(shown[i]).tick();
        if (PendingChangeManager.size() != lastPending) init();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        // si un editor cambio de modo y ya no entra en su mitad, se rearma el reparto
        for (int i = 0; i < panes; i++) {
            if (panes > 1 && editors.get(shown[i]).minHostedWidth() > paneW) { init(); break; }
        }
        for (int i = 0; i < panes; i++) {
            int px = paneX(i);
            editors.get(shown[i]).render(g, mx, my, pt);
            if (panes > 1 && i == focusPane) g.renderOutline(px - 1, TOP_H - 1, paneW + 2, paneH() + 2, KveGuiStyle.ACCENT_GOLD);
        }
        g.fill(0, 0, width, TOP_H, KveGuiStyle.BG_TITLEBAR);
        g.fill(0, height - BOTTOM_H, width, height, KveGuiStyle.BG_TITLEBAR);
        g.hLine(0, width - 1, TOP_H - 1, KveGuiStyle.BORDER_SUBTLE);
        g.hLine(0, width - 1, height - BOTTOM_H, KveGuiStyle.BORDER_SUBTLE);
        String hint = panes < Math.min(2, editors.size()) ? Lang.get("gui.kve.recipe.multi.one_fits")
                : Lang.get("gui.kve.recipe.multi.hint", panes, editors.size());
        KveGuiStyle.drawTextSafe(g, hint, 6, height - BOTTOM_H + 7, Math.max(40, width / 2 - 20), KveGuiStyle.TEXT_MUTED);
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // un menu "Mas" abierto recibe el clic antes que lo que tenga debajo
        if (com.kve.client.gui.KveMenuButton.consumeClick(mx, my, btn)) return true;
        if (super.mouseClicked(mx, my, btn)) return true;
        int p = paneAt(mx, my);
        if (p < 0) return false;
        if (p != focusPane) { focusPane = p; init(); }
        return focused().mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (super.mouseDragged(mx, my, btn, dx, dy)) return true;
        return focused().mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        boolean mine = super.mouseReleased(mx, my, btn);
        return focused().mouseReleased(mx, my, btn) || mine;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (my < TOP_H && chipScrollMax > 0) { scrollChips((int) Math.round(-delta * 60)); return true; }
        int p = paneAt(mx, my);
        if (p >= 0 && editors.get(shown[p]).mouseScrolled(mx, my, scrollX, delta)) return true;
        return super.mouseScrolled(mx, my, scrollX, delta);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        return focused().charTyped(c, mods) || super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (focused().keyPressed(key, scan, mods)) return true;
        return super.keyPressed(key, scan, mods);
    }

    @Override public boolean hostsActive(Screen tab) {
        for (int i = 0; i < panes; i++) if (editors.get(shown[i]) == tab) return true;
        return false;
    }

    @Override public void exitHost() { minecraft.setScreen(parent); }

    @Override
    public void removed() {
        for (CraftEditorScreen e : editors) e.removed();
        super.removed();
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
