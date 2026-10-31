package com.kve.client.gui;

import com.kve.common.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RecipeShareScreen extends Screen {

    private static final int W = 300;
    private static final int H = 150;

    private final Screen parent;
    private int cx, cy;

    public RecipeShareScreen(Screen parent) {
        super(Component.literal(Lang.get("gui.kve.subset.share.title")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        cx = (width - W) / 2;
        cy = (height - H) / 2;
        int x = cx + KveGuiStyle.PAD;
        int w = W - KveGuiStyle.PAD * 2;
        int y = cy + KveGuiStyle.TITLEBAR_H + 8;
        addRenderableWidget(new KvePlateButton(x, y, w, 16, Component.literal(Lang.get("gui.kve.subset.export.btn")),
                b -> minecraft.setScreen(new RecipeSubsetExportScreen(parent))));
        addRenderableWidget(new KvePlateButton(x, y + 42, w, 16, Component.literal(Lang.get("gui.kve.subset.import.btn")),
                b -> minecraft.setScreen(new RecipeSubsetImportScreen(parent))));
        KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(x, cy + H - KveGuiStyle.PAD - 16, cx + W - KveGuiStyle.PAD);
        addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.cancel"), b -> onClose()));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, H, getTitle().getString());
        int x = cx + KveGuiStyle.PAD;
        int w = W - KveGuiStyle.PAD * 2;
        int y = cy + KveGuiStyle.TITLEBAR_H + 8;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.subset.share.export_desc"), x, y + 19, w, KveGuiStyle.TEXT_MUTED);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.subset.share.import_desc"), x, y + 61, w, KveGuiStyle.TEXT_MUTED);
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
