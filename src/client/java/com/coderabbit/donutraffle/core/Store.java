package com.coderabbit.donutraffle.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

/** Reads and writes the raffle as JSON. Written to a temp file first so a crash can't corrupt it. */
public final class Store {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

	private Store() {
	}

	public static String toJson(Raffle r) {
		return GSON.toJson(r);
	}

	public static Raffle fromJson(String json) {
		Raffle r;

		try {
			r = GSON.fromJson(json, Raffle.class);
		} catch (JsonParseException e) {
			r = null;
		}

		if (r == null) {
			r = new Raffle();
		}

		r.normalize();
		return r;
	}

	public static Raffle load(Path file) {
		if (!Files.isRegularFile(file)) {
			return new Raffle();
		}

		try {
			return fromJson(Files.readString(file, StandardCharsets.UTF_8));
		} catch (IOException e) {
			return new Raffle();
		}
	}

	public static void save(Path file, Raffle r) throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, toJson(r), StandardCharsets.UTF_8);

		for (int attempt = 0; ; attempt++) {
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				return;
			} catch (AccessDeniedException e) {
				// Windows: Defender or the indexer briefly holding the file.
				if (attempt >= 4) {
					Files.writeString(file, toJson(r), StandardCharsets.UTF_8);
					Files.deleteIfExists(tmp);
					return;
				}

				try {
					Thread.sleep(50);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}
}
