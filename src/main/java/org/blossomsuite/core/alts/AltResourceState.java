package org.blossomsuite.core.alts;

import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.util.SuiteItemIdUtil;
import org.blossomsuite.core.util.TextUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

public final class AltResourceState {
   private static final Pattern BALANCE_PATTERN = Pattern.compile(
      "(?i)\\b(?:balance|bal|money|cash)\\b\\s*[:\\-]?\\s*\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kmbt]?)"
   );
   private static final Pattern CLAIM_BLOCKS_PATTERN = Pattern.compile("(?i)\\bclaim\\s*blocks?\\b\\s*[:\\-]?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kmbt]?)");
   private static final Pattern CLAIM_BLOCKS_COMPACT_PATTERN = Pattern.compile("(?i)\\bclaimblocks?\\b\\s*[:\\-]?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kmbt]?)");
   private static final Pattern CLAIM_BLOCKS_REVERSE_PATTERN = Pattern.compile("(?i)([0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kmbt]?)\\s*\\bclaim\\s*blocks?\\b");
   private static final long INVENTORY_SCAN_INTERVAL_MS = 1000L;
   private static final long UNCHANGED_PERSIST_INTERVAL_MS = 60000L;
   private static final Map<String, AltResourceState.Snapshot> BY_SERVER = new ConcurrentHashMap<>();
   private static long lastInventoryScanMs = 0L;

   private AltResourceState() {
   }

   public static AltResourceState.ScoreboardParse parseScoreboardLine(String normalizedLine) {
      if (normalizedLine != null && !normalizedLine.isBlank()) {
         String parserLine = normalizeForResourceParser(normalizedLine);
         String compactLine = parserLine.replace(" ", "");
         String balanceRaw = null;
         Double balanceValue = null;
         Long claimBlocks = null;
         Matcher balanceMatcher = BALANCE_PATTERN.matcher(parserLine);
         if (balanceMatcher.find()) {
            balanceRaw = balanceMatcher.group(1).replace(" ", "");
            balanceValue = parseScaledNumber(balanceRaw);
         }

         String claimBlocksRaw = firstGroup(CLAIM_BLOCKS_PATTERN, parserLine);
         if (claimBlocksRaw == null) {
            claimBlocksRaw = firstGroup(CLAIM_BLOCKS_COMPACT_PATTERN, compactLine);
         }

         if (claimBlocksRaw == null) {
            claimBlocksRaw = firstGroup(CLAIM_BLOCKS_REVERSE_PATTERN, parserLine);
         }

         claimBlocks = parseScaledLong(claimBlocksRaw);
         return new AltResourceState.ScoreboardParse(balanceRaw, balanceValue, claimBlocks);
      } else {
         return new AltResourceState.ScoreboardParse(null, null, null);
      }
   }

   public static void updateScoreboard(String serverName, AltResourceState.ScoreboardParse parse, long nowMs) {
      if (serverName != null && !serverName.isBlank() && parse != null && parse.hasAny()) {
         if (isTrackedServer(serverName)) {
            AltResourceState.Snapshot snapshot = snapshotFor(serverName);
            boolean changed = false;
            synchronized (snapshot) {
               snapshot.displayName = displayName(serverName);
               if (parse.balanceRaw() != null || parse.balanceValue() != null) {
                  changed |= !equals(snapshot.balanceRaw, parse.balanceRaw());
                  changed |= !equals(snapshot.balanceValue, parse.balanceValue());
                  snapshot.balanceRaw = parse.balanceRaw();
                  snapshot.balanceValue = parse.balanceValue();
                  changed |= shouldPersistObservedTimestamp(snapshot.balanceSeenAtMs, nowMs);
                  snapshot.balanceSeenAtMs = nowMs;
               }

               if (parse.claimBlocks() != null) {
                  changed |= !equals(snapshot.claimBlocks, parse.claimBlocks());
                  changed |= shouldPersistObservedTimestamp(snapshot.claimBlocksSeenAtMs, nowMs);
                  snapshot.claimBlocks = parse.claimBlocks();
                  snapshot.claimBlocksSeenAtMs = nowMs;
               }

               changed |= shouldPersistObservedTimestamp(snapshot.lastSeenAtMs, nowMs);
               snapshot.lastSeenAtMs = nowMs;
            }

            if (changed) {
               persistSnapshots();
            }
         }
      }
   }

   public static void tickInventoryScan(MinecraftClient client, String serverName, long nowMs) {
      if (nowMs - lastInventoryScanMs >= 1000L) {
         lastInventoryScanMs = nowMs;
         scanInventoryNow(client, serverName, nowMs);
         scanOpenContainerNow(client, serverName, nowMs);
      }
   }

   public static void scanInventoryNow(MinecraftClient client, String serverName, long nowMs) {
      if (client != null && client.player != null) {
         if (serverName != null && !serverName.isBlank()) {
            if (isTrackedServer(serverName)) {
               AltResourceState.ResourceSource openSource = classifyCurrentOpenContainer(client);
               if (openSource != null) {
                  scanInventoryAndOpenContainerNow(client, serverName, openSource, nowMs);
               } else {
                  int resources = 0;
                  PlayerInventory inventory = client.player.getInventory();

                  for (int i = 0; i < inventory.size(); i++) {
                     ItemStack stack = inventory.getStack(i);
                     if (isTrackedResource(stack)) {
                        resources += stack.getCount();
                     }
                  }

                  updateResourceSource(serverName, AltResourceState.ResourceSource.INVENTORY, resources, nowMs);
               }
            }
         }
      }
   }

   public static void scanOpenContainerNow(MinecraftClient client, String serverName, long nowMs) {
      if (client != null && client.player != null && client.currentScreen != null) {
         if (serverName != null && !serverName.isBlank()) {
            if (isTrackedServer(serverName)) {
               AltResourceState.ResourceSource source = classifyCurrentOpenContainer(client);
               if (source != null) {
                  scanInventoryAndOpenContainerNow(client, serverName, source, nowMs);
               }
            }
         }
      }
   }

   private static void scanInventoryAndOpenContainerNow(MinecraftClient client, String serverName, AltResourceState.ResourceSource source, long nowMs) {
      int inventoryResource = 0;
      PlayerInventory playerInventory = client.player.getInventory();

      for (int i = 0; i < playerInventory.size(); i++) {
         ItemStack stack = playerInventory.getStack(i);
         if (isTrackedResource(stack)) {
            inventoryResource += stack.getCount();
         }
      }

      int resources = 0;

      for (Slot slot : client.player.currentScreenHandler.slots) {
         if (slot != null && slot.inventory != playerInventory) {
            ItemStack stack = slot.getStack();
            if (isTrackedResource(stack)) {
               resources += stack.getCount();
            }
         }
      }

      updateInventoryAndContainerResource(serverName, inventoryResource, source, resources, nowMs);
   }

   public static List<AltResourceState.Snapshot> snapshots() {
      ArrayList<AltResourceState.Snapshot> out = new ArrayList<>(BY_SERVER.values());
      out.removeIf(snapshot -> !isTrackedServerKey(snapshot.serverKey));
      out.sort(
         Comparator.<AltResourceState.Snapshot, String>comparing(s -> s.displayName == null ? s.serverKey : s.displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(AltResourceState.Snapshot::serverKey)
      );
      return out;
   }

   public static String formatBalance(AltResourceState.Snapshot snapshot) {
      if (snapshot.balanceValue != null) {
         return "$" + TextUtil.fmtMoney(snapshot.balanceValue);
      } else {
         return snapshot.balanceRaw != null && !snapshot.balanceRaw.isBlank() ? "$" + snapshot.balanceRaw : "?";
      }
   }

   public static String formatClaimBlocks(AltResourceState.Snapshot snapshot) {
      return snapshot.claimBlocks == null ? "?" : String.format("%,d", snapshot.claimBlocks);
   }

   public static String formatTrackedResource(AltResourceState.Snapshot snapshot) {
      return snapshot.trackedResourceTotal == null ? "?" : String.format("%,d", snapshot.trackedResourceTotal);
   }

   public static String formatTrackedResourceBreakdown(AltResourceState.Snapshot snapshot) {
      if (snapshot == null) {
         return "";
      }

      String inv = formatNullableCount(snapshot.inventoryResource);
      String ender = formatNullableCount(snapshot.enderChestResource);
      String backpack = formatNullableCount(snapshot.backpackResource);
      return "Inv " + inv + ", EC " + ender + ", BP " + backpack;
   }

   public static String formatLastUpdated(AltResourceState.Snapshot snapshot, long nowMs) {
      long seenAt = snapshot.lastSeenAtMs();
      if (seenAt <= 0L) {
         return "never";
      }

      long ageSeconds = Math.max(0L, (nowMs - seenAt) / 1000L);
      if (ageSeconds < 60L) {
         return ageSeconds + "s ago";
      }

      long ageMinutes = ageSeconds / 60L;
      if (ageMinutes < 60L) {
         return ageMinutes + "m ago";
      }

      long ageHours = ageMinutes / 60L;
      if (ageHours < 24L) {
         return ageHours + "h ago";
      }

      long ageDays = ageHours / 24L;
      return ageDays + "d ago";
   }

   public static void loadPersistentSnapshots(List<String> encodedSnapshots) {
      BY_SERVER.clear();
      if (encodedSnapshots != null) {
         for (String encoded : encodedSnapshots) {
            AltResourceState.Snapshot snapshot = decodeSnapshot(encoded);
            if (snapshot != null && isTrackedServerKey(snapshot.serverKey)) {
               BY_SERVER.put(snapshot.serverKey, snapshot);
            }
         }
      }
   }

   public static List<String> encodePersistentSnapshots() {
      ArrayList<String> encoded = new ArrayList<>();

      for (AltResourceState.Snapshot snapshot : snapshots()) {
         synchronized (snapshot) {
            encoded.add(encodeSnapshot(snapshot));
         }
      }

      return encoded;
   }

   private static AltResourceState.Snapshot snapshotFor(String serverName) {
      String key = normalizeServerKey(serverName);
      return BY_SERVER.computeIfAbsent(key, ignored -> new AltResourceState.Snapshot(key, displayName(serverName)));
   }

   private static boolean isTrackedServer(String serverName) {
      return isTrackedServerKey(normalizeServerKey(serverName));
   }

   private static boolean isTrackedServerKey(String serverKey) {
      return SuiteRuntime.profile().isTrackedServerKey(serverKey);
   }

   private static void updateResourceSource(String serverName, AltResourceState.ResourceSource source, int resources, long nowMs) {
      AltResourceState.Snapshot snapshot = snapshotFor(serverName);
      boolean changed;
      synchronized (snapshot) {
         snapshot.displayName = displayName(serverName);

         Integer previousSourceValue = switch (source) {
            case INVENTORY -> snapshot.inventoryResource;
            case ENDER_CHEST -> snapshot.enderChestResource;
            case BACKPACK -> snapshot.backpackResource;
         };
         switch (source) {
            case INVENTORY:
               snapshot.inventoryResource = resources;
               break;
            case ENDER_CHEST:
               snapshot.enderChestResource = resources;
               break;
            case BACKPACK:
               snapshot.backpackResource = resources;
         }

         int total = 0;
         boolean hasAnySource = false;
         if (snapshot.inventoryResource != null) {
            total += snapshot.inventoryResource;
            hasAnySource = true;
         }

         if (snapshot.enderChestResource != null) {
            total += snapshot.enderChestResource;
            hasAnySource = true;
         }

         if (snapshot.backpackResource != null) {
            total += snapshot.backpackResource;
            hasAnySource = true;
         }

         Integer nextTotal = hasAnySource ? total : null;
         changed = !equals(previousSourceValue, resources)
            || !equals(snapshot.trackedResourceTotal, nextTotal)
            || shouldPersistObservedTimestamp(snapshot.resourcesSeenAtMs, nowMs)
            || shouldPersistObservedTimestamp(snapshot.lastSeenAtMs, nowMs);
         snapshot.trackedResourceTotal = nextTotal;
         snapshot.resourcesSeenAtMs = nowMs;
         snapshot.lastSeenAtMs = nowMs;
      }

      if (changed) {
         persistSnapshots();
      }
   }

   private static void updateInventoryAndContainerResource(
      String serverName, int inventoryResource, AltResourceState.ResourceSource containerSource, int containerResources, long nowMs
   ) {
      if (containerSource != null && containerSource != AltResourceState.ResourceSource.INVENTORY) {
         AltResourceState.Snapshot snapshot = snapshotFor(serverName);
         boolean changed;
         synchronized (snapshot) {
            snapshot.displayName = displayName(serverName);
            Integer previousInventory = snapshot.inventoryResource;

            Integer previousContainer = switch (containerSource) {
               case INVENTORY -> snapshot.inventoryResource;
               case ENDER_CHEST -> snapshot.enderChestResource;
               case BACKPACK -> snapshot.backpackResource;
            };
            snapshot.inventoryResource = inventoryResource;
            switch (containerSource) {
               case INVENTORY:
                  snapshot.inventoryResource = containerResources;
                  break;
               case ENDER_CHEST:
                  snapshot.enderChestResource = containerResources;
                  break;
               case BACKPACK:
                  snapshot.backpackResource = containerResources;
            }

            Integer nextTotal = computeTrackedResourceTotal(snapshot);
            changed = !equals(previousInventory, inventoryResource)
               || !equals(previousContainer, containerResources)
               || !equals(snapshot.trackedResourceTotal, nextTotal)
               || shouldPersistObservedTimestamp(snapshot.resourcesSeenAtMs, nowMs)
               || shouldPersistObservedTimestamp(snapshot.lastSeenAtMs, nowMs);
            snapshot.trackedResourceTotal = nextTotal;
            snapshot.resourcesSeenAtMs = nowMs;
            snapshot.lastSeenAtMs = nowMs;
         }

         if (changed) {
            persistSnapshots();
         }
      }
   }

   private static Integer computeTrackedResourceTotal(AltResourceState.Snapshot snapshot) {
      int total = 0;
      boolean hasAnySource = false;
      if (snapshot.inventoryResource != null) {
         total += snapshot.inventoryResource;
         hasAnySource = true;
      }

      if (snapshot.enderChestResource != null) {
         total += snapshot.enderChestResource;
         hasAnySource = true;
      }

      if (snapshot.backpackResource != null) {
         total += snapshot.backpackResource;
         hasAnySource = true;
      }

      return hasAnySource ? total : null;
   }

   private static AltResourceState.ResourceSource classifyOpenContainer(String title) {
      String folded = TextUtil.foldToLettersDigitsSpace(title);
      String compact = folded.replace(" ", "");
      if (folded.contains("ender chest") || compact.contains("enderchest")) {
         return AltResourceState.ResourceSource.ENDER_CHEST;
      } else {
         return !folded.contains("backpack") && !compact.contains("backpack") ? null : AltResourceState.ResourceSource.BACKPACK;
      }
   }

   private static AltResourceState.ResourceSource classifyCurrentOpenContainer(MinecraftClient client) {
      return client != null && client.currentScreen != null ? classifyOpenContainer(client.currentScreen.getTitle().getString()) : null;
   }

   private static boolean isTrackedResource(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         String itemId = SuiteItemIdUtil.getBestId(stack);
         String foldedName = TextUtil.foldToLettersDigitsSpace(stack.getName().getString());
         String compactName = foldedName.replace(" ", "");
         return SuiteRuntime.profile().matchesAnyTrackedResource(itemId, foldedName, compactName);
      } else {
         return false;
      }
   }

   private static String formatNullableCount(Integer value) {
      return value == null ? "?" : String.format("%,d", value);
   }

   private static String normalizeServerKey(String serverName) {
      return serverName == null ? "" : serverName.trim().toLowerCase(Locale.ROOT);
   }

   private static String displayName(String serverName) {
      return SuiteRuntime.profile().serverDisplayName(serverName);
   }

   private static Long parseLong(String raw) {
      if (raw == null) {
         return null;
      }

      try {
         return Long.parseLong(raw.replace(",", "").trim());
      } catch (NumberFormatException ignored) {
         return null;
      }
   }

   private static String firstGroup(Pattern pattern, String input) {
      if (pattern != null && input != null && !input.isBlank()) {
         Matcher matcher = pattern.matcher(input);
         return !matcher.find() ? null : matcher.group(1);
      } else {
         return null;
      }
   }

   private static Long parseScaledLong(String raw) {
      Double parsed = parseScaledNumber(raw);
      if (parsed == null) {
         return parseLong(raw);
      } else {
         return parsed < 0.0 ? 0L : Math.round(parsed);
      }
   }

   private static Double parseScaledNumber(String raw) {
      if (raw != null && !raw.isBlank()) {
         String clean = raw.replace(",", "").replace("$", "").trim().toLowerCase(Locale.ROOT);
         double multiplier = 1.0;
         if (clean.endsWith("k") || clean.endsWith("m") || clean.endsWith("b") || clean.endsWith("t")) {
            char suffix = clean.charAt(clean.length() - 1);
            clean = clean.substring(0, clean.length() - 1).trim();

            multiplier = switch (suffix) {
               case 'b' -> 1.0E9;
               case 'k' -> 1000.0;
               case 'm' -> 1000000.0;
               case 't' -> 1.0E12;
               default -> 1.0;
            };
         }

         try {
            return Double.parseDouble(clean) * multiplier;
         } catch (NumberFormatException ignored) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static String normalizeForResourceParser(String raw) {
      return TextUtil.normalizeSmallCaps(TextUtil.stripSectionSignsOnly(raw));
   }

   private static boolean equals(Object a, Object b) {
      return Objects.equals(a, b);
   }

   private static boolean shouldPersistObservedTimestamp(long previousSeenAtMs, long nowMs) {
      return previousSeenAtMs <= 0L || nowMs - previousSeenAtMs >= 60000L;
   }

   private static void persistSnapshots() {
      SuiteConfig.INSTANCE.AltResourcesConfig.snapshots = encodePersistentSnapshots();
      SuiteConfig.INSTANCE.markDirty();
      ConfigIO.saveIfDirty();
   }

   private static String encodeSnapshot(AltResourceState.Snapshot snapshot) {
      return "v2|"
         + escapePart(snapshot.serverKey)
         + "|"
         + escapePart(snapshot.displayName)
         + "|"
         + escapePart(snapshot.balanceRaw)
         + "|"
         + (snapshot.balanceValue == null ? "" : snapshot.balanceValue)
         + "|"
         + snapshot.balanceSeenAtMs
         + "|"
         + (snapshot.claimBlocks == null ? "" : snapshot.claimBlocks)
         + "|"
         + snapshot.claimBlocksSeenAtMs
         + "|"
         + (snapshot.trackedResourceTotal == null ? "" : snapshot.trackedResourceTotal)
         + "|"
         + snapshot.resourcesSeenAtMs
         + "|"
         + snapshot.lastSeenAtMs
         + "|"
         + (snapshot.inventoryResource == null ? "" : snapshot.inventoryResource)
         + "|"
         + (snapshot.enderChestResource == null ? "" : snapshot.enderChestResource)
         + "|"
         + (snapshot.backpackResource == null ? "" : snapshot.backpackResource);
   }

   private static AltResourceState.Snapshot decodeSnapshot(String encoded) {
      if (encoded != null && !encoded.isBlank()) {
         List<String> parts = splitEscaped(encoded);
         if (parts.size() < 11) {
            return null;
         }

         boolean v1 = "v1".equals(parts.get(0));
         boolean v2 = "v2".equals(parts.get(0));
         if (!v1 && !v2) {
            return null;
         }

         String serverKey = normalizeServerKey(unescapePart(parts.get(1)));
         if (serverKey.isBlank()) {
            return null;
         }

         AltResourceState.Snapshot snapshot = new AltResourceState.Snapshot(serverKey, unescapePart(parts.get(2)));
         snapshot.balanceRaw = emptyToNull(unescapePart(parts.get(3)));
         snapshot.balanceValue = parseDoubleOrNull(parts.get(4));
         snapshot.balanceSeenAtMs = parseLongOrDefault(parts.get(5), 0L);
         snapshot.claimBlocks = parseLongOrNull(parts.get(6));
         snapshot.claimBlocksSeenAtMs = parseLongOrDefault(parts.get(7), 0L);
         snapshot.trackedResourceTotal = parseIntOrNull(parts.get(8));
         snapshot.resourcesSeenAtMs = parseLongOrDefault(parts.get(9), 0L);
         snapshot.lastSeenAtMs = parseLongOrDefault(
            parts.get(10), Math.max(snapshot.balanceSeenAtMs, Math.max(snapshot.claimBlocksSeenAtMs, snapshot.resourcesSeenAtMs))
         );
         if (v2 && parts.size() >= 14) {
            snapshot.inventoryResource = parseIntOrNull(parts.get(11));
            snapshot.enderChestResource = parseIntOrNull(parts.get(12));
            snapshot.backpackResource = parseIntOrNull(parts.get(13));
         } else {
            snapshot.inventoryResource = snapshot.trackedResourceTotal;
         }

         if (snapshot.displayName == null || snapshot.displayName.isBlank()) {
            snapshot.displayName = displayName(serverKey);
         }

         return snapshot;
      } else {
         return null;
      }
   }

   private static String escapePart(String value) {
      return value == null ? "" : value.replace("\\", "\\\\").replace("|", "\\|");
   }

   private static String unescapePart(String value) {
      if (value != null && !value.isEmpty()) {
         StringBuilder out = new StringBuilder(value.length());
         boolean escaping = false;

         for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaping) {
               out.append(c);
               escaping = false;
            } else if (c == '\\') {
               escaping = true;
            } else {
               out.append(c);
            }
         }

         if (escaping) {
            out.append('\\');
         }

         return out.toString();
      } else {
         return "";
      }
   }

   private static List<String> splitEscaped(String encoded) {
      ArrayList<String> out = new ArrayList<>();
      StringBuilder part = new StringBuilder();
      boolean escaping = false;

      for (int i = 0; i < encoded.length(); i++) {
         char c = encoded.charAt(i);
         if (escaping) {
            part.append('\\').append(c);
            escaping = false;
         } else if (c == '\\') {
            escaping = true;
         } else if (c == '|') {
            out.add(part.toString());
            part.setLength(0);
         } else {
            part.append(c);
         }
      }

      if (escaping) {
         part.append('\\');
      }

      out.add(part.toString());
      return out;
   }

   private static String emptyToNull(String value) {
      return value != null && !value.isEmpty() ? value : null;
   }

   private static Double parseDoubleOrNull(String raw) {
      if (raw != null && !raw.isBlank()) {
         try {
            return Double.parseDouble(raw);
         } catch (NumberFormatException ignored) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static Long parseLongOrNull(String raw) {
      if (raw != null && !raw.isBlank()) {
         try {
            return Long.parseLong(raw);
         } catch (NumberFormatException ignored) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static long parseLongOrDefault(String raw, long fallback) {
      Long parsed = parseLongOrNull(raw);
      return parsed == null ? fallback : parsed;
   }

   private static Integer parseIntOrNull(String raw) {
      if (raw != null && !raw.isBlank()) {
         try {
            return Integer.parseInt(raw);
         } catch (NumberFormatException ignored) {
            return null;
         }
      } else {
         return null;
      }
   }

   private enum ResourceSource {
      INVENTORY,
      ENDER_CHEST,
      BACKPACK;
   }

   public record ScoreboardParse(String balanceRaw, Double balanceValue, Long claimBlocks) {
      public boolean hasAny() {
         return this.balanceRaw != null || this.balanceValue != null || this.claimBlocks != null;
      }
   }

   public static final class Snapshot {
      private final String serverKey;
      private String displayName;
      private String balanceRaw;
      private Double balanceValue;
      private long balanceSeenAtMs;
      private Long claimBlocks;
      private long claimBlocksSeenAtMs;
      private Integer trackedResourceTotal;
      private Integer inventoryResource;
      private Integer enderChestResource;
      private Integer backpackResource;
      private long resourcesSeenAtMs;
      private long lastSeenAtMs;

      private Snapshot(String serverKey, String displayName) {
         this.serverKey = serverKey;
         this.displayName = displayName;
      }

      public String serverKey() {
         return this.serverKey;
      }

      public String displayName() {
         return this.displayName;
      }

      public String balanceRaw() {
         return this.balanceRaw;
      }

      public Double balanceValue() {
         return this.balanceValue;
      }

      public Long claimBlocks() {
         return this.claimBlocks;
      }

      public Integer trackedResourceTotal() {
         return this.trackedResourceTotal;
      }

      public Integer inventoryResource() {
         return this.inventoryResource;
      }

      public Integer enderChestResource() {
         return this.enderChestResource;
      }

      public Integer backpackResource() {
         return this.backpackResource;
      }

      public long lastSeenAtMs() {
         return this.lastSeenAtMs;
      }

      public long balanceSeenAtMs() {
         return this.balanceSeenAtMs;
      }

      public long claimBlocksSeenAtMs() {
         return this.claimBlocksSeenAtMs;
      }

      public long resourcesSeenAtMs() {
         return this.resourcesSeenAtMs;
      }
   }
}
