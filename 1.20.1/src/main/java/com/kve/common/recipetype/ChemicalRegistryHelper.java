package com.kve.common.recipetype;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ChemicalRegistryHelper {

    /** The four chemical registry names Mekanism 10.4 registers (see class javadoc). */
    public static final List<String> KINDS = List.of("gas", "infuse_type", "pigment", "slurry");

    private ChemicalRegistryHelper() {}

    /** Splits a {@code SlotDef#chemical}-style value ("gas", "gas|slurry"...) into known kinds, in declared order. Unknown names are dropped. */
    public static List<String> parseKinds(String spec) {
        List<String> out = new ArrayList<>();
        if (spec == null || spec.isBlank()) return out;
        for (String part : spec.split("\\|")) {
            String k = part.trim().toLowerCase(Locale.ROOT);
            if (KINDS.contains(k) && !out.contains(k)) out.add(k);
        }
        return out;
    }

    private static ForgeRegistry<Object> registry(String kind) {
        try {
            return RegistryManager.ACTIVE.getRegistry(new ResourceLocation("mekanism", kind));
        } catch (Throwable t) {
            return null;
        }
    }

    /** Every registered id of this chemical kind, sorted; empty when Mekanism isn't loaded. The "empty" chemical ({@code mekanism:empty}) is skipped - it's a sentinel, not something a recipe can use. */
    public static List<String> ids(String kind) {
        List<String> out = new ArrayList<>();
        ForgeRegistry<Object> reg = registry(kind);
        if (reg == null) return out;
        try {
            for (ResourceLocation rl : reg.getKeys()) {
                if (rl.getPath().equals("empty")) continue;
                out.add(rl.toString());
            }
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
        out.sort(String::compareTo);
        return out;
    }

    private static java.util.Map<String, java.util.Set<String>> fakeRegistryForTests = null;

    /** Tests only: replaces the real chemical registries with {@code fake} (null restores them). */
    public static void setFakeRegistryForTests(java.util.Map<String, java.util.Set<String>> fake) {
        fakeRegistryForTests = fake;
    }

    /** True if Mekanism's registry for {@code kind} is present at all (i.e. Mekanism is loaded). */
    public static boolean available(String kind) {
        if (fakeRegistryForTests != null) return fakeRegistryForTests.containsKey(kind);
        return registry(kind) != null;
    }

    /** True if {@code id} is registered under {@code kind}. False on any failure. */
    public static boolean contains(String kind, String id) {
        if (fakeRegistryForTests != null) {
            java.util.Set<String> ids = fakeRegistryForTests.get(kind);
            return ids != null && id != null && ids.contains(id);
        }
        ResourceLocation rl = id == null ? null : ResourceLocation.tryParse(id);
        if (rl == null) return false;
        ForgeRegistry<Object> reg = registry(kind);
        try {
            return reg != null && reg.containsKey(rl);
        } catch (Throwable t) {
            return false;
        }
    }

    public static String kindOf(String id, List<String> allowedKinds) {
        List<String> kinds = allowedKinds == null || allowedKinds.isEmpty() ? KINDS : allowedKinds;
        for (String k : kinds) if (contains(k, id)) return k;
        return null;
    }

    private static Object value(String kind, String id) {
        ResourceLocation rl = id == null ? null : ResourceLocation.tryParse(id);
        if (rl == null) return null;
        ForgeRegistry<Object> reg = registry(kind);
        try {
            return reg == null || !reg.containsKey(rl) ? null : reg.getValue(rl);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object invoke(Object target, String method) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    /** The chemical's tint as 0xRRGGBB, or -1 if unknown. */
    public static int tint(String kind, String id) {
        Object v = invoke(value(kind, id), "getTint");
        return v instanceof Integer i ? (i & 0xFFFFFF) : -1;
    }

    /** The chemical's block-atlas icon texture, or null if unknown. */
    public static ResourceLocation icon(String kind, String id) {
        Object v = invoke(value(kind, id), "getIcon");
        return v instanceof ResourceLocation rl ? rl : null;
    }
}
