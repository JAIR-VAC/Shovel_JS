package com.kve.client.structure;

import com.kve.common.structure.StructureVoxelGrid;

import com.kve.common.Lang;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.ScrollBar;
import net.minecraft.client.gui.GuiGraphics;
import com.kve.client.gui.KvePlateButton;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public class StructureHistoryScreen extends Screen {

    private static final int GUI_W = 260;
    private static final int GUI_H = 200;
    private static final int ROW_H = 12;
    private static final int ROWS_VISIBLE = 12;

    private final Screen parent;
    private final List<StructureVoxelGrid.HistoryEntry> entries;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);

    private int L, T;
    private ScrollBar scrollBar;

    public StructureHistoryScreen(Screen parent, List<StructureVoxelGrid.HistoryEntry> entries) {
        super(Lang.c("gui.kve.structure.history.title"));
        this.parent = parent;
        this.entries = entries;
    }

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();

        scrollBar = new ScrollBar(L + GUI_W - 10, T + 20, ROWS_VISIBLE * ROW_H, entries.size(), ROWS_VISIBLE);

        addRenderableWidget(new KvePlateButton(L + 6, T + GUI_H - 16, GUI_W - 12, 12, Lang.c("gui.kve.btn.close"), b -> onClose()));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.structure.history.title"));

        String hoveredFullDesc = null;
        int descMaxW = GUI_W - 62;
        if (entries.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.structure.history.empty"),
                    L + 6, T + 20, GUI_W - 12, KveGuiStyle.TEXT_MUTED);
        } else {
            int scroll = scrollBar.getScroll();
            int visible = Math.min(ROWS_VISIBLE, Math.max(0, entries.size() - scroll));
            for (int i = 0; i < visible; i++) {
                StructureVoxelGrid.HistoryEntry e = entries.get(i + scroll);
                int ry = T + 20 + i * ROW_H;
                String time = timeFormat.format(new Date(e.timestamp));
                KveGuiStyle.drawTextSafe(g, time, L + 6, ry + 1, 44, KveGuiStyle.TEXT_MUTED);
                KveGuiStyle.drawTextSafe(g, e.description, L + 52, ry + 1, descMaxW, KveGuiStyle.TEXT_PRIMARY);
                if (mx >= L + 52 && mx < L + 52 + descMaxW && my >= ry && my < ry + ROW_H
                        && font.width(e.description) > descMaxW) {
                    hoveredFullDesc = e.description;
                }
            }
        }
        scrollBar.render(g);

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);

        if (hoveredFullDesc != null) {
            KveGuiStyle.drawFramedTooltip(g, hoveredFullDesc, mx, my, width, height);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (scrollBar != null && scrollBar.mouseClicked(mx, my, button)) return true;
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
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (scrollBar != null) { scrollBar.mouseScrolled(delta); return true; }
        return super.mouseScrolled(mx, my, scrollX, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
