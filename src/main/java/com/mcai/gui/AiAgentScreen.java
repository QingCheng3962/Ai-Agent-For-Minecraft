package com.mcai.gui;

import com.mcai.agent.ChatEntry;
import com.mcai.agent.McAiAgent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public final class AiAgentScreen extends Screen {
	private static final int COLOR_TITLE = 0xFFDDDDDD;
	private static final int COLOR_USER = 0xFFFFFFFF;
	private static final int COLOR_ASSISTANT = 0xFF7CD6FF;
	private static final int COLOR_TOOL = 0xFF9A9A9A;
	private static final int COLOR_ERROR = 0xFFFF6B6B;
	private static final int COLOR_STATUS = 0xFF8F8F8F;

	private static final int LINE_HEIGHT = 9;
	private static final int LINE_GAP = 3;

	private EditBox input;
	private Button enableButton;
	private Button taskModeButton;
	private int scrollOffset;
	private int lastEntries;
	private boolean stickToBottom = true;

	public AiAgentScreen() {
		super(Component.literal("AI 智能体"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		int w = this.width;
		int h = this.height;

		this.input = new EditBox(this.font, 8, h - 42, Math.max(60, w - 166), 20, Component.literal("消息"));
		this.input.setMaxLength(512);
		this.input.setCanLoseFocus(false);
		this.input.setHint(Component.literal("下达指令，回车发送..."));
		this.addRenderableWidget(this.input);

		this.addRenderableWidget(Button.builder(Component.literal("发送"), b -> sendMessage())
				.bounds(w - 150, h - 42, 52, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("设置"), b -> this.minecraft.setScreen(new ConfigScreen()))
				.bounds(w - 94, h - 42, 86, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("清空"), b -> {
			McAiAgent.get().clear();
			scrollOffset = 0;
			stickToBottom = true;
		}).bounds(8, h - 20, 52, 18).build());
		this.addRenderableWidget(Button.builder(Component.literal("停止"), b -> McAiAgent.get().stop())
				.bounds(64, h - 20, 52, 18).build());
		this.addRenderableWidget(Button.builder(Component.literal("复制"), b -> copyLast())
				.bounds(120, h - 20, 52, 18).build());
		this.enableButton = Button.builder(Component.literal(""), b -> {
			McAiAgent.get().setEnabled(!McAiAgent.get().isEnabled());
		}).bounds(176, h - 20, 64, 18).build();
		this.addRenderableWidget(this.enableButton);
		this.taskModeButton = Button.builder(Component.literal(""), b -> {
			McAiAgent.get().setTaskMode(!McAiAgent.get().isTaskMode());
		}).bounds(244, h - 20, 96, 18).build();
		this.addRenderableWidget(this.taskModeButton);
		this.addRenderableWidget(Button.builder(Component.literal("任务"), b -> this.minecraft.setScreen(new TaskScreen()))
				.bounds(344, h - 20, 52, 18).build());
		this.addRenderableWidget(Button.builder(Component.literal("会话"), b -> this.minecraft.setScreen(new SessionScreen()))
				.bounds(400, h - 20, 52, 18).build());

		this.setInitialFocus(this.input);
	}

	private void copyLast() {
		List<ChatEntry> entries = McAiAgent.get().getEntries();
		StringBuilder sb = new StringBuilder();
		for (int i = entries.size() - 1; i >= 0; i--) {
			ChatEntry e = entries.get(i);
			if ("assistant".equals(e.kind) || "tool".equals(e.kind)) {
				sb.append(e.text);
				break;
			}
		}
		if (sb.length() > 0) {
			com.mcai.agent.ClipboardUtil.copy(sb.toString());
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isConfirmation()) {
			sendMessage();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean isOutside) {
		return super.mouseClicked(event, isOutside);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		stickToBottom = false;
		scrollOffset -= (int) Math.round(verticalAmount * LINE_HEIGHT * 2);
		clampScroll();
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.enableButton != null) {
			this.enableButton.setMessage(Component.literal(McAiAgent.get().isEnabled() ? "禁用" : "启用"));
		}
		if (this.taskModeButton != null) {
			this.taskModeButton.setMessage(Component.literal(
					McAiAgent.get().isTaskMode() ? "任务模式:开" : "任务模式:关"));
		}
		List<ChatEntry> entries = McAiAgent.get().getEntries();
		if (entries.size() != lastEntries) {
			lastEntries = entries.size();
			if (stickToBottom) {
				clampScroll(true);
			}
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(graphics, mouseX, mouseY, partialTick);

		int w = this.width;
		int h = this.height;

		graphics.drawString(this.font, Component.literal("AI 智能体 [会话: " + McAiAgent.get().getActiveSession() + "]"),
				8, 8, COLOR_TITLE);
		String status;
		if (McAiAgent.get().isBusy()) {
			status = "\u2022 思考中...";
		} else if (!McAiAgent.get().isEnabled()) {
			status = "已禁用";
		} else {
			status = "就绪";
		}
		graphics.drawString(this.font, status, w - 8 - this.font.width(status), 8, COLOR_STATUS);

		int top = 24;
		int bottom = h - 68;
		int contentLeft = 8;
		int contentWidth = w - 16;
		if (bottom > top && contentWidth > 40) {
			List<RenderLine> lines = buildLines(contentWidth);
			int totalH = lines.size() * (LINE_HEIGHT + LINE_GAP);
			int viewport = bottom - top;
			if (totalH <= viewport) {
				scrollOffset = 0;
			}
			graphics.enableScissor(contentLeft - 2, top, contentLeft + contentWidth + 2, bottom);
			int y = top - scrollOffset;
			for (RenderLine line : lines) {
				if (y + LINE_HEIGHT >= top && y <= bottom) {
					graphics.drawString(this.font, line.text, contentLeft, y, line.color);
				}
				y += LINE_HEIGHT + LINE_GAP;
			}
			graphics.disableScissor();

			int maxOffset = Math.max(0, totalH - viewport);
			if (maxOffset > 0) {
				int barX = w - 6;
				int barH = Math.max(20, (int) ((double) viewport / (totalH + viewport) * viewport));
				int barY = top + (int) ((double) scrollOffset / maxOffset * (viewport - barH));
				graphics.fill(barX, barY, barX + 2, barY + barH, 0x88FFFFFF);
			}
		}

		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private List<RenderLine> buildLines(int maxWidth) {
		List<RenderLine> lines = new ArrayList<>();
		for (ChatEntry entry : McAiAgent.get().getEntries()) {
			String prefix;
			int color;
			switch (entry.kind) {
				case "user":
					prefix = "你 > ";
					color = COLOR_USER;
					break;
				case "assistant":
					prefix = "AI > ";
					color = COLOR_ASSISTANT;
					break;
				case "tool":
					prefix = "  ";
					color = COLOR_TOOL;
					break;
				case "error":
					prefix = "! ";
					color = COLOR_ERROR;
					break;
				case "event":
					prefix = "\u25c7 ";
					color = 0xFFFFD700;
					break;
				default:
					prefix = "";
					color = COLOR_STATUS;
					break;
			}
			Component full = Component.literal(prefix + entry.text);
			List<FormattedCharSequence> wrapped = this.font.split(full, maxWidth);
			for (FormattedCharSequence seq : wrapped) {
				lines.add(new RenderLine(seq, color));
			}
		}
		return lines;
	}

	private void sendMessage() {
		String text = this.input.getValue();
		if (text != null && !text.isBlank()) {
			McAiAgent.get().sendUserMessage(text.trim());
			this.input.setValue("");
			stickToBottom = true;
		}
	}

	public boolean isInputFocused() {
		return this.input != null && this.input.isFocused();
	}

	private void clampScroll() {
		clampScroll(false);
	}

	private void clampScroll(boolean forceBottom) {
		int viewport = this.height - 68 - 24;
		int contentWidth = this.width - 16;
		int totalH = 0;
		if (contentWidth > 40) {
			totalH = buildLines(contentWidth).size() * (LINE_HEIGHT + LINE_GAP);
		}
		int maxOffset = Math.max(0, totalH - viewport);
		if (forceBottom) {
			scrollOffset = maxOffset;
		} else {
			scrollOffset = Math.max(0, Math.min(scrollOffset, maxOffset));
		}
	}

	private static final class RenderLine {
		final FormattedCharSequence text;
		final int color;

		RenderLine(FormattedCharSequence text, int color) {
			this.text = text;
			this.color = color;
		}
	}
}
