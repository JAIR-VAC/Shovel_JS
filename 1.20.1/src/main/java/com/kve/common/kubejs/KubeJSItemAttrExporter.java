package com.kve.common.kubejs;

import com.kve.common.util.KveNumbers;
import com.kve.CraftEditorMod;
import com.kve.common.persist.CraftEditorState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class KubeJSItemAttrExporter {

    public enum Op {
        ADDITION("addition"),
        MULTIPLY_BASE("multiply_base"),
        MULTIPLY_TOTAL("multiply_total");

        public final String js;
        Op(String js) { this.js = js; }

        public static Op fromJs(String s) {
            for (Op o : values()) if (o.js.equalsIgnoreCase(s)) return o;
            return ADDITION;
        }
    }

    public static class AttrMod {
        public String attrId;
        public double value;
        public Op     op = Op.ADDITION;

        public AttrMod() {}
        public AttrMod(String attrId, double value, Op op) {
            this.attrId = attrId;
            this.value  = value;
            this.op     = op;
        }

        public AttrMod copy() { return new AttrMod(attrId, value, op); }
    }

    /**
     * Modifier de un slot de Curios ("ring",
     * "necklace",...). {@code slot} vacio = cualquier slot. Lo aplica
     * {@code com.kve.common.itemrules.CurioAttributeEnforcer} en runtime.
     */
    public static class SlotAttrMod {
        public String attrId;
        public double value;
        public Op     op = Op.ADDITION;
        public String slot = "";

        public SlotAttrMod() {}
        public SlotAttrMod(String attrId, double value, Op op, String slot) {
            this.attrId = attrId;
            this.value  = value;
            this.op     = op;
            this.slot   = slot == null ? "" : slot;
        }
        /** Clave estable "slot|atributo" - identifica una fila en el editor y en {@link ItemEntry#curioRemoved}. */
        public String key() { return key(slot, attrId); }
        public static String key(String slot, String attrId) { return (slot == null ? "" : slot) + "|" + attrId; }
        public SlotAttrMod copy() { return new SlotAttrMod(attrId, value, op, slot); }
    }

    public static class FoodEffect {
        public String effectId;
        public int    durationTicks = 100;
        public int    amplifier     = 0;
        public float  chance        = 1.0f;

        public FoodEffect() {}
        public FoodEffect(String effectId, int durationTicks, int amplifier, float chance) {
            this.effectId = effectId;
            this.durationTicks = durationTicks;
            this.amplifier = amplifier;
            this.chance = chance;
        }

        public FoodEffect copy() { return new FoodEffect(effectId, durationTicks, amplifier, chance); }
    }

    /**
     * Vanilla's {@code FoodProperties} exposed through the editor: nutrition
     * (hunger restored), saturation modifier, and a list of "eating this has
     * a chance to also apply a status effect" entries (e.g. rotten flesh's
     * chance of Hunger, or a modded food's custom buff). {@code null} on
     * {@link ItemEntry#food} means "no food override" - the item keeps
     * whatever {@code FoodProperties} it already has (or none, if it isn't
     * edible to begin with).
     */
    public static class FoodProps {
        public int     nutrition     = 4;
        public float   saturationMod = 0.3f;
        public boolean alwaysEdible  = false;
        public boolean fastFood      = false;
        public boolean meat          = false;

        public List<FoodEffect> effects = new ArrayList<>();

        public FoodProps copy() {
            FoodProps c = new FoodProps();
            c.nutrition = nutrition; c.saturationMod = saturationMod;
            c.alwaysEdible = alwaysEdible; c.fastFood = fastFood; c.meat = meat;
            for (FoodEffect fx : effects) c.effects.add(fx.copy());
            return c;
        }
    }

    /**
     * 5 features nuevas del editor de items ("Combate y
     * efectos"), aplicadas 100% en Java por
     * {@link com.kve.common.itemrules.ItemCombatEnforcer} (eventos Forge),
     * no por el script KubeJS: funcionan igual en items vanilla y modded,
     * sin reinicio de por medio mas alla del de siempre.
     * <ol>
     *   <li>{@link #hitEffects}: efectos que el arma aplica al objetivo al
     *       golpear cuerpo a cuerpo (con probabilidad propia).</li>
     *   <li>{@link #passiveEffects}: efectos que recibe el jugador mientras
     *       sostiene el item (manos) o lo lleva puesto (armadura).</li>
     *   <li>{@link #lifestealPct}: % del daño final infligido que cura al
     *       atacante.</li>
     *   <li>{@link #igniteSeconds}: segundos que el objetivo queda en llamas
     *       al ser golpeado.</li>
     *   <li>{@link #useCooldownTicks}: enfriamiento (como la perla de ender)
     *       tras usar el item con click derecho.</li>
     * </ol>
     */
    public static class CombatProps {
        public List<FoodEffect> hitEffects     = new ArrayList<>();
        public List<FoodEffect> passiveEffects = new ArrayList<>();
        public double lifestealPct     = 0;
        public int    igniteSeconds    = 0;
        public int    useCooldownTicks = 0;

        public boolean isEmpty() {
            return hitEffects.isEmpty() && passiveEffects.isEmpty()
                    && lifestealPct <= 0 && igniteSeconds <= 0 && useCooldownTicks <= 0;
        }

        public CombatProps copy() {
            CombatProps c = new CombatProps();
            for (FoodEffect fx : hitEffects) c.hitEffects.add(fx.copy());
            for (FoodEffect fx : passiveEffects) c.passiveEffects.add(fx.copy());
            c.lifestealPct = lifestealPct;
            c.igniteSeconds = igniteSeconds;
            c.useCooldownTicks = useCooldownTicks;
            return c;
        }
    }

    public static class ItemEntry {
        public String  itemId;
        public boolean enabled          = true;
        public String  category         = "";
        public int     maxDamage        = -1;
        public int     maxStackSize     = -1;
        public boolean fireResistant    = false;
        public boolean fireResistantSet = false;
        public String  rarity          = "";
        public int     burnTime        = -1;
        public double  attackDamage    = Double.NaN;
        public double  attackSpeed     = Double.NaN;

        public double  armor           = Double.NaN;
        public double  armorToughness  = Double.NaN;
        public double  movementSpeed   = Double.NaN;

        public Op      attackDamageOp    = Op.ADDITION;
        public Op      attackSpeedOp     = Op.ADDITION;
        public Op      armorOp           = Op.ADDITION;
        public Op      armorToughnessOp  = Op.ADDITION;
        public Op      movementSpeedOp   = Op.ADDITION;

        public boolean hideFromCreativeTab     = false;
        public boolean hideFromRecipeViewers   = false;
        /** Nunca aparece en loot generado (drops, cofres, pesca...) - lo aplica HideFromLootModifier en runtime. */
        public boolean hideFromLoot            = false;

        /** null = no food override for this item. See {@link FoodProps}. */
        public FoodProps food = null;

        /** null = sin propiedades de combate/efectos. Ver {@link CombatProps}. */
        public CombatProps combat = null;

        /**
         * Extra lore lines shown below the
         * item's name in its tooltip. Empty = no override, same convention
         * as {@link #rarity}/{@link #category}. Exported as its own
         * {@code ItemEvents.tooltip} block (see {@link #writeScript()}),
         * NOT inside this entry's {@code event.modify()} block - the KubeJS
         * wiki's confirmed tooltip API (kubejs.com/wiki/tutorials/tooltips,
         * {@code event.add(id, [lines])}) lives on a different top-level
         * event than item modification, same reasoning as
         * {@link #hideFromCreativeTab} above.
         */
        public List<String> tooltip = new ArrayList<>();

        public Map<String, AttrMod> attributes = new LinkedHashMap<>();

        public Set<String> removedAttributes = new LinkedHashSet<>();

        public Set<String> addModeAttributes = new LinkedHashSet<>();

        /**
         * Atributos que el item da puesto en un slot de Curios -
         * solo los que difieren de los que el item trae por defecto (mismo
         * criterio que {@link #attributes}). Port del editor de 1.21.1.
         */
        public List<SlotAttrMod> curioAttributes = new ArrayList<>();
        /** Claves {@link SlotAttrMod#key()} de modifiers de curio por defecto que el jugador borro sin reemplazo. */
        public Set<String> curioRemoved = new LinkedHashSet<>();

        public boolean hasCurioEdits() { return !curioAttributes.isEmpty() || !curioRemoved.isEmpty(); }

        public boolean isAddMode(String attrId) { return attrId != null && addModeAttributes.contains(attrId); }

        public void setAttribute(String attrId, double value, Op op) {
            if (attrId == null || attrId.isEmpty()) return;
            attributes.put(attrId, new AttrMod(attrId, value, op));
        }

        public void removeAttribute(String attrId) {
            attributes.remove(attrId);
        }

        public ItemEntry copy() {
            ItemEntry c = new ItemEntry();
            c.itemId = itemId; c.enabled = enabled; c.category = category;
            c.maxDamage = maxDamage; c.maxStackSize = maxStackSize;
            c.fireResistant = fireResistant; c.fireResistantSet = fireResistantSet;
            c.rarity = rarity; c.burnTime = burnTime;
            c.attackDamage = attackDamage; c.attackSpeed = attackSpeed;
            c.armor = armor; c.armorToughness = armorToughness; c.movementSpeed = movementSpeed;
            c.attackDamageOp = attackDamageOp; c.attackSpeedOp = attackSpeedOp;
            c.armorOp = armorOp; c.armorToughnessOp = armorToughnessOp; c.movementSpeedOp = movementSpeedOp;
            c.hideFromCreativeTab = hideFromCreativeTab; c.hideFromRecipeViewers = hideFromRecipeViewers;
            c.hideFromLoot = hideFromLoot;
            c.food = food != null ? food.copy() : null;
            c.combat = combat != null ? combat.copy() : null;
            c.tooltip = new ArrayList<>(tooltip);
            for (var e : attributes.entrySet()) c.attributes.put(e.getKey(), e.getValue().copy());
            c.removedAttributes = new LinkedHashSet<>(removedAttributes);
            c.addModeAttributes = new LinkedHashSet<>(addModeAttributes);
            for (SlotAttrMod m : curioAttributes) c.curioAttributes.add(m.copy());
            c.curioRemoved = new LinkedHashSet<>(curioRemoved);
            return c;
        }
    }

    /** Semantic validation: is {@code itemId} a real, well-formed item (checked against the live registry when available, see {@link SemanticValidator})? A failing entry is skipped at write time - see {@link #writeScript()}. */
    public static List<String> validateItem(ItemEntry e) {
        List<String> errors = new ArrayList<>();
        if (e == null) { errors.add("Item data is null."); return errors; }
        if (e.itemId == null || e.itemId.isBlank()) errors.add("Item id vacio.");
        else errors.addAll(SemanticValidator.validateItemId(e.itemId, "Item"));
        if (e.food != null) {
            for (FoodEffect fx : e.food.effects) {
                errors.addAll(SemanticValidator.validateEffectId(fx.effectId, "Food effect"));
            }
        }
        if (e.combat != null) {
            for (FoodEffect fx : e.combat.hitEffects) {
                errors.addAll(SemanticValidator.validateEffectId(fx.effectId, "Hit effect"));
            }
            for (FoodEffect fx : e.combat.passiveEffects) {
                errors.addAll(SemanticValidator.validateEffectId(fx.effectId, "Passive effect"));
            }
        }
        return errors;
    }

    /** Entries omitted from the last {@link #writeScript()} because {@link #validateItem()} failed. */
    private static final List<String> LAST_WRITE_WARNINGS = new ArrayList<>();

    public static List<String> getLastWriteWarnings() { return Collections.unmodifiableList(LAST_WRITE_WARNINGS); }

    private static final Map<String, ItemEntry> ENTRIES = new LinkedHashMap<>();

    /**
     * Lo ultimo aplicado (o cargado al arrancar): lo que se guarda en el estado y lo que leen
     * los enforcers. Los borradores pendientes viven solo en ENTRIES hasta Aplicar.
     */
    private static volatile Map<String, ItemEntry> APPLIED = Map.of();

    public static Collection<ItemEntry> getAppliedEntries() { return APPLIED.values(); }
    public static ItemEntry getAppliedEntry(String id) { return APPLIED.get(id); }
    public static boolean hasAppliedEntries() { return !APPLIED.isEmpty(); }

    /** Toma la foto de ENTRIES como aplicada; se llama al escribir el script y al cargar el estado. */
    public static void markApplied() {
        Map<String, ItemEntry> m = new LinkedHashMap<>();
        for (Map.Entry<String, ItemEntry> e : ENTRIES.entrySet()) m.put(e.getKey(), e.getValue().copy());
        APPLIED = java.util.Collections.unmodifiableMap(m);
    }
    private static final Map<String, Deque<ItemEntry>> HISTORY =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Deque<ItemEntry>> eldest) {
                    return size() > Math.max(1, com.kve.common.config.KVEConfig.undoHistoryMaxKeys);
                }
            };

    /** Stages an item into ENTRIES (in-memory only, no script/state write). */
    public static void stageItem(ItemEntry e) {
        ItemEntry previous = ENTRIES.get(e.itemId);
        if (previous != null) pushHistory(e.itemId, previous);
        ENTRIES.put(e.itemId, e);
    }

    /** Undoes a stageItem(): drops the staged entry and restores whatever was there before (null if it was new). */
    public static void discardStagedItem(String id, ItemEntry previousOrNull) {
        if (previousOrNull != null) ENTRIES.put(id, previousOrNull);
        else ENTRIES.remove(id);
    }

    /** Stages an item deletion (in-memory only). */
    public static void stageDeleteItem(String id) {
        ENTRIES.remove(id);
        HISTORY.remove(id);
    }

    /** Stages an undo (in-memory only, no script/state write) - see stageItem(). Returns the restored entry, or null if there's nothing to undo. */
    public static ItemEntry stageUndo(String id) {
        Deque<ItemEntry> h = HISTORY.get(id);
        if (h == null || h.isEmpty()) return null;
        ItemEntry restored = h.removeFirst();
        ENTRIES.put(id, restored);
        return restored;
    }

    /** Undoes a stageUndo(): pushes the restored entry back onto the undo history and restores whatever ENTRIES had before the undo (null if it was new). */
    public static void discardStagedUndo(String id, ItemEntry restored, ItemEntry beforeUndoOrNull) {
        Deque<ItemEntry> h = HISTORY.computeIfAbsent(id, k -> new ArrayDeque<>());
        h.addFirst(restored);
        if (beforeUndoOrNull != null) ENTRIES.put(id, beforeUndoOrNull);
        else ENTRIES.remove(id);
    }

    /** Stages an enabled/disabled toggle (in-memory only, no script/state write) - see stageItem(). Returns false if there's no such entry to toggle. */
    public static boolean stageSetEnabled(String id, boolean enabled) {
        ItemEntry e = ENTRIES.get(id);
        if (e == null) return false;
        pushHistory(id, e);
        e.enabled = enabled;
        return true;
    }

    /** Undoes a stageSetEnabled(): restores the previous enabled value (mirrors discardStagedItem()'s "put the old state back" contract). */
    public static void discardStagedSetEnabled(String id, boolean previousEnabled) {
        ItemEntry e = ENTRIES.get(id);
        if (e != null) e.enabled = previousEnabled;
    }

    /** Writes the currently staged ENTRIES to disk (KubeJS script + KVE state). Only called from a PendingChange's onApply. */
    public static void commitItems() {
        writeScript();
        writeHideViewersScript();
        CraftEditorState.save();
    }

    private static void pushHistory(String id, ItemEntry snapshot) {
        int max = com.kve.common.config.KVEConfig.undoHistorySize;
        if (max <= 0) return;
        Deque<ItemEntry> h = HISTORY.computeIfAbsent(id, k -> new ArrayDeque<>());
        h.addFirst(snapshot.copy());
        while (h.size() > max) h.removeLast();
    }

    public static boolean canUndo(String id) {
        Deque<ItemEntry> h = HISTORY.get(id);
        return h != null && !h.isEmpty();
    }

    public static ItemEntry undo(String id) {
        Deque<ItemEntry> h = HISTORY.get(id);
        if (h == null || h.isEmpty()) return null;
        ItemEntry previous = h.removeFirst();
        ENTRIES.put(id, previous);
        writeScript();
        CraftEditorState.save();
        return previous;
    }

    public static void setEnabled(String id, boolean enabled) {
        ItemEntry e = ENTRIES.get(id);
        if (e == null) return;
        pushHistory(id, e);
        e.enabled = enabled;
        writeScript();
        CraftEditorState.save();
    }

    public static Collection<ItemEntry> getEntries()        { return Collections.unmodifiableCollection(ENTRIES.values()); }
    public static ItemEntry             getEntry(String id) { return ENTRIES.get(id); }

    public static void clearAll() { ENTRIES.clear(); APPLIED = Map.of(); HISTORY.clear(); }

    public static void loadEntry(ItemEntry e) { ENTRIES.put(e.itemId, e); }

    private static Path dir() { return KveScriptPaths.itemsStartupDir(); }

    private static Path clientDir() { return KveScriptPaths.CLIENT_ROOT.resolve("items"); }

    private static void writeScript() {
        markApplied();
        try { Files.createDirectories(dir()); } catch (IOException ex) { return; }

        StringBuilder sb = new StringBuilder();
        sb.append("// Shovel JS - Item Modifications (AUTO-GENERATED)\n");
        sb.append("// Apply with: RESTART the game/server (startup_scripts are not reloaded by /reload)\n\n");
        sb.append("ItemEvents.modification(event => {\n");
        sb.append("  const $UUID = Java.loadClass('java.util.UUID')\n\n");

        LAST_WRITE_WARNINGS.clear();
        for (ItemEntry e : ENTRIES.values()) {
            if (!e.enabled) {
                sb.append("  // DISABLED: ").append(e.itemId).append("\n\n");
                continue;
            }
            List<String> semantic = validateItem(e);
            if (!semantic.isEmpty()) {
                String reason = String.join("; ", semantic);
                sb.append("  // OMITIDO (fallo la validacion semantica): ").append(e.itemId)
                  .append(" - ").append(reason).append("\n\n");
                String warning = "Item '" + e.itemId + "' omitido: " + reason;
                LAST_WRITE_WARNINGS.add(warning);
                CraftEditorMod.LOGGER.warn("[Shovel JS] {}", warning);
                continue;
            }
            if (!e.category.isEmpty()) sb.append("  // category: ").append(e.category).append("\n");
            // KubeJS solo edita atributos de Sword/Digger/Armor/Trident (ver
            // KubeJsItemAttrSupport). Se sigue emitiendo el bloque (el
            // try/catch de appendItemEntry() ya lo aisla y las demas
            // propiedades del item si se aplican - los atributos van al
            // final del cuerpo) pero el jugador se entera por el warning en
            // vez de descubrirlo en un console.error.
            String unsupported = unsupportedAttributesWarning(e);
            if (unsupported != null) {
                sb.append("  // AVISO: ").append(unsupported).append("\n");
                LAST_WRITE_WARNINGS.add(unsupported);
                CraftEditorMod.LOGGER.warn("[Shovel JS] {}", unsupported);
            }
            appendItemEntry(sb, e, "  ");
            sb.append("\n");
        }

        sb.append("})\n");

        List<ItemEntry> hideCreative = new ArrayList<>();
        for (ItemEntry e : ENTRIES.values()) {
            if (e.enabled && e.hideFromCreativeTab) hideCreative.add(e);
        }
        if (!hideCreative.isEmpty()) {
            Map<String, List<ItemEntry>> byTab = new LinkedHashMap<>();
            for (ItemEntry e : hideCreative) {
                net.minecraft.resources.ResourceLocation tabId = com.kve.common.kubejs.CreativeTabIndex.tabIdFor(e.itemId);
                if (tabId == null) {
                    String warning = "No se pudo resolver la creative tab real de '" + e.itemId
                            + "' - hideFromCreativeTab omitido en este export (abrí el inventario "
                            + "creativo al menos una vez en esta sesión de juego y volvé a exportar).";
                    LAST_WRITE_WARNINGS.add(warning);
                    CraftEditorMod.LOGGER.warn("[Shovel JS] {}", warning);
                    continue;
                }
                byTab.computeIfAbsent(tabId.toString(), k -> new ArrayList<>()).add(e);
            }
            for (Map.Entry<String, List<ItemEntry>> tabGroup : byTab.entrySet()) {
                sb.append("\nStartupEvents.modifyCreativeTab('").append(tabGroup.getKey()).append("', event => {\n");
                for (ItemEntry e : tabGroup.getValue()) {
                    sb.append("  try { event.remove('").append(e.itemId)
                      .append("') } catch (ex) { console.error('[Shovel JS] creative tab remove failed for ")
                      .append(e.itemId).append(": ' + ex) }\n");
                }
                sb.append("})\n");
            }
        }

        ScriptWriteGuard.writeIfValid(KveScriptPaths.itemAttributesFile(), sb.toString(), "shovel_js_item_attributes");

        writeTooltipScript();
    }

    private static void writeTooltipScript() {
        try { Files.createDirectories(clientDir()); } catch (IOException ex) { return; }

        StringBuilder sb = new StringBuilder();
        sb.append("// Shovel JS - Item/Tool Tooltips\n");
        sb.append("// Los tooltips de items ahora se aplican directo en Java\n");
        sb.append("// (com.kve.common.itemrules.ItemTooltipEnforcer), no hace falta\n");
        sb.append("// recargar este archivo para que un cambio tenga efecto.\n");

        ScriptWriteGuard.writeIfValid(KveScriptPaths.itemTooltipsFileClient(), sb.toString(), "shovel_js_item_tooltips");
    }

    private static void writeHideViewersScript() {
        try { Files.createDirectories(clientDir()); } catch (IOException ex) { return; }

        List<String> toHide = new ArrayList<>();
        for (ItemEntry e : ENTRIES.values()) {
            if (e.enabled && e.hideFromRecipeViewers) toHide.add(e.itemId);
        }

        ScriptWriteGuard.writeIfValid(KveScriptPaths.itemHideViewersFileClient(), buildHideViewersScript(toHide), "shovel_js_item_hide_viewers");
    }

    /** Texto del script cliente de "ocultar de visores" para {@code itemIds} - separado de la escritura a disco para poder testearlo (ver {@link #writeHideViewersScript()} por el porque de cada linea). */
    static String buildHideViewersScript(List<String> itemIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Shovel JS - Hide from Recipe Viewers (AUTO-GENERATED)\n");
        sb.append("// JEI + REI only. EMI has no scripting hook for this in 1.20.1\n");
        sb.append("// (see KubeJS-Mods/KubeJS#942, open) - not implemented, not faked.\n");
        sb.append("// client_scripts: reinicia el cliente para aplicar (JEI/REI leen esto al armar su lista).\n\n");

        if (itemIds.isEmpty()) {
            sb.append("// (ningun item marcado para ocultar)\n");
            return sb.toString();
        }

        sb.append("if (typeof JEIEvents !== 'undefined') {\n");
        sb.append("  JEIEvents.hideItems(event => {\n");
        for (String id : itemIds) {
            sb.append("    try { event.hide('").append(id).append("') } catch (ex) { console.error('[Shovel JS] JEI hide failed for ")
              .append(id).append(": ' + ex) }\n");
        }
        sb.append("  })\n");
        sb.append("}\n\n");

        sb.append("if (typeof REIEvents !== 'undefined') {\n");
        sb.append("  REIEvents.hide('item', event => {\n");
        for (String id : itemIds) {
            sb.append("    try { event.hide('").append(id).append("') } catch (ex) { console.error('[Shovel JS] REI hide failed for ")
              .append(id).append(": ' + ex) }\n");
        }
        sb.append("  })\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Read-only preview of exactly what {@link #writeScript()} would emit
     * for a single item, without touching disk or the ENTRIES map. Used by
     * {@link com.kve.client.gui.ItemAttributeScreen}'s "Preview" button, the
     * same pattern {@code KubeJSExporter#previewRecipeJS} already
     * established for the recipe editor.
     */
    public static String previewItemJS(ItemEntry e) {
        if (e == null) return "// (sin datos de item)";
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("ItemEvents.modification(event => {\n");
            sb.append("  const $UUID = Java.loadClass('java.util.UUID')\n\n");
            String unsupported = unsupportedAttributesWarning(e);
            if (unsupported != null) sb.append("  // AVISO: ").append(unsupported).append("\n");
            appendItemEntry(sb, e, "  ");
            sb.append("\n})\n");
            if (e.hideFromCreativeTab) {
                net.minecraft.resources.ResourceLocation tabId = com.kve.common.kubejs.CreativeTabIndex.tabIdFor(e.itemId);
                if (tabId != null) {
                    sb.append("\nStartupEvents.modifyCreativeTab('").append(tabId).append("', event => {\n");
                    sb.append("  try { event.remove('").append(e.itemId).append("') } catch (ex) { ... }\n");
                    sb.append("})\n");
                } else {
                    sb.append("\n// hideFromCreativeTab: no se pudo resolver la tab real todavia\n");
                    sb.append("// (abrí el inventario creativo una vez en esta sesión y volvé a previsualizar)\n");
                }
            }
            if (e.hideFromRecipeViewers) {
                sb.append("\n// + kubejs/client_scripts/shovel_js/shovel_js_hide_viewers.js (JEI/REI):\n");
                sb.append("// JEIEvents.hideItems(event => { event.hide('").append(e.itemId).append("') })\n");
                sb.append("// REIEvents.hide('item', event => { event.hide('").append(e.itemId).append("') })\n");
                sb.append("// (EMI no soportado en 1.20.1, ver writeHideViewersScript())\n");
            }
            return sb.toString();
        } catch (Exception ex) {
            return "// ERROR generando preview: " + ex.getMessage();
        }
    }

    private static void appendItemEntry(StringBuilder sb, ItemEntry e, String indent) {
        sb.append(indent).append("event.modify('").append(e.itemId).append("', item => {\n");
        sb.append(indent).append("  try {\n");

        String body = indent + "  ";
        appendItemBody(sb, e, body);

        sb.append(indent).append("  } catch (ex) {\n");
        sb.append(indent).append("    console.error('[Shovel JS] item modify failed for ")
          .append(e.itemId).append(": ' + ex)\n");
        sb.append(indent).append("  }\n");
        sb.append(indent).append("})\n");
    }

    private static void appendItemBody(StringBuilder sb, ItemEntry e, String indent) {

        if (e.maxDamage > 0)
            sb.append(indent).append("  item.maxDamage = ").append(e.maxDamage).append("\n");
        if (e.maxStackSize > 0)
            sb.append(indent).append("  item.maxStackSize = ").append(e.maxStackSize).append("\n");
        if (e.fireResistantSet)
            sb.append(indent).append("  item.fireResistant = ").append(e.fireResistant).append("\n");
        if (!e.rarity.isEmpty())
            sb.append(indent).append("  item.rarity = '").append(e.rarity).append("'\n");
        if (e.burnTime > 0)
            sb.append(indent).append("  item.burnTime = ").append(e.burnTime).append("\n");

        if (e.food != null) appendFoodBlock(sb, e.food, indent);

        if (hasAttributeEdits(e) && !KubeJsItemAttrSupport.supportsAttributeEdits(e.itemId)) {
            sb.append(indent).append("  // atributos aplicados por Shovel JS en runtime (KubeJS no puede editar este item)\n");
            return;
        }
        int counter = 0;
        for (AttrMod m : effectiveAttributeMods(e)) {
            counter = addAttr(sb, indent, m.attrId, m.value, m.op, counter, e.isAddMode(m.attrId));
        }
        for (String removed : effectiveRemovedAttributes(e)) {
            appendRemoveExisting(sb, indent, removed);
        }
    }

    /**
     * Los modifiers que esta entry aplica, en el orden en que se emiten: los
     * 5 core (si tienen valor y no hay un extra manual con el mismo id que
     * los pise) y despues los extras. Fuente unica para el script generado
     * ({@link #appendItemBody()}) y para {@code ItemAttributeEnforcer}, que
     * aplica exactamente lo mismo en runtime via Forge - ver su javadoc.
     */
    public static List<AttrMod> effectiveAttributeMods(ItemEntry e) {
        List<AttrMod> out = new ArrayList<>();
        addCore(out, e, "minecraft:generic.attack_damage", e.attackDamage, e.attackDamageOp);
        addCore(out, e, "minecraft:generic.attack_speed", e.attackSpeed, e.attackSpeedOp);
        addCore(out, e, "minecraft:generic.armor", e.armor, e.armorOp);
        addCore(out, e, "minecraft:generic.armor_toughness", e.armorToughness, e.armorToughnessOp);
        addCore(out, e, "minecraft:generic.movement_speed", e.movementSpeed, e.movementSpeedOp);
        for (AttrMod m : e.attributes.values()) {
            if (m.attrId == null || m.attrId.isEmpty()) continue;
            out.add(m);
        }
        return out;
    }

    private static void addCore(List<AttrMod> out, ItemEntry e, String attrId, double value, Op op) {
        if (!Double.isNaN(value) && !e.attributes.containsKey(attrId)) out.add(new AttrMod(attrId, value, op));
    }

    /** Atributos por defecto que esta entry borra sin reemplazar (ver {@link ItemEntry#removedAttributes}). */
    public static List<String> effectiveRemovedAttributes(ItemEntry e) {
        List<String> out = new ArrayList<>();
        for (String removed : e.removedAttributes) {
            if (removed == null || removed.isEmpty()) continue;
            if (e.attributes.containsKey(removed) || isCoreAttrEmitted(e, removed)) continue;
            out.add(removed);
        }
        return out;
    }

    private static boolean isCoreAttrEmitted(ItemEntry e, String attrId) {
        return switch (attrId) {
            case "minecraft:generic.attack_damage"   -> !Double.isNaN(e.attackDamage);
            case "minecraft:generic.attack_speed"    -> !Double.isNaN(e.attackSpeed);
            case "minecraft:generic.armor"           -> !Double.isNaN(e.armor);
            case "minecraft:generic.armor_toughness" -> !Double.isNaN(e.armorToughness);
            case "minecraft:generic.movement_speed"  -> !Double.isNaN(e.movementSpeed);
            default -> false;
        };
    }


    private static void appendFoodBlock(StringBuilder sb, FoodProps food, String indent) {
        sb.append(indent).append("  item.setFoodProperties(food => {\n");
        sb.append(indent).append("    food.hunger(").append(food.nutrition).append(")\n");
        sb.append(indent).append("    food.saturation(").append(KveNumbers.padded4(food.saturationMod)).append(")\n");
        if (food.alwaysEdible) sb.append(indent).append("    food.alwaysEdible()\n");
        if (food.fastFood)     sb.append(indent).append("    food.fastToEat()\n");
        if (food.meat)         sb.append(indent).append("    food.meat()\n");
        for (FoodEffect fx : food.effects) {
            if (fx.effectId == null || fx.effectId.isEmpty()) continue;
            sb.append(indent).append("    food.effect('").append(fx.effectId).append("', ")
              .append(fx.durationTicks).append(", ").append(fx.amplifier).append(", ")
              .append(KveNumbers.padded4(fx.chance)).append(")\n");
        }
        sb.append(indent).append("  })\n");
    }

    public static final String BASE_ATTACK_DAMAGE_UUID = "CB3F55D3-645C-4F38-A497-9C13A33DB5CF";
    public static final String BASE_ATTACK_SPEED_UUID  = "FA233E1C-4180-4865-B01B-BCCE9785ACA3";

    private static int addAttr(StringBuilder sb, String indent, String attr, double value, Op op, int counter, boolean addMode) {
        String uuidExpr;
        if (addMode) {
            uuidExpr = "$UUID.randomUUID().toString()";
        } else if (attr.equals("minecraft:generic.attack_damage")) {
            uuidExpr = "'" + BASE_ATTACK_DAMAGE_UUID + "'";
        } else if (attr.equals("minecraft:generic.attack_speed")) {
            uuidExpr = "'" + BASE_ATTACK_SPEED_UUID + "'";
        } else {
            uuidExpr = "$UUID.randomUUID().toString()";
        }
        if (!addMode) appendRemoveExisting(sb, indent, attr);
        sb.append(indent).append("  item.addAttribute(\n");
        sb.append(indent).append("    '").append(attr).append("',\n");
        sb.append(indent).append("    ").append(uuidExpr).append(",\n");
        sb.append(indent).append("    'shovel_js_attr_").append(counter).append("',\n");
        sb.append(indent).append("    ").append(KveNumbers.padded4(value)).append(",\n");
        sb.append(indent).append("    '").append(op.js).append("'\n");
        sb.append(indent).append("  )\n");
        return counter + 1;
    }

    private static void appendRemoveExisting(StringBuilder sb, String indent, String attr) {
        sb.append(indent).append("  item.getAttributes('").append(attr)
          .append("').forEach(a => item.removeAttribute('").append(attr).append("', a.id))\n");
    }

    /** True si esta entry toca atributos (los 5 core, los extras, o borra alguno por defecto) - lo que {@link KubeJsItemAttrSupport} decide si KubeJS puede aplicar. */
    public static boolean hasAttributeEdits(ItemEntry e) {
        if (e == null) return false;
        return !Double.isNaN(e.attackDamage) || !Double.isNaN(e.attackSpeed) || !Double.isNaN(e.armor)
                || !Double.isNaN(e.armorToughness) || !Double.isNaN(e.movementSpeed)
                || !e.attributes.isEmpty() || !e.removedAttributes.isEmpty();
    }

    /**
     * Aviso (o null) de que los atributos de esta entry no se van a aplicar.
     * Desde siempre null: {@code ItemAttributeEnforcer} aplica los
     * atributos en runtime a CUALQUIER item (via ItemAttributeModifierEvent),
     * no solo a las clases que KubeJS sabe editar - ver
     * {@link KubeJsItemAttrSupport}. Se conserva la firma para los callers.
     */
    public static String unsupportedAttributesWarning(ItemEntry e) {
        return null;
    }
}
