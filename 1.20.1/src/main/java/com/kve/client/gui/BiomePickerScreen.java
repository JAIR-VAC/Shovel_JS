package com.kve.client.gui;

import com.kve.common.Lang;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reusable biome picker -
 * in {@code com.kve.client.gui} (not under {@code worldgen}) because it's
 * used from 3 places: {@code WorldGenEditorScreen}'s
 * "Biomas" card (multi-select) plus the retrofitted {@code EventEditorScreen}
 * ENTER_BIOME trigger field and {@code EventConditionEditScreen} BIOME
 * condition field (both single-select, wired via the "..." button next to
 * their existing free-text {@code EditBox}).
 * <p>
 * Two list modes: "Bioma específico" (plain registry ids)
 * and "Tag de bioma" (entries shown with a leading "#", from
 * {@code registryOrThrow(Registries.BIOME).getTagNames()}) - [VERIFICAR
 * CONTRA JAR REAL] mismo caveat que el resto de accessors especulativos de
 * este proyecto, no confirmado contra el mapping exacto de Forge 1.20.1 que
 * usa este proyecto.
 * <p>
 * CAVEAT real: biomas son un dynamic registry, solo
 * disponible con un mundo cargado. Sin mundo (o en el remoto caso de un
 * dedicated server sin integrated server local - mismo límite que
 * {@code StructureFrequencyExporter#resolveAccess()} ya documenta para
 * structure sets), esta pantalla no puede listar nada y cae a un mensaje +
 * un {@code EditBox} de id manual, en vez de una lista vacía silenciosa.
 * <p>
 * {@link VirtualListWidget}
 * (orden: Recipe -&gt; Item -&gt; Mob -&gt; Enchantment -&gt; Effect -&gt;
 * Fluid -&gt; Biome). Entries acá son {@code String} planos (ids de bioma o,
 * en modo tag, el string ya prefijado con "#" que {@link #refreshSource()}
 * arma) - sin TagMatcher, mismo motivo que Enchantment/Effect/Fluid: no hay
 * forma de confirmar un accessor de tags de bioma contra un jar real en
 * este sandbox, y acá ni siquiera aplicaría igual porque el "modo tag" ya
 * es una lista de ids distinta - un "#texto" en el buscador cae al fallback de substring
 * que {@link VirtualListWidget} ya provee para ambos modos.
 */
@OnlyIn(Dist.CLIENT)
public class BiomePickerScreen extends Screen {

    private static final int GUI_W = 260;
    private static final int ROWS_VISIBLE = 9;
    private static final int ROW_H = 14;
    private static final int GUI_H = 34 + ROWS_VISIBLE * ROW_H + 44;

    private final Screen parent;
    private final boolean singleSelect;
    private final Consumer<List<String>> onDone;

    private final Set<String> selected = new LinkedHashSet<>();
    private final List<String> allIds = new ArrayList<>();
    private final List<String> filtered = new ArrayList<>();
    private static final VirtualListWidget.Descriptor<String> LIST_DESCRIPTOR = new VirtualListWidget.Descriptor<>() {
        @Override public String id(String s) { return s; }
        @Override public String displayName(String s) { return null; }
    };
    private final VirtualListWidget<String> listIndex = new VirtualListWidget<>(LIST_DESCRIPTOR);

    private boolean tagMode = false;
    private boolean worldAvailable;

    private int L, T;
    private EditBox searchBox;
    private EditBox manualBox;
    private ScrollBar scrollBar;
    private String filter = "";

    private static final int FALLBACK_LIST_ROWS = 6;
    private ScrollBar selectedScrollBar;

    private int fallbackListY() { return T + 98; }

    public BiomePickerScreen(Screen parent, List<String> initialSelected, boolean singleSelect, Consumer<List<String>> onDone) {
        super(Lang.c("gui.kve.biome.picker.title"));
        this.parent = parent;
        this.singleSelect = singleSelect;
        this.onDone = onDone;
        if (initialSelected != null) this.selected.addAll(initialSelected);
    }

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        refreshSource();

        int titleY = T + 15;
        int modeW = (GUI_W - 12 - 4) / 2;
        addRenderableWidget(new KvePlateButton(L + 6, titleY, modeW, 12,
                Component.literal(Lang.get("gui.kve.biome.picker.mode_specific")),
                b -> { tagMode = false; refreshSource(); init(); }));
        addRenderableWidget(new KvePlateButton(L + 6 + modeW + 4, titleY, modeW, 12,
                Component.literal(Lang.get("gui.kve.biome.picker.mode_tag")),
                b -> { tagMode = true; refreshSource(); init(); }));

        int searchY = titleY + 15;
        if (worldAvailable) {
            searchBox = new KveScaledEditBox(font, L + 6, searchY, GUI_W - 12, 12, Lang.c("gui.kve.event.search_hint"));
            searchBox.setValue(filter);
            searchBox.setHint(Component.literal(Lang.get("gui.kve.search.list_hint")));
            searchBox.setResponder(s -> { filter = s; refreshFiltered(); });
            addRenderableWidget(searchBox);
            setFocused(searchBox);

            int listY = searchY + 16;
            scrollBar = new ScrollBar(L + GUI_W - 14, listY, ROWS_VISIBLE * ROW_H, 0, ROWS_VISIBLE);
            refreshFiltered();
        } else {
            manualBox = new KveScaledEditBox(font, L + 6, searchY + 20, GUI_W - 12, 14, Lang.c("gui.kve.biome.picker.manual_hint"));
            manualBox.setMaxLength(128);
            addRenderableWidget(manualBox);
            setFocused(manualBox);
            addRenderableWidget(new KvePlateButton(L + 6, searchY + 38, GUI_W - 12, 14,
                    Lang.c("gui.kve.biome.picker.manual_add"), b -> {
                        String v = manualBox.getValue().trim();
                        if (v.isEmpty()) return;
                        if (singleSelect) { finish(List.of(v)); return; }
                        selected.add(v);
                        manualBox.setValue("");
                        syncSelectedScroll(true);
                    }));
            if (!singleSelect) {
                selectedScrollBar = new ScrollBar(L + GUI_W - 14, fallbackListY(), FALLBACK_LIST_ROWS * ROW_H,
                        selected.size(), FALLBACK_LIST_ROWS);
            }
        }

        int bottomY = T + GUI_H - 16;
        if (!singleSelect) {
            int halfW = (GUI_W - 12 - 4) / 2;
            addRenderableWidget(new KvePlateButton(L + 6, bottomY, halfW, 12,
                    Lang.c("gui.kve.btn.cancel"), b -> minecraft.setScreen(parent)));
            addRenderableWidget(new KvePlateButton(L + 6 + halfW + 4, bottomY, halfW, 12,
                    Lang.c("gui.kve.confirm.yes"), b -> finish(new ArrayList<>(selected))));
        } else {
            addRenderableWidget(new KvePlateButton(L + 6, bottomY, GUI_W - 12, 12,
                    Lang.c("gui.kve.btn.cancel"), b -> minecraft.setScreen(parent)));
        }
    }

    private void syncSelectedScroll(boolean jumpToEnd) {
        if (selectedScrollBar == null) return;
        selectedScrollBar.setTotals(selected.size(), FALLBACK_LIST_ROWS);
        if (jumpToEnd) selectedScrollBar.setScroll(selected.size());
    }

    private void renderFallbackSelected(GuiGraphics g, int mx, int my) {
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.biome.picker.selected_title", selected.size()),
                L + 6, T + 87, GUI_W - 12, KveGuiStyle.TEXT_LABEL);
        int listY = fallbackListY();
        if (selected.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.biome.picker.selected_empty"), L + 8, listY + 3, GUI_W - 16, KveGuiStyle.TEXT_MUTED);
            return;
        }
        List<String> ids = new ArrayList<>(selected);
        int scroll = selectedScrollBar.getScroll();
        int visible = Math.min(FALLBACK_LIST_ROWS, Math.max(0, ids.size() - scroll));
        for (int i = 0; i < visible; i++) {
            int ry = listY + i * ROW_H;
            boolean hov = KveGuiStyle.isHovered(mx, my, L + 6, ry, GUI_W - 22, ROW_H - 2);
            g.fill(L + 6, ry, L + GUI_W - 16, ry + ROW_H - 2, hov ? KveGuiStyle.th(0xFF14243A) : KveGuiStyle.th(0xFF1A3050));
            KveGuiStyle.drawCheck(g, L + 8, ry + 2, true, hov);
            KveGuiStyle.drawTextSafe(g, ids.get(i + scroll), L + 21, ry + 3, GUI_W - 43, KveGuiStyle.TEXT_PRIMARY);
        }
        selectedScrollBar.render(g);
    }

    private boolean clickFallbackSelected(double mx, double my, int btn) {
        if (selectedScrollBar == null) return false;
        if (selectedScrollBar.mouseClicked(mx, my, btn)) return true;
        List<String> ids = new ArrayList<>(selected);
        int scroll = selectedScrollBar.getScroll();
        int visible = Math.min(FALLBACK_LIST_ROWS, Math.max(0, ids.size() - scroll));
        for (int i = 0; i < visible; i++) {
            int ry = fallbackListY() + i * ROW_H;
            if (mx >= L + 6 && mx < L + GUI_W - 16 && my >= ry && my < ry + ROW_H - 2) {
                selected.remove(ids.get(i + scroll));
                syncSelectedScroll(false);
                return true;
            }
        }
        return false;
    }

    private void refreshSource() {
        allIds.clear();
        RegistryAccess access = resolveAccess();
        worldAvailable = access != null;
        if (access == null) return;
        try {
            var registry = access.registryOrThrow(Registries.BIOME);
            if (tagMode) {
                registry.getTagNames().forEach(tag -> allIds.add("#" + tag.location()));
            } else {
                registry.keySet().forEach(k -> allIds.add(k.toString()));
            }
        } catch (Exception ex) {
            worldAvailable = false;
        }
        java.util.Collections.sort(allIds);
        listIndex.setItems(allIds);
    }

    private RegistryAccess resolveAccess() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            try { return mc.getSingleplayerServer().registryAccess(); } catch (Exception ignored) {}
        }
        if (mc.level != null) {
            try { return mc.level.registryAccess(); } catch (Exception ignored) {}
        }
        return null;
    }

    private void refreshFiltered() {
        listIndex.filter(filter, null);
        filtered.clear();
        filtered.addAll(listIndex.filtered());
        if (scrollBar != null) scrollBar.setTotals(filtered.size(), ROWS_VISIBLE);
    }

    private void finish(List<String> result) {
        onDone.accept(result);
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.biome.picker.title"));

        if (!worldAvailable) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.biome.picker.no_world"), L + 6, T + 36, GUI_W - 12, KveGuiStyle.TEXT_MUTED);
            if (!singleSelect) renderFallbackSelected(g, mx, my);
            super.render(g, mx, my, pt);
            return;
        }

        int listY = T + 30 + 16;
        int scroll = scrollBar.getScroll();
        int visible = Math.min(ROWS_VISIBLE, Math.max(0, filtered.size() - scroll));
        for (int i = 0; i < visible; i++) {
            String id = filtered.get(i + scroll);
            int ry = listY + i * ROW_H;
            boolean sel = selected.contains(id);
            boolean hov = KveGuiStyle.isHovered(mx, my, L + 6, ry, GUI_W - 22, ROW_H - 2);
            g.fill(L + 6, ry, L + GUI_W - 16, ry + ROW_H - 2, sel ? KveGuiStyle.th(0xFF1A3050) : (hov ? KveGuiStyle.th(0xFF14243A) : KveGuiStyle.th(0xFF0A1220)));
            KveGuiStyle.drawCheck(g, L + 8, ry + 2, sel, hov);
            KveGuiStyle.drawTextSafe(g, id, L + 21, ry + 3, GUI_W - 43,
                    sel ? KveGuiStyle.TEXT_PRIMARY : KveGuiStyle.TEXT_LABEL);
        }
        if (filtered.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.structure.picker.no_results"), L + 8, listY + 4, GUI_W - 16, KveGuiStyle.TEXT_MUTED);
        }
        scrollBar.render(g);

        if (!singleSelect) {
            int tagCount = 0;
            for (String id : selected) if (id.startsWith("#")) tagCount++;
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.biome.picker.selected_summary", selected.size() - tagCount, tagCount),
                    L + 6, listY + ROWS_VISIBLE * ROW_H + 3, GUI_W - 12, KveGuiStyle.TEXT_LABEL);
        }

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (worldAvailable) {
            if (scrollBar.mouseClicked(mx, my, btn)) return true;
            int listY = T + 30 + 16;
            int scroll = scrollBar.getScroll();
            int visible = Math.min(ROWS_VISIBLE, Math.max(0, filtered.size() - scroll));
            for (int i = 0; i < visible; i++) {
                int ry = listY + i * ROW_H;
                if (mx >= L + 6 && mx < L + GUI_W - 16 && my >= ry && my < ry + ROW_H - 2) {
                    String id = filtered.get(i + scroll);
                    if (singleSelect) { finish(List.of(id)); return true; }
                    if (!selected.remove(id)) selected.add(id);
                    return true;
                }
            }
        }
        else if (!singleSelect && clickFallbackSelected(mx, my, btn)) {
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (worldAvailable && scrollBar.mouseDragged(mx, my, b, dx, dy)) return true;
        if (!worldAvailable && selectedScrollBar != null && selectedScrollBar.mouseDragged(mx, my, b, dx, dy)) return true;
        return super.mouseDragged(mx, my, b, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int b) {
        if (worldAvailable) scrollBar.mouseReleased(mx, my, b);
        else if (selectedScrollBar != null) selectedScrollBar.mouseReleased(mx, my, b);
        return super.mouseReleased(mx, my, b);
    }
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (worldAvailable) { scrollBar.mouseScrolled(delta); return true; }
        if (selectedScrollBar != null) { selectedScrollBar.mouseScrolled(delta); return true; }
        return super.mouseScrolled(mx, my, delta);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        return true;
    }
    @Override public boolean isPauseScreen() { return false; }
}
