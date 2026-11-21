package dev.shirokai.safari.mixin;

import dev.shirokai.safari.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class PickupMixin {
  @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
  private void safariPickup(Player p, CallbackInfo ci) {
    if (Balls.tagged(((ItemEntity) (Object) this).getItem())) ci.cancel();
  }

  @Redirect(
      method = "playerTouch",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
  private boolean safariCollect(
      net.minecraft.world.entity.player.Inventory inventory,
      net.minecraft.world.item.ItemStack stack) {
    return GroundPickup.collect(inventory, stack);
  }
}
