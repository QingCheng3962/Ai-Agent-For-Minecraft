package com.mcai.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcai.llm.ToolCall;

public final class ActionSpec {
	public final ToolCall toolCall;
	public final String name;
	private final JsonObject args;

	public ActionSpec(ToolCall toolCall) {
		this.toolCall = toolCall;
		this.name = toolCall.name;
		this.args = parseArgs(toolCall.arguments);
	}

	private static JsonObject parseArgs(String arguments) {
		if (arguments == null || arguments.isBlank()) {
			return new JsonObject();
		}
		try {
			JsonElement e = JsonParser.parseString(arguments);
			if (e.isJsonObject()) {
				return e.getAsJsonObject();
			}
		} catch (Exception ignored) {
		}
		return new JsonObject();
	}

	public String str(String key, String def) {
		JsonElement e = args.get(key);
		return e == null || e.isJsonNull() ? def : e.getAsString();
	}

	public int intArg(String key, int def) {
		JsonElement e = args.get(key);
		return e == null || e.isJsonNull() ? def : e.getAsInt();
	}

	public float floatArg(String key, float def) {
		JsonElement e = args.get(key);
		return e == null || e.isJsonNull() ? def : e.getAsFloat();
	}

	public boolean boolArg(String key, boolean def) {
		JsonElement e = args.get(key);
		return e == null || e.isJsonNull() ? def : e.getAsBoolean();
	}
}
