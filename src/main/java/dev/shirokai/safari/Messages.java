package dev.shirokai.safari;

import java.nio.file.*;
import java.util.*;

public final class Messages {
  public int schemaVersion = 1;
  public boolean chatEnabled = true, titlesEnabled = true;
  public String audience = "ALL";
  public int fadeInTicks = 10, stayTicks = 80, fadeOutTicks = 20;
  public List<String> eventStartChat =
      List.of(
          "",
          "&2&l✦ EVENTO SAFARI ✦",
          "&f{evento}",
          "&7Duração: &e{duracao}&7. Use &a/safari&7 para participar!",
          "");
  public String eventStartTitle = "&2&lEVENTO SAFARI",
      eventStartSubtitle = "&f{evento} &8• &e{duracao}";
  public List<String> eventEndChat =
      List.of("&2&lSAFARI &8» &7O evento terminou. Os efeitos normais voltaram.");
  public String eventEndTitle = "&2&lSAFARI", eventEndSubtitle = "&fEvento encerrado";
  public String safariBar =
      "&2&lSAFARI &8| &fTempo &e{tempo} &8| &fBolas &a{bolas} &8| &fSpawn &b{spawn}x";
  public String eventBar = "&d&lEVENTO SAFARI &8| &f{evento} &8| &e{tempo_evento}";

  public void validate() {
    if (schemaVersion != 1
        || !Set.of("ALL", "SAFARI").contains(audience)
        || fadeInTicks < 0
        || fadeInTicks > 1200
        || stayTicks < 1
        || stayTicks > 1200
        || fadeOutTicks < 0
        || fadeOutTicks > 1200)
      throw new IllegalArgumentException("Mensagens: configuração inválida");
    for (var text :
        List.of(
            eventStartTitle,
            eventStartSubtitle,
            eventEndTitle,
            eventEndSubtitle,
            safariBar,
            eventBar))
      if (text.length() > 1000) throw new IllegalArgumentException("Mensagem longa demais");
    for (var lines : List.of(eventStartChat, eventEndChat))
      if (lines.size() > 20 || lines.stream().anyMatch(x -> x == null || x.length() > 1000))
        throw new IllegalArgumentException("Linhas de anúncio inválidas");
  }

  public static Messages load(Path file) throws java.io.IOException {
    if (!Files.exists(file)) Storage.write(file, new Messages());
    var m = Storage.read(file, Messages.class);
    m.validate();
    return m;
  }

  public static String render(String template, Map<String, String> variables) {
    String text = template;
    for (var e : variables.entrySet()) text = text.replace("{" + e.getKey() + "}", e.getValue());
    return text;
  }
}
