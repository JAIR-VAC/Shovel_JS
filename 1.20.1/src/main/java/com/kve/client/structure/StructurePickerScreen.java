package com.kve.client.structure;

import com.kve.common.structure.StructureVoxelGrid;

import com.kve.common.Lang;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.ResizeHandle;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.VirtualListWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.KvePlateButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Popup for picking a structure id, used by {@link StructureEditorScreen#doBrowse()}.
 * <p>
 * Unlike the generic {@code TagPresetPickerScreen} it replaces for this one
 * use (that screen is still used as-is for tag presets, which have no
 * meaningful visual), this picker renders a live 3D "tooltip render" of
 * whichever id is currently hovered/selected in the list, next to it - so
 * the player can see what a structure actually looks like before
 * committing to load it. The window is widened (vs the plain list popup)
 * specifically to make room for that render panel.
 * <p>
 * Loading is lazy and cached per id via {@link StructurePreviewCache}: only
 * the row currently under the mouse (or last clicked) is ever resolved off
 * disk, once, not the whole list up front.
 * <p>
 * {@link VirtualListWidget} (orden:
 * Recipe -&gt; Item -&gt; Mob -&gt; Enchantment -&gt; Effect -&gt; Fluid -&gt;
 * Biome -&gt; Tag preset -&gt; Structure). {@code ids} son plain ids
 * (namespace:path de estructura), sin displayName() separado - mismo shape que
 * Fluid/Biome/TagPreset, sin TagMatcher (las estructuras no tienen sistema de
 * tags navegable aca).
 */
@OnlyIn(Dist.CLIENT)
public class StructurePickerScreen extends Screen {

    private static final int GUI_W = 360;
    private static final int GUI_H = 210;
    private static final int ROWS_VISIBLE = 11;
    private static final int ROW_H = 14;

    // 1.4: mismo patron "columna vecina se
    // achica" que StructureChangeHistoryScreen - GUI_W queda fijo, pvW ya se calculaba como
    // "GUI_W - LIST_W - 16" asi que el preview 3D se banca perfecto un
    // ancho variable sin tocar nada mas.
    private static final int LIST_W_DEFAULT = 150;
    private static final int LIST_W_MIN     = 110;
    private static final int LIST_W_MAX     = 220;

    private final Screen parent;
    private final List<String> ids;
    private final Consumer<String> callback;
    private final List<String> filtered = new ArrayList<>();
    private final StructurePreviewCache previewCache = new StructurePreviewCache();
    private static final VirtualListWidget.Descriptor<String> LIST_DESCRIPTOR = new VirtualListWidget.Descriptor<>() {
        @Override public String id(String s) { return s; }
        @Override public String displayName(String s) { return null; }
    };
    private final VirtualListWidget<String> listIndex = new VirtualListWidget<>(LIST_DESCRIPTOR);

    private int L, T, LIST_W;
    private EditBox searchBox;
    private ScrollBar scrollBar;
    private ResizeHandle listResize;
    private String filter = "";

    private String previewId = null;

    private double smoothScroll = 0;

    private int scrollBaseRow() { return (int) Math.floor(smoothScroll); }

    private int scrollPixelShift() { return (int) Math.round((smoothScroll - scrollBaseRow()) * ROW_H); }

    private int pvX, pvY, pvW, pvH;
    private OrbitCamera previewCamera;

    public StructurePickerScreen(Screen parent, List<String> ids, Consumer<String> callback) {
        super(Lang.c("gui.kve.structure.picker.title"));
        this.parent = parent;
        this.ids = ids;
        this.callback = callback;
        listIndex.setItems(ids);
    }

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);
        refresh();

        if (previewCamera == null) {
            previewCamera = new OrbitCamera(10f, 2f, 60f, 60f);
        }
        if (listResize == null) {
            listResize = new ResizeHandle("structure_picker.list", LIST_W_MIN, LIST_W_MAX, LIST_W_DEFAULT);
        }
        LIST_W = listResize.width();
        listResize.reposition(L, T + 15, T + GUI_H);

        searchBox = new KveScaledEditBox(font, L + 6, T + 16, LIST_W - 12, 12, Lang.c("gui.kve.structure.picker.search_hint"));
        searchBox.setValue(filter);
        searchBox.setResponder(s -> { filter = s; refresh(); });
        addRenderableWidget(searchBox);
        setFocused(searchBox);

        if (scrollBar == null) {
            scrollBar = new ScrollBar(L + LIST_W - 8, T + 32, ROWS_VISIBLE * ROW_H, filtered.size(), ROWS_VISIBLE);
        } else {
            scrollBar.setTotals(filtered.size(), ROWS_VISIBLE);
        }
        scrollBar.setPosition(L + LIST_W - 8, T + 32);

        addRenderableWidget(new KvePlateButton(L + 6, T + GUI_H - 16, LIST_W - 12, 12, Lang.c("gui.kve.btn.cancel"), b -> minecraft.setScreen(parent)));

        pvX = L + LIST_W + 8;
        pvY = T + 16;
        pvW = GUI_W - LIST_W - 16;
        pvH = GUI_H - 32;

        if (previewId == null && !filtered.isEmpty()) previewId = filtered.get(0);
    }

    private void refresh() {
        listIndex.filter(filter, null);
        filtered.clear();
        filtered.addAll(listIndex.filtered());
        if (scrollBar != null) scrollBar.setTotals(filtered.size(), ROWS_VISIBLE);
        if (previewId != null && !filtered.contains(previewId)) {
            previewId = filtered.isEmpty() ? null : filtered.get(0);
        }
    }

    /** ESC returns to the caller instead of closing the whole GUI. */
    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.structure.picker.title"));

        int scrollTarget = scrollBar.getScroll();
        if (Math.abs(smoothScroll - scrollTarget) < 0.02) smoothScroll = scrollTarget;
        else smoothScroll += (scrollTarget - smoothScroll) * 0.35;

        int baseRow = scrollBaseRow();
        int pixelShift = scrollPixelShift();
        int listTop = T + 32;
        int listBottom = listTop + ROWS_VISIBLE * ROW_H;

        String hoveredFullId = null;
        int idMaxW = LIST_W - 26;
        g.enableScissor(L + 4, listTop, L + LIST_W - 12, listBottom);
        for (int i = -1; i <= ROWS_VISIBLE; i++) {
            int idx = i + baseRow;
            if (idx < 0 || idx >= filtered.size()) continue;
            int ry = listTop + i * ROW_H - pixelShift;
            if (ry + ROW_H <= listTop || ry >= listBottom) continue;
            String id = filtered.get(idx);
            boolean hoveredRow = KveGuiStyle.isHovered(mx, my, L + 6, ry, LIST_W - 20, ROW_H - 2)
                    && my >= listTop && my < listBottom;
            boolean sel = id.equals(previewId);
            g.fill(L + 6, ry, L + LIST_W - 14, ry + ROW_H - 2, sel ? com.kve.client.gui.KveGuiStyle.th(0xFF1A3050) : (hoveredRow ? com.kve.client.gui.KveGuiStyle.th(0xFF14243A) : com.kve.client.gui.KveGuiStyle.th(0xFF0A1220)));
            KveGuiStyle.drawTextSafe(g, id, L + 8, ry + 3, idMaxW, sel ? KveGuiStyle.TEXT_PRIMARY : KveGuiStyle.TEXT_LABEL);
            if (hoveredRow) {
                previewId = id;
                if (font.width(id) > idMaxW) hoveredFullId = id;
            }
        }
        g.disableScissor();
        if (filtered.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.structure.picker.no_results"), L + 8, T + 36, GUI_W - 16, KveGuiStyle.TEXT_MUTED);
        }
        scrollBar.render(g);

        renderPreviewPanel(g);
        listResize.render(g, mx, my);

        super.render(g, mx, my, pt);

        if (previewId != null) {
            StructureVoxelGrid previewGrid = previewCache.get(previewId);
            if (previewGrid != null) {
                previewCamera.setTarget(centerOf(previewGrid));
                previewCamera.orbit(0.35, 0);
                StructureSceneRenderer.render(pvX, pvY, pvW, pvH, previewGrid, previewCamera, null, false);
            }
        }

        if (hoveredFullId != null) {
            KveGuiStyle.drawFramedTooltip(g, hoveredFullId, mx, my, width, height);
        }
    }

    private void renderPreviewPanel(GuiGraphics g) {
        g.fill(pvX, pvY, pvX + pvW, pvY + pvH, KveGuiStyle.BG_FIELD);
        g.hLine(pvX, pvX + pvW - 1, pvY, KveGuiStyle.BORDER_FIELD);
        g.hLine(pvX, pvX + pvW - 1, pvY + pvH - 1, KveGuiStyle.BORDER_FIELD);
        g.vLine(pvX, pvY, pvY + pvH - 1, KveGuiStyle.BORDER_FIELD);
        g.vLine(pvX + pvW - 1, pvY, pvY + pvH - 1, KveGuiStyle.BORDER_FIELD);

        if (previewId == null) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.structure.picker.hover_hint"),
                    pvX + 6, pvY + pvH / 2 - 4, pvW - 12, KveGuiStyle.TEXT_MUTED);
            return;
        }
        if (!previewCache.hasAttempted(previewId)) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.structure.picker.loading"),
                    pvX + 6, pvY + pvH / 2 - 4, pvW - 12, KveGuiStyle.TEXT_MUTED);
        } else if (previewCache.get(previewId) == null) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.structure.picker.no_preview"),
                    pvX + 6, pvY + pvH / 2 - 4, pvW - 12, KveGuiStyle.TEXT_MUTED);
        }
        KveGuiStyle.drawTextSafe(g, previewId, pvX + 4, pvY + pvH - 10, pvW - 8, KveGuiStyle.TEXT_LABEL);
    }

    private static org.joml.Vector3f centerOf(StructureVoxelGrid grid) {
        int[] size = grid.computeSize();
        if (size == null) return new org.joml.Vector3f(0, 0, 0);
        return new org.joml.Vector3f(size[0] / 2f, size[1] / 2f, size[2] / 2f);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (listResize.mouseClicked(mx, my)) return true;
        if (scrollBar != null && scrollBar.mouseClicked(mx, my, btn)) return true;
        int baseRow = scrollBaseRow();
        int pixelShift = scrollPixelShift();
        int listTop = T + 32;
        int listBottom = listTop + ROWS_VISIBLE * ROW_H;
        for (int i = -1; i <= ROWS_VISIBLE; i++) {
            int idx = i + baseRow;
            if (idx < 0 || idx >= filtered.size()) continue;
            int ry = listTop + i * ROW_H - pixelShift;
            if (ry + ROW_H <= listTop || ry >= listBottom) continue;
            if (mx >= L + 6 && mx < L + LIST_W - 14 && my >= ry && my < ry + ROW_H - 2) {
                String id = filtered.get(idx);
                callback.accept(id);
                minecraft.setScreen(parent);
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (listResize.mouseDragged(mx)) { init(); return true; }
        if (listResize.isDragging()) return true;
        if (scrollBar != null && scrollBar.mouseDragged(mx, my, btn, dx, dy)) return true;
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        listResize.mouseReleased();
        if (scrollBar != null) scrollBar.mouseReleased(mx, my, btn);
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (previewId != null && KveGuiStyle.isHovered((int) mx, (int) my, pvX, pvY, pvW, pvH)) {
            previewCamera.zoom(-delta);
            return true;
        }
        if (scrollBar != null) { scrollBar.scrollBy((int) -Math.signum(delta)); return true; }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        return true;
    }
    @Override public boolean isPauseScreen() { return false; }
}