package dev.shirokai.safari.mixin;

import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import dev.shirokai.safari.SingleUseBalls;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EmptyPokeBallEntity.class, remap = false)
public abstract class BallSingleUseMixin {
  @Inject(method = "<init>*", at = @At("RETURN"), remap = false)
  private void safariConstructed(CallbackInfo ci) {
    SingleUseBalls.mark((EmptyPokeBallEntity) (Object) this);
  }
}
