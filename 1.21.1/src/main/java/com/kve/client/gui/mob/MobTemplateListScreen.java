package com.kve.client.gui.mob;

import com.kve.client.gui.KveConfirmScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.ScrollBar;

import com.kve.common.Lang;
import com.kve.common.persist.MobTemplateStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

/**
 * Lista de plantillas de mob guardadas (MobTemplateStore) - elegir una la
 * aplica al draft actual de MobEditorScreen via el callback (mismo patron
 * que MobPickerScreen: click en la fila -> callback.accept(...) -> vuelve
 * al parent). Cada fila tiene ademas un boton "X" propio para borrar la
 * plantilla sin necesidad de aplicarla primero.
 */
@OnlyIn(Dist.CLIENT)
public class MobTemplateListScreen extends Screen {

    private static final int GUI_W = 220;
    private static final int GUI_H = 200;
    private static final int ROW_H = 18;
    private static final int LIST_Y = 24;
    private static final int VISIBLE_ROWS = 8;
    private static final int DEL_W = 16;

    private final Screen parent;
    private final Consumer<MobTemplateStore.MobTemplate> onPick;
    private List<MobTemplateStore.MobTemplate> items;
    private ScrollBar scrollBar;
    private int guiLeft, guiTop;

    public MobTemplateListScreen(Screen parent, Consumer<MobTemplateStore.MobTemplate> onPick) {
        super(Component.literal(Lang.get("gui.kve.mob.template.pick_title")));
        this.parent = parent;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        guiLeft = (width - GUI_W) / 2;
        guiTop = (height - GUI_H) / 2;
        items = MobTemplateStore.getAll();

        addRenderableWidget(new KvePlateButton(guiLeft + GUI_W / 2 - 28, guiTop + GUI_H - 22, 56, 14,
                Component.literal(Lang.get("gui.kve.btn.cancel")), b -> minecraft.setScreen(parent)));

        scrollBar = new ScrollBar(guiLeft + GUI_W - 10, guiTop + LIST_Y, VISIBLE_ROWS * ROW_H, items.size(), VISIBLE_ROWS);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    private void deleteAt(int idx) {
        if (idx < 0 || idx >= items.size()) return;
        String name = items.get(idx).name;
        minecraft.setScreen(new KveConfirmScreen(
                ok -> {
                    if (ok) MobTemplateStore.delete(name);
                    minecraft.setScreen(MobTemplateListScreen.this);
                },
                Component.literal(Lang.get("gui.kve.mob.template.delete_title")),
                Component.literal(Lang.get("gui.kve.mob.template.delete_msg") + " \"" + name + "\"")));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, guiLeft, guiTop, GUI_W, GUI_H,
                getTitle().getString() + " (" + items.size() + ")");

        if (items.isEmpty()) {
            KveGuiStyle.drawCenteredTextSafe(g, Lang.get("gui.kve.mob.template.empty"),
                    guiLeft + GUI_W / 2, guiTop + GUI_H / 2 - 4, KveGuiStyle.th(0xFF778899));
        }

        int scroll = scrollBar.getScroll();
        int rowW = GUI_W - 20 - DEL_W;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int idx = row + scroll;
            if (idx >= items.size()) break;
            int rx = guiLeft + 8;
            int ry = guiTop + LIST_Y + row * ROW_H;
            boolean hov = mx >= rx && mx < rx + rowW && my >= ry && my < ry + ROW_H - 2;
            g.fill(rx, ry, rx + rowW, ry + ROW_H - 2, hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
            KveGuiStyle.drawTextSafe(g, items.get(idx).name, rx + 4, ry + 4, rowW - 8,
                    hov ? 0xFFFFFFFF : KveGuiStyle.TEXT_PRIMARY);

            int dx = rx + rowW + 2;
            boolean hovDel = mx >= dx && mx < dx + DEL_W && my >= ry && my < ry + ROW_H - 2;
            g.fill(dx, ry, dx + DEL_W, ry + ROW_H - 2, hovDel ? 0xFF884444 : KveGuiStyle.BG_FIELD);
            KveGuiStyle.drawCenteredTextSafe(g, "x", dx + DEL_W / 2, ry + 4, hovDel ? 0xFFFFFFFF : 0xFFAA6666);
        }

        scrollBar.render(g, mx, my);
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar.mouseClicked(mx, my, btn)) return true;
        int scroll = scrollBar.getScroll();
        int rowW = GUI_W - 20 - DEL_W;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int idx = row + scroll;
            if (idx >= items.size()) break;
            int rx = guiLeft + 8;
            int ry = guiTop + LIST_Y + row * ROW_H;
            int dx = rx + rowW + 2;
            if (mx >= dx && mx < dx + DEL_W && my >= ry && my < ry + ROW_H - 2) {
                deleteAt(idx);
                return true;
            }
            if (mx >= rx && mx < rx + rowW && my >= ry && my < ry + ROW_H - 2) {
                onPick.accept(items.get(idx));
                minecraft.setScreen(parent);
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (scrollBar.mouseDragged(mx, my, btn, dx, dy)) return true;
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override public boolean mouseReleased(double mx, double my, int btn) { scrollBar.mouseReleased(mx, my, btn); return super.mouseReleased(mx, my, btn); }
    @Override public boolean mouseScrolled(double mx, double my, double scrollX, double delta) { scrollBar.mouseScrolled(delta); return true; }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
    @Override public boolean isPauseScreen() { return false; }
}
