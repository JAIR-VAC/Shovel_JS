package com.kve.client.access;

import com.kve.common.Lang;
import com.kve.client.permission.ClientPermissionState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Supplier;

@OnlyIn(Dist.CLIENT)
public final class KveAccessGate {

    private KveAccessGate() {}

    public static void open(Supplier<Screen> factory) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientPermissionState.isResolved()) {
            notify(mc, Lang.get("gui.kve.perm.checking"), 0xFFE0C060);
            return;
        }
        if (!ClientPermissionState.canView()) {
            notify(mc, Lang.get("gui.kve.perm.denied"), 0xFFFF6666);
            return;
        }
        mc.setScreen(factory.get());
    }

    private static void notify(Minecraft mc, String msg, int color) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(msg).withStyle(s -> s.withColor(color)), true);
        }
    }
}
