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
import com.kve.common.customitem.CustomItemDraft;
import com.kve.common.customitem.CustomItemExporter;
import com.kve.common.customitem.CustomItemState;
import com.kve.common.customitem.TextureValidation;
import com.kve.common.kubejs.SemanticValidator;
import com.kve.common.kubejs.KubeJSItemAttrExporter.AttrMod;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class CustomItemCreatorEditorScreen extends Screen implements KveStudioTab {

    /** Columna izquierda (lo basico) de 264 px y a la derecha la columna de tipo y extras. */
    private static final int GUI_W = 440;
    private static final int GUI_H = 236;
    private static final int FIELD_W = 264;
    private static final int PREVIEW_SIZE = 32;
    private static final int CUSTOM_ROWS_VISIBLE = 2;
    private static final int CUSTOM_ROW_H = 11;

    private final Runnable onBack;
    private String currentPath;

    private String draftId;
    private String draftDisplayName;
    private String draftTexturePath;
    private boolean draftTexturePathTrusted;
    private int    draftMaxStack;
    private int    draftMaxDamage;
    private boolean draftFireResistant;
    private int    draftRarityIdx;
    private final Map<String, AttrMod> draftAttributes = new LinkedHashMap<>();
    private int customAttrScroll = 0;
    private boolean draftExported;
    /** Tipo, material, comida, tags... (los campos de la columna derecha). */
    private final CustomItemDraft extra = new CustomItemDraft();

    private ResourceLocation previewTex;
    private int previewTexW = 16, previewTexH = 16;

    private int L, T;
    private int previewX, previewY;
    private String flashMsg = "";
    private long flashAt = 0;

    private boolean hosted = false;
    private int hostedX, hostedY, hostedW, hostedH;

    private Screen ownerTab = null;
    private Screen returnScreen = null;

    public CustomItemCreatorEditorScreen withOwnerTab(Screen s) { this.ownerTab = s; return this; }
    public CustomItemCreatorEditorScreen withReturnScreen(Screen s) { this.returnScreen = s; return this; }

    public CustomItemCreatorEditorScreen(Runnable onBack, CustomItemDraft existingOrNull) {
        super(Lang.c("gui.kve.itemcreator.title"));
        this.onBack = onBack;
        if (existingOrNull != null) {
            currentPath = existingOrNull.path();
            draftId = existingOrNull.fullId();
            draftDisplayName = existingOrNull.displayName;
            draftTexturePath = existingOrNull.texturePath;
            draftTexturePathTrusted = false;
            draftMaxStack = existingOrNull.maxStackSize;
            draftMaxDamage = existingOrNull.maxDamage;
            draftFireResistant = existingOrNull.fireResistant;
            draftRarityIdx = existingOrNull.rarityIdx;
            draftAttributes.putAll(existingOrNull.attributeModifiers);
            draftExported = existingOrNull.exported;
            copyExtras(existingOrNull, extra);
        } else {
            currentPath = "";
            draftId = com.kve.CraftEditorMod.NS + ":";
            draftDisplayName = "";
            draftTexturePath = "";
            draftMaxStack = 64;
            draftMaxDamage = 0;
            draftFireResistant = false;
            draftRarityIdx = 0;
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
        hostedX = x0; hostedY = y0; hostedW = w; hostedH = h;
        L = x0 + Math.max(0, (w - GUI_W) / 2);
        T = y0 + Math.max(0, (h - GUI_H) / 2);
        buildWidgets();
    }

    private void refresh() {
        buildWidgets();
    }

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

        EditBox idBox = field(layout.x(), layout.row(), FIELD_W, draftId, Lang.c("gui.kve.itemcreator.id"));
        idBox.setResponder(s -> { draftId = s; updateIdFieldColor(idBox); });
        idBox.setHint(Lang.c("gui.kve.itemcreator.id_hint"));
        updateIdFieldColor(idBox);
        addRenderableWidget(idBox);

        EditBox nameBox = field(layout.x(), layout.row(), FIELD_W, draftDisplayName, Lang.c("gui.kve.itemcreator.name"));
        nameBox.setResponder(s -> draftDisplayName = s);
        nameBox.setHint(Lang.c("gui.kve.itemcreator.name_hint"));
        addRenderableWidget(nameBox);

        int halfW = (FIELD_W - 4) / 2;
        int rowY = layout.row();
        EditBox stackBox = field(L + 8, rowY, halfW, String.valueOf(draftMaxStack), Lang.c("gui.kve.itemcreator.max_stack"));
        stackBox.setResponder(s -> draftMaxStack = parseIntOr(s, draftMaxStack));
        stackBox.setHint(Lang.c("gui.kve.itemcreator.max_stack_hint"));
        addRenderableWidget(stackBox);
        EditBox dmgBox = field(L + 8 + halfW + 4, rowY, halfW, String.valueOf(draftMaxDamage), Lang.c("gui.kve.itemcreator.max_damage"));
        dmgBox.setResponder(s -> draftMaxDamage = parseIntOr(s, draftMaxDamage));
        dmgBox.setHint(Lang.c("gui.kve.itemcreator.max_damage_hint"));
        addRenderableWidget(dmgBox);

        int rowY2 = layout.row();
        addRenderableWidget(new KvePlateButton(L + 8, rowY2, halfW, 14,
                Component.literal(Lang.get("gui.kve.item.fire_resistant") + " "
                        + Lang.get(draftFireResistant ? "gui.kve.item.fire_yes" : "gui.kve.item.fire_no")),
                b -> { draftFireResistant = !draftFireResistant; refresh(); }));
        String rarLabel = draftRarityIdx > 0 ? CustomItemDraft.RARITIES[draftRarityIdx] : Lang.get("gui.kve.item.no_change");
        addRenderableWidget(new KvePlateButton(L + 8 + halfW + 4, rowY2, halfW, 14,
                Component.literal(Lang.get("gui.kve.item.rarity") + " " + rarLabel),
                b -> { draftRarityIdx = (draftRarityIdx + 1) % CustomItemDraft.RARITIES.length; refresh(); }));

        layout.gap(4);
        int attrBtnW = 60;
        addRenderableWidget(new KvePlateButton(L + 8, layout.row(12), attrBtnW, 12,
                Component.literal("+ " + Lang.get("gui.kve.attr.title")), b -> openAttributePicker()));
        this.attrListTop = layout.y();
        this.attrListX = L + 8 + attrBtnW + 6;
        var attrList = new ArrayList<>(draftAttributes.values());
        for (int i = 0; i < CUSTOM_ROWS_VISIBLE; i++) {
            int idx = i + customAttrScroll;
            if (idx >= attrList.size()) break;
            AttrMod m = attrList.get(idx);
            int ry = attrListTop + i * CUSTOM_ROW_H;
            addRenderableWidget(new KvePlateButton(L + 8 + FIELD_W - 10, ry, 10, CUSTOM_ROW_H - 1,
                    Component.literal("X"), b -> removeCustomAttribute(m.attrId)));
        }
        layout.gap(CUSTOM_ROWS_VISIBLE * CUSTOM_ROW_H);

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
        addRenderableWidget(extrasMenu(x, y, w, "gui.kve.itemcreator.type", CustomItemDraft.TYPES, extra.itemType,
                "gui.kve.itemcreator.type.", t -> {
                    extra.itemType = t;
                    // las herramientas no se apilan
                    if (extra.isTool()) draftMaxStack = 1;
                }));
        y += 18;
        var tier = extrasMenu(x, y, w, "gui.kve.itemcreator.tier", CustomItemDraft.TIERS, extra.toolTier,
                "gui.kve.itemcreator.tier.", t -> extra.toolTier = t);
        tier.active = extra.isTool();
        tier.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c("gui.kve.itemcreator.tier_tip")));
        addRenderableWidget(tier);
        y += 20;
        y = extrasLabel("gui.kve.itemcreator.burn", x + half + 4, y, half);
        addRenderableWidget(extrasToggle(x, y, half, "gui.kve.itemcreator.glow", extra.glow, () -> extra.glow = !extra.glow));
        EditBox burn = field(x + half + 4, y + 1, half, extra.burnTime > 0 ? String.valueOf(extra.burnTime) : "", Lang.c("gui.kve.itemcreator.burn"));
        burn.setResponder(s -> extra.burnTime = s.isBlank() ? 0 : Math.max(0, parseIntOr(s, extra.burnTime)));
        burn.setHint(Lang.c("gui.kve.itemcreator.burn_hint"));
        burn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c("gui.kve.itemcreator.burn_tip")));
        addRenderableWidget(burn);
        y += 18;
        y = extrasLabel("gui.kve.itemcreator.tooltip", x, y, w);
        EditBox tip = field(x, y, w, extra.tooltip, Lang.c("gui.kve.itemcreator.tooltip"));
        tip.setMaxLength(160);
        tip.setResponder(s -> extra.tooltip = s);
        addRenderableWidget(tip);
        y += 16;
        y = extrasLabel("gui.kve.creator.tags", x, y, w);
        EditBox tags = field(x, y, w, extra.tags, Lang.c("gui.kve.creator.tags"));
        tags.setMaxLength(256);
        tags.setResponder(s -> extra.tags = s);
        tags.setHint(Lang.c("gui.kve.creator.tags_hint"));
        addRenderableWidget(tags);
        y += 18;
        addRenderableWidget(extrasToggle(x, y, w, "gui.kve.itemcreator.food", extra.food, () -> extra.food = !extra.food));
        y += 18;
        if (extra.food) {
            extrasLabel("gui.kve.itemcreator.food_hunger", x, y, half);
            y = extrasLabel("gui.kve.itemcreator.food_saturation", x + half + 4, y, half);
            EditBox hunger = field(x, y, half, String.valueOf(extra.foodHunger), Lang.c("gui.kve.itemcreator.food_hunger"));
            hunger.setResponder(s -> extra.foodHunger = Math.max(0, Math.min(20, parseIntOr(s, extra.foodHunger))));
            hunger.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c("gui.kve.itemcreator.food_hunger_tip")));
            addRenderableWidget(hunger);
            EditBox sat = field(x + half + 4, y, half, fmt(extra.foodSaturation), Lang.c("gui.kve.itemcreator.food_saturation"));
            sat.setResponder(s -> { try { extra.foodSaturation = Math.max(0f, Float.parseFloat(s.trim())); } catch (Exception ignored) {} });
            sat.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c("gui.kve.itemcreator.food_saturation_tip")));
            addRenderableWidget(sat);
            y += 16;
            addRenderableWidget(extrasToggle(x, y, half, "gui.kve.itemcreator.food_always", extra.foodAlwaysEdible,
                    () -> extra.foodAlwaysEdible = !extra.foodAlwaysEdible));
            addRenderableWidget(extrasToggle(x + half + 4, y, half, "gui.kve.itemcreator.food_fast", extra.foodFast,
                    () -> extra.foodFast = !extra.foodFast));
        }
    }

    /** Copia los campos de la columna de extras (tipo, comida, tags...) de un borrador a otro. */
    private static void copyExtras(CustomItemDraft from, CustomItemDraft to) {
        to.itemType = from.itemType;
        to.toolTier = from.toolTier;
        to.glow = from.glow;
        to.burnTime = from.burnTime;
        to.tooltip = from.tooltip;
        to.tags = from.tags;
        to.food = from.food;
        to.foodHunger = from.foodHunger;
        to.foodSaturation = from.foodSaturation;
        to.foodAlwaysEdible = from.foodAlwaysEdible;
        to.foodFast = from.foodFast;
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
    private int attrListTop, attrListX;

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

    private void openAttributePicker() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        minecraft.setScreen(new AttributePickerScreen(this, mod -> {
            draftAttributes.put(mod.attrId, mod);
            refresh();
        }, draftAttributes::get));
    }

    private void removeCustomAttribute(String attrId) {
        draftAttributes.remove(attrId);
        int maxScroll = Math.max(0, draftAttributes.size() - CUSTOM_ROWS_VISIBLE);
        customAttrScroll = Math.max(0, Math.min(customAttrScroll, maxScroll));
        refresh();
    }

    private CustomItemDraft buildDraft() {
        CustomItemDraft d = new CustomItemDraft();
        d.id = draftId;
        d.displayName = draftDisplayName;
        d.texturePath = draftTexturePath;
        d.texturePathTrusted = draftTexturePathTrusted;
        d.maxStackSize = draftMaxStack;
        d.maxDamage = draftMaxDamage;
        d.fireResistant = draftFireResistant;
        d.rarityIdx = draftRarityIdx;
        d.attributeModifiers.putAll(draftAttributes);
        copyExtras(extra, d);
        d.exported = draftExported;
        if (!currentPath.isEmpty()) {
            CustomItemDraft existing = CustomItemState.getDraft(currentPath);
            if (existing != null) { d.createdAt = existing.createdAt; }
        }
        return d;
    }

    private void confirmExport() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        CustomItemDraft d = buildDraft();
        if (d.path().isBlank()) {
            d.id = KveGuiStyle.randomId();
            draftId = d.id;
        }
        List<String> errors = CustomItemExporter.validateForExport(d);
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
        CustomItemDraft d = buildDraft();
        if (d.path().isBlank()) {
            d.id = KveGuiStyle.randomId();
            draftId = d.id;
        }
        List<String> errors = d.validateDraft();
        if (!errors.isEmpty()) { flash(String.join("; ", errors)); return; }
        CustomItemState.put(d);
        currentPath = d.path();
        flash(Lang.get("gui.kve.itemcreator.save_done"));
        refresh();
    }

    private void doExport(CustomItemDraft d) {
        List<String> errors = CustomItemExporter.exportDraft(d);
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
        CustomItemState.remove(currentPath);
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
                CustomItemDraft existing = CustomItemState.getDraft(p);
                if (existing != null && !p.equals(currentPath)) bad = true;
            }
        }
        box.setTextColor(bad ? 0xFFFF5555 : 0xFFE0E0E0);
    }

    private static int parseIntOr(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception ex) { return fallback; }
    }

    private static String fmt(double v) {
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
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.itemcreator.title"));

        g.fill(previewX - 1, previewY - 1, previewX + PREVIEW_SIZE + 1, previewY + PREVIEW_SIZE + 1, KveGuiStyle.BORDER_FIELD);
        g.fill(previewX, previewY, previewX + PREVIEW_SIZE, previewY + PREVIEW_SIZE, KveGuiStyle.BG_FIELD);
        if (previewTex != null) {
            g.blit(previewTex, previewX, previewY, PREVIEW_SIZE, PREVIEW_SIZE, 0, 0, previewTexW, previewTexH, previewTexW, previewTexH);
        } else {
            KveGuiStyle.drawTextSafe(g, "?", previewX + PREVIEW_SIZE / 2 - 2, previewY + PREVIEW_SIZE / 2 - 4, KveGuiStyle.TEXT_MUTED);
        }
        KveGuiStyle.drawTextSafe(g, texHintText, texHintX, texHintY, texHintW, KveGuiStyle.TEXT_MUTED);

        var attrList = new ArrayList<>(draftAttributes.values());
        for (int i = 0; i < CUSTOM_ROWS_VISIBLE; i++) {
            int idx = i + customAttrScroll;
            if (idx >= attrList.size()) break;
            AttrMod m = attrList.get(idx);
            int ry = attrListTop + i * CUSTOM_ROW_H;
            String opSuffix = switch (m.op) {
                case ADDITION -> "";
                case MULTIPLY_BASE -> " (%base)";
                case MULTIPLY_TOTAL -> " (%total)";
            };
            String label = m.attrId + " = " + fmt(m.value) + opSuffix;
            KveGuiStyle.drawTextSafe(g, label, attrListX, ry + 1, FIELD_W - 12 - (attrListX - L - 8), KveGuiStyle.TEXT_LABEL);
        }
        if (attrList.size() > CUSTOM_ROWS_VISIBLE) {
            KveGuiStyle.drawTextSafe(g, (customAttrScroll + 1) + "-" +
                    Math.min(attrList.size(), customAttrScroll + CUSTOM_ROWS_VISIBLE) + "/" + attrList.size(),
                    attrListX, attrListTop + CUSTOM_ROWS_VISIBLE * CUSTOM_ROW_H + 1, FIELD_W - 12 - (attrListX - L - 8), KveGuiStyle.th(0xFF556677));
        }

        String badge = !draftExported ? Lang.get("gui.kve.itemcreator.badge.draft")
                : Lang.get("gui.kve.itemcreator.badge.exported");
        int badgeColor = !draftExported ? KveGuiStyle.TEXT_MUTED : KveGuiStyle.TEXT_WARN;
        KveGuiStyle.drawTextSafe(g, badge, L + 8, badgeY, FIELD_W, badgeColor);
        renderExtras(g);

        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 5500) {
            List<String> lines = KveGuiStyle.wrapTextSafe(font, flashMsg, FIELD_W);
            int fy = flashY - (lines.size() - 1) * 10;
            for (String line : lines) { KveGuiStyle.drawTextSafe(g, line, L + 8, fy, FIELD_W, KveGuiStyle.ACCENT_GOLD); fy += 10; }
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

    /** KveStudioTab exige mouseDragged() explicito (no basta con el default heredado de Screen) - ver KveStudioTab#mouseDragged() y el mismo patron ya usado en LootTableEditorScreen. */
    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (mx >= L && mx < L + GUI_W && my >= attrListTop && my < attrListTop + CUSTOM_ROWS_VISIBLE * CUSTOM_ROW_H) {
            int maxScroll = Math.max(0, draftAttributes.size() - CUSTOM_ROWS_VISIBLE);
            customAttrScroll = Math.max(0, Math.min(maxScroll, customAttrScroll - (int) Math.signum(delta)));
            refresh();
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, delta);
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
