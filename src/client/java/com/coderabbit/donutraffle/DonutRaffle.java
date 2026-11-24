package com.coderabbit.donutraffle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.coderabbit.donutraffle.core.Money;
import com.coderabbit.donutraffle.core.PayLine;
import com.coderabbit.donutraffle.core.Raffle;
import com.coderabbit.donutraffle.core.Store;
import com.coderabbit.donutraffle.mc.Mc;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * Shared glue between Minecraft and {@link Raffle}: reads Donut's pay lines, posts chat notes,
 * saves the round. Never sends a command or a payment: every /pay button only fills the chat box.
 */
public final class DonutRaffle {
	public static final String MOD_ID = "donutraffle";
	public static final Logger LOGGER = LoggerFactory.getLogger("DonutRaffle");

	private static Raffle raffle = new Raffle();
	private static Path file;
	/** Bumped on every change so open screens know to refresh. */
	private static int version;
	/** Screen asked for by a command; opened next tick, after the chat screen closed. */
	private static Screen pending;

	private DonutRaffle() {
	}

	public static void init() {
		file = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("raffle.json");
		raffle = Store.load(file);
		LOGGER.info("Donut Raffle loaded: {} of {} spots filled", raffle.filled(), raffle.spots);
	}

	public static Raffle get() {
		return raffle;
	}

	public static int version() {
		return version;
	}

	/** Call after changing the raffle. */
	public static void changed() {
		version++;
		save();
	}

	public static void save() {
		if (file == null) {
			return;
		}

		try {
			Store.save(file, raffle);
		} catch (IOException e) {
			LOGGER.warn("Could not save {}", file, e);
		}
	}

	public static void open(Screen s) {
		pending = s;
	}

	public static void tick(boolean menuPressed) {
		if (pending != null) {
			Screen s = pending;
			pending = null;
			Mc.setScreen(s);
		}

		if (menuPressed && Mc.screen() == null) {
			Mc.setScreen(new RaffleScreen());
		}
	}

	// ------------------------------------------------------------------ chat in

	public static void onGameMessage(Component message) {
		String plain = ChatFormatting.stripFormatting(message.getString());
		PayLine.Pay pay = PayLine.parse(plain);

		if (pay == null) {
			return;
		}

		long now = System.currentTimeMillis();

		if (pay.sent()) {
			onSent(pay, now);
		} else {
			onReceived(pay, now);
		}
	}

	private static void onReceived(PayLine.Pay pay, long now) {
		Raffle r = raffle;
		Raffle.Received res = r.onReceived(pay.name(), pay.amount(), now);

		if (!res.counted() && res.queued() == 0 && res.extra() == null) {
			return;
		}


		if (res.counted()) {
			String spots = res.spots().stream().map(i -> String.valueOf(i + 1)).collect(Collectors.joining(", "));
			info(Component.literal(pay.name()).withStyle(ChatFormatting.AQUA)
					.append(Component.literal(" took spot" + (res.spots().size() > 1 ? "s " : " ")).withStyle(ChatFormatting.GRAY))
					.append(Component.literal(spots).withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" (" + r.filled() + "/" + r.spots + ", pot " + Money.format(r.pot()) + ")")
							.withStyle(ChatFormatting.GRAY)));

			if (r.full()) {
				info(Component.literal("All " + r.spots + " spots filled! Pot " + Money.format(r.pot())
								+ ". Decide the winner, then ").withStyle(ChatFormatting.GOLD)
						.append(run("[pick winner]", "/raffle", "Open the raffle menu")));
			}
		}

		if (res.queued() > 0) {
			info(Component.literal(pay.name()).withStyle(ChatFormatting.AQUA)
					.append(Component.literal(" paid late: " + res.queued() + (res.queued() > 1 ? " spots" : " spot")
							+ " in the next round (" + r.queue.size() + " waiting).").withStyle(ChatFormatting.GRAY)));
		}
		Raffle.Extra x = res.extra();

		if (x != null) {
			info(Component.literal(Money.format(x.amount) + " from " + x.name + " did not buy a spot (" + x.reason + "). ")
					.withStyle(ChatFormatting.YELLOW)
					.append(suggest("[refund]", payCommand(x.name, x.amount), "Fill in the refund /pay"))
					.append(Component.literal(" "))
					.append(run("[keep]", "/raffle dismiss " + x.id, "Forget this payment")));
		}

		changed();
	}

	private static void onSent(PayLine.Pay pay, long now) {
		Raffle r = raffle;
		double owedBefore = r.owed();
		Raffle.Sent result = r.onSent(pay.name(), pay.amount(), now);

		if (r.takeAutoPicked()) {
			owedBefore = r.payout();
			info(Component.literal("You paid " + pay.name() + " the jackpot, so they are the winner (spot " + (r.winner + 1) + ").")
					.withStyle(ChatFormatting.GREEN));
		}

		switch (result) {
			case WINNER_PART -> info(Component.literal("Paid " + pay.name() + " " + Money.format(pay.amount()) + " of "
					+ Money.format(r.payout()) + ", " + Money.format(r.owed()) + " still to pay. ").withStyle(ChatFormatting.YELLOW)
					.append(suggest("[pay rest]", payCommand(pay.name(), r.owed()), "Fill in the rest")));
			case WINNER_DONE -> info(Component.literal("Payout to " + pay.name() + " done (" + Money.format(Math.min(owedBefore, pay.amount()))
					+ "). Round closed. ").withStyle(ChatFormatting.GREEN)
					.append(run("[new round]", "/raffle new", "Empty the spots and open a new round")));
			case REFUND -> info(Component.literal("Refund to " + pay.name() + " done.").withStyle(ChatFormatting.GREEN));
			case NONE -> {
				return;
			}
		}

		changed();
	}

	// ------------------------------------------------------------------ hosting

	/** Marks the winner and posts the payout with a [pay] button. */
	public static boolean pickWinner(int spot) {
		Raffle r = raffle;

		if (!r.setWinner(spot)) {
			return false;
		}

		String name = r.winnerName();
		info(Component.literal("Winner: ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(name).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
				.append(Component.literal(" (spot " + (spot + 1) + ")").withStyle(ChatFormatting.GRAY)));
		info(Component.literal(payoutLine(r)).withStyle(ChatFormatting.GRAY));
		info(Component.literal("Pay " + name + " ").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(Money.format(r.payout())).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
				.append(Component.literal(" ").withStyle(ChatFormatting.YELLOW))
				.append(suggest("[pay]", payCommand(name, r.payout()), "Fill in /pay - you press Enter")));
		changed();
		return true;
	}

	/** "Pot $9M x 90% = $8.1M (8.1x), - 2% tax $162K = pay $7.938M" */
	public static String payoutLine(Raffle r) {
		return "Pot " + Money.format(r.pot()) + " x " + pct(r.winnerPercent) + " = " + Money.format(r.share())
				+ (r.price > 0 ? " (" + Money.times(r.share() / r.price) + ")" : "")
				+ ", - " + pct(r.taxPercent) + " tax " + Money.format(r.tax()) + " = pay " + Money.format(r.payout());
	}

	public static String pct(double p) {
		return Money.plain(p) + "%";
	}

	public static String payCommand(String name, double amount) {
		return "/pay " + name + " " + Money.plain(amount);
	}

	/** Puts a /pay in the chat box. You check it and press Enter; nothing is sent for you. */
	public static void fillPay(String name, double amount) {
		Mc.openChat(payCommand(name, amount));
	}

	// ------------------------------------------------------------------ chat out

	public static void info(Component message) {
		Mc.chat(Component.literal("[Raffle] ").withStyle(ChatFormatting.GOLD).append(message));
	}

	static MutableComponent suggest(String label, String command, String hover) {
		return Component.literal(label).withStyle(s -> s.withColor(ChatFormatting.GREEN)
				.withClickEvent(new ClickEvent.SuggestCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover + "\n" + command))));
	}

	static MutableComponent run(String label, String command, String hover) {
		return Component.literal(label).withStyle(s -> s.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}

	/** Starts the next round; late payers in the queue fill the first spots. */
	public static void newRound() {
		Raffle r = raffle;
		int moved = r.newRound();
		changed();
		info(Component.literal("New round open: " + Prizes.name(r) + ", " + Money.format(r.price) + " a spot"
				+ (moved > 0 ? ". " + moved + " from the queue took spots 1-" + moved : "") + ".").withStyle(ChatFormatting.GREEN));
	}

	/** Names of the players holding spots, for command suggestions. */
	static List<String> spotNames() {
		return raffle.entries.stream().filter(e -> e != null).map(e -> e.name).distinct().toList();
	}
}
