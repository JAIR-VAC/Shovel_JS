package com.kve.client.overlay;

import com.kve.client.gui.settings.KveClientSettings;
import com.kve.client.gui.studio.KveStudioShell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public class ToggleButtonOverlay {

    private static boolean visible = true;

    private static int btnX = 0;
    private static int btnY = 0;
    private static final int BTN_W = 16;
    private static final int BTN_H = 16;
    private static final net.minecraft.resources.ResourceLocation ICON = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("shovel_js", "textures/gui/shovel.png");

    public static void toggle() {
        visible = !visible;
    }

    public static boolean isVisible() {
        return visible;
    }

    private static boolean isHiddenBySetting() {
        return KveClientSettings.getOrDefault(KveClientSettings.HIDE_INVENTORY_BUTTON, false);
    }

    private static boolean isSupportedScreen(Screen screen) {
        return screen instanceof AbstractContainerScreen<?>
                && (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen);
    }

    private static final int MARGIN = 6;

    private static boolean computePosition(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?>)) return false;
        btnX = MARGIN;
        btnY = MARGIN;
        avoidOtherWidgets(screen);
        return true;
    }

    private static void avoidOtherWidgets(Screen screen) {
        for (int pass = 0; pass < 8; pass++) {
            boolean moved = false;
            for (var child : screen.children()) {
                if (!(child instanceof net.minecraft.client.gui.components.AbstractWidget w) || !w.visible) continue;
                int wx = w.getX(), wy = w.getY(), ww = w.getWidth(), wh = w.getHeight();
                if (ww <= 0 || wh <= 0) continue;
                boolean overlaps = wx < btnX + BTN_W && btnX < wx + ww && wy < btnY + BTN_H && btnY < wy + wh;
                if (overlaps) {
                    btnY = wy + wh + 2;
                    moved = true;
                }
            }
            if (!moved) return;
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!isSupportedScreen(event.getScreen())) return;
        if (!visible || isHiddenBySetting()) return;
        if (!computePosition(event.getScreen())) return;

        GuiGraphics gfx = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();

        gfx.blit(ICON, btnX, btnY, BTN_W, BTN_H, 0, 0, 16, 16, 16, 16);
        double hmx = event.getMouseX(), hmy = event.getMouseY();
        if (hmx >= btnX && hmx < btnX + BTN_W && hmy >= btnY && hmy < btnY + BTN_H) {
            gfx.fill(btnX + 2, btnY + 2, btnX + BTN_W - 2, btnY + BTN_H - 2, 0x38FFFFFF);
        }
    }

    @SubscribeEvent
    public static void onScreenMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!isSupportedScreen(event.getScreen())) return;
        if (!visible || isHiddenBySetting()) return;
        if (!computePosition(event.getScreen())) return;

        double mx = event.getMouseX();
        double my = event.getMouseY();

        if (mx >= btnX && mx <= btnX + BTN_W && my >= btnY && my <= btnY + BTN_H) {
            com.kve.client.access.KveAccessGate.open(KveStudioShell::new);
            event.setCanceled(true);
        }
    }
}
