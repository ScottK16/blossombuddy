package org.blossomsuite.core.net;

import java.util.concurrent.ConcurrentHashMap;

public final class EtagCache {
   private final ConcurrentHashMap<String, String> etags = new ConcurrentHashMap<>();

   public String get(String key) {
      return this.etags.get(key);
   }

   public void set(String key, String etag) {
      if (etag != null) {
         String cleaned = etag.trim();
         if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() >= 2) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
         }

         this.etags.put(key, cleaned);
      }
   }
}
