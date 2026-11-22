package dev.shirokai.safari;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RegionsTest {
  private Config config() {
    var c = new Config();
    c.area = new Config.Area("multiworld:safari", 0, 0, 0, 10, 100, 10);
    return c;
  }

  @Test
  void oldConfigDefaultsToEmptyRegions() {
    var c = Storage.JSON.fromJson("{}", Config.class);
    c.validate();
    assertTrue(c.regions.isEmpty());
  }

  @Test
  void regionsPersistWithDimensionAndFacing() {
    var c = config();
    var position = new Config.Position("multiworld:safari", 4.5, 70, 2.5, 90, 10);
    c.regions.put("floresta", position);
    c.validate();
    var loaded = Storage.JSON.fromJson(Storage.JSON.toJson(c), Config.class);
    loaded.validate();
    assertEquals(position, loaded.regions.get("floresta"));
  }

  @Test
  void invalidDestinationAndIdentifiersRejected() {
    for (var dim : new String[] {"minecraft:overworld", "multiworld:safari"}) {
      var c = config();
      c.regions.put("floresta", new Config.Position(dim, 50, 70, 2, 0, 0));
      assertThrows(IllegalArgumentException.class, c::validate);
    }
    var c = config();
    c.regions.put("Nome Inválido", new Config.Position("multiworld:safari", 2, 70, 2, 0, 0));
    assertThrows(IllegalArgumentException.class, c::validate);
  }

  @Test
  void missingAreaAndExcessiveMenuEntriesRejected() {
    var c = config();
    for (int i = 0; i < 22; i++)
      c.regions.put("regiao_" + i, new Config.Position("multiworld:safari", 2, 70, 2, 0, 0));
    assertThrows(IllegalArgumentException.class, c::validate);
    c.regions.clear();
    c.regions.put("floresta", new Config.Position("multiworld:safari", 2, 70, 2, 0, 0));
    c.area = null;
    assertThrows(IllegalArgumentException.class, c::validate);
  }

  @Test
  void onlyPublicSafariNpcActionsAllowed() {
    assertTrue(TrainersBridge.safariCommand("player", "safari regioes"));
    assertTrue(TrainersBridge.safariCommand("player", "safari comprar"));
    assertTrue(TrainersBridge.safariCommand("player", "safari regiao floresta"));
    for (String action :
        new String[] {
          "safariadmin delete",
          "enderchest",
          "safari entrar",
          "safari regiao x extra",
          "/safari regioes"
        }) assertFalse(TrainersBridge.safariCommand("player", action));
    assertFalse(TrainersBridge.safariCommand("console", "safari regioes"));
  }
}
