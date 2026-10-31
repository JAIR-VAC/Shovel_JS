package com.kve.client.mixin;

import com.kve.client.gui.KveGuiStyle;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Las pantallas del editor no necesitan cientos de FPS (sin vsync
 * llegaban a 400+ y la GPU iba a fondo con la interfaz quieta). Con "Ahorro de
 * recursos" activo se limitan a EDITOR_FPS_CAP (120), igual que Minecraft limita sus propios
 * menus cuando no hay mundo cargado. Solo baja el limite, nunca lo sube.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftFramerateMixin {

    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void kve$capEditorFramerate(CallbackInfoReturnable<Integer> cir) {
        Minecraft self = (Minecraft) (Object) this;
        if (KveGuiStyle.shouldCapEditorFps(self.screen)) {
            cir.setReturnValue(Math.min(cir.getReturnValueI(), KveGuiStyle.EDITOR_FPS_CAP));
        }
    }
}
