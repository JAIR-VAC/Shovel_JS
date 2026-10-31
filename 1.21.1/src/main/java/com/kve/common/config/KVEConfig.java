package com.kve.common.config;

import com.kve.CraftEditorMod;
import net.neoforged.fml.ModList;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class KVEConfig {

    private KVEConfig() {}

    private static final Path FILE = Paths.get("config", "shovel_js.properties");

    public static boolean showStartupNotice = true;
    public static boolean showStudioOnboarding = true;
    public static String  showEcOptions     = "auto";
    public static int     backupCount       = 3;
    public static int     warnFileSizeKB    = 400;
    public static int     undoHistorySize   = 5;
    public static int     undoHistoryMaxKeys = 300;
    public static String  theme             = "bronze";
    /**
     * El tema por
     * defecto paso de cyan a bronze (cobre). Un config viejo ya trae
     * {@code theme=cyan} escrito aunque el jugador nunca lo eligio (se guardaba
     * el default), asi que sin esto nadie veria el cambio. Version 2 = el tema
     * guardado ya es una eleccion; un config sin esta clave con cyan pasa a cobre
     * una sola vez.
     */
    private static final int THEME_DEFAULT_VERSION = 2;
    public static String  customAccentColor = "";
    public static final java.util.List<String> savedAccentColors = new java.util.ArrayList<>();

    /** Agrega {@code hex} (RRGGBB) a {@link #savedAccentColors} si es valido y no estaba ya. */
    public static void addSavedAccentColor(String hex) {
        String h = normalizeHex(hex);
        if (h != null && !savedAccentColors.contains(h)) savedAccentColors.add(h);
    }

    /** RRGGBB en mayusculas, o null si {@code hex} no es un color de 6 digitos. */
    public static String normalizeHex(String hex) {
        if (hex == null) return null;
        String h = hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        return h.matches("[0-9A-Fa-f]{6}") ? h.toUpperCase(java.util.Locale.ROOT) : null;
    }
    public static float   uiTextScale       = 1.0f;

    /** Escala de GUI de las pantallas del mod: "auto" (se ajusta a la pantalla sin superar la del juego), "game" o "1".."4". */
    public static String uiScale = "auto";

    public static String keybindApply    = "ctrl:257";
    public static String keybindDiscard  = "ctrl:259";
    public static String keybindDiff     = "ctrl:68";
    public static String keybindNextTab  = "ctrl:258";
    public static String keybindPrevTab  = "ctrl+shift:258";

    public static boolean enableStructureEditor      = true;
    public static int     structureEditorMaxEdge     = 128;
    public static float   structureEditorMinZoom     = 3.0f;
    public static float   structureEditorMaxZoom     = 60.0f;
    public static float   structureEditorDefaultFov  = 60.0f;

    /** Comandos extra bloqueados en la accion RUN_COMMAND, ademas de la lista fija en EventEntry. Vacia por defecto. */
    public static List<String> runCommandExtraBlacklist = List.of();

    public static boolean eventsShowStubTriggers = false;

    /** "native" (default: OS file dialog via LWJGL tinyfd) or "ingame" (drawn-in-GUI browser anchored to filePickerImportDir). Also forced to "ingame" automatically at runtime the first time tinyfd itself throws - see {@code com.kve.client.customitem.TexturePicker}. */
    public static String filePicker = "native";
    /** Folder the in-game fallback browser (Opción B) is anchored to - see {@code com.kve.common.customitem.ImportRoot}. Relative to the game/run directory, same base kubejs/ and config/ already use. */
    public static String filePickerImportDir = "shovel_js_import";

    public static List<String> externalScanExtraDirs = List.of();

    public static boolean addExternalScanExtraDir(String dir) {
        if (dir == null) return false;
        String trimmed = dir.trim();
        if (trimmed.isEmpty()) return false;
        String normalized = trimmed.replace('\\', '/');
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.isEmpty()) return false;
        for (String existing : externalScanExtraDirs) {
            String existingNorm = existing.replace('\\', '/');
            while (existingNorm.endsWith("/")) existingNorm = existingNorm.substring(0, existingNorm.length() - 1);
            if (existingNorm.equalsIgnoreCase(normalized)) return false;
        }
        List<String> updated = new ArrayList<>(externalScanExtraDirs);
        updated.add(normalized);
        externalScanExtraDirs = List.copyOf(updated);
        save();
        return true;
    }

    private static Boolean ecDetected  = null;

    public static void load() {
        resetDetectionCache();

        if (!Files.exists(FILE)) {
            writeDefaults();
            return;
        }
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not read config: {}", e.getMessage());
            return;
        }
        showStartupNotice = Boolean.parseBoolean(p.getProperty("show_startup_notice", "true"));
        showStudioOnboarding = Boolean.parseBoolean(p.getProperty("show_studio_onboarding", "true"));
        showEcOptions     = p.getProperty("show_ec_options", "auto").trim().toLowerCase();
        if (!showEcOptions.equals("true") && !showEcOptions.equals("false"))
            showEcOptions = "auto";
        backupCount     = parseIntSafe(p.getProperty("backup_count", "3"), 3);
        warnFileSizeKB  = parseIntSafe(p.getProperty("warn_file_size_kb", "400"), 400);
        undoHistorySize = parseIntSafe(p.getProperty("undo_history_size", "5"), 5);
        undoHistoryMaxKeys = parseIntSafe(p.getProperty("undo_history_max_keys", "300"), 300);
        theme = p.getProperty("theme", "bronze").trim().toLowerCase();
        if (parseIntSafe(p.getProperty("theme_default_version", "1"), 1) < THEME_DEFAULT_VERSION && theme.equals("cyan")) {
            theme = "bronze";
        }
        customAccentColor = p.getProperty("custom_accent_color", "").trim();
        savedAccentColors.clear();
        for (String part : p.getProperty("saved_accent_colors", "").split(",")) addSavedAccentColor(part);
        uiTextScale = parseFloatSafe(p.getProperty("ui_text_scale", "1.0"), 1.0f);
        uiScale = p.getProperty("ui_scale", "auto").trim();
        keybindApply    = p.getProperty("keybind_apply", keybindApply).trim();
        keybindDiscard  = p.getProperty("keybind_discard", keybindDiscard).trim();
        keybindDiff     = p.getProperty("keybind_diff", keybindDiff).trim();
        keybindNextTab  = p.getProperty("keybind_next_tab", keybindNextTab).trim();
        keybindPrevTab  = p.getProperty("keybind_prev_tab", keybindPrevTab).trim();
        applyThemeIfClient();

        enableStructureEditor  = Boolean.parseBoolean(p.getProperty("enable_structure_editor", "true"));
        int rawMaxEdge = parseIntSafe(p.getProperty("structure_editor_max_edge", "128"), 128);
        if (rawMaxEdge == 32) rawMaxEdge = 128;
        structureEditorMaxEdge = clampInt(rawMaxEdge, 16, 128);
        structureEditorMinZoom = parseFloatSafe(p.getProperty("structure_editor_min_zoom", "3.0"), 3.0f);
        structureEditorMaxZoom = parseFloatSafe(p.getProperty("structure_editor_max_zoom", "60.0"), 60.0f);
        structureEditorDefaultFov = parseFloatSafe(p.getProperty("structure_editor_default_fov", "60.0"), 60.0f);

        runCommandExtraBlacklist = parseCsv(p.getProperty("run_command_extra_blacklist", ""));
        eventsShowStubTriggers = Boolean.parseBoolean(p.getProperty("events_show_stub_triggers", "false"));

        filePicker = p.getProperty("file_picker", "native").trim().toLowerCase(Locale.ROOT);
        if (!filePicker.equals("native") && !filePicker.equals("ingame")) filePicker = "native";
        filePickerImportDir = p.getProperty("file_picker_import_dir", "shovel_js_import").trim();
        if (filePickerImportDir.isEmpty()) filePickerImportDir = "shovel_js_import";

        externalScanExtraDirs = parsePathCsv(p.getProperty("external_scan_extra_dirs", ""));

        CraftEditorMod.LOGGER.info("[Shovel JS] Config loaded. startup_notice={} ec_options={} backups={} undo={}",
                showStartupNotice, showEcOptions, backupCount, undoHistorySize);
    }

    private static int parseIntSafe(String s, int def) {
        try { return Math.max(0, Integer.parseInt(s.trim())); } catch (Exception e) { return def; }
    }

    private static float parseFloatSafe(String s, float def) {
        try { return Float.parseFloat(s.trim()); } catch (Exception e) { return def; }
    }

    private static List<String> parseCsv(String s) {
        if (s == null || s.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : s.split(",")) {
            String v = part.trim().toLowerCase(Locale.ROOT);
            if (v.startsWith("/")) v = v.substring(1);
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static List<String> parsePathCsv(String s) {
        if (s == null || s.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : s.split(",")) {
            String v = part.trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }

    /**
     * (separacion runtime/editor): aplicar tema, escala de texto y
     * atajos es cosa de la interfaz, que vive en el editor (kve_editor). El
     * editor instala este gancho al arrancar y lo corre una vez; despues
     * KVEConfig lo llama en cada load()/save(). Sin editor no hay interfaz.
     */
    public static volatile Runnable clientUiHook = null;

    private static void applyThemeIfClient() {
        Runnable hook = clientUiHook;
        if (hook != null) hook.run();
    }

    private static boolean isClientDist() {
        try {
            return net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());

            StringBuilder sb = new StringBuilder();
            sb.append("# Shovel JS - Configuration\n");
            sb.append("#\n");
            sb.append("# show_startup_notice\n");
            sb.append("#   true  = Show the startup info screen when joining a world (default)\n");
            sb.append("#   false = Skip the startup screen\n");
            sb.append("show_startup_notice=").append(showStartupNotice).append("\n");
            sb.append("#\n");
            sb.append("# show_studio_onboarding\n");
            sb.append("#   true  = Show the \"quick start\" screen the first time Shovel JS opens (default)\n");
            sb.append("#   false = Skip the onboarding screen\n");
            sb.append("show_studio_onboarding=").append(showStudioOnboarding).append("\n");
            sb.append("#\n");
            sb.append("# show_ec_options\n");
            sb.append("#   auto  = Show Extended Crafting options only if the mod is installed (default)\n");
            sb.append("#   true  = Always show Extended Crafting options\n");
            sb.append("#   false = Never show Extended Crafting options\n");
            sb.append("show_ec_options=").append(showEcOptions).append("\n");
            sb.append("#\n");
            sb.append("# backup_count - number of rotating backups of shovel_js_state.json to keep (0 = disabled)\n");
            sb.append("backup_count=").append(backupCount).append("\n");
            sb.append("#\n");
            sb.append("# warn_file_size_kb - log a warning when the state file exceeds this size in KB\n");
            sb.append("warn_file_size_kb=").append(warnFileSizeKB).append("\n");
            sb.append("#\n");
            sb.append("# undo_history_size - how many previous versions to keep per entry for Undo (session only)\n");
            sb.append("undo_history_size=").append(undoHistorySize).append("\n");
            sb.append("#\n");
            sb.append("# undo_history_max_keys - max number of distinct entities/items/tags/loot tables\n");
            sb.append("# that can have undo history tracked at once (session only). Oldest-touched entries\n");
            sb.append("# are dropped first once this is exceeded, so very long sessions editing many\n");
            sb.append("# different things don't grow undo memory usage without bound.\n");
            sb.append("undo_history_max_keys=").append(undoHistoryMaxKeys).append("\n");
            sb.append("#\n");
            sb.append("# theme - accent palette for Shovel JS screens: bronze (default) | cyan | gold | purple | green | dark | silver | sage | slate | white\n");
            sb.append("theme=").append(theme).append("\n");
            sb.append("theme_default_version=").append(THEME_DEFAULT_VERSION).append("\n");
            sb.append("#\n");
            sb.append("# custom_accent_color - player-picked hex accent color (RRGGBB, no '#'),\n");
            sb.append("# overrides the theme above when set. Empty = use the theme's own colors.\n");
            sb.append("# Set from Settings -> Appearance -> Custom accent color.\n");
            sb.append("custom_accent_color=").append(customAccentColor).append("\n");
            sb.append("# saved_accent_colors - custom colors saved from Settings (RRGGBB, comma-separated);\n");
            sb.append("# the Theme button cycles through them after the built-in themes.\n");
            sb.append("saved_accent_colors=").append(String.join(",", savedAccentColors)).append("\n");
            sb.append("# ui_text_scale - global text scale for Shovel JS screens: 1.0 (default) | 0.8 (compacto)\n");
            sb.append("ui_text_scale=").append(uiTextScale).append("\n");
            sb.append("# ui_scale - GUI scale for Shovel JS screens: auto (fits the window) | game | 1-4\n");
            sb.append("ui_scale=").append(uiScale).append("\n");
            sb.append("#\n");
            sb.append("# keybind_* - atajos de teclado de Shovel JS, formato\n");
            sb.append("# \"<mods>:<glfw_keycode>\" - editar desde Settings -> Keybinds, no a mano.\n");
            sb.append("keybind_apply=").append(keybindApply).append("\n");
            sb.append("keybind_discard=").append(keybindDiscard).append("\n");
            sb.append("keybind_diff=").append(keybindDiff).append("\n");
            sb.append("keybind_next_tab=").append(keybindNextTab).append("\n");
            sb.append("keybind_prev_tab=").append(keybindPrevTab).append("\n");
            sb.append("#\n");
            sb.append("# enable_structure_editor - turn the Structure Voxel Editor (Estructuras) on/off\n");
            sb.append("enable_structure_editor=").append(enableStructureEditor).append("\n");
            sb.append("#\n");
            sb.append("# structure_editor_max_edge - soft cap (blocks) per axis for a structure, 16-128\n");
            sb.append("structure_editor_max_edge=").append(structureEditorMaxEdge).append("\n");
            sb.append("#\n");
            sb.append("# structure_editor_min_zoom / max_zoom - orbit camera distance clamp (blocks)\n");
            sb.append("structure_editor_min_zoom=").append(structureEditorMinZoom).append("\n");
            sb.append("structure_editor_max_zoom=").append(structureEditorMaxZoom).append("\n");
            sb.append("#\n");
            sb.append("# structure_editor_default_fov - viewport field of view, degrees\n");
            sb.append("structure_editor_default_fov=").append(structureEditorDefaultFov).append("\n");
            sb.append("#\n");
            sb.append("# run_command_extra_blacklist - comandos extra (separados por coma) bloqueados en\n");
            sb.append("# la accion RUN_COMMAND de Eventos, ademas de la lista fija (op/deop/stop/whitelist/\n");
            sb.append("# ban/etc - ver EventEntry.RUN_COMMAND_BLACKLIST). Ej: give,tp,effect\n");
            sb.append("run_command_extra_blacklist=").append(String.join(",", runCommandExtraBlacklist)).append("\n");
            sb.append("#\n");
            sb.append("# events_show_stub_triggers - show event triggers whose KubeJS export isn't\n");
            sb.append("# confirmed yet (currently ITEM_PICKUP, ITEM_CRAFT) in the Trigger picker.\n");
            sb.append("# false = hidden by default (default); true = show them anyway\n");
            sb.append("events_show_stub_triggers=").append(eventsShowStubTriggers).append("\n");
            sb.append("#\n");
            sb.append("# file_picker - how the Item Creator's texture picker opens a file\n");
            sb.append("#   native = OS file dialog (default) - no extra UI, but needs filesystem\n");
            sb.append("#            access from the game process (may not work in some sandboxed\n");
            sb.append("#            launchers/containers)\n");
            sb.append("#   ingame = a file browser drawn in the GUI, anchored to file_picker_import_dir\n");
            sb.append("file_picker=").append(filePicker).append("\n");
            sb.append("#\n");
            sb.append("# file_picker_import_dir - folder the in-game file browser (file_picker=ingame,\n");
            sb.append("# or the automatic fallback if the OS dialog fails) is anchored to - drop PNGs\n");
            sb.append("# there to pick them as item textures. Relative to this game/run directory.\n");
            sb.append("file_picker_import_dir=").append(filePickerImportDir).append("\n");
            sb.append("#\n");
            sb.append("# external_scan_extra_dirs - extra folders (comma-separated, relative to this\n");
            sb.append("# game/run directory) the External Script Scan (Shovel JS -> Scan tab) also\n");
            sb.append("# checks, in addition to the 3 fixed kubejs/server_scripts, kubejs/startup_scripts\n");
            sb.append("# and kubejs/client_scripts. Empty by default. Ej: kubejs/my_addon_scripts\n");
            sb.append("external_scan_extra_dirs=").append(String.join(",", externalScanExtraDirs)).append("\n");
            Files.writeString(FILE, sb, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not save config: {}", e.getMessage());
        }
    }

    private static void writeDefaults() {
        showStartupNotice = true;
        showStudioOnboarding = true;
        showEcOptions     = "auto";
        backupCount       = 3;
        warnFileSizeKB    = 400;
        undoHistorySize   = 5;
        undoHistoryMaxKeys = 300;
        theme             = "bronze";
        customAccentColor = "";
        savedAccentColors.clear();
        uiTextScale       = 1.0f;
        uiScale = "auto";
        applyThemeIfClient();
        enableStructureEditor  = true;
        structureEditorMaxEdge = 128;
        structureEditorMinZoom = 3.0f;
        structureEditorMaxZoom = 60.0f;
        structureEditorDefaultFov = 60.0f;
        runCommandExtraBlacklist = List.of();
        eventsShowStubTriggers = false;
        externalScanExtraDirs = List.of();
        save();
    }

    public static boolean isExtendedCraftingActive() {
        if (showEcOptions.equals("true"))  return true;
        if (showEcOptions.equals("false")) return false;

        if (ecDetected == null) {
            try {
                ecDetected = ModList.get().isLoaded("extendedcrafting");
            } catch (Exception e) {
                ecDetected = false;
            }
            CraftEditorMod.LOGGER.info("[Shovel JS] ExtendedCrafting detected: {}", ecDetected);
        }
        return ecDetected;
    }

    public static void resetDetectionCache() {
        ecDetected = null;
    }
}
