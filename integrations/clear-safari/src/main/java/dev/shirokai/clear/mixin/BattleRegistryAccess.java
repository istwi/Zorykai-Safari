package dev.shirokai.clear.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.BattleRegistry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(
    value = {BattleRegistry.class},
    remap = false)
public interface BattleRegistryAccess {
  @Accessor(value = "battleMap")
  public static ConcurrentHashMap<UUID, PokemonBattle> shirokaiBattles() {
    throw new IllegalStateException("Accessor indispon\u00edvel");
  }
}
