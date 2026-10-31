package com.kve.client.gui.code;

import com.kve.CraftEditorMod;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.function.Consumer;

public final class KveFilePicker {

    private KveFilePicker() {}

    /**
     * @param title       native dialog window title
     * @param filters     glob patterns, e.g. {"*.json", "*.js"}
     * @param filterLabel human-readable description shown next to the filters
     * @param onPicked    receives the chosen file's absolute path; never called if cancelled
     * @param onFailure   called instead of onPicked if the native dialog itself couldn't be shown
     */
    public static void open(String title, String[] filters, String filterLabel,
                             Consumer<Path> onPicked, Runnable onFailure) {
        String picked;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer buf = stack.mallocPointer(filters.length);
            for (String f : filters) buf.put(stack.UTF8(f));
            buf.flip();
            picked = TinyFileDialogs.tinyfd_openFileDialog(title, null, buf, filterLabel, false);
        } catch (Throwable t) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Native file picker failed: {}", t.toString());
            if (onFailure != null) onFailure.run();
            return;
        }
        if (picked == null || picked.isBlank()) return;
        onPicked.accept(Path.of(picked).toAbsolutePath());
    }
}
