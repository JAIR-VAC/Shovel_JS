package com.kve.integration.kubejs;

import net.minecraftforge.fml.ModList;

/**
 * Single choke point for "is KubeJS actually installed right now?".
 *
 * WHY THIS FILE EXISTS (read before adding a KubeJS call anywhere else):
 * -----------------------------------------------------------------------
 * KVE has NO hard dependency on KubeJS (see build.gradle: the KubeJS jar is
 * compileOnly, never bundled, never required at runtime). Every other class
 * in this package (com.kve.integration.kubejs.*) freely imports KubeJS
 * classes like KubeJSPlugin/BindingsEvent/EventGroup - that's fine and
 * normal, because the JVM only loads a class the first time it's actively
 * used (a `new Foo()`, a static method call, etc), not just because some
 * OTHER class imports it.
 *
 * Two rules keep that safe:
 *   1. Nothing outside com.kve.integration.kubejs may import a class from
 *      this package directly. Anything in the rest of KVE that wants to
 *      reach into KubeJS-land must go through the guarded static methods
 *      here (isLoaded()) and a method reference into this package - never
 *      a bare `new KveScriptEvents()` or similar sitting unguarded in, say,
 *      StructureWorldIO.
 *   2. KubeJSPlugin itself is only ever instantiated BY KubeJS, via the
 *      ServiceLoader/kubejs.plugins.txt mechanism - so if KubeJS isn't
 *      installed, nothing ever asks the classloader for KveKubeJSPlugin in
 *      the first place. That half takes care of itself.
 *
 * This class has ZERO KubeJS imports on purpose, so it's always safe to
 * import from anywhere in KVE, on both distributions, whether or not
 * KubeJS is present.
 */
public final class KveIntegrations {

    private KveIntegrations() {}

    private static Boolean cached = null;

    /** True if the "kubejs" mod is present on this instance right now. */
    public static boolean isKubeJSLoaded() {
        if (cached == null) {
            cached = ModList.get().isLoaded("kubejs");
        }
        return cached;
    }
}
