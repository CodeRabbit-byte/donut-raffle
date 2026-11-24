package com.coderabbit.donutraffle;

import com.coderabbit.donutraffle.core.Money;
import com.coderabbit.donutraffle.core.Raffle;
import com.coderabbit.donutraffle.mc.BaseScreen;
import com.coderabbit.donutraffle.mc.Gfx;
import com.coderabbit.donutraffle.mc.Mc;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Winner %, tax %, the top bar's size and height, and whether leftover money is tracked. */
public class SettingsScreen extends BaseScreen {
	private final Screen parent;
	private EditBox winnerBox;
	private EditBox taxBox;
	private String winnerText;
	private String taxText;
	private int seen = -1;

	public SettingsScreen(Screen parent) {
		super(Component.literal("Donut Raffle settings"));
		this.parent = parent;
		Raffle r = DonutRaffle.get();
		winnerText = Money.plain(r.winnerPercent);
		taxText = Money.plain(r.taxPercent);
	}

	@Override
	protected void init() {
		Raffle r = DonutRaffle.get();
		seen = DonutRaffle.version();
		int cx = width / 2;
		int x = cx + 10;
		int y = 28;

		winnerBox = new EditBox(font, x, y, 60, 20, Component.literal("Winner percent"));
		winnerBox.setMaxLength(6);
		winnerBox.setValue(winnerText);
		winnerBox.setResponder(v -> winnerText = v);
		addRenderableWidget(winnerBox);
		y += 22;

		taxBox = new EditBox(font, x, y, 60, 20, Component.literal("Tax percent"));
		taxBox.setMaxLength(6);
		taxBox.setValue(taxText);
		taxBox.setResponder(v -> taxText = v);
		addRenderableWidget(taxBox);
		addRenderableWidget(Button.builder(Component.literal("Apply %"), b -> applyPercents())
				.bounds(x + 64, y - 11, 60, 20)
				.build());
		y += 26;

		addRenderableWidget(Button.builder(Component.literal("Top bar: " + (r.hud ? "on" : "off")), b -> {
					r.hud = !r.hud;
					DonutRaffle.changed();
					rebuildWidgets();
				})
				.bounds(x, y, 124, 20)
				.build());
		y += 22;

		addRenderableWidget(Button.builder(Component.literal("Move / resize bar..."), b -> Mc.setScreen(new HudEditScreen(this)))
				.bounds(x, y, 124, 20)
				.build());
		y += 22;

		addRenderableWidget(Button.builder(Component.literal("Owed-back list: " + (r.trackExtras ? "on" : "off")), b -> {
					r.trackExtras = !r.trackExtras;
					DonutRaffle.changed();
					rebuildWidgets();
				})
				.bounds(x, y, 124, 20)
				.build());

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(cx - 50, height - 26, 100, 20)
				.build());
	}

	@Override
	public void tick() {
		super.tick();

		if (seen != DonutRaffle.version()) {
			rebuildWidgets();
		}
	}

	private void applyPercents() {
		double w = Raffle.parseWinnerPercent(winnerText, DonutRaffle.get().spots);
		double t = parsePercent(taxText);

		if (w < 0 || t < 0) {
			DonutRaffle.info(Component.literal("Percent must be a number from 0 to 100.").withStyle(ChatFormatting.RED));
			return;
		}

		Raffle r = DonutRaffle.get();
		r.winnerPercent = w;
		r.taxPercent = t;
		DonutRaffle.changed();
	}

	private static double parsePercent(String s) {
		try {
			double v = Double.parseDouble(s.trim().replace("%", ""));
			return v >= 0 && v <= 100 ? v : -1;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	@Override
	protected void drawBehind(Gfx g, int mouseX, int mouseY) {
		Raffle r = DonutRaffle.get();
		int cx = width / 2;
		int lx = cx - 150;
		g.centered(font, Component.literal("Donut Raffle settings").withStyle(ChatFormatting.BOLD), cx, 10, Hud.GOLD);
		int y = 34;
		g.text(font, "Winner gets (% of pot)", lx, y, Hud.TEXT, false);
		y += 22;
		g.text(font, "/pay tax taken off (%)", lx, y, Hud.TEXT, false);
		y += 26;
		g.text(font, "Jackpot bar at the top", lx, y, Hud.TEXT, false);
		y += 22;
		g.text(font, "Bar position and size (" + Math.round(r.hudScale * 100) + "%)", lx, y, Hud.TEXT, false);
		y += 22;
		g.text(font, "Keep money that buys no spot", lx, y, Hud.TEXT, false);
		y += 22;

		Raffle f = fake(r, r.fullPot());
		g.centered(font, "Example, all " + r.spots + " spots at " + Money.format(r.price) + ": pot " + Money.format(f.pot()), cx, y, Hud.DIM);
		g.centered(font, DonutRaffle.pct(r.winnerPercent) + " = " + Money.format(f.share()) + " (" + Money.times(f.share() / Math.max(r.price, 1e-9))
				+ "), - " + DonutRaffle.pct(r.taxPercent) + " tax " + Money.format(f.tax()) + " = pay " + Money.format(f.payout()), cx, y + 11, Hud.MONEY);
	}

	/** A copy of the settings with every spot paid, for the example line. */
	private static Raffle fake(Raffle r, double pot) {
		Raffle f = new Raffle();
		f.price = r.price;
		f.spots = r.spots;
		f.winnerPercent = r.winnerPercent;
		f.taxPercent = r.taxPercent;
		f.normalize();

		for (int i = 0; i < f.spots; i++) {
			f.onReceived("x", r.price, 0);
		}

		return f;
	}

	@Override
	public void onClose() {
		Mc.setScreen(parent);
	}
}
