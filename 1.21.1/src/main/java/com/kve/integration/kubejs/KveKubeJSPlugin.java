package com.kve.integration.kubejs;

import dev.latvian.mods.kubejs.plugin.ClassFilter;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

public class KveKubeJSPlugin implements KubeJSPlugin {

    @Override
    public void registerClasses(ClassFilter filter) {
        filter.allow(KveScriptAPI.class);
        filter.allow(com.kve.common.recipe.RecipeData.MachineSlot.class);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("ShovelAPI", KveScriptAPI.class);
    }

}
