package com.kve.client.gui;

import com.kve.common.Lang;
import com.kve.common.code.KubeJsSnippets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Paleta de snippets del editor de codigo. Lista a
 * la izquierda, vista
 * previa del codigo a la derecha; click / Enter inserta el snippet en la
 * posicion del cursor. Si el snippet es de otra carpeta de scripts que el
 * archivo abierto (p.ej. un evento de startup en server_scripts) lo avisa
 * en naranja: es el error mas comun al copiar ejemplos.
 */
@OnlyIn(Dist.CLIENT)
public class CodeSnippetScreen extends Screen {

    private static final int W = 380;
    private static final int H = 220;
    private static final int LIST_W = 150;
    private static final int ROW_H = 12;

    private final Screen parent;
    private final String activeFolder;
    private final Consumer<KubeJsSnippets.Snippet> onPick;
    private int selected = 0;
    private int scroll = 0;
    private int cx, cy;

    public CodeSnippetScreen(Screen parent, String activeFolder, Consumer<KubeJsSnippets.Snippet> onPick) {
        super(Component.literal(Lang.get("gui.kve.code.snippets.title")));
        this.parent = parent;
        this.activeFolder = activeFolder;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        cx = (width - W) / 2;
        cy = (height - H) / 2;
        int by = cy + H - KveGuiStyle.PAD - 16;
        addRenderableWidget(new KvePlateButton(cx + W - KveGuiStyle.PAD - 180, by, 88, 16,
                Component.literal(Lang.get("gui.kve.code.snippets.insert")), b -> pick()));
        addRenderableWidget(new KvePlateButton(cx + W - KveGuiStyle.PAD - 88, by, 88, 16,
                Component.literal(Lang.get("gui.kve.confirm.no")), b -> onClose()));
    }

    private int listTop() { return cy + KveGuiStyle.TITLEBAR_H + 6; }
    private int visibleRows() { return (H - KveGuiStyle.TITLEBAR_H - 6 - KveGuiStyle.PAD - 20) / ROW_H; }

    private void pick() {
        if (selected < 0 || selected >= KubeJsSnippets.ALL.size()) return;
        onPick.accept(KubeJsSnippets.ALL.get(selected));
    }

    private void clampScroll() {
        int max = Math.max(0, KubeJsSnippets.ALL.size() - visibleRows());
        if (selected < scroll) scroll = selected;
        if (selected >= scroll + visibleRows()) scroll = selected - visibleRows() + 1;
        scroll = Math.max(0, Math.min(max, scroll));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int x = cx + KveGuiStyle.PAD;
        if (mx >= x && mx < x + LIST_W && my >= listTop() && my < listTop() + visibleRows() * ROW_H) {
            int idx = scroll + (int) ((my - listTop()) / ROW_H);
            if (idx >= 0 && idx < KubeJsSnippets.ALL.size()) {
                if (idx == selected && btn == 0) { pick(); return true; }
                selected = idx;
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, KubeJsSnippets.ALL.size() - visibleRows()), scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int mods) {
        switch (key) {
            case GLFW.GLFW_KEY_UP -> { selected = Math.max(0, selected - 1); clampScroll(); return true; }
            case GLFW.GLFW_KEY_DOWN -> { selected = Math.min(KubeJsSnippets.ALL.size() - 1, selected + 1); clampScroll(); return true; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { pick(); return true; }
            default -> { return super.keyPressed(key, scancode, mods); }
        }
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, H, getTitle().getString());
        int x = cx + KveGuiStyle.PAD;
        int top = listTop();
        int rows = visibleRows();
        g.fill(x - 2, top - 2, x + LIST_W, top + rows * ROW_H + 1, KveGuiStyle.BG_FIELD);
        for (int r = 0; r < rows; r++) {
            int idx = scroll + r;
            if (idx >= KubeJsSnippets.ALL.size()) break;
            KubeJsSnippets.Snippet s = KubeJsSnippets.ALL.get(idx);
            int ry = top + r * ROW_H;
            boolean hover = mx >= x && mx < x + LIST_W && my >= ry && my < ry + ROW_H;
            if (idx == selected) g.fill(x - 2, ry - 1, x + LIST_W, ry + ROW_H - 1, 0x553D5A80);
            else if (hover) g.fill(x - 2, ry - 1, x + LIST_W, ry + ROW_H - 1, 0x22FFFFFF);
            KveGuiStyle.drawTextSafe(g, s.name(), x, ry + 1, LIST_W - 4,
                    idx == selected ? KveGuiStyle.ACCENT_GOLD : KveGuiStyle.TEXT_PRIMARY);
        }

        KubeJsSnippets.Snippet s = KubeJsSnippets.ALL.get(Math.max(0, Math.min(selected, KubeJsSnippets.ALL.size() - 1)));
        int px = x + LIST_W + 8;
        int pw = cx + W - KveGuiStyle.PAD - px;
        boolean mismatch = activeFolder != null && !activeFolder.equals(s.folder());
        String folderLine = Lang.get("gui.kve.code.snippets.folder", s.folder() + "_scripts");
        KveGuiStyle.drawTextSafe(g, folderLine, px, top, pw, mismatch ? KveGuiStyle.TEXT_WARN : KveGuiStyle.TEXT_LABEL);
        int py = top + 12;
        if (mismatch) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.code.snippets.folder_mismatch", activeFolder + "_scripts"),
                    px, py, pw, KveGuiStyle.TEXT_WARN);
            py += 12;
        }
        g.fill(px - 2, py - 2, px + pw, top + rows * ROW_H + 1, KveGuiStyle.BG_FIELD);
        for (String line : s.lines()) {
            if (py + 9 > top + rows * ROW_H) break;
            KveGuiStyle.drawTextSafe(g, line, px, py, pw - 2, KveGuiStyle.TEXT_PRIMARY);
            py += 10;
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
