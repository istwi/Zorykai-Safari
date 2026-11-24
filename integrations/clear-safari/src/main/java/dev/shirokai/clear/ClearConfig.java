package dev.shirokai.clear;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ClearConfig {
  public boolean enabled = true;
  public int intervalSeconds = 600;
  public boolean clearItems = true;
  public boolean clearWildPokemon = true;
  public boolean clearOtherEntities = false;
  public Items items = new Items();
  public Pokemon pokemon = new Pokemon();
  public Npcs npcs = new Npcs();
  public Worlds worlds = new Worlds();
  public Announcements announcements = new Announcements();
  public Performance performance = new Performance();
  public Logging logging = new Logging();
  public Safari safari = new Safari();
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

  public static ClearConfig load(Path path) throws IOException {
    JsonElement tree;
    if (!Files.exists(path, new LinkOption[0])) {
      Files.createDirectories(path.getParent(), new FileAttribute[0]);
      Files.writeString(
          path,
          (CharSequence) (GSON.toJson((Object) new ClearConfig()) + System.lineSeparator()),
          StandardOpenOption.CREATE_NEW);
    }
    if (!(tree = JsonParser.parseString((String) Files.readString(path))).isJsonObject()) {
      throw new IllegalArgumentException("A configura\u00e7\u00e3o deve ser um objeto JSON");
    }
    ClearConfig c = (ClearConfig) GSON.fromJson(tree, ClearConfig.class);
    c.validate();
    if (!tree.getAsJsonObject().has("safari")) {
      tree.getAsJsonObject().add("safari", GSON.toJsonTree(c.safari));
      Path temp = Files.createTempFile(path.getParent(), "clear-", ".tmp");
      try {
        Files.writeString(temp, GSON.toJson(tree) + System.lineSeparator());
        Files.move(
            temp,
            path,
            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(temp);
      }
    }
    return c;
  }

  public void validate() {
    Objects.requireNonNull(this.items);
    Objects.requireNonNull(this.pokemon);
    Objects.requireNonNull(this.npcs);
    Objects.requireNonNull(this.worlds);
    Objects.requireNonNull(this.announcements);
    Objects.requireNonNull(this.performance);
    Objects.requireNonNull(this.logging);
    Objects.requireNonNull(this.safari);
    Objects.requireNonNull(this.safari.announcements);
    if (safari.intervalSeconds < 1
        || safari.intervalSeconds > 604800
        || safari.minimumItemAgeSeconds < 0
        || safari.minimumItemAgeSeconds > 604800
        || safari.minimumPokemonAgeSeconds < 0
        || safari.minimumPokemonAgeSeconds > 604800)
      throw new IllegalArgumentException("Intervalos/idades do Safari inválidos");
    Objects.requireNonNull(safari.announcements.warningSeconds);
    Objects.requireNonNull(safari.announcements.warningMessage);
    Objects.requireNonNull(safari.announcements.resultMessage);
    for (var n : safari.announcements.warningSeconds)
      if (n == null || n < 1 || n > 604800)
        throw new IllegalArgumentException("Aviso do Safari inválido");
    Objects.requireNonNull(this.items.excludedItemIds);
    Objects.requireNonNull(this.pokemon.excludedSpecies);
    Objects.requireNonNull(this.worlds.enabledDimensions);
    Objects.requireNonNull(this.worlds.excludedDimensions);
    Objects.requireNonNull(this.announcements.warningSeconds);
    Objects.requireNonNull(this.announcements.warningMessage);
    Objects.requireNonNull(this.announcements.resultMessage);
    if (this.intervalSeconds < 1
        || this.intervalSeconds > 604800
        || this.items.minimumAgeSeconds < 0
        || this.pokemon.minimumAgeSeconds < 0
        || this.items.minimumAgeSeconds > 604800
        || this.pokemon.minimumAgeSeconds > 604800
        || this.performance.maxEntitiesPerBatch < 1
        || this.performance.maxEntitiesPerBatch > 10000
        || this.performance.batchIntervalTicks < 1
        || this.performance.batchIntervalTicks > 1200) {
      throw new IllegalArgumentException("Intervalo, idade ou lote fora dos limites seguros");
    }
    if (!(!this.clearOtherEntities
        && this.performance.onlyLoadedChunks
        && this.pokemon.protectBattles
        && this.pokemon.protectPlayerPokemon
        && this.pokemon.protectNpcPokemon
        && this.pokemon.protectRaidBosses
        && this.npcs.protectEasyNPC
        && this.npcs.protectAllNPCs)) {
      throw new IllegalArgumentException(
          "Batalhas, donos, NPCs e raids s\u00e3o prote\u00e7\u00f5es obrigat\u00f3rias; somente"
              + " chunks carregados e itens/Pok\u00e9mon s\u00e3o suportados");
    }
    for (Integer n : this.announcements.warningSeconds) {
      if (n != null && n >= 1 && n <= 604800) continue;
      throw new IllegalArgumentException("Aviso inv\u00e1lido");
    }
    for (Set<String> set :
        List.of(
            this.items.excludedItemIds,
            this.worlds.enabledDimensions,
            this.worlds.excludedDimensions)) {
      for (String id : set) {
        if (id != null && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) continue;
        throw new IllegalArgumentException("Identificador inv\u00e1lido: " + id);
      }
    }
    for (String string : this.pokemon.excludedSpecies) {
      if (string != null && string.matches("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+")) continue;
      throw new IllegalArgumentException("Esp\u00e9cie inv\u00e1lida: " + string);
    }
  }

  public static final class Items {
    public int minimumAgeSeconds = 60;
    public boolean protectNamedItems = true;
    public Set<String> excludedItemIds = new HashSet<String>();
  }

  public static final class Pokemon {
    public boolean protectBattles = true;
    public boolean protectPlayerPokemon = true;
    public boolean protectNpcPokemon = true;
    public boolean protectLegendary = true;
    public boolean protectMythical = true;
    public boolean protectShiny = true;
    public boolean protectRaidBosses = true;
    public boolean protectNamedPokemon = true;
    public boolean protectTaggedPokemon = true;
    public int minimumAgeSeconds = 120;
    public Set<String> excludedSpecies = new HashSet<String>();
  }

  public static final class Npcs {
    public boolean protectEasyNPC = true;
    public boolean protectAllNPCs = true;
  }

  public static final class Worlds {
    public Set<String> enabledDimensions = new HashSet<String>();
    public Set<String> excludedDimensions = new HashSet<String>();

    public boolean accepts(String id) {
      return !this.excludedDimensions.contains(id)
          && (this.enabledDimensions.isEmpty() || this.enabledDimensions.contains(id));
    }
  }

  public static final class Announcements {
    public boolean enabled = true;
    public boolean showResults = true;
    public Set<Integer> warningSeconds =
        new HashSet<Integer>(
            List.of(
                Integer.valueOf(60),
                Integer.valueOf(30),
                Integer.valueOf(10),
                Integer.valueOf(5),
                Integer.valueOf(3),
                Integer.valueOf(2),
                Integer.valueOf(1)));
    public String warningMessage =
        "\u00a7b\u2726 Zorykai \u00a78\u00bb \u00a77Limpeza de entidades em \u00a7e{seconds}"
            + " segundos\u00a77!";
    public String resultMessage =
        "\u00a7b\u2726 Zorykai \u00a78\u00bb \u00a7aLimpeza conclu\u00edda! \u00a77Itens removidos:"
            + " \u00a7f{items} \u00a78| \u00a77Pok\u00e9mon removidos: \u00a7f{pokemon}";
  }

  public static final class Performance {
    public int maxEntitiesPerBatch = 100;
    public int batchIntervalTicks = 1;
    public boolean onlyLoadedChunks = true;
  }

  public static final class Logging {
    public boolean enabled = true;
    public boolean logProtectedEntities = false;
    public boolean logSummary = true;
  }

  public static final class Safari {
    public boolean enabled = true;
    public boolean onlyWhenOccupied = true;
    public int intervalSeconds = 120;
    public boolean clearItems = true, clearWildPokemon = true;
    public int minimumItemAgeSeconds = 0, minimumPokemonAgeSeconds = 0;
    public Announcements announcements = new Announcements();

    public Safari() {
      announcements.warningSeconds = new HashSet<>(List.of(10, 5, 3, 2, 1));
      announcements.warningMessage = "§2✦ Safari §8» §7Limpeza local em §e{seconds}s§7!";
      announcements.resultMessage =
          "§2✦ Safari §8» §aLimpeza concluída! §7Itens: §f{items} §8| §7Pokémon: §f{pokemon}";
    }
  }

  public ClearConfig safariPolicy() {
    var copy = GSON.fromJson(GSON.toJson(this), ClearConfig.class);
    copy.clearItems = safari.clearItems;
    copy.clearWildPokemon = safari.clearWildPokemon;
    copy.items.minimumAgeSeconds = safari.minimumItemAgeSeconds;
    copy.pokemon.minimumAgeSeconds = safari.minimumPokemonAgeSeconds;
    copy.worlds = new Worlds();
    copy.announcements = safari.announcements;
    return copy;
  }
}
