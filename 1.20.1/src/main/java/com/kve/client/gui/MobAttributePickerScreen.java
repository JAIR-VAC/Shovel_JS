package com.kve.client.gui;

import org.lwjgl.glfw.GLFW;

import com.kve.common.Lang;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class MobAttributePickerScreen extends Screen {

    private static final int GUI_W = 320;
    private static final int GUI_H = 220;
    private static final int ROWS_VISIBLE = 12;
    private static final int ROW_H = 14;

    private final Screen parent;
    private final Consumer<String> onPick;

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
    private EditBox searchBox;
    private String searchFilter = "";
    private ScrollBar scrollBar;
    private final ListKeyNav nav = new ListKeyNav();

    private int L, T;

    public MobAttributePickerScreen(Screen parent, Consumer<String> onPick) {
        super(Lang.c("gui.kve.attr.title"));
        this.parent = parent;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        L = (width  - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        buildAttributeList();

        searchBox = new KveScaledEditBox(font, L + 6, T + 18, GUI_W - 12, 14,
                Component.literal(Lang.get("gui.kve.search.attribute")));
        searchBox.setMaxLength(64);
        searchBox.setResponder(s -> { nav.reset();
            searchFilter = s;
            rebuildFilter();
        });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        scrollBar = new ScrollBar(L + GUI_W - 10, T + 38, ROWS_VISIBLE * ROW_H, 0, ROWS_VISIBLE);

        rebuildFilter();

        addRenderableWidget(new KvePlateButton(L + GUI_W - 17, T + 2, 15, 14, Component.literal("X"), b -> onClose()));
    }

    private void buildAttributeList() {
        all.clear();
        List<Entry> vanilla = new ArrayList<>();
        List<Entry> modded  = new ArrayList<>();

        for (Attribute attr : ForgeRegistries.ATTRIBUTES) {
            ResourceLocation key = ForgeRegistries.ATTRIBUTES.getKey(attr);
            if (key == null) continue;
            String id = key.toString();
            String display = friendlyName(attr, key);
            Entry e = new Entry(id, key.getNamespace(), display);
            if (key.getNamespace().equals("minecraft")) vanilla.add(e);
            else modded.add(e);
        }

        vanilla.sort(Comparator.comparing(e -> e.displayName));
        modded.sort(Comparator.comparing((Entry e) -> e.namespace).thenComparing(e -> e.displayName));

        all.addAll(vanilla);
        all.addAll(modded);
    }

    private String friendlyName(Attribute attr, ResourceLocation key) {
        try {
            String translated = Component.translatable(attr.getDescriptionId()).getString();
            if (translated != null && !translated.isEmpty() && !translated.equals(attr.getDescriptionId()))
                return translated;
        } catch (Exception ignored) {}
        String path = key.getPath();
        String last = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
        String[] parts = last.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private void rebuildFilter() {
        filtered.clear();
        for (Entry e : all) {
            if (SearchFilter.matches(e.id, e.displayName, searchFilter)) {
                filtered.add(e);
            }
        }
        if (scrollBar != null) scrollBar.setTotals(filtered.size(), ROWS_VISIBLE);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.attr.count") + " (" + filtered.size() + ")");

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
            KveGuiStyle.drawTextSafe(g, e.displayName, nameX, ry + 3, (L + GUI_W - 12) - nameX, KveGuiStyle.TEXT_PRIMARY);
        }
        scrollBar.render(g);

        super.render(g, mx, my, pt);
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
                String id = filtered.get(idx).id;
                onPick.accept(id);
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
    @Override public boolean mouseScrolled(double mx, double my, double delta) {
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
            onPick.accept(filtered.get(nav.cursor()).id);
            return true;
        }
        if (nav.keyPressed(key, filtered.size(), 1, ROWS_VISIBLE, scrollBar)) return true;
        return super.keyPressed(key, scan, mods);
    }

    @Override public boolean isPauseScreen() { return false; }
}