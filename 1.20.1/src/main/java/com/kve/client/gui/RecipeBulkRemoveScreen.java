package com.kve.client.gui;

import com.kve.common.Lang;
import com.kve.client.permission.ClientPermissionState;
import com.kve.client.recipe.RecipeRemovalQueue;
import com.kve.common.kubejs.KubeJSExporter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Quitar muchas recetas de una vez por filtro.
 * Filtros combinables (todos
 * los que tengan texto deben cumplirse): resultado, ingrediente, mod y tipo
 * de receta. La vista previa muestra en vivo cuantas recetas cumplen y las
 * primeras de la lista; "Encolar" las manda como UN solo cambio pendiente
 * (ver {@link RecipeRemovalQueue#queueBulk()}), que se puede descartar entero.
 * <p>
 * Se resuelve a ids concretos en el momento (en vez de escribir un
 * {@code event.remove({...})} con el filtro) para que la vista previa sea
 * exactamente lo que se quita, y para que cada receta quitada aparezca
 * igual que las quitadas a mano en el resto del editor.
 */
@OnlyIn(Dist.CLIENT)
public class RecipeBulkRemoveScreen extends Screen {

    private static final int W = 360;
    private static final int H = 250;
    private static final int PREVIEW_LINES = 8;

    private final Screen parent;
    private KveScaledEditBox outputBox, inputBox, modBox, typeBox;
    private final List<String> matches = new ArrayList<>();
    private String status = "";
    private int cx, cy;

    public RecipeBulkRemoveScreen(Screen parent) {
        super(Component.literal(Lang.get("gui.kve.recipe.bulk.title")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        cx = (width - W) / 2;
        cy = (height - H) / 2;
        int x = cx + KveGuiStyle.PAD;
        int labelW = 90;
        int boxW = W - KveGuiStyle.PAD * 2 - labelW;
        int y = cy + KveGuiStyle.TITLEBAR_H + 6;
        outputBox = box(x + labelW, y, boxW, "gui.kve.recipe.bulk.output_hint", outputBox);
        y += 18;
        inputBox = box(x + labelW, y, boxW, "gui.kve.recipe.bulk.input_hint", inputBox);
        y += 18;
        modBox = box(x + labelW, y, boxW, "gui.kve.recipe.bulk.mod_hint", modBox);
        y += 18;
        typeBox = box(x + labelW, y, boxW, "gui.kve.recipe.bulk.type_hint", typeBox);

        int by = cy + H - KveGuiStyle.PAD - 16;
        addRenderableWidget(new KvePlateButton(cx + W - KveGuiStyle.PAD - 200, by, 120, 16,
                Component.literal(Lang.get("gui.kve.recipe.bulk.queue")), b -> confirmQueue()));
        addRenderableWidget(new KvePlateButton(cx + W - KveGuiStyle.PAD - 74, by, 74, 16,
                Component.literal(Lang.get("gui.kve.btn.cancel")), b -> onClose()));
        setFocused(outputBox);
        recompute();
    }

    private KveScaledEditBox box(int x, int y, int w, String hintKey, KveScaledEditBox previous) {
        KveScaledEditBox b = new KveScaledEditBox(font, x, y, w, 14, Component.literal(Lang.get(hintKey)));
        b.setMaxLength(128);
        b.setHint(Component.literal(Lang.get(hintKey)));
        if (previous != null) b.setValue(previous.getValue());
        b.setResponder(s -> recompute());
        addRenderableWidget(b);
        return b;
    }

    private static String norm(KveScaledEditBox b) {
        return b == null ? "" : b.getValue().trim().toLowerCase(Locale.ROOT);
    }

    private void recompute() {
        matches.clear();
        String out = norm(outputBox), in = norm(inputBox), mod = norm(modBox), type = norm(typeBox);
        if (out.isEmpty() && in.isEmpty() && mod.isEmpty() && type.isEmpty()) {
            status = Lang.get("gui.kve.recipe.bulk.need_filter");
            return;
        }
        if (minecraft == null || minecraft.getConnection() == null) { status = Lang.get("gui.kve.recipe.conflicts.no_world"); return; }
        RecipeManager mgr = minecraft.getConnection().getRecipeManager();
        RegistryAccess ra = minecraft.getConnection().registryAccess();
        List<String> removed = KubeJSExporter.getRemovedRecipes();
        for (Recipe<?> r : mgr.getRecipes()) {
            ResourceLocation id = r.getId();
            if (id == null || removed.contains(id.toString())) continue;
            if (OWN_NAMESPACE.equals(id.getNamespace())) continue;
            if (!mod.isEmpty() && !id.getNamespace().equals(mod)) continue;
            if (!type.isEmpty() && !matchesType(r, type)) continue;
            if (!out.isEmpty() && !matchesOutput(r, ra, out)) continue;
            if (!in.isEmpty() && !matchesInput(r, in)) continue;
            matches.add(id.toString());
        }
        matches.sort(String::compareTo);
        status = Lang.get("gui.kve.recipe.bulk.count", matches.size());
    }

    private static final String OWN_NAMESPACE = "shovel_js";

    private static boolean matchesType(Recipe<?> r, String type) {
        ResourceLocation t = BuiltInRegistries.RECIPE_TYPE.getKey(r.getType());
        ResourceLocation s = BuiltInRegistries.RECIPE_SERIALIZER.getKey(r.getSerializer());
        return (t != null && t.toString().contains(type)) || (s != null && s.toString().contains(type));
    }

    private static boolean idMatches(ItemStack s, String filter) {
        if (s == null || s.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
        if (id == null) return false;
        return filter.contains(":") ? id.toString().equals(filter) : id.getPath().contains(filter);
    }

    private static boolean matchesOutput(Recipe<?> r, RegistryAccess ra, String filter) {
        try { return idMatches(r.getResultItem(ra), filter); } catch (Throwable t) { return false; }
    }

    private static boolean matchesInput(Recipe<?> r, String filter) {
        try {
            for (Ingredient ing : r.getIngredients()) {
                for (ItemStack s : ing.getItems()) if (idMatches(s, filter)) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void confirmQueue() {
        if (!ClientPermissionState.canEdit()) { status = Lang.get("gui.kve.perm.denied"); return; }
        if (matches.isEmpty()) return;
        List<String> ids = List.copyOf(matches);
        String label = String.join(",", List.of(norm(outputBox), norm(inputBox), norm(modBox), norm(typeBox)));
        minecraft.setScreen(new KveConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        RecipeRemovalQueue.queueBulk(label, ids);
                        minecraft.setScreen(parent);
                    } else {
                        minecraft.setScreen(this);
                    }
                },
                Component.literal(Lang.get("gui.kve.recipe.bulk.confirm_title")),
                Component.literal(Lang.get("gui.kve.recipe.bulk.confirm_msg", ids.size()))));
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, H, getTitle().getString());
        int x = cx + KveGuiStyle.PAD;
        int y = cy + KveGuiStyle.TITLEBAR_H + 9;
        String[] labels = {"gui.kve.recipe.bulk.output", "gui.kve.recipe.bulk.input", "gui.kve.recipe.bulk.mod", "gui.kve.recipe.bulk.type"};
        for (String l : labels) {
            KveGuiStyle.drawTextSafe(g, Lang.get(l), x, y, 86, KveGuiStyle.TEXT_LABEL);
            y += 18;
        }
        y += 4;
        KveGuiStyle.drawTextSafe(g, status, x, y, W - KveGuiStyle.PAD * 2,
                matches.isEmpty() ? KveGuiStyle.TEXT_MUTED : KveGuiStyle.TEXT_WARN);
        y += 12;
        g.fill(x - 2, y - 2, cx + W - KveGuiStyle.PAD, y + PREVIEW_LINES * 10 + 2, KveGuiStyle.BG_FIELD);
        for (int i = 0; i < Math.min(PREVIEW_LINES, matches.size()); i++) {
            KveGuiStyle.drawTextSafe(g, matches.get(i), x, y + i * 10, W - KveGuiStyle.PAD * 2 - 4, KveGuiStyle.TEXT_PRIMARY);
        }
        if (matches.size() > PREVIEW_LINES) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.bulk.more", matches.size() - PREVIEW_LINES),
                    x, y + PREVIEW_LINES * 10 + 5, W - KveGuiStyle.PAD * 2, KveGuiStyle.TEXT_MUTED);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
