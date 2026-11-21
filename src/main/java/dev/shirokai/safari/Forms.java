package dev.shirokai.safari;

import com.cobblemon.mod.common.pokemon.*;
import java.util.Optional;

public final class Forms {
  private Forms() {}

  public static Optional<FormData> find(Species species, String name) {
    return species.getForms().stream()
        .filter(
            f ->
                !f.getAspects().isEmpty()
                    && (f.getName().equalsIgnoreCase(name)
                        || f.getAspects().stream().anyMatch(a -> a.equalsIgnoreCase(name))))
        .findFirst();
  }
}
