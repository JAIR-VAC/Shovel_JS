package com.kve.client.gui.trade;

import com.kve.client.gui.KveConfirmScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveTextInputScreen;
import com.kve.client.gui.ListKeyNav;
import com.kve.client.gui.ScrollBar;

import com.kve.common.Lang;
import com.kve.common.kubejs.TradeExporter.TradeOffer;
import com.kve.common.trade.TradeOfferTemplates;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class TradeTemplateListScreen extends Screen {

    private static final int GUI_W = 300;
    private static final int GUI_H = 226;
    private static final int ROW_H = 22;
    private static final int LIST_Y = 22;
    private static final int VISIBLE_ROWS = 7;

    private final Screen parent;
    private final Consumer<String> onLoad;
    private final List<String> names = new ArrayList<>();
    private final java.util.Map<String, List<TradeOffer>> offersCache = new java.util.HashMap<>();
    private final ListKeyNav nav = new ListKeyNav();
    private ScrollBar scrollBar;
    private int selected = -1;
    private long lastClickAt = 0;
    private int L, T;
    private Button loadBtn, renameBtn, deleteBtn;

    /** {@code onLoad} recibe el nombre elegido; la pantalla vuelve sola a {@code parent} despues. */
    public TradeTemplateListScreen(Screen parent, Consumer<String> onLoad) {
        super(Lang.c("gui.kve.trades.template.list_title"));
        this.parent = parent;
        this.onLoad = onLoad;
    }

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        names.clear();
        names.addAll(TradeOfferTemplates.names());
        offersCache.clear();
        for (String n : names) offersCache.put(n, TradeOfferTemplates.get(n));
        if (selected >= names.size()) selected = names.size() - 1;
        if (selected < 0 && !names.isEmpty()) selected = 0;
        int prevScroll = scrollBar != null ? scrollBar.getScroll() : 0;
        scrollBar = new ScrollBar(L + GUI_W - 10, T + LIST_Y, VISIBLE_ROWS * ROW_H, names.size(), VISIBLE_ROWS);
        scrollBar.setScroll(prevScroll);

        int by = T + GUI_H - 20;
        KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(L + 8, by, L + GUI_W - 8);
        loadBtn = addRenderableWidget(row.add(0, Lang.get("gui.kve.trades.template.create"), b -> loadSelected()));
        loadBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(Lang.get("gui.kve.trades.template.load_tip"))));
        renameBtn = addRenderableWidget(row.add(0, Lang.get("gui.kve.trades.template.rename"), b -> renameSelected()));
        KvePlateButton del = row.add(0, Lang.get("gui.kve.trades.template.delete_btn"), b -> deleteSelected());
        del.withDanger(true);
        deleteBtn = addRenderableWidget(del);
        addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.cancel"), b -> onClose()));
        boolean has = selected >= 0;
        loadBtn.active = has;
        renameBtn.active = has;
        deleteBtn.active = has;
    }

    private String selectedName() { return selected >= 0 && selected < names.size() ? names.get(selected) : null; }

    private void loadSelected() {
        String n = selectedName();
        if (n == null) return;
        onLoad.accept(n);
        minecraft.setScreen(parent);
    }

    private void renameSelected() {
        String old = selectedName();
        if (old == null) return;
        minecraft.setScreen(new KveTextInputScreen(this, Lang.c("gui.kve.trades.template.rename_title"), old,
                Lang.get("gui.kve.trades.template.name_hint"), name -> {
            if (!name.equals(old)) {
                if (TradeOfferTemplates.names().contains(name)) {
                    minecraft.setScreen(new KveConfirmScreen(ok -> {
                        if (ok) TradeOfferTemplates.rename(old, name);
                        selectByName(name);
                        minecraft.setScreen(this);
                    }, Lang.c("gui.kve.trades.template.overwrite_title"),
                            Component.literal(Lang.get("gui.kve.trades.template.overwrite_msg", name))));
                    return;
                }
                TradeOfferTemplates.rename(old, name);
                selectByName(name);
            }
            minecraft.setScreen(this);
        }));
    }

    private void selectByName(String name) {
        List<String> now = new ArrayList<>(TradeOfferTemplates.names());
        selected = now.indexOf(name);
    }

    private void deleteSelected() {
        String n = selectedName();
        if (n == null) return;
        minecraft.setScreen(new KveConfirmScreen(ok -> {
            if (ok) TradeOfferTemplates.delete(n);
            minecraft.setScreen(this);
        }, Lang.c("gui.kve.trades.template.delete_title"),
                Component.literal(Lang.get("gui.kve.trades.template.delete_msg", n))));
    }

    private static ItemStack outputIcon(TradeOffer o) {
        String id = o.outputId == null ? "" : o.outputId.trim();
        if (id.isEmpty()) return ItemStack.EMPTY;
        if (o.outputIsTag || id.startsWith("#")) {
            var members = com.kve.common.recipe.TagIngredient.members(id);
            return members.isEmpty() ? ItemStack.EMPTY : new ItemStack(members.get(0));
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(rl).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private int rowAt(double mx, double my) {
        if (mx < L + 6 || mx >= L + GUI_W - 12 || my < T + LIST_Y) return -1;
        int r = (int) ((my - (T + LIST_Y)) / ROW_H);
        if (r < 0 || r >= VISIBLE_ROWS) return -1;
        int idx = r + scrollBar.getScroll();
        return idx < names.size() ? idx : -1;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, getTitle().getString() + " (" + names.size() + ")");
        if (names.isEmpty()) {
            List<String> lines = KveGuiStyle.wrapTextSafe(font, Lang.get("gui.kve.trades.template.list_empty"), GUI_W - 30);
            for (int i = 0; i < lines.size(); i++) {
                KveGuiStyle.drawCenteredTextSafe(g, lines.get(i), L + GUI_W / 2, T + GUI_H / 2 - 10 + i * 10, KveGuiStyle.th(0xFF778899));
            }
        }
        int scroll = scrollBar.getScroll();
        int rowW = GUI_W - 20;
        for (int r = 0; r < VISIBLE_ROWS; r++) {
            int idx = r + scroll;
            if (idx >= names.size()) break;
            int rx = L + 6, ry = T + LIST_Y + r * ROW_H;
            boolean hov = rowAt(mx, my) == idx;
            boolean sel = idx == selected;
            g.fill(rx, ry, rx + rowW, ry + ROW_H - 2, sel ? KveGuiStyle.th(0xFF2A5E9A) : hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
            if (nav.isCursor(idx)) g.renderOutline(rx, ry, rowW, ROW_H - 2, KveGuiStyle.ACCENT_GOLD);
            String name = names.get(idx);
            List<TradeOffer> offers = offersCache.getOrDefault(name, List.of());
            int icons = Math.min(5, offers.size());
            int iconsX = rx + rowW - 4 - icons * 18;
            for (int i = 0; i < icons; i++) {
                ItemStack s = outputIcon(offers.get(i));
                if (!s.isEmpty()) {
                    try { g.renderItem(s, iconsX + i * 18, ry + 2); } catch (Throwable ignored) {}
                }
            }
            int textW = iconsX - rx - 8;
            KveGuiStyle.drawTextSafe(g, name, rx + 4, ry + 2, textW, sel ? 0xFFFFFFFF : KveGuiStyle.TEXT_PRIMARY);
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.trades.template.offer_count", offers.size()), rx + 4, ry + 11, textW, KveGuiStyle.th(0xFF8899AA));
        }
        scrollBar.render(g);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.trades.template.list_keys"), L + 8, T + GUI_H - 32, GUI_W - 16, KveGuiStyle.th(0xFF556677));
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar.mouseClicked(mx, my, btn)) return true;
        int idx = rowAt(mx, my);
        if (idx >= 0) {
            long now = System.currentTimeMillis();
            boolean dbl = idx == selected && now - lastClickAt < 350;
            selected = idx;
            lastClickAt = now;
            nav.setCursor(idx, names.size(), 1, VISIBLE_ROWS, scrollBar);
            if (dbl) { loadSelected(); return true; }
            init();
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (ListKeyNav.isConfirm(key)) {
            if (nav.active()) selected = nav.cursor();
            loadSelected();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F2) { renameSelected(); return true; }
        if (key == GLFW.GLFW_KEY_DELETE) { deleteSelected(); return true; }
        if (nav.keyPressed(key, names.size(), 1, VISIBLE_ROWS, scrollBar)) {
            if (nav.active()) { selected = nav.cursor(); init(); }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (scrollBar.mouseDragged(mx, my, btn, dx, dy)) return true;
        return super.mouseDragged(mx, my, btn, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int btn) { scrollBar.mouseReleased(mx, my, btn); return super.mouseReleased(mx, my, btn); }
    @Override public boolean mouseScrolled(double mx, double my, double scrollX, double delta) { scrollBar.mouseScrolled(delta); return true; }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
