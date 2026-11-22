package dev.shirokai.safari;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

/** Separate durable ledger: visit recovery remains usable even when a rewards file is invalid. */
public final class Tasks {
  public static final String NATURAL = "shirokai_safari_natural";
  private final SafariMod mod;
  private final Path root, statePath;
  public TasksConfig.Missions missions;
  public TasksConfig.Shop shop;
  public TasksConfig.Menus menus;
  public TasksState data;
  private boolean healthy;
  private final LinkedHashMap<UUID, List<Pokemon>> defeated = new LinkedHashMap<>();

  public Tasks(SafariMod mod) {
    this.mod = mod;
    root = FabricLoader.getInstance().getConfigDir();
    statePath =
        mod.server
            .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("data/shirokai-safari/missions.json");
    try {
      reload();
      data = Files.exists(statePath) ? Storage.read(statePath, TasksState.class) : new TasksState();
      if (data.schemaVersion != 1 || data.players == null)
        throw new IllegalArgumentException("Estado de missões incompatível");
      for (var e : data.players.entrySet()) {
        UUID.fromString(e.getKey());
        e.getValue().validate();
      }
      healthy = true;
      save();
    } catch (Exception e) {
      healthy = false;
      SafariMod.LOG.error("Missões/loja desativadas; arquivos preservados", e);
    }
  }

  private <T> T config(String name, Class<T> type, T defaults) throws Exception {
    var path = root.resolve(name);
    if (!Files.exists(path)) Storage.write(path, defaults);
    if (Files.size(path) > 1_048_576)
      throw new IllegalArgumentException("JSON maior que 1 MiB: " + name);
    return Storage.read(path, type);
  }

  public void reload() throws Exception {
    var m =
        config(
            "shirokai-safari-missions.json",
            TasksConfig.Missions.class,
            new TasksConfig.Missions());
    var s = config("shirokai-safari-shop.json", TasksConfig.Shop.class, new TasksConfig.Shop());
    var v = config("shirokai-safari-menus.json", TasksConfig.Menus.class, new TasksConfig.Menus());
    m.validate();
    s.validate();
    v.validate();
    for (var def : m.missions) {
      icon(def.icon);
      validateReward(def.reward);
    }
    for (var def : s.products) {
      icon(def.icon);
      validateReward(def.reward);
    }
    for (var layout : List.of(v.missions, v.shop))
      for (var id :
          List.of(
              layout.filler,
              layout.dailyIcon,
              layout.weeklyIcon,
              layout.balanceIcon,
              layout.emptyIcon,
              layout.previousIcon,
              layout.nextIcon,
              layout.closeIcon,
              layout.resetIcon)) icon(id);
    missions = m;
    shop = s;
    menus = v;
  }

  public void reloadAll() throws Exception {
    reload();
    if (!healthy) {
      data = Files.exists(statePath) ? Storage.read(statePath, TasksState.class) : new TasksState();
      if (data.schemaVersion != 1 || data.players == null)
        throw new IllegalArgumentException("Estado incompatível");
      for (var e : data.players.entrySet()) {
        UUID.fromString(e.getKey());
        e.getValue().validate();
      }
      healthy = true;
      save();
    }
  }

  public boolean healthy() {
    return healthy;
  }

  public void check() {
    if (!healthy) throw new IllegalStateException("Missões/loja indisponíveis; consulte os logs");
  }

  public void save() {
    try {
      Storage.write(statePath, data);
    } catch (Exception e) {
      healthy = false;
      throw new IllegalStateException("Falha ao salvar missões; operações suspensas", e);
    }
  }

  public TasksState.Player player(ServerPlayer p) {
    check();
    var result = data.players.computeIfAbsent(p.getUUID().toString(), k -> new TasksState.Player());
    TasksState.trim(result, missions, System.currentTimeMillis());
    return result;
  }

  public ItemStack icon(String id) {
    var key = ResourceLocation.tryParse(id);
    if (key == null
        || !BuiltInRegistries.ITEM.containsKey(key)
        || key.toString().equals("minecraft:air"))
      throw new IllegalArgumentException("Item inexistente: " + id);
    return new ItemStack(BuiltInRegistries.ITEM.get(key));
  }

  private void validateReward(TasksConfig.Reward reward) {
    for (var i : reward.items) icon(i.item);
    for (var c : reward.commands) {
      var command =
          render(c, "SafariTest", UUID.fromString("00000000-0000-0000-0000-000000000001"));
      var parsed =
          mod.server
              .getCommands()
              .getDispatcher()
              .parse(command, mod.server.createCommandSourceStack().withPermission(4));
      if (parsed.getReader().canRead()
          || parsed.getContext().build(command).getLastChild().getCommand() == null)
        throw new IllegalArgumentException("Comando inválido/ausente: " + c);
    }
  }

  public static String render(String command, String player, UUID uuid) {
    if (!player.matches("[A-Za-z0-9_]{1,16}"))
      throw new IllegalArgumentException("Nome de jogador inválido");
    return command.replace("{player}", player).replace("{uuid}", uuid.toString());
  }

  public void open(ServerPlayer p, boolean store) {
    check();
    if (!Permissions.has(p, "shirokai.safari.use", true) || !p.isAlive() || p.isSpectator())
      throw new IllegalStateException("Sem acesso ao menu");
    p.openMenu(
        new SimpleMenuProvider(
            (id, inventory, owner) ->
                new TasksMenu(id, inventory, this, store, TasksConfig.Period.DAILY, 0),
            ColorText.parse((store ? menus.shop : menus.missions).title)));
  }

  public void page(ServerPlayer p, boolean store, TasksConfig.Period period, int page) {
    check();
    p.openMenu(
        new SimpleMenuProvider(
            (id, inventory, owner) -> new TasksMenu(id, inventory, this, store, period, page),
            ColorText.parse((store ? menus.shop : menus.missions).title)));
  }

  private boolean eligible(ServerPlayer p) {
    return healthy
        && mod.active(p)
        && mod.inside(p)
        && mod.visit(p).usable(System.currentTimeMillis());
  }

  public void record(
      ServerPlayer p,
      TasksConfig.Objective objective,
      String eventId,
      String species,
      Set<String> types,
      String form,
      String region) {
    if (!eligible(p)) return;
    recordFor(
        player(p), objective, eventId, species, types, form, region, System.currentTimeMillis());
  }

  private void recordFor(
      TasksState.Player state,
      TasksConfig.Objective objective,
      String eventId,
      String species,
      Set<String> types,
      String form,
      String region,
      long now) {
    boolean changed = false;
    for (var m : missions.missions) {
      if (!m.enabled
          || !(m.objective == objective
              || (objective == TasksConfig.Objective.CAPTURE
                  && m.objective == TasksConfig.Objective.UNIQUE_SPECIES))) continue;
      if (!m.species.isEmpty() && !m.species.contains(species)) continue;
      if (!m.types.isEmpty() && Collections.disjoint(m.types, types)) continue;
      if (!m.forms.isEmpty() && !m.forms.contains(form)) continue;
      if (!m.regions.isEmpty() && !m.regions.contains(region)) continue;
      String distinct =
          m.objective == TasksConfig.Objective.UNIQUE_SPECIES
              ? species
              : objective == TasksConfig.Objective.VISIT_REGIONS ? region : eventId;
      changed |= TasksState.increment(state, missions, m, distinct, now);
    }
    if (changed) save();
  }

  public void pokemon(ServerPlayer p, TasksConfig.Objective objective, Pokemon pokemon) {
    if (!pokemon.getPersistentData().getBoolean(NATURAL)) return;
    var types = new HashSet<String>();
    types.add(pokemon.getPrimaryType().getName().toLowerCase(java.util.Locale.ROOT));
    if (pokemon.getSecondaryType() != null)
      types.add(pokemon.getSecondaryType().getName().toLowerCase(java.util.Locale.ROOT));
    record(
        p,
        objective,
        pokemon.getUuid().toString(),
        pokemon.getSpecies().getResourceIdentifier().toString(),
        types,
        pokemon.getForm().getName().toLowerCase(java.util.Locale.ROOT),
        nearest(p, 64));
  }

  private String nearest(ServerPlayer p, int radius) {
    String best = "";
    double distance = radius * radius;
    for (var e : mod.config.regions.entrySet()) {
      var d = e.getValue();
      if (!d.dimension().equals(p.serverLevel().dimension().location().toString())) continue;
      double n = p.position().distanceToSqr(d.x(), d.y(), d.z());
      if (n <= distance) {
        distance = n;
        best = e.getKey();
      }
    }
    return best;
  }

  public void tick(ServerPlayer p) {
    if (!eligible(p) || missions.missions.isEmpty()) return;
    var state = player(p);
    boolean changed = false;
    long now = System.currentTimeMillis();
    for (var m : missions.missions)
      if (m.enabled && m.objective == TasksConfig.Objective.VISIT_REGIONS) {
        for (var e : mod.config.regions.entrySet()) {
          var d = e.getValue();
          if (!m.regions.isEmpty() && !m.regions.contains(e.getKey())) continue;
          if (d.dimension().equals(p.serverLevel().dimension().location().toString())
              && p.position().distanceToSqr(d.x(), d.y(), d.z()) <= m.regionRadius * m.regionRadius)
            changed |= TasksState.increment(state, missions, m, e.getKey(), now);
        }
      }
    if (changed) save();
  }

  private void guard(Runnable action) {
    if (!healthy) return;
    try {
      action.run();
    } catch (RuntimeException e) {
      healthy = false;
      SafariMod.LOG.error("Progresso de missões suspenso; Safari preservado", e);
    }
  }

  private void dispatch(Runnable action) {
    mod.server.execute(() -> guard(action));
  }

  public void safeTick(ServerPlayer p) {
    guard(() -> tick(p));
  }

  public void register() {
    CobblemonEvents.POKEMON_CAPTURED.subscribe(
        event ->
            dispatch(
                () -> {
                  var p = event.getPlayer();
                  var ball = event.getPokeBallEntity();
                  if (ball != null
                      && ball.level() instanceof net.minecraft.server.level.ServerLevel world
                      && ball.getTags().contains(SingleUseBalls.TAG)
                      && mod.inside(world, ball.getX(), ball.getY(), ball.getZ()))
                    pokemon(p, TasksConfig.Objective.CAPTURE, event.getPokemon());
                }));
    CobblemonEvents.BATTLE_FAINTED.subscribe(
        event ->
            dispatch(
                () -> {
                  if (!healthy
                      || missions.missions.stream()
                          .noneMatch(m -> m.enabled && m.objective == TasksConfig.Objective.DEFEAT))
                    return;
                  var killed = event.getKilled();
                  var target = killed.getOriginalPokemon();
                  var entity = target.getEntity() != null ? target.getEntity() : killed.getEntity();
                  if (killed.getActor() instanceof PokemonBattleActor
                      && target.getOwnerUUID() == null
                      && target.getPersistentData().getBoolean(NATURAL)
                      && entity != null
                      && entity.level() instanceof net.minecraft.server.level.ServerLevel world
                      && mod.inside(world, entity.getX(), entity.getY(), entity.getZ())) {
                    defeated
                        .computeIfAbsent(event.getBattle().getBattleId(), k -> new ArrayList<>())
                        .add(target);
                    if (defeated.size() > 512) defeated.remove(defeated.keySet().iterator().next());
                  }
                }));
    CobblemonEvents.BATTLE_VICTORY.subscribe(
        event ->
            dispatch(
                () -> {
                  var targets = defeated.remove(event.getBattle().getBattleId());
                  if (targets == null || event.getWasWildCapture()) return;
                  for (var winner : event.getWinners())
                    if (winner instanceof PlayerBattleActor actor && actor.getEntity() != null)
                      for (var target : targets)
                        pokemon(actor.getEntity(), TasksConfig.Objective.DEFEAT, target);
                }));
  }

  private void available(ServerPlayer p, TasksState.Player state, TasksConfig.Reward reward) {
    if (state.pending != null)
      throw new IllegalStateException(
          "Entrega pendente de conferência administrativa: " + state.pending.id);
    if (reward.external() && (mod.locked(p) || mod.inside(p)))
      throw new IllegalStateException("Saia do Safari para resgatar itens/recompensas externas");
    if (!room(p, reward))
      throw new IllegalStateException("Sem espaço: guarde itens antes de resgatar");
  }

  private boolean room(ServerPlayer p, TasksConfig.Reward reward) {
    var slots = new ArrayList<ItemStack>();
    for (int i = 0; i < 36; i++) slots.add(p.getInventory().getItem(i).copy());
    for (var give : reward.items) {
      int left = give.amount;
      var exemplar = icon(give.item);
      for (var slot : slots)
        if (ItemStack.isSameItemSameComponents(slot, exemplar)) {
          int amount = Math.min(left, slot.getMaxStackSize() - slot.getCount());
          slot.grow(amount);
          left -= amount;
        }
      for (int i = 0; i < slots.size() && left > 0; i++)
        if (slots.get(i).isEmpty()) {
          var stack = exemplar.copy();
          stack.setCount(Math.min(left, stack.getMaxStackSize()));
          slots.set(i, stack);
          left -= stack.getCount();
        }
      if (left > 0) return false;
    }
    return true;
  }

  public void claim(ServerPlayer p, String id) {
    var m =
        missions.missions.stream()
            .filter(x -> x.enabled && x.id.equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Missão inexistente"));
    var state = player(p);
    available(p, state, m.reward);
    long now = System.currentTimeMillis();
    String key = TasksState.key(missions, m, now);
    if (state.claimed.contains(key)
        || TasksState.progress(state, missions, m, now).value < m.target)
      throw new IllegalStateException("Missão incompleta ou já resgatada");
    var receipt = new TasksState.Receipt();
    receipt.id = UUID.randomUUID().toString();
    receipt.kind = "MISSION";
    receipt.key = key;
    receipt.missionId = m.id;
    receipt.daily = m.period == TasksConfig.Period.DAILY;
    receipt.reward = m.reward;
    receipt.pointsDelta = m.reward.points;
    commit(p, state, receipt);
  }

  public void buy(ServerPlayer p, String id) {
    var product =
        shop.products.stream()
            .filter(x -> x.enabled && x.id.equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Produto inexistente"));
    var state = player(p);
    available(p, state, product.reward);
    String key = missions.limit(product.limitPeriod, System.currentTimeMillis()) + product.id;
    if (state.points < product.cost) throw new IllegalStateException("Pontos Safari insuficientes");
    if (state.purchases.getOrDefault(key, 0) >= product.limit)
      throw new IllegalStateException("Limite de compra atingido");
    var receipt = new TasksState.Receipt();
    receipt.id = UUID.randomUUID().toString();
    receipt.kind = "SHOP";
    receipt.key = key;
    receipt.reward = product.reward;
    receipt.pointsDelta = -product.cost;
    commit(p, state, receipt);
  }

  private void commit(ServerPlayer p, TasksState.Player state, TasksState.Receipt receipt) {
    long balance = Math.addExact(state.points, receipt.pointsDelta);
    if (balance < 0 || balance > 2_000_000_000L)
      throw new IllegalStateException("Saldo fora do limite");
    state.points = balance;
    state.pending = receipt;
    if (receipt.kind.equals("MISSION")) state.claimed.add(receipt.key);
    else state.purchases.merge(receipt.key, 1, Integer::sum);
    save();
    SafariMod.LOG.info(
        "Safari reward intent {} jogador={} kind={} key={}",
        receipt.id,
        p.getUUID(),
        receipt.kind,
        receipt.key);
    try {
      for (var give : receipt.reward.items) {
        int left = give.amount;
        while (left > 0) {
          var stack = icon(give.item);
          stack.setCount(Math.min(left, stack.getMaxStackSize()));
          left -= stack.getCount();
          if (!p.getInventory().add(stack))
            throw new IllegalStateException("Entrega de item incompleta");
        }
      }
      for (var command : receipt.reward.commands) {
        int result =
            mod.server
                .getCommands()
                .getDispatcher()
                .execute(
                    render(command, p.getGameProfile().getName(), p.getUUID()),
                    mod.server
                        .createCommandSourceStack()
                        .withPermission(4)
                        .withLevel(p.serverLevel())
                        .withPosition(p.position()));
        if (result <= 0) throw new IllegalStateException("Comando não confirmou sucesso");
      }
      complete(state, receipt);
      mod.tell(p, "Recompensa recebida! Pontos Safari: " + state.points);
    } catch (Exception e) {
      SafariMod.LOG.error(
          "Entrega Safari {} exige conferência; sem repetição automática", receipt.id, e);
      mod.tell(
          p, "Entrega pendente de conferência: " + receipt.id + ". Não tente resgatar novamente.");
    }
  }

  private void complete(TasksState.Player state, TasksState.Receipt receipt) {
    state.pending = null;
    if (receipt.daily)
      recordFor(
          state,
          TasksConfig.Objective.DAILY_COMPLETIONS,
          receipt.key,
          "",
          Set.of(),
          "",
          "",
          System.currentTimeMillis());
    save();
  }

  public void review(ServerPlayer p, boolean delivered) {
    var state = player(p);
    var receipt = state.pending;
    if (receipt == null) throw new IllegalStateException("Nenhuma entrega pendente");
    if (delivered) complete(state, receipt);
    else {
      state.points = Math.subtractExact(state.points, receipt.pointsDelta);
      if (state.points < 0 || state.points > 2_000_000_000L)
        throw new IllegalStateException("Estorno fora do limite");
      if (receipt.kind.equals("MISSION")) state.claimed.remove(receipt.key);
      else state.purchases.computeIfPresent(receipt.key, (k, v) -> v <= 1 ? null : v - 1);
      state.pending = null;
      save();
    }
    SafariMod.LOG.warn(
        "Revisão Safari reward {} jogador={} entregue={}", receipt.id, p.getUUID(), delivered);
  }

  public void points(ServerPlayer p, int delta) {
    var state = player(p);
    if (state.pending != null) throw new IllegalStateException("Resolva a entrega pendente antes");
    long value = Math.addExact(state.points, delta);
    if (value < 0 || value > 2_000_000_000L)
      throw new IllegalArgumentException("Saldo fora do limite");
    state.points = value;
    save();
    SafariMod.LOG.info(
        "Ajuste pontos Safari jogador={} delta={} saldo={}", p.getUUID(), delta, value);
  }
}
