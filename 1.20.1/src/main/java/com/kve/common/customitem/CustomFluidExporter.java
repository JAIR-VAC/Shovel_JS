package com.kve.common.customitem;

import com.kve.CraftEditorMod;
import com.kve.common.util.SafeFiles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Escribe el script de startup que registra un fluido con KubeJS. La textura es la de agua de
 * KubeJS tenida con {@link CustomFluidDraft#color}, asi que no hace falta elegir ningun PNG;
 * el cubo y el bloque del fluido los crea KubeJS solo.
 */
public final class CustomFluidExporter {

    private CustomFluidExporter() {}

    public static List<String> validateForExport(CustomFluidDraft d) {
        List<String> errors = new ArrayList<>(d.validateDraft());
        if (errors.isEmpty()) {
            try {
                ResourceLocation rl = new ResourceLocation(d.fullId());
                if (BuiltInRegistries.FLUID.containsKey(rl)) errors.add(com.kve.common.Lang.get("gui.kve.fluidcreator.id_taken"));
            } catch (Exception ignored) {
            }
        }
        return errors;
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "0x%06X", rgb & 0xFFFFFF);
    }

    public static String previewJs(CustomFluidDraft d) {
        if (d == null) return "// (sin datos de fluido)";
        StringBuilder sb = new StringBuilder();
        sb.append("// Shovel JS - Custom Fluid (AUTO-GENERATED)\n");
        sb.append("// Apply with: RESTART/relaunch the world (fluid registration only happens at startup_scripts load)\n\n");
        sb.append("StartupEvents.registry('fluid', event => {\n");
        sb.append("  try {\n");
        sb.append("    event.create('").append(d.path()).append("')\n");
        sb.append("      .").append(d.thick ? "thickTexture" : "thinTexture").append("(").append(hex(d.color)).append(")\n");
        sb.append("      .bucketColor(").append(hex(d.color)).append(")\n");
        if (!d.bucket) sb.append("      .noBucket()\n");
        for (String t : CustomItemDraft.splitTags(d.tags)) sb.append("      .tag('").append(CustomItemExporter.js(t)).append("')\n");
        sb.append("      .displayName('").append(CustomItemExporter.js(d.displayName.trim())).append("')\n");
        sb.append("  } catch (ex) {\n");
        sb.append("    console.error('[Shovel JS] fluid create failed for ").append(d.fullId()).append(": ' + ex)\n");
        sb.append("  }\n");
        sb.append("})\n");
        return sb.toString();
    }

    private static Path scriptDir() { return Paths.get("kubejs", "startup_scripts", "shovel_js", "custom_fluids"); }

    public static List<String> exportDraft(CustomFluidDraft d) {
        List<String> errors = validateForExport(d);
        if (!errors.isEmpty()) return errors;
        if (!com.kve.common.permission.EditPermission.canApply()) {
            errors.add(com.kve.common.Lang.get("gui.kve.perm.apply_denied"));
            return errors;
        }
        String path = d.path();
        if (!com.kve.common.security.KvePathGuard.isSafeRelativePath(path)) {
            errors.add("Id de fluido invalido (segmento de ruta no permitido): " + d.fullId());
            return errors;
        }
        try {
            Files.createDirectories(scriptDir());
            SafeFiles.writeAtomic(com.kve.common.security.KvePathGuard.resolveContained(scriptDir(), path + ".js"), previewJs(d));
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Custom fluid script write failed for {}: {}", d.fullId(), ex.getMessage());
            errors.add("Script write failed: " + ex.getMessage());
            return errors;
        }
        d.exported = true;
        CustomFluidState.put(d);
        return errors;
    }
}
