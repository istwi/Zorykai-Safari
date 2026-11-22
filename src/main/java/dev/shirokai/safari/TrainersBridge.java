package dev.shirokai.safari;

import java.util.Map;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;

/** Allows only indexed Trainers command NPCs with a public Safari action. */
public final class TrainersBridge {
  private TrainersBridge() {}

  public static boolean safariCommand(String executor, String command) {
    return "player".equals(executor)
        && command != null
        && (command.equals("safari comprar")
            || command.equals("safari regioes")
            || command.equals("safari eventos")
            || command.matches("safari regiao [a-z0-9_-]{1,32}"));
  }

  public static boolean safariNpc(Entity entity) {
    var loader = FabricLoader.getInstance();
    if (!loader.isModLoaded("shirokai_trainers")
        || !(entity instanceof com.cobblemon.mod.common.entity.npc.NPCEntity)) return false;
    try {
      for (var container : loader.getEntrypointContainers("main", ModInitializer.class)) {
        if (!container.getProvider().getMetadata().getId().equals("shirokai_trainers")) continue;
        var mod = container.getEntrypoint();
        var npcs = mod.getClass().getMethod("getNpcs").invoke(mod);
        var records = (Map<?, ?>) npcs.getClass().getMethod("getRecords").invoke(npcs);
        for (var record : records.values()) {
          var c = record.getClass();
          if (!entity.getUUID().equals(c.getMethod("getEntity").invoke(record))
              || (boolean) c.getMethod("getRemoved").invoke(record)
              || c.getMethod("getTrainer").invoke(record) != null) continue;
          var action = c.getMethod("getClickCommand").invoke(record);
          if (action == null) return false;
          return safariCommand(
              (String) action.getClass().getMethod("getExecutor").invoke(action),
              (String) action.getClass().getMethod("getCommand").invoke(action));
        }
      }
    } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
      // An incompatible optional bridge never grants interaction access.
    }
    return false;
  }
}
