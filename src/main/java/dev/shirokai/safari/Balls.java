package dev.shirokai.safari;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class Balls {
  public static final String KEY = "shirokai_safari";

  public static boolean tagged(ItemStack s) {
    return !s.isEmpty()
        && s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains(KEY);
  }

  public static boolean owns(ItemStack s, ServerPlayer p, Visit v) {
    if (!tagged(s)) return false;
    var tag = s.get(DataComponents.CUSTOM_DATA).copyTag().getCompound(KEY);
    return tag.getString("owner").equals(p.getUUID().toString())
        && tag.getLong("cycle") == v.cycleStart;
  }

  public static int count(ServerPlayer p, Visit v) {
    int n = 0;
    for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
      var s = p.getInventory().getItem(i);
      if (owns(s, p, v)) n += s.getCount();
    }
    return n;
  }

  public static void remove(ServerPlayer p) {
    for (int i = 0; i < p.getInventory().getContainerSize(); i++)
      if (tagged(p.getInventory().getItem(i))) p.getInventory().setItem(i, ItemStack.EMPTY);
    if (tagged(p.containerMenu.getCarried())) p.containerMenu.setCarried(ItemStack.EMPTY);
    p.inventoryMenu.broadcastChanges();
  }

  public static boolean empty(ServerPlayer p) {
    return p.getInventory().isEmpty() && p.containerMenu.getCarried().isEmpty();
  }

  public static void give(ServerPlayer p, Visit v, int amount, Config config) {
    var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(config.ballItem));
    while (amount > 0) {
      int n = Math.min(amount, item.getDefaultMaxStackSize());
      var stack = new ItemStack(item, n);
      var data = new CompoundTag();
      var tag = new CompoundTag();
      tag.putString("owner", p.getUUID().toString());
      tag.putLong("cycle", v.cycleStart);
      data.put(KEY, tag);
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
      stack.set(DataComponents.CUSTOM_NAME, Component.literal("Safari Ball • uso exclusivo"));
      if (!p.getInventory().add(stack)) throw new IllegalStateException("Inventário sem espaço");
      amount -= n;
    }
    equip(p, v);
    p.inventoryMenu.broadcastChanges();
  }

  public static boolean room(ServerPlayer p, int amount, Config config) {
    int room = 0;
    for (int i = 0; i < 36; i++) {
      var s = p.getInventory().getItem(i);
      if (s.isEmpty()) room += 64;
      else if (SafariMod.INSTANCE != null && owns(s, p, SafariMod.INSTANCE.visit(p)))
        room += Math.max(0, s.getMaxStackSize() - s.getCount());
    }
    return room >= amount;
  }

  public static boolean equip(ServerPlayer p, Visit v) {
    int selected = p.getInventory().selected;
    if (owns(p.getInventory().getItem(selected), p, v)) return true;
    for (int i = 0; i < 36; i++)
      if (owns(p.getInventory().getItem(i), p, v)) {
        var old = p.getInventory().getItem(selected);
        p.getInventory().setItem(selected, p.getInventory().getItem(i));
        p.getInventory().setItem(i, old);
        p.inventoryMenu.broadcastChanges();
        return true;
      }
    return false;
  }
}
