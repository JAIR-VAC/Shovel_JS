package com.kve.integration.kubejs;

import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.core.ReloadableServerResourcesKJS;
import dev.latvian.mods.kubejs.script.KubeJSContext;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Scriptable;

import java.util.HashSet;
import java.util.Set;

/**
 * Nombres que un script de KubeJS puede usar sin declararlos, para
 * {@link RhinoNameCheck}: builtins de JavaScript, los bindings de KubeJS y
 * los que agregan los addons.
 * <p>
 * Los bindings reales se leen del scope vivo de cada ScriptManager (startup,
 * client y, en singleplayer, server) - asi un addon que agrega su propio
 * binding no aparece como error. Si no hay ningun scope cargado (por ejemplo
 * en un server remoto antes de que corran los client scripts) queda solo la
 * lista fija, y {@link #isGlobal()} no marca nombres que empiezan con mayuscula
 * (asi se llaman casi todos los bindings de addons que no conocemos).
 * <p>
 * Importa clases de KubeJS (compileOnly): solo llamar si
 * {@link KveIntegrations#isKubeJSLoaded()} es true.
 */
public final class KubeJsGlobals {

    private KubeJsGlobals() {}

    private static final Set<String> BUILTIN = Set.of(
            "Object", "Function", "Array", "String", "Number", "Boolean", "Math", "JSON", "Date", "RegExp",
            "Error", "TypeError", "RangeError", "SyntaxError", "ReferenceError", "EvalError", "URIError",
            "Symbol", "Map", "Set", "WeakMap", "WeakSet", "Promise", "Proxy", "Reflect", "BigInt", "Iterator",
            "ArrayBuffer", "DataView", "Int8Array", "Uint8Array", "Uint8ClampedArray", "Int16Array", "Uint16Array",
            "Int32Array", "Uint32Array", "Float32Array", "Float64Array",
            "parseInt", "parseFloat", "isNaN", "isFinite", "encodeURI", "encodeURIComponent", "decodeURI",
            "decodeURIComponent", "escape", "unescape", "eval", "undefined", "NaN", "Infinity", "globalThis",
            "arguments", "Java", "JavaAdapter", "Packages", "java", "javax", "importClass", "importPackage",
            "console", "global", "ServerEvents", "StartupEvents", "ClientEvents", "PlayerEvents", "EntityEvents",
            "BlockEvents", "ItemEvents", "LevelEvents", "NetworkEvents", "WorldgenEvents", "JEIEvents", "REIEvents",
            "Item", "Ingredient", "Text", "Component", "Fluid", "Block", "Blocks", "Items", "Utils", "UtilsJS",
            "JsonIO", "NBT", "NBTIO", "Platform", "Direction", "Facing", "AABB", "Vec3d", "Vec3", "BlockPos",
            "Color", "Rarity", "Stats", "EntityType", "Hand", "InteractionHand", "SoundType", "DamageSource",
            "Duration", "Notification", "KMath", "Mth", "RotationAxis", "BlockProperties", "BlockStatePredicate",
            "ItemFilter", "LangEventJS", "SECOND", "MINUTE", "HOUR", "Client",
            "ShovelAPI", "CuriosJSEvents");

    private static volatile Set<String> live = null;

    /** Vacia el cache de bindings vivos (al tocar "Validar": puede haber recargado scripts). */
    public static void clearCache() { live = null; }

    public static boolean isGlobal(String name) {
        if (BUILTIN.contains(name)) return true;
        Set<String> l = live();
        if (l.contains(name)) return true;
        return l.isEmpty() && !name.isEmpty() && Character.isUpperCase(name.charAt(0));
    }

    private static Set<String> live() {
        Set<String> l = live;
        if (l != null) return l;
        Set<String> out = new HashSet<>();
        addScope(safe(dev.latvian.mods.kubejs.KubeJS::getStartupScriptManager), out);
        addScope(safe(dev.latvian.mods.kubejs.KubeJS::getClientScriptManager), out);
        addScope(safe(KubeJsGlobals::integratedServerManager), out);
        live = out;
        return out;
    }

    private static ScriptManager integratedServerManager() {
        var server = net.minecraft.client.Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return null;
        return ((ReloadableServerResourcesKJS) server.getServerResources().managers()).kjs$getServerScriptManager();
    }

    private static ScriptManager safe(java.util.function.Supplier<ScriptManager> s) {
        try { return s.get(); } catch (Throwable t) { return null; }
    }

    private static void addScope(ScriptManager mgr, Set<String> out) {
        if (mgr == null) return;
        try {
            Context cx = mgr.contextFactory.enter();
            if (!(cx instanceof KubeJSContext kcx)) return;
            for (Scriptable s = kcx.topLevelScope; s != null; s = s.getPrototype(cx)) {
                for (Object id : s.getAllIds(cx)) if (id instanceof String str) out.add(str);
            }
        } catch (Throwable ignored) {
        }
    }
}
