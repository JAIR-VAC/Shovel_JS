package com.kve.client.gui;

import com.kve.common.Lang;
import com.kve.client.gui.studio.KveStudioShell;
import com.kve.client.gui.studio.KveStudioTab;
import com.kve.common.persist.CraftEditorState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

@OnlyIn(Dist.CLIENT)
public class ImportExportScreen extends Screen implements KveStudioTab {

    private enum Mode { PICK_ACTION, EXPORT, IMPORT }

    private static final int GUI_W_DEFAULT = 260;
    private static final int GUI_H_MIN = 90;

    private int L, T, GUI_W, GUI_H = GUI_H_MIN;
    private boolean hosted = false;
    private int hostedLastH;

    private Screen returnScreen = null;
    private Mode mode = Mode.PICK_ACTION;

    private Path pickedImportFile = null;
    private String flashMsg = "";
    private int flashColor = 0xFF66FF99;
    private long flashAt = 0;

    public ImportExportScreen() {
        super(Lang.c("gui.kve.ioscan.title"));
    }

    public ImportExportScreen withReturnScreen(Screen s) {
        this.returnScreen = s;
        return this;
    }

    @Override
    protected void init() {
        if (KveStudioShell.reclaimIfHosted(this, returnScreen)) return;
        hosted = false;
        GUI_W = GUI_W_DEFAULT;
        GUI_H = computeGuiH();
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        buildWidgets();
    }

    @Override
    public void init(int x0, int y0, int w, int h) {
        if (this.minecraft == null) {
            this.minecraft = Minecraft.getInstance();
            this.font = this.minecraft.font;
        }
        hosted = true;
        hostedLastH = h;
        L = x0;
        T = y0;
        GUI_W = Math.max(w, GUI_W_DEFAULT);
        GUI_H = Math.max(h, computeGuiH());
        buildWidgets();
    }

    private int computeGuiH() {
        int contentBottom = switch (mode) {
            case PICK_ACTION -> 40 + 30 + 22;
            case EXPORT -> 34 + 6 * 18 + 14;
            case IMPORT -> 40 + 56 + 20;
        };
        int bottomReserved = mode == Mode.IMPORT ? 42 : 34;
        return Math.max(GUI_H_MIN, contentBottom + 14 + bottomReserved);
    }

    private void refresh() {
        GUI_H = hosted ? Math.max(hostedLastH, computeGuiH()) : computeGuiH();
        if (!hosted) T = (height - GUI_H) / 2;
        buildWidgets();
    }

    private void buildWidgets() {
        clearWidgets();
        CraftEditorState.ensureIoDir();

        switch (mode) {
            case PICK_ACTION -> buildPickAction();
            case EXPORT -> buildExport();
            case IMPORT -> buildImport();
        }

        int backW = 60;
        addRenderableWidget(new KvePlateButton(L + GUI_W - backW - 6, T + GUI_H - 20, backW, 14,
                Lang.c("gui.kve.btn.back"),
                b -> {
                    if (mode == Mode.PICK_ACTION) onClose();
                    else { mode = Mode.PICK_ACTION; pickedImportFile = null; refresh(); }
                }));
    }

    private void buildPickAction() {
        int rowY = T + 40;
        addRenderableWidget(new KvePlateButton(L + 20, rowY, GUI_W - 40, 22,
                Lang.c("gui.kve.ioscan.btn.export"),
                b -> { mode = Mode.EXPORT; refresh(); }));
        addRenderableWidget(new KvePlateButton(L + 20, rowY + 30, GUI_W - 40, 22,
                Lang.c("gui.kve.ioscan.btn.import"),
                b -> { mode = Mode.IMPORT; refresh(); }));
    }

    private void buildExport() {
        int y = T + 34;
        int rowH = 18;
        addDomainButton(y, "gui.kve.ioscan.type.all", CraftEditorState.ExportDomain.ALL); y += rowH;
        addDomainButton(y, "gui.kve.ioscan.type.recipes", CraftEditorState.ExportDomain.RECIPES); y += rowH;
        addDomainButton(y, "gui.kve.ioscan.type.mobs", CraftEditorState.ExportDomain.MOBS); y += rowH;
        addDomainButton(y, "gui.kve.ioscan.type.items", CraftEditorState.ExportDomain.ITEMS); y += rowH;
        addDomainButton(y, "gui.kve.ioscan.type.blocks", CraftEditorState.ExportDomain.BLOCKS); y += rowH;
        addDomainButton(y, "gui.kve.ioscan.type.tags", CraftEditorState.ExportDomain.TAGS); y += rowH;
        addDomainButton(y, "gui.kve.ioscan.type.loot", CraftEditorState.ExportDomain.LOOT);
    }

    private void addDomainButton(int y, String langKey, CraftEditorState.ExportDomain domain) {
        addRenderableWidget(new KvePlateButton(L + 20, y, GUI_W - 40, 14, Lang.c(langKey),
                b -> {
                    boolean ok = CraftEditorState.exportDomain(domain);
                    flashColor = ok ? 0xFF66FF99 : 0xFFFF6666;
                    flashMsg = ok
                            ? Lang.get("gui.kve.ioscan.export.done", CraftEditorState.getExportPath().toString())
                            : Lang.get("gui.kve.ioscan.export.fail");
                    flashAt = System.currentTimeMillis();
                }));
    }

    private void buildImport() {
        if (!com.kve.client.permission.ClientPermissionState.canEdit()) {
            flashColor = 0xFFFF6666;
            flashMsg = Lang.get("gui.kve.perm.denied");
            flashAt = System.currentTimeMillis();
            return;
        }
        int y = T + 40;
        addRenderableWidget(new KvePlateButton(L + 20, y, GUI_W - 40, 20,
                Lang.c("gui.kve.ioscan.import.pick_file"),
                b -> KveFilePicker.open(
                        Lang.get("gui.kve.ioscan.import.pick_file"),
                        new String[]{"*.json", "*.js"},
                        Lang.get("gui.kve.ioscan.import.filter"),
                        path -> { pickedImportFile = path; refresh(); },
                        () -> {
                            flashColor = 0xFFFF6666;
                            flashMsg = Lang.get("gui.kve.ioscan.import.picker_failed",
                                    CraftEditorState.getImportPath().getParent().toString());
                            flashAt = System.currentTimeMillis();
                        })));

        addRenderableWidget(new KvePlateButton(L + 20, y + 26, GUI_W - 40, 20,
                Lang.c("gui.kve.ioscan.import.use_default"),
                b -> { pickedImportFile = CraftEditorState.getImportPath(); refresh(); }));

        int confirmY = y + 56;
        boolean canImport = pickedImportFile != null;
        var confirmBtn = new KvePlateButton(L + 20, confirmY, GUI_W - 40, 20,
                Lang.c("gui.kve.ioscan.import.btn"),
                b -> {
                    boolean ok = CraftEditorState.importFrom(pickedImportFile);
                    flashColor = ok ? 0xFF66FF99 : 0xFFFF6666;
                    flashMsg = ok
                            ? Lang.get("gui.kve.ioscan.import.done", pickedImportFile.toString())
                            : Lang.get("gui.kve.ioscan.import.fail");
                    flashAt = System.currentTimeMillis();
                });
        confirmBtn.active = canImport;
        addRenderableWidget(confirmBtn);
    }

    @Override
    public void onClose() {
        if (KveStudioShell.exitIfHosted(this, returnScreen)) return;
        minecraft.setScreen(returnScreen != null ? returnScreen : new KveStudioShell());
    }

    /** KveStudioTab exige mouseClicked() explicito, mismo motivo que mouseDragged() abajo. */
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return super.mouseClicked(mx, my, button);
    }

    /** KveStudioTab exige mouseDragged() explicito (no basta con el default heredado de Screen) - ver KveStudioTab#mouseDragged() y el mismo patron ya usado en LootTableEditorScreen. */
    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!hosted) renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.ioscan.title"));

        if (mode == Mode.PICK_ACTION) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.ioscan.pick_action"), L + 8, T + 22, GUI_W - 16, KveGuiStyle.th(0xFF8899BB));
        } else if (mode == Mode.EXPORT) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.ioscan.pick_type"), L + 8, T + 20, GUI_W - 16, KveGuiStyle.th(0xFF8899BB));
        } else {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.ioscan.pick_type"), L + 8, T + 20, GUI_W - 16, KveGuiStyle.th(0xFF8899BB));
            String fileLabel = pickedImportFile != null
                    ? Lang.get("gui.kve.ioscan.import.selected", pickedImportFile.getFileName().toString())
                    : Lang.get("gui.kve.ioscan.import.none");
            KveGuiStyle.drawTextSafe(g, fileLabel, L + 8, T + GUI_H - 42, GUI_W - 16, KveGuiStyle.th(0xFF445566));
        }

        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4000) {
            KveGuiStyle.drawTextSafe(g, flashMsg, L + 8, T + GUI_H - 34, GUI_W - 16, flashColor);
        }

        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void removed() {}
}
