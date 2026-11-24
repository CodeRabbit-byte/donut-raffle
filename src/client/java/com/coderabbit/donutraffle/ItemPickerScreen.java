package com.coderabbit.donutraffle;

import java.util.ArrayList;
import java.util.List;

import com.coderabbit.donutraffle.core.Raffle;
import com.coderabbit.donutraffle.mc.BaseScreen;
import com.coderabbit.donutraffle.mc.Gfx;
import com.coderabbit.donutraffle.mc.Mc;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Search every item by name or id ("elytra", "dragon head", "netherite") and click one to make it the prize. */
public class ItemPickerScreen extends BaseScreen {
	private static final int CELL = 22;
	private static final int COLS = 14;

	private final Screen parent;
	private EditBox search;
	private EditBox label;
	private String query = "";
	private String labelText;
	private int page;
	private int rows = 4;
	private List<Prizes.Choice> results = List.of();
	private final List<Button> cells = new ArrayList<>();
	private final List<Prizes.Choice> cellItems = new ArrayList<>();

	public ItemPickerScreen(Screen parent) {
		super(Component.literal("Choose the prize"));
		this.parent = parent;
		labelText = DonutRaffle.get().prizeLabel;
	}

	@Override
	protected void init() {
		int cx = width / 2;
		int gridW = COLS * CELL;
		int left = cx - gridW / 2;
		int y = 22;
		rows = Math.max(2, (height - 46 - 92) / CELL);

		search = new EditBox(font, left, y, gridW, 20, Component.literal("Search"));
		search.setMaxLength(50);
		search.setHint(Component.literal("search items: elytra, dragon head, totem...").withStyle(ChatFormatting.DARK_GRAY));
		search.setValue(query);
		search.setResponder(v -> {
			query = v;
			page = 0;
			fillGrid();
		});
		addRenderableWidget(search);
		setInitialFocus(search);
		y += 26;

		results = Prizes.search(query);
		cells.clear();
		cellItems.clear();

		for (int i = 0; i < COLS * rows; i++) {
			int col = i % COLS;
			int row = i / COLS;
			final int index = i;
			Button b = Button.builder(Component.empty(), x -> choose(index))
					.bounds(left + col * CELL, y + row * CELL, CELL - 2, CELL - 2)
					.build();
			cells.add(b);
			addRenderableWidget(b);
		}

		int below = height - 90;
		addRenderableWidget(Button.builder(Component.literal("<"), b -> {
					page--;
					fillGrid();
				})
				.bounds(left, below, 20, 20)
				.build());
		addRenderableWidget(Button.builder(Component.literal(">"), b -> {
					page++;
					fillGrid();
				})
				.bounds(left + gridW - 20, below, 20, 20)
				.build());

		int ly = height - 50;
		label = new EditBox(font, left + 80, ly, gridW - 80 - 74, 20, Component.literal("Label"));
		label.setMaxLength(40);
		label.setHint(Component.literal("optional, e.g. Elytra + 64 rockets").withStyle(ChatFormatting.DARK_GRAY));
		label.setValue(labelText);
		label.setResponder(v -> labelText = v);
		addRenderableWidget(label);
		addRenderableWidget(Button.builder(Component.literal("Save label"), b -> {
					DonutRaffle.get().prizeLabel = labelText.trim();
					DonutRaffle.changed();
				})
				.bounds(left + gridW - 70, ly, 70, 20)
				.build());

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(cx - 50, height - 26, 100, 20)
				.build());
		fillGrid();
	}

	private int pages() {
		return Math.max(1, (results.size() + COLS * rows - 1) / (COLS * rows));
	}

	private void fillGrid() {
		results = Prizes.search(query);
		page = Math.floorMod(page, pages());
		cellItems.clear();
		int start = page * COLS * rows;

		for (int i = 0; i < cells.size(); i++) {
			int at = start + i;
			Prizes.Choice c = at < results.size() ? results.get(at) : null;
			cellItems.add(c);
			cells.get(i).visible = c != null;
		}
	}

	private void choose(int index) {
		Prizes.Choice c = index < cellItems.size() ? cellItems.get(index) : null;

		if (c == null) {
			return;
		}

		Raffle r = DonutRaffle.get();
		r.prizeItem = c.id();
		DonutRaffle.changed();
		DonutRaffle.info(Component.literal("Prize set to " + c.name() + "."));
	}

	@Override
	protected void drawBehind(Gfx g, int mouseX, int mouseY) {
		int cx = width / 2;
		int gridW = COLS * CELL;
		int left = cx - gridW / 2;
		g.centered(font, Component.literal("Choose the prize").withStyle(ChatFormatting.BOLD), cx, 8, Hud.GOLD);
		int below = height - 90;
		g.centered(font, results.isEmpty() ? "No items match" : "Page " + (page + 1) + " / " + pages() + "  (" + results.size() + " items)",
				cx, below + 6, Hud.DIM);

		Raffle r = DonutRaffle.get();
		int py = below + 26;
		g.item(Prizes.stack(r), left, py - 4);
		g.text(font, "Prize now: " + Prizes.name(r) + "  (" + r.prizeItem + ")", left + 20, py, Hud.TEXT, true);
		g.text(font, "Custom name", left, height - 44, Hud.TEXT, false);
	}

	@Override
	protected void drawFront(Gfx g, int mouseX, int mouseY) {
		Raffle r = DonutRaffle.get();
		Prizes.Choice hover = null;

		for (int i = 0; i < cells.size(); i++) {
			Prizes.Choice c = cellItems.size() > i ? cellItems.get(i) : null;

			if (c == null) {
				continue;
			}

			Button b = cells.get(i);
			g.item(c.stack(), b.getX() + 2, b.getY() + 2);

			if (c.id().equals(r.prizeItem)) {
				g.outline(b.getX() - 1, b.getY() - 1, b.getWidth() + 2, b.getHeight() + 2, Hud.GOLD);
			}

			if (b.isHovered()) {
				hover = c;
			}
		}

		if (hover != null) {
			String s = hover.name() + "  " + hover.id();
			int w = font.width(s) + 8;
			int x = Math.min(mouseX + 10, width - w - 2);
			int y = mouseY - 16;
			g.fill(x, y, x + w, y + 13, 0xF0100010);
			g.text(font, s, x + 4, y + 3, Hud.TEXT, false);
		}
	}

	@Override
	public void onClose() {
		Mc.setScreen(parent);
	}
}
