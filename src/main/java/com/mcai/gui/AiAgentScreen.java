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
	private static final int LINE_HEIGHT = 9;
	private static final int LINE_GAP = 2;
	private static final int BUBBLE_PAD_X = 8;
	private static final int BUBBLE_PAD_Y = 5;
	private static final int ENTRY_GAP = 4;

	private EditBox input;
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
		this.input.setCanLoseFocus(true);
		this.input.setHint(Component.literal("点击输入框输入，回车发送（点击空白处即可移动）"));
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
		this.taskModeButton = Button.builder(Component.literal(""), b -> {
			McAiAgent.get().setTaskMode(!McAiAgent.get().isTaskMode());
		}).bounds(120, h - 20, 96, 18).build();
		this.addRenderableWidget(this.taskModeButton);
		this.addRenderableWidget(Button.builder(Component.literal("任务"), b -> this.minecraft.setScreen(new TaskScreen()))
				.bounds(220, h - 20, 52, 18).build());
		this.addRenderableWidget(Button.builder(Component.literal("会话"), b -> this.minecraft.setScreen(new SessionScreen()))
				.bounds(276, h - 20, 52, 18).build());
		this.addRenderableWidget(Button.builder(Component.literal("AI Player"), b -> this.minecraft.setScreen(new AiPlayerScreen()))
				.bounds(332, h - 20, 52, 18).build());

		this.input.setFocused(false);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isConfirmation() && this.getFocused() instanceof EditBox) {
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
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		this.renderTransparentBackground(g);
		int w = this.width;
		int h = this.height;

		Ui.panel(g, 4, 4, w - 4, 26);
		g.drawString(this.font, Component.literal("\u25c9 AI 智能体"), 10, 9, Ui.TEXT);
		String session = "会话: " + McAiAgent.get().getActiveSession();
		g.drawString(this.font, Component.literal(session), 150, 9, Ui.TEXT_DIM);

		String status;
		int statusColor;
		if (McAiAgent.get().isBusy()) {
			status = "\u2022 思考中";
			statusColor = Ui.GOLD;
		} else if (!McAiAgent.get().isEnabled()) {
			status = "已禁用";
			statusColor = 0xFF8A8A8A;
		} else {
			status = "就绪";
			statusColor = Ui.GREEN;
		}
		int sw = this.font.width(status);
		g.drawString(this.font, status, w - 14 - sw, 9, statusColor);

		int top = 32;
		int bottom = h - 66;
		int contentLeft = 8;
		int contentWidth = w - 22;
		if (bottom > top + 4 && contentWidth > 60) {
			Ui.panel(g, 5, top - 1, w - 5, bottom + 1);
			List<ChatRender> renders = buildRenders(contentWidth - 12);
			int totalH = 0;
			for (ChatRender r : renders) {
				totalH += r.height + ENTRY_GAP;
			}
			if (renders.isEmpty()) {
				g.drawString(this.font, Component.literal("在下方输入框发送消息，或让 AI 智能体开始工作。"),
						contentLeft + 4, top + 8, Ui.TEXT_DIM);
			}
			int viewport = bottom - top;
			if (totalH <= viewport) {
				scrollOffset = 0;
			}
			g.enableScissor(contentLeft, top, contentLeft + contentWidth, bottom);
			int y = top - scrollOffset;
			for (ChatRender r : renders) {
				if (y + r.height >= top && y <= bottom) {
					drawRender(g, r, y, contentLeft, contentWidth - 8);
				}
				y += r.height + ENTRY_GAP;
			}
			g.disableScissor();

			int maxOffset = Math.max(0, totalH - viewport);
			if (maxOffset > 0) {
				int barX = w - 12;
				int barH = Math.max(24, viewport * viewport / Math.max(totalH, viewport));
				int barY = top + ((scrollOffset * (viewport - barH)) / maxOffset);
				g.fill(barX, top, barX + 4, bottom, 0x33203040);
				g.fill(barX, barY, barX + 4, barY + barH, 0xAA6FB8D8);
			}
		}

		super.render(g, mouseX, mouseY, partialTick);
	}

	private static final class ChatRender {
		final String kind;
		final List<FormattedCharSequence> lines;
		final int textColor;
		final int width;
		final int height;

		ChatRender(String kind, List<FormattedCharSequence> lines, int textColor, int width, int height) {
			this.kind = kind;
			this.lines = lines;
			this.textColor = textColor;
			this.width = width;
			this.height = height;
		}
	}

	private List<ChatRender> buildRenders(int maxWidth) {
		List<ChatRender> out = new ArrayList<>();
		for (ChatEntry entry : McAiAgent.get().getEntries()) {
			String prefix;
			int textColor;
			int fill;
			String kind = entry.kind;
			switch (kind) {
				case "user":
					prefix = "";
					textColor = 0xFFFFFFFF;
					fill = Ui.USER_BUBBLE;
					break;
				case "assistant":
					prefix = "";
					textColor = 0xFFCFE8FF;
					fill = Ui.AI_BUBBLE;
					break;
				case "tool":
					prefix = "  ";
					textColor = Ui.TEXT_DIM;
					fill = Ui.TOOL_BUBBLE;
					break;
				case "error":
					prefix = "! ";
					textColor = Ui.ERROR;
					fill = Ui.ERROR_BUBBLE;
					break;
				case "event":
					prefix = "\u25c7 ";
					textColor = Ui.GOLD;
					fill = Ui.EVENT_BUBBLE;
					break;
				default:
					prefix = "";
					textColor = Ui.TEXT_DIM;
					fill = Ui.TOOL_BUBBLE;
					break;
			}
			Component full = Component.literal(prefix + entry.text);
			List<FormattedCharSequence> wrapped = this.font.split(full, maxWidth);
			int maxLine = 0;
			for (FormattedCharSequence seq : wrapped) {
				maxLine = Math.max(maxLine, this.font.width(seq));
			}
			int h = wrapped.size() * (LINE_HEIGHT + LINE_GAP) + BUBBLE_PAD_Y * 2;
			int w = Math.max(maxLine, 20) + BUBBLE_PAD_X * 2;
			out.add(new ChatRender(kind, wrapped, textColor, w, h));
		}
		return out;
	}

	private void drawRender(GuiGraphics g, ChatRender r, int y, int contentLeft, int areaWidth) {
		int w = Math.min(r.width, areaWidth);
		switch (r.kind) {
			case "user": {
				int x = contentLeft + areaWidth - w;
				Ui.bubble(g, x, y, w, r.height, Ui.USER_BUBBLE);
				drawLines(g, r.lines, x + BUBBLE_PAD_X, y + BUBBLE_PAD_Y, r.textColor);
				break;
			}
			case "assistant": {
				Ui.bubble(g, contentLeft, y, w, r.height, Ui.AI_BUBBLE);
				drawLines(g, r.lines, contentLeft + BUBBLE_PAD_X, y + BUBBLE_PAD_Y, r.textColor);
				break;
			}
			case "error": {
				Ui.bubble(g, contentLeft, y, w, r.height, Ui.ERROR_BUBBLE);
				drawLines(g, r.lines, contentLeft + BUBBLE_PAD_X, y + BUBBLE_PAD_Y, r.textColor);
				break;
			}
			case "event": {
				Ui.accentBar(g, contentLeft, y, r.height, Ui.GOLD);
				drawLines(g, r.lines, contentLeft + 6, y + 3, r.textColor);
				break;
			}
			default: {
				Ui.accentBar(g, contentLeft, y, r.height, Ui.ACCENT_DIM);
				drawLines(g, r.lines, contentLeft + 6, y + 3, r.textColor);
				break;
			}
		}
	}

	private void drawLines(GuiGraphics g, List<FormattedCharSequence> lines, int x, int y, int color) {
		int yy = y;
		for (FormattedCharSequence seq : lines) {
			g.drawString(this.font, seq, x, yy, color);
			yy += LINE_HEIGHT + LINE_GAP;
		}
	}

	private void sendMessage() {
		String text = this.input.getValue();
		if (text != null && !text.isBlank()) {
			McAiAgent.get().sendUserMessage(text.trim());
			this.input.setValue("");
			this.input.setFocused(false);
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
		int viewport = this.height - 66 - 32;
		int contentWidth = this.width - 22 - 12;
		int totalH = 0;
		if (contentWidth > 60) {
			for (ChatRender r : buildRenders(contentWidth)) {
				totalH += r.height + ENTRY_GAP;
			}
		}
		int maxOffset = Math.max(0, totalH - viewport);
		if (forceBottom) {
			scrollOffset = maxOffset;
		} else {
			scrollOffset = Math.max(0, Math.min(scrollOffset, maxOffset));
		}
	}
}
