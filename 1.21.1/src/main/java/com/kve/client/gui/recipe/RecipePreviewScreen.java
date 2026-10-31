package com.kve.client.gui.recipe;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.picker.RawNbtTextArea;

import com.kve.common.Lang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * "Preview" window (point 5 of the interactivity pass): shows the exact
 * KubeJS script line(s) {@link com.kve.common.kubejs.KubeJSExporter} (or the
 * relevant per-entry exporter) would write for whatever's currently being
 * edited, without touching disk. Reuses {@link RawNbtTextArea} as the text
 * display/editor - it already handles wrapping-free line rendering,
 * scrolling (mouse wheel, click-drag, and now a real ScrollBar handle), and
 * in-place editing.
 * <p>
 * Read-only by default (every existing call site: recipes, tags, items,
 * blocks, loot tables, events, id-usage jumps) - just pass the 3-arg
 * constructor. MobEditorScreen's "Editar script" ask got folded
 * into this SAME screen/button instead of a separate one, to avoid two
 * near-identical "look at this mob's script" entry points - passing the
 * 5-arg constructor switches the textArea to editable and adds
 * Guardar/Restaurar generado buttons; {@code onSave} receives the edited
 * text on Guardar (it does not write to disk or close the screen itself -
 * same draft-field contract as everything else on the caller's screen), and
 * "Restaurar generado" resets the box back to {@code generatedDefault}.
 */
@OnlyIn(Dist.CLIENT)
public class RecipePreviewScreen extends Screen {

    private static final int GUI_W = 420;
    private static final int GUI_H = 220;

    private final Screen parent;
    private final String recipeId;
    private final String script;
    private final boolean editable;
    private final String generatedDefault;
    private final Consumer<String> onSave;

    private int L, T;
    private RawNbtTextArea textArea;
    private String flashMsg = "";
    private long flashAt = 0;

    public RecipePreviewScreen(Screen parent, String recipeId, String script) {
        this(parent, recipeId, script, null, null);
    }

    /**
     * Editable variant - see class javadoc. {@code generatedDefault} is
     * required (non-null) whenever {@code onSave} is non-null; it's what
     * auto-generation currently produces, used both by "Restaurar generado"
     * and to decide whether Guardar should actually record an override at
     * all (see {@link #doSave()}).
     */
    public RecipePreviewScreen(Screen parent, String recipeId, String script,
                                String generatedDefault, Consumer<String> onSave) {
        super(Lang.c("gui.kve.recipe.preview.title"));
        this.parent = parent;
        this.recipeId = recipeId == null ? "" : recipeId;
        this.script = script == null ? "" : script;
        this.onSave = onSave;
        this.editable = onSave != null;
        this.generatedDefault = generatedDefault == null ? "" : generatedDefault;
    }

    @Override
    protected void init() {
        L = (width  - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        int areaTop = editable ? T + 38 : T + 28;
        textArea = new RawNbtTextArea(font, L + 6, areaTop, GUI_W - 12, T + GUI_H - 24 - areaTop);
        textArea.setValue(script.isEmpty() && !editable ? "// (vacio)" : script);
        textArea.setEditable(editable);
        addRenderableWidget(textArea);
        if (editable) setFocused(textArea);

        int by = T + GUI_H - 20;
        if (editable) {
            addRenderableWidget(new KvePlateButton(L + 6, by, 70, 16, Lang.c("gui.kve.mob.script_override.save"), b -> doSave()));
            addRenderableWidget(new KvePlateButton(L + 80, by, 110, 16, Lang.c("gui.kve.mob.script_override.restore"), b -> doRestore()));
            addRenderableWidget(new KvePlateButton(L + 194, by, 70, 16, Lang.c("gui.kve.recipe.preview.copy"), b -> doCopy()));
        } else {
            addRenderableWidget(new KvePlateButton(L + 6, by, 90, 16, Lang.c("gui.kve.recipe.preview.copy"), b -> doCopy()));
        }
        addRenderableWidget(new KvePlateButton(L + GUI_W - 66, by, 60, 16, Lang.c("gui.kve.btn.back"), b -> minecraft.setScreen(parent)));
    }

    private void doCopy() {
        Minecraft.getInstance().keyboardHandler.setClipboard(textArea != null ? textArea.getValue() : script);
        flashMsg = Lang.get("gui.kve.recipe.preview.copied");
        flashAt = System.currentTimeMillis();
    }

    private void doSave() {
        if (onSave == null) return;
        String text = textArea.getValue();
        String normText = normalizeForCompare(text);
        String normGen = normalizeForCompare(generatedDefault);
        String toSave = (normText.isEmpty() || normText.equals(normGen)) ? "" : normText;
        onSave.accept(toSave);
        flashMsg = Lang.get("gui.kve.mob.script_override.saved_hint");
        flashAt = System.currentTimeMillis();
    }

    private static String normalizeForCompare(String s) {
        if (s == null) return "";
        return s.replace("\r\n", "\n").stripTrailing();
    }

    private void doRestore() {
        if (textArea != null) textArea.setValue(generatedDefault);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H,
                Lang.get("gui.kve.recipe.preview.title") + (recipeId.isEmpty() ? "" : " - " + recipeId));
        if (editable) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.script_override.hint"), L + 6, T + 26, GUI_W - 12, KveGuiStyle.TEXT_MUTED);
        }
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);

        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 3000) {
            int fx = editable ? L + 268 : L + 102;
            KveGuiStyle.drawTextSafe(g, flashMsg, fx, T + GUI_H - 20, L + GUI_W - 70 - fx, 0xFF66FF99);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (textArea != null && textArea.mouseScrolled(mx, my, scrollX, delta)) return true;
        return super.mouseScrolled(mx, my, scrollX, delta);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (textArea != null && textArea.mouseDragged(mx, my, btn, dx, dy)) return true;
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (textArea != null) textArea.mouseReleased(mx, my, btn);
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }
}
