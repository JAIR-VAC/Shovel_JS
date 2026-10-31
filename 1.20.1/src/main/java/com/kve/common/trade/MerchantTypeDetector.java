package com.kve.common.trade;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class MerchantTypeDetector {

    private MerchantTypeDetector() {}

    public enum Kind { VILLAGER_LIKE, MERCHANT, NOT_MERCHANT, UNKNOWN }

    private static final Map<String, Kind> CACHE = new ConcurrentHashMap<>();

    /** Nivel donde instanciar entidades de prueba. El cliente lo reemplaza por el nivel del cliente (ver CraftEditorMod#clientSetup) para no tocar el nivel del server desde el hilo de render. */
    private static volatile Supplier<Level> levelSupplier = MerchantTypeDetector::serverOverworld;

    private static Level serverOverworld() {
        try {
            var server = ServerLifecycleHooks.getCurrentServer();
            return server == null ? null : server.overworld();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Client-side hook: prefiere el nivel del cliente y cae al del server integrado si no hay. */
    public static void setLevelSupplier(Supplier<Level> supplier) {
        Supplier<Level> fallback = MerchantTypeDetector::serverOverworld;
        levelSupplier = supplier == null ? fallback : () -> {
            Level l = null;
            try { l = supplier.get(); } catch (Throwable ignored) {}
            return l != null ? l : fallback.get();
        };
    }

    /** Test-only: fija el resultado para un id sin necesitar un nivel real. */
    public static void putForTests(String entityTypeId, Kind kind) { CACHE.put(entityTypeId, kind); }

    public static void clearCache() { CACHE.clear(); }

    public static Kind detect(String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isBlank()) return Kind.UNKNOWN;
        Kind cached = CACHE.get(entityTypeId);
        if (cached != null) return cached;
        Kind k = probe(entityTypeId);
        if (k != Kind.UNKNOWN) CACHE.put(entityTypeId, k);
        return k;
    }

    private static Kind probe(String entityTypeId) {
        Level level = levelSupplier.get();
        if (level == null) return Kind.UNKNOWN;
        try {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(entityTypeId));
            if (type == null) return Kind.UNKNOWN;
            Entity e = type.create(level);
            if (e == null) return Kind.NOT_MERCHANT;
            return classify(e);
        } catch (Throwable t) {
            return Kind.UNKNOWN;
        }
    }

    /** Clasificacion de una instancia ya existente (tambien usada en runtime por el injector). */
    public static Kind classify(Entity e) {
        if (e instanceof AbstractVillager && e instanceof VillagerDataHolder) return Kind.VILLAGER_LIKE;
        if (e instanceof Merchant) return Kind.MERCHANT;
        return Kind.NOT_MERCHANT;
    }

    /**
     * Id de la profesion de {@code e}, o "" si no tiene / no se pudo
     * resolver. {@code getterPath} (opcional, del descriptor) es una cadena
     * de metodos publicos sin argumentos separados por '.', p.ej.
     * {@code "getRibbitData.getProfession()"}.
     */
    public static String professionOf(Entity e, String getterPath) {
        if (e == null) return "";
        if (e instanceof VillagerDataHolder vdh) {
            VillagerProfession p = vdh.getVillagerData().getProfession();
            ResourceLocation key = ForgeRegistries.VILLAGER_PROFESSIONS.getKey(p);
            return key == null ? "" : key.toString();
        }
        try {
            Object prof = (getterPath != null && !getterPath.isBlank())
                    ? invokePath(e, getterPath)
                    : findProfessionReflectively(e);
            return professionObjectToId(prof);
        } catch (Throwable t) {
            return "";
        }
    }

    private static Object invokePath(Object root, String path) throws Exception {
        Object cur = root;
        for (String name : path.split("\\.")) {
            if (cur == null || name.isBlank()) return null;
            Method m = cur.getClass().getMethod(name.trim());
            cur = m.invoke(cur);
        }
        return cur;
    }

    private static Object findProfessionReflectively(Object entity) throws Exception {
        Method direct = noArg(entity.getClass(), "getProfession");
        if (direct != null) return direct.invoke(entity);
        for (Method m : entity.getClass().getMethods()) {
            if (m.getParameterCount() != 0) continue;
            String n = m.getName();
            if (!n.startsWith("get") || !n.endsWith("Data")) continue;
            if (m.getReturnType().isPrimitive()) continue;
            Method inner = noArg(m.getReturnType(), "getProfession");
            if (inner == null) continue;
            Object data = m.invoke(entity);
            if (data != null) return inner.invoke(data);
        }
        return null;
    }

    private static Method noArg(Class<?> c, String name) {
        try {
            Method m = c.getMethod(name);
            return m.getParameterCount() == 0 ? m : null;
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    static String professionObjectToId(Object prof) {
        if (prof == null) return "";
        if (prof instanceof VillagerProfession vp) {
            ResourceLocation key = ForgeRegistries.VILLAGER_PROFESSIONS.getKey(vp);
            return key == null ? "" : key.toString();
        }
        if (prof instanceof ResourceLocation rl) return rl.toString();
        if (prof instanceof CharSequence cs) return cs.toString();
        for (String getter : new String[]{"getId", "id", "getName", "getKey"}) {
            Method m = noArg(prof.getClass(), getter);
            if (m == null) continue;
            try {
                Object v = m.invoke(prof);
                if (v instanceof ResourceLocation rl) return rl.toString();
                if (v instanceof CharSequence cs && !cs.isEmpty()) return cs.toString();
            } catch (Exception ignored) {}
        }
        if (prof instanceof Enum<?> en) return en.name().toLowerCase(java.util.Locale.ROOT);
        String s = String.valueOf(prof);
        return s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") ? s : "";
    }

    /**
     * Profesiones conocidas para el picker de un tipo MERCHANT: las del
     * descriptor + las observadas en instancias vivas de ese tipo en el nivel
     * cargado (el mod no expone un registro generico que listar).
     */
    public static List<String> knownProfessions(String entityTypeId, List<String> declared, String getterPath) {
        Set<String> out = new LinkedHashSet<>();
        if (declared != null) for (String d : declared) if (d != null && !d.isBlank()) out.add(d.trim());
        if (entityTypeId != null && !entityTypeId.isBlank()) {
            try {
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(entityTypeId));
                Iterable<Entity> loaded = entitySupplier.get();
                if (type != null && loaded != null) {
                    for (Entity e : loaded) if (e.getType() == type) addProf(out, e, getterPath);
                }
            } catch (Throwable ignored) {}
        }
        return new ArrayList<>(out);
    }

    private static volatile Supplier<Iterable<Entity>> entitySupplier = MerchantTypeDetector::serverEntities;

    private static Iterable<Entity> serverEntities() {
        try {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return null;
            List<Entity> all = new ArrayList<>();
            for (var lvl : server.getAllLevels()) for (Entity e : lvl.getEntities().getAll()) all.add(e);
            return all;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void setEntitySupplier(Supplier<Iterable<Entity>> supplier) {
        entitySupplier = supplier == null ? MerchantTypeDetector::serverEntities : supplier;
    }

    private static void addProf(Set<String> out, Entity e, String getterPath) {
        String p = professionOf(e, getterPath);
        if (!p.isBlank()) out.add(p);
    }
}
