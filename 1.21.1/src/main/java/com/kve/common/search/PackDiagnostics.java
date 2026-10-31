package com.kve.common.search;

import com.kve.common.search.ProjectReferences.Domain;
import com.kve.common.search.ProjectReferences.Kind;
import com.kve.common.search.ProjectReferences.Ref;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

public final class PackDiagnostics {
    private PackDiagnostics() {}

    /** @param modMissing true = el mod entero no esta cargado; false = el mod esta pero ese id no. */
    public record Problem(Ref ref, boolean modMissing) {}

    public static List<Problem> find(Collection<Ref> refs, Predicate<Ref> exists, Predicate<String> modLoaded) {
        List<Problem> out = new ArrayList<>();
        for (Ref r : refs) {
            if (!checkable(r.kind()) || exists.test(r)) continue;
            out.add(new Problem(r, !modLoaded.test(r.namespace())));
        }
        return out;
    }

    public static List<Problem> scanProject() {
        return find(ProjectReferences.collect(), PackDiagnostics::existsOrDeclared, PackDiagnostics::isModLoaded);
    }

    /** Problemas solo de las entradas {@code entryIds} (lo que se va a Aplicar). */
    public static List<Problem> forEntries(Collection<String> entryIds) {
        List<Ref> refs = new ArrayList<>();
        for (Ref r : ProjectReferences.collect()) if (entryIds.contains(r.entryId())) refs.add(r);
        return find(refs, PackDiagnostics::existsOrDeclared, PackDiagnostics::isModLoaded);
    }

    static boolean existsOrDeclared(Ref r) {
        return existsInRegistry(r) || DeclaredIds.isDeclared(r.id());
    }

    static boolean checkable(Kind k) {
        return switch (k) {
            case ITEM, BLOCK, ENTITY, FLUID, EFFECT, ATTRIBUTE, SOUND, RECIPE_TYPE, PROFESSION -> true;
            case TAG, NAME, OTHER -> false;
        };
    }

    public static boolean existsInRegistry(Ref r) {
        ResourceLocation rl = ResourceLocation.tryParse(r.id());
        if (rl == null) return false;
        return switch (r.kind()) {
            case ITEM -> BuiltInRegistries.ITEM.containsKey(rl);
            case BLOCK -> BuiltInRegistries.BLOCK.containsKey(rl);
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.containsKey(rl);
            case FLUID -> BuiltInRegistries.FLUID.containsKey(rl);
            case EFFECT -> BuiltInRegistries.MOB_EFFECT.containsKey(rl);
            case ATTRIBUTE -> BuiltInRegistries.ATTRIBUTE.containsKey(rl);
            case SOUND -> BuiltInRegistries.SOUND_EVENT.containsKey(rl);
            case RECIPE_TYPE -> BuiltInRegistries.RECIPE_SERIALIZER.containsKey(rl) || BuiltInRegistries.RECIPE_TYPE.containsKey(rl);
            case PROFESSION -> BuiltInRegistries.VILLAGER_PROFESSION.containsKey(rl);
            case TAG, NAME, OTHER -> true;
        };
    }

    private static boolean isModLoaded(String namespace) {
        if ("minecraft".equals(namespace) || "neoforge".equals(namespace) || "c".equals(namespace)) return true;
        ModList list = ModList.get();
        return list == null || list.isLoaded(namespace);
    }

    /** Referencias a un mod agrupadas por editor (sin los ids propios de KVE). */
    public static Map<Domain, List<Ref>> forNamespace(Collection<Ref> refs, String namespace) {
        Map<Domain, List<Ref>> out = new EnumMap<>(Domain.class);
        for (Ref r : refs) {
            if (r.kind() == Kind.NAME || !r.namespace().equals(namespace)) continue;
            out.computeIfAbsent(r.domain(), d -> new ArrayList<>()).add(r);
        }
        return out;
    }

    /** Cantidad de referencias por mod, ordenado de mayor a menor (para elegir el mod del informe). */
    public static Map<String, Integer> namespaceCounts(Collection<Ref> refs) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Ref r : refs) if (r.kind() != Kind.NAME) counts.merge(r.namespace(), 1, Integer::sum);
        Map<String, Integer> sorted = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    /** Entradas distintas afectadas (un receta con 3 ids rotos cuenta una vez). */
    public static int affectedEntries(Collection<Problem> problems) {
        return (int) problems.stream().map(p -> p.ref().domain() + "|" + p.ref().entryId()).distinct().count();
    }
}
