package com.kve.integration.jade;

import com.kve.CraftEditorMod;
import com.kve.common.blockrules.ExactToolRequirements;
import com.kve.common.blockrules.NetheriteTierRequirements;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public enum RequiredToolComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public int getDefaultPriority() {
        return -5000;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(accessor.getBlock());
        if (blockId == null) return;
        String id = blockId.toString();

        Set<String> exactItems = ExactToolRequirements.requiredItemsFor(id);
        if (!exactItems.isEmpty()) {
            List<ItemStack> icons = itemStacks(exactItems, 3);
            if (!icons.isEmpty()) putIconsOnNameLine(tooltip, icons);
            tooltip.add(Component.translatable("gui.kve.jade.required_tool.exact",
                    describeRequirementLocalized(exactItems)));
            return;
        }

        if (NetheriteTierRequirements.hasRequirement(id)) {
            putIconsOnNameLine(tooltip, List.of(new ItemStack(net.minecraft.world.item.Items.NETHERITE_PICKAXE)));
            tooltip.add(Component.translatable("gui.kve.jade.required_tool.netherite"));
        }
    }

    private static final net.minecraft.world.phys.Vec2 ITEM_SIZE = new net.minecraft.world.phys.Vec2(10, 0);
    private static final net.minecraft.world.phys.Vec2 ITEM_OFFSET = new net.minecraft.world.phys.Vec2(-1, -3);

    private static void putIconsOnNameLine(ITooltip tooltip, List<ItemStack> stacks) {
        tooltip.remove(snownee.jade.api.Identifiers.MC_HARVEST_TOOL);
        IElementHelper helper = tooltip.getElementHelper();
        List<IElement> row = new ArrayList<>();
        row.add(helper.spacer(5, 0));
        for (ItemStack stack : stacks) {
            row.add(helper.item(stack, 0.75f).translate(ITEM_OFFSET).size(ITEM_SIZE).message(null));
        }
        for (IElement e : row) e.align(IElement.Align.RIGHT);
        if (tooltip.isEmpty()) tooltip.add(row);
        else tooltip.append(0, row);
    }

    private static List<ItemStack> itemStacks(Set<String> itemIds, int max) {
        List<ItemStack> out = new ArrayList<>();
        for (String itemId : itemIds) {
            if (out.size() >= max) break;
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) out.add(new ItemStack(BuiltInRegistries.ITEM.get(rl)));
        }
        return out;
    }

    private static Component describeRequirementLocalized(Set<String> itemIds) {
        List<Component> names = new ArrayList<>();
        for (String itemId : itemIds) {
            names.add(itemDisplayName(itemId));
        }
        if (names.isEmpty()) return Component.empty();

        MutableComponent joined = names.get(0).copy();
        for (int i = 1; i < names.size(); i++) {
            joined = joined.append(Component.translatable("gui.kve.jade.required_tool.or"))
                    .append(names.get(i).copy());
        }
        return joined;
    }

    private static Component itemDisplayName(String itemId) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
            Item item = BuiltInRegistries.ITEM.get(rl);
            return new ItemStack(item).getHoverName();
        }
        return Component.literal(itemId);
    }

    /**
     * Unique id Jade uses to key this provider (ordering, per-provider
     * enable/disable in Jade's own config screen). Namespaced under this
     * mod's id like every other KVE resource location.
     */
    @Override
    public ResourceLocation getUid() {
        return new ResourceLocation(CraftEditorMod.MODID, "required_tool");
    }
}
