package dev.shirokai.safari;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Empty catalogs by default: examples belong in documentation, never enabled automatically. */
public final class TasksConfig {
  public enum Period {
    DAILY,
    WEEKLY
  }

  public enum Objective {
    CAPTURE,
    UNIQUE_SPECIES,
    DEFEAT,
    VISIT_REGIONS,
    DAILY_COMPLETIONS
  }

  public enum Limit {
    DAILY,
    WEEKLY,
    MONTHLY,
    LIFETIME
  }

  public static final class Missions {
    public int schemaVersion = 1;
    public String timezone = "America/Sao_Paulo", weeklyResetDay = "MONDAY";
    public int resetHour = 0;
    public List<Mission> missions = new ArrayList<>();

    public void validate() {
      if (schemaVersion != 1
          || resetHour < 0
          || resetHour > 23
          || missions == null
          || missions.size() > 100)
        throw new IllegalArgumentException("Configuração de missões inválida");
      ZoneId.of(timezone);
      DayOfWeek.valueOf(weeklyResetDay);
      var ids = new HashSet<String>();
      for (var m : missions) {
        if (m == null
            || !id(m.id)
            || !ids.add(m.id)
            || m.period == null
            || m.objective == null
            || m.target < 1
            || m.target > 10000
            || m.regionRadius < 1
            || m.regionRadius > 64
            || m.types == null
            || m.species == null
            || m.forms == null
            || m.regions == null) throw new IllegalArgumentException("Missão inválida/duplicada");
        text(m.name);
        texts(m.description);
        text(m.icon);
        for (var list : List.of(m.types, m.species, m.forms, m.regions)) {
          if (list.size() > 100) throw new IllegalArgumentException("Filtros demais");
          for (var entry : list)
            if (entry == null || !entry.matches("[a-z0-9_:/.-]{1,100}"))
              throw new IllegalArgumentException("Filtro inválido");
        }
        m.reward.validate();
      }
    }

    public LocalDate date(long now) {
      var instant = Instant.ofEpochMilli(now);
      var zone = ZoneId.of(timezone);
      var date = instant.atZone(zone).toLocalDate();
      return instant.isBefore(date.atTime(resetHour, 0).atZone(zone).toInstant())
          ? date.minusDays(1)
          : date;
    }

    public String period(Period period, long now) {
      var date = date(now);
      return (period == Period.DAILY
              ? "D:" + date
              : "W:"
                  + date.with(TemporalAdjusters.previousOrSame(DayOfWeek.valueOf(weeklyResetDay))))
          + ":";
    }

    public long next(Period period, long now) {
      var date = date(now);
      if (period == Period.DAILY) date = date.plusDays(1);
      else date = date.with(TemporalAdjusters.next(DayOfWeek.valueOf(weeklyResetDay)));
      return date.atTime(resetHour, 0).atZone(ZoneId.of(timezone)).toInstant().toEpochMilli();
    }

    public String limit(Limit limit, long now) {
      return switch (limit) {
        case DAILY -> period(Period.DAILY, now);
        case WEEKLY -> period(Period.WEEKLY, now);
        case MONTHLY -> "M:" + date(now).withDayOfMonth(1) + ":";
        case LIFETIME -> "L:";
      };
    }
  }

  public static final class Mission {
    public String id = "", name = "", icon = "minecraft:book";
    public boolean enabled = true;
    public Period period = Period.DAILY;
    public Objective objective = Objective.CAPTURE;
    public int target = 1, regionRadius = 6;
    public List<String> description = new ArrayList<>(),
        types = new ArrayList<>(),
        species = new ArrayList<>(),
        forms = new ArrayList<>(),
        regions = new ArrayList<>();
    public Reward reward = new Reward();

    public String fingerprint() {
      return objective
          + ":"
          + target
          + ":"
          + types
          + ":"
          + species
          + ":"
          + forms
          + ":"
          + regions
          + ":"
          + regionRadius;
    }
  }

  public static final class Reward {
    public int points = 0;
    public List<GiveItem> items = new ArrayList<>();
    public List<String> commands = new ArrayList<>();

    public void validate() {
      if (points < 0
          || points > 1000000
          || items == null
          || items.size() > 20
          || commands == null
          || commands.size() > 20) throw new IllegalArgumentException("Recompensa inválida");
      for (var i : items)
        if (i == null
            || i.item == null
            || !i.item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
            || i.amount < 1
            || i.amount > 2304) throw new IllegalArgumentException("Item de recompensa inválido");
      for (String command : commands) {
        if (command == null
            || command.isBlank()
            || command.startsWith("/")
            || command.length() > 1000
            || command.contains("\n")
            || command.contains("\r"))
          throw new IllegalArgumentException("Comando de recompensa inválido");
        var matcher = java.util.regex.Pattern.compile("\\{([^{}]+)}").matcher(command);
        while (matcher.find())
          if (!Set.of("player", "uuid").contains(matcher.group(1)))
            throw new IllegalArgumentException("Placeholder de comando inválido");
      }
    }

    public boolean external() {
      return !items.isEmpty() || !commands.isEmpty();
    }
  }

  public static final class GiveItem {
    public String item = "minecraft:diamond";
    public int amount = 1;
  }

  public static final class Shop {
    public int schemaVersion = 1;
    public List<Product> products = new ArrayList<>();

    public void validate() {
      if (schemaVersion != 1 || products == null || products.size() > 100)
        throw new IllegalArgumentException("Loja inválida");
      var ids = new HashSet<String>();
      for (var p : products) {
        if (p == null
            || !id(p.id)
            || !ids.add(p.id)
            || p.cost < 1
            || p.cost > 1000000
            || p.limit < 1
            || p.limit > 1000
            || p.limitPeriod == null)
          throw new IllegalArgumentException("Produto inválido/duplicado");
        text(p.name);
        text(p.icon);
        texts(p.description);
        p.reward.validate();
        if (p.reward.points != 0 || !p.reward.external())
          throw new IllegalArgumentException(
              "Produto precisa entregar itens/comandos, sem gerar pontos");
      }
    }
  }

  public static final class Product {
    public String id = "", name = "", icon = "minecraft:emerald";
    public boolean enabled = true;
    public List<String> description = new ArrayList<>();
    public int cost = 100, limit = 1;
    public Limit limitPeriod = Limit.WEEKLY;
    public Reward reward = new Reward();
  }

  public static final class Menus {
    public int schemaVersion = 1;
    public Layout missions = new Layout("&2&lMissões Safari"), shop = new Layout("&6&lLoja Safari");
    public String daily = "&eDiárias",
        weekly = "&dSemanais",
        previous = "&7Anterior",
        next = "&7Próxima",
        close = "&cFechar",
        balance = "&aPontos: &f{points}",
        reset = "&7Renova em: &f{reset}",
        empty = "&7Nenhuma opção configurada",
        claim = "&aClique para resgatar",
        claimed = "&7Resgatada",
        incomplete = "&eEm andamento",
        confirm = "&aClique novamente para confirmar",
        buy = "&eClique para selecionar";
    public List<String> missionLore =
        new ArrayList<>(
            List.of(
                "{description}",
                "&7Progresso: &f{progress}/{target}",
                "&7Pontos: &a{reward_points}",
                "{status}"));
    public List<String> productLore =
        new ArrayList<>(
            List.of(
                "{description}",
                "&7Preço: &a{cost} pontos",
                "&7Compras: &f{purchased}/{limit}",
                "{status}"));

    public void validate() {
      if (schemaVersion != 1 || missions == null || shop == null)
        throw new IllegalArgumentException("Menus inválidos");
      missions.validate();
      shop.validate();
      texts(missionLore);
      texts(productLore);
      for (var s :
          List.of(
              daily,
              weekly,
              previous,
              next,
              close,
              balance,
              reset,
              empty,
              claim,
              claimed,
              incomplete,
              confirm,
              buy)) text(s);
    }
  }

  public static final class Layout {
    public String title;
    public int rows = 6,
        dailySlot = 45,
        weeklySlot = 46,
        previousSlot = 48,
        balanceSlot = 49,
        resetSlot = 50,
        nextSlot = 51,
        closeSlot = 53,
        emptySlot = 22;
    public String filler = "minecraft:gray_stained_glass_pane",
        dailyIcon = "minecraft:sunflower",
        weeklyIcon = "minecraft:clock",
        balanceIcon = "minecraft:emerald",
        emptyIcon = "minecraft:barrier",
        previousIcon = "minecraft:arrow",
        nextIcon = "minecraft:arrow",
        closeIcon = "minecraft:barrier",
        resetIcon = "minecraft:clock";
    public List<Integer> contentSlots = new ArrayList<>();

    public Layout() {
      this("");
    }

    public Layout(String title) {
      this.title = title;
      for (int i = 0; i < 45; i++) contentSlots.add(i);
    }

    public void validate() {
      text(title);
      for (var id :
          List.of(
              filler,
              dailyIcon,
              weeklyIcon,
              balanceIcon,
              emptyIcon,
              previousIcon,
              nextIcon,
              closeIcon,
              resetIcon)) text(id);
      if (rows < 3
          || rows > 6
          || contentSlots == null
          || contentSlots.isEmpty()
          || contentSlots.size() > 45) throw new IllegalArgumentException("Layout inválido");
      var seen = new HashSet<Integer>();
      for (int slot :
          new int[] {
            dailySlot, weeklySlot, previousSlot, balanceSlot, resetSlot, nextSlot, closeSlot
          })
        if (slot < 0 || slot >= rows * 9 || !seen.add(slot))
          throw new IllegalArgumentException("Botões sobrepostos/fora do menu");
      for (var slot : contentSlots)
        if (slot == null || slot < 0 || slot >= rows * 9 || !seen.add(slot))
          throw new IllegalArgumentException("Slots de conteúdo sobrepostos/fora do menu");
      if (emptySlot < 0 || emptySlot >= rows * 9 || !contentSlots.contains(emptySlot))
        throw new IllegalArgumentException("emptySlot precisa ser um slot de conteúdo");
    }
  }

  public static boolean id(String s) {
    return s != null && s.matches("[a-z0-9_-]{1,48}");
  }

  public static void text(String s) {
    if (s == null || s.length() > 2000) throw new IllegalArgumentException("Texto inválido");
  }

  public static void texts(List<String> s) {
    if (s == null || s.size() > 40) throw new IllegalArgumentException("Lista de textos inválida");
    for (var line : s) text(line);
  }
}
