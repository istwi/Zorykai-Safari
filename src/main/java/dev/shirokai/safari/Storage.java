package dev.shirokai.safari;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class Storage {
  public static final Gson JSON =
      new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

  public static final class Data {
    public int schemaVersion = 1;
    public Map<String, Visit> players = new LinkedHashMap<>();
    public Event event = new Event();
  }

  public static final class Event {
    public long ends, bonusMillis;
    public double spawn = 1, shinyChance = 0, formChance = 0;
    public Map<String, String> forms = new LinkedHashMap<>();

    public boolean running(long now) {
      return ends > now;
    }
  }

  public static void write(Path path, Object value) throws IOException {
    Files.createDirectories(path.getParent());
    Path temp = Files.createTempFile(path.getParent(), "safari-", ".tmp");
    try {
      try (var channel = java.nio.channels.FileChannel.open(temp, StandardOpenOption.WRITE)) {
        byte[] bytes =
            (JSON.toJson(value) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var buffer = java.nio.ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) channel.write(buffer);
        channel.force(true);
      }
      Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  public static <T> T read(Path p, Class<T> type) throws IOException {
    try {
      T value = JSON.fromJson(Files.readString(p), type);
      if (value == null) throw new IOException("Arquivo vazio: " + p);
      return value;
    } catch (JsonParseException e) {
      throw new IOException("JSON inválido: " + p, e);
    }
  }
}
