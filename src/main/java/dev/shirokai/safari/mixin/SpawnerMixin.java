package dev.shirokai.safari.mixin;

import com.cobblemon.mod.common.api.spawning.SpawnCause;
import com.cobblemon.mod.common.api.spawning.spawner.*;
import dev.shirokai.safari.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PlayerSpawner.class, remap = false)
public abstract class SpawnerMixin {
  @Shadow
  public abstract java.util.UUID getUuid();

  @Shadow private float tickTimerMultiplier;

  @Redirect(
      method = "tick",
      at =
          @At(
              value = "FIELD",
              target =
                  "Lcom/cobblemon/mod/common/api/spawning/spawner/PlayerSpawner;tickTimerMultiplier:F"),
      remap = false)
  private float safariRate(PlayerSpawner self) {
    return (float)
        (tickTimerMultiplier
            * (SafariMod.INSTANCE == null ? 1 : SafariMod.INSTANCE.multiplier(getUuid())));
  }

  @Inject(method = "getZoneInput", at = @At("RETURN"), cancellable = true, remap = false)
  private void safariZone(SpawnCause cause, CallbackInfoReturnable<SpawningZoneInput> cir) {
    var m = SafariMod.INSTANCE;
    var z = cir.getReturnValue();
    if (m == null || z == null || !m.eligible(getUuid())) return;
    var a = m.config.area;
    int x = Math.max(a.minX(), z.getBaseX()),
        y = Math.max(a.minY(), z.getBaseY()),
        v = Math.max(a.minZ(), z.getBaseZ());
    int endX = Math.min(a.maxX() + 1, z.getBaseX() + z.getLength()),
        endY = Math.min(a.maxY() + 1, z.getBaseY() + z.getHeight()),
        endZ = Math.min(a.maxZ() + 1, z.getBaseZ() + z.getWidth());
    cir.setReturnValue(
        endX <= x || endY <= y || endZ <= v
            ? null
            : new SpawningZoneInput(
                z.getCause(), z.getWorld(), x, y, v, endX - x, endY - y, endZ - v));
  }
}
