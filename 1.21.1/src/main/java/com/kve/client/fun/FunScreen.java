package com.kve.client.fun;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.permission.ClientPermissionState;
import com.kve.common.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Herramienta Fun: elegir que aleatorizar, con que intensidad y que dejar fuera. */
@OnlyIn(Dist.CLIENT)
public class FunScreen extends Screen {

    private static final int W = 360;
    private static final int BTN = 16;

    private final Screen parent;
    private final FunOptions opts = FunOptions.CURRENT;
    private KveScaledEditBox seedBox, idsBox;
    private int cx, cy, h;
    private int domainsY, intensityY, seedY, excludeY, noteY;
    private String flash = "";

    public FunScreen(Screen parent) {
        super(Component.literal(Lang.get("gui.kve.fun.title")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int pad = KveGuiStyle.PAD;
        h = Math.min(height - 16, 318);
        cx = (width - W) / 2;
        cy = (height - h) / 2;
        int inner = W - pad * 2;
        int y = cy + KveGuiStyle.TITLEBAR_H + 6 + font.lineHeight * 2 + 6;

        domainsY = y;
        y += 11;
        int colW = (inner - 8) / 3;
        FunOptions.Domain[] ds = FunOptions.Domain.values();
        for (int i = 0; i < ds.length; i++) {
            FunOptions.Domain d = ds[i];
            int bx = cx + pad + (i % 3) * (colW + 4), by = y + (i / 3) * (BTN + 4);
            addRenderableWidget(new KvePlateButton(bx, by, colW, BTN, Component.literal(Lang.get(d.key)), b -> {
                if (!opts.domains.remove(d)) opts.domains.add(d);
                rebuildWidgets();
            }).withSelected(opts.domains.contains(d)));
        }
        y += ((ds.length + 2) / 3) * (BTN + 4) + 6;

        intensityY = y;
        y += 11;
        FunOptions.Intensity[] is = FunOptions.Intensity.values();
        for (int i = 0; i < is.length; i++) {
            FunOptions.Intensity it = is[i];
            addRenderableWidget(new KvePlateButton(cx + pad + i * (colW + 4), y, colW, BTN, Component.literal(Lang.get(it.key)), b -> {
                opts.intensity = it;
                rebuildWidgets();
            }).withSelected(opts.intensity == it));
        }
        y += BTN + 10;

        seedY = y;
        y += 11;
        seedBox = new KveScaledEditBox(font, cx + pad, y, colW * 2 + 4, 14, Component.literal(Lang.get("gui.kve.fun.seed")));
        seedBox.setHint(Component.literal(Lang.get("gui.kve.fun.seed_hint")));
        seedBox.setMaxLength(24);
        seedBox.setValue(opts.seedText);
        seedBox.setResponder(v -> opts.seedText = v);
        addRenderableWidget(seedBox);
        addRenderableWidget(new KvePlateButton(cx + pad + (colW + 4) * 2, y - 1, colW, BTN, Component.literal(Lang.get("gui.kve.fun.seed_random")), b -> {
            opts.seedText = String.valueOf(new java.util.Random().nextInt(1_000_000));
            seedBox.setValue(opts.seedText);
        }));
        y += BTN + 10;

        excludeY = y;
        y += 11;
        String modsLabel = Lang.get("gui.kve.fun.exclude_mods", opts.excludedMods.size());
        addRenderableWidget(new KvePlateButton(cx + pad, y - 1, colW, BTN, Component.literal(modsLabel),
                b -> minecraft.setScreen(new FunModPickerScreen(this, opts.excludedMods))));
        idsBox = new KveScaledEditBox(font, cx + pad + colW + 4, y, inner - colW - 4, 14, Component.literal(Lang.get("gui.kve.fun.exclude_ids")));
        idsBox.setHint(Component.literal(Lang.get("gui.kve.fun.exclude_ids_hint")));
        idsBox.setMaxLength(2000);
        idsBox.setValue(opts.excludedIdsText);
        idsBox.setResponder(v -> opts.excludedIdsText = v);
        addRenderableWidget(idsBox);
        y += BTN + 8;
        noteY = y;

        KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(cx + pad, cy + h - pad - KveGuiStyle.BTN_H, cx + W - pad);
        addRenderableWidget(row.add(0, Lang.get("gui.kve.fun.run"), b -> run()).withPrimary(true));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.close"), b -> onClose()));
    }

    private void run() {
        if (!ClientPermissionState.canEdit()) { flash = Lang.get("gui.kve.perm.denied"); return; }
        if (opts.domains.isEmpty()) { flash = Lang.get("gui.kve.fun.pick_one"); return; }
        minecraft.setScreen(new FunProgressScreen(this, new FunRandomizer(opts)));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, h, getTitle().getString());
        int pad = KveGuiStyle.PAD, inner = W - pad * 2;
        int ty = cy + KveGuiStyle.TITLEBAR_H + 6;
        for (String l : KveGuiStyle.wrapTextSafe(font, Lang.get("gui.kve.fun.desc"), inner)) {
            KveGuiStyle.drawTextSafe(g, l, cx + pad, ty, inner, KveGuiStyle.TEXT_MUTED);
            ty += font.lineHeight;
        }
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.fun.what"), cx + pad, domainsY, inner, KveGuiStyle.ACCENT_GOLD);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.fun.intensity"), cx + pad, intensityY, inner, KveGuiStyle.ACCENT_GOLD);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.fun.seed"), cx + pad, seedY, inner, KveGuiStyle.ACCENT_GOLD);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.fun.exclude"), cx + pad, excludeY, inner, KveGuiStyle.ACCENT_GOLD);
        int ny = noteY;
        List<String> notes = new java.util.ArrayList<>();
        notes.add(Lang.get("gui.kve.fun.note.progression"));
        notes.add(Lang.get("gui.kve.fun.note.pending"));
        boolean local = minecraft.getSingleplayerServer() != null;
        if (!local && (opts.domains.contains(FunOptions.Domain.LOOT) || opts.domains.contains(FunOptions.Domain.WORLDGEN))) {
            notes.add(Lang.get("gui.kve.fun.note.local_only"));
        }
        int bottom = cy + h - pad - KveGuiStyle.BTN_H - 4;
        for (String n : notes) {
            for (String l : KveGuiStyle.wrapTextSafe(font, "• " + n, inner)) {
                if (ny + font.lineHeight > bottom) break;
                KveGuiStyle.drawTextSafe(g, l, cx + pad, ny, inner, KveGuiStyle.TEXT_MUTED);
                ny += font.lineHeight;
            }
        }
        if (!flash.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, flash, cx + pad, bottom - font.lineHeight, inner, KveGuiStyle.TEXT_WARN);
        }
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
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
