package com.kve.client.gui;

import com.kve.common.Lang;
import com.kve.common.packrules.PackRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class PackRulesScreen extends Screen {

    enum Section { RENAME, INFO, COMPOST, SPAWN, GAMERULE }

    private static final int W = 470;
    private static final int H = 270;
    private static final int LIST_W = 220;
    private static final int ROW_H = 12;
    private static final int ROWS = 13;

    private final Screen parent;
    private final PackRules draft;
    private Section section = Section.RENAME;
    private int scroll = 0;
    /** Fila cargada en el formulario para editarla (-1 = agregando una nueva). */
    private int editingRow = -1;

    // formulario (sobrevive a los round-trips por los pickers)
    private String pickedId = "";
    private String pickedLangKey = "";
    private boolean pickedIsBlock = false;
    private String text1 = "", text2 = "";
    private int localeIdx = 0;
    private boolean naturalOnly = true;
    private int ruleIdx = 0;
    private boolean boolValue = false;

    private String flash = "";
    private int flashColor = KveGuiStyle.TEXT_OK;
    private int cx, cy;

    private static Map<String, Boolean> gameRules() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public void visitBoolean(GameRules.Key<GameRules.BooleanValue> key, GameRules.Type<GameRules.BooleanValue> type) {
                out.put(key.getId(), true);
            }

            @Override
            public void visitInteger(GameRules.Key<GameRules.IntegerValue> key, GameRules.Type<GameRules.IntegerValue> type) {
                out.put(key.getId(), false);
            }
        });
        return out;
    }

    private final List<Map.Entry<String, Boolean>> ruleList = new ArrayList<>(gameRules().entrySet());

    public PackRulesScreen(Screen parent) {
        super(Component.literal(Lang.get("gui.kve.rules.title")));
        this.parent = parent;
        this.draft = PackRulesQueue.effective();
    }

    private List<String> locales() {
        List<String> l = new ArrayList<>();
        String game = minecraft != null && minecraft.options != null ? PackRules.normalizeLocale(minecraft.options.languageCode) : "en_us";
        l.add(game);
        if (!l.contains("en_us")) l.add("en_us");
        return l;
    }

    @Override
    protected void init() {
        cx = (width - W) / 2;
        cy = (height - H) / 2;
        int x = cx + KveGuiStyle.PAD;
        int y = cy + KveGuiStyle.TITLEBAR_H + 4;
        int tabW = (W - KveGuiStyle.PAD * 2 - 4 * 2) / 5;
        Section[] all = Section.values();
        for (int i = 0; i < all.length; i++) {
            Section s = all[i];
            KvePlateButton b = new KvePlateButton(x + i * (tabW + 2), y, tabW, 14,
                    Component.literal(Lang.get("gui.kve.rules.tab." + s.name().toLowerCase(java.util.Locale.ROOT)) + " (" + count(s) + ")"),
                    btn -> { section = s; scroll = 0; editingRow = -1; resetForm(); rebuild(); });
            b.withSelected(s == section);
            addRenderableWidget(b);
        }
        buildForm(x + LIST_W + 10, y + 22, cx + W - KveGuiStyle.PAD - (x + LIST_W + 10));

        KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(x, cy + H - KveGuiStyle.PAD - 16, x + LIST_W);
        addRenderableWidget(row.add(0, Lang.get("gui.kve.rules.queue"), b -> queue()));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.close"), b -> onClose()));
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void resetForm() {
        pickedId = ""; pickedLangKey = ""; pickedIsBlock = false; text1 = ""; text2 = "";
        naturalOnly = true; boolValue = false;
    }

    private void buildForm(int fx, int fy, int fw) {
        switch (section) {
            case RENAME -> {
                pickItemButton(fx, fy, fw);
                fy += 30;
                List<String> locs = locales();
                localeIdx = Math.min(localeIdx, locs.size() - 1);
                addRenderableWidget(new KvePlateButton(fx, fy, fw, 14,
                        Component.literal(Lang.get("gui.kve.rules.locale", locs.get(localeIdx))),
                        b -> { localeIdx = (localeIdx + 1) % locs.size(); rebuild(); }));
                fy += 28;
                box(fx, fy, fw, text1, Lang.get("gui.kve.rules.rename.name_hint"), v -> text1 = v);
            }
            case INFO -> {
                pickItemButton(fx, fy, fw);
                fy += 42;
                box(fx, fy, fw, text1, Lang.get("gui.kve.rules.info.title_hint"), v -> text1 = v);
                fy += 28;
                box(fx, fy, fw, text2, Lang.get("gui.kve.rules.info.text_hint"), v -> text2 = v);
            }
            case COMPOST -> {
                pickItemButton(fx, fy, fw);
                fy += 42;
                box(fx, fy, fw, text1.isEmpty() ? "65" : text1, Lang.get("gui.kve.rules.compost.chance_hint"), v -> text1 = v);
            }
            case SPAWN -> {
                addRenderableWidget(new KvePlateButton(fx, fy, fw, 14,
                        Component.literal(pickedId.isEmpty() ? Lang.get("gui.kve.rules.pick_mob") : pickedId),
                        b -> minecraft.setScreen(new MobPickerScreen(this, type -> {
                            ResourceLocation k = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                            if (k != null) pickedId = k.toString();
                        }))));
                fy += 42;
                box(fx, fy, fw, text1, Lang.get("gui.kve.rules.spawn.dims_hint"), v -> text1 = v);
                fy += 18;
                addRenderableWidget(new KvePlateButton(fx, fy, fw, 14,
                        Component.literal(Lang.get(naturalOnly ? "gui.kve.rules.spawn.natural_only" : "gui.kve.rules.spawn.all_sources")),
                        b -> { naturalOnly = !naturalOnly; rebuild(); }));
            }
            case GAMERULE -> {
                if (ruleList.isEmpty()) break;
                ruleIdx = Math.floorMod(ruleIdx, ruleList.size());
                var rule = ruleList.get(ruleIdx);
                addRenderableWidget(new KvePlateButton(fx, fy, 16, 14, Component.literal("<"), b -> { ruleIdx--; rebuild(); }));
                addRenderableWidget(new KvePlateButton(fx + fw - 16, fy, 16, 14, Component.literal(">"), b -> { ruleIdx++; rebuild(); }));
                fy += 30;
                if (rule.getValue()) {
                    addRenderableWidget(new KvePlateButton(fx, fy, fw, 14,
                            Component.literal(Lang.get("gui.kve.rules.gamerule.value", String.valueOf(boolValue))),
                            b -> { boolValue = !boolValue; rebuild(); }));
                } else {
                    box(fx, fy, fw, text1, Lang.get("gui.kve.rules.gamerule.number_hint"), v -> text1 = v);
                }
            }
        }
        int addY = cy + H - KveGuiStyle.PAD - 16;
        if (editingRow >= 0) {
            int half = (fw - 4) / 2;
            addRenderableWidget(new KvePlateButton(fx, addY, half, 14, Component.literal(Lang.get("gui.kve.rules.update")), b -> add()));
            addRenderableWidget(new KvePlateButton(fx + half + 4, addY, fw - half - 4, 14, Component.literal(Lang.get("gui.kve.rules.new")),
                    b -> { editingRow = -1; resetForm(); rebuild(); }));
        } else {
            addRenderableWidget(new KvePlateButton(fx, addY, fw, 14, Component.literal(Lang.get("gui.kve.rules.add")), b -> add()));
        }
    }

    private void pickItemButton(int fx, int fy, int fw) {
        addRenderableWidget(new KvePlateButton(fx + 16, fy, fw - 16, 14,
                Component.literal(pickedId.isEmpty() ? Lang.get("gui.kve.rules.pick_item") : pickedId),
                b -> minecraft.setScreen(new ItemPickerScreen(this, this::onItemPicked))));
    }

    private void onItemPicked(ItemStack stack) {
        ResourceLocation k = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (k == null) return;
        pickedId = k.toString();
        pickedLangKey = stack.getItem().getDescriptionId();
        pickedIsBlock = stack.getItem() instanceof BlockItem;
        if (section == Section.RENAME && text1.isEmpty()) text1 = stack.getHoverName().getString();
    }

    private void box(int x, int y, int w, String value, String hint, Consumer<String> onChange) {
        KveScaledEditBox b = new KveScaledEditBox(font, x, y, w, 12, Component.literal(hint));
        b.setMaxLength(256);
        b.setValue(value);
        b.setHint(Component.literal(hint));
        b.setResponder(onChange);
        addRenderableWidget(b);
    }

    private int count(Section s) {
        return switch (s) {
            case RENAME -> draft.renames.size();
            case INFO -> draft.infoPages.size();
            case COMPOST -> draft.compostables.size();
            case SPAWN -> draft.spawnBlocks.size();
            case GAMERULE -> draft.gameRules.size();
        };
    }

    private List<String> rows() {
        List<String> out = new ArrayList<>();
        switch (section) {
            case RENAME -> draft.renames.forEach(r -> out.add("[" + r.locale + "] " + r.targetId + " → " + r.name));
            case INFO -> draft.infoPages.forEach(p -> out.add(p.itemId + ": " + String.join(" / ", p.lines)));
            case COMPOST -> draft.compostables.forEach(c -> out.add(c.itemId + ": "
                    + (c.chance <= 0 ? Lang.get("gui.kve.rules.compost.removed") : Math.round(c.chance * 100) + "%")));
            case SPAWN -> draft.spawnBlocks.forEach(s -> out.add(s.entityId + " — "
                    + (s.dimensions.isEmpty() ? Lang.get("gui.kve.rules.spawn.all_dims") : String.join(", ", s.dimensions))
                    + (s.naturalOnly ? "" : " (" + Lang.get("gui.kve.rules.spawn.all_sources_short") + ")")));
            case GAMERULE -> draft.gameRules.forEach(g -> out.add(g.rule + " = " + g.value));
        }
        return out;
    }

    private String rowItem(int i) {
        return switch (section) {
            case RENAME -> draft.renames.get(i).targetId;
            case INFO -> draft.infoPages.get(i).itemId;
            case COMPOST -> draft.compostables.get(i).itemId;
            case SPAWN, GAMERULE -> null;
        };
    }

    /** Carga la regla {@code i} en el formulario para cambiarla. */
    private void loadRow(int i) {
        resetForm();
        switch (section) {
            case RENAME -> {
                PackRules.Rename r = draft.renames.get(i);
                pickedId = r.targetId; pickedLangKey = r.langKey; pickedIsBlock = r.block; text1 = r.name;
                int li = locales().indexOf(r.locale);
                localeIdx = li >= 0 ? li : 0;
            }
            case INFO -> {
                PackRules.InfoPage p = draft.infoPages.get(i);
                pickedId = p.itemId; text1 = p.title; text2 = String.join(" | ", p.lines);
            }
            case COMPOST -> {
                PackRules.Compostable c = draft.compostables.get(i);
                pickedId = c.itemId; text1 = com.kve.common.util.KveNumbers.trimmed(c.chance * 100f, 1);
            }
            case SPAWN -> {
                PackRules.SpawnBlock sb = draft.spawnBlocks.get(i);
                pickedId = sb.entityId; naturalOnly = sb.naturalOnly; text1 = String.join(", ", sb.dimensions);
            }
            case GAMERULE -> {
                PackRules.GameRuleDefault g = draft.gameRules.get(i);
                for (int k = 0; k < ruleList.size(); k++) if (ruleList.get(k).getKey().equals(g.rule)) ruleIdx = k;
                boolValue = Boolean.parseBoolean(g.value); text1 = g.value;
            }
        }
        editingRow = i;
        setFlash("", KveGuiStyle.TEXT_OK);
        rebuild();
    }

    private void removeRow(int i) {
        if (editingRow == i) { editingRow = -1; resetForm(); }
        else if (editingRow > i) editingRow--;
        switch (section) {
            case RENAME -> draft.renames.remove(i);
            case INFO -> draft.infoPages.remove(i);
            case COMPOST -> draft.compostables.remove(i);
            case SPAWN -> draft.spawnBlocks.remove(i);
            case GAMERULE -> draft.gameRules.remove(i);
        }
        rebuild();
    }

    private void add() {
        String error = null;
        Object editedBefore = editingRow >= 0 && editingRow < count(section) ? rowObject(editingRow) : null;
        switch (section) {
            case RENAME -> {
                if (pickedId.isEmpty()) error = Lang.get("gui.kve.rules.need_item");
                else if (text1.isBlank()) error = Lang.get("gui.kve.rules.rename.need_name");
                else {
                    String loc = locales().get(localeIdx);
                    draft.renames.removeIf(r -> r.targetId.equals(pickedId) && r.locale.equals(loc));
                    PackRules.Rename r = new PackRules.Rename();
                    r.targetId = pickedId; r.langKey = pickedLangKey; r.block = pickedIsBlock; r.locale = loc; r.name = text1.trim();
                    draft.renames.add(r);
                }
            }
            case INFO -> {
                if (pickedId.isEmpty()) error = Lang.get("gui.kve.rules.need_item");
                else if (text2.isBlank()) error = Lang.get("gui.kve.rules.info.need_text");
                else {
                    draft.infoPages.removeIf(p -> p.itemId.equals(pickedId));
                    PackRules.InfoPage p = new PackRules.InfoPage();
                    p.itemId = pickedId; p.title = text1.trim();
                    for (String l : text2.split("\\|")) if (!l.isBlank()) p.lines.add(l.trim());
                    draft.infoPages.add(p);
                }
            }
            case COMPOST -> {
                if (pickedId.isEmpty()) error = Lang.get("gui.kve.rules.need_item");
                else {
                    try {
                        float pct = Float.parseFloat((text1.isEmpty() ? "65" : text1).trim().replace("%", "").replace(',', '.'));
                        draft.compostables.removeIf(c -> c.itemId.equals(pickedId));
                        PackRules.Compostable c = new PackRules.Compostable();
                        c.itemId = pickedId; c.chance = Math.max(0f, Math.min(100f, pct)) / 100f;
                        draft.compostables.add(c);
                    } catch (NumberFormatException ex) {
                        error = Lang.get("gui.kve.rules.bad_number");
                    }
                }
            }
            case SPAWN -> {
                if (pickedId.isEmpty()) error = Lang.get("gui.kve.rules.need_mob");
                else {
                    draft.spawnBlocks.removeIf(s -> s.entityId.equals(pickedId));
                    PackRules.SpawnBlock s = new PackRules.SpawnBlock();
                    s.entityId = pickedId; s.naturalOnly = naturalOnly;
                    for (String d : text1.split(",")) {
                        String dim = d.trim();
                        if (dim.isEmpty()) continue;
                        s.dimensions.add(dim.contains(":") ? dim : "minecraft:" + dim);
                    }
                    draft.spawnBlocks.add(s);
                }
            }
            case GAMERULE -> {
                var rule = ruleList.get(Math.floorMod(ruleIdx, ruleList.size()));
                String value = rule.getValue() ? String.valueOf(boolValue) : text1.trim();
                if (!rule.getValue() && !value.matches("-?\\d+")) error = Lang.get("gui.kve.rules.bad_number");
                else {
                    draft.gameRules.removeIf(g -> g.rule.equals(rule.getKey()));
                    PackRules.GameRuleDefault g = new PackRules.GameRuleDefault();
                    g.rule = rule.getKey(); g.value = value;
                    draft.gameRules.add(g);
                }
            }
        }
        if (error != null) { setFlash(error, KveGuiStyle.TEXT_ERROR); return; }
        if (editedBefore != null) sectionList().remove(editedBefore);
        boolean wasEdit = editingRow >= 0;
        editingRow = -1;
        resetForm();
        setFlash(Lang.get(wasEdit ? "gui.kve.rules.updated" : "gui.kve.rules.added"), KveGuiStyle.TEXT_OK);
        rebuild();
    }

    private List<?> sectionList() {
        return switch (section) {
            case RENAME -> draft.renames;
            case INFO -> draft.infoPages;
            case COMPOST -> draft.compostables;
            case SPAWN -> draft.spawnBlocks;
            case GAMERULE -> draft.gameRules;
        };
    }

    private Object rowObject(int i) { return sectionList().get(i); }

    private void queue() {
        List<String> problems = PackRulesQueue.queue(draft);
        if (!problems.isEmpty()) setFlash(String.join(" · ", problems), KveGuiStyle.TEXT_ERROR);
        else setFlash(Lang.get("gui.kve.rules.queued"), KveGuiStyle.TEXT_OK);
    }

    private void setFlash(String msg, int color) {
        flash = msg;
        flashColor = color;
    }

    private int listTop() { return cy + KveGuiStyle.TITLEBAR_H + 26; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, H, getTitle().getString());
        int x = cx + KveGuiStyle.PAD;
        int ly = listTop();
        List<String> rows = rows();
        g.fill(x - 2, ly - 1, x + LIST_W, ly + ROWS * ROW_H + 1, KveGuiStyle.BG_FIELD);
        if (rows.isEmpty()) KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.rules.empty"), x, ly + 2, LIST_W - 4, KveGuiStyle.TEXT_MUTED);
        for (int i = 0; i < ROWS && i + scroll < rows.size(); i++) {
            int idx = i + scroll;
            int ry = ly + i * ROW_H;
            String item = rowItem(idx);
            int tx = x;
            if (item != null) {
                MiniItemIcon.draw(g, x + 1, ry + 2, 8, MiniItemIcon.item(item));
                tx += 13;
            }
            boolean hovX = mx >= x + LIST_W - 12 && mx < x + LIST_W && my >= ry && my < ry + ROW_H;
            boolean hovRow = !hovX && mx >= x - 2 && mx < x + LIST_W && my >= ry && my < ry + ROW_H;
            if (idx == editingRow) g.fill(x - 2, ry, x + LIST_W, ry + ROW_H, KveGuiStyle.th(0xFF2A5E9A));
            else if (hovRow) g.fill(x - 2, ry, x + LIST_W, ry + ROW_H, KveGuiStyle.BORDER_FIELD);
            KveGuiStyle.drawTextSafe(g, rows.get(idx), tx, ry + 2, x + LIST_W - 14 - tx, KveGuiStyle.TEXT_PRIMARY);
            KveGuiStyle.drawTextSafe(g, "x", x + LIST_W - 9, ry + 2, 8, hovX ? KveGuiStyle.TEXT_ERROR : KveGuiStyle.TEXT_MUTED);
        }

        int fx = x + LIST_W + 10;
        int fw = cx + W - KveGuiStyle.PAD - fx;
        int fy = cy + KveGuiStyle.TITLEBAR_H + 26;
        drawWrapped(g, Lang.get("gui.kve.rules.help." + section.name().toLowerCase(java.util.Locale.ROOT)),
                fx, cy + H - KveGuiStyle.PAD - 16 - 4 - 3 * 10, fw, 3, KveGuiStyle.TEXT_MUTED);
        if (editingRow >= 0) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.rules.editing"), fx, cy + H - KveGuiStyle.PAD - 16 - 4 - 4 * 10, fw, KveGuiStyle.ACCENT_GOLD);
        }
        switch (section) {
            case RENAME, INFO, COMPOST -> {
                MiniItemIcon.draw(g, fx + 2, fy + 2, 10, MiniItemIcon.item(pickedId));
                if (section == Section.RENAME) {
                    KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.rules.rename.name"), fx, fy + 48, fw, KveGuiStyle.TEXT_LABEL);
                } else if (section == Section.INFO) {
                    KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.rules.info.title"), fx, fy + 32, fw, KveGuiStyle.TEXT_LABEL);
                    KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.rules.info.text"), fx, fy + 60, fw, KveGuiStyle.TEXT_LABEL);
                } else {
                    KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.rules.compost.chance"), fx, fy + 32, fw, KveGuiStyle.TEXT_LABEL);
                }
            }
            case SPAWN -> KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.rules.spawn.dims"), fx, fy + 32, fw, KveGuiStyle.TEXT_LABEL);
            case GAMERULE -> {
                if (!ruleList.isEmpty()) {
                    var rule = ruleList.get(Math.floorMod(ruleIdx, ruleList.size()));
                    KveGuiStyle.drawCenteredTextSafe(g, rule.getKey(), fx + fw / 2, fy + 3, KveGuiStyle.ACCENT_GOLD);
                    KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.rules.gamerule.note"), fx, fy + 18, fw, KveGuiStyle.TEXT_MUTED);
                }
            }
        }
        int msgY = ly + ROWS * ROW_H + 3;
        if (PackRulesQueue.hasPending()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.rules.pending"), x, msgY, LIST_W, KveGuiStyle.TEXT_WARN);
            msgY += 10;
        }
        if (!flash.isEmpty()) drawWrapped(g, flash, x, msgY, LIST_W, (cy + H - KveGuiStyle.PAD - 18 - msgY) / 10, flashColor);
        super.render(g, mx, my, pt);
    }

    private void drawWrapped(GuiGraphics g, String text, int x, int y, int w, int maxLines, int color) {
        if (maxLines <= 0) return;
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(text), w);
        for (int i = 0; i < lines.size() && i < maxLines; i++) g.drawString(font, lines.get(i), x, y + i * 10, color, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int x = cx + KveGuiStyle.PAD;
        int ly = listTop();
        if (btn == 0 && mx >= x - 2 && mx < x + LIST_W && my >= ly && my < ly + ROWS * ROW_H) {
            int idx = (int) ((my - ly) / ROW_H) + scroll;
            if (idx < count(section)) {
                if (mx >= x + LIST_W - 12) removeRow(idx);
                else loadRow(idx);
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, count(section) - ROWS), scroll + (delta > 0 ? -3 : 3)));
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
