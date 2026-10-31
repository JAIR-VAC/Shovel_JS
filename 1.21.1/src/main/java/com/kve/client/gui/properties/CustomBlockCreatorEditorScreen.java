package com.kve.client.gui.properties;

import com.kve.client.gui.KveConfirmScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;

import com.kve.common.Lang;
import com.kve.client.customitem.TexturePicker;
import com.kve.client.gui.studio.KveStudioShell;
import com.kve.client.gui.studio.KveStudioTab;
import com.kve.client.permission.ClientPermissionState;
import com.kve.common.customitem.CustomBlockDraft;
import com.kve.common.customitem.CustomBlockExporter;
import com.kve.common.customitem.CustomBlockState;
import com.kve.common.customitem.TextureValidation;
import com.kve.common.kubejs.SemanticValidator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class CustomBlockCreatorEditorScreen extends Screen implements KveStudioTab {

    /** Columna izquierda (lo basico) de 264 px y a la derecha la columna de comportamiento. */
    private static final int GUI_W = 440;
    private static final int GUI_H = 196;
    private static final int FIELD_W = 264;
    private static final int PREVIEW_SIZE = 32;

    private final Runnable onBack;
    private String currentPath;

    private String draftId;
    private String draftDisplayName;
    private String draftTexturePath;
    private boolean draftTexturePathTrusted;
    private String draftHardnessText;
    private String draftResistanceText;
    private boolean draftRequiresTool;
    private int draftLightLevel;
    private boolean draftExported;
    /** Sonido, herramienta, transparencia, tags... (los campos de la columna derecha). */
    private final CustomBlockDraft extra = new CustomBlockDraft();

    private ResourceLocation previewTex;
    private int previewTexW = 16, previewTexH = 16;

    private int L, T;
    private int previewX, previewY;
    private String flashMsg = "";
    private long flashAt = 0;

    private boolean hosted = false;

    private Screen ownerTab = null;
    private Screen returnScreen = null;

    public CustomBlockCreatorEditorScreen withOwnerTab(Screen s) { this.ownerTab = s; return this; }
    public CustomBlockCreatorEditorScreen withReturnScreen(Screen s) { this.returnScreen = s; return this; }

    public CustomBlockCreatorEditorScreen(Runnable onBack, CustomBlockDraft existingOrNull) {
        super(Lang.c("gui.kve.blockcreator.title"));
        this.onBack = onBack;
        if (existingOrNull != null) {
            currentPath = existingOrNull.path();
            draftId = existingOrNull.fullId();
            draftDisplayName = existingOrNull.displayName;
            draftTexturePath = existingOrNull.texturePath;
            draftTexturePathTrusted = false;
            draftHardnessText = existingOrNull.hardness >= 0 ? fmt(existingOrNull.hardness) : "";
            draftResistanceText = existingOrNull.resistance >= 0 ? fmt(existingOrNull.resistance) : "";
            draftRequiresTool = existingOrNull.requiresTool;
            draftLightLevel = existingOrNull.lightLevel;
            draftExported = existingOrNull.exported;
            copyExtras(existingOrNull, extra);
        } else {
            currentPath = "";
            draftId = com.kve.CraftEditorMod.NS + ":";
            draftDisplayName = "";
            draftTexturePath = "";
            draftHardnessText = "";
            draftResistanceText = "";
            draftRequiresTool = false;
            draftLightLevel = -1;
            draftExported = false;
        }
        reloadPreviewTexture();
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

    private void refresh() { buildWidgets(); }

    private void buildWidgets() {
        clearWidgets();
        setFocused(null);

        KveGuiStyle.Layout layout = new KveGuiStyle.Layout(L + 8, T + 18, 18);

        int texRowY = layout.row(PREVIEW_SIZE + 4);
        previewX = L + 8;
        previewY = texRowY;
        int pickBtnX = previewX + PREVIEW_SIZE + 6;
        int pickBtnW = FIELD_W - PREVIEW_SIZE - 6;
        addRenderableWidget(new KvePlateButton(pickBtnX, previewY, pickBtnW, 14,
                Lang.c("gui.kve.itemcreator.picker.choose"), b -> openTexturePicker()));
        String texHint = draftTexturePath.isEmpty()
                ? Lang.get("gui.kve.itemcreator.picker.none_chosen")
                : Path.of(draftTexturePath).getFileName().toString();
        this.texHintY = previewY + 18;
        this.texHintText = texHint;
        this.texHintX = pickBtnX;
        this.texHintW = pickBtnW;

        EditBox idBox = field(layout.x(), layout.row(), FIELD_W, draftId, Lang.c("gui.kve.blockcreator.id"));
        idBox.setResponder(s -> { draftId = s; updateIdFieldColor(idBox); });
        idBox.setHint(Lang.c("gui.kve.blockcreator.id_hint"));
        updateIdFieldColor(idBox);
        addRenderableWidget(idBox);

        EditBox nameBox = field(layout.x(), layout.row(), FIELD_W, draftDisplayName, Lang.c("gui.kve.blockcreator.name"));
        nameBox.setResponder(s -> draftDisplayName = s);
        nameBox.setHint(Lang.c("gui.kve.blockcreator.name_hint"));
        addRenderableWidget(nameBox);

        int thirdW = (FIELD_W - 8) / 3;
        int rowY = layout.row();
        EditBox hardBox = field(L + 8, rowY, thirdW, draftHardnessText, Lang.c("gui.kve.blockcreator.hardness"));
        hardBox.setResponder(s -> draftHardnessText = s);
        hardBox.setHint(Lang.c("gui.kve.blockcreator.hardness_hint"));
        addRenderableWidget(hardBox);
        EditBox resBox = field(L + 8 + thirdW + 4, rowY, thirdW, draftResistanceText, Lang.c("gui.kve.blockcreator.resistance"));
        resBox.setResponder(s -> draftResistanceText = s);
        resBox.setHint(Lang.c("gui.kve.blockcreator.resistance_hint"));
        addRenderableWidget(resBox);
        EditBox lightBox = field(L + 8 + (thirdW + 4) * 2, rowY, thirdW, draftLightLevel >= 0 ? String.valueOf(draftLightLevel) : "", Lang.c("gui.kve.blockcreator.light"));
        lightBox.setResponder(s -> draftLightLevel = s.isBlank() ? -1 : Math.max(-1, Math.min(15, parseIntOr(s, -1))));
        lightBox.setHint(Lang.c("gui.kve.blockcreator.light_hint"));
        addRenderableWidget(lightBox);

        int rowY2 = layout.row(14);
        addRenderableWidget(new KvePlateButton(L + 8, rowY2, FIELD_W, 14,
                Component.literal(Lang.get("gui.kve.blockcreator.requires_tool") + " "
                        + Lang.get(draftRequiresTool ? "gui.kve.item.fire_yes" : "gui.kve.item.fire_no")),
                b -> { draftRequiresTool = !draftRequiresTool; refresh(); }));

        layout.gap(4);
        int badgeY = layout.row(12);
        this.badgeY = badgeY;

        layout.gap(4);
        int btnY = layout.row(14);
        int fourthW = (FIELD_W - 12) / 4;
        addRenderableWidget(new KvePlateButton(L + 8, btnY, fourthW, 14, Lang.c("gui.kve.btn.menu"), b -> goBack()));
        addRenderableWidget(new KvePlateButton(L + 8 + fourthW + 4, btnY, fourthW, 14, Lang.c("gui.kve.btn.save"), b -> save()).withPrimary(true));
        addRenderableWidget(new KvePlateButton(L + 8 + (fourthW + 4) * 2, btnY, fourthW, 14, Lang.c("gui.kve.btn.delete"), b -> delete()).withDanger(true));
        addRenderableWidget(new KvePlateButton(L + 8 + (fourthW + 4) * 3, btnY, fourthW, 14,
                Component.literal(Lang.get("gui.kve.itemcreator.export")), b -> confirmExport()));
        flashY = btnY + 18;
        buildExtras();
    }

    private void buildExtras() {
        extraLabels.clear();
        int x = L + 8 + FIELD_W + 14, w = GUI_W - (8 + FIELD_W + 14) - 8, half = (w - 4) / 2;
        extrasX = x; extrasW = w;
        int y = T + 30;
        addRenderableWidget(extrasMenu(x, y, w, "gui.kve.blockcreator.sound", CustomBlockDraft.SOUNDS, extra.sound,
                "gui.kve.blockcreator.sound.", v -> extra.sound = v));
        y += 18;
        var tool = extrasMenu(x, y, w, "gui.kve.blockcreator.tool", CustomBlockDraft.TOOLS, extra.toolType,
                "gui.kve.blockcreator.tool.", v -> extra.toolType = v);
        tool.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c("gui.kve.blockcreator.tool_tip")));
        addRenderableWidget(tool);
        y += 18;
        var level = extrasMenu(x, y, w, "gui.kve.blockcreator.level", CustomBlockDraft.TOOL_LEVELS, extra.toolLevel,
                "gui.kve.blockcreator.level.", v -> extra.toolLevel = v);
        level.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c("gui.kve.blockcreator.level_tip")));
        addRenderableWidget(level);
        y += 18;
        addRenderableWidget(extrasToggle(x, y, half, "gui.kve.blockcreator.see_through", extra.seeThrough, () -> extra.seeThrough = !extra.seeThrough))
                .setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c("gui.kve.blockcreator.see_through_tip")));
        addRenderableWidget(extrasToggle(x + half + 4, y, half, "gui.kve.blockcreator.no_collision", extra.noCollision, () -> extra.noCollision = !extra.noCollision))
                .setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c("gui.kve.blockcreator.no_collision_tip")));
        y += 18;
        addRenderableWidget(extrasToggle(x, y, half, "gui.kve.blockcreator.slippery", extra.slippery, () -> extra.slippery = !extra.slippery))
                .setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c("gui.kve.blockcreator.slippery_tip")));
        addRenderableWidget(extrasToggle(x + half + 4, y, half, "gui.kve.blockcreator.slow", extra.slow, () -> extra.slow = !extra.slow))
                .setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c("gui.kve.blockcreator.slow_tip")));
        y += 20;
        y = extrasLabel("gui.kve.creator.tags", x, y, w);
        EditBox tags = field(x, y, w, extra.tags, Lang.c("gui.kve.creator.tags"));
        tags.setMaxLength(256);
        tags.setResponder(s -> extra.tags = s);
        tags.setHint(Lang.c("gui.kve.creator.tags_hint"));
        addRenderableWidget(tags);
    }

    private static void copyExtras(CustomBlockDraft from, CustomBlockDraft to) {
        to.sound = from.sound;
        to.toolType = from.toolType;
        to.toolLevel = from.toolLevel;
        to.seeThrough = from.seeThrough;
        to.noCollision = from.noCollision;
        to.slippery = from.slippery;
        to.slow = from.slow;
        to.tags = from.tags;
    }

    /** Etiqueta chica sobre un campo de la columna de extras. */
    private record ExtraLabel(String text, int x, int y, int w) {}
    private final List<ExtraLabel> extraLabels = new ArrayList<>();
    private int extrasX, extrasW;

    private int extrasLabel(String key, int x, int y, int w) {
        extraLabels.add(new ExtraLabel(Lang.get(key), x, y, w));
        return y + 10;
    }

    private KvePlateButton extrasToggle(int x, int y, int w, String key, boolean on, Runnable flip) {
        return new KvePlateButton(x, y, w, 14,
                Component.literal(Lang.get(key) + " " + Lang.get(on ? "gui.kve.item.fire_yes" : "gui.kve.item.fire_no")),
                b -> { flip.run(); refresh(); });
    }

    /** Menu desplegable "Nombre: valor ▾" con una opcion por valor; "" se muestra como la clave {@code prefix + "any"}. */
    private com.kve.client.gui.KveMenuButton extrasMenu(int x, int y, int w, String labelKey, String[] values, String current,
                                                       String prefix, java.util.function.Consumer<String> set) {
        List<com.kve.client.gui.KveMenuButton.Item> items = new ArrayList<>();
        for (String v : values) {
            items.add(new com.kve.client.gui.KveMenuButton.Item(Lang.get(prefix + (v.isEmpty() ? "any" : v)),
                    () -> { set.accept(v); refresh(); }, false, true));
        }
        String cur = current == null ? "" : current;
        return new com.kve.client.gui.KveMenuButton(x, y, w, 14,
                Component.literal(Lang.get(labelKey) + " " + Lang.get(prefix + (cur.isEmpty() ? "any" : cur)) + " \u25BE"), items);
    }

    private void renderExtras(GuiGraphics g) {
        g.fill(extrasX - 7, T + 18, extrasX - 6, T + GUI_H - 6, KveGuiStyle.BORDER_SUBTLE);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.creator.extras"), extrasX, T + 19, extrasW, KveGuiStyle.ACCENT_GOLD);
        for (ExtraLabel l : extraLabels) KveGuiStyle.drawTextSafe(g, l.text(), l.x(), l.y(), l.w(), KveGuiStyle.TEXT_LABEL);
    }

    private int badgeY;
    private int flashY;
    private int texHintX, texHintY, texHintW;
    private String texHintText = "";

    private void openTexturePicker() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        TexturePicker.open(this, picked -> {
            List<String> errors = TextureValidation.validateFile(Path.of(picked));
            if (!errors.isEmpty()) { flash(String.join("; ", errors)); return; }
            draftTexturePath = picked;
            draftTexturePathTrusted = true;
            reloadPreviewTexture();
            TextureValidation.readDimensions(Path.of(picked)).ifPresent(dims -> {
                if (!TextureValidation.isRecommendedSize(dims[0], dims[1])) {
                    flash(Lang.get("gui.kve.itemcreator.picker.size_warning", dims[0], dims[1]));
                }
            });
            refresh();
        });
    }

    private void reloadPreviewTexture() {
        TexturePicker.releasePreview(previewTex);
        previewTex = null;
        previewTexW = previewTexH = 16;
        if (draftTexturePath.isEmpty()) return;
        Path p = Path.of(draftTexturePath);
        previewTex = TexturePicker.loadPreview(p).orElse(null);
        if (previewTex != null) {
            TextureValidation.readDimensions(p).ifPresent(dims -> { previewTexW = dims[0]; previewTexH = dims[1]; });
        }
    }

    private CustomBlockDraft buildDraft() {
        CustomBlockDraft d = new CustomBlockDraft();
        d.id = draftId;
        d.displayName = draftDisplayName;
        d.texturePath = draftTexturePath;
        d.texturePathTrusted = draftTexturePathTrusted;
        d.hardness = parseFloatOr(draftHardnessText, -1f);
        d.resistance = parseFloatOr(draftResistanceText, -1f);
        d.requiresTool = draftRequiresTool;
        d.lightLevel = draftLightLevel;
        copyExtras(extra, d);
        d.exported = draftExported;
        if (!currentPath.isEmpty()) {
            CustomBlockDraft existing = CustomBlockState.getDraft(currentPath);
            if (existing != null) { d.createdAt = existing.createdAt; }
        }
        return d;
    }

    private void confirmExport() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        CustomBlockDraft d = buildDraft();
        if (d.path().isBlank()) {
            d.id = KveGuiStyle.randomId();
            draftId = d.id;
        }
        List<String> errors = CustomBlockExporter.validateForExport(d);
        if (!errors.isEmpty()) { flash(String.join("; ", errors)); return; }

        boolean multiplayer = minecraft != null && minecraft.getSingleplayerServer() == null && minecraft.getConnection() != null;
        String msgKey = multiplayer
                ? "gui.kve.itemcreator.export_confirm_msg"
                : "gui.kve.itemcreator.export_confirm_msg_server";
        minecraft.setScreen(new KveConfirmScreen(
                confirmed -> {
                    minecraft.setScreen(this);
                    if (!confirmed) return;
                    doExport(d);
                },
                Component.literal(Lang.get("gui.kve.itemcreator.export_confirm_title")),
                Component.literal(Lang.get(msgKey))));
    }

    private void save() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        CustomBlockDraft d = buildDraft();
        if (d.path().isBlank()) {
            d.id = KveGuiStyle.randomId();
            draftId = d.id;
        }
        List<String> errors = d.validateDraft();
        if (!errors.isEmpty()) { flash(String.join("; ", errors)); return; }
        CustomBlockState.put(d);
        currentPath = d.path();
        flash(Lang.get("gui.kve.itemcreator.save_done"));
        refresh();
    }

    private void doExport(CustomBlockDraft d) {
        List<String> errors = CustomBlockExporter.exportDraft(d);
        if (!errors.isEmpty()) {
            flash(Lang.get("gui.kve.itemcreator.export_fail") + ": " + String.join("; ", errors));
            return;
        }
        currentPath = d.path();
        draftExported = true;
        flash(Lang.get("gui.kve.itemcreator.export_done"));
        refresh();
    }

    private void delete() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        if (currentPath.isEmpty()) { goBack(); return; }
        CustomBlockState.remove(currentPath);
        goBack();
    }

    private EditBox field(int x, int y, int w, String value, Component narration) {
        EditBox f = new KveScaledEditBox(font, x, y, w, 12, narration);
        f.setMaxLength(96);
        f.setValue(value);
        return f;
    }

    private void updateIdFieldColor(EditBox box) {
        String p = draftId.contains(":") ? draftId.substring(draftId.indexOf(':') + 1) : draftId;
        boolean bad = false;
        if (!p.isBlank()) {
            String fullId = p.isEmpty() ? "" : com.kve.CraftEditorMod.NS + ":" + p;
            if (!SemanticValidator.validateNamespacedIdFormat(fullId, "id").isEmpty()) bad = true;
            else {
                CustomBlockDraft existing = CustomBlockState.getDraft(p);
                if (existing != null && !p.equals(currentPath)) bad = true;
            }
        }
        box.setTextColor(bad ? 0xFFFF5555 : 0xFFE0E0E0);
    }

    private static int parseIntOr(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception ex) { return fallback; }
    }

    private static float parseFloatOr(String s, float fallback) {
        if (s == null || s.isBlank()) return fallback;
        try { return Float.parseFloat(s.trim()); } catch (Exception ex) { return fallback; }
    }

    private static String fmt(float v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.3f", v);
    }

    private void flash(String msg) { flashMsg = msg; flashAt = System.currentTimeMillis(); }

    private void goBack() {
        TexturePicker.releasePreview(previewTex);
        previewTex = null;
        onBack.run();
    }

    @Override public void onClose() { goBack(); }

    @Override
    public void removed() {
        if (KveStudioShell.willReclaim(ownerTab != null ? ownerTab : this, returnScreen)) {
            super.removed();
            return;
        }
        TexturePicker.releasePreview(previewTex);
        previewTex = null;
        super.removed();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!hosted) renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.blockcreator.title"));

        g.fill(previewX - 1, previewY - 1, previewX + PREVIEW_SIZE + 1, previewY + PREVIEW_SIZE + 1, KveGuiStyle.BORDER_FIELD);
        g.fill(previewX, previewY, previewX + PREVIEW_SIZE, previewY + PREVIEW_SIZE, KveGuiStyle.BG_FIELD);
        if (previewTex != null) {
            g.blit(previewTex, previewX, previewY, PREVIEW_SIZE, PREVIEW_SIZE, 0, 0, previewTexW, previewTexH, previewTexW, previewTexH);
        } else {
            KveGuiStyle.drawTextSafe(g, "?", previewX + PREVIEW_SIZE / 2 - 2, previewY + PREVIEW_SIZE / 2 - 4, KveGuiStyle.TEXT_MUTED);
        }
        KveGuiStyle.drawTextSafe(g, texHintText, texHintX, texHintY, texHintW, KveGuiStyle.TEXT_MUTED);

        String badge = !draftExported ? Lang.get("gui.kve.itemcreator.badge.draft")
                : Lang.get("gui.kve.itemcreator.badge.exported");
        int badgeColor = !draftExported ? KveGuiStyle.TEXT_MUTED : KveGuiStyle.TEXT_WARN;
        KveGuiStyle.drawTextSafe(g, badge, L + 8, badgeY, FIELD_W, badgeColor);
        renderExtras(g);

        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 5500) {
            KveGuiStyle.drawTextSafe(g, flashMsg, L + 8, flashY, FIELD_W, KveGuiStyle.ACCENT_GOLD);
        }

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (com.kve.client.gui.KveMenuButton.consumeClick(mx, my, btn)) return true;
        if (mx >= previewX && mx < previewX + PREVIEW_SIZE && my >= previewY && my < previewY + PREVIEW_SIZE) {
            openTexturePicker();
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        if (com.kve.client.gui.EditorKeys.isSave(key, mods)) { save(); return true; }
        if (com.kve.client.gui.EditorKeys.isFocusTab(key, mods)) { super.keyPressed(key, scan, mods); return true; }
        return com.kve.client.gui.EditorKeys.swallow(mods);
    }
    @Override public boolean isPauseScreen() { return false; }
}
