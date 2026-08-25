package com.mcai.gui;

import com.mcai.chat.ChatConfig;
import com.mcai.chat.ChatResponder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AiPlayerScreen extends Screen {
	private static final int COLOR_TITLE = Ui.TEXT;
	private static final int COLOR_STATUS = Ui.TEXT_DIM;

	private Ui.ToggleButton masterToggle;

	public AiPlayerScreen() {
		super(Component.literal("AI Player"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		ChatResponder.getInstance().refreshFromDisk();
		int w = this.width;
		int h = this.height;
		int cx = w / 2;

		this.masterToggle = new Ui.ToggleButton(cx - 90, 48, 180, 30, "AI Player 开", "AI Player 关",
				() -> ChatResponder.getInstance().getConfig().enabled, b -> {
					ChatConfig c = ChatResponder.getInstance().getConfig();
					c.enabled = !c.enabled;
					c.save();
				});
		this.addRenderableWidget(this.masterToggle);

		this.addRenderableWidget(Button.builder(Component.literal("AI Player 设置"),
				b -> this.minecraft.setScreen(new ChatSettingsScreen()))
				.bounds(cx - 90, 82, 180, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("重开上下文"),
				b -> ChatResponder.getInstance().reloadConfig())
				.bounds(cx - 90, 106, 180, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("黑名单"),
				b -> this.minecraft.setScreen(new BlacklistScreen()))
				.bounds(cx - 90, 130, 180, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("返回"),
				b -> this.minecraft.setScreen(new AiAgentScreen()))
				.bounds(cx - 90, h - 30, 180, 20).build());
	}

	@Override
	public void tick() {
		super.tick();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		this.renderTransparentBackground(g);
		int w = this.width;
		int h = this.height;
		int cx = w / 2;

		Ui.panel(g, 4, 4, w - 4, 26);
		g.drawString(this.font, Component.literal("AI Player（聊天 AI）"), 10, 9, COLOR_TITLE);
		ChatConfig c = ChatResponder.getInstance().getConfig();
		String status = c.enabled ? "已启用" : "已禁用";
		int sw = this.font.width(status);
		Ui.dot(g, w - 20 - sw, 12, c.enabled ? Ui.GREEN : 0xFF707070);
		g.drawString(this.font, status, w - 14 - sw, 9, c.enabled ? Ui.GREEN : COLOR_STATUS);

		String label = "主开关";
		int lw = this.font.width(label);
		g.drawString(this.font, Component.literal(label), cx - lw / 2, 44, COLOR_STATUS);

		Ui.panel(g, cx - 180, 156, cx + 180, 260);
		int yy = 164;
		String[] lines = {
				"提供方: " + c.provider + "    模型: " + c.model,
				"自动回复: " + (c.autoReplyEnabled ? "开" : "关") + "（" + (c.useLogFile ? "读取日志" : "聊天事件") + "）",
				"触发回复: " + (c.triggerEnabled ? "开" : "关") + "（" + c.triggerCooldownSeconds + "s 冷却）",
				"定时回复: " + (c.scheduleEnabled ? "开" : "关") + "（每 " + c.scheduleIntervalSeconds + "s）",
				"消息过滤: " + (c.restrictionEnabled ? "开" : "关"),
				"文生图: " + (c.imageGenerationEnabled ? "开" : "关") + "（" + c.imageModel + "）",
				"上下文: " + (c.contextEnabled ? "开(" + c.contextLength + ")" : "关")
		};
		for (String line : lines) {
			g.drawString(this.font, Component.literal(line), cx - 172, yy, COLOR_STATUS);
			yy += 12;
		}

		super.render(g, mouseX, mouseY, partialTick);
	}
}
