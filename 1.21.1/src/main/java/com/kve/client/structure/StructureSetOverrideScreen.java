package com.kve.client.structure;

import com.kve.common.Lang;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.ScrollBar;
import com.kve.common.structure.StructureFrequencyExporter.StructureSetOverride;
import net.minecraft.client.gui.GuiGraphics;
import com.kve.client.gui.KvePlateButton;
import net.minecraft.client.gui.components.EditBox;
import com.kve.client.gui.KveScaledEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Edits a single {@link StructureSetOverride}: spacing/separation for the
 * whole structure_set, an "entire set banned" toggle, and a per-member
 * ban list (member ids come from {@link com.kve.common.structure.StructureFrequencyExporter#listMemberStructures},
 * i.e. whatever the LIVE registry object actually contains, not a guess).
 * Same self-contained dialog pattern as {@link com.kve.client.gui.mob.MobEquipmentBuildScreen}
 * (one id in, a Consumer callback out, "this" is the parent to return to on close).
 */
@OnlyIn(Dist.CLIENT)
public class StructureSetOverrideScreen extends Screen {

    private static final int GUI_W = 320;
    private static final int GUI_H = 250;
    private static final int ROWS_VISIBLE = 6;
    private static final int ROW_H = 13;

    private final Screen parent;
    private final String structureSetId;
    private List<String> members;
    private Set<String> highlight = Set.of();
    private final Consumer<StructureSetOverride> onConfirm;

    private final int initialSpacing, initialSeparation;
    private final boolean initialSetBanned;
    private final Set<String> initialBanned;

    private boolean draftSetBanned;
    private final Set<String> draftBanned;

    private EditBox spacingBox, separationBox;
    private ScrollBar scrollBar;
    private int L, T;
    private int spacingY, separationY, setBanY, listTop;

    public StructureSetOverrideScreen(Screen parent, String structureSetId, List<String> members,
                                       StructureSetOverride existing, Consumer<StructureSetOverride> onConfirm) {
        super(Lang.c("gui.kve.structfreq.title"));
        this.parent = parent;
        this.structureSetId = structureSetId;
        this.members = members;
        this.onConfirm = onConfirm;

        this.initialSpacing    = existing != null ? existing.spacingOverride : -1;
        this.initialSeparation = existing != null ? existing.separationOverride : -1;
        this.initialSetBanned  = existing != null && !existing.enabled;
        this.initialBanned     = existing != null ? new LinkedHashSet<>(existing.bannedStructures) : new LinkedHashSet<>();
        this.draftSetBanned    = initialSetBanned;
        this.draftBanned       = new LinkedHashSet<>(initialBanned);
    }

    /** Abierta desde el Structure Editor, resalta las estructuras de la plantilla cargada. */
    public StructureSetOverrideScreen withHighlight(Set<String> structureIds) {
        this.highlight = structureIds == null ? Set.of() : structureIds;
        List<String> sorted = new java.util.ArrayList<>(members);
        sorted.sort(java.util.Comparator.comparing((String m) -> !highlight.contains(m)));
        this.members = sorted;
        return this;
    }

    private String rowLabel(String memberId) {
        return (draftBanned.contains(memberId) ? "[X] " : "[ ] ") + (highlight.contains(memberId) ? "\u00bb " : "") + memberId;
    }

    @Override
    protected void init() {
        L = (width  - GUI_W) / 2;
        T = (height - GUI_H) / 2;

        String snapSpacing    = spacingBox    != null ? spacingBox.getValue()    : null;
        String snapSeparation = separationBox != null ? separationBox.getValue() : null;

        clearWidgets();
        setFocused(null);

        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(L + 10, T + 30, 16);

        spacingY = ly.row(14);
        spacingBox = new KveScaledEditBox(font, ly.x() + 90, spacingY, 60, 14, Component.literal("spacing"));
        spacingBox.setMaxLength(7);
        spacingBox.setValue(snapSpacing != null ? snapSpacing : (initialSpacing >= 0 ? String.valueOf(initialSpacing) : ""));
        spacingBox.setHint(Component.literal(Lang.get("gui.kve.structfreq.default_hint")));
        addRenderableWidget(spacingBox);
        setInitialFocus(spacingBox);

        separationY = ly.row(14);
        separationBox = new KveScaledEditBox(font, ly.x() + 90, separationY, 60, 14, Component.literal("separation"));
        separationBox.setMaxLength(7);
        separationBox.setValue(snapSeparation != null ? snapSeparation : (initialSeparation >= 0 ? String.valueOf(initialSeparation) : ""));
        separationBox.setHint(Component.literal(Lang.get("gui.kve.structfreq.default_hint")));
        addRenderableWidget(separationBox);
        ly.gap(4);

        setBanY = ly.row(14);
        addRenderableWidget(new KvePlateButton(ly.x(), setBanY, GUI_W - 20, 14, Component.literal(Lang.get(draftSetBanned ? "gui.kve.structfreq.set_banned_yes" : "gui.kve.structfreq.set_banned_no")),
                b -> { draftSetBanned = !draftSetBanned; init(); }));
        ly.gap(4);

        listTop = ly.row(0);
        if (scrollBar == null) scrollBar = new ScrollBar(L + GUI_W - 16, listTop + 12, ROWS_VISIBLE * ROW_H, members.size(), ROWS_VISIBLE);
        else scrollBar.setTotals(members.size(), ROWS_VISIBLE);

        int scroll = scrollBar.getScroll();
        int visible = Math.min(ROWS_VISIBLE, Math.max(0, members.size() - scroll));
        int rowW = GUI_W - 20 - 12;
        for (int i = 0; i < visible; i++) {
            String memberId = members.get(i + scroll);
            String label = rowLabel(memberId);
            addRenderableWidget(new KvePlateButton(L + 10, listTop + 12 + i * ROW_H, rowW, ROW_H - 1, Component.literal(KveGuiStyle.truncate(font, label, rowW - 6)),
                    b -> { toggleMember(memberId); }));
        }

        addRenderableWidget(new KvePlateButton(L + GUI_W / 2 - 70, T + GUI_H - 18, 70, 14, Component.literal(Lang.get("gui.kve.btn.confirm")), b -> confirm()).withPrimary(true));
        addRenderableWidget(new KvePlateButton(L + GUI_W / 2 + 2, T + GUI_H - 18, 70, 14, Component.literal(Lang.get("gui.kve.btn.cancel")), b -> onClose()));
    }

    private void toggleMember(String id) {
        if (!draftBanned.add(id)) draftBanned.remove(id);
        init();
    }

    private void confirm() {
        StructureSetOverride ov = new StructureSetOverride();
        ov.structureSetId = structureSetId;
        ov.enabled = !draftSetBanned;
        ov.spacingOverride = parsePositiveOrDefault(spacingBox.getValue());
        ov.separationOverride = parsePositiveOrDefault(separationBox.getValue());
        ov.bannedStructures = new LinkedHashSet<>(draftBanned);
        onConfirm.accept(ov);
    }

    private int parsePositiveOrDefault(String s) {
        if (s == null || s.trim().isEmpty()) return -1;
        try { return Math.max(0, Integer.parseInt(s.trim())); } catch (Exception e) { return -1; }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.structfreq.title") + " - " + structureSetId);

        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.structfreq.spacing"),    L + 10, spacingY + 3,    86, KveGuiStyle.TEXT_LABEL);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.structfreq.separation"), L + 10, separationY + 3, 86, KveGuiStyle.TEXT_LABEL);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.structfreq.members"),    L + 10, listTop,          GUI_W - 20, KveGuiStyle.TEXT_LABEL);
        if (members.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.structfreq.no_members"), L + 10, listTop + 14, GUI_W - 20, KveGuiStyle.TEXT_MUTED);
        }

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
        if (scrollBar != null) scrollBar.render(g);

        int scroll = scrollBar != null ? scrollBar.getScroll() : 0;
        int rowW = GUI_W - 20 - 12;
        int visible = Math.min(ROWS_VISIBLE, Math.max(0, members.size() - scroll));
        for (int i = 0; i < visible; i++) {
            String memberId = members.get(i + scroll);
            String label = rowLabel(memberId);
            int ry = listTop + 12 + i * ROW_H;
            if (mx >= L + 10 && mx < L + 10 + rowW && my >= ry && my < ry + ROW_H - 1
                    && font.width(label) > rowW - 6) {
                KveGuiStyle.drawFramedTooltip(g, label, mx, my, width, height);
                break;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (scrollBar != null) { scrollBar.mouseScrolled(delta); init(); return true; }
        return super.mouseScrolled(mx, my, scrollX, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar != null && scrollBar.mouseClicked(mx, my, btn)) { init(); return true; }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (scrollBar != null && scrollBar.mouseDragged(mx, my, btn, dx, dy)) { init(); return true; }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (scrollBar != null) scrollBar.mouseReleased(mx, my, btn);
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override public boolean isPauseScreen() { return false; }
}
