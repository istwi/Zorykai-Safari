package dev.shirokai.safari;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.*;
import net.minecraft.world.BossEvent;

public final class SafariHud {
  private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
  private static final Map<UUID, Set<UUID>> HIDDEN = new HashMap<>();
  private static final Set<UUID> OWN = new HashSet<>();
  private static boolean controlPacket;

  private SafariHud() {}

  public static void register() {
    ServerTickEvents.END_SERVER_TICK.register(
        server -> {
          var mod = SafariMod.INSTANCE;
          if (mod == null || mod.data == null || mod.messages == null) return;
          for (var p : server.getPlayerList().getPlayers()) sync(mod, p);
        });
    ServerPlayConnectionEvents.DISCONNECT.register(
        (handler, server) -> {
          var id = handler.getPlayer().getUUID();
          var bar = BARS.remove(id);
          if (bar != null) {
            OWN.remove(bar.getId());
            bar.removeAllPlayers();
          }
          HIDDEN.remove(id);
        });
    ServerLifecycleEvents.SERVER_STOPPED.register(
        server -> {
          BARS.clear();
          HIDDEN.clear();
          OWN.clear();
        });
  }

  public static boolean suppress(ServerPlayer p, UUID bar) {
    if (controlPacket || OWN.contains(bar)) return false;
    var mod = SafariMod.INSTANCE;
    if (mod == null || !mod.active(p)) return false;
    boolean raid = RaidHudBridge.bars().stream().anyMatch(b -> b.getId().equals(bar));
    return raid;
  }

  private static void sync(SafariMod mod, ServerPlayer p) {
    if (!mod.active(p)) {
      var bar = BARS.remove(p.getUUID());
      if (bar != null) {
        bar.removeAllPlayers();
        OWN.remove(bar.getId());
      }
      var hidden = HIDDEN.remove(p.getUUID());
      if (hidden != null)
        for (var raid : RaidHudBridge.bars())
          if (hidden.contains(raid.getId()) && raid.getPlayers().contains(p))
            p.connection.send(ClientboundBossEventPacket.createAddPacket(raid));
      return;
    }
    var hidden = HIDDEN.computeIfAbsent(p.getUUID(), key -> new HashSet<>());
    for (var raid : RaidHudBridge.bars())
      if (raid.getPlayers().contains(p) && hidden.add(raid.getId())) {
        controlPacket = true;
        try {
          p.connection.send(ClientboundBossEventPacket.createRemovePacket(raid.getId()));
        } finally {
          controlPacket = false;
        }
      }
    var bar = BARS.get(p.getUUID());
    if (bar == null) {
      bar =
          new ServerBossEvent(
              net.minecraft.network.chat.Component.literal("Safari"),
              BossEvent.BossBarColor.GREEN,
              BossEvent.BossBarOverlay.PROGRESS);
      OWN.add(bar.getId());
      BARS.put(p.getUUID(), bar);
      bar.addPlayer(p);
    }
    var v = mod.visit(p);
    var event = mod.data.event;
    long now = System.currentTimeMillis();
    var vars = new HashMap<>(Announcements.variables(mod));
    long available = Math.max(0, Math.min(v.remaining(), v.cycleEnd - now));
    vars.put("tempo", mod.format(available));
    vars.put("bolas", Integer.toString(Balls.count(p, v)));
    boolean activeEvent = event.running(now);
    bar.setName(
        ColorText.parse(
            Messages.render(activeEvent ? mod.messages.eventBar : mod.messages.safariBar, vars)));
    bar.setColor(activeEvent ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.GREEN);
    double progress =
        activeEvent
            ? (double) (event.ends - now)
                / Math.max(1, event.starts > 0 ? event.ends - event.starts : event.ends - now)
            : (double) available / Math.max(1, v.budgetMillis);
    bar.setProgress((float) Math.max(0, Math.min(1, progress)));
  }

  public static ServerBossEvent bar(UUID player) {
    return BARS.get(player);
  }
}
