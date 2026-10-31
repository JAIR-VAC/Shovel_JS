package com.kve.common.event;

import com.kve.common.kubejs.SemanticValidator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class EventEntry {

    public enum TriggerType {
        FIRST_JOIN, PLAYER_LOGIN, INTERVAL, PLAYER_KILL, COMMAND, ENTER_BIOME,
        PLAYER_DEATH, PLAYER_RESPAWN, PLAYER_HURT, DIMENSION_CHANGE,
        BLOCK_BREAK, BLOCK_PLACE, ITEM_PICKUP, ITEM_CRAFT,
        XP_LEVEL_REACH, XP_CHANGED, ADVANCEMENT_EARNED, EFFECT_GAINED,
        ITEM_USE, ITEM_CONSUME, ITEM_SMELT, FISHING_CATCH, VILLAGER_TRADE,
        ENTITY_KILL_ANY, ENTITY_INTERACT, TAMING, BREEDING, MOUNT,
        PLAYER_DISCONNECT, SERVER_STARTUP, EMPTY_BUCKET, FILL_BUCKET,
        ENTITY_SPAWN, ENTITY_DEATH, ENTITY_ATTACKS
    }

    public static class Trigger {
        public TriggerType type = TriggerType.FIRST_JOIN;

        public int intervalSeconds = 60;

        /** PLAYER_KILL only - entity registry id, e.g. "minecraft:zombie". */
        public String entityId = "";
        /** PLAYER_KILL, BLOCK_BREAK, BLOCK_PLACE, ITEM_PICKUP, ITEM_CRAFT - cantidad requerida. */
        public int count = 1;

        /** COMMAND only - name used as {@code /kve event <commandName>}. */
        public String commandName = "";

        /** ENTER_BIOME only - biome id or "#tag". */
        public String biomeId = "";

        public String deathMessageContains = "";

        public float minDamage = 1.0f;

        public String fromDimension = "";
        public String toDimension = "";

        /** BLOCK_BREAK / BLOCK_PLACE only - block id o "#tag". */
        public String blockId = "";

        public String itemId = "";

        /** XP_LEVEL_REACH only - "level (int)". */
        public int xpLevel = 1;
        /** XP_CHANGED only - "minDelta (int, opcional; default cualquier subida)". */
        public int xpMinDelta = 1;
        /** ADVANCEMENT_EARNED only - "advancementId (namespaced)". */
        public String advancementId = "";
        /** EFFECT_GAINED only - "effectId (potion effect)". */
        public String effectId = "";

        /**
         * ENTITY_INTERACT / TAMING / BREEDING / MOUNT -
         * "entityTypeId". Requerido en ENTITY_INTERACT, opcional (vacío = cualquier
         * entidad) en TAMING/BREEDING/MOUNT, mismo patron que itemId opcional en
         * FISHING_CATCH/VILLAGER_TRADE.
         */
        public String entityTypeId = "";

        /** EMPTY_BUCKET / FILL_BUCKET only, opcional - "fluidId optional". */
        public String fluidId = "";

        public Trigger copy() {
            Trigger c = new Trigger();
            c.type = type;
            c.intervalSeconds = intervalSeconds;
            c.entityId = entityId;
            c.count = count;
            c.commandName = commandName;
            c.biomeId = biomeId;
            c.deathMessageContains = deathMessageContains;
            c.minDamage = minDamage;
            c.fromDimension = fromDimension;
            c.toDimension = toDimension;
            c.blockId = blockId;
            c.itemId = itemId;
            c.xpLevel = xpLevel;
            c.xpMinDelta = xpMinDelta;
            c.advancementId = advancementId;
            c.effectId = effectId;
            c.entityTypeId = entityTypeId;
            c.fluidId = fluidId;
            return c;
        }
    }

    public enum ConditionType {
        DIMENSION, PERMISSION, GAMEMODE, TIME_OF_DAY,
        Y_LEVEL_MIN, Y_LEVEL_MAX, BIOME, XP_LEVEL_MIN, HAS_ITEM, RANDOM_CHANCE,
        HEALTH_MAX, HEALTH_MIN, FOOD_LEVEL_MIN, HAS_EFFECT, HAS_ITEM_EQUIPPED,
        ADVANCEMENT_DONE, ADVANCEMENT_MISSING,
        WEATHER, MOON_PHASE, DAY_OF_WEEK, DATE_BETWEEN, HOUR_BETWEEN,
        HAS_TAG_STAGE, NOT_HAS_FLAG, PLAYTIME_MIN_SECONDS,
        // paralela a HAS_ITEM_EQUIPPED, pero sobre la entidad que disparo el
        // trigger (ENTITY_SPAWN/ENTITY_DEATH/ENTITY_ATTACKS) en vez del jugador.
        // Sin campo "chance" propio a proposito - "reusa el mismo campo de
        // RANDOM_CHANCE en vez de duplicar semantica" (agregar una condicion
        // RANDOM_CHANCE aparte en la misma lista AND).
        ENTITY_HAS_ITEM_EQUIPPED
    }

    public enum GameMode { SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR }

    public enum TimeOfDay { DAY, NIGHT }

    /** HAS_ITEM_EQUIPPED only. */
    public enum EquipSlot { MAINHAND, OFFHAND, HEAD, CHEST, LEGS, FEET }

    /** WEATHER condition y SET_WEATHER action comparten este enum - mismo "CLEAR/RAIN/THUNDER" en ambos. */
    public enum WeatherState { CLEAR, RAIN, THUNDER }

    /** DAY_OF_WEEK only - "MON...SUN (reloj del servidor)". */
    public enum WeekDay { MON, TUE, WED, THU, FRI, SAT, SUN }

    public static class Condition {
        public ConditionType type = ConditionType.DIMENSION;

        /** DIMENSION only - e.g. "minecraft:the_nether". */
        public String dimensionId = "";

        /** PERMISSION only - permission node string; "si no hay sistema, skip o op". */
        public String permissionNode = "";

        /** GAMEMODE only. */
        public GameMode gamemode = GameMode.SURVIVAL;

        /** TIME_OF_DAY only. */
        public TimeOfDay timeOfDay = TimeOfDay.DAY;

        public int yMin = 0;
        public int yMax = 320;

        public String biomeId = "";

        public int xpLevelMin = 0;

        public String hasItemId = "";
        public int hasItemMinCount = 1;

        public double randomChance = 1.0;

        /** HEALTH_MAX only - "vida actual <=". */
        public float healthMax = 20.0f;
        /** HEALTH_MIN only. */
        public float healthMin = 0.0f;
        /** FOOD_LEVEL_MIN only. */
        public int foodLevelMin = 0;
        /** HAS_EFFECT only - "effectId". */
        public String hasEffectId = "";
        /** HAS_ITEM_EQUIPPED only - "itemId, slot". */
        public String equippedItemId = "";
        public EquipSlot equippedSlot = EquipSlot.MAINHAND;
        /** ADVANCEMENT_DONE / ADVANCEMENT_MISSING only - "advancementId". */
        public String advancementId = "";

        /** WEATHER only - "CLEAR/RAIN/THUNDER". */
        public WeatherState weatherState = WeatherState.CLEAR;
        public String moonPhase = "0";
        /** DAY_OF_WEEK only - "reloj del servidor". */
        public WeekDay dayOfWeek = WeekDay.MON;
        /** DATE_BETWEEN only - "startMd, endMd (temporada)", formato "MM-DD". */
        public String dateStartMd = "";
        public String dateEndMd = "";
        /** HOUR_BETWEEN only - "startHour, endHour (0-23)". */
        public int hourStart = 0;
        public int hourEnd = 23;

        /**
         * HAS_TAG_STAGE only - "stageId". Mismo storage
         * dual GameStages/persistentData que {@code kve_hasFlag} ya usa para
         * oncePerPlayer (ver EventExporter#appendConditionGuard) - no requiere
         * GameStages instalado.
         */
        public String stageId = "";
        /**
         * NOT_HAS_FLAG only - "flagId" arbitrario, no
         * necesariamente uno de los flags automaticos oncePerPlayer
         * ("kve_flag_" + safe(id)) - el jugador puede apuntar a cualquier
         * nombre de flag/stage propio (p.ej. seteado por otro evento via
         * ADD_STAGE, o por otro script).
         */
        public String flagId = "";
        /**
         * PLAYTIME_MIN_SECONDS only - segundos minimos.
         * Ver EventExporter#kve_playSeconds para la limitacion documentada del
         * fallback sin stat de playtime confirmada.
         */
        public int playtimeMinSeconds = 0;

        /**
         * ENTITY_HAS_ITEM_EQUIPPED only - "itemRef (picker a
         * ItemEntry o tag/id libre)". Campo propio en vez de reusar
         * {@link #equippedItemId} (que HAS_ITEM_EQUIPPED de jugador ya usa) por el
         * mismo motivo que {@code Action#removeEffectId} no reusa
         * {@code Action#effectId}: cambiar de tipo en el editor no debe dejar un
         * id "fantasma" compartido entre los dos. {@link #equippedSlot} SI se
         * reusa (mismo enum EquipSlot, misma semantica de slot) - "slot
         * (reusa EquipSlot)".
         */
        public String entityEquippedItemId = "";

        public Condition copy() {
            Condition c = new Condition();
            c.type = type;
            c.dimensionId = dimensionId;
            c.permissionNode = permissionNode;
            c.gamemode = gamemode;
            c.timeOfDay = timeOfDay;
            c.yMin = yMin;
            c.yMax = yMax;
            c.biomeId = biomeId;
            c.xpLevelMin = xpLevelMin;
            c.hasItemId = hasItemId;
            c.hasItemMinCount = hasItemMinCount;
            c.randomChance = randomChance;
            c.healthMax = healthMax;
            c.healthMin = healthMin;
            c.foodLevelMin = foodLevelMin;
            c.hasEffectId = hasEffectId;
            c.equippedItemId = equippedItemId;
            c.equippedSlot = equippedSlot;
            c.advancementId = advancementId;
            c.weatherState = weatherState;
            c.moonPhase = moonPhase;
            c.dayOfWeek = dayOfWeek;
            c.dateStartMd = dateStartMd;
            c.dateEndMd = dateEndMd;
            c.hourStart = hourStart;
            c.hourEnd = hourEnd;
            c.stageId = stageId;
            c.flagId = flagId;
            c.playtimeMinSeconds = playtimeMinSeconds;
            c.entityEquippedItemId = entityEquippedItemId;
            return c;
        }

        public Integer permissionLevel() {
            String v = permissionNode == null ? "" : permissionNode.trim();
            if (v.isEmpty()) return null;
            if (v.equalsIgnoreCase("op")) return 2;
            try {
                int lvl = Integer.parseInt(v);
                if (lvl >= 0 && lvl <= 4) return lvl;
            } catch (NumberFormatException ignored) { }
            return -1;
        }
    }

    public enum ActionType {
        MESSAGE, TITLE, GIVE_ITEM, RUN_COMMAND, PLAY_SOUND, SPAWN_ENTITY,
        ACTIONBAR, TAKE_ITEM, GIVE_XP_LEVELS, ADD_EFFECT, TELEPORT, TELEPORT_SPAWN,
        CLEAR_ITEM, GIVE_XP_POINTS, SET_XP_LEVEL, ADD_STAGE, REMOVE_STAGE,
        HEAL, SET_FOOD, SET_HEALTH, CLEAR_EFFECTS,
        BOSSBAR_SHOW, BOSSBAR_CLEAR, PARTICLES, SET_TIME, SET_WEATHER,
        EXPLOSION_HARMLESS, RUN_COMMAND_AS_PLAYER, GIVE_MONEY, TAKE_MONEY, FIRE_CUSTOM_EVENT,
        CLEAR_TITLE, GRANT_ADVANCEMENT, REMOVE_EFFECT, SET_SPAWN,
        LIGHTNING, APPLY_LOOT_TABLE
    }

    /** BOSSBAR_SHOW only - vanilla {@code BossEvent.BossBarColor} options. */
    public enum BossbarColor { PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE }

    /** SET_TIME only - "timeValue o DAY/NIGHT". */
    public enum SetTimeMode { VALUE, DAY, NIGHT }

    public static class Action {
        public ActionType type = ActionType.MESSAGE;

        /** MESSAGE and ACTIONBAR - mismo campo, misma semántica de "texto a mostrar". */
        public String text = "";

        /** TITLE only. */
        public String title = "";
        public String subtitle = "";

        /** GIVE_ITEM only. */
        public String itemId = "";
        public int itemCount = 1;
        /** GIVE_ITEM only, optional - SNBT text applied to the given stack. */
        public String nbtString = "";

        /** RUN_COMMAND only - no leading '/'; runs as the server. */
        public String command = "";

        /** PLAY_SOUND only. */
        public String soundId = "";
        public float volume = 1.0f;
        public float pitch = 1.0f;

        /** SPAWN_ENTITY only. */
        public String spawnEntityId = "";
        public int spawnCount = 1;
        public double spawnRadius = 2.0;

        public int xpLevels = 1;

        public String effectId = "";
        public int effectDurationTicks = 200;
        public int effectAmplifier = 0;

        public String teleportDim = "";
        public double teleportX = 0;
        public double teleportY = 64;
        public double teleportZ = 0;
        public float teleportYaw = 0;
        public float teleportPitch = 0;

        public int xpPoints = 1;
        public int setXpLevel = 0;
        public String stageId = "";
        public boolean healFull = true;
        public float healAmount = 1.0f;
        public int setFoodLevel = 20;
        public float setFoodSaturation = 0.0f;
        public boolean setFoodSaturationEnabled = false;
        public float setHealthAmount = 20.0f;

        /** BOSSBAR_SHOW only - "text, color, seconds". */
        public String bossbarText = "";
        public BossbarColor bossbarColor = BossbarColor.PURPLE;
        public int bossbarSeconds = 10;
        /** PARTICLES only - "particleId, count, spread". */
        public String particleId = "";
        public int particleCount = 10;
        public double particleSpread = 0.5;
        /** SET_TIME only - "timeValue o DAY/NIGHT". */
        public SetTimeMode setTimeMode = SetTimeMode.DAY;
        public long setTimeValue = 1000L;
        /** SET_WEATHER only - "CLEAR/RAIN/THUNDER, duration?". 0 = duracion por defecto del juego. */
        public WeatherState setWeatherMode = WeatherState.CLEAR;
        public int setWeatherDurationTicks = 0;
        /** EXPLOSION_HARMLESS only - "power, breakBlocks=false (show only)". breakBlocks siempre false, no es configurable a proposito (nombre de la accion lo deja claro). */
        public float explosionPower = 2.0f;
        /** RUN_COMMAND_AS_PLAYER only - "command (permisos del jugador)". */
        public String commandAsPlayer = "";
        /** GIVE_MONEY / TAKE_MONEY only - "amount, currencyKey (opcional; no-op si no hay mod)". */
        public double moneyAmount = 1.0;
        public String currencyKey = "";
        /** FIRE_CUSTOM_EVENT only - "eventId string". */
        public String customEventId = "";

        /** GRANT_ADVANCEMENT only - "advancementId". */
        public String grantAdvancementId = "";
        /**
         * REMOVE_EFFECT only - "effectId". Campo propio
         * en vez de reusar {@link #effectId} (que ADD_EFFECT ya usa) para que
         * cambiar de ADD_EFFECT a REMOVE_EFFECT en el editor no deje un
         * effectId "fantasma" compartido entre los dos tipos de acción.
         */
        public String removeEffectId = "";
        /** SET_SPAWN only - "dim?, x, y, z". Vacío dim = misma dimensión del jugador. */
        public String setSpawnDim = "";
        public double setSpawnX = 0;
        public double setSpawnY = 64;
        public double setSpawnZ = 0;

        /**
         * APPLY_LOOT_TABLE only - "lootRef (picker a
         * LootTableExporter en vez de id de texto)". El picker
         * ({@code LootTablePickerScreen}, ya usado por el flujo de templates de
         * Loot Table Editor) solo rellena este campo con el id elegido - lo que
         * se exporta sigue siendo el string resuelto, no una referencia viva
         * a KVE.
         */
        public String lootTableId = "";

        public Action copy() {
            Action c = new Action();
            c.type = type;
            c.text = text;
            c.title = title; c.subtitle = subtitle;
            c.itemId = itemId; c.itemCount = itemCount; c.nbtString = nbtString;
            c.command = command;
            c.soundId = soundId; c.volume = volume; c.pitch = pitch;
            c.spawnEntityId = spawnEntityId; c.spawnCount = spawnCount; c.spawnRadius = spawnRadius;
            c.xpLevels = xpLevels;
            c.effectId = effectId; c.effectDurationTicks = effectDurationTicks; c.effectAmplifier = effectAmplifier;
            c.teleportDim = teleportDim; c.teleportX = teleportX; c.teleportY = teleportY; c.teleportZ = teleportZ;
            c.teleportYaw = teleportYaw; c.teleportPitch = teleportPitch;
            c.xpPoints = xpPoints; c.setXpLevel = setXpLevel; c.stageId = stageId;
            c.healFull = healFull; c.healAmount = healAmount;
            c.setFoodLevel = setFoodLevel; c.setFoodSaturation = setFoodSaturation; c.setFoodSaturationEnabled = setFoodSaturationEnabled;
            c.setHealthAmount = setHealthAmount;
            c.bossbarText = bossbarText; c.bossbarColor = bossbarColor; c.bossbarSeconds = bossbarSeconds;
            c.particleId = particleId; c.particleCount = particleCount; c.particleSpread = particleSpread;
            c.setTimeMode = setTimeMode; c.setTimeValue = setTimeValue;
            c.setWeatherMode = setWeatherMode; c.setWeatherDurationTicks = setWeatherDurationTicks;
            c.explosionPower = explosionPower;
            c.commandAsPlayer = commandAsPlayer;
            c.moneyAmount = moneyAmount; c.currencyKey = currencyKey;
            c.customEventId = customEventId;
            c.grantAdvancementId = grantAdvancementId;
            c.removeEffectId = removeEffectId;
            c.setSpawnDim = setSpawnDim; c.setSpawnX = setSpawnX; c.setSpawnY = setSpawnY; c.setSpawnZ = setSpawnZ;
            c.lootTableId = lootTableId;
            return c;
        }
    }

    public static class Limits {
        public boolean oncePerPlayer = false;
        /** 0 = sin cooldown. */
        public int cooldownSeconds = 0;
        /** 0 = ilimitado (solo aplica a INTERVAL/COMMAND). */
        public int maxGlobalStarts = 0;

        public Limits copy() {
            Limits c = new Limits();
            c.oncePerPlayer = oncePerPlayer;
            c.cooldownSeconds = cooldownSeconds;
            c.maxGlobalStarts = maxGlobalStarts;
            return c;
        }
    }

    /** "namespace:path", e.g. "kve:welcome_kit". */
    public String id = "";
    /** UI-only / comentario en script. */
    public String displayName = "";
    public boolean enabled = true;
    /** Opcional: "starter", "seasonal", "staff"... */
    public String category = "";

    public Trigger trigger = new Trigger();
    /** AND; lista vacía = siempre. */
    public List<Condition> conditions = new ArrayList<>();
    /** En orden. */
    public List<Action> actions = new ArrayList<>();
    public Limits limits = new Limits();

    /** Casi siempre false (server_scripts); true solo si más adelante se usan hooks de startup. */
    public boolean requiresRestart = false;

    /** Stable key for maps/history, same role as {@code TagEntry#key()}. */
    public String key() { return id; }

    public EventEntry copy() {
        EventEntry c = new EventEntry();
        c.id = id;
        c.displayName = displayName;
        c.enabled = enabled;
        c.category = category;
        c.trigger = trigger.copy();
        for (Condition cond : conditions) c.conditions.add(cond.copy());
        for (Action act : actions) c.actions.add(act.copy());
        c.limits = limits.copy();
        c.requiresRestart = requiresRestart;
        return c;
    }

    /**
     * Commands blocked by the basic RUN_COMMAND blacklist. Matched on the
     * command's first word, case-insensitive. RUN_COMMAND se exporta como
     * {@code server.runCommandSilent(...)} (ver EventExporter#appendAction) - corre
     * como consola, sin el chequeo de permisos que tendría un jugador normal
     * ejecutando el comando el mismo - por eso esta lista importa mas que un
     * blacklist cosmetico: cualquiera de estos, disparado por un evento (incluso
     * uno con trigger de bajo permiso como FIRST_JOIN), se saltearia el nivel de
     * permiso real del comando.
     */
    private static final List<String> RUN_COMMAND_BLACKLIST = List.of(
            "op", "deop", "stop",
            "whitelist", "ban", "ban-ip", "pardon", "pardon-ip",
            "save-off", "save-on", "save-all",
            "forceload", "datapack", "reload",
            "kick", "gamerule", "difficulty", "publish");

    private static List<String> extraBlacklist() {
        return com.kve.common.config.KVEConfig.runCommandExtraBlacklist;
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        List<String> refs = new ArrayList<>();
        collectValidation(errors, refs);
        List<String> all = new ArrayList<>(errors);
        all.addAll(refs);
        return all;
    }

    /**
     * solo
     * los problemas ESTRUCTURALES (campos vacios, formato, rangos numericos)
     * - nunca los de {@link #referenceWarnings()}. {@code EventExporter} usa
     * esto (no {@link #validate()}) para decidir si un evento entero se
     * saltea al exportar, porque un itemRef/entityRef/lootRef que dejo de
     * resolver no debe tumbar el resto del evento.
     */
    public List<String> validateStructural() {
        List<String> errors = new ArrayList<>();
        List<String> refs = new ArrayList<>();
        collectValidation(errors, refs);
        return errors;
    }

    public List<String> referenceWarnings() {
        List<String> errors = new ArrayList<>();
        List<String> refs = new ArrayList<>();
        collectValidation(errors, refs, null, null);
        return refs;
    }

    public BrokenRefIndices brokenRefIndices() {
        List<String> errors = new ArrayList<>();
        List<String> refs = new ArrayList<>();
        Set<Integer> brokenActions = new HashSet<>();
        Set<Integer> brokenConditions = new HashSet<>();
        collectValidation(errors, refs, brokenActions, brokenConditions);
        return new BrokenRefIndices(brokenActions, brokenConditions);
    }

    /** See {@link #brokenRefIndices()}. Index sets are 0-based, matching {@link #actions}/{@link #conditions} list order. */
    public record BrokenRefIndices(Set<Integer> actionIndices, Set<Integer> conditionIndices) {}

    private void collectValidation(List<String> errors, List<String> refs) {
        collectValidation(errors, refs, null, null);
    }

    private void collectValidation(List<String> errors, List<String> refs, Set<Integer> brokenActionIdx, Set<Integer> brokenConditionIdx) {
        errors.addAll(SemanticValidator.validateNamespacedIdFormat(id, "Event id"));

        if (trigger == null) {
            errors.add("Evento sin trigger.");
        } else {
            validateTrigger(errors, refs);
        }

        if (actions == null || actions.isEmpty()) {
            errors.add("Evento sin acciones: se requiere al menos 1.");
        } else {
            for (int i = 0; i < actions.size(); i++) {
                int before = refs.size();
                validateAction(actions.get(i), i, errors, refs);
                if (brokenActionIdx != null && refs.size() > before) brokenActionIdx.add(i);
            }
        }

        if (conditions != null) {
            for (int i = 0; i < conditions.size(); i++) {
                int before = refs.size();
                validateCondition(conditions.get(i), i, errors, refs);
                if (brokenConditionIdx != null && refs.size() > before) brokenConditionIdx.add(i);
            }
        }

        if (limits != null) {
            if (limits.cooldownSeconds < 0) errors.add("Limits.cooldownSeconds no puede ser negativo.");
            if (limits.maxGlobalStarts < 0) errors.add("Limits.maxGlobalStarts no puede ser negativo.");
        }
    }

    private void validateTrigger(List<String> errors, List<String> refs) {
        switch (trigger.type) {
            case INTERVAL -> {
                if (trigger.intervalSeconds < 60) {
                    errors.add("Trigger INTERVAL: intervalSeconds debe ser >= 60 (actual: " + trigger.intervalSeconds + ").");
                }
            }
            case PLAYER_KILL -> {
                if (trigger.count < 1) {
                    errors.add("Trigger PLAYER_KILL: count debe ser >= 1 (actual: " + trigger.count + ").");
                }
                refs.addAll(SemanticValidator.validateEntityId(trigger.entityId, "Trigger PLAYER_KILL.entityId"));
            }
            case COMMAND -> {
                if (trigger.commandName == null || trigger.commandName.isBlank()) {
                    errors.add("Trigger COMMAND: commandName no puede estar vacio.");
                }
            }
            case ENTER_BIOME -> {
                if (trigger.biomeId == null || trigger.biomeId.isBlank()) {
                    errors.add("Trigger ENTER_BIOME: biomeId no puede estar vacio.");
                }
            }
            case PLAYER_DEATH -> { }
            case PLAYER_RESPAWN -> { }
            case PLAYER_HURT -> {
                if (trigger.minDamage < 0) {
                    errors.add("Trigger PLAYER_HURT: minDamage no puede ser negativo (actual: " + trigger.minDamage + ").");
                }
            }
            case DIMENSION_CHANGE -> {
                errors.addAll(SemanticValidator.validateNamespacedIdFormat(trigger.toDimension, "Trigger DIMENSION_CHANGE.toDimension"));
                if (trigger.fromDimension != null && !trigger.fromDimension.isBlank()) {
                    errors.addAll(SemanticValidator.validateNamespacedIdFormat(trigger.fromDimension, "Trigger DIMENSION_CHANGE.fromDimension"));
                }
            }
            case BLOCK_BREAK, BLOCK_PLACE -> {
                if (trigger.blockId == null || trigger.blockId.isBlank()) {
                    errors.add("Trigger " + trigger.type + ": blockId no puede estar vacio.");
                } else if (!trigger.blockId.startsWith("#")) {
                    errors.addAll(SemanticValidator.validateBlockId(trigger.blockId, "Trigger " + trigger.type + ".blockId"));
                }
                if (trigger.count < 1) {
                    errors.add("Trigger " + trigger.type + ": count debe ser >= 1 (actual: " + trigger.count + ").");
                }
            }
            case ITEM_PICKUP, ITEM_CRAFT -> {
                if (trigger.itemId == null || trigger.itemId.isBlank()) {
                    errors.add("Trigger " + trigger.type + ": itemId no puede estar vacio.");
                } else if (!trigger.itemId.startsWith("#")) {
                    refs.addAll(SemanticValidator.validateItemId(trigger.itemId, "Trigger " + trigger.type + ".itemId"));
                }
                if (trigger.count < 1) {
                    errors.add("Trigger " + trigger.type + ": count debe ser >= 1 (actual: " + trigger.count + ").");
                }
            }
            case FIRST_JOIN, PLAYER_LOGIN -> { }
            case XP_LEVEL_REACH -> {
                if (trigger.xpLevel < 1) {
                    errors.add("Trigger XP_LEVEL_REACH: level debe ser >= 1 (actual: " + trigger.xpLevel + ").");
                }
            }
            case XP_CHANGED -> {
                if (trigger.xpMinDelta < 1) {
                    errors.add("Trigger XP_CHANGED: minDelta debe ser >= 1 (actual: " + trigger.xpMinDelta + ").");
                }
            }
            case ADVANCEMENT_EARNED -> errors.addAll(SemanticValidator.validateNamespacedIdFormat(trigger.advancementId, "Trigger ADVANCEMENT_EARNED.advancementId"));
            case EFFECT_GAINED -> refs.addAll(SemanticValidator.validateEffectId(trigger.effectId, "Trigger EFFECT_GAINED.effectId"));
            case ITEM_USE, ITEM_CONSUME, ITEM_SMELT -> {
                if (trigger.itemId == null || trigger.itemId.isBlank()) {
                    errors.add("Trigger " + trigger.type + ": itemId no puede estar vacio.");
                } else if (!trigger.itemId.startsWith("#")) {
                    refs.addAll(SemanticValidator.validateItemId(trigger.itemId, "Trigger " + trigger.type + ".itemId"));
                }
            }
            case FISHING_CATCH, VILLAGER_TRADE -> {
                if (trigger.itemId != null && !trigger.itemId.isBlank() && !trigger.itemId.startsWith("#")) {
                    refs.addAll(SemanticValidator.validateItemId(trigger.itemId, "Trigger " + trigger.type + ".itemId"));
                }
            }
            case ENTITY_KILL_ANY -> {
                if (trigger.count < 1) {
                    errors.add("Trigger ENTITY_KILL_ANY: count debe ser >= 1 (actual: " + trigger.count + ").");
                }
            }
            case ENTITY_INTERACT -> refs.addAll(SemanticValidator.validateEntityId(trigger.entityTypeId, "Trigger ENTITY_INTERACT.entityTypeId"));
            case TAMING, BREEDING, MOUNT -> {
                if (trigger.entityTypeId != null && !trigger.entityTypeId.isBlank()) {
                    refs.addAll(SemanticValidator.validateEntityId(trigger.entityTypeId, "Trigger " + trigger.type + ".entityTypeId"));
                }
            }
            case PLAYER_DISCONNECT, SERVER_STARTUP -> { }
            case EMPTY_BUCKET, FILL_BUCKET -> {
                if (trigger.fluidId != null && !trigger.fluidId.isBlank() && !trigger.fluidId.startsWith("#")) {
                    errors.addAll(SemanticValidator.validateNamespacedIdFormat(trigger.fluidId, "Trigger " + trigger.type + ".fluidId"));
                }
            }
            case ENTITY_SPAWN, ENTITY_DEATH, ENTITY_ATTACKS -> {
                if (trigger.entityTypeId != null && !trigger.entityTypeId.isBlank()) {
                    refs.addAll(SemanticValidator.validateEntityId(trigger.entityTypeId, "Trigger " + trigger.type + ".entityTypeId"));
                }
            }
        }
    }

    private void validateCondition(Condition c, int index, List<String> errors, List<String> refs) {
        if (c == null) { errors.add("Condition #" + (index + 1) + ": nula."); return; }
        String label = "Condition #" + (index + 1);
        switch (c.type) {
            case DIMENSION -> errors.addAll(SemanticValidator.validateNamespacedIdFormat(c.dimensionId, label + " (DIMENSION).dimensionId"));
            case PERMISSION -> {
                if (c.permissionLevel() != null && c.permissionLevel() == -1) {
                    errors.add(label + " (PERMISSION): debe ser 'op' o un numero de nivel 0-4 (MVP sin puente a permission-nodes de mods; dejalo vacio para no restringir).");
                }
            }
            case GAMEMODE, TIME_OF_DAY -> { }
            case Y_LEVEL_MIN, Y_LEVEL_MAX -> { }
            case BIOME -> {
                if (c.biomeId == null || c.biomeId.isBlank()) errors.add(label + " (BIOME): biomeId no puede estar vacio.");
            }
            case XP_LEVEL_MIN -> {
                if (c.xpLevelMin < 0) errors.add(label + " (XP_LEVEL_MIN): no puede ser negativo (actual: " + c.xpLevelMin + ").");
            }
            case HAS_ITEM -> {
                refs.addAll(SemanticValidator.validateItemId(c.hasItemId, label + " (HAS_ITEM).hasItemId"));
                if (c.hasItemMinCount < 1) errors.add(label + " (HAS_ITEM): minCount debe ser >= 1 (actual: " + c.hasItemMinCount + ").");
            }
            case RANDOM_CHANCE -> {
                if (c.randomChance < 0.0 || c.randomChance > 1.0) {
                    errors.add(label + " (RANDOM_CHANCE): chance debe estar entre 0.0 y 1.0 (actual: " + c.randomChance + ").");
                }
            }
            case HEALTH_MAX -> {
                if (c.healthMax < 0) errors.add(label + " (HEALTH_MAX): no puede ser negativo (actual: " + c.healthMax + ").");
            }
            case HEALTH_MIN -> {
                if (c.healthMin < 0) errors.add(label + " (HEALTH_MIN): no puede ser negativo (actual: " + c.healthMin + ").");
            }
            case FOOD_LEVEL_MIN -> {
                if (c.foodLevelMin < 0 || c.foodLevelMin > 20) {
                    errors.add(label + " (FOOD_LEVEL_MIN): debe estar entre 0 y 20 (actual: " + c.foodLevelMin + ").");
                }
            }
            case HAS_EFFECT -> refs.addAll(SemanticValidator.validateEffectId(c.hasEffectId, label + " (HAS_EFFECT).hasEffectId"));
            case HAS_ITEM_EQUIPPED -> refs.addAll(SemanticValidator.validateItemId(c.equippedItemId, label + " (HAS_ITEM_EQUIPPED).equippedItemId"));
            case ADVANCEMENT_DONE -> errors.addAll(SemanticValidator.validateNamespacedIdFormat(c.advancementId, label + " (ADVANCEMENT_DONE).advancementId"));
            case ADVANCEMENT_MISSING -> errors.addAll(SemanticValidator.validateNamespacedIdFormat(c.advancementId, label + " (ADVANCEMENT_MISSING).advancementId"));
            case WEATHER, DAY_OF_WEEK -> { }
            case MOON_PHASE -> {
                if (!isValidMoonPhase(c.moonPhase)) {
                    errors.add(label + " (MOON_PHASE): debe ser un numero 0-7 o FULL/NEW (actual: '" + c.moonPhase + "').");
                }
            }
            case DATE_BETWEEN -> {
                if (!isValidMonthDay(c.dateStartMd)) errors.add(label + " (DATE_BETWEEN): startMd debe tener formato MM-DD (actual: '" + c.dateStartMd + "').");
                if (!isValidMonthDay(c.dateEndMd)) errors.add(label + " (DATE_BETWEEN): endMd debe tener formato MM-DD (actual: '" + c.dateEndMd + "').");
            }
            case HOUR_BETWEEN -> {
                if (c.hourStart < 0 || c.hourStart > 23) errors.add(label + " (HOUR_BETWEEN): startHour debe estar entre 0 y 23 (actual: " + c.hourStart + ").");
                if (c.hourEnd < 0 || c.hourEnd > 23) errors.add(label + " (HOUR_BETWEEN): endHour debe estar entre 0 y 23 (actual: " + c.hourEnd + ").");
            }
            case HAS_TAG_STAGE -> {
                if (c.stageId == null || c.stageId.isBlank()) errors.add(label + " (HAS_TAG_STAGE): stageId no puede estar vacio.");
            }
            case NOT_HAS_FLAG -> {
                if (c.flagId == null || c.flagId.isBlank()) errors.add(label + " (NOT_HAS_FLAG): flagId no puede estar vacio.");
            }
            case PLAYTIME_MIN_SECONDS -> {
                if (c.playtimeMinSeconds < 0) errors.add(label + " (PLAYTIME_MIN_SECONDS): no puede ser negativo (actual: " + c.playtimeMinSeconds + ").");
            }
            case ENTITY_HAS_ITEM_EQUIPPED -> refs.addAll(SemanticValidator.validateItemId(c.entityEquippedItemId, label + " (ENTITY_HAS_ITEM_EQUIPPED).entityEquippedItemId"));
        }
    }

    private static boolean isValidMoonPhase(String v) {
        if (v == null) return false;
        String t = v.trim();
        if (t.equalsIgnoreCase("FULL") || t.equalsIgnoreCase("NEW")) return true;
        try {
            int n = Integer.parseInt(t);
            return n >= 0 && n <= 7;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean isValidMonthDay(String v) {
        if (v == null) return false;
        String t = v.trim();
        if (!t.matches("\\d{2}-\\d{2}")) return false;
        int month = Integer.parseInt(t.substring(0, 2));
        int day = Integer.parseInt(t.substring(3, 5));
        return month >= 1 && month <= 12 && day >= 1 && day <= 31;
    }

    private void validateAction(Action a, int index, List<String> errors, List<String> refs) {
        if (a == null) { errors.add("Action #" + (index + 1) + ": nula."); return; }
        String label = "Action #" + (index + 1);
        switch (a.type) {
            case MESSAGE -> {
                if (a.text == null || a.text.isBlank()) errors.add(label + " (MESSAGE): text no puede estar vacio.");
            }
            case TITLE -> {
                if (a.title == null || a.title.isBlank()) errors.add(label + " (TITLE): title no puede estar vacio.");
            }
            case GIVE_ITEM -> {
                refs.addAll(SemanticValidator.validateItemId(a.itemId, label + " (GIVE_ITEM).itemId"));
                if (a.itemCount < 1 || a.itemCount > 64) {
                    errors.add(label + " (GIVE_ITEM): count debe estar entre 1 y 64 (actual: " + a.itemCount + ").");
                }
            }
            case RUN_COMMAND -> {
                if (a.command == null || a.command.isBlank()) {
                    errors.add(label + " (RUN_COMMAND): command no puede estar vacio.");
                } else {
                    String first = a.command.trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
                    // A veces se escribe con "/" adelante (copiado de chat) - se lo saca
                    // para no dejar pasar "/op" solo porque no matchea "op" literal.
                    if (first.startsWith("/")) first = first.substring(1);
                    if (RUN_COMMAND_BLACKLIST.contains(first) || extraBlacklist().contains(first)) {
                        errors.add(label + " (RUN_COMMAND): comando '" + first + "' esta en la lista negra.");
                    }
                }
            }
            case PLAY_SOUND -> {
                if (a.soundId == null || a.soundId.isBlank()) errors.add(label + " (PLAY_SOUND): soundId no puede estar vacio.");
            }
            case SPAWN_ENTITY -> {
                refs.addAll(SemanticValidator.validateEntityId(a.spawnEntityId, label + " (SPAWN_ENTITY).spawnEntityId"));
                if (a.spawnCount < 1) errors.add(label + " (SPAWN_ENTITY): count debe ser >= 1 (actual: " + a.spawnCount + ").");
            }
            case ACTIONBAR -> {
                if (a.text == null || a.text.isBlank()) errors.add(label + " (ACTIONBAR): text no puede estar vacio.");
            }
            case TAKE_ITEM -> {
                refs.addAll(SemanticValidator.validateItemId(a.itemId, label + " (TAKE_ITEM).itemId"));
                if (a.itemCount < 1 || a.itemCount > 64) {
                    errors.add(label + " (TAKE_ITEM): count debe estar entre 1 y 64 (actual: " + a.itemCount + ").");
                }
            }
            case GIVE_XP_LEVELS -> {
                if (a.xpLevels < 1) errors.add(label + " (GIVE_XP_LEVELS): levels debe ser >= 1 (actual: " + a.xpLevels + ").");
            }
            case ADD_EFFECT -> {
                refs.addAll(SemanticValidator.validateEffectId(a.effectId, label + " (ADD_EFFECT).effectId"));
                if (a.effectDurationTicks < 1) errors.add(label + " (ADD_EFFECT): durationTicks debe ser >= 1 (actual: " + a.effectDurationTicks + ").");
                if (a.effectAmplifier < 0) errors.add(label + " (ADD_EFFECT): amplifier no puede ser negativo (actual: " + a.effectAmplifier + ").");
            }
            case TELEPORT -> {
                if (a.teleportDim != null && !a.teleportDim.isBlank()) {
                    errors.addAll(SemanticValidator.validateNamespacedIdFormat(a.teleportDim, label + " (TELEPORT).teleportDim"));
                }
            }
            case TELEPORT_SPAWN -> { }
            case CLEAR_ITEM -> refs.addAll(SemanticValidator.validateItemId(a.itemId, label + " (CLEAR_ITEM).itemId"));
            case GIVE_XP_POINTS -> {
                if (a.xpPoints < 1) errors.add(label + " (GIVE_XP_POINTS): points debe ser >= 1 (actual: " + a.xpPoints + ").");
            }
            case SET_XP_LEVEL -> {
                if (a.setXpLevel < 0) errors.add(label + " (SET_XP_LEVEL): level no puede ser negativo (actual: " + a.setXpLevel + ").");
            }
            case ADD_STAGE, REMOVE_STAGE -> {
                if (a.stageId == null || a.stageId.isBlank()) errors.add(label + " (" + a.type + "): stageId no puede estar vacio.");
            }
            case HEAL -> {
                if (!a.healFull && a.healAmount <= 0) errors.add(label + " (HEAL): amount debe ser > 0 cuando no es 'full' (actual: " + a.healAmount + ").");
            }
            case SET_FOOD -> {
                if (a.setFoodLevel < 0 || a.setFoodLevel > 20) {
                    errors.add(label + " (SET_FOOD): foodLevel debe estar entre 0 y 20 (actual: " + a.setFoodLevel + ").");
                }
                if (a.setFoodSaturationEnabled && a.setFoodSaturation < 0) {
                    errors.add(label + " (SET_FOOD): saturation no puede ser negativa (actual: " + a.setFoodSaturation + ").");
                }
            }
            case SET_HEALTH -> {
                if (a.setHealthAmount <= 0) errors.add(label + " (SET_HEALTH): amount debe ser > 0 (actual: " + a.setHealthAmount + ").");
            }
            case CLEAR_EFFECTS -> { }
            case BOSSBAR_SHOW -> {
                if (a.bossbarText == null || a.bossbarText.isBlank()) errors.add(label + " (BOSSBAR_SHOW): text no puede estar vacio.");
                if (a.bossbarSeconds < 1) errors.add(label + " (BOSSBAR_SHOW): seconds debe ser >= 1 (actual: " + a.bossbarSeconds + ").");
            }
            case BOSSBAR_CLEAR -> { }
            case PARTICLES -> {
                errors.addAll(SemanticValidator.validateNamespacedIdFormat(a.particleId, label + " (PARTICLES).particleId"));
                if (a.particleCount < 1) errors.add(label + " (PARTICLES): count debe ser >= 1 (actual: " + a.particleCount + ").");
                if (a.particleSpread < 0) errors.add(label + " (PARTICLES): spread no puede ser negativo (actual: " + a.particleSpread + ").");
            }
            case SET_TIME -> {
                if (a.setTimeMode == SetTimeMode.VALUE && a.setTimeValue < 0) {
                    errors.add(label + " (SET_TIME): timeValue no puede ser negativo (actual: " + a.setTimeValue + ").");
                }
            }
            case SET_WEATHER -> {
                if (a.setWeatherDurationTicks < 0) errors.add(label + " (SET_WEATHER): duration no puede ser negativa (actual: " + a.setWeatherDurationTicks + ").");
            }
            case EXPLOSION_HARMLESS -> {
                if (a.explosionPower <= 0) errors.add(label + " (EXPLOSION_HARMLESS): power debe ser > 0 (actual: " + a.explosionPower + ").");
            }
            case RUN_COMMAND_AS_PLAYER -> {
                if (a.commandAsPlayer == null || a.commandAsPlayer.isBlank()) {
                    errors.add(label + " (RUN_COMMAND_AS_PLAYER): command no puede estar vacio.");
                } else {
                    String first = a.commandAsPlayer.trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
                    if (first.startsWith("/")) first = first.substring(1);
                    if (RUN_COMMAND_BLACKLIST.contains(first) || extraBlacklist().contains(first)) {
                        errors.add(label + " (RUN_COMMAND_AS_PLAYER): comando '" + first + "' esta en la lista negra.");
                    }
                }
            }
            case GIVE_MONEY, TAKE_MONEY -> {
                if (a.moneyAmount <= 0) errors.add(label + " (" + a.type + "): amount debe ser > 0 (actual: " + a.moneyAmount + ").");
            }
            case FIRE_CUSTOM_EVENT -> {
                if (a.customEventId == null || a.customEventId.isBlank()) errors.add(label + " (FIRE_CUSTOM_EVENT): eventId no puede estar vacio.");
            }
            case CLEAR_TITLE -> { }
            case GRANT_ADVANCEMENT -> errors.addAll(SemanticValidator.validateNamespacedIdFormat(a.grantAdvancementId, label + " (GRANT_ADVANCEMENT).advancementId"));
            case REMOVE_EFFECT -> refs.addAll(SemanticValidator.validateEffectId(a.removeEffectId, label + " (REMOVE_EFFECT).effectId"));
            case SET_SPAWN -> {
                if (a.setSpawnDim != null && !a.setSpawnDim.isBlank()) {
                    errors.addAll(SemanticValidator.validateNamespacedIdFormat(a.setSpawnDim, label + " (SET_SPAWN).dim"));
                }
            }
            case LIGHTNING -> { }
            case APPLY_LOOT_TABLE -> refs.addAll(SemanticValidator.validateLootTableId(a.lootTableId, label + " (APPLY_LOOT_TABLE).lootTableId"));
        }
    }
}