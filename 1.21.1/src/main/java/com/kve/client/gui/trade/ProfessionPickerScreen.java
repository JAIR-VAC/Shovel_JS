package com.kve.client.gui.trade;

import com.kve.client.gui.ListKeyNav;
import org.lwjgl.glfw.GLFW;

import com.kve.client.gui.picker.EffectPickerScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.mob.MobPickerScreen;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.VirtualListWidget;

import com.kve.common.Lang;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class ProfessionPickerScreen extends Screen {

    private static final int LIST_W = 320;
    private static final int PREVIEW_W = 92;
    private static final int GUI_W = LIST_W + PREVIEW_W;
    private static final int GUI_H = 220;
    private static final int ROWS_VISIBLE = 12;
    private static final int ROW_H = 14;

    private final Screen parent;
    private final Consumer<String> onPick;
    private final String currentId;

    private static class Entry {
        final String id;
        final String namespace;
        final String displayName;
        Entry(String id, String namespace, String displayName) {
            this.id = id; this.namespace = namespace; this.displayName = displayName;
        }
    }

    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> filtered = new ArrayList<>();
    private static final VirtualListWidget.Descriptor<Entry> LIST_DESCRIPTOR = new VirtualListWidget.Descriptor<>() {
        @Override public String id(Entry e) { return e.id; }
        @Override public String displayName(Entry e) { return e.displayName; }
    };
    private final VirtualListWidget<Entry> listIndex = new VirtualListWidget<>(LIST_DESCRIPTOR);
    private EditBox searchBox;
    private String searchFilter = "";
    private ScrollBar scrollBar;
    private final ListKeyNav nav = new ListKeyNav();

    private int L, T;
    private Entry hovered = null;
    private String previewEntityId = null;

    public ProfessionPickerScreen(Screen parent, Consumer<String> onPick) {
        this(parent, null, onPick);
    }

    public ProfessionPickerScreen(Screen parent, String currentId, Consumer<String> onPick) {
        super(Lang.c("gui.kve.profession.title"));
        this.parent = parent;
        this.currentId = currentId;
        this.onPick = onPick;
    }

    private List<String> explicitIds = null;

    public static ProfessionPickerScreen forIds(Screen parent, String currentId, List<String> ids, Consumer<String> onPick) {
        return forIds(parent, currentId, ids, null, onPick);
    }

    /** Igual que {@link #forIds(Screen, String, List, Consumer)}, mostrando {@code entityTypeId} en la vista previa. */
    public static ProfessionPickerScreen forIds(Screen parent, String currentId, List<String> ids, String entityTypeId, Consumer<String> onPick) {
        ProfessionPickerScreen s = new ProfessionPickerScreen(parent, currentId, onPick);
        s.explicitIds = ids == null ? List.of() : ids;
        s.previewEntityId = entityTypeId;
        return s;
    }

    @Override
    protected void init() {
        L = (width  - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        buildProfessionList();

        searchBox = new KveScaledEditBox(font, L + 6, T + 18, LIST_W - 12, 14,
                Component.literal(Lang.get("gui.kve.search.profession")));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.literal(Lang.get("gui.kve.search.list_hint")));
        searchBox.setResponder(s -> { nav.reset();
            searchFilter = s;
            rebuildFilter();
        });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        scrollBar = new ScrollBar(L + LIST_W - 10, T + 38, ROWS_VISIBLE * ROW_H, 0, ROWS_VISIBLE);

        rebuildFilter();
        // Ver Javadoc del constructor (currentId) - misma idea que
        // "resaltar" de arriba, para el caso comun de una lista mas larga
        // que ROWS_VISIBLE: sin esto la fila resaltada podia estar fuera de
        // la pantalla y el jugador tenia que scrollear a ciegas para
        // encontrarla.
        if (currentId != null && !currentId.isBlank()) {
            for (int i = 0; i < filtered.size(); i++) {
                if (currentId.equals(filtered.get(i).id)) {
                    scrollBar.setScroll(Math.max(0, i - ROWS_VISIBLE / 2));
                    break;
                }
            }
        }

        addRenderableWidget(new KvePlateButton(L + GUI_W - 17, T + 2, 15, 14, Component.literal("X"), b -> onClose()));
    }

    private void buildProfessionList() {
        all.clear();
        List<Entry> vanilla = new ArrayList<>();
        List<Entry> modded = new ArrayList<>();

        if (explicitIds != null) {
            all.add(new Entry("", "*", Lang.get("gui.kve.profession.any")));
            for (String id : explicitIds) {
                ResourceLocation key = ResourceLocation.tryParse(id);
                if (key == null) continue;
                modded.add(new Entry(key.toString(), key.getNamespace(), friendlyName(key)));
            }
            modded.sort(Comparator.comparing((Entry e) -> e.namespace).thenComparing(e -> e.displayName));
            all.addAll(modded);
            listIndex.setItems(all);
            return;
        }

        for (VillagerProfession p : BuiltInRegistries.VILLAGER_PROFESSION) {
            ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(p);
            if (key == null) continue;
            String id = key.toString();
            String display = friendlyName(key);
            Entry e = new Entry(id, key.getNamespace(), display);
            if (key.getNamespace().equals("minecraft")) vanilla.add(e);
            else modded.add(e);
        }

        vanilla.sort(Comparator.comparing(e -> e.displayName));
        modded.sort(Comparator.comparing((Entry e) -> e.namespace).thenComparing(e -> e.displayName));

        all.addAll(vanilla);
        all.addAll(modded);
        listIndex.setItems(all);
    }

    private String friendlyName(ResourceLocation key) {
        String translationKey = (explicitIds != null ? "entity." + key.getNamespace() + "." : "entity.minecraft.villager.") + key.getPath();
        try {
            String translated = Component.translatable(translationKey).getString();
            if (translated != null && !translated.isEmpty() && !translated.equals(translationKey)) return translated;
        } catch (Exception ignored) {}
        String path = key.getPath();
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private void rebuildFilter() {
        listIndex.filter(searchFilter, null);
        filtered.clear();
        filtered.addAll(listIndex.filtered());
        if (scrollBar != null) scrollBar.setTotals(filtered.size(), ROWS_VISIBLE);
    }

    private void pick(Entry e) {
        onPick.accept(e.id);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.profession.count") + " (" + filtered.size() + ")");

        int listY = T + 38;
        int scroll = scrollBar.getScroll();
        Entry mouseRow = null, keyRow = null;
        for (int i = 0; i < ROWS_VISIBLE; i++) {
            int idx = i + scroll;
            if (idx >= filtered.size()) break;
            Entry e = filtered.get(idx);
            int ry = listY + i * ROW_H;
            boolean sel = currentId != null && currentId.equals(e.id);
            boolean mouseOver = mx >= L + 4 && mx < L + LIST_W - 12 && my >= ry && my < ry + ROW_H - 1;
            if (mouseOver) mouseRow = e;
            if (nav.isCursor(idx)) keyRow = e;
            boolean hov = mouseOver || nav.isCursor(idx);
            g.fill(L + 4, ry, L + LIST_W - 12, ry + ROW_H - 1,
                    sel ? KveGuiStyle.th(0xFF2A5E9A) : (hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD));

            int nsColor = e.namespace.equals("minecraft") ? 0xFF7DE0B0 : 0xFFE0B07D;
            KveGuiStyle.drawTextSafe(g, e.namespace + ":", L + 8, ry + 3, LIST_W - 20, nsColor);
            int nameX = L + 8 + font.width(e.namespace + ": ");
            KveGuiStyle.drawTextSafe(g, e.displayName, nameX, ry + 3, (L + LIST_W - 12) - nameX,
                    sel ? 0xFFFFFFFF : KveGuiStyle.TEXT_PRIMARY);
        }
        scrollBar.render(g);
        hovered = mouseRow != null ? mouseRow : keyRow;
        drawPreviewPanel(g, mx, my);

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    private Entry currentEntry() {
        if (currentId == null || currentId.isBlank()) return null;
        for (Entry e : all) if (currentId.equals(e.id)) return e;
        return null;
    }

    private void drawPreviewPanel(GuiGraphics g, int mx, int my) {
        int px = L + LIST_W;
        int py = T + 1;
        int pw = PREVIEW_W - 1;
        int ph = GUI_H - 2;
        g.vLine(px, T, T + GUI_H - 1, KveGuiStyle.BORDER_SUBTLE);
        g.fill(px + 1, py, px + pw, py + ph, KveGuiStyle.BG_FIELD);

        Entry shown = hovered != null ? hovered : currentEntry();
        if (shown == null) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.profession.hover_hint"),
                    px + 6, py + ph / 2 - 10, pw - 10, KveGuiStyle.TEXT_MUTED);
            return;
        }

        LivingEntity preview = explicitIds != null
                ? (previewEntityId == null ? null : TradeEntityPreview.entity(previewEntityId))
                : TradeEntityPreview.villager(shown.id);
        int cx = px + pw / 2;
        int cy = py + ph - 60;
        if (preview != null) {
            float size = Math.max(preview.getBbWidth(), preview.getBbHeight());
            int scale = (int) Math.min(45f, Math.max(14f, 40f / Math.max(1f, size)));
            try {
                KveGuiStyle.renderEntityAt(g, cx, cy, scale, cx - mx, cy - 50 - my, preview);
            } catch (Throwable ignored) {
            }
        } else {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.no_preview"), px + 6, cy, pw - 10, KveGuiStyle.TEXT_MUTED);
        }

        KveGuiStyle.drawCenteredTextSafe(g, KveGuiStyle.truncate(font, shown.displayName, pw - 6), cx, py + ph - 42, KveGuiStyle.TEXT_PRIMARY);
        if (!shown.id.isEmpty()) {
            KveGuiStyle.drawCenteredTextSafe(g, KveGuiStyle.truncate(font, shown.id, pw - 6), cx, py + ph - 32, KveGuiStyle.TEXT_MUTED);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar.mouseClicked(mx, my, btn)) return true;
        int listY = T + 38;
        int scroll = scrollBar.getScroll();
        for (int i = 0; i < ROWS_VISIBLE; i++) {
            int idx = i + scroll;
            if (idx >= filtered.size()) break;
            int ry = listY + i * ROW_H;
            if (mx >= L + 4 && mx < L + LIST_W - 12 && my >= ry && my < ry + ROW_H - 1) {
                pick(filtered.get(idx));
                return true;
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

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE && searchBox != null && !searchBox.getValue().isEmpty()) {
            searchBox.setValue("");
            return true;
        }
        if (ListKeyNav.isConfirm(key) && nav.active() && nav.cursor() < filtered.size()) {
            pick(filtered.get(nav.cursor()));
            return true;
        }
        if (nav.keyPressed(key, filtered.size(), 1, ROWS_VISIBLE, scrollBar)) return true;
        return super.keyPressed(key, scan, mods);
    }

    @Override public boolean isPauseScreen() { return false; }
}
