package dev.shirokai.clear;

import com.cobblemon.mod.common.api.spawning.SpawnCause;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawner;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.Locale;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;

public final class Eligibility {
  private Eligibility() {}

  public static String reason(Entity entity, ClearConfig c, BattleGuard guard) {
    PokemonEntity p;
    block36:
    {
      block35:
      {
        ServerLevel level;
        block34:
        {
          block33:
          {
            Level level2 = entity.level();
            if (!(level2 instanceof ServerLevel)) break block33;
            level = (ServerLevel) level2;
            if (!entity.isRemoved() && entity.isAlive()) break block34;
          }
          return "inativa";
        }
        if (!level.getChunkSource().hasChunk(entity.chunkPosition().x, entity.chunkPosition().z)) {
          return "chunk descarregado";
        }
        if (!c.worlds.accepts(level.dimension().location().toString())) {
          return "dimens\u00e3o exclu\u00edda";
        }
        if (entity instanceof ItemEntity) {
          ItemEntity item = (ItemEntity) entity;
          if (entity.getClass() == ItemEntity.class) {
            if (!c.clearItems) {
              return "itens desativados";
            }
            if ((long) entity.tickCount < (long) c.items.minimumAgeSeconds * 20L) {
              return "item recente";
            }
            if (item.getItem().isEmpty()) {
              return "item vazio";
            }
            if (c.items.protectNamedItems
                && (item.hasCustomName() || item.getItem().has(DataComponents.CUSTOM_NAME))) {
              return "item nomeado";
            }
            if (c.items.excludedItemIds.contains(
                BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString())) {
              return "item exclu\u00eddo";
            }
            if (entity.getTags().stream().anyMatch(Eligibility::specialTag)) {
              return "item protegido";
            }
            return null;
          }
        }
        if (!(entity instanceof PokemonEntity)) break block35;
        p = (PokemonEntity) entity;
        if (entity.getClass() == PokemonEntity.class
            && BuiltInRegistries.ENTITY_TYPE
                .getKey(entity.getType())
                .toString()
                .equals("cobblemon:pokemon")) break block36;
      }
      return "tipo protegido";
    }
    if (!c.clearWildPokemon) {
      return "Pok\u00e9mon desativados";
    }
    try {
      Pokemon pokemon = p.getPokemon();
      if (guard == null
          || guard.protects(p.getUUID(), pokemon.getUuid())
          || p.getBattleId() != null
          || p.isBattling()
          || p.isBusy()) {
        return "batalha ou estado incerto";
      }
      if (!pokemon.isWild()
          || pokemon.isPlayerOwned()
          || pokemon.isNPCOwned()
          || p.getOwnerUUID() != null) {
        return "dono ou treinador";
      }
      if (p.getTethering() != null
          || p.isBattleClone()
          || p.isUncatchable()
          || p.isPersistenceRequired()
          || p.isInvulnerable()
          || p.isNoAi()
          || p.isPassenger()
          || p.isVehicle()
          || p.isLeashed()) {
        return "v\u00ednculo especial";
      }
      if (!pokemon.getPersistentData().isEmpty()) {
        return "metadados de integra\u00e7\u00e3o";
      }
      if (ProtectionApi.protectedByIntegration(p)) {
        return "integra\u00e7\u00e3o protegida";
      }
      if (p.getTags().stream().anyMatch(Eligibility::specialTag)) {
        return "raid/evento/prote\u00e7\u00e3o";
      }
      if (c.pokemon.protectTaggedPokemon && !p.getTags().isEmpty()) {
        return "tag";
      }
      SpawnCause cause = p.getSpawnCause();
      if (cause == null
          || cause.getClass() != SpawnCause.class
          || cause.getSpawner().getClass() != PlayerSpawner.class) {
        return "origem natural n\u00e3o confirmada";
      }
      if ((long) p.tickCount < (long) c.pokemon.minimumAgeSeconds * 20L) {
        return "Pok\u00e9mon recente";
      }
      if (c.pokemon.protectLegendary && pokemon.isLegendary()) {
        return "lend\u00e1rio";
      }
      if (c.pokemon.protectMythical && pokemon.isMythical()) {
        return "m\u00edtico";
      }
      if (c.pokemon.protectShiny && pokemon.getShiny()) {
        return "shiny";
      }
      if (c.pokemon.protectNamedPokemon && (p.hasCustomName() || pokemon.getNickname() != null)) {
        return "nomeado";
      }
      ResourceLocation species = pokemon.getSpecies().getResourceIdentifier();
      if (c.pokemon.excludedSpecies.contains(species.toString())
          || c.pokemon.excludedSpecies.contains(species.getPath())) {
        return "esp\u00e9cie exclu\u00edda";
      }
      return null;
    } catch (LinkageError | RuntimeException e) {
      return "falha ao confirmar prote\u00e7\u00e3o";
    }
  }

  private static boolean specialTag(String tag) {
    String s = tag.toLowerCase(Locale.ROOT);
    return s.contains("raid")
        || s.contains("boss")
        || s.contains("npc")
        || s.contains("trainer")
        || s.contains("gym")
        || s.contains("breed")
        || s.contains("pasture")
        || s.contains("event")
        || s.contains("protect")
        || s.contains("quest");
  }
}
