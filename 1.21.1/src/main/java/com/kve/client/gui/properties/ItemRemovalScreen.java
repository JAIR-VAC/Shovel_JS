package com.kve.client.gui.properties;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.SearchFilter;
import com.kve.client.gui.tag.TagMembersScreen;

import org.lwjgl.glfw.GLFW;
import com.kve.common.Lang;
import com.kve.common.search.ItemUsageResolver.ItemUsage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class ItemRemovalScreen extends Screen {

    private static final int GUI_W = 260;
    private static final int GUI_H = 236;
    private static final int ROWS_VISIBLE = 11;
    private static final int ROW_H = 13;
    private static final int LIST_TOP = 32;
    private static final int X_W = 12;

    private record DisplayRow(String sectionHeader, ItemUsage usage) {
        static DisplayRow header(String label) { return new DisplayRow(label, null); }
        static DisplayRow of(ItemUsage u) { return new DisplayRow(null, u); }
        boolean isHeader() { return sectionHeader != null; }
    }

    private final Screen parent;
    private final String itemId;
    private final List<ItemUsage> allUsages;
    private final Consumer<Set<ItemUsage>> onConfirm;

    private final List<DisplayRow> filtered = new ArrayList<>();
    private final Set<ItemUsage> selected = new LinkedHashSet<>();

    private int L, T;
    private EditBox searchBox;
    private ScrollBar scrollBar;
    private String filter = "";
    private Button removeBtn;

    public ItemRemovalScreen(Screen parent, String itemId, List<ItemUsage> usages,
                              Consumer<Set<ItemUsage>> onConfirm) {
        super(Component.literal(Lang.get("gui.kve.item.removal.title", itemId)));
        this.parent = parent;
        this.itemId = itemId;
        this.allUsages = usages;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);
        refresh();

        searchBox = new KveScaledEditBox(font, L + 6, T + 16, GUI_W - 12, 12, Lang.c("gui.kve.tag.members.search_hint"));
        searchBox.setHint(Lang.c("gui.kve.tag.members.search_hint"));
        searchBox.setValue(filter);
        searchBox.setResponder(s -> { filter = s; refresh(); });
        addRenderableWidget(searchBox);

        if (scrollBar == null) {
            scrollBar = new ScrollBar(L + GUI_W - 10, T + LIST_TOP, ROWS_VISIBLE * ROW_H, filtered.size(), ROWS_VISIBLE);
        } else {
            scrollBar.setTotals(filtered.size(), ROWS_VISIBLE);
        }

        int by1 = T + GUI_H - 44;
        addRenderableWidget(new KvePlateButton(L + 6, by1, (GUI_W - 16) / 2, 13,
                Component.literal(Lang.get("gui.kve.item.removal.select_all")), b -> toggleSelectAllVisible()));
        addRenderableWidget(new KvePlateButton(L + 10 + (GUI_W - 16) / 2, by1, (GUI_W - 16) / 2, 13,
                Component.literal(Lang.get("gui.kve.btn.cancel")), b -> minecraft.setScreen(parent)));

        int by2 = T + GUI_H - 28;
        removeBtn = addRenderableWidget(new KvePlateButton(L + 6, by2, GUI_W - 12, 13,
                Component.literal(Lang.get("gui.kve.item.removal.remove_selected", selected.size())),
                b -> confirm()));
        removeBtn.active = !selected.isEmpty();
    }

    private void refresh() {
        filtered.clear();

        List<ItemUsage> recipes = new ArrayList<>();
        List<ItemUsage> tags = new ArrayList<>();
        List<ItemUsage> loot = new ArrayList<>();
        for (ItemUsage u : allUsages) {
            if (!SearchFilter.matches(u.label(), filter)) continue;
            switch (u.kind()) {
                case RECIPE_INPUT, RECIPE_OUTPUT -> recipes.add(u);
                case TAG_MEMBER -> tags.add(u);
                case LOOT_ENTRY -> loot.add(u);
            }
        }

        if (!recipes.isEmpty()) {
            filtered.add(DisplayRow.header(Lang.get("gui.kve.item.removal.section.recipes")));
            for (ItemUsage u : recipes) filtered.add(DisplayRow.of(u));
        }
        if (!tags.isEmpty()) {
            filtered.add(DisplayRow.header(Lang.get("gui.kve.item.removal.section.tags")));
            for (ItemUsage u : tags) filtered.add(DisplayRow.of(u));
        }
        if (!loot.isEmpty()) {
            filtered.add(DisplayRow.header(Lang.get("gui.kve.item.removal.section.loot")));
            for (ItemUsage u : loot) filtered.add(DisplayRow.of(u));
        }

        if (scrollBar != null) scrollBar.setTotals(filtered.size(), ROWS_VISIBLE);
    }

    private List<ItemUsage> visibleSelectableUsages() {
        List<ItemUsage> out = new ArrayList<>();
        for (DisplayRow r : filtered) if (!r.isHeader()) out.add(r.usage());
        return out;
    }

    private void toggleSelectAllVisible() {
        List<ItemUsage> visible = visibleSelectableUsages();
        boolean allSelected = !visible.isEmpty() && selected.containsAll(visible);
        if (allSelected) selected.removeAll(visible);
        else selected.addAll(visible);
        init();
    }

    private void toggleRow(ItemUsage u) {
        if (!selected.add(u)) selected.remove(u);
        init();
    }

    private void confirm() {
        if (selected.isEmpty()) return;
        Set<ItemUsage> chosen = new LinkedHashSet<>(selected);
        onConfirm.accept(chosen);
        minecraft.setScreen(parent);
    }

    /** ESC vuelve al caller sin tocar nada, mismo comportamiento que TagMembersScreen. */
    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H,
                Lang.get("gui.kve.item.removal.title", itemId) + " (" + allUsages.size() + ")");

        int scroll = scrollBar != null ? scrollBar.getScroll() : 0;
        int visible = Math.min(ROWS_VISIBLE, Math.max(0, filtered.size() - scroll));
        for (int i = 0; i < visible; i++) {
            DisplayRow row = filtered.get(i + scroll);
            int ry = T + LIST_TOP + i * ROW_H;
            if (row.isHeader()) {
                g.fill(L + 4, ry, L + GUI_W - 12, ry + ROW_H - 1, KveGuiStyle.BG_FIELD);
                KveGuiStyle.drawTextSafe(g, row.sectionHeader(), L + 6, ry + 2, GUI_W - 22, KveGuiStyle.th(0xFF88AACC));
                continue;
            }
            ItemUsage u = row.usage();
            boolean sel = selected.contains(u);
            g.fill(L + 4, ry, L + GUI_W - 12, ry + ROW_H - 1, sel ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
            String text = u.detail() == null || u.detail().isEmpty() ? u.label() : u.label() + " (" + u.detail() + ")";
            KveGuiStyle.drawCheck(g, L + 6, ry + 2, sel, KveGuiStyle.isHovered(mx, my, L + 4, ry, GUI_W - 16, ROW_H - 1));
            KveGuiStyle.drawTextSafe(g, text, L + 19, ry + 2, GUI_W - 45, sel ? 0xFFAADDAA : KveGuiStyle.TEXT_LABEL);
            int xRx = L + GUI_W - 12 - X_W;
            KveGuiStyle.drawTextSafe(g, "x", xRx + 3, ry + 2, X_W, sel ? 0xFFFF9955 : KveGuiStyle.th(0xFF667788));
        }
        if (filtered.isEmpty()) {
            List<String> emptyLines =
                    KveGuiStyle.wrapTextSafe(font, Lang.get("gui.kve.item.removal.empty"), GUI_W - 12);
            for (int i = 0; i < emptyLines.size(); i++) {
                KveGuiStyle.drawTextSafe(g, emptyLines.get(i), L + 6, T + LIST_TOP + 4 + i * 10, GUI_W - 12, KveGuiStyle.th(0xFF667788));
            }
        }
        if (scrollBar != null) scrollBar.render(g);

        if (removeBtn != null) {
            removeBtn.setMessage(Component.literal(Lang.get("gui.kve.item.removal.remove_selected", selected.size())));
            removeBtn.active = !selected.isEmpty();
        }

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar != null && scrollBar.mouseClicked(mx, my, btn)) return true;
        int scroll = scrollBar != null ? scrollBar.getScroll() : 0;
        int visible = Math.min(ROWS_VISIBLE, Math.max(0, filtered.size() - scroll));
        int xRx = L + GUI_W - 12 - X_W;
        for (int i = 0; i < visible; i++) {
            DisplayRow row = filtered.get(i + scroll);
            if (row.isHeader()) continue;
            int ry = T + LIST_TOP + i * ROW_H;
            if (mx >= xRx && mx < L + GUI_W - 12 && my >= ry && my < ry + ROW_H - 1) {
                toggleRow(row.usage());
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
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
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (scrollBar != null) { scrollBar.mouseScrolled(delta); return true; }
        return super.mouseScrolled(mx, my, scrollX, delta);
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        return true;
    }
    @Override public boolean isPauseScreen() { return false; }
}
