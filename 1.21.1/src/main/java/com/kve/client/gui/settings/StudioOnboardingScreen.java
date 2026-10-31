package com.kve.client.gui.settings;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.MiniItemIcon;
import com.kve.client.gui.studio.KveStudioShell;
import com.kve.common.Lang;
import com.kve.common.config.KVEConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class StudioOnboardingScreen extends Screen {

    private static final int W = 440;
    private static final int H = 280;
    private static final int CARD_H = 46;
    private static final int STEP_H = 16;

    private final KveStudioShell studio;
    private UserPersona selected;
    private final List<int[]> cardRects = new ArrayList<>();
    private final List<Boolean> stepDone = new ArrayList<>();
    private boolean appliedDone;
    private int cx, cy, stepsY;

    public StudioOnboardingScreen(Screen studio) {
        super(Lang.c("gui.kve.onboarding.title"));
        this.studio = studio instanceof KveStudioShell s ? s : new KveStudioShell();
        this.selected = UserPersona.current();
    }

    @Override
    protected void init() {
        cx = (width - W) / 2;
        cy = (height - H) / 2;
        clearWidgets();

        cardRects.clear();
        int cardW = (W - 28 - 3 * 6) / 4;
        UserPersona[] all = UserPersona.values();
        for (int i = 0; i < all.length; i++) {
            cardRects.add(new int[]{cx + 14 + i * (cardW + 6), cy + 34, cardW, CARD_H});
        }

        UserPersona.loadState();
        stepDone.clear();
        if (selected != null) for (UserPersona.Step s : selected.steps) stepDone.add(s.isDone());
        appliedDone = UserPersona.hasAppliedSomething();

        stepsY = cy + 150;
        if (selected != null) {
            int y = stepsY + 14;
            for (UserPersona.Step s : selected.steps) {
                addRenderableWidget(new KvePlateButton(cx + W - 14 - 40, y, 40, 13, Lang.c("gui.kve.persona.go"),
                        b -> open(s.tabKey())));
                y += STEP_H;
            }
        }

        int by = cy + H - 24;
        String start = Lang.get(selected == null ? "gui.kve.btn.understood" : "gui.kve.persona.start");
        addRenderableWidget(new KvePlateButton(cx + 14, by, 160, 18, Component.literal(start),
                b -> open(selected == null ? null : selected.startTab)));
        addRenderableWidget(new KvePlateButton(cx + W - 14 - 170, by, 170, 18, Lang.c("gui.kve.onboarding.dont_show_again"),
                b -> {
                    KVEConfig.showStudioOnboarding = false;
                    KVEConfig.save();
                    open(selected == null ? null : selected.startTab);
                }));
    }

    private void open(String tabKey) {
        if (tabKey != null) studio.showTab(tabKey);
        minecraft.setScreen(studio);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        UserPersona[] all = UserPersona.values();
        for (int i = 0; i < cardRects.size(); i++) {
            int[] r = cardRects.get(i);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                selected = all[i];
                UserPersona.set(selected);
                init();
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, H, null);
        KveGuiStyle.drawCenteredTextSafe(g, Lang.get("gui.kve.onboarding.header"), cx + W / 2, cy + 6, KveGuiStyle.TEXT_PRIMARY);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.persona.question"), cx + 14, cy + 22, W - 28, KveGuiStyle.ACCENT_GOLD);

        // 1) Tarjetas de perfil.
        UserPersona[] all = UserPersona.values();
        for (int i = 0; i < cardRects.size(); i++) {
            int[] r = cardRects.get(i);
            UserPersona p = all[i];
            boolean sel = p == selected;
            boolean hover = mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], sel ? KveGuiStyle.selectionBg() : KveGuiStyle.BG_FIELD);
            g.renderOutline(r[0], r[1], r[2], r[3], sel ? KveGuiStyle.ACCENT_GOLD : hover ? KveGuiStyle.BORDER_OUTER : KveGuiStyle.BORDER_SUBTLE);
            MiniItemIcon.draw(g, r[0] + 5, r[1] + 5, 16, MiniItemIcon.item(p.iconItem));
            KveGuiStyle.drawTextSafe(g, Lang.get(p.labelKey()), r[0] + 25, r[1] + 9, r[2] - 28, sel ? KveGuiStyle.ACCENT_GOLD : KveGuiStyle.TEXT_PRIMARY);
            KveGuiStyle.drawTextSafe(g, Lang.get(p.descKey()), r[0] + 5, r[1] + 26, r[2] - 8, KveGuiStyle.TEXT_MUTED);
        }

        int fy = cy + 92;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.persona.flow.title"), cx + 14, fy, W - 28, KveGuiStyle.ACCENT_GOLD);
        fy += 12;
        String[] flow = {"gui.kve.persona.flow.save", "gui.kve.persona.flow.pending", "gui.kve.persona.flow.apply", "gui.kve.persona.flow.reload"};
        int arrowW = 14;
        int boxW = (W - 28 - arrowW * 3) / 4;
        for (int i = 0; i < flow.length; i++) {
            int bx = cx + 14 + i * (boxW + arrowW);
            g.fill(bx, fy, bx + boxW, fy + 18, KveGuiStyle.BG_FIELD);
            g.renderOutline(bx, fy, boxW, 18, i == 2 ? KveGuiStyle.TEXT_OK : KveGuiStyle.BORDER_OUTER);
            KveGuiStyle.drawCenteredTextSafe(g, (i + 1) + ". " + Lang.get(flow[i]), bx + boxW / 2, fy + 5, KveGuiStyle.TEXT_PRIMARY);
            if (i < flow.length - 1) {
                KveGuiStyle.drawCenteredTextSafe(g, "→", bx + boxW + arrowW / 2, fy + 5, KveGuiStyle.ACCENT_GOLD);
            }
        }
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.persona.flow.note"), cx + 14, fy + 24, W - 28, KveGuiStyle.TEXT_MUTED);

        // 3) Primeros pasos del perfil, con estado real.
        if (selected == null) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.persona.pick_first"), cx + 14, stepsY, W - 28, KveGuiStyle.TEXT_MUTED);
        } else {
            int done = 0;
            for (boolean b : stepDone) if (b) done++;
            if (appliedDone) done++;
            int total = selected.steps.size() + 1;
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.persona.steps_title", Lang.get(selected.labelKey()), done, total),
                    cx + 14, stepsY, W - 28, KveGuiStyle.ACCENT_GOLD);
            int y = stepsY + 14;
            for (int i = 0; i < selected.steps.size(); i++) {
                drawStep(g, y, stepDone.get(i), Lang.get("gui.kve.persona.step." + selected.steps.get(i).key()));
                y += STEP_H;
            }
            drawStep(g, y, appliedDone, Lang.get("gui.kve.persona.step.apply"));
            int barX = cx + W - 14 - 120, barY = stepsY + 3;
            g.fill(barX, barY, barX + 120, barY + 4, KveGuiStyle.BG_FIELD);
            g.fill(barX, barY, barX + 120 * done / total, barY + 4, KveGuiStyle.TEXT_OK);
        }

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    private void drawStep(GuiGraphics g, int y, boolean done, String text) {
        KveGuiStyle.drawTextSafe(g, done ? "✔" : "○", cx + 16, y + 3, 10, done ? KveGuiStyle.TEXT_OK : KveGuiStyle.TEXT_MUTED);
        KveGuiStyle.drawTextSafe(g, text, cx + 30, y + 3, W - 28 - 60, done ? KveGuiStyle.TEXT_MUTED : KveGuiStyle.TEXT_PRIMARY);
    }

    @Override public boolean isPauseScreen()    { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }

    @Override
    public void onClose() {
        minecraft.setScreen(studio);
    }
}
