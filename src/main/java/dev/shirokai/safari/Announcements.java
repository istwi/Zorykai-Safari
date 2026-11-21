package dev.shirokai.safari;

import java.util.*;
import net.minecraft.network.protocol.game.*;

public final class Announcements {
  private Announcements() {}

  public static void event(SafariMod mod, boolean start) {
    var config = mod.messages;
    var vars = variables(mod);
    for (var p : mod.server.getPlayerList().getPlayers()) {
      if (config.audience.equals("SAFARI") && !mod.active(p)) continue;
      if (config.chatEnabled)
        for (String line : start ? config.eventStartChat : config.eventEndChat)
          p.sendSystemMessage(ColorText.parse(Messages.render(line, vars)));
      if (config.titlesEnabled) {
        p.connection.send(
            new ClientboundSetTitlesAnimationPacket(
                config.fadeInTicks, config.stayTicks, config.fadeOutTicks));
        p.connection.send(
            new ClientboundSetSubtitleTextPacket(
                ColorText.parse(
                    Messages.render(
                        start ? config.eventStartSubtitle : config.eventEndSubtitle, vars))));
        p.connection.send(
            new ClientboundSetTitleTextPacket(
                ColorText.parse(
                    Messages.render(start ? config.eventStartTitle : config.eventEndTitle, vars))));
      }
    }
  }

  public static Map<String, String> variables(SafariMod mod) {
    var e = mod.data.event;
    long remaining = Math.max(0, e.ends - System.currentTimeMillis());
    return Map.of(
        "evento",
        mod.eventEffects(),
        "duracao",
        mod.format(remaining),
        "tempo_evento",
        mod.format(remaining),
        "spawn",
        Double.toString(
            Math.min(
                10,
                mod.config.spawnMultiplier
                    * (e.running(System.currentTimeMillis()) ? e.spawn : 1))),
        "shiny",
        Double.toString(e.shinyChance * 100),
        "formas",
        e.forms.toString(),
        "bonus_tempo",
        mod.format(e.bonusMillis));
  }
}
