package com.kve.client.gui.tag;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.MiniItemIcon;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.SearchFilter;

import org.lwjgl.glfw.GLFW;
import com.kve.common.Lang;
import com.kve.client.gui.studio.KveStudioTab;
import com.kve.common.kubejs.TagExporter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class TagMembersScreen extends Screen implements KveStudioTab {

    private static final int GUI_W_DEFAULT = 230;
    private static final int GUI_H_DEFAULT = 224;
    private int GUI_W = GUI_W_DEFAULT;
    private int GUI_H = GUI_H_DEFAULT;
    private static final int ROWS_VISIBLE = 11;
    private static final int ROW_H = 13;
    private static final int HEADER_H = 13;
    private static final int LIST_TOP = 32;
    private static final int ICON_SIZE = 9;

    private static final Map<String, Boolean> SECTION_EXPANDED = new HashMap<>();
    private static final String SEC_USER = "user_added";
    private static final String SEC_DEFAULT = "default";

    private final Screen parent;
    private final String tagId;
    private final List<String> defaultMembers;
    private final List<String> addedByUser;
    private final TagExporter.RegistryType regType;
    private final Consumer<Set<String>> onConfirm;
    private final Runnable onManualAdd;

    private static final class Section {
        final String key;
        final String label;
        final int headerColor;
        final List<String> entries;
        Section(String key, String label, int headerColor, List<String> entries) {
            this.key = key; this.label = label; this.headerColor = headerColor; this.entries = entries;
        }
    }

    private static final class Row {
        final Section section;
        final String id;
        Row(Section s, String id) { this.section = s; this.id = id; }
        boolean isHeader() { return id == null; }
    }

    private final List<Section> sections = new ArrayList<>();
    private final List<Row> visibleRows = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();

    private int L, T;
    private EditBox searchBox;
    private ScrollBar scrollBar;
    private String filter = "";
    private Button removeBtn;

    private boolean hosted = false;

    private Runnable onDockClose;

    public TagMembersScreen(Screen parent, String tagId, List<String> defaultMembers, List<String> addedByUser,
                             TagExporter.RegistryType regType, Consumer<Set<String>> onConfirm, Runnable onManualAdd) {
        super(Component.literal(Lang.get("gui.kve.tag.members.title", tagId)));
        this.parent = parent;
        this.tagId = tagId;
        this.defaultMembers = new ArrayList<>(defaultMembers);
        this.addedByUser = new ArrayList<>(addedByUser);
        this.regType = regType;
        this.onConfirm = onConfirm;
        this.onManualAdd = onManualAdd;
    }

    /** Fluent setter - ver {@link #onDockClose}. */
    public TagMembersScreen withDockClose(Runnable r) {
        this.onDockClose = r;
        return this;
    }

    public void refreshMembers(List<String> defaultMembers, List<String> addedByUser) {
        this.defaultMembers.clear();
        this.defaultMembers.addAll(defaultMembers);
        this.addedByUser.clear();
        this.addedByUser.addAll(addedByUser);
        Set<String> stillPresent = new java.util.HashSet<>(defaultMembers);
        stillPresent.addAll(addedByUser);
        selected.retainAll(stillPresent);
        buildSections();
        rebuildFilter();
    }

    private void close() {
        if (hosted && onDockClose != null) onDockClose.run();
        else minecraft.setScreen(parent);
    }

    private int totalMembers() { return defaultMembers.size() + addedByUser.size(); }

    private Item resolveIcon(String id) {
        if (regType == TagExporter.RegistryType.ENTITY_TYPE) return null;
        return regType == TagExporter.RegistryType.BLOCK ? MiniItemIcon.block(id) : MiniItemIcon.item(id);
    }

    @Override
    protected void init() {
        hosted = false;
        GUI_W = GUI_W_DEFAULT;
        GUI_H = GUI_H_DEFAULT;
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        buildContents();
    }

    /**
     * §3.1: overload de {@link KveStudioTab}
     * - reemplaza el centrado standalone de arriba por el rect que
     * {@code DockPanel} le asigna dentro de la columna "Quitado" de
     * {@code TagEditorScreen}. Mismo bootstrap de {@code minecraft}/{@code font}
     * que {@code TagEditorScreen.init(int,int,int,int)} ya necesita (ver su
     * Javadoc) - DockPanel nunca pasa esta instancia por
     * {@code Minecraft#setScreen()}, asi que {@code Screen#init(Minecraft,int,int)}
     * nunca corre sola para el primer open dockeado.
     */
    @Override
    public void init(int x, int y, int w, int h) {
        if (this.minecraft == null) {
            this.minecraft = Minecraft.getInstance();
            this.font = this.minecraft.font;
        }
        hosted = true;
        L = x;
        T = y;
        GUI_W = Math.max(1, w);
        GUI_H = Math.max(1, h);
        buildContents();
    }

    private void refresh() {
        if (hosted) buildContents();
        else init();
    }

    private void buildContents() {
        clearWidgets();
        setFocused(null);
        buildSections();
        rebuildFilter();

        searchBox = new KveScaledEditBox(font, L + 6, T + 16, GUI_W - 12, 12, Lang.c("gui.kve.tag.members.search_hint"));
        searchBox.setHint(Lang.c("gui.kve.tag.members.search_hint"));
        searchBox.setValue(filter);
        searchBox.setResponder(s -> { filter = s; rebuildFilter(); });
        addRenderableWidget(searchBox);

        if (scrollBar == null) {
            scrollBar = new ScrollBar(L + GUI_W - 10, T + LIST_TOP, ROWS_VISIBLE * ROW_H, visibleRows.size(), ROWS_VISIBLE);
        } else {
            scrollBar.setPosition(L + GUI_W - 10, T + LIST_TOP);
            scrollBar.setTotals(visibleRows.size(), ROWS_VISIBLE);
        }

        int by1 = T + GUI_H - 44;
        addRenderableWidget(new KvePlateButton(L + 6, by1, (GUI_W - 16) / 2, 13, Component.literal(Lang.get("gui.kve.tag.members.select_all")),
                        b -> toggleSelectAllVisible()));
        addRenderableWidget(new KvePlateButton(L + 10 + (GUI_W - 16) / 2, by1, (GUI_W - 16) / 2, 13, Component.literal(Lang.get("gui.kve.tag.members.manual_add")),
                        b -> { onManualAdd.run(); }));

        int by2 = T + GUI_H - 28;
        removeBtn = addRenderableWidget(new KvePlateButton(L + 6, by2, GUI_W - 12, 13, Component.literal(Lang.get("gui.kve.tag.members.remove_selected", selected.size())),
                        b -> confirm()));
        removeBtn.active = !selected.isEmpty();

        addRenderableWidget(new KvePlateButton(L + 6, T + GUI_H - 14, GUI_W - 12, 12, Component.literal(Lang.get("gui.kve.btn.cancel")),
                        b -> close()));
    }

    private void buildSections() {
        sections.clear();
        List<String> user = new ArrayList<>(addedByUser);
        Collections.sort(user);
        List<String> def = new ArrayList<>(defaultMembers);
        Collections.sort(def);
        if (!user.isEmpty()) {
            sections.add(new Section(SEC_USER, Lang.get("gui.kve.tag.members.section_user", user.size()), 0xFF7DE0B0, user));
        }
        if (!def.isEmpty()) {
            sections.add(new Section(SEC_DEFAULT, Lang.get("gui.kve.tag.members.section_default", def.size()), KveGuiStyle.TEXT_MUTED, def));
        }
    }

    private void rebuildFilter() {
        visibleRows.clear();
        boolean searching = filter != null && !filter.isBlank();
        for (Section sec : sections) {
            List<String> matches = new ArrayList<>();
            for (String id : sec.entries) if (!searching || SearchFilter.matches(id, filter)) matches.add(id);
            if (matches.isEmpty()) continue;
            visibleRows.add(new Row(sec, null));
            boolean expanded = searching || SECTION_EXPANDED.getOrDefault(sec.key, true);
            if (expanded) for (String id : matches) visibleRows.add(new Row(sec, id));
        }
        if (scrollBar != null) scrollBar.setTotals(visibleRows.size(), ROWS_VISIBLE);
    }

    private void toggleSection(Section sec) {
        boolean cur = SECTION_EXPANDED.getOrDefault(sec.key, true);
        SECTION_EXPANDED.put(sec.key, !cur);
        rebuildFilter();
    }

    private void toggleSelectAllVisible() {
        List<String> visibleIds = new ArrayList<>();
        for (Row r : visibleRows) if (!r.isHeader()) visibleIds.add(r.id);
        boolean allSelected = !visibleIds.isEmpty() && selected.containsAll(visibleIds);
        if (allSelected) selected.removeAll(visibleIds);
        else selected.addAll(visibleIds);
        refresh();
    }

    private void toggleRow(String id) {
        if (!selected.add(id)) selected.remove(id);
        refresh();
    }

    private void confirm() {
        if (selected.isEmpty()) return;
        Set<String> chosen = new LinkedHashSet<>(selected);
        onConfirm.accept(chosen);
        close();
    }

    /** ESC returns to the caller instead of closing the whole GUI. */
    @Override
    public void onClose() { close(); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!hosted) {
            renderTransparentBackground(g);
            KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H,
                    Lang.get("gui.kve.tag.members.title", tagId) + " (" + totalMembers() + ")");
        }

        int scroll = scrollBar != null ? scrollBar.getScroll() : 0;
        int visible = Math.min(ROWS_VISIBLE, Math.max(0, visibleRows.size() - scroll));
        for (int i = 0; i < visible; i++) {
            Row row = visibleRows.get(i + scroll);
            int ry = T + LIST_TOP + i * ROW_H;
            if (row.isHeader()) {
                renderHeader(g, row.section, ry, mx, my);
            } else {
                renderMemberRow(g, row.id, ry, mx, my);
            }
        }
        if (visibleRows.isEmpty()) {
            List<String> emptyLines =
                    KveGuiStyle.wrapTextSafe(font, Lang.get("gui.kve.tag.members.empty"), GUI_W - 12);
            for (int i = 0; i < emptyLines.size(); i++) {
                KveGuiStyle.drawTextSafe(g, emptyLines.get(i), L + 6, T + LIST_TOP + 4 + i * 10, GUI_W - 12, KveGuiStyle.TEXT_MUTED);
            }
        }
        if (scrollBar != null) scrollBar.render(g, mx, my);

        if (removeBtn != null) {
            removeBtn.setMessage(Component.literal(Lang.get("gui.kve.tag.members.remove_selected", selected.size())));
            removeBtn.active = !selected.isEmpty();
        }

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    private void renderHeader(GuiGraphics g, Section sec, int ry, int mx, int my) {
        boolean hov = mx >= L + 4 && mx < L + GUI_W - 12 && my >= ry && my < ry + HEADER_H;
        g.fill(L + 4, ry, L + GUI_W - 12, ry + HEADER_H, hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_TITLEBAR);
        g.fill(L + 4, ry, L + 6, ry + HEADER_H, sec.headerColor);

        boolean expanded = SECTION_EXPANDED.getOrDefault(sec.key, true) || (filter != null && !filter.isBlank());
        String arrow = expanded ? "\u25BC" : "\u25B6";
        KveGuiStyle.drawTextSafe(g, arrow + " " + sec.label, L + 10, ry + 3, GUI_W - 24, KveGuiStyle.ACCENT_GOLD);
    }

    private void renderMemberRow(GuiGraphics g, String id, int ry, int mx, int my) {
        boolean sel = selected.contains(id);
        g.fill(L + 4, ry, L + GUI_W - 12, ry + ROW_H - 1, sel ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
        KveGuiStyle.drawCheck(g, L + 6, ry + 2, sel, KveGuiStyle.isHovered(mx, my, L + 4, ry, GUI_W - 16, ROW_H - 1));
        int fx = L + 18, fy = ry + 1;
        MiniItemIcon.draw(g, fx, fy, ICON_SIZE, resolveIcon(id));
        int nameX = fx + ICON_SIZE + 3;
        KveGuiStyle.drawTextSafe(g, id, nameX, ry + 2, GUI_W - 34 - (nameX - (L + 16)), sel ? 0xFFAADDAA : KveGuiStyle.TEXT_LABEL);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar != null && scrollBar.mouseClicked(mx, my, btn)) return true;
        int scroll = scrollBar != null ? scrollBar.getScroll() : 0;
        int visible = Math.min(ROWS_VISIBLE, Math.max(0, visibleRows.size() - scroll));
        for (int i = 0; i < visible; i++) {
            int ry = T + LIST_TOP + i * ROW_H;
            if (mx >= L + 4 && mx < L + GUI_W - 12 && my >= ry && my < ry + ROW_H - 1) {
                Row row = visibleRows.get(i + scroll);
                if (row.isHeader()) toggleSection(row.section);
                else toggleRow(row.id);
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (scrollBar != null && scrollBar.mouseDragged(mx, my, btn, dx, dy)) return true;
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (scrollBar != null) scrollBar.mouseReleased(mx, my, btn);
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        int listTop = T + LIST_TOP, listBot = listTop + ROWS_VISIBLE * ROW_H;
        if (scrollBar != null && mx >= L && mx < L + GUI_W && my >= listTop && my < listBot) {
            scrollBar.mouseScrolled(delta);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, delta);
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        return true;
    }
    @Override public boolean isPauseScreen() { return false; }
}
