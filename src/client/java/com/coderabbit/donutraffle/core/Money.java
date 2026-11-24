package com.coderabbit.donutraffle.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** DonutSMP money: parses "1.1M", "1,100,000", "500k" and prints it back. */
public final class Money {
	private static final Pattern INPUT = Pattern.compile(
			"^\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([KkMmBbTtQq])?$");
	private static final String[] SUFFIXES = {"", "K", "M", "B", "T", "Q"};

	private Money() {
	}

	public static double multiplier(String suffix) {
		if (suffix == null || suffix.isEmpty()) {
			return 1;
		}

		return switch (Character.toUpperCase(suffix.charAt(0))) {
			case 'K' -> 1e3;
			case 'M' -> 1e6;
			case 'B' -> 1e9;
			case 'T' -> 1e12;
			case 'Q' -> 1e15;
			default -> 1;
		};
	}

	/** The value of a chat amount: number text (commas allowed) plus its suffix. */
	public static double value(String number, String suffix) {
		return cents(new BigDecimal(number.replace(",", "")).multiply(BigDecimal.valueOf(multiplier(suffix))).doubleValue());
	}

	/** Parses what the user typed ("1.5m", "250k", "$2,000,000"). Returns -1 if it isn't money. */
	public static double parse(String text) {
		if (text == null) {
			return -1;
		}

		Matcher m = INPUT.matcher(text.trim());

		if (!m.matches()) {
			return -1;
		}

		try {
			return value(m.group(1), m.group(2));
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** Rounds to the cent, half up. */
	public static double cents(double v) {
		return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
	}

	/** Short form for display: 1100000 becomes "$1.1M". */
	public static String format(double v) {
		int i = 0;
		double n = Math.abs(v);

		while (n >= 1000 && i < SUFFIXES.length - 1) {
			n /= 1000;
			i++;
		}

		// Three decimals once a suffix is used, so 7.938M is not shown as 7.94M.
		BigDecimal d = BigDecimal.valueOf(n).setScale(i == 0 ? 2 : 3, RoundingMode.HALF_UP).stripTrailingZeros();
		return (v < 0 ? "-$" : "$") + d.toPlainString() + SUFFIXES[i];
	}

	/** The amount as plain digits for {@code /pay}: no suffix, no commas, at most 2 decimals. */
	public static String plain(double v) {
		BigDecimal d = BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
		return d.scale() < 0 ? d.setScale(0).toPlainString() : d.toPlainString();
	}

	/** "7,938,000" - exact, for showing next to the short form. */
	public static String full(double v) {
		BigDecimal d = BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();

		if (d.scale() < 0) {
			d = d.setScale(0);
		}

		return "$" + String.format(Locale.ROOT, d.scale() == 0 ? "%,d" : "%,.2f",
				d.scale() == 0 ? (Object) d.toBigInteger() : (Object) d);
	}

	/** What to put back in a text box: 1100000 becomes "1.1m". */
	public static String formatInput(double v) {
		return format(v).substring(1).toLowerCase(Locale.ROOT);
	}

	/** "8.1x" style multiple, trailing zeros stripped. */
	public static String times(double v) {
		return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "x";
	}
}
