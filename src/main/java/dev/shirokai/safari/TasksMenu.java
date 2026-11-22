package dev.shirokai.safari;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/** Two independent screens; daily/weekly tabs live inside the missions screen. */
public final class TasksMenu extends AbstractContainerMenu {
  private final Tasks tasks;
  private final ServerPlayer owner;
  private final boolean shop;
  private final TasksConfig.Period period;
  private final int page;
  private final TasksConfig.Layout layout;
  private final TasksConfig.Menus menusSnapshot;
  private final TasksConfig.Missions missionsSnapshot;
  private final TasksConfig.Shop shopSnapshot;
  private final String periodKey;
  private final SimpleContainer chest;
  private final Map<Integer, String> actions = new HashMap<>();
  private String confirmId;
  private long confirmUntil;
  private int pages;

  public TasksMenu(
      int id, Inventory inventory, Tasks tasks, boolean shop, TasksConfig.Period period, int page) {
    super(menuType((shop ? tasks.menus.shop : tasks.menus.missions).rows), id);
    this.tasks = tasks;
    owner = (ServerPlayer) inventory.player;
    this.shop = shop;
    this.period = period;
    this.page = Math.max(0, page);
    layout = shop ? tasks.menus.shop : tasks.menus.missions;
    menusSnapshot = tasks.menus;
    missionsSnapshot = tasks.missions;
    shopSnapshot = tasks.shop;
    periodKey = tasks.missions.period(period, System.currentTimeMillis());
    chest = new SimpleContainer(layout.rows * 9);
    for (int i = 0; i < chest.getContainerSize(); i++)
      addSlot(new Slot(chest, i, 8 + i % 9 * 18, 18 + i / 9 * 18));
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(
            new Slot(
                inventory, col + row * 9 + 9, 8 + col * 18, 18 + layout.rows * 18 + 14 + row * 18));
    for (int col = 0; col < 9; col++)
      addSlot(new Slot(inventory, col, 8 + col * 18, 18 + layout.rows * 18 + 72));
    render();
  }

  private static MenuType<?> menuType(int rows) {
    return switch (rows) {
      case 3 -> MenuType.GENERIC_9x3;
      case 4 -> MenuType.GENERIC_9x4;
      case 5 -> MenuType.GENERIC_9x5;
      case 6 -> MenuType.GENERIC_9x6;
      default -> throw new IllegalArgumentException("Linhas inválidas");
    };
  }

  private void set(int slot, String icon, String name, List<String> lore) {
    var stack = tasks.icon(icon);
    stack.set(DataComponents.CUSTOM_NAME, ColorText.parse(name));
    stack.set(DataComponents.LORE, new ItemLore(lore.stream().map(ColorText::parse).toList()));
    chest.setItem(slot, stack);
  }

  private String template(String text, Map<String, String> values) {
    for (var e : values.entrySet()) text = text.replace("{" + e.getKey() + "}", e.getValue());
    return text;
  }

  private List<String> lore(
      List<String> source, List<String> description, Map<String, String> values) {
    var result = new ArrayList<String>();
    for (var line : source)
      if (line.equals("{description}")) {
        for (var d : description) result.add(template(d, values));
      } else result.add(template(line, values));
    return result;
  }

  private Map<String, String> base() {
    var state = tasks.player(owner);
    var values = new HashMap<String, String>();
    values.put("points", Long.toString(state.points));
    values.put(
        "reset",
        SafariMod.INSTANCE.format(
            tasks.missions.next(period, System.currentTimeMillis()) - System.currentTimeMillis()));
    return values;
  }

  private void render() {
    actions.clear();
    var values = base();
    var cfg = tasks.menus;
    var state = tasks.player(owner);
    long now = System.currentTimeMillis();
    for (int i = 0; i < chest.getContainerSize(); i++) set(i, layout.filler, " ", List.of());
    var ids =
        shop
            ? tasks.shop.products.stream().filter(p -> p.enabled).map(p -> p.id).toList()
            : tasks.missions.missions.stream()
                .filter(m -> m.enabled && m.period == period)
                .map(m -> m.id)
                .toList();
    pages = Math.max(1, (ids.size() + layout.contentSlots.size() - 1) / layout.contentSlots.size());
    int start = Math.min(page, pages - 1) * layout.contentSlots.size();
    for (int i = 0; i < layout.contentSlots.size(); i++) {
      int index = start + i, slot = layout.contentSlots.get(i);
      chest.setItem(slot, ItemStack.EMPTY);
      if (index >= ids.size()) continue;
      String id = ids.get(index);
      actions.put(slot, id);
      var text = new HashMap<>(values);
      if (shop) {
        var product =
            tasks.shop.products.stream().filter(p -> p.id.equals(id)).findFirst().orElseThrow();
        text.put("name", product.name);
        text.put("cost", Integer.toString(product.cost));
        text.put("limit", Integer.toString(product.limit));
        text.put(
            "purchased",
            Integer.toString(
                state.purchases.getOrDefault(
                    tasks.missions.limit(product.limitPeriod, now) + id, 0)));
        text.put("status", id.equals(confirmId) && now < confirmUntil ? cfg.confirm : cfg.buy);
        set(
            slot,
            product.icon,
            template(product.name, text),
            lore(cfg.productLore, product.description, text));
      } else {
        var m =
            tasks.missions.missions.stream()
                .filter(def -> def.id.equals(id))
                .findFirst()
                .orElseThrow();
        var progress = TasksState.progress(state, tasks.missions, m, now);
        text.put("name", m.name);
        text.put("progress", Integer.toString(progress.value));
        text.put("target", Integer.toString(m.target));
        text.put("reward_points", Integer.toString(m.reward.points));
        text.put(
            "status",
            state.claimed.contains(TasksState.key(tasks.missions, m, now))
                ? cfg.claimed
                : progress.value >= m.target ? cfg.claim : cfg.incomplete);
        set(slot, m.icon, template(m.name, text), lore(cfg.missionLore, m.description, text));
      }
    }
    if (ids.isEmpty()) set(layout.emptySlot, layout.emptyIcon, cfg.empty, List.of());
    if (!shop) {
      set(layout.dailySlot, layout.dailyIcon, cfg.daily, List.of());
      set(layout.weeklySlot, layout.weeklyIcon, cfg.weekly, List.of());
      set(layout.resetSlot, layout.resetIcon, template(cfg.reset, values), List.of());
    }
    set(layout.balanceSlot, layout.balanceIcon, template(cfg.balance, values), List.of());
    set(layout.previousSlot, layout.previousIcon, cfg.previous, List.of());
    set(layout.nextSlot, layout.nextIcon, cfg.next, List.of());
    set(layout.closeSlot, layout.closeIcon, cfg.close, List.of());
    broadcastChanges();
  }

  @Override
  public void clicked(int slot, int button, ClickType type, Player player) {
    if (!stillValid(player)
        || owner.containerMenu != this
        || button != 0
        || type != ClickType.PICKUP) return;
    try {
      if (tasks.menus != menusSnapshot
          || tasks.missions != missionsSnapshot
          || tasks.shop != shopSnapshot
          || !tasks.missions.period(period, System.currentTimeMillis()).equals(periodKey)) {
        tasks.page(owner, shop, period, 0);
        return;
      }
      if (slot == layout.closeSlot) {
        SafariMod.closeMenuIfOpen(owner);
        return;
      }
      if (!shop && slot == layout.dailySlot) {
        tasks.page(owner, false, TasksConfig.Period.DAILY, 0);
        return;
      }
      if (!shop && slot == layout.weeklySlot) {
        tasks.page(owner, false, TasksConfig.Period.WEEKLY, 0);
        return;
      }
      if (slot == layout.previousSlot) {
        tasks.page(owner, shop, period, Math.max(0, page - 1));
        return;
      }
      if (slot == layout.nextSlot) {
        tasks.page(owner, shop, period, Math.min(pages - 1, page + 1));
        return;
      }
      if (slot == layout.balanceSlot || slot == layout.resetSlot) {
        render();
        return;
      }
      var id = actions.get(slot);
      if (id == null) return;
      if (shop) {
        if (!id.equals(confirmId) || System.currentTimeMillis() >= confirmUntil) {
          confirmId = id;
          confirmUntil = System.currentTimeMillis() + 15000;
          render();
          return;
        }
        confirmId = null;
        tasks.buy(owner, id);
      } else tasks.claim(owner, id);
      render();
    } catch (RuntimeException e) {
      SafariMod.INSTANCE.tell(owner, e.getMessage());
    }
  }

  @Override
  public boolean stillValid(Player player) {
    return player == owner
        && owner.isAlive()
        && !owner.isSpectator()
        && tasks.healthy()
        && Permissions.has(owner, "shirokai.safari.use", true);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int slot) {
    return ItemStack.EMPTY;
  }
}
