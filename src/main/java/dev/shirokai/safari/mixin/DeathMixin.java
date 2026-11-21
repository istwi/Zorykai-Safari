package dev.shirokai.safari.mixin;

import dev.shirokai.safari.SafariMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class DeathMixin {
  @Inject(method = "die", at = @At("HEAD"))
  private void safariDeath(DamageSource source, CallbackInfo ci) {
    SafariMod.beforeDeath((ServerPlayer) (Object) this);
  }
}
