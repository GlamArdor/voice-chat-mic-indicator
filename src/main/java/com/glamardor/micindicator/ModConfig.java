package com.glamardor.micindicator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plain settings holder, persisted as JSON in the game's config directory.
 * Deliberately free of any Cloth Config / Mod Menu types so the mod keeps
 * working even if those mods are not installed (the config screen is optional).
 */
public class ModConfig {

	public boolean enabled = true;       // show the indicator at all
	public boolean showSelf = true;      // show the indicator above your own head
	public int size = 10;                // icon size in pixels
	public int opacity = 41;             // 0–100 %
	public int height = 30;              // hundredths of a block above the head
	public int distance = 48;            // max render distance, blocks
	public boolean throughWalls = true;  // visible through blocks
	public boolean pulse = true;         // pulse the icon with voice loudness
	public boolean fade = true;          // fade the icon in/out

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH =
			FabricLoader.getInstance().getConfigDir().resolve(VoicechatAddonPlugin.PLUGIN_ID + ".json");

	private static ModConfig instance;

	public static ModConfig get() {
		if (instance == null) {
			load();
		}
		return instance;
	}

	public static void load() {
		ModConfig loaded = null;
		try {
			if (Files.exists(PATH)) {
				try (Reader reader = Files.newBufferedReader(PATH)) {
					loaded = GSON.fromJson(reader, ModConfig.class);
				}
			}
		} catch (Exception e) {
			loaded = null;
		}
		instance = loaded != null ? loaded : new ModConfig();
		save();
	}

	public static void save() {
		if (instance == null) {
			instance = new ModConfig();
		}
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(instance, writer);
			}
		} catch (Exception ignored) {
		}
	}
}
