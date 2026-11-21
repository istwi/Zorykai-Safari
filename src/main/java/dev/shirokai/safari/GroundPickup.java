package dev.shirokai.safari;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Only the vanilla ground-pickup call can introduce a non-Safari stack. */
public final class GroundPickup {
  private record Context(ServerPlayer player, ItemStack stack) {}

  private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

  private GroundPickup() {}

  public static boolean allowed(ServerPlayer p, ItemStack stack) {
    var c = CURRENT.get();
    return c != null && c.player() == p && c.stack() == stack;
  }

  public static boolean collect(Inventory inventory, ItemStack stack) {
    if (!(inventory.player instanceof ServerPlayer p) || !SafariMod.lockedPlayer(p))
      return inventory.add(stack);
    var previous = CURRENT.get();
    CURRENT.set(new Context(p, stack));
    try {
      return inventory.add(stack);
    } finally {
      if (previous == null) CURRENT.remove();
      else CURRENT.set(previous);
    }
  }
}
