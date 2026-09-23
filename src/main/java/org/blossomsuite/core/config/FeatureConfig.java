package org.blossomsuite.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.state.SuiteScheduler;
import org.blossomsuite.core.util.SuiteLog;

/**
 * Settings for the features added in BlossomBuddy (scoreboard, hands, extra hotbar, XP tracker, secondary chat,
 * per-item cooldown toggles). Kept in its own {@code features.json} so it stays independent of the original
 * settings file. Every field has a default, so missing keys in an older file just fall back to it.
 */
public final class FeatureConfig {
   public static FeatureConfig INSTANCE = new FeatureConfig();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static volatile boolean dirty = false;
   private static Path file;

   public Scoreboard scoreboard = new Scoreboard();
   public Hands hands = new Hands();
   public Hotbar hotbar = new Hotbar();
   public Xp xp = new Xp();
   public SlotLocks slotLocks = new SlotLocks();
   public Relay relay = new Relay();
   public XChat xchat = new XChat();
   public Stats stats = new Stats();
   public Presence presence = new Presence();
   public Emotes emotes = new Emotes();
   public Chat chat = new Chat();
   public MapArt mapart = new MapArt();
   /** Cooldown rules the player has switched off, by item id (or item name when a rule has no id). */
   public List<String> disabledCooldownItems = new ArrayList<>();

   /** On-screen HUDs for whatever map art design {@code /buddy mapart} last opened - both off until turned on. */
   public static final class MapArt {
      public boolean showPreview = false;
      public boolean showList = false;
      public Panel previewPanel = new Panel();
      public Panel listPanel = new Panel();
   }

   /** Where a HUD panel sits: normalised 0..1 across the free screen area, or negative for "use the default spot". */
   public static final class Panel {
      public float x = -1.0F;
      public float y = -1.0F;
      public float scale = 1.0F;
      public float opacity = 0.33F;
   }

   public static final class Scoreboard {
      public boolean hidden = false;
      public boolean showNumbers = true;
      public boolean textShadow = false;
      public boolean background = true;
      /** Background opacity 0..1, or negative to use the game's own text-background setting. */
      public float backgroundOpacity = -1.0F;
      public boolean border = false;
      public boolean rounded = false;
      public Panel panel = new Panel();

      /** True once anything differs from how the game draws the scoreboard. Until then we leave it alone. */
      public boolean isCustomized() {
         return this.hidden
            || !this.showNumbers
            || this.textShadow
            || !this.background
            || this.backgroundOpacity >= 0.0F
            || this.border
            || this.rounded
            || this.panel.scale != 1.0F
            || this.panel.x >= 0.0F
            || this.panel.y >= 0.0F;
      }
   }

   public static final class Hands {
      public boolean smallHands = false;
      public float smallHandsScale = 0.6F;
      public boolean freezeSwing = false;
      public boolean freezeEquip = false;
   }

   public static final class Hotbar {
      public boolean show = false;
      /** How many extra rows sit above the hotbar: 1 = double hotbar, 2 = triple. */
      public int extraRows = 1;
      public Panel panel = new Panel();
   }

   /** Vote-party sharing between realms: automatic, anonymous, and switchable. Off until the player opts in. */
   public static final class Relay {
      /** Relay address. Empty means "use the address built into the mod". */
      public String url = "";
      public boolean share = false;
      /** No longer used to gate the notice (it now repeats each login while sharing is off); kept so old configs still parse. */
      public boolean noticeShown = false;
   }

   /**
    * The BlossomBuddy relay every install talks to unless the player sets their own address. Kept apart from the
    * profile's backend URL on purpose: that one switches on the original mod's remote-config check, which stays off.
    */
   public static final String BUILT_IN_RELAY_URL = "https://relay.blossombuddy.site";

   /** The address to use: the player's own if they set one, otherwise the one built into the mod. */
   public static String effectiveRelayUrl(String configured, String builtIn) {
      String c = configured == null ? "" : configured.trim();
      return c.isEmpty() ? (builtIn == null ? "" : builtIn.trim()) : c;
   }

   public static String effectiveRelayUrl() {
      return effectiveRelayUrl(INSTANCE.relay.url, BUILT_IN_RELAY_URL);
   }

   /**
    * Usage counts for the developer. Counting (a random id and the mod version, nothing about the player) is on by
    * default and can be switched off; sharing the Minecraft name is a separate choice and off until the player opts in.
    */
   public static final class Stats {
      public boolean enabled = true;
      public boolean shareName = false;
      /** 32 random hex characters made on first use; empty until then and again after counting is switched off. */
      public String installId = "";
      /** The one-time "we count usage, here is how to turn it off" chat notice has been shown. */
      public boolean noticeShown = false;
      public long lastPingMs = 0L;
      /** The name the relay currently holds for this install (empty when none). */
      public String namedAs = "";
   }

   /**
    * The player list (who else is using the mod, on which realm). On by default, after a one-time chat notice, and the player can
    * switch it off in /buddy who; appearing is also what lets them see it.
    */
   public static final class Presence {
      public boolean enabled = true;
      /** The one-time "you are in the player list, here is how to leave" chat notice has been shown. */
      public boolean noticeShown = false;
      /** A small symbol after the name of players in the tab list who use the mod (only while the list is on). */
      public boolean tabSymbol = true;
   }

   /** Emotes (dances and waves) that other BlossomBuddy players on the same realm can see. */
   public static final class Emotes {
      public boolean enabled = true;
      /** Play other players' emotes on their characters. */
      public boolean showOthers = true;
      /** Tell the relay about your own emotes so others can see them. */
      public boolean share = true;
   }

   /** Cross-realm chat. Off until the player turns it on. */
   public static final class XChat {
      public boolean enabled = false;
      /** Names (any case) or UUIDs whose messages are hidden. */
      public List<String> muted = new ArrayList<>();
   }

   public static final class SlotLocks {
      /** The Q / Ctrl+Q drop key does nothing on a locked slot. */
      public boolean blockDrop = true;
      /** Clicks, shift-clicks and number-key swaps can't move an item out of a locked slot. */
      public boolean blockMoves = true;
   }

   public static final class Xp {
      public boolean show = true;
      public int maxRows = 4;
      public boolean showRates = true;
      public boolean showEta = true;
      public Panel panel = new Panel();
   }

   public static final class Filter {
      public String name = "Filter";
      /** Words separated by '|' (any of them), or a regular expression when {@link #regex} is on. */
      public String pattern = "";
      public boolean regex = false;
      public boolean enabled = true;
      public boolean hideFromMain = false;
      /** Not matched against chat text: lines are put here by the mod itself (cross-realm chat). */
      public boolean external = false;

      public Filter() {
      }

      public Filter(String name, String pattern) {
         this.name = name;
         this.pattern = pattern;
      }
   }

   /** An extra secondary-chat window that shows one filter only (for example Marry chat, or Staff chat). */
   public static final class ChatWindow {
      public boolean enabled = false;
      /** The name of the filter this window shows. */
      public String filter = "";
      public int lines = 6;
      /** While this window is on, lines of its filter are left out of the main window's "All" view. */
      public boolean exclusive = true;
      public Panel panel = new Panel();

      public ChatWindow() {
      }

      public ChatWindow(String filter) {
         this.filter = filter;
      }
   }

   public static final class Chat {
      public boolean show = false;
      public int lines = 8;
      /** Index into {@link #filters} of the filter shown in the panel, or -1 for all. */
      public int selected = -1;
      public List<Filter> filters = defaultFilters();
      public Panel panel = new Panel();
      /** The most extra windows there can be. */
      public static final int MAX_WINDOWS = 4;
      /** The filters the four extra windows start out pointing at (all of them off until the player turns them on). */
      private static final String[] WINDOW_PRESETS = {"Marry", "Staff", "Teleports", "Messages"};
      public List<ChatWindow> windows = defaultWindows();
      /** The Staff filter has been offered once, so a player who deletes it doesn't get it back. */
      public boolean staffFilterAdded = false;
      /**
       * BlossomCraft staff chat looks like "[Staff] Name > message", sometimes with a time such as "[22:59:03]" in front from another
       * mod. Only a line that STARTS with the [Staff] tag counts, so a player typing "[Staff]" in the middle of public chat can't
       * fake one. The filter is off by default; a Staff window catches these lines even so.
       */
      public static final String STAFF_PATTERN = "(?i)^\\s*(\\[\\d{1,2}:\\d{2}(:\\d{2})?\\]\\s*)?\\[staff\\]";
      /** What the first build of the Staff filter shipped (a guess, before the real format was known). */
      public static final String OLD_STAFF_PATTERN = "staff chat|staffchat|[staff]|(staff)|staff >";

      /**
       * Public chat on BlossomCraft looks like "Rank : Name > message". These two filters look for words, so they must not fire on
       * a player merely talking about teleports or marriage: a line in that format is never matched.
       */
      private static final String NOT_PLAYER_CHAT = "(?i)^(?!.*\\s:\\s\\S+\\s>\\s).*";
      public static final String TELEPORTS_PATTERN = NOT_PLAYER_CHAT + "(?:teleport|\\btpa|tpdeny|tp request|request will timeout)";
      public static final String MARRY_PATTERN = NOT_PLAYER_CHAT + "(?:all partners|private marry chat|marry|married|marriage|propose|divorce)";
      /** The versions before public chat was excluded (plain word lists). */
      public static final String OLD_TELEPORTS_PATTERN_2 = "teleport|tpa|tpdeny|tp request|request will timeout";
      public static final String OLD_MARRY_PATTERN_2 = "all partners|private marry chat|marry|married|marriage|propose|divorce";
      /** BlossomCraft private messages start with an upper-case "MESSAGE" label, e.g. "MESSAGE ME > ~Marv > test". */
      public static final String MESSAGES_PATTERN = "(?-i)\\bMESSAGE\\s+\\S|(?i:whispers to you)|(?i:-> me)";
      /** What earlier builds shipped. A filter still holding exactly one of these was never edited, so it is safe to upgrade. */
      public static final String OLD_TELEPORTS_PATTERN = "teleport|tpa|tp request|tpaccept|tpahere";
      public static final String OLD_MARRY_PATTERN = "marry|married|marriage|propose|divorce";
      public static final String OLD_MESSAGES_PATTERN = "whispers|-> me|[me ->|from ";
      public static final String OLD_MESSAGES_PATTERN_2 = "whispers to you|whispers:|-> me|[me ->";

      private static List<Filter> defaultFilters() {
         List<Filter> list = new ArrayList<>();
         Filter teleports = new Filter("Teleports", TELEPORTS_PATTERN);
         teleports.regex = true;
         teleports.hideFromMain = true;
         list.add(teleports);
         Filter marry = new Filter("Marry", MARRY_PATTERN);
         marry.regex = true;
         marry.hideFromMain = true;
         list.add(marry);
         Filter messages = new Filter("Messages", MESSAGES_PATTERN);
         messages.regex = true;
         messages.hideFromMain = true;
         list.add(messages);
         Filter staff = new Filter("Staff", STAFF_PATTERN);
         staff.regex = true;
         staff.enabled = false;
         staff.hideFromMain = true;
         list.add(staff);
         Filter realms = new Filter("Realms", "");
         realms.external = true;
         list.add(realms);
         return list;
      }

      private static List<ChatWindow> defaultWindows() {
         List<ChatWindow> list = new ArrayList<>();
         for (int i = 0; i < MAX_WINDOWS; i++) {
            list.add(new ChatWindow(WINDOW_PRESETS[i]));
         }

         return list;
      }

      /**
       * Makes sure there are exactly {@link #MAX_WINDOWS} usable windows in a saved list.
       *
       * @return true if anything had to be fixed
       */
      public static boolean normalizeWindows(List<ChatWindow> list) {
         boolean changed = false;
         for (int i = 0; i < MAX_WINDOWS; i++) {
            if (list.size() <= i) {
               list.add(new ChatWindow(WINDOW_PRESETS[i]));
               changed = true;
            } else if (list.get(i) == null) {
               list.set(i, new ChatWindow(WINDOW_PRESETS[i]));
               changed = true;
            }

            ChatWindow w = list.get(i);
            if (w.panel == null) {
               w.panel = new Panel();
               changed = true;
            }

            if (w.filter == null) {
               w.filter = "";
               changed = true;
            }

            int lines = Math.max(2, Math.min(20, w.lines));
            if (lines != w.lines) {
               w.lines = lines;
               changed = true;
            }
         }

         while (list.size() > MAX_WINDOWS) {
            list.remove(list.size() - 1);
            changed = true;
         }

         return changed;
      }

      /**
       * Adds the (disabled) Staff filter to a saved list that doesn't have one yet.
       *
       * @return true if it had to be added
       */
      public static boolean ensureStaffFilter(List<Filter> filters) {
         for (Filter f : filters) {
            if (f != null && "staff".equalsIgnoreCase(f.name)) {
               return false;
            }
         }

         Filter staff = new Filter("Staff", STAFF_PATTERN);
         staff.regex = true;
         staff.enabled = false;
         staff.hideFromMain = true;
         int realms = filters.size();
         for (int i = 0; i < filters.size(); i++) {
            if (filters.get(i) != null && filters.get(i).external) {
               realms = i; // before the Realms tab, which stays last
               break;
            }
         }

         filters.add(realms, staff);
         return true;
      }

      /**
       * Makes sure the cross-realm chat tab exists in a saved list.
       *
       * @return true if it had to be added
       */
      public static boolean ensureRealmsFilter(List<Filter> filters) {
         for (Filter f : filters) {
            if (f != null && f.external) {
               return false;
            }
         }

         Filter realms = new Filter("Realms", "");
         realms.external = true;
         filters.add(realms);
         return true;
      }

      /**
       * Brings filters left at an older default up to the current one.
       *
       * @return true if anything changed
       */
      public static boolean upgradeOldDefaults(List<Filter> filters) {
         boolean changed = false;
         for (Filter f : filters) {
            if (f == null || f.pattern == null) {
               continue;
            }

            if ("Teleports".equals(f.name) && (OLD_TELEPORTS_PATTERN.equals(f.pattern) || OLD_TELEPORTS_PATTERN_2.equals(f.pattern))) {
               f.pattern = TELEPORTS_PATTERN;
               f.regex = true;
               f.hideFromMain = true;
               changed = true;
            } else if ("Marry".equals(f.name) && (OLD_MARRY_PATTERN.equals(f.pattern) || OLD_MARRY_PATTERN_2.equals(f.pattern))) {
               f.pattern = MARRY_PATTERN;
               f.regex = true;
               f.hideFromMain = true;
               changed = true;
            } else if ("Staff".equals(f.name) && OLD_STAFF_PATTERN.equals(f.pattern)) {
               f.pattern = STAFF_PATTERN;
               f.regex = true;
               changed = true;
            } else if ("Messages".equals(f.name) && (OLD_MESSAGES_PATTERN.equals(f.pattern) || OLD_MESSAGES_PATTERN_2.equals(f.pattern))) {
               f.pattern = MESSAGES_PATTERN;
               f.regex = true;
               f.hideFromMain = true;
               changed = true;
            }
         }

         return changed;
      }
   }

   private FeatureConfig() {
   }

   // ------------------------------------------------------------------ persistence

   public static void init() {
      file = FabricLoader.getInstance().getConfigDir().resolve(SuiteRuntime.profile().modId()).resolve("features.json");
      if (Files.isRegularFile(file)) {
         try (Reader reader = Files.newBufferedReader(file)) {
            FeatureConfig loaded = GSON.fromJson(reader, FeatureConfig.class);
            if (loaded != null) {
               INSTANCE = loaded;
               loaded.repair();
            }
         } catch (Exception e) {
            SuiteLog.logger().warn("[features] could not read {}: {}", file.getFileName(), e.toString());
         }
      }

      SuiteScheduler.IO.scheduleWithFixedDelay(FeatureConfig::flushIfDirty, 5L, 5L, TimeUnit.SECONDS);
      Runtime.getRuntime().addShutdownHook(new Thread(FeatureConfig::flushIfDirty, "BlossomBuddy-FeatureFlush"));
   }

   /** Fills anything an old or hand-edited file left null. */
   private void repair() {
      if (this.scoreboard == null) this.scoreboard = new Scoreboard();
      if (this.scoreboard.panel == null) this.scoreboard.panel = new Panel();
      if (this.hands == null) this.hands = new Hands();
      if (this.slotLocks == null) this.slotLocks = new SlotLocks();
      if (this.relay == null) this.relay = new Relay();
      if (this.hotbar == null) this.hotbar = new Hotbar();
      if (this.hotbar.panel == null) this.hotbar.panel = new Panel();
      this.hotbar.extraRows = Math.max(1, Math.min(2, this.hotbar.extraRows));
      if (this.xp == null) this.xp = new Xp();
      if (this.xp.panel == null) this.xp.panel = new Panel();
      if (this.chat == null) this.chat = new Chat();
      if (this.chat.panel == null) this.chat.panel = new Panel();
      if (this.chat.filters == null) this.chat.filters = new ArrayList<>();
      if (Chat.upgradeOldDefaults(this.chat.filters) | Chat.ensureRealmsFilter(this.chat.filters)) {
         markDirty();
      }

      if (!this.chat.staffFilterAdded) {
         Chat.ensureStaffFilter(this.chat.filters);
         this.chat.staffFilterAdded = true;
         markDirty();
      }

      if (this.chat.windows == null) this.chat.windows = new ArrayList<>();
      if (Chat.normalizeWindows(this.chat.windows)) {
         markDirty();
      }

      if (this.presence == null) this.presence = new Presence();
      if (this.emotes == null) this.emotes = new Emotes();
      if (this.stats == null) this.stats = new Stats();
      if (this.stats.installId == null) this.stats.installId = "";
      if (this.stats.namedAs == null) this.stats.namedAs = "";
      if (this.xchat == null) this.xchat = new XChat();
      if (this.xchat.muted == null) this.xchat.muted = new ArrayList<>();
      if (this.disabledCooldownItems == null) this.disabledCooldownItems = new ArrayList<>();
      this.hands.smallHandsScale = Math.max(0.2F, Math.min(1.0F, this.hands.smallHandsScale));
   }

   public static void markDirty() {
      dirty = true;
   }

   public static void flushIfDirty() {
      if (!dirty || file == null) {
         return;
      }

      dirty = false;
      try {
         Files.createDirectories(file.getParent());
         Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
         try (Writer writer = Files.newBufferedWriter(tmp)) {
            GSON.toJson(INSTANCE, writer);
         }

         Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
         dirty = true;
         SuiteLog.logger().warn("[features] could not save {}: {}", file.getFileName(), e.toString());
      }
   }
}
