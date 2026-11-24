package com.coderabbit.donutraffle;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * Player skins for the faces on the bar. Players in the tab list use their loaded skin. Anyone else
 * is looked up by name through the game's own skin cache (the one player heads use), which shows the
 * default skin until the lookup finishes.
 */
public final class Faces {
	private static final Map<String, ResolvableProfile> PROFILES = new HashMap<>();

	private Faces() {
	}

	public static PlayerSkin skin(String name) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.getConnection() != null) {
			PlayerInfo info = mc.getConnection().getPlayerInfoIgnoreCase(name);

			if (info != null) {
				return info.getSkin();
			}
		}

		// Bedrock names (".Name") have no Java profile to look up.
		if (!name.matches("[A-Za-z0-9_]{1,16}")) {
			return DefaultPlayerSkin.get(UUIDUtil.createOfflinePlayerUUID(name));
		}

		ResolvableProfile profile = PROFILES.computeIfAbsent(name.toLowerCase(Locale.ROOT), n -> ResolvableProfile.createUnresolved(name));
		return mc.playerSkinRenderCache().getOrDefault(profile).playerSkin();
	}
}
