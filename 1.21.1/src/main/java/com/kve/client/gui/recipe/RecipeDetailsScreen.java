package com.kve.client.gui.recipe;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KveScaledEditBox;

import com.kve.common.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.BiConsumer;

/**
 * Categoria y notas de la receta que se esta editando. La
 * categoria alimenta el filtro {@code cat:} de la lista; las notas salen como
 * comentarios {@code // nota:} en el script (para el resto del equipo). En
 * una sola linea: {@code |} separa las lineas de la nota.
 */
@OnlyIn(Dist.CLIENT)
public class RecipeDetailsScreen extends Screen {

    private static final int W = 320;
    private static final int H = 150;

    private final Screen parent;
    private String category;
    private String notes;
    private final BiConsumer<String, String> onSave;
    private int cx, cy;

    /** @param notes con saltos de linea reales; se muestran con " | " */
    public RecipeDetailsScreen(Screen parent, String category, String notes, BiConsumer<String, String> onSave) {
        super(Component.literal(Lang.get("gui.kve.recipe.details.title")));
        this.parent = parent;
        this.category = category == null ? "" : category;
        this.notes = notes == null ? "" : notes.replace("\n", " | ");
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        cx = (width - W) / 2;
        cy = (height - H) / 2;
        int x = cx + KveGuiStyle.PAD;
        int w = W - KveGuiStyle.PAD * 2;
        int y = cy + KveGuiStyle.TITLEBAR_H + 14;
        KveScaledEditBox cat = new KveScaledEditBox(font, x, y, w, 12, Component.literal(Lang.get("gui.kve.recipe.details.category")));
        cat.setMaxLength(24);
        cat.setValue(category);
        cat.setHint(Component.literal(Lang.get("gui.kve.recipe.details.category_hint")));
        cat.setResponder(v -> category = v);
        addRenderableWidget(cat);
        KveScaledEditBox note = new KveScaledEditBox(font, x, y + 34, w, 12, Component.literal(Lang.get("gui.kve.recipe.details.notes")));
        note.setMaxLength(400);
        note.setValue(notes);
        note.setHint(Component.literal(Lang.get("gui.kve.recipe.details.notes_hint")));
        note.setResponder(v -> notes = v);
        addRenderableWidget(note);
        setInitialFocus(cat);
        KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(x, cy + H - KveGuiStyle.PAD - 16, cx + W - KveGuiStyle.PAD);
        addRenderableWidget(row.add(0, Lang.get("gui.kve.recipe.details.ok"), b -> {
            minecraft.setScreen(parent);
            onSave.accept(category.trim(), joinNotes(notes));
        }));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.cancel"), b -> onClose()));
    }

    static String joinNotes(String oneLine) {
        StringBuilder sb = new StringBuilder();
        for (String part : oneLine.split("\\|")) {
            if (part.isBlank()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(part.trim());
        }
        return sb.toString();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, H, getTitle().getString());
        int x = cx + KveGuiStyle.PAD;
        int y = cy + KveGuiStyle.TITLEBAR_H + 4;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.details.category"), x, y, W - 16, KveGuiStyle.TEXT_LABEL);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.details.notes"), x, y + 34, W - 16, KveGuiStyle.TEXT_LABEL);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.details.notes_help"), x, y + 62, W - 16, KveGuiStyle.TEXT_MUTED);
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
