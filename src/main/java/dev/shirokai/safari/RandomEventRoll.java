package dev.shirokai.safari;

public final class RandomEventRoll {
  private RandomEventRoll() {}

  public static boolean wins(double chance, double draw) {
    return Double.isFinite(chance) && chance > 0 && draw >= 0 && draw < chance;
  }

  public static long next(long now, long intervalSeconds) {
    return Math.addExact(now, Math.multiplyExact(intervalSeconds, 1000L));
  }
}
