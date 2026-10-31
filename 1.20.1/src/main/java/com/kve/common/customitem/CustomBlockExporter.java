package com.kve.common.customitem;

import com.kve.common.util.SafeFiles;
import com.kve.common.util.KveNumbers;
import com.google.gson.*;
import com.kve.CraftEditorMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CustomBlockExporter {

    private CustomBlockExporter() {}

    public static List<String> validateForExport(CustomBlockDraft d) {
        List<String> errors = new ArrayList<>(d.validateDraft());
        if (errors.isEmpty()) {
            try {
                ResourceLocation rl = new ResourceLocation(d.fullId());
                if (BuiltInRegistries.BLOCK.containsKey(rl)) {
                    errors.add(com.kve.common.Lang.get("gui.kve.blockcreator.id_taken"));
                }
            } catch (Exception ignored) {
            }
        }
        return errors;
    }

    public static String previewJs(CustomBlockDraft d) {
        if (d == null) return "// (sin datos de bloque)";
        String path = d.path();
        StringBuilder sb = new StringBuilder();
        sb.append("// Shovel JS - Custom Block (AUTO-GENERATED)\n");
        sb.append("// Apply with: RESTART/relaunch the world (block registration only happens at startup_scripts load)\n\n");
        sb.append("StartupEvents.registry('block', event => {\n");
        sb.append("  try {\n");
        sb.append("    event.create('").append(path).append("')\n");
        if (d.hardness >= 0)
            sb.append("      .hardness(").append(KveNumbers.padded4(d.hardness)).append(")\n");
        if (d.resistance >= 0)
            sb.append("      .resistance(").append(KveNumbers.padded4(d.resistance)).append(")\n");
        if (d.requiresTool)
            sb.append("      .requiresTool()\n");
        if (d.lightLevel >= 0)
            sb.append("      .lightLevel(").append(d.lightLevel).append(")\n");
        if (d.sound != null && !d.sound.isEmpty())
            sb.append("      .").append(d.sound).append("SoundType()\n");
        if (d.seeThrough)
            sb.append("      .defaultCutout()\n");
        if (d.noCollision)
            sb.append("      .noCollision()\n");
        if (d.slippery)
            sb.append("      .slipperiness(0.98)\n");
        if (d.slow)
            sb.append("      .speedFactor(0.4)\n");
        for (String t : blockTags(d)) sb.append("      .tagBlock('").append(CustomItemExporter.js(t)).append("')\n");
        sb.append("  } catch (ex) {\n");
        sb.append("    console.error('[Shovel JS] block create failed for ").append(d.fullId()).append(": ' + ex)\n");
        sb.append("  }\n");
        sb.append("})\n");
        return sb.toString();
    }

    /** Tags de herramienta (que herramienta lo pica y desde que nivel) mas los que escribio el jugador. */
    static List<String> blockTags(CustomBlockDraft d) {
        List<String> out = new ArrayList<>();
        if (d.toolType != null && !d.toolType.isEmpty()) out.add("minecraft:mineable/" + d.toolType);
        if (d.toolLevel != null && !d.toolLevel.isEmpty()) out.add("minecraft:needs_" + d.toolLevel + "_tool");
        for (String t : CustomItemDraft.splitTags(d.tags)) if (!out.contains(t)) out.add(t);
        return out;
    }

    private static String blockModelJson(String path) {
        JsonObject o = new JsonObject();
        o.addProperty("parent", "block/cube_all");
        JsonObject tex = new JsonObject();
        tex.addProperty("all", CraftEditorMod.NS + ":block/" + path);
        o.add("textures", tex);
        return new GsonBuilder().setPrettyPrinting().create().toJson(o) + "\n";
    }

    private static String itemModelJson(String path) {
        JsonObject o = new JsonObject();
        o.addProperty("parent", CraftEditorMod.NS + ":block/" + path);
        return new GsonBuilder().setPrettyPrinting().create().toJson(o) + "\n";
    }

    private static String blockstateJson(String path) {
        JsonObject o = new JsonObject();
        JsonObject variants = new JsonObject();
        JsonObject variant = new JsonObject();
        variant.addProperty("model", CraftEditorMod.NS + ":block/" + path);
        variants.add("", variant);
        o.add("variants", variants);
        return new GsonBuilder().setPrettyPrinting().create().toJson(o) + "\n";
    }

    private static Path scriptDir()     { return Paths.get("kubejs", "startup_scripts", "shovel_js", "custom_blocks"); }
    private static Path blockstateDir() { return Paths.get("kubejs", "assets", "shovel_js", "blockstates"); }
    private static Path blockModelDir() { return Paths.get("kubejs", "assets", "shovel_js", "models", "block"); }
    private static Path itemModelDir()  { return Paths.get("kubejs", "assets", "shovel_js", "models", "item"); }
    private static Path textureDir()    { return Paths.get("kubejs", "assets", "shovel_js", "textures", "block"); }
    private static Path langFile()      { return Paths.get("kubejs", "assets", "shovel_js", "lang", "en_us.json"); }

    private static final String PLACEHOLDER_RESOURCE = "/assets/shovel_js/textures/item_creator/placeholder.png";

    public static List<String> exportDraft(CustomBlockDraft d) {
        List<String> errors = validateForExport(d);
        if (!errors.isEmpty()) return errors;

        if (!com.kve.common.permission.EditPermission.canApply()) {
            errors.add(com.kve.common.Lang.get("gui.kve.perm.apply_denied"));
            return errors;
        }

        String path = d.path();
        if (!com.kve.common.security.KvePathGuard.isSafeRelativePath(path)) {
            errors.add("Id de bloque invalido (segmento de ruta no permitido): " + d.fullId());
            return errors;
        }
        try {
            Files.createDirectories(scriptDir());
            SafeFiles.writeAtomic(com.kve.common.security.KvePathGuard.resolveContained(scriptDir(), path + ".js"), previewJs(d));
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Custom block script write failed for {}: {}", d.fullId(), ex.getMessage());
            errors.add("Script write failed: " + ex.getMessage());
            return errors;
        }

        try {
            Files.createDirectories(blockstateDir());
            SafeFiles.writeAtomic(com.kve.common.security.KvePathGuard.resolveContained(blockstateDir(), path + ".json"), blockstateJson(path));
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Custom block blockstate write failed for {}: {}", d.fullId(), ex.getMessage());
            errors.add("Blockstate write failed: " + ex.getMessage());
            return errors;
        }

        try {
            Files.createDirectories(blockModelDir());
            SafeFiles.writeAtomic(com.kve.common.security.KvePathGuard.resolveContained(blockModelDir(), path + ".json"), blockModelJson(path));
            Files.createDirectories(itemModelDir());
            SafeFiles.writeAtomic(com.kve.common.security.KvePathGuard.resolveContained(itemModelDir(), path + ".json"), itemModelJson(path));
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Custom block model write failed for {}: {}", d.fullId(), ex.getMessage());
            errors.add("Model write failed: " + ex.getMessage());
            return errors;
        }

        try {
            copyTexture(d, com.kve.common.security.KvePathGuard.resolveContained(textureDir(), path + ".png"));
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Custom block texture copy failed for {}: {}", d.fullId(), ex.getMessage());
            errors.add("Texture copy failed: " + ex.getMessage());
            return errors;
        }

        try {
            mergeLang(d);
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Custom block lang merge failed for {}: {}", d.fullId(), ex.getMessage());
            errors.add("Lang merge failed: " + ex.getMessage());
            return errors;
        }

        d.exported = true;
        CustomBlockState.put(d);
        return errors;
    }

    private static void copyTexture(CustomBlockDraft d, Path dest) throws IOException {
        Files.createDirectories(dest.getParent());
        Path tmp = dest.resolveSibling(dest.getFileName().toString() + ".tmp");

        Path source = CustomItemExporter.resolveTrustedTextureSource(d.texturePath, d.texturePathTrusted);
        if (source != null && Files.isRegularFile(source)) {
            Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING);
        } else {
            try (InputStream in = CustomBlockExporter.class.getResourceAsStream(PLACEHOLDER_RESOURCE)) {
                if (in == null) throw new IOException("bundled placeholder texture missing from jar: " + PLACEHOLDER_RESOURCE);
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        SafeFiles.moveReplacing(tmp, dest);
    }

    private static void mergeLang(CustomBlockDraft d) throws IOException {
        Files.createDirectories(langFile().getParent());
        JsonObject root;
        if (Files.exists(langFile())) {
            try {
                root = JsonParser.parseString(Files.readString(langFile(), StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception ex) {
                root = new JsonObject();
            }
        } else {
            root = new JsonObject();
        }
        root.addProperty("block.shovel_js." + d.path(), d.displayName);
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n";
        SafeFiles.writeAtomic(langFile(), json);
    }
}
