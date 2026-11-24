package com.coderabbit.donutraffle.mc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.21.11 client access that moved between versions. */
public final class Mc {
	private Mc() {
	}

	public static Screen screen() {
		return Minecraft.getInstance().screen;
	}

	public static void setScreen(Screen s) {
		Minecraft.getInstance().setScreen(s);
	}

	/** A message only you see. */
	public static void chat(Component c) {
		Minecraft.getInstance().gui.getChat().addMessage(c);
	}

	public static boolean hudHidden() {
		return Minecraft.getInstance().options.hideGui;
	}

	/** Opens the chat box with text typed in, ready for you to press Enter. */
	public static void openChat(String text) {
		setScreen(new ChatScreen(text, false));
	}

	public static String playerName() {
		return Minecraft.getInstance().getUser().getName();
	}
}
