package com.kve.client.gui;

import com.kve.common.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class TooltipEditScreen extends Screen {

    private static final int MAX_LINES = 8;
    private static final int GUI_W = 260;
    private static final int ROW_H = 16;
    private static final int GUI_H = 60 + MAX_LINES * ROW_H;

    private final Screen parent;
    private final Consumer<List<String>> onSave;
    private final List<String> lines = new ArrayList<>();
    private final List<EditBox> rowBoxes = new ArrayList<>();

    private int L, T;

    public TooltipEditScreen(Screen parent, List<String> existing, Consumer<List<String>> onSave) {
        super(Lang.c("gui.kve.tooltip.title"));
        this.parent = parent;
        this.onSave = onSave;
        if (existing != null) {
            for (String s : existing) {
                if (lines.size() >= MAX_LINES) break;
                lines.add(s);
            }
        }
    }

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);
        rowBoxes.clear();

        int rowsToShow = Math.min(MAX_LINES, lines.size() + 1);
        for (int i = 0; i < rowsToShow; i++) {
            int y = T + 24 + i * ROW_H;
            String value = i < lines.size() ? lines.get(i) : "";
            EditBox box = new KveScaledEditBox(font, L + 6, y, GUI_W - 34, 12, Component.literal(""));
            box.setMaxLength(200);
            box.setValue(value);
            final int idx = i;
            box.setResponder(s -> setLine(idx, s));
            addRenderableWidget(box);
            rowBoxes.add(box);

            if (i < lines.size()) {
                addRenderableWidget(new KvePlateButton(L + GUI_W - 24, y - 1, 18, 14, Component.literal("X"),
                        b -> { lines.remove(idx); init(); }));
            }
        }

        addRenderableWidget(new KvePlateButton(L + 6, T + GUI_H - 20, 80, 14, Lang.c("gui.kve.btn.save"),
                b -> { save(); minecraft.setScreen(parent); }).withPrimary(true));
        addRenderableWidget(new KvePlateButton(L + GUI_W - 86, T + GUI_H - 20, 80, 14, Lang.c("gui.kve.btn.back"),
                b -> minecraft.setScreen(parent)));
    }

    private void setLine(int idx, String value) {
        while (lines.size() <= idx) lines.add("");
        if (idx < lines.size()) lines.set(idx, value);
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank() && lines.size() - 1 != idx) {
            lines.remove(lines.size() - 1);
        }
    }

    private void save() {
        List<String> cleaned = new ArrayList<>();
        for (String s : lines) if (s != null && !s.isBlank()) cleaned.add(s);
        onSave.accept(cleaned);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.tooltip.title"));
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.tooltip.hint"), L + 6, T + 12, GUI_W - 12, KveGuiStyle.th(0xFF8899BB));
        super.render(g, mx, my, pt);
    }
}
