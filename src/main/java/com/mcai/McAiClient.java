package com.mcai;

import com.mcai.agent.GameEventWatcher;
import com.mcai.agent.McAiAgent;
import com.mcai.config.ConfigManager;
import com.mcai.gui.AiAgentScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class McAiClient implements ClientModInitializer {
	public static final String MOD_ID = "mcai";

	private static final KeyMapping OPEN_KEY = new KeyMapping(
			"key.mcai.open",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_K,
			KeyMapping.Category.register(Identifier.parse(MOD_ID + ":agent")));

	private final GameEventWatcher eventWatcher = new GameEventWatcher();
	private boolean wasSyncing;

	@Override
	public void onInitializeClient() {
		KeyBindingHelper.registerKeyBinding(OPEN_KEY);

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			McAiAgent.get().onClientTick(mc);
			eventWatcher.tick(mc);
			boolean syncActive = ConfigManager.get().windowMove
					&& mc.screen instanceof AiAgentScreen agentScreen
					&& !agentScreen.isInputFocused();
			if (syncActive) {
				syncMovement(mc);
				wasSyncing = true;
			} else if (wasSyncing) {
				releaseMovementKeys(mc);
				wasSyncing = false;
			}
			while (OPEN_KEY.consumeClick()) {
				mc.setScreen(new AiAgentScreen());
			}
		});

		ClientReceiveMessageEvents.CHAT.register((component, message, profile, bound, instant) -> {
			if (!ConfigManager.get().watchChat || profile == null) {
				return;
			}
			Minecraft mc = Minecraft.getInstance();
			String self = mc.player != null && mc.player.getGameProfile() != null
					? mc.player.getGameProfile().name() : null;
			if (profile.name().equals(self)) {
				return;
			}
			McAiAgent.get().onGameEvent("聊天 " + profile.name() + ": " + component.getString());
		});

		HudRenderCallback.EVENT.register((graphics, deltaTracker) -> {
			if (McAiAgent.get().isBusy()) {
				Minecraft mc = Minecraft.getInstance();
				int w = mc.getWindow().getGuiScaledWidth();
				String s = "[mcai] 思考中...";
				graphics.drawString(mc.font, s, w - mc.font.width(s) - 2, 2, 0xFFFFFFFF);
			}
		});
	}

	private static void syncMovement(Minecraft mc) {
		Options o = mc.options;
		if (o == null || mc.getWindow() == null) {
			return;
		}
		long win = mc.getWindow().handle();
		o.keyUp.setDown(o.keyUp.isDown() || isPressed(win, KeyBindingHelper.getBoundKeyOf(o.keyUp)));
		o.keyDown.setDown(o.keyDown.isDown() || isPressed(win, KeyBindingHelper.getBoundKeyOf(o.keyDown)));
		o.keyLeft.setDown(o.keyLeft.isDown() || isPressed(win, KeyBindingHelper.getBoundKeyOf(o.keyLeft)));
		o.keyRight.setDown(o.keyRight.isDown() || isPressed(win, KeyBindingHelper.getBoundKeyOf(o.keyRight)));
		o.keyJump.setDown(o.keyJump.isDown() || isPressed(win, KeyBindingHelper.getBoundKeyOf(o.keyJump)));
		o.keyShift.setDown(o.keyShift.isDown() || isPressed(win, KeyBindingHelper.getBoundKeyOf(o.keyShift)));
		o.keySprint.setDown(o.keySprint.isDown() || isPressed(win, KeyBindingHelper.getBoundKeyOf(o.keySprint)));
	}

	private static void releaseMovementKeys(Minecraft mc) {
		Options o = mc.options;
		if (o == null) {
			return;
		}
		o.keyUp.setDown(false);
		o.keyDown.setDown(false);
		o.keyLeft.setDown(false);
		o.keyRight.setDown(false);
		o.keyJump.setDown(false);
		o.keyShift.setDown(false);
		o.keySprint.setDown(false);
	}

	private static boolean isPressed(long win, InputConstants.Key key) {
		if (key.getType() == InputConstants.Type.KEYSYM) {
			return GLFW.glfwGetKey(win, key.getValue()) == GLFW.GLFW_PRESS;
		}
		return false;
	}
}
