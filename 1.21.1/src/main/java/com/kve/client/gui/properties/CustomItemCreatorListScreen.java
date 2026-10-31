package com.kve.client.gui.properties;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.SearchFilter;
import com.kve.client.gui.worldgen.WorldGenEditorScreen;
import com.kve.client.gui.worldgen.WorldGenListScreen;

import com.kve.common.Lang;
import com.kve.client.gui.studio.KveStudioTab;
import com.kve.common.customitem.CustomItemDraft;
import com.kve.common.customitem.CustomItemState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class CustomItemCreatorListScreen extends Screen implements KveStudioTab {

    private static final int GUI_W = 260;
    private static final int ROWS_VISIBLE = 8;
    private static final int ROW_H = 24;
    private static final int GUI_H = 34 + ROWS_VISIBLE * ROW_H + 46;

    private final Runnable onBack;
    private final Consumer<CustomItemDraft> onOpenEditor;

    private int L, T;
    private boolean hosted = false;
    private int hostedX, hostedY, hostedW, hostedH;
    private final List<CustomItemDraft> savedList = new ArrayList<>();
    private ScrollBar scrollBar;
    private EditBox searchBox;
    private String searchFilter = "";

    private String flashMsg = "";
    private long flashAt = 0;

    public CustomItemCreatorListScreen(Runnable onBack, Consumer<CustomItemDraft> onOpenEditor) {
        super(Lang.c("gui.kve.itemcreator.title"));
        this.onBack = onBack;
        this.onOpenEditor = onOpenEditor;
    }

    @Override
    protected void init() {
        hosted = false;
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        buildWidgets();
    }

    @Override
    public void init(int x0, int y0, int w, int h) {
        if (this.minecraft == null) {
            this.minecraft = Minecraft.getInstance();
            this.font = this.minecraft.font;
        }
        hosted = true;
        hostedX = x0; hostedY = y0; hostedW = w; hostedH = h;
        L = x0 + Math.max(0, (w - GUI_W) / 2);
        T = y0 + Math.max(0, (h - GUI_H) / 2);
        buildWidgets();
    }

    private void refresh() {
        buildWidgets();
    }

    private void buildWidgets() {
        clearWidgets();
        setFocused(null);
        CustomItemState.loadIfNeeded();
        refreshSaved();

        searchBox = new KveScaledEditBox(font, L + 6, T + 18, GUI_W - 12, 12, Lang.c("gui.kve.search.item"));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchFilter);
        searchBox.setResponder(s -> { searchFilter = s; refreshSaved(); });
        addRenderableWidget(searchBox);

        int listY = T + 34;
        scrollBar = new ScrollBar(L + GUI_W - 14, listY, ROWS_VISIBLE * ROW_H, 0, ROWS_VISIBLE);
        updateScroll();

        int newBtnY = listY + ROWS_VISIBLE * ROW_H + 6;
        addRenderableWidget(btn(Lang.get("gui.kve.itemcreator.new"), L + 6, newBtnY, 0,
                () -> onOpenEditor.accept(null)));

    }

    /** Llamado por {@code ItemAttributeScreen} cada vez que vuelve a mostrar esta lista (p. ej. al volver del editor) - refresca contra {@link CustomItemState} por si el editor guardo/borro/exporto un borrador. */
    public void refreshOnReturn() {
        if (minecraft == null) return;
        refresh();
    }

    private void refreshSaved() {
        savedList.clear();
        for (CustomItemDraft d : CustomItemState.getDrafts()) {
            if (!SearchFilter.matches(d.fullId(), d.displayName, searchFilter)) continue;
            savedList.add(d);
        }
        updateScroll();
    }

    private void updateScroll() {
        if (scrollBar != null) scrollBar.setTotals(savedList.size(), ROWS_VISIBLE);
    }

    private void flash(String msg) { flashMsg = msg; flashAt = System.currentTimeMillis(); }

    private void goBack() { onBack.run(); }

    @Override
    public void onClose() { goBack(); }

    private Button btn(String label, int x, int y, int w, Runnable a) {
        return KveGuiStyle.smartBtn(x, y, w, label, b -> a.run());
    }

    private String badgeFor(CustomItemDraft d) {
        if (!d.exported) return Lang.get("gui.kve.itemcreator.badge.draft");
        try {
            ResourceLocation rl = ResourceLocation.parse(d.fullId());
            if (BuiltInRegistries.ITEM.containsKey(rl)) return Lang.get("gui.kve.itemcreator.badge.active");
        } catch (Exception ignored) {}
        return Lang.get("gui.kve.itemcreator.badge.exported");
    }

    private int badgeColor(CustomItemDraft d) {
        if (!d.exported) return KveGuiStyle.TEXT_MUTED;
        try {
            ResourceLocation rl = ResourceLocation.parse(d.fullId());
            if (BuiltInRegistries.ITEM.containsKey(rl)) return KveGuiStyle.TEXT_OK;
        } catch (Exception ignored) {}
        return KveGuiStyle.TEXT_WARN;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!hosted) renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.itemcreator.title"));

        int listY = T + 34;
        int scroll = scrollBar.getScroll();
        for (int i = 0; i < Math.min(ROWS_VISIBLE, savedList.size() - scroll); i++) {
            CustomItemDraft d = savedList.get(i + scroll);
            int ry = listY + i * ROW_H;
            boolean hov = KveGuiStyle.isHovered(mx, my, L + 4, ry, GUI_W - 20, ROW_H - 2);
            g.fill(L + 4, ry, L + GUI_W - 20, ry + ROW_H - 2, hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
            String title = d.displayName.isEmpty() ? d.fullId() : d.displayName;
            KveGuiStyle.drawTextSafe(g, title, L + 7, ry + 3, GUI_W - 30, KveGuiStyle.TEXT_PRIMARY);
            KveGuiStyle.drawTextSafe(g, d.fullId(), L + 7, ry + 13, GUI_W - 30, KveGuiStyle.TEXT_MUTED);
            KveGuiStyle.drawTextSafe(g, badgeFor(d), L + GUI_W - 90, ry + 8, 78, badgeColor(d));
        }
        scrollBar.render(g);
        if (savedList.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.itemcreator.none"), L + 7, listY + 4, GUI_W - 14, KveGuiStyle.TEXT_MUTED);
        }

        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4500) {
            KveGuiStyle.drawTextSafe(g, flashMsg, L + 6, T + GUI_H - 30, GUI_W - 12, KveGuiStyle.ACCENT_GOLD);
        }

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar.mouseClicked(mx, my, btn)) return true;
        int listY = T + 34;
        int scroll = scrollBar.getScroll();
        for (int i = 0; i < Math.min(ROWS_VISIBLE, savedList.size() - scroll); i++) {
            int ry = listY + i * ROW_H;
            if (mx >= L + 4 && mx < L + GUI_W - 20 && my >= ry && my < ry + ROW_H - 2) {
                onOpenEditor.accept(savedList.get(i + scroll));
                return true;
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
    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        scrollBar.mouseScrolled(delta);
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        return true;
    }
    @Override public boolean isPauseScreen() { return false; }
}
