package com.mcai.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ChatConfig {
	public static final class BlockedPlayer {
		public String name = "";
		public boolean enabled = true;
	}

	public static final String TEMPLATE_ASSISTANT = "你是 Minecraft 聊天中的 AI 助手。回答要简洁，不要使用命令，不要发送以 / 开头的内容。";
	public static final String TEMPLATE_BUDDY = "你是一位 Minecraft 聊天搭子（玩家的好朋友），像真人玩家一样活跃在服务器聊天里。"
			+ "行为准则：每次回复只发一句玩家风格的短话，简短自然口语化，不要长篇大论；"
			+ "如果需要多条消息，每条之间间隔约 1 秒逐条发出；"
			+ "可以解答玩家的问题、回应大家的聊天、调侃玩笑玩梗，让氛围轻松愉快，不要过度严肃；"
			+ "使用 & 符号给文字着色（如 &a绿 &c红 &6金 &b蓝 &d粉 &e黄），像服务器常见彩色聊天一样；"
			+ "可以纯净输出服务器命令：/pay <玩家> <金额>、/tpa <玩家>、/tpahere、/home、/sethome、/afk、/msg <玩家> <内容> 等，需要时直接发出；"
			+ "快速监听玩家消息，快速回复，追求极高的响应速度与效率，不要犹豫拖沓；"
			+ "保持友好，不要恶意冒犯、辱骂或刷屏。";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mcai_chat.json");

	public boolean enabled = false;
	public String templateName = "聊天搭子";
	public String provider = "openai";
	public String apiKey = "";
	public String triggerApiKey = "";
	public String scheduleApiKey = "";
	public String baseUrl = "";
	public String model = "gpt-4o-mini";
	public double temperature = 0.7;
	public int maxTokens = 1024;
	public int maxReplyMessages = 3;
	public int maxCharsPerMessage = 100;
	public boolean contextEnabled = true;
	public int contextLength = 20;
	public boolean triggerEnabled = true;
	public String triggerRegex = "(?i)^(ai|@ai)[\uff0c, ]";
	public int triggerCooldownSeconds = 10;
	public boolean autoReplyEnabled = true;
	public boolean useLogFile = true;
	public boolean scheduleEnabled = true;
	public int scheduleIntervalSeconds = 30;
	public boolean restrictionEnabled = true;
	public List<String> blockedRegexPatterns = new ArrayList<>();
	public List<BlockedPlayer> blockedPlayers = new ArrayList<>();
	public String systemPrompt = TEMPLATE_BUDDY;
	public int timeoutSeconds = 30;
	public int retryCount = 1;
	public boolean debugLog = true;
	public boolean chatLogEnabled = true;
	public boolean chatLogTrigger = true;
	public boolean chatLogSchedule = true;
	public boolean chatLogError = true;
	public boolean chatLogBlocked = true;
	public boolean chatLogReload = true;
	public boolean chatLogClearContext = true;
	public boolean chatLogDebug = false;
	public boolean imageGenerationEnabled = true;
	public String imageProvider = "openai";
	public String imageApiKey = "";
	public String imageBaseUrl = "";
	public String imageModel = "gpt-image-1";
	public String imageSize = "1024x1024";
	public String imageRatio = "1:1";
	public int imageTimeoutSeconds = 120;
	public int imageRetryCount = 1;
	public int imageCooldownSeconds = 30;
	public String imageTriggerRegex = "(?i)^(?:画|生成图片|img)[：: ]?(.*)$";
	public String imageUploaderType = "none";
	public String imageUploaderUrl = "";

	public static ChatConfig load() {
		ChatConfig loaded = null;
		if (Files.exists(CONFIG_PATH)) {
			try {
				String raw = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
				if (raw != null && !raw.isBlank()) {
					JsonElement el = JsonParser.parseString(raw);
					if (el.isJsonObject()) {
						JsonObject root = el.getAsJsonObject();
						JsonElement bp = root.remove("blockedPlayers");
						loaded = GSON.fromJson(root, ChatConfig.class);
						if (loaded != null) {
							migrateBlockedPlayers(bp, loaded);
						}
					}
				}
			} catch (Exception e) {
				System.err.println("[mcai] Failed to read mcai_chat.json, using defaults: " + e);
				loaded = null;
			}
		}
		if (loaded == null) {
			loaded = new ChatConfig();
		}
		loaded.validate();
		loaded.save();
		return loaded;
	}

	private static void migrateBlockedPlayers(JsonElement el, ChatConfig cfg) {
		cfg.blockedPlayers.clear();
		if (el == null || !el.isJsonArray()) {
			return;
		}
		for (JsonElement e : el.getAsJsonArray()) {
			BlockedPlayer b = new BlockedPlayer();
			if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
				b.name = e.getAsString();
			} else if (e.isJsonObject()) {
				JsonObject o = e.getAsJsonObject();
				JsonElement n = o.get("name");
				b.name = (n == null || n.isJsonNull()) ? "" : n.getAsString();
				JsonElement en = o.get("enabled");
				b.enabled = (en == null || en.isJsonNull()) || en.getAsBoolean();
			}
			cfg.blockedPlayers.add(b);
		}
	}

	public void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer w = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(this, w);
			}
		} catch (IOException e) {
			System.err.println("[mcai] Failed to save mcai_chat.json: " + e);
		}
	}

	public void validate() {
		if (templateName == null || templateName.isBlank()) {
			templateName = "聊天搭子";
		}
		if (provider == null || provider.isBlank()) {
			provider = "openai";
		}
		provider = provider.toLowerCase();
		if (apiKey == null) {
			apiKey = "";
		}
		if (triggerApiKey == null || triggerApiKey.isBlank()) {
			triggerApiKey = apiKey;
		}
		if (scheduleApiKey == null || scheduleApiKey.isBlank()) {
			scheduleApiKey = apiKey;
		}
		if (baseUrl == null) {
			baseUrl = "";
		}
		if (model == null || model.isBlank()) {
			model = "gpt-4o-mini";
		}
		if (systemPrompt == null || systemPrompt.isBlank()) {
			systemPrompt = TEMPLATE_BUDDY;
		}
		if (triggerRegex == null || triggerRegex.isBlank()) {
			triggerRegex = "(?i)^(ai|@ai)[\uff0c, ]";
		}
		if (blockedRegexPatterns == null) {
			blockedRegexPatterns = new ArrayList<>();
		}
		if (blockedPlayers == null) {
			blockedPlayers = new ArrayList<>();
		}
		blockedPlayers.removeIf(b -> b == null || b.name == null || b.name.isBlank());
		temperature = clamp(temperature, 0.0, 2.0);
		maxTokens = clamp(maxTokens, 1, 4096);
		maxReplyMessages = clamp(maxReplyMessages, 1, 10);
		maxCharsPerMessage = clamp(maxCharsPerMessage, 1, 256);
		contextLength = clamp(contextLength, 0, 100);
		scheduleIntervalSeconds = clamp(scheduleIntervalSeconds, 5, 3600);
		triggerCooldownSeconds = clamp(triggerCooldownSeconds, 1, 3600);
		timeoutSeconds = clamp(timeoutSeconds, 5, 120);
		retryCount = clamp(retryCount, 0, 5);
		if (imageProvider == null || imageProvider.isBlank()) {
			imageProvider = "openai";
		}
		imageProvider = imageProvider.toLowerCase();
		if (imageApiKey == null) {
			imageApiKey = "";
		}
		if (imageBaseUrl == null) {
			imageBaseUrl = "";
		}
		if (imageModel == null || imageModel.isBlank()) {
			imageModel = "gpt-image-1";
		}
		if (imageSize == null || imageSize.isBlank()) {
			imageSize = "1024x1024";
		}
		if (imageRatio == null || imageRatio.isBlank()) {
			imageRatio = "1:1";
		}
		if (imageTriggerRegex == null || imageTriggerRegex.isBlank()) {
			imageTriggerRegex = "(?i)^(?:画|生成图片|img)[：: ]?(.*)$";
		}
		imageTimeoutSeconds = clamp(imageTimeoutSeconds, 30, 3600);
		imageRetryCount = clamp(imageRetryCount, 0, 3);
		imageCooldownSeconds = clamp(imageCooldownSeconds, 5, 3600);
		if (imageUploaderType == null) {
			imageUploaderType = "none";
		}
		imageUploaderType = imageUploaderType.toLowerCase();
		if (!List.of("none", "catbox", "0x0", "imgbb", "custom").contains(imageUploaderType)) {
			imageUploaderType = "none";
		}
		if (imageUploaderUrl == null) {
			imageUploaderUrl = "";
		}
		if (imageApiKey.isEmpty()) {
			imageApiKey = apiKey;
		}
		if (imageBaseUrl.isEmpty()) {
			imageBaseUrl = baseUrl;
		}
	}

	private static double clamp(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}
}
