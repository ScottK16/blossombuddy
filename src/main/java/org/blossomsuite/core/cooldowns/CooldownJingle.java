package org.blossomsuite.core.cooldowns;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

public final class CooldownJingle {
   private static final Deque<CooldownJingle.NoteEvent> queue = new ArrayDeque<>();
   private static Supplier<Float> volumeSupplier = () -> 1.0F;
   private static SoundCategory soundCategory = SoundCategory.MASTER;

   private CooldownJingle() {
   }

   public static void configure(Supplier<Float> volumeSupplier, SoundCategory soundCategory) {
      CooldownJingle.volumeSupplier = volumeSupplier == null ? () -> 1.0F : volumeSupplier;
      CooldownJingle.soundCategory = soundCategory == null ? SoundCategory.MASTER : soundCategory;
   }

   public static void enqueueReadyJingle(long nowMs) {
      float[] notes = new float[]{1.0F, 1.26F, 1.5F};
      enqueuePattern(nowMs, notes, 90, volume(), 140);
   }

   public static void enqueueRelayReadyJingle(long nowMs) {
      float[] notes = new float[]{0.84F, 1.26F};
      enqueuePattern(nowMs, notes, 70, volume(), 120);
   }

   private static float volume() {
      Float value = volumeSupplier.get();
      return value == null ? 1.0F : value;
   }

   private static void enqueuePattern(long nowMs, float[] pitches, int stepMs, float volume, int gapAfterMs) {
      long startMs = nowMs;
      if (!queue.isEmpty()) {
         CooldownJingle.NoteEvent last = queue.peekLast();
         startMs = Math.max(startMs, last.atMs + gapAfterMs);
      }

      for (int i = 0; i < pitches.length; i++) {
         queue.addLast(new CooldownJingle.NoteEvent(startMs + (long)i * stepMs, pitches[i], volume));
      }
   }

   public static void tick(MinecraftClient client, long nowMs) {
      if (client.world != null && client.player != null) {
         while (!queue.isEmpty() && queue.peekFirst().atMs <= nowMs) {
            CooldownJingle.NoteEvent n = queue.removeFirst();
            client.world
               .playSound(
                  client.player,
                  client.player.getX(),
                  client.player.getY(),
                  client.player.getZ(),
                  SoundEvents.BLOCK_NOTE_BLOCK_PLING,
                  soundCategory,
                  n.volume,
                  n.pitch
               );
         }
      }
   }

   private static final class NoteEvent {
      final long atMs;
      final float pitch;
      final float volume;

      NoteEvent(long atMs, float pitch, float volume) {
         this.atMs = atMs;
         this.pitch = pitch;
         this.volume = volume;
      }
   }
}
