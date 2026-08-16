import com.mcai.agent.Tools;
import com.mcai.config.McAiConfig;
import com.mcai.llm.ChatMessage;
import com.mcai.llm.ChatResult;
import com.mcai.llm.OpenAIClient;
import com.mcai.llm.ToolCall;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class SmokeTest {
	public static void main(String[] args) throws Exception {
		HttpServer server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 18099), 0);
		server.createContext("/v1/chat/completions", SmokeTest::handle);
		server.createContext("/v1/models", SmokeTest::handleModels);
		server.start();
		try {
			McAiConfig cfg = McAiConfig.defaults();
			cfg.baseUrl = "http://127.0.0.1:18099/v1";
			cfg.apiKey = "test-key-123";
			OpenAIClient client = new OpenAIClient(cfg);

			java.util.List<String> toolNames = new java.util.ArrayList<>();
			for (com.mcai.llm.ToolSpec ts : Tools.all()) {
				toolNames.add(ts.name);
			}
			assert toolNames.contains("run_command") : "missing run_command";
			assert toolNames.contains("write_file") : "missing write_file";
			assert toolNames.contains("edit_file") : "missing edit_file";
			assert toolNames.contains("grep") : "missing grep";
			assert toolNames.contains("find_files") : "missing find_files";
			assert toolNames.contains("copy_text") : "missing copy_text";
			assert toolNames.contains("get_state") : "missing get_state";
			assert toolNames.contains("chat") : "missing chat";

			java.util.List<String> gated = new java.util.ArrayList<>();
			for (com.mcai.llm.ToolSpec ts : Tools.forConfig(McAiConfig.defaults())) {
				gated.add(ts.name);
			}
			assert !gated.contains("run_command") : "run_command should be hidden when shell disabled";
			assert !gated.contains("write_file") : "write_file should be hidden when files disabled";
			assert !gated.contains("read_file") : "read_file should be hidden when files disabled";
			assert gated.contains("copy_text") : "copy_text should always be available";
			assert gated.contains("get_state") : "game tools must remain";

			com.google.gson.Gson gson = new com.google.gson.GsonBuilder().create();
			com.mcai.llm.ChatMessage original = com.mcai.llm.ChatMessage.assistant("x",
					java.util.List.of(new com.mcai.llm.ToolCall("call_1", "function", "move", "{\"ticks\":5}")));
			String json = gson.toJson(original);
			com.mcai.llm.ChatMessage back = gson.fromJson(json, com.mcai.llm.ChatMessage.class);
			assert "assistant".equals(back.role) : "role lost in round trip";
			assert back.toolCalls != null && back.toolCalls.size() == 1 : "tool_calls lost in round trip";
			assert "move".equals(back.toolCalls.get(0).name) : "tool call name lost: " + json;
			assert "call_1".equals(back.toolCalls.get(0).id) : "tool call id lost: " + json;

			java.util.List<String> models = client.listModels();
			assert models.size() == 3 : "expected 3 models, got " + models.size();
			assert models.contains("deepseek/deepseek-v4-flash") : "model missing: " + models;
			assert models.get(0).equals("deepseek-chat") : "not sorted: " + models;

			List<ChatMessage> messages = List.of(
					ChatMessage.system(cfg.systemPrompt),
					ChatMessage.user("go forward two steps"));
			ChatResult r1 = client.chat(messages, Tools.all());
			assert r1.ok() : "request failed: " + r1.error;
			assert r1.content.equals("thinking") : "unexpected content: " + r1.content;
			assert r1.toolCalls != null && r1.toolCalls.size() == 2 : "expected 2 tool calls";
			ToolCall first = r1.toolCalls.get(0);
			assert "get_state".equals(first.name) : "expected get_state, got " + first.name;
			ToolCall second = r1.toolCalls.get(1);
			assert "move".equals(second.name) : "expected move, got " + second.name;
			assert second.arguments.contains("\"direction\"") && second.arguments.contains("\"ticks\"")
					: "bad args: " + second.arguments;

			ChatResult r2 = client.chat(List.of(ChatMessage.tool(first.id, first.name, "{\"ok\":true}")), Tools.all());
			assert r2.ok() : "2nd request failed: " + r2.error;
			assert r2.content.equals("done") : "unexpected 2nd content: " + r2.content;

			java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
			java.util.concurrent.atomic.AtomicReference<ChatResult> interruptedResult =
					new java.util.concurrent.atomic.AtomicReference<>();
			java.util.concurrent.atomic.AtomicLong elapsed = new java.util.concurrent.atomic.AtomicLong();
			Thread t = new Thread(() -> {
				started.countDown();
				long t0 = System.currentTimeMillis();
				interruptedResult.set(client.chat(List.of(ChatMessage.user("slow")), Tools.all()));
				elapsed.set(System.currentTimeMillis() - t0);
			});
			t.start();
			started.await();
			Thread.sleep(300);
			long t0 = System.currentTimeMillis();
			t.interrupt();
			t.join(5000);
			long waited = System.currentTimeMillis() - t0;
			assert waited < 3000 : "interrupt did not abort quickly, took " + waited + "ms";
			ChatResult r3 = interruptedResult.get();
			assert r3 != null && !r3.ok() : "expected interrupted error, got " + r3;
			assert r3.error.contains("中断") : "unexpected error: " + r3.error;

			ChatResult r4 = client.chat(List.of(ChatMessage.user("after interrupt")), Tools.all());
			assert r4.ok() : "client unusable after interrupt: " + r4.error;
			System.out.println("SMOKE TEST OK");
		} finally {
			server.stop(0);
		}
	}

	private static void handleModels(HttpExchange ex) throws java.io.IOException {
		String auth = ex.getRequestHeaders().getFirst("Authorization");
		assert "Bearer test-key-123".equals(auth) : "bad auth for models: " + auth;
		String resp = "{\"object\":\"list\",\"data\":["
				+ "{\"id\":\"gpt-4o-mini\",\"object\":\"model\",\"created\":0,\"owned_by\":\"openai\"},"
				+ "{\"id\":\"deepseek-chat\",\"object\":\"model\",\"created\":0,\"owned_by\":\"deepseek\"},"
				+ "{\"id\":\"deepseek/deepseek-v4-flash\",\"object\":\"model\",\"created\":0,\"owned_by\":\"siliconflow\"}"
				+ "]}";
		byte[] out = resp.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().set("Content-Type", "application/json");
		ex.sendResponseHeaders(200, out.length);
		try (OutputStream os = ex.getResponseBody()) {
			os.write(out);
		}
	}

	private static void handle(HttpExchange ex) throws java.io.IOException {
		byte[] body = ex.getRequestBody().readAllBytes();
		String req = new String(body, StandardCharsets.UTF_8);
		String auth = ex.getRequestHeaders().getFirst("Authorization");
		assert "Bearer test-key-123".equals(auth) : "bad auth: " + auth;
		assert req.contains("\"model\":\"gpt-4o-mini\"") : "model missing: " + req;
		assert req.contains("\"tools\"") : "tools missing";

		if (req.contains("slow")) {
			try {
				Thread.sleep(30000);
			} catch (InterruptedException ignored) {
			}
		}

		String resp;
		if (req.contains("tool_call_id")) {
			resp = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"done\"}}]}";
		} else {
			resp = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"thinking\",\"tool_calls\":["
					+ "{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"get_state\",\"arguments\":\"{}\"}},"
					+ "{\"id\":\"call_2\",\"type\":\"function\",\"function\":{\"name\":\"move\",\"arguments\":\"{\\\"direction\\\":\\\"forward\\\",\\\"ticks\\\":40}\"}}"
					+ "]}}]}";
		}
		byte[] out = resp.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().set("Content-Type", "application/json");
		ex.sendResponseHeaders(200, out.length);
		try (OutputStream os = ex.getResponseBody()) {
			os.write(out);
		}
	}
}
