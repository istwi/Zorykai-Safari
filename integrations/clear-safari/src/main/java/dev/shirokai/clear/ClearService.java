package dev.shirokai.clear;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.slf4j.Logger;

public final class ClearService {
  private final MinecraftServer server;
  private final Logger log;
  private final LoadedIndex<Entity> index = new LoadedIndex<>();
  private ClearConfig config, safariConfig;
  private long ticks, next, safariNext;
  private boolean paused, safariFirst, bridgeWarning;
  private Job job, safariJob;
  private Result last = new Result(0, 0, false), lastSafari = new Result(0, 0, false);

  public ClearService(MinecraftServer server, Logger log, ClearConfig config) {
    this.server = server;
    this.log = log;
    this.config = config;
    this.safariConfig = config.safariPolicy();
    resetDeadline();
  }

  public void loaded(Entity e) {
    if (e instanceof ItemEntity || e instanceof PokemonEntity) index.add(e);
  }

  public void unloaded(Entity e) {
    index.remove(e);
  }

  public void stop() {
    job = null;
    safariJob = null;
    index.clear();
  }

  public ClearConfig config() {
    return config;
  }

  public void reload(ClearConfig value) {
    config = value;
    safariConfig = value.safariPolicy();
    resetDeadline();
  }

  public void pause() {
    paused = true;
  }

  public void resume() {
    paused = false;
    resetDeadline();
  }

  private void resetDeadline() {
    next = ticks + config.intervalSeconds * 20L;
    safariNext = ticks + config.safari.intervalSeconds * 20L;
  }

  public long remainingSeconds() {
    return Math.max(0, (next - ticks + 19) / 20);
  }

  public long safariRemainingSeconds() {
    return Math.max(0, (safariNext - ticks + 19) / 20);
  }

  public boolean paused() {
    return paused;
  }

  public boolean busy() {
    return job != null || safariJob != null;
  }

  public boolean safariBusy() {
    return safariJob != null;
  }

  public Result last() {
    return last;
  }

  public Result lastSafari() {
    return lastSafari;
  }

  public boolean start(Mode mode, boolean preview) {
    return start(mode, preview, null);
  }

  public boolean start(Mode mode, boolean preview, Consumer<Result> completion) {
    return startJob(mode, preview, completion, false);
  }

  public boolean startSafari(Mode mode, boolean preview, Consumer<Result> completion) {
    return startJob(mode, preview, completion, true);
  }

  private boolean startJob(
      Mode mode, boolean preview, Consumer<Result> completion, boolean safari) {
    if (!server.isSameThread()) throw new IllegalStateException("Thread incorreta");
    if (safari ? safariJob != null : job != null) return false;
    var area = config.safari.enabled ? SafariArea.current() : null;
    if (safari && area == null) return false;
    var active = new Job(index.boundary(), mode, preview, safari, safari ? area : null);
    active.nextBatch = ticks;
    active.completion = completion;
    if (safari) safariJob = active;
    else job = active;
    return true;
  }

  public void tick() {
    ++ticks;
    SafariArea area;
    try {
      area = config.safari.enabled ? SafariArea.current() : null;
      bridgeWarning = false;
    } catch (RuntimeException failure) {
      ++next;
      ++safariNext;
      if (!bridgeWarning) {
        bridgeWarning = true;
        log.error("Limpeza suspensa por falha na área do Safari", failure);
      }
      return;
    }
    if (config.enabled && !paused) {
      if (ticks >= next) {
        if (job == null) start(Mode.ALL, false);
        next = ticks + config.intervalSeconds * 20L;
      } else if ((next - ticks) % 20 == 0
          && config.announcements.enabled
          && config.announcements.warningSeconds.contains((int) ((next - ticks) / 20)))
        broadcast(
            config.announcements.warningMessage.replace(
                "{seconds}", Long.toString((next - ticks) / 20)),
            false,
            area);
      if (config.safari.enabled && area != null) {
        if (ticks >= safariNext) {
          if (safariJob == null) startSafari(Mode.ALL, false, null);
          safariNext = ticks + config.safari.intervalSeconds * 20L;
        } else if ((safariNext - ticks) % 20 == 0
            && safariConfig.announcements.enabled
            && safariConfig.announcements.warningSeconds.contains(
                (int) ((safariNext - ticks) / 20)))
          broadcast(
              safariConfig.announcements.warningMessage.replace(
                  "{seconds}", Long.toString((safariNext - ticks) / 20)),
              true,
              area);
      } else ++safariNext;
    } else {
      ++next;
      ++safariNext;
    }
    if (job == null && safariJob == null) return;
    var guard = new BattleGuard();
    int budget = config.performance.maxEntitiesPerBatch;
    if (safariFirst) {
      budget -= batch(safariJob, budget, area, guard);
      batch(job, budget, area, guard);
    } else {
      budget -= batch(job, budget, area, guard);
      batch(safariJob, budget, area, guard);
    }
    safariFirst = !safariFirst;
  }

  private int batch(Job active, int budget, SafariArea current, BattleGuard battles) {
    if (active == null || budget <= 0 || ticks < active.nextBatch) return 0;
    active.nextBatch = ticks + config.performance.batchIntervalTicks;
    int processed = 0;
    for (; processed < budget; processed++) {
      var entry = index.after(active.cursor);
      if (entry == null || entry.getKey() > active.boundary) {
        finish(active, current);
        return processed;
      }
      active.cursor = entry.getKey();
      var entity = entry.getValue();
      boolean item = entity instanceof ItemEntity;
      if (active.mode == Mode.ITEMS && !item || active.mode == Mode.POKEMON && item) continue;
      // Both paths re-evaluate dimension and geometry immediately before removal.
      if (!scope(active, entity, current)) continue;
      var policy = active.safari ? safariConfig : config;
      String reason = Eligibility.reason(entity, policy, battles);
      if (reason != null) {
        if (config.logging.enabled && config.logging.logProtectedEntities)
          log.info("Protegida {}: {}", entity.getUUID(), reason);
        continue;
      }
      if (!scope(active, entity, current) || Eligibility.reason(entity, policy, battles) != null)
        continue;
      if (!active.preview) entity.discard();
      if (item) active.items++;
      else active.pokemon++;
    }
    return processed;
  }

  private boolean scope(Job job, Entity entity, SafariArea current) {
    return job.safari
        ? current != null && current.equals(job.area) && current.contains(entity)
        : current == null || !current.contains(entity);
  }

  private void finish(Job active, SafariArea current) {
    if (active.safari) safariJob = null;
    else job = null;
    var result = new Result(active.items, active.pokemon, active.preview);
    if (!active.preview) {
      if (active.safari) lastSafari = result;
      else last = result;
    }
    if (config.logging.enabled && config.logging.logSummary)
      log.info(
          "{} {}: itens={}, Pokémon={}",
          active.preview ? "Prévia" : "Limpeza",
          active.safari ? "Safari" : "global",
          active.items,
          active.pokemon);
    if (active.completion != null) active.completion.accept(result);
    var announcements = active.safari ? safariConfig.announcements : config.announcements;
    if (!active.preview && announcements.enabled && announcements.showResults)
      broadcast(
          announcements
              .resultMessage
              .replace("{items}", Long.toString(active.items))
              .replace("{pokemon}", Long.toString(active.pokemon)),
          active.safari,
          current);
  }

  private void broadcast(String message, boolean safari, SafariArea area) {
    for (var player : server.getPlayerList().getPlayers())
      if (safari ? area != null && area.contains(player) : area == null || !area.contains(player))
        player.sendSystemMessage(safari ? LocalText.parse(message) : Component.literal(message));
  }

  public record Result(long items, long pokemon, boolean preview) {}

  private static final class Job {
    final long boundary;
    final Mode mode;
    final boolean preview, safari;
    final SafariArea area;
    long cursor, items, pokemon, nextBatch;
    Consumer<Result> completion;

    Job(long boundary, Mode mode, boolean preview, boolean safari, SafariArea area) {
      this.boundary = boundary;
      this.mode = mode;
      this.preview = preview;
      this.safari = safari;
      this.area = area;
    }
  }

  public enum Mode {
    ALL,
    ITEMS,
    POKEMON
  }
}
