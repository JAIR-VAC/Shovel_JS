package com.kve.client.gui;

import com.kve.common.kubejs.TradeExporter.TradeEntry;
import com.kve.common.kubejs.TradeExporter.TradeTargetKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class TradeEntityPreview {
    private TradeEntityPreview() {}

    private static final Map<String, LivingEntity> CACHE = new HashMap<>();
    private static Level cacheLevel = null;

    /** Dibuja el mob de {@code e} dentro de la caja (x,y,w,h). No dibuja nada si no se puede resolver. */
    public static void draw(GuiGraphics g, TradeEntry e, int x, int y, int w, int h) {
        drawLiving(g, entityFor(e), x, y, w, h);
    }

    public static void drawEntity(GuiGraphics g, String entityId, int x, int y, int w, int h) {
        drawLiving(g, entityById(entityId), x, y, w, h);
    }

    private static void drawLiving(GuiGraphics g, LivingEntity entity, int x, int y, int w, int h) {
        if (entity == null) return;
        float size = Math.max(entity.getBbHeight(), entity.getBbWidth());
        int scale = (int) Math.max(4, Math.min(18, (h - 4) / Math.max(0.5f, size)));
        int cx = x + w / 2;
        int feetY = y + h - 2;
        g.enableScissor(x, y, x + w, y + h);
        try {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, feetY, scale, -18f, -4f, entity);
        } catch (Throwable ignored) {
        } finally {
            g.disableScissor();
        }
    }

    /**
     * Aldeano con la profesion {@code professionId} (sin profesion si
     * esta vacia o no existe), para la vista previa del selector de profesiones.
     * Mismo cache que el resto de este helper.
     */
    public static LivingEntity villager(String professionId) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return null;
        if (level != cacheLevel) { CACHE.clear(); cacheLevel = level; }
        String key = TradeTargetKind.VILLAGER + "|" + (professionId == null ? "" : professionId.trim());
        if (CACHE.containsKey(key)) return CACHE.get(key);
        LivingEntity created = null;
        try {
            created = createVillager(professionId, level);
        } catch (Throwable ignored) {}
        CACHE.put(key, created);
        return created;
    }

    /** La entidad {@code entityId}, cacheada (vista previa de los oficios de un mod). */
    public static LivingEntity entity(String entityId) {
        return entityById(entityId);
    }

    private static LivingEntity entityFor(TradeEntry e) {
        Level level = Minecraft.getInstance().level;
        if (level == null || e == null) return null;
        if (level != cacheLevel) { CACHE.clear(); cacheLevel = level; }
        String key = e.targetKind + "|" + (e.targetKind == TradeTargetKind.ENTITY ? e.entityTypeId : e.professionId);
        if (CACHE.containsKey(key)) return CACHE.get(key);
        LivingEntity created = null;
        try {
            created = create(e, level);
        } catch (Throwable ignored) {}
        CACHE.put(key, created);
        return created;
    }

    private static LivingEntity entityById(String entityId) {
        Level level = Minecraft.getInstance().level;
        if (level == null || entityId == null) return null;
        if (level != cacheLevel) { CACHE.clear(); cacheLevel = level; }
        String key = "ENTITY|" + entityId.trim();
        if (CACHE.containsKey(key)) return CACHE.get(key);
        LivingEntity created = null;
        try {
            created = createById(entityId, level);
        } catch (Throwable ignored) {}
        CACHE.put(key, created);
        return created;
    }

    private static LivingEntity createById(String entityId, Level level) {
        ResourceLocation id = ResourceLocation.tryParse(entityId == null ? "" : entityId.trim());
        if (id == null) return null;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) return null;
        Entity ent = type.create(level);
        return ent instanceof LivingEntity le ? le : null;
    }

    private static Villager createVillager(String professionId, Level level) {
        Villager v = EntityType.VILLAGER.create(level);
        if (v == null) return null;
        ResourceLocation prof = ResourceLocation.tryParse(professionId == null ? "" : professionId.trim());
        if (prof != null) {
            BuiltInRegistries.VILLAGER_PROFESSION.getOptional(prof)
                    .ifPresent(p -> v.setVillagerData(v.getVillagerData().setProfession(p)));
        }
        return v;
    }

    private static LivingEntity create(TradeEntry e, Level level) {
        switch (e.targetKind) {
            case VILLAGER -> {
                return createVillager(e.professionId, level);
            }
            case WANDERING_TRADER -> {
                return EntityType.WANDERING_TRADER.create(level);
            }
            default -> {
                return createById(e.entityTypeId, level);
            }
        }
    }
}
