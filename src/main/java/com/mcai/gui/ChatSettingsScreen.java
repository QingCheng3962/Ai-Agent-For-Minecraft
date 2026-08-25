package com.mcai.gui;

import com.mcai.chat.ChatConfig;
import com.mcai.chat.ChatResponder;
import com.mcai.config.ConfigManager;
import com.mcai.config.McAiConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public final class ChatSettingsScreen extends Screen {
	private static final int COLOR_LABEL = 0xFF9AA5B1;

	private final List<EditBox> fields = new ArrayList<>();
	private EditBox systemPromptField;
	private EditBox baseUrlField;
	private EditBox apiKeyField;
	private EditBox modelField;
	private EditBox triggerRegexField;
	private EditBox scheduleField;
	private EditBox cooldownField;
	private EditBox imageModelField;
	private Button templateButton;
	private Ui.ToggleButton autoReplyToggle;
	private Ui.ToggleButton triggerToggle;
	private Ui.ToggleButton scheduleToggle;
	private Ui.ToggleButton useLogToggle;
	private Ui.ToggleButton restrictionToggle;
	private Ui.ToggleButton imageToggle;
	private int logScroll;

	public ChatSettingsScreen() {
		super(Component.literal("AI Player 设置"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		ChatResponder.getInstance().refreshFromDisk();
		ChatConfig c = ChatResponder.getInstance().getConfig();
		int x = 8;
		int labelW = 100;
		int fieldW = this.width - 16 - labelW;
		int y = 40;

		this.systemPromptField = makeField(x + labelW, fieldW, y, "系统提示词", c.systemPrompt);
		this.systemPromptField.setMaxLength(4000);
		y += 26;
		this.baseUrlField = makeField(x + labelW, fieldW, y, "API 地址", c.baseUrl);
		y += 26;
		this.apiKeyField = makeField(x + labelW, fieldW, y, "API 密钥", c.apiKey);
		y += 26;
		this.modelField = makeField(x + labelW, fieldW, y, "模型", c.model);
		y += 26;
		this.triggerRegexField = makeField(x + labelW, fieldW, y, "触发正则", c.triggerRegex);
		y += 26;
		this.scheduleField = makeField(x + labelW, fieldW, y, "定时秒", String.valueOf(c.scheduleIntervalSeconds));
		y += 26;
		this.cooldownField = makeField(x + labelW, fieldW, y, "回复冷却秒", String.valueOf(c.triggerCooldownSeconds));
		y += 26;
		this.imageModelField = makeField(x + labelW, fieldW, y, "文生图模型", c.imageModel);
		y += 34;

		this.templateButton = Button.builder(Component.literal(""), b -> cycleTemplate())
				.bounds(x + labelW, y, fieldW, 20).build();
		this.addRenderableWidget(this.templateButton);
		y += 26;

		int toggleW = Math.max(110, (fieldW - 6) / 2);
		int right = x + labelW + toggleW + 6;
		this.autoReplyToggle = new Ui.ToggleButton(x + labelW, y, toggleW, 20, "自动回复: 开", "自动回复: 关",
				() -> ChatResponder.getInstance().getConfig().autoReplyEnabled, b -> {
					ChatConfig cfg = ChatResponder.getInstance().getConfig();
					cfg.autoReplyEnabled = !cfg.autoReplyEnabled;
					cfg.save();
				});
		this.addRenderableWidget(this.autoReplyToggle);
		this.triggerToggle = new Ui.ToggleButton(right, y, toggleW, 20, "触发回复: 开", "触发回复: 关",
				() -> ChatResponder.getInstance().getConfig().triggerEnabled, b -> {
					ChatConfig cfg = ChatResponder.getInstance().getConfig();
					cfg.triggerEnabled = !cfg.triggerEnabled;
					cfg.save();
				});
		this.addRenderableWidget(this.triggerToggle);
		y += 26;
		this.scheduleToggle = new Ui.ToggleButton(x + labelW, y, toggleW, 20, "定时回复: 开", "定时回复: 关",
				() -> ChatResponder.getInstance().getConfig().scheduleEnabled, b -> {
					ChatConfig cfg = ChatResponder.getInstance().getConfig();
					cfg.scheduleEnabled = !cfg.scheduleEnabled;
					cfg.save();
				});
		this.addRenderableWidget(this.scheduleToggle);
		this.useLogToggle = new Ui.ToggleButton(right, y, toggleW, 20, "读取日志文件: 开", "读取日志文件: 关",
				() -> ChatResponder.getInstance().getConfig().useLogFile, b -> {
					ChatConfig cfg = ChatResponder.getInstance().getConfig();
					cfg.useLogFile = !cfg.useLogFile;
					cfg.save();
				});
		this.addRenderableWidget(this.useLogToggle);
		y += 26;
		this.restrictionToggle = new Ui.ToggleButton(x + labelW, y, toggleW, 20, "消息过滤: 开", "消息过滤: 关",
				() -> ChatResponder.getInstance().getConfig().restrictionEnabled, b -> {
					ChatConfig cfg = ChatResponder.getInstance().getConfig();
					cfg.restrictionEnabled = !cfg.restrictionEnabled;
					cfg.save();
				});
		this.addRenderableWidget(this.restrictionToggle);
		this.imageToggle = new Ui.ToggleButton(right, y, toggleW, 20, "文生图: 开", "文生图: 关",
				() -> ChatResponder.getInstance().getConfig().imageGenerationEnabled, b -> {
					ChatConfig cfg = ChatResponder.getInstance().getConfig();
					cfg.imageGenerationEnabled = !cfg.imageGenerationEnabled;
					cfg.save();
				});
		this.addRenderableWidget(this.imageToggle);
		y += 30;

		int btnGap = 6;
		int btnW = (fieldW - btnGap * 2) / 3;
		this.addRenderableWidget(Button.builder(Component.literal("使用主界面 API"), b -> importMainApi())
				.bounds(x + labelW, y, btnW, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("保存并重载"), b -> save())
				.bounds(x + labelW + btnW + btnGap, y, btnW, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("返回"),
				b -> this.minecraft.setScreen(new AiPlayerScreen()))
				.bounds(x + labelW + (btnW + btnGap) * 2, y, btnW, 20).build());
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

	private void save() {
		ChatConfig c = ChatResponder.getInstance().getConfig();
		String oldPrompt = c.systemPrompt;
		c.systemPrompt = systemPromptField.getValue();
		c.baseUrl = baseUrlField.getValue().trim();
		c.apiKey = apiKeyField.getValue();
		c.model = modelField.getValue();
		c.triggerRegex = triggerRegexField.getValue();
		c.scheduleIntervalSeconds = clampInt(scheduleField.getValue(), c.scheduleIntervalSeconds, 5, 3600);
		c.triggerCooldownSeconds = clampInt(cooldownField.getValue(), c.triggerCooldownSeconds, 1, 3600);
		c.imageModel = imageModelField.getValue();
		c.save();
		boolean promptChanged = !java.util.Objects.equals(oldPrompt, c.systemPrompt);
		if (promptChanged) {
			ChatResponder.getInstance().reloadConfig();
		} else {
			ChatResponder.getInstance().refreshFromDisk();
		}
	}

	private void importMainApi() {
		McAiConfig m = ConfigManager.get();
		baseUrlField.setValue(m.baseUrl);
		apiKeyField.setValue(m.apiKey);
		modelField.setValue(m.model);
		save();
	}

	@Override
	public void onClose() {
		save();
		super.onClose();
	}

	@Override
	public void removed() {
		save();
		super.removed();
	}

	private static int clampInt(String s, int def, int min, int max) {
		try {
			return Math.max(min, Math.min(max, Integer.parseInt(s.trim())));
		} catch (Exception e) {
			return def;
		}
	}

	private void cycleTemplate() {
		ChatConfig c = ChatResponder.getInstance().getConfig();
		String next = "聊天助手".equals(c.templateName) ? "聊天搭子" : "聊天助手";
		c.templateName = next;
		String prompt = "聊天搭子".equals(next) ? ChatConfig.TEMPLATE_BUDDY : ChatConfig.TEMPLATE_ASSISTANT;
		c.systemPrompt = prompt;
		systemPromptField.setValue(prompt);
		c.save();
		ChatResponder.getInstance().reloadConfig();
	}

	@Override
	public void tick() {
		super.tick();
		if (this.templateButton != null) {
			String name = ChatResponder.getInstance().getConfig().templateName;
			this.templateButton.setMessage(Component.literal("提示词模板: " + name + "（点击切换）"));
		}
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		int y = 40;
		for (EditBox box : fields) {
			g.drawString(this.font, Component.literal(box.getMessage().getString() + ":"), 8, y + 5, COLOR_LABEL);
			y += 26;
		}
		super.renderTransparentBackground(g);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(g, mouseX, mouseY, partialTick);
		g.drawString(this.font, Component.literal("AI Player 设置"), 8, 8, 0xFFDDDDDD);
		g.drawString(this.font, Component.literal("自动回复会读取 logs/latest.log 捕获玩家消息并逐条回复"),
				8, 22, 0xFF707070);
		super.render(g, mouseX, mouseY, partialTick);
		renderLogBox(g);
	}

	private void renderLogBox(GuiGraphics g) {
		int w = this.width;
		int h = this.height;
		int top = h - 96;
		int bottom = h - 6;
		if (top >= bottom) {
			return;
		}
		Ui.panel(g, 4, top, w - 4, bottom);
		g.drawString(this.font, Component.literal("\u25c9 运行日志"), 10, top + 3, Ui.TEXT_DIM);

		List<String> log = ChatResponder.getInstance().getActivityLog();
		int lx = 8;
		int lw = w - 16;
		int listTop = top + 14;
		int viewport = bottom - listTop - 4;
		int lineH = 10;
		int maxScroll = Math.max(0, log.size() * lineH - viewport);
		logScroll = Math.max(0, Math.min(logScroll, maxScroll));

		g.enableScissor(6, listTop, w - 6, bottom - 2);
		int yy = listTop - logScroll;
		for (int i = 0; i < log.size(); i++) {
			if (yy + lineH >= top && yy <= bottom) {
				String line = log.get(i);
				int color = line.startsWith("AI >") ? 0xFF9AD8FF
						: (line.contains(" > ") ? 0xFFE9ECF1 : Ui.TEXT_DIM);
				List<FormattedCharSequence> parts = this.font.split(Component.literal(line), lw);
				if (!parts.isEmpty()) {
					g.drawString(this.font, parts.get(0), lx, yy, color);
				}
			}
			yy += lineH;
		}
		g.disableScissor();

		if (maxScroll > 0) {
			int barX = w - 12;
			int barH = Math.max(14, viewport * viewport / Math.max(log.size() * lineH, viewport));
			int barY = listTop + ((logScroll * (viewport - barH)) / maxScroll);
			g.fill(barX, listTop, barX + 4, bottom, 0x33203040);
			g.fill(barX, barY, barX + 4, barY + barH, 0xAA6FB8D8);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int top = this.height - 96;
		if (mouseY >= top) {
			logScroll -= (int) Math.round(verticalAmount * 30);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}
}
