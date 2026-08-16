package com.mcai.gui;

import com.mcai.agent.McAiAgent;
import com.mcai.agent.McAiAgent.Task;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class TaskScreen extends Screen {
	private static final int COLOR_TITLE = 0xFFDDDDDD;
	private static final int COLOR_PENDING = 0xFFB0B0B0;
	private static final int COLOR_RUNNING = 0xFF8BFFA0;
	private static final int COLOR_PAUSED = 0xFFFFD700;
	private static final int COLOR_DONE = 0xFF707070;
	private static final int COLOR_CANCELLED = 0xFFFF6B6B;

	private static final int ROW_H = 18;

	private int taskScroll;

	public TaskScreen() {
		super(Component.literal("任务列表"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		int h = this.height;
		this.addRenderableWidget(Button.builder(Component.literal("清空已完成"), b -> McAiAgent.get().removeFinished())
				.bounds(8, h - 24, 92, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("全部取消"), b -> McAiAgent.get().cancelAll())
				.bounds(104, h - 24, 92, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("清除所有"), b -> McAiAgent.get().clearAllTasks())
				.bounds(200, h - 24, 92, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> this.minecraft.setScreen(new AiAgentScreen()))
				.bounds(this.width - 70, h - 24, 62, 20).build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(graphics, mouseX, mouseY, partialTick);

		graphics.drawString(this.font, Component.literal("任务列表"), 8, 8, COLOR_TITLE);

		int top = 24;
		int bottom = this.height - 32;
		List<Task> tasks = McAiAgent.get().getTasks();
		if (tasks.isEmpty()) {
			graphics.drawString(this.font, Component.literal("暂无任务。回到对话窗口下达指令即可创建任务。"),
					8, top + 4, 0xFF8F8F8F);
			super.render(graphics, mouseX, mouseY, partialTick);
			return;
		}

		int x = 8;
		int w = this.width - 16;
		int viewport = bottom - top;
		int totalH = tasks.size() * ROW_H;
		int maxOffset = Math.max(0, totalH - viewport);
		taskScroll = Math.max(0, Math.min(taskScroll, maxOffset));

		graphics.enableScissor(x, top, x + w, bottom);
		int yy = top - taskScroll;
		for (int i = 0; i < tasks.size(); i++) {
			Task t = tasks.get(i);
			if (yy + ROW_H >= top && yy <= bottom) {
				String label = "#" + t.id + " [" + stateLabel(t.state) + "] " + truncate(t.text, 58);
				graphics.drawString(this.font, Component.literal(label), x + 2, yy + 4, stateColor(t.state));
				drawMiniButton(graphics, x + w - 104, yy + 2, 44, "暂停/继续", "paused".equals(t.state));
				drawMiniButton(graphics, x + w - 56, yy + 2, 44, "取消", true);
			}
			yy += ROW_H;
		}
		graphics.disableScissor();

		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void drawMiniButton(GuiGraphics graphics, int x, int y, int w, String label, boolean active) {
		graphics.fill(x, y, x + w, y + 14, active ? 0xFF3A3F52 : 0xFF2A2E3C);
		int textW = this.font.width(label);
		graphics.drawString(this.font, label, x + (w - textW) / 2, y + 3, 0xFFDDDDDD);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean isOutside) {
		int top = 24;
		int bottom = this.height - 32;
		List<Task> tasks = McAiAgent.get().getTasks();
		if (tasks.isEmpty()) {
			return super.mouseClicked(event, isOutside);
		}
		int x = 8;
		int w = this.width - 16;
		if (event.x() < x || event.x() > x + w || event.y() < top || event.y() >= bottom) {
			return super.mouseClicked(event, isOutside);
		}
		int row = (int) (event.y() - top + taskScroll) / ROW_H;
		if (row < 0 || row >= tasks.size()) {
			return true;
		}
		Task t = tasks.get(row);
		double px = event.x();
		if (px >= x + w - 104 && px < x + w - 56) {
			if ("paused".equals(t.state)) {
				McAiAgent.get().resumeTask(t.id);
			} else {
				McAiAgent.get().pauseTask(t.id);
			}
		} else if (px >= x + w - 56 && px <= x + w) {
			McAiAgent.get().cancelTask(t.id);
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		taskScroll -= (int) Math.round(verticalAmount * 30);
		List<Task> tasks = McAiAgent.get().getTasks();
		int viewport = this.height - 32 - 24;
		int maxOffset = Math.max(0, tasks.size() * ROW_H - viewport);
		taskScroll = Math.max(0, Math.min(taskScroll, maxOffset));
		return true;
	}

	private static String stateLabel(String state) {
		switch (state) {
			case "pending": return "排队";
			case "running": return "执行中";
			case "paused": return "已暂停";
			case "done": return "已完成";
			case "cancelled": return "已取消";
			default: return state;
		}
	}

	private static int stateColor(String state) {
		switch (state) {
			case "pending": return COLOR_PENDING;
			case "running": return COLOR_RUNNING;
			case "paused": return COLOR_PAUSED;
			case "done": return COLOR_DONE;
			case "cancelled": return COLOR_CANCELLED;
			default: return COLOR_TITLE;
		}
	}

	private static String truncate(String s, int max) {
		if (s == null) {
			return "";
		}
		return s.length() <= max ? s : s.substring(0, max) + "...";
	}
}
