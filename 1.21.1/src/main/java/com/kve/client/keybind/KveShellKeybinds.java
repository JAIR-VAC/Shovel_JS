package com.kve.client.keybind;

import com.kve.common.config.KVEConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public final class KveShellKeybinds {

    private KveShellKeybinds() {}

    public enum Action {
        APPLY("gui.kve.studio.keybind.apply", GLFW.GLFW_KEY_ENTER, true, false),
        DISCARD("gui.kve.studio.keybind.discard", GLFW.GLFW_KEY_BACKSPACE, true, false),
        DIFF("gui.kve.studio.keybind.diff", GLFW.GLFW_KEY_D, true, false),
        NEXT_TAB("gui.kve.studio.keybind.next_tab", GLFW.GLFW_KEY_TAB, true, false),
        PREV_TAB("gui.kve.studio.keybind.prev_tab", GLFW.GLFW_KEY_TAB, true, true);

        public final String labelKey;
        public final int defaultKey;
        public final boolean defaultCtrl, defaultShift;

        private int key;
        private boolean ctrl, shift;

        Action(String labelKey, int defaultKey, boolean defaultCtrl, boolean defaultShift) {
            this.labelKey = labelKey;
            this.defaultKey = defaultKey;
            this.defaultCtrl = defaultCtrl;
            this.defaultShift = defaultShift;
            this.key = defaultKey;
            this.ctrl = defaultCtrl;
            this.shift = defaultShift;
        }

        /** Called from {@code KveStudioShell#keyPressed} for every action, in order, after the active tab already had first refusal at the event. */
        public boolean matches(int keyCode, int mods) {
            if (keyCode != key) return false;
            boolean c = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
            boolean s = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
            return c == ctrl && s == shift;
        }

        /** Called from the "press a key..." capture row in KveSettingsScreen. Alt is deliberately ignored (not tracked) - same 2-modifier scope as every other keyboard shortcut already in this codebase (Ctrl(+Shift)). */
        public void rebind(int keyCode, int mods) {
            key = keyCode;
            ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
            shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        }

        public void resetToDefault() {
            key = defaultKey;
            ctrl = defaultCtrl;
            shift = defaultShift;
        }

        /** Human-readable label, e.g. "Ctrl+Enter" - reuses the same key-name lookup vanilla's own KeyMapping/Controls screen relies on (InputConstants), so it matches the player's actual keyboard layout instead of a hardcoded US-layout name. */
        public String displayString() {
            StringBuilder sb = new StringBuilder();
            if (ctrl) sb.append("Ctrl+");
            if (shift) sb.append("Shift+");
            sb.append(InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString());
            return sb.toString();
        }

        String encode() {
            String mods = (ctrl ? "ctrl" : "") + (shift ? (ctrl ? "+shift" : "shift") : "");
            if (mods.isEmpty()) mods = "none";
            return mods + ":" + key;
        }

        void decode(String s) {
            if (s == null || s.isBlank()) return;
            try {
                String[] parts = s.split(":", 2);
                ctrl = parts[0].contains("ctrl");
                shift = parts[0].contains("shift");
                key = Integer.parseInt(parts[1].trim());
            } catch (Exception ignored) {
                resetToDefault();
            }
        }
    }

    /**
     * Called once from {@code KVEConfig}'s client-only theme/scale bootstrap
     * (same {@code DistExecutor} guard as {@code applyThemeIfClient()} -
     * {@link InputConstants} is a client-only/LWJGL type, can't load on a
     * dedicated server).
     */
    public static void loadFromConfig() {
        Action.APPLY.decode(KVEConfig.keybindApply);
        Action.DISCARD.decode(KVEConfig.keybindDiscard);
        Action.DIFF.decode(KVEConfig.keybindDiff);
        Action.NEXT_TAB.decode(KVEConfig.keybindNextTab);
        Action.PREV_TAB.decode(KVEConfig.keybindPrevTab);
    }

    /** Called right before {@code KVEConfig.save()} any time a binding changes from the Keybinds section of Settings. */
    public static void saveToConfig() {
        KVEConfig.keybindApply = Action.APPLY.encode();
        KVEConfig.keybindDiscard = Action.DISCARD.encode();
        KVEConfig.keybindDiff = Action.DIFF.encode();
        KVEConfig.keybindNextTab = Action.NEXT_TAB.encode();
        KVEConfig.keybindPrevTab = Action.PREV_TAB.encode();
    }
}
