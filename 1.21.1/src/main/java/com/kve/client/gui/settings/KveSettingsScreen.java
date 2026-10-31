package com.kve.client.gui.settings;

import com.kve.client.gui.KveUiScale;

import com.kve.client.gui.recipe.CraftEditorScreen;
import com.kve.client.gui.KveConfirmScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.pack.PackToolsScreen;
import com.kve.client.gui.ScrollBar;

import com.kve.common.Lang;
import com.kve.client.gui.studio.KveStudioShell;
import com.kve.client.gui.studio.KveStudioTab;
import com.kve.client.keybind.KveShellKeybinds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class KveSettingsScreen extends Screen implements KveStudioTab {

    private static final int W = 220;
    private static final int SECTION_HEADER_H = 13;
    private static final int ROW_H = 18;
    private static final int ROW_STEP = ROW_H + 4;
    private static final int CONTENT_START_Y = 22;
    private static final int FOOTER_PAD = 10;

    private static final float[] TEXT_SCALE_PRESETS = {0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.25f};

    private final Screen parent;
    private final Runnable onCerrarHosted;

    // Mismo patron hosted/hostedX/hostedY/hostedW/hostedH que
    // LootTableEditorScreen/TagEditorScreen (ver Javadoc de
    // KveStudioShell#reclaimIfHosted(), hueco #1): sin esto, cualquier
    // refresco interno (cycleTheme()/cycleTextScale()/toggleConfirmDiscards())
    // recalcularia cx/cy contra Screen#width/height (0 mientras esta
    // hosteada) en vez de contra el rectangulo que el shell le asigno.
    private boolean hosted = false;
    private int hostedX, hostedY, hostedW, hostedH;

    private int headerAppearanceY;
    private int headerWorkflowY;
    private int headerKeybindsY;
    private int headerBetaY;
    private int cx, cy, winW, windowH;

    private ScrollBar contentScrollBar;
    private int viewportTop, viewportBottom;

    private KveShellKeybinds.Action capturingAction = null;

    private EditBox fCustomColor;
    private int customColorRowY;
    private String flashMsg = "";
    private long   flashAt  = 0;

    /** Modo standalone (boton de MainMenuScreen, sin cambios de comportamiento). */
    public KveSettingsScreen(Screen parent) {
        super(Lang.c("gui.kve.settings.title"));
        this.parent = parent;
        this.onCerrarHosted = null;
    }

    /** Modo tab del shell - ver KveStudioShell#KveStudioShell(), TabEntry "gui.kve.studio.tab.settings". */
    public KveSettingsScreen(Screen parent, Runnable onCerrarHosted) {
        super(Lang.c("gui.kve.settings.title"));
        this.parent = parent;
        this.onCerrarHosted = onCerrarHosted;
    }

    private static int computeContentHeight() {
        KveGuiStyle.Layout layout = new KveGuiStyle.Layout(0, CONTENT_START_Y, ROW_STEP);
        layout.row(SECTION_HEADER_H);
        layout.row();
        layout.row();
        layout.row();
        layout.row();
        layout.row();
        layout.row();
        layout.gap(KveGuiStyle.SECTION_GAP);
        layout.row(SECTION_HEADER_H);
        layout.row();
        layout.row();
        layout.row();
        layout.row();
        layout.row();
        layout.row();
        layout.row();
        layout.gap(KveGuiStyle.SECTION_GAP);
        layout.row(SECTION_HEADER_H);
        layout.row();
        layout.gap(KveGuiStyle.SECTION_GAP);
        layout.row(SECTION_HEADER_H);
        for (int i = 0; i < KveShellKeybinds.Action.values().length; i++) layout.row();
        int lastRowY = layout.row();
        return lastRowY + ROW_H + FOOTER_PAD;
    }

    @Override
    protected void init() {
        if (KveStudioShell.reclaimIfHosted(this, parent)) return;
        hosted = false;
        buildWidgets();
    }

    // overload requerido por
    // KveStudioTab (mismo contrato que Loot/Tags/etc - ver esa interfaz).
    // A diferencia de esas tabs (que crecen su GUI_W pero mantienen su
    // GUI_H fijo), esta pantalla llena el rectangulo COMPLETO que el shell
    // ofrece (mismo look "panel de pagina completa" que el mockup html le
    // da a Settings, en vez de la ventanita flotante chica del modo
    // standalone) - no tiene contenido scrolleable que se rompa por eso.
    @Override
    public void init(int x0, int y0, int w, int h) {
        if (this.minecraft == null) {
            this.minecraft = Minecraft.getInstance();
            this.font = this.minecraft.font;
        }
        hosted = true;
        hostedX = x0; hostedY = y0; hostedW = w; hostedH = h;
        buildWidgets();
    }

    private void refresh() {
        buildWidgets();
    }

    private void buildWidgets() {
        clearWidgets();
        setFocused(null);

        winW = hosted ? Math.max(W, hostedW) : W;
        int contentH = computeContentHeight();
        windowH = hosted ? hostedH : Math.min(contentH, Math.max(120, height - 24));
        cx = hosted ? hostedX : (width - winW) / 2;
        cy = hosted ? hostedY : (height - windowH) / 2;
        int fullW = winW - 16 - 10;

        viewportTop = cy + CONTENT_START_Y;
        viewportBottom = cy + windowH;
        int viewportH = Math.max(1, viewportBottom - viewportTop);
        int scrollableH = Math.max(0, contentH - CONTENT_START_Y);
        if (contentScrollBar == null) {
            contentScrollBar = new ScrollBar(cx + winW - 8, viewportTop, viewportH, scrollableH, viewportH);
        } else {
            contentScrollBar.setPosition(cx + winW - 8, viewportTop);
            contentScrollBar.setTotals(scrollableH, viewportH);
        }
        int scrollOffset = contentScrollBar.getScroll();

        KveGuiStyle.Layout layout = new KveGuiStyle.Layout(cx + 8, cy + CONTENT_START_Y - scrollOffset, ROW_STEP);

        headerAppearanceY = layout.row(SECTION_HEADER_H);
        int themeY = layout.row();
        if (rowVisible(themeY, ROW_H)) addRenderableWidget(new KvePlateButton(cx + 8, themeY, fullW, ROW_H,
                Component.literal(themeButtonLabel()), b -> cycleTheme()));
        int scaleY = layout.row();
        if (rowVisible(scaleY, ROW_H)) addRenderableWidget(new KvePlateButton(cx + 8, scaleY, fullW, ROW_H,
                Component.literal(textScaleButtonLabel()), b -> cycleTextScale()));
        int uiScaleY = layout.row();
        if (rowVisible(uiScaleY, ROW_H)) {
            KvePlateButton uiScaleBtn = new KvePlateButton(cx + 8, uiScaleY, fullW, ROW_H,
                    Component.literal(uiScaleButtonLabel()), b -> cycleUiScale());
            uiScaleBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c("gui.kve.settings.ui_scale.tooltip")));
            addRenderableWidget(uiScaleBtn);
        }
        // "Hide inventory button": oculta
        // el boton "[CE]" que ToggleButtonOverlay dibuja sobre el inventario/
        // creative para entrar al mod. Default false (visible) - ver
        // KveClientSettings#HIDE_INVENTORY_BUTTON para por que es una key
        // aparte del toggle de sesion (keybind) que ya existia.
        int hideInvY = layout.row();
        if (rowVisible(hideInvY, ROW_H)) addRenderableWidget(new KvePlateButton(cx + 8, hideInvY, fullW, ROW_H,
                Component.literal(hideInventoryButtonLabel()), b -> toggleHideInventoryButton()));
        int hideViewerY = layout.row();
        if (rowVisible(hideViewerY, ROW_H)) addRenderableWidget(new KvePlateButton(cx + 8, hideViewerY, fullW, ROW_H,
                Component.literal(hideViewerButtonLabel()), b -> toggleHideViewerButton()));

        customColorRowY = layout.row();
        if (rowVisible(customColorRowY, ROW_H)) {
            int clearW = 40, applyW = 40, gapW = 3;
            int fieldW = fullW - applyW - clearW - gapW * 2;
            fCustomColor = new KveScaledEditBox(font, cx + 8, customColorRowY + 3, fieldW, 12, Component.empty());
            fCustomColor.setMaxLength(7);
            fCustomColor.setHint(Component.literal("#RRGGBB"));
            String activeHex = KveGuiStyle.customAccentHex();
            if (!activeHex.isEmpty()) fCustomColor.setValue("#" + activeHex);
            addRenderableWidget(fCustomColor);
            addRenderableWidget(new KvePlateButton(cx + 8 + fieldW + gapW, customColorRowY, applyW, ROW_H,
                    Component.literal(Lang.get("gui.kve.settings.custom_accent.apply")), b -> applyCustomColorField()));
            addRenderableWidget(new KvePlateButton(cx + 8 + fieldW + gapW * 2 + applyW, customColorRowY, clearW, ROW_H,
                    Component.literal(Lang.get("gui.kve.settings.custom_accent.clear")), b -> clearCustomColorField()));
        }

        layout.gap(KveGuiStyle.SECTION_GAP);
        headerWorkflowY = layout.row(SECTION_HEADER_H);
        // Volver a la bienvenida (perfil + primeros pasos con su estado).
        int personaY = layout.row();
        if (rowVisible(personaY, ROW_H)) {
            UserPersona persona = UserPersona.current();
            String personaName = persona == null ? Lang.get("gui.kve.persona.none") : Lang.get(persona.labelKey());
            addRenderableWidget(new KvePlateButton(cx + 8, personaY, fullW, ROW_H,
                    Component.literal(Lang.get("gui.kve.settings.persona", personaName)),
                    b -> minecraft.setScreen(new StudioOnboardingScreen(parent))));
        }
        int confirmY = layout.row();
        if (rowVisible(confirmY, ROW_H)) addRenderableWidget(new KvePlateButton(cx + 8, confirmY, fullW, ROW_H,
                Component.literal(confirmDiscardsLabel()), b -> toggleConfirmDiscards()));
        // "Auto reload": el hook YA
        // existia (KveStudioShell#doApplyPending ya lee
        // KveClientSettings.AUTO_RELOAD y manda /reload solo cuando el
        // apply no requirio restart), pero no habia ningun boton en
        // Settings para prenderlo - quedaba atrapado en "siempre false"
        // porque getOrDefault() nunca encontraba la key guardada. Mismo
        // patron exacto que confirmDiscardsLabel()/toggleConfirmDiscards()
        // arriba, default false (ver KveClientSettings#AUTO_RELOAD).
        int autoReloadY = layout.row();
        if (rowVisible(autoReloadY, ROW_H)) addRenderableWidget(new KvePlateButton(cx + 8, autoReloadY, fullW, ROW_H,
                Component.literal(autoReloadLabel()), b -> toggleAutoReload()));
        int powerY = layout.row();
        if (rowVisible(powerY, ROW_H)) {
            KvePlateButton powerBtn = addRenderableWidget(new KvePlateButton(cx + 8, powerY, fullW, ROW_H,
                    Component.literal(powerSaverLabel()), b -> togglePowerSaver()));
            powerBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(Lang.get("gui.kve.settings.power_saver.tip"))));
        }
        int fpsY = layout.row();
        if (rowVisible(fpsY, ROW_H)) {
            KvePlateButton fpsBtn = addRenderableWidget(new KvePlateButton(cx + 8, fpsY, fullW, ROW_H,
                    Component.literal(fpsLimitLabel()), b -> toggleFpsLimit()));
            fpsBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(Lang.get("gui.kve.settings.fps_limit.tip", KveGuiStyle.EDITOR_FPS_CAP))));
        }
        int wasHintY = layout.row();
        if (rowVisible(wasHintY, ROW_H)) addRenderableWidget(new KvePlateButton(cx + 8, wasHintY, fullW, ROW_H,
                Component.literal(showWasHintLabel()), b -> toggleShowWasHint()));
        int inlineIconsY = layout.row();
        if (rowVisible(inlineIconsY, ROW_H)) addRenderableWidget(new KvePlateButton(cx + 8, inlineIconsY, fullW, ROW_H,
                Component.literal(inlineIconsLabel()), b -> toggleInlineIcons()));
        int reviewY = layout.row();
        if (rowVisible(reviewY, ROW_H)) addRenderableWidget(new KvePlateButton(cx + 8, reviewY, fullW, ROW_H,
                Component.literal(reviewBeforeApplyLabel()), b -> toggleReviewBeforeApply()));
        int packToolsY = layout.row();
        if (rowVisible(packToolsY, ROW_H)) addRenderableWidget(new KvePlateButton(cx + 8, packToolsY, fullW, ROW_H,
                Component.literal(Lang.get("gui.kve.pack_tools.settings_btn")), b -> minecraft.setScreen(new PackToolsScreen(minecraft.screen))));

        layout.gap(KveGuiStyle.SECTION_GAP);
        headerBetaY = layout.row(SECTION_HEADER_H);
        int betaEventsY = layout.row();
        if (rowVisible(betaEventsY, ROW_H)) {
            KvePlateButton b = addRenderableWidget(new KvePlateButton(cx + 8, betaEventsY, fullW, ROW_H,
                    Component.literal(betaEventsLabel()), btn -> toggleBetaEvents()));
            b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(Lang.get("gui.kve.settings.beta_events.tip"))));
        }

        layout.gap(KveGuiStyle.SECTION_GAP);
        headerKeybindsY = layout.row(SECTION_HEADER_H);
        for (KveShellKeybinds.Action action : KveShellKeybinds.Action.values()) {
            boolean capturingThis = capturingAction == action;
            String label = capturingThis
                    ? Lang.get(action.labelKey) + ": " + Lang.get("gui.kve.studio.keybind.press_key")
                    : Lang.get(action.labelKey) + ": " + action.displayString();
            int actionY = layout.row();
            if (rowVisible(actionY, ROW_H)) addRenderableWidget(new KvePlateButton(cx + 8, actionY, fullW, ROW_H,
                    Component.literal(label), b -> { capturingAction = action; refresh(); }));
        }
        int resetY = layout.row();
        if (rowVisible(resetY, ROW_H)) addRenderableWidget(new KvePlateButton(cx + 8, resetY, fullW, ROW_H,
                Component.literal(Lang.get("gui.kve.studio.keybind.reset_all")), b -> resetKeybinds()));

    }

    private boolean rowVisible(int y, int h) {
        return y + h > viewportTop && y < viewportBottom;
    }



    private String themeButtonLabel() {
        String custom = KveGuiStyle.customAccentHex();
        if (!custom.isEmpty()) return Lang.get("gui.kve.theme.btn", "#" + custom);
        var t = KveGuiStyle.currentTheme();
        String name = t.name().substring(0, 1) + t.name().substring(1).toLowerCase(java.util.Locale.ROOT);
        return Lang.get("gui.kve.theme.btn", name);
    }

    private void cycleTheme() {
        var themes = KveGuiStyle.Theme.values();
        var saved = com.kve.common.config.KVEConfig.savedAccentColors;
        int idx = 0;
        int savedIdx = saved.indexOf(KveGuiStyle.customAccentHex());
        if (savedIdx >= 0) {
            idx = themes.length + savedIdx;
        } else {
            for (int i = 0; i < themes.length; i++) if (themes[i] == KveGuiStyle.currentTheme()) idx = i;
        }
        int nextIdx = (idx + 1) % (themes.length + saved.size());
        if (nextIdx >= themes.length) {
            String hex = saved.get(nextIdx - themes.length);
            if (KveGuiStyle.applyCustomAccentColor(hex)) {
                com.kve.common.config.KVEConfig.customAccentColor = KveGuiStyle.customAccentHex();
                com.kve.common.config.KVEConfig.save();
            }
            refresh();
            return;
        }
        var next = themes[nextIdx];
        KveGuiStyle.applyTheme(next);
        com.kve.common.config.KVEConfig.theme = next.name().toLowerCase(java.util.Locale.ROOT);
        if (!KveGuiStyle.customAccentHex().isEmpty()) {
            KveGuiStyle.clearCustomAccentColor();
            com.kve.common.config.KVEConfig.customAccentColor = "";
        }
        com.kve.common.config.KVEConfig.save();
        refresh();
    }

    private void applyCustomColorField() {
        String raw = fCustomColor.getValue();
        if (!KveGuiStyle.applyCustomAccentColor(raw)) {
            flash(Lang.get("gui.kve.settings.custom_accent.invalid"));
            return;
        }
        com.kve.common.config.KVEConfig.customAccentColor = KveGuiStyle.customAccentHex();
        com.kve.common.config.KVEConfig.addSavedAccentColor(KveGuiStyle.customAccentHex());
        com.kve.common.config.KVEConfig.save();
        fCustomColor.setValue("#" + KveGuiStyle.customAccentHex());
        flash(Lang.get("gui.kve.settings.custom_accent.applied_saved"));
        refresh();
    }

    private void clearCustomColorField() {
        com.kve.common.config.KVEConfig.savedAccentColors.remove(KveGuiStyle.customAccentHex());
        KveGuiStyle.clearCustomAccentColor();
        com.kve.common.config.KVEConfig.customAccentColor = "";
        com.kve.common.config.KVEConfig.save();
        fCustomColor.setValue("");
        flash(Lang.get("gui.kve.settings.custom_accent.cleared"));
        refresh();
    }

    private void flash(String m) { flashMsg = m; flashAt = System.currentTimeMillis(); }

    private String uiScaleButtonLabel() {
        String mode = com.kve.common.config.KVEConfig.uiScale;
        String value = switch (mode == null ? "auto" : mode) {
            case "auto" -> Lang.get("gui.kve.settings.ui_scale.auto");
            case "game" -> Lang.get("gui.kve.settings.ui_scale.game");
            default -> mode + "x";
        };
        return Lang.get("gui.kve.settings.ui_scale", value);
    }

    private void cycleUiScale() {
        String[] modes = KveUiScale.MODES;
        int idx = java.util.Arrays.asList(modes).indexOf(com.kve.common.config.KVEConfig.uiScale);
        com.kve.common.config.KVEConfig.uiScale = modes[(idx + 1) % modes.length];
        com.kve.common.config.KVEConfig.save();
        KveUiScale.reapply();
        refresh();
    }

    private String textScaleButtonLabel() {
        int pct = Math.round(KveGuiStyle.uiTextScale() * 100f);
        return Lang.get("gui.kve.textscale.btn", pct);
    }

    private void cycleTextScale() {
        float current = KveGuiStyle.uiTextScale();
        int nearest = 0;
        float nearestDist = Float.MAX_VALUE;
        for (int i = 0; i < TEXT_SCALE_PRESETS.length; i++) {
            float dist = Math.abs(TEXT_SCALE_PRESETS[i] - current);
            if (dist < nearestDist) { nearestDist = dist; nearest = i; }
        }
        float next = TEXT_SCALE_PRESETS[(nearest + 1) % TEXT_SCALE_PRESETS.length];
        KveGuiStyle.setUiTextScale(next);
        com.kve.common.config.KVEConfig.uiTextScale = next;
        com.kve.common.config.KVEConfig.save();
        refresh();
    }

    private String hideInventoryButtonLabel() {
        boolean on = KveClientSettings.getOrDefault(KveClientSettings.HIDE_INVENTORY_BUTTON, false);
        return Lang.get("gui.kve.settings.hide_inventory_button") + ": "
                + Lang.get(on ? "gui.kve.item.fire_yes" : "gui.kve.item.fire_no");
    }

    private void toggleHideInventoryButton() {
        boolean current = KveClientSettings.getOrDefault(KveClientSettings.HIDE_INVENTORY_BUTTON, false);
        KveClientSettings.put(KveClientSettings.HIDE_INVENTORY_BUTTON, !current);
        refresh();
    }

    private String betaEventsLabel() {
        return Lang.get("gui.kve.settings.beta_events") + ": "
                + Lang.get(KveClientSettings.betaEventsEnabled() ? "gui.kve.item.fire_yes" : "gui.kve.item.fire_no");
    }

    private void toggleBetaEvents() {
        KveClientSettings.put(KveClientSettings.BETA_EVENTS, !KveClientSettings.betaEventsEnabled());
        if (minecraft != null && minecraft.screen instanceof com.kve.client.gui.studio.KveStudioShell shell) {
            shell.rebuildTabsForBeta();
        }
        refresh();
    }

    private String hideViewerButtonLabel() {
        boolean on = KveClientSettings.getOrDefault(KveClientSettings.HIDE_VIEWER_BUTTON, false);
        return Lang.get("gui.kve.settings.hide_viewer_button") + ": "
                + Lang.get(on ? "gui.kve.item.fire_yes" : "gui.kve.item.fire_no");
    }

    private void toggleHideViewerButton() {
        boolean current = KveClientSettings.getOrDefault(KveClientSettings.HIDE_VIEWER_BUTTON, false);
        KveClientSettings.put(KveClientSettings.HIDE_VIEWER_BUTTON, !current);
        refresh();
    }

    private String confirmDiscardsLabel() {
        boolean on = KveClientSettings.getOrDefault(KveClientSettings.CONFIRM_DISCARDS, false);
        return Lang.get("gui.kve.settings.confirm_discards") + ": "
                + Lang.get(on ? "gui.kve.item.fire_yes" : "gui.kve.item.fire_no");
    }

    private void toggleConfirmDiscards() {
        boolean current = KveClientSettings.getOrDefault(KveClientSettings.CONFIRM_DISCARDS, false);
        KveClientSettings.put(KveClientSettings.CONFIRM_DISCARDS, !current);
        refresh();
    }

    private String powerSaverLabel() {
        boolean on = KveClientSettings.getOrDefault(KveClientSettings.EDITOR_POWER_SAVER, true);
        return Lang.get("gui.kve.settings.power_saver") + ": " + Lang.get(on ? "gui.kve.item.fire_yes" : "gui.kve.item.fire_no");
    }

    private void togglePowerSaver() {
        boolean on = KveClientSettings.getOrDefault(KveClientSettings.EDITOR_POWER_SAVER, true);
        KveClientSettings.put(KveClientSettings.EDITOR_POWER_SAVER, !on);
        refresh();
    }

    private String fpsLimitLabel() {
        boolean on = KveClientSettings.getOrDefault(KveClientSettings.EDITOR_FPS_LIMIT, true);
        return Lang.get("gui.kve.settings.fps_limit", KveGuiStyle.EDITOR_FPS_CAP) + ": " + Lang.get(on ? "gui.kve.item.fire_yes" : "gui.kve.item.fire_no");
    }

    private void toggleFpsLimit() {
        boolean on = KveClientSettings.getOrDefault(KveClientSettings.EDITOR_FPS_LIMIT, true);
        KveClientSettings.put(KveClientSettings.EDITOR_FPS_LIMIT, !on);
        refresh();
    }

    private String autoReloadLabel() {
        boolean on = KveClientSettings.getOrDefault(KveClientSettings.AUTO_RELOAD, false);
        return Lang.get("gui.kve.settings.auto_reload") + ": "
                + Lang.get(on ? "gui.kve.item.fire_yes" : "gui.kve.item.fire_no");
    }

    private void toggleAutoReload() {
        boolean current = KveClientSettings.getOrDefault(KveClientSettings.AUTO_RELOAD, false);
        KveClientSettings.put(KveClientSettings.AUTO_RELOAD, !current);
        refresh();
    }

    private String showWasHintLabel() {
        boolean on = KveClientSettings.getOrDefault(KveClientSettings.SHOW_WAS_HINT, true);
        return Lang.get("gui.kve.settings.was_hint") + ": "
                + Lang.get(on ? "gui.kve.item.fire_yes" : "gui.kve.item.fire_no");
    }

    private void toggleShowWasHint() {
        boolean current = KveClientSettings.getOrDefault(KveClientSettings.SHOW_WAS_HINT, true);
        KveClientSettings.put(KveClientSettings.SHOW_WAS_HINT, !current);
        refresh();
    }

    private String inlineIconsLabel() {
        boolean on = KveClientSettings.getOrDefault(KveClientSettings.INLINE_ICONS, true);
        return Lang.get("gui.kve.settings.inline_icons") + ": "
                + Lang.get(on ? "gui.kve.item.fire_yes" : "gui.kve.item.fire_no");
    }

    private void toggleInlineIcons() {
        boolean current = KveClientSettings.getOrDefault(KveClientSettings.INLINE_ICONS, true);
        KveClientSettings.put(KveClientSettings.INLINE_ICONS, !current);
        refresh();
    }

    private String reviewBeforeApplyLabel() {
        boolean on = KveClientSettings.getOrDefault(KveClientSettings.REVIEW_BEFORE_APPLY, false);
        return Lang.get("gui.kve.settings.review_before_apply") + ": "
                + Lang.get(on ? "gui.kve.item.fire_yes" : "gui.kve.item.fire_no");
    }

    private void toggleReviewBeforeApply() {
        boolean current = KveClientSettings.getOrDefault(KveClientSettings.REVIEW_BEFORE_APPLY, false);
        KveClientSettings.put(KveClientSettings.REVIEW_BEFORE_APPLY, !current);
        refresh();
    }

    @Override
    public void onClose() {
        if (hosted && onCerrarHosted != null) { onCerrarHosted.run(); return; }
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (hosted) {
            int wantW = Math.max(W, hostedW);
            int wantH = hostedH;
            int wantCx = hostedX, wantCy = hostedY;
            if (wantW != winW || wantH != windowH || wantCx != cx || wantCy != cy) refresh();
        }
        if (!hosted) renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, winW, windowH, Lang.get("gui.kve.settings.title"));
        if (rowVisible(headerAppearanceY, SECTION_HEADER_H))
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.settings.section.appearance"), cx + 8, headerAppearanceY, winW - 16, KveGuiStyle.ACCENT_GOLD);
        if (rowVisible(headerWorkflowY, SECTION_HEADER_H))
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.settings.section.workflow"), cx + 8, headerWorkflowY, winW - 16, KveGuiStyle.ACCENT_GOLD);
        if (rowVisible(headerBetaY, SECTION_HEADER_H))
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.settings.section.beta"), cx + 8, headerBetaY, winW - 16, KveGuiStyle.ACCENT_GOLD);
        if (rowVisible(headerKeybindsY, SECTION_HEADER_H))
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.settings.section.keybinds"), cx + 8, headerKeybindsY, winW - 16, KveGuiStyle.ACCENT_GOLD);
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
        if (contentScrollBar != null) contentScrollBar.render(g, mx, my);
        if (flashAt > 0 && System.currentTimeMillis() - flashAt < 5000 && rowVisible(customColorRowY, ROW_H))
            KveGuiStyle.drawTextSafe(g, flashMsg, cx + 8, customColorRowY + ROW_H + 2, winW - 16, KveGuiStyle.TEXT_OK);
    }

    /**
 * "Atajos de teclado... configurables" - mientras
     * {@link #capturingAction} no es null, ESTA pantalla intercepta la
     * PROXIMA tecla como el nuevo binding para esa accion en vez de
     * dejarla pasar a los widgets/ESC normales (asi ESC mismo puede
     * asignarse como atajo si el jugador quisiera, aunque en la practica
     * lo mas util es que ESC cancele la captura - ver abajo). Cualquier
     * OTRA tecla (incluido ESC) durante la captura: se toma como binding y
     * la captura termina; el jugador puede volver a clickear la fila para
     * reintentar si se equivoco.
     */
    @Override
    public boolean keyPressed(int key, int scancode, int mods) {
        if (capturingAction != null) {
            capturingAction.rebind(key, mods);
            KveShellKeybinds.saveToConfig();
            com.kve.common.config.KVEConfig.save();
            capturingAction = null;
            refresh();
            return true;
        }
        return super.keyPressed(key, scancode, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (contentScrollBar != null && contentScrollBar.mouseClicked(mx, my, button)) {
            refresh();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    /** KveStudioTab exige mouseDragged() explicito (no basta con el default heredado de Screen) - ver KveStudioTab#mouseDragged() y el mismo patron ya usado en LootTableEditorScreen. */
    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (contentScrollBar != null && contentScrollBar.mouseDragged(mx, my, button, dx, dy)) {
            refresh();
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (contentScrollBar != null) contentScrollBar.mouseReleased(mx, my, button);
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (contentScrollBar != null) {
            contentScrollBar.scrollBy(-(int) Math.signum(delta) * ROW_STEP);
            refresh();
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, delta);
    }

    private void resetKeybinds() {
        for (KveShellKeybinds.Action a : KveShellKeybinds.Action.values()) a.resetToDefault();
        KveShellKeybinds.saveToConfig();
        com.kve.common.config.KVEConfig.save();
        flash(Lang.get("gui.kve.studio.keybind.reset_all.done"));
        refresh();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
