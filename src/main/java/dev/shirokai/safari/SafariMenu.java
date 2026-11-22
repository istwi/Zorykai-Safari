package dev.shirokai.safari;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

public final class SafariMenu extends AbstractContainerMenu {
  private final ServerPlayerRef ref;

  public enum Screen {
    MAIN,
    PURCHASE,
    EVENTS
  }

  private final Screen screen;

  private record ServerPlayerRef(net.minecraft.server.level.ServerPlayer player, SafariMod mod) {}

  public SafariMenu(int id, Inventory inventory, SafariMod mod) {
    this(id, inventory, mod, false);
  }

  public SafariMenu(int id, Inventory inventory, SafariMod mod, boolean confirmation) {
    this(id, inventory, mod, confirmation ? Screen.PURCHASE : Screen.MAIN);
  }

  public SafariMenu(int id, Inventory inventory, SafariMod mod, Screen screen) {
    super(MenuType.GENERIC_9x3, id);
    ref = new ServerPlayerRef((net.minecraft.server.level.ServerPlayer) inventory.player, mod);
    this.screen = screen;
    var chest = new SimpleContainer(27);
    for (int i = 0; i < 27; i++) addSlot(new Slot(chest, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18));
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 85 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 143));
    if (screen == Screen.PURCHASE) {
      button(
          chest,
          11,
          Items.LIME_STAINED_GLASS_PANE,
          "CONFIRMAR • "
              + mod.config.ballBundle
              + " Safari Balls por $"
              + mod.config.bundlePrice.toPlainString());
      button(chest, 15, Items.RED_STAINED_GLASS_PANE, "Cancelar compra");
      return;
    }
    if (screen == Screen.EVENTS) {
      button(chest, 13, Items.NETHER_STAR, "Eventos do Safari");
      chest
          .getItem(13)
          .set(
              DataComponents.LORE,
              new net.minecraft.world.item.component.ItemLore(
                  java.util.Arrays.stream(
                          mod.eventDescription().split(java.util.regex.Pattern.quote(" | ")))
                      .map(Component::literal)
                      .map(c -> (Component) c)
                      .toList()));
      button(chest, 22, Items.ARROW, "Atualizar informações");
      return;
    }
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
    if (screen == Screen.PURCHASE) {
      if (slot != 11 && slot != 15) return;
      SafariMod.closeMenuIfOpen(ref.player);
      if (slot == 11)
        try {
          ref.mod.completePurchase(ref.player);
        } catch (RuntimeException e) {
          ref.mod.tell(ref.player, e.getMessage());
        }
      return;
    }
    if (screen == Screen.EVENTS) {
      if (slot == 22) {
        try {
          ref.mod.eventMenu(ref.player);
        } catch (RuntimeException e) {
          ref.mod.tell(ref.player, e.getMessage());
        }
      }
      return;
    }
    if (slot != 10 && slot != 16) return;
    try {
      switch (slot) {
        case 10 -> ref.mod.enter(ref.player);
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
