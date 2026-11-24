package com.coderabbit.donutraffle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.coderabbit.donutraffle.core.Raffle;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The prize item: looking it up by id and searching every item by name or id. */
public final class Prizes {
	public record Choice(String id, String name, ItemStack stack) {
	}

	private static List<Choice> all;
	private static String cachedId;
	private static ItemStack cachedStack = ItemStack.EMPTY;

	private Prizes() {
	}

	/** Every item except air, with its display name, built once. */
	public static List<Choice> all() {
		if (all == null) {
			List<Choice> list = new ArrayList<>();

			for (Item item : BuiltInRegistries.ITEM) {
				if (item == Items.AIR) {
					continue;
				}

				ItemStack stack = new ItemStack(item);
				list.add(new Choice(BuiltInRegistries.ITEM.getKey(item).toString(), stack.getHoverName().getString(), stack));
			}

			list.sort(Comparator.comparing(Choice::name, String.CASE_INSENSITIVE_ORDER));
			all = list;
		}

		return all;
	}

	/**
	 * Items whose name or id contains every word of the query. Exact and starts-with matches first,
	 * so "elytra" puts Elytra at the top and "head" lists every head.
	 */
	public static List<Choice> search(String query) {
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

		if (q.isEmpty()) {
			return all();
		}

		String[] words = q.split("\\s+");
		List<Choice> out = new ArrayList<>();

		for (Choice c : all()) {
			String name = c.name().toLowerCase(Locale.ROOT);
			String id = c.id().toLowerCase(Locale.ROOT);
			boolean ok = true;

			for (String w : words) {
				if (!name.contains(w) && !id.contains(w)) {
					ok = false;
					break;
				}
			}

			if (ok) {
				out.add(c);
			}
		}

		out.sort(Comparator.comparingInt((Choice c) -> rank(c, q)));
		return out;
	}

	private static int rank(Choice c, String q) {
		String name = c.name().toLowerCase(Locale.ROOT);
		String path = c.id().substring(c.id().indexOf(':') + 1).replace('_', ' ');

		if (name.equals(q) || path.equals(q) || c.id().equals(q)) {
			return 0;
		}

		if (name.startsWith(q) || path.startsWith(q)) {
			return 1;
		}

		return 2;
	}

	/** The item for a registry id, or null if there is none. */
	public static Item item(String id) {
		Identifier key = Identifier.tryParse(id == null ? "" : id.trim().toLowerCase(Locale.ROOT));

		if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
			return null;
		}

		Item item = BuiltInRegistries.ITEM.getValue(key);
		return item == Items.AIR ? null : item;
	}

	public static ItemStack stack(Raffle r) {
		if (!r.prizeItem.equals(cachedId)) {
			Item item = item(r.prizeItem);
			cachedStack = new ItemStack(item == null ? Items.BARRIER : item);
			cachedId = r.prizeItem;
		}

		return cachedStack;
	}

	/** The custom label if set, else the item's own name. */
	public static String name(Raffle r) {
		if (!r.prizeLabel.isBlank()) {
			return r.prizeLabel;
		}

		return item(r.prizeItem) == null ? r.prizeItem : stack(r).getHoverName().getString();
	}

	/** Sets the prize from an id or a search. Returns the chosen item's name, or null if nothing matched. */
	public static Choice resolve(String query) {
		Item exact = item(query);

		if (exact == null && !query.contains(":")) {
			exact = item("minecraft:" + query.trim().replace(' ', '_'));
		}

		if (exact != null) {
			ItemStack s = new ItemStack(exact);
			return new Choice(BuiltInRegistries.ITEM.getKey(exact).toString(), s.getHoverName().getString(), s);
		}

		List<Choice> found = search(query);
		return found.isEmpty() ? null : found.get(0);
	}
}
