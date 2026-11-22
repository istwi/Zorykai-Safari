package dev.shirokai.safari;

import java.util.*;

public final class TasksState {
  public int schemaVersion = 1;
  public Map<String, Player> players = new LinkedHashMap<>();

  public static final class Player {
    public long points;
    public Map<String, Progress> progress = new LinkedHashMap<>();
    public Set<String> claimed = new HashSet<>();
    public Map<String, Integer> purchases = new LinkedHashMap<>();
    public Receipt pending;

    public void validate() {
      if (points < 0
          || points > 2_000_000_000L
          || progress == null
          || claimed == null
          || purchases == null) throw new IllegalArgumentException("Dados de missões inválidos");
      for (var p : progress.values())
        if (p == null
            || p.value < 0
            || p.value > 10000
            || p.fingerprint == null
            || p.distinct == null
            || p.distinct.size() > 10000) throw new IllegalArgumentException("Progresso inválido");
      for (var n : purchases.values())
        if (n == null || n < 0 || n > 1000) throw new IllegalArgumentException("Compras inválidas");
      if (pending != null
          && (pending.id == null
              || pending.key == null
              || pending.reward == null
              || !java.util.Set.of("MISSION", "SHOP").contains(pending.kind)
              || Math.abs(pending.pointsDelta) > 1000000))
        throw new IllegalArgumentException("Recibo inválido");
    }
  }

  public static final class Progress {
    public String fingerprint = "";
    public int value;
    public Set<String> distinct = new HashSet<>();
  }

  public static final class Receipt {
    public String id, key, kind, missionId;
    public long pointsDelta;
    public TasksConfig.Reward reward;
    public boolean daily;
  }

  public static String key(TasksConfig.Missions config, TasksConfig.Mission m, long now) {
    return config.period(m.period, now) + m.id;
  }

  public static Progress progress(
      Player p, TasksConfig.Missions config, TasksConfig.Mission m, long now) {
    var record = p.progress.computeIfAbsent(key(config, m, now), k -> new Progress());
    if (!record.fingerprint.equals(m.fingerprint())) {
      record.fingerprint = m.fingerprint();
      record.value = 0;
      record.distinct.clear();
    }
    return record;
  }

  public static boolean increment(
      Player p, TasksConfig.Missions config, TasksConfig.Mission m, String distinct, long now) {
    var record = progress(p, config, m, now);
    if (record.value >= m.target
        || p.claimed.contains(key(config, m, now))
        || !record.distinct.add(distinct)) return false;
    record.value++;
    return true;
  }

  public static void trim(Player p, TasksConfig.Missions config, long now) {
    String daily = config.period(TasksConfig.Period.DAILY, now),
        weekly = config.period(TasksConfig.Period.WEEKLY, now),
        month = config.limit(TasksConfig.Limit.MONTHLY, now);
    java.util.function.Predicate<String> old =
        k ->
            !k.startsWith(daily)
                && !k.startsWith(weekly)
                && !k.startsWith(month)
                && !k.startsWith("L:")
                && (p.pending == null || !k.equals(p.pending.key));
    p.progress.keySet().removeIf(old);
    p.claimed.removeIf(old);
    p.purchases.keySet().removeIf(old);
  }
}
