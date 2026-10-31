package com.kve.client.gui;

import com.kve.common.Lang;
import com.kve.client.gui.studio.KveStudioTab;
import com.kve.client.permission.ClientPermissionState;
import com.kve.common.customitem.CustomBlockDraft;
import com.kve.common.customitem.CustomItemDraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CreateTabScreen extends Screen implements KveStudioTab {

    private enum Mode { CHOOSER, ITEM_LIST, ITEM_EDITOR, BLOCK_LIST, BLOCK_EDITOR, FLUID }

    private static final int CARD_W = 150, CARD_GAP = 16;

    private Mode mode = Mode.CHOOSER;
    private int x0, y0, w, h;

    private Screen ownerTab = null;
    private Screen returnScreen = null;

    private CustomItemCreatorListScreen itemList;
    private CustomItemCreatorEditorScreen itemEditor;
    private CustomBlockCreatorListScreen blockList;
    private CustomBlockCreatorEditorScreen blockEditor;
    private CustomFluidCreatorScreen fluidScreen;

    public CreateTabScreen() {
        super(Lang.c("gui.kve.create.tab"));
    }

    /** La Screen registrada en el shell (PropertiesTabScreen) - los pickers de los editores vuelven ahi, igual que ItemAttributeScreen#withOwnerTab(). */
    public CreateTabScreen withOwnerTab(Screen s) { this.ownerTab = s; return this; }

    public CreateTabScreen withReturnScreen(Screen s) { this.returnScreen = s; return this; }

    private Screen owner() { return ownerTab != null ? ownerTab : this; }

    @Override
    public void init(int x0, int y0, int w, int h) {
        if (this.minecraft == null) {
            this.minecraft = Minecraft.getInstance();
            this.font = this.minecraft.font;
        }
        this.x0 = x0; this.y0 = y0; this.w = w; this.h = h;
        layout();
    }

    private void layout() {
        clearWidgets();
        setFocused(null);
        Screen child = activeChild();
        if (child instanceof KveStudioTab tab) {
            tab.init(x0, y0, w, h);
            return;
        }
        boolean canEdit = ClientPermissionState.canEdit();
        boolean sideBySide = sideBySide();
        int cy = y0 + Math.max(40, h / 3);
        int[] item = cardPos(0, cy), block = cardPos(1, cy), fluid = cardPos(2, cy);
        int itemX = item[0], itemY = item[1], blockX = block[0], blockY = block[1];
        Button itemBtn = addRenderableWidget(new KvePlateButton(itemX, itemY, CARD_W, 20,
                Component.literal(Lang.get("gui.kve.create.item")), b -> openItemList()));
        itemBtn.active = canEdit;
        Button blockBtn = addRenderableWidget(new KvePlateButton(blockX, blockY, CARD_W, 20,
                Component.literal(Lang.get("gui.kve.create.block")), b -> openBlockList()));
        blockBtn.active = canEdit;
        Button fluidBtn = addRenderableWidget(new KvePlateButton(fluid[0], fluid[1], CARD_W, 20,
                Component.literal(Lang.get("gui.kve.create.fluid")), b -> openFluids()));
        fluidBtn.active = canEdit;
    }

    private boolean sideBySide() { return w >= CARD_W * 3 + CARD_GAP * 2 + 8; }

    /** Posicion de la tarjeta {@code i} (item, bloque, fluido): en fila si entran, si no una debajo de otra. */
    private int[] cardPos(int i, int cy) {
        if (sideBySide()) {
            int left = x0 + (w - (CARD_W * 3 + CARD_GAP * 2)) / 2;
            return new int[]{left + i * (CARD_W + CARD_GAP), cy};
        }
        return new int[]{x0 + (w - CARD_W) / 2, cy + i * 44};
    }

    private void openFluids() {
        if (fluidScreen == null) {
            fluidScreen = new CustomFluidCreatorScreen(this::backToChooser);
            fluidScreen.withOwnerTab(owner());
            fluidScreen.withReturnScreen(returnScreen);
        }
        mode = Mode.FLUID;
        layout();
    }

    private void openItemList() {
        if (itemList == null) itemList = new CustomItemCreatorListScreen(this::backToChooser, this::openItemEditor);
        mode = Mode.ITEM_LIST;
        layout();
    }

    private void openItemEditor(CustomItemDraft draftOrNull) {
        itemEditor = new CustomItemCreatorEditorScreen(this::backToItemList, draftOrNull);
        itemEditor.withOwnerTab(owner());
        itemEditor.withReturnScreen(returnScreen);
        mode = Mode.ITEM_EDITOR;
        layout();
    }

    private void backToItemList() {
        mode = Mode.ITEM_LIST;
        layout();
        itemList.refreshOnReturn();
    }

    private void openBlockList() {
        if (blockList == null) blockList = new CustomBlockCreatorListScreen(this::backToChooser, this::openBlockEditor);
        mode = Mode.BLOCK_LIST;
        layout();
    }

    private void openBlockEditor(CustomBlockDraft draftOrNull) {
        blockEditor = new CustomBlockCreatorEditorScreen(this::backToBlockList, draftOrNull);
        blockEditor.withOwnerTab(owner());
        blockEditor.withReturnScreen(returnScreen);
        mode = Mode.BLOCK_EDITOR;
        layout();
    }

    private void backToBlockList() {
        mode = Mode.BLOCK_LIST;
        layout();
        blockList.refreshOnReturn();
    }

    private void backToChooser() {
        mode = Mode.CHOOSER;
        layout();
    }

    private Screen activeChild() {
        return switch (mode) {
            case ITEM_LIST -> itemList;
            case ITEM_EDITOR -> itemEditor;
            case BLOCK_LIST -> blockList;
            case BLOCK_EDITOR -> blockEditor;
            case FLUID -> fluidScreen;
            case CHOOSER -> null;
        };
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Screen child = activeChild();
        if (child != null) { child.render(g, mx, my, pt); return; }

        int cx = x0 + w / 2;
        int cy = y0 + Math.max(40, h / 3);
        KveGuiStyle.drawCenteredTextSafe(g, Lang.get("gui.kve.create.title"), cx, cy - 22, KveGuiStyle.th(0xFFE8C96A));
        super.render(g, mx, my, pt);

        boolean sideBySide = sideBySide();
        String[] desc = {"gui.kve.create.item_desc", "gui.kve.create.block_desc", "gui.kve.create.fluid_desc"};
        for (int i = 0; i < desc.length; i++) {
            int[] p = cardPos(i, cy);
            drawDescription(g, desc[i], p[0], p[1] + (sideBySide ? 24 : 22));
        }
        if (!ClientPermissionState.canEdit()) {
            KveGuiStyle.drawCenteredTextSafe(g, Lang.get("gui.kve.perm.denied"), cx, cy + (sideBySide ? 56 : 144), KveGuiStyle.TEXT_WARN);
        }
    }

    private void drawDescription(GuiGraphics g, String key, int x, int y) {
        var lines = KveGuiStyle.wrapTextSafe(font, Lang.get(key), CARD_W);
        for (int i = 0; i < lines.size() && i < 2; i++) {
            KveGuiStyle.drawTextSafe(g, lines.get(i), x, y + i * 10, CARD_W, KveGuiStyle.TEXT_MUTED);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Screen child = activeChild();
        if (child != null) return child.mouseClicked(mx, my, button);
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        Screen child = activeChild();
        if (child != null) return child.mouseDragged(mx, my, button, dx, dy);
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        Screen child = activeChild();
        boolean own = super.mouseReleased(mx, my, button);
        return (child != null && child.mouseReleased(mx, my, button)) || own;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        Screen child = activeChild();
        if (child != null) return child.mouseScrolled(mx, my, delta);
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        Screen child = activeChild();
        if (child != null) return child.keyPressed(key, scan, mods);
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        Screen child = activeChild();
        if (child != null) return child.charTyped(codePoint, modifiers);
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
