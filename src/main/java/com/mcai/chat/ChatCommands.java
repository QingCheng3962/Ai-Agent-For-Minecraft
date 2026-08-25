package com.mcai.chat;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ChatCommands {
	private ChatCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("mcai")
						.then(ClientCommandManager.literal("chat")
								.then(ClientCommandManager.literal("reload").executes(ctx -> {
									ChatResponder.getInstance().reloadConfig();
									feedback("AI Player 配置已重载。");
									return 1;
								}))
								.then(ClientCommandManager.literal("toggle").executes(ctx -> {
									ChatConfig cfg = ChatResponder.getInstance().getConfig();
									cfg.enabled = !cfg.enabled;
									cfg.save();
									ChatResponder.getInstance().reloadConfig();
									feedback("AI Player " + (cfg.enabled ? "已启用" : "已禁用"));
									return 1;
								}))
								.then(ClientCommandManager.literal("status").executes(ctx -> {
									ChatConfig c = ChatResponder.getInstance().getConfig();
									feedback("AI Player | enabled=" + c.enabled + ", provider=" + c.provider
											+ ", model=" + c.model + ", trigger=" + c.triggerEnabled
											+ "(" + c.triggerCooldownSeconds + "s), schedule=" + c.scheduleEnabled
											+ "(" + c.scheduleIntervalSeconds + "s), restriction=" + c.restrictionEnabled
											+ ", image=" + c.imageGenerationEnabled
											+ ", context=" + c.contextEnabled + "(" + c.contextLength + ")");
									return 1;
								}))
								.then(ClientCommandManager.literal("clear").executes(ctx -> {
									ChatResponder.getInstance().clearContext();
									feedback("AI Player 上下文已清空。");
									return 1;
								})))));
	}

	private static void feedback(String msg) {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.player != null) {
			client.player.displayClientMessage(Component.literal("\u00a7a[mcai AI Player] \u00a7f" + msg), false);
		}
	}
}
