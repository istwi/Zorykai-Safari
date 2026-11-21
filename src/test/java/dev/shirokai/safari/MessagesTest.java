package dev.shirokai.safari;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MessagesTest {
  @Test
  void colorsAndBoldUseAmpersand() {
    var text = ColorText.parse("&6&lEvento &#00FF00Shiny&r ok");
    assertEquals("Evento Shiny ok", text.getString());
    assertEquals(0xFFAA00, text.getSiblings().getFirst().getStyle().getColor().getValue());
    assertTrue(text.getSiblings().getFirst().getStyle().isBold());
    assertEquals(0x00FF00, text.getSiblings().get(1).getStyle().getColor().getValue());
    assertFalse(text.getSiblings().get(1).getStyle().isBold());
  }

  @Test
  void placeholdersRenderWithoutRemovingFormatting() {
    assertEquals(
        "&aShiny por 1h00m",
        Messages.render("&a{evento} por {duracao}", Map.of("evento", "Shiny", "duracao", "1h00m")));
  }

  @Test
  void defaultsAreValidAndAudienceIsValidated() {
    var config = new Messages();
    config.validate();
    config.audience = "EVERYWHERE";
    assertThrows(IllegalArgumentException.class, config::validate);
  }
}
