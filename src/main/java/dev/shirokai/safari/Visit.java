package dev.shirokai.safari;

/** Persisted active-time budget. Offline time never consumes it. */
public final class Visit {
  public long cycleStart, cycleEnd, usedMillis, budgetMillis, lastCharge;
  public int balls;
  public boolean active, recovery;
  public Config.Position origin;
  public String purchase, purchasePrice, purchaseCurrency;
  public long purchaseAmount;

  public void charge(long now) {
    if (active) {
      usedMillis = Math.min(budgetMillis, Math.addExact(usedMillis, Math.max(0, now - lastCharge)));
      lastCharge = now;
    }
  }

  public long remaining() {
    return Math.max(0, budgetMillis - usedMillis);
  }

  public boolean renewable(long now) {
    return !active && !recovery && now >= cycleEnd;
  }

  public void begin(long now, long cycleSeconds, long seconds, int bundle) {
    if (!renewable(now)) throw new IllegalStateException("Ciclo ainda não renovou");
    cycleStart = now;
    cycleEnd = Math.addExact(now, Math.multiplyExact(cycleSeconds, 1000));
    usedMillis = 0;
    budgetMillis = Math.multiplyExact(seconds, 1000);
    balls = bundle;
  }

  public boolean usable(long now) {
    return now < cycleEnd && remaining() > 0;
  }

  public void validate() {
    if (cycleStart < 0
        || cycleEnd < cycleStart
        || usedMillis < 0
        || budgetMillis < 0
        || usedMillis > budgetMillis
        || balls < 0
        || balls > 2304
        || active && origin == null
        || recovery && origin == null) throw new IllegalArgumentException("Estado Safari inválido");
  }
}
