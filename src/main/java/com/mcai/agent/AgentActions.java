package com.mcai.agent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class AgentActions {
	private final Minecraft mc;

	private int forwardTicks;
	private int backTicks;
	private int leftTicks;
	private int rightTicks;
	private int jumpTicks;
	private int attackTicks;
	private boolean sneakOn;
	private boolean sprintOn;

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
		o.keyUp.setDown(forwardTicks > 0);
		o.keyDown.setDown(backTicks > 0);
		o.keyLeft.setDown(leftTicks > 0);
		o.keyRight.setDown(rightTicks > 0);
		o.keyJump.setDown(jumpTicks > 0);
		o.keyShift.setDown(sneakOn);
		o.keySprint.setDown(sprintOn);
		o.keyAttack.setDown(attackTicks > 0);
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
		if (mc.options != null) {
			mc.options.keyUp.setDown(false);
			mc.options.keyDown.setDown(false);
			mc.options.keyLeft.setDown(false);
			mc.options.keyRight.setDown(false);
			mc.options.keyJump.setDown(false);
			mc.options.keyShift.setDown(false);
			mc.options.keySprint.setDown(false);
			mc.options.keyAttack.setDown(false);
		}
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
