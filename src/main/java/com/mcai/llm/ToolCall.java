package com.mcai.llm;

public final class ToolCall {
	public String id;
	public String type;
	public String name;
	public String arguments;

	public ToolCall(String id, String type, String name, String arguments) {
		this.id = id;
		this.type = type;
		this.name = name;
		this.arguments = arguments;
	}
}
