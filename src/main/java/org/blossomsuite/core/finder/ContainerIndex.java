package org.blossomsuite.core.finder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What you have seen inside the chests, barrels and other containers you opened: kept on your computer so you can ask "where did I put
 * my diamonds?". It only knows what was in a container the last time you looked, and never sends anything anywhere.
 */
public final class ContainerIndex {
   public static final int MAX_CONTAINERS = 3000;
   private static final Gson GSON = new GsonBuilder().create();
   private static final Type LIST = new TypeToken<List<Container>>() { }.getType();

   /** One kind of item in a container, e.g. 128 of "Diamond". */
   public static final class Stack {
      public String id = "";
      public String name = "";
      public int count = 0;

      public Stack() {
      }

      public Stack(String id, String name, int count) {
         this.id = id;
         this.name = name;
         this.count = count;
      }
   }

   public static final class Container {
      public String key = "";
      public String realm = "";
      public String dimension = "";
      public String label = "";
      public int x;
      public int y;
      public int z;
      /** An ender chest is yours everywhere, so it has no place to point at. */
      public boolean portable = false;
      public long updatedMs = 0L;
      public List<Stack> items = new ArrayList<>();
   }

   /** A container that has something matching a search in it. */
   public record Hit(Container container, int count, String itemName) {
   }

   private final Map<String, Container> byKey = new LinkedHashMap<>();

   public static String keyFor(String realm, String dimension, int x, int y, int z, boolean portable) {
      String r = realm == null || realm.isBlank() ? "unknown" : realm.toLowerCase(Locale.ROOT);
      return portable ? r + "|ender" : r + "|" + dimension + "|" + x + "|" + y + "|" + z;
   }

   public synchronized int size() {
      return this.byKey.size();
   }

   /** Remembers what is in a container now (an empty one is remembered as empty, so old contents are forgotten). */
   public synchronized void record(Container c) {
      this.byKey.remove(c.key); // re-insert last, so the oldest are the ones that get dropped
      this.byKey.put(c.key, c);
      while (this.byKey.size() > MAX_CONTAINERS) {
         String oldest = this.byKey.keySet().iterator().next();
         this.byKey.remove(oldest);
      }
   }

   public synchronized void forget(String key) {
      this.byKey.remove(key);
   }

   public synchronized void clear() {
      this.byKey.clear();
   }

   /** Containers that hold something matching every word of {@code query}, the most items first. */
   public synchronized List<Hit> search(String query, String realm) {
      List<String> words = new ArrayList<>();
      for (String w : (query == null ? "" : query).toLowerCase(Locale.ROOT).trim().split("\\s+")) {
         if (!w.isEmpty()) {
            words.add(w);
         }
      }

      List<Hit> hits = new ArrayList<>();
      if (words.isEmpty()) {
         return hits;
      }

      for (Container c : this.byKey.values()) {
         if (realm != null && !realm.isBlank() && !realm.equalsIgnoreCase(c.realm)) {
            continue;
         }

         int total = 0;
         String firstName = "";
         for (Stack s : c.items) {
            if (matches(s, words)) {
               total += s.count;
               if (firstName.isEmpty()) {
                  firstName = s.name == null || s.name.isBlank() ? prettyId(s.id) : s.name;
               }
            }
         }

         if (total > 0) {
            hits.add(new Hit(c, total, firstName));
         }
      }

      hits.sort(Comparator.comparingInt(Hit::count).reversed().thenComparing(h -> -h.container().updatedMs));
      return hits;
   }

   static boolean matches(Stack s, List<String> words) {
      String hay = ((s.name == null ? "" : s.name) + " " + prettyId(s.id)).toLowerCase(Locale.ROOT);
      for (String w : words) {
         if (!hay.contains(w)) {
            return false;
         }
      }

      return true;
   }

   /** "minecraft:diamond_block" -> "diamond block". */
   static String prettyId(String id) {
      if (id == null) {
         return "";
      }

      int colon = id.indexOf(':');
      return (colon >= 0 ? id.substring(colon + 1) : id).replace('_', ' ');
   }

   // ------------------------------------------------------------------ saving

   public synchronized void save(Path file) {
      try {
         Files.createDirectories(file.getParent());
         Files.writeString(file, GSON.toJson(new ArrayList<>(this.byKey.values()), LIST), StandardCharsets.UTF_8);
      } catch (IOException e) {
         // losing the index is a nuisance, not a reason to interrupt play
      }
   }

   public synchronized void load(Path file) {
      this.byKey.clear();
      if (!Files.isRegularFile(file)) {
         return;
      }

      try {
         List<Container> list = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), LIST);
         if (list != null) {
            for (Container c : list) {
               if (c != null && c.key != null && !c.key.isEmpty()) {
                  if (c.items == null) {
                     c.items = new ArrayList<>();
                  }

                  this.byKey.put(c.key, c);
               }
            }
         }
      } catch (IOException | RuntimeException unreadable) {
         this.byKey.clear(); // a damaged file just starts the index again
      }
   }
}
