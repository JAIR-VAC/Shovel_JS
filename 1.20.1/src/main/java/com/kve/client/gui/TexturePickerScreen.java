package com.kve.client.gui;

import com.kve.common.Lang;
import com.kve.common.customitem.ImportRoot;
import com.kve.common.customitem.TextureValidation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * a GUI-drawn file browser, anchored to {@link ImportRoot#root()}
 * (never the real filesystem root - see that class' doc for the traversal
 * guard this screen relies on), listing every {@code .png} the player has
 * dropped into that folder. Same list/scroll/filter shell as
 * {@code NamespacePickerScreen}, simplified (no {@link com.kve.client.gui.VirtualListWidget}
 * word-index - a flat "drop your PNGs here" folder is never going to have
 * enough entries to need it).
 * <p>
 * Only ever reached two ways: the player explicitly set
 * {@code file_picker=ingame} in the config, or {@link com.kve.client.customitem.TexturePicker}
 * fell back here after the native dialog threw. Either way this screen is the only place a picked path has to pass
 * {@link TextureValidation} before the callback ever fires - blocking
 * errors (wrong extension, too large) refuse the pick outright with
 * {@link #flash}; the non-blocking "not a recommended size" check
 * still lets the pick through but warns.
 */
@OnlyIn(Dist.CLIENT)
public class TexturePickerScreen extends Screen {

    private static final int GUI_W = 220;
    private static final int GUI_H = 200;
    private static final int ROWS_VISIBLE = 10;
    private static final int ROW_H = 14;

    private final Screen parent;
    private final Consumer<String> callback;

    private final List<String> all = new ArrayList<>();
    private final List<String> filtered = new ArrayList<>();
    private EditBox searchBox;
    private String searchFilter = "";
    private ScrollBar scrollBar;

    private int L, T;
    private String flashMsg = "";
    private long flashAt = 0;

    /** {@code callback} receives the picked file as a root-relative display string (same shape {@link ImportRoot#listPngFiles()} returns) - {@link com.kve.client.customitem.TexturePicker} is the one that resolves it back to an absolute path via {@link ImportRoot#resolveSafe()}, so this screen never has to hand out anything outside root itself. */
    public TexturePickerScreen(Screen parent, Consumer<String> callback) {
        super(Lang.c("gui.kve.itemcreator.picker.title"));
        this.parent = parent;
        this.callback = callback;
    }

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        all.clear();
        all.addAll(ImportRoot.listPngFiles(ImportRoot.root()));

        searchBox = new KveScaledEditBox(font, L + 6, T + 30, GUI_W - 12, 14,
                Component.literal(Lang.get("gui.kve.itemcreator.picker.search")));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.literal(Lang.get("gui.kve.itemcreator.picker.search")));
        searchBox.setResponder(s -> { searchFilter = s; rebuildFilter(); });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        scrollBar = new ScrollBar(L + GUI_W - 10, T + 50, ROWS_VISIBLE * ROW_H, 0, ROWS_VISIBLE);
        rebuildFilter();

        addRenderableWidget(new KvePlateButton(L + GUI_W / 2 - 40, T + GUI_H - 16, 80, 12, Lang.c("gui.kve.btn.cancel"), b -> onClose()));
    }

    private void rebuildFilter() {
        filtered.clear();
        String needle = searchFilter.trim().toLowerCase(Locale.ROOT);
        for (String s : all) {
            if (needle.isEmpty() || s.toLowerCase(Locale.ROOT).contains(needle)) filtered.add(s);
        }
        if (scrollBar != null) scrollBar.setTotals(filtered.size(), ROWS_VISIBLE);
    }

    /** Validates {@code rel} (blocking checks only - see class doc) and, if it passes, fires the callback and closes; a size warning that isn't blocking still gets flashed first via {@link #pickWithWarning}. */
    private void pick(String rel) {
        Path abs = ImportRoot.resolveSafe(ImportRoot.root(), rel).orElse(null);
        if (abs == null) { flash(Lang.get("gui.kve.itemcreator.picker.not_png")); return; }

        List<String> errors = TextureValidation.validateFile(abs);
        if (!errors.isEmpty()) { flash(String.join("; ", errors)); return; }

        TextureValidation.readDimensions(abs).ifPresent(dims -> {
            if (!TextureValidation.isRecommendedSize(dims[0], dims[1])) {
                flash(Lang.get("gui.kve.itemcreator.picker.size_warning", dims[0], dims[1]));
            }
        });

        callback.accept(rel);
        minecraft.setScreen(parent);
    }

    private void flash(String msg) { flashMsg = msg; flashAt = System.currentTimeMillis(); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.itemcreator.picker.title"));

        String hint = Lang.get("gui.kve.itemcreator.picker.import_hint", ImportRoot.root().toString());
        KveGuiStyle.drawTextSafe(g, hint, L + 6, T + 18, GUI_W - 12, KveGuiStyle.TEXT_MUTED);

        if (all.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.itemcreator.picker.no_files"), L + 6, T + 50, GUI_W - 12, KveGuiStyle.TEXT_MUTED);
        }

        int listY = T + 50;
        int scroll = scrollBar.getScroll();
        for (int i = 0; i < ROWS_VISIBLE; i++) {
            int idx = i + scroll;
            if (idx >= filtered.size()) break;
            String name = filtered.get(idx);
            int ry = listY + i * ROW_H;
            boolean hov = mx >= L + 4 && mx < L + GUI_W - 12 && my >= ry && my < ry + ROW_H - 1;
            g.fill(L + 4, ry, L + GUI_W - 12, ry + ROW_H - 1, hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
            KveGuiStyle.drawTextSafe(g, name, L + 8, ry + 3, (L + GUI_W - 12) - (L + 8), KveGuiStyle.TEXT_PRIMARY);
        }
        scrollBar.render(g);

        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4500) {
            List<String> lines = KveGuiStyle.wrapTextSafe(font, flashMsg, GUI_W - 12);
            int fy = T + GUI_H - 30 - (lines.size() - 1) * 10;
            for (String line : lines) { KveGuiStyle.drawTextSafe(g, line, L + 6, fy, GUI_W - 12, KveGuiStyle.ACCENT_GOLD); fy += 10; }
        }

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar.mouseClicked(mx, my, btn)) return true;
        int listY = T + 50;
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
    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        scrollBar.mouseScrolled(delta); return true;
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        return super.keyPressed(key, scan, mods);
    }

    @Override public boolean isPauseScreen() { return false; }
}
