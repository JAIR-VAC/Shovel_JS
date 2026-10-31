package com.kve.client.gui.code;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;

import com.kve.common.Lang;
import com.kve.common.code.TextSearch;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@OnlyIn(Dist.CLIENT)
public class CodeFindReplaceScreen extends Screen {

    /** Lo que el editor expone al dialogo. */
    public interface Target {
        boolean find(Pattern p, boolean forward);
        int count(Pattern p);
        boolean replaceCurrent(Pattern p, String replacement, TextSearch.Options opts);
        int replaceAll(Pattern p, String replacement, TextSearch.Options opts);
    }

    private static String lastFind = "";
    private static String lastReplace = "";
    private static TextSearch.Options lastOptions = TextSearch.Options.DEFAULT;

    private static final int W = 300;
    private static final int H = 132;
    private static final int ROW_H = 16;

    private final Screen parent;
    private final Screen backdrop;
    private final Target target;
    private KveScaledEditBox findBox;
    private KveScaledEditBox replaceBox;
    private boolean matchCase, wholeWord, regex;
    private String status = "";
    private int statusColor = KveGuiStyle.TEXT_MUTED;
    private int cx, cy;

    public CodeFindReplaceScreen(Screen parent, Screen backdrop, Target target, String initialFind) {
        super(Component.literal(Lang.get("gui.kve.code.findreplace.title")));
        this.parent = parent;
        this.backdrop = backdrop;
        this.target = target;
        if (initialFind != null && !initialFind.isEmpty()) lastFind = initialFind;
        this.matchCase = lastOptions.matchCase();
        this.wholeWord = lastOptions.wholeWord();
        this.regex = lastOptions.regex();
    }

    @Override
    protected void init() {
        cx = (width - W) / 2;
        cy = (height - H) / 2;
        int x = cx + KveGuiStyle.PAD;
        int innerW = W - KveGuiStyle.PAD * 2;
        int y = cy + KveGuiStyle.TITLEBAR_H + 6;

        findBox = new KveScaledEditBox(font, x, y, innerW, 14, Component.literal(Lang.get("gui.kve.code.find_hint")));
        findBox.setMaxLength(256);
        findBox.setHint(Component.literal(Lang.get("gui.kve.code.find_hint")));
        findBox.setValue(lastFind);
        findBox.setResponder(s -> { lastFind = s; updateStatus(); });
        addRenderableWidget(findBox);
        y += ROW_H + 2;

        replaceBox = new KveScaledEditBox(font, x, y, innerW, 14, Component.literal(Lang.get("gui.kve.code.findreplace.replace_hint")));
        replaceBox.setMaxLength(256);
        replaceBox.setHint(Component.literal(Lang.get("gui.kve.code.findreplace.replace_hint")));
        replaceBox.setValue(lastReplace);
        replaceBox.setResponder(s -> lastReplace = s);
        addRenderableWidget(replaceBox);
        y += ROW_H + 4;

        int tw = 44;
        addRenderableWidget(new KvePlateButton(x, y, tw, 14, Component.literal(toggleLabel("Aa", matchCase)),
                b -> { matchCase = !matchCase; rebuild(); }));
        addRenderableWidget(new KvePlateButton(x + tw + 3, y, tw, 14, Component.literal(toggleLabel("ab", wholeWord)),
                b -> { wholeWord = !wholeWord; rebuild(); }));
        addRenderableWidget(new KvePlateButton(x + (tw + 3) * 2, y, tw, 14, Component.literal(toggleLabel(".*", regex)),
                b -> { regex = !regex; rebuild(); }));
        y += ROW_H + 6;

        int gap = 3;
        int bw = (innerW - gap * 4) / 5;
        String[] labels = {
                Lang.get("gui.kve.code.findreplace.prev"), Lang.get("gui.kve.code.findreplace.next"),
                Lang.get("gui.kve.code.findreplace.replace"), Lang.get("gui.kve.code.findreplace.replace_all"),
                Lang.get("gui.kve.code.findreplace.close")};
        Runnable[] actions = { () -> doFind(false), () -> doFind(true), this::doReplace, this::doReplaceAll, this::onClose };
        for (int i = 0; i < labels.length; i++) {
            int bx = x + i * (bw + gap);
            final Runnable a = actions[i];
            addRenderableWidget(new KvePlateButton(bx, y, bw, 16,
                    Component.literal(KveGuiStyle.truncate(font, labels[i], bw - 4)), b -> a.run()));
        }

        setFocused(findBox);
        updateStatus();
    }

    private static String toggleLabel(String name, boolean on) {
        return (on ? "■ " : "□ ") + name;
    }

    private void rebuild() {
        lastOptions = options();
        clearWidgets();
        init();
    }

    private TextSearch.Options options() {
        return new TextSearch.Options(matchCase, wholeWord, regex);
    }

    private Pattern pattern() {
        String q = findBox.getValue();
        if (q.isEmpty()) return null;
        try {
            return TextSearch.compile(q, options());
        } catch (PatternSyntaxException ex) {
            status = Lang.get("gui.kve.code.findreplace.bad_regex");
            statusColor = KveGuiStyle.TEXT_ERROR;
            return null;
        }
    }

    private void updateStatus() {
        lastOptions = options();
        status = "";
        statusColor = KveGuiStyle.TEXT_MUTED;
        Pattern p = pattern();
        if (p == null) return;
        int n = target.count(p);
        status = n == 0 ? Lang.get("gui.kve.code.findreplace.none") : Lang.get("gui.kve.code.findreplace.count", n);
        statusColor = n == 0 ? KveGuiStyle.TEXT_WARN : KveGuiStyle.TEXT_MUTED;
    }

    private void doFind(boolean forward) {
        Pattern p = pattern();
        if (p == null) return;
        if (!target.find(p, forward)) {
            status = Lang.get("gui.kve.code.findreplace.none");
            statusColor = KveGuiStyle.TEXT_WARN;
        }
    }

    private void doReplace() {
        Pattern p = pattern();
        if (p == null) return;
        if (!target.replaceCurrent(p, replaceBox.getValue(), options())) target.find(p, true);
        updateStatus();
    }

    private void doReplaceAll() {
        Pattern p = pattern();
        if (p == null) return;
        int n = target.replaceAll(p, replaceBox.getValue(), options());
        updateStatus();
        status = Lang.get("gui.kve.code.findreplace.replaced", n);
        statusColor = n == 0 ? KveGuiStyle.TEXT_WARN : KveGuiStyle.ACCENT_GOLD;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (getFocused() == replaceBox) doReplace();
            else doFind((mods & GLFW.GLFW_MOD_SHIFT) == 0);
            return true;
        }
        if (key == GLFW.GLFW_KEY_F3) { doFind((mods & GLFW.GLFW_MOD_SHIFT) == 0); return true; }
        return super.keyPressed(key, scancode, mods);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (backdrop != null && backdrop != this) {
            try {
                backdrop.render(g, -1, -1, pt);
            } catch (Exception ignored) {
                renderTransparentBackground(g);
            }
            g.fill(0, 0, width, height, 0x66000000);
        } else {
            renderTransparentBackground(g);
        }
        KveGuiStyle.drawWindow(g, cx, cy, W, H, getTitle().getString());
        int sy = cy + KveGuiStyle.TITLEBAR_H + 6 + (ROW_H + 2) + (ROW_H + 4) + 3;
        int sx = cx + KveGuiStyle.PAD + (44 + 3) * 3 + 6;
        KveGuiStyle.drawTextSafe(g, status, sx, sy, cx + W - KveGuiStyle.PAD - sx, statusColor);
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
