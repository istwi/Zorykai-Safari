package dev.shirokai.clear.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.BattleRegistry;
import dev.shirokai.clear.BattleGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
    value = {BattleRegistry.class},
    remap = false)
public abstract class BattleEpochMixin {
  @Inject(
      method = {"startShowdown"},
      at = {@At(value = "HEAD")},
      remap = false)
  private void shirokaiBattleStarting(PokemonBattle battle, CallbackInfo ci) {
    BattleGuard.battleStarting();
  }
}
