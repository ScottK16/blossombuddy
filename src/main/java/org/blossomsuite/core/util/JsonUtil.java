package org.blossomsuite.core.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class JsonUtil {
   public static final Gson GSON = new GsonBuilder().create();

   private JsonUtil() {
   }
}
