package com.kve.client.gui.pack;

import com.kve.client.gui.KveGuiStyle;
import com.kve.common.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Informe de texto con filas de color, resumen arriba y acciones
 * abajo - lo comparten "Probar el pack", "Revision de balance" y "Guia del pack".
 * El contenido lo arma un {@link Supplier} que se vuelve a llamar con
 * "Revisar de nuevo" (o solo, tras {@link #rerunAfter()}).
 */
@OnlyIn(Dist.CLIENT)
public class PackReportScreen extends Screen {

    public record Row(String text, int color) {}

    public record Report(String summary, int summaryColor, List<Row> rows) {}

    /** Boton extra: texto ya traducido y lo que hace (recibe esta pantalla). */
    public record Action(String label, Consumer<PackReportScreen> run) {}

    private static final int W = 460;
    private static final int H = 270;
    private static final int ROW_H = 11;

    private final Screen parent;
    private final Supplier<Report> producer;
    private final List<Action> actions;
    private final boolean rerunnable;
    private Report report;
    private int scroll = 0;
    private String flash = "";
    private int flashColor = KveGuiStyle.TEXT_OK;
    private long rerunAt = 0;
    private int cx, cy;

    public PackReportScreen(Screen parent, String title, Supplier<Report> producer, boolean rerunnable, List<Action> actions) {
        super(Component.literal(title));
        this.parent = parent;
        this.producer = producer;
        this.rerunnable = rerunnable;
        this.actions = actions;
        rerun();
    }

    public void rerun() {
        try {
            report = producer.get();
        } catch (RuntimeException e) {
            report = new Report(Lang.get("gui.kve.report.failed", e.toString()), KveGuiStyle.TEXT_ERROR, List.of());
        }
        scroll = Math.min(scroll, maxScroll());
    }

    /** Vuelve a armar el informe dentro de {@code ms} (ej. despues de mandar /reload). */
    public void rerunAfter(long ms, String waitingMsg) {
        rerunAt = System.currentTimeMillis() + ms;
        flash(waitingMsg, KveGuiStyle.TEXT_LABEL);
    }

    public void flash(String msg, int color) {
        flash = msg;
        flashColor = color;
    }

    public List<Row> rows() { return report.rows(); }

    @Override
    protected void init() {
        cx = (width - W) / 2;
        cy = (height - H) / 2;
        KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(cx + KveGuiStyle.PAD, cy + H - KveGuiStyle.PAD - 16, cx + W - KveGuiStyle.PAD);
        if (rerunnable) addRenderableWidget(row.add(0, Lang.get("gui.kve.report.rerun"), b -> { rerun(); flash(Lang.get("gui.kve.report.updated"), KveGuiStyle.TEXT_OK); }));
        for (Action a : actions) addRenderableWidget(row.add(0, a.label(), b -> a.run().accept(this)));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.health.copy"), b -> copy()));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.close"), b -> onClose()));
    }

    private void copy() {
        List<String> lines = new ArrayList<>();
        lines.add(report.summary());
        for (Row r : report.rows()) lines.add(r.text());
        minecraft.keyboardHandler.setClipboard(String.join("\n", lines).replace("§", ""));
        flash(Lang.get("gui.kve.report.copied"), KveGuiStyle.TEXT_OK);
    }

    private int listTop() { return cy + KveGuiStyle.TITLEBAR_H + 18; }

    private int visibleRows() { return (cy + H - KveGuiStyle.PAD - 34 - listTop()) / ROW_H; }

    private int maxScroll() { return Math.max(0, report.rows().size() - visibleRows()); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (rerunAt > 0 && System.currentTimeMillis() >= rerunAt) {
            rerunAt = 0;
            rerun();
            flash(Lang.get("gui.kve.report.updated"), KveGuiStyle.TEXT_OK);
        }
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, H, getTitle().getString());
        int x = cx + KveGuiStyle.PAD;
        int w = W - KveGuiStyle.PAD * 2;
        KveGuiStyle.drawTextSafe(g, report.summary(), x, cy + KveGuiStyle.TITLEBAR_H + 5, w, report.summaryColor());

        int ly = listTop();
        int rows = visibleRows();
        g.fill(x - 2, ly - 1, x + w, ly + rows * ROW_H + 1, KveGuiStyle.BG_FIELD);
        List<Row> all = report.rows();
        for (int i = 0; i < rows && i + scroll < all.size(); i++) {
            Row r = all.get(i + scroll);
            KveGuiStyle.drawTextSafe(g, r.text(), x, ly + i * ROW_H + 2, w - 4, r.color());
        }
        if (all.size() > rows) {
            int trackH = rows * ROW_H;
            int thumbH = Math.max(8, trackH * rows / all.size());
            int thumbY = ly + (trackH - thumbH) * scroll / Math.max(1, maxScroll());
            g.fill(x + w - 3, ly, x + w - 1, ly + trackH, KveGuiStyle.SCROLL_TRACK);
            g.fill(x + w - 3, thumbY, x + w - 1, thumbY + thumbH, KveGuiStyle.SCROLL_THUMB);
        }
        if (!flash.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, flash, x, ly + rows * ROW_H + 4, w, flashColor);
        }
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll + (delta > 0 ? -3 : 3)));
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
