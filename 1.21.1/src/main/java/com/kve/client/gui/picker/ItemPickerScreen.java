package com.kve.client.gui.picker;

import com.kve.client.gui.SafeItemRender;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.KveToast;
import com.kve.client.gui.ListKeyNav;
import com.kve.client.gui.MiniItemIcon;
import com.kve.client.gui.ModNames;
import com.kve.client.gui.RegistryIndex;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.VirtualListWidget;

import org.lwjgl.glfw.GLFW;
import com.kve.common.Lang;
import com.kve.client.gui.studio.KveStudioTab;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * §9: KveStudioTab-capable como
 * TagMembersScreen/TagPresetPickerScreen - standalone (setScreen(), ESC/
 * Cancelar vuelven a {@code parent}) o dockeada (via {@code DockPanel},
 * ESC/Cancelar/pick llaman {@link #onDockClose}), segun {@link #hosted}.
 * Ver Javadoc de {@link #close()}.
 */
@OnlyIn(Dist.CLIENT)
public class ItemPickerScreen extends Screen implements KveStudioTab {

    private static final int GUI_W_DEFAULT = 240;
    private static final int GUI_H_DEFAULT = 230;
    private int GUI_W = GUI_W_DEFAULT;
    private int GUI_H = GUI_H_DEFAULT;
    private static final int COLS         = 9;
    private static final int SLOT         = 18;
    private static final int VISIBLE_ROWS = 9;
    private static final int GRID_X       = 4;
    private static final int GRID_Y       = 31;
    private static final int BAR_W        = 8;

    private final Screen parent;
    private final Consumer<ItemStack> callback;
    private final boolean allowNbtEdit;

    private static final VirtualListWidget.Descriptor<Item> LIST_DESCRIPTOR = new VirtualListWidget.Descriptor<>() {
        @Override public String id(Item item) { return RegistryIndex.id(item); }
        @Override public String displayName(Item item) { return RegistryIndex.name(item); }
    };

    private static final VirtualListWidget.TagMatcher<Item> LIST_TAG_MATCHER = (item, tagToken) -> {
        try {
            ResourceLocation tagId = ResourceLocation.parse(tagToken.contains(":") ? tagToken : "minecraft:" + tagToken);
            var tagKey = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, tagId);
            return new ItemStack(item).is(tagKey);
        } catch (Exception ignored) { return false; }
    };

    private EditBox searchBox;
    private static String lastQuery = "";
    private List<Item> allItems = null;
    private final List<Item> filtered = new ArrayList<>();

    private boolean invMode = false;
    private final List<ItemStack> invStacks = new ArrayList<>();

    private boolean allowTags = false;
    private boolean tagMode = false;
    private List<String> allTags = null;
    private final List<String> tagShown = new ArrayList<>();
    private final java.util.Map<String, List<Item>> tagMembers = new java.util.HashMap<>();
    private static final int TAG_ROW_H = 12;
    private static final int TAG_ROWS = VISIBLE_ROWS * SLOT / TAG_ROW_H;

    /** Fluent setter: muestra el boton Item/Tag (ver {@link #allowTags}). */
    public ItemPickerScreen withTagOption() {
        this.allowTags = true;
        return this;
    }

    private List<Item> membersOf(String tag) {
        return tagMembers.computeIfAbsent(tag, com.kve.common.recipe.TagIngredient::members);
    }

    private int count() { return tagMode ? tagShown.size() : invMode ? invStacks.size() : filtered.size(); }

    private ItemStack displayStack(int idx) {
        return invMode ? invStacks.get(idx) : RegistryIndex.stack(filtered.get(idx));
    }

    private ItemStack pickStack(int idx) {
        return invMode ? invStacks.get(idx).copyWithCount(1) : new ItemStack(filtered.get(idx));
    }

    private void rebuildInventory(String q) {
        invStacks.clear();
        if (minecraft == null || minecraft.player == null) return;
        var inv = minecraft.player.getInventory();
        String needle = q == null ? "" : q.trim().toLowerCase(java.util.Locale.ROOT);
        List<ItemStack> all = new ArrayList<>(inv.items);
        all.addAll(inv.armor);
        all.addAll(inv.offhand);
        for (ItemStack st : all) {
            if (st.isEmpty()) continue;
            if (invStacks.stream().anyMatch(o -> ItemStack.isSameItemSameComponents(o, st))) continue;
            if (!needle.isEmpty()) {
                var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem());
                String hay = (st.getHoverName().getString() + " " + id).toLowerCase(java.util.Locale.ROOT);
                if (!hay.contains(needle)) continue;
            }
            invStacks.add(st.copy());
        }
    }
    private final VirtualListWidget<Item> listIndex = new VirtualListWidget<>(LIST_DESCRIPTOR);
    private ScrollBar scrollBar;
    private final ListKeyNav nav = new ListKeyNav();
    private int guiLeft, guiTop;

    private boolean hosted = false;
    private Runnable onDockClose;

    public ItemPickerScreen(Screen parent, Consumer<ItemStack> callback) {
        this(parent, callback, false);
    }

    public ItemPickerScreen(Screen parent, Consumer<ItemStack> callback, boolean allowNbtEdit) {
        super(Lang.c("gui.kve.pick.item"));
        this.parent       = parent;
        this.callback     = callback;
        this.allowNbtEdit = allowNbtEdit;
        listIndex.setTagMatcher(LIST_TAG_MATCHER);
    }

    /** Fluent setter - ver {@link #onDockClose}. */
    public ItemPickerScreen withDockClose(Runnable r) {
        this.onDockClose = r;
        return this;
    }

    private void close() {
        if (hosted && onDockClose != null) onDockClose.run();
        else minecraft.setScreen(parent);
    }

    @Override
    protected void init() {
        hosted = false;
        GUI_W = GUI_W_DEFAULT;
        GUI_H = GUI_H_DEFAULT;
        guiLeft = (width  - GUI_W) / 2;
        guiTop  = (height - GUI_H) / 2;
        buildContents();
    }

    /**
     * §9: overload de {@link KveStudioTab}
     * - mismo bootstrap manual de {@code minecraft}/{@code font} que
     * {@code TagMembersScreen#init(int,int,int,int)} (DockPanel nunca pasa
     * esta instancia por {@code Minecraft#setScreen()}, ver su Javadoc).
     * A diferencia de {@code TagMembersScreen} (§5.2, layout adaptado a
     * ancho angosto), esta pantalla mantiene su grilla de tamaño fijo -
     * el rect que le pasa el caller ya viene dimensionado a
     * {@code GUI_W_DEFAULT}/{@code GUI_H_DEFAULT} (ver
     * {@code TagEditorScreen#addPickerFor}), no a una columna angosta.
     */
    @Override
    public void init(int x, int y, int w, int h) {
        if (this.minecraft == null) {
            this.minecraft = Minecraft.getInstance();
            this.font = this.minecraft.font;
        }
        hosted = true;
        GUI_W = w;
        GUI_H = h;
        guiLeft = x;
        guiTop = y;
        buildContents();
    }

    private void buildContents() {

        if (allItems == null) {
            allItems = RegistryIndex.items();
            listIndex.setItems(allItems);
        }

        searchBox = new KveScaledEditBox(font,
                guiLeft + GRID_X, guiTop + 16,
                COLS * SLOT - 2, 12,
                Component.literal("Buscar..."));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.literal(Lang.get("gui.kve.search.list_hint")));
        if (tagMode) {
            searchBox.setHint(Component.literal(Lang.get("gui.kve.picker.tag_hint")));
        } else if (!invMode && !lastQuery.isEmpty()) {
            searchBox.setValue(lastQuery);
            searchBox.moveCursorToEnd(false);
            searchBox.setHighlightPos(0);
        }
        searchBox.setResponder(s -> { if (!invMode && !tagMode) lastQuery = s; rebuildFilter(s); updateScrollBar(); nav.reset(); });
        addRenderableWidget(searchBox);
        setFocused(searchBox);

        addRenderableWidget(new KvePlateButton(guiLeft + GUI_W / 2 - 28, guiTop + GUI_H - 16, 56, 13, Component.literal(Lang.get("gui.kve.btn.cancel")),
                btn -> close()));
        addRenderableWidget(new KvePlateButton(guiLeft + GRID_X, guiTop + GUI_H - 16, 60, 13,
                Component.literal(Lang.get(invMode ? "gui.kve.picker.all_items" : "gui.kve.picker.inventory")),
                btn -> {
                    invMode = !invMode;
                    tagMode = false;
                    clearWidgets();
                    buildContents();
                }).withSelected(invMode));
        if (allowTags) {
            addRenderableWidget(new KvePlateButton(guiLeft + GUI_W - GRID_X - 60, guiTop + GUI_H - 16, 60, 13,
                    Component.literal(Lang.get(tagMode ? "gui.kve.picker.mode_item" : "gui.kve.picker.mode_tag")),
                    btn -> {
                        tagMode = !tagMode;
                        invMode = false;
                        nav.reset();
                        if (scrollBar != null) scrollBar.setScroll(0);
                        clearWidgets();
                        buildContents();
                    }).withSelected(tagMode));
            if (tagMode && allTags == null) allTags = com.kve.common.recipe.TagIngredient.allTags();
        }

        int gridH = VISIBLE_ROWS * SLOT;
        scrollBar = new ScrollBar(
                guiLeft + GRID_X + COLS * SLOT + 2,
                guiTop + GRID_Y,
                gridH, 0, VISIBLE_ROWS);

        rebuildFilter(searchBox.getValue());
        updateScrollBar();
    }

    /** ESC returns to the caller instead of closing the whole GUI. */
    @Override
    public void onClose() { close(); }

    private void rebuildFilter(String q) {
        if (tagMode) {
            tagShown.clear();
            String needle = q == null ? "" : q.trim().toLowerCase(java.util.Locale.ROOT);
            if (needle.startsWith("#")) needle = needle.substring(1);
            if (allTags != null) for (String t : allTags) if (needle.isEmpty() || t.contains(needle)) tagShown.add(t);
            nav.clamp(tagShown.size());
            return;
        }
        if (invMode) { rebuildInventory(q); nav.clamp(invStacks.size()); return; }
        listIndex.filter(q, null);
        filtered.clear();
        filtered.addAll(listIndex.filtered());
        if (q == null || q.isBlank()) {
            List<Item> favs = new ArrayList<>();
            for (Item it : filtered) if (com.kve.common.persist.CraftEditorState.isFavoriteItem(RegistryIndex.id(it))) favs.add(it);
            if (!favs.isEmpty()) {
                filtered.removeAll(new java.util.HashSet<>(favs));
                filtered.addAll(0, favs);
            }
        }
        nav.clamp(filtered.size());
    }

    private void updateScrollBar() {
        if (scrollBar != null && tagMode) {
            scrollBar.setTotals(tagShown.size(), TAG_ROWS);
        } else if (scrollBar != null) {
            int totalRows = (int) Math.ceil(count() / (double) COLS);
            scrollBar.setTotals(totalRows, VISIBLE_ROWS);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!hosted) {
            renderTransparentBackground(g);
            String title = tagMode ? Lang.get("gui.kve.picker.tag_title") : invMode ? Lang.get("gui.kve.picker.inventory") : "Elegir Item";
            KveGuiStyle.drawWindow(g, guiLeft, guiTop, GUI_W, GUI_H, title + " (" + count() + ")");
        }
        if (tagMode) { renderTagList(g, mx, my); KveGuiStyle.renderWidgets(this, g, mx, my, pt); return; }

        int scroll = scrollBar.getScroll();
        ItemStack hoveredStack = ItemStack.EMPTY;
        ItemStack hoveredHotspotStack = ItemStack.EMPTY;
        ItemStack keyboardStack = ItemStack.EMPTY;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = (row + scroll) * COLS + col;
                if (idx >= count()) break;
                int sx = guiLeft + GRID_X + col * SLOT;
                int sy = guiTop  + GRID_Y + row * SLOT;
                g.fill(sx, sy, sx + 16, sy + 16, KveGuiStyle.th(0xFF080C18));
                g.hLine(sx, sx + 15, sy, KveGuiStyle.th(0xFF334455));
                g.hLine(sx, sx + 15, sy + 15, KveGuiStyle.th(0xFF334455));
                g.vLine(sx, sy, sy + 15, KveGuiStyle.th(0xFF334455));
                g.vLine(sx + 15, sy, sy + 15, KveGuiStyle.th(0xFF334455));
                ItemStack stack = displayStack(idx);
                try {
                    SafeItemRender.render(g, stack, sx, sy);
                } catch (Throwable ignored) {
                    g.fill(sx, sy, sx + 16, sy + 16, KveGuiStyle.th(0xFF334455));
                }
                boolean hoveringHotspot = allowNbtEdit && isNbtHotspot(sx, sy, mx, my);
                if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                    if (hoveringHotspot) hoveredHotspotStack = stack;
                    else { g.fill(sx, sy, sx + 16, sy + 16, 0x60AADDFF); hoveredStack = stack; }
                }
                if (nav.isCursor(idx)) {
                    g.hLine(sx - 1, sx + 16, sy - 1, KveGuiStyle.ACCENT_GOLD);
                    g.hLine(sx - 1, sx + 16, sy + 16, KveGuiStyle.ACCENT_GOLD);
                    g.vLine(sx - 1, sy - 1, sy + 16, KveGuiStyle.ACCENT_GOLD);
                    g.vLine(sx + 16, sy - 1, sy + 16, KveGuiStyle.ACCENT_GOLD);
                    if (hoveredStack.isEmpty()) keyboardStack = stack;
                }
                if (allowNbtEdit) g.fill(sx + 10, sy, sx + 16, sy + 6, hoveringHotspot ? 0xFF446688 : 0xFF203040);
                if (!invMode && com.kve.common.persist.CraftEditorState.isFavoriteItem(RegistryIndex.id(filtered.get(idx)))) {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 250);
                    g.hLine(sx + 1, sx + 3, sy + 2, KveGuiStyle.ACCENT_GOLD);
                    g.vLine(sx + 2, sy + 1, sy + 3, KveGuiStyle.ACCENT_GOLD);
                    g.pose().popPose();
                }
            }
        }

        scrollBar.render(g, mx, my);

        int totalRows = (int) Math.ceil(count() / (double) COLS);
        if (invMode && invStacks.isEmpty()) {
            KveGuiStyle.drawCenteredTextSafe(g, Lang.get("gui.kve.picker.inventory_empty"),
                    guiLeft + GUI_W / 2, guiTop + GRID_Y + 20, KveGuiStyle.TEXT_MUTED);
        } else if (!invMode && filtered.isEmpty()) {
            KveGuiStyle.drawCenteredTextSafe(g, Lang.get("gui.kve.picker.no_results", searchBox.getValue().trim()),
                    guiLeft + GUI_W / 2, guiTop + GRID_Y + 20, KveGuiStyle.TEXT_MUTED);
            KveGuiStyle.drawCenteredTextSafe(g, Lang.get("gui.kve.picker.no_results_hint"),
                    guiLeft + GUI_W / 2, guiTop + GRID_Y + 32, KveGuiStyle.TEXT_MUTED);
        } else if (!invMode) {
            KveGuiStyle.drawCenteredTextSafe(g, Lang.get("gui.kve.picker.mouse_hint"),
                    guiLeft + GUI_W / 2, guiTop + GUI_H - 35, KveGuiStyle.TEXT_MUTED);
        }
        KveGuiStyle.drawCenteredTextSafe(g,
                (scroll + 1) + " / " + Math.max(1, totalRows),
                guiLeft + GUI_W / 2, guiTop + GUI_H - 25, KveGuiStyle.th(0xFF778899));

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
        if (!hoveredHotspotStack.isEmpty())
            g.renderTooltip(font, (Component.literal(Lang.get("gui.kve.nbt.hotspot.craft"))), mx, my);
        else if (!hoveredStack.isEmpty())
            g.renderComponentTooltip(font, tooltipWithMod(hoveredStack), mx, my);
        else if (!keyboardStack.isEmpty())
            g.renderTooltip(font, keyboardStack, cursorTooltipX(), cursorTooltipY());
    }

    private void renderTagList(GuiGraphics g, int mx, int my) {
        int x = guiLeft + GRID_X, w = COLS * SLOT;
        int top = guiTop + GRID_Y;
        g.fill(x - 1, top - 1, x + w, top + TAG_ROWS * TAG_ROW_H, KveGuiStyle.BG_FIELD);
        int scroll = scrollBar.getScroll();
        for (int i = 0; i < TAG_ROWS && i + scroll < tagShown.size(); i++) {
            int idx = i + scroll;
            String t = tagShown.get(idx);
            int ry = top + i * TAG_ROW_H;
            boolean hov = mx >= x && mx < x + w && my >= ry && my < ry + TAG_ROW_H;
            if (hov || nav.isCursor(idx)) g.fill(x - 1, ry, x + w, ry + TAG_ROW_H, KveGuiStyle.BORDER_FIELD);
            List<Item> members = membersOf(t);
            MiniItemIcon.draw(g, x + 1, ry + 1, 10, members.isEmpty() ? null : members.get(0));
            String countTxt = Lang.get("gui.kve.recipe.tagpick.count", members.size());
            int countW = Math.round(font.width(countTxt) * KveGuiStyle.uiTextScale()) + 2;
            KveGuiStyle.drawTextSafe(g, "#" + t, x + 14, ry + 2, w - 16 - countW, KveGuiStyle.TEXT_PRIMARY);
            KveGuiStyle.drawTextSafe(g, countTxt, x + w - countW, ry + 2, countW, KveGuiStyle.TEXT_MUTED);
        }
        scrollBar.render(g, mx, my);
        if (tagShown.isEmpty()) {
            KveGuiStyle.drawCenteredTextSafe(g, Lang.get("gui.kve.recipe.tagpick.none"), guiLeft + GUI_W / 2, top + 20, KveGuiStyle.TEXT_MUTED);
        }
    }

    private int tagRowAt(double mx, double my) {
        int x = guiLeft + GRID_X, top = guiTop + GRID_Y;
        if (mx < x || mx >= x + COLS * SLOT || my < top || my >= top + TAG_ROWS * TAG_ROW_H) return -1;
        int idx = (int) ((my - top) / TAG_ROW_H) + scrollBar.getScroll();
        return idx < tagShown.size() ? idx : -1;
    }

    private void pickTag(int idx) {
        if (idx < 0 || idx >= tagShown.size()) return;
        callback.accept(com.kve.common.recipe.TagIngredient.of(tagShown.get(idx), null));
        close();
    }

    private List<Component> tooltipWithMod(ItemStack stack) {
        List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, stack));
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
            String modName = ModNames.of(id.getNamespace());
            boolean present = lines.stream().anyMatch(c -> c.getString().equals(modName));
            if (!present) lines.add(Component.literal(modName).withStyle(net.minecraft.ChatFormatting.BLUE, net.minecraft.ChatFormatting.ITALIC));
        }
        return lines;
    }

    private int cursorTooltipX() {
        int col = nav.cursor() % COLS;
        return guiLeft + GRID_X + col * SLOT + 18;
    }

    private int cursorTooltipY() {
        int row = nav.cursor() / COLS - (scrollBar == null ? 0 : scrollBar.getScroll());
        return guiTop + GRID_Y + row * SLOT + 12;
    }

    private void pick(int idx, boolean viaNbtHotspot) {
        if (idx < 0 || idx >= count()) return;
        if (allowNbtEdit && viaNbtHotspot) {
            minecraft.setScreen(new NbtEditorScreen(this, pickStack(idx), s -> {
                callback.accept(s);
                minecraft.setScreen(parent);
            }));
        } else {
            callback.accept(pickStack(idx));
            close();
        }
    }

    private boolean isNbtHotspot(int sx, int sy, double mx, double my) {
        if (hosted) return false;
        return mx >= sx + 10 && mx < sx + 16 && my >= sy && my < sy + 6;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar.mouseClicked(mx, my, btn)) return true;
        if (tagMode) {
            int row = tagRowAt(mx, my);
            if (row >= 0 && btn == 0) { pickTag(row); return true; }
            return super.mouseClicked(mx, my, btn);
        }

        int scroll = scrollBar.getScroll();
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = (row + scroll) * COLS + col;
                if (idx >= count()) break;
                int sx = guiLeft + GRID_X + col * SLOT;
                int sy = guiTop  + GRID_Y + row * SLOT;
                if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                    String id = RegistryIndex.id(displayStack(idx).getItem());
                    if (btn == 1 && !invMode) {
                        com.kve.common.persist.CraftEditorState.toggleFavoriteItem(id);
                        boolean fav = com.kve.common.persist.CraftEditorState.isFavoriteItem(id);
                        KveToast.info(Lang.get(fav ? "gui.kve.picker.fav_added" : "gui.kve.picker.fav_removed"), id);
                        return true;
                    }
                    if (btn == 2) {
                        minecraft.keyboardHandler.setClipboard(id);
                        KveToast.ok(Lang.get("gui.kve.toast.copied"), id);
                        return true;
                    }
                    pick(idx, allowNbtEdit && isNbtHotspot(sx, sy, mx, my));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (scrollBar.mouseDragged(mx, my, btn, dx, dy)) return true;
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        scrollBar.mouseReleased(mx, my, btn);
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        scrollBar.mouseScrolled(delta);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (tagMode) {
            if (nav.keyPressed(key, count(), 1, TAG_ROWS, scrollBar)) return true;
            if (ListKeyNav.isConfirm(key) && nav.active()) { pickTag(nav.cursor()); return true; }
            if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
            return true;
        }
        if (nav.keyPressed(key, count(), COLS, VISIBLE_ROWS, scrollBar)) return true;
        if (ListKeyNav.isConfirm(key) && nav.active()) {
            pick(nav.cursor(), allowNbtEdit && (mods & GLFW.GLFW_MOD_CONTROL) != 0);
            return true;
        }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }
}