package com.coderabbit.donutraffle;

import java.util.List;

import com.coderabbit.donutraffle.core.Money;
import com.coderabbit.donutraffle.core.Raffle;
import com.coderabbit.donutraffle.mc.BaseScreen;
import com.coderabbit.donutraffle.mc.Gfx;
import com.coderabbit.donutraffle.mc.Mc;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The host's menu (H or /raffle). Top: the same bar the HUD shows; click a spot box to select it.
 * Left: pick the selected spot as winner or empty it, add a player by hand, prize, price, spots.
 * Right: the payout maths, the winner's /pay, and money owed back.
 * Laid out to fit a 427 x 240 GUI (1080p at GUI scale 4, or a small window).
 */
public class RaffleScreen extends BaseScreen {
	private static final int COL = 190;
	private static final int ROW = 22;
	private static final int BAR_Y = 4;

	private int seen = -1;
	private int selected;
	private String nameText = "";
	private String priceText;
	private String winText;
	private String worthText;
	private EditBox priceBox;
	private EditBox worthBox;
	/** True while one box fills in another, so their responders don't undo each other. */
	private boolean syncing;
	private int columnsTop;
	private float barScale = 1;
	private int extrasShown;
	private Button fillPay;

	public RaffleScreen() {
		super(Component.literal("Donut Raffle"));
		selected = DonutRaffle.get().winner;
	}

	private Raffle r() {
		return DonutRaffle.get();
	}

	@Override
	protected void init() {
		Raffle r = r();
		seen = DonutRaffle.version();

		if (selected >= r.spots) {
			selected = -1;
		}

		fillPay = null;
		int cx = width / 2;
		barScale = Hud.fit(font, r, width, 1.0F);
		columnsTop = BAR_Y + Math.round(Hud.height(r) * barScale) + 8;
		int lx = cx - COL - 5;
		int rx = cx + 5;
		int y = columnsTop;

		// ---- left column
		Raffle.Entry sel = r.entry(selected);
		Button win = Button.builder(Component.literal(selected < 0 ? "Click a spot above" : "Winner: spot " + (selected + 1))
						.withStyle(selected < 0 ? ChatFormatting.GRAY : ChatFormatting.GOLD), b -> {
					DonutRaffle.pickWinner(selected);
					rebuildWidgets();
				})
				.bounds(lx, y, 112, 20)
				.build();
		win.active = sel != null && !r.paid && selected != r.winner;
		addRenderableWidget(win);
		Button empty = Button.builder(Component.literal("Empty spot"), b -> {
					if (r().clear(selected)) {
						DonutRaffle.changed();
					}

					rebuildWidgets();
				})
				.bounds(lx + 116, y, COL - 116, 20)
				.build();
		empty.active = sel != null && !r.paid;
		addRenderableWidget(empty);
		y += ROW;

		EditBox nameBox = new EditBox(font, lx, y, 112, 20, Component.literal("Player"));
		nameBox.setMaxLength(17);
		nameBox.setHint(Component.literal("player name").withStyle(ChatFormatting.DARK_GRAY));
		nameBox.setValue(nameText);
		nameBox.setResponder(v -> nameText = v);
		addRenderableWidget(nameBox);
		Button add = Button.builder(Component.literal("Add by hand"), b -> addByHand())
				.bounds(lx + 116, y, COL - 116, 20)
				.build();
		add.active = !r.full() && !r.paid;
		addRenderableWidget(add);
		y += ROW;

		if (worthText == null) {
			worthText = r.prizeValue > 0 ? Money.formatInput(r.prizeValue) : "";
		}

		// Type what the prize is worth and the spot price is worked out from it.
		EditBox worthBox = new EditBox(font, lx + 20, y, 92, 20, Component.literal("Prize worth"));
		worthBox.setMaxLength(20);
		worthBox.setHint(Component.literal("worth, e.g. 81m").withStyle(ChatFormatting.DARK_GRAY));
		worthBox.setValue(worthText);
		worthBox.setResponder(v -> {
			worthText = v;

			if (syncing) {
				return;
			}

			Raffle rr = r();
			double w = v.isBlank() ? 0 : Money.parse(v);

			if (w >= 0 && w != rr.prizeValue) {
				rr.prizeValue = w;
				rr.applyPrizeValue();
				setText(this.priceBox, Money.formatInput(rr.price));
				changedHere();
			}
		});
		addRenderableWidget(worthBox);
		this.worthBox = worthBox;
		addRenderableWidget(Button.builder(Component.literal("Change prize"), b -> Mc.setScreen(new ItemPickerScreen(this)))
				.bounds(lx + 116, y, COL - 116, 20)
				.build());
		y += ROW;

		if (priceText == null) {
			priceText = Money.formatInput(r.price);
		}

		if (winText == null) {
			winText = Money.plain(r.winnerPercent);
		}

		// Both apply as you type (when the text is valid), like the price box on Donut's own menus.
		EditBox priceBox = new EditBox(font, lx + 34, y, 78, 20, Component.literal("Spot price"));
		priceBox.setMaxLength(20);
		priceBox.setHint(Component.literal("10m, 2.5m").withStyle(ChatFormatting.DARK_GRAY));
		priceBox.setValue(priceText);
		priceBox.setResponder(v -> {
			priceText = v;
			double p = Money.parse(v);

			if (!syncing && p > 0 && p != r().price) {
				// A price typed by hand stops following the prize worth.
				r().price = p;
				r().prizeValue = 0;
				setText(this.worthBox, "");
				changedHere();
			}
		});
		addRenderableWidget(priceBox);
		this.priceBox = priceBox;
		EditBox winBox = new EditBox(font, lx + 146, y, COL - 146, 20, Component.literal("Winner gets % of the pot"));
		winBox.setMaxLength(8);
		winBox.setHint(Component.literal("90").withStyle(ChatFormatting.DARK_GRAY));
		winBox.setValue(winText);
		winBox.setResponder(v -> {
			winText = v;
			double w = Raffle.parseWinnerPercent(v, r().spots);

			if (w >= 0 && w != r().winnerPercent) {
				r().winnerPercent = w;

				if (r().prizeValue > 0) {
					r().applyPrizeValue();
					setText(this.priceBox, Money.formatInput(r().price));
				}

				changedHere();
			}
		});
		addRenderableWidget(winBox);
		y += ROW;

		Button less = Button.builder(Component.literal("-"), b -> spots(-1)).bounds(lx + 34, y, 20, 20).build();
		less.active = r.spots > 1;
		addRenderableWidget(less);
		Button more = Button.builder(Component.literal("+"), b -> spots(1)).bounds(lx + 92, y, 20, 20).build();
		more.active = r.spots < Raffle.MAX_SPOTS;
		addRenderableWidget(more);
		addRenderableWidget(Button.builder(Component.literal("Settings"), b -> Mc.setScreen(new SettingsScreen(this)))
				.bounds(lx + 116, y, COL - 116, 20)
				.build());

		// ---- right column
		int ry = columnsTop + 5 * 11 + 3;

		if (r.winner >= 0 && !r.paid) {
			String name = r.winnerName();
			fillPay = addRenderableWidget(Button.builder(fillLabel(),
							b -> DonutRaffle.fillPay(name, r().owed()))
					.bounds(rx, ry, 92, 20)
					.build());
			addRenderableWidget(Button.builder(Component.literal("Mark paid"), b -> {
						r().markPaid(System.currentTimeMillis());
						DonutRaffle.changed();
						rebuildWidgets();
					})
					.bounds(rx + 95, ry, 54, 20)
					.build());
			addRenderableWidget(Button.builder(Component.literal("Unpick"), b -> {
						r().clearWinner();
						DonutRaffle.changed();
						rebuildWidgets();
					})
					.bounds(rx + 152, ry, COL - 152, 20)
					.build());
		}

		ry += ROW + 11;
		int bottom = height - 26;
		List<Row> rows = rows(r);
		extrasShown = Math.max(0, Math.min(rows.size(), (bottom - 2 - ry) / 19));

		for (int i = 0; i < extrasShown; i++) {
			Row row = rows.get(i);
			addRenderableWidget(Button.builder(Component.literal("Refund"), b -> DonutRaffle.fillPay(row.name(), row.amount()))
					.bounds(rx + COL - 70, ry, 46, 18)
					.build());
			addRenderableWidget(Button.builder(Component.literal("x"), b -> {
						row.remove().run();
						DonutRaffle.changed();
						rebuildWidgets();
					})
					.bounds(rx + COL - 20, ry, 20, 18)
					.build());
			ry += 19;
		}

		// ---- bottom row
		Button toggle = Button.builder(Component.literal(r.open ? "Pause entries" : "Open entries"), b -> {
					r().open = !r().open;
					DonutRaffle.changed();
					rebuildWidgets();
				})
				.bounds(cx - 155, bottom + 2, 100, 20)
				.build();
		toggle.active = !r.paid;
		addRenderableWidget(toggle);
		addRenderableWidget(Button.builder(Component.literal(r.paid ? "New round" : "Reset round")
								.withStyle(r.paid ? ChatFormatting.GREEN : ChatFormatting.RED), b -> {
					DonutRaffle.newRound();
					selected = -1;
					rebuildWidgets();
				})
				.bounds(cx - 50, bottom + 2, 100, 20)
				.build());
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(cx + 55, bottom + 2, 100, 20)
				.build());
	}

	@Override
	public void tick() {
		super.tick();

		if (seen != DonutRaffle.version()) {
			// Changed elsewhere (a payment, a command, Settings): show the real values in the boxes,
			// unless what is typed already means the same thing.
			Raffle r = r();

			if (priceText != null && Money.parse(priceText) != r.price) {
				priceText = null;
			}

			if (winText != null && Raffle.parseWinnerPercent(winText, r.spots) != r.winnerPercent) {
				winText = null;
			}

			if (worthText != null && (worthText.isBlank() ? 0 : Money.parse(worthText)) != r.prizeValue) {
				worthText = null;
			}
			rebuildWidgets();
		}
	}

	private static Component fillLabel() {
		return Component.literal("Fill /pay " + Money.format(DonutRaffle.get().owed())).withStyle(ChatFormatting.GREEN);
	}

	private void setText(EditBox box, String text) {
		if (box == null) {
			return;
		}

		syncing = true;

		try {
			box.setValue(text);
		} finally {
			syncing = false;
		}
	}

	/** Saves a change made by typing here without rebuilding (which would drop the text box focus). */
	private void changedHere() {
		DonutRaffle.changed();
		seen = DonutRaffle.version();

		if (fillPay != null) {
			fillPay.setMessage(fillLabel());
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int spot = Hud.spotAt(r(), Math.round(width / 2f / barScale), Math.round(BAR_Y / barScale),
				event.x() / barScale, event.y() / barScale);

		if (spot >= 0) {
			selected = spot;
			rebuildWidgets();
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	/** Middle of a spot box in GUI coordinates (used by the in-game test to click it). */
	public double[] spotCenter(int spot) {
		Raffle r = r();
		int rowW = r.spots * Hud.BOX_W + (r.spots - 1) * Hud.GAP;
		int cx = Math.round(width / 2f / barScale);
		double x = cx - rowW / 2.0 + spot * (Hud.BOX_W + Hud.GAP) + Hud.BOX_W / 2.0;
		double y = Math.round(BAR_Y / barScale) + Hud.PAD + 18 + Hud.BOX_H / 2.0;
		return new double[]{x * barScale, y * barScale};
	}

	private void addByHand() {
		String name = nameText.trim();

		if (!name.matches("\\.?[A-Za-z0-9_]{1,16}")) {
			DonutRaffle.info(Component.literal("Not a player name: " + name).withStyle(ChatFormatting.RED));
			return;
		}

		int spot = r().add(name, System.currentTimeMillis());

		if (spot >= 0) {
			nameText = "";
			selected = spot;
			DonutRaffle.changed();
		}

		rebuildWidgets();
	}

	private void spots(int delta) {
		if (r().setSpots(r().spots + delta)) {
			DonutRaffle.changed();
		} else {
			DonutRaffle.info(Component.literal("Empty the last spot first.").withStyle(ChatFormatting.RED));
		}

		rebuildWidgets();
	}

	@Override
	protected void drawBehind(Gfx g, int mouseX, int mouseY) {
		Raffle r = r();
		int cx = width / 2;
		g.push();
		g.scale(barScale);
		Hud.draw(g, font, r, Math.round(cx / barScale), Math.round(BAR_Y / barScale), true, selected);
		g.pop();

		int lx = cx - COL - 5;
		int rx = cx + 5;
		int y = columnsTop + ROW * 2;

		// Left column labels (the widgets sit next to them).
		g.item(Prizes.stack(r), lx, y + 2);
		y += ROW;
		g.text(font, "Price", lx, y + 6, Hud.TEXT, false);
		g.text(font, "Win %", lx + 116, y + 6, Raffle.parseWinnerPercent(winText, r.spots) < 0 ? Hud.RED : Hud.TEXT, false);
		y += ROW;
		g.text(font, "Spots", lx, y + 6, Hud.TEXT, false);
		g.centered(font, String.valueOf(r.spots), lx + 73, y + 6, Hud.TEXT);

		// Right column: the maths.
		int ry = columnsTop;
		g.fill(rx - 3, ry - 3, rx + COL + 3, ry + 5 * 11, 0x90000000);
		g.text(font, "Pot: " + Money.format(r.pot()) + " (" + r.filled() + "/" + r.spots + " spots)", rx, ry, Hud.TEXT, true);
		ry += 11;
		String mult = r.price > 0 ? " (" + Money.times(r.share() / r.price) + ")" : "";
		g.text(font, "Winner " + DonutRaffle.pct(r.winnerPercent) + ": " + Money.format(r.share()) + mult, rx, ry, Hud.TEXT, true);
		ry += 11;
		g.text(font, "- " + DonutRaffle.pct(r.taxPercent) + " tax: " + Money.format(r.tax()), rx, ry, Hud.DIM, true);
		ry += 11;
		g.text(font, Component.literal("Pay winner: " + Money.format(r.payout())).withStyle(ChatFormatting.BOLD), rx, ry, Hud.MONEY, true);
		g.text(font, Money.full(r.payout()), rx + COL - font.width(Money.full(r.payout())), ry, Hud.MONEY, true);
		ry += 11;
		g.text(font, Hud.fitWidth(font, "Costs " + Money.format(r.payoutCost()) + " with tax, you keep " + Money.format(r.kept()), COL),
				rx, ry, Hud.DIM, false);
		// Same row as the winner buttons in init().
		ry = columnsTop + 5 * 11 + 3;

		if (r.paid) {
			g.text(font, "Paid out. Start a new round below.", rx, ry + 6, Hud.MONEY, false);
		} else if (r.winner < 0) {
			g.text(font, r.full() ? "Full! Decide the winner in-game." : "Waiting for payments...", rx, ry + 6, Hud.DIM, false);
		}

		ry += ROW + 1;
		List<Row> rows = rows(r);
		String head = "Next round: " + r.queue.size() + "   Owed back: " + r.extras.size()
				+ (rows.size() > extrasShown ? "   (+" + (rows.size() - extrasShown) + ")" : "");
		g.text(font, head, rx, ry, rows.isEmpty() ? Hud.DIM : Hud.GOLD, false);
		ry += 10;

		for (int i = 0; i < extrasShown && i < rows.size(); i++) {
			Row row = rows.get(i);
			g.text(font, Hud.fitWidth(font, row.label(), COL - 74), rx, ry + 5, row.queued() ? Hud.MONEY : Hud.TEXT, false);
			ry += 19;
		}
	}

	/** One line of the list: a queued spot (refund = one price) or money owed back. */
	private record Row(String label, String name, double amount, boolean queued, Runnable remove) {
	}

	/** Next-round queue first (in order), then owed back (newest first). */
	private static List<Row> rows(Raffle r) {
		List<Row> rows = new java.util.ArrayList<>();

		for (int i = 0; i < r.queue.size(); i++) {
			Raffle.Entry e = r.queue.get(i);
			final int index = i;
			rows.add(new Row("next #" + (i + 1) + " " + e.name, e.name, e.amount, true, () -> r.unqueue(index)));
		}

		for (int i = r.extras.size() - 1; i >= 0; i--) {
			Raffle.Extra x = r.extras.get(i);
			rows.add(new Row(x.name + " " + Money.format(x.amount), x.name, x.amount, false, () -> r.dismissExtra(x.id)));
		}

		return rows;
	}

	@Override
	public void onClose() {
		Mc.setScreen(null);
	}
}
