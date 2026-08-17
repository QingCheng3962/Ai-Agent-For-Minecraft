package com.mcai.agent;

import com.mcai.config.ConfigManager;
import com.mcai.gui.AiAgentScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

public final class AgentActions {
	private final Minecraft mc;

	private volatile int forwardTicks;
	private volatile int backTicks;
	private volatile int leftTicks;
	private volatile int rightTicks;
	private volatile int jumpTicks;
	private volatile int attackTicks;
	private volatile boolean sneakOn;
	private volatile boolean sprintOn;

	public AgentActions() {
		this.mc = Minecraft.getInstance();
	}

	public void tick() {
		Options o = mc.options;
		if (o == null) {
			return;
		}
		LocalPlayer p = mc.player;
		if (p == null) {
			releaseKeys();
			return;
		}
		boolean control = ConfigManager.get().allowPlayerControl;
		boolean playerInput = isPlayerInputEnabled();
		applyKey(o.keyUp, control && forwardTicks > 0, playerInput);
		applyKey(o.keyDown, control && backTicks > 0, playerInput);
		applyKey(o.keyLeft, control && leftTicks > 0, playerInput);
		applyKey(o.keyRight, control && rightTicks > 0, playerInput);
		applyKey(o.keyJump, control && jumpTicks > 0, playerInput);
		applyKey(o.keyShift, control && sneakOn, playerInput);
		applyKey(o.keySprint, control && sprintOn, playerInput);
		applyKey(o.keyAttack, control && attackTicks > 0, playerInput);
		if (forwardTicks > 0) {
			forwardTicks--;
		}
		if (backTicks > 0) {
			backTicks--;
		}
		if (leftTicks > 0) {
			leftTicks--;
		}
		if (rightTicks > 0) {
			rightTicks--;
		}
		if (jumpTicks > 0) {
			jumpTicks--;
		}
		if (attackTicks > 0) {
			attackTicks--;
		}
	}

	private void applyKey(KeyMapping key, boolean aiDown, boolean playerInput) {
		key.setDown(aiDown || (playerInput && isRawDown(key)));
	}

	private boolean isPlayerInputEnabled() {
		if (mc.screen == null) {
			return true;
		}
		if (mc.screen instanceof AiAgentScreen s) {
			return ConfigManager.get().windowMove && !s.isInputFocused();
		}
		return false;
	}

	private boolean isRawDown(KeyMapping key) {
		if (mc.getWindow() == null) {
			return false;
		}
		long win = mc.getWindow().handle();
		InputConstants.Key k = KeyBindingHelper.getBoundKeyOf(key);
		if (k.getType() == InputConstants.Type.KEYSYM) {
			return GLFW.glfwGetKey(win, k.getValue()) == GLFW.GLFW_PRESS;
		}
		if (k.getType() == InputConstants.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(win, k.getValue()) == GLFW.GLFW_PRESS;
		}
		return false;
	}

	public void releaseKeys() {
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
		o.keyAttack.setDown(false);
	}

	public void move(String direction, int ticks) {
		switch (direction) {
			case "forward": forwardTicks = ticks; break;
			case "back": backTicks = ticks; break;
			case "left": leftTicks = ticks; break;
			case "right": rightTicks = ticks; break;
			default: break;
		}
	}

	public void jump(int ticks) {
		jumpTicks = ticks;
	}

	public void sneak(boolean on) {
		sneakOn = on;
	}

	public void sprint(boolean on) {
		sprintOn = on;
	}

	public void stop() {
		forwardTicks = backTicks = leftTicks = rightTicks = jumpTicks = attackTicks = 0;
		sneakOn = sprintOn = false;
	}

	public void releaseAll() {
		forwardTicks = backTicks = leftTicks = rightTicks = jumpTicks = attackTicks = 0;
		sneakOn = sprintOn = false;
	}

	public void look(float yaw, float pitch) {
		LocalPlayer p = mc.player;
		if (p == null) {
			return;
		}
		p.setYRot(yaw);
		p.setXRot(pitch);
	}

	public void attack(int ticks) {
		attackTicks = Math.min(ticks, 80);
	}

	public String useItem() {
		LocalPlayer p = mc.player;
		if (p == null) {
			return "{\"ok\":false,\"error\":\"player not in world\"}";
		}
		HitResult hit = p.pick(5.0, 1.0f, false);
		if (hit.getType() == HitResult.Type.BLOCK) {
			mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, (BlockHitResult) hit);
		} else {
			mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
		}
		return "{\"ok\":true}";
	}

	public String dropHeldItem() {
		LocalPlayer p = mc.player;
		if (p == null) {
			return "{\"ok\":false,\"error\":\"player not in world\"}";
		}
		ItemStack stack = p.getInventory().getSelectedItem();
		if (stack.isEmpty()) {
			return "{\"ok\":false,\"error\":\"nothing in hand\"}";
		}
		p.drop(false);
		return "{\"ok\":true,\"item\":\"" + stack.getItem().toString() + "\"}";
	}

	public String sendChat(String message) {
		LocalPlayer p = mc.player;
		if (p == null) {
			return "{\"ok\":false,\"error\":\"player not in world\"}";
		}
		p.connection.sendChat(message);
		return "{\"ok\":true}";
	}

	public void selectHotbar(int slot) {
		LocalPlayer p = mc.player;
		if (p == null) {
			return;
		}
		p.getInventory().setSelectedSlot(slot);
	}
}
