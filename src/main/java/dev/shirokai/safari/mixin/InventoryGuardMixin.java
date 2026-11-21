package dev.shirokai.safari.mixin;

import dev.shirokai.safari.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class InventoryGuardMixin {
  @Shadow public ServerPlayer player;

  @Unique
  private boolean safari$locked() {
    return player.server.isSameThread() && SafariMod.lockedPlayer(player);
  }

  @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
  private void safariClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
    if (!safari$locked() || player.containerMenu instanceof SafariMenu) return;
    ci.cancel();
    player.containerMenu.sendAllDataToRemote();
  }

  @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
  private void safariAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
    if (!safari$locked()) return;
    switch (packet.getAction()) {
      case DROP_ITEM, DROP_ALL_ITEMS, SWAP_ITEM_WITH_OFFHAND -> ci.cancel();
      case START_DESTROY_BLOCK, ABORT_DESTROY_BLOCK, STOP_DESTROY_BLOCK -> {
        player.connection.ackBlockChangesUpTo(packet.getSequence());
        var pos = packet.getPos();
        if (pos.distSqr(player.blockPosition()) <= 4096 && player.serverLevel().hasChunkAt(pos))
          player.connection.send(new ClientboundBlockUpdatePacket(player.serverLevel(), pos));
        ci.cancel();
      }
      default -> {}
    }
  }

  @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
  private void safariCreative(ServerboundSetCreativeModeSlotPacket p, CallbackInfo ci) {
    if (safari$locked()) ci.cancel();
  }

  @Inject(method = "handlePickItem", at = @At("HEAD"), cancellable = true)
  private void safariPick(ServerboundPickItemPacket p, CallbackInfo ci) {
    if (safari$locked()) ci.cancel();
  }

  @Inject(method = "handlePlaceRecipe", at = @At("HEAD"), cancellable = true)
  private void safariRecipe(ServerboundPlaceRecipePacket p, CallbackInfo ci) {
    if (safari$locked()) ci.cancel();
  }
}
