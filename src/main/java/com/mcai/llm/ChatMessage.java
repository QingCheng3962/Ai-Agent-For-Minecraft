package com.mcai.llm;

import java.util.List;

public final class ChatMessage {
	public String role;
	public String content;
	public String toolCallId;
	public String name;
	public List<ToolCall> toolCalls;

	public static ChatMessage system(String content) {
		ChatMessage m = new ChatMessage();
		m.role = "system";
		m.content = content;
		return m;
	}

	public static ChatMessage user(String content) {
		ChatMessage m = new ChatMessage();
		m.role = "user";
		m.content = content;
		return m;
	}

	public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
		ChatMessage m = new ChatMessage();
		m.role = "assistant";
		m.content = content;
		m.toolCalls = toolCalls;
		return m;
	}

	public static ChatMessage tool(String toolCallId, String name, String content) {
		ChatMessage m = new ChatMessage();
		m.role = "tool";
		m.toolCallId = toolCallId;
		m.name = name;
		m.content = content;
		return m;
	}
}
