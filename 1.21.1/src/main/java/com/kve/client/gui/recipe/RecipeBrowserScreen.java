package com.kve.client.gui.recipe;

import com.kve.client.gui.SafeItemRender;
import com.kve.client.gui.KveConfirmScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.VirtualListWidget;

import net.minecraft.world.item.crafting.RecipeHolder;

import com.kve.common.Lang;

import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.permission.ClientPermissionState;
import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.recipe.RecipeData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.kve.client.gui.history.KveChangeLog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class RecipeBrowserScreen extends Screen {

    private static final int GUI_W = 360;
    private static final int GUI_H = 260;
    private static final int ROWS_VISIBLE = 9;
    private static final int ROW_H = 18;

    private final Screen parent;

    private static class Entry {
        final ResourceLocation id;
        final Recipe<?> recipe;
        final ItemStack output;
        final RecipeData.RecipeType stationType;
        final String stationCustomTypeId;
        Entry(ResourceLocation id, Recipe<?> recipe, ItemStack output, RecipeData.RecipeType stationType, String stationCustomTypeId) {
            this.id = id; this.recipe = recipe; this.output = output;
            this.stationType = stationType; this.stationCustomTypeId = stationCustomTypeId;
        }
    }

    private static class ItemGroup {
        final String key;
        final Entry representative;
        final List<Entry> entries = new ArrayList<>();
        ItemGroup(String key, Entry representative) { this.key = key; this.representative = representative; }
    }

    private enum SourceFilter { ALL, VANILLA, MODDED, KVE, REMOVED }

    private static final int ROW_ACTION_W = 14;

    private static final VirtualListWidget.Descriptor<Entry> LIST_DESCRIPTOR = new VirtualListWidget.Descriptor<>() {
        @Override public String id(Entry e) { return e.id == null ? null : e.id.toString(); }
        @Override public String displayName(Entry e) { return e.output == null ? null : e.output.getHoverName().getString(); }
    };

    private static final VirtualListWidget.TagMatcher<Entry> LIST_TAG_MATCHER = (e, tagToken) -> {
        try {
            if (e.output == null || e.output.isEmpty()) return false;
            ResourceLocation tagId = ResourceLocation.parse(tagToken.contains(":") ? tagToken : "minecraft:" + tagToken);
            var tagKey = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, tagId);
            return e.output.is(tagKey);
        } catch (Exception ignored) { return false; }
    };

    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> filtered = new ArrayList<>();
    private final VirtualListWidget<Entry> listIndex = new VirtualListWidget<>(LIST_DESCRIPTOR);
    private EditBox searchBox;
    private String searchFilter = "";
    private SourceFilter sourceFilter = SourceFilter.ALL;
    private ScrollBar scrollBar;
    private boolean inWorld = false;
    private int totalRecipeCount = 0;

    private final List<ItemGroup> groups = new ArrayList<>();
    private boolean detailMode = false;
    private ItemGroup detailGroup;

    private final List<Button> filterButtons = new ArrayList<>();

    private int L, T;
    private int listY;

    private double smoothScroll = 0;

    public RecipeBrowserScreen(Screen parent) {
        super(Lang.c("gui.kve.recipe.edit_existing"));
        this.parent = parent;
        listIndex.setTagMatcher(LIST_TAG_MATCHER);
    }

    private int scrollBaseRow() { return (int) Math.floor(smoothScroll); }

    private int scrollPixelShift() { return (int) Math.round((smoothScroll - scrollBaseRow()) * ROW_H); }

    @Override
    protected void init() {
        L = (width  - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        buildRecipeList();

        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(L + 6, T + 18, 20);
        int searchY = ly.row();
        int filterY = ly.row(16);
        int scrollY = ly.row();
        listY = scrollY;

        searchBox = new KveScaledEditBox(font, ly.x(), searchY, GUI_W - 12, 14,
                Component.literal(Lang.get("gui.kve.search.recipe")));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.literal(Lang.get("gui.kve.search.list_hint")));
        searchBox.setResponder(s -> {
            searchFilter = s;
            rebuildFilter();
        });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        filterButtons.clear();
        SourceFilter[] filters = SourceFilter.values();
        int filterW = (GUI_W - 12) / filters.length;
        int fx = L + 6;
        for (SourceFilter f : filters) {
            Button b = addRenderableWidget(new KvePlateButton(fx, filterY, filterW - 2, 12, Component.literal(filterLabel(f)), btn -> {
                        sourceFilter = f;
                        rebuildFilter();
                    }));
            filterButtons.add(b);
            fx += filterW;
        }

        scrollBar = new ScrollBar(L + GUI_W - 10, scrollY, ROWS_VISIBLE * ROW_H, 0, ROWS_VISIBLE);
        smoothScroll = 0;
        rebuildFilter();

        if (detailMode) {
            String backLabel = "<- " + Lang.get("gui.kve.btn.back");
            addRenderableWidget(new KvePlateButton(L + 6, T + 2, KveGuiStyle.btnWidth(backLabel), 14,
                    Component.literal(backLabel), b -> {
                        detailMode = false;
                        detailGroup = null;
                        init();
                    }));
        }

        KveGuiStyle.Layout bottom = new KveGuiStyle.Layout(L + GUI_W / 2 - 90, T + GUI_H - 38, 20);
        int newRecipeY = bottom.row();
        int actionRowY = bottom.row();

        if (!inWorld) {
            addRenderableWidget(new KvePlateButton(L + GUI_W / 2 - 90, newRecipeY, 180, 14, Component.literal(Lang.get("gui.kve.btn.new_recipe")), b ->
                    com.kve.client.gui.studio.KveStudioShell.openRecipe(parent, null, null)));
        }

        {
            String reloadLabel = Lang.get("gui.kve.btn.reload");
            String toolsLabel = Lang.get("gui.kve.recipe.tools.btn");
            int reloadW = KveGuiStyle.btnWidth(reloadLabel);
            int rowW = reloadW;
            if (inWorld) rowW += KveGuiStyle.btnWidth(toolsLabel) + KveGuiStyle.BTN_GAP;
            int reloadX = Math.max(L + 6, L + GUI_W / 2 - rowW / 2);
            KveGuiStyle.ButtonRow actionRow = new KveGuiStyle.ButtonRow(reloadX, actionRowY, L + GUI_W - 6);
            if (inWorld) {
                addRenderableWidget(actionRow.add(0, toolsLabel, b -> minecraft.setScreen(new RecipeToolsScreen(this))));
            }
            addRenderableWidget(actionRow.add(0, reloadLabel, b -> init()));
        }
        addRenderableWidget(new KvePlateButton(L + GUI_W - 17, T + 2, 15, 14, Component.literal("X"), b -> onClose()));
    }

    private void buildRecipeList() {
        all.clear();
        inWorld = false;

        RecipeManager mgr = null;
        RegistryAccess regAccess = null;
        if (minecraft != null) {

            if (minecraft.getConnection() != null) {
                mgr = minecraft.getConnection().getRecipeManager();
                regAccess = minecraft.getConnection().registryAccess();
            } else if (minecraft.level != null) {
                mgr = minecraft.level.getRecipeManager();
                regAccess = minecraft.level.registryAccess();
            }
        }
        if (mgr == null) return;
        inWorld = true;
        if (regAccess == null && minecraft.level != null) regAccess = minecraft.level.registryAccess();

        List<Entry> vanilla = new ArrayList<>();
        List<Entry> modded  = new ArrayList<>();

        totalRecipeCount = 0;
        for (RecipeHolder<?> rHolder : mgr.getRecipes()) {
            Recipe<?> r = rHolder.value();
            totalRecipeCount++;
            ResourceLocation id = rHolder.id();
            if (id == null) continue;

            ItemStack out = ItemStack.EMPTY;
            if (regAccess != null) {
                try { out = r.getResultItem(regAccess); } catch (Throwable ignored) {}
            }
            if (out == null || out.isEmpty()) {

                out = new ItemStack(net.minecraft.world.item.Items.BARRIER);
            }
            RecipeData.RecipeType stationType;
            String stationCustomTypeId = "";
            if (r instanceof ShapedRecipe) {
                stationType = RecipeData.RecipeType.VANILLA_SHAPED;
            } else if (r instanceof ShapelessRecipe) {
                stationType = RecipeData.RecipeType.VANILLA_SHAPELESS;
            } else {
                stationType = RecipeData.RecipeType.CUSTOM_JSON;
                ResourceLocation typeId = net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER
                        .getKey(r.getSerializer());
                stationCustomTypeId = typeId != null ? typeId.toString() : "";
            }
            Entry e = new Entry(id, r, out, stationType, stationCustomTypeId);
            (id.getNamespace().equals("minecraft") ? vanilla : modded).add(e);
        }

        vanilla.sort(Comparator.comparing(e -> e.output.getHoverName().getString()));
        modded.sort(Comparator.comparing((Entry e) -> e.id.getNamespace())
                .thenComparing(e -> e.output.getHoverName().getString()));

        all.addAll(vanilla);
        all.addAll(modded);
        listIndex.setItems(all);
    }

    private void rebuildFilter() {
        listIndex.filter(searchFilter, this::matchesSourceFilter);
        filtered.clear();
        filtered.addAll(listIndex.filtered());
        rebuildGroups();
        if (scrollBar != null) {
            scrollBar.setTotals(detailMode ? detailGroup.entries.size() : groups.size(), ROWS_VISIBLE);
            scrollBar.setScroll(0);
        }
    }

    private void rebuildGroups() {
        groups.clear();
        java.util.Map<String, ItemGroup> byKey = new java.util.LinkedHashMap<>();
        for (Entry e : filtered) {
            String key = itemKey(e.output);
            ItemGroup g = byKey.get(key);
            if (g == null) {
                g = new ItemGroup(key, e);
                byKey.put(key, g);
                groups.add(g);
            }
            g.entries.add(e);
        }
        if (detailMode) {
            ItemGroup stillThere = byKey.get(detailGroup.key);
            if (stillThere == null) { detailMode = false; detailGroup = null; }
            else detailGroup = stillThere;
        }
    }

    private String itemKey(ItemStack s) {
        ResourceLocation rl = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem());
        return rl != null ? rl.toString() : s.getHoverName().getString();
    }

    private boolean matchesSourceFilter(Entry e) {
        return switch (sourceFilter) {
            case ALL -> true;
            case VANILLA -> e.id.getNamespace().equals("minecraft");
            case MODDED -> !e.id.getNamespace().equals("minecraft") && !e.id.getNamespace().equals("shovel_js");
            case KVE -> e.id.getNamespace().equals("shovel_js");
            case REMOVED -> KubeJSExporter.getRemovedRecipes().contains(e.id.toString());
        };
    }

    private String filterLabel(SourceFilter f) {
        return Lang.get(switch (f) {
            case ALL -> "gui.kve.recipe.filter.all";
            case VANILLA -> "gui.kve.recipe.filter.vanilla";
            case MODDED -> "gui.kve.recipe.filter.modded";
            case KVE -> "gui.kve.recipe.filter.kve";
            case REMOVED -> "gui.kve.recipe.filter.removed";
        });
    }

    private boolean isRemovable(Entry e) {
        return !e.id.getNamespace().equals("shovel_js");
    }

    private boolean isRemoved(Entry e) {
        return KubeJSExporter.getRemovedRecipes().contains(e.id.toString());
    }

    private void queueRemove(Entry e) {
        if (!ClientPermissionState.canEdit()) return;
        String id = e.id.toString();
        minecraft.setScreen(new KveConfirmScreen(
                confirmed -> {
                    minecraft.setScreen(this);
                    if (confirmed) doQueueRemove(e, id);
                },
                Component.literal(Lang.get("gui.kve.recipe.remove_confirm_title")),
                Component.literal(Lang.get("gui.kve.recipe.remove_confirm_msg", id))));
    }

    private void doQueueRemove(Entry e, String id) {
        com.kve.client.recipe.RecipeRemovalQueue.queueOne(id);
    }

    private void queueRestore(Entry e) {
        if (!ClientPermissionState.canEdit()) return;
        String id = e.id.toString();
        KubeJSExporter.stageUnremoveVanilla(id);
        PendingChangeManager.queue(PendingChange.of(PendingDomain.RECIPES, id, PendingChange.Action.EDIT, false,
                () -> {
                    KubeJSExporter.commitRemoved();
                    KveChangeLog.record(KveChangeLog.Log.RECIPES, PendingChange.Action.EDIT, id);
                },
                () -> KubeJSExporter.discardStagedUnremoveVanilla(id)));
    }

    private RecipeData toRecipeData(Entry e) {
        return com.kve.client.recipe.ExistingRecipeLoader.toRecipeData(e.id, e.recipe, e.output);
    }

    private void pick(Entry e) {
        RecipeData data = toRecipeData(e);
        boolean isOurs = e.id.getNamespace().equals("shovel_js");
        String typeId = isOurs ? null : e.id.toString();
        com.kve.client.gui.studio.KveStudioShell.openRecipe(parent, data, typeId);
    }

    private void openGroup(ItemGroup g) {
        detailMode = true;
        detailGroup = g;
        init();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        String title = detailMode
                ? detailGroup.representative.output.getHoverName().getString() + " (" + detailGroup.entries.size() + ")"
                : Lang.get("gui.kve.recipe.available") + " (" + groups.size() + ")";
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, null);
        int titleX = L + 4;
        if (detailMode) {
            String backLabel = "<- " + Lang.get("gui.kve.btn.back");
            titleX = L + 6 + KveGuiStyle.btnWidth(backLabel) + KveGuiStyle.BTN_GAP;
        }
        KveGuiStyle.drawTextSafe(g, title, titleX, T + 3, L + GUI_W - 4 - titleX, KveGuiStyle.ACCENT_GOLD);

        SourceFilter[] filters = SourceFilter.values();
        for (int i = 0; i < filterButtons.size() && i < filters.length; i++) {
            boolean active = filters[i] == sourceFilter;
            filterButtons.get(i).setMessage(Component.literal((active ? "»" : " ") + filterLabel(filters[i])));
        }

        boolean empty = detailMode ? detailGroup.entries.isEmpty() : groups.isEmpty();
        int msgMaxW = GUI_W - 12;
        if (!inWorld) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.need_world"), L + 6, T + 42, msgMaxW, KveGuiStyle.th(0xFF8899CC));
            KveGuiStyle.drawTextSafe(g, "", L + 6, T + 53, msgMaxW, KveGuiStyle.th(0xFF8899CC));
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.need_world2"), L + 6, T + 70, msgMaxW, KveGuiStyle.th(0xFF556677));
        } else if (empty) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.none_found"), L + 6, T + 42, msgMaxW, KveGuiStyle.th(0xFF8899CC));
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.total") + totalRecipeCount, L + 6, T + 54, msgMaxW, KveGuiStyle.th(0xFF556677));
        }

        int rowRight = L + GUI_W - 12;
        int scrollTarget = scrollBar.getScroll();
        if (Math.abs(smoothScroll - scrollTarget) < 0.02) smoothScroll = scrollTarget;
        else smoothScroll += (scrollTarget - smoothScroll) * 0.35;

        List<Entry> detailList = detailMode ? detailGroup.entries : null;
        int rowCount = detailMode ? detailList.size() : groups.size();

        int baseRow = scrollBaseRow();
        int pixelShift = scrollPixelShift();
        int listBottom = listY + ROWS_VISIBLE * ROW_H;

        g.enableScissor(L + 4, listY, rowRight, listBottom);
        for (int i = -1; i <= ROWS_VISIBLE; i++) {
            int idx = i + baseRow;
            if (idx < 0 || idx >= rowCount) continue;
            int ry = listY + i * ROW_H - pixelShift;
            if (ry + ROW_H <= listY || ry >= listBottom) continue;
            boolean hov = mx >= L + 4 && mx < rowRight && my >= ry && my < ry + ROW_H - 1
                    && my >= listY && my < listBottom;

            if (!detailMode) {
                ItemGroup grp = groups.get(idx);
                Entry rep = grp.representative;
                g.fill(L + 4, ry, rowRight, ry + ROW_H - 1, hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
                try {
                    SafeItemRender.render(g, rep.output, L + 6, ry + 1);
                } catch (Throwable ignored) {
                    g.fill(L + 6, ry + 1, L + 22, ry + 17, KveGuiStyle.th(0xFF334455));
                    KveGuiStyle.drawTextSafe(g, "?", L + 11, ry + 5, 12, KveGuiStyle.th(0xFF7BC8E0));
                }
                String name = rep.output.getHoverName().getString();
                KveGuiStyle.drawTextSafe(g, name, L + 26, ry + 2, rowRight - (L + 26), 0xFFFFFFFF);
                String countLine = grp.entries.size() > 1
                        ? Lang.get("gui.kve.recipe.group_count", grp.entries.size())
                        : Lang.get("gui.kve.recipe.group_count_one");
                KveGuiStyle.drawTextSafe(g, countLine, L + 26, ry + 11, rowRight - (L + 26), KveGuiStyle.th(0xFF8899CC));
                continue;
            }

            Entry e = detailList.get(idx);
            boolean removable = isRemovable(e);
            boolean removed = removable && isRemoved(e);
            g.fill(L + 4, ry, rowRight, ry + ROW_H - 1, removed ? 0xFF2A1010 : (hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD));

            try {
                SafeItemRender.render(g, e.output, L + 6, ry + 1);
            } catch (Throwable ignored) {

                g.fill(L + 6, ry + 1, L + 22, ry + 17, KveGuiStyle.th(0xFF334455));
                KveGuiStyle.drawTextSafe(g, "?", L + 11, ry + 5, 12, KveGuiStyle.th(0xFF7BC8E0));
            }

            int nsColor = e.id.getNamespace().equals("minecraft") ? 0xFF7DE0B0
                        : e.id.getNamespace().equals("shovel_js") ? KveGuiStyle.th(0xFFE8C96A)
                        : 0xFFE0B07D;
            String name = e.output.getHoverName().getString();
            int textMaxX = rowRight - (removable ? ROW_ACTION_W : 0);
            KveGuiStyle.drawTextSafe(g, name, L + 26, ry + 2, textMaxX - (L + 26), removed ? 0xFFAA8888 : 0xFFFFFFFF);

            String stationName = com.kve.client.recipe.StationResolver.resolveStationName(e.stationType, e.stationCustomTypeId);
            String subLine = e.id.getNamespace() + " - " + stationName + (removed ? " (" + Lang.get("gui.kve.recipe.removed_tag") + ")" : "");
            KveGuiStyle.drawTextSafe(g, subLine, L + 26, ry + 11, textMaxX - (L + 26), removed ? 0xFFCC7766 : nsColor);

            if (removable) {
                int ax = rowRight - ROW_ACTION_W;
                boolean hovAction = mx >= ax && mx < rowRight && my >= ry && my < ry + ROW_H - 1;
                String glyph = removed ? "\u21BA" : "x";
                int glyphColor = removed ? (hovAction ? KveGuiStyle.TEXT_OK : 0xFF77CC99)
                                          : (hovAction ? KveGuiStyle.TEXT_ERROR : 0xFF995555);
                KveGuiStyle.drawTextSafe(g, glyph, ax + 3, ry + 5, ROW_ACTION_W - 4, glyphColor);
            }
        }
        g.disableScissor();
        scrollBar.render(g, mx, my);

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar.mouseClicked(mx, my, btn)) return true;
        int rowRight = L + GUI_W - 12;
        int rowCount = detailMode ? detailGroup.entries.size() : groups.size();
        int listBottom = listY + ROWS_VISIBLE * ROW_H;
        if (my >= listY && my < listBottom) {
            int baseRow = scrollBaseRow();
            int pixelShift = scrollPixelShift();
            for (int i = -1; i <= ROWS_VISIBLE; i++) {
                int idx = i + baseRow;
                if (idx < 0 || idx >= rowCount) continue;
                int ry = listY + i * ROW_H - pixelShift;
                if (mx >= L + 4 && mx < rowRight && my >= ry && my < ry + ROW_H - 1) {
                    if (!detailMode) {
                        openGroup(groups.get(idx));
                    } else {
                        Entry e = detailGroup.entries.get(idx);
                        if (isRemovable(e) && mx >= rowRight - ROW_ACTION_W) {
                            if (isRemoved(e)) queueRestore(e); else queueRemove(e);
                        } else {
                            pick(e);
                        }
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (scrollBar.mouseDragged(mx, my, btn, dx, dy)) return true;
        return super.mouseDragged(mx, my, btn, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int btn) {
        scrollBar.mouseReleased(mx, my, btn);
        return super.mouseReleased(mx, my, btn);
    }
    @Override public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        scrollBar.mouseScrolled(delta); return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }
}
