package com.kve.common.itemrules;

import com.kve.common.kubejs.ItemAttrMath;
import com.kve.common.kubejs.KubeJSItemAttrExporter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.neoforge.common.extensions.IAttributeExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ItemAttrTooltipStyle {
    private ItemAttrTooltipStyle() {}

    private static final Set<String> MODIFIER_KEYS = Set.of(
            "neoforge.modifier.plus", "neoforge.modifier.take",
            "attribute.modifier.plus.0", "attribute.modifier.take.0");

    public static void apply(List<Component> lines, List<KubeJSItemAttrExporter.AttrMod> mods, Set<String> addMode) {
        if (lines == null || lines.isEmpty() || mods == null || mods.isEmpty()) return;
        Map<String, Double> shownByDescId = new HashMap<>();
        for (KubeJSItemAttrExporter.AttrMod m : mods) {
            if (m.attrId == null || m.op != KubeJSItemAttrExporter.Op.ADDITION) continue;
            if (addMode != null && addMode.contains(m.attrId)) continue;
            ResourceLocation rl = ResourceLocation.tryParse(m.attrId);
            Attribute attr = rl == null ? null : BuiltInRegistries.ATTRIBUTE.get(rl);
            if (attr == null) continue;
            shownByDescId.put(attr.getDescriptionId(), m.value + displayBase(m.attrId));
        }
        if (shownByDescId.isEmpty()) return;

        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            Component replaced = restyle(line, shownByDescId);
            if (replaced != null) lines.set(i, replaced);
        }
    }

    static Component restyle(Component line, Map<String, Double> shownByDescId) {
        if (!(line.getContents() instanceof TranslatableContents tc)) return null;
        if (!MODIFIER_KEYS.contains(tc.getKey())) return null;
        Object[] args = tc.getArgs();
        if (args.length < 2 || !(args[1] instanceof Component name)) return null;
        if (!(name.getContents() instanceof TranslatableContents nameKey)) return null;
        Double shown = shownByDescId.get(nameKey.getKey());
        if (shown == null) return null;
        Component value = Component.translatable("neoforge.value.flat", IAttributeExtension.FORMAT.format(shown));
        return Component.literal(" ").append(Component.translatable("attribute.modifier.equals.0", value, name))
                .withStyle(ChatFormatting.DARK_GREEN);
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
