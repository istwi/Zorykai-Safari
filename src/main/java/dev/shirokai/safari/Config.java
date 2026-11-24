package dev.shirokai.safari;

import java.util.*;

public final class Config {
  public int schemaVersion = 1;
  public long cycleSeconds = 86400, normalSeconds = 3600;
  public Map<String, Long> vipSeconds =
      new LinkedHashMap<>(
          Map.of("magma", 7200L, "aqua", 8640L, "plasma", 10368L, "galactic", 12442L));
  public int ballBundle = 32;
  public String ballItem = "cobblemon:safari_ball", currency = "impactor:dollars";
  public java.math.BigDecimal bundlePrice = new java.math.BigDecimal("10000");
  public double spawnMultiplier = 2;
  public RandomEvents randomEvents = new RandomEvents();
  public Area area;
  public Position entrance;
  public Position lobby;
  public Map<String, Position> regions = new LinkedHashMap<>();

  public static final class RandomEvents {
    public boolean enabled = true;
    public long intervalSeconds = 7200, durationSeconds = 1800, exhaustedBonusSeconds = 1800;
    public int exhaustedBonusBalls = 16;
    public double chance = 0.01, shinyMultiplier = 2, spawnMultiplier = 4;
  }

  public record Position(String dimension, double x, double y, double z, float yaw, float pitch) {}

  public record Area(String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public boolean contains(String dim, double x, double y, double z) {
      return dimension.equals(dim)
          && x >= minX
          && x < maxX + 1D
          && y >= minY
          && y < maxY + 1D
          && z >= minZ
          && z < maxZ + 1D;
    }
  }

  public void validate() {
    if (schemaVersion != 1
        || cycleSeconds < 60
        || cycleSeconds > 604800
        || normalSeconds < 1
        || normalSeconds > cycleSeconds
        || ballBundle < 1
        || ballBundle > 64
        || bundlePrice == null
        || bundlePrice.signum() < 0
        || bundlePrice.compareTo(new java.math.BigDecimal("1000000000")) > 0
        || !Double.isFinite(spawnMultiplier)
        || spawnMultiplier < 1
        || spawnMultiplier > 10
        || randomEvents == null
        || randomEvents.intervalSeconds < 60
        || randomEvents.intervalSeconds > 604800
        || randomEvents.durationSeconds < 60
        || randomEvents.durationSeconds > 86400
        || randomEvents.exhaustedBonusSeconds < 60
        || randomEvents.exhaustedBonusSeconds > 86400
        || randomEvents.exhaustedBonusBalls < 1
        || randomEvents.exhaustedBonusBalls > 64
        || !Double.isFinite(randomEvents.chance)
        || randomEvents.chance < 0
        || randomEvents.chance > 1
        || !Double.isFinite(randomEvents.shinyMultiplier)
        || randomEvents.shinyMultiplier < 1
        || randomEvents.shinyMultiplier > 10000
        || !Double.isFinite(randomEvents.spawnMultiplier)
        || randomEvents.spawnMultiplier < 1
        || randomEvents.spawnMultiplier > 10)
      throw new IllegalArgumentException("Limites de configuração inválidos");
    if (vipSeconds == null
        || vipSeconds.size() > 50
        || vipSeconds.entrySet().stream()
            .anyMatch(
                e ->
                    !e.getKey().matches("[a-z0-9_-]{1,64}")
                        || e.getValue() == null
                        || e.getValue() < 1
                        || e.getValue() > cycleSeconds))
      throw new IllegalArgumentException("Tempos VIP inválidos");
    if (area != null
        && (area.dimension == null
            || area.minX > area.maxX
            || area.minY > area.maxY
            || area.minZ > area.maxZ
            || area.maxX - (long) area.minX > 4096
            || area.maxZ - (long) area.minZ > 4096))
      throw new IllegalArgumentException("Área inválida");
    if (regions == null || regions.size() > 21)
      throw new IllegalArgumentException("São permitidas até 21 regiões");
    for (var entry : regions.entrySet()) {
      var dest = entry.getValue();
      if (entry.getKey() == null
          || !entry.getKey().matches("[a-z0-9_-]{1,32}")
          || dest == null
          || area == null
          || !Double.isFinite(dest.x + dest.y + dest.z)
          || !Float.isFinite(dest.yaw + dest.pitch)
          || !area.contains(dest.dimension, dest.x, dest.y, dest.z))
        throw new IllegalArgumentException("Região inválida ou fora da área: " + entry.getKey());
    }
    if (lobby != null
        && (lobby.dimension == null
            || !Double.isFinite(lobby.x + lobby.y + lobby.z)
            || !Float.isFinite(lobby.yaw + lobby.pitch)
            || area != null && area.contains(lobby.dimension, lobby.x, lobby.y, lobby.z)))
      throw new IllegalArgumentException("Saída deve estar fora da área");
    if (entrance != null
        && (area == null
            || !area.contains(entrance.dimension, entrance.x, entrance.y, entrance.z)
            || !Double.isFinite(entrance.x + entrance.y + entrance.z)
            || !Float.isFinite(entrance.yaw + entrance.pitch)))
      throw new IllegalArgumentException("Entrada deve estar na área");
  }
}
