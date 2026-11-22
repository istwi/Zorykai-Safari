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

  @Test
  void purchaseAndEntryRenewOnTheSameCycle() {
    var v = new Visit();
    v.begin(1000, 86400, 3600, 32);
    v.active = true;
    assertTrue(v.canBuy(2000));
    v.creditBundle(32, 2000);
    assertEquals(64, v.balls);
    assertFalse(v.canBuy(3000));
    v.active = false;
    assertFalse(v.renewable(v.cycleEnd - 1));
    v.begin(v.cycleEnd, 86400, 3600, 32);
    v.active = true;
    assertEquals(32, v.balls);
    assertFalse(v.bundlePurchased);
    assertTrue(v.canBuy(v.cycleStart + 1));
  }

  @Test
  void leavingDoesNotResetAdditionalBundle() {
    var v = new Visit();
    v.begin(1000, 86400, 3600, 32);
    v.active = true;
    v.creditBundle(32, 2000);
    v.active = false;
    v.active = true;
    assertFalse(v.canBuy(3000));
    assertThrows(IllegalStateException.class, () -> v.creditBundle(32, 3000));
  }

  @Test
  void interruptedPurchaseBlocksBothRenewalAndRepurchase() {
    var v = new Visit();
    v.begin(1000, 86400, 3600, 32);
    v.purchase = "pending";
    assertFalse(v.renewable(v.cycleEnd + 1));
    v.active = true;
    assertFalse(v.canBuy(2000));
  }

  @Test
  void latePaymentIsReservedForNextCycleInsteadOfLost() {
    var v = new Visit();
    v.begin(1000, 86400, 3600, 32);
    long end = v.cycleEnd;
    v.creditBundle(32, end + 1);
    assertEquals(32, v.nextCycleBundle);
    v.begin(end + 2, 86400, 3600, 32);
    v.active = true;
    assertEquals(64, v.balls);
    assertTrue(v.bundlePurchased);
    assertFalse(v.canBuy(end + 3));
    assertEquals(0, v.nextCycleBundle);
  }

  @Test
  void exhaustedBallsExpireAndBonusGrantsOnce() {
    var v = new Visit();
    v.begin(1000, 86400, 3600, 32);
    v.balls = 90;
    v.usedMillis = v.budgetMillis;
    v.bundlePurchased = true;
    assertTrue(v.expireBalls(2000));
    assertEquals(0, v.balls);
    assertTrue(v.extend(3000, 60000, 32));
    assertEquals(32, v.balls);
    v.balls = 17;
    assertFalse(v.extend(4000, 60000, 32));
    assertEquals(17, v.balls);
    assertTrue(v.bundlePurchased);
  }

  @Test
  void bonusWithoutExhaustionKeepsExistingBalls() {
    var v = new Visit();
    v.begin(1000, 86400, 3600, 32);
    v.balls = 12;
    assertFalse(v.extend(2000, 60000, 32));
    assertEquals(12, v.balls);
    assertEquals(3660000, v.remaining());
  }

  @Test
  void bonusChargesActiveTimeBeforeCheckingExhaustion() {
    var v = new Visit();
    v.begin(1000, 86400, 1, 32);
    v.active = true;
    v.lastCharge = 1000;
    v.balls = 95;
    assertTrue(v.extend(3000, 60000, 32));
    assertEquals(32, v.balls);
    assertEquals(60000, v.remaining());
  }

  @Test
  void expiredDailyWindowAlsoRemovesUnspentBalls() {
    var v = new Visit();
    v.begin(1000, 86400, 3600, 32);
    assertTrue(v.expireBalls(v.cycleEnd));
    assertEquals(0, v.balls);
    assertThrows(IllegalArgumentException.class, () -> v.extend(v.cycleEnd, 60000, 32));
  }

  @Test
  void paymentAfterExhaustionIsReservedAndDeliveredWithBonusOnce() {
    var v = new Visit();
    v.begin(1000, 86400, 3600, 32);
    v.usedMillis = v.budgetMillis;
    v.creditBundle(32, 2000);
    assertEquals(0, v.balls);
    assertEquals(32, v.reservedPaidBalls);
    assertTrue(v.bundlePurchased);
    v.extend(3000, 60000, 32);
    assertEquals(64, v.balls);
    assertEquals(0, v.reservedPaidBalls);
    v.extend(4000, 60000, 32);
    assertEquals(64, v.balls);
  }

  @Test
  void reservedPaymentCanWaitUntilNextDailyCycle() {
    var v = new Visit();
    v.begin(1000, 86400, 3600, 32);
    v.usedMillis = v.budgetMillis;
    v.creditBundle(32, 2000);
    v.begin(v.cycleEnd, 86400, 3600, 32);
    assertEquals(64, v.balls);
    assertTrue(v.bundlePurchased);
    assertEquals(0, v.reservedPaidBalls);
  }

  @Test
  void paymentArrivingAfterBonusAddsToNewBalanceWithoutAnotherStarter() {
    var v = new Visit();
    v.begin(1000, 86400, 3600, 32);
    v.usedMillis = v.budgetMillis;
    v.extend(2000, 60000, 32);
    v.creditBundle(32, 3000);
    assertEquals(64, v.balls);
    v.extend(4000, 60000, 32);
    assertEquals(64, v.balls);
  }
}
