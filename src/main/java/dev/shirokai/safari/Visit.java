package dev.shirokai.safari;

/** Persisted active-time budget. Offline time never consumes it. */
public final class Visit {
  public long cycleStart, cycleEnd, usedMillis, budgetMillis, lastCharge;
  public int balls, nextCycleBundle, reservedPaidBalls;
  public boolean bundlePurchased;
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
    return !active && !recovery && purchase == null && now >= cycleEnd;
  }

  public void begin(long now, long cycleSeconds, long seconds, int bundle) {
    if (!renewable(now)) throw new IllegalStateException("Ciclo ainda não renovou");
    cycleStart = now;
    cycleEnd = Math.addExact(now, Math.multiplyExact(cycleSeconds, 1000));
    usedMillis = 0;
    budgetMillis = Math.multiplyExact(seconds, 1000);
    balls = bundle + nextCycleBundle + reservedPaidBalls;
    bundlePurchased = nextCycleBundle > 0 || reservedPaidBalls > 0;
    nextCycleBundle = 0;
    reservedPaidBalls = 0;
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
        || nextCycleBundle < 0
        || nextCycleBundle > 64
        || reservedPaidBalls < 0
        || reservedPaidBalls > 64
        || balls < 0
        || balls > 2304
        || active && origin == null
        || recovery && origin == null) throw new IllegalArgumentException("Estado Safari inválido");
  }

  /** Unspent visit balls expire; undelivered paid entitlements are kept separately. */
  public boolean expireBalls(long now) {
    if (budgetMillis > 0 && !usable(now)) {
      balls = 0;
      return true;
    }
    return false;
  }

  /** Remaining quota itself is the persisted once-only marker for bonus starter balls. */
  public boolean extend(long now, long bonusMillis, int bundle) {
    if (now >= cycleEnd || bonusMillis <= 0 || bundle < 1 || bundle > 64)
      throw new IllegalArgumentException("Bônus inválido ou ciclo expirado");
    charge(now);
    long nextBudget = Math.addExact(budgetMillis, bonusMillis);
    boolean exhausted = remaining() == 0;
    if (exhausted) {
      balls = Math.addExact(bundle, reservedPaidBalls);
      reservedPaidBalls = 0;
    }
    budgetMillis = nextBudget;
    return exhausted;
  }

  public boolean canBuy(long now) {
    return active && usable(now) && !bundlePurchased && purchase == null;
  }

  public void creditBundle(int count, long now) {
    if (bundlePurchased)
      throw new IllegalStateException("Pacote adicional já creditado neste ciclo");
    if (count < 1 || count > 64)
      throw new IllegalArgumentException("Quantidade de pacote inválida");
    expireBalls(now);
    bundlePurchased = true;
    if (now >= cycleEnd) nextCycleBundle = count;
    else if (remaining() == 0) reservedPaidBalls = count;
    else balls = Math.addExact(balls, count);
  }
}
