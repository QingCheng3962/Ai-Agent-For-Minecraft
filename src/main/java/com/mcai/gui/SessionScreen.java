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

		int w = this.width;
		String active = McAiAgent.get().getActiveSession();
		Ui.panel(graphics, 4, 4, w - 4, 26);
		graphics.drawString(this.font, Component.literal("\u25c9 会话历史（当前: " + active + "）"), 10, 9, Ui.TEXT);

		int top = 30;
		int bottom = this.height - 50;
		List<String> sessions = SessionManager.listSessions();
		if (sessions.isEmpty()) {
			Ui.panel(graphics, 5, top - 1, w - 5, bottom + 1);
			graphics.drawString(this.font, Component.literal("暂无会话。输入名称点新建即可。"),
					10, top + 8, Ui.TEXT_DIM);
			super.render(graphics, mouseX, mouseY, partialTick);
			return;
		}

		int x = 8;
		int listW = w - 16;
		int viewport = bottom - top;
		int totalH = sessions.size() * ROW_H;
		int maxOffset = Math.max(0, totalH - viewport);
		scroll = Math.max(0, Math.min(scroll, maxOffset));

		Ui.panel(graphics, 5, top - 1, w - 5, bottom + 1);
		graphics.enableScissor(x, top, x + listW, bottom);
		int yy = top - scroll;
		for (int i = 0; i < sessions.size(); i++) {
			String s = sessions.get(i);
			if (yy + ROW_H >= top && yy <= bottom) {
				boolean isActive = s.equals(active);
				boolean hover = mouseY >= yy && mouseY < yy + ROW_H;
				int rowBg = isActive ? 0x66308AB0 : (hover ? 0x332A3447 : 0x00000000);
				if (rowBg != 0x00000000) {
					graphics.fill(x, yy, x + listW, yy + ROW_H, rowBg);
				}
				graphics.drawString(this.font, Component.literal((isActive ? "\u25b6 " : "  ") + s),
						x + 2, yy + 4, isActive ? COLOR_ACTIVE : COLOR_NORMAL);
				if (!"default".equals(s)) {
					drawMiniButton(graphics, x + listW - 52, yy + 2, 44, "删除", hover && mouseX >= x + listW - 52);
				}
				boolean switchHover = hover && mouseX >= x + listW - 96 && mouseX < x + listW - 56;
				graphics.drawString(this.font, Component.literal("切换"),
						x + listW - 96, yy + 4, switchHover ? Ui.ACCENT : 0xFF7CD6FF);
			}
			yy += ROW_H;
		}
		graphics.disableScissor();

		if (maxOffset > 0) {
			int barX = w - 12;
			int barH = Math.max(24, viewport * viewport / Math.max(totalH, viewport));
			int barY = top + ((scroll * (viewport - barH)) / maxOffset);
			graphics.fill(barX, top, barX + 4, bottom, 0x33203040);
			graphics.fill(barX, barY, barX + 4, barY + barH, 0xAA6FB8D8);
		}

		super.render(graphics, mouseX, mouseY, partialTick);
		Toast.render(graphics);
	}

	private void drawMiniButton(GuiGraphics graphics, int x, int y, int w, String label, boolean hover) {
		graphics.fill(x, y, x + w, y + 14, hover ? 0xFF3A4558 : 0xFF2A2E3C);
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
