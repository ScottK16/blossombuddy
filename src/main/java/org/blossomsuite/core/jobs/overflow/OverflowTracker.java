package org.blossomsuite.core.jobs.overflow;

import org.blossomsuite.core.jobs.JobXpTracker;

import org.blossomsuite.core.config.FeatureConfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.blossomsuite.core.SuiteFeature;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.config.JobsConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.state.SuiteScheduler;
import org.blossomsuite.core.util.SuiteLog;
import org.blossomsuite.core.util.WorldGate;

/**
 * Tracks Jobs Reborn XP earned after a job's max level ("overflow XP") by reading the Jobs boss bar,
 * and optionally rewrites that boss bar to show the overflow.
 *
 * <p>Ported from Jobs Overflow XP by Mills (MIT, https://github.com/Yullyz/jobs-overflow) and folded into
 * BlossomBuddy's realm detection, config and options screen. Totals are kept per realm and per job.
 */
public final class OverflowTracker {
   public static final OverflowTracker INSTANCE = new OverflowTracker();
   public static final String UNKNOWN_REALM = "unknown";

   // Default Jobs Reborn boss bar: "Lvl 200 Miner: 5000/5000 xp (+125) $100"
   private static final Pattern JOBS_BAR = Pattern.compile(
      "^Lvl\\s+(\\d+)\\s+(.+?):\\s*([0-9.,]+)\\s*/\\s*([0-9.,]+)\\s*xp(?:\\s*\\(([+-]?[0-9.,]+)\\))?.*$", Pattern.CASE_INSENSITIVE
   );
   private static final long STALE_GAIN_MS = 5000L;
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Map<String, int[]> JOB_GRADIENTS = new HashMap<>();

   static {
      JOB_GRADIENTS.put("farmer", new int[]{0x31C300, 0x88FF6A});
      putJobColor("digger", 0x8B4513);
      putJobColor("miner", 0xD3D3D3);
      putJobColor("florist", 0xFF69B4);
      putJobColor("fisher", 0x3B82F6);
      putJobColor("hunter", 0xE53935);
      putJobColor("rancher", 0xFF8C00);
      putJobColor("smither", 0x808080);
      putJobColor("woodcutter", 0x800000);
   }

   /** realm key -> job key -> overflow XP. Guarded by {@code this}. */
   private final Map<String, Map<String, Double>> overflow = new LinkedHashMap<>();
   private final Map<String, Double> lastGain = new HashMap<>();
   private final Map<String, Long> lastGainAtMs = new HashMap<>();
   private final Map<String, Integer> lastVirtualLevel = new HashMap<>();
   /** Live Jobs boss bars by boss bar UUID. */
   private final Map<UUID, ParsedJob> bars = new ConcurrentHashMap<>();
   private volatile boolean dirty = false;
   /** Whether the one-time import from the standalone Jobs Overflow XP mod's save file has already happened. */
   private volatile boolean legacyImported = false;
   private Path file;

   OverflowTracker() {
   }

   public record ParsedJob(String job, int level, double xp, double maxXp, double gain) {
      boolean maxed(int maxLevel) {
         return this.level >= maxLevel && this.maxXp > 0.0 && this.xp >= this.maxXp - 1.0E-4;
      }
   }

   public record VirtualLevel(int level, double xpInto, double xpForLevel) {
   }

   /** What the boss bar renderer needs to draw a cosmetic "levels" bar. */
   public record LevelsBar(int baseColor, int lightColor, float progress) {
   }

   // ------------------------------------------------------------------ lifecycle

   public void init() {
      Path dir = FabricLoader.getInstance().getConfigDir().resolve(SuiteRuntime.profile().modId());
      this.file = dir.resolve("jobs-overflow.json");
      this.load(dir);
      SuiteScheduler.IO.scheduleWithFixedDelay(this::flushIfDirty, 15L, 15L, TimeUnit.SECONDS);
      ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> this.clearLiveState());
      ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
         this.clearLiveState();
         this.flushIfDirty();
      });
      Runtime.getRuntime().addShutdownHook(new Thread(this::flushIfDirty, "BlossomBuddy-OverflowFlush"));
   }

   private void clearLiveState() {
      this.bars.clear();
      synchronized (this) {
         this.lastGain.clear();
         this.lastGainAtMs.clear();
      }
   }

   private static JobsConfig cfg() {
      return SuiteConfig.INSTANCE.JobsConfig;
   }

   private static boolean active() {
      JobsConfig c = cfg();
      return c.capture && c.overflowEnabled && SuiteRuntime.profile().isEnabled(SuiteFeature.JOBS) && SuiteConfig.INSTANCE.isEnabledForCurrentWorld();
   }

   private static boolean xpActive() {
      return FeatureConfig.INSTANCE.xp.show
         && cfg().capture
         && SuiteRuntime.profile().isEnabled(SuiteFeature.JOBS)
         && SuiteConfig.INSTANCE.isEnabledForCurrentWorld();
   }

   private static String currentRealm() {
      String realm = WorldGate.Server;
      return realm == null || realm.isBlank() ? UNKNOWN_REALM : realm;
   }

   // ------------------------------------------------------------------ boss bar feed

   /** Called for every boss bar add / rename packet. */
   public void onBarName(UUID uuid, Text name) {
      if (uuid == null || name == null) {
         return;
      }

      boolean overflowOn = active();
      boolean xpOn = xpActive();
      if (!overflowOn && !xpOn) {
         return;
      }

      ParsedJob job = parse(name.getString());
      if (job == null) {
         this.bars.remove(uuid);
         return;
      }

      if (xpOn) {
         JobXpTracker.INSTANCE.record(job.job(), job.level(), job.xp(), job.maxXp(), job.gain(), System.currentTimeMillis());
      }

      if (!overflowOn) {
         return;
      }

      this.bars.put(uuid, job);
      int maxLevel = cfg().overflowMaxLevel;
      if (job.gain() <= 0.0 || !job.maxed(maxLevel)) {
         return;
      }

      String jobKey = normalise(job.job());
      String realm = currentRealm();
      Accrual accrual = this.accrue(realm, jobKey, job.gain(), System.currentTimeMillis());
      if (accrual != null) {
         this.checkLevelUp(realm + "|" + jobKey, job, accrual.total());
      }
   }

   /** New overflow XP from one boss bar update, and the resulting total for that job. */
   public record Accrual(double delta, double total) {
   }

   /**
    * Jobs Reborn shows a running "(+N)" for the current burst of XP, so only the increase since the last
    * update is new. A burst that goes quiet for a few seconds, or a total that drops, starts over.
    *
    * @return the accrual, or null if this update added nothing
    */
   synchronized Accrual accrue(String realm, String jobKey, double shownGain, long nowMs) {
      String trackKey = realm + "|" + jobKey;
      double previous = this.lastGain.getOrDefault(trackKey, 0.0);
      long lastAt = this.lastGainAtMs.getOrDefault(trackKey, Long.MIN_VALUE / 2);
      if (nowMs - lastAt > STALE_GAIN_MS) {
         previous = 0.0;
      }

      double delta = shownGain >= previous ? shownGain - previous : shownGain;
      this.lastGain.put(trackKey, shownGain);
      this.lastGainAtMs.put(trackKey, nowMs);
      if (delta <= 0.0) {
         return null;
      }

      double total = this.overflow.computeIfAbsent(realm, r -> new LinkedHashMap<>()).merge(jobKey, delta, Double::sum);
      this.dirty = true;
      return new Accrual(delta, total);
   }

   public void onBarRemoved(UUID uuid) {
      if (uuid != null) {
         this.bars.remove(uuid);
      }
   }

   // ------------------------------------------------------------------ display

   /** Replacement boss bar title, or null to leave the vanilla one. Called from BossBar#getName. */
   public Text overrideName(UUID uuid) {
      if (uuid == null || !active()) {
         return null;
      }

      OverflowDisplayMode mode = cfg().overflowDisplay;
      if (mode == OverflowDisplayMode.OFF) {
         return null;
      }

      ParsedJob job = this.bars.get(uuid);
      if (job == null || !job.maxed(cfg().overflowMaxLevel)) {
         return null;
      }

      double total = this.overflowFor(job.job());
      if (mode == OverflowDisplayMode.XP) {
         return Text.literal("Lvl " + job.level() + " " + job.job() + " (Overflow: " + formatNumber(total) + " XP)");
      }

      VirtualLevel v = computeVirtualLevel(job.maxXp(), total, cfg().overflowMaxLevel);
      return Text.literal("Lvl " + v.level() + " " + job.job() + " " + formatNumber(v.xpInto()) + "/" + formatNumber(v.xpForLevel()) + " xp");
   }

   /** Data for the cosmetic levels bar, or null when the vanilla bar should draw. */
   public LevelsBar levelsBarFor(UUID uuid) {
      if (uuid == null || !active() || cfg().overflowDisplay != OverflowDisplayMode.LEVELS) {
         return null;
      }

      ParsedJob job = this.bars.get(uuid);
      if (job == null || !job.maxed(cfg().overflowMaxLevel)) {
         return null;
      }

      VirtualLevel v = computeVirtualLevel(job.maxXp(), this.overflowFor(job.job()), cfg().overflowMaxLevel);
      float progress = v.xpForLevel() <= 0.0 ? 0.0F : (float)(v.xpInto() / v.xpForLevel());
      int[] g = gradientFor(job.job());
      return new LevelsBar(g[0], g[1], Math.max(0.0F, Math.min(1.0F, progress)));
   }

   private void checkLevelUp(String trackKey, ParsedJob job, double newTotal) {
      int maxLevel = cfg().overflowMaxLevel;
      int level = computeVirtualLevel(job.maxXp(), newTotal, maxLevel).level();
      int previous;
      synchronized (this) {
         previous = this.lastVirtualLevel.getOrDefault(trackKey, maxLevel + 1);
         this.lastVirtualLevel.put(trackKey, level);
      }

      if (level > previous && cfg().overflowLevelUpMessage) {
         MinecraftClient client = MinecraftClient.getInstance();
         client.execute(
            () -> ChatOutput.info(Text.literal("Congrats, you have reached level " + level + " in " + job.job() + "!").formatted(Formatting.AQUA))
         );
      }
   }

   // ------------------------------------------------------------------ data access (commands / UI)

   public synchronized double overflowFor(String job) {
      Map<String, Double> byJob = this.overflow.get(currentRealm());
      return byJob == null ? 0.0 : byJob.getOrDefault(normalise(job), 0.0);
   }

   public synchronized Map<String, Double> snapshotCurrentRealm() {
      Map<String, Double> byJob = this.overflow.get(currentRealm());
      return byJob == null ? Map.of() : new LinkedHashMap<>(byJob);
   }

   public synchronized Map<String, Map<String, Double>> snapshotAll() {
      Map<String, Map<String, Double>> copy = new LinkedHashMap<>();
      this.overflow.forEach((realm, jobs) -> copy.put(realm, new LinkedHashMap<>(jobs)));
      return copy;
   }

   public String realmLabel() {
      return currentRealm();
   }

   public synchronized void resetCurrentRealm() {
      this.overflow.remove(currentRealm());
      this.lastVirtualLevel.clear();
      this.dirty = true;
   }

   public synchronized void resetAll() {
      this.overflow.clear();
      this.lastVirtualLevel.clear();
      this.dirty = true;
   }

   public synchronized void resetJob(String job) {
      Map<String, Double> byJob = this.overflow.get(currentRealm());
      if (byJob != null) {
         byJob.remove(normalise(job));
      }

      this.lastVirtualLevel.clear();
      this.dirty = true;
   }

   // ------------------------------------------------------------------ parsing / maths

   public static ParsedJob parse(String rawTitle) {
      if (rawTitle == null) {
         return null;
      }

      Matcher m = JOBS_BAR.matcher(rawTitle.replace(' ', ' ').trim());
      if (!m.matches()) {
         return null;
      }

      try {
         int level = Integer.parseInt(m.group(1));
         double xp = parseNumber(m.group(3));
         double maxXp = parseNumber(m.group(4));
         double gain = m.group(5) == null ? 0.0 : parseNumber(m.group(5));
         return new ParsedJob(m.group(2).trim(), level, xp, maxXp, gain);
      } catch (NumberFormatException e) {
         return null;
      }
   }

   private static double parseNumber(String value) {
      return Double.parseDouble(value.replace(",", ""));
   }

   private static double xpRequiredForLevel(int level) {
      return 2.0 * level * level + 50.0 * level;
   }

   /**
    * Cosmetic levels past the max level. The server's own curve is scaled so that the XP shown for the
    * max level on the boss bar matches, and the same curve then continues upward.
    */
   public static VirtualLevel computeVirtualLevel(double maxXpAtMaxLevel, double overflowXp, int maxLevel) {
      double base = xpRequiredForLevel(maxLevel);
      double scale = maxXpAtMaxLevel > 0.0 && base > 0.0 ? maxXpAtMaxLevel / base : 1.0;
      int level = maxLevel + 1;
      double remaining = overflowXp;
      double threshold = scale * xpRequiredForLevel(level);
      while (remaining >= threshold && level < Integer.MAX_VALUE - 1) {
         remaining -= threshold;
         level++;
         threshold = scale * xpRequiredForLevel(level);
      }

      return new VirtualLevel(level, remaining, threshold);
   }

   public static String normalise(String job) {
      return job == null ? "" : job.trim().toLowerCase(Locale.ROOT);
   }

   public static String formatNumber(double value) {
      if (Math.abs(value - Math.rint(value)) < 1.0E-6) {
         return String.format(Locale.ROOT, "%,d", (long)Math.rint(value));
      }

      return String.format(Locale.ROOT, "%,.2f", value);
   }

   private static void putJobColor(String job, int base) {
      JOB_GRADIENTS.put(job, new int[]{base, lighten(base, 0.5)});
   }

   private static int lighten(int color, double amount) {
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      r = (int)(r + (255 - r) * amount);
      g = (int)(g + (255 - g) * amount);
      b = (int)(b + (255 - b) * amount);
      return r << 16 | g << 8 | b;
   }

   private static int[] gradientFor(String job) {
      int[] g = JOB_GRADIENTS.get(normalise(job));
      return g != null ? g : new int[]{0xFFAA00, lighten(0xFFAA00, 0.5)};
   }

   // ------------------------------------------------------------------ persistence

   private void load(Path dir) {
      try {
         Files.createDirectories(dir);
         if (Files.exists(this.file)) {
            this.readFile(this.file, false);
         }

         // Bring over data from the standalone Jobs Overflow XP mod if it was installed - once ever, tracked by
         // legacyImported (persisted below), not just "the new file doesn't exist yet". A player who already had a
         // little bit of overflow tracked in BlossomBuddy before this existed still gets their real history back;
         // the legacy numbers win on anything both files have, since they are the real totals.
         if (!this.legacyImported) {
            Path legacy = FabricLoader.getInstance().getConfigDir().resolve("jobs-overflow.json");
            if (Files.exists(legacy)) {
               this.readFile(legacy, true);
               SuiteLog.logger().info("[overflow] imported existing data from {}", legacy);
            }

            this.legacyImported = true;
            this.dirty = true;
            this.flushIfDirty();
         }
      } catch (Exception e) {
         SuiteLog.logger().warn("[overflow] could not load overflow data: {}", e.toString());
      }
   }

   private synchronized void readFile(Path path, boolean legacy) throws IOException {
      String text = Files.readString(path);
      if (legacy) {
         // the standalone mod's file is one encrypted blob, not plain JSON; fall back to the raw text in case a
         // particular file ever turns out not to be encrypted after all, rather than refusing to import it
         try {
            text = LegacyOverflowCrypto.decrypt(text.trim());
         } catch (Exception e) {
            SuiteLog.logger().debug("[overflow] legacy file did not decrypt, trying it as plain JSON: {}", e.toString());
         }
      }

      try (Reader reader = new StringReader(text)) {
         JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
         if (root.has("overflow") && root.get("overflow").isJsonObject()) {
            JsonObject realms = root.getAsJsonObject("overflow");
            for (String realm : realms.keySet()) {
               this.overflow.put(realm, readJobs(realms.getAsJsonObject(realm)));
            }
         } else if (root.has("jobs") && root.get("jobs").isJsonObject()) {
            this.overflow.put(UNKNOWN_REALM, readJobs(root.getAsJsonObject("jobs")));
         }

         if (!legacy && root.has("legacyImported")) {
            this.legacyImported = root.get("legacyImported").getAsBoolean();
         }

         if (legacy && root.has("displayMode")) {
            try {
               cfg().overflowDisplay = OverflowDisplayMode.valueOf(root.get("displayMode").getAsString().toUpperCase(Locale.ROOT));
               SuiteConfig.INSTANCE.markDirty();
            } catch (IllegalArgumentException ignored) {
            }
         }
      }
   }

   private static Map<String, Double> readJobs(JsonObject jobs) {
      Map<String, Double> map = new LinkedHashMap<>();
      for (Map.Entry<String, JsonElement> e : jobs.entrySet()) {
         try {
            map.put(normalise(e.getKey()), e.getValue().getAsDouble());
         } catch (RuntimeException ignored) {
         }
      }

      return map;
   }

   public void flushIfDirty() {
      if (!this.dirty || this.file == null) {
         return;
      }

      JsonObject root = new JsonObject();
      root.addProperty("version", 1);
      JsonObject realms = new JsonObject();
      synchronized (this) {
         this.dirty = false;
         this.overflow.forEach((realm, jobs) -> {
            JsonObject o = new JsonObject();
            jobs.forEach(o::addProperty);
            realms.add(realm, o);
         });
      }

      root.add("overflow", realms);
      root.addProperty("legacyImported", this.legacyImported);
      try {
         Files.createDirectories(this.file.getParent());
         Path tmp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
         try (Writer w = Files.newBufferedWriter(tmp)) {
            GSON.toJson(root, w);
         }

         Files.move(tmp, this.file, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
         this.dirty = true;
         SuiteLog.logger().warn("[overflow] could not save overflow data: {}", e.toString());
      }
   }
}
