package org.blossomsuite.core.emote;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Who is playing which emote right now. Written by the network thread and read by the renderer, so it is thread-safe. */
public final class EmoteState {
   public static final EmoteState INSTANCE = new EmoteState();

   /** An emote that started at {@code startMs}. */
   public record Active(Emote emote, long startMs) {
   }

   private final Map<UUID, Active> active = new ConcurrentHashMap<>();

   public void start(UUID player, Emote emote, long nowMs) {
      if (player != null && emote != null) {
         this.active.put(player, new Active(emote, nowMs));
      }
   }

   public void stop(UUID player) {
      if (player != null) {
         this.active.remove(player);
      }
   }

   public void clear() {
      this.active.clear();
   }

   /** What this player is doing, or null when nothing (an emote that has run its time counts as nothing). */
   public Active activeFor(UUID player, long nowMs) {
      Active a = player == null ? null : this.active.get(player);
      if (a == null) {
         return null;
      }

      if ((nowMs - a.startMs()) / 1000.0F >= a.emote().durationSeconds()) {
         this.active.remove(player, a);
         return null;
      }

      return a;
   }

   public boolean isActive(UUID player, long nowMs) {
      return this.activeFor(player, nowMs) != null;
   }

   /** The pose to draw this player in right now, or null to draw them as normal. */
   public EmotePose poseFor(UUID player, long nowMs) {
      Active a = this.activeFor(player, nowMs);
      return a == null ? null : EmoteAnimator.pose(a.emote(), (nowMs - a.startMs()) / 1000.0F);
   }
}
