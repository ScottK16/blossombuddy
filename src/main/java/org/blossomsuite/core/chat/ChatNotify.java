package org.blossomsuite.core.chat;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.config.FeatureConfig;

/**
 * Plays a ding when a word you picked shows up in chat. Everything happens on your computer: the chat line is read, compared with
 * your list and thrown away. Your own messages never set it off, and it will not ring more than once every second or so.
 */
public final class ChatNotify {
   public static final ChatNotify INSTANCE = new ChatNotify(new GameEnv());
   static final long DEBOUNCE_MS = 1500L;
   private static final int SENDER_SCAN = 48;
   private static final String[] SENDER_END = {":", "»", ">", "›"};

   /** What the notifier needs from the game, so it can be tried without one. */
   interface Env {
      boolean enabled();

      List<String> words();

      boolean mentionName();

      String selfName();

      long now();

      boolean isOurOwnLine(String text);

      void ding();
   }

   private final Env env;
   private long lastDingAtMs = Long.MIN_VALUE / 2;

   ChatNotify(Env env) {
      this.env = env;
   }

   /** When it last rang (epoch milliseconds), or a hugely negative number if never; for checks that the chat hook is wired up. */
   public long lastDingAtMs() {
      return this.lastDingAtMs;
   }

   /** A line from a plugin or the server (the usual way chat arrives on BlossomCraft). */
   public void onLine(String text) {
      this.consider(text, null);
   }

   /** A line of signed player chat: the text is only what was said, the sender comes separately. */
   public void onPlayerChat(String said, String senderName) {
      this.consider(said, senderName == null ? "" : senderName);
   }

   private void consider(String text, String knownSender) {
      if (text == null || text.isBlank() || !this.env.enabled() || this.env.isOurOwnLine(text)) {
         return;
      }

      String self = this.env.selfName();
      if (knownSender != null) {
         if (!self.isBlank() && self.equalsIgnoreCase(knownSender.trim())) {
            return;
         }
      } else if (isOwnMessage(text, self)) {
         return;
      }

      boolean hit = firstMatch(text, this.env.words()) != null;
      if (!hit && this.env.mentionName() && !self.isBlank()) {
         String said = knownSender != null ? text : textAfterSender(text);
         hit = said != null && containsWord(said, self);
      }

      if (!hit) {
         return;
      }

      long now = this.env.now();
      if (now - this.lastDingAtMs < DEBOUNCE_MS) {
         return;
      }

      this.lastDingAtMs = now;
      this.env.ding();
   }

   /** The first of {@code words} that appears in {@code text} as a whole word or phrase (any case), or null. */
   static String firstMatch(String text, List<String> words) {
      if (text == null || words == null) {
         return null;
      }

      for (String word : words) {
         if (containsWord(text, word)) {
            return word;
         }
      }

      return null;
   }

   static boolean containsWord(String text, String word) {
      if (text == null || word == null || word.isBlank()) {
         return false;
      }

      Pattern pattern = Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(word.trim()) + "(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
      return pattern.matcher(text).find();
   }

   /** Where the sender's name ends in a chat line like "[Rank] Name: hello" or "Name » hello"; -1 when it does not look like chat. */
   private static int senderEnd(String text) {
      int limit = Math.min(text.length(), SENDER_SCAN);
      int best = -1;
      for (String end : SENDER_END) {
         int at = text.indexOf(end);
         if (at >= 0 && at < limit && (best < 0 || at < best)) {
            best = at;
         }
      }

      return best;
   }

   /** True when the line starts with your own name as the speaker. A line with no speaker (a server broadcast) is never "yours". */
   static boolean isOwnMessage(String text, String self) {
      if (self == null || self.isBlank()) {
         return false;
      }

      int end = senderEnd(text);
      return end >= 0 && containsWord(text.substring(0, end), self);
   }

   /** What was said after the speaker's name, or null if the line has no speaker. */
   static String textAfterSender(String text) {
      int end = senderEnd(text);
      return end < 0 ? null : text.substring(end + 1);
   }

   // ------------------------------------------------------------------ in the game

   /** The sound for a saved id; the pling if the id is not one. */
   static SoundEvent soundFor(String id) {
      try {
         SoundEvent found = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse(id == null ? "" : id.trim().toLowerCase(Locale.ROOT)));
         if (found != null && found != SoundEvents.EMPTY) {
            return found;
         }
      } catch (Exception ignored) {
         // fall through to the default
      }

      return BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse("block.note_block.pling"));
   }

   /** Plays the chosen ding once, now (for the settings page and /buddy notify test). */
   public static void playNow() {
      Minecraft mc = Minecraft.getInstance();
      FeatureConfig.ChatNotify cfg = FeatureConfig.INSTANCE.chatNotify;
      if (mc.player != null && mc.level != null) {
         float volume = Math.max(0.0F, Math.min(1.0F, cfg.volume));
         mc.level.playSound(mc.player, mc.player.getX(), mc.player.getY(), mc.player.getZ(), soundFor(cfg.sound), SoundSource.MASTER, volume, 1.0F);
      }
   }

   private static final class GameEnv implements Env {
      @Override
      public boolean enabled() {
         return FeatureConfig.INSTANCE.chatNotify.enabled;
      }

      @Override
      public List<String> words() {
         return FeatureConfig.INSTANCE.chatNotify.words();
      }

      @Override
      public boolean mentionName() {
         return FeatureConfig.INSTANCE.chatNotify.mentionName;
      }

      @Override
      public String selfName() {
         Minecraft mc = Minecraft.getInstance();
         return mc != null && mc.player != null ? mc.player.getName().getString().trim() : "";
      }

      @Override
      public long now() {
         return System.currentTimeMillis();
      }

      @Override
      public boolean isOurOwnLine(String text) {
         return text.contains("[" + SuiteRuntime.profile().displayName().toUpperCase(Locale.ROOT) + "]");
      }

      @Override
      public void ding() {
         Minecraft mc = Minecraft.getInstance();
         mc.execute(ChatNotify::playNow);
      }
   }
}
