package org.blossomsuite.core.chat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.gui.hud.ChatHudLine;

/**
 * When each visible line of the main chat (vanilla's own {@code ChatHudLine.Visible}) was really created, in wall-clock time, so
 * hovering one can show when it was sent. Recorded by a mixin the moment vanilla builds each visible line (see
 * {@code ChatHudLineVisibleMixin}); vanilla's own {@code creationTick()} is a game tick, not a real time, so it can't answer that on
 * its own.
 *
 * <p>Keyed by object identity, not equality: two lines with identical text sent at the same moment are still different objects, and
 * a {@link ChatHudLine.Visible} is a record, so equality-based storage would wrongly merge them. Bounded, so it never grows forever
 * as chat scrolls; a line that has scrolled out of vanilla's own 100-line history is forgotten here too, at the same rate.
 */
public final class ChatLineTimestamps {
   private static final int MAX = 400;
   private static final Deque<ChatHudLine.Visible> order = new ArrayDeque<>();
   private static final Map<ChatHudLine.Visible, Long> times = new IdentityHashMap<>();

   private ChatLineTimestamps() {
   }

   public static synchronized void record(ChatHudLine.Visible line, long nowMs) {
      if (line == null || times.containsKey(line)) {
         return;
      }

      times.put(line, nowMs);
      order.addLast(line);
      while (order.size() > MAX) {
         times.remove(order.removeFirst());
      }
   }

   /** When {@code line} was created, or null if it was never recorded (or has since aged out). */
   public static synchronized Long timeOf(ChatHudLine.Visible line) {
      return line == null ? null : times.get(line);
   }

   public static synchronized int size() {
      return order.size();
   }

   public static synchronized void clear() {
      times.clear();
      order.clear();
   }
}
