package com.kve.client.structure;

import com.kve.client.gui.ItemPickerScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.RegistryIndex;
import com.kve.common.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Lista de materiales de la estructura del editor: cada bloque usado, con su cantidad, de mayor a menor.
 * Cada fila tiene "Reemplazar": elige otro bloque y cambia todas las celdas de ese bloque (se puede deshacer).
 */
public class StructureMaterialsScreen extends Screen {

    private static final int GUI_W = 300, ROW_H = 18;

    private final Screen parent;
    private final Supplier<List<Map.Entry<Block, Integer>>> source;
    private final BiFunction<Block, Block, Integer> replacer;
    private List<Map.Entry<Block, Integer>> rows = List.of();
    private int total;
    private int L, T, guiH, visibleRows, scroll;
    private String notice = "";
    private boolean noticeOk;

    public StructureMaterialsScreen(Screen parent, Supplier<List<Map.Entry<Block, Integer>>> source,
                                    BiFunction<Block, Block, Integer> replacer) {
        super(Lang.c("gui.kve.structure.materials.title"));
        this.parent = parent;
        this.source = source;
        this.replacer = replacer;
    }

    @Override
    protected void init() {
        // se vuelve a contar al abrir y al volver del selector: un reemplazo cambia la lista
        rows = source.get();
        int sum = 0;
        for (Map.Entry<Block, Integer> e : rows) sum += e.getValue();
        total = sum;
        guiH = Math.min(height - 20, 72 + Math.max(1, rows.size()) * ROW_H);
        visibleRows = Math.max(1, (guiH - 72) / ROW_H);
        L = (width - GUI_W) / 2;
        T = (height - guiH) / 2;
        scroll = Math.max(0, Math.min(scroll, rows.size() - visibleRows));
        String replace = Lang.get("gui.kve.structure.materials.replace");
        int rw = KveGuiStyle.btnWidth(replace);
        int y = T + 34;
        for (int i = 0; i < visibleRows && i + scroll < rows.size(); i++) {
            Block from = rows.get(i + scroll).getKey();
            KvePlateButton b = new KvePlateButton(L + GUI_W - KveGuiStyle.PAD - rw, y + i * ROW_H, rw, 14,
                    Component.literal(replace), btn -> pickReplacement(from));
            b.setTooltip(Tooltip.create(Component.literal(Lang.get("gui.kve.structure.materials.replace_tip", from.getName().getString()))));
            addRenderableWidget(b);
        }
        addRenderableWidget(new KvePlateButton(L + GUI_W - 76, T + guiH - 20, 70, 14, Lang.c("gui.kve.btn.close"), b -> onClose()));
    }

    private void pickReplacement(Block from) {
        minecraft.setScreen(new ItemPickerScreen(this, stack -> {
            if (!(stack.getItem() instanceof BlockItem bi)) {
                setNotice(Lang.get("gui.kve.worldgen.extras.need_block"), false);
                return;
            }
            Block to = bi.getBlock();
            if (to == from) {
                setNotice(Lang.get("gui.kve.structure.materials.replace_same"), false);
                return;
            }
            int n = replacer.apply(from, to);
            setNotice(Lang.get("gui.kve.structure.replace.done", n, from.getName().getString(), to.getName().getString()), n > 0);
        }));
    }

    private void setNotice(String text, boolean ok) {
        notice = text;
        noticeOk = ok;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, guiH, Lang.get("gui.kve.structure.materials.title"));
        int x = L + KveGuiStyle.PAD;
        int y = T + 20;
        String summary = rows.isEmpty()
                ? Lang.get("gui.kve.structure.materials.empty")
                : Lang.get("gui.kve.structure.materials.total", total, rows.size());
        KveGuiStyle.drawTextSafe(g, summary, x, y, GUI_W - KveGuiStyle.PAD * 2, KveGuiStyle.TEXT_MUTED);
        y += 14;
        int rw = KveGuiStyle.btnWidth(Lang.get("gui.kve.structure.materials.replace"));
        int countRight = L + GUI_W - KveGuiStyle.PAD - rw - 6;
        for (int i = 0; i < visibleRows && i + scroll < rows.size(); i++) {
            Map.Entry<Block, Integer> e = rows.get(i + scroll);
            int ry = y + i * ROW_H;
            if (i % 2 == 1) g.fill(x - 2, ry - 1, L + GUI_W - KveGuiStyle.PAD + 2, ry + ROW_H - 1, KveGuiStyle.BG_FIELD);
            ItemStack icon = RegistryIndex.blockIcon(e.getKey());
            try { g.renderItem(icon.isEmpty() ? new ItemStack(e.getKey().asItem()) : icon, x, ry); } catch (Throwable ignored) {}
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(e.getKey());
            String name = e.getKey().getName().getString();
            String count = "x" + e.getValue();
            int textW = countRight - font.width(count) - 6 - (x + 20);
            KveGuiStyle.drawTextSafe(g, name, x + 20, ry + 1, textW, KveGuiStyle.TEXT_PRIMARY);
            KveGuiStyle.drawTextSafe(g, id == null ? "" : id.toString(), x + 20, ry + 10, textW, KveGuiStyle.TEXT_MUTED);
            KveGuiStyle.drawTextSafe(g, count, countRight - font.width(count), ry + 5, 60, KveGuiStyle.ACCENT_GOLD);
        }
        if (!notice.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, notice, x, T + guiH - 17, GUI_W - 90,
                    noticeOk ? KveGuiStyle.TEXT_PRIMARY : KveGuiStyle.TEXT_MUTED);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int next = Math.max(0, Math.min(rows.size() - visibleRows, scroll - (int) Math.signum(delta)));
        if (next != scroll) { scroll = next; rebuildWidgets(); }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
