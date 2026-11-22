package dev.shirokai.safari.test;

import com.mojang.authlib.GameProfile;
import dev.shirokai.safari.*;
import dev.shirokai.safari.test.mixin.PlayerMapAccess;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.impactdev.impactor.api.economy.*;
import net.kyori.adventure.key.Key;
import net.luckperms.api.*;
import net.luckperms.api.node.types.InheritanceNode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

public final class EconomyIntegration implements ModInitializer {
  private boolean started;
  private final List<String> checks = new ArrayList<>();

  public void onInitialize() {
    ServerTickEvents.END_SERVER_TICK.register(
        s -> {
          if (!started && s.getTickCount() > 30) {
            started = true;
            new Thread(() -> test(s), "safari-economy-test").start();
          }
        });
  }

  private static <T> T main(MinecraftServer s, Callable<T> task) throws Exception {
    var f = new CompletableFuture<T>();
    s.execute(
        () -> {
          try {
            f.complete(task.call());
          } catch (Throwable e) {
            f.completeExceptionally(e);
          }
        });
    return f.get(30, TimeUnit.SECONDS);
  }

  private void check(boolean result, String text) {
    if (!result) throw new AssertionError(text);
    checks.add(text);
  }

  private void test(MinecraftServer s) {
    boolean passed = false;
    String error = null;
    try {
      var m = SafariMod.INSTANCE;
      UUID id = UUID.fromString("7607c056-ad53-4b8d-bc4d-2342c9f6d735");
      var lp = LuckPermsProvider.get();
      var user = lp.getUserManager().loadUser(id, "SafariBuyer").get(30, TimeUnit.SECONDS);
      var p =
          main(
              s,
              () -> {
                var w = s.overworld();
                for (int x = -12; x <= 12; x++)
                  for (int z = -12; z <= 12; z++)
                    w.setBlock(new BlockPos(x, 199, z), Blocks.STONE.defaultBlockState(), 3);
                for (int x = 98; x <= 103; x++)
                  for (int z = 98; z <= 103; z++)
                    w.setBlock(new BlockPos(x, 199, z), Blocks.STONE.defaultBlockState(), 3);
                m.config.area = new Config.Area("minecraft:overworld", -10, 199, -10, 10, 210, 10);
                m.config.entrance = new Config.Position("minecraft:overworld", 0.5, 200, 0.5, 0, 0);
                m.config.lobby =
                    new Config.Position("minecraft:overworld", 100.5, 200, 100.5, 0, 0);
                var fake = FakePlayer.get(w, new GameProfile(id, "SafariBuyer"));
                fake.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
                fake.setPos(100.5, 200, 100.5);
                fake.connection = new SafariIntegration.Capture(s, fake);
                var access = (PlayerMapAccess) s.getPlayerList();
                access.safariPlayers().add(fake);
                access.safariMap().put(id, fake);
                return fake;
              });
      for (String group : List.of("magma", "aqua", "plasma", "galactic")) {
        lp.getGroupManager().createAndLoadGroup(group).get(30, TimeUnit.SECONDS);
        user.data().clear();
        user.data().add(InheritanceNode.builder(group).build());
        lp.getUserManager().saveUser(user).get(30, TimeUnit.SECONDS);
        check(
            main(s, () -> Permissions.seconds(p, m.config)).equals(m.config.vipSeconds.get(group)),
            "real LuckPerms group " + group + " receives configured quota");
      }
      main(
          s,
          () -> {
            m.enter(p);
            return null;
          });
      var economy = EconomyService.instance();
      var currency = economy.currencies().currency(Key.key(m.config.currency)).orElseThrow();
      var account = economy.account(currency, id).get(30, TimeUnit.SECONDS);
      account.set(new BigDecimal("50000"));
      economy.save(account).get(30, TimeUnit.SECONDS);
      main(
          s,
          () -> {
            m.confirmBuy(p);
            m.completePurchase(p);
            return null;
          });
      waitFor(s, () -> m.visit(p).purchase == null);
      check(
          account.balance().compareTo(new BigDecimal("40000")) == 0,
          "actual Impactor debited exactly configured 10000");
      check(
          main(s, () -> Balls.count(p, m.visit(p))) == 64,
          "purchase delivered exactly 32 additional bound balls");
      main(
          s,
          () -> {
            m.exit(p);
            m.enter(p);
            return null;
          });
      check(
          main(s, () -> Balls.count(p, m.visit(p))) == 64,
          "purchased ball balance survives exit and reentry");
      boolean duplicate = false;
      try {
        main(
            s,
            () -> {
              m.confirmBuy(p);
              m.completePurchase(p);
              return null;
            });
      } catch (Exception expected) {
        duplicate = true;
      }
      check(
          duplicate && account.balance().compareTo(new BigDecimal("40000")) == 0,
          "one additional purchase per cycle survives exit and reentry without another debit");
      main(
          s,
          () -> {
            m.exit(p);
            m.visit(p).cycleEnd = System.currentTimeMillis();
            m.enter(p);
            return null;
          });
      check(
          main(s, () -> !m.visit(p).bundlePurchased && Balls.count(p, m.visit(p)) == 32),
          "entry quota and purchase availability reset together on next cycle");
      account.set(BigDecimal.ZERO);
      economy.save(account).get(30, TimeUnit.SECONDS);
      main(
          s,
          () -> {
            m.confirmBuy(p);
            m.completePurchase(p);
            return null;
          });
      waitFor(s, () -> m.visit(p).purchase == null);
      check(
          main(s, () -> Balls.count(p, m.visit(p))) == 32,
          "insufficient funds does not grant balls");
      check(account.balance().signum() == 0, "insufficient funds does not make negative balance");
      var capture = (SafariIntegration.Capture) p.connection;
      check(
          main(
              s,
              () ->
                  capture.packets.stream()
                      .anyMatch(
                          packet ->
                              packet
                                      instanceof
                                      net.minecraft.network.protocol.game
                                                  .ClientboundSystemChatPacket
                                              chat
                                  && chat.content().getString().contains("saldo insuficiente")
                                  && chat.content().getString().contains("impactor:dollars"))),
          "refusal explains insufficient funds, currency and available balance");
      check(
          main(
                  s,
                  () ->
                      s.getCommands()
                          .getDispatcher()
                          .execute(
                              "safariadmin moeda impactor:dollars",
                              p.createCommandSourceStack().withPermission(4)))
              == 1,
          "currency command accepts actual namespaced Impactor currency");
      boolean expired = false;
      try {
        main(
            s,
            () -> {
              m.completePurchase(p);
              return null;
            });
      } catch (Exception expected) {
        expired = true;
      }
      check(expired, "confirmation cannot be reused to charge twice");
      main(
          s,
          () -> {
            m.config.bundlePrice = java.math.BigDecimal.ZERO;
            m.confirmBuy(p);
            m.completePurchase(p);
            return null;
          });
      check(
          main(s, () -> Balls.count(p, m.visit(p))) == 64 && account.balance().signum() == 0,
          "zero-price package grants balls without invalid zero withdrawal");
      check(
          main(s, () -> m.visit(p).bundlePurchased),
          "free bundle also consumes the single additional purchase of the cycle");
      passed = true;
    } catch (Throwable e) {
      e.printStackTrace();
      error = e.toString();
    } finally {
      final boolean ok = passed;
      final String failure = error;
      s.execute(
          () -> {
            try {
              var result = new LinkedHashMap<String, Object>();
              result.put("passed", ok);
              result.put("checks", checks);
              result.put("error", failure);
              Files.writeString(Path.of("safari-economy-result.json"), Storage.JSON.toJson(result));
              System.out.println("SAFARI_ECONOMY " + Storage.JSON.toJson(result));
            } catch (Exception e) {
              e.printStackTrace();
            }
            s.halt(false);
          });
    }
  }

  @FunctionalInterface
  interface Condition {
    boolean check();
  }

  private void waitFor(MinecraftServer s, Condition condition) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      if (main(s, condition::check)) return;
      Thread.sleep(100);
    }
    throw new TimeoutException("Purchase did not settle");
  }
}
