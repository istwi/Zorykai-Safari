package dev.shirokai.safari;

/** Matemática independente para preservar a chance natural e aplicar apenas a rolagem extra. */
public final class ShinyBoost {
  private ShinyBoost() {}

  public static double baseChance(double shinyRate) {
    return Double.isFinite(shinyRate) && shinyRate > 1 ? 1D / shinyRate : 1D;
  }

  public static double extraRollChance(double multiplier, double shinyRate) {
    double base = baseChance(shinyRate);
    double target = Math.min(1D, base * Math.max(1D, multiplier));
    return base >= 1D ? 0D : Math.max(0D, Math.min(1D, (target - base) / (1D - base)));
  }

  public static double migrateLegacyChance(double extraChance, double shinyRate) {
    double base = baseChance(shinyRate);
    double oldFinal = base + (1D - base) * Math.max(0D, Math.min(1D, extraChance));
    return Math.max(1D, Math.min(10000D, oldFinal / base));
  }
}
