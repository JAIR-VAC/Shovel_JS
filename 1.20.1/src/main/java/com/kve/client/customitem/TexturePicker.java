package com.kve.client.customitem;

import com.kve.CraftEditorMod;
import com.kve.common.Lang;
import com.kve.client.gui.TexturePickerScreen;
import com.kve.common.config.KVEConfig;
import com.kve.common.customitem.ImportRoot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Two entry points, chosen by {@link KVEConfig#filePicker}:
 * Opción A ({@link #openNative()}, OS-native dialog via LWJGL tinyfd) and
 * Opción B ({@link #openInGame()}, {@link TexturePickerScreen} anchored to
 * {@link ImportRoot}). {@link #open} is the only method callers outside
 * this package should use - it picks between the two and,
 * riesgo #6, transparently falls back Opción A -> B the first time tinyfd
 * throws (macOS threading quirks, missing native lib, headless/CI
 * environments, etc.) instead of ever showing the player a crash.
 * <p>
 * Also owns the live-preview texture lifecycle loading
 * an arbitrary PNG from disk into a real, renderable {@link ResourceLocation}
 * BEFORE the player has exported anything (so {@code CustomItemCreatorEditorScreen}
 * can show what the item will look like), and releasing it again so
 * repeated picks/screen closes don't leak {@link DynamicTexture}s into
 * video memory across an editing session.
 */
public final class TexturePicker {

    private TexturePicker() {}

    public static void open(Screen current, Consumer<String> onPicked) {
        ImportRoot.ensureExists();
        if ("ingame".equals(KVEConfig.filePicker)) {
            openInGame(current, onPicked);
        } else {
            openNative(current, onPicked);
        }
    }

    private static void openNative(Screen current, Consumer<String> onPicked) {
        String picked;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.png"));
            filters.flip();
            picked = TinyFileDialogs.tinyfd_openFileDialog(
                    Lang.get("gui.kve.itemcreator.picker.native_title"),
                    null,
                    filters,
                    Lang.get("gui.kve.itemcreator.picker.png_filter"),
                    false);
        } catch (Throwable t) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Native texture picker failed, switching to in-game picker for this session: {}", t.toString());
            KVEConfig.filePicker = "ingame";
            openInGame(current, onPicked);
            return;
        }
        if (picked == null || picked.isBlank()) return;
        onPicked.accept(Path.of(picked).toAbsolutePath().toString());
    }

    private static void openInGame(Screen current, Consumer<String> onPicked) {
        Minecraft.getInstance().setScreen(new TexturePickerScreen(current,
                relPath -> ImportRoot.resolveSafe(ImportRoot.root(), relPath)
                        .ifPresent(p -> onPicked.accept(p.toString()))));
    }

    public static Optional<ResourceLocation> loadPreview(Path pngFile) {
        if (pngFile == null || !Files.isRegularFile(pngFile)) return Optional.empty();
        try (InputStream in = Files.newInputStream(pngFile)) {
            NativeImage img = NativeImage.read(in);
            DynamicTexture tex;
            try {
                tex = new DynamicTexture(img);
            } catch (RuntimeException ex) {
                img.close();
                throw ex;
            }
            ResourceLocation loc = new ResourceLocation(CraftEditorMod.NS, "preview/" + UUID.randomUUID());
            Minecraft.getInstance().getTextureManager().register(loc, tex);
            return Optional.of(loc);
        } catch (IOException | RuntimeException ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not load texture preview {}: {}", pngFile, ex.getMessage());
            return Optional.empty();
        }
    }

    public static void releasePreview(ResourceLocation loc) {
        if (loc == null) return;
        try {
            Minecraft.getInstance().getTextureManager().release(loc);
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not release texture preview {}: {}", loc, ex.getMessage());
        }
    }
}
