package dev.shirokai.safari;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class StorageTest {
  @TempDir Path dir;

  @Test
  void interruptedVisitAndPurchaseSurviveRoundtrip() throws Exception {
    var data = new Storage.Data();
    var v = new Visit();
    v.begin(1000, 86400, 3600, 32);
    v.origin = new Config.Position("minecraft:overworld", 1.5, 70, 2.5, 30, 10);
    v.active = true;
    v.purchase = "order-1";
    v.purchasePrice = "10000";
    v.purchaseCurrency = "impactor:dollars";
    v.purchaseAmount = 32;
    data.players.put("test", v);
    var p = dir.resolve("data/state.json");
    Storage.write(p, data);
    var loaded = Storage.read(p, Storage.Data.class).players.get("test");
    loaded.validate();
    assertTrue(loaded.active);
    assertEquals(v.origin, loaded.origin);
    assertEquals("order-1", loaded.purchase);
    assertEquals(32, loaded.purchaseAmount);
  }

  @Test
  void malformedInputIsNotSilentlyOverwritten() throws Exception {
    var p = dir.resolve("state.json");
    Files.writeString(p, "{broken");
    assertThrows(java.io.IOException.class, () -> Storage.read(p, Storage.Data.class));
    assertEquals("{broken", Files.readString(p));
  }

  @Test
  void atomicReplacementLeavesNoTemporaryFiles() throws Exception {
    var p = dir.resolve("state.json");
    var d = new Storage.Data();
    Storage.write(p, d);
    d.event.ends = 12345;
    Storage.write(p, d);
    assertEquals(12345, Storage.read(p, Storage.Data.class).event.ends);
    try (var files = Files.list(dir)) {
      assertEquals(1, files.count());
    }
  }

  @Test
  void purchasedAllowanceSurvivesPersistenceAndDoesNotResetOnPause() throws Exception {
    var state = new Storage.Data();
    var v = new Visit();
    v.begin(1000, 86400, 3600, 32);
    v.creditBundle(32, 2000);
    state.players.put("player", v);
    var path = dir.resolve("quota.json");
    Storage.write(path, state);
    var loaded = Storage.read(path, Storage.Data.class).players.get("player");
    assertTrue(loaded.bundlePurchased);
    assertEquals(64, loaded.balls);
    loaded.active = true;
    assertFalse(loaded.canBuy(3000));
  }

  @Test
  void restartPreservesBonusGrantAndReservedPaidEntitlement() throws Exception {
    var state = new Storage.Data();
    var v = new Visit();
    v.begin(1000, 86400, 3600, 32);
    v.usedMillis = v.budgetMillis;
    v.creditBundle(32, 2000);
    state.players.put("player", v);
    var path = dir.resolve("bonus.json");
    Storage.write(path, state);
    var restored = Storage.read(path, Storage.Data.class).players.get("player");
    assertEquals(32, restored.reservedPaidBalls);
    assertEquals(0, restored.balls);
    restored.extend(3000, 60000, 32);
    state.players.put("player", restored);
    Storage.write(path, state);
    var again = Storage.read(path, Storage.Data.class).players.get("player");
    assertEquals(64, again.balls);
    assertEquals(0, again.reservedPaidBalls);
    assertFalse(again.extend(4000, 60000, 32));
    assertEquals(64, again.balls);
    assertTrue(again.bundlePurchased);
  }
}
