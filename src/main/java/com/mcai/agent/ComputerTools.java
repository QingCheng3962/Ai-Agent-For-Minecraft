package com.mcai.agent;

import com.mcai.config.ConfigManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

public final class ComputerTools {
	private static final int MAX_OUTPUT = 8000;
	private static final int MAX_FILE_READ = 20000;
	private static final Path WORKSPACE = FabricLoader.getInstance().getGameDir().resolve("mcai_workspace");

	private ComputerTools() {
	}

	public static boolean isComputerTool(String name) {
		switch (name) {
			case "run_command":
			case "write_file":
			case "read_file":
			case "edit_file":
			case "list_dir":
			case "grep":
			case "find_files":
			case "copy_text":
				return true;
			default:
				return false;
		}
	}

	public static String execute(String name, ActionSpec spec) {
		switch (name) {
			case "run_command":
				return runCommand(spec.str("command", ""));
			case "write_file":
				return writeFile(spec.str("path", ""), spec.str("content", ""));
			case "read_file":
				return readFile(spec.str("path", ""));
			case "edit_file":
				return editFile(spec.str("path", ""), spec.str("find", ""), spec.str("replace", ""));
			case "list_dir":
				return listDir(spec.str("path", ""));
			case "grep":
				return grep(spec.str("path", ""), spec.str("pattern", ""));
			case "find_files":
				return findFiles(spec.str("path", ""), spec.str("pattern", ""));
			case "copy_text":
				return copyText(spec.str("text", ""));
			default:
				return "{\"ok\":false,\"error\":\"unknown computer tool: " + name + "\"}";
		}
	}

	private static String runCommand(String command) {
		if (!ConfigManager.get().allowShell) {
			return "{\"ok\":false,\"error\":\"系统命令被禁用。请在设置中开启「允许命令」。\"}";
		}
		if (command == null || command.isBlank()) {
			return "{\"ok\":false,\"error\":\"命令为空\"}";
		}
		ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
		pb.redirectErrorStream(true);
		try {
			Process p = pb.start();
			Instant start = Instant.now();
			java.util.concurrent.FutureTask<String> reader = new java.util.concurrent.FutureTask<>(
					() -> readAll(p.getInputStream()));
			Thread rt = new Thread(reader, "mcai-cmd-reader");
			rt.setDaemon(true);
			rt.start();
			boolean finished = p.waitFor(30, TimeUnit.SECONDS);
			String output;
			try {
				output = reader.get(finished ? 5 : 1, TimeUnit.SECONDS);
			} catch (Exception e) {
				output = "";
			}
			if (!finished) {
				p.destroyForcibly();
				return "{\"ok\":false,\"error\":\"命令执行超时(30秒)，已强制终止\"}";
			}
			long ms = Duration.between(start, Instant.now()).toMillis();
			return "{\"ok\":true,\"exit\":\"" + p.exitValue() + "\",\"ms\":\"" + ms + "\",\"output\":\""
					+ jsonEscape(truncate(output, MAX_OUTPUT)) + "\"}";
		} catch (IOException e) {
			return "{\"ok\":false,\"error\":\"无法启动命令: " + jsonEscape(e.getMessage()) + "\"}";
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return "{\"ok\":false,\"error\":\"命令被中断\"}";
		}
	}

	private static String writeFile(String path, String content) {
		if (!ConfigManager.get().allowFileWrite) {
			return "{\"ok\":false,\"error\":\"文件写入被禁用。请在设置中开启「允许文件」。\"}";
		}
		Path target = resolve(path);
		if (target == null) {
			return "{\"ok\":false,\"error\":\"非法路径\"}";
		}
		try {
			String data = content == null ? "" : content;
			Files.createDirectories(target.getParent());
			Files.writeString(target, data, StandardCharsets.UTF_8);
			return "{\"ok\":true,\"path\":\"" + jsonEscape(target.toString()) + "\",\"bytes\":\""
					+ data.getBytes(StandardCharsets.UTF_8).length + "\"}";
		} catch (IOException e) {
			return "{\"ok\":false,\"error\":\"写入失败: " + jsonEscape(e.getMessage()) + "\"}";
		}
	}

	private static String readFile(String path) {
		if (!ConfigManager.get().allowFileWrite) {
			return "{\"ok\":false,\"error\":\"文件读取被禁用。请在设置中开启「允许文件」。\"}";
		}
		Path target = resolve(path);
		if (target == null || !Files.exists(target)) {
			return "{\"ok\":false,\"error\":\"文件不存在: " + jsonEscape(path) + "\"}";
		}
		try {
			if (Files.size(target) > 512 * 1024) {
				return "{\"ok\":false,\"error\":\"文件过大，拒绝读取\"}";
			}
			String content = Files.readString(target, StandardCharsets.UTF_8);
			return "{\"ok\":true,\"path\":\"" + jsonEscape(target.toString()) + "\",\"content\":\""
					+ jsonEscape(truncate(content, MAX_FILE_READ)) + "\"}";
		} catch (IOException e) {
			return "{\"ok\":false,\"error\":\"读取失败: " + jsonEscape(e.getMessage()) + "\"}";
		}
	}

	private static String editFile(String path, String find, String replace) {
		if (!ConfigManager.get().allowFileWrite) {
			return "{\"ok\":false,\"error\":\"文件编辑被禁用。请在设置中开启「允许文件」。\"}";
		}
		if (find == null || find.isEmpty()) {
			return "{\"ok\":false,\"error\":\"find 参数不能为空\"}";
		}
		Path target = resolve(path);
		if (target == null || !Files.exists(target)) {
			return "{\"ok\":false,\"error\":\"文件不存在: " + jsonEscape(path) + "\"}";
		}
		try {
			String content = Files.readString(target, StandardCharsets.UTF_8);
			int idx = content.indexOf(find);
			if (idx < 0) {
				return "{\"ok\":false,\"error\":\"未在文件中找到要替换的文本，请先 read_file 确认内容后再试\"}";
			}
			String newContent = content.substring(0, idx) + (replace == null ? "" : replace)
					+ content.substring(idx + find.length());
			Files.writeString(target, newContent, StandardCharsets.UTF_8);
			return "{\"ok\":true,\"path\":\"" + jsonEscape(target.toString()) + "\",\"replaced\":\""
					+ jsonEscape(truncate(find, 120)) + "\"}";
		} catch (IOException e) {
			return "{\"ok\":false,\"error\":\"编辑失败: " + jsonEscape(e.getMessage()) + "\"}";
		}
	}

	private static String grep(String path, String pattern) {
		if (!ConfigManager.get().allowFileWrite) {
			return "{\"ok\":false,\"error\":\"搜索被禁用。请在设置中开启「允许文件」。\"}";
		}
		if (pattern == null || pattern.isEmpty()) {
			return "{\"ok\":false,\"error\":\"pattern 参数不能为空\"}";
		}
		Path target = resolve(path);
		if (target == null || !Files.exists(target)) {
			return "{\"ok\":false,\"error\":\"路径不存在: " + jsonEscape(path) + "\"}";
		}
		try {
			java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
			StringBuilder sb = new StringBuilder();
			int count = 0;
			if (Files.isDirectory(target)) {
				try (java.util.stream.Stream<Path> walk = Files.walk(target, 12)) {
					for (Path f : (Iterable<Path>) walk::iterator) {
						if (count >= 60) {
							break;
						}
						if (Files.isRegularFile(f) && isTextFile(f)) {
							sb.append(grepFile(f, regex, target, 60 - count));
							count = countLines(sb);
						}
					}
				}
			} else {
				if (isTextFile(target)) {
					sb.append(grepFile(target, regex, null, 60));
				}
			}
			if (sb.length() == 0) {
				return "{\"ok\":true,\"path\":\"" + jsonEscape(target.toString()) + "\",\"matches\":\"无匹配结果\"}";
			}
			return "{\"ok\":true,\"path\":\"" + jsonEscape(target.toString()) + "\",\"matches\":\""
					+ jsonEscape(truncate(sb.toString(), MAX_OUTPUT)) + "\"}";
		} catch (java.util.regex.PatternSyntaxException e) {
			return "{\"ok\":false,\"error\":\"正则表达式错误: " + jsonEscape(e.getMessage()) + "\"}";
		} catch (IOException e) {
			return "{\"ok\":false,\"error\":\"搜索失败: " + jsonEscape(e.getMessage()) + "\"}";
		}
	}

	private static String grepFile(Path file, java.util.regex.Pattern regex, Path root, int maxMatches) {
		StringBuilder sb = new StringBuilder();
		try {
			int n = 0;
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				if (n >= maxMatches) {
					break;
				}
				if (regex.matcher(line).find()) {
					if (root != null) {
						sb.append(root.relativize(file)).append(":");
					}
					sb.append(truncate(line, 200)).append("\n");
					n++;
				}
			}
		} catch (IOException ignored) {
		}
		return sb.toString();
	}

	private static int countLines(StringBuilder sb) {
		int n = 0;
		for (int i = 0; i < sb.length(); i++) {
			if (sb.charAt(i) == '\n') {
				n++;
			}
		}
		return n;
	}

	private static String findFiles(String path, String pattern) {
		if (!ConfigManager.get().allowFileWrite) {
			return "{\"ok\":false,\"error\":\"文件浏览被禁用。请在设置中开启「允许文件」。\"}";
		}
		Path target = resolve(path);
		if (target == null || !Files.isDirectory(target)) {
			return "{\"ok\":false,\"error\":\"目录不存在: " + jsonEscape(path) + "\"}";
		}
		java.nio.file.PathMatcher matcher;
		try {
			matcher = java.nio.file.FileSystems.getDefault()
					.getPathMatcher("glob:" + (pattern == null || pattern.isEmpty() ? "**" : pattern));
		} catch (IllegalArgumentException e) {
			return "{\"ok\":false,\"error\":\"无效的 glob 模式: " + jsonEscape(e.getMessage()) + "\"}";
		}
		try {
			StringBuilder sb = new StringBuilder();
			int count = 0;
			try (java.util.stream.Stream<Path> walk = Files.walk(target, 12)) {
				for (Path f : (Iterable<Path>) walk::iterator) {
					if (count >= 200) {
						break;
					}
					if (Files.isRegularFile(f)) {
						Path rel = target.relativize(f);
						if (matcher.matches(rel) || matcher.matches(rel.getFileName())) {
							sb.append(rel).append("\n");
							count++;
						}
					}
				}
			}
			if (sb.length() == 0) {
				return "{\"ok\":true,\"path\":\"" + jsonEscape(target.toString()) + "\",\"files\":\"无匹配文件\"}";
			}
			return "{\"ok\":true,\"path\":\"" + jsonEscape(target.toString()) + "\",\"files\":\""
					+ jsonEscape(truncate(sb.toString(), MAX_OUTPUT)) + "\"}";
		} catch (IOException e) {
			return "{\"ok\":false,\"error\":\"查找失败: " + jsonEscape(e.getMessage()) + "\"}";
		}
	}

	private static boolean isTextFile(Path f) {
		try {
			if (Files.size(f) > 256 * 1024) {
				return false;
			}
			byte[] head = Files.readAllBytes(f);
			for (int i = 0; i < Math.min(head.length, 1024); i++) {
				if (head[i] == 0) {
					return false;
				}
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private static String listDir(String path) {
		if (!ConfigManager.get().allowFileWrite) {
			return "{\"ok\":false,\"error\":\"目录浏览被禁用。请在设置中开启「允许文件」。\"}";
		}
		Path target = resolve(path);
		if (target == null || !Files.isDirectory(target)) {
			return "{\"ok\":false,\"error\":\"目录不存在: " + jsonEscape(path) + "\"}";
		}
		try {
			StringBuilder sb = new StringBuilder();
			int count = 0;
			java.util.List<Path> entries = new java.util.ArrayList<>();
			try (java.util.stream.Stream<Path> s = Files.list(target)) {
				s.forEach(entries::add);
			}
			entries.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()));
			for (Path e : entries) {
				if (count >= 200) {
					break;
				}
				sb.append(Files.isDirectory(e) ? "[dir]  " : "[file] ").append(e.getFileName()).append("\n");
				count++;
			}
			return "{\"ok\":true,\"path\":\"" + jsonEscape(target.toString()) + "\",\"listing\":\""
					+ jsonEscape(sb.toString()) + "\"}";
		} catch (IOException e) {
			return "{\"ok\":false,\"error\":\"浏览失败: " + jsonEscape(e.getMessage()) + "\"}";
		}
	}

	private static String copyText(String text) {
		if (text == null || text.isEmpty()) {
			return "{\"ok\":false,\"error\":\"没有可复制的文本\"}";
		}
		ClipboardUtil.copy(text);
		return "{\"ok\":true,\"copied\":\"" + jsonEscape(truncate(text, 200)) + "\"}";
	}

	private static Path resolve(String path) {
		if (path == null || path.isBlank()) {
			return null;
		}
		Path p = Path.of(path);
		if (!p.isAbsolute()) {
			return WORKSPACE.resolve(p).normalize();
		}
		return p.normalize();
	}

	private static String readAll(java.io.InputStream is) throws IOException {
		StringBuilder sb = new StringBuilder();
		byte[] buf = new byte[4096];
		int n;
		while ((n = is.read(buf)) != -1) {
			sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
		}
		return sb.toString();
	}

	private static String truncate(String s, int max) {
		if (s == null) {
			return "";
		}
		return s.length() <= max ? s : s.substring(0, max) + "...(截断)";
	}

	private static String jsonEscape(String s) {
		if (s == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(s.length() + 16);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"': sb.append("\\\""); break;
				case '\\': sb.append("\\\\"); break;
				case '\n': sb.append("\\n"); break;
				case '\r': sb.append("\\r"); break;
				case '\t': sb.append("\\t"); break;
				default:
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
			}
		}
		return sb.toString();
	}
}
