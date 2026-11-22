package dev.shirokai.safari;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.*;

public final class AdminHelp {
  private AdminHelp() {}

  public static int show(CommandSourceStack source) {
    source.sendSuccess(
        () -> Component.literal("✦ SAFARI • Administração").withStyle(ChatFormatting.GOLD), false);
    for (String command :
        new String[] {
          "/safariadmin menu",
          "/safariadmin mensagens",
          "/safariadmin pos1",
          "/safariadmin pos2",
          "/safariadmin criar",
          "/safariadmin delete",
          "/safariadmin entrada",
          "/safariadmin saida",
          "/safariadmin modo on",
          "/safariadmin modo off",
          "/safariadmin preco 10000",
          "/safariadmin moeda impactor:dollars",
          "/safariadmin economia NomeDoPlayer",
          "/safariadmin tempo NomeDoPlayer 30",
          "/safariadmin evento spawn 2 60",
          "/safariadmin evento shiny 1 60",
          "/safariadmin evento forma cobblemon:rattata alolan 30 60",
          "/safariadmin evento tempo 30 60",
          "/safariadmin evento status",
          "/safariadmin evento parar",
          "/safariadmin reload"
        })
      source.sendSuccess(
          () ->
              Component.literal(command)
                  .withStyle(
                      s ->
                          s.withColor(ChatFormatting.AQUA)
                              .withClickEvent(
                                  new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))),
          false);
    source.sendSuccess(
        () ->
            Component.literal(
                "Clique para preencher. Tempos dos eventos em minutos; chances em %. Use"
                    + " /safariadmin evento status para conferir."),
        false);
    return 1;
  }
}
