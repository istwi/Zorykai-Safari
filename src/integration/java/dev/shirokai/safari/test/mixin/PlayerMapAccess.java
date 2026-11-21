package dev.shirokai.safari.test.mixin;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerList.class)
public interface PlayerMapAccess {
  @Accessor("players")
  List<ServerPlayer> safariPlayers();

  @Accessor("playersByUUID")
  Map<UUID, ServerPlayer> safariMap();
}
