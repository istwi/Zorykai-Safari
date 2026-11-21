package dev.shirokai.safari.mixin;

import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundBossEventPacket.class)
public interface BossPacketAccess {
  @Accessor("id")
  java.util.UUID safariId();
}
