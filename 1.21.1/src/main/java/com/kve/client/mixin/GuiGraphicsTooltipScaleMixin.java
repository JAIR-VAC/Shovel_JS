package com.kve.client.mixin;

import com.kve.client.gui.KveGuiStyle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Los tooltips que dibuja vanilla -
 * {@code Tooltip.create} de botones/cajas, tooltips de items y el de texto
 * truncado ({@code KveTruncatedTextTooltip}) - ignoraban
 * {@link KveGuiStyle#uiTextScale()}; solo {@code drawFramedTooltip} la
 * respetaba. Todos pasan por {@code renderTooltipInternal}, asi que se
 * escalan aca, alrededor del mouse, cuando la pantalla abierta es del mod.
 * Firma verificada en 1.20.1 (mapeos oficiales) y NeoForge 21.1.215.
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsTooltipScaleMixin {

    @Unique
    private final ArrayDeque<Boolean> kve$scaled = new ArrayDeque<>();

    /** El item del tooltip que se esta dibujando (lo agrega Forge/NeoForge; lo leen los mods de tooltips). */
    @Shadow
    private ItemStack tooltipStack;

    @Shadow
    private void renderTooltipInternal(Font font, List<ClientTooltipComponent> components, int mouseX, int mouseY,
                                       ClientTooltipPositioner positioner) {}

    @Inject(method = "renderTooltipInternal", at = @At("HEAD"), cancellable = true)
    private void kve$scaleTooltip(Font font, List<ClientTooltipComponent> components, int mouseX, int mouseY,
                                  ClientTooltipPositioner positioner, CallbackInfo ci) {
        // en las pantallas del mod el tooltip se dibuja al final del fotograma (ver KveTooltipDefer)
        if (com.kve.client.gui.KveTooltipDefer.isDeferring()) {
            GuiGraphics self = (GuiGraphics) (Object) this;
            Matrix4f matrix = new Matrix4f(self.pose().last().pose());
            List<ClientTooltipComponent> copy = List.copyOf(components);
            ItemStack stack = tooltipStack;
            com.kve.client.gui.KveTooltipDefer.defer(() -> {
                self.pose().pushPose();
                self.pose().last().pose().set(matrix);
                ItemStack prev = tooltipStack;
                tooltipStack = stack;
                try {
                    renderTooltipInternal(font, copy, mouseX, mouseY, positioner);
                } finally {
                    tooltipStack = prev;
                    self.pose().popPose();
                }
            });
            ci.cancel();
            return;
        }
        float s = KveGuiStyle.tooltipScaleFor(Minecraft.getInstance().screen);
        boolean push = Math.abs(s - 1f) > 0.001f;
        kve$scaled.push(push);
        if (!push) return;
        PoseStack pose = ((GuiGraphics) (Object) this).pose();
        pose.pushPose();
        pose.translate(mouseX, mouseY, 0);
        pose.scale(s, s, 1f);
        pose.translate(-mouseX, -mouseY, 0);
    }

    @Inject(method = "renderTooltipInternal", at = @At("RETURN"))
    private void kve$restoreTooltip(Font font, List<ClientTooltipComponent> components, int mouseX, int mouseY,
                                    ClientTooltipPositioner positioner, CallbackInfo ci) {
        Boolean pushed = kve$scaled.poll();
        if (Boolean.TRUE.equals(pushed)) ((GuiGraphics) (Object) this).pose().popPose();
    }
}
