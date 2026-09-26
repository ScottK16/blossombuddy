package org.blossomsuite.core.chat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.text.Text;
import org.blossomsuite.core.config.FeatureConfig;

/**
 * A second chat window: copies of incoming chat lines that match the player's filters (teleport requests,
 * marriage chat, private messages, ...), kept in their own list so they don't scroll away in the main chat.
 */
public final class SecondaryChat {
   public static final SecondaryChat INSTANCE = new SecondaryChat();
   private static final int MAX_LINES = 400;

   public record Line(long atMs, Text text, String filter) {
   }

   private final Deque<Line> lines = new ArrayDeque<>();
   /** How many of the newest lines (of the shown tab) the reader has scrolled back past. 0 = following the newest. */
   private int scroll = 0;
   /** Goes up whenever a line is added or the list is cleared, so the window can reuse what it worked out last frame until then. */
   private volatile long version = 0L;

   /** Regex filters, compiled once instead of once per incoming chat line. */
   private static final java.util.Map<String, Pattern> COMPILED = new java.util.concurrent.ConcurrentHashMap<>();
   private static final Pattern NEVER = Pattern.compile("(?!)");

   public long version() {
      return this.version;
   }

   SecondaryChat() {
   }

   /** The tab cross-realm chat lands in. */
   public static final String REALMS = "Realms";

   /** Adds a line that did not come from the game's chat (cross-realm chat). */
   public void addExternal(Text text) {
      if (text != null) {
         this.add(new Line(System.currentTimeMillis(), text, REALMS));
      }
   }

   /**
    * Files an incoming chat line under every filter it matches.
    *
    * @return true if a matching filter asks for the line to be hidden from the main chat. Lines are only hidden
    *         while the secondary window is switched on, so nothing is ever swallowed with nowhere to read it.
    */
   public boolean onMessage(Text message) {
      if (message == null) {
         return false;
      }

      String plain = message.getString();
      boolean hide = false;
      for (FeatureConfig.Filter f : FeatureConfig.INSTANCE.chat.filters) {
         // a filter that has a window of its own turned on catches its lines even if its tab in the main window is off
         if (f != null && (f.enabled || dedicatedWindowShows(f.name)) && matches(f, plain)) {
            this.add(new Line(System.currentTimeMillis(), message, f.name));
            hide |= f.hideFromMain && (FeatureConfig.INSTANCE.chat.show || dedicatedWindowShows(f.name));
         }
      }

      return hide;
   }

   /** The filters (lower case) whose lines the main window's "All" view leaves out because they have a window of their own. */
   private static Set<String> exclusiveFilters() {
      Set<String> out = new HashSet<>();
      List<FeatureConfig.ChatWindow> windows = FeatureConfig.INSTANCE.chat.windows;
      if (windows != null) {
         for (FeatureConfig.ChatWindow w : windows) {
            if (w != null && w.enabled && w.exclusive && w.filter != null && !w.filter.isBlank()) {
               out.add(w.filter.toLowerCase(Locale.ROOT));
            }
         }
      }

      return out;
   }

   /** Is there an extra window turned on that shows this filter? */
   static boolean dedicatedWindowShows(String filterName) {
      List<FeatureConfig.ChatWindow> windows = FeatureConfig.INSTANCE.chat.windows;
      if (windows != null && filterName != null) {
         for (FeatureConfig.ChatWindow w : windows) {
            if (w != null && w.enabled && w.filter != null && !w.filter.isBlank() && w.filter.equalsIgnoreCase(filterName)) {
               return true;
            }
         }
      }

      return false;
   }

   /** Is this line part of the view for {@code filter}? "All" (null) skips the {@code excluded} filters. */
   private static boolean inView(String filter, Set<String> excluded, Line l) {
      String lineFilter = l.filter() == null ? "" : l.filter();
      return filter != null ? filter.equalsIgnoreCase(lineFilter) : !excluded.contains(lineFilter.toLowerCase(Locale.ROOT));
   }

   private synchronized void add(Line line) {
      this.version++;
      this.lines.addLast(line);
      while (this.lines.size() > MAX_LINES) {
         this.lines.removeFirst();
      }

      // someone reading older lines keeps their place while new ones arrive underneath
      String shown = this.selectedName();
      if (this.scroll > 0 && inView(shown, shown == null ? exclusiveFilters() : Set.of(), line)) {
         this.scroll++;
      }

      this.scroll = Math.min(this.scroll, Math.max(0, this.count(shown) - 1));
   }

   /** The newest {@code max} lines, oldest first; only those from {@code filter} when it is non-null. */
   public synchronized List<Line> recent(String filter, int max) {
      return this.window(filter, 0, max);
   }

   /** Like {@link #recent}, but first skips the newest {@code skipNewest} lines: what a scrolled-back reader sees. */
   public synchronized List<Line> window(String filter, int skipNewest, int max) {
      List<Line> out = new ArrayList<>();
      int skipped = 0;
      Set<String> excluded = filter == null ? exclusiveFilters() : Set.of();
      for (java.util.Iterator<Line> it = this.lines.descendingIterator(); it.hasNext() && out.size() < max; ) {
         Line l = it.next();
         if (!inView(filter, excluded, l)) {
            continue;
         }

         if (skipped < skipNewest) {
            skipped++;
            continue;
         }

         out.add(l);
      }

      java.util.Collections.reverse(out);
      return out;
   }

   /** How many lines are kept for {@code filter} (all of them when it is null). */
   public synchronized int count(String filter) {
      int n = 0;
      Set<String> excluded = filter == null ? exclusiveFilters() : Set.of();
      for (Line l : this.lines) {
         if (inView(filter, excluded, l)) {
            n++;
         }
      }

      return n;
   }

   public synchronized int scroll() {
      return this.scroll;
   }

   /** Scroll back ({@code delta} > 0) or forward ({@code delta} < 0); never past the oldest line or the newest. */
   public synchronized boolean scrollBy(int delta) {
      int before = this.scroll;
      this.scroll = Math.max(0, Math.min(this.scroll + delta, Math.max(0, this.count(this.selectedName()) - 1)));
      return this.scroll != before;
   }

   public synchronized void setScroll(int lines) {
      this.scroll = Math.max(0, lines);
   }

   /** Back to following the newest line. */
   public synchronized void resetScroll() {
      this.scroll = 0;
   }

   public synchronized void clear() {
      this.version++;
      this.lines.clear();
      this.scroll = 0;
   }

   /** Name of the filter the panel shows, or null for all. */
   public String selectedName() {
      FeatureConfig.Chat c = FeatureConfig.INSTANCE.chat;
      return c.selected >= 0 && c.selected < c.filters.size() ? c.filters.get(c.selected).name : null;
   }

   /** All -> first filter -> second filter -> ... -> all. */
   public String cycleFilter() {
      FeatureConfig.Chat c = FeatureConfig.INSTANCE.chat;
      c.selected = c.selected + 1 >= c.filters.size() ? -1 : c.selected + 1;
      this.resetScroll();
      FeatureConfig.markDirty();
      String name = this.selectedName();
      return name == null ? "All" : name;
   }

   // ------------------------------------------------------------------ matching

   /**
    * BlossomCraft writes its labels in small-capital Unicode (the word MESSAGE is really U+1D0D U+1D07 U+A731 ...),
    * which looks identical on screen but is not the letters a filter is written with. Small capitals are folded to
    * plain upper-case letters before matching; what is displayed is never changed.
    */
   private static final String SMALL_CAPS = "\u1d00\u0299\u1d04\u1d05\u1d07\ua730\u0262\u029c\u026a\u1d0a\u1d0b\u029f\u1d0d\u0274\u1d0f\u1d18\ua7af\u01eb\u0280\ua731\u1d1b\u1d1c\u1d20\u1d21\u028f\u1d22";
   private static final String SMALL_CAPS_AS = "ABCDEFGHIJKLMNOPQQRSTUVWYZ";

   static String foldSmallCaps(String text) {
      StringBuilder out = null;
      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         int at = c < 0x0100 ? -1 : SMALL_CAPS.indexOf(c);
         if (at >= 0) {
            if (out == null) {
               out = new StringBuilder(text.length()).append(text, 0, i);
            }

            out.append(SMALL_CAPS_AS.charAt(at));
         } else if (out != null) {
            out.append(c);
         }
      }

      return out == null ? text : out.toString();
   }

   /** The compiled form of a filter's regex, remembered; one that isn't valid matches nothing. */
   private static Pattern compiled(String pattern) {
      Pattern cached = COMPILED.get(pattern);
      if (cached != null) {
         return cached;
      }

      Pattern made;
      try {
         made = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
      } catch (PatternSyntaxException e) {
         made = NEVER;
      }

      if (COMPILED.size() >= 64) {
         COMPILED.clear(); // filters are edited by hand, so this only ever holds a handful; this just stops it growing without limit
      }

      COMPILED.put(pattern, made);
      return made;
   }

   static boolean matches(FeatureConfig.Filter f, String rawText) {
      if (f.external || f.pattern == null || f.pattern.isBlank() || rawText == null) {
         return false;
      }

      String plain = foldSmallCaps(rawText);
      if (f.regex) {
         return compiled(f.pattern).matcher(plain).find();
      }

      String haystack = plain.toLowerCase(Locale.ROOT);
      for (String term : f.pattern.split("\\|")) {
         String t = term.toLowerCase(Locale.ROOT);
         if (!t.isBlank() && haystack.contains(t)) {
            return true;
         }
      }

      return false;
   }
}
