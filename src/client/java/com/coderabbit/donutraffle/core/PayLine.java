package com.coderabbit.donutraffle.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DonutSMP's /pay chat lines, seen live (from the pikastrike mod):
 * <ul>
 *   <li>{@code NAME paid you $X.} when someone pays you</li>
 *   <li>{@code You paid NAME $90 (+$2 tax)} when you pay someone (the sender pays the 2% on top)</li>
 * </ul>
 * Both are anchored at the start of the line, so a player typing "Steve paid you $5." in chat
 * after their own name prefix is not matched.
 */
public final class PayLine {
	/** Anything in brackets after the amount, like " (+$2 tax)". */
	private static final String NOTE = "(?:\\s*\\([^)]*\\))?\\.?";
	/** Optional "." prefix for Bedrock names, optional K/M/B suffix, optional trailing ".". */
	private static final Pattern RECEIVED = Pattern.compile(
			"^(\\.?[A-Za-z0-9_]{1,16}) paid you \\$ ?([0-9][0-9,]*(?:\\.[0-9]+)?) ?([KkMmBbTtQq])?\\.?" + NOTE + "$");
	private static final Pattern SENT = Pattern.compile(
			"^You paid (\\.?[A-Za-z0-9_]{1,16}) \\$ ?([0-9][0-9,]*(?:\\.[0-9]+)?) ?([KkMmBbTtQq])?\\.?" + NOTE + "$");

	public record Pay(String name, double amount, boolean sent) {
	}

	private PayLine() {
	}

	/** @param plain the message with formatting codes removed */
	public static Pay parse(String plain) {
		if (plain == null) {
			return null;
		}

		String line = plain.strip();
		Matcher m = RECEIVED.matcher(line);
		boolean sent = false;

		if (!m.matches()) {
			m = SENT.matcher(line);
			sent = true;

			if (!m.matches()) {
				return null;
			}
		}

		try {
			double amount = Money.value(m.group(2), m.group(3));
			return amount > 0 ? new Pay(m.group(1), amount, sent) : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
