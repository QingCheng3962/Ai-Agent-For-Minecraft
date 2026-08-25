package com.mcai.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

public final class Ui {
	public static final int BG = 0xE0141A26;
	public static final int PANEL = 0xF01F2837;
	public static final int PANEL_LIGHT = 0xF02A3447;
	public static final int BORDER = 0x663F4A61;
	public static final int ACCENT = 0xFF4FC3F7;
	public static final int ACCENT_DIM = 0xFF2E6B8F;
	public static final int TEXT = 0xFFE9ECF1;
	public static final int TEXT_DIM = 0xFF9AA5B1;
	public static final int USER_BUBBLE = 0xFF2E5FA3;
	public static final int AI_BUBBLE = 0xFF232C3E;
	public static final int TOOL_BUBBLE = 0xFF1A2230;
	public static final int ERROR_BUBBLE = 0xFF3A2226;
	public static final int EVENT_BUBBLE = 0xFF2A2616;
	public static final int ERROR = 0xFFFF6B6B;
	public static final int GREEN = 0xFF8BFFA0;
	public static final int GOLD = 0xFFFFD54F;

	private Ui() {
	}

	public static void panel(GuiGraphics g, int x1, int y1, int x2, int y2) {
		g.fill(x1, y1, x2, y2, PANEL);
		g.fill(x1, y1, x2, y1 + 1, BORDER);
		g.fill(x1, y2 - 1, x2, y2, BORDER);
		g.fill(x1, y1, x1 + 1, y2, BORDER);
		g.fill(x2 - 1, y1, x2, y2, BORDER);
	}

	public static void bubble(GuiGraphics g, int x, int y, int w, int h, int fill) {
		g.fill(x + 2, y, x + w - 2, y + h, fill);
		g.fill(x, y + 2, x + w, y + h - 2, fill);
		g.fill(x + 1, y + 1, x + w - 1, y + 1, fill);
		g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, fill);
		g.fill(x + 1, y + 1, x + 1, y + h - 1, fill);
		g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, fill);
	}

	public static void accentBar(GuiGraphics g, int x, int y, int h, int color) {
		g.fill(x, y, x + 2, y + h, color);
	}

	public static void dot(GuiGraphics g, int x, int y, int color) {
		g.fill(x, y, x + 5, y + 5, color);
	}

	public static void hLine(GuiGraphics g, int x1, int x2, int y, int color) {
		g.fill(x1, y, x2, y + 1, color);
	}

	public static final class ToggleButton extends Button {
		private final BooleanSupplier active;
		private final String onLabel;
		private final String offLabel;

		public ToggleButton(int x, int y, int width, int height, String onLabel, String offLabel,
				BooleanSupplier active, OnPress onPress) {
			super(x, y, width, height, Component.literal(""), onPress, DEFAULT_NARRATION);
			this.onLabel = onLabel;
			this.offLabel = offLabel;
			this.active = active;
		}

		@Override
		protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
			boolean on = active.getAsBoolean();
			int fill = on ? (isHoveredOrFocused() ? ACCENT : ACCENT_DIM) : (isHoveredOrFocused() ? PANEL_LIGHT : PANEL);
			g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), fill);
			if (on) {
				g.fill(getX() + 1, getY() + getHeight() - 2, getX() + getWidth() - 1, getY() + getHeight() - 1, ACCENT);
			} else {
				g.fill(getX() + 1, getY(), getX() + getWidth() - 1, getY() + 1, BORDER);
			}
			Component label = Component.literal(on ? onLabel : offLabel);
			int tx = getX() + (getWidth() - Minecraft.getInstance().font.width(label)) / 2;
			int ty = getY() + (getHeight() - 8) / 2;
			g.drawString(Minecraft.getInstance().font, label, tx, ty, on ? 0xFFFFFFFF : TEXT_DIM);
		}
	}
}
