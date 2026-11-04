package com.istxicun.backend.distributed.cache;

public record CacheResult<T>(State state, T value) {
  public enum State {
    MISS,
    NULL,
    VALUE
  }

  public CacheResult {
    if (state == null || (state == State.VALUE) != (value != null))
      throw new IllegalArgumentException("Invalid cache result");
  }

  public boolean hit() {
    return state != State.MISS;
  }

  public static <T> CacheResult<T> miss() {
    return new CacheResult<>(State.MISS, null);
  }

  public static <T> CacheResult<T> hit(T value) {
    return new CacheResult<>(value == null ? State.NULL : State.VALUE, value);
  }
}
