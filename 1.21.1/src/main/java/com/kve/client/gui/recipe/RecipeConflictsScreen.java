package com.kve.client.gui.recipe;

import com.kve.client.gui.KveConfirmScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.ScrollBar;

import com.kve.common.Lang;
import com.kve.client.permission.ClientPermissionState;
import com.kve.client.recipe.RecipeConflictFinder;
import com.kve.client.recipe.RecipeRemovalQueue;
import com.kve.common.kubejs.KubeJSExporter;
import com.kve.integration.viewer.RecipeEditorBridge;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Lista de recetas de mesa de crafteo en conflicto -
 * ver
 * {@link RecipeConflictFinder} para como se detectan. Cada grupo muestra la
 * grilla que comparten y cada receta con su resultado, con "Abrir" (la
 * abre en el editor de recetas) y "Quitar" (la encola para quitar, igual
 * que desde el navegador de recetas). Filtro por mod para packs grandes.
 */
@OnlyIn(Dist.CLIENT)
public class RecipeConflictsScreen extends Screen {

    private static final int W = 420;
    private static final int H = 250;
    private static final int ROW_H = 18;
    private static final int BTN_W = 44;

    private final Screen parent;
    private List<RecipeConflictFinder.Group> groups = null;
    private final List<String> namespaces = new ArrayList<>();
    private int nsIdx = -1;
    private final List<Object> rows = new ArrayList<>();
    private ScrollBar scrollBar;
    private RegistryAccess registryAccess;
    private int cx, cy;
    private String flash = "";

    public RecipeConflictsScreen(Screen parent) {
        super(Component.literal(Lang.get("gui.kve.recipe.conflicts.title")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        cx = (width - W) / 2;
        cy = (height - H) / 2;
        if (groups == null) compute();

        String nsLabel = Lang.get("gui.kve.recipe.conflicts.scope",
                nsIdx < 0 ? Lang.get("gui.kve.recipe.conflicts.all_mods") : namespaces.get(nsIdx));
        addRenderableWidget(new KvePlateButton(cx + KveGuiStyle.PAD, cy + KveGuiStyle.TITLEBAR_H + 4, 160, 14,
                Component.literal(KveGuiStyle.truncate(font, nsLabel, 154)), b -> {
                    nsIdx = nsIdx + 1 >= namespaces.size() ? -1 : nsIdx + 1;
                    rebuild();
                }));
        addRenderableWidget(new KvePlateButton(cx + W - KveGuiStyle.PAD - 70, cy + H - KveGuiStyle.PAD - 16, 70, 16,
                Component.literal(Lang.get("gui.kve.btn.back")), b -> onClose()));
        buildRows();
        scrollBar = new ScrollBar(cx + W - 10, listTop(), visibleRows() * ROW_H, rows.size(), visibleRows());
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private int listTop() { return cy + KveGuiStyle.TITLEBAR_H + 24; }
    private int visibleRows() { return (H - KveGuiStyle.TITLEBAR_H - 24 - KveGuiStyle.PAD - 22) / ROW_H; }

    private void compute() {
        groups = new ArrayList<>();
        if (minecraft == null || minecraft.level == null || minecraft.getConnection() == null) return;
        RecipeManager mgr = minecraft.getConnection().getRecipeManager();
        registryAccess = minecraft.getConnection().registryAccess();
        groups = RecipeConflictFinder.findConflicts(mgr, minecraft.level, registryAccess);
        TreeSet<String> ns = new TreeSet<>();
        for (var g : groups) for (var r : g.recipes()) ns.add(r.id().getNamespace());
        namespaces.clear();
        namespaces.addAll(ns);
    }

    private void buildRows() {
        rows.clear();
        String ns = nsIdx < 0 ? null : namespaces.get(nsIdx);
        for (var g : groups) {
            if (ns != null && g.recipes().stream().noneMatch(r -> r.id().getNamespace().equals(ns))) continue;
            rows.add(g);
            rows.addAll(g.recipes());
        }
    }

    private int buttonX(int which) {
        return cx + W - 14 - (2 - which) * (BTN_W + 3);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar != null && scrollBar.mouseClicked(mx, my, btn)) return true;
        int top = listTop();
        if (btn == 0 && my >= top && my < top + visibleRows() * ROW_H) {
            int idx = scrollBar.getScroll() + (int) ((my - top) / ROW_H);
            if (idx >= 0 && idx < rows.size() && rows.get(idx) instanceof net.minecraft.world.item.crafting.RecipeHolder<?> rawR && rawR.value() instanceof CraftingRecipe) {
                net.minecraft.world.item.crafting.RecipeHolder<CraftingRecipe> r = asCrafting(rawR);
                int ry = top + (idx - scrollBar.getScroll()) * ROW_H;
                if (my >= ry + 2 && my < ry + 16) {
                    if (mx >= buttonX(0) && mx < buttonX(0) + BTN_W) {
                        RecipeEditorBridge.openRecipeInEditor(r.id(), this);
                        return true;
                    }
                    if (mx >= buttonX(1) && mx < buttonX(1) + BTN_W) {
                        removeRecipe(r);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @SuppressWarnings("unchecked")
    private static net.minecraft.world.item.crafting.RecipeHolder<CraftingRecipe> asCrafting(net.minecraft.world.item.crafting.RecipeHolder<?> h) {
        return (net.minecraft.world.item.crafting.RecipeHolder<CraftingRecipe>) h;
    }

    private void removeRecipe(net.minecraft.world.item.crafting.RecipeHolder<CraftingRecipe> r) {
        if (!ClientPermissionState.canEdit()) { flash = Lang.get("gui.kve.perm.denied"); return; }
        String id = r.id().toString();
        if (KubeJSExporter.getRemovedRecipes().contains(id)) return;
        minecraft.setScreen(new KveConfirmScreen(
                confirmed -> {
                    minecraft.setScreen(this);
                    if (confirmed) {
                        RecipeRemovalQueue.queueOne(id);
                        flash = Lang.get("gui.kve.recipe.conflicts.queued", id);
                    }
                },
                Component.literal(Lang.get("gui.kve.recipe.remove_confirm_title")),
                Component.literal(Lang.get("gui.kve.recipe.remove_confirm_msg", id))));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        return scrollBar != null && scrollBar.mouseScrolled(delta);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (scrollBar != null && scrollBar.mouseDragged(mx, my, btn, dx, dy)) return true;
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (scrollBar != null) scrollBar.mouseReleased(mx, my, btn);
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, H, getTitle().getString());
        int x = cx + KveGuiStyle.PAD;
        long shown = rows.stream().filter(o -> o instanceof RecipeConflictFinder.Group).count();
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.conflicts.summary", shown),
                x + 168, cy + KveGuiStyle.TITLEBAR_H + 7, W - 190, KveGuiStyle.TEXT_LABEL);

        int top = listTop();
        int rowsVis = visibleRows();
        g.fill(x - 2, top - 2, cx + W - 12, top + rowsVis * ROW_H + 1, KveGuiStyle.BG_FIELD);
        if (rows.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get(groups == null || registryAccess == null
                    ? "gui.kve.recipe.conflicts.no_world" : "gui.kve.recipe.conflicts.none"), x + 4, top + 4, W - 30, KveGuiStyle.TEXT_OK);
        }
        int scroll = scrollBar.getScroll();
        int groupNo = 0;
        for (int i = 0; i < scroll && i < rows.size(); i++) if (rows.get(i) instanceof RecipeConflictFinder.Group) groupNo++;
        for (int r = 0; r < rowsVis; r++) {
            int idx = scroll + r;
            if (idx >= rows.size()) break;
            int ry = top + r * ROW_H;
            Object o = rows.get(idx);
            if (o instanceof RecipeConflictFinder.Group grp) {
                groupNo++;
                g.fill(x - 2, ry, cx + W - 12, ry + ROW_H - 1, 0x33FFAA55);
                KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.conflicts.group", groupNo, grp.recipes().size()),
                        x, ry + 5, 150, KveGuiStyle.TEXT_WARN);
                int ix = x + 150;
                for (ItemStack s : grp.sampleGrid()) {
                    if (s.isEmpty()) continue;
                    g.pose().pushPose();
                    g.pose().translate(ix, ry + 2, 0);
                    g.pose().scale(0.8f, 0.8f, 1f);
                    try { g.renderItem(s, 0, 0); } catch (Throwable ignored) {}
                    g.pose().popPose();
                    ix += 14;
                }
            } else if (o instanceof net.minecraft.world.item.crafting.RecipeHolder<?> rec) {
                ItemStack out = ItemStack.EMPTY;
                try { out = rec.value().getResultItem(registryAccess); } catch (Throwable ignored) {}
                try { g.renderItem(out, x + 4, ry + 1); g.renderItemDecorations(font, out, x + 4, ry + 1); } catch (Throwable ignored) {}
                boolean removed = KubeJSExporter.getRemovedRecipes().contains(rec.id().toString());
                String name = out.isEmpty() ? "?" : out.getHoverName().getString();
                int textW = buttonX(0) - (x + 24) - 4;
                KveGuiStyle.drawTextSafe(g, name, x + 24, ry + 1, textW, removed ? KveGuiStyle.TEXT_MUTED : KveGuiStyle.TEXT_PRIMARY);
                KveGuiStyle.drawTextSafe(g, rec.id().toString() + (removed ? "  " + Lang.get("gui.kve.recipe.conflicts.marked") : ""),
                        x + 24, ry + 10, textW, KveGuiStyle.TEXT_MUTED);
                drawRowButton(g, buttonX(0), ry + 2, Lang.get("gui.kve.recipe.conflicts.open"), mx, my, true);
                drawRowButton(g, buttonX(1), ry + 2, Lang.get("gui.kve.recipe.conflicts.remove"), mx, my, !removed);
            }
        }
        scrollBar.render(g);
        if (!flash.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, flash, x, cy + H - KveGuiStyle.PAD - 12, W - 100, KveGuiStyle.ACCENT_GOLD);
        }
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    private void drawRowButton(GuiGraphics g, int bx, int by, String label, int mx, int my, boolean enabled) {
        boolean hover = enabled && mx >= bx && mx < bx + BTN_W && my >= by && my < by + 14;
        g.fill(bx, by, bx + BTN_W, by + 14, hover ? 0xFF3D5A80 : (enabled ? 0xFF24305A : 0xFF1A1F2E));
        KveGuiStyle.drawTextSafe(g, label, bx + 3, by + 3, BTN_W - 6, enabled ? KveGuiStyle.TEXT_PRIMARY : KveGuiStyle.TEXT_MUTED);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
