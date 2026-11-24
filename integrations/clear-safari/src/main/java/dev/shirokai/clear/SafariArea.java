package dev.shirokai.clear;

import java.lang.reflect.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

/** JDK-only ABI to Safari's configured cuboid, including its actual dimension key. */
public record SafariArea(
    String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
  public static SafariArea current() {
    if (!FabricLoader.getInstance().isModLoaded("shirokai_safari")) return null;
    try {
      var type = Class.forName("dev.shirokai.safari.SafariMod");
      var mod = type.getField("INSTANCE").get(null);
      if (mod == null) return null;
      var config = type.getField("config").get(mod);
      if (config == null) return null;
      var area = config.getClass().getField("area").get(config);
      if (area == null) return null;
      var c = area.getClass();
      return new SafariArea(
          (String) c.getMethod("dimension").invoke(area),
          (int) c.getMethod("minX").invoke(area),
          (int) c.getMethod("minY").invoke(area),
          (int) c.getMethod("minZ").invoke(area),
          (int) c.getMethod("maxX").invoke(area),
          (int) c.getMethod("maxY").invoke(area),
          (int) c.getMethod("maxZ").invoke(area));
    } catch (ReflectiveOperationException | LinkageError failure) {
      throw new IllegalStateException(
          "Safari instalado com API de área incompatível; limpeza suspensa", failure);
    }
  }

  public boolean contains(Entity entity) {
    return contains(
        entity.level().dimension().location().toString(),
        entity.getX(),
        entity.getY(),
        entity.getZ());
  }

  public boolean occupied(MinecraftServer server) {
    for (var player : server.getPlayerList().getPlayers()) if (contains(player)) return true;
    return false;
  }

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
