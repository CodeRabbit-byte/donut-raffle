package com.coderabbit.donutraffle.gametest;

import com.coderabbit.donutraffle.DonutRaffle;
import com.coderabbit.donutraffle.ItemPickerScreen;
import com.coderabbit.donutraffle.RaffleScreen;
import com.coderabbit.donutraffle.SettingsScreen;
import com.coderabbit.donutraffle.core.Raffle;
import com.coderabbit.donutraffle.mc.Mc;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * Plays a whole round in a real client. Donut's pay lines arrive as server chat (sent with
 * /tellraw, which reaches the client the same way), H opens the menu, a spot is picked as the
 * winner, the /pay is filled in, and "You paid" lines close the round. Screenshots land in
 * build/run/clientGameTest/screenshots. Run with {@code ./gradlew runClientGameTest}.
 */
public class DonutRaffleClientGameTest implements FabricClientGameTest {
	private static final double C = 0.001;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			Raffle r = DonutRaffle.get();
			r.newRound();
			r.extras.clear();
			r.queue.clear();
			r.history.clear();
			r.price = 1_000_000;
			r.setSpots(9);
			r.prizeItem = "minecraft:elytra";
			r.prizeLabel = "";
			r.winnerPercent = 90;
			r.taxPercent = 2;
			r.hud = true;
			DonutRaffle.changed();
		});

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(60);
			TestServerContext server = singleplayer.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			context.waitTicks(5);

			// ---- payments fill spots
			pay(context, server, "Steve paid you $1M.");
			pay(context, server, "Alex paid you $3M.");
			pay(context, server, "Bob paid you $1.5M.");
			pay(context, server, "Carl paid you $100K.");
			// A player typing the line in chat after their name prefix must not count.
			pay(context, server, "<Steve> Eve paid you $1M.");

			context.runOnClient(client -> {
				Raffle r = DonutRaffle.get();
				check(r.filled() == 5, "5 spots expected, got " + r.filled());
				check(r.entry(0).name.equals("Steve") && r.entry(3).name.equals("Alex") && r.entry(4).name.equals("Bob"),
						"spot order wrong");
				check(r.extras.size() == 2, "Bob's $500K and Carl's $100K should be owed back, got " + r.extras.size());
				check(Math.abs(r.pot() - 5_000_000) < C, "pot should be $5M, got " + r.pot());
			});
			context.takeScreenshot("raffle-1-hud-filling");

			pay(context, server, "Eve paid you $6M.");
			context.runOnClient(client -> {
				Raffle r = DonutRaffle.get();
				check(r.full(), "should be full");
				check(r.spotsOf("Eve") == 4, "Eve should have 4 spots");
				check(Math.abs(r.pot() - 9_000_000) < C, "pot should be $9M");
				check(Math.abs(r.payout() - 7_938_000) < C, "payout should be $7.938M, got " + r.payout());
				check(r.extras.size() == 2 && r.queue.size() == 2, "Eve's 2 extra spots should wait for the next round");
			});
			context.takeScreenshot("raffle-2-hud-full");

			// ---- H opens the menu; pick spot 5 (Bob) as the winner
			context.getInput().pressKey(InputConstants.KEY_H);
			context.waitForScreen(RaffleScreen.class);
			context.waitTicks(2);
			context.takeScreenshot("raffle-3-menu");
			// Click spot box 5 on the bar with the real mouse.
			double[] at = context.computeOnClient(client -> {
				double[] c = ((RaffleScreen) Mc.screen()).spotCenter(4);
				double s = client.getWindow().getGuiScale();
				return new double[]{c[0] * s, c[1] * s};
			});
			context.getInput().setCursorPos(at[0], at[1]);
			context.waitTicks(1);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTicks(3);
			context.clickScreenButton("Winner: spot 5");
			context.waitTicks(2);
			context.runOnClient(client -> {
				Raffle r = DonutRaffle.get();
				check(r.winner == 4 && "Bob".equals(r.winnerName()), "Bob on spot 5 should win, got " + r.winnerName());
			});
			context.takeScreenshot("raffle-4-menu-winner");

			// ---- Fill /pay puts the command in the chat box; nothing is sent
			context.clickScreenButton("Fill /pay $7.938M");
			context.waitForScreen(ChatScreen.class);
			context.waitTicks(2);
			context.runOnClient(client -> {
				EditBox box = (EditBox) Mc.screen().children().stream().filter(w -> w instanceof EditBox).findFirst().orElseThrow();
				check(box.getValue().equals("/pay Bob 7938000"), "chat box should hold the /pay, got " + box.getValue());
			});
			context.takeScreenshot("raffle-5-fill-pay");
			context.runOnClient(client -> Mc.setScreen(null));
			context.waitTicks(2);
			context.takeScreenshot("raffle-6-hud-winner");

			// ---- your own pay lines count towards the payout
			pay(context, server, "You paid Bob $4M (+$80K tax)");
			context.runOnClient(client -> {
				Raffle r = DonutRaffle.get();
				check(!r.paid && Math.abs(r.owed() - 3_938_000) < C, "$3.938M should still be owed, got " + r.owed());
			});
			pay(context, server, "You paid Bob $3.938M (+$78.76K tax)");
			pay(context, server, "You paid Carl $100K (+$2K tax)");
			context.runOnClient(client -> {
				Raffle r = DonutRaffle.get();
				check(r.paid, "round should be paid");
				check(r.history.size() == 1 && "Bob".equals(r.history.get(0).winner), "history should hold Bob's win");
				check(r.extras.size() == 1, "Carl's refund should clear his owed-back entry");
			});
			context.takeScreenshot("raffle-7-hud-paid");

			// ---- client commands: prize search, new round, fewer spots
			command(context, "raffle prize dragon head");
			command(context, "raffle new");
			command(context, "raffle spots 5");
			command(context, "raffle price 2.5m");
			pay(context, server, "Zed paid you $5M.");
			context.runOnClient(client -> {
				Raffle r = DonutRaffle.get();
				check(r.prizeItem.equals("minecraft:dragon_head"), "prize should be the dragon head, got " + r.prizeItem);
				check(r.spots == 5 && r.open && !r.paid, "new 5-spot round expected");
				check(r.spotsOf("Eve") == 2 && r.entry(0).name.equals("Eve"), "Eve's queued spots should open the new round");
				check(r.spotsOf("Zed") == 2 && r.filled() == 4, "Zed's $5M should buy 2 spots at $2.5M");
				check(r.queue.isEmpty(), "queue should be empty");
				check(Math.abs(r.fullPot() - 12_500_000) < C, "full pot 5 x $2.5M");
			});
			context.takeScreenshot("raffle-8-hud-dragon-head-5-spots");

			// ---- winner's share typed on the menu: 87%, then as a multiple
			context.getInput().pressKey(InputConstants.KEY_H);
			context.waitForScreen(RaffleScreen.class);
			context.waitTicks(2);
			context.runOnClient(client -> {
				EditBox win = (EditBox) Mc.screen().children().stream()
						.filter(w -> w instanceof EditBox e && e.getMessage().getString().startsWith("Winner gets"))
						.findFirst().orElseThrow();
				win.setValue("87");
				Raffle r = DonutRaffle.get();
				check(Math.abs(r.winnerPercent - 87) < C, "typing 87 should set 87%, got " + r.winnerPercent);
				// 5 x $2.5M = $12.5M, 87% = $10.875M, - 2% = $10.6575M
				check(Math.abs(Raffle.payout(r.fullPot(), r.winnerPercent, r.taxPercent) - 10_657_500) < C, "87% payout wrong");
				win.setValue("4.5x");
				check(Math.abs(r.winnerPercent - 90) < C, "4.5x of 5 spots should be 90%, got " + r.winnerPercent);
				win.setValue("87");
			});
			context.waitTicks(3);
			context.runOnClient(client -> check(Mc.screen() instanceof RaffleScreen, "menu should stay open while typing"));
			context.takeScreenshot("raffle-8b-menu-winner-percent");
			command(context, "raffle payout 8.1x");
			context.runOnClient(client -> check(Math.abs(DonutRaffle.get().winnerPercent - 100) > 1, "8.1x of 5 spots is over 100%, refused"));
			command(context, "raffle payout 85%");
			context.runOnClient(client -> check(Math.abs(DonutRaffle.get().winnerPercent - 85) < C, "/raffle payout 85% should set 85%"));
			context.runOnClient(client -> Mc.setScreen(null));
			context.waitTicks(2);

			// ---- late payers wait for the next round; paying a spot holder the jackpot makes them the winner
			pay(context, server, "Late paid you $2.5M.");
			pay(context, server, "Later paid you $2.5M.");
			context.runOnClient(client -> {
				Raffle r = DonutRaffle.get();
				check(r.full() && r.queue.size() == 1 && r.queue.get(0).name.equals("Later"), "Later should be queued");
				// Pot 1 + 1 + 2.5 x 3 = $9.5M, 85% = $8.075M, - 2% = $7.9135M
				check(Math.abs(r.payout() - 7_913_500) < C, "payout should be $7.9135M, got " + r.payout());
			});
			pay(context, server, "You paid Zed $7.9135M (+$158.27K tax)");
			context.runOnClient(client -> {
				Raffle r = DonutRaffle.get();
				check(r.winner == 2 && r.paid, "paying Zed the jackpot should make spot 3 the paid winner, got " + r.winner + " " + r.paid);
			});
			context.takeScreenshot("raffle-8c-hud-paid-green-and-queue");
			// ---- prize search screen
			context.runOnClient(client -> Mc.setScreen(new ItemPickerScreen(null)));
			context.waitForScreen(ItemPickerScreen.class);
			context.runOnClient(client -> {
				EditBox box = (EditBox) Mc.screen().children().stream().filter(w -> w instanceof EditBox).findFirst().orElseThrow();
				box.setValue("head");
			});
			context.waitTicks(3);
			context.takeScreenshot("raffle-9-prize-search");

			context.runOnClient(client -> Mc.setScreen(new SettingsScreen(null)));
			context.waitForScreen(SettingsScreen.class);
			context.waitTicks(2);
			context.takeScreenshot("raffle-10-settings");
			context.runOnClient(client -> Mc.setScreen(null));
			context.waitTicks(2);

			// ---- prize worth: an elytra worth $81M, 9 spots, 90% -> $10M a spot
			command(context, "raffle new");
			command(context, "raffle prize elytra");
			command(context, "raffle spots 9");
			command(context, "raffle payout 90");
			context.getInput().pressKey(InputConstants.KEY_H);
			context.waitForScreen(RaffleScreen.class);
			context.waitTicks(2);
			context.runOnClient(client -> {
				EditBox worth = box("Prize worth");
				worth.setValue("81m");
				Raffle r = DonutRaffle.get();
				check(Math.abs(r.price - 10_000_000) < C, "worth $81M should make $10M spots, got " + r.price);
				check(box("Spot price").getValue().equals("10m"), "price box should show 10m, got " + box("Spot price").getValue());
				box("Winner gets % of the pot").setValue("81");
				check(Math.abs(r.price - 11_111_111.11) < 0.01, "81% should make spots $11.11M, got " + r.price);
				box("Winner gets % of the pot").setValue("90");
			});
			context.waitTicks(3);
			context.takeScreenshot("raffle-11-menu-prize-worth");
			command(context, "raffle spots 5");
			context.runOnClient(client -> check(Math.abs(DonutRaffle.get().price - 18_000_000) < C, "5 spots should make $18M spots"));
			context.runOnClient(client -> {
				box("Spot price").setValue("3m");
				Raffle r = DonutRaffle.get();
				check(r.price == 3_000_000 && r.prizeValue == 0, "typing a price should stop following the worth");
			});
			context.runOnClient(client -> Mc.setScreen(null));
			context.waitTicks(2);
		}
	}

	private static void pay(ClientGameTestContext context, TestServerContext server, String line) {
		server.runCommand("tellraw @a {\"text\":\"" + line + "\"}");
		context.waitTicks(3);
	}

	/** Runs a client command the way typing it in chat does. */
	private static void command(ClientGameTestContext context, String command) {
		context.runOnClient(client -> client.player.connection.sendCommand(command));
		context.waitTicks(3);
	}

	/** The open screen's text box with this name. Call on the client thread. */
	private static EditBox box(String name) {
		return (EditBox) Mc.screen().children().stream()
				.filter(w -> w instanceof EditBox e && e.getMessage().getString().equals(name))
				.findFirst().orElseThrow(() -> new AssertionError("no box " + name));
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
