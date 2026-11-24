package dev.shirokai.clear;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafariConfigTest {
  @TempDir Path dir;

  @Test
  void migrationPreservesGlobalTimerAndMessages() throws Exception {
    var path = dir.resolve("shirokaiclear.json");
    Files.writeString(
        path,
        "{\"intervalSeconds\":900,\"announcements\":{\"warningMessage\":\"MEU AVISO"
            + " {seconds}\",\"warningSeconds\":[30]}}");
    var c = ClearConfig.load(path);
    assertEquals(900, c.intervalSeconds);
    assertEquals("MEU AVISO {seconds}", c.announcements.warningMessage);
    assertEquals(120, c.safari.intervalSeconds);
    assertTrue(c.safari.onlyWhenOccupied);
    assertTrue(Files.readString(path).contains("\"safari\""));
    var again = ClearConfig.load(path);
    assertEquals(900, again.intervalSeconds);
  }

  @Test
  void regionalPolicyKeepsProtectionsButIgnoresGlobalDimensionExclusion() {
    var c = new ClearConfig();
    c.worlds.excludedDimensions.add("multiworld:safari");
    c.pokemon.protectShiny = false;
    c.pokemon.excludedSpecies.add("cobblemon:ditto");
    var local = c.safariPolicy();
    assertTrue(local.worlds.accepts("multiworld:safari"));
    assertFalse(c.worlds.accepts("multiworld:safari"));
    assertFalse(local.pokemon.protectShiny);
    assertTrue(local.pokemon.excludedSpecies.contains("cobblemon:ditto"));
    assertEquals(0, local.pokemon.minimumAgeSeconds);
    assertEquals(120, c.pokemon.minimumAgeSeconds);
    assertEquals(600, c.intervalSeconds);
  }

  @Test
  void areaUsesTheDimensionSelectedInMultiworld() {
    var a = new SafariArea("multiworld:safari", 0, 50, 0, 10, 100, 10);
    assertTrue(a.contains("multiworld:safari", 10.5, 80, 10.5));
    assertFalse(a.contains("minecraft:overworld", 10.5, 80, 10.5));
    assertFalse(a.contains("multiworld:safari", 11, 80, 10));
  }

  @Test
  void unsafeConfigurationStillRejected() {
    var c = new ClearConfig();
    c.safari.intervalSeconds = 0;
    assertThrows(IllegalArgumentException.class, c::validate);
    c.safari.intervalSeconds = 120;
    c.pokemon.protectBattles = false;
    assertThrows(IllegalArgumentException.class, c::validate);
  }
}
