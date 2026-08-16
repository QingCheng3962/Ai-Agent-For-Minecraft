package com.mcai.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mcai.config.McAiConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class OpenAIClient {
	private final McAiConfig config;
	private final HttpClient http;

	public OpenAIClient(McAiConfig config) {
		this.config = config;
		this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
	}

	public ChatResult chat(List<ChatMessage> messages, List<ToolSpec> tools) {		try {
			JsonObject body = new JsonObject();
			body.addProperty("model", config.model);
			body.addProperty("temperature", config.temperature);
			if (config.maxTokens > 0) {
				body.addProperty("max_tokens", config.maxTokens);
			}
			body.add("messages", messagesToJson(messages));
			if (!tools.isEmpty()) {
				body.add("tools", toolsToJson(tools));
				body.addProperty("tool_choice", "auto");
			}

			HttpRequest.Builder rb = HttpRequest.newBuilder(
					URI.create(trimTrailingSlash(config.baseUrl) + "/chat/completions"))
					.header("Content-Type", "application/json")
					.timeout(Duration.ofSeconds(180))
					.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
			if (config.apiKey != null && !config.apiKey.isBlank()) {
				rb.header("Authorization", "Bearer " + config.apiKey);
			}

			HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() >= 400) {
				return errorResult("HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 400));
			}
			return parseResponse(resp.body());
		} catch (IOException e) {
			return errorResult("Network error: " + e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return errorResult("请求被中断");
		} catch (Exception e) {
			return errorResult("Unexpected error: " + e);
		}
	}

	public java.util.List<String> listModels() throws IOException {
		HttpRequest.Builder rb = HttpRequest.newBuilder(
				URI.create(trimTrailingSlash(config.baseUrl) + "/models"))
				.header("Accept", "application/json")
				.timeout(Duration.ofSeconds(30))
				.GET();
		if (config.apiKey != null && !config.apiKey.isBlank()) {
			rb.header("Authorization", "Bearer " + config.apiKey);
		}
		HttpResponse<String> resp;
		try {
			resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("请求被中断");
		}
		if (resp.statusCode() >= 400) {
			throw new IOException("HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 300));
		}
		JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
		JsonArray data = root.getAsJsonArray("data");
		java.util.List<String> ids = new java.util.ArrayList<>();
		if (data != null) {
			for (JsonElement e : data) {
				if (e.isJsonObject()) {
					JsonElement id = e.getAsJsonObject().get("id");
					if (id != null && !id.isJsonNull()) {
						String s = id.getAsString();
						if (!s.isBlank()) {
							ids.add(s);
						}
					}
				}
			}
		}
		ids.sort(java.util.Comparator.naturalOrder());
		return ids;
	}

	private ChatResult parseResponse(String body) {
		JsonObject root = JsonParser.parseString(body).getAsJsonObject();
		JsonArray choices = root.getAsJsonArray("choices");
		if (choices == null || choices.isEmpty()) {
			return errorResult("Empty response: " + truncate(body, 300));
		}
		JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
		ChatResult result = new ChatResult();
		JsonElement content = message.get("content");
		result.content = content == null || content.isJsonNull() ? "" : content.getAsString();
		result.toolCalls = new ArrayList<>();
		JsonElement toolCalls = message.get("tool_calls");
		if (toolCalls != null && toolCalls.isJsonArray()) {
			for (JsonElement e : toolCalls.getAsJsonArray()) {
				JsonObject tc = e.getAsJsonObject();
				JsonObject fn = tc.getAsJsonObject("function");
				result.toolCalls.add(new ToolCall(
						str(tc, "id"),
						str(tc, "type"),
						str(fn, "name"),
						str(fn, "arguments")));
			}
		}
		return result;
	}

	private static ChatResult errorResult(String msg) {
		ChatResult r = new ChatResult();
		r.error = msg;
		return r;
	}

	private JsonArray messagesToJson(List<ChatMessage> messages) {
		JsonArray arr = new JsonArray();
		for (ChatMessage m : messages) {
			JsonObject o = new JsonObject();
			o.addProperty("role", m.role);
			if (m.content != null) {
				o.addProperty("content", m.content);
			}
			if ("tool".equals(m.role)) {
				o.addProperty("tool_call_id", m.toolCallId);
				if (m.name != null) {
					o.addProperty("name", m.name);
				}
			}
			if (m.toolCalls != null && !m.toolCalls.isEmpty()) {
				JsonArray tcs = new JsonArray();
				for (ToolCall tc : m.toolCalls) {
					JsonObject tco = new JsonObject();
					tco.addProperty("id", tc.id);
					if (tc.type != null) {
						tco.addProperty("type", tc.type);
					}
					JsonObject fn = new JsonObject();
					fn.addProperty("name", tc.name);
					if (tc.arguments != null) {
						fn.addProperty("arguments", tc.arguments);
					}
					tco.add("function", fn);
					tcs.add(tco);
				}
				o.add("tool_calls", tcs);
			}
			arr.add(o);
		}
		return arr;
	}

	private JsonArray toolsToJson(List<ToolSpec> tools) {
		JsonArray arr = new JsonArray();
		for (ToolSpec t : tools) {
			JsonObject fn = new JsonObject();
			fn.addProperty("type", "function");
			JsonObject f = new JsonObject();
			f.addProperty("name", t.name);
			f.addProperty("description", t.description);
			f.add("parameters", t.parameters != null ? t.parameters : new JsonObject());
			fn.add("function", f);
			arr.add(fn);
		}
		return arr;
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}

	private static String trimTrailingSlash(String s) {
		while (s.endsWith("/")) {
			s = s.substring(0, s.length() - 1);
		}
		return s;
	}

	private static String truncate(String s, int max) {
		if (s == null) {
			return "";
		}
		return s.length() <= max ? s : s.substring(0, max) + "...";
	}
}
