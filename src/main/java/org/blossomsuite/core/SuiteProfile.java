package org.blossomsuite.core;

import org.blossomsuite.core.util.TextUtil;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class SuiteProfile {
   private final String modId;
   private final String displayName;
   private final String commandName;
   private final List<String> commandAliases;
   private final String configFileName;
   private final String apiBaseUrl;
   private final String ingestKey;
   private final String ingestHeaderName;
   private final Set<SuiteFeature> enabledFeatures;
   private final List<SuiteServer> servers;
   private final Map<String, SuiteServer> serversByKey;
   private final List<SuiteDungeon> dungeons;
   private final List<SuiteResource> resources;
   private final List<String> autoFlyExcludedWorlds;
   private final Set<String> normalizedAutoFlyExcludedWorlds;

   public SuiteProfile(
      String modId,
      String displayName,
      String commandName,
      List<String> commandAliases,
      String configFileName,
      String apiBaseUrl,
      String ingestKey,
      String ingestHeaderName,
      Set<SuiteFeature> enabledFeatures,
      List<SuiteServer> servers,
      List<SuiteDungeon> dungeons,
      List<SuiteResource> resources,
      List<String> autoFlyExcludedWorlds
   ) {
      this.modId = requireText(modId, "modId");
      this.displayName = requireText(displayName, "displayName");
      this.commandName = requireText(commandName, "commandName");
      this.commandAliases = List.copyOf(commandAliases == null ? List.of() : commandAliases);
      this.configFileName = requireText(configFileName, "configFileName");
      this.apiBaseUrl = apiBaseUrl == null ? "" : apiBaseUrl.trim();
      this.ingestKey = Objects.requireNonNullElse(ingestKey, "");
      this.ingestHeaderName = requireText(ingestHeaderName, "ingestHeaderName");
      EnumSet<SuiteFeature> features = enabledFeatures != null && !enabledFeatures.isEmpty()
         ? EnumSet.copyOf(enabledFeatures)
         : EnumSet.noneOf(SuiteFeature.class);
      this.enabledFeatures = Collections.unmodifiableSet(features);
      this.servers = List.copyOf(servers == null ? List.of() : servers);
      LinkedHashMap<String, SuiteServer> byKey = new LinkedHashMap<>();

      for (SuiteServer server : this.servers) {
         byKey.put(server.key(), server);
      }

      this.serversByKey = Collections.unmodifiableMap(byKey);
      this.dungeons = List.copyOf(dungeons == null ? List.of() : dungeons);
      this.resources = List.copyOf(resources == null ? List.of() : resources);
      this.autoFlyExcludedWorlds = List.copyOf(autoFlyExcludedWorlds == null ? List.of() : autoFlyExcludedWorlds);
      this.normalizedAutoFlyExcludedWorlds = this.autoFlyExcludedWorlds
         .stream()
         .map(SuiteProfile::normalizeWorldKey)
         .filter(s -> !s.isBlank())
         .collect(Collectors.toUnmodifiableSet());
   }

   public String modId() {
      return this.modId;
   }

   public String displayName() {
      return this.displayName;
   }

   public String commandName() {
      return this.commandName;
   }

   public List<String> commandAliases() {
      return this.commandAliases;
   }

   public String configFileName() {
      return this.configFileName;
   }

   /** True only when a backend URL has been configured. With none, no network request is ever made. */
   public boolean hasBackend() {
      return !this.apiBaseUrl.isBlank();
   }

   public String apiBaseUrl() {
      return this.apiBaseUrl;
   }

   public String ingestKey() {
      return this.ingestKey;
   }

   public String ingestHeaderName() {
      return this.ingestHeaderName;
   }

   public Set<SuiteFeature> enabledFeatures() {
      return this.enabledFeatures;
   }

   public boolean isEnabled(SuiteFeature feature) {
      return this.enabledFeatures.contains(feature);
   }

   public List<SuiteServer> servers() {
      return this.servers;
   }

   public boolean isTrackedServerKey(String serverKey) {
      return this.serversByKey.containsKey(this.resolveServerKey(serverKey));
   }

   public String resolveServerKey(String serverNameOrKey) {
      if (serverNameOrKey != null && !serverNameOrKey.isBlank()) {
         String key = SuiteServer.normalizeKey(serverNameOrKey);
         if (this.serversByKey.containsKey(key)) {
            return key;
         }

         for (SuiteServer server : this.servers) {
            if (server.matches(serverNameOrKey)) {
               return server.key();
            }
         }

         return key;
      } else {
         return "";
      }
   }

   public String serverDisplayName(String serverNameOrKey) {
      String key = this.resolveServerKey(serverNameOrKey);
      SuiteServer server = this.serversByKey.get(key);
      return server == null ? SuiteServer.defaultDisplayName(serverNameOrKey) : server.displayName();
   }

   public List<SuiteDungeon> dungeons() {
      return this.dungeons;
   }

   public String primaryDungeonName() {
      return this.dungeons.isEmpty() ? "Dungeon" : this.dungeons.get(0).displayName();
   }

   public List<SuiteResource> resources() {
      return this.resources;
   }

   public String primaryResourceDisplayName() {
      return this.resources.isEmpty() ? "Resource" : this.resources.get(0).displayName();
   }

   public boolean matchesAnyTrackedResource(String itemId, String foldedName, String compactName) {
      for (SuiteResource resource : this.resources) {
         if (resource.matchesItem(itemId, foldedName, compactName)) {
            return true;
         }
      }

      return false;
   }

   public List<String> autoFlyExcludedWorlds() {
      return this.autoFlyExcludedWorlds;
   }

   public boolean isAutoFlyExcludedWorld(String worldName) {
      return this.normalizedAutoFlyExcludedWorlds.contains(normalizeWorldKey(worldName));
   }

   private static String normalizeWorldKey(String value) {
      return value != null && !value.isBlank() ? TextUtil.foldToLettersDigitsSpace(value).replace(" ", "").toLowerCase(Locale.ROOT) : "";
   }

   private static String requireText(String value, String field) {
      if (value != null && !value.isBlank()) {
         return value;
      } else {
         throw new IllegalArgumentException(field + " must not be blank");
      }
   }
}
