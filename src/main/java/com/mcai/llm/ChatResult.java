package com.mcai.llm;

import java.util.List;

public final class ChatResult {
	public String content;
	public List<ToolCall> toolCalls;
	public String error;

	public boolean ok() {
		return error == null;
	}
}
