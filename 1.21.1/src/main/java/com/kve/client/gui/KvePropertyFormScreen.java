package com.kve.client.gui;

import com.kve.common.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

@OnlyIn(Dist.CLIENT)
public class KvePropertyFormScreen extends Screen {

    public enum Kind { TEXT, CYCLE }

    /** Una fila. {@code options}/{@code optionLabels()} solo para CYCLE; {@code validator} (texto -> error o null) solo para TEXT. */
    public record Field(String key, String label, String desc, Kind kind, String value,
                        List<String> options, List<String> optionLabels, Function<String, String> validator) {
        public static Field text(String key, String label, String desc, String value, Function<String, String> validator) {
            return new Field(key, label, desc, Kind.TEXT, value == null ? "" : value, List.of(), List.of(), validator);
        }
        public static Field cycle(String key, String label, String desc, String value, List<String> options, List<String> labels) {
            return new Field(key, label, desc, Kind.CYCLE, value == null ? options.get(0) : value, options, labels, null);
        }
        /** Toggle de 3 estados: "" = sin cambio, "true", "false". */
        public static Field toggle(String key, String label, String desc, String value) {
            return cycle(key, label, desc, value == null ? "" : value, List.of("", "true", "false"),
                    List.of(Lang.get("gui.kve.form.unchanged"), Lang.get("gui.kve.form.yes"), Lang.get("gui.kve.form.no")));
        }
    }

    private static final int W = 300;
    private static final int ROW_H = 18;
    private static final int LABEL_W = 150;

    private final Screen parent;
    private final List<Field> fields;
    private final Consumer<Map<String, String>> onSave;
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, KveScaledEditBox> boxes = new LinkedHashMap<>();
    private final List<String> hintLines;
    private String error = "";
    private int cx, cy, h;

    public KvePropertyFormScreen(Screen parent, String title, List<String> hintLines, List<Field> fields, Consumer<Map<String, String>> onSave) {
        super(Component.literal(title));
        this.parent = parent;
        this.fields = new ArrayList<>(fields);
        this.onSave = onSave;
        this.hintLines = hintLines == null ? List.of() : hintLines;
        for (Field f : fields) values.put(f.key(), f.value());
    }

    private int listTop() { return cy + KveGuiStyle.TITLEBAR_H + 6 + hintLines.size() * 10; }

    @Override
    protected void init() {
        h = KveGuiStyle.TITLEBAR_H + 6 + hintLines.size() * 10 + fields.size() * ROW_H + 40;
        cx = (width - W) / 2;
        cy = Math.max(4, (height - h) / 2);
        clearWidgets();
        boxes.clear();
        int y = listTop();
        int wx = cx + KveGuiStyle.PAD + LABEL_W;
        int ww = W - KveGuiStyle.PAD * 2 - LABEL_W;
        for (Field f : fields) {
            if (f.kind() == Kind.TEXT) {
                KveScaledEditBox box = new KveScaledEditBox(font, wx, y + 2, ww, 13, Component.literal(f.label()));
                box.setMaxLength(32);
                box.setValue(values.get(f.key()));
                box.setHint(Component.literal(Lang.get("gui.kve.form.unchanged")));
                box.setResponder(s -> { values.put(f.key(), s.trim()); error = ""; });
                addRenderableWidget(box);
                boxes.put(f.key(), box);
            } else {
                addRenderableWidget(new KvePlateButton(wx, y + 1, ww, 15, Component.literal(cycleLabel(f)), b -> {
                    int i = f.options().indexOf(values.get(f.key()));
                    values.put(f.key(), f.options().get((i + 1) % f.options().size()));
                    init();
                }));
            }
            y += ROW_H;
        }
        int by = cy + h - KveGuiStyle.PAD - 16;
        int bw = (W - KveGuiStyle.PAD * 3) / 2;
        addRenderableWidget(new KvePlateButton(cx + KveGuiStyle.PAD, by, bw, 16, Component.literal(Lang.get("gui.kve.btn.save")), b -> trySave()).withPrimary(true));
        addRenderableWidget(new KvePlateButton(cx + KveGuiStyle.PAD * 2 + bw, by, bw, 16, Component.literal(Lang.get("gui.kve.btn.cancel")), b -> onClose()));
    }

    private String cycleLabel(Field f) {
        int i = Math.max(0, f.options().indexOf(values.get(f.key())));
        return i < f.optionLabels().size() ? f.optionLabels().get(i) : values.get(f.key());
    }

    private void trySave() {
        for (Field f : fields) {
            if (f.kind() != Kind.TEXT || f.validator() == null) continue;
            String v = values.get(f.key());
            if (v == null || v.isBlank()) continue;
            String err = f.validator().apply(v);
            if (err != null) { error = f.label() + ": " + err; return; }
        }
        onSave.accept(new LinkedHashMap<>(values));
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { trySave(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, h, getTitle().getString());
        int hy = cy + KveGuiStyle.TITLEBAR_H + 4;
        for (String line : hintLines) {
            KveGuiStyle.drawTextSafe(g, line, cx + KveGuiStyle.PAD, hy, W - KveGuiStyle.PAD * 2, 0xFF8899AA);
            hy += 10;
        }
        String hover = null;
        int y = listTop();
        for (Field f : fields) {
            boolean hov = mx >= cx + KveGuiStyle.PAD && mx < cx + KveGuiStyle.PAD + LABEL_W && my >= y && my < y + ROW_H;
            KveGuiStyle.drawTextSafe(g, f.label() + (f.desc() == null || f.desc().isBlank() ? "" : " (?)"),
                    cx + KveGuiStyle.PAD, y + 5, LABEL_W - 4, hov ? 0xFFFFFFFF : KveGuiStyle.th(0xFF7BC8E0));
            if (hov && f.desc() != null && !f.desc().isBlank()) hover = f.desc();
            y += ROW_H;
        }
        if (!error.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, error, cx + KveGuiStyle.PAD, cy + h - KveGuiStyle.PAD - 28, W - KveGuiStyle.PAD * 2, KveGuiStyle.TEXT_ERROR);
        }
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
        if (hover != null) KveGuiStyle.drawFramedTooltip(g, hover, mx, my, width, height);
    }

    @Override public boolean isPauseScreen() { return false; }

    /** Numero decimal en [min,max], o mensaje de error. */
    public static Function<String, String> decimal(double min, double max) {
        return s -> {
            try {
                double d = Double.parseDouble(s.replace(',', '.'));
                if (Double.isNaN(d) || d < min || d > max) return Lang.get("gui.kve.form.range", fmt(min), fmt(max));
                return null;
            } catch (NumberFormatException ex) { return Lang.get("gui.kve.form.not_number"); }
        };
    }

    /** Entero en [min,max], o mensaje de error. */
    public static Function<String, String> integer(int min, int max) {
        return s -> {
            try {
                int v = Integer.parseInt(s.trim());
                return v < min || v > max ? Lang.get("gui.kve.form.range", min, max) : null;
            } catch (NumberFormatException ex) { return Lang.get("gui.kve.form.not_integer"); }
        };
    }

    public static String fmt(double v) {
        return v == Math.floor(v) && !Double.isInfinite(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
