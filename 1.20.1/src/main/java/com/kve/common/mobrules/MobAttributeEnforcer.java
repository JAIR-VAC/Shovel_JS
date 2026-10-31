package com.kve.common.mobrules;

import com.kve.CraftEditorMod;
import com.kve.common.kubejs.KubeJSItemAttrExporter;
import com.kve.common.kubejs.KubeJSMobExporter;
import com.kve.mixin.AttributeMapAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MobAttributeEnforcer {

    private MobAttributeEnforcer() {}

    private static final String NBT_APPLIED = "shovel_js_mob_spawn_profile_applied";

    private static final int REINFORCE_INTERVAL_TICKS = 20;

    /** One-shot warning sets, same reasoning as {@code AttackEffectEnforcer#WARNED_BAD_EFFECT_IDS} - a bad id logs once per server run, not once per spawn. */
    private static final Set<String> WARNED_DUPLICATE_ATTRS = new HashSet<>();
    private static final Set<String> WARNED_BAD_ITEM_IDS    = new HashSet<>();
    private static final Set<String> WARNED_BAD_EFFECT_IDS  = new HashSet<>();
    private static final Set<String> WARNED_UNSUPPORTED_ATTRS = new HashSet<>();

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof LivingEntity living) || living instanceof Player) return;

        CompoundTag data = living.getPersistentData();
        if (data.getBoolean(NBT_APPLIED)) return;
        data.putBoolean(NBT_APPLIED, true);

        String typeId = MobTypeIds.idOf(living);
        if (typeId == null) return;
        KubeJSMobExporter.MobEntry e = KubeJSMobExporter.getAppliedEntry(typeId);
        if (e == null || !e.enabled) return;
        if (!KubeJSMobExporter.validateMob(e).isEmpty()) return;

        try {
            if (applyAttributeValues(living, e, false)) living.setHealth(living.getMaxHealth());
        }
        catch (Exception ex) { CraftEditorMod.LOGGER.error("[Shovel JS] mob attributes fallaron para {}: {}", typeId, ex.toString()); }

        try { applyCustomName(living, e); }
        catch (Exception ex) { CraftEditorMod.LOGGER.error("[Shovel JS] mob custom name fallo para {}: {}", typeId, ex.toString()); }

        try { applyEquipment(living, e); }
        catch (Exception ex) { CraftEditorMod.LOGGER.error("[Shovel JS] mob equipment fallo para {}: {}", typeId, ex.toString()); }

        if (living instanceof Mob mob) {
            try { applyStaticBehaviorFlags(mob, e); }
            catch (Exception ex) { CraftEditorMod.LOGGER.error("[Shovel JS] mob behavior flags fallaron para {}: {}", typeId, ex.toString()); }
        }

        try { applyMobEffects(living, e); }
        catch (Exception ex) { CraftEditorMod.LOGGER.error("[Shovel JS] mob effects fallaron para {}: {}", typeId, ex.toString()); }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!KubeJSMobExporter.hasAppliedEntries()) return;

        LivingEntity living = event.getEntity();
        if (living.tickCount % REINFORCE_INTERVAL_TICKS != 0) return;
        if (living instanceof Player) return;
        if (living.level().isClientSide()) return;
        KubeJSMobExporter.MobEntry e = MobTypeIds.entryFor(living);
        if (e == null) return;

        // Solo entidades por las que onJoin() ya pasó alguna vez - evita
        // recalcular typeId/buscar el entry para cualquier entidad viva del
        // mundo cuando la inmensa mayoria nunca va a tener un MobEntry.
        if (!living.getPersistentData().getBoolean(NBT_APPLIED)) return;
        String typeId = e.entityId;

        try { applyAttributeValues(living, e, true); }
        catch (Exception ex) { CraftEditorMod.LOGGER.error("[Shovel JS] mob attribute reinforcement fallo para {}: {}", typeId, ex.toString()); }
    }

    private static void setAttr(LivingEntity living, Attribute attr, double value) {
        if (attr == null || Double.isNaN(value)) return;
        AttributeInstance inst = living.getAttribute(attr);
        if (inst == null) inst = ensureAttribute(living, attr);
        if (inst != null) inst.setBaseValue(value);
    }

    private static final String NBT_ATTR_ROLLED = "shovel_js_mob_attr_rolled_idx";

    private static boolean applyAttributeValues(LivingEntity living, KubeJSMobExporter.MobEntry e, boolean reinforce) {
        boolean wroteHealth = !Double.isNaN(e.health);
        setAttr(living, Attributes.MAX_HEALTH, e.health);
        setAttr(living, Attributes.ARMOR, e.armor);
        setAttr(living, Attributes.ARMOR_TOUGHNESS, e.armorToughness);
        setAttr(living, Attributes.ATTACK_DAMAGE, e.attackDamage);
        setAttr(living, Attributes.ATTACK_SPEED, e.attackSpeed);
        if (living instanceof net.minecraft.world.entity.Mob mob) MobAttackSpeed.applyToRangedGoals(mob, e.attackSpeed);
        setAttr(living, Attributes.MOVEMENT_SPEED, e.movementSpeed);
        setAttr(living, Attributes.KNOCKBACK_RESISTANCE, e.knockbackResist);
        setAttr(living, Attributes.FOLLOW_RANGE, e.followRange);

        CompoundTag data = living.getPersistentData();
        Set<Integer> keptIdx = new HashSet<>();
        if (reinforce) {
            for (int idx : data.getIntArray(NBT_ATTR_ROLLED)) keptIdx.add(idx);
        } else {
            List<Integer> kept = new ArrayList<>();
            for (int i = 0; i < e.attributeMods.size(); i++) {
                KubeJSMobExporter.AttrMod m = e.attributeMods.get(i);
                if (m.probability >= 1.0 || living.getRandom().nextDouble() < m.probability) kept.add(i);
            }
            int[] arr = new int[kept.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = kept.get(i);
            data.putIntArray(NBT_ATTR_ROLLED, arr);
            keptIdx.addAll(kept);
        }

        for (int i = 0; i < e.attributeMods.size(); i++) {
            KubeJSMobExporter.AttrMod m = e.attributeMods.get(i);
            if (m.attrId == null || m.attrId.isEmpty()) continue;

            if (KubeJSMobExporter.isLegacyAttrId(m.attrId)) {
                if (WARNED_DUPLICATE_ATTRS.add(e.entityId + "->" + m.attrId)) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] Mob '{}': el atributo personalizado '{}' duplica un campo dedicado (Vida/Armadura/etc) - se ignoro para no pisar ese valor.",
                            e.entityId, m.attrId);
                }
                continue;
            }
            if (!keptIdx.contains(i)) continue;
            if (m.dimension != null && !m.dimension.isEmpty()) {
                ResourceLocation dimId = living.level().dimension().location();
                if (!dimId.toString().equals(m.dimension)) continue;
            }
            Attribute attr = resolveAttribute(m.attrId);
            if (attr == null) continue;
            if (living.getAttribute(attr) == null && ensureAttribute(living, attr) == null) {
                if (WARNED_UNSUPPORTED_ATTRS.add(e.entityId + "->" + m.attrId)) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] Mob '{}': no se pudo agregar el atributo personalizado '{}' a este tipo de entidad - se ignoro.",
                            e.entityId, m.attrId);
                }
                continue;
            }
            setAttr(living, attr, m.value);
        }

        return wroteHealth;
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<Attribute>> ATTR_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static Attribute resolveAttribute(String attrId) {
        return ATTR_CACHE.computeIfAbsent(attrId, id -> {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            return java.util.Optional.ofNullable(rl == null ? null : ForgeRegistries.ATTRIBUTES.getValue(rl));
        }).orElse(null);
    }

    private static AttributeInstance ensureAttribute(LivingEntity living, Attribute attr) {
        try {
            AttributeMapAccessor map = (AttributeMapAccessor) living.getAttributes();
            AttributeInstance inst = new AttributeInstance(attr, map::kve$onAttributeModified);
            map.kve$getAttributes().put(attr, inst);
            return inst;
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] no se pudo agregar el atributo {} a {}: {}",
                    BuiltInRegistries.ATTRIBUTE.getKey(attr), living.getType(), ex.toString());
            return null;
        }
    }

    private static void applyCustomName(LivingEntity living, KubeJSMobExporter.MobEntry e) {
        if (!e.customNameSet) return;
        if (e.customName != null && !e.customName.isEmpty()) {
            living.setCustomName(Component.literal(e.customName.replace('&', '\u00A7')));
            living.setCustomNameVisible(e.customNameVisible);
        } else {
            living.setCustomName(null);
            living.setCustomNameVisible(false);
        }
    }

    private static EquipmentSlot slotOf(String name) {
        if (name == null) return null;
        return switch (name) {
            case "MAINHAND" -> EquipmentSlot.MAINHAND;
            case "OFFHAND"  -> EquipmentSlot.OFFHAND;
            case "HEAD"     -> EquipmentSlot.HEAD;
            case "CHEST"    -> EquipmentSlot.CHEST;
            case "LEGS"     -> EquipmentSlot.LEGS;
            case "FEET"     -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    private static void applyEquipment(LivingEntity living, KubeJSMobExporter.MobEntry e) {
        List<KubeJSMobExporter.EquipmentItem> items;
        if (!e.equipmentBuilds.isEmpty()) {
            items = pickWeightedBuild(living, e.equipmentBuilds);
            if (items == null) return;
        } else {
            items = e.equipment;
        }
        for (KubeJSMobExporter.EquipmentItem eq : items) {
            if (eq.itemId == null || eq.itemId.isEmpty()) continue;
            EquipmentSlot slot = slotOf(eq.slot);
            if (slot == null) continue;
            if (eq.spawnChance < 1.0 && living.getRandom().nextDouble() >= eq.spawnChance) continue;
            ItemStack stack = buildStack(eq);
            if (stack.isEmpty()) continue;
            living.setItemSlot(slot, stack);
            if (living instanceof Mob mob) {
                try {
                    mob.setDropChance(slot, (float) eq.dropChance);
                } catch (Exception ex) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] dropChance fallo para {} {}: {}", e.entityId, eq.slot, ex.toString());
                }
            }
        }
    }

    private static List<KubeJSMobExporter.EquipmentItem> pickWeightedBuild(LivingEntity living, List<KubeJSMobExporter.EquipmentBuild> builds) {
        double[] probs = KubeJSMobExporter.buildProbabilities(builds);
        double roll = living.getRandom().nextDouble();
        double cumulative = 0;
        int lastUsable = -1;
        for (int i = 0; i < probs.length; i++) {
            if (probs[i] <= 0) continue;
            lastUsable = i;
            cumulative += probs[i];
            if (roll < cumulative) return builds.get(i).equipment;
        }
        // Redondeo de punto flotante cuando la suma es ~1.0.
        if (lastUsable >= 0 && cumulative >= 0.99999) return builds.get(lastUsable).equipment;
        return null;
    }

    private static ItemStack buildStack(KubeJSMobExporter.EquipmentItem eq) {
        ResourceLocation rl = ResourceLocation.tryParse(eq.itemId);
        Item item = rl == null ? null : BuiltInRegistries.ITEM.get(rl);
        if (item == null || item == Items.AIR) {
            if (WARNED_BAD_ITEM_IDS.add(eq.itemId)) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] equipment: item desconocido '{}', se omite", eq.itemId);
            }
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        if (eq.nbt != null && !eq.nbt.isBlank()) {
            try {
                stack.setTag(TagParser.parseTag(eq.nbt));
            } catch (Exception ex) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] equipment: NBT invalido para '{}': {}", eq.itemId, ex.getMessage());
            }
        }
        return stack;
    }

    private static void applyStaticBehaviorFlags(Mob mob, KubeJSMobExporter.MobEntry e) {
        if (e.behaviorFlags.isEmpty()) return;
        if (e.behaviorFlags.contains("NO_AI")) mob.setNoAi(true);
        if (e.behaviorFlags.contains("FLIES")) mob.setNoGravity(true);
        if (e.behaviorFlags.contains("CAN_PICKUP_LOOT")) mob.setCanPickUpLoot(true);
        if (e.behaviorFlags.contains("BABY") && mob instanceof AgeableMob ageable) {
            ageable.setBaby(true);
        }
        if (e.behaviorFlags.contains("PERSISTENT")) mob.setPersistenceRequired();
        if (e.behaviorFlags.contains("SILENT")) mob.setSilent(true);
        if (e.behaviorFlags.contains("INVULNERABLE")) mob.setInvulnerable(true);
        if (e.behaviorFlags.contains("INVISIBLE")) mob.setInvisible(true);
        if (e.behaviorFlags.contains("GLOWING")) mob.setGlowingTag(true);
        if (e.behaviorFlags.contains("LEFT_HANDED")) mob.setLeftHanded(true);
    }

    private static void applyMobEffects(LivingEntity living, KubeJSMobExporter.MobEntry e) {
        for (KubeJSItemAttrExporter.FoodEffect fx : e.mobEffects) {
            if (fx.effectId == null || fx.effectId.isEmpty()) continue;
            if (fx.chance < 1.0f && living.getRandom().nextFloat() >= fx.chance) continue;

            ResourceLocation rl = ResourceLocation.tryParse(fx.effectId);
            MobEffect effect = rl == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(rl);
            if (effect == null) {
                if (WARNED_BAD_EFFECT_IDS.add(fx.effectId)) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] mob effect: efecto desconocido '{}', se omite", fx.effectId);
                }
                continue;
            }
            living.addEffect(new MobEffectInstance(effect, Math.max(0, fx.durationTicks), Math.max(0, fx.amplifier)));
        }
    }
}
