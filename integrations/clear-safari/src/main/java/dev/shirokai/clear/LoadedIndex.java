package dev.shirokai.clear;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class LoadedIndex<T> {
  private final NavigableMap<Long, T> entries = new TreeMap<Long, T>();
  private final Map<T, Long> keys = new IdentityHashMap<T, Long>();
  private long sequence;

  public void add(T value) {
    if (!this.keys.containsKey(value)) {
      long id = ++this.sequence;
      this.keys.put(value, id);
      this.entries.put(id, value);
    }
  }

  public void remove(T value) {
    Long id = this.keys.remove(value);
    if (id != null) {
      this.entries.remove(id);
    }
  }

  public long boundary() {
    return this.sequence;
  }

  public Map.Entry<Long, T> after(long cursor) {
    return this.entries.higherEntry(cursor);
  }

  public void clear() {
    this.entries.clear();
    this.keys.clear();
    this.sequence = 0L;
  }
}
