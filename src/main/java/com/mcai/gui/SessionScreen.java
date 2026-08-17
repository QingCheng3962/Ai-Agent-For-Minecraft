package com.mcai.gui;

import com.mcai.agent.McAiAgent;
import com.mcai.config.SessionManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class SessionScreen extends Screen {
	private static final int ROW_H = 18;
	private static final int COLOR_ACTIVE = 0xFF8BFFA0;
	private static final int COLOR_NORMAL = 0xFFDDDDDD;
	private static final int COLOR_TITLE = 0xFFDDDDDD;

	private EditBox newName;
	private int scroll;

	public SessionScreen() {
		super(Component.literal("会话历史"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		int h = this.height;
		int w = this.width;
		this.newName = new EditBox(this.font, 8, h - 42, Math.max(60, w - 250), 20,
				Component.literal("新会话名"));
		this.newName.setMaxLength(40);
		this.newName.setCanLoseFocus(true);
		this.newName.setHint(Component.literal("新会话名"));
		this.addRenderableWidget(this.newName);

		this.addRenderableWidget(Button.builder(Component.literal("新建"), b -> createNew())
				.bounds(w - 238, h - 42, 60, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> this.minecraft.setScreen(new AiAgentScreen()))
				.bounds(w - 70, h - 42, 62, 20).build());

		this.setInitialFocus(this.newName);
	}

	private void createNew() {
		String name = this.newName.getValue().trim();
		if (!name.isEmpty()) {
			McAiAgent.get().newSession(name);
			this.newName.setValue("");
			this.minecraft.setScreen(new AiAgentScreen());
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isConfirmation() && this.getFocused() instanceof EditBox) {
			createNew();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.renderTransparentBackground(graphics);

		String active = McAiAgent.get().getActiveSession();
		graphics.drawString(this.font, Component.literal("会话历史（当前: " + active + "）"),
				8, 8, COLOR_TITLE);

		int top = 24;
		int bottom = this.height - 50;
		List<String> sessions = SessionManager.listSessions();
		if (sessions.isEmpty()) {
			graphics.drawString(this.font, Component.literal("暂无会话。输入名称点新建即可。"),
					8, top + 4, 0xFF8F8F8F);
			super.render(graphics, mouseX, mouseY, partialTick);
			return;
		}

		int x = 8;
		int w = this.width - 16;
		int viewport = bottom - top;
		int totalH = sessions.size() * ROW_H;
		int maxOffset = Math.max(0, totalH - viewport);
		scroll = Math.max(0, Math.min(scroll, maxOffset));

		graphics.enableScissor(x, top, x + w, bottom);
		int yy = top - scroll;
		for (int i = 0; i < sessions.size(); i++) {
			String s = sessions.get(i);
			if (yy + ROW_H >= top && yy <= bottom) {
				boolean isActive = s.equals(active);
				graphics.drawString(this.font, Component.literal((isActive ? "\u25b6 " : "  ") + s),
						x + 2, yy + 4, isActive ? COLOR_ACTIVE : COLOR_NORMAL);
				if (!"default".equals(s)) {
					drawMiniButton(graphics, x + w - 52, yy + 2, 44, "删除", false);
				}
				graphics.drawString(this.font, Component.literal("切换"),
						x + w - 96, yy + 4, 0xFF7CD6FF);
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
		int bottom = this.height - 50;
		List<String> sessions = SessionManager.listSessions();
		if (sessions.isEmpty()) {
			return super.mouseClicked(event, isOutside);
		}
		int x = 8;
		int w = this.width - 16;
		if (event.x() < x || event.x() > x + w || event.y() < top || event.y() >= bottom) {
			return super.mouseClicked(event, isOutside);
		}
		int row = (int) (event.y() - top + scroll) / ROW_H;
		if (row < 0 || row >= sessions.size()) {
			return true;
		}
		String s = sessions.get(row);
		double px = event.x();
		if (px >= x + w - 52 && px <= x + w && !"default".equals(s)) {
			McAiAgent.get().deleteSession(s);
		} else {
			McAiAgent.get().switchSession(s);
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll -= (int) Math.round(verticalAmount * 30);
		int viewport = this.height - 50 - 24;
		int totalH = SessionManager.listSessions().size() * ROW_H;
		int maxOffset = Math.max(0, totalH - viewport);
		scroll = Math.max(0, Math.min(scroll, maxOffset));
		return true;
	}
}
