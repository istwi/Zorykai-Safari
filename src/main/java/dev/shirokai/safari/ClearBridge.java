package dev.shirokai.safari;

import java.util.*;
import net.fabricmc.loader.api.FabricLoader;

public final class ClearBridge {
  private ClearBridge() {}

  public static Map<String, String> status() {
    if (!FabricLoader.getInstance().isModLoaded("shirokaiclear"))
      return Map.of("status", "Clear não instalado");
    try {
      var result =
          Class.forName("dev.shirokai.clear.ShirokaiClear").getMethod("safariStatus").invoke(null);
      if (result instanceof Map<?, ?> map) {
        var copy = new LinkedHashMap<String, String>();
        map.forEach((k, v) -> copy.put(String.valueOf(k), String.valueOf(v)));
        return copy;
      }
    } catch (ReflectiveOperationException | LinkageError ignored) {
    }
    return Map.of("status", "Atualize o Clear com a integração Safari");
  }
}
