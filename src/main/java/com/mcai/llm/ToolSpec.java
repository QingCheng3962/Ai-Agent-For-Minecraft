package com.mcai.llm;

import com.google.gson.JsonObject;

public final class ToolSpec {
	public final String name;
	public final String description;
	public final JsonObject parameters;

	public ToolSpec(String name, String description, JsonObject parameters) {
		this.name = name;
		this.description = description;
		this.parameters = parameters;
	}
}
