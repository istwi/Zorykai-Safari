package dev.shirokai.safari.test;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.mojang.authlib.GameProfile;
import dev.shirokai.safari.*;
import dev.shirokai.safari.test.mixin.PlayerMapAccess;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Definitions below are disposable fixtures, not default missions or products. */
public final class TasksIntegration implements ModInitializer {
  private final List<String> checks = new ArrayList<>();
  private boolean ran;

  @Override
  public void onInitialize() {
    ServerTickEvents.END_SERVER_TICK.register(
        server -> {
          if (ran || server.getTickCount() < 35) return;
          ran = true;
          try {
            test(server);
            finish(server, true, null);
          } catch (Throwable e) {
            e.printStackTrace();
            finish(server, false, e.toString());
          }
        });
  }

  private void check(boolean condition, String label) {
    if (!condition) throw new AssertionError(label);
    checks.add(label);
  }

  private void denied(Runnable action, String label) {
    boolean denied = false;
    try {
      action.run();
    } catch (RuntimeException e) {
      denied = true;
    }
    check(denied, label);
  }

  private TasksConfig.Mission mission(
      String id, TasksConfig.Objective objective, int target, int points) {
    var m = new TasksConfig.Mission();
    m.id = id;
    m.name = "&a" + id;
    m.objective = objective;
    m.target = target;
    m.reward.points = points;
    return m;
  }

  private void test(MinecraftServer server) throws Exception {
    var mod = SafariMod.INSTANCE;
    var tasks = mod.tasks;
    check(tasks != null && tasks.healthy(), "reward engine starts with valid empty files");
    check(
        tasks.missions.missions.isEmpty() && tasks.shop.products.isEmpty(),
        "no missions or products are installed by default");
    var world = server.overworld();
    for (int x = -12; x <= 12; x++)
      for (int z = -12; z <= 12; z++)
        world.setBlockAndUpdate(new BlockPos(x, 199, z), Blocks.STONE.defaultBlockState());
    for (int x = 98; x <= 103; x++)
      for (int z = 98; z <= 103; z++)
        world.setBlockAndUpdate(new BlockPos(x, 199, z), Blocks.STONE.defaultBlockState());
    var id = UUID.fromString("bf37a943-7868-4725-abd1-b7239cf0c686");
    var p =
        new ServerPlayer(
            server, world, new GameProfile(id, "SafariTasks"), ClientInformation.createDefault());
    p.connection = new SafariIntegration.Capture(server, p);
    p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
    p.setPos(100.5, 200, 100.5);
    var access = (PlayerMapAccess) server.getPlayerList();
    access.safariPlayers().add(p);
    access.safariMap().put(id, p);
    if (Boolean.getBoolean("safari.tasks.restart")) {
      var saved =
          Storage.JSON.fromJson(
              Files.readString(Path.of("tasks-checkpoint.json")), com.google.gson.JsonObject.class);
      var ledger = tasks.player(p);
      check(
          ledger.pending != null && ledger.pending.id.equals(saved.get("id").getAsString()),
          "real restart preserves unresolved external delivery receipt");
      check(
          ledger.points == saved.get("points").getAsLong(),
          "real restart preserves points without replaying debit or reward");
      tasks.review(p, true);
      check(
          ledger.pending == null && ledger.points == saved.get("points").getAsLong(),
          "manual confirmation finalizes delivery without rerunning command");
      denied(() -> tasks.review(p, true), "resolved receipt cannot be confirmed again");
      return;
    }
    var commands = server.getCommands().getDispatcher();
    check(
        commands.execute("safari missoes", p.createCommandSourceStack()) == 1
            && p.containerMenu instanceof TasksMenu,
        "missions command opens its own native menu outside a visit");
    check(
        p.containerMenu
            .getSlot(tasks.menus.missions.emptySlot)
            .getItem()
            .getHoverName()
            .getString()
            .contains("Nenhuma"),
        "empty missions display configured empty state");
    check(
        commands.execute("safari loja", p.createCommandSourceStack()) == 1
            && p.containerMenu instanceof TasksMenu,
        "shop command opens a separate screen");
    check(tasks.player(p).points == 0, "new player has no free points");
    SafariMod.closeMenuIfOpen(p);
    var catalog = new TasksConfig.Missions();
    var capture = mission("catch", TasksConfig.Objective.CAPTURE, 2, 20);
    var unique = mission("unique", TasksConfig.Objective.UNIQUE_SPECIES, 2, 40);
    unique.period = TasksConfig.Period.WEEKLY;
    var visit = mission("visit", TasksConfig.Objective.VISIT_REGIONS, 1, 2);
    visit.regions.add("floresta");
    var complete = mission("daily_done", TasksConfig.Objective.DAILY_COMPLETIONS, 1, 5);
    complete.period = TasksConfig.Period.WEEKLY;
    var defeat = mission("defeat", TasksConfig.Objective.DEFEAT, 1, 3);
    var water = mission("water", TasksConfig.Objective.CAPTURE, 1, 7);
    water.types.add("water");
    catalog.missions.addAll(List.of(capture, unique, visit, complete, defeat, water));
    var store = new TasksConfig.Shop();
    var product = new TasksConfig.Product();
    product.id = "apple";
    product.name = "&aMaçã";
    product.cost = 10;
    var give = new TasksConfig.GiveItem();
    give.item = "minecraft:apple";
    product.reward.items.add(give);
    store.products.add(product);
    var commandProduct = new TasksConfig.Product();
    commandProduct.id = "command";
    commandProduct.name = "Comando";
    commandProduct.cost = 5;
    commandProduct.reward.commands.add("give {player} minecraft:stick 1");
    store.products.add(commandProduct);
    var uncertain = new TasksConfig.Product();
    uncertain.id = "uncertain";
    uncertain.name = "Falha simulada";
    uncertain.cost = 3;
    uncertain.reward.commands.add("clear {player} minecraft:bedrock 1");
    store.products.add(uncertain);
    Storage.write(Path.of("config/shirokai-safari-missions.json"), catalog);
    Storage.write(Path.of("config/shirokai-safari-shop.json"), store);
    tasks.reloadAll();
    check(
        tasks.missions.missions.size() == 6 && tasks.shop.products.size() == 3,
        "catalogs load from JSON without source changes");
    var keep = tasks.missions;
    Files.writeString(Path.of("config/shirokai-safari-shop.json"), "{broken");
    boolean rejected = false;
    try {
      tasks.reloadAll();
    } catch (Exception e) {
      rejected = true;
    }
    check(
        rejected && tasks.missions == keep && tasks.healthy(),
        "invalid reload retains the last complete configuration");
    Storage.write(Path.of("config/shirokai-safari-shop.json"), store);
    tasks.reloadAll();
    mod.config.area = new Config.Area("minecraft:overworld", -10, 199, -10, 10, 210, 10);
    mod.config.entrance = new Config.Position("minecraft:overworld", 0.5, 200, 0.5, 0, 0);
    mod.config.lobby = new Config.Position("minecraft:overworld", 100.5, 200, 100.5, 0, 0);
    mod.config.regions.put(
        "floresta", new Config.Position("minecraft:overworld", 0.5, 200, 0.5, 0, 0));
    var pokemon = PokemonProperties.Companion.parse("eevee level=5").create();
    pokemon.getPersistentData().putBoolean(Tasks.NATURAL, true);
    tasks.pokemon(p, TasksConfig.Objective.CAPTURE, pokemon);
    check(
        TasksState.progress(tasks.player(p), tasks.missions, capture, System.currentTimeMillis())
                .value
            == 0,
        "capture progress cannot be gained outside a visit");
    mod.enter(p);
    var safariBall = com.cobblemon.mod.common.api.pokeball.PokeBalls.getSafariBall();
    var ball =
        new com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity(
            safariBall, world, p, com.cobblemon.mod.common.CobblemonEntities.EMPTY_POKEBALL);
    ball.setPos(0.5, 200, 0.5);
    CobblemonEvents.POKEMON_CAPTURED.post(new PokemonCapturedEvent(pokemon, p, ball));
    CobblemonEvents.POKEMON_CAPTURED.post(new PokemonCapturedEvent(pokemon, p, ball));
    check(
        TasksState.progress(tasks.player(p), tasks.missions, capture, System.currentTimeMillis())
                .value
            == 1,
        "real capture callback counts once per Pokemon UUID");
    check(
        TasksState.progress(tasks.player(p), tasks.missions, water, System.currentTimeMillis())
                .value
            == 0,
        "type filter excludes non-water capture");
    var second = PokemonProperties.Companion.parse("eevee level=6").create();
    second.getPersistentData().putBoolean(Tasks.NATURAL, true);
    CobblemonEvents.POKEMON_CAPTURED.post(new PokemonCapturedEvent(second, p, ball));
    check(
        TasksState.progress(tasks.player(p), tasks.missions, capture, System.currentTimeMillis())
                .value
            == 2,
        "capture objective reaches target from actual events");
    check(
        TasksState.progress(tasks.player(p), tasks.missions, unique, System.currentTimeMillis())
                .value
            == 1,
        "unique species objective ignores repeated species");
    var third = PokemonProperties.Companion.parse("magikarp level=5").create();
    third.getPersistentData().putBoolean(Tasks.NATURAL, true);
    CobblemonEvents.POKEMON_CAPTURED.post(new PokemonCapturedEvent(third, p, ball));
    check(
        TasksState.progress(tasks.player(p), tasks.missions, unique, System.currentTimeMillis())
                    .value
                == 2
            && TasksState.progress(
                        tasks.player(p), tasks.missions, water, System.currentTimeMillis())
                    .value
                == 1,
        "weekly unique and type-filtered objectives track distinct eligible captures");
    var spawned = PokemonProperties.Companion.parse("magikarp level=5").create();
    CobblemonEvents.POKEMON_CAPTURED.post(new PokemonCapturedEvent(spawned, p, ball));
    check(
        TasksState.progress(tasks.player(p), tasks.missions, water, System.currentTimeMillis())
                .value
            == 1,
        "command-created unmarked Pokemon do not contribute");
    var wildEntity = PokemonProperties.Companion.parse("rattata level=5").createEntity(world);
    wildEntity.setPos(0.5, 200, 0.5);
    world.addFreshEntity(wildEntity);
    var wildPokemon = wildEntity.getPokemon();
    wildPokemon.getPersistentData().putBoolean(Tasks.NATURAL, true);
    wildPokemon.setState(new com.cobblemon.mod.common.pokemon.activestate.SentOutState(wildEntity));
    var battlePokemon =
        com.cobblemon.mod.common.battles.pokemon.BattlePokemon.Companion.safeCopyOf(wildPokemon);
    var wildActor =
        new com.cobblemon.mod.common.battles.actor.PokemonBattleActor(
            UUID.randomUUID(), battlePokemon, 32, (a, b, c, d, e) -> null);
    battlePokemon.setActor(wildActor);
    var playerActor =
        new com.cobblemon.mod.common.battles.actor.PlayerBattleActor(p.getUUID(), List.of());
    var battle =
        new com.cobblemon.mod.common.api.battles.model.PokemonBattle(
            new com.cobblemon.mod.common.battles.BattleFormat(),
            new com.cobblemon.mod.common.battles.BattleSide(playerActor),
            new com.cobblemon.mod.common.battles.BattleSide(wildActor));
    var context =
        new com.cobblemon.mod.common.api.battles.interpreter.BasicContext(
            "faint",
            1,
            com.cobblemon.mod.common.api.battles.interpreter.BattleContext.Type.FAINT,
            battlePokemon);
    CobblemonEvents.BATTLE_FAINTED.post(
        new com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent(
            battle, battlePokemon, context, "p2a"));
    check(
        TasksState.progress(tasks.player(p), tasks.missions, defeat, System.currentTimeMillis())
                .value
            == 0,
        "wild faint alone does not count before victory");
    CobblemonEvents.BATTLE_VICTORY.post(
        new com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent(
            battle, List.of(playerActor), List.of(wildActor), false));
    check(
        TasksState.progress(tasks.player(p), tasks.missions, defeat, System.currentTimeMillis())
                .value
            == 1,
        "native faint and victory callbacks count wild defeat");
    CobblemonEvents.BATTLE_VICTORY.post(
        new com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent(
            battle, List.of(playerActor), List.of(wildActor), false));
    check(
        TasksState.progress(tasks.player(p), tasks.missions, defeat, System.currentTimeMillis())
                .value
            == 1,
        "duplicate victory cannot duplicate defeat progress");
    wildEntity.discard();
    tasks.tick(p);
    tasks.tick(p);
    check(
        TasksState.progress(tasks.player(p), tasks.missions, visit, System.currentTimeMillis())
                .value
            == 1,
        "region presence counts each configured destination once");
    tasks.claim(p, "catch");
    check(
        tasks.player(p).points == 20,
        "completed mission awards configured points during active visit");
    denied(() -> tasks.claim(p, "catch"), "mission cannot be claimed twice");
    check(
        TasksState.progress(tasks.player(p), tasks.missions, complete, System.currentTimeMillis())
                .value
            == 1,
        "daily mission claim advances weekly daily-completion objective");
    tasks.open(p, false);
    var gui = p.containerMenu;
    var tick = SafariMod.class.getDeclaredMethod("tick");
    tick.setAccessible(true);
    tick.invoke(mod);
    check(p.containerMenu == gui, "Safari protection keeps mission menu open");
    gui.clicked(tasks.menus.missions.weeklySlot, 0, ClickType.PICKUP, p);
    check(p.containerMenu instanceof TasksMenu, "weekly tab opens without leaking icons");
    check(
        TrainersBridge.safariCommand("player", "safari missoes")
            && TrainersBridge.safariCommand("player", "safari loja"),
        "both independent commands are allowed for Trainers NPCs");
    var admin = p.createCommandSourceStack().withPermission(4);
    check(
        commands.execute("traineradmin npc create-command safari_tarefas", admin) == 1
            && commands.execute(
                    "traineradmin npc command safari_tarefas player safari missoes", admin)
                == 1,
        "actual Trainers accepts an independent missions NPC");
    net.minecraft.world.entity.Entity guide = null;
    for (var entity : world.getAllEntities())
      if (entity instanceof com.cobblemon.mod.common.entity.npc.NPCEntity
          && entity.getName().getString().equals("safari_tarefas")) guide = entity;
    check(
        guide != null && TrainersBridge.safariNpc(guide),
        "missions NPC is recognized by interaction bridge");
    var interaction =
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT
            .invoker()
            .interact(p, world, net.minecraft.world.InteractionHand.MAIN_HAND, guide, null);
    check(
        interaction.consumesAction() && p.containerMenu instanceof TasksMenu,
        "actual NPC interaction opens missions for non-OP visitor");
    commands.execute("traineradmin npc command safari_tarefas player safari loja", admin);
    check(
        TrainersBridge.safariNpc(guide), "NPC bridge also accepts independent points shop action");
    tasks.points(p, 100);
    denied(() -> tasks.buy(p, "apple"), "physical shop rewards require leaving Safari");
    mod.exit(p);
    tasks.open(p, true);
    long balance = tasks.player(p).points;
    p.containerMenu.clicked(0, 0, ClickType.PICKUP, p);
    check(tasks.player(p).points == balance, "first shop click only selects a confirmation");
    p.containerMenu.clicked(0, 0, ClickType.PICKUP, p);
    check(
        tasks.player(p).points == balance - 10 && p.getInventory().countItem(Items.APPLE) == 1,
        "second shop click debits points and delivers actual item");
    denied(() -> tasks.buy(p, "apple"), "per-player weekly shop limit prevents repeat purchase");
    tasks.buy(p, "command");
    check(
        p.getInventory().countItem(Items.STICK) == 1,
        "configured command executes with validated player placeholder");
    long beforePending = tasks.player(p).points;
    tasks.buy(p, "uncertain");
    check(
        tasks.player(p).pending != null && tasks.player(p).points == beforePending - 3,
        "unconfirmed command leaves a durable receipt and reserved debit");
    denied(() -> tasks.claim(p, "unique"), "pending external delivery blocks other claims");
    var path =
        server
            .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("data/shirokai-safari/missions.json");
    var disk = Storage.read(path, TasksState.class).players.get(id.toString());
    check(
        disk.pending != null && disk.points == tasks.player(p).points,
        "pending command is persisted without automatic retry");
    tasks.review(p, false);
    check(
        tasks.player(p).pending == null && tasks.player(p).points == beforePending,
        "admin cancellation restores debit only after manual verification");
    p.getInventory().clearContent();
    for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
    long fullBalance = tasks.player(p).points;
    var fullProduct = new TasksConfig.Product();
    fullProduct.id = "full";
    fullProduct.name = "Cheio";
    fullProduct.reward.items.add(give);
    tasks.shop.products.add(fullProduct);
    denied(() -> tasks.buy(p, "full"), "full inventory refuses before debit or delivery");
    check(
        tasks.player(p).points == fullBalance,
        "full-inventory refusal leaves point balance intact");
    p.getInventory().clearContent();
    tasks.claim(p, "unique");
    check(
        tasks
            .player(p)
            .claimed
            .contains(TasksState.key(tasks.missions, unique, System.currentTimeMillis())),
        "weekly mission can be claimed outside Safari");
    tasks.buy(p, "uncertain");
    check(tasks.player(p).pending != null, "unresolved receipt is saved for actual restart test");
    Storage.write(
        Path.of("./tasks-checkpoint.json"),
        Map.of("id", tasks.player(p).pending.id, "points", tasks.player(p).points));
    Storage.write(Path.of("config/shirokai-safari-missions.json"), new TasksConfig.Missions());
    Storage.write(Path.of("config/shirokai-safari-shop.json"), new TasksConfig.Shop());
    tasks.reloadAll();
    check(
        tasks.missions.missions.isEmpty() && tasks.shop.products.isEmpty(),
        "removing definitions returns both menus to empty state without resetting points");
  }

  private void finish(MinecraftServer server, boolean passed, String error) {
    try {
      var result = new LinkedHashMap<String, Object>();
      result.put("passed", passed);
      result.put("checks", checks);
      result.put("error", error);
      result.put("realClients", 0);
      Files.writeString(Path.of("safari-tasks-result.json"), Storage.JSON.toJson(result));
    } catch (Exception ignored) {
    }
    server.halt(false);
  }
}
