package com.kve.client.structure;

import com.kve.common.Lang;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.client.permission.ClientPermissionState;
import com.kve.common.structure.StructureFrequencyExporter;
import com.kve.common.structure.StructureFrequencyExporter.StructureSetOverride;
import net.minecraft.client.gui.GuiGraphics;
import com.kve.client.gui.KvePlateButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import com.kve.client.gui.KveScaledEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * "Frecuencia y ban de estructuras" panel, opened from
 * {@link StructureEditorScreen}: lists every {@code structure_set} the
 * running world's registry currently has (vanilla + mods + datapacks,
 * see {@link StructureFrequencyExporter#listAllStructureSetIds()}), marks the
 * ones with an active override, and opens {@link StructureSetOverrideScreen}
 * to edit one. This is a client-only, in-world tool - the id list comes
 * from the live, server-synced registry the same way TagEditorScreen's
 * loadedTags() does, so it's empty (with an explanatory message) from the
 * title screen.
 */
@OnlyIn(Dist.CLIENT)
public class StructureFrequencyScreen extends Screen {

    private static final int GUI_W = 320;
    private static final int GUI_H = 220;
    private static final int ROWS_VISIBLE = 10;
    private static final int ROW_H = 14;

    private final Screen parent;
    private final List<String> all = new ArrayList<>();
    private final List<String> filtered = new ArrayList<>();
    private EditBox searchBox;
    private String searchFilter = "";
    private ScrollBar scrollBar;
    private String flashMsg = "";
    private long flashAt = 0;
    private final List<Button> rowWidgets = new ArrayList<>();

    private int L, T;

    private List<String> onlySets = null;
    private Set<String> highlight = Set.of();
    private String titleSuffix = null;
    private boolean direct = false;

    /**
     * Abre frecuencia/ban para la plantilla {@code templateId}: si esta en un
     * solo structure_set va directo a ese set; si esta en varios, lista solo
     * esos; si no se encuentra, la lista completa con un aviso.
     */
    public static void openFor(net.minecraft.client.Minecraft mc, Screen parent, String templateId) {
        StructureFrequencyScreen screen = new StructureFrequencyScreen(parent);
        if (mc.level != null && templateId != null && !templateId.isBlank()) {
            var matches = com.kve.common.structure.StructureTemplateOwners.find(mc.level.registryAccess(), templateId);
            java.util.LinkedHashSet<String> sets = new java.util.LinkedHashSet<>();
            java.util.LinkedHashSet<String> structs = new java.util.LinkedHashSet<>();
            for (var m : matches) { sets.add(m.structureSetId()); structs.add(m.structureId()); }
            screen.highlight = structs;
            if (sets.size() == 1) {
                screen.direct = true;
                mc.setScreen(screen);
                screen.openEditor(sets.iterator().next());
                return;
            }
            if (!sets.isEmpty()) {
                screen.onlySets = new ArrayList<>(sets);
                screen.titleSuffix = templateId;
            } else {
                screen.flash(Lang.get("gui.kve.structfreq.no_owner", templateId));
            }
        }
        mc.setScreen(screen);
    }

    public StructureFrequencyScreen(Screen parent) {
        super(Lang.c("gui.kve.structfreq.panel_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        L = (width  - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);
        rowWidgets.clear();

        all.clear();
        if (minecraft.level != null) {
            all.addAll(onlySets != null ? onlySets : StructureFrequencyExporter.listAllStructureSetIds(minecraft.level.registryAccess()));
        }

        searchBox = new KveScaledEditBox(font, L + 6, T + 18, GUI_W - 12, 14,
                Component.literal(Lang.get("gui.kve.search.structure")));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchFilter);
        searchBox.setResponder(s -> { searchFilter = s; rebuildFilter(); });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        scrollBar = new ScrollBar(L + GUI_W - 10, T + 38, ROWS_VISIBLE * ROW_H, 0, ROWS_VISIBLE);
        rebuildFilter();

        addRenderableWidget(new KvePlateButton(L + GUI_W - 17, T + 2, 15, 14, Component.literal("X"), b -> onClose()));
    }

    private void rebuildFilter() {
        filtered.clear();
        String f = searchFilter.toLowerCase();
        for (String id : all) if (f.isEmpty() || id.toLowerCase().contains(f)) filtered.add(id);
        scrollBar.setTotals(filtered.size(), ROWS_VISIBLE);
        rebuildRows();
    }

    private void rebuildRows() {
        for (Button w : rowWidgets) removeWidget(w);
        rowWidgets.clear();

        int scroll = scrollBar.getScroll();
        int visible = Math.min(ROWS_VISIBLE, Math.max(0, filtered.size() - scroll));
        int rowY0 = T + 38;
        int rowW = GUI_W - 12 - 12;
        for (int i = 0; i < visible; i++) {
            String id = filtered.get(i + scroll);
            StructureSetOverride ov = StructureFrequencyExporter.getEntry(id);
            String label = statusLabel(ov) + id;
            Button row = new KvePlateButton(L + 6, rowY0 + i * ROW_H, rowW, ROW_H - 1, Component.literal(KveGuiStyle.truncate(font, label, rowW - 6)), b -> openEditor(id));
            rowWidgets.add(addRenderableWidget(row));
        }
    }

    private String statusLabel(StructureSetOverride ov) {
        if (ov == null) return "";
        if (!ov.enabled) return "[BAN] ";
        if (!ov.bannedStructures.isEmpty()) return "[ban parcial] ";
        return "[freq] ";
    }

    private void openEditor(String structureSetId) {
        if (minecraft.level == null) return;
        var access = minecraft.level.registryAccess();
        List<String> members = StructureFrequencyExporter.listMemberStructures(access, structureSetId);
        StructureSetOverride existing = StructureFrequencyExporter.getEntry(structureSetId);
        Screen back = direct ? parent : this;
        minecraft.setScreen(new StructureSetOverrideScreen(back, structureSetId, members, existing, ov -> {
            minecraft.setScreen(back);
            if (!ClientPermissionState.canEdit()) {
                flash(Lang.get("gui.kve.perm.denied"));
                return;
            }
            PendingChange.Action action = ov.isNoOp()
                    ? PendingChange.Action.DELETE
                    : (existing == null ? PendingChange.Action.CREATE : PendingChange.Action.EDIT);
            StructureSetOverride previous = StructureFrequencyExporter.stageOverride(ov);
            PendingChangeManager.queue(PendingChange.of(PendingDomain.WORLD, structureSetId, action, true,
                    () -> StructureFrequencyExporter.commitAll(access),
                    () -> StructureFrequencyExporter.discardStagedOverride(structureSetId, previous)));
            if (direct) return;
            init();
            flash(Lang.get("gui.kve.pending.queued_hint_restart"));
        }).withHighlight(highlight));
    }

    private void flash(String msg) {
        flashMsg = msg;
        flashAt = System.currentTimeMillis();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, titleSuffix == null ? Lang.get("gui.kve.structfreq.panel_title")
                : Lang.get("gui.kve.structfreq.owner_title", titleSuffix));

        if (minecraft.level == null) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.structfreq.needs_world"), L + 6, T + 40, GUI_W - 12, KveGuiStyle.TEXT_MUTED);
        } else if (filtered.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.structfreq.no_results"), L + 6, T + 40, GUI_W - 12, KveGuiStyle.TEXT_MUTED);
        }

        super.render(g, mx, my, pt);
        if (scrollBar != null) scrollBar.render(g);

        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 3000) {
            KveGuiStyle.drawCenteredTextSafe(g, flashMsg, L + GUI_W / 2, T + GUI_H - 30, 0xFF88FF88);
        }

        int scroll = scrollBar != null ? scrollBar.getScroll() : 0;
        int rowY0 = T + 38;
        int rowW = GUI_W - 12 - 12;
        int visible = Math.min(ROWS_VISIBLE, Math.max(0, filtered.size() - scroll));
        for (int i = 0; i < visible; i++) {
            String id = filtered.get(i + scroll);
            String label = statusLabel(StructureFrequencyExporter.getEntry(id)) + id;
            int ry = rowY0 + i * ROW_H;
            if (mx >= L + 6 && mx < L + 6 + rowW && my >= ry && my < ry + ROW_H - 1
                    && font.width(label) > rowW - 6) {
                KveGuiStyle.drawFramedTooltip(g, label, mx, my, width, height);
                break;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (scrollBar != null) { scrollBar.mouseScrolled(delta); rebuildRows(); return true; }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar != null && scrollBar.mouseClicked(mx, my, btn)) { rebuildRows(); return true; }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (scrollBar != null && scrollBar.mouseDragged(mx, my, btn, dx, dy)) { rebuildRows(); return true; }
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
