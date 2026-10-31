package com.kve.client.gui.studio;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface KveStudioTab {

    void init(int x, int y, int w, int h);

    void render(GuiGraphics g, int mx, int my, float partialTick);

    boolean mouseClicked(double mx, double my, int button);

    boolean mouseDragged(double mx, double my, int button, double dx, double dy);

    boolean keyPressed(int key, int scancode, int mods);

    void removed();
}
