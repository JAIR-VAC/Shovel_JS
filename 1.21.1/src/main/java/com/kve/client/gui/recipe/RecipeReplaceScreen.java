package com.kve.client.gui.recipe;

import com.kve.client.gui.SafeItemRender;
import com.kve.client.gui.picker.ItemPickerScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;

import com.kve.common.Lang;
import com.kve.client.permission.ClientPermissionState;
import com.kve.client.recipe.RecipeReplacementQueue;
import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.kubejs.RecipeReplacementExporter;
import com.kve.common.kubejs.RecipeReplacementExporter.Kind;
import com.kve.common.kubejs.RecipeReplacementExporter.Rule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * reemplazo
 * GLOBAL de ingredientes/resultados en recetas y unificacion de materiales
 * por tag, con vista previa en vivo de cuantas recetas cargadas afecta cada
 * regla. Las reglas se editan sobre una copia de trabajo y "Encolar" las
 * manda como un solo cambio pendiente (ver {@link RecipeReplacementQueue}).
 * El script real lo genera {@link RecipeReplacementExporter}.
 * <p>
 * {@code unifyMode} abre la pantalla directamente en "Unificar tag".
 */
@OnlyIn(Dist.CLIENT)
public class RecipeReplaceScreen extends Screen {

    private static final int W = 400;
    private static final int H = 280;
    private static final int RULE_ROWS = 5;
    private static final int MEMBER_ROWS = 4;

    private final Screen parent;
    private Kind kind;
    private String matchText = "", replText = "", modText = "";
    private boolean hideOthers = true;
    private final List<Rule> working;
    private final List<String> tagMembers = new ArrayList<>();
    private int ruleScroll = 0, memberScroll = 0;
    private String status = "";
    private int statusColor;
    private int previewCount = -1;
    private int cx, cy;

    public RecipeReplaceScreen(Screen parent, boolean unifyMode) {
        super(Component.literal(Lang.get(unifyMode ? "gui.kve.recipe.unify.title" : "gui.kve.recipe.replace.title")));
        this.parent = parent;
        this.kind = unifyMode ? Kind.UNIFY : Kind.INPUT;
        this.working = new ArrayList<>(RecipeReplacementQueue.effectiveRules());
    }

    @Override
    protected void init() {
        cx = (width - W) / 2;
        cy = (height - H) / 2;
        int x = cx + KveGuiStyle.PAD;
        int labelW = 96;
        int y = cy + KveGuiStyle.TITLEBAR_H + 4;

        String kindLabel = Lang.get("gui.kve.recipe.replace.kind." + kind.name().toLowerCase(Locale.ROOT));
        addRenderableWidget(new KvePlateButton(x + labelW, y, 160, 14, Component.literal(kindLabel), b -> {
            kind = Kind.values()[(kind.ordinal() + 1) % Kind.values().length];
            rebuild();
        }));
        y += 18;

        int pickW = 44;
        int boxW = W - KveGuiStyle.PAD * 2 - labelW - pickW - 4;
        box(x + labelW, y, boxW, kind == Kind.UNIFY ? "gui.kve.recipe.unify.tag_hint" : "gui.kve.recipe.replace.match_hint", matchText, s -> { matchText = s.trim(); recompute(); });
        if (kind != Kind.UNIFY) {
            addRenderableWidget(new KvePlateButton(x + labelW + boxW + 4, y, pickW, 14, Component.literal(Lang.get("gui.kve.recipe.replace.pick")),
                    b -> minecraft.setScreen(new ItemPickerScreen(this, st -> { if (st != null && !st.isEmpty()) matchText = itemId(st); }))));
        }
        y += 18;
        if (kind != Kind.UNIFY) {
            box(x + labelW, y, boxW, "gui.kve.recipe.replace.repl_hint", replText, s -> { replText = s.trim(); recompute(); });
            addRenderableWidget(new KvePlateButton(x + labelW + boxW + 4, y, pickW, 14, Component.literal(Lang.get("gui.kve.recipe.replace.pick")),
                    b -> minecraft.setScreen(new ItemPickerScreen(this, st -> { if (st != null && !st.isEmpty()) replText = itemId(st); }))));
        } else {
            addRenderableWidget(new KvePlateButton(x + labelW, y, 160, 14,
                    Component.literal(Lang.get(hideOthers ? "gui.kve.recipe.unify.hide_on" : "gui.kve.recipe.unify.hide_off")),
                    b -> { hideOthers = !hideOthers; rebuild(); }));
        }
        y += 18;
        box(x + labelW, y, boxW, "gui.kve.recipe.replace.mod_hint", modText, s -> { modText = s.trim().toLowerCase(Locale.ROOT); recompute(); });

        int by = cy + H - KveGuiStyle.PAD - 16;
        KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(x, by, cx + W - KveGuiStyle.PAD);
        addRenderableWidget(row.add(0, Lang.get("gui.kve.recipe.replace.add"), b -> addRule()));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.recipe.replace.queue"), b -> queue()));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.cancel"), b -> onClose()));
        recompute();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private KveScaledEditBox box(int x, int y, int w, String hintKey, String value, java.util.function.Consumer<String> onChange) {
        KveScaledEditBox b = new KveScaledEditBox(font, x, y, w, 14, Component.literal(Lang.get(hintKey)));
        b.setMaxLength(160);
        b.setHint(Component.literal(Lang.get(hintKey)));
        b.setValue(value);
        b.setResponder(onChange);
        addRenderableWidget(b);
        return b;
    }

    private static String itemId(ItemStack st) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(st.getItem());
        return id == null ? "" : id.toString();
    }

    private Rule draftRule() {
        List<String> extra = kind == Kind.UNIFY ? List.copyOf(tagMembers) : List.of();
        return new Rule(RecipeReplacementExporter.newRuleId(working), kind, matchText, replText, modText, extra, kind == Kind.UNIFY && hideOthers);
    }

    private void recompute() {
        tagMembers.clear();
        if (kind == Kind.UNIFY && RecipeReplacementExporter.isTagRef(matchText)) {
            tagMembers.addAll(tagItems(matchText.substring(1)));
            if (!replText.isEmpty() && !tagMembers.contains(replText)) replText = "";
            if (replText.isEmpty() && !tagMembers.isEmpty()) replText = preferredGuess(tagMembers);
        }
        memberScroll = Math.max(0, Math.min(memberScroll, Math.max(0, tagMembers.size() - MEMBER_ROWS)));
        List<String> errors = RecipeReplacementExporter.validate(draftRule());
        previewCount = -1;
        if (matchText.isEmpty()) {
            status = Lang.get(kind == Kind.UNIFY ? "gui.kve.recipe.unify.need_tag" : "gui.kve.recipe.replace.need_match");
            statusColor = KveGuiStyle.TEXT_MUTED;
        } else if (kind == Kind.UNIFY && RecipeReplacementExporter.isTagRef(matchText) && tagMembers.isEmpty()) {
            status = Lang.get("gui.kve.recipe.unify.empty_tag");
            statusColor = KveGuiStyle.TEXT_WARN;
        } else if (!errors.isEmpty()) {
            status = errors.get(0);
            statusColor = KveGuiStyle.TEXT_WARN;
        } else {
            previewCount = countAffected(draftRule());
            status = previewCount < 0 ? Lang.get("gui.kve.recipe.conflicts.no_world")
                    : Lang.get("gui.kve.recipe.replace.preview", previewCount);
            statusColor = previewCount > 0 ? KveGuiStyle.TEXT_OK : KveGuiStyle.TEXT_MUTED;
        }
    }

    private static String preferredGuess(List<String> members) {
        for (String m : members) if (m.startsWith("minecraft:")) return m;
        return members.get(0);
    }

    private static List<String> tagItems(String tagId) {
        List<String> out = new ArrayList<>();
        ResourceLocation rl = ResourceLocation.tryParse(tagId);
        if (rl == null) return out;
        BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, rl)).ifPresent(set -> {
            for (Holder<Item> h : set) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(h.value());
                if (id != null) out.add(id.toString());
            }
        });
        out.sort(String::compareTo);
        return out;
    }

    /** Recetas cargadas que la regla tocaria (aproximado del lado cliente; -1 sin mundo). */
    private int countAffected(Rule r) {
        if (minecraft == null || minecraft.getConnection() == null) return -1;
        RecipeManager mgr = minecraft.getConnection().getRecipeManager();
        RegistryAccess ra = minecraft.getConnection().registryAccess();
        Predicate<String> matches = matcher(r.match());
        List<String> removed = KubeJSExporter.getRemovedRecipes();
        int n = 0;
        for (RecipeHolder<?> recHolder : mgr.getRecipes()) {
            Recipe<?> rec = recHolder.value();
            ResourceLocation id = recHolder.id();
            if (id == null || removed.contains(id.toString()) || !passesMod(id.getNamespace(), r.modFilter())) continue;
            try {
                boolean hit = false;
                if (r.kind() == Kind.INPUT || r.kind() == Kind.UNIFY) {
                    Predicate<String> in = r.kind() == Kind.UNIFY ? s -> r.extraItems().contains(s) && !s.equals(r.replacement()) : matches;
                    for (Ingredient ing : rec.getIngredients()) {
                        for (ItemStack s : ing.getItems()) if (in.test(itemId(s))) { hit = true; break; }
                        if (hit) break;
                    }
                }
                if (!hit && (r.kind() == Kind.OUTPUT || r.kind() == Kind.UNIFY)) {
                    String out = itemId(rec.getResultItem(ra));
                    hit = matches.test(out) && !(r.kind() == Kind.UNIFY && out.equals(r.replacement()));
                }
                if (hit) n++;
            } catch (Throwable ignored) {}
        }
        return n;
    }

    private static Predicate<String> matcher(String match) {
        if (RecipeReplacementExporter.isTagRef(match)) {
            List<String> members = tagItems(match.substring(1));
            return members::contains;
        }
        return match::equals;
    }

    private static boolean passesMod(String ns, String filter) {
        if (filter == null || filter.isEmpty()) return true;
        return filter.startsWith("!") ? !ns.equals(filter.substring(1)) : ns.equals(filter);
    }

    private void addRule() {
        Rule r = draftRule();
        List<String> errors = RecipeReplacementExporter.validate(r);
        if (!errors.isEmpty()) { status = errors.get(0); statusColor = KveGuiStyle.TEXT_ERROR; return; }
        working.add(r);
        matchText = "";
        replText = "";
        ruleScroll = Math.max(0, working.size() - RULE_ROWS);
        rebuild();
        status = Lang.get("gui.kve.recipe.replace.added");
        statusColor = KveGuiStyle.TEXT_OK;
    }

    private void queue() {
        if (!ClientPermissionState.canEdit()) { status = Lang.get("gui.kve.perm.denied"); statusColor = KveGuiStyle.TEXT_ERROR; return; }
        if (working.equals(RecipeReplacementQueue.effectiveRules())) { status = Lang.get("gui.kve.recipe.replace.no_changes"); statusColor = KveGuiStyle.TEXT_MUTED; return; }
        RecipeReplacementQueue.queue(working, Lang.get("gui.kve.recipe.replace.summary", working.size()));
        minecraft.setScreen(parent);
    }

    private int listTop() { return cy + KveGuiStyle.TITLEBAR_H + 4 + 18 * 4 + 16; }

    private int membersTop() { return listTop(); }

    private int rulesTop() { return kind == Kind.UNIFY ? listTop() + 12 + MEMBER_ROWS * 11 + 6 : listTop(); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, H, getTitle().getString());
        int x = cx + KveGuiStyle.PAD;
        int innerW = W - KveGuiStyle.PAD * 2;
        int y = cy + KveGuiStyle.TITLEBAR_H + 7;
        String[] labels = {"gui.kve.recipe.replace.kind", kind == Kind.UNIFY ? "gui.kve.recipe.unify.tag" : "gui.kve.recipe.replace.match",
                kind == Kind.UNIFY ? "gui.kve.recipe.unify.hide" : "gui.kve.recipe.replace.repl", "gui.kve.recipe.replace.mod"};
        for (String l : labels) {
            KveGuiStyle.drawTextSafe(g, Lang.get(l), x, y, 92, KveGuiStyle.TEXT_LABEL);
            y += 18;
        }
        KveGuiStyle.drawTextSafe(g, status, x, y, innerW, statusColor);

        if (kind == Kind.UNIFY) {
            int my0 = membersTop();
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.unify.members", tagMembers.size()), x, my0, innerW, KveGuiStyle.TEXT_LABEL);
            int ry = my0 + 12;
            g.fill(x - 2, ry - 1, x + innerW, ry + MEMBER_ROWS * 11 + 1, KveGuiStyle.BG_FIELD);
            for (int i = 0; i < MEMBER_ROWS; i++) {
                int idx = i + memberScroll;
                if (idx >= tagMembers.size()) break;
                String m = tagMembers.get(idx);
                boolean pref = m.equals(replText);
                boolean hov = mx >= x && mx < x + innerW && my >= ry + i * 11 && my < ry + i * 11 + 11;
                if (hov) g.fill(x - 2, ry + i * 11, x + innerW, ry + i * 11 + 11, 0x22FFFFFF);
                ItemStack st = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(m)));
                g.pose().pushPose();
                g.pose().translate(x, ry + i * 11, 0);
                g.pose().scale(0.625f, 0.625f, 1f);
                SafeItemRender.render(g, st, 0, 0);
                g.pose().popPose();
                String label = (pref ? "★ " : "   ") + m + (pref ? "  " + Lang.get("gui.kve.recipe.unify.preferred") : "");
                KveGuiStyle.drawTextSafe(g, label, x + 13, ry + i * 11 + 1, innerW - 16, pref ? KveGuiStyle.ACCENT_GOLD : KveGuiStyle.TEXT_PRIMARY);
            }
        }

        int rt = rulesTop();
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.replace.rules", working.size()), x, rt, innerW, KveGuiStyle.TEXT_LABEL);
        int ry = rt + 12;
        g.fill(x - 2, ry - 1, x + innerW, ry + RULE_ROWS * 11 + 1, KveGuiStyle.BG_FIELD);
        if (working.isEmpty()) KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.replace.none"), x, ry + 1, innerW, KveGuiStyle.TEXT_MUTED);
        for (int i = 0; i < RULE_ROWS; i++) {
            int idx = i + ruleScroll;
            if (idx >= working.size()) break;
            int rowY = ry + i * 11;
            boolean xHov = mx >= x + innerW - 10 && mx < x + innerW && my >= rowY && my < rowY + 11;
            KveGuiStyle.drawTextSafe(g, RecipeReplacementExporter.describe(working.get(idx)), x, rowY + 1, innerW - 14, KveGuiStyle.TEXT_PRIMARY);
            KveGuiStyle.drawTextSafe(g, "x", x + innerW - 8, rowY + 1, 8, xHov ? KveGuiStyle.TEXT_ERROR : KveGuiStyle.TEXT_MUTED);
        }
        if (RecipeReplacementQueue.hasPending()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.replace.pending"), x, ry + RULE_ROWS * 11 + 4, innerW, KveGuiStyle.ACCENT_GOLD);
        }
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int x = cx + KveGuiStyle.PAD;
        int innerW = W - KveGuiStyle.PAD * 2;
        if (kind == Kind.UNIFY) {
            int ry = membersTop() + 12;
            for (int i = 0; i < MEMBER_ROWS; i++) {
                int idx = i + memberScroll;
                if (idx >= tagMembers.size()) break;
                if (mx >= x && mx < x + innerW && my >= ry + i * 11 && my < ry + i * 11 + 11) {
                    replText = tagMembers.get(idx);
                    recompute();
                    return true;
                }
            }
        }
        int ry = rulesTop() + 12;
        for (int i = 0; i < RULE_ROWS; i++) {
            int idx = i + ruleScroll;
            if (idx >= working.size()) break;
            int rowY = ry + i * 11;
            if (mx >= x + innerW - 10 && mx < x + innerW && my >= rowY && my < rowY + 11) {
                working.remove(idx);
                ruleScroll = Math.max(0, Math.min(ruleScroll, working.size() - RULE_ROWS));
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        int step = delta > 0 ? -1 : 1;
        if (kind == Kind.UNIFY && my < rulesTop()) {
            memberScroll = Math.max(0, Math.min(Math.max(0, tagMembers.size() - MEMBER_ROWS), memberScroll + step));
        } else {
            ruleScroll = Math.max(0, Math.min(Math.max(0, working.size() - RULE_ROWS), ruleScroll + step));
        }
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
