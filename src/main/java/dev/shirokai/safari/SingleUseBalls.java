package dev.shirokai.safari;

import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerPlayer;

public final class SingleUseBalls {
  public static final String TAG = "shirokai_safari_single_use";

  private SingleUseBalls() {}

  public static void mark(EmptyPokeBallEntity ball) {
    if (ball.getPokeBall().getName().toString().equals("cobblemon:safari_ball")
        && ball.getOwner() instanceof ServerPlayer player
        && SafariMod.INSTANCE != null
        && SafariMod.INSTANCE.active(player)) ball.addTag(TAG);
  }

  public static void register() {
    ServerEntityEvents.ENTITY_LOAD.register(
        (entity, world) -> {
          if (entity instanceof EmptyPokeBallEntity ball) mark(ball);
        });
  }
}
