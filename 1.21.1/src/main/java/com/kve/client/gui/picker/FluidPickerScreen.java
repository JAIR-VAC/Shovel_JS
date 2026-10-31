package com.kve.client.gui.picker;

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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class FluidPickerScreen extends Screen {

    private static final int GUI_W = 260;
    private static final int GUI_H = 220;
    private static final int ROWS_VISIBLE = 12;
    private static final int ROW_H = 14;

    private final Screen parent;
    private final Consumer<String> callback;

    private static class Entry {
        final String id;
        final String namespace;
        final String path;
        final String kind;
        Entry(String id, String namespace, String path) { this(id, namespace, path, null); }
        Entry(String id, String namespace, String path, String kind) { this.id = id; this.namespace = namespace; this.path = path; this.kind = kind; }
    }

    /**
     * modo
     * "quimico" - lista los registros de Mekanism ({@code mekanism:gas},
     * {@code mekanism:slurry}...) en vez de {@link ForgeRegistries#FLUIDS}.
     * Antes un tanque de gas de Mekanism abria este mismo selector de
     * fluidos, que nunca lista un solo gas: elegir "mekanism:hydrogen" era
     * imposible desde la GUI. Vacio = modo fluido de siempre.
     */
    private final List<String> chemicalKinds;

    private static final java.util.regex.Pattern TYPED_ID =
            java.util.regex.Pattern.compile("#?[a-z0-9_.-]+:[a-z0-9_./-]+");

    private KvePlateButton useTypedBtn;

    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> filtered = new ArrayList<>();
    private static final VirtualListWidget.Descriptor<Entry> LIST_DESCRIPTOR = new VirtualListWidget.Descriptor<>() {
        @Override public String id(Entry e) { return e.id; }
        @Override public String displayName(Entry e) { return null; }
    };
    private final VirtualListWidget<Entry> listIndex = new VirtualListWidget<>(LIST_DESCRIPTOR);
    private EditBox searchBox;
    private String searchFilter = "";
    private ScrollBar scrollBar;

    private int L, T;

    public FluidPickerScreen(Screen parent, Consumer<String> callback) {
        this(parent, callback, "");
    }

    /** {@code chemicalSpec}: a {@code SlotDef#chemical}-style value ("gas", "gas|slurry"...); blank = plain Forge fluids. */
    public FluidPickerScreen(Screen parent, Consumer<String> callback, String chemicalSpec) {
        super(Lang.c("gui.kve.nbt.fluid.pick"));
        this.parent = parent;
        this.callback = callback;
        this.chemicalKinds = com.kve.common.recipetype.ChemicalRegistryHelper.parseKinds(chemicalSpec);
    }

    private boolean chemicalMode() { return !chemicalKinds.isEmpty(); }

    private String title() {
        return chemicalMode()
                ? Lang.get("gui.kve.fluid.pick_chemical", String.join(" / ", chemicalKinds))
                : Lang.get("gui.kve.nbt.fluid.pick");
    }

    private String typedId() {
        String s = searchFilter == null ? "" : searchFilter.trim().toLowerCase(java.util.Locale.ROOT);
        return TYPED_ID.matcher(s).matches() ? s : null;
    }

    private void useTyped() {
        String id = typedId();
        if (id == null) return;
        callback.accept(id);
        minecraft.setScreen(parent);
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
        searchBox.setResponder(s -> { searchFilter = s; rebuildFilter(); });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        scrollBar = new ScrollBar(L + GUI_W - 10, T + 38, ROWS_VISIBLE * ROW_H, 0, ROWS_VISIBLE);

        useTypedBtn = new KvePlateButton(L + 6, T + GUI_H - 18, 96, 14,
                Component.literal(Lang.get("gui.kve.fluid.use_typed")), b -> useTyped());
        addRenderableWidget(useTypedBtn);
        rebuildFilter();

        addRenderableWidget(new KvePlateButton(L + GUI_W - 17, T + 2, 15, 14, Component.literal("X"), b -> onClose()));
    }

    private void buildList() {
        if (!all.isEmpty()) { listIndex.setItems(all); return; }
        if (chemicalMode()) {
            for (String kind : chemicalKinds) {
                for (String id : com.kve.common.recipetype.ChemicalRegistryHelper.ids(kind)) {
                    ResourceLocation rl = ResourceLocation.tryParse(id);
                    if (rl != null) all.add(new Entry(id, rl.getNamespace(), rl.getPath(), kind));
                }
            }
            listIndex.setItems(all);
            return;
        }
        List<Entry> vanilla = new ArrayList<>(), modded = new ArrayList<>();
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            if (fluid == Fluids.EMPTY) continue;
            if (!fluid.isSource(fluid.defaultFluidState())) continue;
            ResourceLocation key = BuiltInRegistries.FLUID.getKey(fluid);
            if (key == null) continue;
            Entry e = new Entry(key.toString(), key.getNamespace(), key.getPath());
            (key.getNamespace().equals("minecraft") ? vanilla : modded).add(e);
        }
        vanilla.sort(Comparator.comparing(e -> e.path));
        modded.sort(Comparator.comparing((Entry e) -> e.namespace).thenComparing(e -> e.path));
        all.addAll(vanilla);
        all.addAll(modded);
        listIndex.setItems(all);
    }

    private void rebuildFilter() {
        listIndex.filter(searchFilter, null);
        filtered.clear();
        filtered.addAll(listIndex.filtered());
        if (scrollBar != null) scrollBar.setTotals(filtered.size(), ROWS_VISIBLE);
        if (useTypedBtn != null) useTypedBtn.active = typedId() != null;
    }

    private void pick(Entry e) {
        callback.accept(e.id);
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, title() + " (" + filtered.size() + ")");

        if (filtered.isEmpty() && all.isEmpty()) {
            String msg = chemicalMode() ? Lang.get("gui.kve.fluid.no_chemicals") : Lang.get("gui.kve.nbt.fluid.none_registered");
            int ty = T + 40;
            for (String line : KveGuiStyle.wrapTextSafe(font, msg, GUI_W - 16)) {
                KveGuiStyle.drawTextSafe(g, line, L + 6, ty, GUI_W - 12, KveGuiStyle.TEXT_MUTED);
                ty += 10;
            }
        }

        int listY = T + 38;
        int scroll = scrollBar.getScroll();
        for (int i = 0; i < ROWS_VISIBLE; i++) {
            int idx = i + scroll;
            if (idx >= filtered.size()) break;
            Entry e = filtered.get(idx);
            int ry = listY + i * ROW_H;
            boolean hov = mx >= L + 4 && mx < L + GUI_W - 12 && my >= ry && my < ry + ROW_H - 1;
            g.fill(L + 4, ry, L + GUI_W - 12, ry + ROW_H - 1, hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);

            int nsColor = e.namespace.equals("minecraft") ? 0xFF7DE0B0 : 0xFFE0B07D;
            KveGuiStyle.drawTextSafe(g, e.namespace + ":", L + 8, ry + 3, GUI_W - 20, nsColor);
            int nameX = L + 8 + font.width(e.namespace + ": ");
            int kindW = e.kind == null ? 0 : font.width(e.kind) + 6;
            KveGuiStyle.drawTextSafe(g, e.path, nameX, ry + 3, (L + GUI_W - 12) - nameX - kindW, KveGuiStyle.TEXT_PRIMARY);
            if (e.kind != null && chemicalKinds.size() > 1) {
                KveGuiStyle.drawTextSafe(g, e.kind, L + GUI_W - 14 - kindW + 4, ry + 3, kindW, KveGuiStyle.th(0xFF8899BB));
            }
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

    /** Enter: the only match, else a valid typed id, else the first match - so "mekanism:hydrogen" + Enter works without reaching for the mouse. */
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            if (filtered.size() == 1) { pick(filtered.get(0)); return true; }
            if (typedId() != null) { useTyped(); return true; }
            if (!filtered.isEmpty()) { pick(filtered.get(0)); return true; }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }
}