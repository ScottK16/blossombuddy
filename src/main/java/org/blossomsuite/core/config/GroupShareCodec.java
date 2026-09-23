package org.blossomsuite.core.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class GroupShareCodec {
   private static final String AUTOSWAP_PREFIX = "BSWAP1:";
   private static final String AUTODROP_PREFIX = "BDROP1:";

   private GroupShareCodec() {
   }

   public static String exportAutoSwapGroup(QolConfig.AutoSwapCustomGroup group) {
      JsonObject root = new JsonObject();
      root.addProperty("type", "autoswap");
      root.addProperty("version", 1);
      root.addProperty("name", safeName(group == null ? null : group.name, "Custom Group"));
      JsonArray blocks = new JsonArray();
      if (group != null) {
         for (String blockId : group.blockIds) {
            if (blockId != null && !blockId.isBlank()) {
               blocks.add(blockId.trim());
            }
         }
      }

      root.add("blocks", blocks);
      return "BSWAP1:" + encode(root);
   }

   public static String exportAutoDropGroup(QolConfig.AutoDropCustomGroup group) {
      JsonObject root = new JsonObject();
      root.addProperty("type", "autodrop");
      root.addProperty("version", 1);
      root.addProperty("name", safeName(group == null ? null : group.name, "Custom Group"));
      root.addProperty("enabled", group == null || group.enabled);
      JsonArray items = new JsonArray();
      if (group != null) {
         for (QolConfig.AutoDropGroupItem item : group.items) {
            if (item != null && item.itemId != null && !item.itemId.isBlank()) {
               JsonObject obj = new JsonObject();
               obj.addProperty("itemId", item.itemId.trim());
               obj.addProperty("minimumAmount", Math.max(0, item.minimumAmount));
               obj.addProperty("keepItems", Math.max(0, item.keepItems));
               obj.addProperty("keepStacks", Math.max(0, item.keepStacks));
               obj.addProperty("componentFilter", item.componentFilter == null ? "" : item.componentFilter);
               items.add(obj);
            }
         }
      }

      root.add("items", items);
      return "BDROP1:" + encode(root);
   }

   public static GroupShareCodec.ImportResult importAutoSwapGroup(QolConfig cfg, String code) {
      try {
         JsonObject root = decodeWithPrefix(code, "BSWAP1:");
         if (!"autoswap".equalsIgnoreCase(string(root, "type", ""))) {
            return GroupShareCodec.ImportResult.fail("That code is not an AutoSwapper group.");
         }

         QolConfig.AutoSwapCustomGroup group = new QolConfig.AutoSwapCustomGroup();
         group.id = "share-swap-" + System.currentTimeMillis() + "-" + (cfg.autoSwapperCustomGroups.size() + 1);
         group.name = uniqueAutoSwapName(cfg, string(root, "name", "Imported Group"));
         JsonArray blocks = array(root, "blocks");
         if (blocks != null) {
            for (JsonElement element : blocks) {
               if (element != null && !element.isJsonNull()) {
                  String id = element.getAsString();
                  if (id != null && !id.isBlank() && !group.blockIds.contains(id.trim())) {
                     group.blockIds.add(id.trim());
                  }
               }
            }
         }

         cfg.autoSwapperCustomGroups.add(group);
         cfg.autoSwapperActiveCustomGroup = cfg.autoSwapperCustomGroups.size() - 1;
         SuiteConfig.INSTANCE.markDirty();
         return GroupShareCodec.ImportResult.ok(group.name, group.blockIds.size());
      } catch (Exception e) {
         return GroupShareCodec.ImportResult.fail("Could not import AutoSwapper group.");
      }
   }

   public static GroupShareCodec.ImportResult importAutoDropGroup(QolConfig cfg, String code) {
      try {
         JsonObject root = decodeWithPrefix(code, "BDROP1:");
         if (!"autodrop".equalsIgnoreCase(string(root, "type", ""))) {
            return GroupShareCodec.ImportResult.fail("That code is not an AutoDropper group.");
         }

         QolConfig.AutoDropCustomGroup group = new QolConfig.AutoDropCustomGroup();
         group.id = "share-drop-" + System.currentTimeMillis() + "-" + (cfg.autoDropperCustomGroups.size() + 1);
         group.name = uniqueAutoDropName(cfg, string(root, "name", "Imported Group"));
         group.enabled = bool(root, "enabled", true);
         JsonArray items = array(root, "items");
         if (items != null) {
            for (JsonElement element : items) {
               if (element != null && element.isJsonObject()) {
                  JsonObject obj = element.getAsJsonObject();
                  QolConfig.AutoDropGroupItem item = new QolConfig.AutoDropGroupItem();
                  item.itemId = string(obj, "itemId", "").trim();
                  if (!item.itemId.isBlank() && !autoDropItemExists(group, item.itemId)) {
                     item.minimumAmount = Math.max(0, integer(obj, "minimumAmount", 0));
                     item.keepItems = Math.max(0, integer(obj, "keepItems", 0));
                     item.keepStacks = Math.max(0, integer(obj, "keepStacks", 0));
                     item.componentFilter = string(obj, "componentFilter", "");
                     group.items.add(item);
                  }
               }
            }
         }

         cfg.autoDropperCustomGroups.add(group);
         cfg.autoDropperActiveCustomGroup = cfg.autoDropperCustomGroups.size() - 1;
         SuiteConfig.INSTANCE.markDirty();
         return GroupShareCodec.ImportResult.ok(group.name, group.items.size());
      } catch (Exception e) {
         return GroupShareCodec.ImportResult.fail("Could not import AutoDropper group.");
      }
   }

   private static String encode(JsonObject root) {
      return Base64.getUrlEncoder().withoutPadding().encodeToString(root.toString().getBytes(StandardCharsets.UTF_8));
   }

   private static JsonObject decodeWithPrefix(String code, String prefix) {
      if (code == null) {
         throw new IllegalArgumentException("missing code");
      }

      String trimmed = code.trim();
      if (!trimmed.startsWith(prefix)) {
         throw new IllegalArgumentException("wrong prefix");
      }

      String payload = trimmed.substring(prefix.length()).replaceAll("\\s+", "");
      byte[] bytes = Base64.getUrlDecoder().decode(payload);
      return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
   }

   private static String uniqueAutoSwapName(QolConfig cfg, String name) {
      String base = safeName(name, "Imported Group");
      if (!autoSwapNameExists(cfg, base)) {
         return base;
      }

      for (int i = 2; i < 1000; i++) {
         String candidate = base + " (" + i + ")";
         if (!autoSwapNameExists(cfg, candidate)) {
            return candidate;
         }
      }

      return base + " (" + System.currentTimeMillis() + ")";
   }

   private static String uniqueAutoDropName(QolConfig cfg, String name) {
      String base = safeName(name, "Imported Group");
      if (!autoDropNameExists(cfg, base)) {
         return base;
      }

      for (int i = 2; i < 1000; i++) {
         String candidate = base + " (" + i + ")";
         if (!autoDropNameExists(cfg, candidate)) {
            return candidate;
         }
      }

      return base + " (" + System.currentTimeMillis() + ")";
   }

   private static boolean autoSwapNameExists(QolConfig cfg, String name) {
      if (cfg != null && name != null) {
         for (QolConfig.AutoSwapCustomGroup group : cfg.autoSwapperCustomGroups) {
            if (group != null && group.name != null && group.name.equalsIgnoreCase(name)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean autoDropNameExists(QolConfig cfg, String name) {
      if (cfg != null && name != null) {
         for (QolConfig.AutoDropCustomGroup group : cfg.autoDropperCustomGroups) {
            if (group != null && group.name != null && group.name.equalsIgnoreCase(name)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean autoDropItemExists(QolConfig.AutoDropCustomGroup group, String itemId) {
      if (group != null && itemId != null) {
         for (QolConfig.AutoDropGroupItem item : group.items) {
            if (item != null && itemId.equals(item.itemId)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static String safeName(String name, String fallback) {
      return name != null && !name.isBlank() ? name.trim() : fallback;
   }

   private static JsonArray array(JsonObject obj, String key) {
      return obj != null && obj.has(key) && obj.get(key).isJsonArray() ? obj.getAsJsonArray(key) : null;
   }

   private static String string(JsonObject obj, String key, String def) {
      try {
         return obj != null && obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def;
      } catch (Exception ignored) {
         return def;
      }
   }

   private static boolean bool(JsonObject obj, String key, boolean def) {
      try {
         return obj != null && obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsBoolean() : def;
      } catch (Exception ignored) {
         return def;
      }
   }

   private static int integer(JsonObject obj, String key, int def) {
      try {
         return obj != null && obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsInt() : def;
      } catch (Exception ignored) {
         return def;
      }
   }

   public record ImportResult(boolean success, String name, int entries, String message) {
      public static GroupShareCodec.ImportResult ok(String name, int entries) {
         return new GroupShareCodec.ImportResult(true, name, entries, "Imported group.");
      }

      public static GroupShareCodec.ImportResult fail(String message) {
         return new GroupShareCodec.ImportResult(false, "", 0, message);
      }
   }
}
