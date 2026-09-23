package org.blossomsuite.core.chat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.minecraft.text.Text;

/**
 * Everything that has actually shown up in the main chat since you joined (not lines a secondary window or another filter kept out of
 * it), kept so it can be searched by name or by word and copied. Nothing here is saved to disk; it starts empty every time you join a
 * world, and only ever holds what you have already seen on your own screen.
 */
public final class MainChatLog {
   public static final MainChatLog INSTANCE = new MainChatLog();
   private static final int MAX_LINES = 1000;
   /** The most matches a search returns, so a very common word doesn't produce an unusable wall of results. */
   public static final int MAX_RESULTS = 300;

   public record Line(long atMs, Text text, String plain) {
   }

   private final Deque<Line> lines = new ArrayDeque<>();

   MainChatLog() {
   }

   /** Records a line that has just appeared in main chat. Blank lines (an empty message) are not worth keeping. */
   public synchronized void record(Text message) {
      if (message == null) {
         return;
      }

      String plain = message.getString();
      if (plain.isBlank()) {
         return;
      }

      this.lines.addLast(new Line(System.currentTimeMillis(), message, plain));
      while (this.lines.size() > MAX_LINES) {
         this.lines.removeFirst();
      }
   }

   public synchronized void clear() {
      this.lines.clear();
   }

   public synchronized int size() {
      return this.lines.size();
   }

   /**
    * Lines containing {@code query} anywhere, matched without regard to case (so it finds a player's name however it was typed),
    * newest first, at most {@code max}. A blank query returns the most recent lines instead, so opening the search with nothing
    * typed still shows something to copy.
    */
   public synchronized List<Line> search(String query, int max) {
      String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
      List<Line> out = new ArrayList<>();
      Iterator<Line> it = this.lines.descendingIterator();
      while (it.hasNext() && out.size() < max) {
         Line l = it.next();
         if (needle.isEmpty() || l.plain().toLowerCase(Locale.ROOT).contains(needle)) {
            out.add(l);
         }
      }

      return out;
   }

   public synchronized List<Line> search(String query) {
      return this.search(query, MAX_RESULTS);
   }
}
