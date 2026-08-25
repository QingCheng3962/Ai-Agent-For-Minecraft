package com.mcai.gui;

import com.mcai.chat.ChatConfig;
import com.mcai.chat.ChatResponder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class BlacklistScreen extends Screen {
	private static final int ROW_H = 18;

	private EditBox nameBox;
	private int scroll;

	public BlacklistScreen() {
		super(Component.literal("黑名单"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		int h = this.height;
		int w = this.width;
		this.nameBox = new EditBox(this.font, 8, h - 42, Math.max(60, w - 200), 20, Component.literal("玩家名"));
		this.nameBox.setMaxLength(40);
		this.nameBox.setCanLoseFocus(true);
		this.nameBox.setHint(Component.literal("输入玩家名，回车或点添加"));
		this.addRenderableWidget(this.nameBox);

		this.addRenderableWidget(Button.builder(Component.literal("添加"), b -> addPlayer())
				.bounds(w - 188, h - 42, 60, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> this.minecraft.setScreen(new AiPlayerScreen()))
				.bounds(w - 70, h - 42, 62, 20).build());
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isConfirmation() && this.getFocused() instanceof EditBox) {
			addPlayer();
			return true;
		}
		return super.keyPressed(event);
	}

	private void addPlayer() {
		String name = this.nameBox.getValue().trim();
		if (name.isEmpty()) {
			return;
		}
		ChatConfig c = ChatResponder.getInstance().getConfig();
		for (ChatConfig.BlockedPlayer b : c.blockedPlayers) {
			if (b.name != null && b.name.equalsIgnoreCase(name)) {
				this.nameBox.setValue("");
				return;
			}
		}
		ChatConfig.BlockedPlayer b = new ChatConfig.BlockedPlayer();
		b.name = name;
		b.enabled = true;
		c.blockedPlayers.add(b);
		c.save();
		ChatResponder.getInstance().refreshFromDisk();
		this.nameBox.setValue("");
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		this.renderTransparentBackground(g);
		int w = this.width;
		Ui.panel(g, 4, 4, w - 4, 26);
		g.drawString(this.font, Component.literal("\u25c9 黑名单"), 10, 9, Ui.TEXT);
		String hint = "黑名单内玩家消息将被忽略";
		g.drawString(this.font, Component.literal(hint), w - 14 - this.font.width(hint), 9, Ui.TEXT_DIM);

		int top = 30;
		int bottom = this.height - 50;
		int x = 8;
		int listW = w - 16;
		List<ChatConfig.BlockedPlayer> list = ChatResponder.getInstance().getConfig().blockedPlayers;
		if (list.isEmpty()) {
			Ui.panel(g, 5, top - 1, w - 5, bottom + 1);
			g.drawString(this.font, Component.literal("暂无黑名单。在下方输入玩家名点「添加」。"), 10, top + 8, Ui.TEXT_DIM);
			super.render(g, mouseX, mouseY, partialTick);
			return;
		}

		int viewport = bottom - top;
		int totalH = list.size() * ROW_H;
		int maxOffset = Math.max(0, totalH - viewport);
		scroll = Math.max(0, Math.min(scroll, maxOffset));
		Ui.panel(g, 5, top - 1, w - 5, bottom + 1);
		g.enableScissor(x, top, x + listW, bottom);
		int yy = top - scroll;
		for (int i = 0; i < list.size(); i++) {
			ChatConfig.BlockedPlayer b = list.get(i);
			if (yy + ROW_H >= top && yy <= bottom) {
				boolean hover = mouseY >= yy && mouseY < yy + ROW_H;
				int rowBg = b.enabled ? (hover ? 0x334A2630 : 0x26000000) : (hover ? 0x332A3447 : 0x00000000);
				if (rowBg != 0) {
					g.fill(x, yy, x + listW, yy + ROW_H, rowBg);
				}
				String label = (b.enabled ? "\u25cf " : "\u25cb ") + b.name;
				g.drawString(this.font, Component.literal(label), x + 2, yy + 4, b.enabled ? 0xFFFF8A8A : Ui.TEXT_DIM);
				boolean togHover = hover && mouseX >= x + listW - 104 && mouseX < x + listW - 60;
				drawMiniButton(g, x + listW - 104, yy + 2, 44, b.enabled ? "解禁" : "封禁", togHover, b.enabled);
				boolean delHover = hover && mouseX >= x + listW - 56 && mouseX < x + listW - 12;
				drawMiniButton(g, x + listW - 56, yy + 2, 44, "删除", delHover, false);
			}
			yy += ROW_H;
		}
		g.disableScissor();

		if (maxOffset > 0) {
			int barX = w - 12;
			int barH = Math.max(24, viewport * viewport / Math.max(totalH, viewport));
			int barY = top + ((scroll * (viewport - barH)) / maxOffset);
			g.fill(barX, top, barX + 4, bottom, 0x33203040);
			g.fill(barX, barY, barX + 4, barY + barH, 0xAA6FB8D8);
		}

		super.render(g, mouseX, mouseY, partialTick);
	}

	private void drawMiniButton(GuiGraphics g, int x, int y, int w, String label, boolean hover, boolean red) {
		int bg = hover ? 0xFF4A3A3C : (red ? 0xFF3A2226 : 0xFF2A2E3C);
		g.fill(x, y, x + w, y + 14, bg);
		int tw = this.font.width(label);
		g.drawString(this.font, label, x + (w - tw) / 2, y + 3, 0xFFDDDDDD);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean isOutside) {
		int top = 30;
		int bottom = this.height - 50;
		int x = 8;
		int w = this.width - 16;
		List<ChatConfig.BlockedPlayer> list = ChatResponder.getInstance().getConfig().blockedPlayers;
		if (list.isEmpty() || event.y() < top || event.y() >= bottom || event.x() < x || event.x() > x + w) {
			return super.mouseClicked(event, isOutside);
		}
		int row = (int) (event.y() - top + scroll) / ROW_H;
		if (row < 0 || row >= list.size()) {
			return true;
		}
		ChatConfig.BlockedPlayer b = list.get(row);
		double px = event.x();
		ChatConfig c = ChatResponder.getInstance().getConfig();
		if (px >= x + w - 104 && px < x + w - 60) {
			b.enabled = !b.enabled;
			c.save();
			ChatResponder.getInstance().refreshFromDisk();
		} else if (px >= x + w - 56 && px < x + w - 12) {
			list.remove(row);
			c.save();
			ChatResponder.getInstance().refreshFromDisk();
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll -= (int) Math.round(verticalAmount * 30);
		int viewport = this.height - 50 - 30;
		int totalH = ChatResponder.getInstance().getConfig().blockedPlayers.size() * ROW_H;
		int maxOffset = Math.max(0, totalH - viewport);
		scroll = Math.max(0, Math.min(scroll, maxOffset));
		return true;
	}
}
