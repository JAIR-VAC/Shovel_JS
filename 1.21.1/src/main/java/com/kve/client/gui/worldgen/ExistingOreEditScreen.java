package com.kve.client.gui.worldgen;

import com.kve.client.gui.recipe.RecipePreviewScreen;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.MiniItemIcon;
import com.kve.client.gui.picker.ItemPickerScreen;

import com.kve.common.Lang;
import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.common.worldgen.OreOverride;
import com.kve.common.worldgen.OreOverrideExporter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Edita (o quita) un mineral que el mundo ya genera: vetas por chunk,
 * rareza, rango y distribucion de altura, tamano de veta, descarte con aire,
 * el bloque de cada target y en que biomas. Arranca con los valores reales
 * del registro ({@link OreOverride#fromOriginal}); Guardar encola un cambio
 * del dominio WORLDGEN (requiere reiniciar, como el resto del world gen).
 */
@OnlyIn(Dist.CLIENT)
public class ExistingOreEditScreen extends Screen {

    private static final int GUI_W = 300;
    private static final int ROW = 18;
    private static final int MAX_TARGET_ROWS = 4;

    private final Screen parent;
    private final OreOverride draft;
    private final OreOverride saved;

    private int L, T, guiH;
    private int heightBarY;
    private KvePlateButton saveBtn;
    private String flashMsg = "";
    private long flashAt;

    public ExistingOreEditScreen(Screen parent, OreOverride draft, OreOverride saved) {
        super(Lang.c("gui.kve.worldgen.ores.edit_title"));
        this.parent = parent;
        this.draft = draft;
        this.saved = saved;
    }

    private int computeHeight() {
        int rows = 4;
        if (draft.mode == OreOverride.Mode.MODIFY) {
            rows += 1;
            rows += 2;
            rows += 1;
            rows += Math.min(MAX_TARGET_ROWS, draft.targetBlocks.size());
        }
        return 30 + rows * ROW + 30;
    }

    @Override
    protected void init() {
        clearWidgets();
        setFocused(null);
        guiH = computeHeight();
        L = (width - GUI_W) / 2;
        T = Math.max(4, (height - guiH) / 2);
        OreOverride.Original orig = draft.original();
        int x = L + 8, w = GUI_W - 16, half = (w - 6) / 2;
        int y = T + 30;

        addRenderableWidget(new KvePlateButton(x, y, w, 14, Component.literal(Lang.get("gui.kve.worldgen.ores.mode") + ": "
                + Lang.get(draft.mode == OreOverride.Mode.REMOVE ? "gui.kve.worldgen.ores.mode.remove" : "gui.kve.worldgen.ores.mode.modify")),
                b -> {
                    draft.mode = draft.mode == OreOverride.Mode.REMOVE ? OreOverride.Mode.MODIFY : OreOverride.Mode.REMOVE;
                    init();
                }).withDanger(draft.mode == OreOverride.Mode.REMOVE));
        y += ROW;

        if (draft.mode == OreOverride.Mode.MODIFY) {
            if (draft.count != null) {
                addRenderableWidget(intBox(x, y, half, draft.count, v -> draft.count = v, "gui.kve.worldgen.ores.count"));
            }
            if (draft.rarity != null) {
                addRenderableWidget(intBox(x + half + 6, y, half, draft.rarity, v -> draft.rarity = v, "gui.kve.worldgen.ores.rarity"));
            }
            y += ROW;

            if (draft.minY != null && draft.maxY != null) {
                addRenderableWidget(intBox(x, y, half, draft.minY, v -> draft.minY = v, "gui.kve.worldgen.ores.min_y"));
                addRenderableWidget(intBox(x + half + 6, y, half, draft.maxY, v -> draft.maxY = v, "gui.kve.worldgen.ores.max_y"));
            }
            y += ROW;
            heightBarY = y;
            if (draft.minY != null) {
                addRenderableWidget(new KvePlateButton(x + w / 2, y, w - w / 2, 14,
                        Component.literal(Lang.get("gui.kve.worldgen.vein.height_distribution") + ": " + OreOverride.shortType(draft.heightType)),
                        b -> { cycleHeightType(orig); init(); }));
            }
            y += ROW;

            if (draft.veinSize != null) {
                addRenderableWidget(intBox(x, y, half, draft.veinSize, v -> draft.veinSize = v, "gui.kve.worldgen.vein.vein_size"));
            }
            if (draft.discardChance != null) {
                EditBox d = new KveScaledEditBox(font, x + half + 6, y, half, 12, Lang.c("gui.kve.worldgen.vein.discard_chance"));
                d.setValue(String.valueOf(draft.discardChance));
                d.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c("gui.kve.worldgen.vein.discard_chance")));
                d.setResponder(s -> { try { draft.discardChance = Double.parseDouble(s.trim().replace(',', '.')); } catch (Exception ignored) {} });
                addRenderableWidget(d);
            }
            y += ROW;

            for (int i = 0; i < Math.min(MAX_TARGET_ROWS, draft.targetBlocks.size()); i++) {
                final int idx = i;
                EditBox tb = new KveScaledEditBox(font, x + 18, y, w - 18 - 32, 12, Lang.c("gui.kve.worldgen.vein.ore_block"));
                tb.setMaxLength(128);
                tb.setValue(draft.targetBlocks.get(i));
                tb.setResponder(s -> draft.targetBlocks.set(idx, s.trim()));
                addRenderableWidget(tb);
                addRenderableWidget(new KvePlateButton(x + w - 28, y, 28, 12, Component.literal("..."),
                        b -> minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                            ResourceLocation k = BuiltInRegistries.ITEM.getKey(stack.getItem());
                            if (k != null && BuiltInRegistries.BLOCK.containsKey(k)) draft.targetBlocks.set(idx, k.toString());
                        }))));
                y += ROW;
            }
        }

        String biomeLabel = draft.biomeSelectors.isEmpty()
                ? Lang.get(draft.mode == OreOverride.Mode.REMOVE ? "gui.kve.worldgen.ores.biomes_all" : "gui.kve.worldgen.ores.biomes_original", draft.originalBiomes.size())
                : Lang.get("gui.kve.worldgen.ores.biomes_custom", draft.biomeSelectors.size());
        addRenderableWidget(new KvePlateButton(x, y, w - 64, 14, Component.literal(biomeLabel),
                b -> minecraft.setScreen(new BiomePickerScreen(this, draft.biomeSelectors.isEmpty() && draft.mode == OreOverride.Mode.MODIFY
                        ? draft.originalBiomes : draft.biomeSelectors, false,
                        picked -> { draft.biomeSelectors.clear(); draft.biomeSelectors.addAll(picked); }))));
        addRenderableWidget(new KvePlateButton(x + w - 60, y, 60, 14, Lang.c("gui.kve.btn.reset_short"),
                b -> { draft.biomeSelectors.clear(); init(); }));
        y += ROW;

        int by = T + guiH - 20;
        KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(x, by, x + w);
        addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.cancel"), b -> onClose()));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.reset"), b -> { draft.resetToOriginal(); draft.biomeSelectors.clear(); draft.mode = OreOverride.Mode.MODIFY; init(); }));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.preview_short"),
                b -> minecraft.setScreen(new RecipePreviewScreen(this, draft.placedFeatureId, OreOverrideExporter.preview(draft)))));
        if (saved != null) {
            addRenderableWidget(row.add(0, Lang.get("gui.kve.worldgen.ores.restore_original"), b -> restoreOriginal()).withDanger(true));
        }
        saveBtn = row.add(0, Lang.get("gui.kve.btn.save"), b -> save()).withPrimary(true);
        addRenderableWidget(saveBtn);
    }

    private void cycleHeightType(OreOverride.Original orig) {
        String cur = draft.heightType == null ? OreOverride.UNIFORM : draft.heightType;
        boolean origIsOther = orig.heightType != null && !orig.heightType.equals(OreOverride.UNIFORM) && !orig.heightType.equals(OreOverride.TRAPEZOID);
        if (cur.equals(OreOverride.UNIFORM)) draft.heightType = OreOverride.TRAPEZOID;
        else if (cur.equals(OreOverride.TRAPEZOID) && origIsOther) draft.heightType = orig.heightType;
        else draft.heightType = OreOverride.UNIFORM;
    }

    private EditBox intBox(int x, int y, int w, int value, java.util.function.IntConsumer set, String labelKey) {
        EditBox b = new KveScaledEditBox(font, x, y, w, 12, Lang.c(labelKey));
        b.setMaxLength(8);
        b.setValue(String.valueOf(value));
        b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c(labelKey)));
        b.setResponder(s -> { try { set.accept(Integer.parseInt(s.trim())); } catch (Exception ignored) {} });
        return b;
    }

    private void save() {
        List<String> problems = draft.validate();
        if (!problems.isEmpty()) { flash(problems.get(0)); return; }
        OreOverride staged = draft.copy();
        String key = staged.key();
        OreOverride previous = OreOverrideExporter.getEntry(key);
        OreOverrideExporter.stage(staged);
        PendingChangeManager.queue(PendingChange.of(PendingDomain.WORLDGEN, "ore:" + key,
                        previous == null ? PendingChange.Action.CREATE : PendingChange.Action.EDIT, true,
                        OreOverrideExporter::commit,
                        () -> OreOverrideExporter.discardStaged(key, previous))
                .withDiff(previous == null ? null : () -> OreOverrideExporter.preview(previous),
                        () -> OreOverrideExporter.preview(staged)));
        minecraft.setScreen(parent);
    }

    private void restoreOriginal() {
        String key = draft.key();
        OreOverride previous = OreOverrideExporter.getEntry(key);
        if (previous == null) { onClose(); return; }
        OreOverrideExporter.stageDelete(key);
        PendingChangeManager.queue(PendingChange.of(PendingDomain.WORLDGEN, "ore:" + key, PendingChange.Action.DELETE, true,
                        OreOverrideExporter::commit,
                        () -> OreOverrideExporter.stage(previous))
                .withDiff(() -> OreOverrideExporter.preview(previous), null));
        minecraft.setScreen(parent);
    }

    private void flash(String msg) { flashMsg = msg; flashAt = System.currentTimeMillis(); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, guiH, Lang.get("gui.kve.worldgen.ores.edit_title"));
        OreOverride.Original orig = draft.original();
        String block = orig.targetBlocks.isEmpty() ? "" : orig.targetBlocks.get(0);
        MiniItemIcon.draw(g, L + 8, T + 12, 16, MiniItemIcon.block(block));
        KveGuiStyle.drawTextSafe(g, draft.placedFeatureId, L + 28, T + 12, GUI_W - 36, KveGuiStyle.ACCENT_GOLD);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.worldgen.ores.original", originalLine(orig)), L + 28, T + 21, GUI_W - 36, KveGuiStyle.TEXT_MUTED);

        if (draft.mode == OreOverride.Mode.MODIFY) {
            int x = L + 8, w = GUI_W - 16;
            if (draft.count == null && draft.rarity == null) {
                KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.worldgen.ores.no_count"), x, T + 30 + ROW + 2, w, KveGuiStyle.TEXT_MUTED);
            } else if (orig.countVariable) {
                KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.worldgen.ores.count_variable"), x + (w - 6) / 2 + 6, T + 30 + ROW + 2, (w - 6) / 2, KveGuiStyle.TEXT_MUTED);
            }
            if (draft.minY != null && draft.maxY != null) drawHeightBar(g, x, heightBarY + 3, w / 2 - 6);
            int ty = heightBarY + ROW * 2;
            for (int i = 0; i < Math.min(MAX_TARGET_ROWS, draft.targetBlocks.size()); i++) {
                MiniItemIcon.draw(g, x, ty - 2, 16, MiniItemIcon.block(draft.targetBlocks.get(i)));
                ty += ROW;
            }
            if (draft.targetBlocks.size() > MAX_TARGET_ROWS) {
                KveGuiStyle.drawTextSafe(g, "+" + (draft.targetBlocks.size() - MAX_TARGET_ROWS), x, ty - ROW + 12, 30, KveGuiStyle.TEXT_MUTED);
            }
        }

        List<String> problems = draft.validate();
        if (saveBtn != null) saveBtn.active = problems.isEmpty();
        String msg = !flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4500 ? flashMsg
                : !problems.isEmpty() ? problems.get(0)
                : Lang.get(draft.mode == OreOverride.Mode.REMOVE ? "gui.kve.worldgen.ores.hint_remove" : "gui.kve.worldgen.ores.hint_modify");
        KveGuiStyle.drawTextSafe(g, msg, L + 8, T + guiH - 32, GUI_W - 16, problems.isEmpty() ? KveGuiStyle.TEXT_MUTED : KveGuiStyle.TEXT_WARN);
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    private String originalLine(OreOverride.Original o) {
        StringBuilder sb = new StringBuilder();
        if (o.count != null) sb.append('x').append(o.countVariable ? "?" : o.count).append(Lang.get("gui.kve.worldgen.ores.per_chunk"));
        if (o.rarity != null) sb.append(sb.length() > 0 ? ", " : "").append("1/").append(o.rarity);
        if (o.minAnchor != null) {
            sb.append(sb.length() > 0 ? ", " : "").append(OreOverride.shortType(o.heightType)).append(' ')
                    .append(OreOverride.anchorLabel(o.minAnchor)).append(" .. ").append(OreOverride.anchorLabel(o.maxAnchor));
        }
        if (o.veinSize != null) sb.append(", ").append(Lang.get("gui.kve.worldgen.ores.size_short", o.veinSize));
        return sb.toString();
    }

    private void drawHeightBar(GuiGraphics g, int x, int y, int w) {
        int lo = draft.dimMinY, hi = draft.dimMinY + draft.dimHeight - 1;
        int h = 8;
        g.fill(x, y, x + w, y + h, 0xFF1A2230);
        OreOverride.Original o = draft.original();
        if (o.minY != null && o.maxY != null) {
            int ox0 = x + scale(o.minY, lo, hi, w), ox1 = x + scale(o.maxY, lo, hi, w);
            g.fill(ox0, y, Math.max(ox0 + 1, ox1), y + h, 0xFF3A4A60);
        }
        int x0 = x + scale(draft.minY, lo, hi, w), x1 = x + scale(draft.maxY, lo, hi, w);
        g.fill(x0, y + 2, Math.max(x0 + 1, x1), y + h - 2, KveGuiStyle.ACCENT_GOLD);
        String label = lo + " .. " + hi;
        KveGuiStyle.drawTextSafeScaled(g, label, x, y + h + 1, w, KveGuiStyle.TEXT_MUTED, 0.6f);
    }

    private static int scale(int v, int lo, int hi, int w) {
        int c = Math.max(lo, Math.min(hi, v));
        return (int) ((long) (c - lo) * w / Math.max(1, hi - lo));
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
    @Override public boolean isPauseScreen() { return false; }
}
