package com.kve.client.gui.recipe;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KveOpaqueScreen;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.SafeItemRender;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.picker.ItemPickerScreen;

import com.kve.common.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class RecipeGraphScreen extends Screen implements KveOpaqueScreen {

    private static final int MAX_NODES = 120;
    private static final int MAX_USES_FIRST = 12, MAX_USES_DEEPER = 6;
    private static final int ICON = 18, INPUT_COLS = 4, COL_GAP = 48, ROW_GAP = 12;
    private static final float ICON_MIN_ZOOM = 0.45F;
    private static final int HISTORY_MAX = 40;

    private final Screen parent;
    private ItemStack root;
    private boolean usesMode;
    private int maxDepth;
    private final Map<Item, Integer> choice = new HashMap<>();

    private final NodeCanvasWidget canvas = new NodeCanvasWidget(18F, 20F, 0.2F, 2.0F);
    private int L, T, W, H, cx, cy, cw, ch;
    private int contentW, contentH;

    private final List<GNode> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private GNode selected;
    private boolean truncated;
    private String flashMsg = "";
    private final RecipeQuickEditPanel quick = new RecipeQuickEditPanel();
    private static boolean quickOpen;
    private boolean pendingCenter;
    private boolean layoutOpen;
    private long flashAt;

    private record View(ItemStack root, boolean usesMode, int maxDepth, Map<Item, Integer> choice) {}

    private static final Deque<View> BACK = new ArrayDeque<>();
    private static final Deque<View> FORWARD = new ArrayDeque<>();
    private static View last;

    /** Ultimo item visto en el grafo (para reabrirlo sin buscarlo), o EMPTY. */
    public static ItemStack lastRoot() { return last == null ? ItemStack.EMPTY : last.root().copy(); }

    public static boolean lastUsesMode() { return last != null && last.usesMode(); }

    Screen parentScreen() { return parent; }

    public RecipeGraphScreen(Screen parent, ItemStack root, boolean usesMode) {
        super(Lang.c("gui.kve.recipe.graph.title"));
        this.parent = parent;
        quick.setOpen(quickOpen);
        this.root = root.copyWithCount(1);
        this.usesMode = usesMode;
        this.maxDepth = usesMode ? 2 : 4;
        if (last != null && last.usesMode() == usesMode && ItemStack.isSameItemSameComponents(last.root(), this.root)) {
            this.maxDepth = last.maxDepth();
            this.choice.putAll(last.choice());
        } else if (last != null) {
            pushHistory(last);
        }
    }

    private View currentView() { return new View(root.copy(), usesMode, maxDepth, new HashMap<>(choice)); }

    private static void pushHistory(View v) {
        if (!BACK.isEmpty() && sameView(BACK.peek(), v)) return;
        BACK.push(v);
        while (BACK.size() > HISTORY_MAX) BACK.removeLast();
    }

    private static boolean sameView(View a, View b) {
        return a.usesMode() == b.usesMode() && ItemStack.isSameItemSameComponents(a.root(), b.root());
    }

    private void navigate(ItemStack newRoot, boolean newUses) {
        pushHistory(currentView());
        FORWARD.clear();
        root = newRoot.copyWithCount(1);
        if (newUses != usesMode) maxDepth = newUses ? 2 : 4;
        usesMode = newUses;
        choice.clear();
        rebuild();
        buildWidgets();
    }

    private void goBack() {
        if (BACK.isEmpty()) return;
        FORWARD.push(currentView());
        apply(BACK.pop());
    }

    private void goForward() {
        if (FORWARD.isEmpty()) return;
        pushHistory(currentView());
        apply(FORWARD.pop());
    }

    private void apply(View v) {
        root = v.root().copy();
        usesMode = v.usesMode();
        maxDepth = v.maxDepth();
        choice.clear();
        choice.putAll(v.choice());
        rebuild();
        buildWidgets();
    }

    private abstract static class GNode {
        int rank, order;
        int x, y, w, h;
        final List<Edge> in = new ArrayList<>(), out = new ArrayList<>();
        boolean contains(double wx, double wy) { return wx >= x && wx < x + w && wy >= y && wy < y + h; }
        boolean intersects(double x0, double y0, double x1, double y1) { return x + w >= x0 && x <= x1 && y + h >= y0 && y <= y1; }
    }

    private static final class RecipeNode extends GNode {
        final RecipeIndex.Entry recipe;
        final ItemStack result;
        final List<RecipeIndex.Ing> inputs;
        final Item chosenFor;
        String typeText = "";
        int swatch;
        RecipeNode(RecipeIndex.Entry r, ItemStack result, List<RecipeIndex.Ing> inputs, Item chosenFor) {
            this.recipe = r; this.result = result; this.inputs = inputs; this.chosenFor = chosenFor;
        }
    }

    private static final class ItemNode extends GNode {
        final ItemStack stack;
        final boolean cycle;
        final boolean cutByDepth;
        final boolean isRoot;
        String nameText = "";
        ItemNode(ItemStack stack, boolean cycle, boolean cutByDepth, boolean isRoot) {
            this.stack = stack; this.cycle = cycle; this.cutByDepth = cutByDepth; this.isRoot = isRoot;
        }
    }

    private record Edge(GNode from, GNode to, ItemStack item, int color) {}

    private void rebuild() {
        nodes.clear();
        edges.clear();
        selected = null;
        if (!quick.isDirty()) quick.load(null);
        truncated = false;
        RecipeIndex idx = RecipeIndex.get();
        Map<RecipeIndex.Entry, RecipeNode> recipeNodes = new IdentityHashMap<>();
        Map<Item, ItemNode> itemNodes = new HashMap<>();
        if (usesMode) {
            ItemNode rootNode = new ItemNode(root, false, false, true);
            nodes.add(rootNode);
            Set<Item> path = new HashSet<>();
            path.add(root.getItem());
            expandUses(idx, root.getItem(), rootNode, 1, path, recipeNodes);
        } else {
            buildHow(idx, root.getItem(), 0, new HashSet<>(), recipeNodes, itemNodes, true);
        }
        layout();
        canvas.forceRefit();
        last = currentView();
    }

    private GNode buildHow(RecipeIndex idx, Item item, int depth, Set<Item> path,
                           Map<RecipeIndex.Entry, RecipeNode> recipeNodes, Map<Item, ItemNode> itemNodes, boolean isRoot) {
        ItemStack stack = new ItemStack(item);
        if (path.contains(item)) return itemNode(itemNodes, stack, true, false, isRoot);
        List<RecipeIndex.Entry> rs = idx.producing(item);
        if (rs.isEmpty()) return itemNode(itemNodes, stack, false, false, isRoot);
        if (depth >= maxDepth || nodes.size() >= MAX_NODES) {
            if (nodes.size() >= MAX_NODES) truncated = true;
            return itemNode(itemNodes, stack, false, true, isRoot);
        }
        RecipeIndex.Entry r = rs.get(Math.floorMod(choice.getOrDefault(item, 0), rs.size()));
        RecipeNode existing = recipeNodes.get(r);
        if (existing != null) return existing;
        RecipeNode rn = new RecipeNode(r, r.result, idx.inputsOf(r), item);
        recipeNodes.put(r, rn);
        nodes.add(rn);
        path.add(item);
        for (RecipeIndex.Ing ing : rn.inputs) {
            GNode child = buildHow(idx, ing.shown.getItem(), depth + 1, path, recipeNodes, itemNodes, false);
            connect(child, rn, ing.shown);
        }
        path.remove(item);
        return rn;
    }

    private ItemNode itemNode(Map<Item, ItemNode> itemNodes, ItemStack stack, boolean cycle, boolean cut, boolean isRoot) {
        if (!cycle && !isRoot) {
            ItemNode prev = itemNodes.get(stack.getItem());
            if (prev != null) return prev;
        }
        ItemNode n = new ItemNode(stack, cycle, cut, isRoot);
        if (!cycle && !isRoot) itemNodes.put(stack.getItem(), n);
        nodes.add(n);
        return n;
    }

    private void expandUses(RecipeIndex idx, Item item, GNode from, int depth, Set<Item> path, Map<RecipeIndex.Entry, RecipeNode> recipeNodes) {
        if (depth > maxDepth) return;
        List<RecipeIndex.Entry> rs = idx.using(item);
        int cap = depth == 1 ? MAX_USES_FIRST : MAX_USES_DEEPER;
        if (rs.size() > cap) truncated = true;
        int n = 0;
        for (RecipeIndex.Entry r : rs) {
            if (n++ >= cap) break;
            if (nodes.size() >= MAX_NODES) { truncated = true; return; }
            RecipeNode rn = recipeNodes.get(r);
            boolean fresh = rn == null;
            if (fresh) {
                rn = new RecipeNode(r, r.result, idx.inputsOf(r), null);
                recipeNodes.put(r, rn);
                nodes.add(rn);
            }
            if (rn == from || reaches(rn, from)) continue;
            connect(from, rn, new ItemStack(item));
            Item out = rn.result.isEmpty() ? null : rn.result.getItem();
            if (fresh && out != null && !path.contains(out)) {
                path.add(out);
                expandUses(idx, out, rn, depth + 1, path, recipeNodes);
                path.remove(out);
            }
        }
    }

    private static boolean reaches(GNode a, GNode b) {
        ArrayDeque<GNode> q = new ArrayDeque<>();
        Set<GNode> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        q.add(a);
        while (!q.isEmpty()) {
            GNode n = q.poll();
            if (n == b) return true;
            if (!seen.add(n)) continue;
            for (Edge e : n.out) q.add(e.to());
        }
        return false;
    }

    private void connect(GNode from, GNode to, ItemStack item) {
        for (Edge e : to.in) if (e.from() == from) return;
        Edge e = new Edge(from, to, item, edgeColor(item));
        edges.add(e);
        from.out.add(e);
        to.in.add(e);
    }

    private void layout() {
        for (GNode n : nodes) measure(n);
        for (GNode n : nodes) n.rank = 0;
        for (int iter = 0; iter < nodes.size(); iter++) {
            boolean changed = false;
            for (Edge e : edges) {
                if (e.to().rank < e.from().rank + 1) { e.to().rank = e.from().rank + 1; changed = true; }
            }
            if (!changed) break;
        }
        int maxRank = 0;
        for (GNode n : nodes) maxRank = Math.max(maxRank, n.rank);
        List<List<GNode>> cols = new ArrayList<>();
        for (int r = 0; r <= maxRank; r++) cols.add(new ArrayList<>());
        for (GNode n : nodes) cols.get(n.rank).add(n);
        for (List<GNode> col : cols) for (int i = 0; i < col.size(); i++) col.get(i).order = i;
        for (int pass = 0; pass < 4; pass++) {
            boolean forward = pass % 2 == 0;
            for (int ri = 0; ri <= maxRank; ri++) {
                List<GNode> col = cols.get(forward ? ri : maxRank - ri);
                Map<GNode, Double> bary = new IdentityHashMap<>();
                for (GNode n : col) {
                    List<Edge> nb = forward ? n.in : n.out;
                    double s = 0; int c = 0;
                    for (Edge e : nb) { s += (forward ? e.from() : e.to()).order; c++; }
                    bary.put(n, c == 0 ? n.order : s / c);
                }
                col.sort(Comparator.comparingDouble(bary::get));
                for (int i = 0; i < col.size(); i++) col.get(i).order = i;
            }
        }
        int x = 0, tallest = 0;
        int[] colH = new int[cols.size()];
        for (int r = 0; r < cols.size(); r++) {
            int h = 0;
            for (GNode n : cols.get(r)) h += n.h + ROW_GAP;
            colH[r] = Math.max(0, h - ROW_GAP);
            tallest = Math.max(tallest, colH[r]);
        }
        for (int r = 0; r < cols.size(); r++) {
            int colW = 0;
            for (GNode n : cols.get(r)) colW = Math.max(colW, n.w);
            int y = (tallest - colH[r]) / 2;
            for (GNode n : cols.get(r)) {
                n.x = x + (colW - n.w) / 2;
                n.y = y;
                y += n.h + ROW_GAP;
            }
            x += colW + COL_GAP;
        }
        contentW = Math.max(1, x - COL_GAP);
        contentH = Math.max(1, tallest);
    }

    private void measure(GNode n) {
        if (n instanceof RecipeNode rn) {
            int cols = Math.max(1, Math.min(INPUT_COLS, rn.inputs.size()));
            int rows = Math.max(1, (rn.inputs.size() + INPUT_COLS - 1) / INPUT_COLS);
            int bodyW = cols * ICON + 14 + ICON;
            String type = rn.recipe.typeLabel;
            int typeW = 20 + Math.min(150, font.width(type));
            rn.w = 8 + Math.max(bodyW, typeW);
            rn.h = 4 + 16 + 2 + rows * ICON + 4;
            rn.typeText = font.plainSubstrByWidth(type, rn.w - 26);
            rn.swatch = edgeColor(rn.result);
        } else if (n instanceof ItemNode in) {
            String label = (in.cycle ? "↺ " : "") + in.stack.getHoverName().getString();
            in.w = 24 + Math.min(110, font.width(label)) + 6;
            in.h = 22;
            in.nameText = font.plainSubstrByWidth(label, in.w - 28);
        }
    }

    @Override
    protected void init() {
        W = width - 12;
        H = height - 12;
        L = 6;
        T = 6;
        quickOpen = quick.isOpen();
        layoutOpen = quickOpen;
        cy = T + KveGuiStyle.TITLEBAR_H + 20;
        ch = H - (cy - T) - 40;
        quick.layout(L + KveGuiStyle.PAD, cy, ch);
        cx = L + KveGuiStyle.PAD + quick.width() + 4;
        cw = W - KveGuiStyle.PAD * 2 - quick.width() - 4;
        if (nodes.isEmpty()) rebuild();
        buildWidgets();
    }

    private void buildWidgets() {
        clearWidgets();
        if (quick.isOpen() != layoutOpen) {
            pendingCenter = true;
            init(minecraft, width, height);
            return;
        }
        quick.addWidgets(this, w -> addRenderableWidget(w), this::afterQuickSave);
        KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(L + KveGuiStyle.PAD, T + KveGuiStyle.TITLEBAR_H + 2, L + W - KveGuiStyle.PAD);
        String backLbl = BACK.isEmpty() ? "◀" : "◀ " + KveGuiStyle.truncate(font, BACK.peek().root().getHoverName().getString(), 90);
        KvePlateButton backBtn = row.add(0, backLbl, b -> goBack());
        backBtn.active = !BACK.isEmpty();
        backBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c("gui.kve.recipe.graph.back_tooltip")));
        addRenderableWidget(backBtn);
        KvePlateButton fwdBtn = row.add(0, "▶", b -> goForward());
        fwdBtn.active = !FORWARD.isEmpty();
        fwdBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c("gui.kve.recipe.graph.forward_tooltip")));
        addRenderableWidget(fwdBtn);
        addRenderableWidget(row.add(0, Lang.get("gui.kve.recipe.tree.mode_how"), b -> setMode(false)).withSelected(!usesMode));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.recipe.tree.mode_uses"), b -> setMode(true)).withSelected(usesMode));
        addRenderableWidget(row.add(0, "-", b -> setDepth(maxDepth - 1)));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.recipe.graph.depth", maxDepth), b -> {}));
        addRenderableWidget(row.add(0, "+", b -> setDepth(maxDepth + 1)));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.recipe.tree.change_item"), b ->
                minecraft.setScreen(new ItemPickerScreen(this, s -> navigate(s, usesMode)))));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.recipe.graph.center"), b -> canvas.forceRefit()));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.recipe.graph.view_list"), b ->
                minecraft.setScreen(new RecipeTreeScreen(parent, root, usesMode))));

        int by = T + H - KveGuiStyle.PAD - KveGuiStyle.BTN_H;
        String closeL = Lang.get("gui.kve.btn.close");
        int closeX = L + W - KveGuiStyle.PAD - KveGuiStyle.btnWidth(closeL);
        addRenderableWidget(KveGuiStyle.smartBtn(closeX, by, 0, closeL, b -> onClose()));
        KveGuiStyle.ButtonRow sel = new KveGuiStyle.ButtonRow(L + KveGuiStyle.PAD, by, closeX - 6);
        if (selected instanceof RecipeNode rn) {
            if (!quick.isOpen()) {
                addRenderableWidget(sel.add(0, Lang.get("gui.kve.recipe.quick.open"), b -> openQuick()).withPrimary(true));
            }
            if (rn.chosenFor != null) {
                int total = RecipeIndex.get().producing(rn.chosenFor).size();
                if (total > 1) {
                    addRenderableWidget(sel.add(0, "◀", b -> cycleChoice(rn.chosenFor, -1)));
                    int cur = Math.floorMod(choice.getOrDefault(rn.chosenFor, 0), total) + 1;
                    addRenderableWidget(sel.add(0, Lang.get("gui.kve.recipe.graph.alternative", cur, total), b -> cycleChoice(rn.chosenFor, 1)));
                    addRenderableWidget(sel.add(0, "▶", b -> cycleChoice(rn.chosenFor, 1)));
                }
            }
            if (!rn.result.isEmpty()) addRenderableWidget(sel.add(0, Lang.get("gui.kve.recipe.graph.from_here"), b -> navigate(rn.result, usesMode)));
        } else if (selected instanceof ItemNode in && !in.isRoot) {
            addRenderableWidget(sel.add(0, Lang.get("gui.kve.recipe.graph.from_here"), b -> navigate(in.stack, usesMode)));
        }
    }

    private void setMode(boolean uses) {
        if (usesMode == uses) return;
        navigate(root, uses);
    }

    private void setDepth(int d) {
        int nd = Math.max(1, Math.min(8, d));
        if (nd == maxDepth) return;
        maxDepth = nd;
        rebuild();
        buildWidgets();
    }

    private void cycleChoice(Item item, int dir) {
        choice.merge(item, dir, Integer::sum);
        rebuild();
        for (GNode n : nodes) if (n instanceof RecipeNode rn && rn.chosenFor == item) { select(n); break; }
        buildWidgets();
    }

    private void openQuick() {
        quick.setOpen(true);
        buildWidgets();
    }

    /** Selecciona un nodo y lo pasa al panel; con cambios sin guardar el panel se queda en su receta. */
    private void select(GNode n) {
        selected = n;
        RecipeIndex.Entry e = n instanceof RecipeNode rn ? rn.recipe : null;
        if (quick.isDirty() && e != quick.entry()) {
            flashMsg = Lang.get("gui.kve.recipe.quick.unsaved");
            flashAt = System.currentTimeMillis();
        } else {
            quick.load(e);
        }
        if (quick.isOpen() && n != null) pendingCenter = true;
    }

    private void afterQuickSave() {
        String id = quick.draftId();
        rebuild();
        for (GNode n : nodes) {
            if (n instanceof RecipeNode rn && rn.recipe.pending() && rn.recipe.draft.recipeId.equals(id)) {
                selected = n;
                quick.adopt(rn.recipe);
                pendingCenter = true;
                break;
            }
        }
        buildWidgets();
    }

    private double worldX(double mx) { return (mx - canvas.sx(cx, 0)) / canvas.zoom() - 10; }
    private double worldY(double my) { return (my - canvas.sy(cy, 0)) / canvas.zoom() - 10; }
    private boolean inCanvas(double mx, double my) { return mx >= cx && mx < cx + cw && my >= cy && my < cy + ch; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, W, H, Lang.get("gui.kve.recipe.graph.title") + ": " + root.getHoverName().getString()
                + "  (" + Lang.get(usesMode ? "gui.kve.recipe.tree.mode_uses" : "gui.kve.recipe.tree.mode_how") + ")");
        canvas.fitFreeform(contentW + 20, contentH + 20, cw, ch);
        if (pendingCenter && selected != null) {
            canvas.centerOn(cw, ch, selected.x + 10 + selected.w / 2.0, selected.y + 10 + selected.h / 2.0);
        }
        pendingCenter = false;
        canvas.drawBackground(g, cx, cy, cw, ch);
        quick.render(g, font, mx, my);

        float zoom = canvas.zoom();
        boolean icons = zoom >= ICON_MIN_ZOOM;
        double vx0 = worldX(cx), vy0 = worldY(cy), vx1 = worldX(cx + cw), vy1 = worldY(cy + ch);
        double wx = worldX(mx), wy = worldY(my);
        boolean mouseIn = inCanvas(mx, my);
        GNode hovered = null;
        List<GNode> shown = new ArrayList<>();
        for (GNode n : nodes) {
            if (!n.intersects(vx0, vy0, vx1, vy1)) continue;
            shown.add(n);
            if (mouseIn && n.contains(wx, wy)) hovered = n;
        }
        GNode focus = selected != null ? selected : hovered;

        g.enableScissor(cx, cy, cx + cw, cy + ch);
        g.pose().pushPose();
        g.pose().translate(canvas.sx(cx, 0) + 10 * zoom, canvas.sy(cy, 0) + 10 * zoom, 0);
        g.pose().scale(zoom, zoom, 1F);

        final GNode hov = hovered;
        g.drawManaged(() -> {
            for (Edge e : edges) {
                if (!edgeVisible(e, vx0, vy0, vx1, vy1)) continue;
                drawEdge(g, e, focus != null && (e.from() == focus || e.to() == focus));
            }
            for (GNode n : shown) drawNodeChrome(g, n, n == selected, n == hov, icons);
        });
        ItemStack hoverStack = ItemStack.EMPTY;
        if (icons) {
            for (GNode n : shown) {
                ItemStack s = drawNodeIcons(g, n, wx, wy, mouseIn);
                if (!s.isEmpty()) hoverStack = s;
            }
        }
        g.pose().popPose();
        g.disableScissor();
        canvas.drawZoomReadout(g, cx, cy);

        int infoY = cy + ch + 4;
        String info;
        int color = KveGuiStyle.TEXT_MUTED;
        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4500) {
            info = flashMsg;
            color = KveGuiStyle.TEXT_WARN;
        } else if (selected instanceof RecipeNode rn) {
            info = Lang.get("gui.kve.recipe.tree.recipe_row", rn.recipe.typeLabel, rn.recipe.id.toString())
                    + (rn.recipe.pending() ? "  " + Lang.get("gui.kve.recipe.graph.pending") : "");
            color = KveGuiStyle.ACCENT_GOLD;
        } else if (selected instanceof ItemNode in) {
            info = in.cutByDepth ? Lang.get("gui.kve.recipe.graph.cut_by_depth", in.stack.getHoverName().getString())
                    : in.cycle ? Lang.get("gui.kve.recipe.tree.cycle")
                    : Lang.get(usesMode ? "gui.kve.recipe.graph.item_root" : "gui.kve.recipe.tree.base_item");
        } else if (nodes.size() <= 1) {
            info = Lang.get(usesMode ? "gui.kve.recipe.tree.empty_uses" : "gui.kve.recipe.tree.empty_how");
        } else {
            info = Lang.get("gui.kve.recipe.graph.hint", nodes.size(), edges.size())
                    + (truncated ? " " + Lang.get("gui.kve.recipe.graph.truncated") : "");
        }
        KveGuiStyle.drawTextSafe(g, info, cx, infoY, cw, color);

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
        ItemStack quickHover = quick.hovered(mx, my);
        String quickDetail = quick.hoveredDetail(mx, my);
        if (!quickHover.isEmpty() && quickDetail != null) {
            g.renderComponentTooltip(font, List.of(quickHover.getHoverName(), net.minecraft.network.chat.Component.literal(quickDetail)), mx, my);
        } else if (!quickHover.isEmpty()) g.renderTooltip(font, quickHover, mx, my);
        else if (quickDetail != null) g.renderTooltip(font, net.minecraft.network.chat.Component.literal(quickDetail), mx, my);
        else if (!hoverStack.isEmpty()) g.renderTooltip(font, hoverStack, mx, my);
        else if (!icons && hovered != null) {
            ItemStack s = hovered instanceof RecipeNode rn ? rn.result : ((ItemNode) hovered).stack;
            if (!s.isEmpty()) g.renderTooltip(font, s, mx, my);
        }
    }

    private static boolean edgeVisible(Edge e, double x0, double y0, double x1, double y1) {
        GNode a = e.from(), b = e.to();
        double minX = Math.min(a.x + a.w, b.x), maxX = Math.max(a.x + a.w, b.x);
        double minY = Math.min(a.y, b.y), maxY = Math.max(a.y + a.h, b.y + b.h);
        return maxX >= x0 && minX <= x1 && maxY >= y0 && minY <= y1;
    }

    private void drawEdge(GuiGraphics g, Edge e, boolean highlight) {
        GNode a = e.from(), b = e.to();
        int x1 = a.x + a.w, y1 = a.y + a.h / 2;
        int x2 = b.x, y2 = portY(b, e.item());
        int midX = x2 - COL_GAP / 2;
        if (midX < x1 + 4) midX = (x1 + x2) / 2;
        int col = highlight ? KveGuiStyle.ACCENT_GOLD : e.color();
        int t = highlight ? 2 : 1;
        g.fill(x1, y1, midX + t, y1 + t, col);
        g.fill(midX, Math.min(y1, y2), midX + t, Math.max(y1, y2) + t, col);
        g.fill(midX, y2, x2, y2 + t, col);
        g.fill(x2 - 3, y2 - 2, x2 - 2, y2 + 2 + t, col);
        g.fill(x2 - 2, y2 - 1, x2 - 1, y2 + 1 + t, col);
    }

    private int portY(GNode n, ItemStack item) {
        if (n instanceof RecipeNode rn) {
            for (int i = 0; i < rn.inputs.size(); i++) {
                if (rn.inputs.get(i).shown.getItem() == item.getItem()) {
                    return rn.y + 22 + (i / INPUT_COLS) * ICON + ICON / 2;
                }
            }
        }
        return n.y + n.h / 2;
    }

    private static int edgeColor(ItemStack s) {
        int h = s.getItem().hashCode();
        int r = 0x70 + ((h >> 16) & 0x5F), gr = 0x70 + ((h >> 8) & 0x5F), b = 0x90 + (h & 0x5F);
        return 0xFF000000 | (r << 16) | (gr << 8) | Math.min(255, b);
    }

    private void drawNodeChrome(GuiGraphics g, GNode n, boolean sel, boolean hov, boolean icons) {
        int border = sel ? KveGuiStyle.ACCENT_GOLD : hov ? KveGuiStyle.TEXT_LABEL : KveGuiStyle.BORDER_SUBTLE;
        if (n instanceof RecipeNode rn) {
            g.fill(n.x, n.y, n.x + n.w, n.y + n.h, 0xF0141C2C);
            g.fill(n.x, n.y, n.x + n.w, n.y + 20, 0xF01C2A40);
            outline(g, n, !sel && rn.recipe.pending() ? 0xFFFFAA55 : border);
            g.drawString(font, rn.typeText, n.x + 22, n.y + 6, KveGuiStyle.ACCENT_GOLD, false);
            int bx = n.x + 4, by = n.y + 22;
            for (int i = 0; i < rn.inputs.size(); i++) {
                int ix = bx + (i % INPUT_COLS) * ICON, iy = by + (i / INPUT_COLS) * ICON;
                g.fill(ix, iy, ix + 16, iy + 16, icons ? 0xFF0A1220 : edgeColor(rn.inputs.get(i).shown));
            }
            int cols = Math.max(1, Math.min(INPUT_COLS, rn.inputs.size()));
            int rows = Math.max(1, (rn.inputs.size() + INPUT_COLS - 1) / INPUT_COLS);
            int arrowX = bx + cols * ICON + 1;
            int midY = by + (rows * ICON) / 2 - 4;
            g.drawString(font, "→", arrowX, midY, KveGuiStyle.TEXT_MUTED, false);
            int ox = arrowX + 12, oy = midY - 4;
            g.fill(ox - 1, oy - 1, ox + 17, oy + 17, 0xFF2A4A6A);
            g.fill(ox, oy, ox + 16, oy + 16, icons ? 0xFF0A1220 : rn.swatch);
        } else if (n instanceof ItemNode in) {
            int bg = in.isRoot ? 0xF0203A28 : in.cycle ? 0xF0181820 : 0xF0142418;
            g.fill(n.x, n.y, n.x + n.w, n.y + n.h, bg);
            outline(g, n, sel ? KveGuiStyle.ACCENT_GOLD : in.isRoot ? KveGuiStyle.TEXT_OK : border);
            if (!icons) g.fill(n.x + 3, n.y + 3, n.x + 19, n.y + 19, edgeColor(in.stack));
            int color = in.cycle ? KveGuiStyle.TEXT_MUTED : in.cutByDepth ? KveGuiStyle.TEXT_WARN : KveGuiStyle.TEXT_PRIMARY;
            g.drawString(font, in.nameText, n.x + 23, n.y + 7, color, false);
        }
    }

    private ItemStack drawNodeIcons(GuiGraphics g, GNode n, double wx, double wy, boolean mouseIn) {
        ItemStack under = ItemStack.EMPTY;
        if (n instanceof RecipeNode rn) {
            SafeItemRender.render(g, rn.recipe.station, n.x + 3, n.y + 2);
            int bx = n.x + 4, by = n.y + 22;
            for (int i = 0; i < rn.inputs.size(); i++) {
                RecipeIndex.Ing ing = rn.inputs.get(i);
                int ix = bx + (i % INPUT_COLS) * ICON, iy = by + (i / INPUT_COLS) * ICON;
                SafeItemRender.render(g, ing.shown, ix, iy);
                if (ing.shown.getCount() > 1) SafeItemRender.decorations(g, font, ing.shown, ix, iy);
                if (ing.tag != null) {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 300);
                    g.drawString(font, "#", ix, iy + 8, KveGuiStyle.ACCENT_GOLD, false);
                    g.pose().popPose();
                }
                if (mouseIn && wx >= ix && wx < ix + 16 && wy >= iy && wy < iy + 16) under = ing.shown;
            }
            int cols = Math.max(1, Math.min(INPUT_COLS, rn.inputs.size()));
            int rows = Math.max(1, (rn.inputs.size() + INPUT_COLS - 1) / INPUT_COLS);
            int ox = bx + cols * ICON + 13, oy = by + (rows * ICON) / 2 - 8;
            SafeItemRender.render(g, rn.result, ox, oy);
            if (rn.result.getCount() > 1) SafeItemRender.decorations(g, font, rn.result, ox, oy);
            if (mouseIn && wx >= ox && wx < ox + 16 && wy >= oy && wy < oy + 16) under = rn.result;
        } else if (n instanceof ItemNode in) {
            SafeItemRender.render(g, in.stack, n.x + 3, n.y + 3);
            if (mouseIn && wx >= n.x + 3 && wx < n.x + 19 && wy >= n.y + 3 && wy < n.y + 19) under = in.stack;
        }
        return under;
    }

    private static void outline(GuiGraphics g, GNode n, int c) {
        g.fill(n.x, n.y, n.x + n.w, n.y + 1, c);
        g.fill(n.x, n.y + n.h - 1, n.x + n.w, n.y + n.h, c);
        g.fill(n.x, n.y, n.x + 1, n.y + n.h, c);
        g.fill(n.x + n.w - 1, n.y, n.x + n.w, n.y + n.h, c);
    }

    private long lastNodeClick;
    private GNode lastNodeClicked;

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == GLFW.GLFW_MOUSE_BUTTON_4) { goBack(); return true; }
        if (btn == GLFW.GLFW_MOUSE_BUTTON_5) { goForward(); return true; }
        if (super.mouseClicked(mx, my, btn)) return true;
        if (quick.mouseClicked(this, mx, my, btn)) return true;
        if (!inCanvas(mx, my)) return false;
        double wx = worldX(mx), wy = worldY(my);
        GNode hit = null;
        for (GNode n : nodes) if (n.contains(wx, wy)) hit = n;
        if (hit != null) {
            if (btn == 1) {
                ItemStack s = hit instanceof RecipeNode rn ? rn.result : ((ItemNode) hit).stack;
                if (!s.isEmpty()) navigate(s, usesMode);
                return true;
            }
            long now = System.currentTimeMillis();
            boolean dbl = hit == lastNodeClicked && now - lastNodeClick < 350;
            lastNodeClick = now;
            lastNodeClicked = hit;
            select(hit);
            if (dbl && hit instanceof RecipeNode) quick.setOpen(true);
            buildWidgets();
            return true;
        }
        if (btn == 0 && selected != null) { select(null); buildWidgets(); }
        return canvas.handleEmptyClick(btn, mx, my);
    }

    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (canvas.handleDrag(mx, my)) return true;
        return super.mouseDragged(mx, my, b, dx, dy);
    }

    @Override public boolean mouseReleased(double mx, double my, int b) {
        canvas.endDrag();
        return super.mouseReleased(mx, my, b);
    }

    @Override public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (quick.mouseScrolled(this, mx, my, delta)) return true;
        if (inCanvas(mx, my)) { canvas.handleScrollZoom(cx, cy, mx, my, delta); return true; }
        return super.mouseScrolled(mx, my, scrollX, delta);
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        // un campo de la edicion rapida con foco se queda con las teclas (Backspace no vuelve atras)
        if (getFocused() instanceof net.minecraft.client.gui.components.EditBox box && box.isFocused()) {
            return box.keyPressed(key, scan, mods) || box.canConsumeInput();
        }
        boolean alt = (mods & GLFW.GLFW_MOD_ALT) != 0;
        if (key == GLFW.GLFW_KEY_BACKSPACE || (alt && key == GLFW.GLFW_KEY_LEFT)) { goBack(); return true; }
        if (alt && key == GLFW.GLFW_KEY_RIGHT) { goForward(); return true; }
        if (key == GLFW.GLFW_KEY_ENTER && selected instanceof RecipeNode) { openQuick(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
