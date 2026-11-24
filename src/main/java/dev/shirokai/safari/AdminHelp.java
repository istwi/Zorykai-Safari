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
          "/safariadmin missoes reload",
          "/safariadmin pontos NomeDoPlayer",
          "/safariadmin pontos NomeDoPlayer 100",
          "/safariadmin entrega confirmar NomeDoPlayer",
          "/safariadmin entrega cancelar NomeDoPlayer",
          "/safariadmin regiao criar floresta",
          "/safariadmin regiao remover floresta",
          "/safariadmin regiao listar",
          "/safariadmin entrada",
          "/safariadmin saida",
          "/safariadmin modo on",
          "/safariadmin modo off",
          "/safariadmin preco 10000",
          "/safariadmin moeda impactor:dollars",
          "/safariadmin economia NomeDoPlayer",
          "/safariadmin tempo NomeDoPlayer 30",
          "/safariadmin evento spawn 2 60",
          "/safariadmin evento shiny 4 60",
          "/safariadmin evento forma cobblemon:rattata alolan 30 60",
          "/safariadmin evento tempo 30 60",
          "/safariadmin evento status",
          "/safariadmin evento automatico status",
          "/safariadmin evento automatico ligar",
          "/safariadmin evento automatico pausar",
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
                "Clique para preencher. Shiny usa multiplicador da chance base; formas usam %. Use"
                    + " /safariadmin evento status para conferir."),
        false);
    return 1;
  }
}
