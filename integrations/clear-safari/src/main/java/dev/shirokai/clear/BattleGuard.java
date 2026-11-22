package dev.shirokai.clear;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.shirokai.clear.mixin.BattleRegistryAccess;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class BattleGuard {
  private static volatile long epoch;
  private final long capturedEpoch = epoch;
  private final Set<UUID> pokemon = new HashSet<UUID>();
  private final Set<UUID> entities = new HashSet<UUID>();
  private boolean reliable = true;

  public static void battleStarting() {
    ++epoch;
  }

  public BattleGuard() {
    try {
      for (PokemonBattle battle : BattleRegistryAccess.shirokaiBattles().values()) {
        for (BattleActor actor : battle.getActors()) {
          EntityBackedBattleActor backed;
          if (actor instanceof EntityBackedBattleActor
              && (backed = (EntityBackedBattleActor) actor).getEntity() != null) {
            this.entities.add(backed.getEntity().getUUID());
          }
          for (BattlePokemon member : actor.getPokemonList()) {
            this.add(member.getOriginalPokemon());
            this.add(member.getEffectedPokemon());
            if (member.getEntity() == null) continue;
            this.entities.add(member.getEntity().getUUID());
          }
        }
      }
    } catch (LinkageError | RuntimeException failure) {
      this.reliable = false;
    }
  }

  private void add(Pokemon p) {
    if (p == null) {
      this.reliable = false;
      return;
    }
    this.pokemon.add(p.getUuid());
    if (p.getEntity() != null) {
      this.entities.add(p.getEntity().getUUID());
    }
  }

  public boolean protects(UUID entity, UUID id) {
    return !this.reliable
        || this.capturedEpoch != epoch
        || this.entities.contains(entity)
        || this.pokemon.contains(id);
  }

  public boolean reliable() {
    return this.reliable;
  }
}
