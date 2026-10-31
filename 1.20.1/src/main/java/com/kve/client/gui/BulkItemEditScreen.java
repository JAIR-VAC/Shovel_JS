package com.kve.client.gui;

import com.kve.common.Lang;
import com.kve.common.itemrules.BulkItemEdit;
import com.kve.common.util.IdPattern;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class BulkItemEditScreen extends Screen {

    private static final int W = 440;
    private static final int H = 262;
    private static final int PREVIEW_ROWS = 12;

    private final Screen parent;
    private final List<String> universe = new ArrayList<>();
    private final List<String> matches = new ArrayList<>();

    private String pattern = "";
    private String stack = "";
    private String burn = "";
    private int rarityIdx = 0, fireIdx = 0, creativeIdx = 0, viewersIdx = 0;
    private String flash = "";
    private int flashColor = KveGuiStyle.TEXT_OK;

    private KveScaledEditBox patternBox;
    private int cx, cy;

    public BulkItemEditScreen(Screen parent) {
        super(Component.literal(Lang.get("gui.kve.bulk.title")));
        this.parent = parent;
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            if (!id.getPath().equals("air")) universe.add(id.toString());
        }
        universe.sort(String::compareTo);
    }

    static Collection<String> itemTag(String tagId) {
        ResourceLocation rl = ResourceLocation.tryParse(tagId);
        if (rl == null) return List.of();
        return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, rl))
                .map(set -> set.stream().map(Holder::value).map(it -> BuiltInRegistries.ITEM.getKey(it).toString()).toList())
                .orElse(List.of());
    }

    @Override
    protected void init() {
        cx = (width - W) / 2;
        cy = (height - H) / 2;
        int x = cx + KveGuiStyle.PAD;
        int y = cy + KveGuiStyle.TITLEBAR_H + 14;
        patternBox = box(x, y, W - KveGuiStyle.PAD * 2, pattern, Lang.get("gui.kve.bulk.pattern_hint"), v -> { pattern = v; refreshMatches(); });

        int fx = cx + W / 2 + 6;
        int fw = cx + W - KveGuiStyle.PAD - fx;
        int fy = fieldsTop();
        box(fx + 90, fy, fw - 90, stack, "1-64", v -> stack = v);
        fy += 18;
        cycle(fx, fy, fw, "gui.kve.bulk.rarity", rarityLabel(), () -> rarityIdx = (rarityIdx + 1) % (BulkItemEdit.RARITIES.size() + 1));
        fy += 18;
        cycle(fx, fy, fw, "gui.kve.bulk.fire", triLabel(fireIdx, "gui.kve.bulk.yes", "gui.kve.bulk.no"), () -> fireIdx = (fireIdx + 1) % 3);
        fy += 18;
        box(fx + 90, fy, fw - 90, burn, Lang.get("gui.kve.bulk.burn_hint"), v -> burn = v);
        fy += 18;
        cycle(fx, fy, fw, "gui.kve.bulk.creative", triLabel(creativeIdx, "gui.kve.bulk.hide", "gui.kve.bulk.show"), () -> creativeIdx = (creativeIdx + 1) % 3);
        fy += 18;
        cycle(fx, fy, fw, "gui.kve.bulk.viewers", triLabel(viewersIdx, "gui.kve.bulk.hide", "gui.kve.bulk.show"), () -> viewersIdx = (viewersIdx + 1) % 3);

        KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(x, cy + H - KveGuiStyle.PAD - 16, cx + W - KveGuiStyle.PAD);
        addRenderableWidget(row.add(0, Lang.get("gui.kve.bulk.queue"), b -> queue()));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.close"), b -> onClose()));
        setInitialFocus(patternBox);
        refreshMatches();
    }

    private int fieldsTop() { return listTop() + 8; }

    private int listTop() { return cy + KveGuiStyle.TITLEBAR_H + 14 + 16 + 14; }

    private KveScaledEditBox box(int x, int y, int w, String value, String hint, java.util.function.Consumer<String> onChange) {
        KveScaledEditBox b = new KveScaledEditBox(font, x, y, w, 12, Component.literal(hint));
        b.setMaxLength(512);
        b.setValue(value);
        b.setHint(Component.literal(hint));
        b.setResponder(onChange);
        addRenderableWidget(b);
        return b;
    }

    private void cycle(int x, int y, int w, String key, String value, Runnable next) {
        addRenderableWidget(new KvePlateButton(x, y, w, 14, Component.literal(Lang.get(key) + ": " + value), b -> {
            next.run();
            clearWidgets();
            init();
        }));
    }

    private String rarityLabel() {
        return rarityIdx == 0 ? Lang.get("gui.kve.bulk.no_change") : BulkItemEdit.RARITIES.get(rarityIdx - 1);
    }

    private static String triLabel(int idx, String onKey, String offKey) {
        return idx == 0 ? Lang.get("gui.kve.bulk.no_change") : Lang.get(idx == 1 ? onKey : offKey);
    }

    private static Boolean tri(int idx) {
        return idx == 0 ? null : idx == 1;
    }

    private void refreshMatches() {
        matches.clear();
        matches.addAll(IdPattern.select(universe, pattern, BulkItemEditScreen::itemTag));
    }

    private static Integer number(String s) {
        return s == null || s.isBlank() ? null : Integer.parseInt(s.trim());
    }

    private void queue() {
        BulkItemEdit edit;
        try {
            edit = new BulkItemEdit(number(stack), rarityIdx == 0 ? null : BulkItemEdit.RARITIES.get(rarityIdx - 1),
                    tri(fireIdx), number(burn), tri(creativeIdx), tri(viewersIdx));
        } catch (NumberFormatException ex) {
            setFlash(Lang.get("gui.kve.bulk.bad_number"), KveGuiStyle.TEXT_ERROR);
            return;
        }
        List<String> problems = edit.validate();
        if (!problems.isEmpty() || matches.isEmpty()) {
            setFlash(problems.isEmpty() ? Lang.get("gui.kve.bulk.none_matched") : String.join(" · ", problems), KveGuiStyle.TEXT_ERROR);
            return;
        }
        List<String> ids = new ArrayList<>(matches);
        minecraft.setScreen(new KveConfirmScreen(ok -> {
            minecraft.setScreen(this);
            if (!ok) return;
            List<String> p = BulkItemQueue.queue(ids, edit, () -> {});
            if (!p.isEmpty()) setFlash(String.join(" · ", p), KveGuiStyle.TEXT_ERROR);
            else setFlash(Lang.get("gui.kve.bulk.queued", ids.size(), edit.describe()), KveGuiStyle.TEXT_OK);
        }, Component.literal(Lang.get("gui.kve.bulk.title")),
                Component.literal(Lang.get("gui.kve.bulk.confirm_msg", ids.size(), edit.describe()))));
    }

    private void setFlash(String msg, int color) {
        flash = msg;
        flashColor = color;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, H, getTitle().getString());
        int x = cx + KveGuiStyle.PAD;
        int y = cy + KveGuiStyle.TITLEBAR_H + 4;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.bulk.pattern"), x, y, W - 16, KveGuiStyle.TEXT_LABEL);

        int ly = listTop();
        int lw = W / 2 - KveGuiStyle.PAD - 4;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.bulk.matches", matches.size()), x, ly - 10, lw,
                matches.isEmpty() ? KveGuiStyle.TEXT_MUTED : KveGuiStyle.ACCENT_GOLD);
        g.fill(x - 2, ly + 2, x + lw, ly + 4 + PREVIEW_ROWS * 12, KveGuiStyle.BG_FIELD);
        for (int i = 0; i < PREVIEW_ROWS && i < matches.size(); i++) {
            String id = matches.get(i);
            int ry = ly + 4 + i * 12;
            MiniItemIcon.draw(g, x + 1, ry + 1, 8, MiniItemIcon.item(id));
            KveGuiStyle.drawTextSafe(g, id, x + 13, ry + 2, lw - 16, KveGuiStyle.TEXT_PRIMARY);
        }
        if (matches.size() > PREVIEW_ROWS) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.bulk.more", matches.size() - PREVIEW_ROWS), x, ly + 6 + PREVIEW_ROWS * 12, lw, KveGuiStyle.TEXT_MUTED);
        }

        int fx = cx + W / 2 + 6;
        int fy = fieldsTop();
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.bulk.fields"), fx, ly - 10, W / 2 - 16, KveGuiStyle.TEXT_LABEL);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.bulk.stack"), fx, fy + 2, 88, KveGuiStyle.TEXT_LABEL);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.bulk.burn"), fx, fy + 3 * 18 + 2, 88, KveGuiStyle.TEXT_LABEL);
        List<net.minecraft.util.FormattedCharSequence> note = font.split(Component.literal(Lang.get("gui.kve.bulk.note")), W / 2 - 16);
        for (int i = 0; i < note.size() && i < 2; i++) g.drawString(font, note.get(i), fx, fy + 6 * 18 + 2 + i * 10, KveGuiStyle.TEXT_MUTED, false);
        if (!flash.isEmpty()) {
            List<net.minecraft.util.FormattedCharSequence> fl = font.split(Component.literal(flash), W / 2 - 16);
            for (int i = 0; i < fl.size() && i < 3; i++) g.drawString(font, fl.get(i), fx, fy + 7 * 18 + 4 + i * 10, flashColor, false);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
