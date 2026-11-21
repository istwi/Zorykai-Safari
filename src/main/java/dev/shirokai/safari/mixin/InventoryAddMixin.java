package dev.shirokai.safari.mixin;

import dev.shirokai.safari.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public abstract class InventoryAddMixin {
  @Inject(
      method = "add(ILnet/minecraft/world/item/ItemStack;)Z",
      at = @At("HEAD"),
      cancellable = true)
  private void safariIncoming(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
    var inventory = (Inventory) (Object) this;
    if (inventory.player instanceof ServerPlayer p
        && SafariMod.lockedPlayer(p)
        && !Balls.owns(stack, p, SafariMod.INSTANCE.visit(p))
        && !GroundPickup.allowed(p, stack)) cir.setReturnValue(false);
  }
}
