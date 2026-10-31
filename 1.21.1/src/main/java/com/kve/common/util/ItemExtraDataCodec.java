package com.kve.common.util;

import com.kve.CraftEditorMod;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.function.Supplier;

public final class ItemExtraDataCodec {

    private ItemExtraDataCodec() {}

    private static volatile Supplier<RegistryAccess> clientAccess = () -> null;

    /** Lo llama el lado cliente (CraftEditorMod#clientSetup) con el RegistryAccess de la conexion activa. */
    public static void setClientAccess(Supplier<RegistryAccess> supplier) {
        clientAccess = supplier == null ? () -> null : supplier;
    }

    /** RegistryAccess del servidor, del cliente, o solo los registros estaticos. Nunca null. */
    public static RegistryAccess registryAccess() {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) return server.registryAccess();
        } catch (Throwable ignored) {
        }
        try {
            RegistryAccess c = clientAccess.get();
            if (c != null) return c;
        } catch (Throwable ignored) {
        }
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    /** True si el stack tiene algun componente distinto de los de su item (equivale al viejo hasTag()). */
    public static boolean hasMeaningfulExtraData(ItemStack stack) {
        return stack != null && !stack.isEmpty() && !stack.getComponentsPatch().isEmpty();
    }

    public static String encode(ItemStack stack) {
        if (!hasMeaningfulExtraData(stack)) return null;
        try {
            RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registryAccess());
            return DataComponentPatch.CODEC.encodeStart(ops, stack.getComponentsPatch()).result().map(Tag::toString).orElse(null);
        } catch (Throwable t) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] ItemExtraDataCodec.encode fallo para {}: {}", stack.getItem(), t.toString());
            return null;
        }
    }

    /** Igual que {@link #encode} pero "" en vez de null (para los campos String de los json). */
    public static String encodeOrEmpty(ItemStack stack) {
        String s = encode(stack);
        return s == null ? "" : s;
    }

    public static ItemStack applyFromSnbt(ItemStack stack, String snbt) {
        if (stack == null || stack.isEmpty() || snbt == null || snbt.isBlank()) return stack;
        Tag tag;
        try {
            tag = TagParser.parseTag(snbt);
        } catch (Exception e) {
            return stack;
        }
        try {
            RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registryAccess());
            var parsed = DataComponentPatch.CODEC.parse(ops, tag).result();
            if (parsed.isPresent() && !parsed.get().isEmpty()) {
                stack.applyComponents(parsed.get());
                return stack;
            }
        } catch (Throwable ignored) {
        }
        if (tag instanceof CompoundTag ct && !ct.isEmpty()) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(ct));
        }
        return stack;
    }

    public static String replaceFromSnbtStrict(ItemStack stack, String snbt) {
        if (stack == null || stack.isEmpty()) return "stack vacio";
        Tag tag;
        try {
            tag = snbt == null || snbt.isBlank() ? new CompoundTag() : TagParser.parseTag(snbt);
        } catch (Exception e) {
            return e.getMessage() != null ? e.getMessage() : "SNBT invalido";
        }
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registryAccess());
        var result = DataComponentPatch.CODEC.parse(ops, tag);
        if (result.error().isPresent()) return result.error().get().message();
        clear(stack);
        stack.applyComponents(result.getOrThrow());
        return null;
    }

    public static void clear(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        var defaults = stack.getItem().components();
        var types = new java.util.ArrayList<net.minecraft.core.component.DataComponentType<?>>();
        for (var e : stack.getComponentsPatch().entrySet()) types.add(e.getKey());
        for (var t : types) resetComponent(stack, defaults, t);
    }

    private static <T> void resetComponent(ItemStack stack, net.minecraft.core.component.DataComponentMap defaults,
                                           net.minecraft.core.component.DataComponentType<T> type) {
        T def = defaults.get(type);
        if (def != null) stack.set(type, def);
        else stack.remove(type);
    }

    /**
     * Componentes en la sintaxis de string de item de KubeJS 7 / comandos:
     * {@code [minecraft:damage=5,!minecraft:food]} ("" si no hay). Se pega
     * detras del id: {@code Item.of('minecraft:diamond_sword[...]')}.
     * Verificado contra KubeJS 2101 {@code DataComponentWrapper#readPatch}.
     */
    public static String componentString(ItemStack stack) {
        if (!hasMeaningfulExtraData(stack)) return "";
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registryAccess());
        StringBuilder sb = new StringBuilder("[");
        for (var e : stack.getComponentsPatch().entrySet()) {
            var type = e.getKey();
            var key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            if (key == null || type.isTransient()) continue;
            if (e.getValue().isEmpty()) {
                if (sb.length() > 1) sb.append(',');
                sb.append('!').append(key);
                continue;
            }
            String value = encodeValue(ops, type, e.getValue().get());
            if (value == null) continue;
            if (sb.length() > 1) sb.append(',');
            sb.append(key).append('=').append(value);
        }
        return sb.length() == 1 ? "" : sb.append(']').toString();
    }

    @SuppressWarnings("unchecked")
    private static <T> String encodeValue(RegistryOps<Tag> ops, net.minecraft.core.component.DataComponentType<T> type, Object value) {
        var codec = type.codec();
        if (codec == null) return null;
        return codec.encodeStart(ops, (T) value).result().map(Tag::toString).orElse(null);
    }

    /**
     * Objeto {@code "components"} de un resultado de receta en json 1.21
     * ({@code {"id": ..., "count": ..., "components": {...}}}); null si no hay.
     */
    public static com.google.gson.JsonElement componentsJson(ItemStack stack) {
        if (!hasMeaningfulExtraData(stack)) return null;
        try {
            RegistryOps<com.google.gson.JsonElement> ops = RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, registryAccess());
            return DataComponentPatch.CODEC.encodeStart(ops, stack.getComponentsPatch()).result().orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    public static CompoundTag customData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new CompoundTag();
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd == null ? new CompoundTag() : cd.copyTag();
    }

    /** Reemplaza minecraft:custom_data (vacio = lo quita). */
    public static void setCustomData(ItemStack stack, CompoundTag tag) {
        if (stack == null || stack.isEmpty()) return;
        if (tag == null || tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
