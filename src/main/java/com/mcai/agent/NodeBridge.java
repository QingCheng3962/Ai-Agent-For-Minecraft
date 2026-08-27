package com.mcai.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages a persistent Node.js helper process (tools_bridge.js) that provides
 * fast file read/write/edit/search and command execution for the agent.
 *
 * <p>If Node.js is unavailable or the bridge fails, callers should fall back to
 * the built-in Java implementations (see {@link ComputerTools}).</p>
 */
public final class NodeBridge {
	private static final AtomicLong REQ_ID = new AtomicLong(1);
	private static final ConcurrentHashMap<Long, CompletableFuture<JsonObject>> PENDING = new ConcurrentHashMap<>();
	private static final Path TOOLS_DIR = FabricLoader.getInstance().getGameDir().resolve("mcai_tools");

	private static volatile Process process;
	private static volatile BufferedReader reader;
	private static volatile BufferedWriter writer;
	private static volatile boolean available = false;

	private NodeBridge() {
	}

	public static boolean isAvailable() {
		return available;
	}

	/** Ensure the Node bridge is up. Idempotent. */
	public static synchronized void ensureAvailable() {
		if (available) {
			return;
		}
		try {
			if (process != null && process.isAlive()) {
				return;
			}
			String node = findNode();
			if (node == null) {
				return;
			}
			Path bridgeJs = extractBridgeJs();
			if (bridgeJs == null) {
				return;
			}
			Path workspace = FabricLoader.getInstance().getGameDir().resolve("mcai_workspace");
			Files.createDirectories(workspace);

			ProcessBuilder pb = new ProcessBuilder(node, bridgeJs.toString(), workspace.toString());
			pb.redirectErrorStream(true);
			process = pb.start();
			reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
			writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

			Thread rt = new Thread(NodeBridge::readLoop, "mcai-node-bridge-reader");
			rt.setDaemon(true);
			rt.start();

			JsonObject pong = requestRaw("ping", new JsonObject(), 5000);
			if (pong == null || !pong.has("ok")) {
				shutdown();
				return;
			}
			available = true;
			Runtime.getRuntime().addShutdownHook(new Thread(NodeBridge::shutdown));
			System.out.println("[mcai] Node.js bridge ready (" + node + ")");
		} catch (Exception e) {
			shutdown();
			System.err.println("[mcai] Node.js bridge unavailable: " + e);
		}
	}

	/**
	 * Send a request to the Node bridge.
	 *
	 * @return the response object (including "ok" flag), or {@code null} if the
	 *         bridge is not available or the request timed out / failed.
	 */
	public static JsonObject request(String op, JsonObject params, long timeoutMs) {
		ensureAvailable();
		if (!available) {
			return null;
		}
		return requestRaw(op, params, timeoutMs);
	}

	private static JsonObject requestRaw(String op, JsonObject params, long timeoutMs) {
		long id = REQ_ID.getAndIncrement();
		JsonObject req = new JsonObject();
		req.addProperty("id", id);
		req.addProperty("op", op);
		req.add("params", params == null ? new JsonObject() : params);
		CompletableFuture<JsonObject> fut = new CompletableFuture<>();
		PENDING.put(id, fut);
		try {
			BufferedWriter w = writer;
			if (w == null) {
				return null;
			}
			synchronized (w) {
				w.write(req.toString());
				w.newLine();
				w.flush();
			}
			return fut.get(timeoutMs, TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			PENDING.remove(id);
			return null;
		}
	}

	private static void readLoop() {
		String line;
		try {
			BufferedReader r = reader;
			while (r != null && (line = r.readLine()) != null) {
				try {
					JsonElement el = JsonParser.parseString(line);
					if (!el.isJsonObject()) {
						continue;
					}
					JsonObject obj = el.getAsJsonObject();
					JsonElement idEl = obj.get("id");
					if (idEl == null || idEl.isJsonNull()) {
						continue;
					}
					long id = idEl.getAsLong();
					CompletableFuture<JsonObject> fut = PENDING.remove(id);
					if (fut != null) {
						fut.complete(obj);
					}
				} catch (Exception ignored) {
				}
			}
		} catch (Exception ignored) {
		}
		shutdown();
	}

	private static String findNode() {
		String[] pathCandidates = { "node", "node.exe" };
		for (String c : pathCandidates) {
			if (tryVersion(c)) {
				return c;
			}
		}
		String[] extra = {
				System.getenv("ProgramFiles") + "\\nodejs\\node.exe",
				System.getenv("ProgramFiles(x86)") + "\\nodejs\\node.exe",
				System.getenv("LOCALAPPDATA") + "\\Programs\\nodejs\\node.exe",
				System.getenv("APPDATA") + "\\npm\\node.exe",
		};
		for (String p : extra) {
			if (p != null && Files.exists(Path.of(p)) && tryVersion(p)) {
				return p;
			}
		}
		return null;
	}

	private static boolean tryVersion(String node) {
		try {
			ProcessBuilder pb = new ProcessBuilder(node, "--version");
			pb.redirectErrorStream(true);
			Process p = pb.start();
			BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
			String line = r.readLine();
			if (!p.waitFor(3, TimeUnit.SECONDS)) {
				p.destroyForcibly();
			}
			return line != null && line.startsWith("v");
		} catch (Exception e) {
			return false;
		}
	}

	private static Path extractBridgeJs() {
		try {
			Files.createDirectories(TOOLS_DIR);
			Path out = TOOLS_DIR.resolve("tools_bridge.js");
			try (InputStream is = NodeBridge.class.getResourceAsStream("/assets/mcai/tools_bridge.js")) {
				if (is == null) {
					return null;
				}
				byte[] data = is.readAllBytes();
				if (!Files.exists(out) || Files.readAllBytes(out).length != data.length) {
					Files.write(out, data);
				}
			}
			return out;
		} catch (IOException e) {
			return null;
		}
	}

	public static void shutdown() {
		available = false;
		try {
			Process p = process;
			process = null;
			if (p != null && p.isAlive()) {
				p.destroy();
				if (!p.waitFor(1, TimeUnit.SECONDS)) {
					p.destroyForcibly();
				}
			}
		} catch (Exception ignored) {
		}
		try {
			BufferedWriter w = writer;
			writer = null;
			if (w != null) {
				w.close();
			}
		} catch (Exception ignored) {
		}
		for (CompletableFuture<JsonObject> f : PENDING.values()) {
			f.complete(null);
		}
		PENDING.clear();
	}
}
