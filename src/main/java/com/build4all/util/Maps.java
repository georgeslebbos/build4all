 package com.build4all.util;

import java.util.HashMap;
import java.util.Map;

public final class Maps {
  private Maps() {}

  public static Map<String, Object> map(Object... kv) {
    // like Map.of but accepts nulls (skips pairs with null value)
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i + 1 < kv.length; i += 2) {
      String k = String.valueOf(kv[i]);
      Object v = kv[i + 1];
      if (v != null) m.put(k, v);
    }
    return m;
  }
}
