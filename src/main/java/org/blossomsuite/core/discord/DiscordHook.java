package org.blossomsuite.core.discord;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.chat.VanishState;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.dungeons.DungeonState;
import org.blossomsuite.core.session.SessionRecord;
import org.blossomsuite.core.session.SessionTracker;
import org.blossomsuite.core.util.WorldGate;

/**
 * Connects {@link DiscordPresence} to the game: shows "BlossomBuddy - On Cherry Realm" in Discord while you are on a realm, if you turned
 * it on. The name next to "Playing" comes from the Discord application the id belongs to, so the application is called BlossomBuddy.
 */
public final class DiscordHook {
   public static final DiscordHook INSTANCE = new DiscordHook();
   /** The Discord application id of BlossomBuddy (it is public, not a secret). Empty until the application exists. */
   public static final String BUILT_IN_APP_ID = "1550998827996160100";
   static final String IMAGE_KEY = "logo";
   static final String SITE = "https://blossombuddy.site";

   private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "BlossomBuddy-Discord");
      t.setDaemon(true);
      return t;
   });
   private final DiscordPresence presence = new DiscordPresence(DiscordTransports.system(), this.io, System::currentTimeMillis, DiscordHook::pidToReport);

   private boolean hiddenByVanish = false;

   private DiscordHook() {
   }

   /** Always BlossomBuddy's own Discord application: it is what puts the name next to "Playing", so it is not something to change. */
   public static String effectiveAppId() {
      return BUILT_IN_APP_ID;
   }

   /**
    * The process id Discord is told this status belongs to: the game's own, or (when the player asks for it) the program that started the
    * game. Another app that also shows a status for Minecraft may use the game's id, and Discord keeps one status per process.
    */
   static long pidToReport() {
      ProcessHandle self = ProcessHandle.current();
      if ("parent".equalsIgnoreCase(FeatureConfig.INSTANCE.discord.pidMode)) {
         java.util.Optional<ProcessHandle> parent = self.parent();
         if (parent.isPresent() && parent.get().isAlive()) {
            return parent.get().pid();
         }
      }

      return self.pid();
   }

   public String pidModeLabel() {
      return "parent".equalsIgnoreCase(FeatureConfig.INSTANCE.discord.pidMode) ? "launcher" : "game";
   }

   /** Which process the status is tied to: "game" (default) or "launcher". The connection is redone so it takes effect now. */
   public void setPidMode(boolean parent) {
      FeatureConfig.INSTANCE.discord.pidMode = parent ? "parent" : "own";
      FeatureConfig.markDirty();
      this.presence.stop();
   }

   public boolean enabled() {
      return FeatureConfig.INSTANCE.discord.enabled;
   }

   public String status() {
      if (!this.enabled()) {
         return "Off";
      }

      if (VanishState.vanished) {
         return "Hidden while you are vanished";
      }

      if (effectiveAppId().isEmpty()) {
         return this.presence.status();
      }

      return this.desired() == null ? "Waiting: it shows once you are on BlossomCraft (" + this.presence.status() + ")" : this.presence.status();
   }

   public void setEnabled(boolean on) {
      FeatureConfig.Discord cfg = FeatureConfig.INSTANCE.discord;
      cfg.enabled = on;
      FeatureConfig.markDirty();
      if (on) {
         org.blossomsuite.core.progress.Progress.first("presence");
         ChatOutput.info("Discord presence is ON: people who can see your Discord activity will see \"Playing BlossomBuddy\" and which realm you are on. "
            + "It is shown through your own Discord app; nothing is sent to BlossomBuddy. Turn it off any time with /buddy presence off.");
      } else {
         this.presence.stop();
         ChatOutput.info("Discord presence is OFF.");
      }
   }

   /** Call about once a second. */
   public void tick() {
      if (!this.enabled()) {
         return;
      }

      // staff in vanish must not show up on Discord: take the status down at once (not at the next regular update) and keep it down
      if (VanishState.vanished) {
         if (!this.hiddenByVanish) {
            this.hiddenByVanish = true;
            this.presence.stop();
         }

         return;
      }

      this.hiddenByVanish = false;
      this.presence.tick(effectiveAppId(), this.desired());
   }

   /** Leaving the game takes the status down. */
   public void onLeave() {
      if (this.enabled()) {
         this.presence.stop();
      }
   }

   private DiscordPresence.Activity desired() {
      Minecraft mc = Minecraft.getInstance();
      String server = WorldGate.Server;
      if (mc == null || mc.player == null || !SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         return null;
      }

      SessionRecord live = SessionTracker.INSTANCE.live();
      long start = (live != null ? live.startMs() : System.currentTimeMillis()) / 1000L;
      // on a realm: "On Cherry Realm"; on BlossomCraft but not (yet) on a realm, such as the lobby: "On BlossomCraft"
      String where = server == null || server.isBlank() ? "On BlossomCraft" : "On " + SuiteRuntime.profile().serverDisplayName(server) + " Realm";
      return new DiscordPresence.Activity(
         where,
         DungeonState.inRun() ? "In a dungeon" : "",
         start,
         IMAGE_KEY,
         SuiteRuntime.profile().displayName(),
         "Get " + SuiteRuntime.profile().displayName(),
         SITE
      );
   }
}
