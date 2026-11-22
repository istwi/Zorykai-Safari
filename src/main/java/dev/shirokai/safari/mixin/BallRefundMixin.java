package dev.shirokai.safari.mixin;

import dev.shirokai.safari.SingleUseBalls;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class BallRefundMixin {
  @Inject(
      method =
          "spawnAtLocation(Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/entity/item/ItemEntity;",
      at = @At("HEAD"),
      cancellable = true)
  private void safariRefund(ItemLike item, CallbackInfoReturnable<ItemEntity> cir) {
    if (((Entity) (Object) this).getTags().contains(SingleUseBalls.TAG)) cir.setReturnValue(null);
  }

  @Inject(
      method =
          "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;",
      at = @At("HEAD"),
      cancellable = true)
  private void safariRefundStack(
      ItemStack item, float offset, CallbackInfoReturnable<ItemEntity> cir) {
    if (((Entity) (Object) this).getTags().contains(SingleUseBalls.TAG)) cir.setReturnValue(null);
  }
}
