package com.kve.common.customitem;

import com.kve.common.util.SafeFiles;
import com.kve.common.util.KveNumbers;
import com.google.gson.*;
import com.kve.CraftEditorMod;
import com.kve.common.kubejs.KubeJSItemAttrExporter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public final class CustomItemExporter {

    private CustomItemExporter() {}

    /**
     * {@link CustomItemDraft#validateDraft()} plus the one check that needs
     * a live registry: does an item with this id already exist (vanilla or
     * any loaded mod, including {@code kve:} itself from a previous
     * export)? Kept separate from {@code validateDraft()} so that method
     * stays usable from plain JUnit tests with no registries bootstrapped.
     */
    public static List<String> validateForExport(CustomItemDraft d) {
        List<String> errors = new ArrayList<>(d.validateDraft());
        if (errors.isEmpty()) {
            try {
                ResourceLocation rl = ResourceLocation.parse(d.fullId());
                if (BuiltInRegistries.ITEM.containsKey(rl)) {
                    errors.add(com.kve.common.Lang.get("gui.kve.itemcreator.id_taken"));
                }
            } catch (Exception ignored) {
            }
        }
        return errors;
    }

    public static String previewJs(CustomItemDraft d) {
        if (d == null) return "// (sin datos de item)";
        String path = d.path();
        StringBuilder sb = new StringBuilder();
        sb.append("// Shovel JS - Custom Item - AUTO-GENERATED\n");
        sb.append("// Apply with: RESTART/relaunch the world (item registration only happens at startup_scripts load)\n\n");
        sb.append("StartupEvents.registry('item', event => {\n");
        if (d.isTool()) {
            sb.append("  event.create('").append(path).append("', '").append(d.itemType).append("')\n");
            sb.append("    .tier('").append(d.toolTier == null || d.toolTier.isEmpty() ? "iron" : d.toolTier).append("')\n");
        } else {
            sb.append("  event.create('").append(path).append("')\n");
        }
        if (d.maxStackSize > 0 && d.maxStackSize != 64)
            sb.append("    .maxStackSize(").append(d.maxStackSize).append(")\n");
        if (d.maxDamage > 0)
            sb.append("    .maxDamage(").append(d.maxDamage).append(")\n");
        if (d.fireResistant)
            sb.append("    .fireResistant()\n");
        String rarity = d.rarity();
        if (!rarity.isEmpty())
            sb.append("    .rarity('").append(rarity.toLowerCase(java.util.Locale.ROOT)).append("')\n");
        if (d.glow)
            sb.append("    .glow(true)\n");
        if (d.burnTime > 0)
            sb.append("    .burnTime(").append(d.burnTime).append(")\n");
        if (d.tooltip != null && !d.tooltip.isBlank())
            sb.append("    .tooltip('").append(js(d.tooltip.trim())).append("')\n");
        List<String> tags = d.tagList();
        if (!tags.isEmpty()) {
            sb.append("    .tag([");
            for (int i = 0; i < tags.size(); i++) sb.append(i == 0 ? "'" : ", '").append(js(tags.get(i))).append("'");
            sb.append("])\n");
        }
        if (d.food) {
            sb.append("    .food(food => food.nutrition(").append(Math.max(0, d.foodHunger)).append(")")
              .append(".saturation(").append(KveNumbers.padded4(Math.max(0f, d.foodSaturation))).append(")");
            if (d.foodAlwaysEdible) sb.append(".alwaysEdible()");
            if (d.foodFast) sb.append(".fastToEat()");
            sb.append(")\n");
        }
        sb.append("})\n");

        if (!d.attributeModifiers.isEmpty()) {
            sb.append("\n");
            appendAttributeModifiers(sb, d);
        }
        return sb.toString();
    }

    private static void appendAttributeModifiers(StringBuilder sb, CustomItemDraft d) {
        sb.append("// ").append(d.fullId()).append(": ").append(d.attributeModifiers.size())
          .append(" atributo(s) aplicados por Shovel JS en Java (ItemAttributeEnforcer)\n");
    }

    /** Texto para meter entre comillas simples en el script. */
    static String js(String s) {
        return com.kve.common.util.JsStrings.escape(s);
    }

    private static String modelJson(String path, boolean tool) {
        JsonObject o = new JsonObject();
        // las herramientas se ven en diagonal en la mano, como las de vanilla
        o.addProperty("parent", tool ? "item/handheld" : "item/generated");
        JsonObject tex = new JsonObject();
        tex.addProperty("layer0", CraftEditorMod.NS + ":item/" + path);
        o.add("textures", tex);
        return new GsonBuilder().setPrettyPrinting().create().toJson(o) + "\n";
    }

    private static Path scriptDir()  { return Paths.get("kubejs", "startup_scripts", "shovel_js", "custom_items"); }
    private static Path modelDir()   { return Paths.get("kubejs", "assets", "shovel_js", "models", "item"); }
    private static Path textureDir() { return Paths.get("kubejs", "assets", "shovel_js", "textures", "item"); }
    private static Path langFile()   { return Paths.get("kubejs", "assets", "shovel_js", "lang", "en_us.json"); }

    private static final String PLACEHOLDER_RESOURCE = "/assets/shovel_js/textures/item_creator/placeholder.png";

    /**
     * Writes the per-item startup script, copies the texture (chosen PNG
     * or the bundled placeholder), writes the item model JSON, and merges
     * the display name into the resourcepack overlay's lang file - then
     * marks the draft {@code exported} and persists it via
     * {@link CustomItemState#put}. Returns validation/IO errors instead of
     * throwing; an empty list means the export succeeded.
     */
    public static List<String> exportDraft(CustomItemDraft d) {
        List<String> errors = validateForExport(d);
        if (!errors.isEmpty()) return errors;

        if (!com.kve.common.permission.EditPermission.canApply()) {
            errors.add(com.kve.common.Lang.get("gui.kve.perm.apply_denied"));
            return errors;
        }

        String path = d.path();
        if (!com.kve.common.security.KvePathGuard.isSafeRelativePath(path)) {
            errors.add("Id de item invalido (segmento de ruta no permitido): " + d.fullId());
            return errors;
        }
        try {
            Files.createDirectories(scriptDir());
            SafeFiles.writeAtomic(com.kve.common.security.KvePathGuard.resolveContained(scriptDir(), path + ".js"), previewJs(d));
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Custom item script write failed for {}: {}", d.fullId(), ex.getMessage());
            errors.add("Script write failed: " + ex.getMessage());
            return errors;
        }

        try {
            copyTexture(d, com.kve.common.security.KvePathGuard.resolveContained(textureDir(), path + ".png"));
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Custom item texture copy failed for {}: {}", d.fullId(), ex.getMessage());
            errors.add("Texture copy failed: " + ex.getMessage());
            return errors;
        }

        try {
            Files.createDirectories(modelDir());
            SafeFiles.writeAtomic(com.kve.common.security.KvePathGuard.resolveContained(modelDir(), path + ".json"), modelJson(path, d.isTool()));
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Custom item model write failed for {}: {}", d.fullId(), ex.getMessage());
            errors.add("Model write failed: " + ex.getMessage());
            return errors;
        }

        try {
            mergeLang(d);
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Custom item lang merge failed for {}: {}", d.fullId(), ex.getMessage());
            errors.add("Lang merge failed: " + ex.getMessage());
            return errors;
        }

        d.exported = true;
        CustomItemState.put(d);
        return errors;
    }

    private static void copyTexture(CustomItemDraft d, Path dest) throws IOException {
        Files.createDirectories(dest.getParent());
        Path tmp = dest.resolveSibling(dest.getFileName().toString() + ".tmp");

        Path source = resolveTrustedTextureSource(d.texturePath, d.texturePathTrusted);
        if (source != null && Files.isRegularFile(source)) {
            Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING);
        } else {
            try (InputStream in = CustomItemExporter.class.getResourceAsStream(PLACEHOLDER_RESOURCE)) {
                if (in == null) throw new IOException("bundled placeholder texture missing from jar: " + PLACEHOLDER_RESOURCE);
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        SafeFiles.moveReplacing(tmp, dest);
    }

    static Path resolveTrustedTextureSource(String texturePath, boolean trusted) {
        if (texturePath == null || texturePath.isBlank()) return null;
        if (trusted) return Paths.get(texturePath);
        return ImportRoot.resolveSafe(ImportRoot.root(), texturePath).orElse(null);
    }

    private static void mergeLang(CustomItemDraft d) throws IOException {
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
        root.addProperty("item.shovel_js." + d.path(), d.displayName);
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n";
        SafeFiles.writeAtomic(langFile(), json);
    }
}
