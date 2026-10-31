package com.kve.client.gui;

import com.kve.common.kubejs.LootTableExporter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Read-only simulator for a {@link LootTableExporter.LootTableEntry}
 * snapshot (may be unsaved). Runs N rolls in Java using
 * {@link LootTableExporter#simulate} - the exact same weight/condition
 * logic the real export uses - and shows how often each item actually
 * came out, plus how often nothing dropped at all. Never touches the
 * exporter's saved entries or the real exported files.
 */
@OnlyIn(Dist.CLIENT)
public class LootSimulatorScreen extends Screen {

    private static final int GUI_W = 280;
    private static final int GUI_H = 246;
    private static final int ROWS_VISIBLE = 8;
    private static final int ROW_H = 16;

    private final Screen parent;
    private final LootTableExporter.LootTableEntry entry;

    private int L, T;
    private int lootingLevel = 0;
    private LootTableExporter.SimResult result = null;
    private final List<Map.Entry<String, Integer>> sortedRows = new ArrayList<>();
    private ScrollBar scrollBar;

    public LootSimulatorScreen(Screen parent, LootTableExporter.LootTableEntry entry) {
        super(Component.literal("Simulador de Loot"));
        this.parent = parent;
        this.entry = entry;
    }

    private boolean anyPoolUsesLooting() {
        for (LootTableExporter.LootPoolData p : entry.pools) {
            if (p.condition == LootTableExporter.Condition.RANDOM_CHANCE_WITH_LOOTING) return true;
        }
        return false;
    }

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        int by = T + 18;
        // §7.5: migrado de cursor manual (simCx +=
        // btnWidth()+BTN_GAP) a KveGuiStyle.ButtonRow - fila real de 3-4
        // botones de texto (10/100/1000/Looting: N), candidata de riesgo
        // real porque "Looting: N" crece si lootingLevel llega a 2+
        // digitos en el futuro. maxX = L+GUI_W-6, mismo margen derecho
        // que ya usaba el resto de la pantalla.
        KveGuiStyle.ButtonRow simRow = new KveGuiStyle.ButtonRow(L + 6, by, L + GUI_W - 6);
        addRenderableWidget(simRow.add(0, "10", b -> runSim(10)));
        addRenderableWidget(simRow.add(0, "100", b -> runSim(100)));
        addRenderableWidget(simRow.add(0, "1000", b -> runSim(1000)));

        if (anyPoolUsesLooting()) {
            String lootingLabel = "Looting: " + lootingLevel;
            addRenderableWidget(simRow.add(0, lootingLabel, b -> { lootingLevel = (lootingLevel + 1) % 4; init(); }));
        }

        int scrollY = Math.max(T + 46, simRow.bottomY() + 2);
        scrollBar = new ScrollBar(L + GUI_W - 10, scrollY, ROWS_VISIBLE * ROW_H, 0, ROWS_VISIBLE);
        if (result != null) scrollBar.setTotals(sortedRows.size(), ROWS_VISIBLE);

        addRenderableWidget(btn("<- Volver", L + 6, T + GUI_H - 18, 0, this::onClose));
    }

    private void runSim(int rolls) {
        result = LootTableExporter.simulate(entry, rolls, lootingLevel);
        sortedRows.clear();
        sortedRows.addAll(result.itemDrops.entrySet());
        sortedRows.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        if (scrollBar != null) scrollBar.setTotals(sortedRows.size(), ROWS_VISIBLE);
    }

    private String shortId(String id) {
        return id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, "Simulador de Loot - " + shortId(entry.targetId));

        int listTop = T + 46;

        if (result == null) {
            KveGuiStyle.drawTextSafe(g, "Elige cuantas tiradas simular.", L + 6, listTop, GUI_W - 12, KveGuiStyle.TEXT_LABEL);
        } else {
            int scroll = scrollBar.getScroll();
            int vis = Math.min(ROWS_VISIBLE, sortedRows.size() - scroll);
            for (int i = 0; i < vis; i++) {
                Map.Entry<String, Integer> row = sortedRows.get(i + scroll);
                int count = row.getValue();
                double pct = 100.0 * count / result.totalRolls;
                int ry = listTop + i * ROW_H;

                String name = shortId(row.getKey());
                KveGuiStyle.drawTextSafe(g, name, L + 6, ry, 110, KveGuiStyle.TEXT_PRIMARY);
                String stat = count + " (" + trimPct(pct) + "%)";
                KveGuiStyle.drawTextSafe(g, stat, L + 118, ry, 60, KveGuiStyle.TEXT_OK);

                int barX = L + 180, barW = 84, barH = 8;
                g.fill(barX, ry + 1, barX + barW, ry + 1 + barH, KveGuiStyle.BG_FIELD);
                int fillW = (int) Math.round(barW * Math.min(1.0, pct / 100.0));
                if (fillW > 0) g.fill(barX, ry + 1, barX + fillW, ry + 1 + barH, 0xFF66DD99);
                g.hLine(barX, barX + barW - 1, ry + 1, KveGuiStyle.BORDER_FIELD);
                g.hLine(barX, barX + barW - 1, ry + 1 + barH, KveGuiStyle.BORDER_FIELD);
            }
            if (sortedRows.isEmpty()) {
                KveGuiStyle.drawTextSafe(g, "Ningun item cayo en estas tiradas.", L + 6, listTop, GUI_W - 12, KveGuiStyle.TEXT_MUTED);
            }
            scrollBar.render(g);

            double noDropPct = 100.0 * result.noDropRolls / result.totalRolls;
            KveGuiStyle.drawTextSafe(g, "Sin drop: " + result.noDropRolls + " (" + trimPct(noDropPct) + "%) de "
                    + result.totalRolls + " tiradas", L + 6, T + GUI_H - 32, GUI_W - 12, KveGuiStyle.TEXT_WARN);
        }

        super.render(g, mx, my, pt);
    }

    private String trimPct(double pct) {
        return String.format(java.util.Locale.ROOT, "%.1f", pct);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar != null && scrollBar.mouseClicked(mx, my, btn)) return true;
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (scrollBar != null && scrollBar.mouseDragged(mx, my, b, dx, dy)) return true;
        return super.mouseDragged(mx, my, b, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int b) {
        if (scrollBar != null) scrollBar.mouseReleased(mx, my, b);
        return super.mouseReleased(mx, my, b);
    }
    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        if (scrollBar != null) { scrollBar.mouseScrolled(delta); return true; }
        return super.mouseScrolled(mx, my, delta);
    }

    private Button btn(String label, int x, int y, int w, Runnable a) {
        return KveGuiStyle.smartBtn(x, y, w, label, b -> a.run());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }
}
