package com.kve.common.trade;

import com.kve.common.util.ItemExtraDataCodec;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.Optional;

/**
 * 1.21: los precios de un {@link MerchantOffer} son {@link ItemCost} (item +
 * cantidad + predicado de componentes), no ItemStack, y la oferta se guarda
 * con {@link MerchantOffer#CODEC} en vez de createTag()/new MerchantOffer(tag).
 */
public final class TradeCosts {
    private TradeCosts() {}

    public static ItemCost of(ItemStack stack) {
        DataComponentPredicate.Builder b = DataComponentPredicate.builder();
        for (var e : stack.getComponentsPatch().entrySet()) {
            e.getValue().ifPresent(v -> expect(b, e.getKey(), v));
        }
        return new ItemCost(stack.getItemHolder(), stack.getCount(), b.build());
    }

    @SuppressWarnings("unchecked")
    private static <T> void expect(DataComponentPredicate.Builder b, DataComponentType<T> type, Object value) {
        b.expect(type, (T) value);
    }

    /** Segundo precio opcional: vacio si el stack esta vacio. */
    public static Optional<ItemCost> optional(ItemStack stack) {
        return stack == null || stack.isEmpty() ? Optional.empty() : Optional.of(of(stack));
    }

    public static MerchantOffer offer(ItemStack costA, ItemStack costB, ItemStack result, int maxUses, int xp, float priceMultiplier) {
        return new MerchantOffer(of(costA), optional(costB), result, maxUses, xp, priceMultiplier);
    }

    /** Oferta -> NBT (null si no se pudo codificar). */
    public static CompoundTag save(MerchantOffer offer) {
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, ItemExtraDataCodec.registryAccess());
        return MerchantOffer.CODEC.encodeStart(ops, offer).result()
                .filter(t -> t instanceof CompoundTag).map(t -> (CompoundTag) t).orElse(null);
    }

    /** NBT -> oferta (null si no se pudo leer). */
    public static MerchantOffer load(CompoundTag tag) {
        if (tag == null) return null;
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, ItemExtraDataCodec.registryAccess());
        return MerchantOffer.CODEC.parse(ops, tag).result().orElse(null);
    }
}
