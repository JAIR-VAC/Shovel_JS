package com.kve.client.gui;

import com.kve.common.Lang;
import com.kve.client.permission.ClientPermissionState;
import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.client.gui.studio.KveStudioShell;
import com.kve.client.worldgen.TerrainSketch;
import com.kve.common.kubejs.SemanticValidator;
import com.kve.common.worldgen.TerrainPresetExporter;
import com.kve.common.worldgen.VanillaOres;
import com.kve.common.worldgen.WorldGenEntry;
import com.kve.common.worldgen.WorldGenExporter;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class WorldGenEditorScreen extends Screen {

    private static final int GUI_W_MIN = 520;
    private static final int GUI_H_MIN = 320;
    private int GUI_W = GUI_W_MIN;
    private int GUI_H = GUI_H_MIN;
    private static final int OUTER_PAD = 4;
    private static final int VEIN_ROW_H = 26;
    private int VEIN_ROWS_VISIBLE = 4;
    private static final int LEFT_COL_W = 250;
    private static final int RIGHT_COL_X_OFFSET = LEFT_COL_W + 16;
    private int RIGHT_COL_W = GUI_W_MIN - RIGHT_COL_X_OFFSET - 8;
    private static final int PRESET_ROW_H = 14;

    private final Screen parent;
    private String currentKey;

    private boolean hosted = false;
    private int hostedW, hostedH;
    private Screen ownerTab = null;
    private Screen shellReturnScreen = null;
    private Runnable onEmbeddedExit = null;

    /** Fluent setter, llamado por {@code WorldGenListScreen} al embeber esta pantalla - ver campos de arriba. */
    public WorldGenEditorScreen withEmbedding(Screen ownerTab, Screen shellReturnScreen, Runnable onEmbeddedExit) {
        this.ownerTab = ownerTab;
        this.shellReturnScreen = shellReturnScreen;
        this.onEmbeddedExit = onEmbeddedExit;
        return this;
    }

    private String baselineJson = "";

    private String draftId = "";
    private String draftDisplayName = "";
    private boolean draftEnabled = true;
    private final List<String> draftBiomeIds = new ArrayList<>();
    private final List<WorldGenEntry.OreVein> draftVeins = new ArrayList<>();
    private WorldGenEntry.TerrainPreset draftPreset = null;
    private WorldGenEntry.PresetTarget draftPresetTarget = WorldGenEntry.PresetTarget.LAB;
    private final List<String> draftRemovedFeatures = new ArrayList<>();
    private final List<WorldGenEntry.SpawnRule> draftSpawns = new ArrayList<>();
    private final List<WorldGenEntry.Patch> draftPatches = new ArrayList<>();

    private int L, T;
    private String flashMsg = "";
    private long flashAt = 0;

    private ScrollBar veinsScrollBar;
    private int idLabelY, nameLabelY;
    private boolean moreOpen = false;
    private int sketchIndex = 0;
    private TerrainSketch sketch;
    private String sketchKey = "";
    private int mapX, mapY, mapSide, stripY, stripH, legendY, sketchHeaderY, disclaimerY;

    public WorldGenEditorScreen(Screen parent) {
        this(parent, null);
    }

    public WorldGenEditorScreen(Screen parent, WorldGenEntry existingOrNull) {
        super(Lang.c("gui.kve.worldgen.editor.title"));
        this.parent = parent;
        if (existingOrNull != null) {
            applyEntryToDraft(existingOrNull);
        } else {
            currentKey = "";
        }
        markClean();
    }

    private String snapshot() {
        return WorldGenExporter.entryToJson(buildDraftEntry()).toString();
    }

    private void markClean() { baselineJson = snapshot(); }

    private boolean isDirty() { return !snapshot().equals(baselineJson); }

    /**
     * Copia cada campo de {@code e} al draft (vetas clonadas, nunca
     * compartidas con la entry viva de {@link WorldGenExporter}) sin tocar
     * widgets/init() - compartido por el constructor (pantalla todavia no
     * abierta) y {@link #loadDraft()} (Undo/Reset sobre pantalla ya abierta),
     * mismo reparto que EventEditorScreen#applyEntryToDraft().
     */
    private void applyEntryToDraft(WorldGenEntry e) {
        currentKey = e.key();
        draftId = e.id;
        draftDisplayName = e.displayName;
        draftEnabled = e.enabled;
        draftBiomeIds.clear();
        draftBiomeIds.addAll(e.biomeIds);
        draftVeins.clear();
        for (WorldGenEntry.OreVein v : e.oreVeins) draftVeins.add(v.copy());
        draftPreset = e.terrainPreset;
        draftPresetTarget = e.presetTarget;
        draftRemovedFeatures.clear();
        draftRemovedFeatures.addAll(e.removedFeatures);
        draftSpawns.clear();
        for (WorldGenEntry.SpawnRule r : e.spawns) draftSpawns.add(r.copy());
        draftPatches.clear();
        for (WorldGenEntry.Patch p : e.patches) draftPatches.add(p.copy());
    }

    @Override
    protected void init() {
        if (KveStudioShell.reclaimIfHosted(this, ownerTab != null ? ownerTab : this, shellReturnScreen)) return;
        hosted = false;
        GUI_W = Math.max(GUI_W_MIN, width - OUTER_PAD * 2);
        GUI_H = Math.max(GUI_H_MIN, height - OUTER_PAD * 2);
        RIGHT_COL_W = GUI_W - RIGHT_COL_X_OFFSET - 8;
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        buildWidgets();
    }

    /** Overload de {@link KveStudioTab}: WorldGenListScreen llama esto directo (sin minecraft.setScreen()) para embeber esta pantalla en el rectangulo que el shell le asigno a la lista. */
    public void init(int x0, int y0, int w, int h) {
        if (this.minecraft == null) {
            this.minecraft = net.minecraft.client.Minecraft.getInstance();
            this.font = this.minecraft.font;
        }
        hosted = true;
        hostedW = w;
        hostedH = h;
        GUI_W = Math.max(GUI_W_MIN, w);
        GUI_H = Math.max(GUI_H_MIN, h);
        RIGHT_COL_W = GUI_W - RIGHT_COL_X_OFFSET - 8;
        L = x0;
        T = y0;
        buildWidgets();
    }

    private void refresh() {
        if (hosted) {
            GUI_W = Math.max(GUI_W_MIN, hostedW);
            GUI_H = Math.max(GUI_H_MIN, hostedH);
            RIGHT_COL_W = GUI_W - RIGHT_COL_X_OFFSET - 8;
            buildWidgets();
        } else {
            init();
        }
    }

    private void buildWidgets() {
        clearWidgets();
        setFocused(null);

        addRenderableWidget(KveGuiStyle.smartBtn(L + 4, T + 2, 0, "< " + Lang.get("gui.kve.btn.back"), b -> goBack()));

        int x0 = L + 8;
        int halfW = (LEFT_COL_W - 6) / 2;
        int y = T + 18;
        generalHeaderY = y;
        y += SECTION_H;
        idLabelY = y;
        nameLabelY = y;
        y += 10;
        EditBox idBox = field(x0, y, halfW, draftId, Lang.c("gui.kve.worldgen.editor.id"));
        idBox.setResponder(s -> draftId = s);
        addRenderableWidget(idBox);
        EditBox nameBox = field(x0 + halfW + 6, y, halfW, draftDisplayName, Lang.c("gui.kve.worldgen.editor.display_name"));
        nameBox.setResponder(s -> draftDisplayName = s);
        addRenderableWidget(nameBox);
        y += 20;

        biomesHeaderY = y;
        y += SECTION_H;
        addRenderableWidget(new KvePlateButton(x0, y, LEFT_COL_W, 14,
                Component.literal(Lang.get("gui.kve.worldgen.editor.pick_biomes") + " (" + draftBiomeIds.size() + ")"),
                b -> minecraft.setScreen(new BiomePickerScreen(this, draftBiomeIds, false,
                        picked -> { draftBiomeIds.clear(); draftBiomeIds.addAll(picked); sketchIndex = 0; refresh(); }))));
        y += 16;
        WorldGenEntry.Dimension currentDim = WorldGenEntry.Dimension.of(draftBiomeIds);
        WorldGenEntry.Dimension[] dims = WorldGenEntry.Dimension.values();
        int dimW = (LEFT_COL_W - 4) / dims.length;
        for (int i = 0; i < dims.length; i++) {
            WorldGenEntry.Dimension d = dims[i];
            addRenderableWidget(new KvePlateButton(x0 + i * (dimW + 2), y, dimW, 14,
                    Lang.c("gui.kve.worldgen.dim." + d.name().toLowerCase(java.util.Locale.ROOT)), b -> setWholeDimension(d))
                    .withSelected(d == currentDim));
        }
        y += 16;
        summaryY = y;
        y += 12;
        int extrasCount = draftRemovedFeatures.size() + draftSpawns.size() + draftPatches.size();
        addRenderableWidget(new KvePlateButton(x0, y, LEFT_COL_W, 14,
                Component.literal(Lang.get("gui.kve.worldgen.extras.open", extrasCount)),
                b -> minecraft.setScreen(new BiomeExtrasScreen(this, draftRemovedFeatures, draftSpawns, draftPatches, r -> {
                    draftRemovedFeatures.clear();
                    draftRemovedFeatures.addAll(r.removedFeatures());
                    draftSpawns.clear();
                    draftSpawns.addAll(r.spawns());
                    draftPatches.clear();
                    draftPatches.addAll(r.patches());
                }))));
        y += 20;

        veinsHeaderY = y;
        y += SECTION_H + 2;
        // dos formas de agregar una veta, con su nombre: desde cero, o eligiendo un mineral de la lista
        String addVein = Lang.get("gui.kve.worldgen.editor.add_vein");
        String fromTpl = Lang.get("gui.kve.worldgen.template.menu");
        int addVeinW = KveGuiStyle.btnWidth(addVein), tplMenuW = KveGuiStyle.btnWidth(fromTpl);
        addRenderableWidget(new KvePlateButton(x0, y, addVeinW, 14, Component.literal(addVein), b -> openVeinEditor(-1)).withPrimary(true));
        List<KveMenuButton.Item> tplItems = new ArrayList<>();
        for (VanillaOres.Template t : VanillaOres.TEMPLATES) {
            tplItems.add(new KveMenuButton.Item(Lang.get("gui.kve.worldgen.template." + t.key()), () -> addTemplateVein(t), false, true));
        }
        KveMenuButton tplMenu = new KveMenuButton(x0 + addVeinW + KveGuiStyle.BTN_GAP, y, tplMenuW, 14, Component.literal(fromTpl), tplItems);
        tplMenu.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c("gui.kve.worldgen.template.menu_tip")));
        addRenderableWidget(tplMenu);
        y += 18;
        veinsListY = y;

        int btnY = T + GUI_H - KveGuiStyle.BTN_H - 6;
        KveGuiStyle.ButtonRow actions = new KveGuiStyle.ButtonRow(x0, btnY, x0 + LEFT_COL_W, true);
        KveGuiStyle.ActionBar bar = new KveGuiStyle.ActionBar(actions, this::addRenderableWidget);
        bar.primary(Lang.get("gui.kve.btn.save"), b -> save());
        bar.button(Lang.get("gui.kve.btn.preview"), b -> preview());
        bar.button(Lang.get("gui.kve.btn.duplicate"), b -> duplicateEntry());
        bar.more(Lang.get("gui.kve.worldgen.veins_half"), () -> scaleVeins(0.5), !draftVeins.isEmpty())
                .more(Lang.get("gui.kve.worldgen.veins_double"), () -> scaleVeins(2.0), !draftVeins.isEmpty())
                .more(Lang.get("gui.kve.worldgen.copy_biomes"), () -> copyToBiomes())
                .more(Lang.get("gui.kve.worldgen.to_library"), () -> exportToLibrary())
                .history(Lang.get("gui.kve.btn.undo"), () -> undo(), !currentKey.isEmpty() && WorldGenExporter.canUndo(currentKey))
                .history(Lang.get("gui.kve.btn.reset"), () -> resetDraft());
        bar.enabled(draftEnabled, b -> { draftEnabled = !draftEnabled; refresh(); });
        bar.end(() -> delete());
        int actionTop = actions.topY();
        flashY = actionTop - 11;
        VEIN_ROWS_VISIBLE = Math.max(1, (flashY - 2 - veinsListY) / VEIN_ROW_H);
        int keepScroll = veinsScrollBar != null ? veinsScrollBar.getScroll() : 0;
        veinsScrollBar = new ScrollBar(L + LEFT_COL_W + 2, veinsListY, VEIN_ROWS_VISIBLE * VEIN_ROW_H, draftVeins.size(), VEIN_ROWS_VISIBLE);
        veinsScrollBar.setScroll(scrollVeinsToEnd ? Integer.MAX_VALUE : keepScroll);
        scrollVeinsToEnd = false;
        int veinsScroll = veinsScrollBar.getScroll();
        for (int i = 0; i < Math.min(VEIN_ROWS_VISIBLE, draftVeins.size() - veinsScroll); i++) {
            int ry = veinsListY + i * VEIN_ROW_H;
            int idx = i + veinsScroll;
            int removeX = L + LEFT_COL_W - 14;
            addRenderableWidget(new KvePlateButton(removeX, ry + 5, 12, 14, Component.literal("x"), b -> { draftVeins.remove(idx); refresh(); }));
            addRenderableWidget(new KvePlateButton(removeX - 30, ry + 5, 28, 14,
                    Lang.c("gui.kve.worldgen.vein.dup_btn"), b -> duplicateVein(idx)));
            KvePlateButton up = new KvePlateButton(removeX - 44, ry + 1, 12, 11, Component.literal("▲"), b -> moveVein(idx, -1));
            KvePlateButton down = new KvePlateButton(removeX - 44, ry + 12, 12, 11, Component.literal("▼"), b -> moveVein(idx, 1));
            up.active = idx > 0;
            down.active = idx < draftVeins.size() - 1;
            addRenderableWidget(up);
            addRenderableWidget(down);
            WorldGenEntry.OreVein v = draftVeins.get(idx);
            if (!VanillaOres.vanillaFeaturesFor(v.oreBlock).isEmpty()) {
                String vl = Lang.get("gui.kve.worldgen.vein.no_vanilla");
                int vw = Math.min(KveGuiStyle.btnWidth(vl) - 8, 84);
                addRenderableWidget(new KvePlateButton(veinTextRight() - vw, ry + 12, vw, 11, Component.literal(vl),
                        b -> toggleVanilla(idx)).withSelected(VanillaOres.vanillaRemoved(v.oreBlock, draftRemovedFeatures)));
            }
        }

        int rx = L + RIGHT_COL_X_OFFSET;
        int rw = RIGHT_COL_W;
        int ry = T + 18;
        presetsHeaderY = ry;
        ry += SECTION_H;
        WorldGenEntry.TerrainPreset[] presets = WorldGenEntry.TerrainPreset.values();
        int cols = 3;
        int presetColW = (rw - (cols - 1) * 3) / cols;
        for (int i = 0; i < presets.length; i++) {
            WorldGenEntry.TerrainPreset preset = presets[i];
            int px = rx + (i % cols) * (presetColW + 3);
            int py = ry + (i / cols) * (PRESET_ROW_H + 2);
            addRenderableWidget(new KvePlateButton(px, py, presetColW, PRESET_ROW_H, Component.literal(presetLabel(preset)),
                    b -> { draftPreset = (draftPreset == preset) ? null : preset; refresh(); }).withSelected(draftPreset == preset));
        }
        ry += ((presets.length + cols - 1) / cols) * (PRESET_ROW_H + 2) + 2;
        disclaimerY = -1;
        if (draftPreset != null) {
            String goLabel = Lang.get(inLabDimension() ? "gui.kve.worldgen.preset.go_back" : "gui.kve.worldgen.preset.goto");
            int goW = KveGuiStyle.btnWidth(goLabel);
            boolean mainWorld = draftPreset.supportsMainWorld() && draftPresetTarget == WorldGenEntry.PresetTarget.MAIN_WORLD;
            KvePlateButton targetBtn = new KvePlateButton(rx, ry, rw - goW - 4, PRESET_ROW_H,
                    Lang.c(mainWorld ? "gui.kve.worldgen.preset.target_main" : "gui.kve.worldgen.preset.target_lab"),
                    b -> togglePresetTarget());
            targetBtn.active = draftPreset.supportsMainWorld();
            addRenderableWidget(targetBtn);
            KvePlateButton goBtn = new KvePlateButton(rx + rw - goW, ry, goW, PRESET_ROW_H,
                    Component.literal(goLabel), b -> gotoLabDimension());
            goBtn.active = !mainWorld || inLabDimension();
            addRenderableWidget(goBtn);
            ry += PRESET_ROW_H + 3;
            disclaimerY = ry;
            ry += 22;
        }
        ry += 4;
        sketchHeaderY = ry;
        if (draftBiomeIds.size() > 1) {
            addRenderableWidget(new KvePlateButton(rx + rw - 28, ry - 3, 12, 14, Component.literal("<"),
                    b -> { sketchIndex--; refresh(); }));
            addRenderableWidget(new KvePlateButton(rx + rw - 14, ry - 3, 12, 14, Component.literal(">"),
                    b -> { sketchIndex++; refresh(); }));
        }
        ry += SECTION_H + 2;

        int testY = T + GUI_H - KveGuiStyle.BTN_H - 6;
        int testW = (rw - 4) / 2;
        addRenderableWidget(new KvePlateButton(rx, testY, testW, 14, Lang.c("gui.kve.worldgen.test.run"), b -> runLiveTest()));
        KvePlateButton undoTest = new KvePlateButton(rx + testW + 4, testY, testW, 14, Lang.c("gui.kve.worldgen.test.undo"), b -> undoLiveTest());
        undoTest.active = com.kve.client.worldgen.WorldGenTestPlacer.canUndo();
        addRenderableWidget(undoTest);

        legendY = testY - 12;
        stripH = 46;
        stripY = legendY - stripH - 3;
        int mapBottom = stripY - 6;
        mapSide = Math.max(40, Math.min(rw, mapBottom - ry));
        mapX = rx + (rw - mapSide) / 2;
        mapY = ry;
    }

    private int veinsListY;
    private int generalHeaderY, biomesHeaderY, veinsHeaderY, presetsHeaderY;
    private static final int SECTION_H = 12;
    private int flashY;
    private int summaryY;

    private void sectionHeader(GuiGraphics g, String text, int x, int y, int w) {
        KveGuiStyle.drawTextSafe(g, text, x, y, w, KveGuiStyle.ACCENT_GOLD);
        int tw = Math.min(w, Math.round(font.width(text) * KveGuiStyle.uiTextScale()));
        if (tw + 6 < w) g.fill(x + tw + 4, y + 4, x + w, y + 5, KveGuiStyle.BORDER_FIELD);
    }

    private String presetLabel(WorldGenEntry.TerrainPreset p) {
        return Lang.get("gui.kve.worldgen.preset." + p.name().toLowerCase());
    }

    private String biomesSummaryText() {
        if (draftBiomeIds.isEmpty()) return Lang.get("gui.kve.worldgen.editor.no_biomes");
        return String.join(", ", draftBiomeIds);
    }

    private void openVeinEditor(int index) {
        WorldGenEntry.OreVein existing = index >= 0 && index < draftVeins.size() ? draftVeins.get(index) : null;
        minecraft.setScreen(new OreVeinEditScreen(this, existing, vein -> {
            if (index >= 0 && index < draftVeins.size()) {
                draftVeins.set(index, vein);
            } else {
                WorldGenEntry.Dimension dim = WorldGenEntry.Dimension.of(draftBiomeIds);
                if (dim != null && WorldGenEntry.Dimension.isDefaultTarget(vein.replaceableTarget)) vein.replaceableTarget = dim.oreTarget;
                draftVeins.add(vein);
            }
            minecraft.setScreen(this);
        }));
    }

    private void setWholeDimension(WorldGenEntry.Dimension d) {
        draftBiomeIds.clear();
        draftBiomeIds.add(d.biomeTag);
        for (WorldGenEntry.OreVein v : draftVeins) {
            if (WorldGenEntry.Dimension.isDefaultTarget(v.replaceableTarget)) v.replaceableTarget = d.oreTarget;
        }
        flash(Lang.get("gui.kve.worldgen.dim_set", Lang.get("gui.kve.worldgen.dim." + d.name().toLowerCase(java.util.Locale.ROOT))));
        refresh();
    }

    private void togglePresetTarget() {
        if (draftPreset == null || !draftPreset.supportsMainWorld()) return;
        if (draftPresetTarget == WorldGenEntry.PresetTarget.LAB) {
            String other = WorldGenExporter.mainWorldPresetOwner(currentKey);
            if (other != null) { flash(Lang.get("gui.kve.worldgen.preset.main_taken", other)); return; }
            WorldGenEntry.TerrainPreset preset = draftPreset;
            String msgKey = preset == WorldGenEntry.TerrainPreset.OCEANIC
                    ? "gui.kve.worldgen.preset.main_confirm_oceanic" : "gui.kve.worldgen.preset.main_confirm_msg";
            minecraft.setScreen(new KveConfirmScreen(
                    confirmed -> {
                        minecraft.setScreen(this);
                        if (confirmed && draftPreset == preset) {
                            draftPresetTarget = WorldGenEntry.PresetTarget.MAIN_WORLD;
                            refresh();
                        }
                    },
                    Component.literal(Lang.get("gui.kve.worldgen.preset.main_confirm_title")),
                    Component.literal(Lang.get(msgKey, presetLabel(preset), TerrainPresetExporter.OCEANIC_SEA_LEVEL))));
            return;
        } else {
            draftPresetTarget = WorldGenEntry.PresetTarget.LAB;
        }
        refresh();
    }

    private boolean inLabDimension() {
        if (minecraft == null || minecraft.level == null) return false;
        ResourceLocation here = minecraft.level.dimension().location();
        return here.getNamespace().equals("shovel_js") && here.getPath().startsWith("worldgen_preview_");
    }

    private void gotoLabDimension() {
        if (draftPreset == null || minecraft.player == null) return;
        if (inLabDimension()) {
            com.kve.common.network.KVENetwork.requestLabTeleport("");
            return;
        }
        WorldGenEntry e = buildDraftEntry();
        if (e.id.isEmpty()) { flash(Lang.get("gui.kve.event.type_id_first")); return; }
        String dim = TerrainPresetExporter.labDimensionId(e);
        boolean exists = minecraft.player.connection.levels().stream().anyMatch(k -> k.location().toString().equals(dim));
        if (!exists) { flash(Lang.get("gui.kve.worldgen.preset.goto_missing", dim)); return; }
        com.kve.common.network.KVENetwork.requestLabTeleport(dim);
        flash(Lang.get("gui.kve.worldgen.preset.goto_sent", dim));
    }

    private void copyToBiomes() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        minecraft.setScreen(new BiomePickerScreen(this, new ArrayList<>(), false, picked -> {
            if (picked.isEmpty()) return;
            duplicateEntry();
            draftBiomeIds.clear();
            draftBiomeIds.addAll(picked);
            flash(Lang.get("gui.kve.worldgen.copied_to_biomes", picked.size()));
            refresh();
        }));
    }

    private void exportToLibrary() {
        WorldGenEntry e = buildDraftEntry();
        if (e.id.isEmpty()) { flash(Lang.get("gui.kve.event.type_id_first")); return; }
        try {
            String name = e.displayName.isBlank() ? e.id : e.displayName;
            java.nio.file.Path file = com.kve.common.packtools.TemplateLibrary.exportWorldGenRule(e, name);
            flash(Lang.get("gui.kve.worldgen.library_saved", file.getFileName().toString()));
        } catch (Exception ex) {
            flash(Lang.get("gui.kve.report.failed", ex.getMessage()));
        }
    }

    private void runLiveTest() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        com.kve.client.worldgen.WorldGenTestPlacer.placeAtPlayer(buildDraftEntry()).thenAccept(r -> minecraft.execute(() -> {
            if (r.error() == null) {
                flash(r.changedBlocks() == 0 ? Lang.get("gui.kve.worldgen.test.none_changed")
                        : Lang.get("gui.kve.worldgen.test.done", r.changedBlocks(), r.features()));
            } else {
                switch (r.error()) {
                    case "no_local" -> flash(Lang.get("gui.kve.worldgen.test.no_local"));
                    case "nothing" -> flash(Lang.get("gui.kve.worldgen.test.nothing"));
                    case "not_loaded", "no_level" -> flash(Lang.get("gui.kve.worldgen.test.not_loaded"));
                    default -> flash(Lang.get("gui.kve.worldgen.test.failed", r.error()));
                }
            }
            refresh();
        }));
    }

    private void undoLiveTest() {
        com.kve.client.worldgen.WorldGenTestPlacer.undo().thenAccept(n -> minecraft.execute(() -> {
            flash(Lang.get("gui.kve.worldgen.test.undone", n));
            refresh();
        }));
    }

    private void duplicateVein(int index) {
        if (index < 0 || index >= draftVeins.size()) return;
        draftVeins.add(index + 1, draftVeins.get(index).copyWithNewId());
        refresh();
    }

    private boolean scrollVeinsToEnd = false;

    private void addTemplateVein(VanillaOres.Template t) {
        WorldGenEntry.OreVein v = t.create();
        WorldGenEntry.Dimension dim = WorldGenEntry.Dimension.of(draftBiomeIds);
        if (t.overworld() && dim != null && dim != WorldGenEntry.Dimension.OVERWORLD) {
            v.replaceableTarget = dim.oreTarget;
            v.deepslateOreBlock = "";
        }
        draftVeins.add(v);
        scrollVeinsToEnd = true;
        flash(Lang.get("gui.kve.worldgen.template.added", Lang.get("gui.kve.worldgen.template." + t.key())));
        refresh();
    }

    private void scaleVeins(double factor) {
        if (draftVeins.isEmpty()) return;
        for (WorldGenEntry.OreVein v : draftVeins) {
            if (v.rarity > 0) {
                v.rarity = Math.max(1, (int) Math.round(v.rarity / factor));
            } else {
                v.veinsPerChunk = Math.max(1, Math.min(WorldGenEntry.OreVein.MAX_VEINS_PER_CHUNK, (int) Math.round(v.veinsPerChunk * factor)));
            }
        }
        flash(Lang.get(factor > 1 ? "gui.kve.worldgen.scale.more" : "gui.kve.worldgen.scale.less"));
        refresh();
    }

    private void moveVein(int index, int delta) {
        int to = index + delta;
        if (index < 0 || index >= draftVeins.size() || to < 0 || to >= draftVeins.size()) return;
        java.util.Collections.swap(draftVeins, index, to);
        refresh();
    }

    private void toggleVanilla(int index) {
        if (index < 0 || index >= draftVeins.size()) return;
        String ore = draftVeins.get(index).oreBlock;
        List<String> features = VanillaOres.vanillaFeaturesFor(ore);
        if (features.isEmpty()) return;
        if (VanillaOres.vanillaRemoved(ore, draftRemovedFeatures)) {
            draftRemovedFeatures.removeAll(features);
            flash(Lang.get("gui.kve.worldgen.vein.vanilla_kept"));
        } else {
            for (String f : features) if (!draftRemovedFeatures.contains(f)) draftRemovedFeatures.add(f);
            flash(Lang.get("gui.kve.worldgen.vein.vanilla_removed", features.size()));
        }
        refresh();
    }

    private WorldGenEntry buildDraftEntry() {
        WorldGenEntry e = new WorldGenEntry();
        e.id = draftId.trim();
        e.displayName = draftDisplayName.trim();
        e.enabled = draftEnabled;
        e.biomeIds.addAll(draftBiomeIds);
        for (WorldGenEntry.OreVein v : draftVeins) e.oreVeins.add(v.copy());
        e.terrainPreset = draftPreset;
        e.presetTarget = draftPresetTarget;
        e.removedFeatures.addAll(draftRemovedFeatures);
        for (WorldGenEntry.SpawnRule r : draftSpawns) e.spawns.add(r.copy());
        for (WorldGenEntry.Patch p : draftPatches) e.patches.add(p.copy());
        return e;
    }

    private List<String> validateBiomesAgainstRegistry(List<String> biomeIds) {
        List<String> problems = new ArrayList<>();
        RegistryAccess access = resolveRegistryAccess();
        if (access == null) return problems; // sin mundo cargado: no se puede verificar, no bloquea el guardado.
        try {
            var registry = access.registryOrThrow(Registries.BIOME);
            for (String id : biomeIds) {
                if (id == null || id.isBlank()) continue;
                if (id.startsWith("#")) {
                    String tagId = id.substring(1);
                    boolean tagExists = registry.getTagNames()
                            .anyMatch(tag -> tag.location().toString().equals(tagId));
                    if (!tagExists) problems.add(Lang.get("gui.kve.worldgen.editor.biome_tag_unknown", id));
                } else {
                    net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
                    if (rl == null || !registry.containsKey(rl)) {
                        problems.add(Lang.get("gui.kve.worldgen.editor.biome_unknown", id));
                    }
                }
            }
        } catch (Exception ignored) {
            // Mismo criterio que BiomePickerScreen#refreshSource: si el
            // registro no está disponible por la razón que sea, no bloquear
            // el guardado por algo que no se puede confirmar.
        }
        return problems;
    }

    private RegistryAccess resolveRegistryAccess() {
        if (minecraft == null) return null;
        if (minecraft.getSingleplayerServer() != null) {
            try { return minecraft.getSingleplayerServer().registryAccess(); } catch (Exception ignored) {}
        }
        if (minecraft.level != null) {
            try { return minecraft.level.registryAccess(); } catch (Exception ignored) {}
        }
        return null;
    }

    private List<String> validateOreVeinsAgainstRegistry(List<WorldGenEntry.OreVein> veins) {
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < veins.size(); i++) {
            WorldGenEntry.OreVein v = veins.get(i);
            String veinLabel = Lang.get("gui.kve.worldgen.editor.vein_label", i + 1);
            if (v.oreBlock != null && !v.oreBlock.isBlank()) {
                problems.addAll(SemanticValidator.validateBlockId(v.oreBlock, veinLabel));
            }
            if (v.deepslateOreBlock != null && !v.deepslateOreBlock.isBlank()) {
                problems.addAll(SemanticValidator.validateBlockId(v.deepslateOreBlock, veinLabel));
            }
            checkReplaceableTag(v.replaceableTarget, veinLabel, problems);
            checkReplaceableTag(v.deepslateReplaceableTarget, veinLabel, problems);
        }
        return problems;
    }

    /**
     * Un id inexistente en un remove_features/add_spawns no es un
     * no-op - el HolderSet no decodifica y el mundo no carga. Las placed
     * features solo existen en el servidor, asi que se chequean contra el
     * integrado (singleplayer); en multijugador no se puede, no bloquea.
     */
    private List<String> validateExtrasAgainstRegistry(WorldGenEntry e) {
        List<String> problems = new ArrayList<>();
        if (minecraft != null && minecraft.getSingleplayerServer() != null && !e.removedFeatures.isEmpty()) {
            try {
                RegistryAccess access = minecraft.getSingleplayerServer().registryAccess();
                var placed = access.registryOrThrow(Registries.PLACED_FEATURE);
                for (String id : e.removedFeatures) {
                    String t = id.trim();
                    if (t.startsWith("#")) {
                        ResourceLocation rl = ResourceLocation.tryParse(t.substring(1));
                        if (rl == null || placed.getTag(TagKey.create(Registries.PLACED_FEATURE, rl)).isEmpty()) {
                            problems.add(Lang.get("gui.kve.worldgen.extras.unknown_feature", t));
                        }
                    } else {
                        ResourceLocation rl = ResourceLocation.tryParse(t);
                        if (rl == null || !placed.containsKey(rl)) problems.add(Lang.get("gui.kve.worldgen.extras.unknown_feature", t));
                    }
                }
            } catch (Exception ignored) {
                // registro no disponible: no se puede verificar, no bloquea.
            }
        }
        for (WorldGenEntry.SpawnRule r : e.spawns) {
            String t = r.entityId.trim();
            if (t.startsWith("#")) continue;
            ResourceLocation rl = ResourceLocation.tryParse(t);
            if (rl == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) problems.add(Lang.get("gui.kve.worldgen.extras.unknown_entity", t));
        }
        for (WorldGenEntry.Patch p : e.patches) {
            problems.addAll(SemanticValidator.validateBlockId(p.block.trim(), Lang.get("gui.kve.worldgen.extras.tab.patches")));
        }
        return problems;
    }

    private void checkReplaceableTag(String id, String veinLabel, List<String> problems) {
        if (id == null || id.isBlank()) return;
        if (!SemanticValidator.blockRegistryReady()) return;
        String bare = id.startsWith("#") ? id.substring(1) : id;
        ResourceLocation rl = ResourceLocation.tryParse(bare);
        boolean known = rl != null && BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, rl)).isPresent();
        if (!known) problems.add(Lang.get("gui.kve.worldgen.editor.vein_target_unknown", veinLabel, id));
    }

    private void save() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        WorldGenEntry e = buildDraftEntry();
        if (e.id == null || e.id.isBlank()) {
            e.id = KveGuiStyle.randomId();
            draftId = e.id;
        }
        List<String> problems = WorldGenExporter.validateEntry(e);
        problems.addAll(validateBiomesAgainstRegistry(e.biomeIds));
        problems.addAll(validateOreVeinsAgainstRegistry(e.oreVeins));
        problems.addAll(validateExtrasAgainstRegistry(e));
        if (!problems.isEmpty()) { flash(String.join(" ", problems)); return; }

        String oldKey = (!currentKey.isEmpty() && !currentKey.equals(e.key())) ? currentKey : null;
        WorldGenEntry previousUnderOldKey = oldKey != null ? WorldGenExporter.getEntry(oldKey) : null;
        WorldGenEntry previous = WorldGenExporter.getEntry(e.key());
        PendingChange.Action action = previous == null ? PendingChange.Action.CREATE : PendingChange.Action.EDIT;

        if (oldKey != null) WorldGenExporter.stageDelete(oldKey);
        WorldGenExporter.stageEntry(e);

        PendingChangeManager.queue(PendingChange.of(PendingDomain.WORLDGEN, e.key(), action, true,
                () -> WorldGenExporter.commitWorldGen(),
                () -> {
                    WorldGenExporter.discardStagedEntry(e.key(), previous);
                    if (oldKey != null) WorldGenExporter.discardStagedEntry(oldKey, previousUnderOldKey);
                })
                .withDiff(previous == null ? null : () -> WorldGenExporter.previewWorldGenJson(previous),
                        () -> WorldGenExporter.previewWorldGenJson(e)));

        currentKey = e.key();
        markClean();
        flash(Lang.get("gui.kve.pending.queued_hint_restart") + conflictNote(e.key()));
        refresh();
    }

    private String conflictNote(String key) {
        for (var c : com.kve.common.conflict.ConflictDetector.worldGenConflicts(WorldGenExporter.getEntries())) {
            if (!c.involves(key)) continue;
            return " ⚠ " + ConflictsScreen.describe(c);
        }
        return "";
    }

    private void duplicateEntry() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        currentKey = "";
        String base = draftId.trim();
        String candidate = (base.isEmpty() ? "shovel_js:worldgen" : base) + "_copy";
        String finalId = candidate;
        int n = 2;
        while (WorldGenExporter.getEntry(finalId) != null) {
            finalId = candidate + n;
            n++;
        }
        draftId = finalId;
        draftVeins.replaceAll(WorldGenEntry.OreVein::copyWithNewId);
        flash(Lang.get("gui.kve.worldgen.duplicated_hint"));
        refresh();
    }

    private void delete() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        if (currentKey.isEmpty()) { flash(Lang.get("gui.kve.event.select_first")); return; }
        minecraft.setScreen(new KveConfirmScreen(
                confirmed -> {
                    minecraft.setScreen(this);
                    if (confirmed) doDelete();
                },
                Component.literal(Lang.get("gui.kve.worldgen.delete_confirm_title")),
                Component.literal(Lang.get("gui.kve.worldgen.delete_confirm_msg",
                        draftDisplayName.isEmpty() ? draftId : draftDisplayName, draftVeins.size()))));
    }

    private void resetDraft() {
        WorldGenEntry saved = currentKey.isEmpty() ? null : WorldGenExporter.getEntry(currentKey);
        if (saved != null) {
            flash(Lang.get("gui.kve.worldgen.reset_saved_hint"));
            loadDraft(saved);
        } else {
            currentKey = "";
            draftId = "";
            draftDisplayName = "";
            draftEnabled = true;
            draftBiomeIds.clear();
            draftVeins.clear();
            draftPreset = null;
            draftPresetTarget = WorldGenEntry.PresetTarget.LAB;
            draftRemovedFeatures.clear();
            draftSpawns.clear();
            draftPatches.clear();
            markClean();
            flash(Lang.get("gui.kve.worldgen.reset_blank_hint"));
            refresh();
        }
    }

    private void loadDraft(WorldGenEntry e) {
        applyEntryToDraft(e);
        markClean();
        refresh();
    }

    private void preview() {
        WorldGenEntry e = buildDraftEntry();
        if (e.id.isEmpty()) { flash(Lang.get("gui.kve.event.type_id_first")); return; }
        minecraft.setScreen(new RecipePreviewScreen(this, e.id, WorldGenExporter.previewWorldGenJson(e)));
    }

    private void undo() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        if (currentKey.isEmpty()) return;
        String key = currentKey;
        WorldGenEntry beforeUndo = WorldGenExporter.getEntry(key);
        WorldGenEntry restored = WorldGenExporter.stageUndo(key);
        if (restored == null) return;
        flash(Lang.get("gui.kve.pending.queued_hint_restart"));
        loadDraft(restored);
        PendingChangeManager.queue(PendingChange.of(PendingDomain.WORLDGEN, key, PendingChange.Action.EDIT, true,
                () -> WorldGenExporter.commitWorldGen(),
                () -> WorldGenExporter.discardStagedUndo(key, restored, beforeUndo))
                .withDiff(() -> WorldGenExporter.previewWorldGenJson(beforeUndo == null ? restored : beforeUndo),
                        () -> WorldGenExporter.previewWorldGenJson(restored)));
    }

    private void doDelete() {
        String key = currentKey;
        WorldGenEntry previous = WorldGenExporter.getEntry(key);
        WorldGenExporter.stageDelete(key);
        PendingChangeManager.queue(PendingChange.of(PendingDomain.WORLDGEN, key, PendingChange.Action.DELETE, true,
                () -> WorldGenExporter.commitWorldGen(),
                () -> { if (previous != null) WorldGenExporter.stageEntry(previous); })
                .withDiff(previous == null ? null : () -> WorldGenExporter.previewWorldGenJson(previous), null));
        flash(Lang.get("gui.kve.pending.queued_hint_restart"));
        exit();
    }

    private EditBox field(int x, int y, int w, String value, Component narration) {
        EditBox f = new KveScaledEditBox(font, x, y, w, 12, narration);
        f.setMaxLength(128);
        f.setValue(value);
        return f;
    }

    private void flash(String msg) { flashMsg = msg; flashAt = System.currentTimeMillis(); }

    private void goBack() {
        if (isDirty()) {
            minecraft.setScreen(new KveConfirmScreen(
                    leave -> {
                        minecraft.setScreen(this);
                        if (leave) exit();
                    },
                    Component.literal(Lang.get("gui.kve.worldgen.unsaved_title")),
                    Component.literal(Lang.get("gui.kve.worldgen.unsaved_msg"))));
            return;
        }
        exit();
    }

    private void exit() {
        if (hosted && onEmbeddedExit != null) { onEmbeddedExit.run(); return; }
        minecraft.setScreen(parent != null ? parent : new WorldGenListScreen(new KveStudioShell()));
    }

    @Override public void onClose() { goBack(); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        {
            int wantW = hosted ? Math.max(GUI_W_MIN, hostedW) : Math.max(GUI_W_MIN, width - OUTER_PAD * 2);
            int wantH = hosted ? Math.max(GUI_H_MIN, hostedH) : Math.max(GUI_H_MIN, height - OUTER_PAD * 2);
            if (wantW != GUI_W || wantH != GUI_H) refresh();
        }
        if (!hosted) renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, null);
        int titleX = L + 4 + KveGuiStyle.btnWidth("< " + Lang.get("gui.kve.btn.back")) + 6;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.worldgen.editor.title"), titleX, T + 3, L + GUI_W - 4 - titleX, KveGuiStyle.ACCENT_GOLD);

        int halfW = (LEFT_COL_W - 6) / 2;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.worldgen.editor.id"), L + 8, idLabelY, halfW, KveGuiStyle.TEXT_LABEL);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.worldgen.editor.display_name"), L + 8 + halfW + 6, nameLabelY, halfW, KveGuiStyle.TEXT_LABEL);
        sectionHeader(g, Lang.get("gui.kve.worldgen.section.general"), L + 8, generalHeaderY, LEFT_COL_W);
        sectionHeader(g, Lang.get("gui.kve.worldgen.section.biomes"), L + 8, biomesHeaderY, LEFT_COL_W);
        sectionHeader(g, Lang.get("gui.kve.worldgen.section.veins", draftVeins.size()), L + 8, veinsHeaderY, LEFT_COL_W);
        KveGuiStyle.drawTextSafe(g, biomesSummaryText(), L + 8, summaryY, LEFT_COL_W, KveGuiStyle.TEXT_MUTED);

        int veinsScroll = veinsScrollBar != null ? veinsScrollBar.getScroll() : 0;
        String hoveredFullOreBlock = null;
        int rowRight = veinTextRight();
        int labelMaxW = rowRight - (L + 11) - 2;
        java.util.Set<Integer> dupVeins = duplicateVeins();
        for (int i = 0; i < Math.min(VEIN_ROWS_VISIBLE, draftVeins.size() - veinsScroll); i++) {
            WorldGenEntry.OreVein v = draftVeins.get(i + veinsScroll);
            int ry = veinsListY + i * VEIN_ROW_H;
            boolean hov = mx >= L + 8 && mx < rowRight && my >= ry && my < ry + VEIN_ROW_H - 2;
            g.fill(L + 8, ry, L + LEFT_COL_W, ry + VEIN_ROW_H - 2, hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
            g.fill(L + 8, ry + 2, L + 10, ry + VEIN_ROW_H - 4, oreColor(v.oreBlock));
            boolean dup = dupVeins.contains(i + veinsScroll);
            String label = (dup ? "⚠ " : "") + (v.oreBlock.isEmpty() ? "?" : v.oreBlock);
            KveGuiStyle.drawTextSafe(g, label, L + 13, ry + 3, labelMaxW, dup ? KveGuiStyle.TEXT_WARN : KveGuiStyle.TEXT_PRIMARY);
            int line2W = VanillaOres.vanillaFeaturesFor(v.oreBlock).isEmpty() ? labelMaxW : labelMaxW - 88;
            String amount = v.rarity > 0 ? Lang.get("gui.kve.worldgen.vein.rarity_short", v.rarity) : "x" + v.veinsPerChunk;
            KveGuiStyle.drawTextSafe(g, "Y " + v.minY + ".." + v.maxY + "  " + amount,
                    L + 13, ry + 13, line2W, KveGuiStyle.TEXT_MUTED);
            if (hov && !v.oreBlock.isEmpty() && font.width(label) > labelMaxW) hoveredFullOreBlock = v.oreBlock;
        }
        if (veinsScrollBar != null) veinsScrollBar.render(g);
        if (draftVeins.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.worldgen.editor.no_veins"), L + 8, veinsListY + 2, LEFT_COL_W - 16, KveGuiStyle.TEXT_MUTED);
        }

        int rx = L + RIGHT_COL_X_OFFSET;
        sectionHeader(g, Lang.get("gui.kve.worldgen.section.height"), rx, presetsHeaderY, RIGHT_COL_W);
        if (disclaimerY >= 0) {
            boolean main = draftPreset.supportsMainWorld() && draftPresetTarget == WorldGenEntry.PresetTarget.MAIN_WORLD;
            String text = Lang.get(main ? "gui.kve.worldgen.preset.main_disclaimer" : "gui.kve.worldgen.preset.lab_disclaimer");
            int ly = disclaimerY;
            for (String line : KveGuiStyle.wrapTextSafe(font, text, RIGHT_COL_W)) {
                if (ly > disclaimerY + 10) break;
                KveGuiStyle.drawTextSafe(g, line, rx, ly, RIGHT_COL_W, main ? KveGuiStyle.TEXT_WARN : KveGuiStyle.TEXT_MUTED);
                ly += 10;
            }
        }
        sectionHeader(g, Lang.get("gui.kve.worldgen.sketch.title", biomeDisplayName(sketchBiome())), rx, sketchHeaderY,
                RIGHT_COL_W - (draftBiomeIds.size() > 1 ? 32 : 0));
        renderSketch(g);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.worldgen.sketch.legend"), rx, legendY, RIGHT_COL_W, KveGuiStyle.TEXT_MUTED);
        int hoveredBand = stripVeinAt(mx, my);

        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4500) {
            KveGuiStyle.drawTextSafe(g, flashMsg, L + 8, flashY, LEFT_COL_W, KveGuiStyle.ACCENT_GOLD);
        }
        super.render(g, mx, my, pt);

        if (hoveredFullOreBlock != null) {
            KveGuiStyle.drawFramedTooltip(g, hoveredFullOreBlock, mx, my, width, height);
        } else if (hoveredBand >= 0) {
            WorldGenEntry.OreVein v = draftVeins.get(hoveredBand);
            KveGuiStyle.drawFramedTooltip(g, Lang.get("gui.kve.worldgen.sketch.band_hint",
                    v.oreBlock.isEmpty() ? "?" : v.oreBlock, v.minY, v.maxY), mx, my, width, height);
        }
    }

    private String sketchBiome() {
        if (draftBiomeIds.isEmpty()) return "";
        return draftBiomeIds.get(Math.floorMod(sketchIndex, draftBiomeIds.size()));
    }

    private TerrainSketch currentSketch() {
        String biome = sketchBiome();
        String key = biome + "|" + draftPreset + "|" + (minecraft != null && minecraft.level != null);
        if (sketch == null || !key.equals(sketchKey)) {
            sketch = TerrainSketch.build(TerrainSketch.profileFor(resolveRegistryAccess(), biome, draftPreset), biome.hashCode());
            sketchKey = key;
        }
        return sketch;
    }

    private String biomeDisplayName(String id) {
        if (id.isEmpty()) return Lang.get("gui.kve.worldgen.sketch.default");
        if (id.startsWith("#")) return id;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return id;
        String key = "biome." + rl.getNamespace() + "." + rl.getPath();
        String translated = Component.translatable(key).getString();
        return translated.equals(key) ? id : translated;
    }

    private static final int STRIP_Y_MIN = -64, STRIP_Y_MAX = 256;

    private static DynamicTexture sketchTexture;
    private static ResourceLocation sketchTextureId;
    private static TerrainSketch uploadedSketch;

    private static ResourceLocation sketchTexture(TerrainSketch s) {
        int n = TerrainSketch.SIZE;
        if (sketchTexture == null) {
            sketchTexture = new DynamicTexture(n, n, false);
            sketchTextureId = net.minecraft.client.Minecraft.getInstance().getTextureManager().register("shovel_js_worldgen_sketch", sketchTexture);
            uploadedSketch = null;
        }
        if (uploadedSketch != s) {
            NativeImage img = sketchTexture.getPixels();
            if (img != null) {
                for (int z = 0; z < n; z++) {
                    for (int x = 0; x < n; x++) {
                        int c = s.color(x, z);
                        img.setPixelRGBA(x, z, (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16));
                    }
                }
                sketchTexture.upload();
            }
            uploadedSketch = s;
        }
        return sketchTextureId;
    }

    private void renderSketch(GuiGraphics g) {
        TerrainSketch s = currentSketch();
        int n = TerrainSketch.SIZE;
        g.fill(mapX - 1, mapY - 1, mapX + mapSide + 1, mapY + mapSide + 1, KveGuiStyle.BORDER_SUBTLE);
        g.blit(sketchTexture(s), mapX, mapY, mapSide, mapSide, 0f, 0f, n, n, n, n);
        if (draftBiomeIds.isEmpty()) {
            int cy = mapY + mapSide / 2;
            g.fill(mapX, cy - 8, mapX + mapSide, cy + 10, 0xB0101418);
            KveGuiStyle.drawCenteredTextSafe(g, Lang.get("gui.kve.worldgen.sketch.pick"), mapX + mapSide / 2, cy - 3, KveGuiStyle.TEXT_PRIMARY);
        }

        g.drawManaged(() -> {
            int sx = L + RIGHT_COL_X_OFFSET, sw = RIGHT_COL_W;
            g.fill(sx, stripY, sx + sw, stripY + stripH, 0xFF161A22);
            TerrainSketch.Profile p = s.profile;
            int mid = n / 2;
            int rock = p.nether() ? 0xFF4A1E1E : 0xFF55555E;
            int water = (p.water() & 0xFFFFFF) | 0xC0000000;
            int c = 0;
            while (c < sw) {
                int x = c * n / sw;
                int c1 = c + 1;
                while (c1 < sw && c1 * n / sw == x) c1++;
                int h = s.height(x, mid);
                if (h != TerrainSketch.VOID) {
                    int top = stripYFor(h);
                    if (!p.end() && h < p.seaLevel()) g.fill(sx + c, stripYFor(p.seaLevel()), sx + c1, top, water);
                    g.fill(sx + c, top, sx + c1, stripY + stripH, rock);
                    g.fill(sx + c, top, sx + c1, Math.min(stripY + stripH, top + 2), s.color(x, mid));
                }
                c = c1;
            }
            for (WorldGenEntry.OreVein v : draftVeins) {
                int col = oreColor(v.oreBlock);
                int y0 = stripYFor(Math.min(STRIP_Y_MAX, v.maxY)), y1 = stripYFor(Math.max(STRIP_Y_MIN, v.minY));
                if (y1 <= y0) y1 = y0 + 1;
                g.fill(sx, y0, sx + sw, y1, (col & 0xFFFFFF) | 0x55000000);
                g.fill(sx, y0, sx + sw, y0 + 1, col);
            }
        });
    }

    private int stripYFor(int y) {
        int clamped = Math.max(STRIP_Y_MIN, Math.min(STRIP_Y_MAX, y));
        return stripY + stripH - 1 - (clamped - STRIP_Y_MIN) * (stripH - 1) / (STRIP_Y_MAX - STRIP_Y_MIN);
    }

    private static int oreColor(String blockId) {
        String id = blockId == null ? "" : blockId.toLowerCase(java.util.Locale.ROOT);
        if (id.contains("diamond")) return 0xFF5DECF5;
        if (id.contains("emerald")) return 0xFF17DD62;
        if (id.contains("gold")) return 0xFFFCEE4B;
        if (id.contains("iron")) return 0xFFD8AF93;
        if (id.contains("copper")) return 0xFFE77C56;
        if (id.contains("coal")) return 0xFF3A3A3A;
        if (id.contains("redstone")) return 0xFFE02020;
        if (id.contains("lapis")) return 0xFF2F5FD0;
        if (id.contains("quartz")) return 0xFFEAE5DE;
        if (id.contains("debris") || id.contains("netherite")) return 0xFF7A5A48;
        int h = id.hashCode();
        return 0xFF000000 | (0x60 + ((h >> 16) & 0x7F)) << 16 | (0x60 + ((h >> 8) & 0x7F)) << 8 | (0x60 + (h & 0x7F));
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_D) { duplicateEntry(); return true; }
        if (EditorKeys.isFocusTab(key, mods)) { super.keyPressed(key, scan, mods); return true; }
        return EditorKeys.swallow(mods);
    }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // un menu "Mas" abierto recibe el clic antes que lo que tenga debajo
        if (com.kve.client.gui.KveMenuButton.consumeClick(mx, my, btn)) return true;
        if (veinsScrollBar != null && veinsScrollBar.mouseClicked(mx, my, btn)) return true;
        if (super.mouseClicked(mx, my, btn)) return true;
        int row = veinRowAt(mx, my);
        if (row >= 0 && btn == 0) { openVeinEditor(row); return true; }
        int band = stripVeinAt(mx, my);
        if (band >= 0 && btn == 0) { openVeinEditor(band); return true; }
        return false;
    }

    private int stripVeinAt(double mx, double my) {
        int sx = L + RIGHT_COL_X_OFFSET;
        if (mx < sx || mx >= sx + RIGHT_COL_W || my < stripY || my >= stripY + stripH) return -1;
        for (int i = draftVeins.size() - 1; i >= 0; i--) {
            WorldGenEntry.OreVein v = draftVeins.get(i);
            int y0 = stripYFor(Math.min(STRIP_Y_MAX, v.maxY)), y1 = Math.max(y0 + 2, stripYFor(Math.max(STRIP_Y_MIN, v.minY)));
            if (my >= y0 - 1 && my <= y1) return i;
        }
        return -1;
    }

    private java.util.Set<Integer> dupVeinsCache = java.util.Set.of();
    private long dupVeinsAt;

    private java.util.Set<Integer> duplicateVeins() {
        long now = System.currentTimeMillis();
        if (now - dupVeinsAt >= 250) {
            dupVeinsAt = now;
            WorldGenEntry veinView = new WorldGenEntry();
            veinView.oreVeins = draftVeins;
            dupVeinsCache = com.kve.common.conflict.ConflictDetector.duplicateVeinIndexes(veinView);
        }
        return dupVeinsCache;
    }

    private int veinTextRight() { return L + LEFT_COL_W - 60; }

    private int veinRowAt(double mx, double my) {
        if (mx < L + 8 || mx >= veinTextRight() || my < veinsListY || my >= veinsListY + VEIN_ROWS_VISIBLE * VEIN_ROW_H) return -1;
        int scroll = veinsScrollBar != null ? veinsScrollBar.getScroll() : 0;
        int idx = scroll + (int) ((my - veinsListY) / VEIN_ROW_H);
        return idx < draftVeins.size() ? idx : -1;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (veinsScrollBar != null && veinsScrollBar.mouseDragged(mx, my, btn, dx, dy)) return true;
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (veinsScrollBar != null && veinsScrollBar.mouseReleased(mx, my, btn)) return true;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (veinsScrollBar != null && insideVeinsList(mx, my)) { veinsScrollBar.mouseScrolled(delta); return true; }
        return super.mouseScrolled(mx, my, delta);
    }

    private boolean insideVeinsList(double mx, double my) {
        return mx >= L + 8 && mx < L + LEFT_COL_W && my >= veinsListY && my < veinsListY + VEIN_ROWS_VISIBLE * VEIN_ROW_H;
    }
}