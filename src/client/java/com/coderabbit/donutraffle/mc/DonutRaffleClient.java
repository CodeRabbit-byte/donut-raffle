package com.coderabbit.donutraffle.mc;

import com.coderabbit.donutraffle.DonutRaffle;
import com.coderabbit.donutraffle.Hud;
import com.coderabbit.donutraffle.RaffleCommands;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** 1.21.11 entrypoint: registers the key, HUD, chat hook and /raffle, then hands off to shared code. */
public class DonutRaffleClient implements ClientModInitializer {
	private static KeyMapping menuKey;
	private static KeyMapping toggleKey;

	@Override
	public void onInitializeClient() {
		DonutRaffle.init();

		menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.donutraffle.menu", InputConstants.Type.KEYSYM, InputConstants.KEY_H, KeyMapping.Category.MISC));
		// Unbound by default (set it in Controls); /raffle on|off and the menu button also work.
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.donutraffle.toggle", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			boolean pressed = false;
			boolean toggle = false;

			while (menuKey.consumeClick()) {
				pressed = true;
			}

			while (toggleKey.consumeClick()) {
				toggle = !toggle;
			}

			DonutRaffle.tick(pressed, toggle);
		});

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(DonutRaffle.MOD_ID, "jackpot"),
				(g, tick) -> Hud.render(new Gfx(g)));

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				DonutRaffle.onGameMessage(message);
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(RaffleCommands.tree()));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> DonutRaffle.save());
	}

	public static String menuKeyName() {
		return menuKey == null ? "H" : menuKey.getTranslatedKeyMessage().getString();
	}
}
