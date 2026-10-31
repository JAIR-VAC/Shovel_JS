package com.kve.client.gui;

import com.kve.common.Lang;
import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.recipe.RecipeData;
import com.kve.common.recipe.UncraftableAudit;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * lista los items de un mod que NINGUNA receta cargada
 * produce (ver {@link UncraftableAudit}). Los mods se recorren con &lt; &gt;
 * ordenados por cuantos items sin receta tienen (el mas "roto" primero).
 * Clic en un item abre el editor de recetas con ese item ya como resultado;
 * "Copiar lista" deja los ids en el portapapeles para pasarlos a un issue o
 * a la documentacion del pack. Las recetas quitadas con KVE (aunque sigan
 * cargadas hasta el proximo /reload) ya cuentan como quitadas.
 */
@OnlyIn(Dist.CLIENT)
public class UncraftableItemsScreen extends Screen {

    private static final int W = 380;
    private static final int H = 262;
    private static final int ROW_H = 12;
    private static final int ROWS = 14;

    private final Screen parent;
    private final List<String> registered = new ArrayList<>();
    private final Set<String> produced = new HashSet<>();
    private final List<String> mods = new ArrayList<>();
    private Map<String, int[]> summary = Map.of();
    private int modIdx = 0;
    private boolean skipTechnical = true;
    private final List<String> results = new ArrayList<>();
    private int scroll = 0;
    private boolean inWorld = false;
    private String flash = "";
    private int cx, cy;

    public UncraftableItemsScreen(Screen parent) {
        super(Component.literal(Lang.get("gui.kve.recipe.uncraftable.title")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        cx = (width - W) / 2;
        cy = (height - H) / 2;
        if (registered.isEmpty()) scan();
        int x = cx + KveGuiStyle.PAD;
        int y = cy + KveGuiStyle.TITLEBAR_H + 4;
        addRenderableWidget(new KvePlateButton(x, y, 16, 14, Component.literal("<"), b -> { step(-1); }));
        addRenderableWidget(new KvePlateButton(x + 190, y, 16, 14, Component.literal(">"), b -> { step(1); }));
        String techLabel = Lang.get(skipTechnical ? "gui.kve.recipe.uncraftable.tech_hidden" : "gui.kve.recipe.uncraftable.tech_shown");
        addRenderableWidget(new KvePlateButton(x + 212, y, W - KveGuiStyle.PAD * 2 - 212, 14, Component.literal(techLabel), b -> {
            skipTechnical = !skipTechnical;
            rebuildSummary();
            clearWidgets();
            init();
        }));
        int by = cy + H - KveGuiStyle.PAD - 16;
        KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(x, by, cx + W - KveGuiStyle.PAD);
        addRenderableWidget(row.add(0, Lang.get("gui.kve.recipe.uncraftable.copy"), b -> copyList()));
        // lo tipico con items que no se pueden fabricar es sacarlos de JEI/REI.
        addRenderableWidget(row.add(0, Lang.get("gui.kve.recipe.uncraftable.hide_all"), b -> hideAllInViewers()));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.close"), b -> onClose()));
    }

    private void scan() {
        registered.clear();
        produced.clear();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) registered.add(id.toString());
        if (minecraft != null && minecraft.getConnection() != null) {
            inWorld = true;
            RecipeManager mgr = minecraft.getConnection().getRecipeManager();
            RegistryAccess ra = minecraft.getConnection().registryAccess();
            List<String> removed = KubeJSExporter.getRemovedRecipes();
            for (Recipe<?> r : mgr.getRecipes()) {
                if (r.getId() != null && removed.contains(r.getId().toString())) continue;
                try {
                    ItemStack out = r.getResultItem(ra);
                    if (out != null && !out.isEmpty()) {
                        ResourceLocation id = BuiltInRegistries.ITEM.getKey(out.getItem());
                        if (id != null) produced.add(id.toString());
                    }
                } catch (Throwable ignored) {}
            }
        }
        rebuildSummary();
    }

    private void rebuildSummary() {
        String current = mods.isEmpty() ? null : mods.get(modIdx);
        summary = UncraftableAudit.summaryByMod(registered, produced, skipTechnical);
        mods.clear();
        for (Map.Entry<String, int[]> e : summary.entrySet()) if (e.getValue()[0] > 0) mods.add(e.getKey());
        modIdx = current != null && mods.contains(current) ? mods.indexOf(current) : 0;
        refreshResults();
    }

    private void step(int d) {
        if (mods.isEmpty()) return;
        modIdx = Math.floorMod(modIdx + d, mods.size());
        refreshResults();
    }

    private void refreshResults() {
        results.clear();
        scroll = 0;
        if (!inWorld || mods.isEmpty()) return;
        results.addAll(UncraftableAudit.find(registered, produced, mods.get(modIdx), skipTechnical));
    }

    private void copyList() {
        if (results.isEmpty()) return;
        minecraft.keyboardHandler.setClipboard(String.join("\n", results));
        flash = Lang.get("gui.kve.recipe.uncraftable.copied", results.size());
    }

    private void hideAllInViewers() {
        if (results.isEmpty()) return;
        List<String> ids = new ArrayList<>(results);
        minecraft.setScreen(new KveConfirmScreen(ok -> {
            minecraft.setScreen(this);
            if (!ok) return;
            List<String> problems = BulkItemQueue.queue(ids, com.kve.common.itemrules.BulkItemEdit.hideFromViewers(), () -> {});
            flash = problems.isEmpty() ? Lang.get("gui.kve.recipe.uncraftable.hidden", ids.size()) : String.join(" · ", problems);
        }, Component.literal(Lang.get("gui.kve.recipe.uncraftable.hide_all")),
                Component.literal(Lang.get("gui.kve.recipe.uncraftable.hide_confirm", ids.size()))));
    }

    private void openEditorFor(String id) {
        ItemStack st = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(id)));
        RecipeData d = new RecipeData();
        d.type = RecipeData.RecipeType.VANILLA_SHAPED;
        for (int i = 0; i < d.gridWidth * d.gridHeight; i++) d.ingredients.add(ItemStack.EMPTY);
        d.output = st;
        com.kve.client.gui.studio.KveStudioShell.openRecipe(this, d, null);
    }

    private int listTop() { return cy + KveGuiStyle.TITLEBAR_H + 4 + 18 + 24; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, H, getTitle().getString());
        int x = cx + KveGuiStyle.PAD;
        int innerW = W - KveGuiStyle.PAD * 2;
        int y = cy + KveGuiStyle.TITLEBAR_H + 7;
        String modLabel = mods.isEmpty() ? "-" : mods.get(modIdx) + "  (" + (modIdx + 1) + "/" + mods.size() + ")";
        KveGuiStyle.drawCenteredTextSafe(g, modLabel, x + 103, y, KveGuiStyle.ACCENT_GOLD);
        y += 18;
        String line;
        int color = KveGuiStyle.TEXT_LABEL;
        if (!inWorld) { line = Lang.get("gui.kve.recipe.conflicts.no_world"); color = KveGuiStyle.TEXT_WARN; }
        else if (mods.isEmpty()) { line = Lang.get("gui.kve.recipe.uncraftable.all_ok"); color = KveGuiStyle.TEXT_OK; }
        else {
            int[] c = summary.get(mods.get(modIdx));
            line = Lang.get("gui.kve.recipe.uncraftable.summary", c[0], c[1], mods.get(modIdx));
        }
        KveGuiStyle.drawTextSafe(g, line, x, y, innerW, color);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.uncraftable.note"), x, y + 11, innerW, KveGuiStyle.TEXT_MUTED);

        int ly = listTop();
        g.fill(x - 2, ly - 1, x + innerW, ly + ROWS * ROW_H + 1, KveGuiStyle.BG_FIELD);
        for (int i = 0; i < ROWS; i++) {
            int idx = i + scroll;
            if (idx >= results.size()) break;
            String id = results.get(idx);
            int ry = ly + i * ROW_H;
            boolean hov = mx >= x && mx < x + innerW && my >= ry && my < ry + ROW_H;
            if (hov) g.fill(x - 2, ry, x + innerW, ry + ROW_H, 0x22FFFFFF);
            ItemStack st = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(id)));
            g.pose().pushPose();
            g.pose().translate(x, ry + 1, 0);
            g.pose().scale(0.625f, 0.625f, 1f);
            SafeItemRender.render(g, st, 0, 0);
            g.pose().popPose();
            KveGuiStyle.drawTextSafe(g, st.getHoverName().getString() + "  §8" + id, x + 14, ry + 2, innerW - 16, KveGuiStyle.TEXT_PRIMARY);
        }
        if (results.size() > ROWS) {
            KveGuiStyle.drawTextSafe(g, (scroll + 1) + "-" + Math.min(results.size(), scroll + ROWS) + " / " + results.size(),
                    x, ly + ROWS * ROW_H + 4, innerW, KveGuiStyle.TEXT_MUTED);
        }
        if (!flash.isEmpty()) KveGuiStyle.drawTextSafe(g, flash, x + 150, ly + ROWS * ROW_H + 4, innerW - 150, KveGuiStyle.TEXT_OK);
        super.render(g, mx, my, pt);
        int hovIdx = rowAt(mx, my);
        if (hovIdx >= 0) g.renderTooltip(font, Component.literal(Lang.get("gui.kve.recipe.uncraftable.click_hint")), mx, my);
    }

    private int rowAt(double mx, double my) {
        int x = cx + KveGuiStyle.PAD;
        int innerW = W - KveGuiStyle.PAD * 2;
        int ly = listTop();
        if (mx < x || mx >= x + innerW || my < ly || my >= ly + ROWS * ROW_H) return -1;
        int idx = (int) ((my - ly) / ROW_H) + scroll;
        return idx < results.size() ? idx : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int idx = rowAt(mx, my);
        if (idx >= 0 && btn == 0) { openEditorFor(results.get(idx)); return true; }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, results.size() - ROWS), scroll + (delta > 0 ? -3 : 3)));
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
