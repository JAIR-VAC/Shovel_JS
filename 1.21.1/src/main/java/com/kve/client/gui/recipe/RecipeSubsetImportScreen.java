package com.kve.client.gui.recipe;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;

import com.kve.common.Lang;
import com.kve.client.permission.ClientPermissionState;
import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.persist.RecipeSubsetIO;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@OnlyIn(Dist.CLIENT)
public class RecipeSubsetImportScreen extends Screen {

    private static final int GUI_W = 260;
    private static final int GUI_H = 200;
    private static final int ROW_H = 14;

    private final Screen parent;
    private final List<String> files = new ArrayList<>();
    private String flashMsg = "";
    private long flashAt = 0;
    private int flashColor = 0xFF66FF99;

    public RecipeSubsetImportScreen(Screen parent) {
        super(Lang.c("gui.kve.subset.import.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int L = (width - GUI_W) / 2;
        int T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        files.clear();
        try {
            if (Files.isDirectory(RecipeSubsetIO.DEFAULT_DIR)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(RecipeSubsetIO.DEFAULT_DIR, "*.json")) {
                    for (Path p : ds) files.add(p.getFileName().toString());
                }
            }
        } catch (IOException ignored) {}
        Collections.sort(files);

        int y = T + 16;
        if (files.isEmpty()) {
        }
        for (String f : files) {
            if (y > T + GUI_H - 30) break;
            addRenderableWidget(new KvePlateButton(L + 6, y, GUI_W - 12, 12, net.minecraft.network.chat.Component.literal(
                            KveGuiStyle.truncate(font, f, GUI_W - 20)),
                    b -> doImport(RecipeSubsetIO.DEFAULT_DIR.resolve(f))));
            y += ROW_H;
        }

        addRenderableWidget(new KvePlateButton(L + 6, T + GUI_H - 16, GUI_W - 12, 12, Lang.c("gui.kve.btn.back"), b -> minecraft.setScreen(parent)));
    }

    private void doImport(Path file) {
        if (!ClientPermissionState.canEdit()) {
            flashColor = 0xFFFF6666;
            flashMsg = Lang.get("gui.kve.perm.denied");
            flashAt = System.currentTimeMillis();
            return;
        }
        List<RecipeSubsetIO.StagedRecipe> staged = RecipeSubsetIO.importSubsetMerge(file);
        if (staged == null) {
            flashColor = 0xFFFF6666;
            flashMsg = Lang.get("gui.kve.subset.import.fail");
            flashAt = System.currentTimeMillis();
            return;
        }
        for (RecipeSubsetIO.StagedRecipe s : staged) {
            PendingChange.Action action = s.previous() == null ? PendingChange.Action.CREATE : PendingChange.Action.EDIT;
            PendingChangeManager.queue(PendingChange.of(PendingDomain.RECIPES, s.recipeId(), action, false,
                    KubeJSExporter::commitAdded,
                    () -> KubeJSExporter.discardStagedRecipe(s.recipeId(), s.previous())));
        }
        flashColor = 0xFF66FF99;
        flashMsg = Lang.get("gui.kve.subset.import.done", staged.size()) + " - " + Lang.get("gui.kve.pending.queued_hint_reload");
        flashAt = System.currentTimeMillis();
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        int L = (width - GUI_W) / 2;
        int T = (height - GUI_H) / 2;
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.subset.import.title"));
        if (files.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.subset.import.none_found",
                    RecipeSubsetIO.DEFAULT_DIR.toAbsolutePath().toString()), L + 6, T + 20, GUI_W - 12, KveGuiStyle.th(0xFF667788));
        }
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4000) {
            KveGuiStyle.drawCenteredTextSafe(g, KveGuiStyle.truncate(font, flashMsg, GUI_W - 12), L + GUI_W / 2, T + GUI_H - 26, flashColor);
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override public boolean isPauseScreen() { return false; }
}
