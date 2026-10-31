package com.kve.client.gui.properties;

import com.kve.client.gui.EditorKeys;
import com.kve.client.gui.KveConfirmScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;

import com.kve.client.gui.studio.KveStudioShell;
import com.kve.client.gui.studio.KveStudioTab;
import com.kve.client.permission.ClientPermissionState;
import com.kve.common.Lang;
import com.kve.common.customitem.CustomFluidDraft;
import com.kve.common.customitem.CustomFluidExporter;
import com.kve.common.customitem.CustomFluidState;
import com.kve.common.kubejs.SemanticValidator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Creador de fluidos: a la izquierda los fluidos guardados, a la derecha el formulario del elegido.
 * Un fluido se define con nombre, color y si es fino (agua) o espeso (lava); KubeJS arma la textura,
 * el bloque y el cubo a partir de eso, asi que no hay que elegir imagenes.
 */
@OnlyIn(Dist.CLIENT)
public class CustomFluidCreatorScreen extends Screen implements KveStudioTab {

    private static final int GUI_W = 440, GUI_H = 206;
    private static final int LIST_W = 150, ROW_H = 22;
    private static final int SWATCH = 14;
    /** Colores rapidos: agua, lava, veneno, miel, sangre, magia, aceite, nieve. */
    private static final int[] PRESETS = {0x3F76E4, 0xE25822, 0x5DBB3F, 0xE8A723, 0x8A0303, 0x9B4DE0, 0x2B2B2B, 0xEAF4F7};

    private final Runnable onBack;
    private CustomFluidDraft draft = newDraft();
    private String currentPath = "";
    private String colorText = CustomFluidExporter.hex(draft.color);

    private int L, T, listScroll;
    private boolean hosted;
    private final List<CustomFluidDraft> saved = new ArrayList<>();
    private String flashMsg = "";
    private long flashAt;

    private Screen ownerTab = null;
    private Screen returnScreen = null;

    public CustomFluidCreatorScreen withOwnerTab(Screen s) { this.ownerTab = s; return this; }
    public CustomFluidCreatorScreen withReturnScreen(Screen s) { this.returnScreen = s; return this; }

    public CustomFluidCreatorScreen(Runnable onBack) {
        super(Lang.c("gui.kve.fluidcreator.title"));
        this.onBack = onBack;
    }

    private static CustomFluidDraft newDraft() {
        CustomFluidDraft d = new CustomFluidDraft();
        d.id = com.kve.CraftEditorMod.NS + ":";
        return d;
    }

    @Override
    protected void init() {
        if (KveStudioShell.reclaimIfHosted(this, ownerTab != null ? ownerTab : this, returnScreen)) return;
        hosted = false;
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        buildWidgets();
    }

    @Override
    public void init(int x0, int y0, int w, int h) {
        if (this.minecraft == null) {
            this.minecraft = Minecraft.getInstance();
            this.font = this.minecraft.font;
        }
        hosted = true;
        L = x0 + Math.max(0, (w - GUI_W) / 2);
        T = y0 + Math.max(0, (h - GUI_H) / 2);
        buildWidgets();
    }

    private int formX() { return L + LIST_W + 14; }
    private int formW() { return GUI_W - LIST_W - 22; }
    private int listRows() { return Math.max(1, (GUI_H - 50) / ROW_H); }

    private void buildWidgets() {
        clearWidgets();
        setFocused(null);
        CustomFluidState.loadIfNeeded();
        saved.clear();
        saved.addAll(CustomFluidState.getDrafts());
        listScroll = Math.max(0, Math.min(listScroll, saved.size() - listRows()));

        int backW = KveGuiStyle.btnWidth(Lang.get("gui.kve.btn.menu"));
        addRenderableWidget(new KvePlateButton(L + 6, T + GUI_H - 22, backW, 14, Lang.c("gui.kve.btn.menu"), b -> goBack()));
        addRenderableWidget(new KvePlateButton(L + 6 + backW + 4, T + GUI_H - 22, LIST_W - backW - 4, 14,
                Component.literal(Lang.get("gui.kve.fluidcreator.new")), b -> startNew()));

        int x = formX(), w = formW();
        EditBox idBox = field(x, T + 30, w, draft.id);
        idBox.setResponder(s -> { draft.id = s; updateIdColor(idBox); });
        idBox.setHint(Lang.c("gui.kve.fluidcreator.id_hint"));
        updateIdColor(idBox);
        addRenderableWidget(idBox);

        EditBox nameBox = field(x, T + 56, w, draft.displayName);
        nameBox.setResponder(s -> draft.displayName = s);
        nameBox.setHint(Lang.c("gui.kve.fluidcreator.name_hint"));
        addRenderableWidget(nameBox);

        EditBox colorBox = field(x, T + 82, 70, colorText);
        colorBox.setMaxLength(8);
        colorBox.setResponder(s -> {
            colorText = s;
            Integer c = parseColor(s);
            if (c != null) draft.color = c;
        });
        colorBox.setTooltip(Tooltip.create(Lang.c("gui.kve.fluidcreator.color_tip")));
        addRenderableWidget(colorBox);

        int half = (w - 4) / 2;
        addRenderableWidget(new KvePlateButton(x, T + 102, half, 14,
                Component.literal(Lang.get(draft.thick ? "gui.kve.fluidcreator.kind.thick" : "gui.kve.fluidcreator.kind.thin")),
                b -> { draft.thick = !draft.thick; buildWidgets(); }))
                .setTooltip(Tooltip.create(Lang.c("gui.kve.fluidcreator.kind_tip")));
        addRenderableWidget(new KvePlateButton(x + half + 4, T + 102, half, 14,
                Component.literal(Lang.get("gui.kve.fluidcreator.bucket") + " " + Lang.get(draft.bucket ? "gui.kve.item.fire_yes" : "gui.kve.item.fire_no")),
                b -> { draft.bucket = !draft.bucket; buildWidgets(); }));

        EditBox tagsBox = field(x, T + 132, w, draft.tags);
        tagsBox.setMaxLength(256);
        tagsBox.setResponder(s -> draft.tags = s);
        tagsBox.setHint(Lang.c("gui.kve.creator.tags_hint"));
        addRenderableWidget(tagsBox);

        int btnY = T + GUI_H - 22;
        int bw = (w - 8) / 3;
        addRenderableWidget(new KvePlateButton(x, btnY, bw, 14, Lang.c("gui.kve.btn.save"), b -> save()).withPrimary(true));
        KvePlateButton export = new KvePlateButton(x + bw + 4, btnY, bw, 14,
                Component.literal(Lang.get("gui.kve.itemcreator.export")), b -> confirmExport());
        export.setTooltip(Tooltip.create(Lang.c("gui.kve.fluidcreator.export_tip")));
        addRenderableWidget(export);
        addRenderableWidget(new KvePlateButton(x + (bw + 4) * 2, btnY, bw, 14, Lang.c("gui.kve.btn.delete"), b -> delete()).withDanger(true));
    }

    private EditBox field(int x, int y, int w, String value) {
        EditBox f = new KveScaledEditBox(font, x, y, w, 12, Component.empty());
        f.setMaxLength(96);
        f.setValue(value == null ? "" : value);
        return f;
    }

    private static Integer parseColor(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        else if (t.startsWith("0x") || t.startsWith("0X")) t = t.substring(2);
        if (t.length() != 6) return null;
        try { return Integer.parseInt(t, 16); } catch (NumberFormatException ex) { return null; }
    }

    private void updateIdColor(EditBox box) {
        String p = draft.path();
        boolean bad = false;
        if (!p.isBlank()) {
            if (!SemanticValidator.validateNamespacedIdFormat(draft.fullId(), "id").isEmpty()) bad = true;
            else if (CustomFluidState.getDraft(p) != null && !p.equals(currentPath)) bad = true;
        }
        box.setTextColor(bad ? 0xFFFF5555 : 0xFFE0E0E0);
    }

    private void startNew() {
        draft = newDraft();
        currentPath = "";
        colorText = CustomFluidExporter.hex(draft.color);
        buildWidgets();
    }

    private void open(CustomFluidDraft d) {
        draft = d.copy();
        draft.id = d.fullId();
        currentPath = d.path();
        colorText = CustomFluidExporter.hex(draft.color);
        buildWidgets();
    }

    private void save() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        if (draft.path().isBlank()) draft.id = KveGuiStyle.randomId();
        List<String> errors = draft.validateDraft();
        if (!errors.isEmpty()) { flash(String.join("; ", errors)); return; }
        if (!currentPath.isEmpty() && !currentPath.equals(draft.path())) CustomFluidState.remove(currentPath);
        CustomFluidState.put(draft.copy());
        currentPath = draft.path();
        flash(Lang.get("gui.kve.itemcreator.save_done"));
        buildWidgets();
    }

    private void confirmExport() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        if (draft.path().isBlank()) draft.id = KveGuiStyle.randomId();
        List<String> errors = CustomFluidExporter.validateForExport(draft);
        if (!errors.isEmpty()) { flash(String.join("; ", errors)); return; }
        CustomFluidDraft d = draft.copy();
        minecraft.setScreen(new KveConfirmScreen(confirmed -> {
            minecraft.setScreen(ownerTab != null ? ownerTab : this);
            if (!confirmed) return;
            List<String> fail = CustomFluidExporter.exportDraft(d);
            if (!fail.isEmpty()) { flash(Lang.get("gui.kve.itemcreator.export_fail") + ": " + String.join("; ", fail)); return; }
            draft.exported = true;
            currentPath = d.path();
            flash(Lang.get("gui.kve.itemcreator.export_done"));
            buildWidgets();
        }, Component.literal(Lang.get("gui.kve.itemcreator.export_confirm_title")),
                Component.literal(Lang.get("gui.kve.fluidcreator.export_confirm_msg"))));
    }

    private void delete() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        if (!currentPath.isEmpty()) CustomFluidState.remove(currentPath);
        startNew();
    }

    private void flash(String msg) { flashMsg = msg; flashAt = System.currentTimeMillis(); }

    private String badge(CustomFluidDraft d) {
        if (!d.exported) return Lang.get("gui.kve.itemcreator.badge.draft");
        try {
            if (BuiltInRegistries.FLUID.containsKey(ResourceLocation.parse(d.fullId()))) return Lang.get("gui.kve.itemcreator.badge.active");
        } catch (Exception ignored) {}
        return Lang.get("gui.kve.itemcreator.badge.exported");
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!hosted) renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.fluidcreator.title"));

        // lista de fluidos guardados
        int lx = L + 6, ly = T + 20;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.fluidcreator.saved", saved.size()), lx, ly - 1, LIST_W, KveGuiStyle.TEXT_LABEL);
        for (int i = 0; i < listRows() && i + listScroll < saved.size(); i++) {
            CustomFluidDraft d = saved.get(i + listScroll);
            int ry = ly + 10 + i * ROW_H;
            boolean sel = d.path().equals(currentPath);
            boolean hov = KveGuiStyle.isHovered(mx, my, lx, ry, LIST_W, ROW_H - 2);
            g.fill(lx, ry, lx + LIST_W, ry + ROW_H - 2, sel ? KveGuiStyle.BORDER_FIELD : hov ? KveGuiStyle.BORDER_SUBTLE : KveGuiStyle.BG_FIELD);
            g.fill(lx + 3, ry + 3, lx + 17, ry + 17, 0xFF000000 | d.color);
            String title = d.displayName.isEmpty() ? d.fullId() : d.displayName;
            KveGuiStyle.drawTextSafe(g, title, lx + 21, ry + 2, LIST_W - 24, KveGuiStyle.TEXT_PRIMARY);
            KveGuiStyle.drawTextSafe(g, badge(d), lx + 21, ry + 11, LIST_W - 24, d.exported ? KveGuiStyle.TEXT_OK : KveGuiStyle.TEXT_MUTED);
        }
        if (saved.isEmpty()) KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.fluidcreator.none"), lx + 2, ly + 14, LIST_W - 4, KveGuiStyle.TEXT_MUTED);

        // formulario
        int x = formX(), w = formW();
        g.fill(x - 7, T + 18, x - 6, T + GUI_H - 6, KveGuiStyle.BORDER_SUBTLE);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.fluidcreator.id"), x, T + 20, w, KveGuiStyle.TEXT_LABEL);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.fluidcreator.name"), x, T + 46, w, KveGuiStyle.TEXT_LABEL);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.fluidcreator.color"), x, T + 72, w, KveGuiStyle.TEXT_LABEL);
        int sx = x + 76;
        g.fill(sx - 1, T + 81, sx + SWATCH + 1, T + 95, KveGuiStyle.BORDER_FIELD);
        g.fill(sx, T + 82, sx + SWATCH, T + 94, 0xFF000000 | draft.color);
        for (int i = 0; i < PRESETS.length; i++) {
            int px = presetX(i);
            boolean hov = KveGuiStyle.isHovered(mx, my, px, T + 83, 10, 10);
            g.fill(px - 1, T + 82, px + 11, T + 94, hov || PRESETS[i] == draft.color ? 0xFFFFFFFF : KveGuiStyle.BORDER_SUBTLE);
            g.fill(px, T + 83, px + 10, T + 93, 0xFF000000 | PRESETS[i]);
        }
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.creator.tags"), x, T + 122, w, KveGuiStyle.TEXT_LABEL);
        String status = !draft.exported ? Lang.get("gui.kve.itemcreator.badge.draft") : Lang.get("gui.kve.itemcreator.badge.exported");
        KveGuiStyle.drawTextSafe(g, status, x, T + 150, w, draft.exported ? KveGuiStyle.TEXT_WARN : KveGuiStyle.TEXT_MUTED);
        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 5500) {
            KveGuiStyle.drawTextSafe(g, flashMsg, x, T + GUI_H - 34, w, KveGuiStyle.ACCENT_GOLD);
        }
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    private int presetX(int i) { return formX() + 76 + SWATCH + 8 + i * 14; }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int lx = L + 6, ly = T + 30;
        for (int i = 0; i < listRows() && i + listScroll < saved.size(); i++) {
            int ry = ly + i * ROW_H;
            if (mx >= lx && mx < lx + LIST_W && my >= ry && my < ry + ROW_H - 2) { open(saved.get(i + listScroll)); return true; }
        }
        for (int i = 0; i < PRESETS.length; i++) {
            int px = presetX(i);
            if (mx >= px && mx < px + 10 && my >= T + 83 && my < T + 93) {
                draft.color = PRESETS[i];
                colorText = CustomFluidExporter.hex(draft.color);
                buildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (mx >= L && mx < L + LIST_W + 8) {
            listScroll = Math.max(0, Math.min(Math.max(0, saved.size() - listRows()), listScroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, delta);
    }

    private void goBack() { onBack.run(); }

    @Override public void onClose() { goBack(); }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        if (EditorKeys.isSave(key, mods)) { save(); return true; }
        if (EditorKeys.isFocusTab(key, mods)) { super.keyPressed(key, scan, mods); return true; }
        return EditorKeys.swallow(mods);
    }

    @Override public boolean isPauseScreen() { return false; }
}
