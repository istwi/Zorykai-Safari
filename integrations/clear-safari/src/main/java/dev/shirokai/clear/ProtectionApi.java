package dev.shirokai.clear;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

public final class ProtectionApi {
  private static final List<Predicate<PokemonEntity>> VETOES =
      new CopyOnWriteArrayList<Predicate<PokemonEntity>>();

  private ProtectionApi() {}

  public static void register(Predicate<PokemonEntity> veto) {
    VETOES.add(Objects.requireNonNull(veto));
  }

  public static boolean protectedByIntegration(PokemonEntity entity) {
    for (Predicate<PokemonEntity> veto : VETOES) {
      try {
        if (!veto.test(entity)) continue;
        return true;
      } catch (LinkageError | RuntimeException e) {
        return true;
      }
    }
    return false;
  }
}
