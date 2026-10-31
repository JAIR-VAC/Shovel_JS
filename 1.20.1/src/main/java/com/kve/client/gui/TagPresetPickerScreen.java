package com.kve.client.gui;

import org.lwjgl.glfw.GLFW;
import com.kve.client.gui.studio.KveStudioTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class TagPresetPickerScreen extends Screen implements KveStudioTab {

    private static final int GUI_W_DEFAULT = 200;
    private static final int GUI_H_DEFAULT = 170;
    private int GUI_W = GUI_W_DEFAULT;
    private int GUI_H = GUI_H_DEFAULT;
    private static final int ROWS_VISIBLE = 8;
    private static final int ROW_H = 14;

    private final Screen parent;
    private String title;
    private final List<String> presets;
    private final Consumer<String> callback;
    private final List<String> filtered = new ArrayList<>();
    private static final VirtualListWidget.Descriptor<String> LIST_DESCRIPTOR = new VirtualListWidget.Descriptor<>() {
        @Override public String id(String s) { return s; }
        @Override public String displayName(String s) { return null; }
    };
    private final VirtualListWidget<String> listIndex = new VirtualListWidget<>(LIST_DESCRIPTOR);

    private int L, T;
    private EditBox searchBox;
    private ScrollBar scrollBar;
    private String filter = "";
    private final List<Button> rowButtons = new ArrayList<>();

    private boolean hosted = false;
    private Runnable onDockClose;

    public TagPresetPickerScreen(Screen parent, List<String> presets, Consumer<String> callback) {
        this(parent, "Tags Comunes", presets, callback);
    }

    public TagPresetPickerScreen(Screen parent, String title, List<String> presets, Consumer<String> callback) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
        this.presets = new ArrayList<>(presets);
        this.callback = callback;
        listIndex.setItems(this.presets);
    }

    /** Fluent setter - ver {@link #onDockClose}. */
    public TagPresetPickerScreen withDockClose(Runnable r) {
        this.onDockClose = r;
        return this;
    }

    /**
     * mismo hueco que
     * {@code TagMembersScreen#refreshMembers} cerraba para Miembros -
     * reabrir esta pestaña con el dock ya cacheado para el mismo
     * {@code draftType} debe reflejar la lista de tags cargados actual
     * (puede cambiar si el jugador cambia de mundo/servidor entre aperturas), no quedarse para siempre con la primera resuelta.
     */
    public void refreshOptions(String title, List<String> presets) {
        this.title = title;
        this.presets.clear();
        this.presets.addAll(presets);
        listIndex.setItems(this.presets);
        refreshLayout();
    }

    private void close() {
        if (hosted && onDockClose != null) onDockClose.run();
        else minecraft.setScreen(parent);
    }

    @Override
    protected void init() {
        hosted = false;
        GUI_W = GUI_W_DEFAULT;
        GUI_H = GUI_H_DEFAULT;
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        buildContents();
    }

    /** Overload de {@link KveStudioTab} - mismo patron que {@code TagMembersScreen.init(int,int,int,int)}. */
    @Override
    public void init(int x, int y, int w, int h) {
        if (this.minecraft == null) {
            this.minecraft = Minecraft.getInstance();
            this.font = this.minecraft.font;
        }
        hosted = true;
        L = x;
        T = y;
        GUI_W = Math.max(1, w);
        GUI_H = Math.max(1, h);
        buildContents();
    }

    private void refreshLayout() {
        if (hosted) buildContents();
        else init();
    }

    private void buildContents() {
        clearWidgets();
        setFocused(null);
        rowButtons.clear();
        refreshFiltered();

        searchBox = new KveScaledEditBox(font, L + 6, T + 16, GUI_W - 12, 12, Component.literal("Buscar..."));
        searchBox.setValue(filter);
        searchBox.setResponder(s -> { filter = s; refreshFiltered(); rebuildRows(); });
        addRenderableWidget(searchBox);

        if (scrollBar == null) {
            scrollBar = new ScrollBar(L + GUI_W - 10, T + 32, ROWS_VISIBLE * ROW_H, filtered.size(), ROWS_VISIBLE);
        } else {
            scrollBar.setPosition(L + GUI_W - 10, T + 32);
            scrollBar.setTotals(filtered.size(), ROWS_VISIBLE);
        }

        rebuildRows();

        addRenderableWidget(new KvePlateButton(L + 6, T + GUI_H - 16, GUI_W - 12, 12, Component.literal("Cancelar"), b -> close()));
    }

    private void refreshFiltered() {
        listIndex.filter(filter, null);
        filtered.clear();
        filtered.addAll(listIndex.filtered());
        if (scrollBar != null) scrollBar.setTotals(filtered.size(), ROWS_VISIBLE);
    }

    private void rebuildRows() {
        for (Button b : rowButtons) removeWidget(b);
        rowButtons.clear();
        if (scrollBar == null) return;
        int scroll = scrollBar.getScroll();
        int visible = Math.min(ROWS_VISIBLE, Math.max(0, filtered.size() - scroll));
        for (int i = 0; i < visible; i++) {
            String id = filtered.get(i + scroll);
            Button rowBtn = new KvePlateButton(L + 6, T + 32 + i * ROW_H, GUI_W - 20, 12, Component.literal(id), b -> {
                        callback.accept(id);
                        close();
                    });
            rowButtons.add(rowBtn);
            addRenderableWidget(rowBtn);
        }
    }

    /** ESC returns to the caller instead of closing the whole GUI. */
    @Override
    public void onClose() { close(); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!hosted) {
            renderBackground(g);
            KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, title);
        }
        if (filtered.isEmpty()) KveGuiStyle.drawTextSafe(g, "(sin resultados)", L + 6, T + 34, GUI_W - 12, KveGuiStyle.TEXT_MUTED);
        if (scrollBar != null) scrollBar.render(g);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar != null && scrollBar.mouseClicked(mx, my, btn)) return true;
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
    public boolean mouseScrolled(double mx, double my, double delta) {
        int listTop = T + 32, listBot = listTop + ROWS_VISIBLE * ROW_H;
        if (scrollBar != null && mx >= L && mx < L + GUI_W && my >= listTop && my < listBot) {
            scrollBar.mouseScrolled(delta);
            rebuildRows();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        return true;
    }
    @Override public boolean isPauseScreen() { return false; }
}