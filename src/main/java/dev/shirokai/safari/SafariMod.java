package dev.shirokai.safari;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.block.HealingMachineBlock;
import com.cobblemon.mod.common.block.PCBlock;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.brigadier.arguments.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import org.slf4j.*;

public final class SafariMod implements ModInitializer {
  public static final Logger LOG = LoggerFactory.getLogger("shirokai_safari");
  public static SafariMod INSTANCE;
  public Tasks tasks;
  public Config config;
  public Storage.Data data;
  public Messages messages;
  public MinecraftServer server;
  private Path configPath, statePath;
  private boolean healthy;
  private Shop shop;
  private int ticks;
  private final Set<UUID> adminMode = new HashSet<>();
  private final Map<UUID, Config.Position[]> selections = new HashMap<>();

  private record Confirmation(
      long ends, java.math.BigDecimal price, int bundle, String currency, long cycle) {}

  private final Map<UUID, Confirmation> confirmations = new HashMap<>();
  private final Map<UUID, Long> retry = new HashMap<>();
  private final Set<UUID> externalMenus = new HashSet<>();

  @Override
  public void onInitialize() {
    INSTANCE = this;
    SafariHud.register();
    SpawnMetrics.register();
    SingleUseBalls.register();
    configPath = FabricLoader.getInstance().getConfigDir().resolve("shirokai-safari.json");
    try {
      if (!Files.exists(configPath)) Storage.write(configPath, new Config());
      config = Storage.read(configPath, Config.class);
      if ("cobblemon:poke_ball".equals(config.ballItem)) {
        config.ballItem = "cobblemon:safari_ball";
        Storage.write(configPath, config);
      }
      config.validate();
      messages = Messages.load(configPath.resolveSibling("shirokai-safari-messages.json"));
    } catch (Exception e) {
      throw new IllegalStateException("Configuração do Safari inválida", e);
    }
    ServerLifecycleEvents.SERVER_STARTED.register(
        s -> {
          server = s;
          statePath = s.getWorldPath(LevelResource.ROOT).resolve("data/shirokai-safari/state.json");
          try {
            data =
                Files.exists(statePath)
                    ? Storage.read(statePath, Storage.Data.class)
                    : new Storage.Data();
            if (data.schemaVersion != 1 || data.players == null || data.event == null)
              throw new IllegalStateException("Estado incompatível");
            for (var e : data.players.entrySet()) {
              UUID.fromString(e.getKey());
              e.getValue().validate();
              e.getValue().expireBalls(System.currentTimeMillis());
              if (e.getValue().active) {
                e.getValue().active = false;
                e.getValue().recovery = true;
              }
            }
            validateItems();
            if (data.event.running(System.currentTimeMillis()) && data.event.starts == 0) {
              data.event.starts = System.currentTimeMillis();
              data.event.endAnnounced = false;
            }
            healthy = true;
            shop = new Shop(this);
            save();
            LOG.info("Safari carregado: {} ciclos persistidos", data.players.size());
            tasks = new Tasks(this);
            tasks.register();
          } catch (Exception e) {
            healthy = false;
            LOG.error("Safari desativado; arquivos preservados", e);
          }
        });
    ServerLifecycleEvents.SERVER_STOPPING.register(
        s -> {
          if (!healthy) return;
          for (var p : List.copyOf(s.getPlayerList().getPlayers()))
            if (locked(p))
              try {
                exit(p);
              } catch (Exception e) {
                LOG.error("Retorno pendente no encerramento: {}", p.getUUID(), e);
              }
          save();
        });
    ServerTickEvents.END_SERVER_TICK.register(
        s -> {
          if (++ticks % 20 == 0) tick();
        });
    ServerPlayConnectionEvents.JOIN.register(
        (handler, sender, s) -> {
          var p = handler.getPlayer();
          if (!healthy) return;
          var v = visit(p);
          if (v.expireBalls(System.currentTimeMillis())) Balls.remove(p);
          if (v.active) {
            v.active = false;
            v.recovery = true;
            save();
          }
          if (v.recovery) recover(p);
          else Balls.remove(p);
        });
    ServerPlayConnectionEvents.DISCONNECT.register(
        (handler, s) -> {
          var p = handler.getPlayer();
          confirmations.remove(p.getUUID());
          adminMode.remove(p.getUUID());
          externalMenus.remove(p.getUUID());
          if (healthy && active(p)) {
            var v = visit(p);
            captureVisit(p, System.currentTimeMillis());
            v.active = false;
            v.recovery = true;
            save();
          }
        });
    ServerPlayerEvents.AFTER_RESPAWN.register(
        (old, p, alive) -> {
          if (healthy && locked(p)) {
            var v = visit(p);
            v.active = false;
            v.recovery = true;
            save();
            recover(p);
          }
        });
    UseBlockCallback.EVENT.register(
        (p, w, h, hit) -> {
          if (!(p instanceof ServerPlayer sp) || !locked(sp)) return InteractionResult.PASS;
          var block = w.getBlockState(hit.getBlockPos()).getBlock();
          if (block instanceof PCBlock || block instanceof HealingMachineBlock) {
            externalMenus.add(sp.getUUID());
            return InteractionResult.PASS;
          }
          return InteractionResult.FAIL;
        });
    AttackBlockCallback.EVENT.register(
        (p, w, h, pos, face) ->
            p instanceof ServerPlayer sp && locked(sp)
                ? InteractionResult.FAIL
                : InteractionResult.PASS);
    UseEntityCallback.EVENT.register(
        (p, w, h, e, hit) -> {
          if (!(p instanceof ServerPlayer sp) || !locked(sp)) return InteractionResult.PASS;
          if (e instanceof NPCEntity) {
            externalMenus.add(sp.getUUID());
            return InteractionResult.PASS;
          }
          return e instanceof PokemonEntity ? InteractionResult.PASS : InteractionResult.FAIL;
        });
    AttackEntityCallback.EVENT.register(
        (p, w, h, e, hit) ->
            p instanceof ServerPlayer sp && locked(sp)
                ? InteractionResult.FAIL
                : InteractionResult.PASS);
    UseItemCallback.EVENT.register(
        (p, w, h) -> {
          var stack = p.getItemInHand(h);
          if (!(p instanceof ServerPlayer sp)) return InteractionResultHolder.pass(stack);
          if (locked(sp) && (!active(sp) || !Balls.owns(stack, sp, visit(sp)))
              || !active(sp) && Balls.tagged(stack)) return InteractionResultHolder.fail(stack);
          return InteractionResultHolder.pass(stack);
        });
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registry, environment) -> {
          var root =
              Commands.literal("safari")
                  .requires(
                      s ->
                          !s.isPlayer()
                              || (Permissions.has(s.getPlayer(), "shirokai.safari.use", true)
                                  || locked(s.getPlayer())))
                  .executes(
                      c -> run(c.getSource(), () -> menu(c.getSource().getPlayerOrException())));
          root.then(
              Commands.literal("entrar")
                  .executes(
                      c -> run(c.getSource(), () -> enter(c.getSource().getPlayerOrException()))));
          root.then(
              Commands.literal("sair")
                  .executes(
                      c -> run(c.getSource(), () -> exit(c.getSource().getPlayerOrException()))));
          root.then(
              Commands.literal("tempo")
                  .executes(
                      c -> run(c.getSource(), () -> status(c.getSource().getPlayerOrException()))));
          root.then(
              Commands.literal("comprar")
                  .executes(
                      c ->
                          run(
                              c.getSource(),
                              () -> confirmBuy(c.getSource().getPlayerOrException())))
                  .then(
                      Commands.literal("confirmar")
                          .executes(
                              c ->
                                  run(
                                      c.getSource(),
                                      () -> {
                                        completePurchase(c.getSource().getPlayerOrException());
                                      }))));
          root.then(
              Commands.literal("saldo")
                  .executes(
                      c ->
                          run(
                              c.getSource(),
                              () ->
                                  shop.balance(
                                      c.getSource(), c.getSource().getPlayerOrException()))));
          root.then(
              Commands.literal("eventos")
                  .executes(
                      c ->
                          run(
                              c.getSource(),
                              () -> eventMenu(c.getSource().getPlayerOrException()))));
          root.then(
              Commands.literal("bolas")
                  .executes(
                      c ->
                          run(
                              c.getSource(),
                              () -> {
                                var p = c.getSource().getPlayerOrException();
                                if (!active(p) || !Balls.equip(p, visit(p)))
                                  throw new IllegalStateException(
                                      "Nenhuma Safari Ball disponível para equipar");
                                tell(p, "Safari Balls equipadas.");
                              })));
          root.then(
              Commands.literal("regioes")
                  .executes(
                      c ->
                          run(
                              c.getSource(),
                              () -> regionMenu(c.getSource().getPlayerOrException()))));
          root.then(
              Commands.literal("regiao")
                  .then(
                      Commands.argument("nome", StringArgumentType.word())
                          .suggests(
                              (c, b) ->
                                  SharedSuggestionProvider.suggest(config.regions.keySet(), b))
                          .executes(
                              c ->
                                  run(
                                      c.getSource(),
                                      () ->
                                          teleportRegion(
                                              c.getSource().getPlayerOrException(),
                                              StringArgumentType.getString(c, "nome"))))));
          root.then(
              Commands.literal("missoes")
                  .executes(
                      c ->
                          run(
                              c.getSource(),
                              () -> tasks.open(c.getSource().getPlayerOrException(), false))));
          root.then(
              Commands.literal("loja")
                  .executes(
                      c ->
                          run(
                              c.getSource(),
                              () -> tasks.open(c.getSource().getPlayerOrException(), true))));
          dispatcher.register(root);
          var admin =
              Commands.literal("safariadmin")
                  .requires(Permissions::admin)
                  .executes(c -> adminRoot(c.getSource()));
          admin.then(
              Commands.literal("menu")
                  .executes(
                      c ->
                          run(
                              c.getSource(),
                              () -> openAdmin(c.getSource().getPlayerOrException()))));
          admin.then(Commands.literal("ajuda").executes(c -> AdminHelp.show(c.getSource())));
          admin.then(Commands.literal("help").executes(c -> AdminHelp.show(c.getSource())));
          admin.then(
              Commands.literal("mensagens")
                  .executes(
                      c ->
                          run(
                              c.getSource(),
                              () -> {
                                var candidate =
                                    Messages.load(
                                        configPath.resolveSibling("shirokai-safari-messages.json"));
                                messages = candidate;
                                c.getSource()
                                    .sendSuccess(
                                        () -> Component.literal("Safari: mensagens recarregadas."),
                                        false);
                              })));
          admin.then(
              Commands.literal("eventos")
                  .executes(c -> run(c.getSource(), () -> eventStatus(c.getSource()))));
          admin.then(
              Commands.literal("economia")
                  .then(
                      Commands.argument(
                              "jogador", net.minecraft.commands.arguments.EntityArgument.player())
                          .executes(
                              c ->
                                  run(
                                      c.getSource(),
                                      () ->
                                          shop.balance(
                                              c.getSource(),
                                              net.minecraft.commands.arguments.EntityArgument
                                                  .getPlayer(c, "jogador"))))));
          admin.then(
              Commands.literal("moeda")
                  .then(
                      Commands.argument(
                              "moeda",
                              net.minecraft.commands.arguments.ResourceLocationArgument.id())
                          .executes(
                              c ->
                                  run(
                                      c.getSource(),
                                      () -> {
                                        String reference =
                                            net.minecraft.commands.arguments
                                                .ResourceLocationArgument.getId(c, "moeda")
                                                .toString();
                                        if (!Shop.currencyAvailable(reference))
                                          throw new IllegalStateException(
                                              "Moeda não encontrada no Impactor: " + reference);
                                        var next = copyConfig();
                                        next.currency = reference;
                                        saveConfig(next);
                                        c.getSource()
                                            .sendSuccess(
                                                () ->
                                                    Component.literal(
                                                        "Safari: moeda alterada para " + reference),
                                                false);
                                      }))));
          for (int i = 0; i < 2; i++) {
            final int index = i;
            admin.then(
                Commands.literal("pos" + (i + 1))
                    .executes(
                        c ->
                            run(
                                c.getSource(),
                                () -> {
                                  var p = c.getSource().getPlayerOrException();
                                  selections
                                          .computeIfAbsent(
                                              p.getUUID(), k -> new Config.Position[2])[index] =
                                      position(p);
                                  tell(p, "Posição " + (index + 1) + " selecionada.");
                                })));
          }
          admin.then(
              Commands.literal("criar")
                  .executes(
                      c ->
                          run(
                              c.getSource(),
                              () -> createArea(c.getSource().getPlayerOrException()))));
          admin.then(
              Commands.literal("delete")
                  .executes(c -> run(c.getSource(), () -> deleteArea(c.getSource()))));
          admin.then(
              Commands.literal("entrada")
                  .executes(
                      c ->
                          run(
                              c.getSource(),
                              () -> {
                                var p = c.getSource().getPlayerOrException();
                                var next = copyConfig();
                                next.entrance = position(p);
                                next.validate();
                                saveConfig(next);
                                tell(p, "Entrada salva.");
                              })));
          admin.then(
              Commands.literal("saida")
                  .executes(
                      c ->
                          run(
                              c.getSource(),
                              () -> {
                                var next = copyConfig();
                                next.lobby = position(c.getSource().getPlayerOrException());
                                next.validate();
                                saveConfig(next);
                                c.getSource()
                                    .sendSuccess(
                                        () ->
                                            Component.literal("Safari: saída de emergência salva."),
                                        false);
                              })));
          admin.then(
              Commands.literal("reload")
                  .executes(
                      c ->
                          run(
                              c.getSource(),
                              () -> {
                                var next = Storage.read(configPath, Config.class);
                                next.validate();
                                if (!next.ballItem.equals("cobblemon:safari_ball"))
                                  throw new IllegalStateException(
                                      "Safari usa cobblemon:safari_ball");
                                if (data.players.values().stream()
                                    .anyMatch(v -> v.active || v.recovery))
                                  throw new IllegalStateException(
                                      "Retire os participantes antes de recarregar");
                                config = next;
                                validateItems();
                              })));
          admin.then(
              Commands.literal("preco")
                  .then(
                      Commands.argument("valor", StringArgumentType.word())
                          .executes(
                              c ->
                                  run(
                                      c.getSource(),
                                      () -> {
                                        var next = copyConfig();
                                        next.bundlePrice =
                                            new java.math.BigDecimal(
                                                StringArgumentType.getString(c, "valor"));
                                        next.validate();
                                        saveConfig(next);
                                        c.getSource()
                                            .sendSuccess(
                                                () ->
                                                    Component.literal(
                                                        "Safari: configuração salva."),
                                                false);
                                      }))));
          admin.then(
              Commands.literal("tempo")
                  .then(
                      Commands.argument(
                              "jogador", net.minecraft.commands.arguments.EntityArgument.player())
                          .then(
                              Commands.argument("minutos", IntegerArgumentType.integer(1, 1440))
                                  .executes(
                                      c ->
                                          run(
                                              c.getSource(),
                                              () -> {
                                                var p =
                                                    net.minecraft.commands.arguments.EntityArgument
                                                        .getPlayer(c, "jogador");
                                                var v = visit(p);
                                                if (v.cycleEnd <= System.currentTimeMillis())
                                                  throw new IllegalStateException(
                                                      "Jogador sem ciclo vigente");
                                                extendVisit(
                                                    p.getUUID(),
                                                    v,
                                                    IntegerArgumentType.getInteger(c, "minutos")
                                                        * 60000L,
                                                    System.currentTimeMillis());
                                                save();
                                                tell(
                                                    p, "Bônus de tempo adicionado ao ciclo atual.");
                                              })))));
          var event =
              Commands.literal("evento")
                  .executes(c -> run(c.getSource(), () -> eventStatus(c.getSource())));
          event.then(
              Commands.literal("status")
                  .executes(c -> run(c.getSource(), () -> eventStatus(c.getSource()))));
          event.then(
              Commands.literal("parar")
                  .executes(
                      c ->
                          run(
                              c.getSource(),
                              () -> {
                                data.event = new Storage.Event();
                                save();
                                eventChanged(c.getSource());
                              })));
          event.then(
              Commands.literal("tempo")
                  .then(
                      Commands.argument("bonus_minutos", IntegerArgumentType.integer(1, 1440))
                          .then(
                              Commands.argument(
                                      "duracao_minutos", IntegerArgumentType.integer(1, 1440))
                                  .executes(
                                      c ->
                                          run(
                                              c.getSource(),
                                              () -> {
                                                long bonus =
                                                    IntegerArgumentType.getInteger(
                                                            c, "bonus_minutos")
                                                        * 60000L;
                                                eventFor(
                                                    IntegerArgumentType.getInteger(
                                                        c, "duracao_minutos"));
                                                long now = System.currentTimeMillis();
                                                for (var entry : data.players.entrySet())
                                                  if (entry.getValue().cycleEnd > now)
                                                    extendVisit(
                                                        UUID.fromString(entry.getKey()),
                                                        entry.getValue(),
                                                        bonus,
                                                        now);
                                                data.event.bonusMillis = bonus;
                                                save();
                                                eventChanged(c.getSource());
                                              })))));
          event.then(
              Commands.literal("spawn")
                  .then(
                      Commands.argument("multiplicador", DoubleArgumentType.doubleArg(1, 10))
                          .then(
                              Commands.argument("minutos", IntegerArgumentType.integer(1, 1440))
                                  .executes(
                                      c ->
                                          run(
                                              c.getSource(),
                                              () -> {
                                                eventFor(
                                                    IntegerArgumentType.getInteger(c, "minutos"));
                                                data.event.spawn =
                                                    DoubleArgumentType.getDouble(
                                                        c, "multiplicador");
                                                save();
                                                eventChanged(c.getSource());
                                              })))));
          event.then(
              Commands.literal("shiny")
                  .then(
                      Commands.argument("chance_percentual", DoubleArgumentType.doubleArg(0, 100))
                          .then(
                              Commands.argument("minutos", IntegerArgumentType.integer(1, 1440))
                                  .executes(
                                      c ->
                                          run(
                                              c.getSource(),
                                              () -> {
                                                eventFor(
                                                    IntegerArgumentType.getInteger(c, "minutos"));
                                                data.event.shinyChance =
                                                    DoubleArgumentType.getDouble(
                                                            c, "chance_percentual")
                                                        / 100;
                                                save();
                                                eventChanged(c.getSource());
                                              })))));
          event.then(
              Commands.literal("forma")
                  .then(
                      Commands.argument(
                              "especie",
                              net.minecraft.commands.arguments.ResourceLocationArgument.id())
                          .then(
                              Commands.argument("forma", StringArgumentType.word())
                                  .then(
                                      Commands.argument(
                                              "chance_percentual",
                                              DoubleArgumentType.doubleArg(0, 100))
                                          .then(
                                              Commands.argument(
                                                      "minutos",
                                                      IntegerArgumentType.integer(1, 1440))
                                                  .executes(
                                                      c ->
                                                          run(
                                                              c.getSource(),
                                                              () -> {
                                                                var species =
                                                                    PokemonSpecies.getByIdentifier(
                                                                        net.minecraft.commands
                                                                            .arguments
                                                                            .ResourceLocationArgument
                                                                            .getId(c, "especie"));
                                                                String form =
                                                                    StringArgumentType.getString(
                                                                        c, "forma");
                                                                if (species == null
                                                                    || Forms.find(species, form)
                                                                        .isEmpty())
                                                                  throw new IllegalArgumentException(
                                                                      "Espécie/forma não"
                                                                          + " encontrada; use uma"
                                                                          + " forma com aspectos"
                                                                          + " registrados");
                                                                eventFor(
                                                                    IntegerArgumentType.getInteger(
                                                                        c, "minutos"));
                                                                data.event.forms.put(
                                                                    species
                                                                        .getResourceIdentifier()
                                                                        .toString(),
                                                                    form);
                                                                data.event.formChance =
                                                                    DoubleArgumentType.getDouble(
                                                                            c, "chance_percentual")
                                                                        / 100;
                                                                save();
                                                                eventChanged(c.getSource());
                                                              })))))));
          var mode = Commands.literal("modo");
          mode.then(
              Commands.literal("on")
                  .executes(
                      c ->
                          run(
                              c.getSource(),
                              () -> {
                                var p = c.getSource().getPlayerOrException();
                                adminMode.add(p.getUUID());
                                tell(
                                    p,
                                    "Modo administrativo ativado: comandos liberados nesta"
                                        + " sessão.");
                              })));
          mode.then(
              Commands.literal("off")
                  .executes(
                      c ->
                          run(
                              c.getSource(),
                              () -> {
                                var p = c.getSource().getPlayerOrException();
                                adminMode.remove(p.getUUID());
                                tell(p, "Modo administrativo desativado.");
                              })));
          admin.then(mode);
          admin.then(event);
          var review =
              Commands.literal("compra")
                  .then(
                      Commands.argument(
                              "jogador", net.minecraft.commands.arguments.EntityArgument.player())
                          .then(
                              Commands.literal("confirmar")
                                  .executes(
                                      c ->
                                          run(
                                              c.getSource(),
                                              () ->
                                                  review(
                                                      net.minecraft.commands.arguments
                                                          .EntityArgument.getPlayer(c, "jogador"),
                                                      true))))
                          .then(
                              Commands.literal("cancelar")
                                  .executes(
                                      c ->
                                          run(
                                              c.getSource(),
                                              () ->
                                                  review(
                                                      net.minecraft.commands.arguments
                                                          .EntityArgument.getPlayer(c, "jogador"),
                                                      false)))));
          admin.then(review);
          var region = Commands.literal("regiao");
          region.then(
              Commands.literal("criar")
                  .then(
                      Commands.argument("nome", StringArgumentType.word())
                          .executes(
                              c ->
                                  run(
                                      c.getSource(),
                                      () ->
                                          createRegion(
                                              c.getSource().getPlayerOrException(),
                                              StringArgumentType.getString(c, "nome"))))));
          region.then(
              Commands.literal("remover")
                  .then(
                      Commands.argument("nome", StringArgumentType.word())
                          .suggests(
                              (c, b) ->
                                  SharedSuggestionProvider.suggest(config.regions.keySet(), b))
                          .executes(
                              c ->
                                  run(
                                      c.getSource(),
                                      () ->
                                          removeRegion(StringArgumentType.getString(c, "nome"))))));
          region.then(
              Commands.literal("listar")
                  .executes(
                      c ->
                          run(
                              c.getSource(),
                              () ->
                                  c.getSource()
                                      .sendSuccess(
                                          () ->
                                              Component.literal(
                                                  "Regiões Safari: "
                                                      + String.join(", ", config.regions.keySet())),
                                          false))));
          admin.then(region);
          admin.then(
              Commands.literal("missoes")
                  .then(
                      Commands.literal("reload")
                          .executes(
                              c ->
                                  run(
                                      c.getSource(),
                                      () -> {
                                        tasks.reloadAll();
                                        c.getSource()
                                            .sendSuccess(
                                                () ->
                                                    Component.literal(
                                                        "Missões, loja e menus recarregados."),
                                                false);
                                      }))));
          admin.then(
              Commands.literal("pontos")
                  .then(
                      Commands.argument(
                              "jogador", net.minecraft.commands.arguments.EntityArgument.player())
                          .executes(
                              c ->
                                  run(
                                      c.getSource(),
                                      () -> {
                                        var balance =
                                            tasks.player(
                                                    net.minecraft.commands.arguments.EntityArgument
                                                        .getPlayer(c, "jogador"))
                                                .points;
                                        c.getSource()
                                            .sendSuccess(
                                                () ->
                                                    Component.literal("Pontos Safari: " + balance),
                                                false);
                                      }))
                          .then(
                              Commands.argument(
                                      "ajuste", IntegerArgumentType.integer(-1000000, 1000000))
                                  .executes(
                                      c ->
                                          run(
                                              c.getSource(),
                                              () ->
                                                  tasks.points(
                                                      net.minecraft.commands.arguments
                                                          .EntityArgument.getPlayer(c, "jogador"),
                                                      IntegerArgumentType.getInteger(
                                                          c, "ajuste")))))));
          for (String action : List.of("confirmar", "cancelar"))
            admin.then(
                Commands.literal("entrega")
                    .then(
                        Commands.literal(action)
                            .then(
                                Commands.argument(
                                        "jogador",
                                        net.minecraft.commands.arguments.EntityArgument.player())
                                    .executes(
                                        c ->
                                            run(
                                                c.getSource(),
                                                () ->
                                                    tasks.review(
                                                        net.minecraft.commands.arguments
                                                            .EntityArgument.getPlayer(c, "jogador"),
                                                        action.equals("confirmar")))))));
          dispatcher.register(admin);
        });
  }

  @FunctionalInterface
  interface Action {
    void run() throws Exception;
  }

  private int run(CommandSourceStack source, Action action) {
    try {
      requireHealthy();
      action.run();
      return 1;
    } catch (Exception e) {
      source.sendFailure(
          Component.literal(
              "Safari: "
                  + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())));
      return 0;
    }
  }

  private void requireHealthy() {
    if (!healthy) throw new IllegalStateException("Persistência indisponível; confira o console");
  }

  public void save() {
    try {
      Storage.write(statePath, data);
    } catch (Exception e) {
      healthy = false;
      throw new IllegalStateException("Falha ao salvar Safari; operações suspensas", e);
    }
  }

  private void saveConfig(Config next) throws Exception {
    Storage.write(configPath, next);
    config = next;
  }

  private Config copyConfig() {
    return Storage.JSON.fromJson(Storage.JSON.toJson(config), Config.class);
  }

  private void validateItems() {
    if (!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(config.ballItem))
        || !config.ballItem.equals("cobblemon:safari_ball"))
      throw new IllegalArgumentException("Safari usa cobblemon:safari_ball");
  }

  public Visit visit(ServerPlayer p) {
    return data.players.computeIfAbsent(p.getUUID().toString(), k -> new Visit());
  }

  public boolean active(ServerPlayer p) {
    return data != null
        && data.players.containsKey(p.getUUID().toString())
        && data.players.get(p.getUUID().toString()).active;
  }

  public boolean locked(ServerPlayer p) {
    if (data == null) return false;
    var v = data.players.get(p.getUUID().toString());
    return v != null && (v.active || v.recovery);
  }

  public boolean inside(ServerPlayer p) {
    return inside(p.serverLevel(), p.getX(), p.getY(), p.getZ());
  }

  public boolean inside(ServerLevel w, double x, double y, double z) {
    return config.area != null
        && config.area.contains(w.dimension().location().toString(), x, y, z);
  }

  public void tell(ServerPlayer p, String text) {
    p.sendSystemMessage(Component.literal("✦ SAFARI » " + text));
  }

  public String format(long ms) {
    long s = Math.max(0, ms) / 1000;
    return String.format("%dh%02dm%02ds", s / 3600, s / 60 % 60, s % 60);
  }

  public void status(ServerPlayer p) {
    var v = visit(p);
    tell(
        p,
        "Saldo: "
            + format(v.remaining())
            + " | renovação: "
            + format(Math.max(0, v.cycleEnd - System.currentTimeMillis()))
            + " | bolas: "
            + (v.active ? Balls.count(p, v) : v.balls));
    if (v.purchase != null) tell(p, "Compra pendente de revisão: " + v.purchase);
    tell(
        p,
        v.bundlePurchased
            ? "Pacote adicional já comprado; renova junto com o próximo ciclo."
            : "Pacote adicional disponível: uma compra por ciclo.");
    eventStatus(p.createCommandSourceStack());
  }

  public void menu(ServerPlayer p) {
    requireHealthy();
    p.openMenu(
        new SimpleMenuProvider(
            (id, inventory, player) -> new SafariMenu(id, inventory, this),
            Component.literal("✦ SAFARI")));
  }

  public void enter(ServerPlayer p) {
    requireHealthy();
    var v = visit(p);
    long now = System.currentTimeMillis();
    if (locked(p))
      throw new IllegalStateException(
          "Você já está no Safari ou tem um retorno pendente. Use /safari sair");
    if (v.purchase != null)
      throw new IllegalStateException("Compra pendente de revisão antes de nova entrada");
    if (config.entrance == null || config.area == null || config.lobby == null)
      throw new IllegalStateException("Área/entrada ainda não configuradas");
    if (!p.isAlive()
        || p.isPassenger()
        || p.isCreative()
        || p.isSpectator()
        || BattleRegistry.getBattleByParticipatingPlayer(p) != null)
      throw new IllegalStateException("Saia da batalha/veículo e use sobrevivência");
    if (!Balls.empty(p))
      throw new IllegalStateException(
          "Esvazie inventário, armadura e mão secundária antes de entrar");
    closeMenuIfOpen(p);
    if (inside(p))
      throw new IllegalStateException("Use a entrada do Safari a partir de fora da área");
    var dest = config.entrance;
    var w = world(dest.dimension());
    if (w == null || !safe(p, w, dest))
      throw new IllegalStateException("Entrada indisponível ou insegura");
    if (v.renewable(now)) {
      v.begin(now, config.cycleSeconds, Permissions.seconds(p, config), config.ballBundle);
      if (data.event.running(now)) v.budgetMillis += data.event.bonusMillis;
    }
    if (!v.usable(now))
      throw new IllegalStateException("Tempo esgotado; renovação em " + format(v.cycleEnd - now));
    v.origin = position(p);
    v.active = true;
    v.recovery = false;
    v.lastCharge = now;
    save();
    if (!p.teleportTo(w, dest.x(), dest.y(), dest.z(), Set.of(), dest.yaw(), dest.pitch())) {
      v.active = false;
      v.origin = null;
      save();
      throw new IllegalStateException("Teleporte recusado pela proteção");
    }
    Balls.give(p, v, v.balls, config);
    tell(p, "Bem-vindo! Saldo: " + format(v.remaining()) + ". Use /safari sair para pausar.");
  }

  public void captureVisit(ServerPlayer p, long now) {
    var v = visit(p);
    v.charge(now);
    v.balls = Balls.count(p, v);
    if (v.expireBalls(now)) Balls.remove(p);
  }

  private void extendVisit(UUID id, Visit v, long bonus, long now) {
    var p = server.getPlayerList().getPlayer(id);
    if (p != null && v.active) captureVisit(p, now);
    boolean fresh = v.extend(now, bonus, config.ballBundle);
    // Persist before physical delivery. A reconnect reconstructs from this balance, never
    // re-grants.
    save();
    if (fresh && p != null && v.active) {
      Balls.remove(p);
      if (!Balls.room(p, v.balls, config)) {
        v.active = false;
        v.recovery = true;
        save();
        recover(p);
        tell(
            p,
            "Tempo extra e novas bolas reservados. Guarde os itens e entre novamente; seu"
                + " inventário estava cheio.");
        return;
      }
      Balls.give(p, v, v.balls, config);
    }
  }

  public void exit(ServerPlayer p) {
    requireHealthy();
    var v = visit(p);
    if (!v.active && !v.recovery) throw new IllegalStateException("Você não está no Safari");
    if (v.active) {
      captureVisit(p, System.currentTimeMillis());
    }
    v.active = false;
    v.recovery = true;
    save();
    if (!recover(p))
      throw new IllegalStateException(
          "Retorno pendente; destino indisponível. A administração deve conferir a posição salva.");
  }

  private boolean recover(ServerPlayer p) {
    var v = visit(p);
    if (!v.recovery || v.origin == null) return false;
    if (v.expireBalls(System.currentTimeMillis())) Balls.remove(p);
    try {
      var dest = v.origin;
      var w = world(dest.dimension());
      if (w == null || !safe(p, w, dest)) {
        dest = config.lobby;
        if (dest == null) return false;
        w = world(dest.dimension());
        if (w == null || !safe(p, w, dest)) return false;
      }
      var battle = BattleRegistry.getBattleByParticipatingPlayer(p);
      if (battle != null) battle.stop();
      p.stopRiding();
      closeMenuIfOpen(p);
      if (!p.teleportTo(w, dest.x(), dest.y(), dest.z(), Set.of(), dest.yaw(), dest.pitch()))
        return false;
      if (p.serverLevel() != w
          || p.position().distanceToSqr(new Vec3(dest.x(), dest.y(), dest.z())) > 0.01)
        return false;
      Balls.remove(p);
      v.recovery = false;
      v.origin = null;
      save();
      tell(p, "Você saiu. Tempo restante: " + format(v.remaining()));
      return true;
    } catch (Exception e) {
      LOG.error("Falha no retorno Safari de {}", p.getUUID(), e);
      return false;
    }
  }

  private ServerLevel world(String dim) {
    return server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dim)));
  }

  private boolean safe(ServerPlayer p, ServerLevel w, Config.Position dest) {
    if (!Double.isFinite(dest.x() + dest.y() + dest.z())
        || Math.abs(dest.x()) > 29999900
        || Math.abs(dest.z()) > 29999900
        || dest.y() < w.getMinBuildHeight() + 1
        || dest.y() + 2 >= w.getMaxBuildHeight()) return false;
    var pos = BlockPos.containing(dest.x(), dest.y(), dest.z());
    if (!w.getWorldBorder().isWithinBounds(pos)) return false;
    w.getChunkAt(pos);
    var box =
        p.getBoundingBox().move(dest.x() - p.getX(), dest.y() - p.getY(), dest.z() - p.getZ());
    return w.noCollision(p, box)
        && !w.containsAnyLiquid(box)
        && w.getBlockState(pos.below()).isFaceSturdy(w, pos.below(), Direction.UP);
  }

  private Config.Position position(ServerPlayer p) {
    return new Config.Position(
        p.serverLevel().dimension().location().toString(),
        p.getX(),
        p.getY(),
        p.getZ(),
        p.getYRot(),
        p.getXRot());
  }

  private void deleteArea(CommandSourceStack source) throws Exception {
    if (data.players.values().stream().anyMatch(v -> v.active || v.recovery))
      throw new IllegalStateException(
          "Retire os participantes e resolva os retornos pendentes antes de remover a área");
    if (config.area == null) throw new IllegalStateException("Nenhuma área de Safari configurada");
    var next = copyConfig();
    next.area = null;
    next.entrance = null;
    next.regions.clear();
    next.validate();
    saveConfig(next);
    selections.clear();
    source.sendSuccess(
        () ->
            Component.literal(
                "Safari: área, entrada e destinos de regiões removidos. O mundo e os dados dos"
                    + " jogadores foram preservados. Selecione pos1/pos2, use /safariadmin criar e"
                    + " defina /safariadmin entrada."),
        false);
  }

  private void createArea(ServerPlayer p) throws Exception {
    if (data.players.values().stream().anyMatch(v -> v.active || v.recovery))
      throw new IllegalStateException("Retire os participantes antes de alterar a área");
    var a = selections.get(p.getUUID());
    if (a == null || a[0] == null || a[1] == null || !a[0].dimension().equals(a[1].dimension()))
      throw new IllegalStateException("Selecione pos1 e pos2 na mesma dimensão");
    var x = BlockPos.containing(a[0].x(), a[0].y(), a[0].z());
    var y = BlockPos.containing(a[1].x(), a[1].y(), a[1].z());
    var next = copyConfig();
    next.area =
        new Config.Area(
            a[0].dimension(),
            Math.min(x.getX(), y.getX()),
            Math.min(x.getY(), y.getY()),
            Math.min(x.getZ(), y.getZ()),
            Math.max(x.getX(), y.getX()),
            Math.max(x.getY(), y.getY()),
            Math.max(x.getZ(), y.getZ()));
    next.entrance = null;
    next.regions
        .entrySet()
        .removeIf(
            e ->
                !next.area.contains(
                    e.getValue().dimension(),
                    e.getValue().x(),
                    e.getValue().y(),
                    e.getValue().z()));
    next.validate();
    saveConfig(next);
    tell(p, "Área criada. Posicione-se dentro e use /safariadmin entrada.");
  }

  public void confirmBuy(ServerPlayer p) {
    if (!active(p)) throw new IllegalStateException("Compre apenas dentro do Safari");
    if (visit(p).bundlePurchased)
      throw new IllegalStateException(
          "Você já comprou o pacote deste ciclo. A compra e a entrada renovam juntas em "
              + format(visit(p).cycleEnd - System.currentTimeMillis()));
    if (visit(p).purchase != null)
      throw new IllegalStateException("Compra pendente de conferência");
    confirmations.put(
        p.getUUID(),
        new Confirmation(
            System.currentTimeMillis() + 30000,
            config.bundlePrice,
            config.ballBundle,
            config.currency,
            visit(p).cycleStart));
    tell(
        p,
        "Pacote: "
            + config.ballBundle
            + " bolas por $"
            + config.bundlePrice.toPlainString()
            + ". Confirme em 30s no menu ou use /safari comprar confirmar. Moeda: "
            + config.currency);
    p.sendSystemMessage(
        Component.literal("[CONFIRMAR COMPRA]")
            .withStyle(
                style ->
                    style
                        .withColor(net.minecraft.ChatFormatting.GREEN)
                        .withClickEvent(
                            new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                "/safari comprar confirmar"))));
    p.openMenu(
        new SimpleMenuProvider(
            (id, inventory, player) -> new SafariMenu(id, inventory, this, true),
            Component.literal("Confirmar compra de Safari Balls")));
  }

  private void review(ServerPlayer p, boolean confirmed) {
    var v = visit(p);
    if (v.purchase == null) throw new IllegalStateException("Sem compra pendente");
    if (shop.inFlight(v.purchase))
      throw new IllegalStateException("Compra ainda em andamento; aguarde a conclusão");
    if (active(p)) throw new IllegalStateException("Jogador deve sair antes da revisão");
    if (confirmed) v.creditBundle(Math.toIntExact(v.purchaseAmount), System.currentTimeMillis());
    LOG.warn(
        "Revisão manual compra Safari {} jogador {} confirmada={}",
        v.purchase,
        p.getUUID(),
        confirmed);
    v.purchase = null;
    v.purchaseAmount = 0;
    save();
  }

  private void eventFor(int minutes) {
    if (!data.event.running(System.currentTimeMillis())) data.event = new Storage.Event();
    data.event.starts = System.currentTimeMillis();
    data.event.endAnnounced = false;
    data.event.ends = data.event.starts + minutes * 60000L;
  }

  public boolean eligible(UUID player) {
    if (!healthy || server == null) return false;
    var p = server.getPlayerList().getPlayer(player);
    return p != null && active(p) && inside(p);
  }

  public double multiplier(UUID player) {
    if (!healthy || server == null) return 1;
    var p = server.getPlayerList().getPlayer(player);
    if (p == null || !active(p) || !inside(p)) return 1;
    return Math.min(
        10,
        config.spawnMultiplier
            * (data.event.running(System.currentTimeMillis()) ? data.event.spawn : 1));
  }

  public static boolean commands(CommandSourceStack source, String command) {
    var m = INSTANCE;
    if (m == null || !source.isPlayer() || !m.locked(source.getPlayer())) return false;
    String root = command.strip().split("\\s+", 2)[0];
    if (root.startsWith("/")) root = root.substring(1);
    if (root.equals("safariadmin") && Permissions.admin(source)) return false;
    if (m.adminMode.contains(source.getPlayer().getUUID())) {
      if (Permissions.admin(source)) return false;
      m.adminMode.remove(source.getPlayer().getUUID());
    }
    if (root.equals("safari")
        || Set.of("login", "l", "easyauth:login", "easyauth:l").contains(root)) return false;
    m.tell(source.getPlayer(), "Comandos externos bloqueados. Use /safari sair.");
    return true;
  }

  private void tick() {
    if (!healthy || server == null) return;
    long now = System.currentTimeMillis();
    if (data.event.ends > 0 && !data.event.running(now) && !data.event.endAnnounced) {
      data.event.endAnnounced = true;
      save();
      Announcements.event(this, false);
    }
    boolean changed = false;
    for (var p : List.copyOf(server.getPlayerList().getPlayers())) {
      var v = data.players.get(p.getUUID().toString());
      if (v != null && v.active) {
        captureVisit(p, now);
        changed = true;
        if (!v.usable(now) || !inside(p)) {
          try {
            exit(p);
          } catch (Exception e) {
            tell(p, e.getMessage());
          }
          continue;
        }
        if (p.containerMenu != p.inventoryMenu
            && !(p.containerMenu instanceof SafariMenu)
            && !(p.containerMenu instanceof RegionsMenu)
            && !(p.containerMenu instanceof TasksMenu)
            && !(p.containerMenu instanceof AdminMenu)
            && !externalMenus.contains(p.getUUID())) closeMenuIfOpen(p);
        if (p.containerMenu == p.inventoryMenu) externalMenus.remove(p.getUUID());
        if (tasks != null) tasks.safeTick(p);
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(5);
        p.getFoodData().setExhaustion(0);

      } else if (v != null && v.recovery && now >= retry.getOrDefault(p.getUUID(), 0L)) {
        retry.put(p.getUUID(), now + 10000);
        recover(p);
      } else if (v == null || !v.recovery) {
        Balls.remove(p);
        if (inside(p)
            && !Permissions.admin(p.createCommandSourceStack())
            && config.lobby != null
            && now >= retry.getOrDefault(p.getUUID(), 0L)) {
          retry.put(p.getUUID(), now + 10000);
          var dest = config.lobby;
          var w = world(dest.dimension());
          if (w != null && safe(p, w, dest)) {
            p.stopRiding();
            p.teleportTo(w, dest.x(), dest.y(), dest.z(), Set.of(), dest.yaw(), dest.pitch());
            tell(p, "Entre pela opção /safari entrar para visitar esta área.");
          }
        }
      }
    }
    if (changed) save();
  }

  public static boolean lockedPlayer(net.minecraft.world.entity.player.Player p) {
    return p instanceof ServerPlayer sp && INSTANCE != null && INSTANCE.locked(sp);
  }

  public static void beforeDeath(ServerPlayer p) {
    var m = INSTANCE;
    if (m != null && m.healthy && m.active(p)) {
      var v = m.visit(p);
      m.captureVisit(p, System.currentTimeMillis());
      Balls.remove(p);
      v.active = false;
      v.recovery = true;
      m.save();
    }
  }

  public void completePurchase(ServerPlayer p) {
    var offer = confirmations.remove(p.getUUID());
    if (offer == null
        || offer.ends() < System.currentTimeMillis()
        || offer.price().compareTo(config.bundlePrice) != 0
        || offer.bundle() != config.ballBundle
        || !offer.currency().equals(config.currency)
        || offer.cycle() != visit(p).cycleStart)
      throw new IllegalStateException("Oferta expirada/alterada; use /safari comprar novamente");
    shop.buy(p);
  }

  public String eventDescription() {
    var event = data.event;
    if (!event.running(System.currentTimeMillis())) return "Nenhum evento ativo.";
    var effects = new java.util.ArrayList<String>();
    if (event.spawn != 1)
      effects.add("Spawn " + Math.min(10, config.spawnMultiplier * event.spawn) + "x");
    if (event.shinyChance > 0) effects.add("Shiny adicional " + percent(event.shinyChance) + "%");
    if (event.formChance > 0 && !event.forms.isEmpty())
      effects.add("Formas " + event.forms + " • " + percent(event.formChance) + "%");
    if (event.bonusMillis > 0) effects.add("Tempo extra " + format(event.bonusMillis));
    return "Evento ativo • "
        + String.join(" | ", effects)
        + " | restante "
        + format(event.ends - System.currentTimeMillis());
  }

  private String percent(double chance) {
    return java.math.BigDecimal.valueOf(chance * 100).stripTrailingZeros().toPlainString();
  }

  public void eventStatus(CommandSourceStack source) {
    source.sendSuccess(() -> Component.literal("✦ SAFARI » " + eventDescription()), false);
  }

  private void eventChanged(CommandSourceStack source) {
    Announcements.event(this, data.event.running(System.currentTimeMillis()));
    eventStatus(source);
    if (source.isPlayer())
      tell(
          source.getPlayer(),
          "Somente novos spawns do Safari recebem os efeitos. Use /safariadmin evento status para"
              + " consultar.");
  }

  public String eventEffects() {
    var e = data.event;
    var parts = new java.util.ArrayList<String>();
    if (e.spawn != 1) parts.add("Spawn " + Math.min(10, config.spawnMultiplier * e.spawn) + "x");
    if (e.shinyChance > 0) parts.add("Shiny +" + percent(e.shinyChance) + "%");
    if (e.formChance > 0 && !e.forms.isEmpty())
      parts.add("Formas especiais " + percent(e.formChance) + "%");
    if (e.bonusMillis > 0) parts.add("Tempo extra " + format(e.bonusMillis));
    return parts.isEmpty() ? "Safari" : String.join(" • ", parts);
  }

  private int adminRoot(CommandSourceStack source) {
    if (!source.isPlayer()) return AdminHelp.show(source);
    return run(source, () -> openAdmin(source.getPlayerOrException()));
  }

  public void openAdmin(ServerPlayer player) {
    if (!Permissions.admin(player.createCommandSourceStack()))
      throw new IllegalStateException("Sem permissão administrativa");
    player.openMenu(
        new SimpleMenuProvider(
            (id, inventory, p) -> new AdminMenu(id, inventory, this),
            Component.literal("Safari • Administração")));
  }

  public void createRegion(ServerPlayer p, String name) throws Exception {
    if (!name.matches("[a-z0-9_-]{1,32}"))
      throw new IllegalArgumentException(
          "Use nome com letras minúsculas, números, _ ou - (até 32)");
    if (config.area == null || !inside(p))
      throw new IllegalStateException("Posicione-se dentro da área do Safari");
    if (config.regions.containsKey(name))
      throw new IllegalStateException("Região já existe. Remova-a antes de redefinir");
    var dest = position(p);
    if (!safe(p, p.serverLevel(), dest))
      throw new IllegalStateException("Posição insegura: fique em chão sólido e livre");
    var next = copyConfig();
    next.regions.put(name, dest);
    next.validate();
    saveConfig(next);
    tell(p, "Região salva: " + name);
  }

  private void removeRegion(String name) throws Exception {
    if (!config.regions.containsKey(name)) throw new IllegalStateException("Região inexistente");
    var next = copyConfig();
    next.regions.remove(name);
    next.validate();
    saveConfig(next);
  }

  public boolean canTravel(ServerPlayer p) {
    return active(p)
        && inside(p)
        && visit(p).usable(System.currentTimeMillis())
        && Permissions.has(p, "shirokai.safari.use", true)
        && p.isAlive()
        && !p.isSpectator();
  }

  public void eventMenu(ServerPlayer p) {
    requireHealthy();
    if (!canTravel(p))
      throw new IllegalStateException("Consulte os eventos durante uma visita ativa ao Safari");
    p.openMenu(
        new SimpleMenuProvider(
            (id, inventory, player) ->
                new SafariMenu(id, inventory, this, SafariMenu.Screen.EVENTS),
            Component.literal("Safari • Eventos")));
  }

  public void regionMenu(ServerPlayer p) {
    requireHealthy();
    if (!canTravel(p))
      throw new IllegalStateException(
          "Entre no Safari e tenha tempo disponível para visitar regiões");
    if (config.regions.isEmpty()) throw new IllegalStateException("Nenhuma região cadastrada");
    p.openMenu(
        new SimpleMenuProvider(
            (id, inventory, player) -> new RegionsMenu(id, inventory, this),
            Component.literal("Safari • Regiões")));
  }

  public void teleportRegion(ServerPlayer p, String name) {
    requireHealthy();
    if (!canTravel(p))
      throw new IllegalStateException(
          "Entre no Safari e tenha tempo disponível para visitar regiões");
    if (p.isPassenger()
        || p.isCreative()
        || BattleRegistry.getBattleByParticipatingPlayer(p) != null)
      throw new IllegalStateException("Saia da batalha/veículo e use sobrevivência");
    var dest = config.regions.get(name);
    if (dest == null
        || config.area == null
        || !config.area.contains(dest.dimension(), dest.x(), dest.y(), dest.z()))
      throw new IllegalStateException("Região inexistente ou fora do Safari");
    var w = world(dest.dimension());
    if (w == null || !safe(p, w, dest))
      throw new IllegalStateException("Destino indisponível ou inseguro");
    var v = visit(p);
    captureVisit(p, System.currentTimeMillis());
    if (!v.usable(System.currentTimeMillis()))
      throw new IllegalStateException("Tempo de Safari esgotado");
    save();
    closeMenuIfOpen(p);
    if (!p.teleportTo(w, dest.x(), dest.y(), dest.z(), Set.of(), dest.yaw(), dest.pitch()))
      throw new IllegalStateException("Teleporte recusado pela proteção");
    tell(p, "Você chegou à região " + name + ".");
  }

  public static void closeMenuIfOpen(ServerPlayer p) {
    if (p.containerMenu != p.inventoryMenu) p.closeContainer();
  }
}
