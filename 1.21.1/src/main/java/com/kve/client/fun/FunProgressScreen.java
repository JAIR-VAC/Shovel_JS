package com.kve.client.fun;

import com.kve.client.gui.DiffPreviewScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.common.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Ventana de carga del aleatorizador: paso actual, barra, porcentaje y tiempo restante; al final, el resumen. */
@OnlyIn(Dist.CLIENT)
public class FunProgressScreen extends Screen {

    private static final int W = 320;
    private static final long BUDGET_NANOS = 30_000_000L;

    private final Screen parent;
    private final FunRandomizer job;
    private final long startedAt = System.currentTimeMillis();
    private long finishedAt;
    private int cx, cy, h;

    FunProgressScreen(Screen parent, FunRandomizer job) {
        super(Component.literal(Lang.get("gui.kve.fun.progress.title")));
        this.parent = parent;
        this.job = job;
    }

    @Override
    protected void init() {
        h = job.finished() ? 150 : 104;
        cx = (width - W) / 2;
        cy = (height - h) / 2;
        int by = cy + h - KveGuiStyle.PAD - KveGuiStyle.BTN_H;
        KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(cx + KveGuiStyle.PAD, by, cx + W - KveGuiStyle.PAD);
        if (job.finished()) {
            KvePlateButton review = row.add(0, Lang.get("gui.kve.fun.progress.review"), b -> minecraft.setScreen(new DiffPreviewScreen(parent,
                    PendingChangeManager.snapshot(), () -> com.kve.client.gui.KveToast.applied(PendingChangeManager.applyAll())))).withPrimary(true);
            review.active = !PendingChangeManager.isEmpty();
            addRenderableWidget(review);
            addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.close"), b -> onClose()));
        } else {
            addRenderableWidget(row.add(0, Lang.get("gui.kve.fun.progress.cancel"), b -> onClose()).withDanger(true));
        }
    }

    @Override
    public void tick() {
        if (job.finished()) return;
        if (job.step(BUDGET_NANOS)) {
            finishedAt = System.currentTimeMillis();
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, h, getTitle().getString());
        int pad = KveGuiStyle.PAD, inner = W - pad * 2;
        int y = cy + KveGuiStyle.TITLEBAR_H + 6;
        if (!job.finished()) {
            KveGuiStyle.drawTextSafe(g, job.currentLabel(), cx + pad, y, inner, KveGuiStyle.ACCENT_GOLD);
            y += 14;
            int total = Math.max(1, job.totalUnits()), done = Math.min(total, job.doneUnits());
            double frac = done / (double) total;
            g.fill(cx + pad, y, cx + pad + inner, y + 10, KveGuiStyle.BG_FIELD);
            g.fill(cx + pad, y, cx + pad + (int) Math.round(inner * frac), y + 10, KveGuiStyle.ACCENT_GOLD);
            y += 14;
            String pct = Math.round(frac * 100) + "%  ·  " + done + " / " + total;
            KveGuiStyle.drawTextSafe(g, pct, cx + pad, y, inner, KveGuiStyle.TEXT_PRIMARY);
            y += 11;
            long elapsed = System.currentTimeMillis() - startedAt;
            String eta = elapsed < 500 || done == 0 ? Lang.get("gui.kve.fun.progress.estimating")
                    : Lang.get("gui.kve.fun.progress.eta", seconds((long) (elapsed * (total - done) / (double) done)));
            KveGuiStyle.drawTextSafe(g, eta, cx + pad, y, inner, KveGuiStyle.TEXT_MUTED);
        } else {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.fun.progress.done", seconds(finishedAt - startedAt)), cx + pad, y, inner, KveGuiStyle.TEXT_OK);
            y += 13;
            List<String> lines = new ArrayList<>();
            if (job.results().isEmpty()) lines.add(Lang.get("gui.kve.fun.progress.nothing"));
            for (Map.Entry<FunOptions.Domain, Integer> e : job.results().entrySet()) {
                lines.add("• " + Lang.get("gui.kve.fun.progress.result_line", Lang.get(e.getKey().key), e.getValue()));
            }
            for (String n : job.notes()) lines.add("⚠ " + n);
            if (!job.results().isEmpty()) lines.add(Lang.get("gui.kve.fun.progress.next"));
            int bottom = cy + h - pad - KveGuiStyle.BTN_H - 4;
            for (String l : lines) {
                for (String w : KveGuiStyle.wrapTextSafe(font, l, inner)) {
                    if (y + font.lineHeight > bottom) break;
                    KveGuiStyle.drawTextSafe(g, w, cx + pad, y, inner, l.startsWith("⚠") ? KveGuiStyle.TEXT_WARN : KveGuiStyle.TEXT_PRIMARY);
                    y += font.lineHeight;
                }
            }
        }
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    private static String seconds(long ms) {
        long s = Math.max(0, Math.round(ms / 1000.0));
        return s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s";
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
