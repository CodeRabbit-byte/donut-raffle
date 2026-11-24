package com.coderabbit.donutraffle.mc;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;

/** 1.21.11 drawing: the shared code draws through this so it compiles on both versions. */
public final class Gfx {
	private final GuiGraphics g;

	public Gfx(GuiGraphics g) {
		this.g = g;
	}

	public int width() {
		return g.guiWidth();
	}

	public int height() {
		return g.guiHeight();
	}

	public void fill(int x1, int y1, int x2, int y2, int color) {
		g.fill(x1, y1, x2, y2, color);
	}

	public void outline(int x, int y, int w, int h, int color) {
		g.renderOutline(x, y, w, h, color);
	}

	public void text(Font font, String s, int x, int y, int color, boolean shadow) {
		g.drawString(font, s, x, y, color, shadow);
	}

	public void text(Font font, Component c, int x, int y, int color, boolean shadow) {
		g.drawString(font, c, x, y, color, shadow);
	}

	public void centered(Font font, String s, int cx, int y, int color) {
		g.drawCenteredString(font, s, cx, y, color);
	}

	public void centered(Font font, Component c, int cx, int y, int color) {
		g.drawCenteredString(font, c, cx, y, color);
	}

	public void item(ItemStack stack, int x, int y) {
		g.renderItem(stack, x, y);
	}

	/** A player's face (with hat layer), size x size pixels. */
	public void face(PlayerSkin skin, int x, int y, int size) {
		PlayerFaceRenderer.draw(g, skin, x, y, size);
	}

	public void push() {
		g.pose().pushMatrix();
	}

	public void pop() {
		g.pose().popMatrix();
	}

	public void translate(float x, float y) {
		g.pose().translate(x, y);
	}

	public void scale(float s) {
		g.pose().scale(s, s);
	}
}
