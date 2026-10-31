package com.kve.common.itemrules;

import com.kve.common.kubejs.ItemAttrMath;
import com.kve.common.kubejs.KubeJSItemAttrExporter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * (el default, ver
 * {@link KubeJSItemAttrExporter.ItemEntry#addModeAttributes}) es un stat
 * propio del item y se muestra sin signo, en verde, como el dano base de una
 * espada ({@code " 18.5 Dano por golpe"}); solo el modo "Sumar" se ve como
 * modifier aparte ({@code "+1 Alcance"}).
 * <p>
 * Vanilla solo hace eso para dano/velocidad de ataque y solo si el UUID es la
 * instancia exacta de {@code Item.BASE_ATTACK_*_UUID} (ver
 * {@code ItemAttributeEnforcer#uuidFor}). Para el resto (y para la vista
 * previa del editor, cuyos modifiers vienen de NBT y nunca pasan ese ==), se
 * reescribe la linea {@code attribute.modifier.plus/take.0} del atributo.
 * El numero mostrado es el mismo que el campo del editor: modifier + la base
 * del jugador para los stats que el editor muestra como valor final.
 */
public final class ItemAttrTooltipStyle {
    private ItemAttrTooltipStyle() {}

    public static void apply(List<Component> lines, List<KubeJSItemAttrExporter.AttrMod> mods, Set<String> addMode) {
        if (lines == null || lines.isEmpty() || mods == null || mods.isEmpty()) return;
        Map<String, Double> shownByDescId = new HashMap<>();
        for (KubeJSItemAttrExporter.AttrMod m : mods) {
            if (m.attrId == null || m.op != KubeJSItemAttrExporter.Op.ADDITION) continue;
            if (addMode != null && addMode.contains(m.attrId)) continue;
            ResourceLocation rl = ResourceLocation.tryParse(m.attrId);
            Attribute attr = rl == null ? null : ForgeRegistries.ATTRIBUTES.getValue(rl);
            if (attr == null) continue;
            shownByDescId.put(attr.getDescriptionId(), m.value + displayBase(m.attrId));
        }
        if (shownByDescId.isEmpty()) return;

        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (!(line.getContents() instanceof TranslatableContents tc)) continue;
            String key = tc.getKey();
            if (!key.equals("attribute.modifier.plus.0") && !key.equals("attribute.modifier.take.0")) continue;
            Object[] args = tc.getArgs();
            if (args.length < 2 || !(args[1] instanceof Component name)) continue;
            if (!(name.getContents() instanceof TranslatableContents nameKey)) continue;
            Double shown = shownByDescId.get(nameKey.getKey());
            if (shown == null) continue;
            lines.set(i, Component.literal(" ").append(Component.translatable("attribute.modifier.equals.0",
                    ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(shown), name)).withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    static double displayBase(String attrId) {
        return switch (attrId) {
            case "minecraft:generic.attack_damage" -> ItemAttrMath.ATTACK_DAMAGE_BASE;
            case "minecraft:generic.attack_speed" -> ItemAttrMath.ATTACK_SPEED_BASE;
            case "minecraft:generic.movement_speed" -> ItemAttrMath.MOVEMENT_SPEED_BASE;
            default -> 0.0;
        };
    }
}
