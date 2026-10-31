package com.kve.client.gui.studio;

import com.kve.common.Lang;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.CodeEditorScreen;
import com.kve.client.gui.CraftEditorScreen;
import com.kve.client.gui.DiffPreviewScreen;
import com.kve.client.gui.EventListScreen;
import com.kve.client.gui.KveClientSettings;
import com.kve.client.gui.KveConfirmScreen;
import com.kve.client.gui.KveSettingsScreen;
import com.kve.client.gui.LootTableEditorScreen;
import com.kve.client.gui.MainMenuScreen;
import com.kve.client.gui.MobEditorScreen;
import com.kve.client.gui.PropertiesTabScreen;
import com.kve.client.gui.TagEditorScreen;
import com.kve.client.gui.TradeEditorScreen;
import com.kve.client.gui.WorldGenListScreen;
import com.kve.client.structure.StructureEditorScreen;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.client.keybind.KveShellKeybinds;
import com.kve.client.permission.ClientPermissionState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class KveStudioShell extends Screen implements KveTabHost {

    private static final int TAB_BAR_H = 20;
    private static final int BOTTOM_BAR_H = KveGuiStyle.ROW_H;
    private static final int PAD = 4;

    private static final java.util.Set<String> GROUP_STARTS = java.util.Set.of(
            "gui.kve.studio.tab.mobs", "gui.kve.studio.tab.structures",
            "gui.kve.studio.tab.events");
    private static final int GROUP_GAP = 4;

    private static final java.util.Set<String> OUT_OF_BAR = java.util.Set.of(
            "gui.kve.studio.tab.scan", "gui.kve.studio.tab.io");

    private final java.util.List<Integer> groupDividerX = new java.util.ArrayList<>();

    private static final class TabEntry {
        final String labelKey;
        final KveStudioTab tab;
        final Screen screenRef;
        final PendingDomain domain;
        final boolean groupStart;

        TabEntry(String labelKey, KveStudioTab tab) {
            this(labelKey, tab, null);
        }

        TabEntry(String labelKey, KveStudioTab tab, PendingDomain domain) {
            this.groupStart = GROUP_STARTS.contains(labelKey);
            this.labelKey = labelKey;
            this.tab = tab;
            this.screenRef = (Screen) tab;
            this.domain = domain;
        }
    }

    private final TabEntry[] tabs;
    private int activeIndex;

    private final int settingsIndex;
    private int lastNonSettingsIndex;

    private int tabScrollX = 0;
    private int tabScrollMax = 0;

    private void closeSettingsTab() {
        switchTo(lastNonSettingsIndex);
    }

    private int contentX, contentY, contentW, contentH;

    private int bottomBarTextX;
    private int bottomBarTextRight;

    private KvePlateButton applyBtn;
    private KvePlateButton diffBtn;
    private KvePlateButton discardBtn;
    private KvePlateButton pendingCountBtn;
    private int pendingCountBtnLastCount = -1;

    private String flashMsg = "";
    private long flashAt = 0;
    private int flashColor = KveGuiStyle.TEXT_OK;

    private static final int PENDING_AMBER = 0xFFE0C060;

    private boolean pendingListenerRegistered = false;
    private final PendingChangeManager.Listener pendingListener = this::onPendingQueueChanged;

    private void onPendingQueueChanged() {
        if (minecraft != null && minecraft.screen == this) init();
    }

    private Screen returnScreen = null;

    public KveStudioShell() {
        super(Lang.c("gui.kve.studio.title"));

        CraftEditorScreen recipes = new CraftEditorScreen();
        recipes.withReturnScreen(this);
        LootTableEditorScreen loot = new LootTableEditorScreen();
        loot.withReturnScreen(this);
        TagEditorScreen tags = new TagEditorScreen();
        tags.withReturnScreen(this);
        PropertiesTabScreen properties = new PropertiesTabScreen();
        properties.withReturnScreen(this);
        WorldGenListScreen worldgen = new WorldGenListScreen(this);
        MobEditorScreen mobs = new MobEditorScreen();
        mobs.withReturnScreen(this);
        StructureEditorScreen structures = new StructureEditorScreen();
        structures.withReturnScreen(this);
        EventListScreen events = new EventListScreen(this);
        CodeEditorScreen files = new CodeEditorScreen();
        files.withReturnScreen(this);
        TradeEditorScreen trades = new TradeEditorScreen();
        trades.withReturnScreen(this);
        com.kve.client.gui.ExternalScanTab scan = new com.kve.client.gui.ExternalScanTab();
        scan.withReturnScreen(this);
        com.kve.client.gui.ImportExportScreen io = new com.kve.client.gui.ImportExportScreen();
        io.withReturnScreen(this);
        KveSettingsScreen settingsTab = new KveSettingsScreen(this, this::closeSettingsTab);
        tabs = new TabEntry[] {
                new TabEntry("gui.kve.studio.tab.recipes", recipes, PendingDomain.RECIPES),
                new TabEntry("gui.kve.studio.tab.tags", tags, PendingDomain.TAGS),
                new TabEntry("gui.kve.studio.tab.loot", loot, PendingDomain.LOOT),
                new TabEntry("gui.kve.studio.tab.properties", properties, PendingDomain.CONTENT),
                new TabEntry("gui.kve.studio.tab.mobs", mobs, PendingDomain.CONTENT),
                new TabEntry("gui.kve.studio.tab.trades", trades, PendingDomain.TRADES),
                new TabEntry("gui.kve.studio.tab.structures", structures, PendingDomain.WORLD),
                new TabEntry("gui.kve.studio.tab.worldgen", worldgen, PendingDomain.WORLDGEN),
                new TabEntry("gui.kve.studio.tab.events", events, PendingDomain.EVENTS),
                new TabEntry("gui.kve.studio.tab.files", files, PendingDomain.RAW_SCRIPT),
                new TabEntry("gui.kve.studio.tab.scan", scan),
                new TabEntry("gui.kve.studio.tab.io", io),
                new TabEntry("gui.kve.studio.tab.settings", settingsTab),
        };
        settingsIndex = tabs.length - 1;

        activeIndex = rememberedActiveIndex;
        if (activeIndex >= 0 && activeIndex < tabs.length && hiddenBeta(activeIndex)) activeIndex = 0;
        // La primera vez en la sesion, arrancar en la pestania del
        // perfil elegido en la bienvenida (despues manda la ultima visitada).
        if (!personaStartApplied) {
            personaStartApplied = true;
            com.kve.client.gui.UserPersona persona = com.kve.client.gui.UserPersona.current();
            if (persona != null) {
                for (int i = 0; i < tabs.length; i++) {
                    if (tabs[i].labelKey.equals(persona.startTab) && !hiddenBeta(i)) {
                        activeIndex = i;
                        rememberedActiveIndex = i;
                        break;
                    }
                }
            }
        }
        lastNonSettingsIndex = activeIndex;
    }

    public KveStudioShell withRecipe(com.kve.common.recipe.RecipeData data, String originalIdToRemove) {
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i].screenRef instanceof com.kve.client.gui.CraftEditorScreen recipes) {
                if (data != null) recipes.loadExternalRecipe(data, originalIdToRemove);
                activeIndex = i;
                lastNonSettingsIndex = i;
                rememberedActiveIndex = i;
                break;
            }
        }
        return this;
    }

    /**
     * Abre la receta en la pestania Recetas del Studio. Reusa el Studio del que viene {@code from}
     * (o el que hospeda su editor de recetas); si no hay ninguno abre uno nuevo, nunca el editor suelto.
     */
    public static void openRecipe(Screen from, com.kve.common.recipe.RecipeData data, String originalIdToRemove) {
        KveStudioShell shell = from instanceof KveStudioShell s ? s
                : from instanceof CraftEditorScreen ces && ces.returnScreen() instanceof KveStudioShell s2 ? s2
                : new KveStudioShell();
        Minecraft.getInstance().setScreen(shell.withRecipe(data, originalIdToRemove));
    }

    private static int rememberedActiveIndex = 0;
    private static boolean personaStartApplied = false;
    /** Rects (x, y, w, h) de las pestanias del perfil elegido, para marcarlas con una linea dorada. */
    private final java.util.List<int[]> personaTabMarks = new java.util.ArrayList<>();
    private static long reloadOfferUntil = 0L;
    private KvePlateButton reloadOfferBtn;

    /** Fluent setter, mismo patron que {@code LootTableEditorScreen#withReturnScreen()}. */
    public KveStudioShell withReturnScreen(Screen s) {
        this.returnScreen = s;
        return this;
    }

    @Override
    protected void init() {
        if (!pendingListenerRegistered) {
            PendingChangeManager.addListener(pendingListener);
            pendingListenerRegistered = true;
        }
        clearWidgets();
        reloadOfferBtn = null;

        contentX = PAD;
        contentY = TAB_BAR_H;
        contentW = Math.max(1, width - PAD * 2);
        contentH = Math.max(1, height - TAB_BAR_H - BOTTOM_BAR_H);

        buildTabBar();
        buildBottomBar();

        TabEntry active = tabs[activeIndex];
        if (active.tab != null) {
            active.tab.init(contentX, contentY, contentW, contentH);
        }
    }

    private static final int TAB_SCROLL_ARROW_W = 12;
    private int tabViewportX0, tabViewportX1;

    private void buildTabBar() {
        int y = 2;
        int h = TAB_BAR_H - 4;

        String settingsLabel = Lang.get(tabs[settingsIndex].labelKey);
        int settingsW = Math.max(36, this.font.width(settingsLabel) + 10);
        int settingsX = width - PAD - settingsW;
        String toolsLabel = Lang.get("gui.kve.hub.btn");
        int toolsW = Math.max(36, this.font.width(toolsLabel) + 10);
        int toolsX = settingsX - 2 - toolsW;
        addRenderableWidget(new KvePlateButton(toolsX, y, toolsW, h, Component.literal(toolsLabel),
                b -> minecraft.setScreen(new com.kve.client.gui.PackHubScreen(this))));
        settingsX = toolsX;

        int[] tabW = new int[tabs.length];
        // settingsX ya deja un pequeno respiro (-4) contra Settings para que
        // la ultima tab normal nunca quede pegada a su borde.
        int available = Math.max(0, settingsX - PAD - 4);
        int totalW = 0;
        for (int pad : new int[] {10, 8, 6, 4}) {
            totalW = 0;
            for (int i = 0; i < tabs.length; i++) {
                if (i == settingsIndex || !inBar(i)) continue;
                tabW[i] = Math.max(pad == 10 ? 36 : 24, this.font.width(tabLabel(tabs[i])) + pad);
                totalW += tabW[i] + 2 + (tabs[i].groupStart || (i > 0 && hiddenBeta(i - 1) && tabs[i - 1].groupStart) ? GROUP_GAP : 0);
            }
            if (totalW > 0) totalW -= 2;
            if (totalW <= available) break;
        }
        boolean scrollable = totalW > available;
        tabScrollMax = scrollable ? (totalW - available) : 0;
        clampTabScroll();

        int viewX0 = PAD + (scrollable ? TAB_SCROLL_ARROW_W + 2 : 0);
        int viewX1 = settingsX - 4 - (scrollable ? TAB_SCROLL_ARROW_W + 2 : 0);
        tabViewportX0 = viewX0;
        tabViewportX1 = viewX1;

        if (scrollable) {
            addRenderableWidget(new KvePlateButton(PAD, y, TAB_SCROLL_ARROW_W, h, Component.literal("<"),
                    b -> { tabScrollX -= 40; clampTabScroll(); init(); }));
            addRenderableWidget(new KvePlateButton(settingsX - 4 - TAB_SCROLL_ARROW_W, y, TAB_SCROLL_ARROW_W, h,
                    Component.literal(">"), b -> { tabScrollX += 40; clampTabScroll(); init(); }));
        }

        int x = viewX0 - tabScrollX;
        groupDividerX.clear();
        personaTabMarks.clear();
        com.kve.client.gui.UserPersona persona = com.kve.client.gui.UserPersona.current();
        boolean first = true;
        boolean carryGroup = false;
        for (int i = 0; i < tabs.length; i++) {
            if (i != settingsIndex && hiddenBeta(i)) { carryGroup |= tabs[i].groupStart; continue; }
            if (i != settingsIndex && !inBar(i)) continue;
            TabEntry te = tabs[i];
            boolean groupStart = te.groupStart || carryGroup;
            carryGroup = false;
            String label = tabLabel(te);
            int idx = i;
            if (i != settingsIndex && groupStart && !first) {
                x += GROUP_GAP;
                int dx = x - 2 - GROUP_GAP / 2;
                if (!scrollable || (dx > viewX0 && dx < viewX1)) groupDividerX.add(dx);
            }
            if (i != settingsIndex) first = false;
            int w = (i == settingsIndex) ? settingsW : tabW[i];
            int bx = (i == settingsIndex) ? width - PAD - settingsW : x;
            KvePlateButton b = new KvePlateButton(bx, y, w, h, Component.literal(label), btn -> switchTo(idx));
            b.withSelected(idx == activeIndex);
            if (i != settingsIndex && scrollable) {
                boolean visible = bx >= viewX0 && bx + w <= viewX1;
                b.visible = visible;
                b.active = visible;
            }
            addRenderableWidget(b);
            if (persona != null && i != settingsIndex && b.visible && persona.tabs.contains(te.labelKey)) {
                personaTabMarks.add(new int[]{bx, y, w, h});
            }
            if (i != settingsIndex) x += w + 2;
        }
    }

    private String tabLabel(TabEntry te) {
        String label = Lang.get(te.labelKey);
        if (te.domain != null && PendingChangeManager.sizeForDomain(te.domain) > 0) label = label + " \u2022";
        return label;
    }

    private boolean inBar(int i) {
        if (hiddenBeta(i)) return false;
        return !OUT_OF_BAR.contains(tabs[i].labelKey) || i == activeIndex;
    }

    private static final java.util.Set<String> BETA_TABS = java.util.Set.of("gui.kve.studio.tab.events");

    /** Llamado por Ajustes al cambiar una opcion beta: rearma la barra de pestañas. */
    public void rebuildTabsForBeta() {
        if (hiddenBeta(rememberedActiveIndex)) rememberedActiveIndex = 0;
        if (hiddenBeta(activeIndex)) activeIndex = 0;
        if (minecraft != null) init();
    }

    private boolean hiddenBeta(int i) {
        if (i < 0 || i >= tabs.length) return false;
        return BETA_TABS.contains(tabs[i].labelKey) && !com.kve.client.gui.KveClientSettings.betaEventsEnabled();
    }

    /**
     * Abre la tab {@code labelKey} (tambien las que no van en la barra, como
     * Escaneo o Importar/Exportar) y devuelve este shell para mostrarlo.
     */
    public KveStudioShell showTab(String labelKey) {
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i].labelKey.equals(labelKey)) {
                if (minecraft != null && i != activeIndex) switchTo(i);
                else { activeIndex = i; if (i != settingsIndex) rememberedActiveIndex = i; }
                break;
            }
        }
        return this;
    }

    private void clampTabScroll() {
        if (tabScrollX < 0) tabScrollX = 0;
        if (tabScrollX > tabScrollMax) tabScrollX = tabScrollMax;
    }

    private void buildBottomBar() {
        int h = KveGuiStyle.BTN_H;
        int y = height - BOTTOM_BAR_H + (BOTTOM_BAR_H - h) / 2;
        int x = width - PAD;

        int exitW = Math.max(46, this.font.width(Lang.get("gui.kve.btn.menu")) + 12);
        KvePlateButton exit = new KvePlateButton(PAD, y, exitW, h, Lang.c("gui.kve.btn.menu"), b -> exitStudio());
        addRenderableWidget(exit);
        bottomBarTextX = PAD + exitW + 6;

        int applyW = Math.max(46, this.font.width(Lang.get("gui.kve.pending.apply")) + 12);
        x -= applyW;
        applyBtn = new KvePlateButton(x, y, applyW, h, Lang.c("gui.kve.pending.apply"), b -> applyPending()).withPrimary(true);
        applyBtn.active = PendingChangeManager.size() > 0;
        addRenderableWidget(applyBtn);

        int diffW = Math.max(40, this.font.width(Lang.get("gui.kve.diff.title")) + 12);
        x -= 2 + diffW;
        diffBtn = new KvePlateButton(x, y, diffW, h, Lang.c("gui.kve.diff.title"), b -> openDiff());
        diffBtn.active = PendingChangeManager.size() > 0;
        addRenderableWidget(diffBtn);

        int discardW = Math.max(46, this.font.width(Lang.get("gui.kve.pending.discard")) + 12);
        x -= 2 + discardW;
        discardBtn = new KvePlateButton(x, y, discardW, h, Lang.c("gui.kve.pending.discard"), b -> discardPending()).withDanger(true);
        discardBtn.active = PendingChangeManager.size() > 0;
        addRenderableWidget(discardBtn);

        int pendCount = PendingChangeManager.size();
        if (pendCount > 0) {
            int pendW = Math.max(46, this.font.width(Lang.get("gui.kve.pending.header", pendCount)) + 12);
            x -= 2 + pendW;
            pendingCountBtn = new KvePlateButton(x, y, pendW, h,
                    Component.literal(Lang.get("gui.kve.pending.header", pendCount)), b -> openDiff());
            addRenderableWidget(pendingCountBtn);
        } else {
            pendingCountBtn = null;
        }
        pendingCountBtnLastCount = pendCount;

        bottomBarTextRight = x - 6;
    }

    private void syncBottomBarState() {
        boolean hasPending = PendingChangeManager.size() > 0;
        if (applyBtn != null) applyBtn.active = hasPending;
        if (diffBtn != null) diffBtn.active = hasPending;
        if (discardBtn != null) discardBtn.active = hasPending;

        int count = PendingChangeManager.size();
        if (count != pendingCountBtnLastCount) {
            syncPendingCountButton(count);
        }
    }

    private void syncPendingCountButton(int count) {
        if (pendingCountBtn != null) {
            removeWidget(pendingCountBtn);
            pendingCountBtn = null;
        }
        if (count > 0 && discardBtn != null) {
            int h = KveGuiStyle.BTN_H;
            int pendW = Math.max(46, this.font.width(Lang.get("gui.kve.pending.header", count)) + 12);
            int x = discardBtn.getX() - 2 - pendW;
            pendingCountBtn = new KvePlateButton(x, discardBtn.getY(), pendW, h,
                    Component.literal(Lang.get("gui.kve.pending.header", count)), b -> openDiff());
            addRenderableWidget(pendingCountBtn);
            bottomBarTextRight = x - 6;
        } else if (discardBtn != null) {
            bottomBarTextRight = discardBtn.getX() - 6;
        }
        pendingCountBtnLastCount = count;
    }

    private void exitStudio() {
        minecraft.setScreen(returnScreen);
    }

    @Override public boolean hostsActive(Screen tab) { return isActiveTabInstance(tab); }

    @Override public void exitHost() { exitStudio(); }

    public static boolean exitIfHosted(Screen tabScreen, Screen returnScreen) {
        return exitIfHosted(tabScreen, tabScreen, returnScreen);
    }

    /** Owner-aware overload - ver Javadoc de {@link #exitIfHosted(Screen, Screen)} y el mismo patron en {@link #reclaimIfHosted(Screen, Screen, Screen)}. */
    public static boolean exitIfHosted(Screen currentScreen, Screen ownerTabScreen, Screen returnScreen) {
        if (!(returnScreen instanceof KveTabHost host)) return false;
        if (!host.hostsActive(ownerTabScreen)) return false;
        if (Minecraft.getInstance().screen != host) return false;
        host.exitHost();
        return true;
    }

    private void openDiff() {
        if (PendingChangeManager.isEmpty()) return;
        minecraft.setScreen(new DiffPreviewScreen(this, PendingChangeManager.snapshot(), this::doApplyPending));
    }

    private void discardPending() {
        if (!ClientPermissionState.canEdit()) {
            flashColor = 0xFFFF6666;
            flashMsg = Lang.get("gui.kve.perm.denied");
            flashAt = System.currentTimeMillis();
            return;
        }
        int count = PendingChangeManager.size();
        if (count == 0) return;
        if (KveClientSettings.getOrDefault(KveClientSettings.CONFIRM_DISCARDS, false)) {
            minecraft.setScreen(new KveConfirmScreen(
                    confirmed -> {
                        minecraft.setScreen(this);
                        if (!confirmed) return;
                        doDiscardPending(count);
                    },
                    Component.literal(Lang.get("gui.kve.settings.confirm_discards.title")),
                    Component.literal(Lang.get("gui.kve.settings.confirm_discards.msg", count))));
            return;
        }
        doDiscardPending(count);
    }

    private void doDiscardPending(int count) {
        PendingChangeManager.discardAll();
        flashColor = KveGuiStyle.TEXT_OK;
        flashMsg = Lang.get("gui.kve.pending.discarded", count);
        flashAt = System.currentTimeMillis();
    }

    private void switchTo(int idx) {
        if (idx == activeIndex || tabs[idx].tab == null) return;
        TabEntry cur = tabs[activeIndex];
        if (cur.tab != null) cur.tab.removed();
        if (activeIndex != settingsIndex) lastNonSettingsIndex = activeIndex;
        activeIndex = idx;
        if (activeIndex != settingsIndex) rememberedActiveIndex = activeIndex;
        PendingDomain domain = tabs[idx].domain;
        boolean flashActive = !flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 3000;
        if (domain != null && !flashActive) {
            int here = PendingChangeManager.sizeForDomain(domain);
            if (here > 0) {
                flashColor = PENDING_AMBER;
                flashMsg = Lang.get("gui.kve.studio.pending_here", here);
                flashAt = System.currentTimeMillis();
            }
        }
        init();
    }

    private void applyPending() {
        if (!ClientPermissionState.canApply()) {
            flashColor = 0xFFFF6666;
            flashMsg = Lang.get("gui.kve.perm.denied");
            flashAt = System.currentTimeMillis();
            return;
        }
        if (PendingChangeManager.isEmpty()) return;
        java.util.Set<String> pendingIds = new java.util.HashSet<>();
        for (var c : PendingChangeManager.snapshot()) pendingIds.add(c.entryId());
        var broken = com.kve.common.search.PackDiagnostics.forEntries(pendingIds);
        if (!broken.isEmpty()) {
            minecraft.setScreen(new com.kve.client.gui.KveConfirmScreen(
                    confirmed -> {
                        minecraft.setScreen(this);
                        if (confirmed) applyAfterIdCheck();
                    },
                    Component.literal(Lang.get("gui.kve.health.apply_broken.title")),
                    Component.literal(Lang.get("gui.kve.health.apply_broken.msg", broken.size(), broken.get(0).ref().id()))));
            return;
        }
        applyAfterIdCheck();
    }

    private void applyAfterIdCheck() {
        if (com.kve.client.gui.KveClientSettings.getOrDefault(com.kve.client.gui.KveClientSettings.REVIEW_BEFORE_APPLY, false)) {
            int count = PendingChangeManager.size();
            minecraft.setScreen(new com.kve.client.gui.KveConfirmScreen(
                    confirmed -> {
                        minecraft.setScreen(this);
                        if (!confirmed) return;
                        doApplyPending();
                    },
                    Component.literal(Lang.get("gui.kve.settings.review_before_apply.title")),
                    Component.literal(Lang.get("gui.kve.settings.review_before_apply.msg", count))));
            return;
        }
        doApplyPending();
    }

    private void doApplyPending() {
        PendingChangeManager.ApplyResult result = PendingChangeManager.applyAll();
        var fb = com.kve.client.gui.KveToast.applied(result);
        flashAt = System.currentTimeMillis();
        flashMsg = fb.message();
        flashColor = !result.allSucceeded() ? PENDING_AMBER : KveGuiStyle.TEXT_OK;
        if (fb.offerReload() && minecraft.player != null) reloadOfferUntil = System.currentTimeMillis() + 60_000L;
    }

    private void syncReloadOffer() {
        boolean want = reloadOfferUntil > System.currentTimeMillis() && PendingChangeManager.isEmpty() && minecraft != null && minecraft.player != null;
        if (want && reloadOfferBtn == null) {
            String label = Lang.get("gui.kve.pending.reload_now");
            int w = Math.max(46, this.font.width(label) + 12);
            int h = KveGuiStyle.BTN_H;
            int y = height - BOTTOM_BAR_H + (BOTTOM_BAR_H - h) / 2;
            int x = Math.max(bottomBarTextX, bottomBarTextRight - w);
            reloadOfferBtn = new KvePlateButton(x, y, w, h, Component.literal(label), b -> {
                reloadOfferUntil = 0L;
                try { minecraft.player.connection.sendCommand("reload"); } catch (Throwable ignored) {}
                flashColor = KveGuiStyle.TEXT_OK;
                flashMsg = Lang.get("gui.kve.pending.reload_sent");
                flashAt = System.currentTimeMillis();
            }).withSelected(true);
            bottomBarTextRight = x - 6;
            addRenderableWidget(reloadOfferBtn);
        } else if (!want && reloadOfferBtn != null) {
            removeWidget(reloadOfferBtn);
            reloadOfferBtn = null;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        syncBottomBarState();
        TabEntry active = tabs[activeIndex];
        this.renderBackground(g);
        if (active.tab != null) {
            active.tab.render(g, mx, my, pt);
        }

        g.fill(0, 0, width, TAB_BAR_H, KveGuiStyle.BG_TITLEBAR);
        g.fill(0, height - BOTTOM_BAR_H, width, height, KveGuiStyle.BG_TITLEBAR);
        g.hLine(0, width - 1, TAB_BAR_H, KveGuiStyle.BORDER_SUBTLE);
        g.hLine(0, width - 1, height - BOTTOM_BAR_H, KveGuiStyle.BORDER_SUBTLE);
        for (int dx : groupDividerX) g.vLine(dx, 4, TAB_BAR_H - 4, KveGuiStyle.BORDER_SUBTLE);
        syncReloadOffer();

        super.render(g, mx, my, pt);
        // Pestanias del perfil (bienvenida): linea dorada abajo, para encontrarlas de un vistazo.
        for (int[] r : personaTabMarks) g.fill(r[0] + 3, r[1] + r[3] - 2, r[0] + r[2] - 3, r[1] + r[3] - 1, KveGuiStyle.ACCENT_GOLD);

        int count = PendingChangeManager.size();
        boolean flashActive = !flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 3000;
        String pendingText = flashActive ? flashMsg
                : (count > 0 ? Lang.get("gui.kve.pending.header", count) : Lang.get("gui.kve.pending.empty"));
        int pendingTextX = bottomBarTextX;
        int pendingTextW = Math.max(0, bottomBarTextRight - pendingTextX);
        int pendingTextY = height - BOTTOM_BAR_H + 5;
        int pendingTextColor = flashActive ? flashColor : (count > 0 ? PENDING_AMBER : KveGuiStyle.TEXT_MUTED);
        KveGuiStyle.drawTextSafe(g, pendingText, pendingTextX, pendingTextY, pendingTextW, pendingTextColor);

        if (!flashActive && count > 0 && KveGuiStyle.isHovered(mx, my, pendingTextX, height - BOTTOM_BAR_H, pendingTextW, BOTTOM_BAR_H)) {
            java.util.List<Component> lines = new java.util.ArrayList<>();
            for (PendingDomain d : PendingChangeManager.domainsPresent()) {
                lines.add(Component.literal(Lang.get(d.labelKey) + ": " + PendingChangeManager.sizeForDomain(d)));
            }
            g.renderComponentTooltip(font, lines, mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // un menu "Mas" abierto recibe el clic antes que lo que tenga debajo
        if (com.kve.client.gui.KveMenuButton.consumeClick(mx, my, button)) return true;
        if (super.mouseClicked(mx, my, button)) return true;
        TabEntry active = tabs[activeIndex];
        return active.tab != null && active.tab.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (super.mouseDragged(mx, my, button, dx, dy)) return true;
        TabEntry active = tabs[activeIndex];
        return active.tab != null && active.tab.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean shellHandled = super.mouseReleased(mx, my, button);
        TabEntry active = tabs[activeIndex];
        boolean tabHandled = active.screenRef != null && active.screenRef.mouseReleased(mx, my, button);
        return shellHandled || tabHandled;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (my < TAB_BAR_H && tabScrollMax > 0) {
            tabScrollX -= (int) Math.round(delta * 20);
            clampTabScroll();
            init();
            return true;
        }
        TabEntry active = tabs[activeIndex];
        if (active.screenRef != null && active.screenRef.mouseScrolled(mx, my, delta)) return true;
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        TabEntry active = tabs[activeIndex];
        if (active.screenRef != null && active.screenRef.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int mods) {
        TabEntry active = tabs[activeIndex];
        if (active.tab != null && active.tab.keyPressed(key, scancode, mods)) return true;
        if (KveShellKeybinds.Action.APPLY.matches(key, mods)) { applyPending(); return true; }
        if (KveShellKeybinds.Action.DISCARD.matches(key, mods)) { discardPending(); return true; }
        if (KveShellKeybinds.Action.DIFF.matches(key, mods)) { openDiff(); return true; }
        if (KveShellKeybinds.Action.NEXT_TAB.matches(key, mods)) { cycleTab(1); return true; }
        if (KveShellKeybinds.Action.PREV_TAB.matches(key, mods)) { cycleTab(-1); return true; }
        return super.keyPressed(key, scancode, mods);
    }

    private void cycleTab(int dir) {
        int n = tabs.length;
        int idx = activeIndex;
        for (int i = 0; i < n; i++) {
            idx = ((idx + dir) % n + n) % n;
            if (tabs[idx].tab != null && !hiddenBeta(idx)) { switchTo(idx); return; }
        }
    }

    @Override
    public void onClose() {
        exitStudio();
    }

    @Override
    public void removed() {
        PendingChangeManager.removeListener(pendingListener);
        pendingListenerRegistered = false;
        TabEntry active = tabs[activeIndex];
        if (active.tab != null) active.tab.removed();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean isActiveTabInstance(Screen s) {
        if (s == null || tabs.length == 0) return false;
        return tabs[activeIndex].screenRef == s;
    }

    public static boolean reclaimIfHosted(Screen tabScreen, Screen returnScreen) {
        return reclaimIfHosted(tabScreen, tabScreen, returnScreen);
    }

    public static boolean reclaimIfHosted(Screen currentScreen, Screen ownerTabScreen, Screen returnScreen) {
        if (!(returnScreen instanceof KveTabHost host)) return false;
        if (!host.hostsActive(ownerTabScreen)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != currentScreen) return false;
        mc.setScreen((Screen) host);
        return true;
    }

    public static boolean willReclaim(Screen ownerTabScreen, Screen returnScreen) {
        if (!(returnScreen instanceof KveTabHost host)) return false;
        return host.hostsActive(ownerTabScreen);
    }


    public boolean jumpToTab(String labelKey) {
        return jumpToTab(labelKey, null);
    }

    public boolean jumpToTab(String labelKey, String selectEntryId) {
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i].labelKey.equals(labelKey)) {
                if (tabs[i].tab == null) return false;
                switchTo(i);
                if (selectEntryId != null && !selectEntryId.isBlank() && tabs[i].screenRef instanceof TradeEditorScreen trades) {
                    trades.selectEntryById(selectEntryId);
                }
                if (selectEntryId != null && !selectEntryId.isBlank() && tabs[i].screenRef instanceof LootTableEditorScreen loot) {
                    loot.selectEntryById(selectEntryId);
                }
                if (selectEntryId != null && !selectEntryId.isBlank() && tabs[i].screenRef instanceof com.kve.client.gui.PropertiesTabScreen props) {
                    props.selectItemById(selectEntryId);
                }
                return true;
            }
        }
        return false;
    }
}
