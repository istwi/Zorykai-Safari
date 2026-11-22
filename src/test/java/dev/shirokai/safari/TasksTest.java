package dev.shirokai.safari;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TasksTest {
  private long time(String text) {
    return Instant.parse(text).toEpochMilli();
  }

  private TasksConfig.Mission mission() {
    var m = new TasksConfig.Mission();
    m.id = "test";
    m.name = "Teste";
    m.target = 3;
    return m;
  }

  @Test
  void defaultsDoNotCreateMissionsOrProducts() {
    var m = new TasksConfig.Missions();
    var s = new TasksConfig.Shop();
    m.validate();
    s.validate();
    new TasksConfig.Menus().validate();
    assertTrue(m.missions.isEmpty());
    assertTrue(s.products.isEmpty());
  }

  @Test
  void dailyAndWeeklyResetAtBrasiliaMidnight() {
    var c = new TasksConfig.Missions();
    long before = time("2026-11-23T02:59:59Z"), after = time("2026-11-23T03:00:00Z");
    assertNotEquals(
        c.period(TasksConfig.Period.DAILY, before), c.period(TasksConfig.Period.DAILY, after));
    assertNotEquals(
        c.period(TasksConfig.Period.WEEKLY, before), c.period(TasksConfig.Period.WEEKLY, after));
    assertEquals(after, c.next(TasksConfig.Period.DAILY, before));
    assertEquals(after, c.next(TasksConfig.Period.WEEKLY, before));
    assertTrue(c.next(TasksConfig.Period.WEEKLY, after) > after);
  }

  @Test
  void customResetHourAndWeekdayAreUsed() {
    var c = new TasksConfig.Missions();
    c.resetHour = 6;
    c.weeklyResetDay = "WEDNESDAY";
    c.validate();
    long before = time("2026-11-25T08:59:59Z"), after = time("2026-11-25T09:00:00Z");
    assertNotEquals(
        c.period(TasksConfig.Period.WEEKLY, before), c.period(TasksConfig.Period.WEEKLY, after));
    assertEquals(after, c.next(TasksConfig.Period.WEEKLY, before));
  }

  @Test
  void eachSourceCountsOnlyOnceAndProgressCapsAtTarget() {
    var c = new TasksConfig.Missions();
    var m = mission();
    var p = new TasksState.Player();
    long now = time("2026-11-23T12:00:00Z");
    assertTrue(TasksState.increment(p, c, m, "pokemon-1", now));
    assertFalse(TasksState.increment(p, c, m, "pokemon-1", now));
    TasksState.increment(p, c, m, "pokemon-2", now);
    TasksState.increment(p, c, m, "pokemon-3", now);
    assertFalse(TasksState.increment(p, c, m, "pokemon-4", now));
    assertEquals(3, TasksState.progress(p, c, m, now).value);
  }

  @Test
  void nextDayStartsNewProgressAndDoesNotResetWeeklyProgress() {
    var c = new TasksConfig.Missions();
    var day = mission();
    var week = mission();
    week.id = "week";
    week.period = TasksConfig.Period.WEEKLY;
    var p = new TasksState.Player();
    long now = time("2026-11-23T12:00:00Z"), next = now + 86400000;
    TasksState.increment(p, c, day, "a", now);
    TasksState.increment(p, c, week, "a", now);
    assertEquals(0, TasksState.progress(p, c, day, next).value);
    assertEquals(1, TasksState.progress(p, c, week, next).value);
  }

  @Test
  void editedObjectiveDoesNotAllowSecondClaimForSameIdAndPeriod() {
    var c = new TasksConfig.Missions();
    var m = mission();
    var p = new TasksState.Player();
    long now = time("2026-11-23T12:00:00Z");
    p.claimed.add(TasksState.key(c, m, now));
    m.target = 10;
    assertFalse(TasksState.increment(p, c, m, "new", now));
    assertTrue(p.claimed.contains(TasksState.key(c, m, now)));
  }

  @Test
  void pendingReceiptAndBalanceSurviveRoundtrip() {
    var state = new TasksState();
    var p = new TasksState.Player();
    p.points = 123;
    p.pending = new TasksState.Receipt();
    p.pending.id = "receipt";
    p.pending.kind = "SHOP";
    p.pending.key = "L:item";
    p.pending.pointsDelta = -100;
    p.pending.reward = new TasksConfig.Reward();
    state.players.put("id", p);
    var loaded =
        Storage.JSON.fromJson(Storage.JSON.toJson(state), TasksState.class).players.get("id");
    loaded.validate();
    assertEquals(123, loaded.points);
    assertEquals("receipt", loaded.pending.id);
    assertEquals(-100, loaded.pending.pointsDelta);
  }

  @Test
  void trimKeepsLifetimeAndPendingPurchase() {
    var c = new TasksConfig.Missions();
    var p = new TasksState.Player();
    p.purchases.put("L:unlock", 1);
    p.purchases.put("D:2020-01-01:old", 1);
    p.purchases.put("D:2020-01-01:pending", 1);
    p.pending = new TasksState.Receipt();
    p.pending.key = "D:2020-01-01:pending";
    TasksState.trim(p, c, time("2026-11-23T12:00:00Z"));
    assertTrue(p.purchases.containsKey("L:unlock"));
    assertTrue(p.purchases.containsKey(p.pending.key));
    assertFalse(p.purchases.containsKey("D:2020-01-01:old"));
  }

  @Test
  void invalidDefinitionsAndDuplicateIdsAreRejected() {
    var c = new TasksConfig.Missions();
    var m = mission();
    c.missions.add(m);
    c.missions.add(m);
    assertThrows(IllegalArgumentException.class, c::validate);
    c.missions.remove(1);
    m.target = 0;
    assertThrows(IllegalArgumentException.class, c::validate);
  }

  @Test
  void shopCannotMintPointsOrHaveNonpositiveCost() {
    var c = new TasksConfig.Shop();
    var p = new TasksConfig.Product();
    p.id = "test";
    p.reward.commands.add("say ok");
    c.products.add(p);
    c.validate();
    p.reward.points = 1;
    assertThrows(IllegalArgumentException.class, c::validate);
    p.reward.points = 0;
    p.cost = 0;
    assertThrows(IllegalArgumentException.class, c::validate);
  }

  @Test
  void menuSlotsCannotOverlapAndPartialJsonRetainsDefaults() {
    var c = Storage.JSON.fromJson("{\"missions\":{\"title\":\"Teste\"}}", TasksConfig.Menus.class);
    c.validate();
    c.missions.balanceSlot = c.missions.closeSlot;
    assertThrows(IllegalArgumentException.class, c::validate);
  }

  @Test
  void invalidCommandsAndTimezoneAreRejected() {
    var r = new TasksConfig.Reward();
    r.commands.add("give {unknown} minecraft:diamond");
    assertThrows(IllegalArgumentException.class, r::validate);
    r.commands.clear();
    r.commands.add("say ok\nstop");
    assertThrows(IllegalArgumentException.class, r::validate);
    var c = new TasksConfig.Missions();
    c.timezone = "not-a-zone";
    assertThrows(Exception.class, c::validate);
  }

  @Test
  void purchasePeriodKeysDistinguishDailyWeeklyMonthlyLifetime() {
    var c = new TasksConfig.Missions();
    long a = time("2026-11-30T12:00:00Z"), b = time("2026-12-01T12:00:00Z");
    assertNotEquals(c.limit(TasksConfig.Limit.DAILY, a), c.limit(TasksConfig.Limit.DAILY, b));
    assertEquals(c.limit(TasksConfig.Limit.WEEKLY, a), c.limit(TasksConfig.Limit.WEEKLY, b));
    assertNotEquals(c.limit(TasksConfig.Limit.MONTHLY, a), c.limit(TasksConfig.Limit.MONTHLY, b));
    assertEquals(c.limit(TasksConfig.Limit.LIFETIME, a), c.limit(TasksConfig.Limit.LIFETIME, b));
  }

  @Test
  void resetRemainsCorrectAcrossDaylightSavingFallback() {
    var c = new TasksConfig.Missions();
    c.timezone = "America/New_York";
    c.resetHour = 6;
    long before = time("2026-11-01T10:30:00Z"), after = time("2026-11-01T11:00:00Z");
    assertEquals("D:2026-10-31:", c.period(TasksConfig.Period.DAILY, before));
    assertEquals(after, c.next(TasksConfig.Period.DAILY, before));
    assertEquals("D:2026-11-01:", c.period(TasksConfig.Period.DAILY, after));
  }
}
