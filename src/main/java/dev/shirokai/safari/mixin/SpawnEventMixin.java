package dev.shirokai.safari.mixin;

import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnAction;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.shirokai.safari.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PokemonSpawnAction.class, remap = false)
public abstract class SpawnEventMixin {
  @Inject(
      method = "createEntity()Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;",
      at = @At("RETURN"),
      remap = false)
  private void safariEvent(CallbackInfoReturnable<PokemonEntity> cir) {
    var m = SafariMod.INSTANCE;
    var entity = cir.getReturnValue();
    if (m == null || m.server == null || entity == null || m.data == null) return;
    var pos = ((PokemonSpawnAction) (Object) this).getSpawnablePosition();
    if (!(pos.getSpawner()
            instanceof com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawner sp)
        || !m.eligible(sp.getUuid())
        || !m.inside(
            pos.getWorld(),
            pos.getPosition().getX(),
            pos.getPosition().getY(),
            pos.getPosition().getZ())) return;
    entity.getPokemon().getPersistentData().putBoolean(Tasks.NATURAL, true);
    SpawnMetrics.prepared(entity);
    var event = m.data.event;
    if (!event.running(System.currentTimeMillis())) return;
    var pokemon = entity.getPokemon();
    var random = entity.getRandom();
    if (event.shinyChance > 0 && random.nextDouble() < event.shinyChance) pokemon.setShiny(true);
    var formName = event.forms.get(pokemon.getSpecies().getResourceIdentifier().toString());
    if (formName != null && random.nextDouble() < event.formChance) {
      Forms.find(pokemon.getSpecies(), formName)
          .ifPresent(
              f -> {
                var aspects = new java.util.HashSet<>(pokemon.getForcedAspects());
                aspects.addAll(f.getAspects());
                pokemon.setForcedAspects(aspects);
                pokemon.updateAspects();
                pokemon.setForm(f);
              });
    }
  }
}
