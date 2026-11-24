package com.coderabbit.donutraffle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CoreTest {
	private static final double C = 0.001;

	@Test
	void parsesDonutPayLines() {
		PayLine.Pay p = PayLine.parse("Steve paid you $1M.");
		assertEquals("Steve", p.name());
		assertEquals(1_000_000, p.amount(), C);
		assertFalse(p.sent());

		assertEquals(1_500_000, PayLine.parse("Alex paid you $1.5M.").amount(), C);
		assertEquals(2_000_000, PayLine.parse("Bob paid you $2,000,000.").amount(), C);
		assertEquals(250_000, PayLine.parse("Carl paid you $ 250K").amount(), C);
		assertEquals(".BedrockGuy", PayLine.parse(".BedrockGuy paid you $3.1B.").name());

		PayLine.Pay s = PayLine.parse("You paid Steve $7.938M (+$158.76K tax)");
		assertTrue(s.sent());
		assertEquals("Steve", s.name());
		assertEquals(7_938_000, s.amount(), C);
		assertEquals(90, PayLine.parse("You paid NAME $ 90 (+$ 2 tax)").amount(), C);
	}

	@Test
	void ignoresChatSpoofs() {
		assertNull(PayLine.parse("Steve: Bob paid you $1M."));
		assertNull(PayLine.parse("<Steve> Bob paid you $1M."));
		assertNull(PayLine.parse("Steve paid you nothing"));
		assertNull(PayLine.parse("ThisNameIsWayTooLongForMc paid you $1M."));
	}

	@Test
	void moneyFormats() {
		assertEquals("$7.938M", Money.format(7_938_000));
		assertEquals("$8.1M", Money.format(8_100_000));
		assertEquals("$162K", Money.format(162_000));
		assertEquals("7938000", Money.plain(7_938_000));
		assertEquals("12.5", Money.plain(12.5));
		assertEquals("$7,938,000", Money.full(7_938_000));
		assertEquals(1_500_000, Money.parse("1.5m"), C);
		assertEquals(-1, Money.parse("abc"), C);
		assertEquals("8.1x", Money.times(8.1));
	}

	/** The user's rule: 90% of the pot (8.1x the price for 9 spots), minus 2% tax on that. */
	@Test
	void payoutIsNinetyPercentMinusTwoPercentTax() {
		Raffle r = new Raffle();
		r.price = 1_000_000;

		for (int i = 0; i < 9; i++) {
			r.onReceived("P" + i, 1_000_000, i);
		}

		assertTrue(r.full());
		assertEquals(9_000_000, r.pot(), C);
		assertEquals(8_100_000, r.share(), C);
		assertEquals(162_000, r.tax(), C);
		assertEquals(7_938_000, r.payout(), C);
		assertEquals(8.1, r.share() / r.price, C);
		// Sending $7.938M costs another 2% on top: $8,096,760. You keep $903,240.
		assertEquals(8_096_760, r.payoutCost(), C);
		assertEquals(903_240, r.kept(), C);

		// A $1M pot: $900K share - $18K tax = $882K.
		assertEquals(882_000, Raffle.payout(1_000_000, 90, 2), C);
		// 5 spots open at $1M: 4.5x share, pays $4.41M.
		assertEquals(4_410_000, Raffle.payout(5_000_000, 90, 2), C);
	}

	@Test
	void winnerShareIsCustomisable() {
		assertEquals(87, Raffle.parseWinnerPercent("87", 9), C);
		assertEquals(87.5, Raffle.parseWinnerPercent("87.5%", 9), C);
		assertEquals(90, Raffle.parseWinnerPercent("8.1x", 9), C);
		assertEquals(80, Raffle.parseWinnerPercent("4X", 5), C);
		assertEquals(-1, Raffle.parseWinnerPercent("101", 9), C);
		assertEquals(-1, Raffle.parseWinnerPercent("10x", 9), C);
		assertEquals(-1, Raffle.parseWinnerPercent("abc", 9), C);

		Raffle r = new Raffle();
		r.price = 10_000_000;
		r.winnerPercent = 87;

		for (int i = 0; i < 9; i++) {
			r.onReceived("P" + i, 10_000_000, i);
		}

		// 87% of $90M = $78.3M (7.83x), - 2% = $76.734M.
		assertEquals(78_300_000, r.share(), C);
		assertEquals(7.83, r.multiple(), C);
		assertEquals(76_734_000, r.payout(), C);
	}

	@Test
	void paymentsFillSpotsInOrderAndMultiplesTakeMore() {
		Raffle r = new Raffle();
		r.price = 1_000_000;

		Raffle.Received a = r.onReceived("Steve", 1_000_000, 1);
		assertEquals(List.of(0), a.spots());
		assertNull(a.extra());

		Raffle.Received b = r.onReceived("Alex", 3_000_000, 2);
		assertEquals(List.of(1, 2, 3), b.spots());
		assertEquals(3, r.spotsOf("alex"));

		// 1.5 spots: one spot, $500K owed back.
		Raffle.Received c = r.onReceived("Bob", 1_500_000, 3);
		assertEquals(List.of(4), c.spots());
		assertNotNull(c.extra());
		assertEquals(500_000, c.extra().amount, C);
		assertEquals("not a whole spot", c.extra().reason);

		// Under the price: no spot.
		Raffle.Received d = r.onReceived("Carl", 100_000, 4);
		assertTrue(d.spots().isEmpty());
		assertEquals("less than the spot price", d.extra().reason);

		// 4 spots left, Eve pays for 6: 4 taken, 2 queued for the next round.
		Raffle.Received e = r.onReceived("Eve", 6_000_000, 5);
		assertEquals(4, e.spots().size());
		assertEquals(2, e.queued());
		assertNull(e.extra());
		assertTrue(r.full());
		assertEquals(9_000_000, r.pot(), C);
		assertEquals(2, r.extras.size());
		assertEquals(2, r.queue.size());
	}

	@Test
	void pausedOrPickedRoundsDoNotTakeSpots() {
		Raffle r = new Raffle();
		r.price = 1_000;
		r.open = false;
		assertTrue(r.onReceived("Steve", 1_000, 1).spots().isEmpty());
		assertEquals("entries paused", r.extras.get(0).reason);

		r.open = true;
		r.onReceived("Steve", 1_000, 2);
		assertTrue(r.setWinner(0));
		Raffle.Received late = r.onReceived("Alex", 1_000, 3);
		assertTrue(late.spots().isEmpty());
		assertEquals(1, late.queued());
		assertEquals(1, r.extras.size());
	}

	@Test
	void winnerPayoutClosesTheRound() {
		Raffle r = new Raffle();
		r.price = 1_000_000;

		for (int i = 0; i < 9; i++) {
			r.onReceived(i == 4 ? "Winner" : "P" + i, 1_000_000, i);
		}

		assertFalse(r.setWinner(-1));
		assertTrue(r.setWinner(4));
		assertEquals("Winner", r.winnerName());
		assertEquals(Raffle.Sent.NONE, r.onSent("Someone", 7_938_000, 10));
		assertEquals(Raffle.Sent.WINNER_PART, r.onSent("winner", 4_000_000, 11));
		assertEquals(3_938_000, r.owed(), C);
		assertEquals(Raffle.Sent.WINNER_DONE, r.onSent("Winner", 3_938_000, 12));
		assertTrue(r.paid);
		assertFalse(r.open);
		assertEquals(1, r.history.size());
		assertEquals("Winner", r.history.get(0).winner);
		assertEquals(5, r.history.get(0).winnerSpot);
		assertEquals(7_938_000, r.history.get(0).payout, C);

		r.newRound();
		assertEquals(0, r.filled());
		assertTrue(r.open);
		assertFalse(r.paid);
		assertEquals(-1, r.winner);
		assertEquals(1_000_000, r.price, C);
	}

	@Test
	void prizeWorthSetsThePrice() {
		// An elytra worth $81M, 9 spots, winner gets 90%: $10M a spot.
		assertEquals(10_000_000, Raffle.priceForValue(81_000_000, 9, 90), C);
		Raffle r = new Raffle();
		r.prizeValue = 81_000_000;
		r.applyPrizeValue();
		assertEquals(10_000_000, r.price, C);
		assertEquals(81_000_000, Raffle.share(r.fullPot(), r.winnerPercent), C);
		// Fewer spots: dearer spots, same share.
		assertTrue(r.setSpots(5));
		assertEquals(18_000_000, r.price, C);
		assertEquals(-1, Raffle.priceForValue(0, 9, 90), C);
	}

	@Test
	void latePayersGoIntoTheNextRound() {
		Raffle r = new Raffle();
		r.price = 1_000_000;
		r.setSpots(3);
		r.onReceived("A", 1_000_000, 1);
		r.onReceived("B", 1_000_000, 2);

		// 2 wanted, 1 left: one spot now, one queued, nothing owed back.
		Raffle.Received c = r.onReceived("C", 2_000_000, 3);
		assertEquals(1, c.spots().size());
		assertEquals(1, c.queued());
		assertNull(c.extra());
		assertTrue(r.full());

		// Winner picked: whole prices queue, the half spot is owed back.
		r.setWinner(0);
		Raffle.Received d = r.onReceived("D", 1_500_000, 4);
		assertEquals(1, d.queued());
		assertEquals(500_000, d.extra().amount, C);

		// Paid out: still queues.
		r.markPaid(5);
		assertEquals(1, r.onReceived("E", 1_000_000, 6).queued());
		assertEquals(3, r.queue.size());

		// Paused (not paid, no winner): owed back, not queued.
		assertEquals(3, r.newRound());
		assertEquals("C", r.entry(0).name);
		assertEquals("D", r.entry(1).name);
		assertEquals("E", r.entry(2).name);
		assertTrue(r.queue.isEmpty());
		r.newRound();
		r.open = false;
		Raffle.Received f = r.onReceived("F", 1_000_000, 7);
		assertEquals(0, f.queued());
		assertEquals("entries paused", f.extra().reason);

		// More queued than spots: the rest waits for the round after.
		r.open = true;
		r.onReceived("G", 5_000_000, 8);
		assertEquals(2, r.queue.size());
		assertEquals(2, r.newRound());
		assertTrue(r.queue.isEmpty());
		assertEquals(2, r.filled());

		// Queue off: late money is owed back as before.
		r.queueLate = false;
		r.onReceived("H", 1_000_000, 9);
		Raffle.Received h = r.onReceived("I", 1_000_000, 10);
		assertEquals("spots full", h.extra().reason);
	}

	@Test
	void payingASpotHolderTheJackpotMakesThemTheWinner() {
		Raffle r = new Raffle();
		r.price = 1_000_000;

		for (int i = 0; i < 9; i++) {
			r.onReceived(i == 6 ? "Lucky" : "P" + i, 1_000_000, i);
		}

		// Small payments to a holder do not pick them.
		assertEquals(Raffle.Sent.NONE, r.onSent("P1", 1_000_000, 10));
		assertEquals(-1, r.winner);
		// Paying the jackpot does.
		assertEquals(Raffle.Sent.WINNER_DONE, r.onSent("lucky", 7_938_000, 11));
		assertTrue(r.takeAutoPicked());
		assertFalse(r.takeAutoPicked());
		assertEquals(6, r.winner);
		assertTrue(r.paid);

		// A non-holder never becomes the winner.
		r.newRound();
		r.onReceived("A", 1_000_000, 12);
		assertEquals(Raffle.Sent.NONE, r.onSent("Stranger", 9_000_000, 13));
		assertEquals(-1, r.winner);
	}

	@Test
	void refundingQueuedSpotsRemovesThem() {
		Raffle r = new Raffle();
		r.price = 1_000_000;
		r.setSpots(1);
		r.onReceived("A", 1_000_000, 1);
		r.onReceived("B", 2_000_000, 2);
		assertEquals(2, r.queue.size());
		assertEquals(Raffle.Sent.REFUND, r.onSent("B", 2_000_000, 3));
		assertTrue(r.queue.isEmpty());
	}

	@Test
	void refundsMatchOwedBack() {
		Raffle r = new Raffle();
		r.price = 1_000_000;
		r.onReceived("Bob", 1_500_000, 1);
		assertEquals(1, r.extras.size());
		assertEquals(Raffle.Sent.REFUND, r.onSent("bob", 500_000, 2));
		assertTrue(r.extras.isEmpty());
	}

	@Test
	void handEditsAndResize() {
		Raffle r = new Raffle();
		r.price = 10;
		assertEquals(0, r.add("Steve", 1));
		assertTrue(r.entry(0).manual);
		r.onReceived("Alex", 10, 2);
		assertTrue(r.setWinner(1));
		assertTrue(r.clear(1));
		assertEquals(-1, r.winner);
		assertFalse(r.setSpots(0));
		assertTrue(r.setSpots(5));
		assertEquals(5, r.entries.size());
		r.add("A", 3);
		r.add("B", 3);
		r.add("C", 3);
		r.add("D", 3);
		assertEquals(-1, r.add("E", 3));
		assertFalse(r.setSpots(3));
		assertTrue(r.setSpots(9));
		assertEquals(9, r.entries.size());
	}

	@Test
	void jsonRoundTripKeepsEmptySpots() {
		Raffle r = new Raffle();
		r.price = 2_500_000;
		r.prizeItem = "minecraft:dragon_head";
		r.onReceived("Steve", 2_500_000, 1);
		r.add("Alex", 2);
		r.clear(0);
		r.setWinner(1);

		Raffle back = Store.fromJson(Store.toJson(r));
		assertEquals(9, back.entries.size());
		assertNull(back.entry(0));
		assertEquals("Alex", back.entry(1).name);
		assertEquals(1, back.winner);
		assertEquals("minecraft:dragon_head", back.prizeItem);
		assertEquals(2_500_000, back.price, C);

		Raffle broken = Store.fromJson("{not json");
		assertEquals(9, broken.entries.size());
	}
}
