package com.kve.client.gui;

import com.kve.common.Lang;
import com.kve.common.recipe.GridOps;
import com.kve.common.recipe.RecipeHelpers;
import com.kve.common.recipe.RecipeTemplates;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.UnaryOperator;

@OnlyIn(Dist.CLIENT)
public class CraftToolsScreen extends Screen {

    private static final int W = 340;
    private static final int H = 250;

    private final CraftEditorScreen editor;
    private boolean templatesPage = false;
    private int cx, cy;

    public CraftToolsScreen(CraftEditorScreen editor) {
        super(Component.literal(Lang.get("gui.kve.recipe.tools2.title")));
        this.editor = editor;
    }

    @Override
    protected void init() {
        cx = (width - W) / 2;
        cy = (height - H) / 2;
        if (templatesPage) initTemplates();
        else initTools();
        KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(cx + KveGuiStyle.PAD, cy + H - KveGuiStyle.PAD - 16, cx + W - KveGuiStyle.PAD);
        if (templatesPage) addRenderableWidget(row.add(0, Lang.get("gui.kve.recipe.tools2.back"), b -> { templatesPage = false; rebuild(); }));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.close"), b -> onClose()));
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private int colW() { return (W - KveGuiStyle.PAD * 2 - 4) / 2; }

    private void initTools() {
        int x = cx + KveGuiStyle.PAD;
        int x2 = x + colW() + 4;
        int y = cy + KveGuiStyle.TITLEBAR_H + 16;
        boolean grid = editor.gridToolsAvailable();

        tool(x, y, "templates", grid, () -> { templatesPage = true; rebuild(); });
        tool(x2, y, "trim", grid, () -> gridOp(GridOps::trimToCorner));
        y += 16;
        tool(x, y, "mirror_h", grid, () -> gridOp(GridOps::mirrorHorizontal));
        tool(x2, y, "mirror_v", grid, () -> gridOp(GridOps::mirrorVertical));
        y += 16;
        tool(x, y, "rotate", grid && editor.rotateAvailable(), () -> gridOp(GridOps::rotateClockwise));
        int q = (colW() - 6) / 4;
        String[] arrows = {"←", "→", "↑", "↓"};
        int[][] d = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int i = 0; i < 4; i++) {
            int[] dd = d[i];
            KvePlateButton b = new KvePlateButton(x2 + i * (q + 2), y, q, 14, Component.literal(arrows[i]),
                    btn -> gridOp(g -> GridOps.shift(g, dd[0], dd[1])));
            b.active = grid;
            b.setTooltip(Tooltip.create(Component.literal(Lang.get("gui.kve.recipe.tools2.shift"))));
            addRenderableWidget(b);
        }
        y += 16;
        tool(x, y, "fill_empty", grid, () -> editor.toolFill(false));
        tool(x2, y, "fill_border", grid && editor.borderAvailable(), () -> editor.toolFill(true));

        y += 30;
        tool(x, y, "reverse", editor.reverseAvailable(), () -> back(editor::toolReverse));
        tool(x2, y, "paste", true, () -> back(editor::toolPasteJson));
        y += 16;
        boolean cooking = editor.cookingAvailable();
        String[] cookTypes = RecipeHelpers.COOKING_TYPES.keySet().toArray(new String[0]);
        int cw = (colW() * 2 + 4 - 6) / 4;
        for (int i = 0; i < cookTypes.length; i++) {
            String t = cookTypes[i];
            KvePlateButton b = new KvePlateButton(x + i * (cw + 2), y, cw, 14,
                    Component.literal(Lang.get("gui.kve.recipe.tools2.cook." + t.substring(10))),
                    btn -> back(() -> editor.toolCookingVariant(t)));
            b.active = cooking && editor.cookingVariantAvailable(t);
            b.setTooltip(Tooltip.create(Component.literal(Lang.get("gui.kve.recipe.tools2.cook_tooltip"))));
            addRenderableWidget(b);
        }
        y += 16;
        tool(x, y, "copy_js", true, () -> back(editor::toolCopyJs));
        tool(x2, y, "give", editor.giveAvailable(), () -> back(editor::toolGiveIngredients));

        y += 30;
        tool(x, y, "details", true, () -> editor.toolDetails());
        tool(x2, y, "auto_id", editor.autoIdAvailable(), () -> back(editor::toolAutoId));
    }

    private void tool(int x, int y, String key, boolean active, Runnable action) {
        KvePlateButton b = new KvePlateButton(x, y, colW(), 14, Component.literal(Lang.get("gui.kve.recipe.tools2." + key)), btn -> action.run());
        b.active = active;
        b.setTooltip(Tooltip.create(Component.literal(Lang.get("gui.kve.recipe.tools2." + key + ".tip"))));
        addRenderableWidget(b);
    }

    private void gridOp(UnaryOperator<GridOps.Grid> op) {
        back(() -> editor.toolGrid(op));
    }

    private void back(Runnable action) {
        minecraft.setScreen(editor);
        action.run();
    }

    private void initTemplates() {
        int x = cx + KveGuiStyle.PAD;
        int y = cy + KveGuiStyle.TITLEBAR_H + 16;
        int cols = 3;
        int w = (W - KveGuiStyle.PAD * 2 - (cols - 1) * 3) / cols;
        int i = 0;
        for (RecipeTemplates.Template t : RecipeTemplates.ALL) {
            int bx = x + (i % cols) * (w + 3);
            int by = y + (i / cols) * 16;
            KvePlateButton b = new KvePlateButton(bx, by, w, 14, Component.literal(Lang.get("gui.kve.recipe.template." + t.id())),
                    btn -> { minecraft.setScreen(editor); editor.toolTemplate(t); });
            b.active = editor.templateFits(t);
            b.setTooltip(Tooltip.create(Component.literal(String.join("\n", t.rows()).replace(' ', '·')
                    + "\n→ " + t.outputCount())));
            addRenderableWidget(b);
            i++;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, H, getTitle().getString());
        int x = cx + KveGuiStyle.PAD;
        int y = cy + KveGuiStyle.TITLEBAR_H + 4;
        if (templatesPage) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.tools2.templates_hint"), x, y, W - 16, KveGuiStyle.TEXT_MUTED);
        } else {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.tools2.sec_grid"), x, y, W - 16, KveGuiStyle.ACCENT_GOLD);
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.tools2.sec_generate"), x, y + 4 * 16 + 14, W - 16, KveGuiStyle.ACCENT_GOLD);
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.tools2.sec_details"), x, y + 7 * 16 + 28, W - 16, KveGuiStyle.ACCENT_GOLD);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() { minecraft.setScreen(editor); }

    @Override
    public boolean isPauseScreen() { return false; }
}
