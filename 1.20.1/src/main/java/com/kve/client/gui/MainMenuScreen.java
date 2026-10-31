package com.kve.client.gui;

import org.lwjgl.glfw.GLFW;
import com.kve.common.Lang;
import com.kve.client.gui.studio.KveStudioShell;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.common.kubejs.ExternalScriptScanner;
import com.kve.common.persist.CraftEditorState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MainMenuScreen extends Screen {

    private static final int W     = 300;
    private static final int SECTION_HEADER_H = 13;
    private static final int COL_W = 138;
    private static final int BTN_H = 26;

    private static final int CONTENT_START_Y = 35;
    private static final int FOOTER_PAD = 26;

    private static int cachedH = -1;

    private static int H() {
        if (cachedH < 0) cachedH = computeContentHeight();
        return cachedH;
    }

    private static int computeContentHeight() {
        KveGuiStyle.Layout layout = new KveGuiStyle.Layout(0, CONTENT_START_Y, BTN_H + 4);
        layout.row(SECTION_HEADER_H);
        layout.row();
        layout.row();
        layout.row();
        layout.row(BTN_H);
        layout.row(BTN_H + 4);
        layout.gap(KveGuiStyle.SECTION_GAP);
        layout.row(SECTION_HEADER_H);
        if (KveClientSettings.betaEventsEnabled()) layout.row(BTN_H + 4);
        layout.row(BTN_H + 4);
        layout.gap(KveGuiStyle.SECTION_GAP);
        layout.row(SECTION_HEADER_H);
        layout.row(22);
        layout.row(22);
        int ioY = layout.row(22);
        return ioY + 18 + FOOTER_PAD;
    }

    private static Component numbered(int n, Component label) {
        return Component.literal("§7" + n + " §r").append(label);
    }

    private String flashMsg = "";
    private long   flashAt  = 0;
    private int    flashColor = 0xFF66FF99;

    private int exportPathY = 0;

    // Y de cada encabezado de seccion, calculados en init() a partir del
    // mismo Layout cursor que ya usan los botones (nunca hardcodeados) -
    // dibujados en render() con drawTextSafe() para que tambien respeten la
    // escala de texto global (ver KveGuiStyle.uiTextScale).
    private int headerContentY = 0;
    private int headerWorldY   = 0;
    private int headerToolsY   = 0;

    private boolean pendingListenerRegistered = false;
    private final PendingChangeManager.Listener pendingListener = this::onPendingQueueChanged;

    private void onPendingQueueChanged() {
        if (minecraft != null && minecraft.screen == this) init();
    }

    public MainMenuScreen() {
        super(Lang.c("gui.kve.title"));
    }

    /** Counterpart to the registration in init() - see pendingListenerRegistered's comment. Screen#removed() is called exactly once, when Minecraft actually swaps away from this screen (unlike init(), which can run many times). */
    @Override
    public void removed() {
        PendingChangeManager.removeListener(pendingListener);
        pendingListenerRegistered = false;
        super.removed();
    }

    @Override
    protected void init() {
        if (!pendingListenerRegistered) {
            PendingChangeManager.addListener(pendingListener);
            pendingListenerRegistered = true;
        }
        clearWidgets();

        CraftEditorState.loadIfNeeded();
        int cx = (width  - W) / 2;
        int cy = Math.max(4, (height - H()) / 2);
        int col1 = cx + (W - (COL_W * 2 + 6)) / 2;
        int col2 = col1 + COL_W + 6;

        KveGuiStyle.Layout layout = new KveGuiStyle.Layout(col1, cy + CONTENT_START_Y, BTN_H + 4);

        headerContentY = layout.row(SECTION_HEADER_H);
        int row1Y = layout.row();
        addRenderableWidget(new KvePlateButton(col1, row1Y, COL_W, BTN_H, numbered(1, Lang.c("gui.kve.main.recipes")),
                b -> com.kve.client.access.KveAccessGate.open(CraftEditorScreen::new)));
        addRenderableWidget(new KvePlateButton(col2, row1Y, COL_W, BTN_H, numbered(2, Lang.c("gui.kve.main.mobs")),
                b -> com.kve.client.access.KveAccessGate.open(MobEditorScreen::new)));

        int row2Y = layout.row();
        addRenderableWidget(new KvePlateButton(col1, row2Y, COL_W, BTN_H, numbered(3, Component.literal(Lang.get("gui.kve.main.items"))),
                b -> com.kve.client.access.KveAccessGate.open(ItemAttributeScreen::new)));
        addRenderableWidget(new KvePlateButton(col2, row2Y, COL_W, BTN_H, numbered(4, Component.literal(Lang.get("gui.kve.main.tags"))),
                b -> com.kve.client.access.KveAccessGate.open(TagEditorScreen::new)));

        int fullW = COL_W * 2 + 6;
        int row2bY = layout.row();
        addRenderableWidget(new KvePlateButton(col1, row2bY, fullW, BTN_H, numbered(5, Component.literal(Lang.get("gui.kve.main.blocks"))),
                b -> com.kve.client.access.KveAccessGate.open(BlockAttributeScreen::new)));

        int row3Y = layout.row(BTN_H);
        if (com.kve.common.config.KVEConfig.enableStructureEditor) {
            addRenderableWidget(new KvePlateButton(col1, row3Y, COL_W, BTN_H, numbered(6, Component.literal(Lang.get("gui.kve.main.loot"))),
                    b -> com.kve.client.access.KveAccessGate.open(LootTableEditorScreen::new)));
            addRenderableWidget(new KvePlateButton(col2, row3Y, COL_W, BTN_H, Component.literal(Lang.get("gui.kve.main.structures")),
                    b -> com.kve.client.access.KveAccessGate.open(() ->
                            new com.kve.client.structure.StructureEditorScreen(this))));
        } else {
            addRenderableWidget(new KvePlateButton(col1, row3Y, fullW, BTN_H, numbered(6, Component.literal(Lang.get("gui.kve.main.loot"))),
                    b -> com.kve.client.access.KveAccessGate.open(LootTableEditorScreen::new)));
        }

        int row3bY = layout.row(BTN_H + 4);
        addRenderableWidget(new KvePlateButton(col1, row3bY, fullW, BTN_H, numbered(7, Component.literal(Lang.get("gui.kve.main.trades"))),
                b -> com.kve.client.access.KveAccessGate.open(TradeEditorScreen::new)));

        layout.gap(KveGuiStyle.SECTION_GAP);
        headerWorldY = layout.row(SECTION_HEADER_H);
        if (KveClientSettings.betaEventsEnabled()) {
            int row4Y = layout.row(BTN_H + 4);
            addRenderableWidget(new KvePlateButton(col1, row4Y, fullW, BTN_H, numbered(8, Component.literal(Lang.get("gui.kve.main.events"))),
                    b -> com.kve.client.access.KveAccessGate.open(() -> new EventListScreen(this))));
        }

        int row4bY = layout.row(BTN_H + 4);
        addRenderableWidget(new KvePlateButton(col1, row4bY, fullW, BTN_H, numbered(9, Component.literal(Lang.get("gui.kve.main.worldgen"))),
                b -> com.kve.client.access.KveAccessGate.open(() -> new WorldGenListScreen(this))));

        layout.gap(KveGuiStyle.SECTION_GAP);
        headerToolsY = layout.row(SECTION_HEADER_H);

        int studioY = layout.row(22);
        KvePlateButton studioBtn = new KvePlateButton(col1, studioY, fullW, 18,
                Lang.c("gui.kve.studio.open.btn"),
                b -> com.kve.client.access.KveAccessGate.open(() -> {
                    KveStudioShell shell = new KveStudioShell();
                    return com.kve.common.config.KVEConfig.showStudioOnboarding
                            ? new StudioOnboardingScreen(shell)
                            : shell;
                }));
        studioBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c("gui.kve.studio.open.tooltip")));
        addRenderableWidget(studioBtn);

        int rescanY = layout.row(22);
        addRenderableWidget(new KvePlateButton(col1, rescanY, fullW, 18, Lang.c("gui.kve.btn.rescan"),
                b -> {
                    if (ExternalScriptScanner.isScanning()) return;
                    ExternalScriptScanner.ensureRootsExist();
                    ExternalScriptScanner.scanAsync(() -> {
                        long missing = ExternalScriptScanner.getLastRootStatuses().stream().filter(s -> !s.existed()).count();
                        flashColor = missing == 0 ? 0xFF66FF99 : 0xFFFFAA55;
                        flashMsg = Lang.get("gui.kve.rescan.done") + ": "
                                + ExternalScriptScanner.getItemHitCount() + " items, "
                                + ExternalScriptScanner.getMobHitCount() + " mobs, "
                                + ExternalScriptScanner.getTagHitCount() + " tags"
                                + (missing > 0 ? " - " + Lang.get("gui.kve.scan.missing_roots", missing) : "");
                        flashAt = System.currentTimeMillis();
                    });
                }));

        CraftEditorState.ensureIoDir();

        int ioY = layout.row(22);
        addRenderableWidget(new KvePlateButton(col1, ioY, fullW, 18, Lang.c("gui.kve.ioscan.title"),
                b -> minecraft.setScreen(new ImportExportScreen().withReturnScreen(this))));

        exportPathY = ioY + 18 + 8;


        addRenderableWidget(new KvePlateButton(cx + 70, cy + 2, 44, 14, Component.literal(Lang.get("gui.kve.main.settings.btn")),
                b -> minecraft.setScreen(new KveSettingsScreen(this))));

        addRenderableWidget(new KvePlateButton(cx + 118, cy + 2, 40, 14, Component.literal(Lang.get("gui.kve.files.btn")),
                b -> minecraft.setScreen(new CodeEditorScreen().withReturnScreen(this))));

        addRenderableWidget(new KvePlateButton(cx + 160, cy + 2, 40, 14, Component.literal(Lang.get("gui.kve.idusage.btn")),
                b -> com.kve.client.access.KveAccessGate.open(() -> new IdUsageScreen(this))));

        addRenderableWidget(KveGuiStyle.pendingChangesButton(cx + 244, cy + 2, 36, 14, this));

        if (com.kve.client.permission.ClientPermissionState.currentRole()
                != com.kve.common.permission.KvePermissionRole.ADMIN) {
            addRenderableWidget(new KvePlateButton(cx + 2, cy + 2, 60, 14, Lang.c("gui.kve.perm.refresh"),
                    b -> {
                        com.kve.client.permission.ClientPermissionState.reset();
                        com.kve.client.permission.ClientPermissionState.requestIfNeeded();
                        flashColor = 0xFFE0C060;
                        flashMsg = Lang.get("gui.kve.perm.checking");
                        flashAt = System.currentTimeMillis();
                    }));
        } else if (Minecraft.getInstance().getSingleplayerServer() == null) {
            addRenderableWidget(new KvePlateButton(cx + 2, cy + 2, 16, 14, Component.literal("\u2699"),
                    b -> minecraft.setScreen(new PermissionAdminScreen(this))));
        }
    }

    /**
     * Small status readout in the corner of the main menu so a player on a
     * multiplayer server can immediately see why buttons might be doing
     * nothing (VIEW role) instead of silently failing. Invisible in the
     * common singleplayer/LAN case since that always resolves to ADMIN and
     * this label is skipped for ADMIN to avoid clutter.
     */
    private void drawPermissionBadge(GuiGraphics g, int cx, int cy) {
        var role = com.kve.client.permission.ClientPermissionState.currentRole();
        if (role == com.kve.common.permission.KvePermissionRole.ADMIN) return;
        int color = switch (role) {
            case NONE -> 0xFFFF6666;
            case VIEW -> 0xFFE0C060;
            case EDIT -> 0xFF66FF99;
            case ADMIN -> 0xFF66FF99;
        };
        String label = Lang.get("gui.kve.perm.role." + role.name().toLowerCase(java.util.Locale.ROOT));
        Font font = Minecraft.getInstance().font;
        KveGuiStyle.drawTextSafe(g, label, cx + 4, cy + 4, W - 12, color);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int cx = (width  - W) / 2;
        int cy = Math.max(4, (height - H()) / 2);

        KveGuiStyle.drawWindow(g, cx, cy, W, H(), Lang.get("gui.kve.title"));
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.subtitle"), cx + 6, cy + 19, W - 12, KveGuiStyle.th(0xFF8899BB));
        KveGuiStyle.drawTextSafe(g, "§7" + Lang.get("gui.kve.main.shortcuts_hint"), cx + 6, cy + 19 + 9, W - 12, KveGuiStyle.th(0xFF667788));
        String ioFolder = CraftEditorState.getExportPath().getParent().toString();
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.export.path", ioFolder), cx + 6, cy + exportPathY, W - 12, KveGuiStyle.th(0xFF445566));
        drawPermissionBadge(g, cx, cy);

        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.main.section.content"), cx + 6, cy + headerContentY, W - 12, KveGuiStyle.ACCENT_GOLD);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.main.section.world"), cx + 6, cy + headerWorldY, W - 12, KveGuiStyle.ACCENT_GOLD);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.main.section.tools"), cx + 6, cy + headerToolsY, W - 12, KveGuiStyle.ACCENT_GOLD);

        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4000) {
            Font font = Minecraft.getInstance().font;
            KveGuiStyle.drawCenteredTextSafe(g, KveGuiStyle.truncate(font, flashMsg, W - 12), cx + W / 2, cy + H() - 9, flashColor);
        }

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        boolean typingSomewhere = getFocused() instanceof net.minecraft.client.gui.components.EditBox eb && eb.isFocused();
        if (!typingSomewhere && key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            switch (key - GLFW.GLFW_KEY_1 + 1) {
                case 1 -> com.kve.client.access.KveAccessGate.open(CraftEditorScreen::new);
                case 2 -> com.kve.client.access.KveAccessGate.open(MobEditorScreen::new);
                case 3 -> com.kve.client.access.KveAccessGate.open(ItemAttributeScreen::new);
                case 4 -> com.kve.client.access.KveAccessGate.open(TagEditorScreen::new);
                case 5 -> com.kve.client.access.KveAccessGate.open(BlockAttributeScreen::new);
                case 6 -> com.kve.client.access.KveAccessGate.open(LootTableEditorScreen::new);
                case 7 -> com.kve.client.access.KveAccessGate.open(TradeEditorScreen::new);
                case 8 -> { if (KveClientSettings.betaEventsEnabled()) com.kve.client.access.KveAccessGate.open(() -> new EventListScreen(this)); }
                case 9 -> com.kve.client.access.KveAccessGate.open(() -> new WorldGenListScreen(this));
            }
            return true;
        }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }
}
