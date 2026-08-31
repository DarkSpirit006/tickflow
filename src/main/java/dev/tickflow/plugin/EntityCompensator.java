package dev.tickflow.plugin;

import org.bukkit.World;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.potion.PotionEffect;

import java.util.List;

/** Applies optional timer compensation with explicit work limits. */
final class EntityCompensator {
    int acceleratePickup(List<World> worlds, int missed, int limit, CompensationStats stats) {
        int processed = 0;
        for (World world : worlds) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (processed >= limit) {
                    return processed;
                }
                if (!item.isValid()) {
                    continue;
                }
                item.setPickupDelay(Math.max(0, item.getPickupDelay() - missed));
                processed++;
            }
        }
        stats.addPickupUpdates(processed);
        return processed;
    }

    int accelerateMobTimers(List<World> worlds, int missed, int limit, CompensationStats stats) {
        int processed = 0;
        for (World world : worlds) {
            for (Ageable mob : world.getEntitiesByClass(Ageable.class)) {
                if (processed >= limit) {
                    return processed;
                }
                if (!mob.isValid() || mob.getAgeLock()) {
                    continue;
                }
                int age = mob.getAge();
                if (age < 0) {
                    mob.setAge(Math.min(0, age + missed));
                    processed++;
                } else if (age > 0) {
                    mob.setAge(Math.max(0, age - missed));
                    processed++;
                }
            }
        }
        stats.addMobTimerUpdates(processed);
        return processed;
    }

    int acceleratePotionDurations(List<World> worlds, int missed, int limit, CompensationStats stats) {
        int processed = 0;
        for (World world : worlds) {
            for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class)) {
                if (processed >= limit) {
                    return processed;
                }
                if (!living.isValid()) {
                    continue;
                }
                for (PotionEffect effect : living.getActivePotionEffects()) {
                    if (effect.getDuration() <= 0) {
                        continue;
                    }
                    PotionEffect replacement = new PotionEffect(
                            effect.getType(),
                            Math.max(0, effect.getDuration() - missed),
                            effect.getAmplifier(),
                            effect.isAmbient(),
                            effect.hasParticles()
                    );
                    living.addPotionEffect(replacement);
                    processed++;
                    break;
                }
            }
        }
        stats.addPotionUpdates(processed);
        return processed;
    }

    int accelerateTnt(List<World> worlds, int missed, int limit, CompensationStats stats) {
        int processed = 0;
        for (World world : worlds) {
            for (TNTPrimed tnt : world.getEntitiesByClass(TNTPrimed.class)) {
                if (processed >= limit) {
                    return processed;
                }
                if (!tnt.isValid()) {
                    continue;
                }
                tnt.setFuseTicks(Math.max(0, tnt.getFuseTicks() - missed));
                processed++;
            }
        }
        stats.addTntUpdates(processed);
        return processed;
    }
}
