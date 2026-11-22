package dev.shirokai.safari;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;

public final class AdminMenu extends AbstractContainerMenu {
  private final ServerPlayer owner;
  private final SafariMod mod;
  private final SimpleContainer chest = new SimpleContainer(27);
  private int ticks;

  public AdminMenu(int id, Inventory inventory, SafariMod mod) {
    super(MenuType.GENERIC_9x3, id);
    owner = (ServerPlayer) inventory.player;
    this.mod = mod;
    for (int i = 0; i < 27; i++) addSlot(new Slot(chest, i, 8 + i % 9 * 18, 18 + i / 9 * 18));
    for (int r = 0; r < 3; r++)
      for (int c = 0; c < 9; c++)
        addSlot(new Slot(inventory, c + r * 9 + 9, 8 + c * 18, 85 + r * 18));
    for (int c = 0; c < 9; c++) addSlot(new Slot(inventory, c, 8 + c * 18, 143));
    refresh();
  }

  private void item(int slot, Item item, String title, String... lines) {
    var stack = new ItemStack(item);
    stack.set(DataComponents.CUSTOM_NAME, ColorText.parse(title));
    stack.set(
        DataComponents.LORE, new ItemLore(Arrays.stream(lines).map(ColorText::parse).toList()));
    chest.setItem(slot, stack);
  }

  private void refresh() {
    var event = mod.data.event;
    long now = System.currentTimeMillis();
    boolean running = event.running(now);
    item(
        10,
        Items.NETHER_STAR,
        "&d&lEvento",
        running ? "&aATIVO" : "&7Nenhum evento ativo",
        mod.eventEffects(),
        "Restante: " + mod.format(Math.max(0, event.ends - now)),
        "Afeta novos spawns, não Pokémon existentes.");
    double effective = Math.min(10, mod.config.spawnMultiplier * (running ? event.spawn : 1));
    var timers = SpawnMetrics.recentTimers();
    String observed =
        timers.isEmpty()
            ? "Sem amostra recente de visitante"
            : "Última amostra: "
                + timers.getLast().base()
                + " → "
                + timers.getLast().effective()
                + " por tick";
    item(
        12,
        Items.SPAWNER,
        "&b&lSpawn",
        "Base: " + mod.config.spawnMultiplier + "x | evento: " + (running ? event.spawn : 1) + "x",
        "Total configurado: " + effective + "x",
        observed,
        "Tentativas na última janela de 60s: " + SpawnMetrics.attempts(),
        "Pokémon gerados na última janela de 60s: " + SpawnMetrics.spawns(),
        "Limites de população, bioma e condições continuam valendo.");
    long visitors = mod.server.getPlayerList().getPlayers().stream().filter(mod::active).count();
    item(
        14,
        Items.PLAYER_HEAD,
        "&a&lVisitantes",
        Long.toString(visitors) + " ativos",
        "Janela diária: " + mod.format(mod.config.cycleSeconds * 1000));
    var area = mod.config.area;
    item(
        16,
        Items.COMPASS,
        "&e&lÁrea e destinos",
        area == null ? "Área não criada" : area.dimension(),
        area == null
            ? "Use pos1, pos2 e criar"
            : area.minX()
                + ","
                + area.minY()
                + ","
                + area.minZ()
                + " → "
                + area.maxX()
                + ","
                + area.maxY()
                + ","
                + area.maxZ(),
        "Entrada: " + (mod.config.entrance == null ? "NÃO definida" : "definida"),
        "Saída: " + (mod.config.lobby == null ? "NÃO definida" : "definida"));
    var cleanup = ClearBridge.status();
    item(
        18,
        Items.BUCKET,
        "&a&lLimpeza do Safari",
        cleanup.getOrDefault("status", "indisponível"),
        "Próxima: "
            + cleanup.getOrDefault("next", "-")
            + "s | intervalo: "
            + cleanup.getOrDefault("interval", "-")
            + "s",
        "Última: "
            + cleanup.getOrDefault("items", "0")
            + " itens, "
            + cleanup.getOrDefault("pokemon", "0")
            + " Pokémon",
        "Contagem e mensagens independentes da limpeza global.");
    item(
        20,
        Items.EMERALD,
        "&6&lLoja",
        mod.config.ballBundle + " Safari Balls por " + mod.config.bundlePrice.toPlainString(),
        "Moeda: " + mod.config.currency,
        "/safariadmin economia NomeDoPlayer");
    item(
        22,
        Items.BOOK,
        "&b&lComandos e ajuda",
        "Clique para enviar todos os comandos ao chat.",
        "Este menu consulta os dados; não muda eventos ao clicar.");
    item(
        24,
        Items.CLOCK,
        "&f&lAtualizar",
        "Atualização automática e por clique.",
        "Sem visitantes, não há aceleração a medir.");
  }

  @Override
  public void broadcastChanges() {
    if (++ticks % 20 == 0 && stillValid(owner)) refresh();
    super.broadcastChanges();
  }

  @Override
  public boolean stillValid(Player p) {
    return p == owner && p.isAlive() && Permissions.admin(owner.createCommandSourceStack());
  }

  @Override
  public void clicked(int slot, int button, ClickType type, Player p) {
    if (!stillValid(p)) {
      owner.closeContainer();
      return;
    }
    if (type != ClickType.PICKUP || button != 0) return;
    if (slot == 22) {
      owner.closeContainer();
      AdminHelp.show(owner.createCommandSourceStack());
    } else refresh();
  }

  @Override
  public ItemStack quickMoveStack(Player p, int slot) {
    return ItemStack.EMPTY;
  }
}
