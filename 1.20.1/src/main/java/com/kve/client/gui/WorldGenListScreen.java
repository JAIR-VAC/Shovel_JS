package com.kve.client.gui;

import com.kve.common.Lang;
import com.kve.client.gui.studio.KveStudioShell;
import com.kve.client.gui.studio.KveStudioTab;
import com.kve.client.permission.ClientPermissionState;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.common.worldgen.WorldGenEntry;
import com.kve.common.worldgen.WorldGenExporter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class WorldGenListScreen extends Screen implements KveStudioTab {

    private static final int GUI_W_MIN = 260;
    private int GUI_W = GUI_W_MIN;
    private static final int ROWS_VISIBLE_MIN = 8;
    private int ROWS_VISIBLE = ROWS_VISIBLE_MIN;
    private static final int ROW_H = 22;
    private int GUI_H = 34 + ROWS_VISIBLE_MIN * ROW_H + 46;

    private final Screen returnScreen;

    private boolean hosted = false;
    private int hostedX, hostedY, hostedW, hostedH;

    private int L, T;
    private final List<WorldGenEntry> savedList = new ArrayList<>();
    private ScrollBar scrollBar;
    private EditBox searchBox;
    private String searchFilter = "";

    private String flashMsg = "";
    private long flashAt = 0;

    private WorldGenEditorScreen embeddedEditor;

    public WorldGenListScreen(Screen returnScreen) {
        super(Lang.c("gui.kve.worldgen.list.title"));
        this.returnScreen = returnScreen;
    }

    @Override
    protected void init() {
        if (KveStudioShell.reclaimIfHosted(this, returnScreen)) return;
        hosted = false;
        GUI_W = GUI_W_MIN;
        ROWS_VISIBLE = ROWS_VISIBLE_MIN;
        GUI_H = 34 + ROWS_VISIBLE * ROW_H + 46;
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        buildWidgets();
    }

    @Override
    public void init(int x0, int y0, int w, int h) {
        if (this.minecraft == null) {
            this.minecraft = net.minecraft.client.Minecraft.getInstance();
            this.font = this.minecraft.font;
        }
        hosted = true;
        hostedX = x0; hostedY = y0; hostedW = w; hostedH = h;
        // Si hay un editor embebido activo, este init (llamado por el
        // shell al reabrirse via reclaimIfHosted(), o al cambiar de tab y
        // volver) debe reconstruir ESE editor en el rectangulo actualizado,
        // no la lista de atras - de lo contrario, cualquier picker interno
        // del editor (BiomePickerScreen/OreVeinEditScreen/confirmación de
        // borrado) que vuelve via minecraft.setScreen(editor) reaparecería
        // mostrando la lista en vez del editor que se estaba usando.
        if (embeddedEditor != null) { embeddedEditor.init(x0, y0, w, h); return; }
        GUI_W = Math.max(w, GUI_W_MIN);
        ROWS_VISIBLE = Math.max(ROWS_VISIBLE_MIN, (h - 34 - 46) / ROW_H);
        GUI_H = Math.max(h, 34 + ROWS_VISIBLE * ROW_H + 46);
        L = x0;
        T = y0;
        buildWidgets();
    }

    private void refresh() {
        if (hosted) {
            if (embeddedEditor != null) { embeddedEditor.init(hostedX, hostedY, hostedW, hostedH); return; }
            GUI_W = Math.max(hostedW, GUI_W_MIN);
            ROWS_VISIBLE = Math.max(ROWS_VISIBLE_MIN, (hostedH - 34 - 46) / ROW_H);
            GUI_H = Math.max(hostedH, 34 + ROWS_VISIBLE * ROW_H + 46);
            L = hostedX;
            T = hostedY;
            buildWidgets();
        } else {
            init();
        }
    }

    private void openEditor(WorldGenEntry entry) {
        if (hosted) {
            WorldGenEditorScreen ed = entry != null
                    ? new WorldGenEditorScreen(this, entry)
                    : new WorldGenEditorScreen(this);
            ed.withEmbedding(this, returnScreen, this::closeEmbeddedEditor);
            embeddedEditor = ed;
            ed.init(hostedX, hostedY, hostedW, hostedH);
        } else {
            minecraft.setScreen(entry != null ? new WorldGenEditorScreen(this, entry) : new WorldGenEditorScreen(this));
        }
    }

    private void closeEmbeddedEditor() {
        embeddedEditor = null;
        refreshSaved();
        refresh();
    }

    private void buildWidgets() {
        clearWidgets();
        setFocused(null);
        WorldGenExporter.loadIfNeeded();
        refreshSaved();

        if (!hosted) {
            int pendCount = PendingChangeManager.size();
            String applyL = pendCount > 0 ? Lang.get("gui.kve.pending.apply") + " (" + pendCount + ")" : Lang.get("gui.kve.pending.apply");
            String discardL = pendCount > 0 ? Lang.get("gui.kve.pending.discard") + " (" + pendCount + ")" : Lang.get("gui.kve.pending.discard");
            int discardW = KveGuiStyle.btnWidth(discardL), applyW = KveGuiStyle.btnWidth(applyL);
            int discardX = L + GUI_W - 2 - discardW;
            int applyX = discardX - KveGuiStyle.BTN_GAP - applyW;
            KveGuiStyle.ButtonRow pendRow = new KveGuiStyle.ButtonRow(applyX, T + 2, L + GUI_W - 2);
            Button applyPendBtn = addRenderableWidget(pendRow.add(0, applyL, b -> onApplyPending()));
            applyPendBtn.active = pendCount > 0 && ClientPermissionState.canApply();
            Button discardPendBtn = addRenderableWidget(pendRow.add(0, discardL, b -> onDiscardPending()));
            discardPendBtn.active = pendCount > 0;
        }

        searchBox = new KveScaledEditBox(font, L + 6, T + 18, GUI_W - 12, 12, Lang.c("gui.kve.event.search_hint"));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchFilter);
        searchBox.setResponder(s -> { searchFilter = s; refreshSaved(); });
        addRenderableWidget(searchBox);

        int listY = T + 34;
        scrollBar = new ScrollBar(L + GUI_W - 14, listY, ROWS_VISIBLE * ROW_H, 0, ROWS_VISIBLE);
        updateScroll();

        int newBtnY = listY + ROWS_VISIBLE * ROW_H + 6;
        Button newBtn = addRenderableWidget(btn(Lang.get("gui.kve.worldgen.list.new"), L + 6, newBtnY, 0,
                () -> openEditor(null)));
        Button oresBtn = addRenderableWidget(btn(Lang.get("gui.kve.worldgen.ores.open"), newBtn.getX() + newBtn.getWidth() + KveGuiStyle.BTN_GAP, newBtnY, 0,
                () -> minecraft.setScreen(new ExistingOreListScreen(this))));
        int conflicts = com.kve.common.conflict.ConflictDetector.worldGenConflicts(WorldGenExporter.getEntries()).size();
        if (conflicts > 0) {
            Button cBtn = addRenderableWidget(btn("⚠ " + Lang.get("gui.kve.conflict.button", conflicts),
                    oresBtn.getX() + oresBtn.getWidth() + KveGuiStyle.BTN_GAP, newBtnY, 0, this::openConflicts));
            cBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c("gui.kve.conflict.worldgen_tooltip")));
        }
    }

    private void openConflicts() {
        minecraft.setScreen(new ConflictsScreen(this, Lang.c("gui.kve.conflict.worldgen_title"),
                () -> com.kve.common.conflict.ConflictDetector.worldGenConflicts(WorldGenExporter.getEntries()),
                key -> { WorldGenEntry e = WorldGenExporter.getEntry(key); if (e != null) openEditor(e); }));
    }

    private void refreshSaved() {
        savedList.clear();
        for (WorldGenEntry e : WorldGenExporter.getEntries()) {
            if (!matchesEntry(e, searchFilter)) continue;
            savedList.add(e);
        }
        savedList.sort(java.util.Comparator
                .comparing((WorldGenEntry e) -> !e.enabled)
                .thenComparing(e -> (e.displayName.isEmpty() ? e.id : e.displayName).toLowerCase(java.util.Locale.ROOT)));
        updateScroll();
    }

    private static boolean matchesEntry(WorldGenEntry e, String query) {
        if (query == null || query.isBlank()) return true;
        StringBuilder haystack = new StringBuilder(e.displayName);
        for (String b : e.biomeIds) haystack.append(' ').append(b);
        for (WorldGenEntry.OreVein v : e.oreVeins) {
            haystack.append(' ').append(v.oreBlock);
            if (v.deepslateOreBlock != null && !v.deepslateOreBlock.isBlank()) haystack.append(' ').append(v.deepslateOreBlock);
        }
        return SearchFilter.matches(e.id, haystack.toString(), query);
    }

    private static String biomesSummary(int idCount, int tagCount) {
        if (tagCount == 0) return idCount + " " + Lang.get("gui.kve.worldgen.list.ids_short");
        if (idCount == 0) return tagCount + " " + Lang.get("gui.kve.worldgen.list.tags_short");
        return idCount + " " + Lang.get("gui.kve.worldgen.list.ids_short")
                + " + " + tagCount + " " + Lang.get("gui.kve.worldgen.list.tags_short");
    }

    private void updateScroll() {
        if (scrollBar != null) scrollBar.setTotals(savedList.size(), ROWS_VISIBLE);
    }

    private void onApplyPending() {
        if (!ClientPermissionState.canApply()) { flash(Lang.get("gui.kve.perm.apply_denied")); return; }
        if (PendingChangeManager.isEmpty()) return;
        if (PendingChangeManager.domainHasDiff(PendingDomain.WORLDGEN)) {
            minecraft.setScreen(new DiffPreviewScreen(this,
                    PendingChangeManager.entriesForDomain(PendingDomain.WORLDGEN),
                    this::proceedApply));
            return;
        }
        proceedApply();
    }

    private void proceedApply() {
        java.util.Set<PendingDomain> domains = PendingChangeManager.domainsPresent();
        if (domains.size() > 1) {
            int otherCount = PendingChangeManager.size() - PendingChangeManager.sizeForDomain(PendingDomain.WORLDGEN);
            minecraft.setScreen(new KveConfirmScreen(
                    confirmed -> {
                        minecraft.setScreen(this);
                        applyPendingResult(confirmed ? PendingChangeManager.applyAll() : PendingChangeManager.applyDomain(PendingDomain.WORLDGEN));
                    },
                    Component.literal(Lang.get("gui.kve.pending.other_domains_title")),
                    Component.literal(Lang.get("gui.kve.pending.other_domains_msg", otherCount))));
            return;
        }
        applyPendingResult(PendingChangeManager.applyDomain(PendingDomain.WORLDGEN));
    }

    private void applyPendingResult(PendingChangeManager.ApplyResult result) {
        String veins = partialVeinNote().trim();
        com.kve.client.gui.KveToast.applied(result, veins.isEmpty() ? java.util.List.of() : java.util.List.of(veins));
        refreshSaved();
        refresh();
    }

    private String partialVeinNote() {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, int[]> c : WorldGenExporter.lastCommitSummary().entrySet()) {
            int written = c.getValue()[0], total = c.getValue()[1];
            if (written < total) {
                sb.append(' ').append(Lang.get("gui.kve.worldgen.list.partial_veins", c.getKey(), written, total));
            }
        }
        return sb.toString();
    }

    private void onDiscardPending() {
        if (PendingChangeManager.isEmpty()) return;
        int count = PendingChangeManager.sizeForDomain(PendingDomain.WORLDGEN);
        if (count == 0) return;
        PendingChangeManager.discardDomain(PendingDomain.WORLDGEN);
        flash(Lang.get("gui.kve.pending.discarded", count));
        refreshSaved();
        refresh();
    }

    private void flash(String msg) { flashMsg = msg; flashAt = System.currentTimeMillis(); }

    private void goBack() {
        if (KveStudioShell.exitIfHosted(this, returnScreen)) return;
        minecraft.setScreen(returnScreen != null ? returnScreen : new KveStudioShell());
    }

    @Override
    public void onClose() { goBack(); }

    private Button btn(String label, int x, int y, int w, Runnable a) {
        return KveGuiStyle.smartBtn(x, y, w, label, b -> a.run());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (embeddedEditor != null) { embeddedEditor.render(g, mx, my, pt); return; }
        if (hosted) {
            int wantW = Math.max(hostedW, GUI_W_MIN);
            int wantRows = Math.max(ROWS_VISIBLE_MIN, (hostedH - 34 - 46) / ROW_H);
            int wantH = Math.max(hostedH, 34 + wantRows * ROW_H + 46);
            if (wantW != GUI_W || wantH != GUI_H || wantRows != ROWS_VISIBLE) refresh();
        }
        if (!hosted) renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.worldgen.list.title") + " (" + savedList.size() + ")");

        int listY = T + 34;
        int scroll = scrollBar.getScroll();
        for (int i = 0; i < Math.min(ROWS_VISIBLE, savedList.size() - scroll); i++) {
            WorldGenEntry e = savedList.get(i + scroll);
            int ry = listY + i * ROW_H;
            boolean hov = KveGuiStyle.isHovered(mx, my, L + 4, ry, GUI_W - 20, ROW_H - 2);
            g.fill(L + 4, ry, L + GUI_W - 20, ry + ROW_H - 2, hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
            String title = (e.enabled ? "" : "[OFF] ") + (e.displayName.isEmpty() ? e.id : e.displayName);
            KveGuiStyle.drawTextSafe(g, title, L + 7, ry + 3, GUI_W - 30, e.enabled ? KveGuiStyle.TEXT_PRIMARY : KveGuiStyle.TEXT_MUTED);
            int tagCount = 0;
            for (String b : e.biomeIds) if (b != null && b.startsWith("#")) tagCount++;
            int idCount = e.biomeIds.size() - tagCount;
            String biomesPart = biomesSummary(idCount, tagCount);
            String summary = biomesPart
                    + " - " + e.oreVeins.size() + " " + Lang.get("gui.kve.worldgen.list.veins_short");
            int extras = e.removedFeatures.size() + e.spawns.size() + e.patches.size();
            if (extras > 0) summary += " - " + Lang.get("gui.kve.worldgen.list.extras_short", extras);
            KveGuiStyle.drawTextSafe(g, summary, L + 7, ry + 13, GUI_W - 30, KveGuiStyle.TEXT_MUTED);
        }
        scrollBar.render(g);
        if (savedList.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.worldgen.list.empty"), L + 7, listY + 4, GUI_W - 14, KveGuiStyle.TEXT_MUTED);
        }

        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4500) {
            KveGuiStyle.drawTextSafe(g, flashMsg, L + 6, T + GUI_H - 30, GUI_W - 12, KveGuiStyle.ACCENT_GOLD);
        }

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (embeddedEditor != null) return embeddedEditor.mouseClicked(mx, my, btn);
        if (scrollBar.mouseClicked(mx, my, btn)) return true;
        int listY = T + 34;
        int scroll = scrollBar.getScroll();
        for (int i = 0; i < Math.min(ROWS_VISIBLE, savedList.size() - scroll); i++) {
            int ry = listY + i * ROW_H;
            if (mx >= L + 4 && mx < L + GUI_W - 20 && my >= ry && my < ry + ROW_H - 2) {
                openEditor(savedList.get(i + scroll));
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (embeddedEditor != null) return embeddedEditor.mouseDragged(mx, my, b, dx, dy);
        if (scrollBar.mouseDragged(mx, my, b, dx, dy)) return true;
        return super.mouseDragged(mx, my, b, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int b) {
        if (embeddedEditor != null) return embeddedEditor.mouseReleased(mx, my, b);
        scrollBar.mouseReleased(mx, my, b);
        return super.mouseReleased(mx, my, b);
    }
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (embeddedEditor != null) return embeddedEditor.mouseScrolled(mx, my, delta);
        scrollBar.mouseScrolled(delta);
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (embeddedEditor != null) return embeddedEditor.keyPressed(key, scan, mods);
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        if (EditorKeys.isFocusTab(key, mods)) { super.keyPressed(key, scan, mods); return true; }
        return EditorKeys.swallow(mods);
    }
    @Override public boolean charTyped(char c, int mods) {
        if (embeddedEditor != null) return embeddedEditor.charTyped(c, mods);
        return super.charTyped(c, mods);
    }
    @Override public boolean isPauseScreen() { return false; }
}
