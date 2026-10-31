package com.kve.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public final class KveTypingKeyGuard {

    private KveTypingKeyGuard() {}

    private static final String KVE_PACKAGE_PREFIX = "com.kve.";

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        Screen screen = event.getScreen();
        if (!isKveScreen(screen)) return;

        GuiEventListener focused = screen.getFocused();
        if (!(focused instanceof EditBox)) return;

        InputConstants.Key raw = InputConstants.getKey(event.getKeyCode(), event.getScanCode());
        KeyMapping.set(raw, false);
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (mapping.matches(event.getKeyCode(), event.getScanCode())) {
                while (mapping.consumeClick()) {
                    // drena cualquier click ya contado para esta tecla -
                    // ver Javadoc de la clase, por que no alcanza con
                    // cancelar el evento.
                }
            }
        }
        // Sin event.setCanceled(true) a propósito: la pantalla de KVE tiene
        // que seguir procesando la tecla con total normalidad (tipeo,
        // cursor, sus propios atajos Ctrl+letra) - lo único que se
        // neutraliza es la keybind AJENA que compartía esa tecla.
    }

    private static boolean isKveScreen(Screen screen) {
        return screen != null && screen.getClass().getName().startsWith(KVE_PACKAGE_PREFIX);
    }
}
