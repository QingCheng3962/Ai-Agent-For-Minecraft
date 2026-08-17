package com.mcai.agent;

import com.mcai.config.ConfigManager;
import com.mcai.config.McAiConfig;
import com.mcai.llm.ChatMessage;
import com.mcai.llm.ChatResult;
import com.mcai.llm.OpenAIClient;
import com.mcai.llm.ToolCall;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class McAiAgent {
	private static final McAiAgent INSTANCE = new McAiAgent();

	public static McAiAgent get() {
		return INSTANCE;
	}

	private static final String TASK_SUFFIX = "\n\n【自主任务模式】这是一个需要自主完成的任务。请按步骤工作："
			+ "用 get_state 观察，执行动作，再用 get_state 验证结果，然后继续下一步。"
			+ "持续工作直到目标完成或确认无法完成，不要做一步就停。"
			+ "如果同一个动作重复多次仍无变化，换一种方式，或收尾并说明原因。"
			+ "完成后用一句简短中文汇报结果。";

	public static final class Task {
		public final long id;
		public final String text;
		public final boolean userTriggered;
		public volatile String state = "pending";

		Task(long id, String text, boolean userTriggered) {
			this.id = id;
			this.text = text;
			this.userTriggered = userTriggered;
		}
	}

	private final AgentActions actions = new AgentActions();
	private final CopyOnWriteArrayList<ChatEntry> entries = new CopyOnWriteArrayList<>();
	private final List<ChatMessage> history = new CopyOnWriteArrayList<>();
	private final Object historyLock = new Object();
	private final LinkedBlockingQueue<Task> taskQueue = new LinkedBlockingQueue<>();
	private final CopyOnWriteArrayList<Task> tasks = new CopyOnWriteArrayList<>();
	private final AtomicLong taskIds = new AtomicLong(1);
	private final AtomicBoolean running = new AtomicBoolean(false);

	private final Thread worker = new Thread(this::workerLoop, "mcai-agent-worker");

	private volatile OpenAIClient client = new OpenAIClient(ConfigManager.get());
	private volatile boolean taskMode = true;
	private volatile Thread workerThread;
	private volatile Task currentTask;
	private volatile String session = "default";

	private McAiAgent() {
		history.addAll(com.mcai.config.SessionManager.load(session));
		rebuildEntriesFromHistory();
		worker.setDaemon(true);
		worker.start();
	}

	public void reloadClient() {
		client = new OpenAIClient(ConfigManager.get());
	}

	public void onClientTick(Minecraft mc) {
		actions.tick();
		ActionBatch batch = actionQueue.poll();
		if (batch != null) {
			for (ActionSpec spec : batch.specs) {
				batch.results.add(execute(spec));
			}
			batch.latch.countDown();
		}
	}

	private void workerLoop() {
		while (true) {
			Task t;
			try {
				t = taskQueue.take();
			} catch (InterruptedException e) {
				continue;
			}
			if (t == null) {
				continue;
			}
			if (!"pending".equals(t.state)) {
				continue;
			}
			currentTask = t;
			try {
				t.state = "running";
				runTurnInternal(t);
			} finally {
				currentTask = null;
			}
		}
	}

	public void sendUserMessage(String text) {
		if (text == null || text.isBlank()) {
			return;
		}
		entries.add(new ChatEntry("user", text));
		if (!ConfigManager.get().enabled) {
			entries.add(new ChatEntry("error", "智能体已禁用，请在窗口中启用。"));
			return;
		}
		addTask(text, true);
	}

	public void onGameEvent(String text) {
		entries.add(new ChatEntry("event", text));
		synchronized (historyLock) {
			history.add(ChatMessage.system("[游戏事件] " + text));
			trimHistory(history, ConfigManager.get().maxHistory);
		}
		if (ConfigManager.get().autoRespondEvents && taskQueue.isEmpty() && currentTask == null) {
			addTask(text, false);
		}
	}

	private void addTask(String text, boolean userTriggered) {
		Task t = new Task(taskIds.getAndIncrement(), text, userTriggered);
		tasks.add(t);
		taskQueue.offer(t);
	}

	private void runTurnInternal(Task task) {
		Thread.interrupted();
		workerThread = Thread.currentThread();
		running.set(true);
		String taskSession = this.session;
		try {
			McAiConfig cfg = ConfigManager.get();
			List<ChatMessage> turnHistory = new ArrayList<>(history);
			if (turnHistory.isEmpty()) {
				turnHistory.add(ChatMessage.system(cfg.systemPrompt));
			}
			if (task.userTriggered) {
				turnHistory.add(ChatMessage.user(taskMode ? task.text + TASK_SUFFIX : task.text));
				trimHistory(turnHistory, cfg.maxHistory);
			}

			String lastText = null;
			String lastActionKey = null;
			int repeats = 0;
			for (int i = 0; i < cfg.maxToolIterations && running.get(); i++) {
				while ("paused".equals(task.state) && running.get()) {
					sleepQuietly(150);
				}
				if (!running.get()) {
					break;
				}

				ChatResult result = client.chat(new ArrayList<>(turnHistory), Tools.forConfig(cfg));
				if (!result.ok()) {
					if (running.get()) {
						entries.add(new ChatEntry("error", "API 错误: " + result.error));
					}
					break;
				}
				turnHistory.add(ChatMessage.assistant(result.content, result.toolCalls));
				trimHistory(turnHistory, cfg.maxHistory);

				if (result.toolCalls == null || result.toolCalls.isEmpty()) {
					lastText = result.content;
					break;
				}

				List<ActionSpec> specs = new ArrayList<>();
				StringBuilder key = new StringBuilder();
				for (ToolCall tc : result.toolCalls) {
					ActionSpec spec = new ActionSpec(tc);
					specs.add(spec);
					key.append(spec.name).append('|').append(tc.arguments == null ? "" : tc.arguments).append(';');
				}

				if (key.toString().equals(lastActionKey)) {
					repeats++;
				} else {
					repeats = 1;
				}
				lastActionKey = key.toString();
				if (repeats >= 4) {
					turnHistory.add(ChatMessage.system("你似乎一直在重复同一个动作却没有进展。"
							+ "请先调用 get_state 检查现状，尝试不同的方法；如果任务确实无法完成，就用中文说明原因并结束。"));
					trimHistory(turnHistory, cfg.maxHistory);
					repeats = 0;
				}

				List<ActionSpec> gameSpecs = new ArrayList<>();
				java.util.Map<ActionSpec, String> results = new java.util.HashMap<>();
				for (ActionSpec s : specs) {
					if (ComputerTools.isComputerTool(s.name)) {
						results.put(s, ComputerTools.execute(s.name, s));
					} else {
						gameSpecs.add(s);
					}
				}
				if (!gameSpecs.isEmpty()) {
					ActionBatch batch = new ActionBatch(gameSpecs);
					actionQueue.offer(batch);
					boolean completed;
					try {
						completed = batch.latch.await(15, TimeUnit.SECONDS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						completed = false;
					}
					for (int j = 0; j < gameSpecs.size(); j++) {
						results.put(gameSpecs.get(j),
								completed && j < batch.results.size()
										? batch.results.get(j).result
										: "{\"ok\":false,\"error\":\"action timed out\"}");
					}
					sleepQuietly(durationMs(gameSpecs));
				}

				for (ActionSpec spec : specs) {
					turnHistory.add(ChatMessage.tool(spec.toolCall.id, spec.name, results.get(spec)));
					entries.add(new ChatEntry("tool", spec.name + " -> " + results.get(spec)));
					trimHistory(turnHistory, cfg.maxHistory);
				}
			}
			if (lastText != null && !lastText.isEmpty() && running.get()) {
				entries.add(new ChatEntry("assistant", lastText));
			}
			boolean sameSession = session.equals(taskSession);
			if (sameSession) {
				synchronized (historyLock) {
					history.clear();
					history.addAll(turnHistory);
				}
			}
			if (sameSession || com.mcai.config.SessionManager.listSessions().contains(taskSession)) {
				com.mcai.config.SessionManager.save(taskSession, new ArrayList<>(turnHistory));
			}
		} catch (Exception e) {
			if (running.get()) {
				entries.add(new ChatEntry("error", "智能体出错: " + e));
			}
		} finally {
			actions.stop();
			workerThread = null;
			running.set(false);
			if (!"cancelled".equals(task.state)) {
				task.state = "done";
			}
		}
	}

	public void pauseTask(long id) {
		Task t = findTask(id);
		if (t == null) {
			return;
		}
		if ("running".equals(t.state) || "pending".equals(t.state)) {
			if ("pending".equals(t.state)) {
				taskQueue.remove(t);
			}
			t.state = "paused";
		}
	}

	public void resumeTask(long id) {
		Task t = findTask(id);
		if (t == null) {
			return;
		}
		if ("paused".equals(t.state)) {
			if (t == currentTask) {
				t.state = "running";
			} else {
				t.state = "pending";
				taskQueue.offer(t);
			}
		}
	}

	public void cancelTask(long id) {
		Task t = findTask(id);
		if (t == null) {
			return;
		}
		if (t == currentTask) {
			t.state = "cancelled";
			running.set(false);
			actions.stop();
			tasks.remove(t);
			Thread wt = workerThread;
			if (wt != null && wt != Thread.currentThread()) {
				wt.interrupt();
			}
		} else {
			taskQueue.remove(t);
			tasks.remove(t);
			t.state = "cancelled";
		}
	}

	public void cancelAll() {
		for (Task t : tasks) {
			if (!"done".equals(t.state) && !"cancelled".equals(t.state)) {
				cancelTask(t.id);
			}
		}
	}

	public void removeFinished() {
		for (Task t : tasks) {
			if ("done".equals(t.state) || "cancelled".equals(t.state)) {
				tasks.remove(t);
			}
		}
	}

	public void clearAllTasks() {
		if (currentTask != null) {
			Task t = currentTask;
			t.state = "cancelled";
			running.set(false);
			actions.stop();
			Thread wt = workerThread;
			if (wt != null && wt != Thread.currentThread()) {
				wt.interrupt();
			}
		}
		taskQueue.clear();
		tasks.clear();
	}

	public void stop() {
		cancelAll();
	}

	private Task findTask(long id) {
		for (Task t : tasks) {
			if (t.id == id) {
				return t;
			}
		}
		return null;
	}

	public List<Task> getTasks() {
		return tasks;
	}

	public Task getCurrentTask() {
		return currentTask;
	}

	public void clear() {
		entries.clear();
		synchronized (historyLock) {
			history.clear();
		}
		com.mcai.config.SessionManager.save(session, new ArrayList<>(history));
	}

	public List<ChatEntry> getEntries() {
		return entries;
	}

	public boolean isBusy() {
		return currentTask != null;
	}

	public boolean isEnabled() {
		return ConfigManager.get().enabled;
	}

	public void setEnabled(boolean enabled) {
		McAiConfig cfg = ConfigManager.get();
		cfg.enabled = enabled;
		ConfigManager.save();
		if (!enabled) {
			stop();
		}
	}

	public boolean isTaskMode() {
		return taskMode;
	}

	public void setTaskMode(boolean taskMode) {
		this.taskMode = taskMode;
	}

	public String getActiveSession() {
		return session;
	}

	public void switchSession(String name) {
		if (name == null || name.isBlank() || name.equals(session)) {
			return;
		}
		com.mcai.config.SessionManager.save(session, new ArrayList<>(history));
		session = name;
		synchronized (historyLock) {
			history.clear();
			history.addAll(com.mcai.config.SessionManager.load(session));
		}
		rebuildEntriesFromHistory();
	}

	public void newSession(String name) {
		String s = com.mcai.config.SessionManager.sanitize(name);
		if ("default".equals(s) && com.mcai.config.SessionManager.listSessions().contains(s)) {
			return;
		}
		com.mcai.config.SessionManager.save(session, new ArrayList<>(history));
		session = s;
		synchronized (historyLock) {
			history.clear();
		}
		com.mcai.config.SessionManager.save(session, new ArrayList<>(history));
		rebuildEntriesFromHistory();
	}

	public void deleteSession(String name) {
		if (name == null || "default".equals(name)) {
			return;
		}
		com.mcai.config.SessionManager.delete(name);
		if (name.equals(session)) {
			session = "default";
			synchronized (historyLock) {
				history.clear();
				history.addAll(com.mcai.config.SessionManager.load(session));
			}
			rebuildEntriesFromHistory();
		}
	}

	private void rebuildEntriesFromHistory() {
		entries.clear();
		for (ChatMessage m : history) {
			switch (m.role) {
				case "user":
					if (m.content != null && !m.content.isEmpty()) {
						entries.add(new ChatEntry("user", m.content));
					}
					break;
				case "assistant":
					if (m.content != null && !m.content.isEmpty()) {
						entries.add(new ChatEntry("assistant", m.content));
					}
					break;
				case "tool":
					if (m.content != null) {
						entries.add(new ChatEntry("tool", (m.name == null ? "tool" : m.name) + " -> " + m.content));
					}
					break;
				default:
					break;
			}
		}
	}

	private long durationMs(List<ActionSpec> specs) {
		long ticks = 0;
		for (ActionSpec s : specs) {
			switch (s.name) {
				case "move":
					ticks = Math.max(ticks, Math.min(Math.max(s.intArg("ticks", 10), 1), 100));
					break;
				case "jump":
					ticks = Math.max(ticks, Math.min(Math.max(s.intArg("ticks", 5), 1), 20));
					break;
				case "attack":
					ticks = Math.max(ticks, Math.min(Math.max(s.intArg("ticks", 8), 1), 80));
					break;
				default:
					break;
			}
		}
		return ticks * 50L + 100L;
	}

	private static void sleepQuietly(long ms) {
		if (ms <= 0) {
			return;
		}
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static void trimHistory(List<ChatMessage> list, int max) {
		while (list.size() > Math.max(4, max)) {
			ChatMessage first = list.get(0);
			if ("system".equals(first.role)) {
				break;
			}
			list.remove(0);
		}
	}

	private ActionResult execute(ActionSpec spec) {
		String name = spec.name;
		if (!ConfigManager.get().allowPlayerControl && Tools.isControlTool(name)) {
			return fail("操控玩家已禁用，请在设置中开启「允许操控玩家」");
		}
		try {
			switch (name) {
				case "get_state":
					return ok(GameStateProvider.stateJson());
				case "move": {
					int ticks = Math.min(Math.max(spec.intArg("ticks", 10), 1), 100);
					String dir = spec.str("direction", "forward");
					actions.move(dir, ticks);
					return ok("正在向" + dir + "移动 " + ticks + " tick");
				}
				case "jump": {
					int ticks = Math.min(Math.max(spec.intArg("ticks", 5), 1), 20);
					actions.jump(ticks);
					return ok("跳跃 " + ticks + " tick");
				}
				case "sneak": {
					boolean on = spec.boolArg("on", true);
					actions.sneak(on);
					return ok("潜行=" + on);
				}
				case "sprint": {
					boolean on = spec.boolArg("on", true);
					actions.sprint(on);
					return ok("疾跑=" + on);
				}
				case "stop":
					actions.stop();
					return ok("已停止");
				case "look": {
					float yaw = spec.floatArg("yaw", 0);
					float pitch = spec.floatArg("pitch", 0);
					actions.look(yaw, pitch);
					return ok("已看向 yaw=" + yaw + " pitch=" + pitch);
				}
				case "use_held_item":
					return ok(actions.useItem());
				case "attack": {
					int ticks = Math.min(Math.max(spec.intArg("ticks", 8), 1), 80);
					actions.attack(ticks);
					return ok("正在攻击/挖掘 " + ticks + " tick");
				}
				case "select_hotbar": {
					int slot = Math.min(Math.max(spec.intArg("slot", 0), 0), 8);
					actions.selectHotbar(slot);
					return ok("已选择物品栏第 " + slot + " 格");
				}
				case "drop_held_item":
					return ok(actions.dropHeldItem());
				case "chat": {
					String msg = spec.str("message", "");
					if (msg.isBlank()) {
						return fail("消息为空");
					}
					return ok(actions.sendChat(msg));
				}
				default:
					return fail("未知工具: " + name);
			}
		} catch (Exception e) {
			return fail("执行 " + name + " 出错: " + e);
		}
	}

	private static ActionResult ok(String result) {
		return new ActionResult(result);
	}

	private static ActionResult fail(String msg) {
		return new ActionResult("{\"ok\":false,\"error\":\"" + msg.replace("\"", "'") + "\"}");
	}

	private final LinkedBlockingQueue<ActionBatch> actionQueue = new LinkedBlockingQueue<>();

	private static final class ActionBatch {
		final List<ActionSpec> specs;
		final List<ActionResult> results = new ArrayList<>();
		final CountDownLatch latch = new CountDownLatch(1);

		ActionBatch(List<ActionSpec> specs) {
			this.specs = specs;
		}
	}

	private static final class ActionResult {
		final String result;

		ActionResult(String result) {
			this.result = result;
		}
	}
}
