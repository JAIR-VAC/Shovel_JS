package com.kve.client.gui;

import com.kve.common.code.CodeEditOps;
import com.kve.common.code.CodeUndoHistory;
import com.kve.common.Lang;
import com.kve.client.gui.studio.KveStudioShell;
import com.kve.client.gui.studio.KveStudioTab;
import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.client.permission.ClientPermissionState;
import com.kve.common.kubejs.LootTableExporter;
import com.kve.common.kubejs.ScriptWriteGuard;
import com.kve.common.kubejs.SemanticValidator;
import com.kve.common.kubejs.TagExporter;
import com.kve.common.recipetype.RecipeTypeDescriptor;
import com.kve.common.recipetype.RecipeTypeDescriptorRegistry;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@OnlyIn(Dist.CLIENT)
public class CodeEditorScreen extends net.minecraft.client.gui.screens.Screen implements KveStudioTab {

    private static final int CONTENT_Y_EXTRA = 14;
    private static final int GUI_H_DEFAULT = 210 + CONTENT_Y_EXTRA;
    private int contentYExtra = 0;
    private int GUI_H = GUI_H_DEFAULT;
    private static final int LIST_W = 132;
    private static final int GUI_W_MIN = 360;
    private static final int ROW_H = 13;
    private static final int LINE_H = 10;
    private static final int TAB_STRIP_H = 14;
    private static final long DOUBLE_CLICK_MS = 350;

    private static final Path SERVER_SCRIPTS_DIR = Paths.get("kubejs", "server_scripts", "shovel_js");
    private static final Path STARTUP_SCRIPTS_DIR = Paths.get("kubejs", "startup_scripts", "shovel_js");
    private static final Path DATA_DIR = Paths.get("kubejs", "data");

    private static final String[] KEYWORDS = {
            "function", "return", "const", "let", "var", "if", "else", "for", "while",
            "new", "true", "false", "null", "undefined", "typeof", "this", "break", "continue",
            "switch", "case", "default", "in", "of", "delete"
    };

    private record FileEntry(String category, Path path, long size, long mtime, boolean missing) {
        boolean editable() { return !category.equals("structures"); }
    }

    private static final class EditorTab {
        // No-final: antes
        // era final porque nada cambiaba el path de un tab ya abierto -
        // renombrar el archivo respaldado por este buffer (doRename())
        // necesita actualizarlo en el lugar para no tener que cerrar/
        // reabrir la pestaña (y perder cursor/scroll/undo) solo por un
        // rename.
        Path path;
        final List<String> lines = new ArrayList<>();
        String lastSaved;
        String queuedText;
        int cursorLine = 0, cursorCol = 0;
        int scroll = 0;

        EditorTab(Path path, String text) {
            this.path = path;
            this.lastSaved = text;
            for (String ln : text.split("\n", -1)) lines.add(ln);
            if (lines.isEmpty()) lines.add("");
        }

        String currentText() { return String.join("\n", lines); }
        boolean dirty() { return !currentText().equals(queuedText != null ? queuedText : lastSaved); }

        int anchorLine = -1, anchorCol = 0;
        final com.kve.common.code.CodeUndoHistory history = new com.kve.common.code.CodeUndoHistory();

        float hScroll = 0;
        int followLine = -1, followCol = -1;
        int metricsHash = 0;
        boolean metricsValid = false;
        int maxLineW = 0, maxIcons = 0;

        com.kve.common.code.CodeEditOps.Pos cursor() { return new com.kve.common.code.CodeEditOps.Pos(cursorLine, cursorCol); }
        com.kve.common.code.CodeEditOps.Pos anchor() { return new com.kve.common.code.CodeEditOps.Pos(anchorLine, anchorCol); }
        boolean hasSelection() { return anchorLine >= 0 && (anchorLine != cursorLine || anchorCol != cursorCol); }
        void clearSelection() { anchorLine = -1; }
        void setAnchorHere() { anchorLine = cursorLine; anchorCol = cursorCol; }
        void moveCursor(com.kve.common.code.CodeEditOps.Pos p) { cursorLine = p.line(); cursorCol = p.col(); }
        com.kve.common.code.CodeEditOps.Pos selStart() { return com.kve.common.code.CodeEditOps.Pos.min(cursor(), anchor()); }
        com.kve.common.code.CodeEditOps.Pos selEnd() { return com.kve.common.code.CodeEditOps.Pos.max(cursor(), anchor()); }
        String selectedText() { return com.kve.common.code.CodeEditOps.textBetween(lines, anchor(), cursor()); }
        boolean deleteSelection() {
            if (!hasSelection()) { clearSelection(); return false; }
            moveCursor(com.kve.common.code.CodeEditOps.deleteRange(lines, anchor(), cursor()));
            clearSelection();
            return true;
        }
        com.kve.common.code.CodeUndoHistory.Snapshot snapshot() {
            return new com.kve.common.code.CodeUndoHistory.Snapshot(currentText(), cursorLine, cursorCol);
        }
        void restore(com.kve.common.code.CodeUndoHistory.Snapshot s) {
            lines.clear();
            for (String ln : s.text().split("\n", -1)) lines.add(ln);
            if (lines.isEmpty()) lines.add("");
            moveCursor(com.kve.common.code.CodeEditOps.clamp(lines, new com.kve.common.code.CodeEditOps.Pos(s.line(), s.col())));
            clearSelection();
        }
    }

    private Screen returnScreen;
    private boolean hosted = false;
    private int L, T, guiW;
    private int hostedH = 0;

    private final List<FileEntry> entries = new ArrayList<>();
    private final List<FileEntry> filtered = new ArrayList<>();
    private final Set<Path> pinned = new LinkedHashSet<>();
    private record DisplayRow(boolean header, String label, int filteredIdx) {}
    private final List<DisplayRow> displayRows = new ArrayList<>();
    private ScrollBar fileScroll;
    private EditBox searchBox;
    private String searchFilter = "";
    private record MenuItem(String label, String shortcut, Runnable action) {}
    private static final int MENU_ROW_H = 12, MENU_SEP_H = 5;
    private final KvePlateButton[] menuButtons = new KvePlateButton[3];
    private int openMenu = -1;
    private List<MenuItem> menuItems = List.of();
    private int menuX, menuY;
    private int selectedIdx = -1;
    private int lastClickIdx = -1;
    private long lastClickAt = 0;
    private final ListKeyNav listNav = new ListKeyNav();

    private final List<EditorTab> openTabs = new ArrayList<>();
    private int activeTab = -1;
    private ScrollBar editorScroll;
    private int visibleRows = 1;
    private int textX, textY, textW;
    private int gutterW = 0;
    private int tabScrollX = 0;

    private static final Path LOG_DIR = Paths.get("logs", "kubejs");
    private static final String[] LOG_SCOPES = {"startup", "server", "client"};
    private static final int LOG_TAIL_BYTES = 48 * 1024;
    private static final int LOG_MAX_LINES = 300;

    private record ConsoleLine(String text, int color, String plainHint) {
        ConsoleLine(String text, int color) { this(text, color, null); }
    }

    private boolean consoleMode = false;
    private int consoleScopeIdx = 1;
    private final List<ConsoleLine> consoleLines = new ArrayList<>();
    private ScrollBar consoleScroll;

    private String flashMsg = "";
    private long flashAt = 0;
    private boolean flashError = false;

    // "Buscar en archivo"
    // (Ctrl+F). Sin modelo de seleccion (ver Javadoc largo mas abajo sobre
    // Ctrl+C/X/V - agregar uno es cambio de alcance mucho mayor), asi que
    // esto es deliberadamente solo "saltar a la proxima ocurrencia" con un
    // resaltado puramente visual (no editable, no interactua con
    // copiar/cortar/tipear) - lastSearchTerm sobrevive entre aperturas de
    // Ctrl+F para poder repetir la busqueda con Ctrl+F, Enter sin retipear.
    // El resaltado se limpia al cambiar de pestaña/reabrir un archivo (ver
    // openInTab()/closeTab()) para que nunca apunte a una linea/columna de OTRO
    // archivo.
    private String lastSearchTerm = "";

    // s autoguardado de borradores.
    // Ver CodeDraftStore - el script real nunca se toca, solo la copia aparte.
    private static final com.kve.common.code.CodeDraftStore DRAFTS =
            new com.kve.common.code.CodeDraftStore(Paths.get("config", "shovel_js", "code_drafts"));
    private static final long AUTOSAVE_INTERVAL_MS = 5000L;
    private long lastAutosaveAt = 0L;
    private final java.util.Map<Path, Integer> autosavedHash = new java.util.HashMap<>();
    private int searchHighlightLine = -1, searchHighlightStart = -1, searchHighlightEnd = -1;

    private static final String[] CORE_SNIPPETS = {
            "StartupEvents.registry('item', event => { })",
            "StartupEvents.registry('block', event => { })",
            "event.create('id')",

            "ServerEvents.recipes(event => { })",
            "event.shaped('id', ['XXX', 'XXX', 'XXX'], { X: 'minecraft:stick' })",
            "event.shapeless('id', ['minecraft:stick'])",
            "event.smelting('id', 'minecraft:iron_ingot', 'minecraft:iron_ore')",
            "event.remove({ output: 'minecraft:stick' })",
            "event.custom({ type: 'minecraft:crafting_shaped' })",
            "event.replaceInput({ input: 'minecraft:stick' }, 'minecraft:stick', '#minecraft:saplings')",
            "event.replaceOutput({ output: 'minecraft:stick' }, 'minecraft:stick', 'minecraft:blaze_rod')",

            "ServerEvents.tags('item', event => { })",
            "ServerEvents.tags('block', event => { })",
            "event.add('forge:ingots/copper', 'minecraft:iron_ingot')",
            "event.remove('forge:ingots/copper', 'minecraft:iron_ingot')",
            "event.removeAll('forge:ingots/copper')",

            "ItemEvents.modification(event => { event.modify('minecraft:diamond', item => { }) })",
            "BlockEvents.modification(event => { event.modify('minecraft:stone', block => { }) })",
            "ItemEvents.foodEaten('minecraft:diamond', event => { })",
            "ItemEvents.crafted(event => { })",
            "ItemEvents.rightClicked(event => { })",

            "EntityEvents.spawned(event => { })",
            "EntityEvents.death(event => { })",
            "EntityEvents.hurt(event => { })",
            "EntityEvents.breed(event => { })",
            "EntityEvents.tame(event => { })",
            "EntityEvents.interact(event => { })",
            "PlayerEvents.tick(event => { })",
            "PlayerEvents.loggedIn(event => { })",
            "PlayerEvents.loggedOut(event => { })",
            "PlayerEvents.respawned(event => { })",
            "PlayerEvents.advancement(event => { })",

            "ServerEvents.loaded(event => { })",
            "ServerEvents.tick(event => { })",
            "ServerEvents.commandRegistry(event => { })",

            "console.log()",
            "console.error()",
            "Item.of('minecraft:stick')",
            "Ingredient.of('minecraft:stick')",
    };

    private boolean acOpen = false;
    private final List<String> acCandidates = new ArrayList<>();
    private int acSelected = 0;
    private int acScroll = 0;
    private int acPrefixStart = 0;

    private boolean diagDirty = true;
    private static final long DIAG_DEBOUNCE_MS = 300;
    private long diagEditSig = Long.MIN_VALUE, diagEditAt = 0;
    private static String stripCommentsForDiagnostics(String line, boolean[] inBlockRef, boolean[] inTemplateRef) {
        int n = line.length();
        StringBuilder out = new StringBuilder(n);
        boolean inBlock = inBlockRef[0];
        boolean inStr = inTemplateRef[0];
        char strCh = inStr ? '`' : 0;
        int i = 0;
        while (i < n) {
            char c = line.charAt(i);
            if (inBlock) {
                if (c == '*' && i + 1 < n && line.charAt(i + 1) == '/') {
                    out.append("  ");
                    i += 2;
                    inBlock = false;
                } else {
                    out.append(' ');
                    i++;
                }
                continue;
            }
            if (inStr) {
                out.append(c);
                if (c == '\\' && i + 1 < n) { out.append(line.charAt(i + 1)); i += 2; continue; }
                if (c == strCh) inStr = false;
                i++;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') { inStr = true; strCh = c; out.append(c); i++; continue; }
            if (c == '/' && i + 1 < n && line.charAt(i + 1) == '/') {
                while (i < n) { out.append(' '); i++; }
                break;
            }
            if (c == '/' && i + 1 < n && line.charAt(i + 1) == '*') {
                out.append("  ");
                i += 2;
                inBlock = true;
                continue;
            }
            out.append(c);
            i++;
        }
        inBlockRef[0] = inBlock;
        inTemplateRef[0] = inStr && strCh == '`';
        return out.toString();
    }

    private record DiagIssue(int start, int end, String message) {}
    private final java.util.Map<Integer, List<DiagIssue>> diagRanges = new java.util.HashMap<>();
    private int diagCount = 0;
    private static final Pattern QUOTED_ID = Pattern.compile("['\"]([a-z0-9_.\\-]+:[a-z0-9_./\\-]+)['\"]");
    private static final Pattern IF_WHILE_CONDITION =
            Pattern.compile("\\b(?:if|while)\\s*\\(([^()]*(?:\\([^()]*\\)[^()]*)*)\\)");

    public CodeEditorScreen(Screen parent) {
        this();
        this.returnScreen = parent;
    }

    public CodeEditorScreen() {
        super(Lang.c("gui.kve.files.title"));
        pinned.addAll(KveFilesUiState.loadPinned());
        searchFilter = KveFilesUiState.loadLastSearch();
    }

    /**
     * Persiste busqueda + toggle "Mas opciones" al cerrar la pantalla
     * (una sola escritura a disco por sesion en vez de una por tecla en
     * el searchBox - ver Javadoc de la clase). Los pines ya persisten
     * aparte, en el momento exacto en que cambian ({@link #togglePinSelected()}/
     * {@link #performDelete()}), porque perder un pin silenciosamente si el
     * juego crashea antes de cerrar la pantalla seria peor que la escritura
     * extra.
     */
    @Override
    public void removed() {
        KveFilesUiState.saveLastSearch(searchFilter);
        super.removed();
    }

    public CodeEditorScreen withReturnScreen(Screen s) {
        this.returnScreen = s;
        return this;
    }

    @Override
    protected void init() {
        if (KveStudioShell.reclaimIfHosted(this, returnScreen)) return;
        hosted = false;
        guiW = Math.max(GUI_W_MIN, width - 40);
        GUI_H = GUI_H_DEFAULT;
        L = (width - guiW) / 2;
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
        hostedH = h;
        L = x0;
        T = y0;
        guiW = Math.max(GUI_W_MIN, w);
        GUI_H = Math.max(h, GUI_H_DEFAULT);
        buildWidgets();
    }

    private void refresh() {
        if (hosted) GUI_H = Math.max(hostedH, GUI_H_DEFAULT);
        buildWidgets();
    }

    private void buildWidgets() {
        clearWidgets();
        setFocused(null);

        scan();
        refreshFiltered();
        if (selectedIdx >= filtered.size()) selectedIdx = -1;

        int toolbarY = T + 2;
        closeMenu();

        int rightBx = L + guiW - 2;
        rightBx -= addToolbarBtnRight(Lang.get("gui.kve.code.save"), rightBx, toolbarY, this::onSaveActive);
        rightBx -= addToolbarBtnRight(Lang.get("gui.kve.code.discard"), rightBx, toolbarY, this::onDiscardActive);

        toolbarCursorX = L + 2;
        toolbarCursorY = toolbarY;
        toolbarRow1Limit = rightBx - 4;
        toolbarWrapLimit = L + guiW - 2;

        String[] menuLabels = {
                Lang.get("gui.kve.code.menu.file") + " ▾",
                Lang.get("gui.kve.code.menu.edit") + " ▾",
                Lang.get("gui.kve.code.menu.run") + " ▾",
        };
        toolbarGroup(menuLabels);
        for (int i = 0; i < menuLabels.length; i++) {
            int idx = i;
            menuButtons[i] = addToolbarBtn(menuLabels[i], () -> toggleMenu(idx));
        }
        String lblValidate = Lang.get("gui.kve.code.validate");
        String lblPrev = Lang.get("gui.kve.code.diag_prev");
        String lblNext = Lang.get("gui.kve.code.diag_next");
        toolbarGroup(lblValidate, lblPrev, lblNext);
        addToolbarBtn(lblValidate, this::runDiagnostics);
        addToolbarBtn(lblPrev, () -> jumpToDiagnostic(false));
        addToolbarBtn(lblNext, () -> jumpToDiagnostic(true));
        String lblReloadClient = Lang.get("gui.kve.code.reload_client");
        toolbarGroup(lblReloadClient);
        addToolbarBtn(lblReloadClient, () -> onReloadKube(null));
        contentYExtra = toolbarCursorY - toolbarY;

        searchBox = new KveScaledEditBox(font, L + 2, T + 30 + contentYExtra, LIST_W - 4, 11, Lang.c("gui.kve.files.search_hint"));
        searchBox.setMaxLength(64);
        searchBox.setHint(Lang.c("gui.kve.files.search_hint"));
        searchBox.setValue(searchFilter);
        searchBox.setResponder(s -> { searchFilter = s; selectedIdx = -1; listNav.reset(); refreshFiltered(); });
        addRenderableWidget(searchBox);

        int rowsVisible = fileRowsVisible();
        fileScroll = new ScrollBar(L + LIST_W - 8, T + 43 + contentYExtra, rowsVisible * ROW_H, displayRows.size(), rowsVisible);

        textY = T + 43 + contentYExtra + TAB_STRIP_H;
        visibleRows = Math.max(1, (T + GUI_H - 4 - textY) / LINE_H - 1);
        updateEditorGeometry();
        consoleScroll = new ScrollBar(L + guiW - 8, textY, visibleRows * LINE_H, consoleLines.size(), visibleRows);
    }

    private int toolbarCursorX;
    private int toolbarCursorY;
    private int toolbarRow1Limit;
    private int toolbarWrapLimit;

    private KvePlateButton addToolbarBtn(String label, Runnable action) {
        int w = Math.max(20, font.width(label) + 8);
        int limit = toolbarCursorY == T + 2 ? toolbarRow1Limit : toolbarWrapLimit;
        if (toolbarCursorX + w > limit && toolbarCursorX > L + 2) {
            toolbarCursorX = L + 2;
            toolbarCursorY += 14;
        }
        KvePlateButton b = addRenderableWidget(new KvePlateButton(toolbarCursorX, toolbarCursorY, w, 12, Component.literal(label), btn -> action.run()));
        toolbarCursorX += w + 2;
        return b;
    }

    /**
     * Inicio de un grupo de botones del toolbar: si el grupo
     * ENTERO (labels, con el mismo ancho que usara addToolbarBtn()) no entra en
     * lo que queda de la linea actual, salta a la linea siguiente ANTES de
     * empezarlo - una familia de botones nunca queda partida entre dos
     * filas; si entra, deja 6px de aire respecto al grupo anterior. Un grupo
     * mas ancho que una fila entera igual wrappea boton a boton como siempre
     * (addToolbarBtn()). No hace nada al inicio de una linea.
     */
    private void toolbarGroup(String... labels) {
        if (toolbarCursorX <= L + 2) return;
        int total = -2;
        for (String l : labels) total += Math.max(20, font.width(l) + 8) + 2;
        int limit = toolbarCursorY == T + 2 ? toolbarRow1Limit : toolbarWrapLimit;
        if (toolbarCursorX + total > limit) {
            toolbarCursorX = L + 2;
            toolbarCursorY += 14;
        } else {
            toolbarCursorX += 6;
        }
    }

    private int addToolbarBtnRight(String label, int x, int y, Runnable action) {
        int w = Math.max(20, font.width(label) + 8);
        addRenderableWidget(new KvePlateButton(x - w, y, w, 12, Component.literal(label), b -> action.run()));
        return w + 2;
    }

    private int fileRowsVisible() {
        return Math.max(1, (GUI_H - 46 - contentYExtra) / ROW_H);
    }

    private static float editorZoom = 1.0f;
    private static final float ZOOM_MIN = 0.5f, ZOOM_MAX = 3.0f;
    private static final int MAX_GUTTER_ICONS = 4;
    private int editorRows = 1;
    private int gutterIconSlots = 1;
    private boolean panning = false;
    private double panAccY = 0;

    private float editorScale() { return KveGuiStyle.uiTextScale() * editorZoom; }

    private int lineH() { return Math.max(6, Math.round(LINE_H * editorZoom)); }

    private int gutterIconSize() { return Math.max(6, Math.min(lineH() - 1, Math.round(8 * editorScale()))); }

    private void updateEditorGeometry() {
        float scale = editorScale();
        int numW = Math.round(font.width("0000") * scale) + 6;
        gutterW = numW + gutterIconSlots * (gutterIconSize() + 1) + 2;
        textX = L + LIST_W + 6 + gutterW;
        textW = guiW - LIST_W - 10 - gutterW;
        editorRows = Math.max(1, (statusBarY() - 1 - textY) / lineH());
        int keep = editorScroll != null ? editorScroll.getScroll() : 0;
        editorScroll = new ScrollBar(L + guiW - 8, textY, editorRows * lineH(),
                activeTab >= 0 ? openTabs.get(activeTab).lines.size() : 0, editorRows);
        editorScroll.setScroll(activeTab >= 0 ? openTabs.get(activeTab).scroll : keep);
    }

    private void setZoom(float z) {
        float nz = Math.round(Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, z)) * 10f) / 10f;
        if (nz == editorZoom) return;
        editorZoom = nz;
        updateEditorGeometry();
        if (activeTab >= 0) {
            EditorTab t = openTabs.get(activeTab);
            t.followLine = -1;
            ensureVisible(t);
        }
        flash(Lang.get("gui.kve.code.zoom", Math.round(editorZoom * 100)), false);
    }

    private void refreshMetrics(EditorTab t) {
        int h = t.lines.hashCode();
        if (t.metricsValid && t.metricsHash == h) return;
        int maxW = 0, maxIc = 0;
        for (String l : t.lines) {
            maxW = Math.max(maxW, font.width(l));
            maxIc = Math.max(maxIc, lineIcons(l).size());
        }
        t.maxLineW = maxW;
        t.maxIcons = maxIc;
        t.metricsHash = h;
        t.metricsValid = true;
    }

    private float maxHScroll(EditorTab t) {
        refreshMetrics(t);
        return Math.max(0, t.maxLineW + 12 - textW / editorScale());
    }

    private void scrollHorizontally(EditorTab t, float deltaPx) {
        t.hScroll = Math.max(0, Math.min(maxHScroll(t), t.hScroll + deltaPx));
    }

    private static final int H_BAR_H = 4;
    private static final float H_WHEEL_STEP = 90f;
    private boolean hBarDragging;
    private int hBarGrab;

    /** Barra horizontal {trackX, trackW, y, thumbX, thumbW}, o null si todo entra a lo ancho. */
    private int[] hBar(EditorTab t) {
        float maxH = maxHScroll(t);
        if (maxH <= 0) return null;
        float viewW = textW / editorScale();
        int trackX0 = textX, trackW = textW - 10, barY = textY + editorRows * lineH() - H_BAR_H;
        int thumbW = Math.max(16, Math.round(trackW * viewW / (viewW + maxH)));
        int thumbX = trackX0 + Math.round((trackW - thumbW) * (t.hScroll / maxH));
        return new int[]{trackX0, trackW, barY, thumbX, thumbW};
    }

    /** Lleva el pulgar de la barra horizontal a la x del mouse (menos donde se lo agarro). */
    private void dragHBar(EditorTab t, double mx) {
        int[] hb = hBar(t);
        if (hb == null) return;
        float range = Math.max(1, hb[1] - hb[4]);
        float f = (float) ((mx - hBarGrab - hb[0]) / range);
        t.hScroll = Math.max(0, Math.min(1, f)) * maxHScroll(t);
    }

    private void toggleMenu(int idx) {
        if (openMenu == idx) { closeMenu(); return; }
        KvePlateButton b = menuButtons[idx];
        if (b == null) return;
        acOpen = false;
        openMenu = idx;
        menuItems = buildMenu(idx);
        menuX = b.getX();
        menuY = b.getY() + b.getHeight() + 1;
    }

    private void closeMenu() {
        openMenu = -1;
        menuItems = List.of();
    }

    private static MenuItem menuItem(String key, String shortcut, Runnable action) {
        return new MenuItem(Lang.get(key), shortcut, action);
    }

    private static final MenuItem MENU_SEP = new MenuItem(null, null, null);

    private List<MenuItem> buildMenu(int idx) {
        List<MenuItem> m = new ArrayList<>();
        switch (idx) {
            case 0 -> {
                m.add(menuItem("gui.kve.code.menu.new_server", null, () -> promptNewFile(SERVER_SCRIPTS_DIR)));
                m.add(menuItem("gui.kve.code.menu.new_startup", null, () -> promptNewFile(STARTUP_SCRIPTS_DIR)));
                m.add(MENU_SEP);
                m.add(menuItem("gui.kve.files.rename_btn", null, this::promptRenameSelected));
                m.add(menuItem("gui.kve.files.duplicate_btn", null, this::duplicateSelected));
                m.add(menuItem("gui.kve.code.pin_toggle", null, this::togglePinSelected));
                m.add(menuItem("gui.kve.files.delete_btn", null, this::deleteSelected));
                m.add(MENU_SEP);
                m.add(menuItem("gui.kve.code.save", "Ctrl+S", this::onSaveActive));
                m.add(menuItem("gui.kve.code.discard", null, this::onDiscardActive));
                m.add(menuItem("gui.kve.code.menu.close_tab", "Ctrl+W", () -> { if (activeTab >= 0) closeTab(activeTab); }));
                m.add(MENU_SEP);
                m.add(menuItem("gui.kve.code.menu.reload_list", null, this::onReload));
                m.add(menuItem("gui.kve.files.open_folder", null, () -> openFolder(SERVER_SCRIPTS_DIR)));
                m.add(menuItem("gui.kve.files.copy_path_btn", null, this::copySelectedPath));
                m.add(menuItem("gui.kve.files.jump_tab", null, this::jumpToOwnerTab));
            }
            case 1 -> {
                m.add(menuItem("gui.kve.code.menu.undo", "Ctrl+Z", () -> undoActive(false)));
                m.add(menuItem("gui.kve.code.menu.redo", "Ctrl+Y", () -> undoActive(true)));
                m.add(MENU_SEP);
                m.add(menuItem("gui.kve.code.menu.find", "Ctrl+F", this::openFindPrompt));
                m.add(menuItem("gui.kve.code.menu.goto_line", "Ctrl+G", this::openGoToLinePrompt));
                m.add(MENU_SEP);
                m.add(menuItem("gui.kve.code.menu.insert_item", null, this::openItemInsert));
                m.add(menuItem("gui.kve.code.menu.snippets", "Ctrl+J", this::openSnippets));
                m.add(menuItem("gui.kve.code.format.btn", "Ctrl+Shift+F", this::formatActive));
                m.add(menuItem("gui.kve.code.menu.toggle_comment", "Ctrl+/", () -> withUndo(this::toggleLineComment)));
                m.add(MENU_SEP);
                m.add(menuItem("gui.kve.code.menu.zoom_in", "Ctrl +", () -> setZoom(editorZoom + 0.1f)));
                m.add(menuItem("gui.kve.code.menu.zoom_out", "Ctrl -", () -> setZoom(editorZoom - 0.1f)));
                m.add(menuItem("gui.kve.code.menu.zoom_reset", "Ctrl+0", () -> setZoom(1f)));
            }
            default -> {
                m.add(menuItem("gui.kve.code.validate", null, this::runDiagnostics));
                m.add(menuItem("gui.kve.code.menu.prev_problem", null, () -> jumpToDiagnostic(false)));
                m.add(menuItem("gui.kve.code.menu.next_problem", null, () -> jumpToDiagnostic(true)));
                m.add(menuItem("gui.kve.code.menu.copy_problems", null, this::copyAllProblems));
                m.add(MENU_SEP);
                m.add(menuItem("gui.kve.code.reload_client", null, () -> onReloadKube(null)));
                if (ClientPermissionState.canApply()) {
                    m.add(menuItem("gui.kve.code.reload_server", null, () -> onReloadKube("server_scripts")));
                    m.add(menuItem("gui.kve.code.reload_startup", null, () -> onReloadKube("startup_scripts")));
                }
                m.add(MENU_SEP);
                m.add(menuItem("gui.kve.code.console_toggle", null, this::toggleConsole));
            }
        }
        return m;
    }

    /** Alto de una fila de menu: crece con la escala de letra de Ajustes. */
    private static int menuRowH() { return KveGuiStyle.rowHeight(MENU_ROW_H); }

    private int menuWidth() {
        int w = 80;
        for (MenuItem it : menuItems) {
            if (it.label() == null) continue;
            int iw = KveGuiStyle.textWidth(it.label()) + 12 + (it.shortcut() != null ? KveGuiStyle.textWidth(it.shortcut()) + 16 : 0);
            w = Math.max(w, iw);
        }
        return w;
    }

    private int menuHeight() {
        int h = 4;
        for (MenuItem it : menuItems) h += it.label() == null ? MENU_SEP_H : menuRowH();
        return h;
    }

    private void clampMenu() {
        var win = Minecraft.getInstance().getWindow();
        int w = menuWidth(), h = menuHeight();
        menuX = Math.max(2, Math.min(menuX, win.getGuiScaledWidth() - 2 - w));
        menuY = Math.max(2, Math.min(menuY, win.getGuiScaledHeight() - 2 - h));
    }

    private int menuHit(double mx, double my) {
        int w = menuWidth();
        if (mx < menuX || mx >= menuX + w) return -1;
        int ry = menuY + 2;
        for (int i = 0; i < menuItems.size(); i++) {
            MenuItem it = menuItems.get(i);
            int rh = it.label() == null ? MENU_SEP_H : menuRowH();
            if (my >= ry && my < ry + rh) return it.label() == null ? -1 : i;
            ry += rh;
        }
        return -1;
    }

    private void renderMenu(GuiGraphics g, int mx, int my) {
        if (openMenu < 0) return;
        clampMenu();
        int w = menuWidth(), h = menuHeight();
        int hit = menuHit(mx, my);
        g.pose().pushPose();
        g.pose().translate(0, 0, 450);
        g.fill(menuX - 1, menuY - 1, menuX + w + 1, menuY + h + 1, KveGuiStyle.BORDER_OUTER);
        g.fill(menuX, menuY, menuX + w, menuY + h, KveGuiStyle.BG_PANEL);
        int ry = menuY + 2;
        for (int i = 0; i < menuItems.size(); i++) {
            MenuItem it = menuItems.get(i);
            if (it.label() == null) {
                g.fill(menuX + 4, ry + 2, menuX + w - 4, ry + 3, KveGuiStyle.BORDER_FIELD);
                ry += MENU_SEP_H;
                continue;
            }
            int rh = menuRowH();
            if (i == hit) g.fill(menuX + 1, ry, menuX + w - 1, ry + rh, KveGuiStyle.BORDER_FIELD);
            KveGuiStyle.drawText(g, it.label(), menuX + 6, ry + 2, KveGuiStyle.TEXT_PRIMARY);
            if (it.shortcut() != null) {
                KveGuiStyle.drawText(g, it.shortcut(), menuX + w - 6 - KveGuiStyle.textWidth(it.shortcut()), ry + 2, KveGuiStyle.TEXT_MUTED);
            }
            ry += rh;
        }
        g.pose().popPose();
    }

    private String hoverCopyText = null;

    private void copyToClipboard(String text) {
        minecraft.keyboardHandler.setClipboard(text);
        flash(Lang.get("gui.kve.code.copied"), false);
    }

    private void copyAllProblems() {
        if (activeTab < 0 || diagRanges.isEmpty()) { flash(Lang.get("gui.kve.code.menu.no_problems"), false); return; }
        StringBuilder sb = new StringBuilder(openTabs.get(activeTab).path.toString().replace('\\', '/')).append('\n');
        new java.util.TreeMap<>(diagRanges).forEach((line, issues) -> {
            for (DiagIssue d : issues) sb.append(Lang.get("gui.kve.code.problem_line", line + 1, d.message())).append('\n');
        });
        copyToClipboard(sb.toString().stripTrailing());
    }

    // ------------------------------------------------------------
    // Explorador (scan calcado de FilesViewerScreen, ver su Javadoc
    // original para el porque de leer disco fresco cada vez)
    // ------------------------------------------------------------

    private void scan() {
        entries.clear();
        addDirListing("scripts", SERVER_SCRIPTS_DIR);
        addDirListing("scripts", STARTUP_SCRIPTS_DIR);
        addManifest("tags", TagExporter.getManifestPaths());
        addManifest("loot", LootTableExporter.getManifestPaths());
        addStructures();
        entries.sort(Comparator.comparing((FileEntry e) -> pinned.contains(e.path()) ? 0 : 1)
                .thenComparing(FileEntry::category)
                .thenComparing(e -> e.path().getFileName().toString()));
    }

    private void addDirListing(String category, Path dir) {
        if (!Files.isDirectory(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile).forEach(p -> entries.add(statEntry(category, p)));
        } catch (IOException ignored) {
        }
    }

    private void addManifest(String category, List<String> paths) {
        for (String s : paths) entries.add(statEntry(category, Paths.get(s)));
    }

    private void addStructures() {
        if (!Files.isDirectory(DATA_DIR)) return;
        try (DirectoryStream<Path> namespaces = Files.newDirectoryStream(DATA_DIR)) {
            for (Path ns : namespaces) {
                Path structDir = ns.resolve("structures");
                if (!Files.isDirectory(structDir)) continue;
                try (var walk = Files.walk(structDir)) {
                    walk.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().endsWith(".nbt"))
                            .forEach(p -> entries.add(statEntry("structures", p)));
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    private String displayLabel(Path p, String category) {
        if (!"scripts".equals(category)) return p.getFileName().toString();
        return displayLabel(p);
    }

    private String displayLabel(FileEntry e) { return displayLabel(e.path(), e.category()); }

    private String displayLabel(Path p) {
        for (Path root : new Path[]{SERVER_SCRIPTS_DIR, STARTUP_SCRIPTS_DIR}) {
            if (p.startsWith(root)) return root.relativize(p).toString().replace('\\', '/');
        }
        return p.getFileName().toString();
    }

    private FileEntry statEntry(String category, Path p) {
        try {
            return new FileEntry(category, p, Files.size(p), Files.getLastModifiedTime(p).toMillis(), false);
        } catch (IOException ex) {
            return new FileEntry(category, p, 0, 0, true);
        }
    }

    private void refreshFiltered() {
        filtered.clear();
        for (FileEntry e : entries) {
            if (searchFilter != null && !searchFilter.isBlank()
                    && !SearchFilter.matches(displayLabel(e), e.path().getFileName().toString(), searchFilter)) {
                continue;
            }
            filtered.add(e);
        }
        rebuildDisplayRows();
        if (fileScroll != null) fileScroll.setTotals(displayRows.size(), fileRowsVisible());
        listNav.clamp(filtered.size());
    }

    private static String categoryLabel(String category) {
        return switch (category) {
            case "scripts" -> Lang.get("gui.kve.files.category.scripts");
            case "tags" -> Lang.get("gui.kve.files.category.tags");
            case "loot" -> Lang.get("gui.kve.files.category.loot");
            case "structures" -> Lang.get("gui.kve.files.category.structures");
            default -> category.toUpperCase(java.util.Locale.ROOT);
        };
    }

    private static int categoryDotColor(String category) {
        return switch (category) {
            // colores fijos: con los acentos del tema (cobre, plata...) las categorias quedaban del mismo tono
            case "scripts" -> 0xFF4FC1FF;
            case "tags" -> 0xFFE5C07B;
            case "loot" -> 0xFFC678DD;
            case "structures" -> 0xFF98C379;
            default -> 0xFF9CA3AF;
        };
    }

    private String groupKeyOf(FileEntry e) {
        return pinned.contains(e.path()) ? "\u0000pinned" : e.category();
    }

    private void rebuildDisplayRows() {
        displayRows.clear();
        int i = 0;
        while (i < filtered.size()) {
            String key = groupKeyOf(filtered.get(i));
            int j = i;
            while (j < filtered.size() && groupKeyOf(filtered.get(j)).equals(key)) j++;
            String label = key.equals("\u0000pinned")
                    ? Lang.get("gui.kve.files.section_pinned")
                    : categoryLabel(key);
            displayRows.add(new DisplayRow(true, label + " (" + (j - i) + ")", -1));
            for (int k = i; k < j; k++) displayRows.add(new DisplayRow(false, null, k));
            i = j;
        }
    }

    private FileEntry selectedEntry() {
        if (selectedIdx < 0 || selectedIdx >= filtered.size()) return null;
        return filtered.get(selectedIdx);
    }

    /**
     * Scrollea fileScroll para que la fila de {@code listNav.cursor()}
     * quede visible. No se le puede pasar el ScrollBar directo a
     * {@link ListKeyNav#keyPressed()} (su matematica interna asume
     * fila == indice, ver Javadoc de esa clase) porque esta lista
     * intercala encabezados de seccion no seleccionables entre las filas
     * reales (ver {@link DisplayRow}/{@link #rebuildDisplayRows()}) - hay
     * que traducir el indice de {@code filtered} a su fila real dentro de
     * {@code displayRows} antes de decidir si hace falta scrollear.
     */
    private void ensureFileListCursorVisible() {
        if (!listNav.active() || fileScroll == null) return;
        int cursor = listNav.cursor();
        int displayIdx = -1;
        for (int i = 0; i < displayRows.size(); i++) {
            if (displayRows.get(i).filteredIdx() == cursor) { displayIdx = i; break; }
        }
        if (displayIdx < 0) return;
        int rowsVisible = fileRowsVisible();
        int scroll = fileScroll.getScroll();
        if (displayIdx < scroll) fileScroll.setScroll(displayIdx);
        else if (displayIdx >= scroll + rowsVisible) fileScroll.setScroll(displayIdx - rowsVisible + 1);
    }

    private void togglePinSelected() {
        FileEntry e = selectedEntry();
        if (e == null) { flash(Lang.get("gui.kve.files.jump_no_selection"), true); return; }
        if (!pinned.remove(e.path())) pinned.add(e.path());
        KveFilesUiState.savePinned(pinned);
        refresh();
    }

    private void openInTab(FileEntry e) {
        com.kve.client.code.ScriptIdChecker.clearCache();
        if (!e.editable()) {
            flash(Lang.get("gui.kve.code.binary"), true);
            return;
        }
        searchHighlightLine = -1;
        for (int i = 0; i < openTabs.size(); i++) {
            if (openTabs.get(i).path.equals(e.path())) {
                activeTab = i;
                diagDirty = true;
                ensureEditorScrollForTab();
                ensureActiveTabVisible();
                return;
            }
        }
        String text;
        try {
            text = Files.readString(e.path(), StandardCharsets.UTF_8);
        } catch (MalformedInputException ex) {
            flash(Lang.get("gui.kve.code.binary"), true);
            return;
        } catch (IOException ex) {
            flash(Lang.get("gui.kve.code.read_fail"), true);
            return;
        }
        EditorTab opened = new EditorTab(e.path(), text);
        openTabs.add(opened);
        activeTab = openTabs.size() - 1;
        ensureEditorScrollForTab();
        ensureActiveTabVisible();
        diagDirty = true;
        diagRanges.clear();
        diagCount = 0;
        offerDraftRecovery(opened, text);
    }

    private void offerDraftRecovery(EditorTab tab, String diskText) {
        var draft = DRAFTS.load(tab.path);
        if (draft.isEmpty()) return;
        if (draft.get().text().equals(diskText)) { DRAFTS.clear(tab.path); return; }
        long mins = Math.max(0, (System.currentTimeMillis() - draft.get().savedAtMillis()) / 60000L);
        String text = draft.get().text();
        minecraft.setScreen(new KveConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        tab.lines.clear();
                        for (String ln : text.split("\n", -1)) tab.lines.add(ln);
                        if (tab.lines.isEmpty()) tab.lines.add("");
                        tab.cursorLine = 0;
                        tab.cursorCol = 0;
                        diagDirty = true;
                        flash(Lang.get("gui.kve.code.draft.restored"), false);
                    } else {
                        DRAFTS.clear(tab.path);
                    }
                    minecraft.setScreen(this);
                },
                Component.literal(Lang.get("gui.kve.code.draft.title")),
                Component.literal(Lang.get("gui.kve.code.draft.msg", tab.path.getFileName().toString(), mins))));
    }

    private void autosaveDrafts() {
        long now = System.currentTimeMillis();
        if (now - lastAutosaveAt < AUTOSAVE_INTERVAL_MS) return;
        lastAutosaveAt = now;
        for (EditorTab t : openTabs) {
            String text = t.currentText();
            if (!text.equals(t.lastSaved)) {
                int h = text.hashCode();
                Integer prev = autosavedHash.get(t.path);
                if (prev == null || prev != h) {
                    DRAFTS.save(t.path, text);
                    autosavedHash.put(t.path, h);
                }
            } else if (autosavedHash.remove(t.path) != null) {
                DRAFTS.clear(t.path);
            }
        }
    }

    private String activeScriptFolder() {
        if (activeTab < 0) return null;
        String p = openTabs.get(activeTab).path.toAbsolutePath().normalize().toString().replace('\\', '/');
        if (p.contains("/server_scripts/")) return "server";
        if (p.contains("/startup_scripts/")) return "startup";
        if (p.contains("/client_scripts/")) return "client";
        return null;
    }

    private void formatActive() { withUndo(this::formatActiveInner); }

    private void formatActiveInner() {
        if (activeTab < 0) return;
        EditorTab t = openTabs.get(activeTab);
        List<String> formatted = com.kve.common.code.JsFormatter.reindent(t.lines);
        if (formatted.equals(t.lines)) { flash(Lang.get("gui.kve.code.format.unchanged"), false); return; }
        String cursorLineBefore = t.lines.get(t.cursorLine);
        int leadBefore = cursorLineBefore.length() - cursorLineBefore.stripLeading().length();
        t.lines.clear();
        t.lines.addAll(formatted);
        String cursorLineAfter = t.lines.get(t.cursorLine);
        int leadAfter = cursorLineAfter.length() - cursorLineAfter.stripLeading().length();
        t.cursorCol = Math.max(0, Math.min(cursorLineAfter.length(), t.cursorCol + (leadAfter - leadBefore)));
        diagDirty = true;
        searchHighlightLine = -1;
        flash(Lang.get("gui.kve.code.format.done"), false);
    }

    private void openSnippets() {
        if (activeTab < 0) { flash(Lang.get("gui.kve.code.no_file"), true); return; }
        minecraft.setScreen(new CodeSnippetScreen(this, activeScriptFolder(), snippet -> {
            minecraft.setScreen(this);
            insertSnippet(snippet);
        }));
    }

    private void insertSnippet(com.kve.common.code.KubeJsSnippets.Snippet snippet) { withUndo(() -> insertSnippetInner(snippet)); }

    private void insertSnippetInner(com.kve.common.code.KubeJsSnippets.Snippet snippet) {
        if (activeTab < 0) return;
        EditorTab t = openTabs.get(activeTab);
        String line = t.lines.get(t.cursorLine);
        String indent = line.substring(0, line.length() - line.stripLeading().length());
        List<String> ins = com.kve.common.code.KubeJsSnippets.indented(snippet, indent);
        String before = line.substring(0, Math.min(t.cursorCol, line.length()));
        String after = line.substring(Math.min(t.cursorCol, line.length()));
        t.lines.set(t.cursorLine, before + ins.get(0));
        for (int i = 1; i < ins.size(); i++) t.lines.add(t.cursorLine + i, ins.get(i));
        int last = t.cursorLine + ins.size() - 1;
        t.cursorCol = t.lines.get(last).length();
        t.lines.set(last, t.lines.get(last) + after);
        t.cursorLine = last;
        diagDirty = true;
        ensureVisible(t);
    }

    /**
 * (boton "Item"): elegir un item del
     * picker e insertar su id en el cursor - entre comillas si el cursor no
     * esta ya dentro de un string, reemplazando la seleccion si la hay.
 * Evita tipear ids de memoria (y los errores "Item with ID... does not
     * exist" que el validador despues marca).
     */
    private void openItemInsert() {
        if (activeTab < 0) { flash(Lang.get("gui.kve.code.no_file"), true); return; }
        minecraft.setScreen(new ItemPickerScreen(this, stack -> {
            if (stack == null || stack.isEmpty()) return;
            net.minecraft.resources.ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (key == null) return;
            insertAtCursor(key.toString(), true);
        }));
    }

    private void insertAtCursor(String text, boolean quoteIfNeeded) {
        if (activeTab < 0) return;
        withUndo(() -> {
            EditorTab t = openTabs.get(activeTab);
            t.deleteSelection();
            String line = t.lines.get(t.cursorLine);
            String ins = quoteIfNeeded && !CodeEditOps.insideString(line, t.cursorCol) ? "'" + text + "'" : text;
            t.moveCursor(CodeEditOps.insert(t.lines, t.cursor(), ins));
            ensureVisible(t);
        });
    }

    private void promptConsoleCommand() {
        minecraft.setScreen(new KveTextInputScreen(this,
                Component.literal(Lang.get("gui.kve.code.console_cmd_title")),
                "kubejs errors", Lang.get("gui.kve.code.console_cmd_hint"),
                raw -> {
                    minecraft.setScreen(this);
                    String cmd = raw.startsWith("/") ? raw.substring(1) : raw;
                    if (cmd.isBlank() || minecraft.player == null) return;
                    com.kve.client.events.ConsoleCommandCapture.startCapture();
                    consoleLines.add(new ConsoleLine("> /" + cmd, KveGuiStyle.ACCENT_GOLD));
                    scrollConsoleToEnd();
                    minecraft.player.connection.sendCommand(cmd);
                }));
    }

    private void drainConsoleCapture() {
        List<String> captured = com.kve.client.events.ConsoleCommandCapture.drain();
        if (captured.isEmpty()) return;
        for (String line : captured) consoleLines.add(new ConsoleLine(line, KveGuiStyle.TEXT_PRIMARY));
        scrollConsoleToEnd();
    }

    private void scrollConsoleToEnd() {
        if (consoleScroll == null) return;
        consoleScroll.setTotals(consoleLines.size(), visibleRows);
        consoleScroll.setScroll(Math.max(0, consoleLines.size() - visibleRows));
    }

    private final CodeFindReplaceScreen.Target findTarget = new CodeFindReplaceScreen.Target() {
        @Override
        public boolean find(java.util.regex.Pattern p, boolean forward) {
            if (activeTab < 0) return false;
            EditorTab t = openTabs.get(activeTab);
            int fromCol = forward ? (searchHighlightLine == t.cursorLine && searchHighlightStart == t.cursorCol ? t.cursorCol : t.cursorCol - 1)
                                  : t.cursorCol;
            var m = forward ? com.kve.common.code.TextSearch.findNext(t.lines, t.cursorLine, fromCol, p)
                            : com.kve.common.code.TextSearch.findPrevious(t.lines, t.cursorLine, fromCol, p);
            if (m == null) { searchHighlightLine = -1; return false; }
            t.clearSelection();
            t.cursorLine = m.line();
            t.cursorCol = m.start();
            searchHighlightLine = m.line();
            searchHighlightStart = m.start();
            searchHighlightEnd = m.end();
            ensureVisible(t);
            return true;
        }

        @Override
        public int count(java.util.regex.Pattern p) {
            return activeTab < 0 ? 0 : com.kve.common.code.TextSearch.count(openTabs.get(activeTab).lines, p);
        }

        @Override
        public boolean replaceCurrent(java.util.regex.Pattern p, String replacement, com.kve.common.code.TextSearch.Options opts) {
            boolean[] r = {false};
            withUndo(() -> r[0] = replaceCurrentInner(p, replacement, opts));
            return r[0];
        }

        private boolean replaceCurrentInner(java.util.regex.Pattern p, String replacement, com.kve.common.code.TextSearch.Options opts) {
            if (activeTab < 0 || searchHighlightLine < 0) return false;
            EditorTab t = openTabs.get(activeTab);
            if (searchHighlightLine >= t.lines.size()) return false;
            String line = t.lines.get(searchHighlightLine);
            var m = p.matcher(line);
            if (!m.find(Math.min(searchHighlightStart, line.length())) || m.start() != searchHighlightStart || m.end() == m.start()) return false;
            var match = new com.kve.common.code.TextSearch.Match(searchHighlightLine, m.start(), m.end());
            int end = com.kve.common.code.TextSearch.replaceOne(t.lines, match, p, replacement, opts);
            t.cursorLine = match.line();
            t.cursorCol = Math.max(0, end - 1);
            searchHighlightLine = -1;
            diagDirty = true;
            find(p, true);
            return true;
        }

        @Override
        public int replaceAll(java.util.regex.Pattern p, String replacement, com.kve.common.code.TextSearch.Options opts) {
            int[] r = {0};
            withUndo(() -> r[0] = replaceAllInner(p, replacement, opts));
            return r[0];
        }

        private int replaceAllInner(java.util.regex.Pattern p, String replacement, com.kve.common.code.TextSearch.Options opts) {
            if (activeTab < 0) return 0;
            EditorTab t = openTabs.get(activeTab);
            int n = com.kve.common.code.TextSearch.replaceAll(t.lines, p, replacement, opts);
            if (n > 0) {
                t.cursorLine = Math.min(t.cursorLine, t.lines.size() - 1);
                t.cursorCol = Math.min(t.cursorCol, t.lines.get(t.cursorLine).length());
                diagDirty = true;
            }
            searchHighlightLine = -1;
            return n;
        }
    };

    private void ensureEditorScrollForTab() {
        if (editorScroll == null || activeTab < 0) return;
        EditorTab t = openTabs.get(activeTab);
        editorScroll.setTotals(t.lines.size(), editorRows);
        editorScroll.setScroll(t.scroll);
    }

    private void closeTab(int idx) {
        if (idx < 0 || idx >= openTabs.size()) return;
        EditorTab t = openTabs.get(idx);
        if (t.dirty()) { flash(Lang.get("gui.kve.code.unsaved_block"), true); return; }
        openTabs.remove(idx);
        if (activeTab == idx) { activeTab = openTabs.isEmpty() ? -1 : Math.max(0, idx - 1); diagDirty = true; }
        else if (activeTab > idx) activeTab--;
        searchHighlightLine = -1;
        ensureEditorScrollForTab();
        ensureActiveTabVisible();
    }

    private void onSaveActive() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied"), true); return; }
        if (activeTab < 0) { flash(Lang.get("gui.kve.code.no_file"), true); return; }
        EditorTab t = openTabs.get(activeTab);
        String newText = t.currentText();
        if (!ScriptWriteGuard.looksWellFormed(newText)) {
            flash(Lang.get("gui.kve.code.malformed"), true);
            return;
        }
        String oldText = t.lastSaved;
        Path target = t.path;
        // "Editar el contenido generado antes de Apply": onApply
        // reads through changeHolder[0].effectiveNewScript() (not the
        // captured `newText`) so a manual edit made from DiffPreviewScreen's
        // "Editar" button before Apply is what actually gets written -
        // changeHolder exists only because the Runnable needs to reference
        // the very PendingChange it ends up inside (self-referential, can't
        // capture `change` before it's built).
        PendingChange[] changeHolder = new PendingChange[1];
        PendingChange change = PendingChange.of(
                PendingDomain.RAW_SCRIPT, target.toString(), PendingChange.Action.EDIT, false,
                () -> {
                    String finalText = changeHolder[0].effectiveNewScript();
                    if (!ScriptWriteGuard.writeIfValid(target, finalText, "CodeEditorScreen:" + target.getFileName())) {
                        throw new IllegalStateException("Write rejected by ScriptWriteGuard for " + target.getFileName());
                    }
                    EditorTab live = findTab(target);
                    if (live != null) { live.lastSaved = finalText; live.queuedText = null; }
                },
                () -> {
                    EditorTab live = findTab(target);
                    if (live != null) {
                        live.lines.clear();
                        for (String ln : oldText.split("\n", -1)) live.lines.add(ln);
                        if (live.lines.isEmpty()) live.lines.add("");
                        live.lastSaved = oldText;
                        live.queuedText = null;
                        live.cursorLine = Math.min(live.cursorLine, live.lines.size() - 1);
                        if (activeTab >= 0 && openTabs.get(activeTab) == live) {
                            diagDirty = true;
                            diagRanges.clear();
                            diagCount = 0;
                        }
                    }
                }
        ).withDiff(() -> oldText, () -> newText).withFile(target.toString());
        changeHolder[0] = change;
        PendingChangeManager.queue(change);
        t.queuedText = newText;
        flash(Lang.get("gui.kve.code.queued"), false);
    }

    private static PendingChange pendingSaveFor(Path p) {
        String id = p.toString();
        for (PendingChange c : PendingChangeManager.entriesForDomain(PendingDomain.RAW_SCRIPT)) {
            if (c.entryId().equals(id)) return c;
        }
        return null;
    }

    private void onDiscardActive() {
        if (activeTab < 0) return;
        EditorTab t = openTabs.get(activeTab);
        PendingChange queued = pendingSaveFor(t.path);
        if (queued != null) PendingChangeManager.discard(queued);
        t.queuedText = null;
        t.lines.clear();
        for (String ln : t.lastSaved.split("\n", -1)) t.lines.add(ln);
        if (t.lines.isEmpty()) t.lines.add("");
        t.cursorLine = Math.min(t.cursorLine, t.lines.size() - 1);
        t.cursorCol = Math.min(t.cursorCol, t.lines.get(t.cursorLine).length());
        diagDirty = true;
        diagRanges.clear();
        diagCount = 0;
        flash(Lang.get("gui.kve.code.discarded"), false);
    }

    private EditorTab findTab(Path p) {
        for (EditorTab t : openTabs) if (t.path.equals(p)) return t;
        return null;
    }

    private void onReload() {
        selectedIdx = -1;
        listNav.reset();
        refresh();
        flash(Lang.get("gui.kve.btn.reload"), false);
    }

    /**
 * "Boton de reload contextual": mismo patron ya establecido
     * por {@code KveStudioShell}'s auto-reload (ver su Javadoc de settings
     * AUTO_RELOAD) - {@code sendCommand()} best-effort, el servidor lo
     * rechaza en silencio si el jugador no tiene permiso vanilla (op level
     * 2+), asi que no hace falta un packet/gate propio de KVE aca.
     * <p>
     * {@code scope == null} -&gt; client_scripts, que no pasa por el
     * servidor en absoluto ({@code /kubejs reload client_scripts} solo
     * afecta al cliente que lo tipea, sin importar sus permisos - por eso
     * el boton correspondiente en el toolbar no esta gateado a {@link
     * ClientPermissionState#canApply()} como los otros dos).
     */
    private void toggleConsole() {
        consoleMode = !consoleMode;
        if (consoleMode) refreshConsole();
    }

    private void selectConsoleScope(int idx) {
        consoleScopeIdx = idx;
        refreshConsole();
    }

    private void refreshConsole() {
        consoleLines.clear();
        String scope = LOG_SCOPES[consoleScopeIdx];
        Path file = resolveLogFile(scope);
        if (file == null) {
            consoleLines.add(new ConsoleLine(Lang.get("gui.kve.code.console_missing", LOG_DIR.resolve(scope + ".log")), KveGuiStyle.TEXT_MUTED));
        } else {
            for (String raw : tailLines(file, LOG_TAIL_BYTES, LOG_MAX_LINES)) {
                consoleLines.add(colorizeLogLine(raw, scope));
            }
            if (consoleLines.isEmpty()) {
                consoleLines.add(new ConsoleLine(Lang.get("gui.kve.code.console_empty"), KveGuiStyle.TEXT_MUTED));
            }
        }
        if (consoleScroll != null) {
            consoleScroll.setTotals(consoleLines.size(), visibleRows);
            consoleScroll.setScroll(Math.max(0, consoleLines.size() - visibleRows));
        }
    }

    private static Path resolveLogFile(String scope) {
        Path log = LOG_DIR.resolve(scope + ".log");
        if (Files.isRegularFile(log)) return log;
        Path txt = LOG_DIR.resolve(scope + ".txt");
        if (Files.isRegularFile(txt)) return txt;
        return null;
    }

    private static List<String> tailLines(Path file, int maxBytes, int maxLines) {
        List<String> out = new ArrayList<>();
        try (var raf = new java.io.RandomAccessFile(file.toFile(), "r")) {
            long len = raf.length();
            long start = Math.max(0, len - maxBytes);
            raf.seek(start);
            byte[] buf = new byte[(int) (len - start)];
            raf.readFully(buf);
            String text = new String(buf, StandardCharsets.UTF_8);
            String[] split = text.split("\n", -1);
            int from = start > 0 ? 1 : 0;
            for (int i = from; i < split.length; i++) {
                String ln = split[i].stripTrailing();
                if (!ln.isEmpty()) out.add(ln);
            }
        } catch (IOException ignored) {
            return out;
        }
        if (out.size() > maxLines) out = out.subList(out.size() - maxLines, out.size());
        return out;
    }

    /**
     * Coloreado + atribucion: lineas con ERROR/SEVERE en rojo, WARN en
     * amarillo, resto en gris tenue (mismo criterio de "no es un parser
     * real" que el tokenizer del editor). Si la linea menciona
     * "algo.js:NN" y ese archivo tiene un line-map guardado por
     * FaultTracker (los exportores de KVE lo escriben al generar el
     * script), se le agrega el id de entry al final - la unica forma hoy
 * de saber "que mob/receta/etc rompio" sin abrir el.js a mano.
     */
    private static ConsoleLine colorizeLogLine(String line, String scope) {
        String upper = line.toUpperCase(java.util.Locale.ROOT);
        int color;
        if (upper.contains("ERROR") || upper.contains("SEVERE") || upper.contains("EXCEPTION")) {
            color = KveGuiStyle.TEXT_ERROR;
        } else if (upper.contains("WARN")) {
            color = 0xFFE0C060;
        } else {
            color = KveGuiStyle.TEXT_MUTED;
        }

        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([\\w.\\-]+\\.js):(\\d+)").matcher(line);
        if (m.find()) {
            String fileName = m.group(1);
            int lineNo;
            try {
                lineNo = Integer.parseInt(m.group(2));
            } catch (NumberFormatException ex) {
                lineNo = -1;
            }
            if (lineNo > 0) {
                var entryId = com.kve.common.kubejs.FaultTracker.findEntryForLine(fileName, lineNo);
                if (entryId.isPresent()) {
                    line = line + "  [entry: " + entryId.get() + "]";
                }
            }
        }
        String plainHint = color == KveGuiStyle.TEXT_ERROR ? plainLanguageForLogLine(line) : null;
        return new ConsoleLine(line, color, plainHint);
    }

    private static final Pattern LOG_TYPE_ERROR_UNDEFINED =
            Pattern.compile("Cannot read propert(?:y|ies) ['\"]?([\\w$]*)['\"]? of (undefined|null)");
    private static final Pattern LOG_NOT_A_FUNCTION =
            Pattern.compile("([\\w$.]+) is not a function");
    private static final Pattern LOG_REFERENCE_ERROR =
            Pattern.compile("ReferenceError: ?([\\w$]+) is not defined");
    private static final Pattern LOG_FILE_MISSING =
            Pattern.compile("(?:NoSuchFileException|FileNotFoundException): ?(.*)$");
    private static final Pattern LOG_NO_SUCH_FUNCTION =
            Pattern.compile("Cannot find function ([\\w$]+) in object");
    private static final Pattern LOG_NO_SUCH_JAVA_MEMBER =
            Pattern.compile("Java class [\\w.$]+ has no public instance field or method named ['\"]([\\w$]+)['\"]");
    private static final Pattern LOG_CONST_REDECLARATION =
            Pattern.compile("redeclaration of const");

    private static String plainLanguageForLogLine(String line) {
        Matcher m;
        if ((m = LOG_TYPE_ERROR_UNDEFINED.matcher(line)).find()) {
            String prop = m.group(1);
            return prop.isEmpty() ? Lang.get("gui.kve.code.plain.type_error_undefined_generic")
                    : Lang.get("gui.kve.code.plain.type_error_undefined", prop);
        }
        if ((m = LOG_REFERENCE_ERROR.matcher(line)).find()) {
            return Lang.get("gui.kve.code.plain.reference_error", m.group(1));
        }
        if ((m = LOG_NO_SUCH_FUNCTION.matcher(line)).find()) {
            return Lang.get("gui.kve.code.plain.no_such_function", m.group(1));
        }
        if ((m = LOG_NO_SUCH_JAVA_MEMBER.matcher(line)).find()) {
            return Lang.get("gui.kve.code.plain.no_such_java_member", m.group(1));
        }
        if ((m = LOG_NOT_A_FUNCTION.matcher(line)).find()) {
            return Lang.get("gui.kve.code.plain.not_a_function", m.group(1));
        }
        if (LOG_CONST_REDECLARATION.matcher(line).find()) {
            return Lang.get("gui.kve.code.plain.const_redeclaration");
        }
        if (line.contains("SyntaxError")) {
            return Lang.get("gui.kve.code.plain.syntax_error");
        }
        if (line.contains("NullPointerException")) {
            return Lang.get("gui.kve.code.plain.null_pointer");
        }
        if (line.contains("ClassCastException")) {
            return Lang.get("gui.kve.code.plain.class_cast");
        }
        if (line.contains("StackOverflowError")) {
            return Lang.get("gui.kve.code.plain.stack_overflow");
        }
        if ((m = LOG_FILE_MISSING.matcher(line)).find()) {
            return Lang.get("gui.kve.code.plain.file_missing", m.group(1).trim());
        }
        return Lang.get("gui.kve.code.plain.generic_error");
    }

    private void onReloadKube(String scope) {
        if (minecraft == null || minecraft.player == null) return;
        String command = switch (scope == null ? "client_scripts" : scope) {
            case "server_scripts" -> "reload";
            case "startup_scripts" -> "kubejs reload startup_scripts";
            default -> "kubejs reload client_scripts";
        };
        try {
            minecraft.player.connection.sendCommand(command);
            flash(Lang.get("gui.kve.code.reload_sent"), false);
        } catch (Throwable ex) {
            flash(Lang.get("gui.kve.code.reload_fail"), true);
        }
    }

    private void openFolder(Path dir) {
        try {
            Files.createDirectories(dir);
            Util.getPlatform().openFile(dir.toAbsolutePath().toFile());
        } catch (Exception ex) {
            flash(Lang.get("gui.kve.files.open_folder_fail"), true);
        }
    }

    private void copySelectedPath() {
        FileEntry e = selectedEntry();
        if (e == null) { flash(Lang.get("gui.kve.files.none"), true); return; }
        minecraft.keyboardHandler.setClipboard(e.path().toString());
        flash(Lang.get("gui.kve.files.copied"), false);
    }

    private void deleteSelected() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied"), true); return; }
        FileEntry e = selectedEntry();
        if (e == null) { flash(Lang.get("gui.kve.files.delete_no_selection"), true); return; }
        String fileName = e.path().getFileName().toString();
        minecraft.setScreen(new KveConfirmScreen(
                confirmed -> {
                    minecraft.setScreen(this);
                    if (confirmed) performDelete(e);
                },
                Component.literal(Lang.get("gui.kve.files.delete_confirm_title")),
                Component.literal(Lang.get("gui.kve.files.delete_confirm_msg", fileName))));
    }

    private void performDelete(FileEntry e) {
        PendingChange queued = pendingSaveFor(e.path());
        if (queued != null) PendingChangeManager.discard(queued);
        DRAFTS.clear(e.path());
        autosavedHash.remove(e.path());
        try {
            boolean removed = Files.deleteIfExists(e.path());
            if (!removed) { flash(Lang.get("gui.kve.files.delete_fail"), true); return; }
        } catch (IOException ex) {
            flash(Lang.get("gui.kve.files.delete_fail"), true);
            return;
        }
        pinned.remove(e.path());
        KveFilesUiState.savePinned(pinned);
        for (int i = 0; i < openTabs.size(); i++) {
            if (openTabs.get(i).path.equals(e.path())) {
                openTabs.remove(i);
                if (activeTab == i) { activeTab = openTabs.isEmpty() ? -1 : Math.max(0, i - 1); diagDirty = true; }
                else if (activeTab > i) activeTab--;
                break;
            }
        }
        selectedIdx = -1;
        listNav.reset();
        flash(Lang.get("gui.kve.files.delete_ok"), false);
        refresh();
    }

    private static final Pattern SAFE_RELATIVE_PATH = Pattern.compile("[A-Za-z0-9_\\-./]+");

    private static boolean isSafeRelativeName(String name) {
        return !name.isEmpty() && !name.contains("..") && SAFE_RELATIVE_PATH.matcher(name).matches();
    }

    private FileEntry findEntryByPath(Path p) {
        for (FileEntry e : entries) if (e.path().equals(p)) return e;
        return null;
    }

    private void promptNewFile(Path root) {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied"), true); return; }
        minecraft.setScreen(new KveTextInputScreen(this,
                Component.literal(Lang.get("gui.kve.files.new_title")),
                "", Lang.get("gui.kve.files.new_hint"),
                name -> { minecraft.setScreen(this); doCreateFile(root, name); }));
    }

    private void doCreateFile(Path root, String rawName) {
        String name = rawName.trim().replace('\\', '/');
        while (name.startsWith("/")) name = name.substring(1);
        if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".js")) name = name + ".js";
        if (!isSafeRelativeName(name)) { flash(Lang.get("gui.kve.files.name_invalid"), true); return; }
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root)) { flash(Lang.get("gui.kve.files.name_invalid"), true); return; }
        if (Files.exists(target)) { flash(Lang.get("gui.kve.files.name_exists"), true); return; }
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, "", StandardCharsets.UTF_8);
        } catch (IOException ex) {
            flash(Lang.get("gui.kve.files.create_fail"), true);
            return;
        }
        flash(Lang.get("gui.kve.files.create_ok"), false);
        refresh();
        FileEntry created = findEntryByPath(target);
        if (created != null) openInTab(created);
    }

    private void promptRenameSelected() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied"), true); return; }
        FileEntry e = selectedEntry();
        if (e == null) { flash(Lang.get("gui.kve.files.rename_no_selection"), true); return; }
        if (!"scripts".equals(e.category())) { flash(Lang.get("gui.kve.files.rename_unsupported"), true); return; }
        String current = displayLabel(e);
        minecraft.setScreen(new KveTextInputScreen(this,
                Component.literal(Lang.get("gui.kve.files.rename_title")),
                current, Lang.get("gui.kve.files.new_hint"),
                newName -> { minecraft.setScreen(this); doRename(e, newName); }));
    }

    private void doRename(FileEntry e, String rawName) {
        EditorTab openTab = findTab(e.path());
        if (openTab != null && openTab.dirty()) { flash(Lang.get("gui.kve.code.unsaved_block"), true); return; }
        if (pendingSaveFor(e.path()) != null) { flash(Lang.get("gui.kve.files.rename_pending_block"), true); return; }
        String name = rawName.trim().replace('\\', '/');
        while (name.startsWith("/")) name = name.substring(1);
        if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".js")) name = name + ".js";
        if (!isSafeRelativeName(name)) { flash(Lang.get("gui.kve.files.name_invalid"), true); return; }
        Path root = e.path().startsWith(STARTUP_SCRIPTS_DIR) ? STARTUP_SCRIPTS_DIR : SERVER_SCRIPTS_DIR;
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root)) { flash(Lang.get("gui.kve.files.name_invalid"), true); return; }
        if (target.equals(e.path())) return;
        if (Files.exists(target)) { flash(Lang.get("gui.kve.files.name_exists"), true); return; }
        try {
            Files.createDirectories(target.getParent());
            Files.move(e.path(), target);
        } catch (IOException ex) {
            flash(Lang.get("gui.kve.files.rename_fail"), true);
            return;
        }
        if (pinned.remove(e.path())) {
            pinned.add(target);
            KveFilesUiState.savePinned(pinned);
        }
        if (openTab != null) openTab.path = target;
        selectedIdx = -1;
        listNav.reset();
        flash(Lang.get("gui.kve.files.rename_ok"), false);
        refresh();
    }

    private void duplicateSelected() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied"), true); return; }
        FileEntry e = selectedEntry();
        if (e == null) { flash(Lang.get("gui.kve.files.duplicate_no_selection"), true); return; }
        if (!"scripts".equals(e.category())) { flash(Lang.get("gui.kve.files.duplicate_unsupported"), true); return; }
        Path root = e.path().startsWith(STARTUP_SCRIPTS_DIR) ? STARTUP_SCRIPTS_DIR : SERVER_SCRIPTS_DIR;
        String rel = displayLabel(e);
        String base = rel.toLowerCase(java.util.Locale.ROOT).endsWith(".js") ? rel.substring(0, rel.length() - 3) : rel;
        Path target = root.resolve(base + "_copy.js");
        int n = 2;
        while (Files.exists(target)) { target = root.resolve(base + "_copy" + n + ".js"); n++; }
        EditorTab openTab = findTab(e.path());
        try {
            Files.createDirectories(target.getParent());
            if (openTab != null) {
                Files.writeString(target, openTab.currentText(), StandardCharsets.UTF_8);
            } else {
                Files.copy(e.path(), target);
            }
        } catch (IOException ex) {
            flash(Lang.get("gui.kve.files.duplicate_fail"), true);
            return;
        }
        flash(Lang.get(openTab != null && openTab.dirty()
                ? "gui.kve.files.duplicate_ok_unsaved" : "gui.kve.files.duplicate_ok"), false);
        refresh();
        FileEntry created = findEntryByPath(target);
        if (created != null) openInTab(created);
    }

    private void jumpToOwnerTab() {
        FileEntry e = selectedEntry();
        if (e == null) { flash(Lang.get("gui.kve.files.jump_no_selection"), true); return; }
        String ownerKey = ownerTabKey(e);
        if (ownerKey == null) { flash(Lang.get("gui.kve.files.jump_no_domain"), true); return; }
        if (hosted && returnScreen instanceof KveStudioShell shell && shell.jumpToTab(ownerKey)) return;
        flash(Lang.get("gui.kve.files.jump_unavailable"), true);
    }

    private String ownerTabKey(FileEntry e) {
        return switch (e.category()) {
            case "tags" -> "gui.kve.studio.tab.tags";
            case "loot" -> "gui.kve.studio.tab.loot";
            case "structures" -> "gui.kve.studio.tab.structures";
            case "scripts" -> scriptOwnerTabKey(displayLabel(e));
            default -> null;
        };
    }

    private String scriptOwnerTabKey(String fileName) {
        String n = fileName.toLowerCase(java.util.Locale.ROOT);
        if (n.contains("event")) return "gui.kve.studio.tab.events";
        if (n.contains("mob")) return "gui.kve.studio.tab.mobs";
        if (n.contains("item") || n.contains("block")) return "gui.kve.studio.tab.properties";
        if (n.contains("recipe") || n.contains("craft")) return "gui.kve.studio.tab.recipes";
        if (n.contains("worldgen") || n.contains("terrain") || n.contains("ore")) return "gui.kve.studio.tab.worldgen";
        if (n.contains("tag")) return "gui.kve.studio.tab.tags";
        if (n.contains("loot")) return "gui.kve.studio.tab.loot";
        if (n.contains("trade")) return "gui.kve.studio.tab.trades";
        return null;
    }

    private void flash(String msg, boolean error) {
        flashMsg = msg;
        flashAt = System.currentTimeMillis();
        flashError = error;
    }

    // ------------------------------------------------------------
    // "Diagnosticos basicos" (mismo recorte de alcance que el
    // autocompletado: SIN analisis semantico completo). Dos chequeos,
    // ambos baratos y reusando cosas que KVE ya tiene resueltas en otro
    // lado:
    //  1. ids con pinta de namespace:path que no existen en ningun
    //     registry conocido (item/block/fluid/entity) - mismo criterio
    //     que SemanticValidator.validateItemId, pero corrido sobre texto
    //     libre en vez de sobre un campo tipado.
    //  2. eventos de startup (StartupEvents.*) usados fuera de
    //     startup_scripts, o eventos de runtime (ServerEvents./
    //     PlayerEvents./EntityEvents.*) usados dentro de startup_scripts -
    //     dato que ya sabemos gratis por la carpeta del archivo abierto.
    // No corre en cada frame/tecla (evitaria justo el patron "recalcular

    private static final String[] STARTUP_ONLY = {"StartupEvents."};
    private static final String[] RUNTIME_ONLY = {"ServerEvents.", "PlayerEvents.", "EntityEvents."};

    private void computeDiagnostics() {
        if (activeTab < 0) return;
        EditorTab t = openTabs.get(activeTab);
        diagRanges.clear();
        diagCount = 0;
        boolean isStartup = t.path.toString().contains("startup_scripts");
        boolean[] inBlockComment = {false};
        boolean[] inTemplate = {false};
        List<String> sanitized = new ArrayList<>(t.lines.size());
        Set<String> declaredHere = com.kve.common.search.DeclaredIds.scanScript(String.join("\n", t.lines));
        for (int i = 0; i < t.lines.size(); i++) {
            String line = stripCommentsForDiagnostics(t.lines.get(i), inBlockComment, inTemplate);
            sanitized.add(line);
            List<DiagIssue> issues = new ArrayList<>();

            Matcher m = QUOTED_ID.matcher(line);
            while (m.find()) {
                String id = m.group(1);
                if (com.kve.common.search.DeclaredIds.isDeclared(id, declaredHere)) continue;
                if (id.contains("loot_tables/") || id.contains("advancements/") || id.contains("recipes/")) continue;
                var check = com.kve.client.code.ScriptIdChecker.check(id);
                if (check.kind() == com.kve.client.code.ScriptIdChecker.Kind.TYPO) {
                    issues.add(new DiagIssue(m.start(1), m.end(1),
                            Lang.get("gui.kve.code.diag.typo_id", id, check.suggestion())));
                    diagCount++;
                } else if (check.kind() == com.kve.client.code.ScriptIdChecker.Kind.MOD_MISSING) {
                    issues.add(new DiagIssue(m.start(1), m.end(1),
                            Lang.get("gui.kve.code.diag.mod_missing", id, check.suggestion())));
                    diagCount++;
                }
            }

            if (isStartup) {
                for (String kw : RUNTIME_ONLY) {
                    int idx = line.indexOf(kw);
                    if (idx >= 0) {
                        issues.add(new DiagIssue(idx, idx + kw.length() - 1,
                                Lang.get("gui.kve.code.diag.runtime_in_startup", kw)));
                        diagCount++;
                    }
                }
            } else {
                for (String kw : STARTUP_ONLY) {
                    int idx = line.indexOf(kw);
                    if (idx >= 0) {
                        issues.add(new DiagIssue(idx, idx + kw.length() - 1,
                                Lang.get("gui.kve.code.diag.startup_in_server", kw)));
                        diagCount++;
                    }
                }
            }

            Matcher condM = IF_WHILE_CONDITION.matcher(line);
            while (condM.find()) {
                String cond = condM.group(1);
                int badEq = findBareAssignment(cond);
                if (badEq >= 0) {
                    int col = condM.start(1) + badEq;
                    issues.add(new DiagIssue(col, col + 1, Lang.get("gui.kve.code.diag.assignment_in_condition")));
                    diagCount++;
                }
            }

            if (!issues.isEmpty()) diagRanges.put(i, issues);
        }
        int beforeBrackets = diagCount;
        checkBracketBalance(sanitized);
        if (diagCount == beforeBrackets) checkJsSyntax(t);
        diagDirty = false;
    }

    private void checkJsSyntax(EditorTab t) {
        if (!t.path.toString().endsWith(".js")) return;
        if (!com.kve.integration.kubejs.KveIntegrations.isKubeJSLoaded()) return;
        String source = String.join("\n", t.lines);
        String file = t.path.getFileName().toString();
        var syntax = com.kve.integration.kubejs.RhinoSyntaxCheck.check(source, file);
        for (var p : syntax) {
            addDiagAt(t, p.offset(), p.length(), Lang.get("gui.kve.code.diag.syntax_error", p.message()));
        }
        if (!syntax.isEmpty()) return;
        for (var p : com.kve.integration.kubejs.RhinoNameCheck.check(source, file,
                com.kve.integration.kubejs.KubeJsGlobals::isGlobal)) {
            addDiagAt(t, p.offset(), p.length(), Lang.get("gui.kve.code.diag.unknown_name", p.name()));
        }
    }

    private void addDiagAt(EditorTab t, int offset, int length, String msg) {
        int line = 0, lineStart = 0;
        while (line < t.lines.size() - 1 && lineStart + t.lines.get(line).length() < offset) {
            lineStart += t.lines.get(line).length() + 1;
            line++;
        }
        int col = Math.max(0, offset - lineStart);
        addDiag(line, col, col + Math.max(1, length), msg);
    }

    /** Handler del boton "Validar" - envuelve {@link #computeDiagnostics()} con el guard de "no hay archivo" y el toast con el conteo (ver Javadoc de computeDiagnostics() para por que estan separados). */
    private void runDiagnostics() {
        if (activeTab < 0) { flash(Lang.get("gui.kve.code.no_file"), true); return; }
        com.kve.client.code.ScriptIdChecker.clearCache();
        if (com.kve.integration.kubejs.KveIntegrations.isKubeJSLoaded()) com.kve.integration.kubejs.KubeJsGlobals.clearCache();
        computeDiagnostics();
        flash(Lang.get("gui.kve.code.validate_done", diagCount), diagCount > 0);
    }

    private record BracketPos(char ch, int line, int col) {}

    private static boolean endsWithLineContinuation(String line) {
        int trailing = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) trailing++;
        return trailing % 2 == 1;
    }

    private void checkBracketBalance(List<String> sanitizedLines) {
        java.util.ArrayDeque<BracketPos> stack = new java.util.ArrayDeque<>();
        boolean inStr = false;
        char strCh = 0;
        int strStartLine = -1, strStartCol = -1;
        for (int ln = 0; ln < sanitizedLines.size(); ln++) {
            String line = sanitizedLines.get(ln);
            for (int c = 0; c < line.length(); c++) {
                char ch = line.charAt(c);
                if (inStr) {
                    if (ch == '\\' && c + 1 < line.length()) { c++; continue; }
                    if (ch == strCh) inStr = false;
                    continue;
                }
                switch (ch) {
                    case '\'', '"', '`' -> { inStr = true; strCh = ch; strStartLine = ln; strStartCol = c; }
                    case '(', '{', '[' -> stack.push(new BracketPos(ch, ln, c));
                    case ')', '}', ']' -> {
                        char expected = ch == ')' ? '(' : (ch == '}' ? '{' : '[');
                        if (!stack.isEmpty() && stack.peek().ch() == expected) {
                            stack.pop();
                        } else {
                            addDiag(ln, c, c, Lang.get("gui.kve.code.diag.unmatched_closing", String.valueOf(ch)));
                        }
                    }
                    default -> { }
                }
            }
            // Una
            // comilla simple/doble abierta y NUNCA cerrada en la misma
            // linea es (salvo un '\' de continuacion al final de linea,
            // forma real y poco comun de partir un string largo en JS) un
            // error real - a diferencia de un backtick (template literal,
            // que SI puede saltar de linea a proposito), ' y " no pueden.
            // Sin este chequeo, una comilla de cierre olvidada dejaba
            // TODO el resto del archivo marcado como "dentro de un
            // string" para este scan, generando decenas de falsos
            // positivos/negativos en cascada sin relacion con el error
            // real - se corta la cadena ahi mismo, en la linea donde de
            // verdad paso, y se resetea el estado para seguir escaneando
            // el resto del archivo normal en vez de arrastrar el problema.
            if (inStr && strCh != '`' && !endsWithLineContinuation(line)) {
                addDiag(strStartLine, strStartCol, strStartCol,
                        Lang.get("gui.kve.code.diag.unterminated_string", String.valueOf(strCh)));
                inStr = false;
                strCh = 0;
            }
        }
        // Lo que sobrevive en la pila son aperturas que nunca se cerraron -
        // cada una se marca en su propia linea/columna real de apertura.
        for (BracketPos p : stack) {
            addDiag(p.line(), p.col(), p.col(), Lang.get("gui.kve.code.diag.unclosed_bracket", String.valueOf(p.ch())));
        }
    }

    private static int findBareAssignment(String cond) {
        char quote = 0;
        for (int i = 0; i < cond.length(); i++) {
            char c = cond.charAt(i);
            if (quote != 0) {
                if (c == '\\' && i + 1 < cond.length()) { i++; continue; }
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') { quote = c; continue; }
            if (c != '=') continue;
            char prev = i > 0 ? cond.charAt(i - 1) : 0;
            char next = i + 1 < cond.length() ? cond.charAt(i + 1) : 0;
            if (next == '=' || next == '>') continue;
            if (prev == '=' || prev == '!' || prev == '<' || prev == '>'
                    || prev == '+' || prev == '-' || prev == '*' || prev == '/'
                    || prev == '%' || prev == '&' || prev == '|' || prev == '^') continue;
            return i;
        }
        return -1;
    }

    private void addDiag(int line, int start, int end, String message) {
        diagRanges.computeIfAbsent(line, k -> new ArrayList<>()).add(new DiagIssue(start, end, message));
        diagCount++;
    }

    private void jumpToDiagnostic(boolean forward) {
        if (activeTab < 0) { flash(Lang.get("gui.kve.code.no_file"), true); return; }
        if (diagDirty) computeDiagnostics();
        if (diagRanges.isEmpty()) { flash(Lang.get("gui.kve.code.diag_none"), false); return; }
        EditorTab t = openTabs.get(activeTab);
        List<Integer> sortedLines = new ArrayList<>(diagRanges.keySet());
        java.util.Collections.sort(sortedLines);
        int cur = t.cursorLine;
        Integer target = null;
        if (forward) {
            for (int ln : sortedLines) { if (ln > cur) { target = ln; break; } }
            if (target == null) target = sortedLines.get(0);
        } else {
            for (int i = sortedLines.size() - 1; i >= 0; i--) { if (sortedLines.get(i) < cur) { target = sortedLines.get(i); break; } }
            if (target == null) target = sortedLines.get(sortedLines.size() - 1);
        }
        DiagIssue first = diagRanges.get(target).get(0);
        t.clearSelection();
        t.cursorLine = target;
        t.cursorCol = Math.min(first.start(), t.lines.get(target).length());
        ensureVisible(t);
        flash(first.message(), true);
    }

    /**
     * "hover sobre un id conocido en Archivos muestra el render del
     * objeto/entidad/efecto": mismo criterio de resolucion que
     * {@code ScriptIdChecker} (ids de datapack/tags no cuentan, solo elementos
     * reales) pero acotado a los 4 tipos que de verdad tienen un icono
     * para mostrar - item, bloque (via su ItemBlock si tiene uno, igual
     * que {@link KveGuiStyle#renderInlineItemIcons} ya hace para items
     * sueltos), efecto de pocion (Forge registry, ver comentario de
     * {@link SemanticValidator#itemRegistryReady()} sobre por que MobEffect
     * no es un BuiltInRegistries vanilla) y tipo de entidad (via su
     * spawn egg si el mod lo registro, mismo id + "_spawn_egg" que usa la
     * convencion vanilla - si no hay egg no hay icono real que mostrar y
     * se degrada a placeholder de texto).
     */
    private enum ElemKind { ITEM, BLOCK, EFFECT, ENTITY }

    private record KnownElem(ElemKind kind, ResourceLocation id) {}

    private static KnownElem resolveKnownElement(String idStr) {
        ResourceLocation rl = ResourceLocation.tryParse(idStr);
        if (rl == null) return null;
        try {
            if (BuiltInRegistries.ITEM.containsKey(rl) && BuiltInRegistries.ITEM.get(rl) != Items.AIR) {
                return new KnownElem(ElemKind.ITEM, rl);
            }
            if (ForgeRegistries.MOB_EFFECTS.containsKey(rl)) {
                return new KnownElem(ElemKind.EFFECT, rl);
            }
            if (BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
                return new KnownElem(ElemKind.ENTITY, rl);
            }
            if (BuiltInRegistries.BLOCK.containsKey(rl) && BuiltInRegistries.BLOCK.get(rl) != Blocks.AIR) {
                return new KnownElem(ElemKind.BLOCK, rl);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static KveGuiStyle.IconPainter iconFor(KnownElem elem) {
        switch (elem.kind()) {
            case ITEM: {
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(elem.id()));
                return (g, x, y, size) -> {
                    g.pose().pushPose();
                    g.pose().translate(x, y, 0);
                    g.pose().scale(size / 16f, size / 16f, 1f);
                    SafeItemRender.render(g, stack, 0, 0);
                    g.pose().popPose();
                };
            }
            case BLOCK: {
                Block block = BuiltInRegistries.BLOCK.get(elem.id());
                Item asItem = Item.byBlock(block);
                if (asItem == Items.AIR) return null;
                ItemStack stack = new ItemStack(asItem);
                return (g, x, y, size) -> {
                    g.pose().pushPose();
                    g.pose().translate(x, y, 0);
                    g.pose().scale(size / 16f, size / 16f, 1f);
                    SafeItemRender.render(g, stack, 0, 0);
                    g.pose().popPose();
                };
            }
            case EFFECT: {
                var effect = ForgeRegistries.MOB_EFFECTS.getValue(elem.id());
                if (effect == null) return null;
                int color = 0xFF000000 | (effect.getColor() & 0xFFFFFF);
                return (g, x, y, size) -> {
                    g.fill(x, y, x + size, y + size, color);
                    g.fill(x, y, x + size, y + 1, KveGuiStyle.BORDER_OUTER);
                    g.fill(x, y + size - 1, x + size, y + size, KveGuiStyle.BORDER_OUTER);
                    g.fill(x, y, x + 1, y + size, KveGuiStyle.BORDER_OUTER);
                    g.fill(x + size - 1, y, x + size, y + size, KveGuiStyle.BORDER_OUTER);
                };
            }
            case ENTITY: {
                ResourceLocation eggId = new ResourceLocation(elem.id().getNamespace(), elem.id().getPath() + "_spawn_egg");
                if (!BuiltInRegistries.ITEM.containsKey(eggId)) return null;
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(eggId));
                return (g, x, y, size) -> {
                    g.pose().pushPose();
                    g.pose().translate(x, y, 0);
                    g.pose().scale(size / 16f, size / 16f, 1f);
                    SafeItemRender.render(g, stack, 0, 0);
                    g.pose().popPose();
                };
            }
            default:
                return null;
        }
    }

    private String currentWordPrefix(EditorTab t) {
        String line = t.lines.get(t.cursorLine);
        int start = t.cursorCol;
        while (start > 0) {
            char c = line.charAt(start - 1);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == ':' || c == '/' || c == '#') start--;
            else break;
        }
        acPrefixStart = start;
        return line.substring(start, t.cursorCol);
    }

    private void openAutocomplete() {
        if (activeTab < 0 || consoleMode) return;
        EditorTab t = openTabs.get(activeTab);
        String prefix = currentWordPrefix(t);
        acCandidates.clear();
        String pl = prefix.toLowerCase(java.util.Locale.ROOT);

        String event = ScriptCompletions.enclosingEvent(t.lines, t.cursorLine, acPrefixStart);
        List<String> members = ScriptCompletions.eventMembers(event);
        boolean memberMode = !members.isEmpty() && pl.startsWith("event");
        if (memberMode) acCandidates.addAll(ScriptCompletions.filter(members, prefix));

        if (pl.startsWith("#")) {
            addTagCandidates(pl.substring(1), "#");
            finishAutocomplete();
            return;
        }

        for (String s : CORE_SNIPPETS) {
            if (memberMode && s.startsWith("event.")) continue;
            if (acCandidates.contains(s)) continue;
            if (prefix.isEmpty() || s.toLowerCase(java.util.Locale.ROOT).contains(pl)) acCandidates.add(s);
        }
        for (RecipeTypeDescriptor d : RecipeTypeDescriptorRegistry.all()) {
            if (d.recipeSerializer == null || d.recipeSerializer.isBlank()) continue;
            String snippet = "event.recipes." + d.recipeSerializer.replace(':', '.') + "(...)";
            if (prefix.isEmpty() || snippet.toLowerCase(java.util.Locale.ROOT).contains(pl)) acCandidates.add(snippet);
        }
        if (prefix.contains(":") && SemanticValidator.itemRegistryReady()) {
            String idPrefix = pl.replace("'", "").replace("\"", "");
            int found = 0;
            for (var rl : BuiltInRegistries.ITEM.keySet()) {
                if (found >= 25) break;
                String s = rl.toString();
                if (s.contains(idPrefix)) { acCandidates.add(s); found++; }
            }
            found = 0;
            for (var rl : BuiltInRegistries.BLOCK.keySet()) {
                if (found >= 15) break;
                String s = rl.toString();
                if (s.contains(idPrefix)) { acCandidates.add(s); found++; }
            }
            ScriptCompletions.addMatching(acCandidates, idStrings(BuiltInRegistries.ENTITY_TYPE.keySet()), idPrefix, "", 10);
            ScriptCompletions.addMatching(acCandidates, idStrings(BuiltInRegistries.ATTRIBUTE.keySet()), idPrefix, "", 8);
            if (ScriptCompletions.isTagEvent(event)) addTagCandidates(idPrefix, "");
        }
        finishAutocomplete();
    }

    private void finishAutocomplete() {
        if (acCandidates.size() > 40) acCandidates.subList(40, acCandidates.size()).clear();
        acOpen = !acCandidates.isEmpty();
        acSelected = 0;
        acScroll = 0;
    }

    private static List<String> idStrings(java.util.Set<ResourceLocation> keys) {
        List<String> out = new ArrayList<>(keys.size());
        for (ResourceLocation rl : keys) out.add(rl.toString());
        return out;
    }

    private void addTagCandidates(String needle, String prefix) {
        String n = needle.replace("'", "").replace("\"", "");
        try {
            ScriptCompletions.addMatching(acCandidates, tagStrings(BuiltInRegistries.ITEM.getTagNames()), n, prefix, 15);
            ScriptCompletions.addMatching(acCandidates, tagStrings(BuiltInRegistries.BLOCK.getTagNames()), n, prefix, 10);
            ScriptCompletions.addMatching(acCandidates, tagStrings(BuiltInRegistries.ENTITY_TYPE.getTagNames()), n, prefix, 5);
        } catch (RuntimeException ignored) {
        }
    }

    private static <T> List<String> tagStrings(java.util.stream.Stream<net.minecraft.tags.TagKey<T>> tags) {
        return tags.map(k -> k.location().toString()).sorted().toList();
    }

    private void acAccept() {
        if (!acOpen || activeTab < 0 || acSelected < 0 || acSelected >= acCandidates.size()) { acOpen = false; return; }
        EditorTab t = openTabs.get(activeTab);
        String candidate = acCandidates.get(acSelected);
        currentWordPrefix(t);
        String line = t.lines.get(t.cursorLine);
        int start = Math.max(0, Math.min(acPrefixStart, t.cursorCol));
        String newLine = line.substring(0, start) + candidate + line.substring(t.cursorCol);
        t.lines.set(t.cursorLine, newLine);
        t.cursorCol = start + candidate.length();
        acOpen = false;
        diagDirty = true;
    }

    private record Span(int start, int end, int color) {}

    private static boolean isKubeJsApiWord(String word) {
        return word.equals("onEvent") || word.equals("event") || word.endsWith("Events");
    }

    /**
     * Estado "ya estoy dentro de un comentario de bloque {@code /* *&#47;}" al INICIO
     * de cada linea de {@code lines} - hace falta porque {@link #tokenize}
     * colorea linea por linea (sin estado propio entre llamadas) y un
     * comentario de bloque puede cruzar muchas lineas. Se recalcula
     * ENTERO en cada frame (ver {@code renderEditor()}) en vez de cachearse
     * con un dirty-flag propio: mas simple que enganchar ese flag a cada
     * uno de los muchos call sites que mutan {@code EditorTab#lines}
     * (tipear, pegar, deshacer, cargar archivo...), y el costo (un scan
     * lineal de texto plano por frame) es insignificante comparado con el
     * tokenize() que YA corre por cada linea VISIBLE en cada frame - este
     * editor apunta a scripts chicos de KubeJS, no a archivos de miles de
     * lineas.
     */
    private static boolean[] computeBlockCommentStarts(List<String> lines) {
        boolean[] starts = new boolean[lines.size()];
        boolean inComment = false;
        for (int li = 0; li < lines.size(); li++) {
            starts[li] = inComment;
            inComment = lineEndsInBlockComment(lines.get(li), inComment);
        }
        return starts;
    }

    private static boolean lineEndsInBlockComment(String line, boolean inComment) {
        int i = 0, n = line.length();
        while (i < n) {
            if (inComment) {
                int close = line.indexOf("*/", i);
                if (close < 0) return true;
                i = close + 2;
                inComment = false;
                continue;
            }
            char c = line.charAt(i);
            if (c == '/' && i + 1 < n && line.charAt(i + 1) == '/') return false;
            if (c == '/' && i + 1 < n && line.charAt(i + 1) == '*') { inComment = true; i += 2; continue; }
            if (c == '"' || c == '\'' || c == '`') {
                char quote = c; i++;
                while (i < n && line.charAt(i) != quote) { if (line.charAt(i) == '\\' && i + 1 < n) i++; i++; }
                if (i < n) i++;
                continue;
            }
            i++;
        }
        return inComment;
    }

    private static List<Span> templateLiteralSpans(String line, int start, int end) {
        List<Span> out = new ArrayList<>();
        int i = start;
        while (i < end) {
            int interpStart = line.indexOf("${", i);
            if (interpStart < 0 || interpStart >= end - 1) { out.add(new Span(i, end, KveGuiStyle.SYNTAX_STRING)); break; }
            if (interpStart > i) out.add(new Span(i, interpStart, KveGuiStyle.SYNTAX_STRING));
            int depth = 1, j = interpStart + 2;
            while (j < end && depth > 0) {
                char cj = line.charAt(j);
                if (cj == '{') depth++;
                else if (cj == '}') depth--;
                j++;
            }
            out.add(new Span(interpStart, j, KveGuiStyle.SYNTAX_PLAIN));
            i = j;
        }
        return out;
    }

    private List<Span> tokenize(String line, boolean startInBlockComment) {
        List<Span> spans = new ArrayList<>();
        int i = 0, n = line.length();
        boolean inBlockComment = startInBlockComment;
        while (i < n) {
            if (inBlockComment) {
                int close = line.indexOf("*/", i);
                int end = close < 0 ? n : close + 2;
                spans.add(new Span(i, end, KveGuiStyle.SYNTAX_COMMENT));
                i = end;
                inBlockComment = close < 0;
                continue;
            }
            char c = line.charAt(i);
            if (c == '/' && i + 1 < n && line.charAt(i + 1) == '/') {
                spans.add(new Span(i, n, KveGuiStyle.SYNTAX_COMMENT));
                break;
            }
            if (c == '/' && i + 1 < n && line.charAt(i + 1) == '*') {
                inBlockComment = true;
                continue;
            }
            if (c == '"' || c == '\'' || c == '`') {
                int start = i; char quote = c; i++;
                while (i < n && line.charAt(i) != quote) {
                    if (line.charAt(i) == '\\' && i + 1 < n) i++;
                    i++;
                }
                if (i < n) i++;
                if (quote == '`') spans.addAll(templateLiteralSpans(line, start, i));
                else spans.add(new Span(start, i, KveGuiStyle.SYNTAX_STRING));
                continue;
            }
            if (Character.isDigit(c)) {
                int start = i;
                while (i < n && (Character.isDigit(line.charAt(i)) || line.charAt(i) == '.')) i++;
                spans.add(new Span(start, i, KveGuiStyle.SYNTAX_NUMBER));
                continue;
            }
            if (Character.isLetter(c) || c == '_' || c == '$') {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(line.charAt(i)) || line.charAt(i) == '_' || line.charAt(i) == '$')) i++;
                String word = line.substring(start, i);
                boolean kw = false;
                for (String k : KEYWORDS) if (k.equals(word)) { kw = true; break; }
                int col = kw ? KveGuiStyle.SYNTAX_KEYWORD : isKubeJsApiWord(word) ? KveGuiStyle.SYNTAX_API : KveGuiStyle.SYNTAX_PLAIN;
                spans.add(new Span(start, i, col));
                continue;
            }
            spans.add(new Span(i, i + 1, KveGuiStyle.SYNTAX_PUNCT));
            i++;
        }
        return spans;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (hosted && GUI_H != Math.max(hostedH, GUI_H_DEFAULT)) refresh();
        autosaveDrafts();
        if (consoleMode) drainConsoleCapture();
        if (!hosted) renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, guiW, GUI_H, null);

        int midX = L + LIST_W;
        g.fill(midX, T + 15, midX + 1, T + GUI_H - 2, KveGuiStyle.BORDER_FIELD);

        renderFileList(g, mx, my);
        renderTabStrip(g, mx, my);
        // Ver Javadoc de computeDiagnostics(): re-corre el escaneo (sin
        // toast) apenas hay cambios pendientes, para que el subrayado rojo
        // de abajo (renderEditor()) nunca quede desactualizado mientras se
        // sigue editando.
        // El escaneo en vivo
        // espera DIAG_DEBOUNCE_MS sin cambios (cursor/lineas) antes de correr, en
        // vez de correr en el mismo frame de cada tecla. "Validar" sigue inmediato.
        if (!consoleMode && activeTab >= 0 && diagDirty) {
            EditorTab dt = openTabs.get(activeTab);
            long sig = ((long) dt.lines.size() * 31 + dt.cursorLine) * 1_000_003L + dt.cursorCol + (long) activeTab * 7;
            long now = System.currentTimeMillis();
            if (sig != diagEditSig) { diagEditSig = sig; diagEditAt = now; }
            if (now - diagEditAt >= DIAG_DEBOUNCE_MS) computeDiagnostics();
        }
        int wantSlots = 1;
        if (!consoleMode && activeTab >= 0) {
            EditorTab at = openTabs.get(activeTab);
            refreshMetrics(at);
            wantSlots = Math.max(1, Math.min(MAX_GUTTER_ICONS, at.maxIcons));
        }
        if (wantSlots != gutterIconSlots) {
            gutterIconSlots = wantSlots;
            updateEditorGeometry();
        }
        hoverCopyText = null;
        renderEditor(g, mx, my);
        if (acOpen) renderAutocomplete(g);

        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < (flashError ? 4500 : 2500)) {
            float ts = KveGuiStyle.uiTextScale();
            int maxW = Math.max(60, guiW - 24);
            List<String> fl = KveGuiStyle.wrapTextSafe(font, flashMsg, maxW);
            int lh = Math.max(9, Math.round(font.lineHeight * ts) + 1);
            int fw = 0;
            for (String s : fl) fw = Math.max(fw, Math.round(font.width(s) * ts));
            fw = Math.min(maxW, fw) + 8;
            int fy1 = T + GUI_H - 1;
            int fy0 = fy1 - fl.size() * lh - 2;
            g.fill(L + guiW / 2 - fw / 2, fy0, L + guiW / 2 + fw / 2, fy1, 0xE0101418);
            int col = flashError ? KveGuiStyle.TEXT_ERROR : KveGuiStyle.TEXT_OK;
            for (int i = 0; i < fl.size(); i++) {
                KveGuiStyle.drawCenteredTextSafe(g, fl.get(i), L + guiW / 2, fy0 + 2 + i * lh, col);
            }
        }
        super.render(g, mx, my, pt);
        renderMenu(g, mx, my);
    }

    private void renderFileList(GuiGraphics g, int mx, int my) {
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.files.list_title") + " (" + entries.size() + ")", L + 2, T + 18 + contentYExtra, LIST_W - 6, KveGuiStyle.TEXT_LABEL);
        int listY = T + 43 + contentYExtra;
        int rowRight = L + LIST_W - 12;
        int scroll = fileScroll.getScroll();
        int rowsVisible = fileRowsVisible();
        if (filtered.isEmpty()) {
            String emptyMsg = (searchFilter != null && !searchFilter.isBlank())
                    ? Lang.get("gui.kve.files.no_matches", searchFilter)
                    : Lang.get("gui.kve.files.none");
            KveGuiStyle.drawTextSafe(g, emptyMsg, L + 2, listY, LIST_W - 6, KveGuiStyle.TEXT_MUTED);
        }
        for (int i = 0; i < rowsVisible; i++) {
            int idx = i + scroll;
            if (idx >= displayRows.size()) break;
            DisplayRow row = displayRows.get(idx);
            int ry = listY + i * ROW_H;
            if (row.header()) {
                KveGuiStyle.drawTextSafe(g, row.label(), L + 4, ry + 3, rowRight - (L + 4) - 2, KveGuiStyle.TEXT_LABEL);
                continue;
            }
            FileEntry e = filtered.get(row.filteredIdx());
            boolean sel = row.filteredIdx() == selectedIdx;
            boolean hov = mx >= L + 2 && mx < rowRight && my >= ry && my < ry + ROW_H - 1;
            g.fill(L + 2, ry, rowRight, ry + ROW_H - 1,
                    e.missing() ? 0xFF2A1010 : (sel ? KveGuiStyle.BORDER_FIELD : (hov ? KveGuiStyle.BG_FIELD : KveGuiStyle.BG_PANEL)));
            g.fill(L + 4, ry + 5, L + 8, ry + 9, categoryDotColor(e.category()));
            String star = pinned.contains(e.path()) ? "* " : "";
            String name = star + displayLabel(e);
            EditorTab openTab = findTab(e.path());
            String suffix = openTab == null ? "" : Lang.get("gui.kve.files.open_indicator");
            int nameColor = e.missing() ? KveGuiStyle.TEXT_ERROR
                    : (openTab != null && openTab.dirty()) ? KveGuiStyle.ACCENT_GOLD
                    : KveGuiStyle.TEXT_PRIMARY;
            KveGuiStyle.drawTextSafe(g, name + suffix, L + 10, ry + 3, rowRight - (L + 10) - 2, nameColor);
        }
        fileScroll.render(g);
    }

    private void renderTabStrip(GuiGraphics g, int mx, int my) {
        int sx = L + LIST_W + 3;
        int sy = T + 43 + contentYExtra;
        g.fill(sx, sy, L + guiW - 2, sy + TAB_STRIP_H - 1, KveGuiStyle.BG_TITLEBAR);
        int x = sx + 2;
        if (consoleMode) {
            for (int i = 0; i < LOG_SCOPES.length; i++) {
                String label = LOG_SCOPES[i];
                int w = font.width(label) + 10;
                boolean active = i == consoleScopeIdx;
                g.fill(x, sy, x + w, sy + TAB_STRIP_H - 1, active ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
                KveGuiStyle.drawTextSafe(g, label, x + 3, sy + 3, w - 6, active ? KveGuiStyle.TEXT_PRIMARY : KveGuiStyle.TEXT_MUTED);
                x += w + 1;
            }
            String hint = Lang.get("gui.kve.code.console_hint");
            KveGuiStyle.drawTextSafe(g, hint, L + guiW - 3 - font.width(hint), sy + 3, font.width(hint) + 4, KveGuiStyle.TEXT_MUTED);
            return;
        }
        int stripRight = L + guiW - 2;
        // Clip: sin esto, la pestaña que cae parcial/totalmente fuera de
        // [sx, stripRight] se seguia dibujando (y detectando click) mas
        // alla del borde de la ventana - ver comentario del campo
        // tabScrollX arriba.
        g.enableScissor(sx, sy, stripRight, sy + TAB_STRIP_H - 1);
        x -= tabScrollX;
        for (int i = 0; i < openTabs.size(); i++) {
            EditorTab t = openTabs.get(i);
            String label = displayLabel(t.path) + (t.dirty() ? " *" : "");
            int w = font.width(label) + 14;
            boolean active = i == activeTab;
            g.fill(x, sy, x + w, sy + TAB_STRIP_H - 1, active ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
            KveGuiStyle.drawTextSafe(g, label, x + 3, sy + 3, w - 14, active ? KveGuiStyle.TEXT_PRIMARY : KveGuiStyle.TEXT_MUTED);
            KveGuiStyle.drawTextSafe(g, "x", x + w - 9, sy + 3, 8, KveGuiStyle.TEXT_MUTED);
            x += w + 1;
        }
        g.disableScissor();
        if (openTabs.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.code.pick_file"), sx + 2, sy + 3, L + guiW - sx - 4, KveGuiStyle.TEXT_MUTED);
        }
    }

    private int totalTabStripWidth() {
        int w = 0;
        for (EditorTab t : openTabs) {
            w += font.width(displayLabel(t.path) + (t.dirty() ? " *" : "")) + 14 + 1;
        }
        return w;
    }

    private void ensureActiveTabVisible() {
        if (consoleMode || activeTab < 0) return;
        int visibleW = (L + guiW - 2) - (L + LIST_W + 3) - 2;
        int before = 0;
        for (int i = 0; i < activeTab; i++) {
            before += font.width(displayLabel(openTabs.get(i).path) + (openTabs.get(i).dirty() ? " *" : "")) + 14 + 1;
        }
        int w = font.width(displayLabel(openTabs.get(activeTab).path) + (openTabs.get(activeTab).dirty() ? " *" : "")) + 14;
        if (before < tabScrollX) tabScrollX = before;
        else if (before + w > tabScrollX + visibleW) tabScrollX = before + w - visibleW;
        tabScrollX = Math.max(0, Math.min(tabScrollX, Math.max(0, totalTabStripWidth() - visibleW)));
    }

    private void renderEditor(GuiGraphics g, int mx, int my) {
        if (consoleMode) { renderConsole(g, mx, my); return; }
        if (activeTab < 0) return;
        EditorTab t = openTabs.get(activeTab);
        g.fill(textX - 2 - gutterW, textY - 2, textX + textW, T + GUI_H - 4, KveGuiStyle.BG_FIELD);
        g.fill(textX - 2 - gutterW, textY - 2, textX - 3, T + GUI_H - 4, 0x33000000);
        int scroll = editorScroll.getScroll();
        int lh = lineH();
        if (t.cursorLine != t.followLine || t.cursorCol != t.followCol) {
            t.followLine = t.cursorLine;
            t.followCol = t.cursorCol;
            if (t.cursorLine < t.lines.size()) {
                String cl = t.lines.get(t.cursorLine);
                int cxLocal = font.width(cl.substring(0, Math.min(t.cursorCol, cl.length())));
                float viewW = textW / editorScale() - 8;
                if (cxLocal < t.hScroll + 4) t.hScroll = Math.max(0, cxLocal - 24);
                else if (cxLocal > t.hScroll + viewW) t.hScroll = cxLocal - viewW + 24;
            }
        }
        t.hScroll = Math.max(0, Math.min(maxHScroll(t), t.hScroll));
        if (t.anchorLine >= 0) {
            var a = CodeEditOps.clamp(t.lines, t.anchor());
            t.anchorLine = a.line(); t.anchorCol = a.col();
        }
        CodeEditOps.Pos selS = t.hasSelection() ? t.selStart() : null, selE = t.hasSelection() ? t.selEnd() : null;
        CodeEditOps.Pos bracketMatch = null;
        if (t.cursorLine < t.lines.size()) {
            bracketMatch = CodeEditOps.matchingBracket(t.lines, CodeEditOps.clamp(t.lines, t.cursor()));
        }
        CodeEditOps.Pos bracketAt = null;
        if (bracketMatch != null) {
            String cl = t.lines.get(t.cursorLine);
            bracketAt = (t.cursorCol < cl.length() && "()[]{}".indexOf(cl.charAt(t.cursorCol)) >= 0)
                    ? t.cursor() : new CodeEditOps.Pos(t.cursorLine, t.cursorCol - 1);
        }
        String hoverTip = null;
        KnownElem hoverElem = null;
        String hoverElemLabel = null;
        float scale = editorScale();
        boolean[] blockCommentStarts = computeBlockCommentStarts(t.lines);
        g.enableScissor(textX - 2, textY - 1, textX + textW, textY + editorRows * lh);
        for (int row = 0; row < editorRows; row++) {
            int lineIdx = row + scroll;
            if (lineIdx >= t.lines.size()) break;
            String line = t.lines.get(lineIdx);
            int ly = textY + row * lh;
            g.pose().pushPose();
            g.pose().translate(textX - t.hScroll * scale, ly, 0);
            g.pose().scale(scale, scale, 1f);
            if (lineIdx == t.cursorLine && selS == null) {
                g.fill(Math.round(t.hScroll) - 2, 0, Math.round(t.hScroll + textW / scale), LINE_H - 1, 0x10FFFFFF);
            }
            if (selS != null && lineIdx >= selS.line() && lineIdx <= selE.line()) {
                int c0 = lineIdx == selS.line() ? Math.min(selS.col(), line.length()) : 0;
                int c1 = lineIdx == selE.line() ? Math.min(selE.col(), line.length()) : line.length();
                int x0 = font.width(line.substring(0, c0));
                int x1 = font.width(line.substring(0, c1)) + (lineIdx < selE.line() ? 3 : 0);
                if (x1 > x0) g.fill(x0, 0, x1, LINE_H - 1, 0x663A7BD5);
            }
            for (CodeEditOps.Pos bp : new CodeEditOps.Pos[]{bracketAt, bracketMatch}) {
                if (bp == null || bp.line() != lineIdx || bp.col() >= line.length()) continue;
                int bx0 = font.width(line.substring(0, bp.col()));
                int bx1 = bx0 + font.width(String.valueOf(line.charAt(bp.col())));
                g.fill(bx0 - 1, 0, bx1 + 1, LINE_H - 1, 0x4DE8C96A);
            }
            int cx = 0;
            for (Span sp : tokenize(line, blockCommentStarts[lineIdx])) {
                String part = line.substring(sp.start(), Math.min(sp.end(), line.length()));
                g.drawString(font, part, cx, 0, sp.color(), false);
                cx += font.width(part);
            }
            List<DiagIssue> issues = diagRanges.get(lineIdx);
            if (issues != null) {
                for (DiagIssue issue : issues) {
                    int ux0 = font.width(line.substring(0, Math.min(issue.start(), line.length())));
                    int ux1 = font.width(line.substring(0, Math.min(issue.end(), line.length())));
                    int uxEnd = Math.max(ux0 + 1, ux1);
                    g.fill(ux0, LINE_H - 2, uxEnd, LINE_H - 1, KveGuiStyle.TEXT_ERROR);
                    int sx0 = textX + Math.round((ux0 - t.hScroll) * scale);
                    int sx1 = textX + Math.round((uxEnd - t.hScroll) * scale);
                    int sy0 = ly;
                    int sy1 = ly + Math.round(LINE_H * scale);
                    if (mx >= textX - 2 && mx < textX + textW && mx >= sx0 && mx < sx1 && my >= sy0 && my < sy1) hoverTip = issue.message();
                }
            }
            if (searchHighlightLine == lineIdx
                    && searchHighlightEnd <= line.length()
                    && line.regionMatches(true, searchHighlightStart, lastSearchTerm, 0, lastSearchTerm.length())) {
                int hx0 = font.width(line.substring(0, searchHighlightStart));
                int hx1 = font.width(line.substring(0, searchHighlightEnd));
                g.fill(hx0, 0, Math.max(hx0 + 1, hx1), LINE_H - 1, 0x66E8C96A);
            }
            if (!line.trim().startsWith("//")) {
                Matcher idm = QUOTED_ID.matcher(line);
                while (idm.find()) {
                    int ux0 = font.width(line.substring(0, idm.start(1)));
                    int ux1 = font.width(line.substring(0, idm.end(1)));
                    int sx0 = textX + Math.round((ux0 - t.hScroll) * scale);
                    int sx1 = textX + Math.round((ux1 - t.hScroll) * scale);
                    int sy0 = ly;
                    int sy1 = ly + Math.round(LINE_H * scale);
                    if (mx >= textX - 2 && mx < textX + textW && mx >= sx0 && mx < sx1 && my >= sy0 && my < sy1) {
                        KnownElem elem = resolveKnownElement(idm.group(1));
                        if (elem != null) {
                            hoverElem = elem;
                            hoverElemLabel = idm.group(1);
                        }
                    }
                }
            }
            if (lineIdx == t.cursorLine && (System.currentTimeMillis() / 400) % 2 == 0) {
                int cursorX = font.width(line.substring(0, Math.min(t.cursorCol, line.length())));
                g.fill(cursorX, 0, cursorX + 1, LINE_H - 1, KveGuiStyle.TEXT_PRIMARY);
            }
            g.pose().popPose();
        }
        g.disableScissor();
        g.enableScissor(textX - 2 - gutterW, textY - 1, textX - 2, textY + editorRows * lh);
        for (int row = 0; row < editorRows; row++) {
            int lineIdx = row + scroll;
            if (lineIdx >= t.lines.size()) break;
            renderGutterRow(g, t, lineIdx, t.lines.get(lineIdx), textY + row * lh, scale, diagRanges.containsKey(lineIdx));
        }
        g.disableScissor();
        int[] hb = hBar(t);
        if (hb != null) {
            boolean hov = hBarDragging || (mx >= hb[0] && mx < hb[0] + hb[1] && my >= hb[2] - 2 && my < hb[2] + H_BAR_H + 2);
            g.fill(hb[0], hb[2], hb[0] + hb[1], hb[2] + H_BAR_H, 0x30FFFFFF);
            g.fill(hb[3], hb[2], hb[3] + hb[4], hb[2] + H_BAR_H, hov ? 0xE0FFFFFF : 0xA0FFFFFF);
        }
        renderStatusBar(g, t, mx, my);
        editorScroll.render(g);
        if (openMenu >= 0) { hoverTip = null; hoverElem = null; }
        if (hoverTip != null) {
            hoverCopyText = hoverTip;
            var win = Minecraft.getInstance().getWindow();
            KveGuiStyle.drawFramedTooltip(g, hoverTip + "\n" + Lang.get("gui.kve.code.tooltip_copy_hint"), mx, my,
                    win.getGuiScaledWidth(), win.getGuiScaledHeight(), KveGuiStyle.TEXT_ERROR);
        } else if (hoverElem != null) {
            hoverCopyText = hoverElemLabel;
            var win = Minecraft.getInstance().getWindow();
            KveGuiStyle.IconPainter icon = iconFor(hoverElem);
            if (icon != null) {
                KveGuiStyle.drawFramedIconTooltip(g, icon, hoverElemLabel, mx, my,
                        win.getGuiScaledWidth(), win.getGuiScaledHeight(), KveGuiStyle.BORDER_OUTER);
            } else {
                KveGuiStyle.drawFramedTooltip(g, hoverElemLabel, mx, my, win.getGuiScaledWidth(), win.getGuiScaledHeight());
            }
        }
    }

    private final java.util.Map<String, List<KnownElem>> lineIconCache = new java.util.LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, List<KnownElem>> e) { return size() > 4096; }
    };

    private List<KnownElem> lineIcons(String line) {
        if (line.isBlank() || line.stripLeading().startsWith("//")) return List.of();
        return lineIconCache.computeIfAbsent(line, l -> {
            List<KnownElem> out = new ArrayList<>();
            Set<ResourceLocation> seen = new java.util.HashSet<>();
            Matcher m = QUOTED_ID.matcher(l);
            while (m.find()) {
                KnownElem e = resolveKnownElement(m.group(1));
                if (e != null && iconFor(e) != null && seen.add(e.id())) out.add(e);
            }
            return out.isEmpty() ? List.of() : List.copyOf(out);
        });
    }

    /** Numero de linea (resaltado en la del cursor, rojo si tiene un problema) + icono del item/bloque de la linea. */
    private void renderGutterRow(GuiGraphics g, EditorTab t, int lineIdx, String line, int ly, float scale, boolean hasIssue) {
        String num = String.valueOf(lineIdx + 1);
        int color = hasIssue ? KveGuiStyle.TEXT_ERROR : (lineIdx == t.cursorLine ? KveGuiStyle.TEXT_PRIMARY : 0xFF6B7280);
        int numW = Math.round(font.width(num) * scale);
        g.pose().pushPose();
        g.pose().translate(textX - 5 - numW, ly, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, num, 0, 0, color, false);
        g.pose().popPose();
        List<KnownElem> icons = lineIcons(line);
        if (!icons.isEmpty()) {
            int size = gutterIconSize();
            int x = textX - 2 - gutterW + 1;
            int shown = Math.min(icons.size(), gutterIconSlots);
            if (icons.size() > gutterIconSlots) shown--;
            for (int i = 0; i < shown; i++) {
                KveGuiStyle.IconPainter p = iconFor(icons.get(i));
                if (p != null) p.paint(g, x + i * (size + 1), ly, size);
            }
            if (icons.size() > gutterIconSlots) {
                g.drawString(font, "+" + (icons.size() - shown), x + shown * (size + 1), ly + 1, KveGuiStyle.TEXT_MUTED, false);
            }
        }
    }

    private int diagnosticCount() {
        int n = 0;
        for (List<DiagIssue> l : diagRanges.values()) n += l.size();
        return n;
    }

    private int statusBarY() { return T + GUI_H - 4 - LINE_H; }

    private void renderStatusBar(GuiGraphics g, EditorTab t, int mx, int my) {
        int y = statusBarY();
        int x0 = textX - 2 - gutterW, x1 = textX + textW;
        g.fill(x0, y - 1, x1, y + LINE_H, 0x55000000);
        String pos = Lang.get("gui.kve.code.status.pos", t.cursorLine + 1, t.cursorCol + 1);
        if (t.hasSelection()) pos += "  " + Lang.get("gui.kve.code.status.sel", t.selectedText().length());
        if (t.history.canUndo()) pos += "  " + Lang.get("gui.kve.code.status.undo_hint");
        if (Math.abs(editorZoom - 1f) > 0.001f) pos += "  " + Math.round(editorZoom * 100) + "%";
        KveGuiStyle.drawTextSafe(g, pos, x0 + 3, y + 1, (x1 - x0) / 2, 0xFF9CA3AF);
        int errs = diagnosticCount();
        String right = errs == 0 ? Lang.get("gui.kve.code.status.ok") : Lang.get("gui.kve.code.status.errors", errs);
        int rw = Math.round(font.width(right) * KveGuiStyle.uiTextScale());
        int rx = x1 - rw - 12;
        boolean hov = errs > 0 && mx >= rx && mx < x1 - 10 && my >= y && my < y + LINE_H;
        KveGuiStyle.drawTextSafe(g, right, rx, y + 1, rw + 2,
                errs == 0 ? 0xFF7DE0B0 : (hov ? 0xFFFFB4B4 : KveGuiStyle.TEXT_ERROR));
    }

    private void renderAutocomplete(GuiGraphics g) {
        if (activeTab < 0) return;
        EditorTab t = openTabs.get(activeTab);
        int row = t.cursorLine - editorScroll.getScroll();
        int px = textX + Math.round((font.width(t.lines.get(t.cursorLine).substring(0, Math.min(acPrefixStart, t.lines.get(t.cursorLine).length()))) - t.hScroll) * editorScale());
        px = Math.max(textX, px);
        int py = textY + (row + 1) * lineH();
        int visible = Math.min(8, acCandidates.size());
        int w = 0;
        for (int i = 0; i < visible; i++) w = Math.max(w, font.width(acCandidates.get(acScroll + i)));
        w = Math.min(w + 6, L + guiW - px - 4);
        int h = visible * LINE_H + 2;
        if (py + h > T + GUI_H) py = textY + row * lineH() - h;
        g.fill(px, py, px + w, py + h, KveGuiStyle.BG_TITLEBAR);
        g.fill(px, py, px + w, py + 1, KveGuiStyle.BORDER_FIELD);
        for (int i = 0; i < visible; i++) {
            int idx = acScroll + i;
            boolean sel = idx == acSelected;
            int ry = py + 1 + i * LINE_H;
            if (sel) g.fill(px, ry, px + w, ry + LINE_H, KveGuiStyle.BORDER_FIELD);
            KveGuiStyle.drawTextSafe(g, acCandidates.get(idx), px + 2, ry + 1, w - 4, sel ? KveGuiStyle.ACCENT_GOLD : KveGuiStyle.TEXT_PRIMARY);
        }
    }

    private void renderConsole(GuiGraphics g, int mx, int my) {
        g.fill(textX - 2, textY - 2, textX + textW, T + GUI_H - 4, KveGuiStyle.BG_FIELD);
        int scroll = consoleScroll.getScroll();
        int avail = textW - 4;
        String hoverFull = null;
        String hoverPlain = null;
        for (int row = 0; row < visibleRows; row++) {
            int lineIdx = row + scroll;
            if (lineIdx >= consoleLines.size()) break;
            ConsoleLine cl = consoleLines.get(lineIdx);
            int ly = textY + row * LINE_H;
            KveGuiStyle.drawTextSafe(g, cl.text(), textX, ly, avail, cl.color());
            boolean truncated = font.width(cl.text()) > avail;
            boolean hovered = mx >= textX && mx < textX + avail && my >= ly && my < ly + LINE_H;
            if (hovered) {
                hoverCopyText = cl.text();
                if (cl.plainHint() != null) {
                    hoverPlain = cl.plainHint();
                    if (truncated) hoverPlain += "\n\n" + Lang.get("gui.kve.code.plain.full_line_label", cl.text());
                } else if (truncated) {
                    hoverFull = cl.text();
                }
            }
        }
        consoleScroll.render(g);
        if (openMenu >= 0) { hoverPlain = null; hoverFull = null; }
        String copyHint = "\n" + Lang.get("gui.kve.code.tooltip_copy_hint");
        if (hoverPlain != null) {
            var win = Minecraft.getInstance().getWindow();
            KveGuiStyle.drawFramedTooltip(g, hoverPlain + copyHint, mx, my, win.getGuiScaledWidth(), win.getGuiScaledHeight(), KveGuiStyle.TEXT_ERROR);
        } else if (hoverFull != null) {
            var win = Minecraft.getInstance().getWindow();
            KveGuiStyle.drawFramedTooltip(g, hoverFull + copyHint, mx, my, win.getGuiScaledWidth(), win.getGuiScaledHeight());
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        acOpen = false;
        if (openMenu >= 0) {
            int hit = menuHit(mx, my);
            MenuItem it = hit >= 0 ? menuItems.get(hit) : null;
            int prev = openMenu;
            closeMenu();
            if (it != null && it.action() != null) { it.action().run(); return true; }
            for (int i = 0; i < menuButtons.length; i++) {
                if (i != prev && menuButtons[i] != null && menuButtons[i].isMouseOver(mx, my)) { toggleMenu(i); break; }
            }
            return true;
        }
        if (btn == 0 && !consoleMode && activeTab >= 0) {
            int[] hb = hBar(openTabs.get(activeTab));
            if (hb != null && mx >= hb[0] && mx < hb[0] + hb[1] && my >= hb[2] - 2 && my < hb[2] + H_BAR_H + 2) {
                hBarGrab = mx >= hb[3] && mx < hb[3] + hb[4] ? (int) (mx - hb[3]) : hb[4] / 2;
                hBarDragging = true;
                dragHBar(openTabs.get(activeTab), mx);
                return true;
            }
        }
        if (btn == 1) {
            int sb = statusBarY();
            if (!consoleMode && activeTab >= 0 && my >= sb && my < sb + LINE_H && mx >= textX + textW / 2 && mx < textX + textW) {
                copyAllProblems();
                return true;
            }
            if (hoverCopyText != null) {
                copyToClipboard(hoverCopyText);
                return true;
            }
        }
        if (fileScroll.mouseClicked(mx, my, btn)) return true;
        if (editorScroll != null && editorScroll.mouseClicked(mx, my, btn)) return true;
        if (consoleScroll != null && consoleScroll.mouseClicked(mx, my, btn)) return true;

        int listY = T + 43 + contentYExtra;
        int rowRight = L + LIST_W - 12;
        int scroll = fileScroll.getScroll();
        int rowsVisible = fileRowsVisible();
        for (int i = 0; i < rowsVisible; i++) {
            int rowIdx = i + scroll;
            if (rowIdx >= displayRows.size()) break;
            DisplayRow row = displayRows.get(rowIdx);
            if (row.header()) continue;
            int idx = row.filteredIdx();
            int ry = listY + i * ROW_H;
            if (mx >= L + 2 && mx < rowRight && my >= ry && my < ry + ROW_H - 1) {
                long now = System.currentTimeMillis();
                boolean doubleClick = idx == lastClickIdx && now - lastClickAt < DOUBLE_CLICK_MS;
                lastClickIdx = idx;
                lastClickAt = now;
                selectedIdx = idx;
                listNav.setCursor(idx, filtered.size(), 1, rowsVisible, null);
                if (doubleClick) openInTab(filtered.get(idx));
                return true;
            }
        }

        int sx = L + LIST_W + 3, sy = T + 43 + contentYExtra;
        int stripRightClick = L + guiW - 2;
        if (my >= sy && my < sy + TAB_STRIP_H - 1 && mx >= sx && mx < stripRightClick) {
            int x = sx + 2;
            if (consoleMode) {
                for (int i = 0; i < LOG_SCOPES.length; i++) {
                    int w = font.width(LOG_SCOPES[i]) + 10;
                    if (mx >= x && mx < x + w) { selectConsoleScope(i); return true; }
                    x += w + 1;
                }
                return true;
            }
            x -= tabScrollX;
            for (int i = 0; i < openTabs.size(); i++) {
                EditorTab t = openTabs.get(i);
                String label = displayLabel(t.path) + (t.dirty() ? " *" : "");
                int w = font.width(label) + 14;
                if (mx >= x && mx < x + w) {
                    if (mx >= x + w - 11) closeTab(i);
                    else { activeTab = i; diagDirty = true; ensureEditorScrollForTab(); ensureActiveTabVisible(); }
                    return true;
                }
                x += w + 1;
            }
        }

        if (!consoleMode && activeTab >= 0) {
            EditorTab gt = openTabs.get(activeTab);
            int sy0 = statusBarY();
            if (my >= sy0 && my < sy0 + LINE_H && mx >= textX + textW / 2 && mx < textX + textW) {
                if (diagnosticCount() > 0) jumpToDiagnostic(true);
                return true;
            }
            if (mx >= textX - 2 - gutterW && mx < textX - 2 && my >= textY && my < sy0 - 1) {
                setFocused(null);
                int row = Math.min(gt.lines.size() - 1, (int) ((my - textY) / lineH()) + editorScroll.getScroll());
                if (!hasShiftDown() || gt.anchorLine < 0) { gt.anchorLine = row; gt.anchorCol = 0; }
                if (row + 1 < gt.lines.size()) { gt.cursorLine = row + 1; gt.cursorCol = 0; }
                else { gt.cursorLine = row; gt.cursorCol = gt.lines.get(row).length(); }
                gt.history.breakCoalescing();
                return true;
            }
        }
        if (!consoleMode && activeTab >= 0 && mx >= textX - 2 && mx < textX + textW && my >= textY - 2 && my < statusBarY() - 1) {
            setFocused(null);
            EditorTab t = openTabs.get(activeTab);
            if (btn == 2) {
                panning = true;
                panAccY = 0;
                return true;
            }
            int row = (int) ((my - textY) / lineH()) + editorScroll.getScroll();
            if (row >= t.lines.size()) row = t.lines.size() - 1;
            if (row >= 0) {
                if (hasShiftDown()) { if (t.anchorLine < 0) t.setAnchorHere(); }
                else t.clearSelection();
                t.history.breakCoalescing();
                t.cursorLine = row;
                int relX = (int) ((mx - textX) / editorScale() + t.hScroll);
                t.cursorCol = columnForX(t.lines.get(row), relX);
                long now = System.currentTimeMillis();
                boolean dbl = btn == 0 && now - lastEditorClickAt < DOUBLE_CLICK_MS
                        && lastEditorClickLine == t.cursorLine && Math.abs(lastEditorClickCol - t.cursorCol) <= 1;
                lastEditorClickAt = now;
                lastEditorClickLine = t.cursorLine;
                lastEditorClickCol = t.cursorCol;
                if (dbl) {
                    String ln = t.lines.get(row);
                    int a = wordLeft(ln, Math.min(ln.length(), t.cursorCol + 1)), b = wordRight(ln, t.cursorCol);
                    if (b > a) { t.anchorLine = row; t.anchorCol = a; t.cursorCol = b; }
                    editorDragging = false;
                } else if (btn == 0) {
                    editorDragging = true;
                    if (!hasShiftDown()) t.setAnchorHere();
                }
            }
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    private int columnForX(String line, int relX) {
        if (relX <= 0) return 0;
        int w = 0;
        for (int i = 0; i < line.length(); i++) {
            int cw = font.width(String.valueOf(line.charAt(i)));
            if (w + cw / 2 >= relX) return i;
            w += cw;
        }
        return line.length();
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (hBarDragging && !consoleMode && activeTab >= 0) { dragHBar(openTabs.get(activeTab), mx); return true; }
        if (fileScroll.mouseDragged(mx, my, btn, dx, dy)) return true;
        if (editorScroll != null && editorScroll.mouseDragged(mx, my, btn, dx, dy)) return true;
        if (consoleScroll != null && consoleScroll.mouseDragged(mx, my, btn, dx, dy)) return true;
        if (panning && btn == 2 && !consoleMode && activeTab >= 0) {
            EditorTab t = openTabs.get(activeTab);
            scrollHorizontally(t, (float) (-dx / editorScale()));
            panAccY += dy;
            int rows = (int) (panAccY / lineH());
            if (rows != 0) {
                editorScroll.scrollBy(-rows);
                t.scroll = editorScroll.getScroll();
                panAccY -= rows * lineH();
            }
            return true;
        }
        if (editorDragging && !consoleMode && activeTab >= 0 && btn == 0) {
            EditorTab t = openTabs.get(activeTab);
            if (my < textY) { editorScroll.setScroll(Math.max(0, editorScroll.getScroll() - 1)); t.scroll = editorScroll.getScroll(); }
            else if (my > textY + editorRows * lineH()) { editorScroll.setScroll(editorScroll.getScroll() + 1); t.scroll = editorScroll.getScroll(); }
            int row = Math.max(0, Math.min(t.lines.size() - 1, (int) Math.floor((my - textY) / lineH()) + editorScroll.getScroll()));
            t.cursorLine = row;
            t.cursorCol = columnForX(t.lines.get(row), (int) ((mx - textX) / editorScale() + t.hScroll));
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    private boolean editorDragging = false;
    private long lastEditorClickAt = 0;
    private int lastEditorClickLine = -1, lastEditorClickCol = -1;

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        hBarDragging = false;
        fileScroll.mouseReleased(mx, my, btn);
        if (editorScroll != null) editorScroll.mouseReleased(mx, my, btn);
        if (consoleScroll != null) consoleScroll.mouseReleased(mx, my, btn);
        if (btn == 2) panning = false;
        if (editorDragging) {
            editorDragging = false;
            if (activeTab >= 0 && !openTabs.get(activeTab).hasSelection()) openTabs.get(activeTab).clearSelection();
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int sx = L + LIST_W + 3, sy = T + 43 + contentYExtra;
        if (!consoleMode && my >= sy && my < sy + TAB_STRIP_H - 1 && mx >= sx) {
            int visibleW = (L + guiW - 2) - sx - 2;
            int maxScroll = Math.max(0, totalTabStripWidth() - visibleW);
            tabScrollX = Math.max(0, Math.min(maxScroll, tabScrollX - (int) (delta * 20)));
            return true;
        }
        if (openMenu >= 0) return true;
        boolean overEditor = mx >= L + LIST_W;
        if (consoleMode && overEditor) {
            consoleScroll.mouseScrolled(delta);
        } else if (!consoleMode && activeTab >= 0 && overEditor) {
            EditorTab t = openTabs.get(activeTab);
            if (hasControlDown()) {
                setZoom(editorZoom + (delta > 0 ? 0.1f : -0.1f));
            } else if (hasShiftDown()) {
                scrollHorizontally(t, (float) (-delta * H_WHEEL_STEP));
            } else {
                editorScroll.mouseScrolled(delta);
                t.scroll = editorScroll.getScroll();
            }
        } else {
            fileScroll.mouseScrolled(delta);
        }
        return true;
    }

    private EditorTab editingTab() {
        if (consoleMode || activeTab < 0 || getFocused() instanceof EditBox) return null;
        return openTabs.get(activeTab);
    }

    private void withUndo(Runnable edit) {
        if (activeTab < 0) { edit.run(); return; }
        EditorTab t = openTabs.get(activeTab);
        CodeUndoHistory.Snapshot before = t.snapshot();
        edit.run();
        if (activeTab >= 0 && openTabs.get(activeTab) == t && !t.currentText().equals(before.text())) {
            t.history.record(before, false, System.currentTimeMillis());
            t.clearSelection();
            diagDirty = true;
        }
    }

    private void undoActive(boolean redo) {
        EditorTab t = editingTab();
        if (t == null) return;
        CodeUndoHistory.Snapshot s = redo ? t.history.redo(t.snapshot()) : t.history.undo(t.snapshot());
        if (s == null) { flash(Lang.get(redo ? "gui.kve.code.redo_empty" : "gui.kve.code.undo_empty"), false); return; }
        t.restore(s);
        diagDirty = true;
        searchHighlightLine = -1;
        ensureVisible(t);
    }

    @Override
    public boolean charTyped(char ch, int mods) {
        EditorTab t = editingTab();
        if (t == null) return charTypedInner(ch, mods);
        CodeUndoHistory.Snapshot before = t.snapshot();
        boolean wasSelection = t.hasSelection();
        boolean handled = typeWithPairs(t, ch);
        if (!handled) handled = charTypedInner(ch, mods);
        if (!t.currentText().equals(before.text())) {
            t.history.record(before, !wasSelection && ch != ' ', System.currentTimeMillis());
            diagDirty = true;
        }
        return handled;
    }

    private boolean typeWithPairs(EditorTab t, char ch) {
        char closer = CodeEditOps.closerFor(ch);
        if (t.hasSelection()) {
            if (closer != 0) {
                CodeEditOps.Pos s = t.selStart(), e = t.selEnd();
                boolean sameLine = s.line() == e.line();
                CodeEditOps.insert(t.lines, e, String.valueOf(closer));
                CodeEditOps.insert(t.lines, s, String.valueOf(ch));
                t.anchorLine = s.line(); t.anchorCol = s.col() + 1;
                t.cursorLine = e.line(); t.cursorCol = e.col() + (sameLine ? 1 : 0);
                return true;
            }
            t.deleteSelection();
        }
        t.clearSelection();
        String line = t.lines.get(t.cursorLine);
        if (CodeEditOps.shouldOvertype(line, t.cursorCol, ch)
                && (ch == ')' || ch == ']' || ch == '}' || CodeEditOps.betweenEmptyPair(line, t.cursorCol) || CodeEditOps.insideString(line, t.cursorCol))) {
            t.cursorCol++;
            return true;
        }
        if (ch == '}' && line.isBlank() && line.length() >= 2 && t.cursorCol == line.length()) {
            t.lines.set(t.cursorLine, line.substring(2));
            t.cursorCol -= 2;
            return false;
        }
        if (autoPairEnabled && CodeEditOps.shouldAutoPair(line, t.cursorCol, ch)) {
            t.lines.set(t.cursorLine, line.substring(0, t.cursorCol) + ch + closer + line.substring(t.cursorCol));
            t.cursorCol++;
            if (acOpen) openAutocomplete();
            return true;
        }
        return false;
    }

    private boolean autoPairEnabled = true;

    private boolean charTypedInner(char ch, int mods) {
        if (getFocused() == searchBox) return searchBox.charTyped(ch, mods);
        if (consoleMode && ch == '/') { promptConsoleCommand(); return true; }
        if (consoleMode || activeTab < 0) return false;
        EditorTab t = openTabs.get(activeTab);
        String line = t.lines.get(t.cursorLine);
        t.lines.set(t.cursorLine, line.substring(0, t.cursorCol) + ch + line.substring(t.cursorCol));
        t.cursorCol++;
        diagDirty = true;
        if (acOpen) openAutocomplete();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (openMenu >= 0) {
            closeMenu();
            if (key == GLFW.GLFW_KEY_ESCAPE) return true;
        }
        EditorTab t = editingTab();
        if (t == null) return keyPressedInner(key, scan, mods);
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean alt = (mods & GLFW.GLFW_MOD_ALT) != 0;

        if (ctrl && !alt && key == GLFW.GLFW_KEY_Z) { undoActive(shift); return true; }
        if (ctrl && !alt && key == GLFW.GLFW_KEY_Y) { undoActive(true); return true; }
        if (ctrl && !alt && key == GLFW.GLFW_KEY_A) {
            t.anchorLine = 0; t.anchorCol = 0;
            t.cursorLine = t.lines.size() - 1; t.cursorCol = t.lines.get(t.cursorLine).length();
            ensureVisible(t);
            return true;
        }

        boolean movement = !alt && !acOpen && switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN,
                 GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_END, GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_PAGE_DOWN -> true;
            default -> false;
        };
        if (movement) {
            t.history.breakCoalescing();
            if (shift) { if (t.anchorLine < 0) t.setAnchorHere(); }
            else if (t.hasSelection() && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
                t.moveCursor(key == GLFW.GLFW_KEY_LEFT ? t.selStart() : t.selEnd());
                t.clearSelection();
                return true;
            } else t.clearSelection();
            return keyPressedInner(key, scan, mods);
        }

        CodeUndoHistory.Snapshot before = t.snapshot();
        boolean handled = false;
        boolean keepSelection = t.hasSelection() && !acOpen
                && ((!ctrl && key == GLFW.GLFW_KEY_TAB) || (ctrl && key == GLFW.GLFW_KEY_SLASH));
        if (t.hasSelection() && !acOpen) {
            if (ctrl && key == GLFW.GLFW_KEY_C) {
                minecraft.keyboardHandler.setClipboard(t.selectedText());
                return true;
            } else if (ctrl && key == GLFW.GLFW_KEY_X) {
                minecraft.keyboardHandler.setClipboard(t.selectedText());
                t.deleteSelection();
                handled = true;
            } else if (!ctrl && (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE)) {
                t.deleteSelection();
                handled = true;
            } else if (!ctrl && key == GLFW.GLFW_KEY_TAB) {
                int from = t.selStart().line(), to = t.selEnd().line();
                if (shift) {
                    int[] removed = CodeEditOps.dedentLines(t.lines, from, to, 2);
                    t.cursorCol = Math.max(0, t.cursorCol - removed[t.cursorLine - from]);
                    t.anchorCol = Math.max(0, t.anchorCol - removed[t.anchorLine - from]);
                } else {
                    boolean cursorLineEmpty = t.lines.get(t.cursorLine).isEmpty(), anchorLineEmpty = t.lines.get(t.anchorLine).isEmpty();
                    CodeEditOps.indentLines(t.lines, from, to, "  ");
                    if (!cursorLineEmpty) t.cursorCol += 2;
                    if (!anchorLineEmpty) t.anchorCol += 2;
                }
                handled = true;
            } else if (ctrl && key == GLFW.GLFW_KEY_SLASH) {
                toggleCommentRange(t, t.selStart().line(), t.selEnd().line());
                handled = true;
            } else if ((!ctrl && key == GLFW.GLFW_KEY_ENTER) || (ctrl && key == GLFW.GLFW_KEY_V)) {
                t.deleteSelection();
            }
        } else if (!ctrl && key == GLFW.GLFW_KEY_BACKSPACE && !acOpen) {
            String line = t.lines.get(t.cursorLine);
            if (autoPairEnabled && CodeEditOps.betweenEmptyPair(line, t.cursorCol)) {
                t.lines.set(t.cursorLine, line.substring(0, t.cursorCol - 1) + line.substring(t.cursorCol + 1));
                t.cursorCol--;
                handled = true;
            } else if (t.cursorCol >= 2 && line.substring(0, t.cursorCol).isBlank()) {
                int strip = t.cursorCol % 2 == 0 ? 2 : 1;
                t.lines.set(t.cursorLine, line.substring(0, t.cursorCol - strip) + line.substring(t.cursorCol));
                t.cursorCol -= strip;
                handled = true;
            }
        }
        if (!handled) handled = keyPressedInner(key, scan, mods);
        if (activeTab >= 0 && openTabs.get(activeTab) == t && !t.currentText().equals(before.text())) {
            t.history.record(before, false, System.currentTimeMillis());
            if (!keepSelection) t.clearSelection();
            diagDirty = true;
            ensureVisible(t);
        }
        return handled;
    }

    private void toggleCommentRange(EditorTab t, int from, int to) {
        boolean allCommented = true;
        int minIndent = Integer.MAX_VALUE;
        for (int i = from; i <= to; i++) {
            String l = t.lines.get(i);
            if (l.isBlank()) continue;
            int ind = l.length() - l.stripLeading().length();
            minIndent = Math.min(minIndent, ind);
            if (!l.stripLeading().startsWith("//")) allCommented = false;
        }
        if (minIndent == Integer.MAX_VALUE) return;
        for (int i = from; i <= to; i++) {
            String l = t.lines.get(i);
            if (l.isBlank()) continue;
            if (allCommented) {
                int ind = l.length() - l.stripLeading().length();
                String rest = l.substring(ind);
                rest = rest.startsWith("// ") ? rest.substring(3) : rest.substring(2);
                t.lines.set(i, l.substring(0, ind) + rest);
            } else {
                t.lines.set(i, l.substring(0, minIndent) + "// " + l.substring(minIndent));
            }
        }
        t.cursorCol = Math.min(t.cursorCol, t.lines.get(t.cursorLine).length());
    }

    private boolean keyPressedInner(int key, int scan, int mods) {
        boolean listNavEligible = !consoleMode && !acOpen && (getFocused() == searchBox || activeTab < 0);
        if (listNavEligible) {
            int rowsVisibleFiles = fileRowsVisible();
            if (listNav.keyPressed(key, filtered.size(), 1, rowsVisibleFiles, null)) {
                selectedIdx = listNav.cursor();
                ensureFileListCursorVisible();
                return true;
            }
            if (ListKeyNav.isConfirm(key) && listNav.active()) {
                FileEntry e = selectedEntry();
                if (e != null) openInTab(e);
                return true;
            }
        }
        if (getFocused() instanceof EditBox eb && eb.keyPressed(key, scan, mods)) return true;
        if (consoleMode) { if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; } return super.keyPressed(key, scan, mods); }
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;

        if (acOpen) {
            switch (key) {
                case GLFW.GLFW_KEY_UP -> {
                    acSelected = Math.max(0, acSelected - 1);
                    if (acSelected < acScroll) acScroll = acSelected;
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    acSelected = Math.min(acCandidates.size() - 1, acSelected + 1);
                    int visible = Math.min(8, acCandidates.size());
                    if (acSelected >= acScroll + visible) acScroll = acSelected - visible + 1;
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_TAB -> { acAccept(); return true; }
                case GLFW.GLFW_KEY_ESCAPE -> { acOpen = false; return true; }
                case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_END -> acOpen = false;
                default -> { }
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_SPACE) { openAutocomplete(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_S) { onSaveActive(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_W) { closeTab(activeTab); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_C) { copyActiveLine(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_X) { cutActiveLine(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_V) { pasteAtCursor(); return true; }
        // 3 atajos mas,
        // mismo patron self-contained que copy/cut/paste de arriba (cada
        // metodo hace su propio "if (activeTab < 0) return;" en vez de
        // depender del guard de abajo, asi que estos checks pueden vivir
        // antes de el sin duplicar logica). NO se uso Ctrl+D para "duplicar
        // linea" (el atajo mas comun en otros editores) a proposito -
        // KveShellKeybinds.Action.DIFF ya lo reserva por default a nivel
        // shell ("Ver diff", ver su enum) para esta MISMA pantalla (RAW_SCRIPT);
        // interceptarlo aca antes de que la tab devuelva false habria roto
        // ese atajo global en silencio. Duplicar linea vive en Alt+Shift+Arriba/
        // Abajo en cambio (ver el case UP/DOWN mas abajo), junto al Alt+Arriba/
        // Abajo de mover linea que ya se agrega ahi - mismo par que usa VSCode.
        boolean shiftDown = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        if (ctrl && shiftDown && key == GLFW.GLFW_KEY_F) { formatActive(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_H) { openFindPrompt(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_J) { openSnippets(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_F) { openFindPrompt(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_G) { openGoToLinePrompt(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_SLASH) { toggleLineComment(); return true; }
        if (ctrl && (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD || key == GLFW.GLFW_KEY_RIGHT_BRACKET)) { setZoom(editorZoom + 0.1f); return true; }
        if (ctrl && (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT)) { setZoom(editorZoom - 0.1f); return true; }
        if (ctrl && (key == GLFW.GLFW_KEY_0 || key == GLFW.GLFW_KEY_KP_0)) { setZoom(1f); return true; }
        if (activeTab < 0) return super.keyPressed(key, scan, mods);

        EditorTab t = openTabs.get(activeTab);
        String line = t.lines.get(t.cursorLine);
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> {
                if (ctrl) {
                    if (t.cursorCol > 0) t.cursorCol = wordLeft(line, t.cursorCol);
                    else if (t.cursorLine > 0) { t.cursorLine--; t.cursorCol = t.lines.get(t.cursorLine).length(); }
                } else if (t.cursorCol > 0) t.cursorCol--;
                else if (t.cursorLine > 0) { t.cursorLine--; t.cursorCol = t.lines.get(t.cursorLine).length(); }
                ensureVisible(t);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (ctrl) {
                    if (t.cursorCol < line.length()) t.cursorCol = wordRight(line, t.cursorCol);
                    else if (t.cursorLine < t.lines.size() - 1) { t.cursorLine++; t.cursorCol = 0; }
                } else if (t.cursorCol < line.length()) t.cursorCol++;
                else if (t.cursorLine < t.lines.size() - 1) { t.cursorLine++; t.cursorCol = 0; }
                ensureVisible(t);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                // Alt+Arriba
                // reordena la linea actual con la de arriba (mismo atajo que
                // VSCode/IntelliJ) en vez de solo mover el cursor.
                // Alt+Mayus+Arriba duplica la linea actual en su lugar
                // (mismo par de atajos que VSCode - "duplicar linea" vive
                // junto a "mover linea" en vez de en Ctrl+D, que
                // KveShellKeybinds.Action.DIFF ya reserva a nivel shell para
                // esta pantalla - ver el comentario largo mas arriba en
                // keyPressed()). Mayus se chequea ANTES que Alt-solo porque
                // Alt+Mayus+Arriba tambien trae prendido el bit de Alt.
                if ((mods & GLFW.GLFW_MOD_ALT) != 0) {
                    if ((mods & GLFW.GLFW_MOD_SHIFT) != 0) duplicateActiveLine(); else moveActiveLine(-1);
                    return true;
                }
                if (t.cursorLine > 0) { t.cursorLine--; t.cursorCol = Math.min(t.cursorCol, t.lines.get(t.cursorLine).length()); ensureVisible(t); }
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if ((mods & GLFW.GLFW_MOD_ALT) != 0) {
                    if ((mods & GLFW.GLFW_MOD_SHIFT) != 0) duplicateActiveLine(); else moveActiveLine(1);
                    return true;
                }
                if (t.cursorLine < t.lines.size() - 1) { t.cursorLine++; t.cursorCol = Math.min(t.cursorCol, t.lines.get(t.cursorLine).length()); ensureVisible(t); }
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                t.cursorLine = Math.max(0, t.cursorLine - editorRows);
                t.cursorCol = Math.min(t.cursorCol, t.lines.get(t.cursorLine).length());
                ensureVisible(t);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                t.cursorLine = Math.min(t.lines.size() - 1, t.cursorLine + editorRows);
                t.cursorCol = Math.min(t.cursorCol, t.lines.get(t.cursorLine).length());
                ensureVisible(t);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                if (ctrl) {
                    t.cursorLine = 0; t.cursorCol = 0; ensureVisible(t);
                } else {
                    // "Home inteligente" (mismo comportamiento que VSCode/
                    // IntelliJ): el primer toque va al primer caracter NO
                    // blanco de la linea (donde arranca el codigo de
                    // verdad, no la indentacion); si el cursor ya esta ahi,
                    // un segundo toque va a la columna 0 real. Sin esto,
                    // Home en una linea indentada siempre te dejaba antes
                    // de la indentacion, un paso extra para llegar al
                    // codigo cada vez.
                    int firstNonBlank = 0;
                    while (firstNonBlank < line.length() && line.charAt(firstNonBlank) == ' ') firstNonBlank++;
                    t.cursorCol = (t.cursorCol == firstNonBlank) ? 0 : firstNonBlank;
                }
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                if (ctrl) {
                    t.cursorLine = t.lines.size() - 1;
                    t.cursorCol = t.lines.get(t.cursorLine).length();
                    ensureVisible(t);
                } else {
                    t.cursorCol = line.length();
                }
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (t.cursorCol > 0) {
                    t.lines.set(t.cursorLine, line.substring(0, t.cursorCol - 1) + line.substring(t.cursorCol));
                    t.cursorCol--;
                } else if (t.cursorLine > 0) {
                    String prev = t.lines.get(t.cursorLine - 1);
                    int newCol = prev.length();
                    t.lines.set(t.cursorLine - 1, prev + line);
                    t.lines.remove(t.cursorLine);
                    t.cursorLine--;
                    t.cursorCol = newCol;
                }
                diagDirty = true;
                ensureVisible(t);
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (t.cursorCol < line.length()) {
                    t.lines.set(t.cursorLine, line.substring(0, t.cursorCol) + line.substring(t.cursorCol + 1));
                } else if (t.cursorLine < t.lines.size() - 1) {
                    t.lines.set(t.cursorLine, line + t.lines.get(t.cursorLine + 1));
                    t.lines.remove(t.cursorLine + 1);
                }
                diagDirty = true;
                ensureVisible(t);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER -> {
                String tail = line.substring(t.cursorCol);
                t.lines.set(t.cursorLine, line.substring(0, t.cursorCol));
                // Auto-indent -
                // la linea nueva hereda la indentacion (los espacios del
                // inicio) de la linea que se acaba de partir, en vez de
                // arrancar siempre en columna 0. Sin esto, escribir un
                // bloque anidado (function/if/for de varios niveles, comun
                // en los scripts que este editor edita) obligaba a
                // reindentar a mano cada linea nueva con Tab. Solo mira
                // espacios (misma convencion 2-espacios que Tab/Shift+Tab ya
                // usan en este archivo, nunca tabs reales) y se limita a lo
                // que ya habia antes del cursor - no inventa indentacion
                // nueva por "{" al final de la linea, para no arriesgar un
                // falso positivo dentro de un string/comentario.
                String beforeCursor = line.substring(0, t.cursorCol);
                int indentEnd = 0;
                while (indentEnd < beforeCursor.length() && beforeCursor.charAt(indentEnd) == ' ') indentEnd++;
                String indent = beforeCursor.substring(0, indentEnd);
                String trimmedBefore = beforeCursor.stripTrailing();
                char opener = trimmedBefore.isEmpty() ? 0 : trimmedBefore.charAt(trimmedBefore.length() - 1);
                boolean opens = (opener == '{' || opener == '[' || opener == '(')
                        && !CodeEditOps.insideString(trimmedBefore, trimmedBefore.length() - 1);
                String extra = opens ? "  " : "";
                String tailTrim = tail.stripLeading();
                if (opens && !tailTrim.isEmpty() && tailTrim.charAt(0) == CodeEditOps.closerFor(opener)) {
                    t.lines.add(t.cursorLine + 1, indent + extra);
                    t.lines.add(t.cursorLine + 2, indent + tailTrim);
                } else {
                    t.lines.add(t.cursorLine + 1, indent + extra + (opens ? tailTrim : tail));
                }
                t.cursorLine++;
                t.cursorCol = indent.length() + extra.length();
                diagDirty = true;
                ensureVisible(t);
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                boolean dedent = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
                if (dedent) {
                    int strip = 0;
                    while (strip < 2 && strip < line.length() && line.charAt(strip) == ' ') strip++;
                    if (strip > 0) {
                        t.lines.set(t.cursorLine, line.substring(strip));
                        t.cursorCol = Math.max(0, t.cursorCol - strip);
                    }
                } else {
                    t.lines.set(t.cursorLine, line.substring(0, t.cursorCol) + "  " + line.substring(t.cursorCol));
                    t.cursorCol += 2;
                }
                diagDirty = true;
                return true;
            }
            default -> {
                return super.keyPressed(key, scan, mods);
            }
        }
    }

    private void ensureVisible(EditorTab t) {
        if (editorScroll == null) return;
        editorScroll.setTotals(t.lines.size(), editorRows);
        int scroll = editorScroll.getScroll();
        if (t.cursorLine < scroll) editorScroll.setScroll(t.cursorLine);
        else if (t.cursorLine >= scroll + editorRows) editorScroll.setScroll(t.cursorLine - editorRows + 1);
        t.scroll = editorScroll.getScroll();
    }

    private void copyActiveLine() {
        if (activeTab < 0) return;
        EditorTab t = openTabs.get(activeTab);
        minecraft.keyboardHandler.setClipboard(t.lines.get(t.cursorLine));
    }

    private void cutActiveLine() {
        if (activeTab < 0) return;
        EditorTab t = openTabs.get(activeTab);
        minecraft.keyboardHandler.setClipboard(t.lines.get(t.cursorLine));
        if (t.lines.size() > 1) {
            t.lines.remove(t.cursorLine);
            if (t.cursorLine >= t.lines.size()) t.cursorLine = t.lines.size() - 1;
        } else {
            t.lines.set(0, "");
        }
        t.cursorCol = 0;
        diagDirty = true;
        ensureVisible(t);
    }

    private void pasteAtCursor() {
        if (activeTab < 0) return;
        String clip = minecraft.keyboardHandler.getClipboard();
        if (clip == null || clip.isEmpty()) return;
        clip = clip.replace("\r\n", "\n").replace("\r", "\n");
        EditorTab t = openTabs.get(activeTab);
        String line = t.lines.get(t.cursorLine);
        String head = line.substring(0, t.cursorCol);
        String tail = line.substring(t.cursorCol);
        String[] pasted = clip.split("\n", -1);
        if (pasted.length == 1) {
            t.lines.set(t.cursorLine, head + pasted[0] + tail);
            t.cursorCol = head.length() + pasted[0].length();
        } else {
            t.lines.set(t.cursorLine, head + pasted[0]);
            for (int i = 1; i < pasted.length - 1; i++) {
                t.lines.add(t.cursorLine + i, pasted[i]);
            }
            int lastIdx = t.cursorLine + pasted.length - 1;
            t.lines.add(lastIdx, pasted[pasted.length - 1] + tail);
            t.cursorLine = lastIdx;
            t.cursorCol = pasted[pasted.length - 1].length();
        }
        diagDirty = true;
        ensureVisible(t);
    }

    private static int wordLeft(String line, int col) {
        int i = Math.min(col, line.length());
        while (i > 0 && line.charAt(i - 1) == ' ') i--;
        if (i == 0) return 0;
        boolean word = isWordChar(line.charAt(i - 1));
        while (i > 0 && line.charAt(i - 1) != ' ' && isWordChar(line.charAt(i - 1)) == word) i--;
        return i;
    }

    private static int wordRight(String line, int col) {
        int n = line.length();
        int i = Math.max(0, Math.min(col, n));
        while (i < n && line.charAt(i) == ' ') i++;
        if (i >= n) return n;
        boolean word = isWordChar(line.charAt(i));
        while (i < n && line.charAt(i) != ' ' && isWordChar(line.charAt(i)) == word) i++;
        return i;
    }

    private static boolean isWordChar(char c) { return Character.isLetterOrDigit(c) || c == '_'; }

    private void moveActiveLine(int direction) {
        if (activeTab < 0) return;
        EditorTab t = openTabs.get(activeTab);
        int from = t.cursorLine;
        int to = from + direction;
        if (to < 0 || to >= t.lines.size()) return;
        String a = t.lines.get(from);
        String b = t.lines.get(to);
        t.lines.set(from, b);
        t.lines.set(to, a);
        t.cursorLine = to;
        diagDirty = true;
        ensureVisible(t);
    }

    private void duplicateActiveLine() {
        if (activeTab < 0) return;
        EditorTab t = openTabs.get(activeTab);
        String line = t.lines.get(t.cursorLine);
        t.lines.add(t.cursorLine + 1, line);
        t.cursorLine++;
        diagDirty = true;
        ensureVisible(t);
    }

    /**
     * Antepone/quita "// " al principio de la linea del cursor - Ctrl+/ en
     * keyPressed(), mismo atajo que VSCode/IntelliJ/Sublime. Detecta
     * "ya comentada" mirando el primer caracter no-blanco de la linea (no
     * columna 0 a secas) para no confundir una linea indentada ya comentada
     * ("    // algo") con una sin comentar - descomentar preserva esa misma
     * indentacion en vez de tirarla. Una linea en blanco (solo espacios o
     * vacia) se deja intacta: comentar "nada" no tiene sentido y evita
     * ensuciar lineas vacias usadas como separador visual.
     */
    private void toggleLineComment() {
        if (activeTab < 0) return;
        EditorTab t = openTabs.get(activeTab);
        String line = t.lines.get(t.cursorLine);
        if (line.isBlank()) return;
        int firstNonBlank = 0;
        while (firstNonBlank < line.length() && line.charAt(firstNonBlank) == ' ') firstNonBlank++;
        String indent = line.substring(0, firstNonBlank);
        String rest = line.substring(firstNonBlank);
        boolean cursorAfterMarker = t.cursorCol >= firstNonBlank;
        if (rest.startsWith("// ")) {
            t.lines.set(t.cursorLine, indent + rest.substring(3));
            if (cursorAfterMarker) t.cursorCol = Math.max(firstNonBlank, t.cursorCol - 3);
        } else if (rest.startsWith("//")) {
            t.lines.set(t.cursorLine, indent + rest.substring(2));
            if (cursorAfterMarker) t.cursorCol = Math.max(firstNonBlank, t.cursorCol - 2);
        } else {
            t.lines.set(t.cursorLine, indent + "// " + rest);
            if (cursorAfterMarker) t.cursorCol += 3;
        }
        diagDirty = true;
    }

    private void openFindPrompt() {
        if (activeTab < 0) return;
        minecraft.setScreen(new CodeFindReplaceScreen(this, minecraft.screen, findTarget, lastSearchTerm));
    }

    private void findNext(String term) {
        if (activeTab < 0 || term == null || term.isEmpty()) return;
        lastSearchTerm = term;
        EditorTab t = openTabs.get(activeTab);
        String needle = term.toLowerCase(java.util.Locale.ROOT);
        int n = t.lines.size();
        for (int pass = 0; pass < 2; pass++) {
            int start = pass == 0 ? t.cursorLine : 0;
            int end = pass == 0 ? n : t.cursorLine + 1;
            for (int li = start; li < end; li++) {
                String hay = t.lines.get(li).toLowerCase(java.util.Locale.ROOT);
                int fromCol = (pass == 0 && li == t.cursorLine) ? t.cursorCol + 1 : 0;
                if (fromCol > hay.length()) continue;
                int idx = hay.indexOf(needle, fromCol);
                if (idx < 0) continue;
                t.clearSelection();
                t.cursorLine = li;
                t.cursorCol = idx;
                searchHighlightLine = li;
                searchHighlightStart = idx;
                searchHighlightEnd = idx + term.length();
                ensureVisible(t);
                return;
            }
        }
        searchHighlightLine = -1;
        flash(Lang.get("gui.kve.code.find_not_found", term), true);
    }

    private void openGoToLinePrompt() {
        if (activeTab < 0) return;
        EditorTab t = openTabs.get(activeTab);
        minecraft.setScreen(new KveTextInputScreen(this,
                Component.literal(Lang.get("gui.kve.code.goto_line_title")),
                String.valueOf(t.cursorLine + 1), Lang.get("gui.kve.code.goto_line_hint"),
                raw -> { minecraft.setScreen(this); goToLine(raw); }));
    }

    private void goToLine(String raw) {
        if (activeTab < 0) return;
        int oneBased;
        try {
            oneBased = Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            flash(Lang.get("gui.kve.code.goto_line_invalid"), true);
            return;
        }
        EditorTab t = openTabs.get(activeTab);
        int target = Math.max(0, Math.min(t.lines.size() - 1, oneBased - 1));
        t.clearSelection();
        t.cursorLine = target;
        t.cursorCol = 0;
        ensureVisible(t);
    }

    @Override
    public void onClose() {
        long dirtyCount = openTabs.stream().filter(EditorTab::dirty).count();
        if (dirtyCount > 0) {
            minecraft.setScreen(new KveConfirmScreen(
                    confirmed -> {
                        if (confirmed) {
                            for (EditorTab t : openTabs) if (t.dirty()) DRAFTS.clear(t.path);
                            doClose();
                        }
                        else minecraft.setScreen(this);
                    },
                    Component.literal(Lang.get("gui.kve.code.unsaved_close_confirm_title")),
                    Component.literal(Lang.get("gui.kve.code.unsaved_close_confirm_msg", dirtyCount))));
            return;
        }
        doClose();
    }

    private void doClose() {
        if (KveStudioShell.exitIfHosted(this, returnScreen)) return;
        minecraft.setScreen(returnScreen != null ? returnScreen : new KveStudioShell());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
