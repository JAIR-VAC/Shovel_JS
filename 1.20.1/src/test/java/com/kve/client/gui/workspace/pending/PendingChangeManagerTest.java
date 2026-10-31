package com.kve.client.gui.workspace.pending;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PendingChangeManagerTest {

    @BeforeEach
    void grantApplyPermission() {
        PendingChangeManager.setApplyPermissionForTests(() -> true);
    }

    @AfterEach
    void drainQueue() {
        PendingChangeManager.setApplyPermissionForTests(null);
        PendingChangeManager.discardAll();
    }

    private static PendingChange change(PendingDomain domain, String id, AtomicInteger applyCounter) {
        return PendingChange.of(domain, id, PendingChange.Action.EDIT, false,
                applyCounter::incrementAndGet, () -> {});
    }

    private static PendingChange failingChange(PendingDomain domain, String id) {
        return PendingChange.of(domain, id, PendingChange.Action.EDIT, false,
                () -> { throw new RuntimeException("boom"); }, () -> {});
    }

    @Test
    void domainsPresentReflectsOnlyQueuedDomains() {
        assertTrue(PendingChangeManager.domainsPresent().isEmpty());

        PendingChangeManager.queue(change(PendingDomain.TAGS, "shovel_js:a", new AtomicInteger()));
        assertEquals(Set.of(PendingDomain.TAGS), PendingChangeManager.domainsPresent());

        PendingChangeManager.queue(change(PendingDomain.LOOT, "shovel_js:b", new AtomicInteger()));
        assertEquals(Set.of(PendingDomain.TAGS, PendingDomain.LOOT), PendingChangeManager.domainsPresent());
    }

    @Test
    void sizeForDomainCountsOnlyThatDomain() {
        PendingChangeManager.queue(change(PendingDomain.RECIPES, "shovel_js:r1", new AtomicInteger()));
        PendingChangeManager.queue(change(PendingDomain.RECIPES, "shovel_js:r2", new AtomicInteger()));
        PendingChangeManager.queue(change(PendingDomain.TAGS, "shovel_js:t1", new AtomicInteger()));

        assertEquals(2, PendingChangeManager.sizeForDomain(PendingDomain.RECIPES));
        assertEquals(1, PendingChangeManager.sizeForDomain(PendingDomain.TAGS));
        assertEquals(0, PendingChangeManager.sizeForDomain(PendingDomain.LOOT));
        assertEquals(3, PendingChangeManager.size());
    }

    @Test
    void applyDomainOnlyRunsAndRemovesThatDomainsEntries() {
        AtomicInteger recipesApplied = new AtomicInteger();
        AtomicInteger tagsApplied = new AtomicInteger();
        PendingChangeManager.queue(change(PendingDomain.RECIPES, "shovel_js:r1", recipesApplied));
        PendingChangeManager.queue(change(PendingDomain.TAGS, "shovel_js:t1", tagsApplied));

        PendingChangeManager.ApplyResult result = PendingChangeManager.applyDomain(PendingDomain.RECIPES);

        assertEquals(1, result.appliedCount());
        assertTrue(result.allSucceeded());
        assertEquals(1, recipesApplied.get());
        assertEquals(0, tagsApplied.get(), "TAGS entry must not be touched by an applyDomain(RECIPES) call");
        assertEquals(0, PendingChangeManager.sizeForDomain(PendingDomain.RECIPES), "applied entry must leave the queue");
        assertEquals(1, PendingChangeManager.sizeForDomain(PendingDomain.TAGS), "other domain's ticket must stay queued");
        assertEquals(Set.of(PendingDomain.TAGS), PendingChangeManager.domainsPresent());
    }

    @Test
    void applyDomainKeepsFailedEntryQueuedSameAsApplyAll() {
        PendingChangeManager.queue(failingChange(PendingDomain.LOOT, "shovel_js:bad"));
        PendingChangeManager.queue(change(PendingDomain.LOOT, "shovel_js:good", new AtomicInteger()));

        PendingChangeManager.ApplyResult result = PendingChangeManager.applyDomain(PendingDomain.LOOT);

        assertEquals(1, result.appliedCount());
        assertFalse(result.allSucceeded());
        assertEquals(1, result.failed().size());
        assertEquals("shovel_js:bad", result.failed().get(0).entryId());
        assertEquals(1, PendingChangeManager.sizeForDomain(PendingDomain.LOOT), "failed entry must stay in the queue, not be lost");
    }

    @Test
    void discardDomainOnlyClearsThatDomain() {
        AtomicInteger worldDiscards = new AtomicInteger();
        PendingChange worldChange = PendingChange.of(PendingDomain.WORLD, "shovel_js:w1", PendingChange.Action.DELETE, false,
                () -> {}, worldDiscards::incrementAndGet);
        PendingChangeManager.queue(worldChange);
        PendingChangeManager.queue(change(PendingDomain.CONTENT, "shovel_js:c1", new AtomicInteger()));

        PendingChangeManager.discardDomain(PendingDomain.WORLD);

        assertEquals(1, worldDiscards.get());
        assertEquals(0, PendingChangeManager.sizeForDomain(PendingDomain.WORLD));
        assertEquals(1, PendingChangeManager.sizeForDomain(PendingDomain.CONTENT), "CONTENT ticket must survive a discardDomain(WORLD) call");
    }

    @Test
    void applyAllStillAppliesEveryDomain() {
        PendingChangeManager.queue(change(PendingDomain.EVENTS, "shovel_js:e1", new AtomicInteger()));
        PendingChangeManager.queue(change(PendingDomain.CONTENT, "shovel_js:c1", new AtomicInteger()));

        PendingChangeManager.ApplyResult result = PendingChangeManager.applyAll();

        assertEquals(2, result.appliedCount());
        assertTrue(PendingChangeManager.isEmpty());
    }

    @Test
    void withDiffAttachesLazySuppliersWithoutMutatingOriginal() {
        PendingChange plain = change(PendingDomain.RECIPES, "shovel_js:r1", new AtomicInteger());
        assertFalse(plain.hasDiff());

        PendingChange withDiff = plain.withDiff(() -> "old", () -> "new");
        assertFalse(plain.hasDiff(), "withDiff must not mutate the original record");
        assertTrue(withDiff.hasDiff());
        assertEquals("old", withDiff.oldScript().get());
        assertEquals("new", withDiff.newScript().get());
    }

    @Test
    void domainHasDiffAndEntriesForDomainReflectOnlyDiffTaggedEntries() {
        PendingChangeManager.queue(change(PendingDomain.LOOT, "shovel_js:no_diff", new AtomicInteger()));
        assertFalse(PendingChangeManager.domainHasDiff(PendingDomain.LOOT));

        PendingChangeManager.queue(change(PendingDomain.LOOT, "shovel_js:with_diff", new AtomicInteger())
                .withDiff(() -> "old loot js", () -> "new loot js"));

        assertTrue(PendingChangeManager.domainHasDiff(PendingDomain.LOOT));
        assertEquals(2, PendingChangeManager.entriesForDomain(PendingDomain.LOOT).size());
        assertTrue(PendingChangeManager.entriesForDomain(PendingDomain.LOOT).stream()
                .anyMatch(c -> c.entryId().equals("shovel_js:with_diff") && c.hasDiff()));
    }

    @Test
    void applyWithoutPermissionKeepsEveryEntryQueued() {
        PendingChangeManager.setApplyPermissionForTests(() -> false);
        AtomicInteger applied = new AtomicInteger();
        PendingChangeManager.queue(change(PendingDomain.RECIPES, "shovel_js:r1", applied));

        PendingChangeManager.ApplyResult result = PendingChangeManager.applyAll();

        assertEquals(0, result.appliedCount());
        assertEquals(0, applied.get(), "onApply must not run without ADMIN");
        assertEquals(1, PendingChangeManager.size(), "rejected entry must stay queued");
    }

    @Test
    void requeueingTheSameIdKeepsBothUndoStepsAndTheOriginalBaseline() {
        java.util.List<String> undone = new java.util.ArrayList<>();
        PendingChangeManager.queue(PendingChange.of(PendingDomain.RECIPES, "shovel_js:x", PendingChange.Action.CREATE, false,
                () -> {}, () -> undone.add("v1")).withDiff(null, () -> "v1"));
        PendingChangeManager.queue(PendingChange.of(PendingDomain.RECIPES, "shovel_js:x", PendingChange.Action.EDIT, false,
                () -> {}, () -> undone.add("v2")).withDiff(() -> "v1", () -> "v2"));
        assertEquals(1, PendingChangeManager.sizeForDomain(PendingDomain.RECIPES));
        PendingChange merged = PendingChangeManager.entriesForDomain(PendingDomain.RECIPES).get(0);
        assertEquals(PendingChange.Action.CREATE, merged.action(), "sigue siendo una receta nueva respecto del disco");
        assertNull(merged.oldScript(), "el diff compara contra el estado de antes del primer Guardar");
        assertEquals("v2", merged.newScript().get());

        PendingChangeManager.discardDomain(PendingDomain.RECIPES);
        assertEquals(java.util.List.of("v2", "v1"), undone, "deshace los dos pasos, el mas nuevo primero");
    }
}
