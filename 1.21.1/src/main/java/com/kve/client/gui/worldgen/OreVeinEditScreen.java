package com.kve.client.gui.worldgen;

import com.kve.client.gui.picker.ItemPickerScreen;
import com.kve.client.gui.RegistryIndex;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.RangeSlider;
import com.kve.client.gui.tag.TagPresetPickerScreen;

import com.kve.common.Lang;
import com.kve.common.worldgen.WorldGenEntry;
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
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class OreVeinEditScreen extends Screen {

    private static final int GUI_W = 260;
    private static final int RANGE_ROW_EXTRA_H = 8;
    private static final int GUI_H_BASE = 186 + 18 + RANGE_ROW_EXTRA_H;
    private static final int FIELD_ROW_H = 18;

    private static final List<String> COMMON_REPLACEABLE_TARGETS = List.of(
            "minecraft:stone_ore_replaceables",
            "minecraft:deepslate_ore_replaceables",
            "minecraft:base_stone_overworld",
            "minecraft:base_stone_nether",
            "minecraft:netherrack_replaceables"
    );

    private final Screen parent;
    private final WorldGenEntry.OreVein original;
    private final Consumer<WorldGenEntry.OreVein> onSave;

    private String draftOreBlock;
    private String draftReplaceableTarget;
    private int draftVeinSize;
    private int draftVeinsPerChunk;
    private int draftRarity;
    private int draftMinY;
    private int draftMaxY;
    private double draftDiscardChance;
    private WorldGenEntry.OreVein.HeightDistribution draftHeightDistribution;
    private boolean draftHasDeepslate;
    private String draftDeepslateOreBlock;
    private String draftDeepslateReplaceableTarget;

    private int L, T;
    private String flashMsg = "";
    private long flashAt = 0;
    private RangeSlider heightSlider;
    private KvePlateButton saveBtn;
    private int rangeRowY;
    private int deepslateToggleRowY;

    public OreVeinEditScreen(Screen parent, WorldGenEntry.OreVein existingOrNull, Consumer<WorldGenEntry.OreVein> onSave) {
        super(Lang.c("gui.kve.worldgen.vein.title"));
        this.parent = parent;
        this.original = existingOrNull;
        this.onSave = onSave;
        WorldGenEntry.OreVein src = existingOrNull != null ? existingOrNull : new WorldGenEntry.OreVein();
        draftOreBlock = src.oreBlock;
        draftReplaceableTarget = src.replaceableTarget;
        draftVeinSize = src.veinSize;
        draftVeinsPerChunk = src.veinsPerChunk;
        draftRarity = src.rarity;
        draftMinY = src.minY;
        draftMaxY = src.maxY;
        draftDiscardChance = src.discardChanceOnAirExposure;
        draftHeightDistribution = src.heightDistribution;
        draftDeepslateOreBlock = src.deepslateOreBlock == null ? "" : src.deepslateOreBlock;
        draftDeepslateReplaceableTarget = (src.deepslateReplaceableTarget == null || src.deepslateReplaceableTarget.isBlank())
                ? "minecraft:deepslate_ore_replaceables" : src.deepslateReplaceableTarget;
        draftHasDeepslate = !draftDeepslateOreBlock.isBlank();
    }

    private int guiH() {
        return GUI_H_BASE + (draftHasDeepslate ? FIELD_ROW_H * 2 : 0);
    }

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - guiH()) / 2;
        clearWidgets();
        setFocused(null);

        KveGuiStyle.Layout layout = new KveGuiStyle.Layout(L + 8, T + 18, FIELD_ROW_H);

        EditBox oreBlockBox = field(layout.x(), layout.row(), GUI_W - 46, draftOreBlock, Lang.c("gui.kve.worldgen.vein.ore_block"));
        oreBlockBox.setResponder(s -> draftOreBlock = s);
        addRenderableWidget(oreBlockBox);
        addRenderableWidget(new KvePlateButton(L + GUI_W - 34, T + 18, 26, 12,
                Component.literal("..."), b -> pickOreBlock()));

        int replaceableRowY = layout.row();
        EditBox replaceableBox = field(layout.x(), replaceableRowY, GUI_W - 46, draftReplaceableTarget, Lang.c("gui.kve.worldgen.vein.replaceable_target"));
        replaceableBox.setResponder(s -> draftReplaceableTarget = s);
        addRenderableWidget(replaceableBox);
        addRenderableWidget(new KvePlateButton(L + GUI_W - 34, replaceableRowY, 26, 12,
                Component.literal("..."), b -> pickReplaceableTarget(s -> draftReplaceableTarget = s)));

        int thirdW = (GUI_W - 16 - 12) / 3;
        int sizeRowY = layout.row();
        EditBox sizeBox = field(layout.x(), sizeRowY, thirdW, String.valueOf(draftVeinSize), Lang.c("gui.kve.worldgen.vein.vein_size"));
        sizeBox.setResponder(s -> draftVeinSize = parseInt(s, draftVeinSize));
        addRenderableWidget(sizeBox);
        EditBox perChunkBox = field(layout.x() + thirdW + 6, sizeRowY, thirdW, String.valueOf(draftVeinsPerChunk), Lang.c("gui.kve.worldgen.vein.veins_per_chunk"));
        perChunkBox.setResponder(s -> draftVeinsPerChunk = parseInt(s, draftVeinsPerChunk));
        addRenderableWidget(perChunkBox);
        EditBox rarityBox = field(layout.x() + (thirdW + 6) * 2, sizeRowY, thirdW, String.valueOf(draftRarity), Lang.c("gui.kve.worldgen.vein.rarity"));
        rarityBox.setResponder(s -> draftRarity = parseInt(s, draftRarity));
        addRenderableWidget(rarityBox);

        int rangeY = layout.row(FIELD_ROW_H + RANGE_ROW_EXTRA_H);
        rangeRowY = rangeY;
        if (heightSlider == null) {
            heightSlider = new RangeSlider(layout.x(), rangeY + 12, GUI_W - 16, -64, 320, draftMinY, draftMaxY);
        } else {
            heightSlider.setPosition(layout.x(), rangeY + 12);
        }

        EditBox discardBox = field(layout.x(), layout.row(24), GUI_W - 16,
                String.valueOf(draftDiscardChance), Lang.c("gui.kve.worldgen.vein.discard_chance"));
        discardBox.setResponder(s -> draftDiscardChance = parseDouble(s, draftDiscardChance));
        addRenderableWidget(discardBox);

        int distY = layout.row();
        addRenderableWidget(new KvePlateButton(layout.x(), distY, GUI_W - 16, 12,
                Component.literal(Lang.get("gui.kve.worldgen.vein.height_distribution") + ": "
                        + Lang.get("gui.kve.worldgen.vein.height_distribution." + draftHeightDistribution.name().toLowerCase())),
                b -> {
                    draftHeightDistribution = draftHeightDistribution == WorldGenEntry.OreVein.HeightDistribution.UNIFORM
                            ? WorldGenEntry.OreVein.HeightDistribution.TRAPEZOID
                            : WorldGenEntry.OreVein.HeightDistribution.UNIFORM;
                    init();
                }));

        int deepslateToggleY = layout.row();
        deepslateToggleRowY = deepslateToggleY;
        addRenderableWidget(new KvePlateButton(layout.x(), deepslateToggleY, GUI_W - 16, 12,
                Component.literal(Lang.get("gui.kve.worldgen.vein.deepslate_toggle") + ": "
                        + Lang.get("gui.kve.worldgen.vein.deepslate_toggle." + (draftHasDeepslate ? "on" : "off"))),
                b -> { draftHasDeepslate = !draftHasDeepslate; init(); }));

        if (draftHasDeepslate) {
            int deepslateOreY = layout.row();
            EditBox deepslateOreBox = field(layout.x(), deepslateOreY, GUI_W - 46, draftDeepslateOreBlock,
                    Lang.c("gui.kve.worldgen.vein.deepslate_ore_block"));
            deepslateOreBox.setResponder(s -> draftDeepslateOreBlock = s);
            addRenderableWidget(deepslateOreBox);
            addRenderableWidget(new KvePlateButton(L + GUI_W - 34, deepslateOreY, 26, 12,
                    Component.literal("..."), b -> pickDeepslateOreBlock()));

            int deepslateTargetY = layout.row();
            EditBox deepslateTargetBox = field(layout.x(), deepslateTargetY, GUI_W - 46, draftDeepslateReplaceableTarget,
                    Lang.c("gui.kve.worldgen.vein.deepslate_replaceable_target"));
            deepslateTargetBox.setResponder(s -> draftDeepslateReplaceableTarget = s);
            addRenderableWidget(deepslateTargetBox);
            addRenderableWidget(new KvePlateButton(L + GUI_W - 34, deepslateTargetY, 26, 12,
                    Component.literal("..."), b -> pickReplaceableTarget(s -> draftDeepslateReplaceableTarget = s)));
        }

        int btnY = T + guiH() - 16;
        addRenderableWidget(new KvePlateButton(L + 8, btnY, (GUI_W - 16 - 6) / 2, 12,
                Lang.c("gui.kve.btn.cancel"), b -> minecraft.setScreen(parent)));
        saveBtn = new KvePlateButton(L + 8 + (GUI_W - 16 - 6) / 2 + 6, btnY, (GUI_W - 16 - 6) / 2, 12,
                Lang.c("gui.kve.btn.save"), b -> save()).withPrimary(true);
        addRenderableWidget(saveBtn);
    }

    private void pickOreBlock() { pickBlock(id -> draftOreBlock = id); }

    private void pickDeepslateOreBlock() { pickBlock(id -> draftDeepslateOreBlock = id); }

    private void pickBlock(java.util.function.Consumer<String> setter) {
        minecraft.setScreen(new ItemPickerScreen(this, stack -> {
            if (stack.getItem() instanceof net.minecraft.world.item.BlockItem bi) {
                setter.accept(RegistryIndex.id(bi.getBlock()));
                flashMsg = "";
            } else {
                flashMsg = Lang.get("gui.kve.worldgen.extras.need_block");
                flashAt = System.currentTimeMillis();
            }
        }));
    }

    private void pickReplaceableTarget(Consumer<String> setter) {
        minecraft.setScreen(new TagPresetPickerScreen(this,
                Lang.get("gui.kve.worldgen.vein.replaceable_target"),
                COMMON_REPLACEABLE_TARGETS, setter));
    }

    private WorldGenEntry.OreVein buildVein() {
        WorldGenEntry.OreVein v = new WorldGenEntry.OreVein();
        v.oreBlock = draftOreBlock.trim();
        v.replaceableTarget = draftReplaceableTarget.trim().isEmpty() ? "minecraft:stone_ore_replaceables" : draftReplaceableTarget.trim();
        v.veinSize = draftVeinSize;
        v.veinsPerChunk = draftVeinsPerChunk;
        v.rarity = draftRarity;
        v.minY = heightSlider.getMin();
        v.maxY = heightSlider.getMax();
        v.discardChanceOnAirExposure = draftDiscardChance;
        v.heightDistribution = draftHeightDistribution;
        v.deepslateOreBlock = draftHasDeepslate ? draftDeepslateOreBlock.trim() : "";
        v.deepslateReplaceableTarget = draftDeepslateReplaceableTarget.trim().isEmpty()
                ? "minecraft:deepslate_ore_replaceables" : draftDeepslateReplaceableTarget.trim();
        return v;
    }

    private void save() {
        WorldGenEntry.OreVein v = buildVein();
        v.veinId = (original != null && original.veinId != null && !original.veinId.isBlank())
                ? original.veinId : java.util.UUID.randomUUID().toString();

        var problems = v.validate();
        if (!problems.isEmpty()) {
            flashMsg = String.join(" ", problems);
            flashAt = System.currentTimeMillis();
            return;
        }
        onSave.accept(v);
        minecraft.setScreen(parent);
    }

    private EditBox field(int x, int y, int w, String value, Component narration) {
        EditBox f = new KveScaledEditBox(font, x, y, w, 12, narration);
        f.setMaxLength(128);
        f.setValue(value);
        return f;
    }

    private int parseInt(String s, int def) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; } }
    private double parseDouble(String s, double def) { try { return Double.parseDouble(s.trim().replace(',', '.')); } catch (Exception e) { return def; } }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, guiH(), Lang.get("gui.kve.worldgen.vein.title"));

        KveGuiStyle.helpIcon(g, L + GUI_W - 12, rangeRowY, mx, my, Lang.c("gui.kve.worldgen.vein.help.height"));
        KveGuiStyle.drawTextSafe(g, heightSlider.getMin() + " .. " + heightSlider.getMax(),
                L + 8, rangeRowY + 2, GUI_W - 30, KveGuiStyle.TEXT_MUTED);
        heightSlider.render(g);
        if (draftHasDeepslate) {
            KveGuiStyle.helpIcon(g, L + GUI_W - 12, deepslateToggleRowY, mx, my, Lang.c("gui.kve.worldgen.vein.help.deepslate"));
        }

        WorldGenEntry.OreVein live = buildVein();
        List<String> liveProblems = live.validate();
        if (saveBtn != null) saveBtn.active = liveProblems.isEmpty();
        if (!liveProblems.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, liveProblems.get(0), L + 8, T + guiH() - 42, GUI_W - 16, KveGuiStyle.TEXT_WARN);
        } else if (live.rarity > 0) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.worldgen.vein.estimate_rare", live.veinSize, live.rarity),
                    L + 8, T + guiH() - 42, GUI_W - 16, KveGuiStyle.TEXT_MUTED);
        } else {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.worldgen.vein.estimate",
                            (long) live.veinSize * live.veinsPerChunk, live.veinsPerChunk, live.veinSize),
                    L + 8, T + guiH() - 42, GUI_W - 16, KveGuiStyle.TEXT_MUTED);
        }

        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4500) {
            KveGuiStyle.drawTextSafe(g, flashMsg, L + 8, T + guiH() - 30, GUI_W - 16, 0xFFFF6666);
        }

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (heightSlider.mouseClicked(mx, my, btn)) return true;
        return super.mouseClicked(mx, my, btn);
    }
    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (heightSlider.mouseDragged(mx, my, b, dx, dy)) return true;
        return super.mouseDragged(mx, my, b, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int b) {
        if (heightSlider.mouseReleased(mx, my, b)) return true;
        return super.mouseReleased(mx, my, b);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        if (com.kve.client.gui.EditorKeys.isSave(key, mods)) { save(); return true; }
        if (com.kve.client.gui.EditorKeys.isFocusTab(key, mods)) { super.keyPressed(key, scan, mods); return true; }
        return com.kve.client.gui.EditorKeys.swallow(mods);
    }
    @Override public boolean isPauseScreen() { return false; }
}
