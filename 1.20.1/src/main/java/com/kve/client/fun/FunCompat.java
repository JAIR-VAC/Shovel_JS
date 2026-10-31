package com.kve.client.fun;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/** Lo que cambia entre Forge 1.20.1 y NeoForge 1.21.1 para el aleatorizador. */
@OnlyIn(Dist.CLIENT)
final class FunCompat {

    private FunCompat() {}

    record RecipeView(ResourceLocation id, Recipe<?> recipe, ItemStack result, List<Ingredient> ingredients) {}

    static List<RecipeView> recipes() {
        Minecraft mc = Minecraft.getInstance();
        RecipeManager mgr = mc.getConnection() != null ? mc.getConnection().getRecipeManager()
                : mc.level != null ? mc.level.getRecipeManager() : null;
        RegistryAccess access = mc.getConnection() != null ? mc.getConnection().registryAccess()
                : mc.level != null ? mc.level.registryAccess() : RegistryAccess.EMPTY;
        List<RecipeView> out = new ArrayList<>();
        if (mgr == null) return out;
        for (Recipe<?> r : mgr.getRecipes()) {
            ItemStack result;
            try { result = r.getResultItem(access); } catch (Throwable t) { continue; }
            List<Ingredient> ings;
            try { ings = new ArrayList<>(r.getIngredients()); } catch (Throwable t) { ings = List.of(); }
            out.add(new RecipeView(r.getId(), r, result == null ? ItemStack.EMPTY : result, ings));
        }
        return out;
    }

    /** Valor por defecto (suma) de un atributo del item en su ranura natural; NaN si no lo tiene. */
    static double itemAttribute(Item item, String attrKey) {
        ItemStack stack = new ItemStack(item);
        EquipmentSlot slot = LivingEntity.getEquipmentSlotForItem(stack);
        Attribute attr = BuiltInRegistries.ATTRIBUTE.get(new ResourceLocation(attrKey));
        if (attr == null) return Double.NaN;
        double sum = Double.NaN;
        try {
            for (AttributeModifier m : item.getAttributeModifiers(slot, stack).get(attr)) {
                if (m.getOperation() != AttributeModifier.Operation.ADDITION) continue;
                sum = (Double.isNaN(sum) ? 0 : sum) + m.getAmount();
            }
        } catch (Throwable ignored) {
            return Double.NaN;
        }
        return sum;
    }

    static boolean isArmorSlot(Item item) {
        EquipmentSlot s = LivingEntity.getEquipmentSlotForItem(new ItemStack(item));
        return s.getType() == EquipmentSlot.Type.ARMOR;
    }

    static String slotName(Item item) {
        return LivingEntity.getEquipmentSlotForItem(new ItemStack(item)).getName();
    }

    /** Bonus de progresion por material de herramienta (madera 0 ... netherite 4). */
    static int tierBonus(Item item) {
        if (item instanceof TieredItem t) {
            try { return Math.max(0, t.getTier().getLevel()); } catch (Throwable ignored) { }
        }
        return 0;
    }

    /** Valor base de un atributo del mob; NaN si el tipo no tiene ese atributo o no es un ser vivo. */
    @SuppressWarnings("unchecked")
    static double mobBase(EntityType<?> type, String which) {
        try {
            if (!DefaultAttributes.hasSupplier(type)) return Double.NaN;
            AttributeSupplier sup = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
            Attribute a = switch (which) {
                case "health" -> Attributes.MAX_HEALTH;
                case "damage" -> Attributes.ATTACK_DAMAGE;
                case "speed" -> Attributes.MOVEMENT_SPEED;
                case "armor" -> Attributes.ARMOR;
                default -> null;
            };
            if (a == null || !sup.hasAttribute(a)) return Double.NaN;
            return sup.getBaseValue(a);
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    /**
     * Que equipo le sirve al mob: 0 ninguno, 1 solo armadura (ya trae su arma: esqueletos,
     * illagers, piglins), 2 armadura y espada (zombies). Crea una entidad suelta para mirar su clase.
     */
    static int gearKind(EntityType<?> type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
        net.minecraft.world.entity.Entity e;
        try { e = type.create(mc.level); } catch (Throwable t) { return 0; }
        if (e == null) return 0;
        try {
            if (e instanceof net.minecraft.world.entity.monster.Zombie) return 2;
            if (e instanceof net.minecraft.world.entity.monster.AbstractSkeleton
                    || e instanceof net.minecraft.world.entity.monster.AbstractIllager
                    || e instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin) return 1;
            return 0;
        } finally {
            e.discard();
        }
    }

    private static final TagKey<EntityType<?>> FORGE_BOSSES = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("forge", "bosses"));
    private static final TagKey<EntityType<?>> C_BOSSES = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("c", "bosses"));

    static boolean isBossTagged(EntityType<?> type) {
        try { return type.is(FORGE_BOSSES) || type.is(C_BOSSES); } catch (Throwable t) { return false; }
    }
}
