package com.mcai.chat;

import com.mcai.llm.ChatMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ChatProvider {
	CompletableFuture<String> sendRequest(String systemPrompt, List<ChatMessage> messages, ChatConfig config);
}
