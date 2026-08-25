package com.mcai.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcai.llm.ChatMessage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class OpenAiChatProvider implements ChatProvider {
	private final HttpClient httpClient;
	private final String endpoint;
	private final String apiKey;
	private final String model;
	private final double temperature;
	private final int maxTokens;
	private final int timeoutSeconds;
	private final int retryCount;

	public OpenAiChatProvider(String baseUrl, ChatConfig config, String apiKey) {
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(config.timeoutSeconds)).build();
		this.endpoint = normalizeBaseUrl(baseUrl) + "chat/completions";
		this.apiKey = apiKey == null ? "" : apiKey;
		this.model = config.model;
		this.temperature = config.temperature;
		this.maxTokens = config.maxTokens;
		this.timeoutSeconds = config.timeoutSeconds;
		this.retryCount = config.retryCount;
	}

	@Override
	public CompletableFuture<String> sendRequest(String systemPrompt, List<ChatMessage> messages, ChatConfig config) {
		JsonObject body = new JsonObject();
		body.addProperty("model", model);
		body.addProperty("temperature", temperature);
		body.addProperty("max_tokens", maxTokens);
		JsonArray messageArray = new JsonArray();
		if (systemPrompt != null && !systemPrompt.isBlank()) {
			JsonObject systemObj = new JsonObject();
			systemObj.addProperty("role", "system");
			systemObj.addProperty("content", systemPrompt);
			messageArray.add(systemObj);
		}
		for (ChatMessage msg : messages) {
			JsonObject msgObj = new JsonObject();
			msgObj.addProperty("role", msg.role);
			msgObj.addProperty("content", msg.content == null ? "" : msg.content);
			messageArray.add(msgObj);
		}
		body.add("messages", messageArray);
		return sendWithRetry(body.toString(), retryCount);
	}

	private CompletableFuture<String> sendWithRetry(String json, int retriesLeft) {
		CompletableFuture<String> future = new CompletableFuture<>();
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(endpoint))
					.timeout(Duration.ofSeconds(timeoutSeconds))
					.header("Authorization", "Bearer " + apiKey)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(json))
					.build();
			httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, throwable) -> {
				if (throwable != null) {
					if (retriesLeft > 0) {
						sendWithRetry(json, retriesLeft - 1).whenComplete(relay(future));
					} else {
						future.completeExceptionally(throwable);
					}
					return;
				}
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					try {
						future.complete(parseResponse(response.body()));
					} catch (Exception e) {
						future.completeExceptionally(e);
					}
				} else if (retriesLeft > 0) {
					sendWithRetry(json, retriesLeft - 1).whenComplete(relay(future));
				} else {
					future.completeExceptionally(
							new RuntimeException("HTTP " + response.statusCode() + ": " + response.body()));
				}
			});
		} catch (Exception e) {
			future.completeExceptionally(e);
		}
		return future;
	}

	private static java.util.function.BiConsumer<String, Throwable> relay(CompletableFuture<String> future) {
		return (r, t) -> {
			if (t != null) {
				future.completeExceptionally(t);
			} else {
				future.complete(r);
			}
		};
	}

	private static String parseResponse(String body) {
		JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
		JsonArray choices = obj.getAsJsonArray("choices");
		if (choices == null || choices.isEmpty()) {
			throw new IllegalStateException("No choices in response: " + body);
		}
		JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
		if (message == null) {
			throw new IllegalStateException("No message in response: " + body);
		}
		JsonElement content = message.get("content");
		return content == null || content.isJsonNull() ? "" : content.getAsString();
	}

	private static String normalizeBaseUrl(String baseUrl) {
		String s = baseUrl.trim();
		while (s.endsWith("/")) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "/";
	}
}
