package dev.shirokai.safari;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RandomEventRollTest {
  @Test void onePercentUsesStrictProbabilityBoundary() {
    assertTrue(RandomEventRoll.wins(.01,.009999));
    assertFalse(RandomEventRoll.wins(.01,.01));
    assertFalse(RandomEventRoll.wins(0,0));
  }

  @Test void defaultScheduleIsTwoHours() {
    var config=new Config();config.validate();
    assertEquals(7_200_000L,RandomEventRoll.next(0,config.randomEvents.intervalSeconds));
    assertEquals(.01,config.randomEvents.chance);
    assertEquals(2,config.randomEvents.shinyMultiplier);
    assertEquals(4,config.randomEvents.spawnMultiplier);
    assertEquals(1800,config.randomEvents.exhaustedBonusSeconds);
    assertEquals(16,config.randomEvents.exhaustedBonusBalls);
  }
}
