package com.mcai.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mcai.llm.ChatMessage;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SessionManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("mcai_sessions");

	private SessionManager() {
	}

	public static List<String> listSessions() {
		try {
			Files.createDirectories(DIR);
			try (Stream<Path> s = Files.list(DIR)) {
				return s.filter(p -> p.getFileName().toString().endsWith(".json"))
						.map(p -> p.getFileName().toString())
						.map(n -> n.substring(0, n.length() - ".json".length()))
						.sorted(String.CASE_INSENSITIVE_ORDER)
						.collect(Collectors.toList());
			}
		} catch (IOException e) {
			return new ArrayList<>();
		}
	}

	public static List<ChatMessage> load(String name) {
		Path p = DIR.resolve(sanitize(name) + ".json");
		if (!Files.exists(p)) {
			return new ArrayList<>();
		}
		try {
			SessionData d = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), SessionData.class);
			return d.messages == null ? new ArrayList<>() : d.messages;
		} catch (Exception e) {
			System.err.println("[mcai] Failed to load session " + name + ": " + e);
			return new ArrayList<>();
		}
	}

	public static void save(String name, List<ChatMessage> messages) {
		try {
			Files.createDirectories(DIR);
			SessionData d = new SessionData();
			d.messages = messages;
			Files.writeString(DIR.resolve(sanitize(name) + ".json"), GSON.toJson(d), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.err.println("[mcai] Failed to save session " + name + ": " + e);
		}
	}

	public static void delete(String name) {
		try {
			Files.deleteIfExists(DIR.resolve(sanitize(name) + ".json"));
		} catch (IOException e) {
			System.err.println("[mcai] Failed to delete session " + name + ": " + e);
		}
	}

	public static String sanitize(String name) {
		if (name == null) {
			return "default";
		}
		String s = name.trim().replaceAll("[\\\\/:*?\"<>|\\s]", "_");
		if (s.isEmpty() || s.equals(".") || s.equals("..")) {
			return "default";
		}
		return s.length() > 40 ? s.substring(0, 40) : s;
	}

	private static final class SessionData {
		List<ChatMessage> messages;
	}
}
