package com.mcai.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcai.config.McAiConfig;
import com.mcai.llm.ToolSpec;

import java.util.ArrayList;
import java.util.List;

public final class Tools {
	private Tools() {
	}

	public static boolean isControlTool(String name) {
		switch (name) {
			case "move":
			case "jump":
			case "sneak":
			case "sprint":
			case "stop":
			case "look":
			case "use_held_item":
			case "attack":
			case "select_hotbar":
			case "drop_held_item":
				return true;
			default:
				return false;
		}
	}

	public static List<ToolSpec> forConfig(McAiConfig cfg) {
		List<ToolSpec> tools = new ArrayList<>();
		tools.addAll(gameTools(cfg.allowPlayerControl));
		if (cfg.allowShell) {
			tools.add(runCommandTool());
		}
		if (cfg.allowFileWrite) {
			tools.add(writeFileTool());
			tools.add(readFileTool());
			tools.add(editFileTool());
			tools.add(listDirTool());
			tools.add(grepTool());
			tools.add(findFilesTool());
		}
		tools.add(copyTextTool());
		return tools;
	}

	public static List<ToolSpec> all() {
		McAiConfig cfg = McAiConfig.defaults();
		cfg.allowShell = true;
		cfg.allowFileWrite = true;
		cfg.allowPlayerControl = true;
		return forConfig(cfg);
	}

	private static List<ToolSpec> gameTools(boolean control) {
		List<ToolSpec> tools = new ArrayList<>();
		tools.add(new ToolSpec("get_state", "Read the current player and world state as JSON. Call this to observe "
				+ "your position, health, inventory and surroundings before acting and after actions.", emptyParams()));
		if (control) {
			tools.add(new ToolSpec("move", "Move the player in a direction for a number of ticks (20 ticks = 1 second). "
					+ "left/right strafe sideways. Use stop() to halt early.",
					obj(e("direction", en("forward", "back", "left", "right"), "Direction to move"),
							e("ticks", number(1, 100), "How many ticks to keep moving"))));
			tools.add(new ToolSpec("jump", "Jump for a number of ticks.",
					obj(e("ticks", number(1, 20), "How many ticks to keep jumping"))));
			tools.add(new ToolSpec("sneak", "Toggle sneaking.",
					obj(e("on", bool(), "true to start sneaking, false to stop"))));
			tools.add(new ToolSpec("sprint", "Toggle sprinting.",
					obj(e("on", bool(), "true to start sprinting, false to stop"))));
			tools.add(new ToolSpec("stop", "Stop all movement and release all held keys immediately.", emptyParams()));
			tools.add(new ToolSpec("look", "Aim the camera at an absolute yaw and pitch. yaw 0 = +Z (south), "
					+ "90 = -X (west), -90 = +X (east), 180 = north. pitch positive looks up.",
					obj(e("yaw", number(-180, 180), "Horizontal rotation"),
							e("pitch", number(-90, 90), "Vertical rotation"))));
			tools.add(new ToolSpec("use_held_item", "Right-click: use the held item, place a block, or interact with the "
					+ "block/entity currently under the crosshair.", emptyParams()));
			tools.add(new ToolSpec("attack", "Left-click: break the block or attack the entity under the crosshair.",
					obj(e("ticks", number(1, 80), "How many ticks to keep attacking/mining (default 8)"))));
			tools.add(new ToolSpec("select_hotbar", "Select a hotbar slot.",
					obj(e("slot", number(0, 8), "Hotbar slot index 0-8"))));
			tools.add(new ToolSpec("drop_held_item", "Drop the currently held item (one).", emptyParams()));
		}
		tools.add(new ToolSpec("chat", "Send a public chat message to the server.",
				obj(e("message", str(), "The message text"))));
		return tools;
	}

	private static ToolSpec runCommandTool() {
		return new ToolSpec("run_command", "Run a system command on the user's computer (cmd.exe). "
				+ "Returns its output.",
				obj(e("command", str(), "The command to run")));
	}

	private static ToolSpec writeFileTool() {
		return new ToolSpec("write_file", "Write or overwrite a text file on the computer (documents, code, "
				+ "configs...). Relative paths are stored under the workspace folder.",
				obj(e("path", str(), "File path (absolute, or relative to workspace)"),
						e("content", str(), "Full file content")));
	}

	private static ToolSpec readFileTool() {
		return new ToolSpec("read_file", "Read a text file from the computer.",
				obj(e("path", str(), "File path")));
	}

	private static ToolSpec editFileTool() {
		return new ToolSpec("edit_file", "Precisely edit a file by replacing a text snippet. By default only the "
				+ "first occurrence is replaced; set replaceAll=true to replace every occurrence. Read the file "
				+ "first.",
				obj(e("path", str(), "File path"),
						e("find", str(), "The exact existing text to replace"),
						e("replace", str(), "The new text to put in its place"),
						e("replaceAll", bool(), "Optional: true to replace every occurrence instead of just the first")));
	}

	private static ToolSpec listDirTool() {
		return new ToolSpec("list_dir", "List a directory on the computer.",
				obj(e("path", str(), "Directory path")));
	}

	private static ToolSpec grepTool() {
		return new ToolSpec("grep", "Search files for a regex pattern, returns matching lines.",
				obj(e("path", str(), "File or directory to search"),
						e("pattern", str(), "Regex pattern")));
	}

	private static ToolSpec findFilesTool() {
		return new ToolSpec("find_files", "Find files under a directory matching a glob pattern "
				+ "(e.g. **/*.py).",
				obj(e("path", str(), "Directory to search"),
						e("pattern", str(), "Glob pattern")));
	}

	private static ToolSpec copyTextTool() {
		return new ToolSpec("copy_text", "Copy a piece of text to the system clipboard.",
				obj(e("text", str(), "The text to copy")));
	}

	private static JsonObject emptyParams() {
		JsonObject o = new JsonObject();
		o.addProperty("type", "object");
		o.add("properties", new JsonObject());
		o.addProperty("additionalProperties", false);
		return o;
	}

	private static JsonObject obj(JsonElement... props) {
		JsonObject properties = new JsonObject();
		for (JsonElement p : props) {
			JsonObject po = p.getAsJsonObject();
			properties.add(po.get("__name").getAsString(), po.get("__prop").getAsJsonObject());
		}
		JsonObject o = new JsonObject();
		o.addProperty("type", "object");
		o.add("properties", properties);
		o.addProperty("additionalProperties", false);
		return o;
	}

	private static JsonObject e(String name, JsonObject prop, String desc) {
		JsonObject wrapper = new JsonObject();
		wrapper.addProperty("__name", name);
		JsonObject p = prop;
		JsonObject copy = new JsonObject();
		for (String k : p.keySet()) {
			copy.add(k, p.get(k));
		}
		copy.addProperty("description", desc);
		wrapper.add("__prop", copy);
		return wrapper;
	}

	private static JsonObject en(String... values) {
		JsonObject o = new JsonObject();
		o.addProperty("type", "string");
		JsonArray arr = new JsonArray();
		for (String v : values) {
			arr.add(v);
		}
		o.add("enum", arr);
		return o;
	}

	private static JsonObject number(int min, int max) {
		JsonObject o = new JsonObject();
		o.addProperty("type", "integer");
		o.addProperty("minimum", min);
		o.addProperty("maximum", max);
		return o;
	}

	private static JsonObject bool() {
		JsonObject o = new JsonObject();
		o.addProperty("type", "boolean");
		return o;
	}

	private static JsonObject str() {
		JsonObject o = new JsonObject();
		o.addProperty("type", "string");
		return o;
	}
}
