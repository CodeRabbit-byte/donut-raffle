package com.coderabbit.donutraffle;

import com.coderabbit.donutraffle.core.Raffle;
import com.coderabbit.donutraffle.mc.BaseScreen;
import com.coderabbit.donutraffle.mc.Gfx;
import com.coderabbit.donutraffle.mc.Mc;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Move and resize the jackpot bar over the live game: drag to move, scroll to resize, right-click
 * to hide or show it, arrow keys nudge by one pixel, and Shift turns off snapping to the centre
 * line and the top edge.
 */
public class HudEditScreen extends BaseScreen {
	private static final int SNAP = 6;
	private static final int PANEL_BG = 0xC0202024;
	private static final int PANEL_EDGE = 0x40FFFFFF;
	private static final int GUIDE = 0xFFFFD83B;

	private final Screen parent;
	private boolean dragging;
	private double offX;
	private double offY;
	private boolean snappedX;
	private boolean snappedY;
	private boolean shift;
	private boolean panelWasOnTop;

	@Override
	public void tick() {
		super.tick();

		// The bar crossed the middle: move the panel (and its buttons) to the other half.
		if (panelOnTop() != panelWasOnTop) {
			rebuildWidgets();
		}
	}

	public HudEditScreen(Screen parent) {
		super(Component.literal("Move the jackpot bar"));
		this.parent = parent;
	}

	/** True while the bar is held by the mouse (for the in-game test). */
	public boolean isMovingBar() {
		return dragging;
	}

	private Raffle r() {
		return DonutRaffle.get();
	}

	private Hud.Place place() {
		return Hud.place(font, r(), width, height);
	}

	private static final int PANEL_H = 56;

	/** The help panel sits in whichever half of the screen the bar is not in, so it never covers it. */
	private boolean panelOnTop() {
		Hud.Place p = place();
		return p.y() + p.h() / 2 > height / 2;
	}

	private int panelTop() {
		return panelOnTop() ? Math.max(4, height / 4 - PANEL_H / 2) : height * 3 / 4 - PANEL_H / 2;
	}

	@Override
	protected void init() {
		int cx = width / 2;
		panelWasOnTop = panelOnTop();
		int y = panelTop() + 31;
		addRenderableWidget(Button.builder(Component.literal("Reset sizes"), b -> reset())
				.bounds(cx - 84, y, 80, 20)
				.build());
		addRenderableWidget(Button.builder(Component.literal("Done").withStyle(ChatFormatting.GREEN), b -> onClose())
				.bounds(cx + 4, y, 80, 20)
				.build());
	}

	private void reset() {
		Raffle r = r();
		r.hudScale = 1.0F;
		r.hudX = 0.5F;
		r.hudY = 2;
		r.hud = true;
		DonutRaffle.changed();
	}

	@Override
	protected int backgroundColor() {
		// Light, so the game shows through and you can see where the bar will sit.
		return 0x30000000;
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Hud.Place p = place();

		if (p.contains(event.x(), event.y())) {
			// Named constants: 26.3 numbers the buttons left = 1, right = 3; 1.21.11 uses 0 and 1.
			if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
				r().hud = !r().hud;
				DonutRaffle.changed();
				return true;
			}

			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				dragging = true;
				// 26.3 only sends mouseDragged to a screen that says it is dragging.
				setDragging(true);
				offX = event.x() - p.x();
				offY = event.y() - p.y();
				return true;
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (!dragging) {
			return super.mouseDragged(event, dx, dy);
		}

		shift = event.hasShiftDown();
		dragTo(event.x(), event.y());
		return true;
	}

	/**
	 * Moves the bar so the grabbed point sits under the mouse. Called from mouseDragged and every frame
	 * while dragging, because 26.3 does not always send mouseDragged to a screen.
	 */
	private void dragTo(double mx, double my) {
		Hud.Place p = place();
		double left = mx - offX;
		double top = my - offY;
		double centre = left + p.w() / 2.0;
		boolean snap = !shift;
		snappedX = snap && Math.abs(centre - width / 2.0) <= SNAP;
		snappedY = snap && Math.abs(top - 2) <= SNAP;
		Raffle r = r();
		r.hudX = snappedX ? 0.5F : (float) (centre / width);
		r.hudY = snappedY ? 2 : (int) Math.round(top);
		r.normalize();
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (event.input() == InputConstants.KEY_LSHIFT || event.input() == InputConstants.KEY_RSHIFT) {
			shift = false;
		}

		return super.keyReleased(event);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging) {
			dragging = false;
			setDragging(false);
			snappedX = false;
			snappedY = false;
			DonutRaffle.changed();
			return true;
		}

		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		if (scrollY == 0) {
			return false;
		}

		Raffle r = r();
		r.hudScale = Math.round((r.hudScale + (scrollY > 0 ? 0.05F : -0.05F)) * 100) / 100F;
		r.normalize();
		DonutRaffle.changed();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.input() == InputConstants.KEY_LSHIFT || event.input() == InputConstants.KEY_RSHIFT) {
			shift = true;
		}

		int dx = event.isLeft() ? -1 : event.isRight() ? 1 : 0;
		int dy = event.isUp() ? -1 : event.isDown() ? 1 : 0;

		if (dx == 0 && dy == 0) {
			return super.keyPressed(event);
		}

		Raffle r = r();
		r.hudX = (float) ((r.hudX * width + dx) / width);
		r.hudY += dy;
		r.normalize();
		DonutRaffle.changed();
		return true;
	}

	// ------------------------------------------------------------------ drawing

	@Override
	protected void drawBehind(Gfx g, int mouseX, int mouseY) {
		if (dragging) {
			dragTo(mouseX, mouseY);
		}

		Raffle r = r();
		Hud.Place p = place();

		if (dragging && snappedX) {
			g.fill(width / 2, 0, width / 2 + 1, height, GUIDE);
		}

		if (dragging && snappedY) {
			g.fill(0, p.y(), width, p.y() + 1, GUIDE);
		}

		Hud.drawAt(g, font, r, p);

		if (!r.hud) {
			g.fill(p.x(), p.y(), p.x() + p.w(), p.y() + p.h(), 0xB0000000);
			g.centered(font, "Hidden - right-click to show", p.x() + p.w() / 2, p.y() + p.h() / 2 - 4, Hud.DIM);
		}

		boolean hover = p.contains(mouseX, mouseY);
		g.outline(p.x() - 1, p.y() - 1, p.w() + 2, p.h() + 2, dragging || hover ? GUIDE : 0x80FFFFFF);

		// Instruction panel, two lines so it fits small screens.
		String line1 = "Drag to move · Scroll to resize · Right-click to hide";
		String line2 = "Arrows nudge · Shift = no snap · Size " + Math.round(p.scale() * 100) + "%"
				+ (p.scale() < r.hudScale - 0.001 ? " (shrunk to fit)" : "");
		int pw = Math.min(width - 8, Math.max(font.width(line1), font.width(line2)) + 24);
		int px = width / 2 - pw / 2;
		int py = panelTop();
		g.fill(px - 1, py - 1, px + pw + 1, py + PANEL_H + 1, PANEL_EDGE);
		g.fill(px, py, px + pw, py + PANEL_H, PANEL_BG);
		g.centered(font, Hud.fitWidth(font, line1, pw - 8), width / 2, py + 6, 0xFFB0B0B8);
		g.centered(font, Hud.fitWidth(font, line2, pw - 8), width / 2, py + 18, 0xFFB0B0B8);
	}
	@Override
	public void onClose() {
		DonutRaffle.changed();
		Mc.setScreen(parent);
	}
}
