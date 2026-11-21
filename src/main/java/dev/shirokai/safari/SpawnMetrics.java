package dev.shirokai.safari;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;

public final class SpawnMetrics {
  public record Timer(double base, double effective, long sampledAt) {}

  private static final Map<UUID, Timer> TIMERS = new HashMap<>();
  private static final ArrayDeque<Long> ATTEMPTS = new ArrayDeque<>(), SPAWNS = new ArrayDeque<>();
  private static final Map<UUID, Long> PENDING = new HashMap<>();

  private SpawnMetrics() {}

  public static void register() {
    ServerEntityEvents.ENTITY_LOAD.register(
        (entity, world) -> {
          if (PENDING.remove(entity.getUUID()) != null) {
            SPAWNS.add(System.currentTimeMillis());
            prune();
          }
        });
    ServerLifecycleEvents.SERVER_STOPPED.register(
        server -> {
          TIMERS.clear();
          ATTEMPTS.clear();
          SPAWNS.clear();
          PENDING.clear();
        });
  }

  public static void timer(UUID player, double base, double effective) {
    TIMERS.put(player, new Timer(base, effective, System.currentTimeMillis()));
  }

  public static void attempt() {
    ATTEMPTS.add(System.currentTimeMillis());
    prune();
  }

  public static void prepared(net.minecraft.world.entity.Entity entity) {
    prune();
    PENDING.put(entity.getUUID(), System.currentTimeMillis());
  }

  private static void prune() {
    long before = System.currentTimeMillis() - 60000;
    while (!ATTEMPTS.isEmpty() && ATTEMPTS.peek() < before) ATTEMPTS.remove();
    while (!SPAWNS.isEmpty() && SPAWNS.peek() < before) SPAWNS.remove();
    PENDING.values().removeIf(time -> time < before);
    TIMERS.values().removeIf(t -> t.sampledAt() < before);
  }

  public static int attempts() {
    prune();
    return ATTEMPTS.size();
  }

  public static int spawns() {
    prune();
    return SPAWNS.size();
  }

  public static List<Timer> recentTimers() {
    prune();
    long cutoff = System.currentTimeMillis() - 5000;
    return TIMERS.values().stream().filter(t -> t.sampledAt() >= cutoff).toList();
  }
}
