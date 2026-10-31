package com.kve.client.structure;

import com.kve.common.structure.StructureVoxelGrid;

import com.kve.common.Lang;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.RawNbtTextArea;
import net.minecraft.client.gui.GuiGraphics;
import com.kve.client.gui.KvePlateButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * A small raw-SNBT
 * editor for one cell's preserved block-entity data within the Structure
 * Voxel Editor. Deliberately mirrors {@code NbtEditorScreen}'s "Raw" tab
 * ({@link RawNbtTextArea} + {@link TagParser}, same validate-on-save flow)
 * rather than building per-block-type structured fields (chest slots,
 * spawner config, sign text, ...) - same "editar TE en GUI" scope line
 * {@link StructureVoxelGrid}'s class doc already draws for v1: round-trip
 * whatever nbt is there and let the player hand-edit it as text, instead of
 * trying to model every mod's block-entity schema individually.
 * <p>
 * x/y/z are re-written on save regardless of what the player typed (or
 * left out) - vanilla structure NBT stores a block entity's local position
 * redundantly inside its own tag, and {@link StructureVoxelGrid#normalizeOrigin}/
 * {@code rotate90} already keep that in sync automatically whenever a cell
 * moves for the exact same reason (see their {@code withPos} helper); doing
 * it here too means a stale/omitted position in hand-typed SNBT can never
 * silently break on the next load.
 */
@OnlyIn(Dist.CLIENT)
public class StructureBlockEntityNbtScreen extends Screen {

    private static final int GUI_W = 360;
    private static final int GUI_H = 216;
    private static final int BOX_H = 140;
    private static final int RAW_MAX_CHARS = 8000;

    private final Screen parent;
    private final BlockState state;
    private final BlockPos pos;
    private final CompoundTag original;
    private final Consumer<CompoundTag> onSave;

    private RawNbtTextArea textArea;
    private String errorMsg = "";

    /**
     * @param existing preserved nbt for this cell, or null if it has none yet (a freshly palette-placed entity block, or one that was never given data)
     * @param onSave   called with the parsed+position-synced tag (or null to clear it) once the player presses Save with valid SNBT
     */
    public StructureBlockEntityNbtScreen(Screen parent, BlockState state, BlockPos pos, CompoundTag existing, Consumer<CompoundTag> onSave) {
        super(Lang.c("gui.kve.structure.nbt.title"));
        this.parent = parent;
        this.state = state;
        this.pos = pos.immutable();
        this.original = existing;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        clearWidgets();
        int guiLeft = (width - GUI_W) / 2;
        int guiTop = (height - GUI_H) / 2;
        int cx = guiLeft + KveGuiStyle.PAD;
        int cy = guiTop + 20;

        textArea = new RawNbtTextArea(font, cx, cy, GUI_W - KveGuiStyle.PAD * 2, BOX_H);
        textArea.setMaxLength(RAW_MAX_CHARS);
        textArea.setValue(original != null ? original.toString() : "{}");
        addRenderableWidget(textArea);
        setFocused(textArea);

        int by = cy + BOX_H + 8;
        addRenderableWidget(new KvePlateButton(cx, by, 90, 16, Lang.c("gui.kve.btn.save"), b -> doSave()).withPrimary(true));
        addRenderableWidget(new KvePlateButton(cx + 94, by, 110, 16, Lang.c("gui.kve.structure.nbt.btn.clear_data"), b -> {
            textArea.setValue("{}");
            errorMsg = "";
        }));
        addRenderableWidget(new KvePlateButton(guiLeft + GUI_W - KveGuiStyle.PAD - 90, by, 90, 16, Component.literal(Lang.get("gui.kve.btn.close")), b -> onClose()));
    }

    private void doSave() {
        String text = textArea.getValue().trim();
        try {
            CompoundTag parsed = (text.isEmpty() || text.equals("{}")) ? null : TagParser.parseTag(text);
            if (parsed != null) {
                parsed.putInt("x", pos.getX());
                parsed.putInt("y", pos.getY());
                parsed.putInt("z", pos.getZ());
            }
            onSave.accept(parsed);
            errorMsg = "";
            minecraft.setScreen(parent);
        } catch (Throwable ex) {
            // Throwable, not just Exception: TagParser is recursive-descent, so a
            // pathologically nested pasted string throws StackOverflowError rather
            // than a normal parse Exception - same defensive catch NbtEditorScreen
            // uses for its own Raw tab.
            errorMsg = ex instanceof StackOverflowError
                    ? Lang.get("gui.kve.structure.nbt.too_nested")
                    : (ex.getMessage() != null ? ex.getMessage() : Lang.get("gui.kve.structure.nbt.invalid"));
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (textArea != null && textArea.mouseScrolled(mx, my, delta)) return true;
        return super.mouseScrolled(mx, my, delta);
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
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int guiLeft = (width - GUI_W) / 2;
        int guiTop = (height - GUI_H) / 2;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String blockLabel = blockId != null ? blockId.toString() : state.getBlock().getName().getString();
        String title = Lang.get("gui.kve.structure.nbt.title") + " - " + blockLabel
                + " (" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
        KveGuiStyle.drawWindow(g, guiLeft, guiTop, GUI_W, GUI_H, title);
        super.render(g, mx, my, pt);
        if (!errorMsg.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, errorMsg, guiLeft + KveGuiStyle.PAD, guiTop + GUI_H - 12,
                    GUI_W - KveGuiStyle.PAD * 2, KveGuiStyle.TEXT_ERROR);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
