package dev.shirokai.safari.mixin;

import com.mojang.brigadier.ParseResults;
import dev.shirokai.safari.SafariMod;
import net.minecraft.commands.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
public abstract class CommandGuardMixin {
  @Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
  private void safariCommand(
      ParseResults<CommandSourceStack> parse, String command, CallbackInfo ci) {
    if (SafariMod.commands(parse.getContext().getSource(), command)) ci.cancel();
  }
}
