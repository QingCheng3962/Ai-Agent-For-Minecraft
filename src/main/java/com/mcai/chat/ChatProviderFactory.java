package com.mcai.chat;

import java.util.Map;

public final class ChatProviderFactory {
	private static final Map<String, String> DEFAULT_BASE_URLS = Map.of(
			"openai", "https://api.openai.com/v1",
			"deepseek", "https://api.deepseek.com/v1",
			"moonshot", "https://api.moonshot.cn/v1",
			"anthropic", "https://api.anthropic.com",
			"ollama", "http://localhost:11434/v1");

	private ChatProviderFactory() {
	}

	public static ChatProvider create(ChatConfig config, String apiKeyOverride) {
		String provider = config.provider == null ? "openai" : config.provider.toLowerCase();
		String baseUrl = config.baseUrl;
		if (baseUrl == null || baseUrl.isBlank()) {
			baseUrl = DEFAULT_BASE_URLS.getOrDefault(provider, "");
		}
		if (baseUrl == null || baseUrl.isBlank()) {
			throw new IllegalArgumentException(
					"[mcai] 聊天 AI 需要 baseUrl，请在 mcai_chat.json 中设置 provider 或 baseUrl。");
		}
		if ("anthropic".equals(provider)) {
			return new AnthropicChatProvider(baseUrl, config, apiKeyOverride);
		}
		return new OpenAiChatProvider(baseUrl, config, apiKeyOverride);
	}
}
