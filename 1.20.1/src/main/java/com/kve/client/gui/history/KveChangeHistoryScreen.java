package com.kve.client.gui.history;

import com.kve.common.Lang;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.workspace.pending.PendingChange;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class KveChangeHistoryScreen extends Screen {

    private static final int GUI_W = 260;
    private static final int GUI_H = 210;
    private static final int ROW_H = 12;
    private static final int ROWS_VISIBLE = 15;

    private final Screen parent;
    private final List<KveChangeLog.Entry> entries;
    private final Consumer<String> onContinueEditing;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);

    private int L, T;
    private ScrollBar scrollBar;

    public KveChangeHistoryScreen(Screen parent, String logId, Component title, Consumer<String> onContinueEditing) {
        super(title);
        this.parent = parent;
        this.onContinueEditing = onContinueEditing;
        this.entries = KveChangeLog.snapshot(logId);
    }

    private java.util.function.Function<String, net.minecraft.world.item.Item> iconFor = null;
    private final java.util.Map<String, net.minecraft.world.item.Item> iconCache = new java.util.HashMap<>();

    public KveChangeHistoryScreen withIcons(java.util.function.Function<String, net.minecraft.world.item.Item> iconFor) {
        this.iconFor = iconFor;
        return this;
    }

    private net.minecraft.world.item.Item iconOf(String entryId) {
        // containsKey en vez de computeIfAbsent: un "sin icono" (null) tambien queda guardado y no se
        // vuelve a buscar recorriendo todas las recetas en cada fotograma
        if (iconCache.containsKey(entryId)) return iconCache.get(entryId);
        net.minecraft.world.item.Item it;
        try { it = iconFor.apply(entryId); } catch (Exception ex) { it = null; }
        iconCache.put(entryId, it);
        return it;
    }

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();

        scrollBar = new ScrollBar(L + GUI_W - 8, T + 26, ROWS_VISIBLE * ROW_H, entries.size(), ROWS_VISIBLE);
        int half = (GUI_W - 12 - 4) / 2;
        addRenderableWidget(new KvePlateButton(L + 6, T + GUI_H - 16, half, 12, Lang.c("gui.kve.pack_tools.open_btn"),
                b -> minecraft.setScreen(new com.kve.client.gui.PackToolsScreen(this))));
        addRenderableWidget(new KvePlateButton(L + 10 + half, T + GUI_H - 16, half, 12, Lang.c("gui.kve.btn.close"), b -> onClose()));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, getTitle().getString());

        if (entries.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.change_history.empty"),
                    L + 6, T + 20, GUI_W - 12, KveGuiStyle.TEXT_MUTED);
        } else {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.change_history.hint"),
                    L + 6, T + 16, GUI_W - 16, KveGuiStyle.TEXT_MUTED);
            int scroll = scrollBar.getScroll();
            int visible = Math.min(ROWS_VISIBLE, Math.max(0, entries.size() - scroll));
            for (int i = 0; i < visible; i++) {
                KveChangeLog.Entry e = entries.get(i + scroll);
                int ry = T + 26 + i * ROW_H;
                boolean hoveredRow = KveGuiStyle.isHovered(mx, my, L + 6, ry, GUI_W - 20, ROW_H - 1);
                if (hoveredRow) g.fill(L + 6, ry, L + GUI_W - 14, ry + ROW_H - 1, com.kve.client.gui.KveGuiStyle.th(0xFF14243A));
                String time = KveGuiStyle.formatDate("HH:mm:ss", e.timestamp());
                KveGuiStyle.drawTextSafe(g, time, L + 6, ry + 1, 44, KveGuiStyle.TEXT_MUTED);
                KveGuiStyle.drawTextSafe(g, actionLabel(e.action()), L + 52, ry + 1, 36,
                        e.action() == PendingChange.Action.CREATE ? KveGuiStyle.TEXT_OK : KveGuiStyle.ACCENT_GOLD);
                int idW = iconFor != null ? GUI_W - 120 : GUI_W - 100;
                KveGuiStyle.drawTextSafe(g, e.entryId(), L + 90, ry + 1, idW, KveGuiStyle.TEXT_PRIMARY);
                if (iconFor != null) {
                    com.kve.client.gui.MiniItemIcon.draw(g, L + GUI_W - 25, ry + 1, 9, iconOf(e.entryId()));
                }
            }
        }
        scrollBar.render(g);

        super.render(g, mx, my, pt);
    }

    private String actionLabel(PendingChange.Action action) {
        return switch (action) {
            case CREATE -> Lang.get("gui.kve.change_history.action.create");
            case EDIT -> Lang.get("gui.kve.change_history.action.edit");
            case DELETE -> Lang.get("gui.kve.change_history.action.delete");
        };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (scrollBar != null && scrollBar.mouseClicked(mx, my, button)) return true;
        int scroll = scrollBar.getScroll();
        int visible = Math.min(ROWS_VISIBLE, Math.max(0, entries.size() - scroll));
        for (int i = 0; i < visible; i++) {
            int ry = T + 26 + i * ROW_H;
            if (mx >= L + 6 && mx < L + GUI_W - 14 && my >= ry && my < ry + ROW_H - 1) {
                String id = entries.get(i + scroll).entryId();
                if (onContinueEditing != null) onContinueEditing.accept(id);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (scrollBar != null && scrollBar.mouseDragged(mx, my, button, dx, dy)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (scrollBar != null) scrollBar.mouseReleased(mx, my, button);
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (scrollBar != null) { scrollBar.mouseScrolled(delta); return true; }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
