package dev.shirokai.safari;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

/** Read-only destination snapshot; every click resolves the current destination again. */
public final class RegionsMenu extends AbstractContainerMenu {
  private final ServerPlayer owner;
  private final SafariMod mod;
  private final List<String> names;

  public RegionsMenu(int id, Inventory inventory, SafariMod mod) {
    super(MenuType.GENERIC_9x3, id);
    this.owner = (ServerPlayer) inventory.player;
    this.mod = mod;
    names = List.copyOf(mod.config.regions.keySet());
    var chest = new SimpleContainer(27);
    for (int i = 0; i < 27; i++) addSlot(new Slot(chest, i, 8 + i % 9 * 18, 18 + i / 9 * 18));
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 85 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 143));
    for (int i = 0; i < names.size(); i++) button(chest, i, Items.COMPASS, names.get(i));
    button(chest, 22, Items.OAK_DOOR, "Voltar ao Safari");
  }

  private void button(SimpleContainer chest, int slot, Item item, String text) {
    var stack = new ItemStack(item);
    stack.set(DataComponents.CUSTOM_NAME, Component.literal(text));
    chest.setItem(slot, stack);
  }

  @Override
  public void clicked(int slot, int button, ClickType type, Player player) {
    if (!stillValid(player) || button != 0 || type != ClickType.PICKUP) return;
    try {
      if (slot == 22) mod.menu(owner);
      else if (slot >= 0 && slot < names.size()) mod.teleportRegion(owner, names.get(slot));
    } catch (RuntimeException e) {
      mod.tell(owner, e.getMessage());
    }
  }

  @Override
  public boolean stillValid(Player player) {
    return player == owner && mod.canTravel(owner);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int slot) {
    return ItemStack.EMPTY;
  }
}
