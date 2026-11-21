package dev.shirokai.safari;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

public final class SafariMenu extends AbstractContainerMenu {
  private final ServerPlayerRef ref;

  private record ServerPlayerRef(net.minecraft.server.level.ServerPlayer player, SafariMod mod) {}

  public SafariMenu(int id, Inventory inventory, SafariMod mod) {
    super(MenuType.GENERIC_9x3, id);
    ref = new ServerPlayerRef((net.minecraft.server.level.ServerPlayer) inventory.player, mod);
    var chest = new SimpleContainer(27);
    for (int i = 0; i < 27; i++) addSlot(new Slot(chest, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18));
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 85 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 143));
    var v = mod.visit(ref.player);
    button(chest, 10, Items.COMPASS, "Entrar / continuar");
    button(
        chest,
        12,
        Items.CLOCK,
        "Saldo: "
            + mod.format(v.remaining())
            + " | renovação em "
            + mod.format(Math.max(0, v.cycleEnd - System.currentTimeMillis())));
    button(
        chest,
        14,
        Items.EMERALD,
        "Comprar " + mod.config.ballBundle + " bolas: $" + mod.config.bundlePrice.toPlainString());
    button(chest, 16, Items.OAK_DOOR, "Sair e guardar o saldo");
  }

  private void button(SimpleContainer c, int slot, Item item, String text) {
    var s = new ItemStack(item);
    s.set(DataComponents.CUSTOM_NAME, Component.literal(text));
    c.setItem(slot, s);
  }

  @Override
  public void clicked(int slot, int button, ClickType type, Player player) {
    if (player != ref.player || !stillValid(player)) return;
    if (type != ClickType.PICKUP || button != 0) return;
    ref.player.closeContainer();
    try {
      switch (slot) {
        case 10 -> ref.mod.enter(ref.player);
        case 14 -> ref.mod.confirmBuy(ref.player);
        case 16 -> ref.mod.exit(ref.player);
        default -> {}
      }
    } catch (RuntimeException e) {
      ref.mod.tell(ref.player, e.getMessage());
    }
  }

  @Override
  public boolean stillValid(Player player) {
    return player == ref.player && player.isAlive();
  }

  @Override
  public ItemStack quickMoveStack(Player p, int slot) {
    return ItemStack.EMPTY;
  }
}
