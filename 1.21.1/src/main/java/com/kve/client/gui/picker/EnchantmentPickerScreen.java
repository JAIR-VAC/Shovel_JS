package com.kve.client.gui.picker;

import com.kve.client.gui.ListKeyNav;
import org.lwjgl.glfw.GLFW;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.VirtualListWidget;

import com.kve.common.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Searchable list of every registered enchantment (vanilla and modded),
 * used by {@link NbtEditorScreen} to add an enchantment to an ItemStack's
 * NBT without needing to know its exact resource id by heart.
 */
@OnlyIn(Dist.CLIENT)
public class EnchantmentPickerScreen extends Screen {

    private static final int GUI_W = 300;
    private static final int GUI_H = 220;
    private static final int ROWS_VISIBLE = 12;
    private static final int ROW_H = 14;

    private final Screen parent;
    private final Consumer<Holder<Enchantment>> callback;

    private static class Entry {
        final Holder<Enchantment> enchantment;
        final String id;
        final String namespace;
        final String displayName;
        Entry(Holder<Enchantment> e, String id, String namespace, String displayName) {
            this.enchantment = e; this.id = id; this.namespace = namespace; this.displayName = displayName;
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

    public EnchantmentPickerScreen(Screen parent, Consumer<Holder<Enchantment>> callback) {
        super(Lang.c("gui.kve.nbt.enchant.pick"));
        this.parent = parent;
        this.callback = callback;
    }

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        buildList();

        searchBox = new KveScaledEditBox(font, L + 6, T + 18, GUI_W - 12, 14,
                Component.literal(Lang.get("gui.kve.search.item")));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.literal(Lang.get("gui.kve.search.list_hint")));
        searchBox.setResponder(s -> { nav.reset(); searchFilter = s; rebuildFilter(); });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        scrollBar = new ScrollBar(L + GUI_W - 10, T + 38, ROWS_VISIBLE * ROW_H, 0, ROWS_VISIBLE);
        rebuildFilter();

        addRenderableWidget(new KvePlateButton(L + GUI_W - 17, T + 2, 15, 14, Component.literal("X"), b -> onClose()));
    }

    private void buildList() {
        if (!all.isEmpty()) { listIndex.setItems(all); return; }
        List<Entry> vanilla = new ArrayList<>(), modded = new ArrayList<>();
        var conn = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (conn == null) { listIndex.setItems(all); return; }
        var registry = conn.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        registry.holders().forEach(ench -> {
            ResourceLocation key = ench.key().location();
            String display = friendlyName(ench, key);
            Entry e = new Entry(ench, key.toString(), key.getNamespace(), display);
            (key.getNamespace().equals("minecraft") ? vanilla : modded).add(e);
        });
        vanilla.sort(Comparator.comparing(e -> e.displayName));
        modded.sort(Comparator.comparing((Entry e) -> e.namespace).thenComparing(e -> e.displayName));
        all.addAll(vanilla);
        all.addAll(modded);
        listIndex.setItems(all);
    }

    private String friendlyName(Holder<Enchantment> ench, ResourceLocation key) {
        try {
            String translated = ench.value().description().getString();
            if (translated != null && !translated.isEmpty()) return translated;
        } catch (Exception ignored) {}
        return key.getPath();
    }

    private void rebuildFilter() {
        listIndex.filter(searchFilter, null);
        filtered.clear();
        filtered.addAll(listIndex.filtered());
        if (scrollBar != null) scrollBar.setTotals(filtered.size(), ROWS_VISIBLE);
    }

    private void pick(Entry e) {
        callback.accept(e.enchantment);
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.nbt.enchant.pick") + " (" + filtered.size() + ")");

        int listY = T + 38;
        int scroll = scrollBar.getScroll();
        for (int i = 0; i < ROWS_VISIBLE; i++) {
            int idx = i + scroll;
            if (idx >= filtered.size()) break;
            Entry e = filtered.get(idx);
            int ry = listY + i * ROW_H;
            boolean hov = mx >= L + 4 && mx < L + GUI_W - 12 && my >= ry && my < ry + ROW_H - 1;
            hov = hov || nav.isCursor(idx);
            g.fill(L + 4, ry, L + GUI_W - 12, ry + ROW_H - 1, hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);

            int nsColor = e.namespace.equals("minecraft") ? 0xFF7DE0B0 : 0xFFE0B07D;
            KveGuiStyle.drawTextSafe(g, e.namespace + ":", L + 8, ry + 3, GUI_W - 20, nsColor);
            int nameX = L + 8 + font.width(e.namespace + ": ");
            KveGuiStyle.drawTextSafe(g, e.displayName, nameX, ry + 3, (L + GUI_W - 12) - nameX, 0xFFFFFFFF);
        }
        scrollBar.render(g);

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
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
            if (mx >= L + 4 && mx < L + GUI_W - 12 && my >= ry && my < ry + ROW_H - 1) {
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
