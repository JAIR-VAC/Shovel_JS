package com.kve.common.itemrules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kve.CraftEditorMod;
import com.kve.common.kubejs.KubeJSItemAttrExporter;
import com.kve.common.kubejs.KubeJSItemAttrExporter.SlotAttrMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Atributos de items puestos en un slot de Curios o Accessories
 * (anillos, collares, amuletos...). Mismo criterio que
 * {@link ItemAttributeEnforcer}: se aplican en runtime leyendo
 * {@link KubeJSItemAttrExporter#getEntry()} - cubre tanto el componente
 * {@code curios:attribute_modifiers}/{@code accessories:attributes} como los
 * modifiers que el mod arma por codigo.
 * <p>
 * Ninguno de los dos mods es dependencia de compilacion, asi que los ganchos
 * se registran por reflexion y solo si el mod esta cargado (formatos y firmas
 * verificados contra curios-neoforge 9.5.1+1.21.1 y accessories-neoforge
 * 1.1.0-beta.53+1.21.1):
 * <ul>
 *   <li>Curios: {@code CurioAttributeModifierEvent} en el bus de NeoForge
 *       ({@code getSlotContext().identifier()}, {@code removeAttribute},
 *       {@code addModifier}).</li>
 *   <li>Accessories: {@code AdjustAttributeModifierCallback.EVENT}
 *       ({@code slotReference.slotName()}, {@code AccessoryAttributeBuilder}).</li>
 * </ul>
 */
public final class CurioAttributeEnforcer {

    private CurioAttributeEnforcer() {}

    public static final String CURIOS_COMPONENT = "curios:attribute_modifiers";
    public static final String ACCESSORIES_COMPONENT = "accessories:attributes";

    /** True si hay Curios o Accessories cargado - la UI solo ofrece el editor en ese caso. */
    public static boolean available() {
        try {
            ModList ml = ModList.get();
            return ml != null && (ml.isLoaded("curios") || ml.isLoaded("accessories"));
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

    /** Registra los ganchos de los mods presentes. Llamar una vez al iniciar el mod. */
    public static void register() {
        ModList ml = ModList.get();
        if (ml == null) return;
        if (ml.isLoaded("curios")) {
            try { registerCurios(); }
            catch (Throwable t) { CraftEditorMod.LOGGER.warn("[Shovel JS] Curios attribute hook not registered: {}", t.toString()); }
        }
        if (ml.isLoaded("accessories")) {
            try { registerAccessories(); }
            catch (Throwable t) { CraftEditorMod.LOGGER.warn("[Shovel JS] Accessories attribute hook not registered: {}", t.toString()); }
        }
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

    static ResourceLocation idFor(String itemId, String attrId, String slotId, int index) {
        UUID u = UUID.nameUUIDFromBytes(("shovel_js-curio:" + itemId + ":" + attrId + ":" + slotId + ":" + index).getBytes(StandardCharsets.UTF_8));
        return ResourceLocation.fromNamespaceAndPath("shovel_js", "curio_attr/" + u);
    }

    private static KubeJSItemAttrExporter.ItemEntry entryFor(ItemStack stack) {
        if (stack == null || stack.isEmpty() || ItemTooltipEnforcer.isPreview(stack)) return null;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        KubeJSItemAttrExporter.ItemEntry e = KubeJSItemAttrExporter.getAppliedEntry(itemId.toString());
        return e != null && e.enabled && e.hasCurioEdits() ? e : null;
    }

    private static Method curioGetStack, curioGetSlotContext, curioRemoveAttr, curioAddModifier;
    private static Method slotCtxIdentifier, slotCtxIndex;

    @SuppressWarnings("unchecked")
    private static void registerCurios() throws Exception {
        Class<?> evCls = Class.forName("top.theillusivec4.curios.api.event.CurioAttributeModifierEvent");
        Class<?> ctxCls = Class.forName("top.theillusivec4.curios.api.SlotContext");
        curioGetStack       = evCls.getMethod("getItemStack");
        curioGetSlotContext = evCls.getMethod("getSlotContext");
        curioRemoveAttr     = evCls.getMethod("removeAttribute", Holder.class);
        curioAddModifier    = evCls.getMethod("addModifier", Holder.class, AttributeModifier.class);
        slotCtxIdentifier   = ctxCls.getMethod("identifier");
        slotCtxIndex        = ctxCls.getMethod("index");
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, (Class<Event>) evCls, CurioAttributeEnforcer::onCurioEvent);
        CraftEditorMod.LOGGER.info("[Shovel JS] Curios attribute hook registered");
    }

    private static final ThreadLocal<Boolean> READING_DEFAULTS = ThreadLocal.withInitial(() -> false);

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
                Holder<Attribute> attr = ItemAttributeEnforcer.attribute(attrId);
                if (attr != null) curioRemoveAttr.invoke(event, attr);
            }
            for (SlotAttrMod m : modsForSlot(e, slotId)) {
                Holder<Attribute> attr = ItemAttributeEnforcer.attribute(m.attrId);
                if (attr == null) continue;
                curioAddModifier.invoke(event, attr,
                        new AttributeModifier(idFor(e.itemId, m.attrId, slotId, index), m.value, m.op.vanilla()));
            }
        } catch (Throwable t) {
            CraftEditorMod.LOGGER.debug("[Shovel JS] Curios attribute hook failed: {}", t.toString());
        }
    }

    private static Method accSlotName, accStacked, accExclusive, accAddStackable;

    private static void registerAccessories() throws Exception {
        Class<?> cbCls = Class.forName("io.wispforest.accessories.api.events.AdjustAttributeModifierCallback");
        Class<?> refCls = Class.forName("io.wispforest.accessories.api.slot.SlotReference");
        Class<?> builderCls = Class.forName("io.wispforest.accessories.api.attributes.AccessoryAttributeBuilder");
        accSlotName     = refCls.getMethod("slotName");
        accStacked      = builderCls.getMethod("stackedAttributes");
        accExclusive    = builderCls.getMethod("exclusiveAttributes");
        accAddStackable = builderCls.getMethod("addStackable", Holder.class, AttributeModifier.class);

        java.lang.reflect.Field evField = cbCls.getField("EVENT");
        Object event = evField.get(null);
        Object listener = Proxy.newProxyInstance(cbCls.getClassLoader(), new Class<?>[]{cbCls}, (proxy, m, args) -> {
            switch (m.getName()) {
                case "adjustAttributes" -> { onAccessory(args[0], args[1], args[2]); return null; }
                case "hashCode" -> { return System.identityHashCode(proxy); }
                case "equals" -> { return proxy == args[0]; }
                case "toString" -> { return "KveCurioAttributeListener"; }
                default -> { return null; }
            }
        });
        evField.getType().getMethod("register", Object.class).invoke(event, listener);
        CraftEditorMod.LOGGER.info("[Shovel JS] Accessories attribute hook registered");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onAccessory(Object stackObj, Object slotRef, Object builder) {
        try {
            KubeJSItemAttrExporter.ItemEntry e = entryFor((ItemStack) stackObj);
            if (e == null) return;
            String slotId = String.valueOf(accSlotName.invoke(slotRef));
            Object stacked = accStacked.invoke(builder);
            Object exclusive = accExclusive.invoke(builder);
            for (String attrId : attrsToClear(e, slotId)) {
                Holder<Attribute> attr = ItemAttributeEnforcer.attribute(attrId);
                if (attr == null) continue;
                if (stacked instanceof com.google.common.collect.Multimap mm) mm.removeAll(attr);
                if (exclusive instanceof Map map) map.remove(attr);
            }
            for (SlotAttrMod m : modsForSlot(e, slotId)) {
                Holder<Attribute> attr = ItemAttributeEnforcer.attribute(m.attrId);
                if (attr == null) continue;
                accAddStackable.invoke(builder, attr,
                        new AttributeModifier(idFor(e.itemId, m.attrId, slotId, 0), m.value, m.op.vanilla()));
            }
        } catch (Throwable t) {
            CraftEditorMod.LOGGER.debug("[Shovel JS] Accessories attribute hook failed: {}", t.toString());
        }
    }

    /**
     * Modifiers de curio/accesorio que el item trae por defecto en sus
     * componentes ({@value #CURIOS_COMPONENT} / {@value #ACCESSORIES_COMPONENT}).
     * Los que el mod arma por codigo no se ven aca (no hay forma de pedirlos
     * sin una entidad equipada) - igual se pueden pisar agregando el atributo.
     */
    public static List<SlotAttrMod> defaultModifiers(Item item) {
        List<SlotAttrMod> out = new ArrayList<>();
        if (item == null) return out;
        readComponent(item, CURIOS_COMPONENT, "slot", out);
        readComponent(item, ACCESSORIES_COMPONENT, "slot_name", out);
        readCuriosApi(item, out);
        return collapseIdenticalSlots(out);
    }

    @SuppressWarnings("unchecked")
    private static void readCuriosApi(Item item, List<SlotAttrMod> out) {
        try {
            if (!ModList.get().isLoaded("curios")) return;
        } catch (Throwable t) { return; }
        Set<String> seen = new LinkedHashSet<>();
        for (SlotAttrMod m : out) seen.add(m.key());
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Class<?> ctxCls = Class.forName("top.theillusivec4.curios.api.SlotContext");
            Method getSlots = api.getMethod("getItemStackSlots", ItemStack.class, boolean.class);
            Method getSlotsFor = api.getMethod("getItemStackSlots", ItemStack.class, net.minecraft.world.entity.LivingEntity.class);
            Method getMods = api.getMethod("getAttributeModifiers", ctxCls, ResourceLocation.class, ItemStack.class);
            java.lang.reflect.Constructor<?> newCtx = ctxCls.getConstructor(String.class, net.minecraft.world.entity.LivingEntity.class, int.class, boolean.class, boolean.class);
            ItemStack stack = new ItemStack(item);
            net.minecraft.world.entity.LivingEntity wearer = localPlayer();
            Map<String, ?> slots = (Map<String, ?>) (wearer != null ? getSlotsFor.invoke(null, stack, wearer) : getSlots.invoke(null, stack, true));
            if (slots.isEmpty()) slots = (Map<String, ?>) getSlots.invoke(null, stack, true);
            READING_DEFAULTS.set(true);
            try {
                for (String slotId : slots.keySet()) {
                    try {
                        Object ctx = newCtx.newInstance(slotId, wearer, 0, false, true);
                        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("shovel_js", "curio_default/" + slotId.replace(':', '_'));
                        com.google.common.collect.Multimap<Holder<Attribute>, AttributeModifier> mods =
                                (com.google.common.collect.Multimap<Holder<Attribute>, AttributeModifier>) getMods.invoke(null, ctx, id, stack);
                        if (mods == null) continue;
                        for (Map.Entry<Holder<Attribute>, AttributeModifier> en : mods.entries()) {
                            ResourceLocation rl = en.getKey().unwrapKey().map(k -> k.location()).orElse(null);
                            if (rl == null) continue;
                            AttributeModifier mod = en.getValue();
                            SlotAttrMod m = new SlotAttrMod(rl.toString(), mod.amount(), KubeJSItemAttrExporter.Op.fromVanilla(mod.operation()), slotId);
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
    }

    private static net.minecraft.world.entity.LivingEntity localPlayer() {
        try {
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft");
            Object inst = mc.getMethod("getInstance").invoke(null);
            Object player = inst.getClass().getField("player").get(inst);
            return player instanceof net.minecraft.world.entity.LivingEntity le ? le : null;
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

    @SuppressWarnings("unchecked")
    private static void readComponent(Item item, String componentId, String slotField, List<SlotAttrMod> out) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(componentId);
            if (rl == null) return;
            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(rl);
            if (type == null || type.codec() == null) return;
            Object value = item.components().get(type);
            if (value == null) return;
            JsonElement json = ((Codec<Object>) type.codec()).encodeStart(JsonOps.INSTANCE, value).result().orElse(null);
            out.addAll(parseModifiers(json, slotField));
        } catch (Throwable t) {
            CraftEditorMod.LOGGER.debug("[Shovel JS] Could not read {} of {}: {}", componentId, item, t.toString());
        }
    }

    /** {@code {modifiers:[{type, id, amount, operation, <slotField>}]}} -> lista. Separado para poder testearlo. */
    public static List<SlotAttrMod> parseModifiers(JsonElement json, String slotField) {
        List<SlotAttrMod> out = new ArrayList<>();
        if (json == null || !json.isJsonObject()) return out;
        JsonObject root = json.getAsJsonObject();
        if (!root.has("modifiers") || !root.get("modifiers").isJsonArray()) return out;
        JsonArray mods = root.getAsJsonArray("modifiers");
        Set<String> seen = new LinkedHashSet<>();
        for (JsonElement el : mods) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String attr = o.has("type") ? o.get("type").getAsString() : "";
            if (attr.isEmpty() || !o.has("amount")) continue;
            ResourceLocation attrRl = ResourceLocation.tryParse(attr);
            if (attrRl == null) continue;
            String slot = o.has(slotField) ? o.get(slotField).getAsString() : "";
            SlotAttrMod m = new SlotAttrMod(attrRl.toString(), o.get("amount").getAsDouble(),
                    opFromSerialized(o.has("operation") ? o.get("operation").getAsString() : ""), slot);
            if (seen.add(m.key())) out.add(m);
        }
        return out;
    }

    static KubeJSItemAttrExporter.Op opFromSerialized(String s) {
        for (AttributeModifier.Operation op : AttributeModifier.Operation.values()) {
            if (op.getSerializedName().equalsIgnoreCase(s)) return KubeJSItemAttrExporter.Op.fromVanilla(op);
        }
        return KubeJSItemAttrExporter.Op.ADDITION;
    }
}
