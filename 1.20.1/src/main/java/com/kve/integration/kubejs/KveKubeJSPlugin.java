package com.kve.integration.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ClassFilter;

/**
 * The ONLY class KubeJS ever asks the classloader for. Discovered via
 * src/main/resources/kubejs.plugins.txt (KubeJS's own ServiceLoader-style
 * plugin list) - if KubeJS isn't installed, nothing ever reads that file
 * and nothing ever triggers loading this class at runtime. See
 * KveIntegrations for the full explanation.
 *
 * IMPORTANT: that runtime-optional loading does NOT make KubeJS optional
 * at COMPILE time. javac must resolve every import and supertype for every
 * class in the project regardless of whether it's ever instantiated, so
 * build.gradle declares KubeJS/Rhino/Architectury as `compileOnly` deps
 * (compile-time only, not bundled, not required at runtime - matches
 * mods.toml's `modId=kubejs, mandatory=false`).
 *
 * Signatures verified 2026-08 by cloning kube-mods/kubejs at the actual
 * `2001` branch (the one that ships for 1.20.1) and reading the real
 * source - NOT the `main`/newer branches, which target a much later MC
 * version and have a completely different plugin package layout
 * (dev.latvian.mods.kubejs.plugin.*), which is what caused the previous
 * two rounds of import errors. For 1.20.1 specifically:
 *   - KubeJSPlugin is a concrete CLASS (not an interface) at
 *     dev.latvian.mods.kubejs.KubeJSPlugin (no ".plugin" subpackage).
 *   - ClassFilter lives at dev.latvian.mods.kubejs.util.ClassFilter.
 *   - BindingsEvent (dev.latvian.mods.kubejs.script.BindingsEvent) is
 *     real for this version - registerBindings(BindingsEvent) is correct.
 *   - registerClasses() takes TWO params: (ScriptType, ClassFilter).
 */
public class KveKubeJSPlugin extends KubeJSPlugin {

    @Override
    public void registerClasses(ScriptType type, ClassFilter filter) {
        filter.allow(KveScriptAPI.class);
        filter.allow(com.kve.common.recipe.RecipeData.MachineSlot.class);
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        event.add("ShovelAPI", KveScriptAPI.class);
    }

}
