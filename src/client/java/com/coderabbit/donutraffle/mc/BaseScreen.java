package com.coderabbit.donutraffle.mc;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.21.11 screen base: routes rendering to {@link #drawBehind} and {@link #drawFront}. */
public abstract class BaseScreen extends Screen {
	protected BaseScreen(Component title) {
		super(title);
	}

	/** Panels and text under the widgets. */
	protected void drawBehind(Gfx g, int mouseX, int mouseY) {
	}

	/** Item icons and highlights on top of the widgets. */
	protected void drawFront(Gfx g, int mouseX, int mouseY) {
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		g.fill(0, 0, width, height, backgroundColor());
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		Gfx gfx = new Gfx(g);
		drawBehind(gfx, mouseX, mouseY);
		super.render(g, mouseX, mouseY, delta);
		drawFront(gfx, mouseX, mouseY);
	}

	/** The dim drawn over the world behind this screen. */
	protected int backgroundColor() {
		return 0xC8000000;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
