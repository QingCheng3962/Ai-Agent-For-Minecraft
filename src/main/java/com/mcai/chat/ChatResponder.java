package com.mcai.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcai.llm.ChatMessage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ChatResponder {
	private static final Pattern GENERIC_CHAT_PREFIX = Pattern.compile(
			"^\\s*(?:[<\\[][^\\]<>]{0,32}[>\\]]|\\|[^»>]{0,64}[»>])\\s*[:\uff1a]?\\s*");
	private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile(
			"^\\s*\\|?\\s*\\[[^\\]]+\\]\\s*([^\\s»]+)\\s*[»>]");
	private static final Pattern LOG_CHAT_LINE = Pattern.compile(
			"^(?:\\[\\d{1,2}:\\d{2}:\\d{2}\\]\\s*\\[.*?\\]\\s*:\\s*)?(?:\\[Not Secure\\]\\s*)?(?:\\[CHAT\\]\\s*)?<([^>]+)>\\s*(.+)$");
	private static final int MAX_LOG_LINES = 200;

	private static ChatResponder instance;

	private volatile ChatConfig config;
	private ChatProvider triggerProvider;
	private ChatProvider scheduleProvider;
	private final AtomicBoolean triggerReplying = new AtomicBoolean(false);
	private final AtomicBoolean scheduleReplying = new AtomicBoolean(false);
	private final AtomicBoolean scheduledRequestQueued = new AtomicBoolean(false);
	private final AtomicBoolean imageGenerating = new AtomicBoolean(false);
	private final AtomicReference<String> lastPlayerMessage = new AtomicReference<>();
	private final AtomicLong lastPlayerMessageTime = new AtomicLong(0);
	private final AtomicLong lastHandledUserMessageTime = new AtomicLong(0);
	private final AtomicLong nextScheduledReplyTime = new AtomicLong(0);
	private final AtomicLong nextTriggerReplyTime = new AtomicLong(0);
	private final AtomicLong nextImageCooldownTime = new AtomicLong(0);
	private final Map<String, Long> recentAiMessages = new ConcurrentHashMap<>();
	private final Map<String, Long> recentPlayerMessages = new ConcurrentHashMap<>();
	private final Deque<ChatMessage> context = new ArrayDeque<>();
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "mcai-chat-scheduler");
		t.setDaemon(true);
		return t;
	});
	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(120)).build();
	private volatile Pattern triggerPattern;
	private volatile Pattern imageTriggerPattern;
	private volatile List<Pattern> blockedPatterns = List.of();
	private volatile boolean stripChatPrefix = true;
	private final Deque<String> activityLog = new ArrayDeque<>();
	private final Path logPath = FabricLoader.getInstance().getGameDir().resolve("logs/latest.log");
	private long logFilePosition = 0;
	private String pendingLogLine = "";
	private boolean logBaselineReady = false;

	private ChatResponder(ChatConfig config) {
		this.config = config;
		this.triggerProvider = safeCreateProvider(config, config.triggerApiKey);
		this.scheduleProvider = safeCreateProvider(config, config.scheduleApiKey);
		compilePatterns();
		nextScheduledReplyTime.set(System.currentTimeMillis() + (long) config.scheduleIntervalSeconds * 1000);
		nextTriggerReplyTime.set(System.currentTimeMillis());
		nextImageCooldownTime.set(System.currentTimeMillis());
	}

	public static ChatResponder getInstance() {
		return instance;
	}

	public static void init() {
		if (instance == null) {
			ChatConfig cfg = ChatConfig.load();
			instance = new ChatResponder(cfg);
			instance.scheduler.scheduleAtFixedRate(instance::schedulerTick, 1, 1, TimeUnit.SECONDS);
		}
	}

	public ChatConfig getConfig() {
		return config;
	}

	public void reloadConfig() {
		ChatConfig newConfig = ChatConfig.load();
		config = newConfig;
		triggerProvider = safeCreateProvider(newConfig, newConfig.triggerApiKey);
		scheduleProvider = safeCreateProvider(newConfig, newConfig.scheduleApiKey);
		compilePatterns();
		synchronized (context) {
			context.clear();
		}
		lastPlayerMessage.set(null);
		lastPlayerMessageTime.set(0);
		lastHandledUserMessageTime.set(0);
		nextScheduledReplyTime.set(System.currentTimeMillis() + (long) newConfig.scheduleIntervalSeconds * 1000);
		nextTriggerReplyTime.set(System.currentTimeMillis());
		nextImageCooldownTime.set(System.currentTimeMillis());
		recentAiMessages.clear();
		recentPlayerMessages.clear();
		triggerReplying.set(false);
		scheduleReplying.set(false);
		imageGenerating.set(false);
		if (newConfig.chatLogEnabled && newConfig.chatLogReload) {
			sendLocalChatMessage("\u00a7a[mcai AI Player] \u00a7f配置已重载。");
		}
	}

	public void refreshFromDisk() {
		ChatConfig old = config;
		ChatConfig newConfig = ChatConfig.load();
		config = newConfig;
		triggerProvider = safeCreateProvider(newConfig, newConfig.triggerApiKey);
		scheduleProvider = safeCreateProvider(newConfig, newConfig.scheduleApiKey);
		compilePatterns();
		if (old == null || !java.util.Objects.equals(old.systemPrompt, newConfig.systemPrompt)) {
			synchronized (context) {
				context.clear();
			}
			lastPlayerMessage.set(null);
			lastPlayerMessageTime.set(0);
			lastHandledUserMessageTime.set(0);
			if (newConfig.chatLogEnabled && newConfig.chatLogReload) {
				sendLocalChatMessage("\u00a7a[mcai AI Player] \u00a7f提示词已更新，上下文已重开。");
			}
		}
	}

	public void clearContext() {
		synchronized (context) {
			context.clear();
		}
		if (config.chatLogEnabled && config.chatLogClearContext) {
			sendLocalChatMessage("\u00a7a[mcai AI Player] \u00a7f上下文已清空。");
		}
	}

	public List<String> getActivityLog() {
		synchronized (activityLog) {
			return List.copyOf(activityLog);
		}
	}

	private void logActivity(String line) {
		synchronized (activityLog) {
			activityLog.addLast(line);
			while (activityLog.size() > MAX_LOG_LINES) {
				activityLog.removeFirst();
			}
		}
	}

	private ChatProvider safeCreateProvider(ChatConfig cfg, String apiKey) {
		try {
			return ChatProviderFactory.create(cfg, apiKey);
		} catch (Exception e) {
			error("创建聊天 AI 提供方失败: " + e.getMessage());
			return null;
		}
	}

	private boolean isInGame() {
		Minecraft client = Minecraft.getInstance();
		return client != null && client.player != null && client.getConnection() != null;
	}

	private void schedulerTick() {
		try {
			if (config != null && config.useLogFile) {
				tailLogFile();
			}
			if (config == null || !config.enabled || !config.scheduleEnabled) {
				return;
			}
			if (scheduleReplying.get() || scheduledRequestQueued.get() || imageGenerating.get()) {
				return;
			}
			if (!isInGame()) {
				lastPlayerMessage.set(null);
				lastPlayerMessageTime.set(0);
				lastHandledUserMessageTime.set(System.currentTimeMillis());
				nextScheduledReplyTime.set(System.currentTimeMillis() + 60000);
				return;
			}
			cleanupRecentMessages();
			long now = System.currentTimeMillis();
			if (now < nextScheduledReplyTime.get()) {
				return;
			}
			String msg = lastPlayerMessage.get();
			if (msg == null || msg.isBlank()) {
				return;
			}
			if (lastPlayerMessageTime.get() <= lastHandledUserMessageTime.get()) {
				return;
			}
			if (scheduledRequestQueued.compareAndSet(false, true)) {
				long userMessageTime = lastPlayerMessageTime.get();
				lastHandledUserMessageTime.set(userMessageTime);
				Minecraft client = Minecraft.getInstance();
				client.execute(() -> {
					scheduledRequestQueued.set(false);
					if (!isInGame()) {
						return;
					}
					String latestMsg = lastPlayerMessage.get();
					if (latestMsg != null && !latestMsg.isBlank()) {
						if (config.chatLogEnabled && config.chatLogSchedule) {
							sendLocalChatMessage("\u00a7a[mcai AI Player] \u00a7f自动回复已触发。");
						}
						startAiReply(latestMsg, userMessageTime, false);
					}
				});
			}
		} catch (Exception e) {
			error("定时回复出错: " + e);
		}
	}

	public void onGameMessage(String text) {
		processIncoming(text, null, true);
	}

	public void onChatMessage(String text, Object sender) {
		processIncoming(text, sender != null ? profileName(sender) : null, false);
	}

	private void onLogPlayerMessage(String text, String playerName) {
		processIncoming(text, playerName, false);
	}

	private void processIncoming(String text, String playerName, boolean fromGame) {
		if (text == null || text.isBlank() || !isInGame()) {
			return;
		}
		String originalText = text;
		if (playerName == null) {
			playerName = extractPlayerNameFromRawText(originalText);
		}
		if (isSelfPlayerName(playerName)) {
			if (config.debugLog) {
				log("忽略自己的消息: '" + playerName + "'");
			}
			return;
		}
		if (isPlayerBlocked(playerName)) {
			if (config.debugLog) {
				log("忽略被屏蔽玩家的消息: '" + playerName + "'");
			}
			return;
		}
		if (playerName != null) {
			text = stripPlayerPrefix(text, playerName);
		} else if (fromGame) {
			String stripped = stripPlayerPrefix(text, null);
			if (stripped.equals(originalText)) {
				if (config.debugLog) {
					log("忽略非玩家系统消息: '" + originalText + "'");
				}
				return;
			}
			text = stripped;
		}
		String dedupeKey = (playerName == null ? "null" : playerName) + "|" + text;
		long now = System.currentTimeMillis();
		Long lastTime = recentPlayerMessages.get(dedupeKey);
		if (lastTime != null && now - lastTime < 2000) {
			if (config.debugLog) {
				log("忽略重复消息 (player='" + playerName + "', text='" + text + "')");
			}
			return;
		}
		recentPlayerMessages.put(dedupeKey, now);
		if (config.debugLog && !originalText.equals(text)) {
			log("剥离聊天前缀: '" + originalText + "' -> '" + text + "'");
		}
		logActivity((playerName == null ? "?" : playerName) + " > " + text);
		if (config.enabled && config.imageGenerationEnabled && matchesImageTrigger(text)) {
			long currentTime = System.currentTimeMillis();
			if (currentTime >= nextImageCooldownTime.get()) {
				nextImageCooldownTime.set(currentTime + (long) config.imageCooldownSeconds * 1000);
				String prompt = extractImagePrompt(text);
				if (prompt != null && !prompt.isBlank()) {
					lastHandledUserMessageTime.set(System.currentTimeMillis());
					lastPlayerMessage.set(text);
					lastPlayerMessageTime.set(System.currentTimeMillis());
					if (config.chatLogEnabled && config.chatLogTrigger) {
						sendLocalChatMessage("\u00a7a[mcai AI Player] \u00a7f文生图已触发，提示词: " + prompt);
					}
					generateImage(prompt);
				}
			} else {
				long remaining = nextImageCooldownTime.get() - System.currentTimeMillis();
				log("文生图冷却中 (剩余 " + remaining + " ms)。");
				lastHandledUserMessageTime.set(System.currentTimeMillis());
			}
			return;
		}
		lastPlayerMessage.set(text);
		lastPlayerMessageTime.set(System.currentTimeMillis());
		if (config == null || !config.enabled) {
			return;
		}
		if (config.contextEnabled && config.contextLength > 0) {
			addToContext(ChatMessage.user(text));
		}
		boolean matches = config.triggerEnabled && matchesTrigger(text);
		boolean autoReply = config.autoReplyEnabled;
		boolean shouldReply = autoReply || matches;
		boolean cooldownPassed = nextTriggerReplyTime.get() <= System.currentTimeMillis();
		if (config.debugLog) {
			log("消息='" + text + "', autoReply=" + autoReply + ", regexMatch=" + matches
					+ ", cooldownPassed=" + cooldownPassed);
		}
		if (shouldReply && cooldownPassed) {
			nextTriggerReplyTime.set(System.currentTimeMillis() + (long) config.triggerCooldownSeconds * 1000);
			lastHandledUserMessageTime.set(lastPlayerMessageTime.get());
			log("回复已启动，冷却 " + config.triggerCooldownSeconds + " 秒。");
			if (config.chatLogEnabled && config.chatLogTrigger) {
				sendLocalChatMessage("\u00a7a[mcai AI Player] \u00a7f回复已启动。");
			}
			startAiReply(text, lastPlayerMessageTime.get(), true);
		} else if (shouldReply) {
			long remaining = nextTriggerReplyTime.get() - System.currentTimeMillis();
			log("命中回复但冷却中 (剩余 " + remaining + " ms)。");
			lastHandledUserMessageTime.set(System.currentTimeMillis());
		}
	}

	private void tailLogFile() {
		try {
			if (!Files.exists(logPath)) {
				logFilePosition = 0;
				return;
			}
			long size = Files.size(logPath);
			if (!logBaselineReady) {
				logFilePosition = size;
				logBaselineReady = true;
				return;
			}
			if (size < logFilePosition) {
				logFilePosition = 0;
				pendingLogLine = "";
			}
			if (size <= logFilePosition) {
				return;
			}
			byte[] data = new byte[(int) (size - logFilePosition)];
			try (RandomAccessFile raf = new RandomAccessFile(logPath.toFile(), "r")) {
				raf.seek(logFilePosition);
				raf.readFully(data);
				logFilePosition = size;
			}
			String combined = pendingLogLine + new String(data, StandardCharsets.UTF_8);
			int lastNl = combined.lastIndexOf('\n');
			if (lastNl >= 0) {
				String complete = combined.substring(0, lastNl);
				pendingLogLine = combined.substring(lastNl + 1);
				for (String line : complete.split("\n", -1)) {
					processLogLine(line.trim());
				}
			} else {
				pendingLogLine = combined;
			}
		} catch (Exception e) {
			logFilePosition = 0;
		}
	}

	private void processLogLine(String line) {
		if (line == null || line.isEmpty()) {
			return;
		}
		Matcher m = LOG_CHAT_LINE.matcher(line);
		if (m.matches()) {
			String name = m.group(1).trim();
			String msg = m.group(2).trim();
			if (!msg.isEmpty()) {
				onLogPlayerMessage(msg, name);
			}
		}
	}

	private String extractPlayerNameFromRawText(String rawText) {
		Matcher m = PLAYER_NAME_PATTERN.matcher(rawText);
		if (m.find()) {
			return m.group(1);
		}
		Pattern p2 = Pattern.compile("^\\s*[<\\[]\\s*([^\\]<>]+)\\s*[>\\]]");
		Matcher m2 = p2.matcher(rawText);
		if (m2.find()) {
			return m2.group(1).trim();
		}
		return null;
	}

	private boolean isPlayerBlocked(String playerName) {
		if (playerName == null || config.blockedPlayers == null) {
			return false;
		}
		for (ChatConfig.BlockedPlayer b : config.blockedPlayers) {
			if (b != null && b.enabled && b.name != null && b.name.equalsIgnoreCase(playerName)) {
				return true;
			}
		}
		return false;
	}

	private boolean isSelfPlayerName(String playerName) {
		if (playerName == null) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.player == null) {
			return false;
		}
		String selfName = client.player.getName().getString();
		return selfName != null && selfName.equalsIgnoreCase(playerName);
	}

	private String stripPlayerPrefix(String text, String playerName) {
		if (!stripChatPrefix) {
			return text;
		}
		if (playerName != null) {
			String quotedName = Pattern.quote(playerName);
			Pattern prefixPattern = Pattern.compile(
					"^<[^<>]*" + quotedName + "[^<>]*>\\s*|^\\[[^\\[\\]]*" + quotedName + "[^\\[\\]]*\\]\\s*");
			Matcher matcher = prefixPattern.matcher(text);
			if (matcher.find()) {
				String stripped = text.substring(matcher.end()).trim();
				if (!stripped.isEmpty()) {
					return stripped;
				}
			}
			int nameIndex = text.indexOf(playerName);
			if (nameIndex > 0) {
				int start = -1;
				for (int i = nameIndex - 1; i >= 0; i--) {
					char c = text.charAt(i);
					if (c == '<' || c == '[') {
						start = i;
						break;
					}
				}
				int end = -1;
				for (int i = nameIndex + playerName.length(); i < text.length(); i++) {
					char c = text.charAt(i);
					if (c == '>' || c == ']') {
						end = i + 1;
						break;
					}
				}
				if (start >= 0 && end > start && end > nameIndex + playerName.length()) {
					String stripped = text.substring(end).trim();
					if (!stripped.isEmpty()) {
						return stripped;
					}
				}
			}
			return text;
		}
		Matcher genericMatcher = GENERIC_CHAT_PREFIX.matcher(text);
		if (genericMatcher.find()) {
			String stripped = text.substring(genericMatcher.end()).trim();
			if (!stripped.isEmpty()) {
				return stripped;
			}
		}
		return text;
	}

	private boolean matchesImageTrigger(String text) {
		Pattern pattern = imageTriggerPattern;
		return pattern != null && pattern.matcher(text).find();
	}

	private String extractImagePrompt(String text) {
		Pattern pattern = imageTriggerPattern;
		if (pattern == null) {
			return null;
		}
		Matcher matcher = pattern.matcher(text);
		if (matcher.find()) {
			if (matcher.groupCount() >= 1) {
				String group = matcher.group(1);
				if (group != null && !group.isBlank()) {
					return group.trim();
				}
			}
			String stripped = text.substring(matcher.end()).trim();
			return stripped.isEmpty() ? null : stripped;
		}
		return null;
	}

	private void generateImage(String prompt) {
		if (!imageGenerating.compareAndSet(false, true)) {
			log("文生图已在进行中。");
			return;
		}
		CompletableFuture.supplyAsync(() -> {
			try {
				String imageUrl = requestImageGeneration(prompt);
				if (imageUrl == null || imageUrl.isBlank()) {
					return null;
				}
				return ImageUploader.upload(imageUrl, config, httpClient);
			} catch (Exception e) {
				error("文生图失败: " + e);
				return null;
			}
		}).thenAccept(finalUrl -> {
			Minecraft client = Minecraft.getInstance();
			client.execute(() -> {
				imageGenerating.set(false);
				if (finalUrl == null || finalUrl.isBlank()) {
					if (config.chatLogEnabled && config.chatLogError) {
						sendLocalChatMessage("\u00a7c[mcai AI Player] \u00a7f图像生成失败，请查看日志。");
					}
					return;
				}
				sendChatMessage(finalUrl);
				if (config.chatLogEnabled && config.chatLogTrigger) {
					sendLocalChatMessage("\u00a7a[mcai AI Player] \u00a7f图像生成完成，已发送 URL。");
				}
			});
		});
	}

	private String requestImageGeneration(String prompt) {
		if (config == null) {
			return null;
		}
		String base = config.imageBaseUrl.trim();
		String endpoint = base.contains("images/generations")
				? base : normalizeBaseUrl(base) + "images/generations";
		String apiKey = config.imageApiKey;
		JsonObject body = new JsonObject();
		body.addProperty("model", config.imageModel);
		body.addProperty("prompt", prompt);
		body.addProperty("size", config.imageSize);
		body.addProperty("ratio", config.imageRatio);
		JsonObject extra = new JsonObject();
		extra.addProperty("response_format", "url");
		body.add("extra_body", extra);
		String json = body.toString();
		for (int retries = config.imageRetryCount; retries >= 0; retries--) {
			try {
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(endpoint))
						.timeout(Duration.ofSeconds(config.imageTimeoutSeconds))
						.header("Authorization", "Bearer " + apiKey)
						.header("Content-Type", "application/json")
						.version(HttpClient.Version.HTTP_1_1)
						.POST(HttpRequest.BodyPublishers.ofString(json))
						.build();
				HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					return parseImageResponse(response.body());
				}
				error("图像 API 返回 HTTP " + response.statusCode() + ": " + response.body());
			} catch (Exception e) {
				error("图像 API 请求错误: " + e);
			}
		}
		return null;
	}

	private static String parseImageResponse(String body) {
		JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
		JsonArray data = obj.getAsJsonArray("data");
		if (data == null || data.isEmpty()) {
			return null;
		}
		JsonObject first = data.get(0).getAsJsonObject();
		if (first.has("url") && !first.get("url").isJsonNull()) {
			return first.get("url").getAsString();
		}
		return null;
	}

	private static String normalizeBaseUrl(String baseUrl) {
		String s = baseUrl.trim();
		while (s.endsWith("/")) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "/";
	}

	private void startAiReply(String userMessage, long userMessageTime, boolean isTrigger) {
		AtomicBoolean replyingFlag = isTrigger ? triggerReplying : scheduleReplying;
		if (!replyingFlag.compareAndSet(false, true)) {
			log((isTrigger ? "触发" : "定时") + "回复已在进行中，跳过。");
			return;
		}
		ChatProvider provider = isTrigger ? triggerProvider : scheduleProvider;
		if (provider == null) {
			error((isTrigger ? "触发" : "定时") + "AI 提供方为 null。");
			if (config.chatLogEnabled && config.chatLogError) {
				sendLocalChatMessage("\u00a7c[mcai AI Player] \u00a7fAI 提供方尚未正确配置。");
			}
			replyingFlag.set(false);
			return;
		}
		try {
			if (config == null || !config.enabled) {
				replyingFlag.set(false);
				return;
			}
			if (userMessage == null || userMessage.isBlank() || !isInGame()) {
				replyingFlag.set(false);
				return;
			}
			List<ChatMessage> requestMessages = buildContext(userMessage);
			log("请求 AI (provider=" + config.provider + ", model=" + config.model
					+ ", type=" + (isTrigger ? "trigger" : "schedule") + ")...");
			provider.sendRequest(config.systemPrompt, requestMessages, config).whenComplete((reply, error) -> {
				Minecraft client = Minecraft.getInstance();
				client.execute(() -> {
					try {
						if (error != null) {
							error("AI 请求失败: " + error);
							if (config.chatLogEnabled && config.chatLogError) {
								sendLocalChatMessage("\u00a7c[mcai AI Player] \u00a7fAI 请求失败: " + error.getMessage());
							}
							return;
						}
						if (reply == null || reply.isBlank()) {
							return;
						}
						if (!isInGame()) {
							return;
						}
						handleAiReply(reply, userMessageTime);
					} finally {
						replyingFlag.set(false);
					}
				});
			});
		} catch (Exception e) {
			error("startAiReply 出错: " + e);
			replyingFlag.set(false);
		}
	}

	private void handleAiReply(String reply, long userMessageTime) {
		List<String> chunks = splitReply(reply);
		if (chunks.isEmpty()) {
			return;
		}
		int sent = 0;
		for (String chunk : chunks) {
			if (isBlocked(chunk)) {
				log("跳过被拦截的 AI 消息: " + chunk);
				if (config.chatLogEnabled && config.chatLogBlocked) {
					sendLocalChatMessage("\u00a7e[mcai AI Player] \u00a7f已拦截 AI 消息: " + chunk);
				}
				continue;
			}
			sendChatMessage(chunk);
			recentAiMessages.put(chunk, System.currentTimeMillis());
			logActivity("AI > " + chunk);
			sent++;
		}
		if (sent > 0 && config.contextEnabled && config.contextLength > 0) {
			addToContext(ChatMessage.assistant(reply, null));
		}
		nextScheduledReplyTime.set(System.currentTimeMillis() + (long) config.scheduleIntervalSeconds * 1000);
		log("已发送 " + sent + " 条 AI 消息。");
	}

	private List<ChatMessage> buildContext(String currentUserMessage) {
		List<ChatMessage> messages = new ArrayList<>();
		if (config.contextEnabled && config.contextLength > 0) {
			synchronized (context) {
				messages.addAll(context);
			}
			if (messages.isEmpty()) {
				messages.add(ChatMessage.user(currentUserMessage));
			}
			while (messages.size() > config.contextLength) {
				messages.remove(0);
			}
		} else {
			messages.add(ChatMessage.user(currentUserMessage));
		}
		return messages;
	}

	private void addToContext(ChatMessage msg) {
		if (config.contextLength <= 0) {
			return;
		}
		synchronized (context) {
			context.addLast(msg);
			while (context.size() > config.contextLength) {
				context.removeFirst();
			}
		}
	}

	private boolean matchesTrigger(String text) {
		Pattern pattern = triggerPattern;
		if (pattern == null) {
			warn("触发正则表达式为 null。");
			return false;
		}
		return pattern.matcher(text).find();
	}

	private void compilePatterns() {
		try {
			triggerPattern = Pattern.compile(config.triggerRegex);
		} catch (PatternSyntaxException e) {
			triggerPattern = null;
			error("无效的触发正则: '" + config.triggerRegex + "' -> " + e.getMessage());
		}
		try {
			imageTriggerPattern = Pattern.compile(config.imageTriggerRegex);
		} catch (PatternSyntaxException e) {
			imageTriggerPattern = null;
			error("无效的文生图触发正则: '" + config.imageTriggerRegex + "' -> " + e.getMessage());
		}
		List<Pattern> patterns = new ArrayList<>();
		if (config.blockedRegexPatterns != null) {
			for (String patternStr : config.blockedRegexPatterns) {
				try {
					patterns.add(Pattern.compile(patternStr));
				} catch (PatternSyntaxException e) {
					error("无效的拦截正则: '" + patternStr + "' -> " + e.getMessage());
				}
			}
		}
		blockedPatterns = List.copyOf(patterns);
	}

	private boolean isBlocked(String text) {
		if (!config.restrictionEnabled) {
			return false;
		}
		for (Pattern pattern : blockedPatterns) {
			if (pattern.matcher(text).find()) {
				return true;
			}
		}
		return false;
	}

	private List<String> splitReply(String reply) {
		List<String> result = new ArrayList<>();
		if (reply == null) {
			return result;
		}
		String normalized = reply.replace("\r\n", "\n").trim();
		if (normalized.isEmpty()) {
			return result;
		}
		int maxChars = Math.max(1, config.maxCharsPerMessage);
		int maxMessages = Math.max(1, config.maxReplyMessages);
		for (String paragraph : normalized.split("\n")) {
			paragraph = paragraph.trim();
			if (paragraph.isEmpty()) {
				continue;
			}
			while (paragraph.length() > maxChars && result.size() < maxMessages) {
				int cut = maxChars;
				int space = paragraph.lastIndexOf(' ', maxChars);
				if (space > 0) {
					cut = space;
				}
				String chunk = paragraph.substring(0, cut).trim();
				if (chunk.isEmpty()) {
					break;
				}
				result.add(chunk);
				paragraph = paragraph.substring(cut).trim();
			}
			if (!paragraph.isEmpty() && result.size() < maxMessages) {
				result.add(paragraph);
			}
			if (result.size() >= maxMessages) {
				break;
			}
		}
		return result;
	}

	private void sendChatMessage(String text) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.getConnection() == null) {
			warn("未连接世界，无法发送 AI 聊天消息。");
			return;
		}
		if (text.length() > 256) {
			text = text.substring(0, 256);
		}
		client.getConnection().sendChat(text);
	}

	private void sendLocalChatMessage(String message) {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}
		client.execute(() -> {
			if (client.player != null) {
				client.player.displayClientMessage(Component.literal(message), false);
			}
		});
	}

	private void cleanupRecentMessages() {
		long now = System.currentTimeMillis();
		recentAiMessages.entrySet().removeIf(e -> now - e.getValue() > 30000);
		recentPlayerMessages.entrySet().removeIf(e -> now - e.getValue() > 5000);
	}

	private static String profileName(Object profile) {
		if (profile == null) {
			return null;
		}
		Class<?> type = profile.getClass();
		try {
			Method m = type.getMethod("name");
			Object v = m.invoke(profile);
			return v instanceof String ? (String) v : null;
		} catch (Exception e1) {
			try {
				Method m = type.getMethod("getName");
				Object v = m.invoke(profile);
				return v instanceof String ? (String) v : null;
			} catch (Exception e2) {
				try {
					Field f = type.getDeclaredField("name");
					f.setAccessible(true);
					Object v = f.get(profile);
					return v instanceof String ? (String) v : null;
				} catch (Exception e3) {
					return null;
				}
			}
		}
	}

	private static void log(String msg) {
		System.out.println("[mcai-chat] " + msg);
	}

	private static void warn(String msg) {
		System.out.println("[mcai-chat] WARN " + msg);
	}

	private static void error(String msg) {
		System.err.println("[mcai-chat] ERROR " + msg);
	}
}
