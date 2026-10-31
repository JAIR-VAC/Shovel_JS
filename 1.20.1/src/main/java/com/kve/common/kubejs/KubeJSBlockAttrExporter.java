package com.kve.common.kubejs;

import com.kve.common.util.KveNumbers;
import com.kve.common.util.JsStrings;
import com.kve.CraftEditorMod;
import com.kve.common.persist.CraftEditorState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class KubeJSBlockAttrExporter {

    public static class BlockEntry {
        public String  blockId;
        public boolean enabled  = true;
        public String  category = "";

        /** -1 = no change (block keeps its vanilla/mod value). */
        public double  destroySpeed         = -1;
        /** -1 = no change. */
        public double  explosionResistance  = -1;
        /** -1 = no change; valid range once set is 0-15. */
        public int     lightEmission        = -1;
        /** -1 = no change. */
        public double  friction             = -1;

        public boolean requiresTool    = false;
        public boolean requiresToolSet = false;

        /** Zero or more of {@link #HARVEST_TOOLS} (excluding "" itself) - "any of these tool types can mine the block". Empty = no change. */
        public List<String> harvestTools = new ArrayList<>();
        /** 0=wood 1=stone 2=iron 3=diamond 4=netherite, or -1 = no change. Shared minimum tier across every tool in harvestTools. */
        public int    harvestLevel = -1;

        public List<String> harvestToolItems = new ArrayList<>();


        public boolean requireExactHarvestTool = false;

        /**
 * Same convention/export path as
         * {@link KubeJSItemAttrExporter.ItemEntry#tooltip} - a block's
         * tooltip is really its {@code BlockItem}'s tooltip, and KubeJS'
         * {@code ItemEvents.tooltip} takes an item id, so this is exported
         * against {@link #blockId} directly (blocks registered the normal
 * way - {@code event.create(id,...)} - always get a BlockItem
         * under that SAME id, same assumption {@code BlockAttributeScreen}
         * already makes elsewhere when it resolves an icon for blockId via
         * {@code BuiltInRegistries.ITEM}).
         */
        public List<String> tooltip = new ArrayList<>();

        /** Multiplicador de velocidad al caminar encima (arena de almas = 0.4); -1 = sin cambio. */
        public double  speedFactor = -1;
        /** Multiplicador de salto (bloque de miel = 0.5); -1 = sin cambio. */
        public double  jumpFactor  = -1;
        /** Nombre de SoundType (ver {@link #SOUND_TYPES}); "" = sin cambio. */
        public String  soundType   = "";
        /** null = sin cambio; false = se puede atravesar. */
        public Boolean hasCollision = null;
        /** null = sin cambio; true = recibe ticks aleatorios (como cultivos). */
        public Boolean randomlyTicking = null;

        public boolean hasPhysicsTweaks() {
            return speedFactor >= 0 || jumpFactor >= 0 || (soundType != null && !soundType.isBlank())
                    || hasCollision != null || randomlyTicking != null;
        }

        public BlockEntry copy() {
            BlockEntry c = new BlockEntry();
            c.blockId = blockId; c.enabled = enabled; c.category = category;
            c.destroySpeed = destroySpeed; c.explosionResistance = explosionResistance;
            c.lightEmission = lightEmission; c.friction = friction;
            c.requiresTool = requiresTool; c.requiresToolSet = requiresToolSet;
            c.harvestTools = new ArrayList<>(harvestTools); c.harvestLevel = harvestLevel;
            c.harvestToolItems = new ArrayList<>(harvestToolItems);
            c.requireExactHarvestTool = requireExactHarvestTool;
            c.tooltip = new ArrayList<>(tooltip);
            c.speedFactor = speedFactor; c.jumpFactor = jumpFactor; c.soundType = soundType;
            c.hasCollision = hasCollision; c.randomlyTicking = randomlyTicking;
            return c;
        }
    }

    /**
     * Sonidos aceptados por {@code block.setSoundType(...)}: KubeJS los resuelve
     * por nombre de campo estatico de {@code SoundType} en minusculas
     * (SoundTypeWrapper) y cualquier nombre desconocido cae EN SILENCIO a
     * {@code SoundType.EMPTY} (bloque mudo) - por eso lista cerrada, validada
     * contra la clase real en SoundTypeListTest.
     */
    public static final String[] SOUND_TYPES = {
            "stone", "wood", "gravel", "grass", "sand", "wool", "glass", "metal", "snow",
            "ladder", "anvil", "slime_block", "honey_block", "wet_grass", "coral_block", "bamboo",
            "scaffolding", "netherrack", "nether_bricks", "bone_block", "lantern", "chain",
            "amethyst", "copper", "deepslate", "mud", "sculk", "cherry_wood", "powder_snow"
    };

    /** Cycle order for the harvest-tool picker in {@code BlockAttributeScreen}; "" (first) means "sin cambios / cualquiera". */
    public static final String[] HARVEST_TOOLS = { "", "pickaxe", "axe", "shovel", "hoe" };
    /** Cycle order for the harvest-level picker; index doubles as the KubeJS harvestLevel value, -1 (first) means "sin cambios". */
    public static final String[] HARVEST_LEVELS = { "-1", "0", "1", "2", "3", "4" };

    /** Semantic validation: is {@code blockId} a real, well-formed block? A failing entry is skipped at write time - see {@link #writeScript()}. */
    public static List<String> validateBlock(BlockEntry e) {
        List<String> errors = new ArrayList<>();
        if (e == null) { errors.add("Block data is null."); return errors; }
        if (e.blockId == null || e.blockId.isBlank()) errors.add("Block id vacio.");
        else errors.addAll(SemanticValidator.validateBlockId(e.blockId, "Block"));
        if (e.lightEmission > 15) errors.add("lightEmission fuera de rango (0-15): " + e.lightEmission);
        if (e.harvestTools != null) {
            for (String tool : e.harvestTools) {
                if (tool == null || tool.isEmpty()
                        || java.util.Arrays.stream(HARVEST_TOOLS).noneMatch(t -> t.equals(tool)))
                    errors.add("harvestTool invalido: " + tool);
            }
        }
        if (e.harvestLevel > 4) errors.add("harvestLevel fuera de rango (0-4): " + e.harvestLevel);
        if (e.requireExactHarvestTool && exactToolItems(e).isEmpty())
            errors.add("requireExactHarvestTool activo pero no hay ningun item exacto elegido (harvestToolItems vacio)");
        if (e.speedFactor > 5) errors.add("speedFactor fuera de rango (0-5): " + e.speedFactor);
        if (e.jumpFactor > 5) errors.add("jumpFactor fuera de rango (0-5): " + e.jumpFactor);
        if (e.soundType != null && !e.soundType.isBlank()
                && java.util.Arrays.stream(SOUND_TYPES).noneMatch(s -> s.equals(e.soundType)))
            errors.add("soundType desconocido (quedaria mudo): " + e.soundType);
        return errors;
    }

    /** Entries omitted from the last {@link #writeScript()} because {@link #validateBlock()} failed. */
    private static final List<String> LAST_WRITE_WARNINGS = new ArrayList<>();

    public static List<String> getLastWriteWarnings() { return Collections.unmodifiableList(LAST_WRITE_WARNINGS); }

    private static final Map<String, BlockEntry> ENTRIES = new LinkedHashMap<>();

    /**
     * Lo ultimo aplicado (o cargado al arrancar): lo que se guarda en el estado y lo que leen
     * los enforcers. Los borradores pendientes viven solo en ENTRIES hasta Aplicar.
     */
    private static volatile Map<String, BlockEntry> APPLIED = Map.of();

    public static Collection<BlockEntry> getAppliedEntries() { return APPLIED.values(); }
    public static BlockEntry getAppliedEntry(String id) { return APPLIED.get(id); }
    public static boolean hasAppliedEntries() { return !APPLIED.isEmpty(); }

    /** Toma la foto de ENTRIES como aplicada; se llama al escribir el script y al cargar el estado. */
    public static void markApplied() {
        Map<String, BlockEntry> m = new LinkedHashMap<>();
        for (Map.Entry<String, BlockEntry> e : ENTRIES.entrySet()) m.put(e.getKey(), e.getValue().copy());
        APPLIED = java.util.Collections.unmodifiableMap(m);
    }
    private static final Map<String, Deque<BlockEntry>> HISTORY =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Deque<BlockEntry>> eldest) {
                    return size() > Math.max(1, com.kve.common.config.KVEConfig.undoHistoryMaxKeys);
                }
            };

    public static Collection<BlockEntry> getEntries()         { return Collections.unmodifiableCollection(ENTRIES.values()); }
    public static BlockEntry             getEntry(String id)  { return ENTRIES.get(id); }

    public static void clearAll() { ENTRIES.clear(); APPLIED = Map.of(); HISTORY.clear(); }

    public static void loadEntry(BlockEntry e) { ENTRIES.put(e.blockId, e); }

    public static void setEnabled(String id, boolean enabled) {
        BlockEntry e = ENTRIES.get(id);
        if (e == null) return;
        pushHistory(id, e);
        e.enabled = enabled;
        writeScript();
        CraftEditorState.save();
    }

    /** Stages an enabled/disabled toggle (in-memory only, no script/state write) - see stageBlock(). Returns false if there's no such entry to toggle. */
    public static boolean stageSetEnabled(String id, boolean enabled) {
        BlockEntry e = ENTRIES.get(id);
        if (e == null) return false;
        pushHistory(id, e);
        e.enabled = enabled;
        return true;
    }

    /** Undoes a stageSetEnabled(): restores the previous enabled value (mirrors discardStagedBlock()'s "put the old state back" contract). */
    public static void discardStagedSetEnabled(String id, boolean previousEnabled) {
        BlockEntry e = ENTRIES.get(id);
        if (e != null) e.enabled = previousEnabled;
    }

    /** Stages a block into ENTRIES (in-memory only, no script/state write). */
    public static void stageBlock(BlockEntry e) {
        BlockEntry previous = ENTRIES.get(e.blockId);
        if (previous != null) pushHistory(e.blockId, previous);
        ENTRIES.put(e.blockId, e);
    }

    /** Undoes a stageBlock(): drops the staged entry and restores whatever was there before (null if it was new). */
    public static void discardStagedBlock(String id, BlockEntry previousOrNull) {
        if (previousOrNull != null) ENTRIES.put(id, previousOrNull);
        else ENTRIES.remove(id);
    }

    /** Stages a block-entry deletion (in-memory only). */
    public static void stageDeleteBlock(String id) {
        ENTRIES.remove(id);
        HISTORY.remove(id);
    }

    /** Stages an undo (in-memory only, no script/state write) - see stageBlock(). Returns the restored entry, or null if there's nothing to undo. */
    public static BlockEntry stageUndo(String id) {
        Deque<BlockEntry> h = HISTORY.get(id);
        if (h == null || h.isEmpty()) return null;
        BlockEntry restored = h.removeFirst();
        ENTRIES.put(id, restored);
        return restored;
    }

    /** Undoes a stageUndo(): pushes the restored entry back onto the undo history and restores whatever ENTRIES had before the undo (null if it was new). */
    public static void discardStagedUndo(String id, BlockEntry restored, BlockEntry beforeUndoOrNull) {
        Deque<BlockEntry> h = HISTORY.computeIfAbsent(id, k -> new ArrayDeque<>());
        h.addFirst(restored);
        if (beforeUndoOrNull != null) ENTRIES.put(id, beforeUndoOrNull);
        else ENTRIES.remove(id);
    }

    /** Writes the currently staged ENTRIES to disk (KubeJS script + KVE state). Only called from a PendingChange's onApply. */
    public static void commitBlocks() {
        writeScript();
        CraftEditorState.save();
    }

    private static void pushHistory(String id, BlockEntry snapshot) {
        int max = com.kve.common.config.KVEConfig.undoHistorySize;
        if (max <= 0) return;
        Deque<BlockEntry> h = HISTORY.computeIfAbsent(id, k -> new ArrayDeque<>());
        h.addFirst(snapshot.copy());
        while (h.size() > max) h.removeLast();
    }

    public static boolean canUndo(String id) {
        Deque<BlockEntry> h = HISTORY.get(id);
        return h != null && !h.isEmpty();
    }

    public static BlockEntry undo(String id) {
        Deque<BlockEntry> h = HISTORY.get(id);
        if (h == null || h.isEmpty()) return null;
        BlockEntry previous = h.removeFirst();
        ENTRIES.put(id, previous);
        writeScript();
        CraftEditorState.save();
        return previous;
    }

    private static Path dir() { return KveScriptPaths.blocksStartupDir(); }

    private static Path serverTagsDir() { return KveScriptPaths.blocksServerDir(); }

    private static Path clientDir() { return KveScriptPaths.CLIENT_ROOT.resolve("blocks"); }

    private static void writeScript() {
        markApplied();
        try { Files.createDirectories(dir()); } catch (IOException ex) { return; }

        StringBuilder sb = new StringBuilder();
        sb.append("// Shovel JS - Block Property Modifications (AUTO-GENERATED)\n");
        sb.append("// Apply with: RESTART the game/server (startup_scripts are not reloaded by /reload)\n\n");
        sb.append("BlockEvents.modification(event => {\n");

        LAST_WRITE_WARNINGS.clear();
        List<BlockEntry> exportable = new ArrayList<>();
        for (BlockEntry e : ENTRIES.values()) {
            if (!e.enabled) {
                sb.append("  // DISABLED: ").append(e.blockId).append("\n\n");
                continue;
            }
            List<String> semantic = validateBlock(e);
            if (!semantic.isEmpty()) {
                String reason = String.join("; ", semantic);
                sb.append("  // OMITIDO (fallo la validacion semantica): ").append(e.blockId)
                  .append(" - ").append(reason).append("\n\n");
                String warning = "Block '" + e.blockId + "' omitido: " + reason;
                LAST_WRITE_WARNINGS.add(warning);
                CraftEditorMod.LOGGER.warn("[Shovel JS] {}", warning);
                continue;
            }
            if (!e.category.isEmpty()) sb.append("  // category: ").append(e.category).append("\n");
            appendBlockEntry(sb, e, "  ");
            sb.append("\n");
            exportable.add(e);
        }

        sb.append("})\n");

        ScriptWriteGuard.writeIfValid(KveScriptPaths.blockAttributesFile(), sb.toString(), "shovel_js_block_attributes");

        writeTooltipScript();

        // harvestTool/harvestLevel aren't MutableBlock properties, so they
        // can't go inside the BlockEvents.modification block above - they're
        // written as tag additions in their own SERVER script instead (see
        // serverTagsDir()'s javadoc for why this MUST be a separate file,
        // not just a separate top-level block in the same startup file),
        // and only when at least one already-exportable entry actually set
        // one of the two fields (an empty/missing file is harmless but
        // pointless noise otherwise - and also avoids leaving a stale tags
        // file with old entries around once every block stops needing one).
        List<BlockEntry> withHarvestTags = new ArrayList<>();
        for (BlockEntry e : exportable) {
            boolean hasTool  = e.harvestTools != null && !e.harvestTools.isEmpty();
            boolean hasLevel = e.harvestLevel >= 0;
            boolean hasKveSpecificRequirement =
                    (e.requireExactHarvestTool && !exactToolItems(e).isEmpty()) || e.harvestLevel == 4;
            if (hasTool || hasLevel || hasKveSpecificRequirement) withHarvestTags.add(e);
        }
        try {
            Files.createDirectories(serverTagsDir());
            Path tagsFile = KveScriptPaths.blockHarvestTagsFile();
            if (withHarvestTags.isEmpty()) {
                Files.deleteIfExists(tagsFile);
            } else {
                StringBuilder tagsSb = new StringBuilder();
                tagsSb.append("// Shovel JS - Block Harvest Tool Tags (AUTO-GENERATED)\n");
                tagsSb.append("// Companion file to blocks/attributes.js - MUST stay a separate SERVER script\n");
                tagsSb.append("// (ServerEvents.tags is invalid inside a STARTUP script, see KubeJSBlockAttrExporter#serverTagsDir).\n");
                tagsSb.append("// Apply with: /reload (server_scripts ARE reloaded by /reload, unlike blocks/attributes.js)\n\n");
                tagsSb.append("ServerEvents.tags('block', event => {\n");
                for (BlockEntry e : withHarvestTags) appendHarvestTags(tagsSb, e, "  ");
                tagsSb.append("})\n");
                ScriptWriteGuard.writeIfValid(tagsFile, tagsSb.toString(), "shovel_js_block_harvest_tags");
            }
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Block harvest tags write failed: {}", ex.getMessage());
        }


        List<BlockEntry> withExactTool = new ArrayList<>();
        for (BlockEntry e : exportable) {
            if (e.requireExactHarvestTool && !exactToolItems(e).isEmpty()) withExactTool.add(e);
        }
        try {
            Files.createDirectories(serverTagsDir());
            Path exactFile = KveScriptPaths.blockExactToolFile();
            if (withExactTool.isEmpty()) {
                Files.deleteIfExists(exactFile);
            } else {
                StringBuilder exSb = new StringBuilder();
                exSb.append("// Shovel JS - Block Exact Tool Requirement (AUTO-GENERATED)\n");
                exSb.append("// Companion file to blocks/attributes.js / blocks/harvest_tags.js.\n");
                exSb.append("// Minecraft/KubeJS solo controla 'que herramienta' via tags de categoria+tier\n");
                exSb.append("// (ver blocks/harvest_tags.js) - no existe forma declarativa de exigir\n");
                exSb.append("// UN item puntual. Delega en ShovelAPI.requireExactTool/requireExactToolAny,\n");
                exSb.append("// respaldadas por hooks Forge reales (ExactToolEnforcer) en vez de un evento\n");
                exSb.append("// simulado en JS.\n");
                exSb.append("// Apply with: /reload (server_scripts ARE reloaded by /reload)\n\n");
                appendExactToolRegistration(exSb, withExactTool);
                ScriptWriteGuard.writeIfValid(exactFile, exSb.toString(), "shovel_js_block_exact_tool");
            }
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Block exact-tool write failed: {}", ex.getMessage());
        }

        List<BlockEntry> withNetheriteTier = new ArrayList<>();
        for (BlockEntry e : exportable) {
            if (e.harvestLevel == 4) withNetheriteTier.add(e);
        }
        try {
            Files.createDirectories(serverTagsDir());
            Path netheriteFile = KveScriptPaths.blockNetheriteTierFile();
            if (withNetheriteTier.isEmpty()) {
                Files.deleteIfExists(netheriteFile);
            } else {
                StringBuilder ntSb = new StringBuilder();
                ntSb.append("// Shovel JS - Block Netherite Tier Requirement (AUTO-GENERATED)\n");
                ntSb.append("// Companion file to blocks/harvest_tags.js. Vanilla has no\n");
                ntSb.append("// needs_netherite_tool tag (only stone/iron/diamond exist), so this last\n");
                ntSb.append("// step is enforced via ShovelAPI.requireNetheriteTier, backed by real Forge\n");
                ntSb.append("// hooks (NetheriteTierEnforcer) instead of a tag that does not exist.\n");
                ntSb.append("// Apply with: /reload (server_scripts ARE reloaded by /reload)\n\n");
                appendNetheriteTierRegistration(ntSb, withNetheriteTier);
                ScriptWriteGuard.writeIfValid(netheriteFile, ntSb.toString(), "shovel_js_block_netherite_tier");
            }
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Block netherite-tier write failed: {}", ex.getMessage());
        }
    }

    /**
     * Read-only preview of exactly what {@link #writeScript()} would emit
     * for a single block, without touching disk or the ENTRIES map. Same
     * role as {@link KubeJSItemAttrExporter#previewItemJS}, used by
     * {@code BlockAttributeScreen}'s "Preview" button.
     */
    public static String previewBlockJS(BlockEntry e) {
        if (e == null) return "// (sin datos de bloque)";
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("// startup_scripts/shovel_js/blocks/attributes.js:\n");
            sb.append("BlockEvents.modification(event => {\n");
            appendBlockEntry(sb, e, "  ");
            sb.append("\n})\n");
            boolean hasTool  = e.harvestTools != null && !e.harvestTools.isEmpty();
            boolean hasLevel = e.harvestLevel >= 0;
            boolean hasKveSpecificRequirement =
                    (e.requireExactHarvestTool && !exactToolItems(e).isEmpty()) || e.harvestLevel == 4;
            if (hasTool || hasLevel || hasKveSpecificRequirement) {
                sb.append("\n// server_scripts/shovel_js/blocks/harvest_tags.js:\n");
                sb.append("ServerEvents.tags('block', event => {\n");
                appendHarvestTags(sb, e, "  ");
                sb.append("})\n");
            }
            if (e.requireExactHarvestTool && !exactToolItems(e).isEmpty()) {
                sb.append("\n// server_scripts/shovel_js/blocks/exact_tool.js:\n");
                appendExactToolRegistration(sb, java.util.Collections.singletonList(e));
            }
            if (e.harvestLevel == 4) {
                sb.append("\n// server_scripts/shovel_js/blocks/netherite_tier.js:\n");
                appendNetheriteTierRegistration(sb, java.util.Collections.singletonList(e));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "// ERROR generando preview: " + ex.getMessage();
        }
    }

    private static void appendBlockEntry(StringBuilder sb, BlockEntry e, String indent) {
        sb.append(indent).append("event.modify('").append(e.blockId).append("', block => {\n");

        if (e.destroySpeed >= 0)
            sb.append(indent).append("  block.destroySpeed = ").append(KveNumbers.trimmed(e.destroySpeed, 4)).append("\n");
        if (e.explosionResistance >= 0)
            sb.append(indent).append("  block.explosionResistance = ").append(KveNumbers.trimmed(e.explosionResistance, 4)).append("\n");
        if (e.lightEmission >= 0)
            sb.append(indent).append("  block.lightEmission = ").append(e.lightEmission).append("\n");
        if (e.friction >= 0)
            sb.append(indent).append("  block.friction = ").append(KveNumbers.trimmed(e.friction, 4)).append("\n");
        if (e.speedFactor >= 0)
            sb.append(indent).append("  try { block.setSpeedFactor(").append(KveNumbers.trimmed(e.speedFactor, 4)).append(") } catch (ex) { console.error('[Shovel JS] ").append(e.blockId).append(" speedFactor: ' + ex) }\n");
        if (e.jumpFactor >= 0)
            sb.append(indent).append("  try { block.setJumpFactor(").append(KveNumbers.trimmed(e.jumpFactor, 4)).append(") } catch (ex) { console.error('[Shovel JS] ").append(e.blockId).append(" jumpFactor: ' + ex) }\n");
        if (e.soundType != null && !e.soundType.isBlank())
            sb.append(indent).append("  try { block.setSoundType('").append(e.soundType).append("') } catch (ex) { console.error('[Shovel JS] ").append(e.blockId).append(" soundType: ' + ex) }\n");
        if (e.hasCollision != null)
            sb.append(indent).append("  try { block.setHasCollision(").append(e.hasCollision).append(") } catch (ex) { console.error('[Shovel JS] ").append(e.blockId).append(" hasCollision: ' + ex) }\n");
        if (e.randomlyTicking != null)
            sb.append(indent).append("  try { block.setIsRandomlyTicking(").append(e.randomlyTicking).append(") } catch (ex) { console.error('[Shovel JS] ").append(e.blockId).append(" randomlyTicking: ' + ex) }\n");
        if (e.requiresToolSet) {

            sb.append(indent).append("  try {\n");
            sb.append(indent).append("    block.requiredTool = ").append(e.requiresTool).append("\n");
            sb.append(indent).append("  } catch (ex) {\n");
            sb.append(indent).append("    console.warn('[Shovel JS] block.requiredTool no soportado para ")
                    .append(e.blockId).append(" (KubeJS-Mods/KubeJS#418): ' + ex)\n");
            sb.append(indent).append("  }\n");
        }

        sb.append(indent).append("})\n");
    }


    private static void appendHarvestTags(StringBuilder sb, BlockEntry e, String indent) {
  
        boolean hasKveSpecificToolRequirement =
                (e.requireExactHarvestTool && !exactToolItems(e).isEmpty()) || e.harvestLevel == 4;

        

        if (hasKveSpecificToolRequirement) {
            for (String tool : new String[]{"pickaxe", "axe", "shovel", "hoe"}) {
                sb.append(indent).append("event.remove('minecraft:mineable/").append(tool)
                  .append("', '").append(e.blockId).append("')\n");
            }
            for (String tierTag : new String[]{"minecraft:needs_stone_tool", "minecraft:needs_iron_tool", "minecraft:needs_diamond_tool"}) {
                sb.append(indent).append("event.remove('").append(tierTag)
                  .append("', '").append(e.blockId).append("')\n");
            }
            return;
        }

        if (e.harvestTools != null) {
            for (String tool : e.harvestTools) {
                if (tool == null || tool.isEmpty()) continue;
                sb.append(indent).append("event.add('minecraft:mineable/").append(tool)
                  .append("', '").append(e.blockId).append("')\n");
            }
        }

        if (e.harvestLevel >= 0) {
            String tierTag = needsToolTag(e.harvestLevel);
            if (tierTag != null) {
                sb.append(indent).append("event.add('").append(tierTag)
                  .append("', '").append(e.blockId).append("')\n");
            }
        }
    }

    private static void appendNetheriteTierRegistration(StringBuilder sb, List<BlockEntry> entries) {
        sb.append("ShovelAPI.clearNetheriteTierRequirements()\n");
        for (BlockEntry e : entries) {
            sb.append("ShovelAPI.requireNetheriteTier('").append(JsStrings.escape(e.blockId)).append("')\n");
        }
    }

    private static String needsToolTag(int harvestLevel) {
        return switch (harvestLevel) {
            case 1 -> "minecraft:needs_stone_tool";
            case 2 -> "minecraft:needs_iron_tool";
            case 3 -> "minecraft:needs_diamond_tool";
            default -> null;
        };
    }

    private static List<String> exactToolItems(BlockEntry e) {
        List<String> out = new ArrayList<>();
        if (e.harvestToolItems != null) {
            for (String id : e.harvestToolItems) {
                if (id != null && !id.isBlank()) out.add(id);
            }
        }
        return out;
    }

    private static void appendExactToolRegistration(StringBuilder sb, List<BlockEntry> entries) {
        sb.append("ShovelAPI.clearExactToolRequirements()\n");
        for (BlockEntry e : entries) {
            List<String> items = exactToolItems(e);
            if (items.isEmpty()) continue;
            if (items.size() == 1) {
                sb.append("ShovelAPI.requireExactTool('").append(JsStrings.escape(e.blockId))
                        .append("', '").append(JsStrings.escape(items.get(0))).append("')\n");
            } else {
                sb.append("ShovelAPI.requireExactToolAny('").append(JsStrings.escape(e.blockId)).append("', [");
                for (int i = 0; i < items.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append("'").append(JsStrings.escape(items.get(i))).append("'");
                }
                sb.append("])\n");
            }
        }
    }

    private static void writeTooltipScript() {
        try { Files.createDirectories(clientDir()); } catch (IOException ex) { return; }

        List<BlockEntry> withTooltip = new ArrayList<>();
        for (BlockEntry e : ENTRIES.values()) {
            if (e.enabled && e.tooltip != null && !e.tooltip.isEmpty()) withTooltip.add(e);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("// Shovel JS - Block Tooltips (AUTO-GENERATED)\n");
        sb.append("// client_scripts: apply with F3+T or /kubejs reload client_scripts.\n\n");
        sb.append("ItemEvents.tooltip(event => {\n");
        for (BlockEntry e : withTooltip) {
            sb.append("  try {\n");
            sb.append("    event.addAdvanced('").append(e.blockId).append("', (item, advanced, text) => {\n");
            sb.append("      text.add(1, [\n");
            for (int i = 0; i < e.tooltip.size(); i++) {
                sb.append("        '").append(JsStrings.escape(e.tooltip.get(i))).append("'");
                sb.append(i < e.tooltip.size() - 1 ? ",\n" : "\n");
            }
            sb.append("      ])\n");
            sb.append("    })\n");
            sb.append("  } catch (ex) { console.error('[Shovel JS] tooltip add failed for ")
              .append(e.blockId).append(": ' + ex) }\n");
        }
        sb.append("})\n");

        ScriptWriteGuard.writeIfValid(KveScriptPaths.blockTooltipsFileClient(), sb.toString(), "shovel_js_block_tooltips");
    }

}
