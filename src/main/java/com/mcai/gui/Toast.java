package com.mcai.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class Toast {
	private static volatile String text;
	private static volatile int color = 0xFF8BFFA0;
	private static volatile long expireTime;

	private Toast() {
	}

	public static void show(String msg) {
		show(msg, 0xFF8BFFA0, 3000);
	}

	public static void show(String msg, int textColor) {
		show(msg, textColor, 3000);
	}

	public static void show(String msg, int textColor, long durationMs) {
		text = msg;
		color = textColor;
		expireTime = System.currentTimeMillis() + durationMs;
	}

	public static void render(GuiGraphics g) {
		String t = text;
		if (t == null || t.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now > expireTime) {
			text = null;
			return;
		}
		long remaining = expireTime - now;
		float alpha = remaining < 500 ? remaining / 500f : 1f;

		Minecraft mc = Minecraft.getInstance();
		int w = mc.getWindow().getGuiScaledWidth();
		int tw = mc.font.width(t);
		int pad = 8;
		int boxW = tw + pad * 2;
		int boxH = 16;
		int x = w - boxW - 10;
		int y = 10;

		int bgAlpha = (int) (0xCC * alpha);
		int bg = (bgAlpha << 24) | 0x1F2837;
		g.fill(x - 1, y - 1, x + boxW + 1, y + boxH + 1, (int) (0x66 * alpha) << 24 | 0x3F4A61);
		g.fill(x, y, x + boxW, y + boxH, bg);
		int textAlpha = (int) (255 * alpha);
		int textColor = (textAlpha << 24) | (color & 0x00FFFFFF);
		g.drawString(mc.font, Component.literal(t), x + pad, y + 4, textColor);
	}
}
