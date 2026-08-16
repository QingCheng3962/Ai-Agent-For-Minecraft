package com.mcai.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mcai.json");

	private static McAiConfig current = loadFromDisk();

	private ConfigManager() {
	}

	public static McAiConfig get() {
		return current;
	}

	public static void reload() {
		current = loadFromDisk();
	}

	public static void save() {
		try {
			Files.writeString(CONFIG_PATH, GSON.toJson(current), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.err.println("[mcai] Failed to save config: " + e);
		}
	}

	private static McAiConfig loadFromDisk() {
		McAiConfig c = McAiConfig.defaults();
		if (!Files.exists(CONFIG_PATH)) {
			save();
			return c;
		}
		try {
			McAiConfig loaded = GSON.fromJson(Files.readString(CONFIG_PATH, StandardCharsets.UTF_8), McAiConfig.class);
			if (loaded != null) {
				c = applyDefaults(loaded);
			}
		} catch (Exception e) {
			System.err.println("[mcai] Failed to load config, using defaults: " + e);
		}
		return c;
	}

	private static McAiConfig applyDefaults(McAiConfig c) {
		McAiConfig d = McAiConfig.defaults();
		if (c.baseUrl == null || c.baseUrl.isBlank()) {
			c.baseUrl = d.baseUrl;
		}
		if (c.apiKey == null) {
			c.apiKey = "";
		}
		if (c.model == null || c.model.isBlank()) {
			c.model = d.model;
		}
		if (c.systemPrompt == null || c.systemPrompt.isBlank()) {
			c.systemPrompt = d.systemPrompt;
		}
		if (c.temperature <= 0 || c.temperature > 2) {
			c.temperature = d.temperature;
		}
		if (c.maxTokens <= 0) {
			c.maxTokens = d.maxTokens;
		}
		if (c.maxHistory <= 0) {
			c.maxHistory = d.maxHistory;
		}
		if (c.maxToolIterations <= 0) {
			c.maxToolIterations = d.maxToolIterations;
		}
		return c;
	}
}
