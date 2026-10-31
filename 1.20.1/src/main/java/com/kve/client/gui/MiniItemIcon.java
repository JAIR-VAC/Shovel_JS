package com.kve.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class MiniItemIcon {
    private MiniItemIcon() {}

    public static final int BORDER = 0xFF2A4A6A;
    public static final int BG = 0xFF080C18;

    /**
     * Icono de {@code size}px con su interior en (x, y); el borde ocupa
     * desde (x-1, y-1) hasta (x+size+1, y+size+1). {@code item} null = marco vacio.
     */
    public static void draw(GuiGraphics g, int x, int y, int size, Item item) {
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, KveGuiStyle.th(BORDER));
        g.fill(x, y, x + size, y + size, KveGuiStyle.th(BG));
        if (item == null) return;
        float scale = size / 16f;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        SafeItemRender.render(g, item, 0, 0);
        g.pose().popPose();
    }

    /** Item por id; null si esta vacio, es invalido o no existe. */
    public static Item item(String id) {
        ResourceLocation rl = parse(id);
        if (rl == null) return null;
        return BuiltInRegistries.ITEM.getOptional(rl).filter(it -> it != Items.AIR).orElse(null);
    }

    /** Item de un bloque por id (bloques sin item, ej. fuego, -> null). */
    public static Item block(String id) {
        ResourceLocation rl = parse(id);
        if (rl == null) return null;
        return BuiltInRegistries.BLOCK.getOptional(rl).map(Block::asItem).filter(it -> it != Items.AIR).orElse(null);
    }

    /** Primer item de un tag de items (con o sin '#'), como representante visual; null si el tag esta vacio o no existe. */
    public static Item firstOfItemTag(String tagId) {
        ResourceLocation rl = parse(tagId);
        if (rl == null) return null;
        return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, rl))
                .flatMap(set -> set.stream().findFirst())
                .map(Holder::value)
                .orElse(null);
    }

    /** Item para un id que puede ser un tag de items (ofertas de trade, ingredientes). */
    public static Item itemOrTag(String id, boolean isTag) {
        if (isTag || (id != null && id.startsWith("#"))) return firstOfItemTag(id);
        return item(id);
    }

    private static ResourceLocation parse(String id) {
        if (id == null || id.isBlank()) return null;
        return ResourceLocation.tryParse(id.startsWith("#") ? id.substring(1) : id);
    }
}
