package com.kve.client.mixin;

import com.kve.client.gui.KveGuiStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GameRenderer.class)
public abstract class GameRendererEditorMixin {

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean kve$skipWorldBehindStudio(boolean renderLevel) {
        return renderLevel && !KveGuiStyle.shouldSkipWorldRender(Minecraft.getInstance().screen);
    }
}
