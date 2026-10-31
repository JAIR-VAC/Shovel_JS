package com.kve.common.kubejs;

import com.google.gson.*;
import com.kve.CraftEditorMod;
import com.kve.common.trade.TradeBackend;
import com.kve.common.trade.TradeBackendRegistry;
import com.kve.common.trade.TradeEmitContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class TradeExporter {

    private TradeExporter() {}

    public enum TradeTargetKind {
        VILLAGER,
        WANDERING_TRADER,
        ENTITY
    }

    public enum TradeEditMode {
        REPLACE_LEVEL,
        ADD_OFFERS,
        REMOVE_MATCHING,
        CLEAR_PROFESSION,
        CLEAR_ENTITY
    }

    public static class TradeOffer {
        public String  inputAId = "";
        public int     inputACount = 1;
        public boolean inputAIsTag = false;

        /** blank = no second input. */
        public String  inputBId = "";
        public int     inputBCount = 1;
        public boolean inputBIsTag = false;

        public String  outputId = "";
        public int     outputCount = 1;
        public boolean outputIsTag = false;

        /** blank = none. */
        public String  outputNbt = "";

        public int     maxUses = 12;
        public int     villagerXp = 1;
        public float   priceMultiplier = 0.05f;
        public boolean enabled = true;

        /**
         * Niveles DESPUES de
         * {@link TradeEntry#level} en que se desbloquea esta oferta. 0 = junto
         * a las demas ofertas de ese nivel; N = el aldeano la recibe recien al
         * llegar a {@code level + N} (tope 5). Solo aplica a destinos con
         * nivel (VILLAGER / ENTITY por tabla de profesiones) en ADD_OFFERS.
         */
        public int     unlockOffset = 0;

        public String  editInputAId = "";
        public String  editInputBId = "";
        public String  editOutputId = "";

        public TradeOffer() {}

        public boolean isEdit() { return editInputAId != null && !editInputAId.isBlank(); }

        /** Nivel real en que se desbloquea con un nivel base dado (1..5). */
        public int effectiveLevel(int baseLevel) { return Math.max(1, Math.min(5, baseLevel + Math.max(0, unlockOffset))); }

        public TradeOffer copy() {
            TradeOffer c = new TradeOffer();
            c.inputAId = inputAId; c.inputACount = inputACount; c.inputAIsTag = inputAIsTag;
            c.inputBId = inputBId; c.inputBCount = inputBCount; c.inputBIsTag = inputBIsTag;
            c.outputId = outputId; c.outputCount = outputCount; c.outputIsTag = outputIsTag;
            c.outputNbt = outputNbt;
            c.maxUses = maxUses; c.villagerXp = villagerXp; c.priceMultiplier = priceMultiplier;
            c.enabled = enabled;
            c.unlockOffset = unlockOffset;
            c.editInputAId = editInputAId; c.editInputBId = editInputBId; c.editOutputId = editOutputId;
            return c;
        }
    }

    public static class TradeEntry {
        /** Stable KVE key, e.g. "kve:armorer_level2_core" - IS the {@code ENTRIES} map key directly, not a derived composite like loot's. */
        public String id = "";
        public boolean enabled = true;
        public String category = "";
        public TradeTargetKind targetKind = TradeTargetKind.VILLAGER;

        /** required if targetKind == VILLAGER, e.g. "minecraft:armorer". */
        public String professionId = "";
        /** "minecraft:wandering_trader" | "mod:merchant" - required for WANDERING_TRADER/ENTITY. */
        public String entityTypeId = "";

        /** 1..5. v1: 0/ANY not supported for VILLAGER (D4). */
        public int level = 1;
        public TradeEditMode mode = TradeEditMode.REPLACE_LEVEL;
        public List<TradeOffer> offers = new ArrayList<>();

        /** "" = auto (first available backend that supports the target/mode). */
        public String backendId = "";
        public long modifiedAt = 0L;

        public TradeEntry() {}

        public TradeEntry copy() {
            TradeEntry c = new TradeEntry();
            c.id = id; c.enabled = enabled; c.category = category; c.targetKind = targetKind;
            c.professionId = professionId; c.entityTypeId = entityTypeId; c.level = level;
            c.mode = mode; c.backendId = backendId; c.modifiedAt = modifiedAt;
            for (TradeOffer o : offers) c.offers.add(o.copy());
            return c;
        }
    }

    /**
     * No dedicated {@code TradeValidator} class on purpose -
     * same convention as {@code LootTableExporter.validateLootTable}/
     * {@code KubeJSItemAttrExporter.validateItem}: a static method living
     * directly on the exporter, reusing {@link SemanticValidator} for
     * anything id-shaped rather than reinventing format checks.
     */
    public static List<String> validateTrade(TradeEntry e) {
        List<String> errors = new ArrayList<>();
        if (e == null) { errors.add("Trade entry is null."); return errors; }
        if (e.id == null || e.id.isBlank()) errors.add("Trade id vacio.");

        switch (e.targetKind) {
            case VILLAGER -> {
                if (e.professionId == null || e.professionId.isBlank()) {
                    errors.add("Profession vacia (requerida para VILLAGER).");
                } else {
                    errors.addAll(SemanticValidator.validateNamespacedIdFormat(e.professionId, "Profession"));
                }
                if (e.level < 1 || e.level > 5) errors.add("Level fuera de rango (1..5 requerido para VILLAGER en v1, D4).");
                if (e.mode == TradeEditMode.CLEAR_ENTITY) errors.add("CLEAR_ENTITY no es válido para VILLAGER (usar CLEAR_PROFESSION).");
            }
            case WANDERING_TRADER -> {
                errors.addAll(SemanticValidator.validateEntityId(e.entityTypeId, "Entity"));
                if (e.mode == TradeEditMode.CLEAR_PROFESSION) errors.add("CLEAR_PROFESSION no es válido fuera de VILLAGER (usar CLEAR_ENTITY).");
                if (e.level < 1 || e.level > 2) errors.add("Level fuera de rango (1=generic, 2=rare requerido para WANDERING_TRADER).");
            }
            case ENTITY -> {
                errors.addAll(SemanticValidator.validateEntityId(e.entityTypeId, "Entity"));
                if (e.mode == TradeEditMode.CLEAR_PROFESSION && !com.kve.common.trade.KveNativeTradeBackend.usesProfessionTable(e)) {
                    errors.add("CLEAR_PROFESSION solo es válido con una profesion vanilla (usar CLEAR_ENTITY).");
                }
                if (e.professionId != null && !e.professionId.isBlank()) {
                    errors.addAll(SemanticValidator.validateNamespacedIdFormat(e.professionId, "Profession"));
                }
                if (com.kve.common.trade.KveNativeTradeBackend.usesProfessionTable(e)) {
                    if (e.level < 1 || e.level > 5) errors.add("Level fuera de rango (1..5 requerido, D4).");
                    if (e.mode == TradeEditMode.CLEAR_ENTITY) errors.add("CLEAR_ENTITY no es válido para ENTITY con profesion vanilla (usar CLEAR_PROFESSION).");
                } else if (com.kve.common.trade.TradeTypeDescriptorRegistry.merchantKind(e.entityTypeId)
                        == com.kve.common.trade.MerchantTypeDetector.Kind.NOT_MERCHANT) {
                    errors.add("La entidad '" + e.entityTypeId + "' no es un comerciante (no implementa Merchant) - ningun trade la alcanza.");
                }
            }
        }

        boolean needsOffers = e.mode == TradeEditMode.REPLACE_LEVEL || e.mode == TradeEditMode.ADD_OFFERS
                || e.mode == TradeEditMode.REMOVE_MATCHING;
        if (needsOffers && e.enabled) {
            boolean anyEnabled = e.offers.stream().anyMatch(o -> o.enabled);
            if (!anyEnabled) errors.add("Se requiere al menos una oferta habilitada.");
        }

        TradeBackend backend = resolveBackend(e);
        for (TradeOffer o : e.offers) {
            if (!o.enabled) continue;
            errors.addAll(validateOffer(o, backend));
        }
        boolean anyEdit = e.offers.stream().anyMatch(o -> o.enabled && o.isEdit());
        boolean anyOffset = e.offers.stream().anyMatch(o -> o.enabled && o.unlockOffset > 0);
        if (anyEdit && e.mode != TradeEditMode.ADD_OFFERS) {
            errors.add("Las ofertas que editan un trade existente requieren el modo Agregar/editar.");
        }
        if (anyOffset && (e.mode != TradeEditMode.ADD_OFFERS || !hasLevels(e))) {
            errors.add("El desbloqueo en niveles posteriores solo aplica a aldeanos con nivel en modo Agregar/editar.");
        }

        if (backend != null && !backend.isAvailable()) {
            errors.add("Backend '" + backend.id() + "' elegido explícitamente pero no está disponible (¿falta el addon o está desactivado en la config?).");
        } else if (backend == null) {
            errors.add("Sin backend disponible para este trade (ver gui.kve.trades.no_backend).");
        } else if (!backend.supportsMode(e.mode)) {
            errors.add("El backend resuelto no soporta el modo " + e.mode + ".");
        } else if (!backend.supportsEntry(e)) {
            errors.add("El backend resuelto no soporta el target " + e.targetKind + ".");
        }

        return errors;
    }

    public static List<String> validateOffer(TradeOffer o, TradeBackend backend) {
        List<String> errors = new ArrayList<>();
        if (o == null) { errors.add("Oferta nula."); return errors; }
        errors.addAll(validateOfferSide(o.inputAId, o.inputAIsTag, o.inputACount, "Input A"));
        if (o.inputBId != null && !o.inputBId.isBlank()) {
            errors.addAll(validateOfferSide(o.inputBId, o.inputBIsTag, o.inputBCount, "Input B"));
        }
        if (o.outputId == null || o.outputId.isBlank()) errors.add("Output vacio en una oferta.");
        else errors.addAll(validateOfferSide(o.outputId, o.outputIsTag, o.outputCount, "Output"));
        if (o.outputIsTag && (backend == null || !backend.supportsTagOutput())) {
            errors.add("Output como tag no soportado por el backend resuelto.");
        }
        errors.addAll(SemanticValidator.validateNbt(o.outputNbt, "Output NBT"));
        if (o.isEdit()) {
            errors.addAll(SemanticValidator.validateItemId(o.editInputAId, "Trade a editar (input A)"));
            if (o.editInputBId != null && !o.editInputBId.isBlank()) errors.addAll(SemanticValidator.validateItemId(o.editInputBId, "Trade a editar (input B)"));
            if (o.editOutputId == null || o.editOutputId.isBlank()) errors.add("Trade a editar sin output.");
            else errors.addAll(SemanticValidator.validateItemId(o.editOutputId, "Trade a editar (output)"));
        }
        if (o.unlockOffset < 0 || o.unlockOffset > 4) errors.add("Desbloqueo fuera de rango (0..4 niveles despues).");
        if (o.maxUses < 1) errors.add("Max uses debe ser >= 1 (con 0 la oferta nace agotada).");
        if (o.villagerXp < 0) errors.add("Villager XP no puede ser negativo.");
        if (Float.isNaN(o.priceMultiplier) || Float.isInfinite(o.priceMultiplier) || o.priceMultiplier < 0f) {
            errors.add("Price multiplier debe ser un numero >= 0.");
        }
        return errors;
    }

    private static List<String> validateOfferSide(String id, boolean isTag, int count, String label) {
        List<String> errors = new ArrayList<>();
        if (id == null || id.isBlank()) { errors.add(label + " vacio."); return errors; }
        errors.addAll(isTag ? SemanticValidator.validateTagId(id, label) : SemanticValidator.validateItemId(id, label));
        if (count < 1) errors.add(label + ": count debe ser >= 1.");
        // Un count por encima del stack maximo del item nunca se puede
        // cumplir como input (el slot de comercio no acepta mas) ni
        // entregar bien como output - vanilla no lo rechaza, simplemente
        // deja una oferta muerta. Solo si el id ya es valido (sin ruido
        // duplicado) y el registry de items esta listo.
        if (errors.isEmpty() && !isTag && count > 1) {
            int max = maxStackSizeOf(id);
            if (max > 0 && count > max) {
                errors.add(label + ": count " + count + " excede el stack maximo (" + max + ") de " + id + ".");
            }
        }
        return errors;
    }

    /** Stack maximo real del item, o -1 si no se puede saber (registry no listo, id desconocido/AIR) - -1 significa "no validar", nunca "invalido". */
    private static int maxStackSizeOf(String id) {
        if (!SemanticValidator.itemRegistryReady()) return -1;
        try {
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .get(net.minecraft.resources.ResourceLocation.parse(id));
            return item == net.minecraft.world.item.Items.AIR ? -1 : new net.minecraft.world.item.ItemStack(item).getMaxStackSize();
        } catch (Throwable t) {
            return -1;
        }
    }

    public static List<String> findConflicts(TradeEntry e) {
        List<String> ids = new ArrayList<>();
        if (e == null || !e.enabled || e.mode != TradeEditMode.REPLACE_LEVEL) return ids;
        for (TradeEntry o : ENTRIES.values()) {
            if (o == e || o.id.equals(e.id) || !o.enabled || o.mode != TradeEditMode.REPLACE_LEVEL) continue;
            if (o.targetKind != e.targetKind || o.level != e.level) continue;
            boolean sameTarget = switch (e.targetKind) {
                case VILLAGER -> o.professionId.equals(e.professionId);
                case WANDERING_TRADER -> true;
                case ENTITY -> o.entityTypeId.equals(e.entityTypeId) && o.professionId.equals(e.professionId);
            };
            if (sameTarget) ids.add(o.id);
        }
        return ids;
    }

    /** True si el destino tiene niveles de aldeano (VILLAGER, o ENTITY que usa la tabla de profesiones vanilla). */
    public static boolean hasLevels(TradeEntry e) {
        if (e == null) return false;
        return e.targetKind == TradeTargetKind.VILLAGER
                || com.kve.common.trade.KveNativeTradeBackend.usesProfessionTable(e);
    }

    /** Resolves whatever backend would handle this entry today - "" backendId means auto (first available match). Pure, no I/O. */
    public static TradeBackend resolveBackend(TradeEntry e) {
        if (e == null) return null;
        return TradeBackendRegistry.resolve(e).orElse(null);
    }

    /** True if the backend that would handle this entry only takes effect after a restart. False (safe default) if no backend resolves - {@code validateTrade()} is what actually blocks that case. */
    public static boolean requiresRestartFor(TradeEntry e) {
        TradeBackend b = resolveBackend(e);
        return b != null && b.requiresRestart();
    }

    private static final Map<String, TradeEntry> ENTRIES = new LinkedHashMap<>();
    private static final Map<String, Deque<TradeEntry>> HISTORY =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Deque<TradeEntry>> eldest) {
                    return size() > Math.max(1, com.kve.common.config.KVEConfig.undoHistoryMaxKeys);
                }
            };

    public static Collection<TradeEntry> getEntries() { return Collections.unmodifiableCollection(ENTRIES.values()); }
    public static TradeEntry             getEntry(String id) { return ENTRIES.get(id); }

    /** ENTRIES.clear() + HISTORY.clear() - tests, same contract as every sibling exporter's clearAll(). */
    public static void clearAll() { ENTRIES.clear(); HISTORY.clear(); }

    /** ENTRIES.put(e.id, e) - loads from disk (state file bootstrap). */
    public static void loadEntry(TradeEntry e) { ENTRIES.put(e.id, e); }

    public static void stageTrade(TradeEntry entry) {
        TradeEntry previous = ENTRIES.get(entry.id);
        if (previous != null) pushHistory(entry.id, previous);
        entry.modifiedAt = System.currentTimeMillis();
        ENTRIES.put(entry.id, entry);
    }

    public static void discardStagedTrade(String id, TradeEntry previousOrNull) {
        if (previousOrNull != null) ENTRIES.put(id, previousOrNull);
        else ENTRIES.remove(id);
    }

    public static void stageDeleteTrade(String id) {
        ENTRIES.remove(id);
        HISTORY.remove(id);
    }

    public static TradeEntry stageUndo(String id) {
        Deque<TradeEntry> h = HISTORY.get(id);
        if (h == null || h.isEmpty()) return null;
        TradeEntry restored = h.removeFirst();
        ENTRIES.put(id, restored);
        return restored;
    }

    public static void discardStagedUndo(String id, TradeEntry restored, TradeEntry beforeUndoOrNull) {
        Deque<TradeEntry> h = HISTORY.computeIfAbsent(id, k -> new ArrayDeque<>());
        h.addFirst(restored);
        if (beforeUndoOrNull != null) ENTRIES.put(id, beforeUndoOrNull);
        else ENTRIES.remove(id);
    }

    public static boolean stageSetEnabled(String id, boolean enabled) {
        TradeEntry e = ENTRIES.get(id);
        if (e == null) return false;
        pushHistory(id, e);
        e.enabled = enabled;
        return true;
    }

    public static void discardStagedSetEnabled(String id, boolean previousEnabled) {
        TradeEntry e = ENTRIES.get(id);
        if (e != null) e.enabled = previousEnabled;
    }

    public static boolean canUndo(String id) {
        Deque<TradeEntry> h = HISTORY.get(id);
        return h != null && !h.isEmpty();
    }

    public static TradeEntry undo(String id) {
        Deque<TradeEntry> h = HISTORY.get(id);
        if (h == null || h.isEmpty()) return null;
        TradeEntry previous = h.removeFirst();
        ENTRIES.put(id, previous);
        return previous;
    }

    private static void pushHistory(String id, TradeEntry snapshot) {
        int max = com.kve.common.config.KVEConfig.undoHistorySize;
        if (max <= 0) return;
        Deque<TradeEntry> h = HISTORY.computeIfAbsent(id, k -> new ArrayDeque<>());
        h.addFirst(snapshot.copy());
        while (h.size() > max) h.removeLast();
    }

    /**
     * v1.2 §4.3 order: (1) persist state always - it's the editor's source
     * of truth regardless of which backend(s) resolve; (2) per enabled
     * entry, apply+emit via its resolved backend - {@code kve_native}
     * mutates the runtime trade tables directly (see {@link
     * TradeBackend#applyRuntime()}); a JS-based backend would instead
     * contribute lines to a freshly rewritten {@code trades.js} - both can
     * coexist per §4.3 point 3, one entry's backend choice never depends on
     * another's.
     */
    public static void commitTrades() throws IOException {
        saveState();
        writeScript();
        deleteLegacyTradesScript();
    }

    private static void deleteLegacyTradesScript() {
        try {
            Files.deleteIfExists(KveScriptPaths.SERVER_ROOT.resolve("trades.js"));
        } catch (IOException ignored) {}
    }

    /** Public boot-time entry point for {@code CraftEditorMod#commonSetup} - same cleanup as {@link #deleteLegacyTradesScript()}, reachable before any Trade Editor Save this session. */
    public static void deleteLegacyTradesScriptOnBoot() { deleteLegacyTradesScript(); }

    /** kubejs/server_scripts/kve/trades.js (§4.1, §9). */
    public static Path scriptPath() { return KveScriptPaths.tradesFile(); }

    /** kubejs/config/kve_trades_state.json - misma convención que CustomItemState (§4.1, §9). */
    public static Path statePath() { return Paths.get("kubejs", "config", "shovel_js_trades_state.json"); }

    private static final List<String> LAST_WRITE_WARNINGS = new ArrayList<>();
    public static List<String> getLastWriteWarnings() { return Collections.unmodifiableList(LAST_WRITE_WARNINGS); }

    private static void writeScript() throws IOException {
        LAST_WRITE_WARNINGS.clear();
        Files.createDirectories(scriptPath().getParent());

        Map<String, TradeBackend> backendsUsed = new LinkedHashMap<>();
        Map<String, List<String>> bodyLinesByBackend = new LinkedHashMap<>();
        // §13 "DISABLE / delete regeneran... sin residuos": which entry
        // ids are, right now, enabled+valid and resolved to each backend -
        // fed to every backend's reconcileRuntime() below so a backend that
        // tracks its own runtime mutations (kve_native) can retract
        // anything it applied for an id that dropped out of this set,
        // whether because the entry got disabled or deleted outright.
        Map<String, Set<String>> committedIdsByBackend = new LinkedHashMap<>();

        for (TradeEntry e : ENTRIES.values()) {
            TradeBackend backend = resolveBackend(e);

            if (!e.enabled) {
                if (backend != null) backend.applyRuntime(e);
                continue;
            }

            List<String> semantic = validateTrade(e);
            if (!semantic.isEmpty()) {
                String reason = String.join("; ", semantic);
                String warning = "Trade '" + e.id + "' omitido: " + reason;
                LAST_WRITE_WARNINGS.add(warning);
                CraftEditorMod.LOGGER.warn("[Shovel JS] {}", warning);
                continue;
            }
            if (backend == null) continue;

            backend.applyRuntime(e);
            committedIdsByBackend.computeIfAbsent(backend.id(), k -> new LinkedHashSet<>()).add(e.id);

            List<String> lines = backend.emit(e, TradeEmitContext.of("  "));
            if (lines.isEmpty()) continue;
            backendsUsed.putIfAbsent(backend.id(), backend);
            bodyLinesByBackend.computeIfAbsent(backend.id(), k -> new ArrayList<>()).addAll(lines);
        }

        for (TradeBackend backend : TradeBackendRegistry.all()) {
            backend.reconcileRuntime(committedIdsByBackend.getOrDefault(backend.id(), Set.of()));
        }

        if (backendsUsed.isEmpty()) {
            Files.deleteIfExists(scriptPath());
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("// Shovel JS - Trades (AUTO-GENERATED, no editar a mano)\n\n");
        for (var entry : backendsUsed.entrySet()) {
            TradeBackend backend = entry.getValue();
            List<String> lines = bodyLinesByBackend.getOrDefault(entry.getKey(), List.of());
            String body = String.join("\n", lines);
            sb.append(backend.wrapFile(body));
            sb.append("\n");
        }

        Path tmp = scriptPath().resolveSibling(scriptPath().getFileName().toString() + ".tmp");
        Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp, scriptPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Read-only preview of what one entry would emit, for the editor's
     * Preview button and {@code PendingChange.withDiff} - pure, no I/O, no
     * ENTRIES/backendsUsed grouping (§4.1: "usado por Diff (withDiff) y
     * por el panel Preview").
     */
    public static String previewTradeJS(TradeEntry e) {
        if (e == null) return "// (sin datos de trade)";
        try {
            TradeBackend backend = resolveBackend(e);
            if (backend == null) return "// SIN BACKEND DISPONIBLE (" + (e.backendId.isBlank() ? "auto" : e.backendId) + ")";
            List<String> lines = backend.previewLines(e, TradeEmitContext.of("  "));
            return backend.wrapFile(String.join("\n", lines));
        } catch (Exception ex) {
            return "// ERROR generando preview: " + ex.getMessage();
        }
    }

    private static boolean loaded = false;
    private static long lastMtime = 0L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void loadStateIfNeeded() {
        if (loaded) {
            try {
                long mtime = Files.exists(statePath()) ? Files.getLastModifiedTime(statePath()).toMillis() : 0;
                if (mtime == lastMtime) return;
            } catch (IOException ignored) { return; }
        }
        loadState();
    }

    private static void loadState() {
        loaded = true;
        ENTRIES.clear();
        if (!Files.exists(statePath())) { lastMtime = 0; return; }
        try {
            lastMtime = Files.getLastModifiedTime(statePath()).toMillis();
            for (TradeEntry e : readStateFile()) ENTRIES.put(e.id, e);
            CraftEditorMod.LOGGER.info("[Shovel JS] Trades loaded from {}", statePath());
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Failed to load trades state: {}", ex.getMessage());
        }
    }

    private static List<TradeEntry> readStateFile() throws IOException {
        List<TradeEntry> out = new ArrayList<>();
        if (!Files.exists(statePath())) return out;
        String json = Files.readString(statePath(), StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (root.has("trades")) {
            for (JsonElement el : root.getAsJsonArray("trades")) {
                try {
                    TradeEntry e = entryFromJson(el.getAsJsonObject());
                    if (!e.id.isBlank()) out.add(e);
                } catch (Exception ex) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped trade entry: {}", ex.getMessage());
                }
            }
        }
        return out;
    }

    public static int reapplyRuntimeFromState() {
        List<TradeEntry> persisted;
        try {
            persisted = readStateFile();
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] No se pudo leer {} para re-aplicar trades al arrancar: {}", statePath(), ex.getMessage());
            return 0;
        }
        return reapplyRuntime(persisted);
    }

    static int reapplyRuntime(Collection<TradeEntry> entries) {
        Map<String, Set<String>> committedIdsByBackend = new LinkedHashMap<>();
        int applied = 0;
        for (TradeEntry e : entries) {
            if (e == null || !e.enabled) continue;
            try {
                TradeBackend backend = resolveBackend(e);
                if (backend == null) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] Trade '{}' no se re-aplica al arrancar: sin backend disponible.", e.id);
                    continue;
                }
                // catch Throwable (no solo Exception) a proposito: en un
                // servidor dedicado un Error de clase-cliente que se colara
                // desde validacion no debe tumbar el arranque del server.
                List<String> problems = validateTrade(e);
                if (!problems.isEmpty()) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] Trade '{}' omitido al arrancar: {}", e.id, String.join("; ", problems));
                    continue;
                }
                backend.applyRuntime(e);
                committedIdsByBackend.computeIfAbsent(backend.id(), k -> new LinkedHashSet<>()).add(e.id);
                applied++;
            } catch (Throwable t) {
                CraftEditorMod.LOGGER.error("[Shovel JS] Fallo re-aplicando trade '{}' al arrancar: {}", e.id, t.toString());
            }
        }
        for (TradeBackend backend : TradeBackendRegistry.all()) {
            try {
                backend.reconcileRuntime(committedIdsByBackend.getOrDefault(backend.id(), Set.of()));
            } catch (Throwable t) {
                CraftEditorMod.LOGGER.error("[Shovel JS] reconcileRuntime fallo para backend '{}': {}", backend.id(), t.toString());
            }
        }
        return applied;
    }

    private static void saveState() throws IOException {
        Files.createDirectories(statePath().getParent());
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (TradeEntry e : ENTRIES.values()) arr.add(entryToJson(e));
        root.add("trades", arr);
        Path tmp = statePath().resolveSibling(statePath().getFileName().toString() + ".tmp");
        Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp, statePath(), StandardCopyOption.REPLACE_EXISTING);
        try { lastMtime = Files.getLastModifiedTime(statePath()).toMillis(); } catch (IOException ignored) {}
    }

    private static JsonObject entryToJson(TradeEntry e) {
        JsonObject o = new JsonObject();
        o.addProperty("id", e.id);
        o.addProperty("enabled", e.enabled);
        o.addProperty("category", e.category);
        o.addProperty("targetKind", e.targetKind.name());
        o.addProperty("professionId", e.professionId);
        o.addProperty("entityTypeId", e.entityTypeId);
        o.addProperty("level", e.level);
        o.addProperty("mode", e.mode.name());
        o.addProperty("backendId", e.backendId);
        o.addProperty("modifiedAt", e.modifiedAt);
        JsonArray offers = new JsonArray();
        for (TradeOffer of : e.offers) {
            JsonObject oo = new JsonObject();
            oo.addProperty("inputAId", of.inputAId); oo.addProperty("inputACount", of.inputACount); oo.addProperty("inputAIsTag", of.inputAIsTag);
            oo.addProperty("inputBId", of.inputBId); oo.addProperty("inputBCount", of.inputBCount); oo.addProperty("inputBIsTag", of.inputBIsTag);
            oo.addProperty("outputId", of.outputId); oo.addProperty("outputCount", of.outputCount); oo.addProperty("outputIsTag", of.outputIsTag);
            oo.addProperty("outputNbt", of.outputNbt);
            oo.addProperty("maxUses", of.maxUses); oo.addProperty("villagerXp", of.villagerXp); oo.addProperty("priceMultiplier", of.priceMultiplier);
            oo.addProperty("enabled", of.enabled);
            writeOfferExtras(oo, of);
            offers.add(oo);
        }
        o.add("offers", offers);
        return o;
    }

    private static TradeEntry entryFromJson(JsonObject o) {
        TradeEntry e = new TradeEntry();
        e.id = str(o, "id", "");
        e.enabled = !o.has("enabled") || o.get("enabled").getAsBoolean();
        e.category = str(o, "category", "");
        e.targetKind = o.has("targetKind") ? TradeTargetKind.valueOf(o.get("targetKind").getAsString()) : TradeTargetKind.VILLAGER;
        e.professionId = str(o, "professionId", "");
        e.entityTypeId = str(o, "entityTypeId", "");
        e.level = o.has("level") ? o.get("level").getAsInt() : 1;
        e.mode = o.has("mode") ? TradeEditMode.valueOf(o.get("mode").getAsString()) : TradeEditMode.REPLACE_LEVEL;
        e.backendId = str(o, "backendId", "");
        e.modifiedAt = o.has("modifiedAt") ? o.get("modifiedAt").getAsLong() : System.currentTimeMillis();
        if (o.has("offers")) {
            for (JsonElement el : o.getAsJsonArray("offers")) {
                try {
                    JsonObject oo = el.getAsJsonObject();
                    TradeOffer of = new TradeOffer();
                    of.inputAId = str(oo, "inputAId", ""); of.inputACount = oo.has("inputACount") ? oo.get("inputACount").getAsInt() : 1;
                    of.inputAIsTag = oo.has("inputAIsTag") && oo.get("inputAIsTag").getAsBoolean();
                    of.inputBId = str(oo, "inputBId", ""); of.inputBCount = oo.has("inputBCount") ? oo.get("inputBCount").getAsInt() : 1;
                    of.inputBIsTag = oo.has("inputBIsTag") && oo.get("inputBIsTag").getAsBoolean();
                    of.outputId = str(oo, "outputId", ""); of.outputCount = oo.has("outputCount") ? oo.get("outputCount").getAsInt() : 1;
                    of.outputIsTag = oo.has("outputIsTag") && oo.get("outputIsTag").getAsBoolean();
                    of.outputNbt = str(oo, "outputNbt", "");
                    of.maxUses = oo.has("maxUses") ? oo.get("maxUses").getAsInt() : 12;
                    of.villagerXp = oo.has("villagerXp") ? oo.get("villagerXp").getAsInt() : 1;
                    of.priceMultiplier = oo.has("priceMultiplier") ? oo.get("priceMultiplier").getAsFloat() : 0.05f;
                    of.enabled = !oo.has("enabled") || oo.get("enabled").getAsBoolean();
                    readOfferExtras(oo, of);
                    e.offers.add(of);
                } catch (Exception ex) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped trade offer on '{}': {}", e.id, ex.getMessage());
                }
            }
        }
        return e;
    }

    /** Lo que el editor tenia abierto: la entrada en edicion + estado de la UI. */
    public record EditorDraft(TradeEntry entry, String currentId, int selectedOffer, String search) {}

    private static EditorDraft sessionDraft = null;

    public static Path draftPath() { return KveScriptPaths.CONFIG_ROOT.resolve("shovel_js_trades_draft.json"); }

    public static JsonObject draftToJson(EditorDraft d) {
        JsonObject o = new JsonObject();
        o.add("entry", entryToJson(d.entry()));
        o.addProperty("currentId", d.currentId() == null ? "" : d.currentId());
        o.addProperty("selectedOffer", d.selectedOffer());
        o.addProperty("search", d.search() == null ? "" : d.search());
        return o;
    }

    public static EditorDraft draftFromJson(JsonObject o) {
        TradeEntry e = entryFromJson(o.getAsJsonObject("entry"));
        // entryFromJson() cae a REPLACE_LEVEL sin "mode" (compat de archivos
        // viejos); el borrador siempre lo escribe, asi que no aplica aca.
        return new EditorDraft(e, str(o, "currentId", ""),
                o.has("selectedOffer") ? o.get("selectedOffer").getAsInt() : -1, str(o, "search", ""));
    }

    /** Guarda el borrador en memoria y en {@link #draftPath()}. Nunca tira. */
    public static void saveEditorDraft(EditorDraft d) {
        if (d == null || d.entry() == null) return;
        sessionDraft = d;
        try {
            com.kve.common.util.SafeFiles.writeAtomic(draftPath(), GSON.toJson(draftToJson(d)));
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] No se pudo guardar el borrador de trades: {}", ex.getMessage());
        }
    }

    /** Ultimo borrador (memoria primero, despues disco); null si no hay. */
    public static EditorDraft loadEditorDraft() {
        if (sessionDraft != null) return sessionDraft;
        Path p = draftPath();
        if (!Files.exists(p)) return null;
        try {
            sessionDraft = draftFromJson(JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Borrador de trades ilegible, se ignora: {}", ex.getMessage());
        }
        return sessionDraft;
    }

    /** Campos (desbloqueo/edicion) - solo se escriben si no son default, asi el JSON de entries viejas no cambia. Compartido con TradeOfferTemplates. */
    public static void writeOfferExtras(JsonObject oo, TradeOffer of) {
        if (of.unlockOffset != 0) oo.addProperty("unlockOffset", of.unlockOffset);
        if (of.isEdit()) {
            oo.addProperty("editInputAId", of.editInputAId);
            oo.addProperty("editInputBId", of.editInputBId == null ? "" : of.editInputBId);
            oo.addProperty("editOutputId", of.editOutputId == null ? "" : of.editOutputId);
        }
    }

    public static void readOfferExtras(JsonObject oo, TradeOffer of) {
        of.unlockOffset = oo.has("unlockOffset") ? oo.get("unlockOffset").getAsInt() : 0;
        of.editInputAId = str(oo, "editInputAId", "");
        of.editInputBId = str(oo, "editInputBId", "");
        of.editOutputId = str(oo, "editOutputId", "");
    }

    private static String str(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }
}
