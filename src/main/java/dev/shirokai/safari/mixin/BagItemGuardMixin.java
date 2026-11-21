package dev.shirokai.safari.mixin;

import com.cobblemon.mod.common.battles.*;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import dev.shirokai.safari.SafariMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BagItemActionResponse.class, remap = false)
public abstract class BagItemGuardMixin {
  @Inject(method = "isValid", at = @At("HEAD"), cancellable = true, remap = false)
  private void safariBag(
      ActiveBattlePokemon active,
      ShowdownMoveset moves,
      boolean force,
      CallbackInfoReturnable<Boolean> cir) {
    if (active.getActor() instanceof PlayerBattleActor actor
        && actor.getEntity() != null
        && SafariMod.lockedPlayer(actor.getEntity())) cir.setReturnValue(false);
  }
}
