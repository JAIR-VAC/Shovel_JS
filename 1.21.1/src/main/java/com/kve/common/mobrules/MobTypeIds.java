package com.kve.common.mobrules;

import com.kve.common.kubejs.KubeJSMobExporter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Optimizacion todos los enforcers de mobrules hacian
 * {@code BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()} en cada
 * evento - algunos en CADA tick de CADA mob - creando un String nuevo cada
 * vez solo para buscarlo en un mapa. Los EntityType no cambian en runtime,
 * asi que el id se calcula una vez por tipo y se reusa (el hash del String
 * queda cacheado tambien).
 */
public final class MobTypeIds {
    private MobTypeIds() {}

    private static final ConcurrentHashMap<EntityType<?>, String> IDS = new ConcurrentHashMap<>();
    private static final String NONE = "";

    /** Id "namespace:path" del tipo, o null si no esta registrado. */
    public static String idOf(EntityType<?> type) {
        String id = IDS.computeIfAbsent(type, t -> {
            ResourceLocation rl = BuiltInRegistries.ENTITY_TYPE.getKey(t);
            return rl == null ? NONE : rl.toString();
        });
        return id.isEmpty() ? null : id;
    }

    public static String idOf(Entity entity) {
        return entity == null ? null : idOf(entity.getType());
    }

    /** MobEntry habilitado del Mob Editor para esta entidad (nunca jugadores), o null. */
    public static KubeJSMobExporter.MobEntry entryFor(Entity entity) {
        if (!KubeJSMobExporter.hasAppliedEntries()) return null;
        if (!(entity instanceof LivingEntity) || entity instanceof Player) return null;
        String id = idOf(entity.getType());
        if (id == null) return null;
        KubeJSMobExporter.MobEntry e = KubeJSMobExporter.getAppliedEntry(id);
        return e != null && e.enabled ? e : null;
    }
}
