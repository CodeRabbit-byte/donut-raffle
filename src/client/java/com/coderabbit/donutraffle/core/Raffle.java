package com.coderabbit.donutraffle.core;

import java.util.ArrayList;
import java.util.List;

/**
 * One jackpot round plus its settings. Pure logic, no Minecraft classes, so it is unit tested.
 *
 * <p>Flow: players pay the spot price with {@code /pay}; each whole price fills the next open
 * spot. When the spots are full (or whenever you like) you decide the winner in-game and mark
 * their spot. The payout is {@code winnerPercent} of the pot (90% of 9 spots = 8.1x the price),
 * minus {@code taxPercent} of that amount (Donut's 2% /pay tax), so a full 9 x $1M round pays
 * $8.1M - $162K = $7.938M. Your own "You paid NAME $X" line counts towards the payout and closes
 * the round when it is covered. The mod never sends /pay itself.
 */
public final class Raffle {
	public static final int MAX_SPOTS = 9;
	private static final double EPS = 0.005;

	public static final class Entry {
		public String name;
		public double amount;
		public long time;
		/** Added by hand (a missed chat line), not from a payment. */
		public boolean manual;

		public Entry() {
		}

		Entry(String name, double amount, long time, boolean manual) {
			this.name = name;
			this.amount = amount;
			this.time = time;
			this.manual = manual;
		}
	}

	/** Money that came in but did not buy a spot: you probably owe it back. */
	public static final class Extra {
		public long id;
		public String name;
		public double amount;
		public long time;
		public String reason;
	}

	/** A finished round, for the history list. */
	public static final class Round {
		public long ended;
		public String prize;
		public double price;
		public int spots;
		public int filled;
		public double pot;
		public String winner;
		public int winnerSpot;
		public double payout;
		public double paid;
	}

	public enum Sent {
		NONE, WINNER_PART, WINNER_DONE, REFUND
	}

	/** What a received payment did: the spots it took (0-based) and any money left over. */
	public record Received(List<Integer> spots, int queued, Extra extra) {
		public boolean counted() {
			return !spots.isEmpty();
		}
	}

	// ----------------------------------------------------------------- settings

	/** The whole mod. Off: payments are ignored and the bar is hidden. */
	public boolean enabled = true;

	/** Registry id of the item shown as the prize, e.g. "minecraft:elytra". */
	public String prizeItem = "minecraft:elytra";
	/** Optional text instead of the item's own name ("Elytra + 64 rockets"). */
	public String prizeLabel = "";
	public double price = 1_000_000;
	public int spots = MAX_SPOTS;
	public double winnerPercent = 90;
	public double taxPercent = 2;
	/** Keep payments that did not buy a spot in the "owed back" list. */
	public boolean trackExtras = true;
	/** Whole prices paid late go into the next round instead of owed back. */
	public boolean queueLate = true;
	/** Show the bar at the top of the screen. */
	public boolean hud = true;
	public float hudScale = 1.0F;
	/** Pixels from the top edge, to move the bar below a boss bar. */
	public int hudY = 2;
	/** Where the bar's centre sits across the screen, 0 = left edge, 1 = right edge. */
	public float hudX = 0.5F;

	// ----------------------------------------------------------------- round state

	/** Taking entries. Paused rounds ignore payments for spots. */
	public boolean open = true;
	/** One slot per spot; null = open. */
	public List<Entry> entries = new ArrayList<>();
	/** 0-based spot of the winner, or -1. */
	public int winner = -1;
	/** What you have sent the winner so far. */
	public double paidOut;
	/** The payout is covered and the round is over. */
	public boolean paid;
	public List<Extra> extras = new ArrayList<>();
	/** Late payers, one entry per spot, waiting for the next round. */
	public List<Entry> queue = new ArrayList<>();
	/** Set by onSent when it picked the winner itself. Not saved. */
	private transient boolean autoPicked;
	public List<Round> history = new ArrayList<>();
	public long nextId = 1;

	public Raffle() {
		normalize();
	}

	/** Fixes up a freshly loaded or resized raffle: spot list length, bounds. */
	public void normalize() {
		spots = Math.max(1, Math.min(MAX_SPOTS, spots));

		if (entries == null) {
			entries = new ArrayList<>();
		}

		while (entries.size() < spots) {
			entries.add(null);
		}

		while (entries.size() > spots) {
			entries.remove(entries.size() - 1);
		}

		for (int i = 0; i < entries.size(); i++) {
			Entry e = entries.get(i);

			if (e != null && (e.name == null || e.name.isBlank())) {
				entries.set(i, null);
			}
		}

		if (winner >= spots || (winner >= 0 && entries.get(winner) == null)) {
			winner = -1;
		}

		if (queue == null) {
			queue = new ArrayList<>();
		}

		queue.removeIf(e -> e == null || e.name == null || e.name.isBlank());

		if (extras == null) {
			extras = new ArrayList<>();
		}

		if (history == null) {
			history = new ArrayList<>();
		}

		if (prizeItem == null || prizeItem.isBlank()) {
			prizeItem = "minecraft:elytra";
		}

		if (prizeLabel == null) {
			prizeLabel = "";
		}

		hudScale = (float) clamp(hudScale, 0.5, 2.0);
		hudY = (int) clamp(hudY, 0, 2000);
		hudX = (float) clamp(hudX, 0, 1);
		winnerPercent = clamp(winnerPercent, 0, 100);
		taxPercent = clamp(taxPercent, 0, 100);
	}

	/**
	 * The winner's share as typed: "87", "87%", or a multiple of the spot price like "8.1x"
	 * (8.1x of 9 spots = 90%). Returns -1 if it isn't a number from 0 to 100%.
	 */
	public static double parseWinnerPercent(String text, int spots) {
		if (text == null) {
			return -1;
		}

		String s = text.trim().toLowerCase(java.util.Locale.ROOT).replace(" ", "");
		boolean multiple = s.endsWith("x");
		s = s.replace("%", "").replace("x", "");

		try {
			double v = Double.parseDouble(s);

			if (multiple) {
				v = v / Math.max(1, spots) * 100.0;
			}

			v = Math.round(v * 10_000.0) / 10_000.0;
			return v >= 0 && v <= 100 && !Double.isNaN(v) ? v : -1;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** The winner's share of a full pot as a multiple of the spot price (90% of 9 spots = 8.1). */
	public double multiple() {
		return winnerPercent / 100.0 * spots;
	}

	private static double clamp(double v, double lo, double hi) {
		return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
	}

	// ----------------------------------------------------------------- queries

	public Entry entry(int spot) {
		return spot >= 0 && spot < entries.size() ? entries.get(spot) : null;
	}

	public int filled() {
		int n = 0;

		for (Entry e : entries) {
			if (e != null) {
				n++;
			}
		}

		return n;
	}

	public boolean full() {
		return filled() >= spots;
	}

	/** Payments go to spots only while open, before a winner is picked, with a price set. */
	public boolean accepting() {
		return open && winner < 0 && !paid && price > 0 && !full();
	}

	/** Money in: the sum of what the filled spots paid. */
	public double pot() {
		double sum = 0;

		for (Entry e : entries) {
			if (e != null) {
				sum += e.amount;
			}
		}

		return Money.cents(sum);
	}

	/** The pot if every spot is filled at the current price. */
	public double fullPot() {
		return Money.cents(price * spots);
	}

	/** The winner's share before tax: 90% of the pot. */
	public static double share(double pot, double winnerPercent) {
		return Money.cents(pot * winnerPercent / 100.0);
	}

	/** 2% of the share. */
	public static double tax(double share, double taxPercent) {
		return Money.cents(share * taxPercent / 100.0);
	}

	/** What to /pay the winner: the share minus the tax on it. */
	public static double payout(double pot, double winnerPercent, double taxPercent) {
		double s = share(pot, winnerPercent);
		return Money.cents(s - tax(s, taxPercent));
	}

	public double share() {
		return share(pot(), winnerPercent);
	}

	public double tax() {
		return tax(share(), taxPercent);
	}

	public double payout() {
		return payout(pot(), winnerPercent, taxPercent);
	}

	/** Donut charges the sender the tax on top, so sending the payout costs this much. */
	public double payoutCost() {
		return Money.cents(payout() + tax(payout(), taxPercent));
	}

	/** What you keep after paying the winner (pot minus what sending the payout costs). */
	public double kept() {
		return Money.cents(pot() - payoutCost());
	}

	public double owed() {
		return Math.max(0, Money.cents(payout() - paidOut));
	}

	public String winnerName() {
		Entry e = entry(winner);
		return e == null ? null : e.name;
	}

	/** How many spots this player holds. */
	public int spotsOf(String name) {
		int n = 0;

		for (Entry e : entries) {
			if (e != null && e.name.equalsIgnoreCase(name)) {
				n++;
			}
		}

		return n;
	}

	// ----------------------------------------------------------------- payments

	/**
	 * Someone paid you. Each whole price buys the next open spot. Whole prices that arrive late
	 * (spots full, winner picked, round paid out) are queued for the next round when
	 * {@code queueLate} is on. Anything else is kept as owed back.
	 */
	public Received onReceived(String name, double amount, long now) {
		List<Integer> taken = new ArrayList<>();
		int queued = 0;
		String reason;

		if (price <= 0) {
			reason = "no price set";
		} else if (!open && !paid && winner < 0) {
			reason = "entries paused";
		} else {
			int want = (int) Math.floor((amount + EPS) / price);
			boolean canFill = open && winner < 0 && !paid;
			int i = 0;

			while (canFill && taken.size() < want && i < entries.size()) {
				if (entries.get(i) == null) {
					entries.set(i, new Entry(name, price, now, false));
					taken.add(i);
				}

				i++;
			}

			int late = want - taken.size();

			if (late > 0 && queueLate) {
				for (int k = 0; k < late; k++) {
					queue.add(new Entry(name, price, now, false));
				}

				queued = late;
			}

			String lateReason = paid ? "round already paid out" : winner >= 0 ? "winner already picked" : "spots full";
			reason = want == 0 ? "less than the spot price" : late > 0 && !queueLate ? lateReason : "not a whole spot";
		}

		double left = Money.cents(amount - (taken.size() + queued) * price);
		Extra extra = null;

		if (left > EPS && trackExtras) {
			extra = new Extra();
			extra.id = nextId++;
			extra.name = name;
			extra.amount = left;
			extra.time = now;
			extra.reason = reason;
			extras.add(extra);
		}

		return new Received(taken, queued, extra);
	}

	/** First spot this player holds, or -1. */
	public int firstSpotOf(String name) {
		for (int i = 0; i < entries.size(); i++) {
			Entry e = entries.get(i);

			if (e != null && e.name.equalsIgnoreCase(name)) {
				return i;
			}
		}

		return -1;
	}

	/**
	 * You paid someone. If they won, it counts towards the payout. With no winner picked yet, paying
	 * a spot holder at least half the payout makes them the winner (you paid them the jackpot).
	 * Otherwise it settles a matching refund or takes them out of the next-round queue.
	 */
	public Sent onSent(String name, double amount, long now) {
		// With no winner yet, an exact refund wins over "this looks like the jackpot".
		if (winner < 0 && refund(name, amount)) {
			return Sent.REFUND;
		}

		if (winner < 0 && !paid && price > 0 && amount + EPS >= payout() * 0.5) {
			int spot = firstSpotOf(name);

			if (spot >= 0) {
				setWinner(spot);
				autoPicked = true;
			}
		}

		String w = winnerName();

		if (w != null && !paid && w.equalsIgnoreCase(name)) {
			paidOut = Money.cents(paidOut + amount);

			if (paidOut + EPS >= payout()) {
				finish(now);
				return Sent.WINNER_DONE;
			}

			return Sent.WINNER_PART;
		}

		return refund(name, amount) ? Sent.REFUND : Sent.NONE;
	}

	/** Settles a matching owed-back entry, or removes refunded spots from the queue. */
	private boolean refund(String name, double amount) {
		for (int i = 0; i < extras.size(); i++) {
			Extra x = extras.get(i);

			if (x.name.equalsIgnoreCase(name) && Math.abs(x.amount - amount) <= EPS + 0.01) {
				extras.remove(i);
				return true;
			}
		}

		// Refunding queued spots: one price per queued spot, newest first.
		int n = price > 0 ? (int) Math.round(amount / price) : 0;

		if (n > 0 && Math.abs(n * price - amount) <= EPS + 0.01) {
			int removed = 0;

			for (int i = queue.size() - 1; i >= 0 && removed < n; i--) {
				if (queue.get(i).name.equalsIgnoreCase(name)) {
					queue.remove(i);
					removed++;
				}
			}

			return removed > 0;
		}

		return false;
	}

	/** True once after {@link #onSent} picked the winner by itself; reading clears it. */
	public boolean takeAutoPicked() {
		boolean b = autoPicked;
		autoPicked = false;
		return b;
	}

	public boolean unqueue(int index) {
		if (index < 0 || index >= queue.size()) {
			return false;
		}

		queue.remove(index);
		return true;
	}
	// ----------------------------------------------------------------- hosting actions

	public boolean setWinner(int spot) {
		if (paid || entry(spot) == null) {
			return false;
		}

		winner = spot;
		paidOut = 0;
		return true;
	}

	public void clearWinner() {
		if (!paid) {
			winner = -1;
			paidOut = 0;
		}
	}

	/** Gives the next open spot to a player by hand. Returns the spot or -1 if full. */
	public int add(String name, long now) {
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i) == null) {
				entries.set(i, new Entry(name, price, now, true));
				return i;
			}
		}

		return -1;
	}

	/** Empties a spot. Clearing the winner's spot clears the winner too. */
	public boolean clear(int spot) {
		if (paid || entry(spot) == null) {
			return false;
		}

		entries.set(spot, null);

		if (winner == spot) {
			winner = -1;
			paidOut = 0;
		}

		return true;
	}

	/** Changes the number of spots. Refused if a filled spot would be cut off. */
	public boolean setSpots(int n) {
		if (n < 1 || n > MAX_SPOTS) {
			return false;
		}

		for (int i = n; i < entries.size(); i++) {
			if (entries.get(i) != null) {
				return false;
			}
		}

		spots = n;
		normalize();
		return true;
	}

	/** Marks the payout done by hand (if the chat line was missed). */
	public void markPaid(long now) {
		if (winner >= 0 && !paid) {
			paidOut = Math.max(paidOut, payout());
			finish(now);
		}
	}

	private void finish(long now) {
		paid = true;
		open = false;
		Round r = new Round();
		r.ended = now;
		r.prize = prizeItem;
		r.price = price;
		r.spots = spots;
		r.filled = filled();
		r.pot = pot();
		r.winner = winnerName();
		r.winnerSpot = winner + 1;
		r.payout = payout();
		r.paid = paidOut;
		history.add(0, r);

		while (history.size() > 100) {
			history.remove(history.size() - 1);
		}
	}

	/**
	 * Empties the spots and opens a new round with the same prize and price. Late payers in the queue
	 * take the first spots, in the order they paid. Returns how many came from the queue.
	 */
	public int newRound() {
		for (int i = 0; i < entries.size(); i++) {
			entries.set(i, null);
		}

		winner = -1;
		paidOut = 0;
		paid = false;
		open = true;
		autoPicked = false;
		int moved = 0;

		while (moved < entries.size() && !queue.isEmpty()) {
			Entry e = queue.remove(0);
			entries.set(moved++, e);
		}

		return moved;
	}

	public boolean dismissExtra(long id) {
		return extras.removeIf(x -> x.id == id);
	}

	public Extra extra(long id) {
		for (Extra x : extras) {
			if (x.id == id) {
				return x;
			}
		}

		return null;
	}
}
