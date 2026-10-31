package com.kve.common.itemrules;

import com.google.common.collect.Multimap;
import com.kve.CraftEditorMod;
import com.kve.common.kubejs.KubeJSItemAttrExporter;
import com.kve.common.kubejs.KubeJSItemAttrExporter.SlotAttrMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Port a 1.20.1 del editor de atributos de Curios de 1.21.1
 * (ver el CurioAttributeEnforcer de esa version). Atributos de items puestos
 * en un slot de Curios (anillos, collares, amuletos...), aplicados en runtime
 * leyendo {@link KubeJSItemAttrExporter#getEntry()}.
 * <p>
 * Curios no es dependencia de compilacion: el gancho se registra por
 * reflexion y solo si el mod esta cargado. Firmas verificadas contra
 * curios-forge 5.14.1+1.20.1: {@code CurioAttributeModifierEvent} en el bus
 * de Forge ({@code getSlotContext().identifier()},
 * {@code removeAttribute(Attribute)}, {@code addModifier(Attribute, AttributeModifier)}).
 * Accessories no se soporta en 1.20.1.
 */
public final class CurioAttributeEnforcer {

    private CurioAttributeEnforcer() {}

    /** True si Curios esta cargado - la UI solo ofrece el editor en ese caso. */
    public static boolean available() {
        try {
            ModList ml = ModList.get();
            return ml != null && ml.isLoaded("curios");
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * True si {@code item} se puede poner en algun slot de Curios (anillo,
     * collar...). La usa el editor de items para mostrar "Atributos de
     * Curios" a la vista en vez de dentro de "Mas opciones". Por reflexion,
     * como el resto de esta clase; false si Curios no esta o algo falla.
     */
    @SuppressWarnings("unchecked")
    public static boolean isCurio(Item item) {
        if (item == null || !available()) return false;
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Map<String, ?> slots = (Map<String, ?>) api.getMethod("getItemStackSlots", ItemStack.class, boolean.class)
                    .invoke(null, new ItemStack(item), true);
            return slots != null && !slots.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Registra el gancho de Curios si esta presente. Llamar una vez al iniciar el mod. */
    public static void register() {
        if (!available()) return;
        try { registerCurios(); }
        catch (Throwable t) { CraftEditorMod.LOGGER.warn("[Shovel JS] Curios attribute hook not registered: {}", t.toString()); }
    }

    /** Modifiers de la entry que aplican a {@code slotId} (slot vacio en la entry = cualquier slot). */
    public static List<SlotAttrMod> modsForSlot(KubeJSItemAttrExporter.ItemEntry e, String slotId) {
        List<SlotAttrMod> out = new ArrayList<>();
        for (SlotAttrMod m : e.curioAttributes) {
            if (m.attrId == null || m.attrId.isEmpty()) continue;
            if (slotMatches(m.slot, slotId)) out.add(m);
        }
        return out;
    }

    /**
     * Atributos cuyos modifiers originales hay que borrar en {@code slotId}
     * antes de agregar los de la entry: los que la entry reemplaza y los que
     * el jugador borro (ver {@link KubeJSItemAttrExporter.ItemEntry#curioRemoved}).
     */
    public static Set<String> attrsToClear(KubeJSItemAttrExporter.ItemEntry e, String slotId) {
        Set<String> out = new LinkedHashSet<>();
        for (SlotAttrMod m : modsForSlot(e, slotId)) out.add(m.attrId);
        for (String key : e.curioRemoved) {
            int bar = key.indexOf('|');
            if (bar < 0) continue;
            if (slotMatches(key.substring(0, bar), slotId)) out.add(key.substring(bar + 1));
        }
        return out;
    }

    static boolean slotMatches(String entrySlot, String slotId) {
        return entrySlot == null || entrySlot.isEmpty() || entrySlot.equals(slotId);
    }

    static UUID uuidFor(String itemId, String attrId, String slotId, int index) {
        return UUID.nameUUIDFromBytes(("shovel_js-curio:" + itemId + ":" + attrId + ":" + slotId + ":" + index).getBytes(StandardCharsets.UTF_8));
    }

    private static KubeJSItemAttrExporter.ItemEntry entryFor(ItemStack stack) {
        if (stack == null || stack.isEmpty() || ItemTooltipEnforcer.isPreview(stack)) return null;
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) return null;
        KubeJSItemAttrExporter.ItemEntry e = KubeJSItemAttrExporter.getAppliedEntry(itemId.toString());
        return e != null && e.enabled && e.hasCurioEdits() ? e : null;
    }

    private static Method curioGetStack, curioGetSlotContext, curioRemoveAttr, curioAddModifier;
    private static Method slotCtxIdentifier, slotCtxIndex;

    private static final ThreadLocal<Boolean> READING_DEFAULTS = ThreadLocal.withInitial(() -> false);

    @SuppressWarnings("unchecked")
    private static void registerCurios() throws Exception {
        Class<?> evCls = Class.forName("top.theillusivec4.curios.api.event.CurioAttributeModifierEvent");
        Class<?> ctxCls = Class.forName("top.theillusivec4.curios.api.SlotContext");
        curioGetStack       = evCls.getMethod("getItemStack");
        curioGetSlotContext = evCls.getMethod("getSlotContext");
        curioRemoveAttr     = evCls.getMethod("removeAttribute", Attribute.class);
        curioAddModifier    = evCls.getMethod("addModifier", Attribute.class, AttributeModifier.class);
        slotCtxIdentifier   = ctxCls.getMethod("identifier");
        slotCtxIndex        = ctxCls.getMethod("index");
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, (Class<Event>) evCls, CurioAttributeEnforcer::onCurioEvent);
        CraftEditorMod.LOGGER.info("[Shovel JS] Curios attribute hook registered");
    }

    private static void onCurioEvent(Event event) {
        if (READING_DEFAULTS.get()) return;
        try {
            ItemStack stack = (ItemStack) curioGetStack.invoke(event);
            KubeJSItemAttrExporter.ItemEntry e = entryFor(stack);
            if (e == null) return;
            Object ctx = curioGetSlotContext.invoke(event);
            String slotId = String.valueOf(slotCtxIdentifier.invoke(ctx));
            int index = (Integer) slotCtxIndex.invoke(ctx);
            for (String attrId : attrsToClear(e, slotId)) {
                Attribute attr = ItemAttributeEnforcer.attribute(attrId);
                if (attr != null) curioRemoveAttr.invoke(event, attr);
            }
            for (SlotAttrMod m : modsForSlot(e, slotId)) {
                Attribute attr = ItemAttributeEnforcer.attribute(m.attrId);
                if (attr == null) continue;
                curioAddModifier.invoke(event, attr, new AttributeModifier(
                        uuidFor(e.itemId, m.attrId, slotId, index), "kve curio " + m.attrId, m.value, vanillaOp(m.op)));
            }
        } catch (Throwable t) {
            CraftEditorMod.LOGGER.debug("[Shovel JS] Curios attribute hook failed: {}", t.toString());
        }
    }

    static AttributeModifier.Operation vanillaOp(KubeJSItemAttrExporter.Op op) {
        return switch (op) {
            case ADDITION -> AttributeModifier.Operation.ADDITION;
            case MULTIPLY_BASE -> AttributeModifier.Operation.MULTIPLY_BASE;
            case MULTIPLY_TOTAL -> AttributeModifier.Operation.MULTIPLY_TOTAL;
        };
    }

    static KubeJSItemAttrExporter.Op fromVanilla(AttributeModifier.Operation op) {
        return switch (op) {
            case ADDITION -> KubeJSItemAttrExporter.Op.ADDITION;
            case MULTIPLY_BASE -> KubeJSItemAttrExporter.Op.MULTIPLY_BASE;
            case MULTIPLY_TOTAL -> KubeJSItemAttrExporter.Op.MULTIPLY_TOTAL;
        };
    }

    private static LivingEntity localPlayer() {
        try {
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft");
            Object inst = mc.getMethod("getInstance").invoke(null);
            Object player = inst.getClass().getField("player").get(inst);
            return player instanceof LivingEntity le ? le : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static final Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void warnOnce(Item item, String slotId, Throwable t) {
        if (WARNED.add(item + "|" + slotId)) {
            Throwable root = t instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : t;
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not read curio attributes of {} in slot {}: {}", item, slotId, root.toString());
        }
    }

    @SuppressWarnings("unchecked")
    public static List<SlotAttrMod> defaultModifiers(Item item) {
        List<SlotAttrMod> out = new ArrayList<>();
        if (item == null || !available()) return out;
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Class<?> ctxCls = Class.forName("top.theillusivec4.curios.api.SlotContext");
            Method getSlots = api.getMethod("getItemStackSlots", ItemStack.class, boolean.class);
            Method getSlotsFor = api.getMethod("getItemStackSlots", ItemStack.class, LivingEntity.class);
            Method getMods = api.getMethod("getAttributeModifiers", ctxCls, UUID.class, ItemStack.class);
            Constructor<?> newCtx = ctxCls.getConstructor(String.class, LivingEntity.class, int.class, boolean.class, boolean.class);
            ItemStack stack = new ItemStack(item);
            LivingEntity wearer = localPlayer();
            Map<String, ?> slots = (Map<String, ?>) (wearer != null
                    ? getSlotsFor.invoke(null, stack, wearer)
                    : getSlots.invoke(null, stack, true));
            if (slots.isEmpty()) slots = (Map<String, ?>) getSlots.invoke(null, stack, true);
            Set<String> seen = new LinkedHashSet<>();
            READING_DEFAULTS.set(true);
            try {
                for (String slotId : slots.keySet()) {
                    try {
                        Object ctx = newCtx.newInstance(slotId, wearer, 0, false, true);
                        UUID uuid = UUID.nameUUIDFromBytes(("shovel_js-curio-default:" + slotId).getBytes(StandardCharsets.UTF_8));
                        Multimap<Attribute, AttributeModifier> mods = (Multimap<Attribute, AttributeModifier>) getMods.invoke(null, ctx, uuid, stack);
                        if (mods == null) continue;
                        for (Map.Entry<Attribute, AttributeModifier> en : mods.entries()) {
                            ResourceLocation rl = ForgeRegistries.ATTRIBUTES.getKey(en.getKey());
                            if (rl == null) continue;
                            AttributeModifier mod = en.getValue();
                            SlotAttrMod m = new SlotAttrMod(rl.toString(), mod.getAmount(), fromVanilla(mod.getOperation()), slotId);
                            if (seen.add(m.key())) out.add(m);
                        }
                    } catch (Throwable t) {
                        warnOnce(item, slotId, t);
                    }
                }
            } finally {
                READING_DEFAULTS.set(false);
            }
        } catch (Throwable t) {
            CraftEditorMod.LOGGER.debug("[Shovel JS] Could not read curio defaults of {}: {}", item, t.toString());
        }
        return collapseIdenticalSlots(out);
    }

    static List<SlotAttrMod> collapseIdenticalSlots(List<SlotAttrMod> mods) {
        java.util.Map<String, Set<String>> bySlot = new java.util.LinkedHashMap<>();
        for (SlotAttrMod m : mods) bySlot.computeIfAbsent(m.slot, k -> new java.util.TreeSet<>()).add(m.attrId + "|" + m.value + "|" + m.op);
        if (bySlot.size() < 2 || bySlot.containsKey("")) return mods;
        Set<String> first = bySlot.values().iterator().next();
        for (Set<String> other : bySlot.values()) if (!other.equals(first)) return mods;
        String firstSlot = bySlot.keySet().iterator().next();
        List<SlotAttrMod> out = new ArrayList<>();
        for (SlotAttrMod m : mods) if (m.slot.equals(firstSlot)) out.add(new SlotAttrMod(m.attrId, m.value, m.op, ""));
        return out;
    }
}
