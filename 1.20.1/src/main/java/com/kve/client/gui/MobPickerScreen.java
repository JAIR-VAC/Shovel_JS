package com.kve.client.gui;

import org.lwjgl.glfw.GLFW;
import com.kve.common.Lang;
import com.kve.client.gui.studio.KveStudioTab;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class MobPickerScreen extends Screen implements KveStudioTab {

    private static final int GRID_W       = 290;
    private static final int PREVIEW_W    = 92;
    private static final int GUI_W_DEFAULT = GRID_W + PREVIEW_W;
    private static final int GUI_H_DEFAULT = 240;
    private int GUI_W = GUI_W_DEFAULT;
    private int GUI_H = GUI_H_DEFAULT;
    private static final int COLS         = 2;
    private static final int CELL_W       = 132;
    private static final int CELL_H       = 18;
    private static final int VISIBLE_ROWS = 10;
    private static final int GRID_X       = 4;
    private static final int GRID_Y       = 31;

    private final Screen parent;
    private final Consumer<EntityType<?>> callback;

    private static final VirtualListWidget.Descriptor<EntityType<?>> LIST_DESCRIPTOR = new VirtualListWidget.Descriptor<>() {
        @Override public String id(EntityType<?> et) { return RegistryIndex.id(et); }
        @Override public String displayName(EntityType<?> et) { return RegistryIndex.name(et); }
    };

    private static final VirtualListWidget.TagMatcher<EntityType<?>> LIST_TAG_MATCHER = (et, tagToken) -> {
        try {
            ResourceLocation tagId = new ResourceLocation(tagToken.contains(":") ? tagToken : "minecraft:" + tagToken);
            var tagKey = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, tagId);
            return et.is(tagKey);
        } catch (Exception ignored) { return false; }
    };

    private EditBox searchBox;
    private List<EntityType<?>> allMobs = null;
    private final List<EntityType<?>> filtered = new ArrayList<>();
    private final VirtualListWidget<EntityType<?>> listIndex = new VirtualListWidget<>(LIST_DESCRIPTOR);
    private ScrollBar scrollBar;
    private final ListKeyNav nav = new ListKeyNav();
    private int guiLeft, guiTop;

    private final Map<EntityType<?>, LivingEntity> previewCache = new HashMap<>();
    private EntityType<?> hoveredType = null;

    private boolean hosted = false;
    private Runnable onDockClose;

    public MobPickerScreen(Screen parent, Consumer<EntityType<?>> callback) {
        super(Lang.c("gui.kve.pick.mob"));
        this.parent   = parent;
        this.callback = callback;
        listIndex.setTagMatcher(LIST_TAG_MATCHER);
    }

    /** Fluent setter - ver {@link #onDockClose}. */
    public MobPickerScreen withDockClose(Runnable r) {
        this.onDockClose = r;
        return this;
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
        guiLeft = (width  - GUI_W) / 2;
        guiTop  = (height - GUI_H) / 2;
        buildContents();
    }

    /** §9: overload de {@link KveStudioTab}, mismo criterio que {@code ItemPickerScreen#init(int,int,int,int)} (tamaño fijo, solo se reposiciona). */
    @Override
    public void init(int x, int y, int w, int h) {
        if (this.minecraft == null) {
            this.minecraft = Minecraft.getInstance();
            this.font = this.minecraft.font;
        }
        hosted = true;
        GUI_W = w;
        GUI_H = h;
        guiLeft = x;
        guiTop = y;
        buildContents();
    }

    private void buildContents() {

        if (allMobs == null) {
            allMobs = RegistryIndex.editableMobTypes();
            listIndex.setItems(allMobs);
        }

        searchBox = new KveScaledEditBox(font,
                guiLeft + GRID_X, guiTop + 16,
                COLS * CELL_W + 2, 12,
                Component.literal(Lang.get("gui.kve.search.mob")));
        searchBox.setMaxLength(48);
        searchBox.setHint(Component.literal(Lang.get("gui.kve.search.list_hint")));
        searchBox.setResponder(s -> { rebuildFilter(s); updateScrollBar(); nav.reset(); });
        addRenderableWidget(searchBox);
        setFocused(searchBox);

        addRenderableWidget(new KvePlateButton(guiLeft + GRID_W / 2 - 28, guiTop + GUI_H - 16, 56, 13, Component.literal(Lang.get("gui.kve.btn.cancel")),
                btn -> close()));

        int gridH = VISIBLE_ROWS * CELL_H;
        scrollBar = new ScrollBar(
                guiLeft + GRID_X + COLS * CELL_W + 4,
                guiTop + GRID_Y,
                gridH, 0, VISIBLE_ROWS);

        rebuildFilter("");
        updateScrollBar();
    }

    /** ESC returns to the caller instead of closing the whole GUI. */
    @Override
    public void onClose() { close(); }

    private void rebuildFilter(String q) {
        listIndex.filter(q, null);
        filtered.clear();
        filtered.addAll(listIndex.filtered());
        nav.clamp(filtered.size());
    }

    private void updateScrollBar() {
        if (scrollBar != null) {
            int totalRows = (int) Math.ceil(filtered.size() / (double) COLS);
            scrollBar.setTotals(totalRows, VISIBLE_ROWS);
        }
    }

    private LivingEntity previewFor(EntityType<?> et) {
        LivingEntity cached = previewCache.get(et);
        if (cached != null) return cached;
        try {
            var e = et.create(minecraft.level);
            if (e instanceof LivingEntity le) {
                previewCache.put(et, le);
                return le;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Same formula as MobEditorScreen#calcScale(), so a mob looks the same size here as it will in that screen's own preview. */
    private float calcScale(EntityType<?> et) {
        var bb = et.getDimensions();
        float max = Math.max(bb.width, bb.height);
        return Math.min(45f, Math.max(14f, 40f / Math.max(1f, max)));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!hosted) {
            renderBackground(g);
            KveGuiStyle.drawWindow(g, guiLeft, guiTop, GUI_W, GUI_H, "Elegir Mob (" + filtered.size() + ")");
        }

        hoveredType = null;
        int scroll = scrollBar.getScroll();
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = (row + scroll) * COLS + col;
                if (idx >= filtered.size()) break;
                EntityType<?> et = filtered.get(idx);
                int cx = guiLeft + GRID_X + col * (CELL_W + 2);
                int cy = guiTop  + GRID_Y + row * CELL_H;
                boolean hov = mx >= cx && mx < cx + CELL_W && my >= cy && my < cy + CELL_H - 2;
                boolean kb  = nav.isCursor(idx);
                if (hov) hoveredType = et;
                else if (kb && hoveredType == null) hoveredType = et;
                g.fill(cx, cy, cx + CELL_W, cy + CELL_H - 2, hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
                g.hLine(cx, cx + CELL_W - 1, cy, KveGuiStyle.BORDER_SUBTLE);
                g.hLine(cx, cx + CELL_W - 1, cy + CELL_H - 3, KveGuiStyle.BORDER_SUBTLE);
                g.vLine(cx, cy, cy + CELL_H - 3, KveGuiStyle.BORDER_SUBTLE);
                g.vLine(cx + CELL_W - 1, cy, cy + CELL_H - 3, KveGuiStyle.BORDER_SUBTLE);
                if (kb) {
                    g.hLine(cx, cx + CELL_W - 1, cy, KveGuiStyle.ACCENT_GOLD);
                    g.hLine(cx, cx + CELL_W - 1, cy + CELL_H - 3, KveGuiStyle.ACCENT_GOLD);
                    g.vLine(cx, cy, cy + CELL_H - 3, KveGuiStyle.ACCENT_GOLD);
                    g.vLine(cx + CELL_W - 1, cy, cy + CELL_H - 3, KveGuiStyle.ACCENT_GOLD);
                }
                String name = RegistryIndex.name(et);
                String fullId = RegistryIndex.id(et);
                int nsReserved = 0;
                String ns = null;
                int colon = fullId.indexOf(':');
                if (colon > 0 && !fullId.regionMatches(0, "minecraft", 0, colon)) {
                    ns = fullId.substring(0, colon);
                    if (ns.length() > 8) ns = ns.substring(0, 7) + ".";
                    nsReserved = font.width(ns) + 8;
                }
                KveGuiStyle.drawTextSafe(g, name, cx + 4, cy + 4, CELL_W - nsReserved - 4, (hov || kb) ? 0xFFFFFFFF : KveGuiStyle.TEXT_PRIMARY);
                if (ns != null) {
                    KveGuiStyle.drawTextSafe(g, ns, cx + CELL_W - font.width(ns) - 3, cy + 4, nsReserved, 0xFF556688);
                }
            }
        }

        scrollBar.render(g, mx, my);

        int totalRows = (int) Math.ceil(filtered.size() / (double) COLS);
        KveGuiStyle.drawCenteredTextSafe(g,
                (scroll + 1) + " / " + Math.max(1, totalRows),
                guiLeft + GRID_W / 2, guiTop + GUI_H - 25, KveGuiStyle.th(0xFF778899));

        drawPreviewPanel(g, mx, my);

        super.render(g, mx, my, pt);
    }

    private void drawPreviewPanel(GuiGraphics g, int mx, int my) {
        int px = guiLeft + GRID_W;
        int py = guiTop + 1;
        int pw = PREVIEW_W - 1;
        int ph = GUI_H - 2;
        g.vLine(px, guiTop, guiTop + GUI_H - 1, KveGuiStyle.th(0xFF2A4A6A));
        g.fill(px + 1, py, px + pw, py + ph, 0xFF0A0A18);

        if (hoveredType == null) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.pick.mob.hover_hint"),
                    px + 6, py + ph / 2 - 10, pw - 10, KveGuiStyle.th(0xFF556677));
            return;
        }

        LivingEntity preview = previewFor(hoveredType);
        int cx = px + pw / 2;
        int cy = py + ph - 60;
        if (preview != null) {
            try {
                float offX = (float) (cx - mx);
                float offY = (float) (cy - 50 - my);
                InventoryScreen.renderEntityInInventoryFollowsMouse(
                        g, cx, cy, (int) calcScale(hoveredType), offX, offY, preview);
            } catch (Exception ignored) {
                KveGuiStyle.drawCenteredTextSafe(g, "Error", cx, cy, 0xFF664444);
            }
        } else {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.no_preview"),
                    px + 6, cy, pw - 10, KveGuiStyle.th(0xFF556677));
        }

        String name = KveGuiStyle.truncate(font, RegistryIndex.name(hoveredType), pw - 6);
        KveGuiStyle.drawCenteredTextSafe(g, name, cx, py + ph - 42, KveGuiStyle.TEXT_PRIMARY);

        String fullId = RegistryIndex.id(hoveredType);
        if (!fullId.isEmpty()) {
            String id = KveGuiStyle.truncate(font, fullId, pw - 6);
            KveGuiStyle.drawCenteredTextSafe(g, id, cx, py + ph - 32, KveGuiStyle.th(0xFF667788));
        }
    }

    private void pick(int idx) {
        if (idx < 0 || idx >= filtered.size()) return;
        callback.accept(filtered.get(idx));
        close();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar.mouseClicked(mx, my, btn)) return true;
        int scroll = scrollBar.getScroll();
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = (row + scroll) * COLS + col;
                if (idx >= filtered.size()) break;
                int cx = guiLeft + GRID_X + col * (CELL_W + 2);
                int cy = guiTop + GRID_Y + row * CELL_H;
                if (mx >= cx && mx < cx + CELL_W && my >= cy && my < cy + CELL_H - 2) {
                    pick(idx);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (scrollBar.mouseDragged(mx, my, btn, dx, dy)) return true;
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override public boolean mouseReleased(double mx, double my, int btn) { scrollBar.mouseReleased(mx, my, btn); return super.mouseReleased(mx, my, btn); }
    @Override public boolean mouseScrolled(double mx, double my, double delta) { scrollBar.mouseScrolled(delta); return true; }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (nav.keyPressed(key, filtered.size(), COLS, VISIBLE_ROWS, scrollBar)) return true;
        if (ListKeyNav.isConfirm(key) && nav.active()) { pick(nav.cursor()); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        return true; }
    @Override public boolean isPauseScreen() { return false; }
}