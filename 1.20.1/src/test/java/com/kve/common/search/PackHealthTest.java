package com.kve.common.search;

import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.kubejs.TagExporter;
import com.kve.common.kubejs.TradeExporter;
import com.kve.common.packrules.PackRules;
import com.kve.common.recipe.RecipeData;
import com.kve.common.search.ProjectReferences.Domain;
import com.kve.common.search.ProjectReferences.Kind;
import com.kve.common.search.ProjectReferences.Ref;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Referencias tipadas, diagnostico de ids rotos e informe por mod. */
class PackHealthTest {

    @BeforeEach
    @AfterEach
    void clean() {
        KubeJSExporter.clearAll();
        TagExporter.clearAll();
        TradeExporter.clearAll();
        PackRules.setCurrent(new PackRules());
    }

    private static void seed() {
        RecipeData r = new RecipeData();
        r.recipeId = "gear";
        r.type = RecipeData.RecipeType.VANILLA_SHAPED;
        r.output = new ItemStack(Items.IRON_INGOT);
        r.ingredients = new ArrayList<>(List.of(new ItemStack(Items.STICK)));
        KubeJSExporter.stageRecipe(r);

        TagExporter.TagEntry t = new TagExporter.TagEntry();
        t.tagId = "forge:gears";
        t.added = new ArrayList<>(List.of("minecraft:stone", "oldmod:copper_gear", "create:missing_gear"));
        TagExporter.loadEntry(t);

        TradeExporter.TradeEntry tr = new TradeExporter.TradeEntry();
        tr.id = "smith";
        tr.professionId = "minecraft:armorer";
        TradeExporter.TradeOffer o = new TradeExporter.TradeOffer();
        o.inputAId = "forge:gems"; o.inputAIsTag = true;
        o.outputId = "create:cogwheel";
        tr.offers.add(o);
        TradeExporter.loadEntry(tr);

        PackRules rules = new PackRules();
        PackRules.Compostable c = new PackRules.Compostable();
        c.itemId = "oldmod:rotten_thing";
        rules.compostables.add(c);
        PackRules.setCurrent(rules);
    }

    @Test
    void collectsTypedReferencesFromEveryDomainIncludingTradesAndPackRules() {
        seed();
        List<Ref> refs = ProjectReferences.collect();
        assertTrue(refs.contains(new Ref(Domain.RECIPE, "gear", "output", Kind.ITEM, "minecraft:iron_ingot")));
        assertTrue(refs.contains(new Ref(Domain.TAG, "ITEM|forge:gears", "added to forge:gears", Kind.ITEM, "oldmod:copper_gear")));
        assertTrue(refs.contains(new Ref(Domain.TRADE, "smith", "offer input A", Kind.TAG, "#forge:gems")), "un tag de trade se marca como tag");
        assertTrue(refs.contains(new Ref(Domain.TRADE, "smith", "profession", Kind.PROFESSION, "minecraft:armorer")));
        assertTrue(refs.contains(new Ref(Domain.PACK_RULE, "compost:oldmod:rotten_thing", "compostable", Kind.ITEM, "oldmod:rotten_thing")));
        assertEquals("forge", new Ref(Domain.TRADE, "x", "f", Kind.TAG, "#forge:gems").namespace());
    }

    @Test
    void diagnosticsSeparateMissingModsFromRenamedIds() {
        seed();
        Set<String> loadedMods = Set.of("minecraft", "forge", "create");
        List<PackDiagnostics.Problem> problems = PackDiagnostics.find(ProjectReferences.collect(),
                PackDiagnostics::existsInRegistry, loadedMods::contains);
        List<String> ids = problems.stream().map(p -> p.ref().id()).toList();
        assertTrue(ids.contains("oldmod:copper_gear"));
        assertTrue(ids.contains("create:missing_gear"));
        assertTrue(ids.contains("oldmod:rotten_thing"));
        assertFalse(ids.contains("minecraft:stone"));
        assertFalse(ids.contains("#forge:gems"), "los tags no se comprueban");
        assertFalse(ids.contains("gear"), "los ids propios de Shovel JS no se comprueban");
        PackDiagnostics.Problem createOne = problems.stream().filter(p -> p.ref().id().equals("create:missing_gear")).findFirst().orElseThrow();
        assertFalse(createOne.modMissing(), "create esta cargado: el id cambio");
        assertTrue(problems.stream().filter(p -> p.ref().id().startsWith("oldmod:")).allMatch(PackDiagnostics.Problem::modMissing));
        assertEquals(3, PackDiagnostics.affectedEntries(problems));
    }

    @Test
    void modReportGroupsEverythingThatTouchesOneMod() {
        seed();
        List<Ref> refs = ProjectReferences.collect();
        Map<Domain, List<Ref>> create = PackDiagnostics.forNamespace(refs, "create");
        assertEquals(Set.of(Domain.TAG, Domain.TRADE), create.keySet());
        Map<String, Integer> counts = PackDiagnostics.namespaceCounts(refs);
        assertEquals("minecraft", counts.keySet().iterator().next(), "el mod mas referenciado primero");
        assertFalse(counts.containsKey("gear"));
    }

    @Test
    void idUsageSearchNowFindsTradesToo() {
        seed();
        List<IdUsageIndex.Hit> hits = IdUsageIndex.search("cogwheel");
        assertEquals(1, hits.size());
        assertEquals(IdUsageIndex.Domain.TRADE, hits.get(0).domain());
        assertTrue(IdUsageIndex.search("").isEmpty());
    }
}
