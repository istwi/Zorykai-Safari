package dev.shirokai.safari;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ShinyBoostTest {
  @Test void multiplierPreservesNaturalProbability() {
    double base=1D/8192D, extra=ShinyBoost.extraRollChance(4,8192);
    assertEquals(base*4,base+(1-base)*extra,1e-12);
  }

  @Test void oneMeansNoAdditionalRollAndHugeValuesCapAtCertain() {
    assertEquals(0,ShinyBoost.extraRollChance(1,8192));
    double base=1D/8192D, extra=ShinyBoost.extraRollChance(10000,8192);
    assertEquals(1,base+(1-base)*extra,1e-12);
  }

  @Test void legacyOnePercentKeepsApproximatelyItsOldFinalChance() {
    double multiplier=ShinyBoost.migrateLegacyChance(.01,8192),base=1D/8192D;
    assertEquals(base+(1-base)*.01,base*multiplier,1e-12);
  }
}
