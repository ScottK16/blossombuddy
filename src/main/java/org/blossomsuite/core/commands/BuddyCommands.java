package org.blossomsuite.core.commands;

import net.minecraft.client.MinecraftClient;
import org.blossomsuite.core.presence.PresenceClient;
import org.blossomsuite.core.stats.StatsClient;
import org.blossomsuite.core.emote.Emote;
import org.blossomsuite.core.emote.EmoteClient;
import org.blossomsuite.core.ui.ChatSearchScreen;
import org.blossomsuite.core.ui.EmoteWheelScreen;
import org.blossomsuite.core.ui.MapArtScreen;
import org.blossomsuite.core.ui.PlayerListScreen;
import org.blossomsuite.core.xchat.XChatClient;
import org.blossomsuite.core.xchat.XChatMode;

import com.mojang.brigadier.CommandDispatcher;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.net.URI;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.chat.SecondaryChat;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.hud.ScoreboardHud;
import org.blossomsuite.core.jobs.JobXpTracker;
import org.blossomsuite.core.state.SuiteState;

/** Subcommands for the BlossomBuddy features, attached under the main command (/buddy, /bb, /bsuite). */
public final class BuddyCommands {
   public static final String DEVELOPER = "IrishScotty";

   private BuddyCommands() {
   }

   public static List<LiteralArgumentBuilder<FabricClientCommandSource>> subcommands() {
      return List.of(credit(), xp(), chat(), scoreboard(), share(), xchat(), stats(), who(), emote(), search(), mapart(), privacy());
   }

   public static final String DISCORD_INVITE = "https://discord.gg/EA4WwSdGTj";
   public static final String PRIVACY_URL = "https://blossombuddy.site/privacy.html";

   public static void sendCredits() {
      ChatOutput.info(Text.literal("Developed by ").formatted(Formatting.GRAY).append(Text.literal(DEVELOPER).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)));
      ChatOutput.info(
         Text.literal("Acknowledgement to ").formatted(Formatting.GRAY)
            .append(Text.literal("Zodancy").formatted(Formatting.AQUA))
            .append(Text.literal(", who made the original BlossomSuite.").formatted(Formatting.GRAY))
      );
      ChatOutput.info(
         Text.literal("Jobs overflow tracking is based on Jobs Overflow XP by ").formatted(Formatting.GRAY)
            .append(Text.literal("Mills").formatted(Formatting.AQUA))
            .append(Text.literal(".").formatted(Formatting.GRAY))
      );
      ChatOutput.info(
         Text.literal("Support and suggestions: ").formatted(Formatting.GRAY)
            .append(
               Text.literal(DISCORD_INVITE)
                  .formatted(Formatting.LIGHT_PURPLE, Formatting.UNDERLINE)
                  .styled(style -> style.withClickEvent(new ClickEvent.OpenUrl(URI.create(DISCORD_INVITE))).withHoverEvent(new HoverEvent.ShowText(Text.literal("Open the Discord invite"))))
            )
      );
   }

   private static LiteralArgumentBuilder<FabricClientCommandSource> credit() {
      return ClientCommandManager.literal("credit").executes(ctx -> {
         sendCredits();
         return 1;
      });
   }

   /** {@code /buddy privacy}: what the mod collects, what's opt-in, and where to read the full statement. */
   public static void sendPrivacy() {
      ChatOutput.info(Text.literal("BlossomBuddy privacy").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
      ChatOutput.info(
         Text.literal("Self-hosted by ").formatted(Formatting.GRAY)
            .append(Text.literal(DEVELOPER).formatted(Formatting.AQUA))
            .append(Text.literal(" - no third-party service handles chat, and there's no access to your private or marry messages.").formatted(Formatting.GRAY))
      );
      ChatOutput.info(
         Text.literal("Cross-realm chat, the player list and vote-party sharing are ").formatted(Formatting.GRAY)
            .append(Text.literal("opt-in").formatted(Formatting.AQUA))
            .append(Text.literal(" (the player list is on by default with a notice first) and held in memory only, never written to disk.").formatted(Formatting.GRAY))
      );
      ChatOutput.info(
         Text.literal("Usage stats are a random ID and the mod version by default; sharing your username is opt-in (").formatted(Formatting.GRAY)
            .append(Text.literal("/buddy stats").formatted(Formatting.AQUA))
            .append(Text.literal(").").formatted(Formatting.GRAY))
      );
      ChatOutput.info(
         Text.literal("Emote grants are stored against your Minecraft account so they persist between sessions - message ").formatted(Formatting.GRAY)
            .append(Text.literal(DEVELOPER).formatted(Formatting.AQUA))
            .append(Text.literal(" any time to have that removed.").formatted(Formatting.GRAY))
      );
      ChatOutput.info(
         Text.literal("Full statement: ").formatted(Formatting.GRAY)
            .append(
               Text.literal(PRIVACY_URL)
                  .formatted(Formatting.LIGHT_PURPLE, Formatting.UNDERLINE)
                  .styled(style -> style.withClickEvent(new ClickEvent.OpenUrl(URI.create(PRIVACY_URL))).withHoverEvent(new HoverEvent.ShowText(Text.literal("Open the privacy page"))))
            )
      );
   }

   private static LiteralArgumentBuilder<FabricClientCommandSource> privacy() {
      return ClientCommandManager.literal("privacy").executes(ctx -> {
         sendPrivacy();
         return 1;
      });
   }

   private static LiteralArgumentBuilder<FabricClientCommandSource> xp() {
      return ClientCommandManager.literal("xp").then(ClientCommandManager.literal("reset").executes(ctx -> {
         JobXpTracker.INSTANCE.reset();
         ChatOutput.info("XP tracker reset.");
         return 1;
      }));
   }

   private static LiteralArgumentBuilder<FabricClientCommandSource> scoreboard() {
      return ClientCommandManager.literal("scoreboard")
         .then(ClientCommandManager.literal("hide").executes(ctx -> setHidden(true)))
         .then(ClientCommandManager.literal("show").executes(ctx -> setHidden(false)))
         .then(ClientCommandManager.literal("reset").executes(ctx -> {
            FeatureConfig.Scoreboard sb = FeatureConfig.INSTANCE.scoreboard;
            sb.panel = new FeatureConfig.Panel();
            sb.hidden = false;
            FeatureConfig.markDirty();
            ChatOutput.info("Scoreboard back to its default spot and size.");
            return 1;
         }));
   }

   /** {@code /xc <message>}: say something to every BlossomBuddy player on every realm. */
   public static void registerTopLevel(CommandDispatcher<FabricClientCommandSource> dispatcher) {
      dispatcher.register(
         ClientCommandManager.literal("xc")
            .executes(ctx -> {
               XChatMode.toggleAndTell(); // "/xc" on its own switches cross-realm chat mode on or off
               return 1;
            })
            .then(ClientCommandManager.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
               XChatClient.INSTANCE.sendAsync(StringArgumentType.getString(ctx, "message"));
               return 1;
            }))
      );
   }

   /** {@code /buddy xchat [on|off|mute <name>|unmute <name>]}: cross-realm chat. */
   private static LiteralArgumentBuilder<FabricClientCommandSource> xchat() {
      return ClientCommandManager.literal("xchat")
         .executes(ctx -> {
            ChatOutput.info("Cross-realm chat is " + (FeatureConfig.INSTANCE.xchat.enabled ? "ON. " : "OFF. ") + XChatClient.INSTANCE.status());
            return 1;
         })
         .then(ClientCommandManager.literal("on").executes(ctx -> setXChat(true)))
         .then(ClientCommandManager.literal("off").executes(ctx -> setXChat(false)))
         .then(ClientCommandManager.literal("mode")
            .executes(ctx -> {
               XChatMode.toggleAndTell();
               return 1;
            })
            .then(ClientCommandManager.literal("on").executes(ctx -> setXChatMode(true)))
            .then(ClientCommandManager.literal("off").executes(ctx -> setXChatMode(false))))
         .then(ClientCommandManager.literal("mute").then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(ctx -> mute(StringArgumentType.getString(ctx, "name"), true))))
         .then(ClientCommandManager.literal("unmute").then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(ctx -> mute(StringArgumentType.getString(ctx, "name"), false))));
   }

   private static int setXChatMode(boolean on) {
      if (on && !FeatureConfig.INSTANCE.xchat.enabled) {
         ChatOutput.info("Cross-realm chat is off. Turn it on with /buddy xchat on.");
         return 1;
      }

      XChatMode.set(on);
      XChatMode.tell(on);
      return 1;
   }

   private static int setXChat(boolean on) {
      FeatureConfig.INSTANCE.xchat.enabled = on;
      if (!on) {
         XChatMode.set(false);
      }

      FeatureConfig.markDirty();
      if (on) {
         XChatClient.INSTANCE.wake();
         ChatOutput.info("Cross-realm chat is ON. Send with /xc <message>. Messages show in the secondary chat's Realms tab (or in this chat if that window is off).");
         if (SuiteState.INSTANCE.http == null || !SuiteState.INSTANCE.http.enabled()) {
            ChatOutput.info("No relay address is set yet, so it can't connect.");
         }
      } else {
         ChatOutput.info("Cross-realm chat is off.");
      }

      return 1;
   }

   private static int mute(String name, boolean mute) {
      List<String> muted = FeatureConfig.INSTANCE.xchat.muted;
      String key = name.toLowerCase(java.util.Locale.ROOT);
      if (mute) {
         if (!muted.contains(key)) {
            muted.add(key);
         }

         ChatOutput.info("Muted " + name + ". You won't see their cross-realm messages.");
      } else {
         muted.remove(key);
         ChatOutput.info("Unmuted " + name + ".");
      }

      FeatureConfig.markDirty();
      return 1;
   }

   /** {@code /buddy search [term...]}: find a name or word across main chat, and copy lines out. */
   private static LiteralArgumentBuilder<FabricClientCommandSource> search() {
      return ClientCommandManager.literal("search")
         .executes(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.send(() -> mc.setScreen(new ChatSearchScreen(mc.currentScreen)));
            return 1;
         })
         .then(ClientCommandManager.argument("term", StringArgumentType.greedyString()).executes(ctx -> {
            String term = StringArgumentType.getString(ctx, "term");
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.send(() -> mc.setScreen(new ChatSearchScreen(mc.currentScreen, term)));
            return 1;
         }));
   }

   /** {@code /buddy emote [list|stop|<name>]}: the emote wheel, or play one by name. */
   private static LiteralArgumentBuilder<FabricClientCommandSource> emote() {
      return ClientCommandManager.literal("emote")
         .executes(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.send(() -> mc.setScreen(new EmoteWheelScreen()));
            return 1;
         })
         .then(ClientCommandManager.literal("list").executes(ctx -> {
            ChatOutput.info("Emotes: " + EmoteClient.emoteNames() + ". Play one with /buddy emote <name>, or open the wheel with /buddy emote.");
            return 1;
         }))
         .then(ClientCommandManager.literal("stop").executes(ctx -> {
            EmoteClient.INSTANCE.stop();
            return 1;
         }))
         .then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(ctx -> {
            EmoteClient.INSTANCE.play(StringArgumentType.getString(ctx, "name"));
            return 1;
         }));
   }

   /** {@code /buddy mapart [<code>]}: opens the map art screen, looking up a design by its code if one was given. */
   private static LiteralArgumentBuilder<FabricClientCommandSource> mapart() {
      return ClientCommandManager.literal("mapart")
         .executes(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.send(() -> mc.setScreen(new MapArtScreen(mc.currentScreen, "")));
            return 1;
         })
         .then(ClientCommandManager.argument("code", StringArgumentType.word()).executes(ctx -> {
            String code = StringArgumentType.getString(ctx, "code");
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.send(() -> mc.setScreen(new MapArtScreen(mc.currentScreen, code)));
            return 1;
         }));
   }

   /** {@code /buddy who [on|off]}: the list of players using the mod, by realm. Appearing in it is opt-in. */
   private static LiteralArgumentBuilder<FabricClientCommandSource> who() {
      return ClientCommandManager.literal("who")
         .executes(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.send(() -> mc.setScreen(new PlayerListScreen())); // a tick later, so the chat screen closing doesn't undo it
            return 1;
         })
         .then(ClientCommandManager.literal("on").executes(ctx -> {
            PresenceClient.INSTANCE.setEnabled(true);
            return 1;
         }))
         .then(ClientCommandManager.literal("off").executes(ctx -> {
            PresenceClient.INSTANCE.setEnabled(false);
            return 1;
         }));
   }

   /** {@code /buddy stats [on|off|name on|name off]}: anonymous usage counts, and sharing your username. */
   private static LiteralArgumentBuilder<FabricClientCommandSource> stats() {
      return ClientCommandManager.literal("stats")
         .executes(ctx -> {
            ChatOutput.info(StatsClient.INSTANCE.status());
            ChatOutput.info("Only a random ID and the mod version are sent. /buddy stats off stops it. /buddy stats name on|off shares or hides your username.");
            return 1;
         })
         .then(ClientCommandManager.literal("on").executes(ctx -> {
            StatsClient.INSTANCE.setEnabled(true);
            ChatOutput.info("Usage counting is on (random ID only).");
            return 1;
         }))
         .then(ClientCommandManager.literal("off").executes(ctx -> {
            StatsClient.INSTANCE.setEnabled(false);
            return 1;
         }))
         .then(ClientCommandManager.literal("name")
            .then(ClientCommandManager.literal("on").executes(ctx -> {
               StatsClient.INSTANCE.setShareName(true);
               return 1;
            }))
            .then(ClientCommandManager.literal("off").executes(ctx -> {
               StatsClient.INSTANCE.setShareName(false);
               return 1;
            })));
   }

   /** {@code /buddy share [on|off]}: vote-party sharing between realms. */
   private static LiteralArgumentBuilder<FabricClientCommandSource> share() {
      return ClientCommandManager.literal("share")
         .executes(ctx -> {
            boolean relay = SuiteState.INSTANCE.http != null && SuiteState.INSTANCE.http.enabled();
            ChatOutput.info("Vote-party sharing is " + (FeatureConfig.INSTANCE.relay.share ? "ON" : "OFF") + (relay ? "." : " (no relay address is set, so nothing is sent)."));
            return 1;
         })
         .then(ClientCommandManager.literal("on").executes(ctx -> setShare(true)))
         .then(ClientCommandManager.literal("off").executes(ctx -> setShare(false)));
   }

   private static int setShare(boolean on) {
      FeatureConfig.INSTANCE.relay.share = on;
      FeatureConfig.markDirty();
      ChatOutput.info(on ? "Sharing your realm's vote-party count with other realms (anonymous)." : "Vote-party sharing is off. You won't get other realms' alerts either.");
      return 1;
   }

   private static int setHidden(boolean hidden) {
      FeatureConfig.INSTANCE.scoreboard.hidden = hidden;
      FeatureConfig.markDirty();
      ChatOutput.info("Scoreboard " + (hidden ? "hidden." : "shown."));
      if (ScoreboardHud.foreign()) {
         ChatOutput.info(ScoreboardHud.foreignName() + " is drawing the scoreboard, so this has no effect on it. Change it in " + ScoreboardHud.foreignName() + "'s own settings.");
      }

      return 1;
   }

   /** {@code /buddy chat [filter]}: print the latest secondary-chat lines into the main chat. */
   private static LiteralArgumentBuilder<FabricClientCommandSource> chat() {
      return ClientCommandManager.literal("chat")
         .executes(ctx -> print(null))
         .then(ClientCommandManager.argument("filter", StringArgumentType.greedyString()).executes(ctx -> print(StringArgumentType.getString(ctx, "filter"))));
   }

   private static int print(String filter) {
      List<SecondaryChat.Line> lines = SecondaryChat.INSTANCE.recent(filter, 10);
      if (lines.isEmpty()) {
         ChatOutput.info(filter == null ? "No filtered chat yet." : "Nothing under '" + filter + "' yet.");
         return 1;
      }

      ChatOutput.info(filter == null ? "Latest filtered chat:" : "Latest '" + filter + "':");
      for (SecondaryChat.Line line : lines) {
         ChatOutput.raw(Text.literal("  ").append(line.text()));
      }

      return 1;
   }

}
