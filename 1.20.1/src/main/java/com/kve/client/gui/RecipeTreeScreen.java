package com.kve.client.gui;

import com.kve.common.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Vista en LISTA del arbol de recetas (la vista principal es el
 * grafo, {@link RecipeGraphScreen}; esta queda como alternativa compacta).
 * Dos modos sobre un item raiz:
 * <ul>
 *   <li><b>Como se hace</b>: item -&gt; recetas que lo dan -&gt; sus ingredientes -&gt; ...</li>
 *   <li><b>Para que sirve</b>: item -&gt; recetas que lo usan -&gt; lo que dan -&gt; ...</li>
 * </ul>
 * Cada receta tiene "Editar" (la carga en el Craft Editor). Click derecho en un
 * item lo vuelve la raiz. Los hijos se calculan al abrir cada nodo, y un item
 * que ya aparece mas arriba en la misma rama no se vuelve a abrir (ciclos).
 */
@OnlyIn(Dist.CLIENT)
public class RecipeTreeScreen extends Screen implements KveOpaqueScreen {

    private static final int ROW_H = 18;
    private static final int INDENT = 12;
    private static final int MAX_CHILDREN = 40;

    private final Screen parent;
    private ItemStack root;
    private boolean usesMode;

    private int L, T, W, H;
    private ScrollBar scrollBar;
    private final List<Node> visible = new ArrayList<>();
    private Node rootNode;
    private String flashMsg = "";
    private long flashAt = 0;

    public RecipeTreeScreen(Screen parent, ItemStack root) {
        this(parent, root, false);
    }

    public RecipeTreeScreen(Screen parent, ItemStack root, boolean usesMode) {
        super(Lang.c("gui.kve.recipe.tree.title"));
        this.parent = parent;
        this.root = root.copyWithCount(1);
        this.usesMode = usesMode;
    }

    private final class Node {
        final Node parent;
        final int depth;
        final ItemStack stack;
        final RecipeIndex.Entry recipe;
        final String tagId;
        final int alternatives;
        boolean expanded;
        List<Node> children;

        Node(Node parent, ItemStack stack, RecipeIndex.Entry recipe, String tagId, int alternatives) {
            this.parent = parent;
            this.depth = parent == null ? 0 : parent.depth + 1;
            this.stack = stack;
            this.recipe = recipe;
            this.tagId = tagId;
            this.alternatives = alternatives;
        }

        boolean isRecipe() { return recipe != null; }

        /** Item que ya aparece mas arriba en la rama: no se abre (evita ciclos). */
        boolean repeatsAncestor() {
            if (isRecipe()) return false;
            for (Node p = parent; p != null; p = p.parent) {
                if (!p.isRecipe() && p.stack.getItem() == stack.getItem()) return true;
            }
            return false;
        }

        boolean canExpand() {
            if (repeatsAncestor()) return false;
            if (children != null) return !children.isEmpty();
            return isRecipe() || !recipesFor(stack.getItem()).isEmpty();
        }

        List<Node> children() {
            if (children == null) children = buildChildren(this);
            return children;
        }
    }

    private List<Node> buildChildren(Node n) {
        RecipeIndex idx = RecipeIndex.get();
        List<Node> out = new ArrayList<>();
        if (!n.isRecipe()) {
            for (RecipeIndex.Entry r : recipesFor(n.stack.getItem())) {
                if (out.size() >= MAX_CHILDREN) break;
                out.add(new Node(n, r.result, r, null, 0));
            }
        } else if (usesMode) {
            ItemStack res = n.recipe.result;
            if (!res.isEmpty()) out.add(new Node(n, res, null, null, 0));
        } else {
            for (RecipeIndex.Ing ing : idx.inputsOf(n.recipe)) out.add(new Node(n, ing.shown, null, ing.tag, ing.alternatives));
        }
        return out;
    }

    private List<RecipeIndex.Entry> recipesFor(Item item) {
        RecipeIndex idx = RecipeIndex.get();
        return usesMode ? idx.using(item) : idx.producing(item);
    }

    @Override
    protected void init() {
        W = Math.min(width - 16, 480);
        H = Math.min(height - 16, 300);
        L = (width - W) / 2;
        T = (height - H) / 2;
        clearWidgets();
        if (rootNode == null) rebuildRoot();

        KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(L + KveGuiStyle.PAD, T + KveGuiStyle.TITLEBAR_H + 2, L + W - KveGuiStyle.PAD);
        addRenderableWidget(row.add(0, Lang.get("gui.kve.recipe.tree.mode_how"), b -> setMode(false)).withSelected(!usesMode));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.recipe.tree.mode_uses"), b -> setMode(true)).withSelected(usesMode));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.recipe.tree.change_item"), b ->
                minecraft.setScreen(new ItemPickerScreen(this, s -> { root = s.copyWithCount(1); rebuildRoot(); }))));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.recipe.tree.collapse"), b -> { rebuildRoot(); init(); }));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.recipe.graph.view_graph"), b ->
                minecraft.setScreen(new RecipeGraphScreen(parent, root, usesMode))));
        String closeL = Lang.get("gui.kve.btn.close");
        addRenderableWidget(KveGuiStyle.smartBtn(L + W - KveGuiStyle.PAD - KveGuiStyle.btnWidth(closeL), T + H - KveGuiStyle.PAD - KveGuiStyle.BTN_H,
                0, closeL, b -> onClose()));

        int keep = scrollBar == null ? 0 : scrollBar.getScroll();
        scrollBar = new ScrollBar(L + W - KveGuiStyle.PAD - 6, listTop(), visibleRows() * ROW_H, 0, visibleRows());
        rebuildVisible();
        scrollBar.setScroll(keep);
    }

    private void setMode(boolean uses) {
        if (usesMode == uses) return;
        usesMode = uses;
        rebuildRoot();
        init();
    }

    private void rebuildRoot() {
        rootNode = new Node(null, root, null, null, 0);
        rootNode.expanded = true;
        if (scrollBar != null) scrollBar.setScroll(0);
        rebuildVisible();
    }

    private void rebuildVisible() {
        visible.clear();
        if (rootNode != null) addVisible(rootNode);
        if (scrollBar != null) scrollBar.setTotals(visible.size(), visibleRows());
    }

    private void addVisible(Node n) {
        visible.add(n);
        if (n.expanded && n.canExpand()) for (Node c : n.children()) addVisible(c);
    }

    private int listTop() { return T + KveGuiStyle.TITLEBAR_H + 20; }
    private int listBottom() { return T + H - KveGuiStyle.PAD - KveGuiStyle.BTN_H - 14; }
    private int visibleRows() { return Math.max(1, (listBottom() - listTop()) / ROW_H); }
    private int editBtnW() { return KveGuiStyle.btnWidth(Lang.get("gui.kve.recipe.tree.edit")); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, W, H, Lang.get("gui.kve.recipe.tree.title") + ": " + root.getHoverName().getString());
        int x0 = L + KveGuiStyle.PAD, right = L + W - KveGuiStyle.PAD - 10, top = listTop();
        g.fill(x0 - 2, top - 2, right + 2, top + visibleRows() * ROW_H + 1, KveGuiStyle.BG_FIELD);
        int scroll = scrollBar.getScroll();
        Node hoveredItem = null;
        for (int i = 0; i < visibleRows() && i + scroll < visible.size(); i++) {
            Node n = visible.get(i + scroll);
            int ry = top + i * ROW_H;
            int x = x0 + n.depth * INDENT;
            boolean hov = mx >= x0 && mx < right && my >= ry && my < ry + ROW_H;
            if (hov) g.fill(x0 - 2, ry, right + 2, ry + ROW_H, KveGuiStyle.BORDER_FIELD);
            if (n.canExpand()) {
                KveGuiStyle.drawTextSafe(g, n.expanded ? "▼" : "▶", x, ry + 5, 10, KveGuiStyle.TEXT_LABEL);
            } else if (n.repeatsAncestor()) {
                KveGuiStyle.drawTextSafe(g, "↺", x, ry + 5, 10, KveGuiStyle.TEXT_MUTED);
            }
            int ix = x + 10;
            if (n.isRecipe()) {
                SafeItemRender.render(g, n.recipe.station, ix, ry + 1);
                int editW = editBtnW();
                int editX = right - editW;
                String text = Lang.get("gui.kve.recipe.tree.recipe_row", n.recipe.typeLabel, n.recipe.id.toString())
                        + (n.recipe.pending() ? "  " + Lang.get("gui.kve.recipe.graph.pending") : "");
                KveGuiStyle.drawTextSafe(g, text, ix + 20, ry + 5, editX - ix - 24, n.recipe.pending() ? 0xFFFFAA55 : KveGuiStyle.ACCENT_GOLD);
                boolean hovEdit = mx >= editX && mx < editX + editW && my >= ry + 2 && my < ry + ROW_H - 2;
                g.fill(editX, ry + 2, editX + editW, ry + ROW_H - 2, hovEdit ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_TITLEBAR);
                KveGuiStyle.drawCenteredTextSafe(g, Lang.get("gui.kve.recipe.tree.edit"), editX + editW / 2, ry + 5,
                        hovEdit ? KveGuiStyle.TEXT_OK : KveGuiStyle.TEXT_LABEL);
            } else {
                SafeItemRender.render(g, n.stack, ix, ry + 1);
                String name = n.stack.getHoverName().getString();
                String label = (n.stack.getCount() > 1 ? n.stack.getCount() + " x " : "") + name
                        + (n.tagId != null ? "  #" + n.tagId : "")
                        + (n.alternatives > 0 ? "  " + Lang.get("gui.kve.recipe.tree.alternatives", n.alternatives) : "");
                int count = n.repeatsAncestor() ? 0 : recipesFor(n.stack.getItem()).size();
                String suffix = n.repeatsAncestor() ? Lang.get("gui.kve.recipe.tree.cycle")
                        : count == 0 ? Lang.get(usesMode ? "gui.kve.recipe.tree.no_uses" : "gui.kve.recipe.tree.base_item")
                        : Lang.get(usesMode ? "gui.kve.recipe.tree.uses_count" : "gui.kve.recipe.tree.recipes_count", count);
                int sw = Math.min(140, Math.round(font.width(suffix) * KveGuiStyle.uiTextScale()) + 4);
                KveGuiStyle.drawTextSafe(g, label, ix + 20, ry + 5, right - sw - ix - 24, n.tagId != null ? KveGuiStyle.ACCENT_GOLD : KveGuiStyle.TEXT_PRIMARY);
                KveGuiStyle.drawTextSafe(g, suffix, right - sw, ry + 5, sw, KveGuiStyle.TEXT_MUTED);
                if (hov && mx >= ix && mx < ix + 16) hoveredItem = n;
            }
        }
        scrollBar.render(g, mx, my);
        if (visible.size() <= 1 && rootNode != null && !rootNode.canExpand()) {
            KveGuiStyle.drawTextSafe(g, Lang.get(usesMode ? "gui.kve.recipe.tree.empty_uses" : "gui.kve.recipe.tree.empty_how"),
                    x0 + 4, top + ROW_H + 6, right - x0 - 8, KveGuiStyle.TEXT_MUTED);
        }
        String hint = !flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4500 ? flashMsg : Lang.get("gui.kve.recipe.tree.hint");
        KveGuiStyle.drawTextSafe(g, hint, x0, listBottom() + 4, W - KveGuiStyle.PAD * 2 - 60, KveGuiStyle.TEXT_MUTED);
        super.render(g, mx, my, pt);
        if (hoveredItem != null) g.renderTooltip(font, hoveredItem.stack, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar.mouseClicked(mx, my, btn)) return true;
        int x0 = L + KveGuiStyle.PAD, right = L + W - KveGuiStyle.PAD - 10, top = listTop();
        if (mx >= x0 && mx < right && my >= top && my < top + visibleRows() * ROW_H) {
            int i = (int) ((my - top) / ROW_H) + scrollBar.getScroll();
            if (i < visible.size()) {
                Node n = visible.get(i);
                if (n.isRecipe() && btn == 0 && mx >= right - editBtnW()) {
                    String err = RecipeIndex.get().openInEditor(parent, n.recipe);
                    if (err != null) { flashMsg = err; flashAt = System.currentTimeMillis(); }
                    return true;
                }
                if (!n.isRecipe() && btn == 1) {
                    root = n.stack.copyWithCount(1);
                    rebuildRoot();
                    init();
                    return true;
                }
                if (btn == 0 && n.canExpand()) {
                    n.expanded = !n.expanded;
                    rebuildVisible();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (scrollBar.mouseDragged(mx, my, b, dx, dy)) return true;
        return super.mouseDragged(mx, my, b, dx, dy);
    }

    @Override public boolean mouseReleased(double mx, double my, int b) {
        scrollBar.mouseReleased(mx, my, b);
        return super.mouseReleased(mx, my, b);
    }

    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        scrollBar.mouseScrolled(delta);
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
