package dev.shirokai.clear;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;

public final class LocalText {
  private LocalText() {}

  public static Component parse(String text) {
    var result = Component.empty();
    var style = Style.EMPTY;
    var buffer = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      Style next = null;
      int consumed = 0;
      if ((ch == '&' || ch == '§') && i + 1 < text.length()) {
        char code = text.charAt(i + 1);
        if (code == '#' && i + 7 < text.length()) {
          try {
            next = Style.EMPTY.withColor(Integer.parseInt(text.substring(i + 2, i + 8), 16));
            consumed = 7;
          } catch (NumberFormatException ignored) {
          }
        } else {
          var format = ChatFormatting.getByCode(code);
          if (format != null) {
            next =
                format == ChatFormatting.RESET
                    ? Style.EMPTY
                    : format.isColor()
                        ? Style.EMPTY.applyFormat(format)
                        : style.applyFormat(format);
            consumed = 1;
          }
        }
      }
      if (next != null) {
        if (!buffer.isEmpty()) {
          result.append(Component.literal(buffer.toString()).setStyle(style));
          buffer.setLength(0);
        }
        style = next;
        i += consumed;
      } else buffer.append(ch);
    }
    if (!buffer.isEmpty()) result.append(Component.literal(buffer.toString()).setStyle(style));
    return result;
  }
}
