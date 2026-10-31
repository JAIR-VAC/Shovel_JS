package com.kve.common.itemrules;

import com.kve.common.kubejs.KubeJSItemAttrExporter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resumen legible de {@link KubeJSItemAttrExporter.CombatProps}
 * para el tooltip del item ("Al golpear: Veneno II (5s, 30%)", "Robo de
 * vida: 10%"...). Sin esto las 5 features de combate serian invisibles para
 * el jugador en el juego. Lo usan {@link ItemTooltipEnforcer} (tooltip real)
 * y la vista previa en vivo de ItemAttributeScreen, asi que ambos muestran
 * exactamente lo mismo. Componentes traducibles (tooltip.kve.combat.*).
 */
public final class ItemCombatTooltip {

    private ItemCombatTooltip() {}

    public static List<Component> lines(KubeJSItemAttrExporter.CombatProps c) {
        List<Component> out = new ArrayList<>();
        if (c == null || c.isEmpty()) return out;
        out.add(Component.empty());
        out.add(Component.translatable("tooltip.kve.combat.header").withStyle(ChatFormatting.GRAY));
        for (KubeJSItemAttrExporter.FoodEffect fx : c.hitEffects) {
            out.add(line("tooltip.kve.combat.on_hit", effectText(fx, true)));
        }
        for (KubeJSItemAttrExporter.FoodEffect fx : c.passiveEffects) {
            out.add(line("tooltip.kve.combat.passive", effectText(fx, false)));
        }
        if (c.lifestealPct > 0) out.add(line("tooltip.kve.combat.lifesteal", Component.literal(num(c.lifestealPct) + "%")));
        if (c.igniteSeconds > 0) out.add(line("tooltip.kve.combat.ignite", Component.literal(c.igniteSeconds + "s")));
        if (c.useCooldownTicks > 0) out.add(line("tooltip.kve.combat.cooldown", Component.literal(num(c.useCooldownTicks / 20.0) + "s")));
        return out;
    }

    private static Component line(String key, Component value) {
        return Component.literal(" ").append(Component.translatable(key, value)).withStyle(ChatFormatting.BLUE);
    }

    private static Component effectText(KubeJSItemAttrExporter.FoodEffect fx, boolean withChance) {
        ResourceLocation rl = ResourceLocation.tryParse(fx.effectId);
        MobEffect effect = rl == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(rl);
        MutableComponent name = effect != null ? effect.getDisplayName().copy() : Component.literal(fx.effectId);
        if (fx.amplifier > 0) {
            name.append(" ").append(Component.translatable("enchantment.level." + (fx.amplifier + 1)));
        }
        if (withChance) {
            String extra = " (" + num(fx.durationTicks / 20.0) + "s"
                    + (fx.chance < 1.0f ? ", " + Math.round(fx.chance * 100) + "%" : "") + ")";
            name.append(extra);
        }
        return name;
    }

    private static String num(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return new java.math.BigDecimal(String.format(Locale.ROOT, "%.2f", v)).stripTrailingZeros().toPlainString();
    }
}
