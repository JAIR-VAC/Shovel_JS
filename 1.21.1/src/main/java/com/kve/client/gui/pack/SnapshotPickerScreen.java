package com.kve.client.gui.pack;

import com.kve.client.gui.KveGuiStyle;
import com.kve.common.Lang;
import com.kve.common.persist.PackSnapshotManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * Elegir una version del pack de una lista (en vez del boton que
 * ciclaba por todas). Devuelve el nombre de la instantanea, o {@code null}
 * para "estado actual", por el callback.
 */
@OnlyIn(Dist.CLIENT)
public class SnapshotPickerScreen extends Screen {

    private static final int W = 300;
    private static final int H = 230;
    private static final int ROW_H = 13;

    private final Screen parent;
    private final String title;
    private final Consumer<String> onPick;
    private final List<String> names = new ArrayList<>();
    private final List<Long> dates = new ArrayList<>();
    private int scroll = 0, cx, cy;

    public SnapshotPickerScreen(Screen parent, String title, boolean includeNow, Consumer<String> onPick) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
        this.onPick = onPick;
        if (includeNow) { names.add(null); dates.add(System.currentTimeMillis()); }
        for (PackSnapshotManager.Info i : PackSnapshotManager.list(PackSnapshotManager.gameDir())) {
            names.add(i.fileName());
            dates.add(i.modifiedMs());
        }
    }

    private int rows() { return (cy + H - KveGuiStyle.PAD - 22 - (cy + KveGuiStyle.TITLEBAR_H + 8)) / ROW_H; }

    @Override
    protected void init() {
        cx = (width - W) / 2;
        cy = (height - H) / 2;
        KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(cx + KveGuiStyle.PAD, cy + H - KveGuiStyle.PAD - 16, cx + W - KveGuiStyle.PAD);
        addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.cancel"), b -> minecraft.setScreen(parent)));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, H, title);
        int x = cx + KveGuiStyle.PAD, w = W - KveGuiStyle.PAD * 2;
        int ly = cy + KveGuiStyle.TITLEBAR_H + 8;
        int rows = rows();
        if (names.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.snapdiff.none"), x, ly + 2, w, KveGuiStyle.TEXT_MUTED);
        }
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (int i = 0; i < rows && i + scroll < names.size(); i++) {
            int idx = i + scroll;
            int ry = ly + i * ROW_H;
            boolean hov = mx >= x && mx < x + w && my >= ry && my < ry + ROW_H;
            if (hov) g.fill(x - 2, ry, x + w, ry + ROW_H, KveGuiStyle.selectionBg());
            String name = names.get(idx) == null ? Lang.get("gui.kve.snapdiff.current") : names.get(idx).replaceFirst("\\.zip$", "");
            KveGuiStyle.drawTextSafe(g, name, x, ry + 3, w - 110, KveGuiStyle.TEXT_PRIMARY);
            KveGuiStyle.drawTextSafe(g, KveGuiStyle.formatDate("yyyy-MM-dd HH:mm", dates.get(idx)), x + w - 108, ry + 3, 106, KveGuiStyle.TEXT_MUTED);
        }
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int x = cx + KveGuiStyle.PAD, w = W - KveGuiStyle.PAD * 2;
        int ly = cy + KveGuiStyle.TITLEBAR_H + 8;
        if (btn == 0 && mx >= x && mx < x + w && my >= ly && my < ly + rows() * ROW_H) {
            int idx = (int) ((my - ly) / ROW_H) + scroll;
            if (idx < names.size()) {
                onPick.accept(names.get(idx));
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, names.size() - rows()), scroll + (delta > 0 ? -2 : 2)));
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
