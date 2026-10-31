package com.kve.common.customitem;

import com.kve.CraftEditorMod;
import com.kve.common.kubejs.KubeJSItemAttrExporter;
import com.kve.common.kubejs.SemanticValidator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CustomItemDraft {

    /** COMMON/UNCOMMON/RARE/EPIC, or blank for "no override" - same values/casing {@code ItemAttributeScreen.RARITIES} already uses. */
    public static final String[] RARITIES = {"", "COMMON", "UNCOMMON", "RARE", "EPIC"};

    /** Tipos de KubeJS que ofrece el creador: "basic" es un item normal; el resto son herramientas con material. */
    public static final String[] TYPES = {"basic", "sword", "pickaxe", "axe", "shovel", "hoe"};
    /** Materiales de herramienta de vanilla (el nombre que entiende {@code .tier('...')}). */
    public static final String[] TIERS = {"wood", "stone", "iron", "gold", "diamond", "netherite"};

    public String id = "";
    public String displayName = "";
    public String texturePath = "";
    public transient boolean texturePathTrusted = false;
    public int    maxStackSize = 64;
    public int    maxDamage = 0;
    public boolean fireResistant = false;
    /** Index into {@link #RARITIES}; 0 = sin override (item.rarity no se toca). */
    public int    rarityIdx = 0;
    public final Map<String, KubeJSItemAttrExporter.AttrMod> attributeModifiers = new LinkedHashMap<>();
    public String itemType = "basic";
    public String toolTier = "iron";
    /** Brillo de encantado sin encantamientos. */
    public boolean glow = false;
    /** Ticks que dura como combustible en un horno; 0 = no es combustible. 200 = un objeto. */
    public int    burnTime = 0;
    /** Una linea de texto bajo el nombre; vacio = sin tooltip. */
    public String tooltip = "";
    /** Tags de item separados por coma, ej. "forge:ingots, minecraft:planks". */
    public String tags = "";
    public boolean food = false;
    public int    foodHunger = 4;
    public float  foodSaturation = 0.3f;
    public boolean foodAlwaysEdible = false;
    public boolean foodFast = false;
    public boolean exported = false;
    public long   createdAt = System.currentTimeMillis();
    public long   updatedAt = System.currentTimeMillis();

    public CustomItemDraft() {}

    public CustomItemDraft copy() {
        CustomItemDraft c = new CustomItemDraft();
        c.id = id;
        c.displayName = displayName;
        c.texturePath = texturePath;
        c.texturePathTrusted = texturePathTrusted;
        c.maxStackSize = maxStackSize;
        c.maxDamage = maxDamage;
        c.fireResistant = fireResistant;
        c.rarityIdx = rarityIdx;
        c.attributeModifiers.putAll(attributeModifiers);
        c.itemType = itemType;
        c.toolTier = toolTier;
        c.glow = glow;
        c.burnTime = burnTime;
        c.tooltip = tooltip;
        c.tags = tags;
        c.food = food;
        c.foodHunger = foodHunger;
        c.foodSaturation = foodSaturation;
        c.foodAlwaysEdible = foodAlwaysEdible;
        c.foodFast = foodFast;
        c.exported = exported;
        c.createdAt = createdAt;
        c.updatedAt = updatedAt;
        return c;
    }

    public String rarity() {
        return rarityIdx > 0 && rarityIdx < RARITIES.length ? RARITIES[rarityIdx] : "";
    }

    public boolean isTool() { return itemType != null && !itemType.isEmpty() && !"basic".equals(itemType); }

    /** Tags ya separados y sin espacios; descarta los vacios y el '#' inicial. */
    public List<String> tagList() { return splitTags(tags); }

    public static List<String> splitTags(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String t : raw.split("[,\\s]+")) {
            String s = t.trim();
            if (s.startsWith("#")) s = s.substring(1);
            if (!s.isEmpty() && !out.contains(s)) out.add(s);
        }
        return out;
    }

    public String path() {
        int idx = id.indexOf(':');
        return idx >= 0 ? id.substring(idx + 1) : id;
    }

    public String fullId() {
        String p = path();
        return p.isEmpty() ? "" : CraftEditorMod.NS + ":" + p;
    }

    public List<String> validateDraft() {
        List<String> errors = new ArrayList<>();
        String p = path();
        if (p.isBlank()) {
            errors.add(msg("gui.kve.itemcreator.id_invalid"));
        } else {
            errors.addAll(SemanticValidator.validateNamespacedIdFormat(fullId(), "id"));
        }
        if (displayName == null || displayName.isBlank()) {
            errors.add(msg("gui.kve.itemcreator.name_empty"));
        }
        if (maxStackSize < 1 || maxStackSize > 64) {
            errors.add(msg("gui.kve.itemcreator.stack_range"));
        }
        if (maxDamage < 0) {
            errors.add(msg("gui.kve.itemcreator.damage_negative"));
        }
        if (burnTime < 0) {
            errors.add(msg("gui.kve.itemcreator.burn_negative"));
        }
        if (food && (foodHunger < 0 || foodHunger > 20 || foodSaturation < 0f)) {
            errors.add(msg("gui.kve.itemcreator.food_range"));
        }
        for (String t : tagList()) {
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
