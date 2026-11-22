package dev.shirokai.safari.test;

import com.cobblemon.mod.common.api.spawning.BestSpawner;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawner;
import com.mojang.authlib.GameProfile;
import dev.shirokai.safari.*;
import dev.shirokai.safari.test.mixin.PlayerMapAccess;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

public final class SafariIntegration implements ModInitializer {
  private final List<String> checks = new ArrayList<>();
  private boolean ran;

  static class Capture extends net.minecraft.server.network.ServerGamePacketListenerImpl {
    final java.util.List<net.minecraft.network.protocol.Packet<?>> packets =
        new java.util.ArrayList<>();

    Capture(MinecraftServer server, net.minecraft.server.level.ServerPlayer player) {
      super(
          server,
          new net.minecraft.network.Connection(
              net.minecraft.network.protocol.PacketFlow.SERVERBOUND),
          player,
          net.minecraft.server.network.CommonListenerCookie.createInitial(
              player.getGameProfile(), false));
    }

    @Override
    public void send(
        net.minecraft.network.protocol.Packet<?> packet,
        net.minecraft.network.PacketSendListener callbacks) {
      packets.add(packet);
    }
  }

  private int bossOps(Capture capture, UUID id, String operation) {
    int count = 0;
    for (var packet : capture.packets)
      if (packet instanceof net.minecraft.network.protocol.game.ClientboundBossEventPacket boss) {
        var found = new boolean[1];
        boss.dispatch(
            new net.minecraft.network.protocol.game.ClientboundBossEventPacket.Handler() {
              public void add(
                  UUID uuid,
                  net.minecraft.network.chat.Component name,
                  float progress,
                  net.minecraft.world.BossEvent.BossBarColor color,
                  net.minecraft.world.BossEvent.BossBarOverlay overlay,
                  boolean dark,
                  boolean music,
                  boolean fog) {
                found[0] = uuid.equals(id) && operation.equals("add");
              }

              public void remove(UUID uuid) {
                found[0] = uuid.equals(id) && operation.equals("remove");
              }
            });
        if (found[0]) count++;
      }
    return count;
  }

  private static final UUID ID = UUID.fromString("a0c4c320-178c-43de-a26c-f067e01e0fbb");

  public void onInitialize() {
    if (Boolean.getBoolean("safari.economy")) {
      new EconomyIntegration().onInitialize();
      return;
    }
    if (!Boolean.getBoolean("safari.test")) throw new IllegalStateException("Disposable test only");
    ServerTickEvents.END_SERVER_TICK.register(
        s -> {
          if (ran || s.getTickCount() < 30) return;
          ran = true;
          try {
            test(s);
            finish(s, true, null);
          } catch (Throwable e) {
            e.printStackTrace();
            finish(s, false, e.toString());
          }
        });
  }

  private void check(boolean ok, String what) {
    if (!ok) throw new AssertionError(what);
    checks.add(what);
  }

  private void test(MinecraftServer s) throws Exception {
    var m = SafariMod.INSTANCE;
    var w = s.overworld();
    for (int x = -12; x <= 12; x++)
      for (int z = -12; z <= 12; z++)
        w.setBlock(new BlockPos(x, 199, z), Blocks.STONE.defaultBlockState(), 3);
    for (int x = 98; x <= 103; x++)
      for (int z = 98; z <= 103; z++)
        w.setBlock(new BlockPos(x, 199, z), Blocks.STONE.defaultBlockState(), 3);
    var p =
        new net.minecraft.server.level.ServerPlayer(
            s,
            w,
            new GameProfile(ID, "SafariTest"),
            net.minecraft.server.level.ClientInformation.createDefault());
    p.setPos(100.5, 200, 100.5);
    var capture = new Capture(s, p);
    p.connection = capture;
    p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
    var access = (PlayerMapAccess) s.getPlayerList();
    access.safariPlayers().add(p);
    access.safariMap().put(ID, p);
    if (Boolean.getBoolean("safari.restart")) {
      var v = m.visit(p);
      check(
          v.recovery && !v.active,
          "restart preserves interrupted return without charging offline time");
      check(v.balls == 19, "ball balance survived real restart");
      check(
          "uncertain-test".equals(v.purchase),
          "interrupted purchase survives restart without automatic credit");
      m.exit(p);
      check(!m.locked(p) && p.getX() == 100.5, "recovery clears lock only after return");
      check(
          s.getCommands()
                  .getDispatcher()
                  .execute(
                      "safariadmin compra SafariTest confirmar",
                      p.createCommandSourceStack().withPermission(4))
              == 1,
          "administrator can review interrupted purchase after checking payment");
      check(
          v.balls == 51 && v.purchase == null, "manual review credits pending bundle exactly once");
      return;
    }
    m.config.area = new Config.Area("minecraft:overworld", -10, 199, -10, 10, 210, 10);
    m.config.entrance = new Config.Position("minecraft:overworld", 0.5, 200, 0.5, 0, 0);
    m.config.lobby = new Config.Position("minecraft:overworld", 100.5, 200, 100.5, 0, 0);
    m.config.validate();
    var v = m.visit(p);
    var raidBars = RaidHudBridge.bars();
    net.minecraft.server.level.ServerBossEvent raidBar =
        raidBars.isEmpty() ? null : raidBars.getFirst();
    if (raidBar != null) {
      raidBar.addPlayer(p);
      check(
          bossOps(capture, raidBar.getId(), "add") > 0,
          "actual Raid boss bar visible before Safari entry");
    }
    p.getInventory().add(new ItemStack(Items.DIAMOND));
    boolean denied = false;
    try {
      m.enter(p);
    } catch (IllegalStateException expected) {
      denied = true;
    }
    check(denied && !v.active, "nonempty inventory denied before cycle starts");
    p.getInventory().clearContent();
    m.enter(p);
    check(m.active(p) && m.inside(p), "entry teleports into area");
    var syncHud =
        SafariHud.class.getDeclaredMethod(
            "sync", SafariMod.class, net.minecraft.server.level.ServerPlayer.class);
    syncHud.setAccessible(true);
    syncHud.invoke(null, m, p);
    check(SafariHud.bar(ID) != null, "Safari boss bar is created on entry");
    if (raidBar != null) {
      check(
          bossOps(capture, raidBar.getId(), "remove") > 0,
          "Raid boss bar is hidden while Safari is active");
      int packets = capture.packets.size();
      raidBar.setName(net.minecraft.network.chat.Component.literal("RAID • teste visual"));
      check(
          capture.packets.size() == packets,
          "Raid updates are suppressed without changing raid membership");
    }

    check(Balls.count(p, v) == 32, "first entry grants 32 bound balls");
    check(
        net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getKey(p.getInventory().getItem(0).getItem())
            .toString()
            .equals("cobblemon:safari_ball"),
        "ball bundle uses actual Cobblemon Safari Ball");
    check(v.budgetMillis == 3600000, "normal budget one hour");
    var safariBall =
        (com.cobblemon.mod.common.item.PokeBallItem) p.getInventory().getItem(0).getItem();
    var flight =
        new com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity(
            safariBall.getPokeBall(),
            w,
            p,
            com.cobblemon.mod.common.CobblemonEntities.EMPTY_POKEBALL);
    check(
        flight.getTags().contains(SingleUseBalls.TAG),
        "single-use marking happens before chunk-load callbacks or projectile impact");
    w.addFreshEntity(flight);
    check(
        flight.getTags().contains(SingleUseBalls.TAG),
        "Safari projectile receives persistent single-use tag");
    check(
        flight.spawnAtLocation(safariBall) == null,
        "missed Safari ball cannot refund ordinary ground ball");
    check(
        flight.spawnAtLocation(new ItemStack(safariBall), 0) == null,
        "stack refund path is blocked for Safari projectile");
    flight.discard();
    var normal =
        new com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity(
            safariBall.getPokeBall(), w, com.cobblemon.mod.common.CobblemonEntities.EMPTY_POKEBALL);
    check(
        normal.spawnAtLocation(safariBall) != null,
        "ordinary Safari balls outside visit preserve native refund behavior");
    normal.discard();

    check(
        !p.getInventory().add(new ItemStack(Items.DIRT)),
        "direct external inventory additions blocked");
    var drop = new ItemEntity(w, p.getX(), p.getY(), p.getZ(), new ItemStack(Items.EMERALD));
    drop.playerTouch(p);
    check(
        drop.isRemoved() && p.getInventory().contains(new ItemStack(Items.EMERALD)),
        "ground items can be collected inside Safari");
    check(
        !GroundPickup.allowed(p, new ItemStack(Items.DIRT)),
        "ground pickup authorization does not leak into direct grants");
    for (String cmd :
        List.of(
            "home",
            "sethome test",
            "tpa Someone",
            "tpaccept",
            "enderchest",
            "minecraft:tp @s 0 200 0",
            "execute run spawn",
            "spawn"))
      check(SafariMod.commands(p.createCommandSourceStack(), cmd), "command blocked: " + cmd);
    for (String cmd : List.of("safari sair", "safari comprar", "login secret", "l secret"))
      check(!SafariMod.commands(p.createCommandSourceStack(), cmd), "Safari/auth allowed: " + cmd);
    check(
        s.getCommands()
                    .getDispatcher()
                    .execute("safariadmin modo on", p.createCommandSourceStack().withPermission(4))
                == 1
            && !SafariMod.commands(p.createCommandSourceStack().withPermission(4), "home"),
        "authorized admin mode permits commands");
    check(
        SafariMod.commands(p.createCommandSourceStack().withPermission(0), "home"),
        "permission revoked disables admin mode");
    m.menu(p);
    var menu = p.containerMenu;
    check(menu instanceof SafariMenu, "native ServerPlayer opens actual Safari menu");
    check(menu.slots.size() == 63, "native menu has 27 protected slots and player inventory");
    int before = p.getInventory().getItem(0).getCount();
    menu.clicked(27, 0, ClickType.QUICK_MOVE, p);
    check(p.getInventory().getItem(0).getCount() == before, "menu cannot transfer inventory");
    p.closeContainer();
    p.getInventory().getItem(0).shrink(5);
    v.lastCharge = System.currentTimeMillis() - 1800000;
    m.exit(p);
    check(
        v.balls == 27 && !m.locked(p) && p.getX() == 100.5,
        "exit stores remaining balls and returns");
    syncHud.invoke(null, m, p);
    check(SafariHud.bar(ID) == null, "Safari boss bar removed after exit");
    if (raidBar != null)
      check(
          bossOps(capture, raidBar.getId(), "add") >= 2,
          "Raid boss bar restored after Safari exit");
    long remaining = v.remaining();
    var ordinarySafari =
        new ItemStack(
            net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.parse("cobblemon:safari_ball")));
    p.getInventory().add(ordinarySafari);
    m.menu(p);
    var afterExit = p.containerMenu;
    var nativeTick = SafariMod.class.getDeclaredMethod("tick");
    nativeTick.setAccessible(true);
    long oldClose =
        capture.packets.stream()
            .filter(
                packet ->
                    packet
                        instanceof
                        net.minecraft.network.protocol.game.ClientboundContainerClosePacket)
            .count();
    nativeTick.invoke(m);
    check(
        capture.packets.stream()
                .filter(
                    packet ->
                        packet
                            instanceof
                            net.minecraft.network.protocol.game.ClientboundContainerClosePacket)
                .count()
            == oldClose,
        "outside Safari tick does not send a spurious container-close packet");
    check(
        p.containerMenu == afterExit && afterExit.stillValid(p),
        "Safari menu stays open after exit even with ordinary Safari Ball inventory");
    afterExit.clicked(10, 0, ClickType.PICKUP, p);
    check(
        p.containerMenu == afterExit && !m.active(p),
        "inventory-full entry refusal leaves Safari menu open instead of closing it");
    p.closeContainer();

    check(
        p.getInventory().contains(new ItemStack(Items.EMERALD)),
        "ground loot survives Safari exit");
    p.getInventory().clearContent();
    m.enter(p);
    check(
        Balls.count(p, v) == 27 && v.remaining() == remaining,
        "reentry preserves budget and balls");
    var adminMenu = new AdminMenu(92, p.getInventory(), m);
    check(adminMenu.slots.size() == 63, "admin menu has protected status slots");
    int eventCommand =
        s.getCommands()
            .getDispatcher()
            .execute(
                "safariadmin evento forma cobblemon:rattata alolan 100 1",
                p.createCommandSourceStack().withPermission(4));
    check(
        eventCommand == 1 && m.data.event.forms.containsKey("cobblemon:rattata"),
        "form event command accepts namespaced species without quotes");
    check(
        capture.packets.stream()
            .anyMatch(
                packet ->
                    packet
                        instanceof
                        net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket),
        "event broadcasts actual title packet");
    check(
        capture.packets.stream()
            .anyMatch(
                packet ->
                    packet
                            instanceof
                            net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat
                        && chat.content().getString().contains("EVENTO SAFARI")),
        "event broadcasts configurable chat announcement");
    check(
        m.data.event.starts > 0 && !m.data.event.endAnnounced,
        "event records its timer for boss bar and end broadcast");
    m.data.event = new Storage.Event();
    check(m.multiplier(ID) == 2, "area spawn multiplier 2x");
    var spawner = new PlayerSpawner(p, BestSpawner.INSTANCE.getFishingSpawner().getSpawnPool());
    spawner.setTicksUntilNextSpawn(100);
    spawner.setTickTimerMultiplier(1);
    spawner.tick();
    check(spawner.getTicksUntilNextSpawn() == 98, "actual Cobblemon spawner consumes double timer");
    m.data.event.ends = System.currentTimeMillis() + 60000;
    m.data.event.spawn = 2;
    check(m.multiplier(ID) == 4, "spawn event multiplies baseline");
    var hud =
        SafariHud.class.getDeclaredMethod(
            "sync", SafariMod.class, net.minecraft.server.level.ServerPlayer.class);
    hud.setAccessible(true);
    hud.invoke(null, m, p);
    check(
        SafariHud.bar(ID) != null && SafariHud.bar(ID).getName().getString().contains("EVENTO"),
        "active event replaces normal Safari boss bar");
    var keptEnd = m.data.event.ends;
    m.data.event.ends = System.currentTimeMillis() - 1;
    hud.invoke(null, m, p);
    check(
        SafariHud.bar(ID).getName().getString().contains("Tempo"),
        "Safari boss bar returns after event expiration");
    m.data.event.ends = keptEnd;

    m.data.event.shinyChance = 1;
    var cause = new com.cobblemon.mod.common.api.spawning.SpawnCause(spawner, p);
    var pos =
        new com.cobblemon.mod.common.api.spawning.position.BasicSpawnablePosition(
            cause, w, new BlockPos(2, 200, 2), 15, 15, true, new java.util.ArrayList<>());
    var props =
        com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(
            "rattata level=5 shiny=false");
    var details = new com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail();
    var action =
        new com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnAction(
            pos,
            "common",
            details,
            props,
            ItemStack.EMPTY,
            new com.cobblemon.mod.common.api.drop.DropTable(),
            new kotlin.ranges.IntRange(5, 5));
    m.data.event.forms.put("cobblemon:rattata", "alolan");
    m.data.event.formChance = 1;
    var spawned = action.createEntity();
    check(spawned.getPokemon().getShiny(), "shiny event applies to real natural spawn action");
    check(
        spawned.getPokemon().getForm().getName().equalsIgnoreCase("Alola"),
        "regional form event applies to real spawn action");
    w.addFreshEntity(spawned);
    check(SpawnMetrics.spawns() > 0, "admin spawn counter counts actual entity insertion");
    spawned.discard();
    var outside =
        new com.cobblemon.mod.common.api.spawning.position.BasicSpawnablePosition(
            cause, w, new BlockPos(100, 200, 100), 15, 15, true, new java.util.ArrayList<>());
    var regular =
        new com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnAction(
                outside,
                "common",
                details,
                com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(
                    "rattata level=5 shiny=false"),
                ItemStack.EMPTY,
                new com.cobblemon.mod.common.api.drop.DropTable(),
                new kotlin.ranges.IntRange(5, 5))
            .createEntity();
    check(!regular.getPokemon().getShiny(), "event does not affect spawn outside Safari");
    regular.discard();
    m.data.event = new Storage.Event();
    boolean noEconomy = false;
    try {
      new Shop(m).buy(p);
    } catch (IllegalStateException expected) {
      noEconomy = true;
    }
    check(noEconomy && v.purchase == null, "missing economy refuses purchase before journal");
    m.exit(p);
    m.enter(p);
    v.origin = new Config.Position("missing:dimension", 0, 200, 0, 0, 0);
    var lobby = m.config.lobby;
    m.config.lobby = null;
    boolean pending = false;
    try {
      m.exit(p);
    } catch (IllegalStateException expected) {
      pending = true;
    }
    check(
        pending && v.recovery && v.origin != null,
        "failed return preserves origin and pauses quota");
    m.config.lobby = lobby;
    m.exit(p);
    check(
        !m.locked(p) && p.getX() == 100.5,
        "retry uses configured safe lobby when original dimension is absent");
    m.enter(p);
    v.usedMillis = v.budgetMillis;
    var tick = SafariMod.class.getDeclaredMethod("tick");
    tick.setAccessible(true);
    tick.invoke(m);
    check(!m.locked(p) && p.getX() == 100.5, "exhausted quota automatically returns visitor");
    v.budgetMillis += 60000;
    m.enter(p);
    p.setPos(102, 200, 102);
    tick.invoke(m);
    check(!m.locked(p) && p.getX() == 100.5, "external teleport ends visit and returns safely");
    var regionSource = p.createCommandSourceStack().withPermission(4);
    var regionCommands = s.getCommands().getDispatcher();
    check(
        regionCommands.execute("safari regioes", p.createCommandSourceStack()) == 0,
        "region menu refuses players outside a visit");
    m.enter(p);
    w.setBlockAndUpdate(new BlockPos(4, 199, 0), Blocks.STONE.defaultBlockState());
    p.setPos(4.5, 200, 0.5);
    check(
        regionCommands.execute("safariadmin regiao criar floresta", regionSource) == 1,
        "admin registers safe region within Safari");
    check(
        regionCommands.execute("safariadmin regiao criar floresta", regionSource) == 0,
        "duplicate region does not overwrite destination");
    var beforeOrigin = v.origin;
    long beforeCycle = v.cycleEnd;
    int beforeBalls = Balls.count(p, v);
    boolean beforePurchased = v.bundlePurchased;
    p.setPos(0.5, 200, 0.5);
    check(
        regionCommands.execute("safari regiao floresta", p.createCommandSourceStack()) == 1
            && p.getX() == 4.5,
        "public region command teleports active visitor");
    check(
        v.origin.equals(beforeOrigin)
            && v.cycleEnd == beforeCycle
            && Balls.count(p, v) == beforeBalls
            && v.bundlePurchased == beforePurchased,
        "regional travel preserves origin, cycle, balls and purchase allowance");
    regionCommands.execute("safari regioes", p.createCommandSourceStack());
    check(p.containerMenu instanceof RegionsMenu, "region menu opens through player command");
    var regionTick = SafariMod.class.getDeclaredMethod("tick");
    regionTick.setAccessible(true);
    regionTick.invoke(m);
    check(p.containerMenu instanceof RegionsMenu, "Safari tick keeps region menu open");
    p.containerMenu.clicked(0, 0, ClickType.QUICK_MOVE, p);
    check(p.containerMenu instanceof RegionsMenu, "shift click cannot extract destination icons");
    p.containerMenu.clicked(0, 0, ClickType.PICKUP, p);
    check(
        p.containerMenu == p.inventoryMenu && p.getX() == 4.5,
        "menu click safely teleports and closes interface");
    w.setBlockAndUpdate(new BlockPos(4, 200, 0), Blocks.STONE.defaultBlockState());
    check(
        regionCommands.execute("safari regiao floresta", p.createCommandSourceStack()) == 0,
        "travel rechecks obstruction at destination");
    w.setBlockAndUpdate(new BlockPos(4, 200, 0), Blocks.AIR.defaultBlockState());
    if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("shirokai_trainers")) {
      check(
          regionCommands.execute("traineradmin npc create-command safari_guia", regionSource) == 1,
          "actual Trainers creates independent command NPC");
      check(
          regionCommands.execute(
                  "traineradmin npc command safari_guia player safari regioes", regionSource)
              == 1,
          "actual Trainers accepts Safari region menu command");
      net.minecraft.world.entity.Entity guide = null;
      for (var entity : w.getAllEntities())
        if (entity instanceof com.cobblemon.mod.common.entity.npc.NPCEntity
            && entity.getName().getString().equals("safari_guia")) guide = entity;
      check(
          guide != null && TrainersBridge.safariNpc(guide),
          "optional bridge recognizes actual indexed Safari guide");
      var interaction =
          net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT
              .invoker()
              .interact(p, w, net.minecraft.world.InteractionHand.MAIN_HAND, guide, null);
      check(
          interaction.consumesAction() && p.containerMenu instanceof RegionsMenu,
          "real Trainers NPC callback opens menu for non-OP active visitor");
      SafariMod.closeMenuIfOpen(p);
      regionCommands.execute(
          "traineradmin npc command safari_guia player safari comprar", regionSource);
      check(TrainersBridge.safariNpc(guide), "seller action is allowed by optional NPC bridge");
      regionCommands.execute(
          "traineradmin npc command safari_guia player safari tempo", regionSource);
      check(
          !TrainersBridge.safariNpc(guide),
          "unrelated NPC action does not bypass Safari entity guard");
    }
    m.exit(p);
    check(
        regionCommands.execute("safari regiao floresta", p.createCommandSourceStack()) == 0,
        "regional travel cannot start a visit or bypass entry");
    var configured = m.config;
    var source = p.createCommandSourceStack().withPermission(4);
    var dispatcher = s.getCommands().getDispatcher();
    m.enter(p);
    check(
        dispatcher.execute("safariadmin delete", source) == 0 && m.config.area != null,
        "area deletion refuses active visitors");
    m.exit(p);
    v.recovery = true;
    check(
        dispatcher.execute("safariadmin delete", source) == 0 && m.config.area != null,
        "area deletion refuses pending returns");
    v.recovery = false;
    long originalCycle = v.cycleEnd;
    int originalBalls = v.balls;
    check(
        dispatcher.execute("safariadmin delete", source) == 1,
        "administrator can delete configured Safari area");
    check(
        m.config.area == null && m.config.entrance == null && m.config.regions.isEmpty(),
        "deletion clears area, entrance and region destinations");
    var persisted =
        Storage.JSON.fromJson(
            Files.readString(Path.of("config/shirokai-safari.json")), Config.class);
    check(
        persisted.area == null && persisted.entrance == null,
        "area deletion persists to configuration");
    check(
        v.cycleEnd == originalCycle
            && v.balls == originalBalls
            && m.config.lobby.equals(configured.lobby),
        "area deletion preserves player cycle, balls and safe exit");
    check(
        dispatcher.execute("safariadmin delete", source) == 0,
        "deleting absent area reports refusal");
    m.config = configured;
    Storage.write(Path.of("config/shirokai-safari.json"), configured);
    m.enter(p);
    v.balls = 19;
    v.purchase = "uncertain-test";
    v.purchaseAmount = 32;
    v.purchasePrice = "10000";
    v.purchaseCurrency = "impactor:dollars";
    m.save();
    var field = SafariMod.class.getDeclaredField("healthy");
    field.setAccessible(true);
    field.setBoolean(m, false);
  }

  private void finish(MinecraftServer s, boolean passed, String error) {
    try {
      var result = new LinkedHashMap<String, Object>();
      result.put("passed", passed);
      result.put("checks", checks);
      result.put("error", error);
      result.put("realClients", 0);
      Files.writeString(
          Path.of(
              Boolean.getBoolean("safari.restart")
                  ? "safari-restart-result.json"
                  : "safari-integration-result.json"),
          Storage.JSON.toJson(result));
      System.out.println("SAFARI_RESULT " + Storage.JSON.toJson(result));
    } catch (Exception e) {
      e.printStackTrace();
    }
    s.halt(false);
  }
}
