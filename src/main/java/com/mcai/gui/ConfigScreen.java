package com.mcai.gui;

import com.mcai.agent.McAiAgent;
import com.mcai.config.ConfigManager;
import com.mcai.config.McAiConfig;
import com.mcai.llm.OpenAIClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ConfigScreen extends Screen {
	private static final int COLOR_LABEL = 0xFFBBBBBB;
	private static final int ROW_H = 13;

	private final List<EditBox> fields = new ArrayList<>();
	private final ExecutorService modelExecutor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "mcai-model-fetch");
		t.setDaemon(true);
		return t;
	});

	private EditBox baseUrlField;
	private EditBox apiKeyField;
	private EditBox modelField;
	private EditBox temperatureField;
	private EditBox maxTokensField;
	private EditBox maxHistoryField;
	private EditBox systemPromptField;
	private EditBox modelFilter;
	private Button fetchModelsButton;
	private Button shellToggleButton;
	private Button fileToggleButton;
	private Button respondToggleButton;
	private Button chatToggleButton;
	private Button moveToggleButton;
	private Button controlToggleButton;

	private volatile List<String> models;
	private volatile String modelsError;
	private volatile boolean fetchingModels;
	private int modelScroll;
	private int panelTop;

	public ConfigScreen() {
		super(Component.literal("AI 智能体设置"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		McAiConfig cfg = ConfigManager.get();

		int x = 8;
		int labelW = 120;
		int fieldRight = this.width - 8;
		int fieldW = fieldRight - (x + labelW);
		int y = 40;

		this.baseUrlField = makeField(x + labelW, fieldW, y, "API 地址", cfg.baseUrl);
		y += 26;
		this.apiKeyField = makeField(x + labelW, fieldW, y, "API 密钥", cfg.apiKey);
		y += 26;
		this.modelField = makeField(x + labelW, fieldW, y, "模型", cfg.model);
		y += 26;
		this.temperatureField = makeField(x + labelW, fieldW, y, "温度", String.valueOf(cfg.temperature));
		y += 26;
		this.maxTokensField = makeField(x + labelW, fieldW, y, "最大 Token", String.valueOf(cfg.maxTokens));
		y += 26;
		this.maxHistoryField = makeField(x + labelW, fieldW, y, "历史条数", String.valueOf(cfg.maxHistory));
		y += 26;
		this.systemPromptField = makeField(x + labelW, fieldW, y, "系统提示词", cfg.systemPrompt);
		y += 34;

		this.modelFilter = new EditBox(this.font, 0, 0, 200, 16, Component.literal("过滤模型"));
		this.modelFilter.setMaxLength(80);
		this.modelFilter.setCanLoseFocus(true);
		this.modelFilter.setVisible(false);
		this.addRenderableWidget(this.modelFilter);

		int btnRight = fieldRight;
		this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> this.minecraft.setScreen(new AiAgentScreen()))
				.bounds(btnRight - 60, y, 60, 20).build());
		btnRight -= 66;
		this.addRenderableWidget(Button.builder(Component.literal("保存并重载"), b -> save())
				.bounds(btnRight - 110, y, 110, 20).build());
		btnRight -= 116;
		this.fetchModelsButton = Button.builder(Component.literal("获取模型列表"), b -> fetchModels())
				.bounds(btnRight - 100, y, 100, 20).build();
		this.addRenderableWidget(this.fetchModelsButton);
		y += 26;

		int toggleW = Math.max(100, (fieldW - 6) / 2);
		this.shellToggleButton = Button.builder(Component.literal(""), b -> {
			McAiConfig c = ConfigManager.get();
			c.allowShell = !c.allowShell;
			ConfigManager.save();
		}).bounds(x + labelW, y, toggleW, 20).build();
		this.addRenderableWidget(this.shellToggleButton);
		this.fileToggleButton = Button.builder(Component.literal(""), b -> {
			McAiConfig c = ConfigManager.get();
			c.allowFileWrite = !c.allowFileWrite;
			ConfigManager.save();
		}).bounds(x + labelW + toggleW + 6, y, toggleW, 20).build();
		this.addRenderableWidget(this.fileToggleButton);
		y += 26;
		this.respondToggleButton = Button.builder(Component.literal(""), b -> {
			McAiConfig c = ConfigManager.get();
			c.autoRespondEvents = !c.autoRespondEvents;
			ConfigManager.save();
		}).bounds(x + labelW, y, toggleW, 20).build();
		this.addRenderableWidget(this.respondToggleButton);
		this.chatToggleButton = Button.builder(Component.literal(""), b -> {
			McAiConfig c = ConfigManager.get();
			c.watchChat = !c.watchChat;
			ConfigManager.save();
		}).bounds(x + labelW + toggleW + 6, y, toggleW, 20).build();
		this.addRenderableWidget(this.chatToggleButton);
		y += 26;
		this.moveToggleButton = Button.builder(Component.literal(""), b -> {
			McAiConfig c = ConfigManager.get();
			c.windowMove = !c.windowMove;
			ConfigManager.save();
		}).bounds(x + labelW, y, toggleW, 20).build();
		this.addRenderableWidget(this.moveToggleButton);
		y += 26;
		this.controlToggleButton = Button.builder(Component.literal(""), b -> {
			McAiConfig c = ConfigManager.get();
			c.allowPlayerControl = !c.allowPlayerControl;
			ConfigManager.save();
		}).bounds(x + labelW, y, toggleW, 20).build();
		this.addRenderableWidget(this.controlToggleButton);
		this.panelTop = y + 22;

		this.setInitialFocus(this.modelField);
	}

	private EditBox makeField(int x, int width, int y, String label, String value) {
		EditBox box = new EditBox(this.font, x, y, Math.max(60, width), 20, Component.literal(label));
		box.setMaxLength(2000);
		box.setValue(value == null ? "" : value);
		box.setCanLoseFocus(true);
		this.addRenderableWidget(box);
		fields.add(box);
		return box;
	}

	private void fetchModels() {
		if (fetchingModels) {
			return;
		}
		fetchingModels = true;
		models = null;
		modelsError = null;
		modelScroll = 0;

		McAiConfig t = new McAiConfig();
		t.baseUrl = baseUrlField.getValue().trim();
		t.apiKey = apiKeyField.getValue();
		t.model = modelField.getValue();

		modelExecutor.submit(() -> {
			try {
				List<String> list = new OpenAIClient(t).listModels();
				models = list;
				modelsError = null;
			} catch (Exception e) {
				modelsError = "获取失败: " + e.getMessage();
				models = null;
			} finally {
				fetchingModels = false;
			}
		});
	}

	private void save() {
		McAiConfig cfg = ConfigManager.get();
		cfg.baseUrl = baseUrlField.getValue().trim();
		cfg.apiKey = apiKeyField.getValue();
		cfg.model = modelField.getValue();
		cfg.temperature = parseDouble(temperatureField.getValue(), cfg.temperature);
		cfg.maxTokens = parseInt(maxTokensField.getValue(), cfg.maxTokens);
		cfg.maxHistory = parseInt(maxHistoryField.getValue(), cfg.maxHistory);
		cfg.systemPrompt = systemPromptField.getValue();
		ConfigManager.save();
		McAiAgent.get().reloadClient();
	}

	@Override
	public void tick() {
		super.tick();
		if (this.fetchModelsButton != null) {
			this.fetchModelsButton.setMessage(Component.literal(
					fetchingModels ? "获取中..." : "获取模型列表"));
		}
		if (this.shellToggleButton != null) {
			this.shellToggleButton.setMessage(Component.literal(
					ConfigManager.get().allowShell ? "允许命令:开" : "允许命令:关"));
		}
		if (this.fileToggleButton != null) {
			this.fileToggleButton.setMessage(Component.literal(
					ConfigManager.get().allowFileWrite ? "允许文件:开" : "允许文件:关"));
		}
		if (this.respondToggleButton != null) {
			this.respondToggleButton.setMessage(Component.literal(
					ConfigManager.get().autoRespondEvents ? "自动响应事件:开" : "自动响应事件:关"));
		}
		if (this.chatToggleButton != null) {
			this.chatToggleButton.setMessage(Component.literal(
					ConfigManager.get().watchChat ? "监听聊天:开" : "监听聊天:关"));
		}
		if (this.moveToggleButton != null) {
			this.moveToggleButton.setMessage(Component.literal(
					ConfigManager.get().windowMove ? "窗口内移动:开" : "窗口内移动:关"));
		}
		if (this.controlToggleButton != null) {
			this.controlToggleButton.setMessage(Component.literal(
					ConfigManager.get().allowPlayerControl ? "允许操控玩家:开" : "允许操控玩家:关"));
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.drawString(this.font, Component.literal("API 与智能体设置"),
				8, 8, COLOR_LABEL);
		graphics.drawString(this.font, Component.literal("支持任意 OpenAI 兼容端点：OpenAI、DeepSeek、Ollama、vLLM、硅基流动等"),
				8, 22, 0xFF8F8F8F);
		super.render(graphics, mouseX, mouseY, partialTick);
		renderModelPanel(graphics);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		int y = 42;
		for (EditBox box : fields) {
			graphics.drawString(this.font, box.getMessage().getString() + ":",
					8, y + 5, COLOR_LABEL);
			y += 26;
		}
		super.renderTransparentBackground(graphics);
	}

	private void renderModelPanel(GuiGraphics graphics) {
		if (models == null && modelsError == null) {
			modelFilter.setVisible(false);
			return;
		}

		int x = 8;
		int w = this.width - 16;
		int top = this.panelTop;
		int bottom = this.height - 10;
		int viewport = bottom - top - 20;
		if (bottom <= top + 30) {
			return;
		}

		graphics.fill(x - 2, top - 2, x + w + 2, bottom, 0xE0202030);
		graphics.drawString(this.font, Component.literal(models != null
				? ("选择模型（共 " + models.size() + " 个，点击选择）：")
				: modelsError), x + 2, top + 1, 0xFF7CD6FF);

		modelFilter.setX(x);
		modelFilter.setY(top + 10);
		modelFilter.setWidth(w);
		modelFilter.setVisible(true);

		if (models == null) {
			return;
		}
		String filter = modelFilter.getValue().toLowerCase().trim();
		List<String> shown = new ArrayList<>();
		for (String m : models) {
			if (filter.isEmpty() || m.toLowerCase().contains(filter)) {
				shown.add(m);
			}
		}

		int listTop = top + 30;
		int listBottom = bottom - 2;
		int maxOffset = Math.max(0, shown.size() * ROW_H - (listBottom - listTop));
		modelScroll = Math.max(0, Math.min(modelScroll, maxOffset));

		graphics.enableScissor(x, listTop, x + w, listBottom);
		int yy = listTop - modelScroll;
		for (int i = 0; i < shown.size(); i++) {
			if (yy + ROW_H >= listTop && yy <= listBottom) {
				String id = shown.get(i);
				boolean current = id.equals(modelField.getValue());
				graphics.drawString(this.font, Component.literal(id),
						x + 3, yy, current ? 0xFF8BFFA0 : 0xFFFFFFFF);
			}
			yy += ROW_H;
		}
		graphics.disableScissor();

		int barX = x + w - 3;
		if (maxOffset > 0) {
			int barH = Math.max(18, (int) ((double) viewport / (shown.size() * ROW_H + viewport) * (listBottom - listTop)));
			int barY = listTop + (int) ((double) modelScroll / maxOffset * (listBottom - listTop - barH));
			graphics.fill(barX, barY, barX + 2, barY + barH, 0xAAFFFFFF);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean isOutside) {
		if (models != null) {
			int x = 8;
			int w = this.width - 16;
			int listTop = this.panelTop + 30;
			int listBottom = this.height - 10 - 2;
			if (event.x() >= x && event.x() <= x + w
					&& event.y() >= listTop && event.y() < listBottom) {
				int index = (int) event.y() - listTop + modelScroll;
				int row = index / ROW_H;
				String filter = modelFilter.getValue().toLowerCase().trim();
				List<String> shown = new ArrayList<>();
				for (String m : models) {
					if (filter.isEmpty() || m.toLowerCase().contains(filter)) {
						shown.add(m);
					}
				}
				if (row >= 0 && row < shown.size()) {
					modelField.setValue(shown.get(row));
					modelField.setFocused(true);
					return true;
				}
				return true;
			}
		}
		return super.mouseClicked(event, isOutside);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (models != null && modelsError == null) {
			modelScroll -= (int) Math.round(verticalAmount * 30);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public void onClose() {
		super.onClose();
	}

	private static double parseDouble(String s, double def) {
		try {
			return Double.parseDouble(s.trim());
		} catch (Exception e) {
			return def;
		}
	}

	private static int parseInt(String s, int def) {
		try {
			return Integer.parseInt(s.trim());
		} catch (Exception e) {
			return def;
		}
	}
}
