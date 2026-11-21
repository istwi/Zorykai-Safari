package dev.shirokai.safari;

import java.lang.reflect.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerBossEvent;

/** Optional read-only discovery: never changes Raid membership or server-side boss bars. */
public final class RaidHudBridge {
  private RaidHudBridge() {}

  private static boolean warned;

  private static Object field(Object object, String name) throws ReflectiveOperationException {
    var f = object.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(object);
  }

  public static List<ServerBossEvent> bars() {
    if (!FabricLoader.getInstance().isModLoaded("shirokai_raids")) return List.of();
    try {
      var type = Class.forName("com.shirokai.raids.ShirokaiRaids");
      var instance = type.getDeclaredField("instance");
      instance.setAccessible(true);
      var raids = instance.get(null);
      if (raids == null) return List.of();
      var bars = new ArrayList<ServerBossEvent>();
      var countdown = field(raids, "countdownBar");
      if (countdown instanceof ServerBossEvent event) bars.add(event);
      var active = field(raids, "active");
      if (active != null && field(active, "bar") instanceof ServerBossEvent event) bars.add(event);
      return bars;
    } catch (ReflectiveOperationException | LinkageError e) {
      if (!warned) {
        warned = true;
        SafariMod.LOG.warn(
            "Integração visual Raid indisponível nesta versão; barras de outros mods preservadas",
            e);
      }
      return List.of();
    }
  }
}
