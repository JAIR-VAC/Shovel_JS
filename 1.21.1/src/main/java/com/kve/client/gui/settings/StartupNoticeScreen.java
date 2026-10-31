package com.kve.client.gui.settings;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;

import com.kve.common.Lang;
import com.kve.common.config.KVEConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;

@OnlyIn(Dist.CLIENT)
public class StartupNoticeScreen extends Screen {

    private static final int W = 360;
    private static final int H = 202;

    private static final String[][] OPTIONAL = {
            {"jei", "gui.kve.notice.opt.viewers"},
            {"jade", "gui.kve.notice.opt.jade"},
    };

    private final Screen returnTo;

    public StartupNoticeScreen(Screen returnTo) {
        super(Component.literal("Shovel JS"));
        this.returnTo = returnTo;
    }

    @Override
    protected void init() {
        int cx = (width  - W) / 2;
        int cy = (height - H) / 2;
        int bx = cx + 10;
        int by = cy + H - 24;

        addRenderableWidget(new KvePlateButton(bx, by, 150, 18, Component.literal(Lang.get("gui.kve.notice.ok")),
                b -> minecraft.setScreen(returnTo)));

        addRenderableWidget(new KvePlateButton(bx + 160, by, 180, 18, Component.literal(Lang.get("gui.kve.onboarding.dont_show_again")),
                b -> {
                    KVEConfig.showStartupNotice = false;
                    KVEConfig.save();
                    minecraft.setScreen(returnTo);
                }));
    }

    private static boolean loaded(String modId) {
        try {
            ModList ml = ModList.get();
            if (ml == null) return false;
            if (modId.equals("jei")) return ml.isLoaded("jei") || ml.isLoaded("emi");
            return ml.isLoaded(modId);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        int cx = (width  - W) / 2;
        int cy = (height - H) / 2;

        KveGuiStyle.drawWindow(g, cx, cy, W, H, null);
        KveGuiStyle.drawCenteredTextSafe(g, Lang.get("gui.kve.notice.title"), cx + W / 2, cy + 4, KveGuiStyle.TEXT_PRIMARY);

        int lx = cx + 14;
        int tw = W - 28;
        KveGuiStyle.Layout layout = new KveGuiStyle.Layout(lx, cy + 24, 12);

        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.notice.intro"), lx, layout.row(), tw, -1);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.notice.open"), lx, layout.row(), tw, -1);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.notice.apply"), lx, layout.row(), tw, -1);
        layout.gap(4);

        boolean kubejs = loaded("kubejs");
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.notice.kubejs") + "  "
                        + Lang.get(kubejs ? "gui.kve.notice.installed" : "gui.kve.notice.missing"),
                lx + 8, layout.row(), tw - 8, -1);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.notice.kubejs_hint"), lx + 8, layout.row(), tw - 8, -1);
        layout.gap(4);

        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.notice.optional"), lx, layout.row(), tw, -1);
        layout.gap(2);
        for (String[] opt : OPTIONAL) {
            String status = Lang.get(loaded(opt[0]) ? "gui.kve.notice.installed" : "gui.kve.notice.not_detected");
            KveGuiStyle.drawTextSafe(g, Lang.get(opt[1]) + "  " + status, lx + 8, layout.row(), tw - 8, -1);
        }
        layout.gap(4);

        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.notice.disable"), lx, layout.row(), tw, -1);

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override public boolean isPauseScreen()     { return false; }
    @Override public boolean shouldCloseOnEsc()  { return true; }

    @Override
    public void onClose() {
        minecraft.setScreen(returnTo);
    }
}
