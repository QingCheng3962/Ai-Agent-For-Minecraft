package com.mcai.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class GameStateProvider {
	private GameStateProvider() {
	}

	public static String stateJson() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		JsonObject root = new JsonObject();
		if (p == null) {
			root.addProperty("ok", false);
			root.addProperty("error", "Player not in a world");
			return root.toString();
		}

		Level level = p.level();

		JsonObject player = new JsonObject();
		player.addProperty("name", p.getGameProfile().name());
		player.addProperty("position", fmt(p.getX()) + ", " + fmt(p.getY()) + ", " + fmt(p.getZ()));
		player.addProperty("dimension", level.dimension().identifier().toString());
		player.addProperty("health", fmt(p.getHealth()) + "/" + fmt(p.getMaxHealth()));
		player.addProperty("hunger", p.getFoodData().getFoodLevel());
		player.addProperty("saturation", fmt(p.getFoodData().getSaturationLevel()));
		player.addProperty("level", p.experienceLevel);
		player.addProperty("xpProgress", fmt(p.experienceProgress));
		player.addProperty("selectedSlot", p.getInventory().getSelectedSlot());
		player.addProperty("onGround", p.onGround());
		player.addProperty("inWater", p.isInWater());
		player.addProperty("sneaking", p.isShiftKeyDown());		player.addProperty("sprinting", p.isSprinting());
		player.addProperty("yaw", fmt(p.getYRot()));
		player.addProperty("pitch", fmt(p.getXRot()));
		player.addProperty("facing", facing(p.getYRot()));

		player.add("hotbar", hotbar(p));

		HitResult hit = p.pick(5.0, 1.0f, false);
		if (hit.getType() == HitResult.Type.BLOCK) {
			BlockHitResult bhr = (BlockHitResult) hit;
			BlockPos pos = bhr.getBlockPos();
			BlockState bs = level.getBlockState(pos);
			JsonObject target = new JsonObject();
			target.addProperty("pos", pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
			target.addProperty("block", blockName(bs));
			target.addProperty("distance", fmt(blockDistance(p, pos)));
			player.add("targetBlock", target);
		}

		root.add("player", player);
		root.add("entitiesNearby", entitiesNearby(p));
		root.add("blocksNearby", blocksNearby(level, p));
		root.addProperty("timeOfDay", (level.getDayTime() % 24000L) < 12000L ? "day" : "night");
		root.addProperty("ok", true);
		return root.toString();
	}

	private static JsonArray hotbar(LocalPlayer p) {
		JsonArray arr = new JsonArray();
		for (int i = 0; i < 9; i++) {
			ItemStack stack = p.getInventory().getItem(i);
			JsonObject o = new JsonObject();
			o.addProperty("slot", i);
			o.addProperty("item", itemName(stack));
			o.addProperty("count", stack.getCount());
			arr.add(o);
		}
		return arr;
	}

	private static JsonArray entitiesNearby(LocalPlayer p) {
		JsonArray arr = new JsonArray();
		List<Entity> entities = p.level().getEntities(p, p.getBoundingBox().inflate(10.0), e -> e != p);
		int count = 0;
		for (Entity e : entities) {
			if (count >= 12) {
				break;
			}
			double d = Math.round(p.distanceTo(e) * 10.0) / 10.0;
			JsonObject o = new JsonObject();
			o.addProperty("type", e.getType().getDescriptionId().replace("entity.minecraft.", ""));
			o.addProperty("name", e.getDisplayName().getString());
			o.addProperty("distance", d);
			arr.add(o);
			count++;
		}
		return arr;
	}

	private static JsonArray blocksNearby(Level level, LocalPlayer p) {
		JsonArray arr = new JsonArray();
		BlockPos base = p.getOnPos();
		int count = 0;
		for (int dy = -2; dy <= 1 && count < 24; dy++) {
			for (int dx = -2; dx <= 2 && count < 24; dx++) {
				for (int dz = -2; dz <= 2 && count < 24; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}
					BlockPos pos = base.offset(dx, dy, dz);
					BlockState bs = level.getBlockState(pos);
					if (bs.isAir()) {
						continue;
					}
					JsonObject o = new JsonObject();
					o.addProperty("block", blockName(bs));
					o.addProperty("rel", (dx > 0 ? "+" : "") + dx + " " + dy + " " + (dz > 0 ? "+" : "") + dz);
					arr.add(o);
					count++;
				}
			}
		}
		return arr;
	}

	private static String blockName(BlockState bs) {
		try {
			Identifier id = BuiltInRegistries.BLOCK.getKey(bs.getBlock());
			return id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
		} catch (Exception e) {
			return "unknown";
		}
	}

	private static String itemName(ItemStack stack) {
		if (stack.isEmpty()) {
			return "air";
		}
		try {
			Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
			return id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
		} catch (Exception e) {
			return "unknown";
		}
	}

	private static String facing(float yaw) {
		int d = Math.floorMod(Math.round(yaw / 45.0f), 8);
		switch (d) {
			case 0: return "south (+Z)";
			case 1: return "southwest";
			case 2: return "west (-X)";
			case 3: return "northwest";
			case 4: return "north (-Z)";
			case 5: return "northeast";
			case 6: return "east (+X)";
			default: return "southeast";
		}
	}

	private static double blockDistance(LocalPlayer p, BlockPos pos) {
		Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		return Math.round(p.getEyePosition().distanceTo(center) * 10.0) / 10.0;
	}

	private static String fmt(double d) {
		long v = Math.round(d * 100.0);
		return String.valueOf(v / 100.0);
	}
}
