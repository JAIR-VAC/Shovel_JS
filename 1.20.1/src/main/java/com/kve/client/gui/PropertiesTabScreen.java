package com.kve.client.gui;

import com.kve.common.Lang;
import com.kve.client.gui.studio.KveStudioShell;
import com.kve.client.gui.studio.KveStudioTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PropertiesTabScreen extends Screen implements KveStudioTab {

    private static final int TOOLBAR_H_MIN = 16;
    private static final int TOGGLE_EXTRA_PAD = 20;
    private static final int GUI_W_MIN = 500;
    private static final int GUI_H_MIN = 360 + TOOLBAR_H_MIN;

    private final ItemAttributeScreen itemsScreen;
    private final BlockAttributeScreen blocksScreen;
    private final CreateTabScreen createScreen;

    private enum SubTab { ITEMS, BLOCKS, CREATE }

    private SubTab subTab = SubTab.ITEMS;

    private Screen returnScreen = null;
    private boolean hosted = false;

    private int hostedX, hostedY, hostedW, hostedH;

    public PropertiesTabScreen() {
        super(Lang.c("gui.kve.studio.tab.properties"));
        itemsScreen = new ItemAttributeScreen();
        blocksScreen = new BlockAttributeScreen();
        createScreen = new CreateTabScreen();
    }

    public PropertiesTabScreen withReturnScreen(Screen s) {
        this.returnScreen = s;
        return this;
    }

    @Override
    protected void init() {
        if (KveStudioShell.reclaimIfHosted(this, returnScreen)) return;
        hosted = false;
        int w = Math.max(width, GUI_W_MIN);
        int h = Math.max(height, GUI_H_MIN);
        int x0 = Math.max(0, (width - GUI_W_MIN) / 2);
        int y0 = Math.max(0, (height - GUI_H_MIN) / 2);
        layout(x0, y0, GUI_W_MIN, GUI_H_MIN);
    }

    @Override
    public void init(int x0, int y0, int w, int h) {
        if (this.minecraft == null) {
            this.minecraft = Minecraft.getInstance();
            this.font = this.minecraft.font;
        }
        hosted = true;
        layout(x0, y0, w, h);
    }

    private void refresh() {
        layout(hostedX, hostedY, hostedW, hostedH);
    }

    private void layout(int x0, int y0, int w, int h) {
        hostedX = x0; hostedY = y0; hostedW = w; hostedH = h;
        clearWidgets();
        setFocused(null);

        itemsScreen.withReturnScreen(returnScreen);
        blocksScreen.withReturnScreen(returnScreen);
        itemsScreen.withOwnerTab(this);
        blocksScreen.withOwnerTab(this);
        createScreen.withOwnerTab(this);
        createScreen.withReturnScreen(returnScreen);

        int tx = x0 + 2;
        int maxX = x0 + w - 2;
        int by = y0 + 2;
        int bx = tx;
        int rowBottom = by + KveGuiStyle.BTN_H;
        String[] labels = {
                Lang.get("gui.kve.main.items"),
                Lang.get("gui.kve.main.blocks"),
                Lang.get("gui.kve.create.tab")
        };
        SubTab[] tabs = SubTab.values();
        for (int i = 0; i < tabs.length; i++) {
            int bw = KveGuiStyle.btnWidth(labels[i]) + TOGGLE_EXTRA_PAD;
            if (i > 0 && bx + bw > maxX) {
                bx = tx;
                by += KveGuiStyle.BTN_H + KveGuiStyle.BTN_GAP;
            }
            SubTab target = tabs[i];
            Button tabBtn = addRenderableWidget(new KvePlateButton(bx, by, bw, KveGuiStyle.BTN_H,
                    Component.literal(labels[i]), b -> switchTo(target)));
            tabBtn.active = subTab != target;
            bx += bw + KveGuiStyle.BTN_GAP;
            rowBottom = Math.max(rowBottom, by + KveGuiStyle.BTN_H);
        }

        int childY = Math.max(y0 + TOOLBAR_H_MIN, rowBottom + 2);
        int childH = Math.max(1, h - (childY - y0));
        switch (subTab) {
            case ITEMS -> itemsScreen.init(x0, childY, w, childH);
            case BLOCKS -> blocksScreen.init(x0, childY, w, childH);
            case CREATE -> createScreen.init(x0, childY, w, childH);
        }
    }

    /** Ver {@link ItemAttributeScreen#selectEntryById()} - llamado por {@code KveStudioShell#jumpToTab} con el id de item a abrir. */
    public void selectItemById(String itemId) {
        if (subTab != SubTab.ITEMS) {
            subTab = SubTab.ITEMS;
            refresh();
        }
        itemsScreen.selectEntryById(itemId);
    }

    private void switchTo(SubTab target) {
        if (subTab == target) return;
        subTab = target;
        refresh();
    }

    private Screen active() {
        return switch (subTab) {
            case ITEMS -> itemsScreen;
            case BLOCKS -> blocksScreen;
            case CREATE -> createScreen;
        };
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!hosted) renderBackground(g);
        super.render(g, mx, my, pt);
        active().render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        return active().mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (super.mouseDragged(mx, my, button, dx, dy)) return true;
        return active().mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean own = super.mouseReleased(mx, my, button);
        boolean child = active().mouseReleased(mx, my, button);
        return own || child;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (super.mouseScrolled(mx, my, delta)) return true;
        return active().mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (active().keyPressed(key, scan, mods)) return true;
        if (!EditorKeys.swallow(mods)) return false;
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (active().charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Button btn(String label, int x, int y, int w, Runnable action) {
        return KveGuiStyle.smartBtn(x, y, w, label, b -> action.run());
    }
}
