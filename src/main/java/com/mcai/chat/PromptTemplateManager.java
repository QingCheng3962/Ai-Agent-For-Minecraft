package com.mcai.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PromptTemplateManager {
	public static final class Template {
		public String name;
		public String prompt;

		public Template(String name, String prompt) {
			this.name = name;
			this.prompt = prompt;
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type LIST_TYPE = new TypeToken<List<Template>>() {}.getType();
	private static final Path FILE_PATH = FabricLoader.getInstance().getConfigDir().resolve("mcai_prompt_templates.json");

	private static List<Template> templates;

	private PromptTemplateManager() {
	}

	public static List<Template> getAll() {
		if (templates == null) {
			templates = load();
		}
		return templates;
	}

	public static void save(String name, String prompt) {
		List<Template> list = getAll();
		for (Template t : list) {
			if (t.name.equals(name)) {
				t.prompt = prompt;
				persist(list);
				return;
			}
		}
		list.add(new Template(name, prompt));
		persist(list);
	}

	public static void delete(String name) {
		List<Template> list = getAll();
		list.removeIf(t -> t.name.equals(name));
		persist(list);
	}

	private static List<Template> load() {
		if (!Files.exists(FILE_PATH)) {
			return new ArrayList<>();
		}
		try (Reader r = Files.newBufferedReader(FILE_PATH, StandardCharsets.UTF_8)) {
			List<Template> list = GSON.fromJson(r, LIST_TYPE);
			return list != null ? new ArrayList<>(list) : new ArrayList<>();
		} catch (Exception e) {
			System.err.println("[mcai] Failed to load prompt templates: " + e);
			return new ArrayList<>();
		}
	}

	private static void persist(List<Template> list) {
		try {
			Files.createDirectories(FILE_PATH.getParent());
			try (Writer w = Files.newBufferedWriter(FILE_PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(list, w);
			}
		} catch (IOException e) {
			System.err.println("[mcai] Failed to save prompt templates: " + e);
		}
	}
}
