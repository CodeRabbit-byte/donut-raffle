package com.coderabbit.donutraffle;

import java.util.Locale;

import com.coderabbit.donutraffle.core.Money;
import com.coderabbit.donutraffle.core.Raffle;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * {@code /raffle} (client side only, nothing reaches the server). Built with plain Brigadier
 * builders so the same tree registers on both Minecraft versions.
 */
public final class RaffleCommands {
	private RaffleCommands() {
	}

	private static <S> LiteralArgumentBuilder<S> lit(String name) {
		return LiteralArgumentBuilder.literal(name);
	}

	private static <S, T> RequiredArgumentBuilder<S, T> arg(String name, com.mojang.brigadier.arguments.ArgumentType<T> type) {
		return RequiredArgumentBuilder.argument(name, type);
	}

	public static <S> LiteralArgumentBuilder<S> tree() {
		return RaffleCommands.<S>lit("raffle")
				.executes(ctx -> {
					DonutRaffle.open(new RaffleScreen());
					return 1;
				})
				.then(RaffleCommands.<S>lit("status").executes(ctx -> status()))
				.then(RaffleCommands.<S>lit("on").executes(ctx -> {
					DonutRaffle.setEnabled(true);
					return 1;
				}))
				.then(RaffleCommands.<S>lit("off").executes(ctx -> {
					DonutRaffle.setEnabled(false);
					return 1;
				}))
				.then(RaffleCommands.<S>lit("toggle").executes(ctx -> {
					DonutRaffle.setEnabled(!DonutRaffle.get().enabled);
					return 1;
				}))
				.then(RaffleCommands.<S>lit("settings").executes(ctx -> {
					DonutRaffle.open(new SettingsScreen(null));
					return 1;
				}))
				.then(RaffleCommands.<S>lit("hud").executes(ctx -> {
					DonutRaffle.open(new HudEditScreen(null));
					return 1;
				}))
				.then(RaffleCommands.<S>lit("price")
						.then(RaffleCommands.<S, String>arg("amount", StringArgumentType.greedyString())
								.executes(ctx -> price(StringArgumentType.getString(ctx, "amount")))))
				.then(RaffleCommands.<S>lit("payout")
						.then(RaffleCommands.<S, String>arg("percent", StringArgumentType.greedyString())
								.executes(ctx -> payout(StringArgumentType.getString(ctx, "percent")))))
				.then(RaffleCommands.<S>lit("tax")
						.then(RaffleCommands.<S, String>arg("percent", StringArgumentType.greedyString())
								.executes(ctx -> tax(StringArgumentType.getString(ctx, "percent")))))
				.then(RaffleCommands.<S>lit("spots")
						.then(RaffleCommands.<S, Integer>arg("count", IntegerArgumentType.integer(1, Raffle.MAX_SPOTS))
								.executes(ctx -> spots(IntegerArgumentType.getInteger(ctx, "count")))))
				.then(RaffleCommands.<S>lit("prize")
						.executes(ctx -> {
							DonutRaffle.open(new ItemPickerScreen(null));
							return 1;
						})
						.then(RaffleCommands.<S, String>arg("item", StringArgumentType.greedyString())
								.suggests((ctx, b) -> {
									String rem = b.getRemaining().toLowerCase(Locale.ROOT);
									Prizes.search(rem).stream().limit(40).forEach(c -> b.suggest(c.id()));
									return b.buildFuture();
								})
								.executes(ctx -> prize(StringArgumentType.getString(ctx, "item")))))
				.then(RaffleCommands.<S>lit("winner")
						.then(RaffleCommands.<S, Integer>arg("spot", IntegerArgumentType.integer(1, Raffle.MAX_SPOTS))
								.executes(ctx -> winner(IntegerArgumentType.getInteger(ctx, "spot")))))
				.then(RaffleCommands.<S>lit("unpick").executes(ctx -> {
					DonutRaffle.get().clearWinner();
					DonutRaffle.changed();
					DonutRaffle.info(Component.literal("Winner cleared."));
					return 1;
				}))
				.then(RaffleCommands.<S>lit("add")
						.then(RaffleCommands.<S, String>arg("player", StringArgumentType.word())
								.executes(ctx -> add(StringArgumentType.getString(ctx, "player")))))
				.then(RaffleCommands.<S>lit("clear")
						.then(RaffleCommands.<S, Integer>arg("spot", IntegerArgumentType.integer(1, Raffle.MAX_SPOTS))
								.executes(ctx -> clear(IntegerArgumentType.getInteger(ctx, "spot")))))
				.then(RaffleCommands.<S>lit("pause").executes(ctx -> setOpen(false)))
				.then(RaffleCommands.<S>lit("open").executes(ctx -> setOpen(true)))
				.then(RaffleCommands.<S>lit("paid").executes(ctx -> {
					Raffle r = DonutRaffle.get();

					if (r.winner < 0 || r.paid) {
						return fail("No unpaid winner.");
					}

					r.markPaid(System.currentTimeMillis());
					DonutRaffle.changed();
					DonutRaffle.info(Component.literal("Marked as paid. Round closed.").withStyle(ChatFormatting.GREEN));
					return 1;
				}))
				.then(RaffleCommands.<S>lit("new").executes(ctx -> {
					DonutRaffle.newRound();
					return 1;
				}))
				.then(RaffleCommands.<S>lit("unqueue")
						.then(RaffleCommands.<S, Integer>arg("n", IntegerArgumentType.integer(1))
								.executes(ctx -> {
									if (!DonutRaffle.get().unqueue(IntegerArgumentType.getInteger(ctx, "n") - 1)) {
										return fail("No such place in the queue.");
									}

									DonutRaffle.changed();
									DonutRaffle.info(Component.literal("Removed from the next-round queue."));
									return 1;
								})))
				.then(RaffleCommands.<S>lit("dismiss")
						.then(RaffleCommands.<S, Long>arg("id", LongArgumentType.longArg(1))
								.executes(ctx -> {
									if (!DonutRaffle.get().dismissExtra(LongArgumentType.getLong(ctx, "id"))) {
										return fail("Already gone.");
									}

									DonutRaffle.changed();
									DonutRaffle.info(Component.literal("Removed from the owed-back list."));
									return 1;
								})));
	}

	private static int fail(String text) {
		DonutRaffle.info(Component.literal(text).withStyle(ChatFormatting.RED));
		return 0;
	}

	private static int status() {
		Raffle r = DonutRaffle.get();
		DonutRaffle.info(Component.literal(Prizes.name(r) + " jackpot, " + Money.format(r.price) + " a spot, "
				+ r.filled() + "/" + r.spots + " filled" + (r.open ? "" : " (paused)")));
		DonutRaffle.info(Component.literal(DonutRaffle.payoutLine(r)).withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private static int price(String text) {
		double v = Money.parse(text);

		if (v <= 0) {
			return fail("Not an amount: " + text + " (try 1m, 250k or 1,000,000)");
		}

		Raffle r = DonutRaffle.get();
		r.price = v;
		DonutRaffle.changed();
		DonutRaffle.info(Component.literal("Spot price set to " + Money.format(v) + ". A full pot pays the winner "
				+ Money.format(Raffle.payout(r.fullPot(), r.winnerPercent, r.taxPercent)) + "."));
		return 1;
	}

	/** "/raffle payout 87" or "/raffle payout 8.1x": the winner's share of the pot. */
	private static int payout(String text) {
		Raffle r = DonutRaffle.get();
		double w = Raffle.parseWinnerPercent(text, r.spots);

		if (w < 0) {
			return fail("Not a share: " + text + " (try 87, 90% or 8.1x)");
		}

		r.winnerPercent = w;
		DonutRaffle.changed();
		DonutRaffle.info(Component.literal("Winner gets " + DonutRaffle.pct(w) + " of the pot (" + Money.times(r.multiple())
				+ " with " + r.spots + " spots). A full pot pays " + Money.format(Raffle.payout(r.fullPot(), r.winnerPercent, r.taxPercent)) + "."));
		return 1;
	}

	private static int tax(String text) {
		double t;

		try {
			t = Double.parseDouble(text.trim().replace("%", ""));
		} catch (NumberFormatException e) {
			t = -1;
		}

		if (t < 0 || t > 100) {
			return fail("Not a percent: " + text);
		}

		Raffle r = DonutRaffle.get();
		r.taxPercent = t;
		DonutRaffle.changed();
		DonutRaffle.info(Component.literal("Tax taken off the winner's share: " + DonutRaffle.pct(t) + "."));
		return 1;
	}

	private static int spots(int count) {
		Raffle r = DonutRaffle.get();

		if (!r.setSpots(count)) {
			return fail("Can't go to " + count + " spots: a spot past " + count + " is taken. Empty it first.");
		}

		DonutRaffle.changed();
		DonutRaffle.info(Component.literal(count + " spots open. A full pot of " + Money.format(r.fullPot())
				+ " pays the winner " + Money.format(Raffle.payout(r.fullPot(), r.winnerPercent, r.taxPercent)) + "."));
		return 1;
	}

	private static int prize(String query) {
		Prizes.Choice c = Prizes.resolve(query);

		if (c == null) {
			return fail("No item matches \"" + query + "\".");
		}

		Raffle r = DonutRaffle.get();
		r.prizeItem = c.id();
		r.prizeLabel = "";
		DonutRaffle.changed();
		DonutRaffle.info(Component.literal("Prize set to " + c.name() + " (" + c.id() + ")."));
		return 1;
	}

	private static int winner(int spot) {
		Raffle r = DonutRaffle.get();

		if (r.paid) {
			return fail("This round is already paid out. /raffle new starts the next one.");
		}

		if (r.entry(spot - 1) == null) {
			return fail("Spot " + spot + " is empty.");
		}

		DonutRaffle.pickWinner(spot - 1);
		return 1;
	}

	private static int add(String player) {
		int spot = DonutRaffle.get().add(player, System.currentTimeMillis());

		if (spot < 0) {
			return fail("All spots are taken.");
		}

		DonutRaffle.changed();
		DonutRaffle.info(Component.literal(player + " added to spot " + (spot + 1) + " by hand."));
		return 1;
	}

	private static int clear(int spot) {
		if (!DonutRaffle.get().clear(spot - 1)) {
			return fail("Spot " + spot + " is already empty (or the round is paid out).");
		}

		DonutRaffle.changed();
		DonutRaffle.info(Component.literal("Spot " + spot + " emptied."));
		return 1;
	}

	private static int setOpen(boolean open) {
		DonutRaffle.get().open = open;
		DonutRaffle.changed();
		DonutRaffle.info(Component.literal(open ? "Entries open." : "Entries paused: payments will not take spots.")
				.withStyle(open ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		return 1;
	}
}
