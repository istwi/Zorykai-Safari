package dev.shirokai.clear.test;

import com.cobblemon.mod.common.*;
import com.cobblemon.mod.common.api.pokemon.*;
import com.cobblemon.mod.common.api.spawning.*;
import com.cobblemon.mod.common.api.spawning.detail.*;
import com.cobblemon.mod.common.api.spawning.position.*;
import com.cobblemon.mod.common.api.spawning.spawner.*;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.authlib.GameProfile;
import dev.shirokai.clear.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;

public final class ClearSafariIntegration implements ModInitializer {
  private final List<String> checks = new ArrayList<>();
  private boolean ran;
  private Runnable deferred;
  private int resumeAt;

  private static class Capture extends ServerGamePacketListenerImpl {
    final List<Packet<?>> packets = new ArrayList<>();

    Capture(MinecraftServer s, ServerPlayer p) {
      super(
          s,
          new Connection(PacketFlow.SERVERBOUND),
          p,
          CommonListenerCookie.createInitial(p.getGameProfile(), false));
    }

    public void send(Packet<?> packet, PacketSendListener listener) {
      packets.add(packet);
    }

    boolean chat(String marker) {
      return packets.stream()
          .anyMatch(
              p ->
                  p instanceof ClientboundSystemChatPacket c
                      && c.content().getString().contains(marker));
    }
  }

  public void onInitialize() {
    if (!Boolean.getBoolean("clear.safari.test"))
      throw new IllegalStateException("Disposable test only");
    ServerTickEvents.END_SERVER_TICK.register(
        s -> {
          try {
            if (!ran && s.getTickCount() >= 30) {
              ran = true;
              test(s);
            }
            if (deferred != null && s.getTickCount() >= resumeAt) {
              var continuation = deferred;
              deferred = null;
              continuation.run();
              finish(s, true, null);
            }
          } catch (Throwable e) {
            deferred = null;
            e.printStackTrace();
            finish(s, false, e.toString());
          }
        });
  }

  private void check(boolean condition, String text) {
    if (!condition) throw new AssertionError(text);
    checks.add(text);
  }

  private ServerPlayer player(MinecraftServer s, ServerLevel w, String name, double x, double z)
      throws Exception {
    var p =
        new ServerPlayer(
            s,
            w,
            new GameProfile(
                UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                name),
            ClientInformation.createDefault());
    p.setPos(x, 200, z);
    p.connection = new Capture(s, p);
    s.getPlayerList().getPlayers().add(p);
    ((dev.shirokai.clear.test.mixin.PlayerMapAccess) s.getPlayerList())
        .safariMap()
        .put(p.getUUID(), p);
    s.getPlayerList().op(p.getGameProfile());
    w.addNewPlayer(p);
    return p;
  }

  private ItemEntity item(ServerLevel w, double x, double z) {
    w.setChunkForced(((int) x) >> 4, ((int) z) >> 4, true);
    w.getChunkAt(new BlockPos((int) x, 200, (int) z));
    var item = new ItemEntity(w, x, 200, z, new ItemStack(Items.DIRT));
    item.tickCount = 5000;
    w.addFreshEntity(item);
    return item;
  }

  private PokemonEntity wild(ServerLevel w, ServerPlayer owner, double x, double z) {
    var spawner = new PlayerSpawner(owner, BestSpawner.INSTANCE.getFishingSpawner().getSpawnPool());
    var position =
        new BasicSpawnablePosition(
            new SpawnCause(spawner, owner),
            w,
            new BlockPos((int) x, 200, (int) z),
            15,
            15,
            true,
            new ArrayList<>());
    var action =
        new PokemonSpawnAction(
            position,
            "common",
            new PokemonSpawnDetail(),
            PokemonProperties.Companion.parse("rattata level=5 shiny=false"),
            ItemStack.EMPTY,
            new com.cobblemon.mod.common.api.drop.DropTable(),
            new kotlin.ranges.IntRange(5, 5));
    var entity = action.createEntity();
    entity.setPos(x, 200, z);
    entity.tickCount = 5000;
    w.getChunkAt(entity.blockPosition());
    w.addFreshEntity(entity);
    return entity;
  }

  private void test(MinecraftServer s) throws Exception {
    var key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("multiworld:safari"));
    var local = s.getLevel(key);
    check(local != null, "real custom multiworld:safari dimension loaded from datapack");
    for (var w : List.of(local, s.overworld()))
      for (int x = 0; x < 12; x++)
        for (int z = 0; z < 12; z++) {
          w.getChunkAt(new BlockPos(x, 200, z));
          w.setBlock(new BlockPos(x, 199, z), Blocks.STONE.defaultBlockState(), 3);
        }
    var inside = player(s, local, "ClearInside", 1.5, 1.5);
    var outside = player(s, s.overworld(), "ClearOutside", 1.5, 1.5);
    var far = player(s, local, "ClearFar", 100.5, 100.5);
    var area = SafariArea.current();
    check(
        area != null && area.dimension().equals("multiworld:safari"),
        "Clear reads Safari's configured dimension, not Overworld");
    check(
        area.contains(inside) && !area.contains(outside) && !area.contains(far),
        "scope checks dimension and all cuboid coordinates");
    var field = ShirokaiClear.class.getDeclaredField("service");
    field.setAccessible(true);
    var service = (ClearService) field.get(ShirokaiClear.INSTANCE);
    var c = new ClearConfig();
    c.intervalSeconds = 600;
    c.items.minimumAgeSeconds = 0;
    c.pokemon.minimumAgeSeconds = 0;
    c.worlds.excludedDimensions.add("multiworld:safari");
    c.announcements.warningSeconds = Set.of(480);
    c.announcements.warningMessage = "GLOBAL_WARN";
    c.announcements.resultMessage = "GLOBAL_DONE {items} {pokemon}";
    c.safari.announcements.warningSeconds = Set.of(1);
    c.safari.announcements.warningMessage = "SAFARI_WARN";
    c.safari.announcements.resultMessage = "SAFARI_DONE {items} {pokemon}";
    service.reload(c);
    var localItem = item(local, 2, 2);
    var globalItem = item(s.overworld(), 2, 2);
    var farItem = item(local, 100, 100);
    var localWild = wild(local, inside, 3, 3);
    var globalWild = wild(s.overworld(), outside, 3, 3);
    var owned = wild(local, inside, 4, 4);
    Cobblemon.INSTANCE.getStorage().getParty(inside).add(owned.getPokemon());
    var battle = wild(local, inside, 5, 5);
    battle.setBattleId(UUID.randomUUID());
    var capture = wild(local, inside, 6, 6);
    capture.getBusyLocks().add(new Object());
    var raid = wild(local, inside, 7, 7);
    raid.addTag("shirokai_raids_boss");
    var npc = wild(local, inside, 8, 8);
    npc.setPersistenceRequired();
    npc.setNoAi(true);
    var shiny = wild(local, inside, 9, 9);
    shiny.getPokemon().setShiny(true);
    deferred =
        () -> {
          service.reload(c);
          for (int i = 0; i < 2399; i++) service.tick();
          check(
              !localItem.isRemoved() && !localWild.isRemoved(),
              "Safari cleanup does not run before 120 seconds of Clear ticks");
          service.tick();
          check(
              localItem.isRemoved() && localWild.isRemoved(),
              "local cleanup removes eligible ground items and natural wild Pokemon at 120"
                  + " seconds");
          check(
              !globalItem.isRemoved() && !globalWild.isRemoved() && !farItem.isRemoved(),
              "local cleanup leaves other dimensions and outside cuboid intact");
          check(
              service.remainingSeconds() == 480,
              "local cleanup does not reset the configured 600-second global timer");
          check(
              service.safariRemainingSeconds() == 120,
              "regional timer resets independently to 120 seconds");
          check(
              !owned.isRemoved() && !battle.isRemoved() && !capture.isRemoved(),
              "real owned Pokemon, battle-marked and capture-busy Pokemon are preserved");
          check(
              !raid.isRemoved() && !npc.isRemoved(),
              "raid and persistent NPC Pokemon are preserved");
          check(!shiny.isRemoved(), "existing global shiny protection is retained inside Safari");
          var inMessages = (Capture) inside.connection;
          var outMessages = (Capture) outside.connection;
          var farMessages = (Capture) far.connection;
          check(
              inMessages.chat("SAFARI_WARN") && inMessages.chat("SAFARI_DONE"),
              "Safari warnings and results reach physical local occupants");
          check(
              !outMessages.chat("SAFARI_WARN")
                  && !outMessages.chat("SAFARI_DONE")
                  && !farMessages.chat("SAFARI_WARN"),
              "Safari announcements do not leak to other dimensions or outside cuboid");
          check(
              !inMessages.chat("GLOBAL_WARN") && outMessages.chat("GLOBAL_WARN"),
              "global warning goes outside Safari only");
          var newLocal = item(local, 2, 3);
          c.worlds.excludedDimensions.clear();
          service.reload(c);
          // No human client acknowledges the remote chunk in this fixture; invoke the original
          // idempotent entity-load sink for this eligible far-away entity before the outside pass.
          service.loaded(farItem);
          check(
              service.start(ClearService.Mode.ALL, false),
              "global cleanup can start independently");
          for (int i = 0; i < 5 && service.busy(); i++) service.tick();
          check(
              globalItem.isRemoved() && globalWild.isRemoved() && farItem.isRemoved(),
              "global cleanup still removes outside entities, including same Multiworld dimension"
                  + " outside Safari; globalItem="
                  + globalItem.isRemoved()
                  + " globalWild="
                  + globalWild.isRemoved()
                  + " farItem="
                  + farItem.isRemoved()
                  + " reason="
                  + Eligibility.reason(farItem, c, new BattleGuard()));
          check(!newLocal.isRemoved(), "global cleanup never removes inside Safari");
          check(
              !inMessages.chat("GLOBAL_DONE") && outMessages.chat("GLOBAL_DONE"),
              "global result is excluded from Safari occupants");
          var moved = item(local, 2, 4);
          check(
              service.startSafari(ClearService.Mode.ALL, false, null),
              "manual test can queue regional pass");
          moved.setPos(100, 200, 100);
          for (int i = 0; i < 10 && service.busy(); i++) service.tick();
          check(
              !moved.isRemoved(),
              "entity that leaves Safari before removal is rechecked and preserved");
          c.performance.maxEntitiesPerBatch = 1;
          service.reload(c);
          var first = item(local, 2, 5);
          var second = item(s.overworld(), 2, 5);
          service.start(ClearService.Mode.ALL, false);
          service.startSafari(ClearService.Mode.ALL, false, null);
          service.tick();
          check(
              (first.isRemoved() ? 1 : 0) + (second.isRemoved() ? 1 : 0) <= 1,
              "simultaneous global and local passes share the configured per-tick batch budget");
          for (int i = 0; i < 100 && service.busy(); i++) service.tick();
          check(
              first.isRemoved() && second.isRemoved(),
              "both regional and global jobs finish without starvation");
        };
    resumeAt = s.getTickCount() + 10;
  }

  private void finish(MinecraftServer s, boolean passed, String error) {
    try {
      var result = new LinkedHashMap<String, Object>();
      result.put("passed", passed);
      result.put("checks", checks);
      result.put("error", error);
      result.put("realClients", 0);
      Files.writeString(
          Path.of("clear-safari-result.json"),
          new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
    } catch (Exception e) {
      e.printStackTrace();
    }
    s.halt(false);
  }
}
