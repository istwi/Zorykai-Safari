package dev.shirokai.safari.mixin;

import dev.shirokai.safari.SafariHud;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.network.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class BossPacketGuardMixin {
  @Inject(
      method = "send(Lnet/minecraft/network/protocol/Packet;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void safariBoss(Packet<?> packet, CallbackInfo ci) {
    if ((Object) this instanceof ServerGamePacketListenerImpl game
        && packet instanceof ClientboundBossEventPacket
        && SafariHud.suppress(game.player, ((BossPacketAccess) packet).safariId())) ci.cancel();
  }
}
