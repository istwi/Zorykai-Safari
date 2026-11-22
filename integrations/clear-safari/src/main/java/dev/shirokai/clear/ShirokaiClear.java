package dev.shirokai.clear;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.nio.file.Path;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShirokaiClear implements ModInitializer {
  public static final Logger LOG = LoggerFactory.getLogger((String) "ShirokaiClear");
  private static final Path CONFIG =
      FabricLoader.getInstance().getConfigDir().resolve("shirokaiclear.json");
  public static ShirokaiClear INSTANCE;
  private ClearService service;
  private boolean configurationHealthy;

  public void onInitialize() {
    INSTANCE = this;
    ServerLifecycleEvents.SERVER_STARTING.register(
        server -> {
          try {
            this.service = new ClearService(server, LOG, ClearConfig.load(CONFIG));
            this.configurationHealthy = true;
          } catch (Exception error) {
            ClearConfig disabled = new ClearConfig();
            disabled.enabled = false;
            disabled.clearItems = false;
            disabled.clearWildPokemon = false;
            disabled.safari.enabled = false;
            this.service = new ClearService(server, LOG, disabled);
            this.configurationHealthy = false;
            LOG.error(
                "Limpeza desativada: configura\u00e7\u00e3o inv\u00e1lida; arquivo preservado."
                    + " Corrija e use reload",
                (Throwable) error);
          }
        });
    ServerLifecycleEvents.SERVER_STOPPED.register(
        server -> {
          if (this.service != null) {
            this.service.stop();
          }
          this.service = null;
        });
    ServerEntityEvents.ENTITY_LOAD.register(
        (entity, world) -> {
          if (this.service != null) {
            this.service.loaded(entity);
          }
        });
    ServerEntityEvents.ENTITY_UNLOAD.register(
        (entity, world) -> {
          if (this.service != null) {
            this.service.unloaded(entity);
          }
        });
    ServerTickEvents.END_SERVER_TICK.register(
        server -> {
          if (this.service != null) {
            this.service.tick();
          }
        });
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registry, environment) -> {
          LiteralArgumentBuilder root =
              (LiteralArgumentBuilder)
                  Commands.literal((String) "shirokaiclear")
                      .requires(Permissions.require((String) "shirokaiclear.admin", (int) 2));
          for (String name :
              new String[] {
                "now", "items", "pokemon", "preview", "reload", "status", "pause", "resume"
              }) {
            root.then(
                Commands.literal((String) name)
                    .executes(
                        context -> {
                          CommandSourceStack source = (CommandSourceStack) context.getSource();
                          try {
                            if (name.equals("reload")) {
                              ClearConfig config = ClearConfig.load(CONFIG);
                              if (this.service == null) {
                                throw new IllegalStateException(
                                    "Reinicie o servidor ap\u00f3s corrigir a"
                                        + " configura\u00e7\u00e3o inicial para indexar as"
                                        + " entidades com seguran\u00e7a");
                              }
                              this.service.reload(config);
                              this.configurationHealthy = true;
                              source.sendSuccess(
                                  () ->
                                      Component.literal(
                                          (String) "Configura\u00e7\u00e3o recarregada."),
                                  false);
                              return 1;
                            }
                            if (this.service == null || !this.configurationHealthy) {
                              throw new IllegalStateException(
                                  "Limpeza indispon\u00edvel: corrija a configura\u00e7\u00e3o e"
                                      + " use reload");
                            }
                            String message =
                                switch (name) {
                                  case "status" ->
                                      "Autom\u00e1tica: "
                                          + (this.service.paused()
                                              ? "pausada"
                                              : (this.service.config().enabled
                                                  ? "ativa"
                                                  : "desativada"))
                                          + "; pr\u00f3xima: "
                                          + this.service.remainingSeconds()
                                          + "s; em execu\u00e7\u00e3o: "
                                          + this.service.busy()
                                          + "; \u00faltima: "
                                          + this.service.last().items()
                                          + " itens, "
                                          + this.service.last().pokemon()
                                          + " Pok\u00e9mon. Safari: "
                                          + this.service.safariRemainingSeconds()
                                          + "s; última: "
                                          + this.service.lastSafari().items()
                                          + " itens, "
                                          + this.service.lastSafari().pokemon()
                                          + " Pokémon.";
                                  case "pause" -> {
                                    this.service.pause();
                                    yield "Limpeza autom\u00e1tica pausada.";
                                  }
                                  case "resume" -> {
                                    this.service.resume();
                                    yield "Limpeza autom\u00e1tica retomada.";
                                  }
                                  default -> {
                                    ClearService.Mode mode =
                                        name.equals("items")
                                            ? ClearService.Mode.ITEMS
                                            : (name.equals("pokemon")
                                                ? ClearService.Mode.POKEMON
                                                : ClearService.Mode.ALL);
                                    if (!this.service.start(
                                        mode,
                                        name.equals("preview"),
                                        result -> {
                                          if (result.preview()) {
                                            source.sendSuccess(
                                                () ->
                                                    Component.literal(
                                                        (String)
                                                            ("Pr\u00e9via: "
                                                                + result.items()
                                                                + " itens, "
                                                                + result.pokemon()
                                                                + " Pok\u00e9mon; nenhuma"
                                                                + " remo\u00e7\u00e3o.")),
                                                false);
                                          }
                                        })) {
                                      throw new IllegalStateException(
                                          "J\u00e1 existe uma limpeza/pr\u00e9via em andamento");
                                    }
                                    yield "Processamento em lotes iniciado.";
                                  }
                                };
                            source.sendSuccess(() -> Component.literal((String) message), false);
                            return 1;
                          } catch (Exception e) {
                            source.sendFailure(
                                (Component)
                                    Component.literal(
                                        (String) ("Zorykai Clear: " + e.getMessage())));
                            return 0;
                          }
                        }));
          }
          dispatcher.register(root);
        });
  }

  public static java.util.Map<String, String> safariStatus() {
    var self = INSTANCE;
    if (self == null || self.service == null) return java.util.Map.of("status", "indisponível");
    var s = self.service;
    var result = new java.util.LinkedHashMap<String, String>();
    result.put(
        "status",
        !self.configurationHealthy
            ? "configuração inválida"
            : s.paused()
                ? "pausada"
                : s.config().enabled && s.config().safari.enabled ? "ativa" : "desativada");
    result.put("next", Long.toString(s.safariRemainingSeconds()));
    result.put("interval", Integer.toString(s.config().safari.intervalSeconds));
    result.put("items", Long.toString(s.lastSafari().items()));
    result.put("pokemon", Long.toString(s.lastSafari().pokemon()));
    result.put("busy", Boolean.toString(s.safariBusy()));
    try {
      var area = SafariArea.current();
      result.put("dimension", area == null ? "não definida" : area.dimension());
      if (area == null) result.put("status", "aguardando seleção da área");
    } catch (RuntimeException e) {
      result.put("status", "integração da área indisponível");
    }
    return result;
  }
}
