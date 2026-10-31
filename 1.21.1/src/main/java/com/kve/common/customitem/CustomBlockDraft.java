package com.kve.common.customitem;

import com.kve.CraftEditorMod;
import com.kve.common.kubejs.SemanticValidator;

import java.util.ArrayList;
import java.util.List;

public final class CustomBlockDraft {

    /** namespace:path - namespace siempre {@link CraftEditorMod#NS}, mismo criterio que {@link CustomItemDraft#id}. */
    public String id = "";
    public String displayName = "";
    /** Absolute source path of the chosen texture PNG, or "" for "use the bundled placeholder" - mismo contrato que {@link CustomItemDraft#texturePath}. */
    public String texturePath = "";
    public transient boolean texturePathTrusted = false;
    /** -1 = sin override (KubeJS usa su default de BlockBuilder, 1.5). */
    public float hardness = -1f;
 /** -1 = sin override (KubeJS usa hardness como resistance por default si no se llama.resistance()). */
    public float resistance = -1f;
    public boolean requiresTool = false;
    /** 0-15, -1 = sin override (bloque no emite luz). */
    public int lightLevel = -1;

    /** Sonidos que ofrece KubeJS con un metodo propio ({@code .woodSoundType()}...); "" = el de por defecto. */
    public static final String[] SOUNDS = {"", "wood", "stone", "gravel", "grass", "sand", "glass", "crop"};
    /** Herramienta que lo pica rapido (tag minecraft:mineable/...); "" = cualquiera. */
    public static final String[] TOOLS = {"", "pickaxe", "axe", "shovel", "hoe"};
    /** Nivel minimo de herramienta (tag minecraft:needs_..._tool); "" = cualquiera. */
    public static final String[] TOOL_LEVELS = {"", "stone", "iron", "diamond"};

    public String sound = "";
    public String toolType = "";
    public String toolLevel = "";
    /** Como el vidrio: se ve a traves de las partes transparentes de la textura. */
    public boolean seeThrough = false;
    /** Se puede atravesar (como una planta). */
    public boolean noCollision = false;
    /** Resbala como el hielo. */
    public boolean slippery = false;
    /** Frena al caminar, como la arena de almas. */
    public boolean slow = false;
    /** Tags de bloque separados por coma. */
    public String tags = "";

    public boolean exported = false;
    public long   createdAt = System.currentTimeMillis();
    public long   updatedAt = System.currentTimeMillis();

    public CustomBlockDraft() {}

    public CustomBlockDraft copy() {
        CustomBlockDraft c = new CustomBlockDraft();
        c.id = id;
        c.displayName = displayName;
        c.texturePath = texturePath;
        c.texturePathTrusted = texturePathTrusted;
        c.hardness = hardness;
        c.resistance = resistance;
        c.requiresTool = requiresTool;
        c.lightLevel = lightLevel;
        c.sound = sound;
        c.toolType = toolType;
        c.toolLevel = toolLevel;
        c.seeThrough = seeThrough;
        c.noCollision = noCollision;
        c.slippery = slippery;
        c.slow = slow;
        c.tags = tags;
        c.exported = exported;
        c.createdAt = createdAt;
        c.updatedAt = updatedAt;
        return c;
    }

    /** Bare path (no namespace) as typed by the player - mismo contrato que {@link CustomItemDraft#path()}. */
    public String path() {
        int idx = id.indexOf(':');
        return idx >= 0 ? id.substring(idx + 1) : id;
    }

    /** Full namespace:path, siempre bajo {@link CraftEditorMod#NS}. */
    public String fullId() {
        String p = path();
        return p.isEmpty() ? "" : CraftEditorMod.NS + ":" + p;
    }

    /** Mismo split que {@link CustomItemDraft#validateDraft()} - la colision contra el registry real vive en {@link CustomBlockExporter#validateForExport} porque necesita registries bootstrapped. */
    public List<String> validateDraft() {
        List<String> errors = new ArrayList<>();
        String p = path();
        if (p.isBlank()) {
            errors.add(msg("gui.kve.blockcreator.id_invalid"));
        } else {
            errors.addAll(SemanticValidator.validateNamespacedIdFormat(fullId(), "id"));
        }
        if (displayName == null || displayName.isBlank()) {
            errors.add(msg("gui.kve.blockcreator.name_empty"));
        }
        if (hardness < -1f) {
            errors.add(msg("gui.kve.blockcreator.hardness_negative"));
        }
        if (resistance < -1f) {
            errors.add(msg("gui.kve.blockcreator.resistance_negative"));
        }
        if (lightLevel < -1 || lightLevel > 15) {
            errors.add(msg("gui.kve.blockcreator.light_range"));
        }
        for (String t : CustomItemDraft.splitTags(tags)) {
            if (net.minecraft.resources.ResourceLocation.tryParse(t) == null) {
                errors.add(msg("gui.kve.itemcreator.tag_invalid") + " " + t);
                break;
            }
        }
        return errors;
    }

    private static String msg(String key) {
        try { return com.kve.common.Lang.get(key); } catch (Throwable t) { return key; }
    }
}
