package com.kve.client.gui.worldgen;

import com.kve.client.gui.EditorKeys;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.picker.ItemPickerScreen;
import com.kve.client.gui.mob.MobPickerScreen;
import com.kve.client.gui.RegistryIndex;
import com.kve.common.Lang;
import com.kve.common.worldgen.WorldGenEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class BiomeExtrasScreen extends Screen {

    /** Resultado editado: listas nuevas (nunca las del llamador). */
    public record Result(List<String> removedFeatures, List<WorldGenEntry.SpawnRule> spawns, List<WorldGenEntry.Patch> patches) {}

    private enum Tab { FEATURES, SPAWNS, PATCHES }

    private static final String[][] VANILLA_ORES = {
            {"coal", "minecraft:ore_coal_upper", "minecraft:ore_coal_lower"},
            {"iron", "minecraft:ore_iron_upper", "minecraft:ore_iron_middle", "minecraft:ore_iron_small"},
            {"copper", "minecraft:ore_copper", "minecraft:ore_copper_large"},
            {"gold", "minecraft:ore_gold", "minecraft:ore_gold_lower", "minecraft:ore_gold_extra"},
            {"redstone", "minecraft:ore_redstone", "minecraft:ore_redstone_lower"},
            {"lapis", "minecraft:ore_lapis", "minecraft:ore_lapis_buried"},
            {"diamond", "minecraft:ore_diamond", "minecraft:ore_diamond_medium", "minecraft:ore_diamond_large", "minecraft:ore_diamond_buried"},
            {"emerald", "minecraft:ore_emerald"},
    };

    private static final int GUI_W = 340;
    private static final int GUI_H = 250;
    private static final int ROW_H = 14;

    private final Screen parent;
    private final Consumer<Result> onSave;
    private final List<String> removed;
    private final List<WorldGenEntry.SpawnRule> spawns = new ArrayList<>();
    private final List<WorldGenEntry.Patch> patches = new ArrayList<>();

    private Tab tab = Tab.FEATURES;
    private int scroll = 0;
    private int L, T, listY, listRows;
    private String flashMsg = "";
    private long flashAt = 0;

    private String newFeature = "";
    private String newEntity = "";
    private boolean newSpawnRemove = false;
    private String newWeight = "10", newMin = "1", newMax = "4";
    private String newBlock = "";
    private String newTries = "32", newSpread = "7", newRarity = "8";
    private Runnable addAction = () -> {};

    public BiomeExtrasScreen(Screen parent, List<String> removedFeatures, List<WorldGenEntry.SpawnRule> spawns,
                             List<WorldGenEntry.Patch> patches, Consumer<Result> onSave) {
        super(Lang.c("gui.kve.worldgen.extras.title"));
        this.parent = parent;
        this.onSave = onSave;
        this.removed = new ArrayList<>(removedFeatures);
        for (WorldGenEntry.SpawnRule r : spawns) this.spawns.add(r.copy());
        for (WorldGenEntry.Patch p : patches) this.patches.add(p.copy());
    }

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        int x = L + 8, w = GUI_W - 16;
        int tabW = (w - 8) / 3;
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int count = switch (t) { case FEATURES -> removed.size(); case SPAWNS -> spawns.size(); case PATCHES -> patches.size(); };
            String label = (tab == t ? "> " : "") + Lang.get("gui.kve.worldgen.extras.tab." + t.name().toLowerCase()) + " (" + count + ")";
            addRenderableWidget(new KvePlateButton(x + i * (tabW + 4), T + 18, tabW, 14, Component.literal(label),
                    b -> { tab = t; scroll = 0; init(); }));
        }

        int y = T + 38;
        switch (tab) {
            case FEATURES -> y = buildFeaturesInput(x, y, w);
            case SPAWNS -> y = buildSpawnsInput(x, y, w);
            case PATCHES -> y = buildPatchesInput(x, y, w);
        }

        listY = y + 4;
        int btnY = T + GUI_H - 18;
        listRows = Math.max(1, (btnY - 14 - listY) / ROW_H);
        int size = currentSize();
        scroll = Math.max(0, Math.min(scroll, size - listRows));
        for (int i = 0; i < Math.min(listRows, size - scroll); i++) {
            int idx = scroll + i;
            addRenderableWidget(new KvePlateButton(L + GUI_W - 8 - 14, listY + i * ROW_H, 14, 12, Component.literal("x"),
                    b -> { removeAt(idx); init(); }));
        }

        int half = (w - 6) / 2;
        addRenderableWidget(new KvePlateButton(x, btnY, half, 12, Lang.c("gui.kve.btn.cancel"), b -> minecraft.setScreen(parent)));
        addRenderableWidget(new KvePlateButton(x + half + 6, btnY, half, 12, Lang.c("gui.kve.btn.save"), b -> {
            onSave.accept(new Result(new ArrayList<>(removed), spawns, patches));
            minecraft.setScreen(parent);
        }).withPrimary(true));
    }

    private int buildFeaturesInput(int x, int y, int w) {
        int addW = 60;
        EditBox box = field(x, y, w - addW - 4, newFeature, Lang.c("gui.kve.worldgen.extras.feature_id"));
        box.setResponder(s -> newFeature = s);
        addRenderableWidget(box);
        addAction = () -> {
            String id = newFeature.trim();
            String bare = id.replaceFirst("^#", "");
            if (!WorldGenEntry.isValidResourceId(bare)) { flash(Lang.get("gui.kve.worldgen.extras.bad_id", id)); return; }
            if (!removed.contains(id)) removed.add(id);
            newFeature = "";
            init();
        };
        addRenderableWidget(new KvePlateButton(x + w - addW, y, addW, 12, Lang.c("gui.kve.btn.add"), b -> addAction.run()));
        y += 16;
        int cols = 4, gap = 4, bw = (w - gap * (cols - 1)) / cols;
        for (int i = 0; i < VANILLA_ORES.length; i++) {
            String[] ore = VANILLA_ORES[i];
            int bx = x + (i % cols) * (bw + gap), by = y + (i / cols) * 14;
            boolean all = true;
            for (int k = 1; k < ore.length; k++) all &= removed.contains(ore[k]);
            String label = (all ? "- " : "+ ") + Lang.get("gui.kve.worldgen.extras.ore." + ore[0]);
            final boolean allNow = all;
            addRenderableWidget(new KvePlateButton(bx, by, bw, 12, Component.literal(label), b -> {
                for (int k = 1; k < ore.length; k++) {
                    if (allNow) removed.remove(ore[k]);
                    else if (!removed.contains(ore[k])) removed.add(ore[k]);
                }
                init();
            }));
        }
        return y + ((VANILLA_ORES.length + cols - 1) / cols) * 14;
    }

    private int buildSpawnsInput(int x, int y, int w) {
        int pickW = 20, modeW = 64;
        EditBox ent = field(x, y, w - pickW - modeW - 8, newEntity, Lang.c("gui.kve.worldgen.extras.entity"));
        ent.setResponder(s -> newEntity = s);
        addRenderableWidget(ent);
        addRenderableWidget(new KvePlateButton(x + w - pickW - modeW - 4, y, pickW, 12, Component.literal("..."), b ->
                minecraft.setScreen(new MobPickerScreen(this, et -> {
                    newEntity = RegistryIndex.id(et);
                    flashMsg = "";
                }))));
        addRenderableWidget(new KvePlateButton(x + w - modeW, y, modeW, 12,
                Lang.c(newSpawnRemove ? "gui.kve.worldgen.extras.spawn_remove" : "gui.kve.worldgen.extras.spawn_add"),
                b -> { newSpawnRemove = !newSpawnRemove; init(); }));
        y += 16;
        if (!newSpawnRemove) {
            int third = (w - 60 - 12) / 3;
            EditBox wBox = field(x, y, third, newWeight, Lang.c("gui.kve.worldgen.extras.weight"));
            wBox.setResponder(s -> newWeight = s);
            addRenderableWidget(wBox);
            EditBox minBox = field(x + third + 4, y, third, newMin, Lang.c("gui.kve.worldgen.extras.min"));
            minBox.setResponder(s -> newMin = s);
            addRenderableWidget(minBox);
            EditBox maxBox = field(x + (third + 4) * 2, y, third, newMax, Lang.c("gui.kve.worldgen.extras.max"));
            maxBox.setResponder(s -> newMax = s);
            addRenderableWidget(maxBox);
        }
        addAction = () -> {
            WorldGenEntry.SpawnRule r = new WorldGenEntry.SpawnRule();
            r.entityId = newEntity.trim();
            r.remove = newSpawnRemove;
            r.weight = parseInt(newWeight, -1);
            r.minCount = parseInt(newMin, -1);
            r.maxCount = parseInt(newMax, -1);
            List<String> problems = r.validate();
            if (!problems.isEmpty()) { flash(problems.get(0)); return; }
            spawns.add(r);
            newEntity = "";
            init();
        };
        addRenderableWidget(new KvePlateButton(x + w - 60, y, 60, 12, Lang.c("gui.kve.btn.add"), b -> addAction.run()));
        return y + 16;
    }

    private int buildPatchesInput(int x, int y, int w) {
        int pickW = 20;
        EditBox blk = field(x, y, w - pickW - 4, newBlock, Lang.c("gui.kve.worldgen.extras.block"));
        blk.setResponder(s -> newBlock = s);
        addRenderableWidget(blk);
        addRenderableWidget(new KvePlateButton(x + w - pickW, y, pickW, 12, Component.literal("..."), b ->
                minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                    if (stack.getItem() instanceof BlockItem bi) newBlock = RegistryIndex.id(bi.getBlock());
                    else flash(Lang.get("gui.kve.worldgen.extras.need_block"));
                }))));
        y += 16;
        int third = (w - 60 - 12) / 3;
        EditBox triesBox = field(x, y, third, newTries, Lang.c("gui.kve.worldgen.extras.tries"));
        triesBox.setResponder(s -> newTries = s);
        addRenderableWidget(triesBox);
        EditBox spreadBox = field(x + third + 4, y, third, newSpread, Lang.c("gui.kve.worldgen.extras.spread"));
        spreadBox.setResponder(s -> newSpread = s);
        addRenderableWidget(spreadBox);
        EditBox rarityBox = field(x + (third + 4) * 2, y, third, newRarity, Lang.c("gui.kve.worldgen.extras.rarity"));
        rarityBox.setResponder(s -> newRarity = s);
        addRenderableWidget(rarityBox);
        addAction = () -> {
            WorldGenEntry.Patch p = new WorldGenEntry.Patch();
            p.patchId = java.util.UUID.randomUUID().toString();
            p.block = newBlock.trim();
            p.tries = parseInt(newTries, -1);
            p.xzSpread = parseInt(newSpread, -1);
            p.ySpread = Math.min(WorldGenEntry.Patch.MAX_Y_SPREAD, Math.max(1, p.xzSpread / 2));
            p.rarity = parseInt(newRarity, -1);
            List<String> problems = p.validate();
            if (!problems.isEmpty()) { flash(problems.get(0)); return; }
            patches.add(p);
            newBlock = "";
            init();
        };
        addRenderableWidget(new KvePlateButton(x + w - 60, y, 60, 12, Lang.c("gui.kve.btn.add"), b -> addAction.run()));
        return y + 16;
    }

    private int currentSize() {
        return switch (tab) { case FEATURES -> removed.size(); case SPAWNS -> spawns.size(); case PATCHES -> patches.size(); };
    }

    private void removeAt(int idx) {
        switch (tab) {
            case FEATURES -> { if (idx < removed.size()) removed.remove(idx); }
            case SPAWNS -> { if (idx < spawns.size()) spawns.remove(idx); }
            case PATCHES -> { if (idx < patches.size()) patches.remove(idx); }
        }
    }

    private String rowText(int idx) {
        return switch (tab) {
            case FEATURES -> removed.get(idx);
            case SPAWNS -> {
                WorldGenEntry.SpawnRule r = spawns.get(idx);
                yield r.remove
                        ? Lang.get("gui.kve.worldgen.extras.row_spawn_remove", r.entityId)
                        : Lang.get("gui.kve.worldgen.extras.row_spawn_add", r.entityId, r.weight, r.minCount, r.maxCount);
            }
            case PATCHES -> {
                WorldGenEntry.Patch p = patches.get(idx);
                yield Lang.get("gui.kve.worldgen.extras.row_patch", p.block, p.tries, p.rarity);
            }
        };
    }

    private EditBox field(int x, int y, int w, String value, Component hint) {
        EditBox f = new KveScaledEditBox(font, x, y, w, 12, hint);
        f.setMaxLength(128);
        f.setValue(value);
        f.setHint(hint);
        return f;
    }

    private static int parseInt(String s, int def) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; } }

    private void flash(String msg) { flashMsg = msg; flashAt = System.currentTimeMillis(); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.worldgen.extras.title"));
        int size = currentSize();
        if (size == 0) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.worldgen.extras.empty." + tab.name().toLowerCase()),
                    L + 8, listY + 2, GUI_W - 16, KveGuiStyle.TEXT_MUTED);
        }
        for (int i = 0; i < Math.min(listRows, size - scroll); i++) {
            int ry = listY + i * ROW_H;
            g.fill(L + 8, ry, L + GUI_W - 8 - 16, ry + ROW_H - 2, KveGuiStyle.BG_FIELD);
            KveGuiStyle.drawTextSafe(g, rowText(scroll + i), L + 11, ry + 2, GUI_W - 40, KveGuiStyle.TEXT_PRIMARY);
        }
        if (size > listRows) {
            KveGuiStyle.drawTextSafe(g, (scroll + 1) + "-" + Math.min(size, scroll + listRows) + " / " + size,
                    L + 8, listY + listRows * ROW_H, GUI_W - 16, KveGuiStyle.TEXT_MUTED);
        }
        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4500) {
            KveGuiStyle.drawTextSafe(g, flashMsg, L + 8, T + GUI_H - 30, GUI_W - 16, KveGuiStyle.TEXT_WARN);
        }
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double delta) {
        int size = currentSize();
        if (size > listRows && my >= listY) {
            scroll = Math.max(0, Math.min(size - listRows, scroll - (int) Math.signum(delta)));
            init();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, delta);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && getFocused() instanceof EditBox) { addAction.run(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        if (EditorKeys.isFocusTab(key, mods)) { super.keyPressed(key, scan, mods); return true; }
        return EditorKeys.swallow(mods);
    }

    @Override public boolean isPauseScreen() { return false; }
}
