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

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.chat.SecondaryChat;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.cooldowns.CooldownRules;
import org.blossomsuite.core.cooldowns.CooldownSoundStore;
import org.blossomsuite.core.cooldowns.CustomCooldownStore;
import org.blossomsuite.core.hud.ScoreboardHud;
import org.blossomsuite.core.jobs.JobXpTracker;
import org.blossomsuite.core.state.SuiteState;
import org.blossomsuite.core.util.SuiteItemIdUtil;

/** Subcommands for the BlossomBuddy features, attached under the main command (/buddy, /bb, /bsuite). */
public final class BuddyCommands {
   public static final String DEVELOPER = "IrishScotty";

   private BuddyCommands() {
   }

   public static List<LiteralArgumentBuilder<FabricClientCommandSource>> subcommands() {
      return List.of(credit(), xp(), chat(), scoreboard(), share(), xchat(), stats(), who(), emote(), search(), mapart(), privacy(), cooldown());
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
            .append(Text.literal(" (the player list is on by default with a notice first). The relay keeps them in memory only, but /xc messages are also posted to a BlossomBuddy Discord channel, where Discord keeps them.").formatted(Formatting.GRAY))
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
         ChatOutput.info("Note: /xc messages are also posted to a channel in the BlossomBuddy Discord, where Discord keeps them. See /buddy privacy.");
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

   /** {@code /buddy mapart [<file>]}: opens the map art screen, opening a design by file name if one was given. */
   private static LiteralArgumentBuilder<FabricClientCommandSource> mapart() {
      return ClientCommandManager.literal("mapart")
         .executes(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.send(() -> mc.setScreen(new MapArtScreen(mc.currentScreen, "")));
            return 1;
         })
         .then(ClientCommandManager.argument("file", StringArgumentType.greedyString()).executes(ctx -> {
            String file = StringArgumentType.getString(ctx, "file");
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.send(() -> mc.setScreen(new MapArtScreen(mc.currentScreen, file)));
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

   /**
    * {@code /buddy cooldown add <key> <seconds> [trigger]}, {@code addheld <seconds> [trigger]}, {@code remove <key>},
    * {@code removeheld}, {@code sound <key> <sound id|default>}, {@code soundheld <sound id|default>}, {@code list}:
    * player-added cooldown rules, layered on top of whatever cooldowns.json already has. The sound subcommands work
    * on any item that has a cooldown, not just ones added here - it's a separate per-item override on top of
    * whatever timer the item already uses.
    */
   private static LiteralArgumentBuilder<FabricClientCommandSource> cooldown() {
      return ClientCommandManager.literal("cooldown")
         .then(
            ClientCommandManager.literal("add")
               .then(
                  ClientCommandManager.argument("key", StringArgumentType.word())
                     .then(
                        ClientCommandManager.argument("seconds", IntegerArgumentType.integer(1))
                           .executes(ctx -> addCooldown(StringArgumentType.getString(ctx, "key"), IntegerArgumentType.getInteger(ctx, "seconds"), null))
                           .then(
                              ClientCommandManager.argument("trigger", StringArgumentType.word())
                                 .executes(
                                    ctx -> addCooldown(
                                       StringArgumentType.getString(ctx, "key"), IntegerArgumentType.getInteger(ctx, "seconds"), StringArgumentType.getString(ctx, "trigger")
                                    )
                                 )
                           )
                     )
               )
         )
         .then(
            ClientCommandManager.literal("addheld")
               .then(
                  ClientCommandManager.argument("seconds", IntegerArgumentType.integer(1))
                     .executes(ctx -> addHeldCooldown(IntegerArgumentType.getInteger(ctx, "seconds"), null))
                     .then(
                        ClientCommandManager.argument("trigger", StringArgumentType.word())
                           .executes(ctx -> addHeldCooldown(IntegerArgumentType.getInteger(ctx, "seconds"), StringArgumentType.getString(ctx, "trigger")))
                     )
               )
         )
         .then(ClientCommandManager.literal("remove").then(ClientCommandManager.argument("key", StringArgumentType.word()).executes(ctx -> removeCooldown(StringArgumentType.getString(ctx, "key")))))
         .then(ClientCommandManager.literal("removeheld").executes(ctx -> removeHeldCooldown()))
         .then(
            ClientCommandManager.literal("sound")
               .then(
                  ClientCommandManager.argument("key", StringArgumentType.word())
                     .then(
                        ClientCommandManager.argument("sound", StringArgumentType.greedyString())
                           .executes(ctx -> setCooldownSound(StringArgumentType.getString(ctx, "key"), StringArgumentType.getString(ctx, "sound")))
                     )
               )
         )
         .then(
            ClientCommandManager.literal("soundheld")
               .then(
                  ClientCommandManager.argument("sound", StringArgumentType.greedyString())
                     .executes(ctx -> setHeldCooldownSound(StringArgumentType.getString(ctx, "sound")))
               )
         )
         .then(ClientCommandManager.literal("list").executes(ctx -> listCooldowns()));
   }

   private static int addCooldown(String key, int seconds, String triggerName) {
      CooldownRules.Trigger trigger = parseTrigger(triggerName);
      if (trigger == null) {
         ChatOutput.info("Unknown trigger '" + triggerName + "'. Try one of: " + triggerNames() + ".");
         return 1;
      }

      CustomCooldownStore.add(key, seconds * 1000L, trigger);
      CooldownRules.loadLocalFile();
      ChatOutput.info(
         "Added a " + seconds + "s cooldown for '" + key + "' (" + trigger.name().toLowerCase(Locale.ROOT) + "). Undo with /buddy cooldown remove " + key + ", or /buddy cooldown removeheld while holding it."
      );
      return 1;
   }

   private static int addHeldCooldown(int seconds, String triggerName) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null) {
         return 1;
      }

      ItemStack stack = mc.player.getMainHandStack();
      if (stack.isEmpty()) {
         ChatOutput.info("Hold the item you want a cooldown on first, or use /buddy cooldown add <key> <seconds>.");
         return 1;
      }

      return addCooldown(SuiteItemIdUtil.getBestId(stack), seconds, triggerName);
   }

   private static int removeCooldown(String key) {
      if (CustomCooldownStore.remove(key)) {
         CooldownRules.loadLocalFile();
         ChatOutput.info("Removed the cooldown rule for '" + key + "'.");
      } else {
         ChatOutput.info("No custom cooldown rule for '" + key + "'. Check /buddy cooldown list.");
      }

      return 1;
   }

   private static int removeHeldCooldown() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null) {
         return 1;
      }

      ItemStack stack = mc.player.getMainHandStack();
      if (stack.isEmpty()) {
         ChatOutput.info("Hold the item you want to remove a cooldown from first, or use /buddy cooldown remove <key>.");
         return 1;
      }

      return removeCooldown(SuiteItemIdUtil.getBestId(stack));
   }

   private static int listCooldowns() {
      List<CustomCooldownStore.Entry> entries = CustomCooldownStore.entries();
      if (entries.isEmpty()) {
         ChatOutput.info("No custom cooldown rules yet. /buddy cooldown addheld <seconds> uses whatever's in your hand, or add <key> <seconds> for a specific item key.");
      } else {
         ChatOutput.info("Your custom cooldown rules:");
         for (CustomCooldownStore.Entry e : entries) {
            ChatOutput.info("  " + e.id() + " - " + e.ms() / 1000L + "s (" + e.trigger().toLowerCase(Locale.ROOT) + ")");
         }
      }

      Map<String, String> sounds = CooldownSoundStore.entries();
      if (!sounds.isEmpty()) {
         ChatOutput.info("Custom ready sounds:");
         for (Map.Entry<String, String> e : sounds.entrySet()) {
            ChatOutput.info("  " + e.getKey() + " -> " + e.getValue());
         }
      }

      return 1;
   }

   /**
    * Sets or clears which sound plays when a specific item's cooldown ends - works on any item with a cooldown,
    * not just ones added with {@code /buddy cooldown add}. "default" or "clear" removes the override.
    */
   private static int setCooldownSound(String key, String soundId) {
      if (soundId.equalsIgnoreCase("default") || soundId.equalsIgnoreCase("clear")) {
         if (CooldownSoundStore.clear(key)) {
            ChatOutput.info("'" + key + "' is back to the default ready jingle.");
         } else {
            ChatOutput.info("'" + key + "' didn't have a custom sound set.");
         }

         return 1;
      }

      String normalized = soundId.contains(":") ? soundId : "minecraft:" + soundId;
      Identifier id;
      try {
         id = Identifier.of(normalized);
      } catch (Exception e) {
         ChatOutput.info("'" + soundId + "' isn't a valid sound id.");
         return 1;
      }

      SoundEvent sound = Registries.SOUND_EVENT.get(id);
      if (sound == null || sound == SoundEvents.INTENTIONALLY_EMPTY) {
         ChatOutput.info("No sound called '" + normalized + "'. Try something like block.note_block.bell or entity.experience_orb.pickup.");
         return 1;
      }

      CooldownSoundStore.set(key, normalized);
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.world != null) {
         mc.world.playSound(mc.player, mc.player.getX(), mc.player.getY(), mc.player.getZ(), sound, SoundCategory.MASTER, SuiteConfig.INSTANCE.CooldownsConfig.completeSoundVolume, 1.0F);
      }

      ChatOutput.info("'" + key + "' now plays " + normalized + " when it's ready. /buddy cooldown sound " + key + " default to undo.");
      return 1;
   }

   private static int setHeldCooldownSound(String soundId) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null) {
         return 1;
      }

      ItemStack stack = mc.player.getMainHandStack();
      if (stack.isEmpty()) {
         ChatOutput.info("Hold the item you want to set a sound for first, or use /buddy cooldown sound <key> <sound>.");
         return 1;
      }

      return setCooldownSound(SuiteItemIdUtil.getBestId(stack), soundId);
   }

   private static CooldownRules.Trigger parseTrigger(String name) {
      if (name == null || name.isBlank()) {
         return CooldownRules.Trigger.RIGHT_CLICK;
      }

      try {
         return CooldownRules.Trigger.valueOf(name.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   private static String triggerNames() {
      StringBuilder sb = new StringBuilder();
      for (CooldownRules.Trigger t : CooldownRules.Trigger.values()) {
         if (sb.length() > 0) {
            sb.append(", ");
         }

         sb.append(t.name().toLowerCase(Locale.ROOT));
      }

      return sb.toString();
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
