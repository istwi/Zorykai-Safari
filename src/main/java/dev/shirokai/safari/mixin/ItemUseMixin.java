package dev.shirokai.safari.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.shirokai.safari.SafariMod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PokemonEntity.class, remap = false)
public abstract class ItemUseMixin {
  @Inject(
      method = {"offerHeldItem", "offerCosmeticItem"},
      at = @At("HEAD"),
      cancellable = true,
      remap = false)
  private void safariHeld(Player player, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
    if (SafariMod.lockedPlayer(player)) cir.setReturnValue(false);
  }
}
