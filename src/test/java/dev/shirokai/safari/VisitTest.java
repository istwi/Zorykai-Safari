package dev.shirokai.safari;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class VisitTest {
  @Test
  void clockStartsAtFirstEntry() {
    var v = new Visit();
    assertTrue(v.renewable(1_000));
    v.begin(1_000, 86400, 3600, 32);
    assertEquals(86_401_000, v.cycleEnd);
    assertEquals(3_600_000, v.remaining());
    assertEquals(32, v.balls);
  }

  @Test
  void pausedTimeDoesNotConsumeBudget() {
    var v = new Visit();
    v.begin(1000, 86400, 3600, 32);
    v.active = true;
    v.lastCharge = 1000;
    v.charge(1_801_000);
    v.active = false;
    v.charge(10_801_000);
    assertEquals(1_800_000, v.remaining());
  }

  @Test
  void resumeDoesNotGrantAnotherBundle() {
    var v = new Visit();
    v.begin(1000, 86400, 3600, 32);
    v.balls = 7;
    assertThrows(IllegalStateException.class, () -> v.begin(2000, 86400, 3600, 32));
    assertEquals(7, v.balls);
  }

  @Test
  void cycleRenewalAtExactBoundary() {
    var v = new Visit();
    v.begin(1000, 86400, 3600, 32);
    assertFalse(v.renewable(v.cycleEnd - 1));
    assertTrue(v.renewable(v.cycleEnd));
    v.begin(v.cycleEnd, 86400, 7200, 32);
    assertEquals(7_200_000, v.remaining());
  }

  @Test
  void pendingReturnNeverErasesOriginForNewCycle() {
    var v = new Visit();
    v.begin(1000, 86400, 3600, 32);
    v.recovery = true;
    assertFalse(v.renewable(v.cycleEnd + 1000));
  }

  @Test
  void usageStopsAtBudgetAndIgnoresBackwardClock() {
    var v = new Visit();
    v.begin(1000, 86400, 3600, 32);
    v.active = true;
    v.lastCharge = 1000;
    v.charge(500);
    assertEquals(3_600_000, v.remaining());
    v.charge(9_000_000);
    assertEquals(0, v.remaining());
  }

  @Test
  void quotaAndDailyWindowAreIndependent() {
    var v = new Visit();
    v.begin(1000, 86400, 3600, 32);
    assertFalse(v.usable(v.cycleEnd));
    v.usedMillis = v.budgetMillis;
    assertFalse(v.usable(2000));
  }

  @Test
  void vipProgressionAndConfigurationBounds() {
    var c = new Config();
    c.validate();
    assertEquals(7200, c.vipSeconds.get("magma"));
    assertEquals(8640, c.vipSeconds.get("aqua"));
    assertEquals(10368, c.vipSeconds.get("plasma"));
    assertEquals(12442, c.vipSeconds.get("galactic"));
    c.bundlePrice = new java.math.BigDecimal("-1");
    assertThrows(IllegalArgumentException.class, c::validate);
  }

  @Test
  void areaUsesInclusiveBlocksAndDimension() {
    var a = new Config.Area("minecraft:overworld", 0, 60, 0, 10, 90, 10);
    assertTrue(a.contains("minecraft:overworld", 10.9, 90.9, 10.9));
    assertFalse(a.contains("minecraft:overworld", 11, 70, 0));
    assertFalse(a.contains("minecraft:the_nether", 0, 70, 0));
  }

  @Test
  void lobbyInsideSafariIsRejected() {
    var c = new Config();
    c.area = new Config.Area("minecraft:overworld", 0, 0, 0, 10, 100, 10);
    c.lobby = new Config.Position("minecraft:overworld", 5, 70, 5, 0, 0);
    assertThrows(IllegalArgumentException.class, c::validate);
  }

  @Test
  void persistentStateRejectsImpossibleBudget() {
    var v = new Visit();
    v.usedMillis = 1;
    assertThrows(IllegalArgumentException.class, v::validate);
  }

  @Test
  void eventsExpireAtDeadline() {
    var e = new Storage.Event();
    e.ends = 1000;
    assertTrue(e.running(999));
    assertFalse(e.running(1000));
  }
}
