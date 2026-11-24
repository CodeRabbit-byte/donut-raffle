package com.coderabbit.donutraffle;

import com.coderabbit.donutraffle.core.Money;
import com.coderabbit.donutraffle.core.Raffle;
import com.coderabbit.donutraffle.mc.Gfx;
import com.coderabbit.donutraffle.mc.Mc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/**
 * The jackpot bar at the top centre of the screen:
 * <pre>
 *   [elytra] ELYTRA JACKPOT  -  $1M a spot  -  winner gets 8.1x
 *   [1 Steve][2 Alex][3 open] ... [9 open]
 *   Pot $2M / $9M  -  7 spots left  -  /pay YourName 1000000
 * </pre>
 */
public final class Hud {
	public static final int BG = 0xC0101018;
	public static final int BORDER = 0x80FFB627;
	public static final int GOLD = 0xFFFFC23B;
	public static final int TEXT = 0xFFEDEDED;
	public static final int DIM = 0xFF9A9AA6;
	public static final int MONEY = 0xFF55FF7A;
	public static final int OPEN = 0xFF6E6E7A;
	public static final int TAKEN = 0xFF1C3A2A;
	public static final int TAKEN_EDGE = 0xFF3FBF6F;
	public static final int WIN = 0xFF4A3A0A;
	public static final int RED = 0xFFFF5555;
	public static final int PAID_EDGE = 0xFF3BFF6A;
	public static final int PAID_FILL = 0xC0127A2E;

	static final int BOX_W = 48;
	static final int BOX_H = 24;
	static final int GAP = 3;
	static final int PAD = 5;

	private Hud() {
	}

	public static void render(Gfx g) {
		Raffle r = DonutRaffle.get();

		if (!r.enabled || !r.hud || Mc.hudHidden() || Mc.screen() instanceof com.coderabbit.donutraffle.mc.BaseScreen) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		drawAt(g, font, r, place(font, r, g.width(), g.height()));
	}

	/** Where the bar is on screen, in GUI pixels: top-left corner, size, and the scale it is drawn at. */
	public record Place(float scale, int x, int y, int w, int h) {
		public boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	/** The bar's place from its saved centre (hudX) and top (hudY), kept fully on screen. */
	public static Place place(Font font, Raffle r, int screenW, int screenH) {
		float s = fit(font, r, screenW, r.hudScale);
		int w = Math.round((barWidth(font, r) + 2) * s);
		int h = Math.round((height(r) + 2) * s);
		int cx = Math.round(r.hudX * screenW);
		int x = Math.max(0, Math.min(cx - w / 2, screenW - w));
		int y = Math.max(0, Math.min(r.hudY, screenH - h));
		return new Place(s, x, y, w, h);
	}

	public static void drawAt(Gfx g, Font font, Raffle r, Place p) {
		g.push();
		g.scale(p.scale());
		draw(g, font, r, Math.round((p.x() + p.w() / 2f) / p.scale()), Math.round(p.y() / p.scale()) + 1, false);
		g.pop();
	}

	/** Height of the bar, for laying out the menu screen. One line more while people wait for the next round. */
	public static int height(Raffle r) {
		return PAD + 18 + BOX_H + 4 + 10 + (r.queue.isEmpty() ? 0 : 12) + PAD;
	}

	public static int width(Raffle r) {
		return r.spots * BOX_W + (r.spots - 1) * GAP + PAD * 2;
	}

	private static String title(Raffle r) {
		// Just "JACKPOT": the prize shows as the item icon next to it.
		return "JACKPOT";
	}

	private static String priceText(Raffle r) {
		return Money.format(r.price) + " a spot";
	}

	private static String multText(Raffle r) {
		return "winner gets " + Money.times(Raffle.share(r.fullPot(), r.winnerPercent) / Math.max(r.price, 1e-9));
	}

	private static int headWidth(Font font, Raffle r) {
		return 18 + font.width(title(r)) + 12 + font.width(priceText(r)) + 12 + font.width(multText(r));
	}

	/** Full width of the bar including its padding. */
	public static int barWidth(Font font, Raffle r) {
		return Math.max(width(r), Math.max(headWidth(font, r), font.width(footer(r))) + PAD * 2);
	}

	/** The wanted scale, shrunk if the bar would not fit across the screen. */
	public static float fit(Font font, Raffle r, int screenWidth, float wanted) {
		return Math.min(wanted, (screenWidth - 6) / (float) (barWidth(font, r) + 2));
	}

	/** Which spot box is at (mx, my), in the bar's own unscaled coordinates; -1 if none. */
	public static int spotAt(Raffle r, int cx, int y, double mx, double my) {
		int rowW = r.spots * BOX_W + (r.spots - 1) * GAP;
		int bx = cx - rowW / 2;
		int by = y + PAD + 18;

		if (my < by || my >= by + BOX_H || mx < bx) {
			return -1;
		}

		int i = (int) ((mx - bx) / (BOX_W + GAP));
		double inBox = (mx - bx) - i * (BOX_W + GAP);
		return i < r.spots && inBox < BOX_W ? i : -1;
	}

	/**
	 * Draws the bar centred on {@code cx}. {@code selected} is the spot highlighted in the menu (-1 for none).
	 */
	public static void draw(Gfx g, Font font, Raffle r, int cx, int y, boolean inMenu) {
		draw(g, font, r, cx, y, inMenu, -1);
	}

	public static void draw(Gfx g, Font font, Raffle r, int cx, int y, boolean inMenu, int selected) {
		String title = title(r);
		String priceText = priceText(r);
		String mult = multText(r);
		int headW = headWidth(font, r);
		String foot = footer(r);
		int w = barWidth(font, r);
		int h = height(r);
		int x = cx - w / 2;

		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, BORDER);
		g.fill(x, y, x + w, y + h, BG);

		// Header: prize icon, title, price, multiple.
		int hx = cx - headW / 2;
		g.item(Prizes.stack(r), hx, y + PAD - 1);
		hx += 18;
		g.text(font, title, hx, y + PAD + 4, GOLD, true);
		hx += font.width(title) + 12;
		g.text(font, priceText, hx, y + PAD + 4, MONEY, true);
		hx += font.width(priceText) + 12;
		g.text(font, mult, hx, y + PAD + 4, TEXT, true);

		// Spots.
		int rowW = r.spots * BOX_W + (r.spots - 1) * GAP;
		int bx = cx - rowW / 2;
		int by = y + PAD + 18;

		for (int i = 0; i < r.spots; i++) {
			Raffle.Entry e = r.entry(i);
			boolean win = i == r.winner;
			// Gold once picked, green once you have paid them.
			boolean paidWin = win && (r.paid || r.paidOut > 0);
			int edge = paidWin ? PAID_EDGE : win ? GOLD : e != null ? 0x50FFFFFF : 0x30FFFFFF;
			int fill = paidWin ? PAID_FILL : win ? WIN : e != null ? 0x70000000 : 0x40000000;
			g.fill(bx, by, bx + BOX_W, by + BOX_H, edge);
			g.fill(bx + 1, by + 1, bx + BOX_W - 1, by + BOX_H - 1, fill);

			if (i == selected) {
				g.outline(bx - 1, by - 1, BOX_W + 2, BOX_H + 2, 0xFFFFFFFF);
			}

			// "#1 [face]" on top, the name under it (3/4 size so ~10 characters fit).
			g.text(font, "#" + (i + 1), bx + 3, by + 2, paidWin ? 0xFFB6FFC8 : e != null || win ? GOLD : DIM, true);

			if (e != null) {
				g.face(Faces.skin(e.name), bx + BOX_W - 11, by + 2, 8);
			}

			String name = e == null ? "open" : e.name;
			g.push();
			g.translate(bx + BOX_W / 2f, by + 13);
			g.scale(0.75F);
			String fit = fitWidth(font, name, (int) ((BOX_W - 4) / 0.75F));
			g.centered(font, fit, 0, 0, e == null ? OPEN : paidWin ? 0xFFFFFFFF : win ? GOLD : TEXT);
			g.pop();
			bx += BOX_W + GAP;
		}

		int fy = by + BOX_H + 4;
		g.centered(font, foot, cx, fy, footColor(r));

		if (!r.queue.isEmpty()) {
			drawQueue(g, font, r, x + PAD, fy + 12, w - PAD * 2);
		}
	}

	/** "Next round: 3   #1 [face] LaserCannon7437   #2 [face] koilt319 ..." cut to the bar's width. */
	private static void drawQueue(Gfx g, Font font, Raffle r, int x, int y, int maxW) {
		String head = "Next round: " + r.queue.size();
		int end = x + maxW;
		g.text(font, head, x, y, GOLD, true);
		int qx = x + font.width(head) + 8;

		for (int i = 0; i < r.queue.size(); i++) {
			Raffle.Entry e = r.queue.get(i);
			String num = "#" + (i + 1);
			int itemW = font.width(num) + 2 + 8 + 2 + font.width(e.name) + 8;
			int left = r.queue.size() - i;
			String more = "+" + left + " more";

			// Leave room for "+N more" unless this is the last one.
			if (qx + itemW > end || (i < r.queue.size() - 1 && qx + itemW + font.width("+99 more") > end)) {
				g.text(font, more, Math.min(qx, end - font.width(more)), y, DIM, true);
				return;
			}

			g.text(font, num, qx, y, GOLD, true);
			qx += font.width(num) + 2;
			g.face(Faces.skin(e.name), qx, y, 8);
			qx += 10;
			g.text(font, e.name, qx, y, TEXT, true);
			qx += font.width(e.name) + 8;
		}
	}
	static String footer(Raffle r) {
		if (!r.enabled) {
			return "RAFFLE OFF  -  payments are not tracked";
		}

		if (r.paid) {
			return "PAID  -  " + r.winnerName() + " won " + Money.format(r.paidOut);
		}

		if (r.winner >= 0) {
			return "WINNER: " + r.winnerName() + " (spot " + (r.winner + 1) + ")  -  pays " + Money.format(r.payout())
					+ (r.paidOut > 0 ? "  -  " + Money.format(r.owed()) + " left" : "");
		}

		int left = r.spots - r.filled();
		String pot = "Pot " + Money.format(r.pot()) + " / " + Money.format(r.fullPot());

		if (!r.open) {
			return pot + "  -  ENTRIES PAUSED";
		}

		if (left == 0) {
			return pot + "  -  FULL, picking the winner";
		}

		return pot + "  -  " + left + (left == 1 ? " spot" : " spots") + " left  -  /pay " + Mc.playerName() + " "
				+ Money.formatInput(r.price);
	}

	static int footColor(Raffle r) {
		if (!r.enabled) {
			return RED;
		}

		if (r.paid) {
			return MONEY;
		}

		if (r.winner >= 0 || r.full()) {
			return GOLD;
		}

		return r.open ? TEXT : RED;
	}

	static String fitWidth(Font font, String s, int max) {
		if (font.width(s) <= max) {
			return s;
		}

		return font.plainSubstrByWidth(s, max - font.width("..")) + "..";
	}
}
