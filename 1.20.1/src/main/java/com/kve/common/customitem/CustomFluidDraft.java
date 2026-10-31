package com.kve.common.customitem;

import com.kve.CraftEditorMod;
import com.kve.common.kubejs.SemanticValidator;

import java.util.ArrayList;
import java.util.List;

/** Borrador de un fluido nuevo: mismo criterio que {@link CustomBlockDraft} (id siempre bajo {@link CraftEditorMod#NS}). */
public final class CustomFluidDraft {

    public String id = "";
    public String displayName = "";
    /** Color RGB (sin alfa) con el que KubeJS tine la textura de agua y el cubo. */
    public int color = 0x3F76E4;
    /** Espeso como la lava (textura densa) en vez de fino como el agua. */
    public boolean thick = false;
    /** Registra tambien el cubo para recogerlo. */
    public boolean bucket = true;
    public String tags = "";

    public boolean exported = false;
    public long createdAt = System.currentTimeMillis();
    public long updatedAt = System.currentTimeMillis();

    public CustomFluidDraft copy() {
        CustomFluidDraft c = new CustomFluidDraft();
        c.id = id;
        c.displayName = displayName;
        c.color = color;
        c.thick = thick;
        c.bucket = bucket;
        c.tags = tags;
        c.exported = exported;
        c.createdAt = createdAt;
        c.updatedAt = updatedAt;
        return c;
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
            errors.add(msg("gui.kve.fluidcreator.id_invalid"));
        } else {
            errors.addAll(SemanticValidator.validateNamespacedIdFormat(fullId(), "id"));
        }
        if (displayName == null || displayName.isBlank()) {
            errors.add(msg("gui.kve.fluidcreator.name_empty"));
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
