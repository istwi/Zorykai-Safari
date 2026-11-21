package dev.shirokai.safari;

import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPermsProvider;
import net.minecraft.server.level.ServerPlayer;

public final class Permissions {
  public static boolean has(ServerPlayer p, String node, boolean fallback) {
    if (!FabricLoader.getInstance().isModLoaded("luckperms")) return fallback;
    try {
      var u = LuckPermsProvider.get().getUserManager().getUser(p.getUUID());
      if (u == null) return false;
      var decision = u.getCachedData().getPermissionData().checkPermission(node);
      return decision == net.luckperms.api.util.Tristate.UNDEFINED
          ? fallback
          : decision.asBoolean();
    } catch (RuntimeException | LinkageError e) {
      return false;
    }
  }

  public static boolean admin(net.minecraft.commands.CommandSourceStack s) {
    return !s.isPlayer() || has(s.getPlayer(), "shirokai.safari.admin", s.hasPermission(2));
  }

  public static long seconds(ServerPlayer p, Config c) {
    long result = c.normalSeconds;
    if (!FabricLoader.getInstance().isModLoaded("luckperms")) return result;
    try {
      var lp = LuckPermsProvider.get();
      var user = lp.getUserManager().getUser(p.getUUID());
      if (user == null) return result;
      var groups =
          user.getInheritedGroups(
              lp.getContextManager()
                  .getQueryOptions(user)
                  .orElse(lp.getContextManager().getStaticQueryOptions()));
      for (var e : c.vipSeconds.entrySet())
        if (has(p, "shirokai.safari.vip." + e.getKey(), false)
            || groups.stream().anyMatch(g -> g.getName().equals(e.getKey())))
          result = Math.max(result, e.getValue());
    } catch (RuntimeException | LinkageError ignored) {
    }
    return result;
  }
}
