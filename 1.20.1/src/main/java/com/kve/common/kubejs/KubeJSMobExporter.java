package com.kve.common.kubejs;

import com.kve.common.util.KveNumbers;
import com.kve.common.util.JsStrings;
import com.kve.CraftEditorMod;
import com.kve.common.persist.CraftEditorState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class KubeJSMobExporter {

    public static final String[] EQUIP_SLOTS = {"MAINHAND", "OFFHAND", "HEAD", "CHEST", "LEGS", "FEET"};

    private static final Set<String> LEGACY_ATTR_IDS = new LinkedHashSet<>(Arrays.asList(
            "minecraft:generic.max_health", "minecraft:generic.armor", "minecraft:generic.armor_toughness",
            "minecraft:generic.attack_damage", "minecraft:generic.attack_speed", "minecraft:generic.movement_speed",
            "minecraft:generic.knockback_resistance", "minecraft:generic.follow_range"));

    /** Index of {@code slot} in {@link #EQUIP_SLOTS}, or -1 if it isn't one of the six valid slots. */
    public static int indexOfSlot(String slot) {
        for (int i = 0; i < EQUIP_SLOTS.length; i++) if (EQUIP_SLOTS[i].equals(slot)) return i;
        return -1;
    }

    public static final String[] BEHAVIOR_FLAGS = {
            "NO_AI", "FLIES", "CAN_PICKUP_LOOT", "BABY",
            "PERSISTENT", "SILENT", "INVULNERABLE", "INVISIBLE", "GLOWING", "LEFT_HANDED",
            "GRIEFS", "HOSTILE", "NEUTRAL", "PASSIVE", "FLEES_PLAYER", "RANGED_ATTACK",
            "FIRE_IMMUNE", "FALL_IMMUNE", "DROWN_IMMUNE", "EXPLOSION_IMMUNE", "PROJECTILE_IMMUNE", "NO_KNOCKBACK"
    };

    /** Flags de actitud mutuamente excluyentes - elegir uno en el picker desmarca los otros (HOSTILE y PASSIVE a la vez no tiene sentido). */
    public static final Set<String> EXCLUSIVE_ATTITUDE_FLAGS = new LinkedHashSet<>(Arrays.asList("HOSTILE", "NEUTRAL", "PASSIVE"));

    static List<String> validateCombat(MobEntry e) {
        List<String> errors = new ArrayList<>();
        if (e.xpDrop > 100000) errors.add("XP al morir fuera de rango (0-100000): " + e.xpDrop);
        if (!Double.isNaN(e.regenPerSecond) && (e.regenPerSecond < 0 || e.regenPerSecond > 1000)) errors.add("Regeneracion fuera de rango (0-1000 por segundo): " + e.regenPerSecond);
        if (!Double.isNaN(e.damageTakenMult) && (e.damageTakenMult < 0 || e.damageTakenMult > 100)) errors.add("Multiplicador de daño recibido fuera de rango (0-100): " + e.damageTakenMult);
        if (!Double.isNaN(e.damageDealtMult) && (e.damageDealtMult < 0 || e.damageDealtMult > 100)) errors.add("Multiplicador de daño hecho fuera de rango (0-100): " + e.damageDealtMult);
        return errors;
    }

    private static final Set<String> EXTENDED_BEHAVIOR_FLAGS = new LinkedHashSet<>(Arrays.asList(
            "GRIEFS", "HOSTILE", "NEUTRAL", "PASSIVE", "FLEES_PLAYER", "RANGED_ATTACK"));

    /** True if {@code slot} is one of the six valid equipment slots. */
    public static boolean isValidSlot(String slot) {
        return indexOfSlot(slot) >= 0;
    }

    /**
     * True if {@code attrId} is one of {@link #LEGACY_ATTR_IDS} - exposed
     * for {@code com.kve.common.mobrules.MobAttributeEnforcer}, which
     * needs the exact same dedup rule this class already used inside the
     * old {@code appendGeneratedMobBody()} attributeMods loop (a custom
     * Atributos Personalizados entry that repeats one of the 8 dedicated
     * fields must lose to the dedicated field, not double-apply / silently
     * override it) now that the enforcer applies attributes directly in
     * Java instead of via generated JS - see its class doc.
     */
    public static boolean isLegacyAttrId(String attrId) {
        return LEGACY_ATTR_IDS.contains(attrId);
    }

    public static class AttrMod {
        public String attrId;
        public double value;
        public String dimension = "";
        public double probability = 1.0;

        public AttrMod() {}
        public AttrMod(String attrId, double value, String dimension, double probability) {
            this.attrId = attrId;
            this.value = value;
            this.dimension = dimension == null ? "" : dimension;
            this.probability = probability;
        }

        public AttrMod copy() { return new AttrMod(attrId, value, dimension, probability); }
    }

    public static class EquipmentItem {
        public String slot;
        public String itemId = "";
        public double dropChance = 0.0;
        public double spawnChance = 1.0;
        /** Raw SNBT (e.g. `{Enchantments:[{id:"minecraft:sharpness",lvl:5}]}`), empty when the item carries no extra data. Same string shape CraftEditorScreen/NbtEditorScreen already use for recipe ingredients - see KubeJSExporter#itemJs. */
        public String nbt = "";

        public EquipmentItem() {}
        public EquipmentItem(String slot, String itemId, double dropChance) {
            this.slot = slot;
            this.itemId = itemId == null ? "" : itemId;
            this.dropChance = dropChance;
        }
        public EquipmentItem(String slot, String itemId, double dropChance, String nbt) {
            this(slot, itemId, dropChance);
            this.nbt = nbt == null ? "" : nbt;
        }
        public EquipmentItem(String slot, String itemId, double dropChance, String nbt, double spawnChance) {
            this(slot, itemId, dropChance, nbt);
            this.spawnChance = spawnChance;
        }
        public EquipmentItem copy() {
            EquipmentItem c = new EquipmentItem(slot, itemId, dropChance);
            c.nbt = nbt;
            c.spawnChance = spawnChance;
            return c;
        }
    }

    public static double[] buildProbabilities(List<EquipmentBuild> builds) {
        double[] out = new double[builds.size()];
        double total = 0;
        int usable = 0, only = -1;
        for (int i = 0; i < builds.size(); i++) {
            EquipmentBuild b = builds.get(i);
            if (!b.enabled || !(b.weight > 0)) continue;
            total += b.weight;
            usable++;
            only = i;
        }
        if (usable == 0) return out;
        if (usable == 1 && builds.get(only).weight > 1) {
            out[only] = Math.min(100, builds.get(only).weight) / 100.0;
            return out;
        }
        double scale = total <= 1 ? 1 : total;
        for (int i = 0; i < builds.size(); i++) {
            EquipmentBuild b = builds.get(i);
            if (b.enabled && b.weight > 0) out[i] = b.weight / scale;
        }
        return out;
    }

    /** Lee un valor de probabilidad tipeado en la GUI: {@code <= 1} es fraccion (0.1 = 10%), {@code > 1} se toma como porcentaje (50 = 50%). Resultado siempre en 0..1; NaN si no es un numero. */
    public static double parseProbability(String raw) {
        double v;
        try { v = Double.parseDouble(raw.trim().replace(',', '.').replace("%", "")); } catch (Exception e) { return Double.NaN; }
        if (Double.isNaN(v)) return Double.NaN;
        if (v > 1) v = v / 100.0;
        return Math.max(0, Math.min(1, v));
    }

    public static class EquipmentBuild {
        public String name = "";
        /** Relative weight, not a 0..1 probability - e.g. two builds both at weight 1 are 50/50 regardless of their absolute value. Zero or negative excludes the build entirely. */
        public double weight = 1.0;
        public boolean enabled = true;
        public List<EquipmentItem> equipment = new ArrayList<>();

        public EquipmentBuild copy() {
            EquipmentBuild c = new EquipmentBuild();
            c.name = name; c.weight = weight; c.enabled = enabled;
            for (EquipmentItem eq : equipment) c.equipment.add(eq.copy());
            return c;
        }
    }

    public static class MobEntry {
        public String entityId;
        public boolean enabled  = true;
        public String  category = "";

        public double health          = Double.NaN;
        public double armor           = Double.NaN;
        public double armorToughness  = Double.NaN;
        public double attackDamage    = Double.NaN;
        public double attackSpeed     = Double.NaN;
        public double movementSpeed   = Double.NaN;
        public double knockbackResist = Double.NaN;
        public double followRange     = Double.NaN;

        public List<AttrMod> attributeMods = new ArrayList<>();

        public String  customName        = "";
        public boolean customNameSet     = false;
        public boolean customNameVisible = false;

        public List<EquipmentItem> equipment = new ArrayList<>();

        public List<EquipmentBuild> equipmentBuilds = new ArrayList<>();

        /**
         * Status effects this mob has a chance of applying to whatever it
         * hits in melee - reuses {@link KubeJSItemAttrExporter.FoodEffect}
         * (effectId/durationTicks/amplifier/chance) rather than a new type,
         * since the shape is identical to a food's "chance to also apply an
         * effect" list; see {@link #appendAttackEffectHandlers()} for how this
         * is exported.
         */
        public List<KubeJSItemAttrExporter.FoodEffect> attackEffects = new ArrayList<>();

        /**
         * Status effects the mob itself has a chance of spawning with -
         * same {@link KubeJSItemAttrExporter.FoodEffect} shape as
         * {@link #attackEffects} (effectId/durationTicks/amplifier/chance),
         * but applied to the mob, not to whatever it hits. Unlike
         * attackEffects this needs no separate event hook - it's applied
         * directly in the per-mob spawn body, see
         * {@link #writeMobEffects()}/{@link #appendGeneratedMobBody()}. Added
         * Alongside the MobEditorScreen "Efectos" tab rename
         * (was "Efectos de ataque", now covers both lists).
         */
        public List<KubeJSItemAttrExporter.FoodEffect> mobEffects = new ArrayList<>();

        /**
         * Entity ids this mob is configured to never fight (checked both
         * ways at enforcement time - see
         * {@code AllyRequirements#areAllies}), i.e. "no friendly fire"
         * between this mob and whatever's on the list. Exported via
         * {@code ShovelAPI.addAlly()} the same way {@link #attackEffects} is
         * exported via {@code ShovelAPI.addAttackEffect()} - see
         * {@link #appendAllyHandlers()}.
         */
        public List<String> allies = new ArrayList<>();

        /**
         * Entity ids this mob is configured to ALWAYS attack on sight
         * (checked both ways at enforcement time - see {@code
         * EnemyRequirements#areEnemies}), i.e. forced aggro between this
         * mob and whatever's on the list - the opposite of {@link
         * #allies}. Exported via {@code ShovelAPI.addEnemy()} the same way
         * {@link #allies} is exported via {@code ShovelAPI.addAlly()} - see
         * {@link #appendEnemyHandlers()}.
         */
        public List<String> enemies = new ArrayList<>();

        public Set<String> behaviorFlags = new LinkedHashSet<>();

        public String scriptOverride = "";

        /** XP que suelta al morir; -1 = la de vanilla/mod. */
        public int    xpDrop          = -1;
        /** Vida que regenera por segundo; NaN = sin regeneracion extra. */
        public double regenPerSecond  = Double.NaN;
        /** Multiplicador del daño que RECIBE (0.5 = la mitad); NaN = sin cambio. */
        public double damageTakenMult = Double.NaN;
        /** Multiplicador del daño que HACE, cuerpo a cuerpo y proyectiles propios; NaN = sin cambio. */
        public double damageDealtMult = Double.NaN;

        /** True si algun campo de combate esta seteado (para resumenes/preview). */
        public boolean hasCombatTweaks() {
            return xpDrop >= 0 || !Double.isNaN(regenPerSecond) || !Double.isNaN(damageTakenMult) || !Double.isNaN(damageDealtMult);
        }

        public MobEntry copy() {
            MobEntry c = new MobEntry();
            c.entityId = entityId; c.enabled = enabled; c.category = category;
            c.health = health; c.armor = armor; c.armorToughness = armorToughness;
            c.attackDamage = attackDamage; c.attackSpeed = attackSpeed;
            c.movementSpeed = movementSpeed; c.knockbackResist = knockbackResist;
            c.followRange = followRange;
            c.customName = customName; c.customNameSet = customNameSet; c.customNameVisible = customNameVisible;
            c.scriptOverride = scriptOverride;
            c.xpDrop = xpDrop; c.regenPerSecond = regenPerSecond;
            c.damageTakenMult = damageTakenMult; c.damageDealtMult = damageDealtMult;
            for (AttrMod m : attributeMods) c.attributeMods.add(m.copy());
            for (EquipmentItem eq : equipment) c.equipment.add(eq.copy());
            for (EquipmentBuild b : equipmentBuilds) c.equipmentBuilds.add(b.copy());
            for (KubeJSItemAttrExporter.FoodEffect fx : attackEffects) c.attackEffects.add(fx.copy());
            for (KubeJSItemAttrExporter.FoodEffect fx : mobEffects) c.mobEffects.add(fx.copy());
            c.allies.addAll(allies);
            c.enemies.addAll(enemies);
            c.behaviorFlags.addAll(behaviorFlags);
            return c;
        }
    }

    /**
     * Semantic validation for a mob entry: is {@code entityId} a real,
     * well-formed entity type (checked against the live registry when one
     * is available, see {@link SemanticValidator}), and are the item ids
     * used in its equipment/equipmentBuilds well-formed/existing too? An
     * entry that fails this never reaches {@code crafteditor_mobs.js} -
     * see {@link #writeScript()}.
     */
    public static List<String> validateMob(MobEntry e) {
        List<String> errors = new ArrayList<>();
        if (e == null) { errors.add("Mob data is null."); return errors; }
        if (e.entityId == null || e.entityId.isBlank()) {
            errors.add("Entity id vacio.");
        } else {
            errors.addAll(SemanticValidator.validateEntityId(e.entityId, "Entidad"));
        }
        for (EquipmentItem eq : e.equipment) {
            if (eq.itemId != null && !eq.itemId.isBlank())
                errors.addAll(SemanticValidator.validateItemId(eq.itemId, "Equipo"));
        }
        for (EquipmentBuild b : e.equipmentBuilds) {
            for (EquipmentItem eq : b.equipment) {
                if (eq.itemId != null && !eq.itemId.isBlank())
                    errors.addAll(SemanticValidator.validateItemId(eq.itemId, "Equipo (" + b.name + ")"));
            }
        }
        for (KubeJSItemAttrExporter.FoodEffect fx : e.attackEffects) {
            if (fx.effectId != null && !fx.effectId.isBlank())
                errors.addAll(SemanticValidator.validateEffectId(fx.effectId, "Efecto de ataque"));
        }
        for (KubeJSItemAttrExporter.FoodEffect fx : e.mobEffects) {
            if (fx.effectId != null && !fx.effectId.isBlank())
                errors.addAll(SemanticValidator.validateEffectId(fx.effectId, "Efecto de mob"));
        }
        for (String allyId : e.allies) {
            if (allyId != null && !allyId.isBlank())
                errors.addAll(SemanticValidator.validateEntityId(allyId, "Aliado"));
        }
        for (String enemyId : e.enemies) {
            if (enemyId != null && !enemyId.isBlank())
                errors.addAll(SemanticValidator.validateEntityId(enemyId, "Enemigo"));
        }
        errors.addAll(validateCombat(e));
        return errors;
    }

    /** Entries omitted from the last {@link #writeScript()} because {@link #validateMob()} failed, with the reason(s) why - see the class-level note on {@code TagExporter.LAST_WRITE_WARNINGS} for why this is "omit and warn" rather than ever mixing a bad entry's broken reference into the same handler map as good ones. */
    private static final List<String> LAST_WRITE_WARNINGS = new ArrayList<>();

    public static List<String> getLastWriteWarnings() { return Collections.unmodifiableList(LAST_WRITE_WARNINGS); }

    private static final Map<String, MobEntry> ENTRIES = new LinkedHashMap<>();

    /**
     * Lo ultimo aplicado (o cargado al arrancar): lo que se guarda en el estado y lo que leen
     * los enforcers. Los borradores pendientes viven solo en ENTRIES hasta Aplicar.
     */
    private static volatile Map<String, MobEntry> APPLIED = Map.of();

    public static Collection<MobEntry> getAppliedEntries() { return APPLIED.values(); }
    public static MobEntry getAppliedEntry(String id) { return APPLIED.get(id); }
    public static boolean hasAppliedEntries() { return !APPLIED.isEmpty(); }

    /** Toma la foto de ENTRIES como aplicada; se llama al escribir el script y al cargar el estado. */
    public static void markApplied() {
        Map<String, MobEntry> m = new LinkedHashMap<>();
        for (Map.Entry<String, MobEntry> e : ENTRIES.entrySet()) m.put(e.getKey(), e.getValue().copy());
        APPLIED = java.util.Collections.unmodifiableMap(m);
    }
    private static final Map<String, Deque<MobEntry>> HISTORY =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Deque<MobEntry>> eldest) {
                    return size() > Math.max(1, com.kve.common.config.KVEConfig.undoHistoryMaxKeys);
                }
            };

    /** Stages a mob into ENTRIES (in-memory only, no script/state write). */
    public static void stageMob(MobEntry e) {
        MobEntry previous = ENTRIES.get(e.entityId);
        if (previous != null) pushHistory(e.entityId, previous);
        ENTRIES.put(e.entityId, e);
    }

    /** Undoes a stageMob(): drops the staged entry and restores whatever was there before (null if it was new). */
    public static void discardStagedMob(String id, MobEntry previousOrNull) {
        if (previousOrNull != null) ENTRIES.put(id, previousOrNull);
        else ENTRIES.remove(id);
    }

    /** Stages a mob deletion (in-memory only). */
    public static void stageDeleteMob(String id) {
        ENTRIES.remove(id);
        HISTORY.remove(id);
    }

    /** Stages an undo (in-memory only, no script/state write) - see stageMob(). Returns the restored entry, or null if there's nothing to undo. */
    public static MobEntry stageUndo(String id) {
        Deque<MobEntry> h = HISTORY.get(id);
        if (h == null || h.isEmpty()) return null;
        MobEntry restored = h.removeFirst();
        ENTRIES.put(id, restored);
        return restored;
    }

    /** Undoes a stageUndo(): pushes the restored entry back onto the undo history and restores whatever ENTRIES had before the undo (null if it was new). */
    public static void discardStagedUndo(String id, MobEntry restored, MobEntry beforeUndoOrNull) {
        Deque<MobEntry> h = HISTORY.computeIfAbsent(id, k -> new ArrayDeque<>());
        h.addFirst(restored);
        if (beforeUndoOrNull != null) ENTRIES.put(id, beforeUndoOrNull);
        else ENTRIES.remove(id);
    }

    /** Stages an enabled/disabled toggle (in-memory only, no script/state write) - see stageMob(). Returns false if there's no such entry to toggle. */
    public static boolean stageSetEnabled(String id, boolean enabled) {
        MobEntry e = ENTRIES.get(id);
        if (e == null) return false;
        pushHistory(id, e);
        e.enabled = enabled;
        return true;
    }

    /** Undoes a stageSetEnabled(): restores the previous enabled value (mirrors discardStagedMob()'s "put the old state back" contract). */
    public static void discardStagedSetEnabled(String id, boolean previousEnabled) {
        MobEntry e = ENTRIES.get(id);
        if (e != null) e.enabled = previousEnabled;
    }

    /** Writes the currently staged ENTRIES to disk (KubeJS script + KVE state). Only called from a PendingChange's onApply. */
    public static void commitMobs() {
        writeScript();
        CraftEditorState.save();
    }

    private static void pushHistory(String id, MobEntry snapshot) {
        int max = com.kve.common.config.KVEConfig.undoHistorySize;
        if (max <= 0) return;
        Deque<MobEntry> h = HISTORY.computeIfAbsent(id, k -> new ArrayDeque<>());
        h.addFirst(snapshot.copy());
        while (h.size() > max) h.removeLast();
    }

    public static boolean canUndo(String id) {
        Deque<MobEntry> h = HISTORY.get(id);
        return h != null && !h.isEmpty();
    }

    public static MobEntry undo(String id) {
        Deque<MobEntry> h = HISTORY.get(id);
        if (h == null || h.isEmpty()) return null;
        MobEntry previous = h.removeFirst();
        ENTRIES.put(id, previous);
        writeScript();
        CraftEditorState.save();
        return previous;
    }

    public static void setEnabled(String id, boolean enabled) {
        MobEntry e = ENTRIES.get(id);
        if (e == null) return;
        pushHistory(id, e);
        e.enabled = enabled;
        writeScript();
        CraftEditorState.save();
    }

    public static Collection<MobEntry> getEntries()    { return Collections.unmodifiableCollection(ENTRIES.values()); }
    public static MobEntry             getEntry(String id) { return ENTRIES.get(id); }

    /** Whether ANY mob entry exists at all - lets {@code MobAttributeEnforcer}'s periodic reinforcement tick short-circuit instantly on worlds/sessions that never configured a single mob, same pattern as {@code MobBehaviorTypeFlags#anyRegistered()}. */
    public static boolean hasEntries() { return !ENTRIES.isEmpty(); }

    public static void clearAll() { ENTRIES.clear(); APPLIED = Map.of(); HISTORY.clear(); }

    public static void loadEntry(MobEntry e) { ENTRIES.put(e.entityId, e); }

    private static Path dir() { return KveScriptPaths.mobsDir(); }

    private static void writeScript() {
        markApplied();
        try { Files.createDirectories(dir()); } catch (IOException ex) { return; }

        StringBuilder sb = new StringBuilder();
        sb.append("// Shovel JS - Mob attack effects / aliados / enemigos / behavior extendido (AUTO-GENERATED)\n");
        sb.append("// Atributos, nombre, equipo y flags basicas de mob YA NO pasan por aca -\n");
        sb.append("// se aplican directo en Java (com.kve.common.mobrules.MobAttributeEnforcer).\n");
        sb.append("// Apply with: /reload\n\n");

        LAST_WRITE_WARNINGS.clear();
        List<MobEntry> validEntries = new ArrayList<>();
        for (MobEntry e : ENTRIES.values()) {
            if (!e.enabled) continue;
            List<String> semantic = validateMob(e);
            if (!semantic.isEmpty()) {
                String reason = String.join("; ", semantic);
                String warning = "Mob '" + e.entityId + "' omitido: " + reason;
                LAST_WRITE_WARNINGS.add(warning);
                CraftEditorMod.LOGGER.warn("[Shovel JS] {}", warning);
                continue;
            }
            validEntries.add(e);
        }

        appendAttackEffectHandlers(sb, validEntries);
        appendAllyHandlers(sb, validEntries);
        appendEnemyHandlers(sb, validEntries);
        appendExtendedBehaviorHandlers(sb, validEntries);

        if (ScriptWriteGuard.writeIfValid(KveScriptPaths.mobsFile(), sb.toString(), "shovel_js_mobs")) {
            CraftEditorMod.LOGGER.info("[CraftEditor] Wrote: {}", KveScriptPaths.mobsFile().toAbsolutePath());
        }

        deleteLegacyMobScript();
    }

    private static void deleteLegacyMobScript() {
        Path legacy = KveScriptPaths.SERVER_ROOT.resolve("shovel_js_mobs.js");
        try {
            boolean deleted = Files.deleteIfExists(legacy);
            if (deleted) {
                CraftEditorMod.LOGGER.info("[Shovel JS] Legacy mob script eliminado: {}", legacy.toAbsolutePath());
            }
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] No se pudo eliminar el legacy mob script {} - si sigue existiendo, KubeJS va a tirar 'redeclaration of const $MOB_HANDLERS' al cargar scripts: {}",
                    legacy.toAbsolutePath(), ex.getMessage());
        }
    }

    /**
     * Public boot-time entry point for {@link com.kve.CraftEditorMod#commonSetup} -
     * same cleanup as {@link #deleteLegacyMobScript()}, just reachable
     * before any Mob Editor Save has happened this session (see call site
     * for why that matters).
     */
    public static void deleteLegacyMobScriptOnBoot() {
        deleteLegacyMobScript();
    }

    private static void appendIndented(StringBuilder sb, String raw, String indent) {
        String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');
        for (String line : normalized.split("\n", -1)) {
            if (line.isEmpty()) { sb.append("\n"); continue; }
            sb.append(indent).append(line).append("\n");
        }
    }

    public static String previewGeneratedMobBody(MobEntry e) {
        if (e == null) return "";
        StringBuilder sb = new StringBuilder();
        try {
            appendGeneratedMobBody(sb, e, "");
        } catch (Exception ex) {
            return "// ERROR generando preview: " + ex.getMessage();
        }
        return sb.toString();
    }

    public static String previewMobJS(MobEntry e) {
        if (e == null) return "// (sin datos de mob)";
        try {
            StringBuilder sb = new StringBuilder();
            if (mobHasAnyEquipment(e)) sb.append("// $EquipmentSlot ya esta declarado una vez arriba de todo en el archivo real\n");
            sb.append("'").append(e.entityId).append("': (mob, event) => {\n");
            appendMobBody(sb, e, "  ");
            sb.append("}\n");
            if (mobHasAnyAttackEffects(e)) {
                sb.append("\n// + attack effects (vía ShovelAPI.addAttackEffect):\n");
                appendAttackEffectHandlers(sb, Collections.singletonList(e));
            }
            if (mobHasAnyAllies(e)) {
                sb.append("\n// + aliados / no friendly fire (vía ShovelAPI.addAlly):\n");
                appendAllyHandlers(sb, Collections.singletonList(e));
            }
            if (mobHasAnyEnemies(e)) {
                sb.append("\n// + enemigos / forced aggro (vía ShovelAPI.addEnemy):\n");
                appendEnemyHandlers(sb, Collections.singletonList(e));
            }
            if (mobHasAnyExtendedBehaviorFlags(e)) {
                sb.append("\n// + behavior extendido (vía ShovelAPI.setMobBehaviorFlags):\n");
                appendExtendedBehaviorHandlers(sb, Collections.singletonList(e));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "// ERROR generando preview: " + ex.getMessage();
        }
    }

    static void appendMobBody(StringBuilder sb, MobEntry e, String indent) {
        if (e.scriptOverride != null && !e.scriptOverride.isBlank()) {
            sb.append(indent).append("// [Shovel JS] override manual activo para '").append(e.entityId)
              .append("' - cuerpo auto-generado (atributos/equipo/nombre/flags) IGNORADO.")
              .append(" GUI: Vista previa -> Restaurar generado -> Guardar para volver a auto-generar.\n");
            appendIndented(sb, e.scriptOverride, indent);
            return;
        }
        appendGeneratedMobBody(sb, e, indent);
    }

    private static void appendGeneratedMobBody(StringBuilder sb, MobEntry e, String indent) {
        boolean wroteAnyHealthAttr = false;

        wroteAnyHealthAttr |= writeLegacyAttr(sb, indent, "minecraft:generic.max_health",       e.health);
        writeLegacyAttr(sb, indent, "minecraft:generic.armor",            e.armor);
        writeLegacyAttr(sb, indent, "minecraft:generic.armor_toughness",  e.armorToughness);
        writeLegacyAttr(sb, indent, "minecraft:generic.attack_damage",    e.attackDamage);
        writeLegacyAttr(sb, indent, "minecraft:generic.attack_speed",     e.attackSpeed);
        writeLegacyAttr(sb, indent, "minecraft:generic.movement_speed",   e.movementSpeed);
        writeLegacyAttr(sb, indent, "minecraft:generic.knockback_resistance", e.knockbackResist);
        writeLegacyAttr(sb, indent, "minecraft:generic.follow_range",     e.followRange);

        for (AttrMod m : e.attributeMods) {
            if (m.attrId == null || m.attrId.isEmpty()) continue;

            if (LEGACY_ATTR_IDS.contains(m.attrId)) {
                String warning = "Mob '" + e.entityId + "': el atributo personalizado '" + m.attrId
                        + "' duplica un campo dedicado (Vida/Armadura/etc) - se ignoro para no pisar ese valor."
                        + " Borra esta entrada de Atributos Personalizados si no la necesitas.";
                LAST_WRITE_WARNINGS.add(warning);
                CraftEditorMod.LOGGER.warn("[Shovel JS] {}", warning);
                sb.append(indent).append("// OMITIDO (duplica campo dedicado): mob.setAttributeBaseValue('")
                  .append(m.attrId).append("', ").append(KveNumbers.padded4(m.value)).append(")\n");
                continue;
            }

            boolean hasProb = m.probability < 1.0;
            boolean hasDim  = m.dimension != null && !m.dimension.isEmpty();

            String pad = indent;
            if (hasProb) {
                sb.append(pad).append("if (Math.random() < ").append(KveNumbers.padded4(m.probability)).append(") {\n");
                pad += "  ";
            }
            if (hasDim) {
                sb.append(pad).append("if (event.level.dimension == '").append(m.dimension).append("') {\n");
                pad += "  ";
            }
            sb.append(pad).append("mob.setAttributeBaseValue('").append(m.attrId).append("', ")
              .append(KveNumbers.padded4(m.value)).append(")\n");
            if (hasDim) {
                pad = pad.substring(0, pad.length() - 2);
                sb.append(pad).append("}\n");
            }
            if (hasProb) {
                pad = pad.substring(0, pad.length() - 2);
                sb.append(pad).append("}\n");
            }
        }

        if (wroteAnyHealthAttr) sb.append(indent).append("mob.health = mob.maxHealth\n");

        if (e.customNameSet) {
            if (e.customName != null && !e.customName.isEmpty()) {
                String jsLiteral = JsStrings.escape((e.customName).replace('&', '\u00A7'));
                sb.append(indent).append("mob.setCustomName(Text.of('").append(jsLiteral).append("'))\n");
                sb.append(indent).append("mob.setCustomNameVisible(").append(e.customNameVisible).append(")\n");
            } else if (e.customNameVisible) {
                sb.append(indent).append("mob.setCustomNameVisible(true)\n");
            } else {
                sb.append(indent).append("mob.setCustomName(null)\n");
                sb.append(indent).append("mob.setCustomNameVisible(false)\n");
            }
        }

        writeEquipment(sb, e, indent);
        writeBehaviorFlags(sb, e, indent);
        writeMobEffects(sb, e, indent);
    }

    private static void writeMobEffects(StringBuilder sb, MobEntry e, String indent) {
        for (KubeJSItemAttrExporter.FoodEffect fx : e.mobEffects) {
            if (fx.effectId == null || fx.effectId.isEmpty()) continue;
            boolean hasChance = fx.chance < 1.0f;
            String pad = indent;
            if (hasChance) {
                sb.append(pad).append("if (Math.random() < ").append(KveNumbers.padded4(fx.chance)).append(") {\n");
                pad += "  ";
            }
            sb.append(pad).append("mob.potionEffects.add('").append(fx.effectId).append("', ")
              .append(Math.max(0, fx.durationTicks)).append(", ").append(Math.max(0, fx.amplifier)).append(")\n");
            if (hasChance) {
                sb.append(indent).append("}\n");
            }
        }
    }

    private static void writeBehaviorFlags(StringBuilder sb, MobEntry e, String indent) {
        if (e.behaviorFlags.isEmpty()) return;
        if (e.behaviorFlags.contains("NO_AI")) {
            writeFlagCall(sb, indent, "mob.setNoAi(true)");
        }
        if (e.behaviorFlags.contains("FLIES")) {
            writeFlagCall(sb, indent, "mob.setNoGravity(true)");
        }
        if (e.behaviorFlags.contains("CAN_PICKUP_LOOT")) {
            writeFlagCall(sb, indent, "mob.setCanPickUpLoot(true)");
        }
        if (e.behaviorFlags.contains("BABY")) {
            writeFlagCall(sb, indent, "mob.setBaby(true)");
        }
        if (e.behaviorFlags.contains("PERSISTENT")) {
            writeFlagCall(sb, indent, "mob.setPersistenceRequired()");
        }
        if (e.behaviorFlags.contains("SILENT")) {
            writeFlagCall(sb, indent, "mob.setSilent(true)");
        }
        if (e.behaviorFlags.contains("INVULNERABLE")) {
            writeFlagCall(sb, indent, "mob.setInvulnerable(true)");
        }
        if (e.behaviorFlags.contains("INVISIBLE")) {
            writeFlagCall(sb, indent, "mob.setInvisible(true)");
        }
        if (e.behaviorFlags.contains("GLOWING")) {
            writeFlagCall(sb, indent, "mob.setGlowingTag(true)");
        }
        if (e.behaviorFlags.contains("LEFT_HANDED")) {
            writeFlagCall(sb, indent, "mob.setLeftHanded(true)");
        }
        // NAME_VISIBLE queda afuera a proposito (ver javadoc de
        // BEHAVIOR_FLAGS - duplicaria customNameVisible); si un MobEntry
        // viejo todavia trae ese string en behaviorFlags, se ignora en
        // silencio en vez de fallar, mismo criterio "dato viejo inofensivo"
        // que el resto del exporter.
    }

    private static void writeFlagCall(StringBuilder sb, String indent, String call) {
        sb.append(indent).append("try { ").append(call).append(" } catch (ex) { console.error('[Shovel JS] behavior flag failed: ").append(call).append(" -> ' + ex) }\n");
    }

    private static boolean mobHasAnyEquipment(MobEntry e) {
        if (!e.equipmentBuilds.isEmpty()) {
            for (EquipmentBuild b : e.equipmentBuilds) {
                if (!b.enabled || b.weight <= 0) continue;
                for (EquipmentItem eq : b.equipment) if (eq.itemId != null && !eq.itemId.isEmpty()) return true;
            }
            return false;
        }
        for (EquipmentItem eq : e.equipment) if (eq.itemId != null && !eq.itemId.isEmpty()) return true;
        return false;
    }

    private static boolean mobHasAnyAttackEffects(MobEntry e) {
        for (KubeJSItemAttrExporter.FoodEffect fx : e.attackEffects)
            if (fx.effectId != null && !fx.effectId.isEmpty()) return true;
        return false;
    }

    private static boolean mobHasAnyAllies(MobEntry e) {
        for (String allyId : e.allies)
            if (allyId != null && !allyId.isEmpty()) return true;
        return false;
    }

    private static boolean mobHasAnyEnemies(MobEntry e) {
        for (String enemyId : e.enemies)
            if (enemyId != null && !enemyId.isEmpty()) return true;
        return false;
    }

    private static void appendAttackEffectHandlers(StringBuilder sb, List<MobEntry> validEntries) {
        boolean any = false;
        for (MobEntry e : validEntries) {
            if (mobHasAnyAttackEffects(e)) { any = true; break; }
        }
        if (!any) return;

        sb.append("// Mob attack effects -> ShovelAPI.addAttackEffect (real LivingHurtEvent hook, no JS event dispatch)\n");
        sb.append("try {\n");
        sb.append("  ShovelAPI.clearAllAttackEffects()\n");
        int count = 0;
        for (MobEntry e : validEntries) {
            if (!mobHasAnyAttackEffects(e)) continue;
            for (KubeJSItemAttrExporter.FoodEffect fx : e.attackEffects) {
                if (fx.effectId == null || fx.effectId.isEmpty()) continue;
                sb.append("  ShovelAPI.addAttackEffect('").append(e.entityId).append("', '").append(fx.effectId)
                  .append("', ").append(fx.durationTicks).append(", ").append(fx.amplifier).append(", ")
                  .append(KveNumbers.padded4(fx.chance)).append(")\n");
                count++;
            }
        }
        sb.append("  console.info('[Shovel JS] attack effects: ").append(count)
          .append(" regla(s) registrada(s) vía ShovelAPI para ")
          .append(validEntries.stream().filter(KubeJSMobExporter::mobHasAnyAttackEffects).count())
          .append(" mob(s)')\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] mob attack effects (ShovelAPI.addAttackEffect) no se pudo registrar: ' + ex) }\n");
    }

    /**
     * Applies {@link MobEntry#allies} (the "Aliados" tab - no friendly fire
     * between the listed entity types) by emitting calls to {@code
     * ShovelAPI.addAlly(...)}, enforced by a real Forge {@code
     * LivingAttackEvent} hook ({@code com.kve.common.mobrules.AllyEnforcer})
     * - same "generate a call into a real Java-backed API instead of
     * hand-rolled event JS" pattern as {@link #appendAttackEffectHandlers()},
     * for the same reason: resolving both combatants' entity types
     * reliably needs real Forge API, not a JS-side {@code event.source}/
     * {@code event.entity} guess. Skips emitting anything when nothing uses
     * this feature, so mobs without allies pay zero extra cost.
     */
    private static void appendAllyHandlers(StringBuilder sb, List<MobEntry> validEntries) {
        boolean any = false;
        for (MobEntry e : validEntries) {
            if (mobHasAnyAllies(e)) { any = true; break; }
        }
        if (!any) return;

        sb.append("// Mob allies (no friendly fire) -> ShovelAPI.addAlly (real LivingAttackEvent hook, no JS event dispatch)\n");
        sb.append("try {\n");
        sb.append("  ShovelAPI.clearAllAllies()\n");
        int count = 0;
        for (MobEntry e : validEntries) {
            if (!mobHasAnyAllies(e)) continue;
            for (String allyId : e.allies) {
                if (allyId == null || allyId.isEmpty()) continue;
                sb.append("  ShovelAPI.addAlly('").append(e.entityId).append("', '").append(allyId).append("')\n");
                count++;
            }
        }
        sb.append("  console.info('[Shovel JS] aliados: ").append(count)
          .append(" relacion(es) registrada(s) vía ShovelAPI para ")
          .append(validEntries.stream().filter(KubeJSMobExporter::mobHasAnyAllies).count())
          .append(" mob(s)')\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] mob allies (ShovelAPI.addAlly) no se pudo registrar: ' + ex) }\n");
    }

    /**
     * Applies {@link MobEntry#enemies} (the "Enemigos" tab - forced aggro
     * against the listed entity types) by emitting calls to {@code
     * ShovelAPI.addEnemy(...)}, enforced by a real Forge {@code
     * LivingEvent.LivingTickEvent} scan ({@code
     * com.kve.common.mobrules.EnemyEnforcer}, see its class doc for why
     * this needs a tick scan rather than a reactive event hook like
     * {@link #appendAllyHandlers()} uses) - same "generate a call into a
     * real Java-backed API instead of hand-rolled event JS" pattern as
     * {@link #appendAllyHandlers()}/{@link #appendAttackEffectHandlers()}.
     * Skips emitting anything when nothing uses this feature, so mobs
     * without enemies pay zero extra cost.
     */
    private static void appendEnemyHandlers(StringBuilder sb, List<MobEntry> validEntries) {
        boolean any = false;
        for (MobEntry e : validEntries) {
            if (mobHasAnyEnemies(e)) { any = true; break; }
        }
        if (!any) return;

        sb.append("// Mob enemies (forced aggro) -> ShovelAPI.addEnemy (real LivingTickEvent scan, no JS event dispatch)\n");
        sb.append("try {\n");
        sb.append("  ShovelAPI.clearAllEnemies()\n");
        int count = 0;
        for (MobEntry e : validEntries) {
            if (!mobHasAnyEnemies(e)) continue;
            for (String enemyId : e.enemies) {
                if (enemyId == null || enemyId.isEmpty()) continue;
                sb.append("  ShovelAPI.addEnemy('").append(e.entityId).append("', '").append(enemyId).append("')\n");
                count++;
            }
        }
        sb.append("  console.info('[Shovel JS] enemigos: ").append(count)
          .append(" relacion(es) registrada(s) vía ShovelAPI para ")
          .append(validEntries.stream().filter(KubeJSMobExporter::mobHasAnyEnemies).count())
          .append(" mob(s)')\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] mob enemies (ShovelAPI.addEnemy) no se pudo registrar: ' + ex) }\n");
    }

    private static boolean mobHasAnyExtendedBehaviorFlags(MobEntry e) {
        for (String flag : e.behaviorFlags) {
            if (EXTENDED_BEHAVIOR_FLAGS.contains(flag)) return true;
        }
        return false;
    }

    private static void appendExtendedBehaviorHandlers(StringBuilder sb, List<MobEntry> validEntries) {
        boolean any = false;
        for (MobEntry e : validEntries) {
            if (mobHasAnyExtendedBehaviorFlags(e)) { any = true; break; }
        }
        if (!any) return;

        sb.append("// Mob behavior extendido (grief/hostil-neutral-pasivo/huir/ataque a distancia) -> ShovelAPI.setMobBehaviorFlags (real Forge hooks, no JS event dispatch)\n");
        sb.append("try {\n");
        sb.append("  ShovelAPI.clearAllMobBehaviorFlags()\n");
        int count = 0;
        for (MobEntry e : validEntries) {
            if (!mobHasAnyExtendedBehaviorFlags(e)) continue;
            sb.append("  ShovelAPI.setMobBehaviorFlags('").append(e.entityId).append("', [");
            boolean first = true;
            for (String flag : e.behaviorFlags) {
                if (!EXTENDED_BEHAVIOR_FLAGS.contains(flag)) continue;
                if (!first) sb.append(", ");
                sb.append("'").append(flag).append("'");
                first = false;
            }
            sb.append("])\n");
            count++;
        }
        sb.append("  console.info('[Shovel JS] behavior extendido: ").append(count)
          .append(" mob(s) con flags registrado(s) vía ShovelAPI')\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] mob behavior extendido (ShovelAPI.setMobBehaviorFlags) no se pudo registrar: ' + ex) }\n");
    }

    private static void writeEquipment(StringBuilder sb, MobEntry e, String indent) {
        if (!e.equipmentBuilds.isEmpty()) {
            writeEquipmentBuilds(sb, e, indent);
            return;
        }

        List<EquipmentItem> items = new ArrayList<>();
        for (EquipmentItem eq : e.equipment) if (eq.itemId != null && !eq.itemId.isEmpty()) items.add(eq);
        if (items.isEmpty()) return;

        sb.append(indent).append("try {\n");
        appendBuildItems(sb, items, indent + "  ");
        sb.append(indent).append("} catch (ex) { console.error('[Shovel JS] equipo fallo para ' + mob.type + ': ' + ex) }\n");
    }

    private static void writeEquipmentBuilds(StringBuilder sb, MobEntry e, String indent) {
        double[] probs = buildProbabilities(e.equipmentBuilds);
        List<EquipmentBuild> builds = new ArrayList<>();
        List<Double> p = new ArrayList<>();
        double sum = 0;
        for (int i = 0; i < probs.length; i++) {
            if (probs[i] <= 0) continue;
            builds.add(e.equipmentBuilds.get(i));
            p.add(probs[i]);
            sum += probs[i];
        }
        if (builds.isEmpty()) return;
        // Suma >= 1 (redondeo incluido): el ultimo build es el "else"
        // incondicional; si no, lo que sobra es "spawnea sin equipo".
        boolean exhaustive = sum >= 0.99999;

        sb.append(indent).append("try {\n");
        if (builds.size() == 1 && exhaustive) {
            appendBuildItems(sb, builds.get(0).equipment, indent + "  ");
        } else if (builds.size() == 1) {
            sb.append(indent).append("  if (Math.random() < ").append(KveNumbers.padded4(p.get(0))).append(") {\n");
            appendBuildItems(sb, builds.get(0).equipment, indent + "    ");
            sb.append(indent).append("  }\n");
        } else {
            sb.append(indent).append("  var $roll = Math.random()\n");
            double cumulative = 0;
            for (int i = 0; i < builds.size(); i++) {
                EquipmentBuild b = builds.get(i);
                boolean last = i == builds.size() - 1;
                cumulative += p.get(i);
                if (last && exhaustive) {
                    sb.append(indent).append("  } else {\n");
                } else {
                    sb.append(indent).append("  ").append(i == 0 ? "if" : "} else if")
                      .append(" ($roll < ").append(KveNumbers.padded4(cumulative)).append(") {\n");
                }
                if (b.name != null && !b.name.isBlank()) sb.append(indent).append("    // ").append(b.name).append("\n");
                appendBuildItems(sb, b.equipment, indent + "    ");
            }
            sb.append(indent).append("  }\n");
        }
        sb.append(indent).append("} catch (ex) { console.error('[Shovel JS] equipo (build) fallo para ' + mob.type + ': ' + ex) }\n");
    }

    private static void appendBuildItems(StringBuilder sb, List<EquipmentItem> equipment, String innerIndent) {
        for (EquipmentItem eq : equipment) {
            if (eq.itemId == null || eq.itemId.isEmpty()) continue;
            if (eq.spawnChance <= 0) continue;
            String ind = innerIndent;
            boolean rolled = eq.spawnChance < 1.0;
            if (rolled) {
                sb.append(innerIndent).append("if (Math.random() < ").append(KveNumbers.padded4(eq.spawnChance)).append(") {\n");
                ind = innerIndent + "  ";
            }
            sb.append(ind).append("mob.setEquipment($EquipmentSlot.").append(eq.slot).append(", ")
              .append(equipmentItemJs(eq)).append(")\n");
            sb.append(ind).append("try { mob.setDropChance($EquipmentSlot.").append(eq.slot).append(", ")
              .append(KveNumbers.padded4(eq.dropChance)).append(") } catch (ex2) { console.error('[Shovel JS] dropChance fallo para ' + mob.type + ' ").append(eq.slot).append(": ' + ex2) }\n");
            if (rolled) sb.append(innerIndent).append("}\n");
        }
    }

    private static String equipmentItemJs(EquipmentItem eq) {
        if (eq.nbt == null || eq.nbt.isBlank()) return "Item.of('" + eq.itemId + "')";
        String nbt = JsStrings.escape(eq.nbt);
        return "Item.of('" + eq.itemId + "').withNBT('" + nbt + "')";
    }

    private static boolean writeLegacyAttr(StringBuilder sb, String indent, String attr, double v) {
        if (Double.isNaN(v)) return false;
        sb.append(indent).append("mob.setAttributeBaseValue('").append(attr).append("', ")
          .append(KveNumbers.padded4(v)).append(")\n");
        return true;
    }
}
