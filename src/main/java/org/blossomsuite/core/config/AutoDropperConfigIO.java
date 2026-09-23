package org.blossomsuite.core.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.blossomsuite.core.SuiteRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class AutoDropperConfigIO {
   private AutoDropperConfigIO() {
   }

   private static Path file() {
      return FabricLoader.getInstance().getConfigDir().resolve(SuiteRuntime.profile().modId()).resolve("autodropper.json");
   }

   private static Path legacyAutoDropFile() {
      return FabricLoader.getInstance().getConfigDir().resolve("autodrop.json");
   }

   public static void loadInto(QolConfig cfg) {
      Path file = file();
      if (cfg != null && Files.exists(file)) {
         try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            readSuiteAutoDropper(cfg, root);
         } catch (Exception var3) {
         }
      }
   }

   public static void saveFrom(QolConfig cfg) {
      if (cfg != null) {
         Path file = file();

         try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, writeSuiteAutoDropper(cfg).toString());
         } catch (IOException var3) {
         }
      }
   }

   public static AutoDropperConfigIO.ImportResult importLegacyAutoDrop() {
      return importLegacyAutoDrop(legacyAutoDropFile());
   }

   public static boolean legacyAutoDropConfigExists() {
      return Files.exists(legacyAutoDropFile());
   }

   public static AutoDropperConfigIO.ImportResult importLegacyAutoDrop(Path path) {
      if (path != null && Files.exists(path)) {
         try {
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            JsonObject config = object(root, "config");
            if (config == null) {
               return new AutoDropperConfigIO.ImportResult(false, 0, 0, "AutoDrop config did not contain a config object.");
            }

            JsonArray archives = array(config, "archives");
            if (archives == null) {
               return new AutoDropperConfigIO.ImportResult(false, 0, 0, "AutoDrop config did not contain archives.");
            }

            QolConfig cfg = SuiteConfig.INSTANCE.QolConfig;
            int itemCount = 0;
            int importedGroups = 0;
            int firstImportedIndex = cfg.autoDropperCustomGroups.size();
            int groupIndex = 1;
            long importId = System.currentTimeMillis();

            for (JsonElement archiveElement : archives) {
               if (archiveElement.isJsonObject()) {
                  JsonObject archive = archiveElement.getAsJsonObject();
                  QolConfig.AutoDropCustomGroup group = new QolConfig.AutoDropCustomGroup();
                  group.id = "autodrop-import-" + importId + "-" + groupIndex;
                  group.name = uniqueGroupName(cfg, string(archive, "name", "Imported " + groupIndex));
                  group.enabled = bool(archive, "enabled", true);
                  JsonArray entries = array(archive, "entries");
                  if (entries != null) {
                     for (JsonElement entryElement : entries) {
                        if (entryElement.isJsonObject()) {
                           JsonObject entry = entryElement.getAsJsonObject();
                           String itemId = string(entry, "type", "").trim();
                           if (!itemId.isBlank() && groupItemById(group, itemId) == null) {
                              int amount = Math.max(0, integer(entry, "amount", 0));
                              boolean dropEverything = bool(entry, "dropEverything", true);
                              String predicate = string(entry, "predicate", "");
                              QolConfig.AutoDropGroupItem item = new QolConfig.AutoDropGroupItem();
                              item.itemId = itemId;
                              item.minimumAmount = amount;
                              item.keepItems = dropEverything ? 0 : amount;
                              item.keepStacks = 0;
                              item.componentFilter = predicate != null && !predicate.isBlank() && !"[]".equals(predicate.trim()) ? predicate.trim() : "";
                              group.items.add(item);
                              itemCount++;
                           }
                        }
                     }
                  }

                  cfg.autoDropperCustomGroups.add(group);
                  importedGroups++;
                  groupIndex++;
               }
            }

            if (firstImportedIndex == 0 && !cfg.autoDropperCustomGroups.isEmpty()) {
               cfg.autoDropperActiveCustomGroup = 0;
            } else if (cfg.autoDropperActiveCustomGroup >= cfg.autoDropperCustomGroups.size()) {
               cfg.autoDropperActiveCustomGroup = Math.max(0, cfg.autoDropperCustomGroups.size() - 1);
            }

            SuiteConfig.INSTANCE.markDirty();
            return new AutoDropperConfigIO.ImportResult(true, importedGroups, itemCount, "Imported AutoDrop groups.");
         } catch (Exception e) {
            return new AutoDropperConfigIO.ImportResult(false, 0, 0, "Failed to import AutoDrop config: " + e.getMessage());
         }
      } else {
         return new AutoDropperConfigIO.ImportResult(false, 0, 0, "AutoDrop config not found: " + (path == null ? "" : path));
      }
   }

   private static void readSuiteAutoDropper(QolConfig cfg, JsonObject root) {
      cfg.autoDropperEnabled = bool(root, "enabled", cfg.autoDropperEnabled);
      cfg.autoDropperOnPickup = bool(root, "onPickup", cfg.autoDropperOnPickup);
      cfg.autoDropperOnSneak = bool(root, "onSneak", cfg.autoDropperOnSneak);
      cfg.autoDropperDelayMs = Math.max(0, Math.min(10000, integer(root, "delayMs", cfg.autoDropperDelayMs)));
      cfg.autoDropperMaxStacksPerTick = Math.max(1, Math.min(64, integer(root, "maxStacksPerTick", cfg.autoDropperMaxStacksPerTick)));
      cfg.autoDropperIncludeHotbar = bool(root, "includeHotbar", cfg.autoDropperIncludeHotbar);
      cfg.autoDropperProtectSelectedSlot = bool(root, "protectSelectedSlot", cfg.autoDropperProtectSelectedSlot);
      cfg.autoDropperPauseWhileScreenOpen = bool(root, "pauseWhileScreenOpen", cfg.autoDropperPauseWhileScreenOpen);
      cfg.autoDropperPauseWhileSneaking = bool(root, "pauseWhileSneaking", cfg.autoDropperPauseWhileSneaking);
      cfg.autoDropperPauseOnPlayerAttack = bool(root, "pauseOnPlayerAttack", cfg.autoDropperPauseOnPlayerAttack);
      cfg.autoDropperPauseOnAttackingPlayer = bool(root, "pauseOnAttackingPlayer", cfg.autoDropperPauseOnAttackingPlayer);
      cfg.autoDropperPauseOnTargetingPlayer = bool(root, "pauseOnTargetingPlayer", cfg.autoDropperPauseOnTargetingPlayer);
      cfg.autoDropperActiveCustomGroup = Math.max(0, integer(root, "activeCustomGroup", cfg.autoDropperActiveCustomGroup));
      cfg.autoDropperRules.clear();
      JsonArray rules = array(root, "rules");
      if (rules != null) {
         for (JsonElement element : rules) {
            if (element.isJsonObject()) {
               QolConfig.AutoDropRule rule = readRule(element.getAsJsonObject());
               if (rule != null) {
                  cfg.autoDropperRules.add(rule);
               }
            }
         }
      }

      cfg.autoDropperCustomGroups.clear();
      JsonArray groups = array(root, "customGroups");
      if (groups != null) {
         for (JsonElement element : groups) {
            if (element.isJsonObject()) {
               QolConfig.AutoDropCustomGroup group = readGroup(element.getAsJsonObject());
               if (group != null) {
                  cfg.autoDropperCustomGroups.add(group);
               }
            }
         }
      }

      if (cfg.autoDropperActiveCustomGroup >= cfg.autoDropperCustomGroups.size()) {
         cfg.autoDropperActiveCustomGroup = Math.max(0, cfg.autoDropperCustomGroups.size() - 1);
      }
   }

   private static JsonObject writeSuiteAutoDropper(QolConfig cfg) {
      JsonObject root = new JsonObject();
      root.addProperty("version", 1);
      root.addProperty("enabled", cfg.autoDropperEnabled);
      root.addProperty("onPickup", cfg.autoDropperOnPickup);
      root.addProperty("onSneak", cfg.autoDropperOnSneak);
      root.addProperty("delayMs", cfg.autoDropperDelayMs);
      root.addProperty("maxStacksPerTick", cfg.autoDropperMaxStacksPerTick);
      root.addProperty("includeHotbar", cfg.autoDropperIncludeHotbar);
      root.addProperty("protectSelectedSlot", cfg.autoDropperProtectSelectedSlot);
      root.addProperty("pauseWhileScreenOpen", cfg.autoDropperPauseWhileScreenOpen);
      root.addProperty("pauseWhileSneaking", cfg.autoDropperPauseWhileSneaking);
      root.addProperty("pauseOnPlayerAttack", cfg.autoDropperPauseOnPlayerAttack);
      root.addProperty("pauseOnAttackingPlayer", cfg.autoDropperPauseOnAttackingPlayer);
      root.addProperty("pauseOnTargetingPlayer", cfg.autoDropperPauseOnTargetingPlayer);
      root.addProperty("activeCustomGroup", cfg.autoDropperActiveCustomGroup);
      JsonArray rules = new JsonArray();

      for (QolConfig.AutoDropRule rule : cfg.autoDropperRules) {
         rules.add(writeRule(rule));
      }

      root.add("rules", rules);
      JsonArray groups = new JsonArray();

      for (QolConfig.AutoDropCustomGroup group : cfg.autoDropperCustomGroups) {
         groups.add(writeGroup(group));
      }

      root.add("customGroups", groups);
      return root;
   }

   private static JsonObject writeRule(QolConfig.AutoDropRule rule) {
      if (rule == null) {
         rule = new QolConfig.AutoDropRule();
      }

      JsonObject obj = new JsonObject();
      obj.addProperty("type", String.valueOf(rule.type));
      obj.addProperty("itemId", rule.itemId == null ? "" : rule.itemId);
      obj.addProperty("customGroupId", rule.customGroupId == null ? "" : rule.customGroupId);
      obj.addProperty("minimumAmount", Math.max(0, rule.minimumAmount));
      obj.addProperty("keepItems", Math.max(0, rule.keepItems));
      obj.addProperty("keepStacks", Math.max(0, rule.keepStacks));
      obj.addProperty("enabled", rule.enabled);
      obj.addProperty("componentFilter", rule.componentFilter == null ? "" : rule.componentFilter);
      return obj;
   }

   private static QolConfig.AutoDropRule readRule(JsonObject obj) {
      QolConfig.AutoDropRule rule = new QolConfig.AutoDropRule();

      try {
         rule.type = QolConfig.AutoDropTargetType.valueOf(string(obj, "type", "ITEM"));
      } catch (Exception ignored) {
         rule.type = QolConfig.AutoDropTargetType.ITEM;
      }

      rule.itemId = string(obj, "itemId", "minecraft:cobblestone");
      rule.customGroupId = string(obj, "customGroupId", "");
      rule.minimumAmount = Math.max(0, integer(obj, "minimumAmount", 0));
      rule.keepItems = Math.max(0, integer(obj, "keepItems", 0));
      rule.keepStacks = Math.max(0, integer(obj, "keepStacks", 0));
      rule.enabled = bool(obj, "enabled", true);
      rule.componentFilter = string(obj, "componentFilter", "");
      return rule;
   }

   private static JsonObject writeGroup(QolConfig.AutoDropCustomGroup group) {
      if (group == null) {
         group = new QolConfig.AutoDropCustomGroup();
      }

      JsonObject obj = new JsonObject();
      obj.addProperty("id", group.id == null ? "" : group.id);
      obj.addProperty("name", group.name == null ? "Custom Group" : group.name);
      obj.addProperty("enabled", group.enabled);
      JsonArray items = new JsonArray();

      for (QolConfig.AutoDropGroupItem item : group.items) {
         items.add(writeItem(item));
      }

      obj.add("items", items);
      return obj;
   }

   private static QolConfig.AutoDropCustomGroup readGroup(JsonObject obj) {
      QolConfig.AutoDropCustomGroup group = new QolConfig.AutoDropCustomGroup();
      group.id = string(obj, "id", "drop-" + System.currentTimeMillis());
      group.name = string(obj, "name", "Custom Group");
      group.enabled = bool(obj, "enabled", true);
      JsonArray items = array(obj, "items");
      if (items != null) {
         for (JsonElement element : items) {
            if (element.isJsonObject()) {
               QolConfig.AutoDropGroupItem item = readItem(element.getAsJsonObject());
               if (item != null && !item.itemId.isBlank() && groupItemById(group, item.itemId) == null) {
                  group.items.add(item);
               }
            }
         }
      }

      return group;
   }

   private static JsonObject writeItem(QolConfig.AutoDropGroupItem item) {
      if (item == null) {
         item = new QolConfig.AutoDropGroupItem();
      }

      JsonObject obj = new JsonObject();
      obj.addProperty("itemId", item.itemId == null ? "" : item.itemId);
      obj.addProperty("minimumAmount", Math.max(0, item.minimumAmount));
      obj.addProperty("keepItems", Math.max(0, item.keepItems));
      obj.addProperty("keepStacks", Math.max(0, item.keepStacks));
      obj.addProperty("componentFilter", item.componentFilter == null ? "" : item.componentFilter);
      return obj;
   }

   private static QolConfig.AutoDropGroupItem readItem(JsonObject obj) {
      QolConfig.AutoDropGroupItem item = new QolConfig.AutoDropGroupItem();
      item.itemId = string(obj, "itemId", "").trim();
      item.minimumAmount = Math.max(0, integer(obj, "minimumAmount", 0));
      item.keepItems = Math.max(0, integer(obj, "keepItems", 0));
      item.keepStacks = Math.max(0, integer(obj, "keepStacks", 0));
      item.componentFilter = string(obj, "componentFilter", "");
      return item.itemId.isBlank() ? null : item;
   }

   private static QolConfig.AutoDropGroupItem groupItemById(QolConfig.AutoDropCustomGroup group, String itemId) {
      if (group != null && itemId != null) {
         for (QolConfig.AutoDropGroupItem item : group.items) {
            if (item != null && itemId.equals(item.itemId)) {
               return item;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static String uniqueGroupName(QolConfig cfg, String requestedName) {
      String base = requestedName != null && !requestedName.isBlank() ? requestedName.trim() : "Imported";
      if (!groupNameExists(cfg, base)) {
         return base;
      }

      for (int i = 2; i < 1000; i++) {
         String candidate = base + " (" + i + ")";
         if (!groupNameExists(cfg, candidate)) {
            return candidate;
         }
      }

      return base + " (" + System.currentTimeMillis() + ")";
   }

   private static boolean groupNameExists(QolConfig cfg, String name) {
      if (cfg != null && cfg.autoDropperCustomGroups != null && name != null) {
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

   private static JsonObject object(JsonObject obj, String key) {
      return obj != null && obj.has(key) && obj.get(key).isJsonObject() ? obj.getAsJsonObject(key) : null;
   }

   private static JsonArray array(JsonObject obj, String key) {
      return obj != null && obj.has(key) && obj.get(key).isJsonArray() ? obj.getAsJsonArray(key) : null;
   }

   private static String string(JsonObject obj, String key, String def) {
      try {
         return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def;
      } catch (Exception ignored) {
         return def;
      }
   }

   private static boolean bool(JsonObject obj, String key, boolean def) {
      try {
         return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsBoolean() : def;
      } catch (Exception ignored) {
         return def;
      }
   }

   private static int integer(JsonObject obj, String key, int def) {
      try {
         return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsInt() : def;
      } catch (Exception ignored) {
         return def;
      }
   }

   public record ImportResult(boolean success, int groups, int items, String message) {
   }
}
