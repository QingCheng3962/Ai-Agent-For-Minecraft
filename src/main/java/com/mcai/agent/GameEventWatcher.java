package com.mcai.agent;

import com.mcai.config.ConfigManager;
import com.mcai.config.McAiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;

import java.util.List;

public final class GameEventWatcher {
	private boolean wasDead;
	private boolean lastLowHealth;
	private boolean lastHostile;
	private boolean lastNight;
	private String lastDimension = "";
	private int tickCounter;

	public void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) {
			wasDead = false;
			lastLowHealth = false;
			lastHostile = false;
			lastNight = false;
			lastDimension = "";
			return;
		}
		if ((++tickCounter % 10) != 0) {
			return;
		}
		McAiConfig cfg = ConfigManager.get();

		if (!p.isAlive() && !wasDead) {
			McAiAgent.get().onGameEvent("你死亡了，即将重生");
			wasDead = true;
		} else if (p.isAlive() && wasDead) {
			McAiAgent.get().onGameEvent("你已重生，位置 " + fmt(p.getX()) + "," + fmt(p.getY()) + "," + fmt(p.getZ())
					+ "，生命值 " + fmt(p.getHealth()));
			wasDead = false;
			lastHealthReset(p);
		}

		String dim = p.level().dimension().identifier().toString();
		if (!lastDimension.isEmpty() && !dim.equals(lastDimension)) {
			McAiAgent.get().onGameEvent("你进入了新维度: " + dim);
		}
		lastDimension = dim;

		if (cfg.watchHealth && p.isAlive()) {
			boolean low = p.getHealth() < cfg.healthThreshold;
			if (low && !lastLowHealth) {
				McAiAgent.get().onGameEvent("生命值过低: " + fmt(p.getHealth()) + "/" + fmt(p.getMaxHealth()));
			}
			lastLowHealth = low;
		}

		if (cfg.watchHostiles && p.isAlive() && cfg.hostileRange > 0) {
			double nearest = nearestHostile(p, cfg.hostileRange);
			boolean hostile = nearest >= 0;
			if (hostile && !lastHostile) {
				McAiAgent.get().onGameEvent("附近有敌对生物，最近距离约 " + fmt(nearest) + " 格");
			}
			lastHostile = hostile;
		}

		boolean night = (p.level().getDayTime() % 24000L) >= 12000L;
		if (night && !lastNight) {
			McAiAgent.get().onGameEvent("天黑了");
		}
		lastNight = night;
	}

	private void lastHealthReset(LocalPlayer p) {
		lastLowHealth = p.isAlive() && p.getHealth() < ConfigManager.get().healthThreshold;
	}

	private static double nearestHostile(LocalPlayer p, double range) {
		double best = -1;
		List<Entity> entities = p.level().getEntities(p, p.getBoundingBox().inflate(range),
				e -> e.getType().getCategory() == MobCategory.MONSTER);
		for (Entity e : entities) {
			double d = p.distanceTo(e);
			if (best < 0 || d < best) {
				best = d;
			}
		}
		return best;
	}

	private static String fmt(double d) {
		long v = Math.round(d * 10.0);
		return String.valueOf(v / 10.0);
	}
}
