package org.blossomsuite.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.hud.HudEditScreen;
import org.blossomsuite.core.ui.BuddyKeysScreen;
import org.blossomsuite.core.ui.ChatSearchScreen;
import org.blossomsuite.core.ui.EmoteWheelScreen;
import org.blossomsuite.core.ui.InventorySlotLocksScreen;
import org.blossomsuite.core.ui.PlayerListScreen;
import org.blossomsuite.core.ui.SuiteSettingsScreen;

/** Opens BlossomBuddy's screens and features in a real client and screenshots them, so rendering errors show up as a failed run. */
public class BuddySmokeTest implements FabricClientGameTest {
   @Override
   public void runTest(ClientGameTestContext context) {
      context.getInput().resizeWindow(1280, 720);
      context.waitForScreen(TitleScreen.class);
      context.takeScreenshot("00-title");

      // every settings tab, opened from the main menu (where no world is loaded yet)
      context.setScreen(() -> new SuiteSettingsScreen(Minecraft.getInstance().screen));
      context.waitTicks(5);
      int tabs = context.computeOnClient(mc -> tabs(mc.screen).size());
      System.out.println("[SMOKE] settings tabs: " + tabs);
      for (int i = 0; i < tabs; i++) {
         final int index = i;
         context.runOnClient(mc -> selectTab(mc.screen, index));
         context.waitTicks(3);
         context.takeScreenshot(String.format("01-settings-tab-%02d", i));
      }

      screen(context, "02-hudedit", HudEditScreen::new);
      screen(context, "02-keys", () -> new BuddyKeysScreen(null));
      screen(context, "02-search", () -> new ChatSearchScreen(null));
      screen(context, "02-slotlocks", () -> new InventorySlotLocksScreen(null));
      context.setScreen(() -> null);
      context.waitTicks(2);

      // a world, with the features switched on (they stay off in singleplayer by default)
      context.runOnClient(mc -> {
         SuiteConfig.INSTANCE.activationMode = SuiteConfig.ActivationMode.ON;
         FeatureConfig.INSTANCE.scoreboard.rounded = true;
      });
      try (TestSingleplayerContext world = context.worldBuilder().create()) {
         world.getClientLevel().waitForChunksRender();
         context.waitTicks(20);
         context.takeScreenshot("10-world");

         // the flip emotes, seen from the front at several moments of the move (gui hidden so the pictures are clean)
         context.runOnClient(mc -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            mc.options.hideGui = true;
         });
         for (org.blossomsuite.core.emote.Emote emote : java.util.List.of(org.blossomsuite.core.emote.Emote.BACKFLIP, org.blossomsuite.core.emote.Emote.CARTWHEEL)) {
            for (int i = 0; i < 8; i++) {
               final long offsetMs = 100L + i * 270L;
               context.runOnClient(mc -> org.blossomsuite.core.emote.EmoteState.INSTANCE.startLooping(mc.player.getUUID(), emote, System.currentTimeMillis() - offsetMs));
               context.waitTicks(1);
               context.takeScreenshot("emote-" + emote.id() + "-" + i);
            }

            context.runOnClient(mc -> org.blossomsuite.core.emote.EmoteState.INSTANCE.stop(mc.player.getUUID()));
         }

         context.runOnClient(mc -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.options.hideGui = false;
         });

         // the lightmap mixin: an enclosed dark room, without and with full bright
         world.getServer().runCommand("fill ~-3 ~-1 ~-3 ~3 ~4 ~3 stone hollow");
         world.getServer().runCommand("time set midnight");
         context.waitTicks(30);
         context.takeScreenshot("11-darkroom-normal");
         context.runOnClient(mc -> FeatureConfig.INSTANCE.render.fullBright = true);
         context.waitTicks(10);
         context.takeScreenshot("12-darkroom-fullbright");
         context.runOnClient(mc -> FeatureConfig.INSTANCE.render.fullBright = false);
         world.getServer().runCommand("fill ~-3 ~ ~-3 ~3 ~4 ~3 air");
         world.getServer().runCommand("time set noon");

         // first-person hand: item held, then shrunk by the hands mixin
         world.getServer().runCommand("give @s diamond_pickaxe");
         context.waitTicks(30);
         context.takeScreenshot("20-hand-normal");
         context.runOnClient(mc -> {
            FeatureConfig.INSTANCE.hands.smallHands = true;
            FeatureConfig.INSTANCE.hands.smallHandsScale = 0.4F;
         });
         context.waitTicks(5);
         context.takeScreenshot("21-hand-small");
         context.runOnClient(mc -> FeatureConfig.INSTANCE.hands.smallHands = false);

         // crosshair tint + shape, and the target block outline colour
         world.getServer().runCommand("setblock ~ ~1 ~-3 gold_block");
         context.getInput().lookAt(180.0F, 0.0F);
         context.runOnClient(mc -> {
            QolConfig q = SuiteConfig.INSTANCE.QolConfig;
            q.crosshairTintEnabled = true;
            q.crosshairShape = QolConfig.CrosshairShape.PLUS;
            q.targetBlockOutlineEnabled = true;
         });
         context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
         context.waitTicks(10);
         context.takeScreenshot("30-crosshair-outline");

         // gizmo overlay: the mining track guide lines along a row of blocks
         world.getServer().runCommand("fill ~2 ~ ~-12 ~2 ~1 ~-5 stone");
         context.runOnClient(mc -> {
            QolConfig q = SuiteConfig.INSTANCE.QolConfig;
            q.miningTrackIndicator = true;
            q.miningTrackDir = "north";
            q.miningTrackCoord = mc.player.blockPosition().getX() + 2;
            q.miningTrackLineThickness = 3;
         });
         context.waitTicks(10);
         context.takeScreenshot("31-mining-track");


         // scoreboard sidebar (our own rounded one), a boss bar and some chat
         world.getServer().runCommand("scoreboard objectives add smoke dummy \"Smoke test\"");
         world.getServer().runCommand("scoreboard objectives setdisplay sidebar smoke");
         world.getServer().runCommand("scoreboard players set Alice smoke 7");
         world.getServer().runCommand("scoreboard players set Bob smoke 3");
         world.getServer().runCommand("bossbar add smoke \"A boss bar\"");
         world.getServer().runCommand("bossbar set minecraft:smoke players @a");
         world.getServer().runCommand("tellraw @a \"hello from the smoke test\"");
         context.runOnClient(mc -> mc.player.connection.sendCommand("buddy privacy"));
         context.waitTicks(20);
         context.takeScreenshot("40-sidebar-bossbar-chat");

         // a chat line that carries its own item hover (as CoreProtect inspect does): hovering it must show the item, not a timestamp
         world.getServer().runCommand("tellraw @a {\"text\":\"CoreProtect - hover this: added x1 dark prismarine\",\"hover_event\":{\"action\":\"show_item\",\"id\":\"minecraft:dark_prismarine\"}}");
         context.waitTicks(10);
         context.setScreen(() -> new net.minecraft.client.gui.screens.ChatScreen("", false));
         context.waitTicks(5);
         context.getInput().setCursorPos(300.0, 587.0);
         context.waitTicks(5);
         context.takeScreenshot("45-chat-hover-item");
         context.setScreen(() -> null);

         // the secondary chat window fading away when quiet, and coming back with a new line
         context.runOnClient(mc -> {
            FeatureConfig.INSTANCE.chat.show = true;
            FeatureConfig.INSTANCE.chat.fadeWhenIdle = true;
            FeatureConfig.INSTANCE.chat.fadeSeconds = 3;
         });
         world.getServer().runCommand("tellraw @a \"Alice and Bob are now married (secondary chat line one)\"");
         context.waitTicks(20);
         context.takeScreenshot("46-secondary-visible");
         context.waitTicks(130);
         context.takeScreenshot("47-secondary-faded");
         world.getServer().runCommand("tellraw @a \"Alice and Bob got married again (secondary chat line two)\"");
         context.waitTicks(10);
         context.takeScreenshot("48-secondary-back");

         // the staff sidebar: "Visibility: Vanished" is the server's own word on vanish, and "TPS: 12.5" its own TPS.
         // BlossomCraft draws its lines as team prefixes on blank entries, so that is what is built here.
         world.getServer().runCommand("team add tv");
         world.getServer().runCommand("team modify tv prefix {\"text\":\"VISIBILITY: Vanished\"}");
         world.getServer().runCommand("team join tv visline");
         world.getServer().runCommand("scoreboard players set visline smoke 2");
         world.getServer().runCommand("team add tt");
         world.getServer().runCommand("team modify tt prefix {\"text\":\"TPS: 12.5\"}");
         world.getServer().runCommand("team join tt tpsline");
         world.getServer().runCommand("scoreboard players set tpsline smoke 1");
         context.waitTicks(40);
         boolean sawVanished = context.computeOnClient(mc -> org.blossomsuite.core.chat.VanishState.vanished);
         double sawTps = context.computeOnClient(mc -> org.blossomsuite.core.tps.TpsTracker.INSTANCE.tps(System.currentTimeMillis()));
         System.out.println("[SMOKE] sidebar vanished=" + sawVanished + " tps=" + sawTps);
         if (!sawVanished) {
            throw new AssertionError("the sidebar said Vanished but the mod did not notice");
         }

         if (Math.abs(sawTps - 12.5) > 0.01) {
            throw new AssertionError("the mod should show the sidebar's TPS of 12.5, not " + sawTps);
         }

         world.getServer().runCommand("team modify tv prefix {\"text\":\"VISIBILITY: Visible\"}");
         context.waitTicks(40);
         boolean stillVanished = context.computeOnClient(mc -> org.blossomsuite.core.chat.VanishState.vanished);
         if (stillVanished) {
            throw new AssertionError("the sidebar said Visible but the mod still thinks vanished");
         }

         boolean staffHidden = context.computeOnClient(mc -> org.blossomsuite.core.chat.VanishState.hiddenFromLists());
         if (!staffHidden) {
            throw new AssertionError("staff must stay off Discord and the list even while visible");
         }

         context.runOnClient(mc -> FeatureConfig.INSTANCE.staff.showWhenStaff = true);
         boolean shownByChoice = context.computeOnClient(mc -> !org.blossomsuite.core.chat.VanishState.hiddenFromLists());
         if (!shownByChoice) {
            throw new AssertionError("a staff member who chose to show while visible should be shown");
         }

         context.runOnClient(mc -> {
            FeatureConfig.INSTANCE.staff.showWhenStaff = false;
            org.blossomsuite.core.chat.VanishState.staffSidebar = false;
         });
         world.getServer().runCommand("scoreboard players reset visline smoke");
         world.getServer().runCommand("scoreboard players reset tpsline smoke");
         world.getServer().runCommand("team remove tv");
         world.getServer().runCommand("team remove tt");

         // secondary chat has to keep working for someone in spectator mode (staff use it a lot)
         String modeBefore = context.computeOnClient(mc -> mc.gameMode.getPlayerMode().getName());
         world.getServer().runCommand("tellraw @a \"Alice and Bob got married a third time (spectator line)\"");
         world.getServer().runCommand("gamemode spectator @a");
         context.waitTicks(15);
         world.getServer().runCommand("tellraw @a \"Alice and Bob got married a fourth time (spectator line two)\"");
         context.waitTicks(15);
         context.takeScreenshot("49-secondary-spectator");
         world.getServer().runCommand("gamemode " + modeBefore + " @a");
         context.waitTicks(10);

         // the dungeon timer: chat lines from the server start it, a teammate dying changes nothing, finishing starts the cooldown
         context.runOnClient(mc -> {
            FeatureConfig.INSTANCE.chat.show = false;
            org.blossomsuite.core.config.SuiteConfig.INSTANCE.DungeonConfig.cooldownEndsByServer.clear();
         });
         world.getServer().runCommand("tellraw @a \"[Dungeons] All players are ready! Entering the dungeon!\"");
         context.waitTicks(40);
         world.getServer().runCommand("tellraw @a \"[Dungeons] Bob lost all their lives!\"");
         context.waitTicks(20);
         boolean stillRunning = context.computeOnClient(mc -> org.blossomsuite.core.dungeons.DungeonState.inRun());
         if (!stillRunning) {
            throw new AssertionError("a teammate dying must not end your dungeon run");
         }

         context.takeScreenshot("70-dungeon-running");
         world.getServer().runCommand("tellraw @a \"AKUMA HAS FALLEN!\"");
         context.waitTicks(20);
         context.takeScreenshot("71-dungeon-cooldown");

         // chat notify rings when a keyword shows up in a chat line that arrives through the normal chat events
         context.runOnClient(mc -> {
            FeatureConfig.INSTANCE.chatNotify.enabled = true;
            FeatureConfig.INSTANCE.chatNotify.keywords = "Claude";
         });
         world.getServer().runCommand("tellraw @a \"Somebody said Claude in chat\"");
         context.waitTicks(10);
         long dinged = context.computeOnClient(mc -> org.blossomsuite.core.chat.ChatNotify.INSTANCE.lastDingAtMs());
         if (System.currentTimeMillis() - dinged > 30_000L) {
            throw new AssertionError("chat notify did not ring for a keyword in chat");
         }

         context.runOnClient(mc -> FeatureConfig.INSTANCE.chatNotify.enabled = false);

         // the server TPS panel: the time packets from the integrated server should give a reading near 20
         context.runOnClient(mc -> FeatureConfig.INSTANCE.tps.show = true);
         context.waitTicks(140);
         double tpsReading = context.computeOnClient(mc -> org.blossomsuite.core.tps.TpsTracker.INSTANCE.tps(System.currentTimeMillis()));
         System.out.println("[SMOKE] tps reading " + tpsReading);
         if (tpsReading < 5.0 || tpsReading > 20.0) {
            throw new AssertionError("expected a TPS reading near 20, got " + tpsReading);
         }

         context.takeScreenshot("72-tps");
         context.runOnClient(mc -> FeatureConfig.INSTANCE.tps.show = false);

         // the chest finder: open a real chest with something in it, close it, then ask where the diamonds are
         world.getServer().runCommand("setblock 0 -59 3 minecraft:chest");
         world.getServer().runCommand("item replace block 0 -59 3 container.0 with minecraft:diamond 5");
         world.getServer().runCommand("item replace block 0 -59 3 container.1 with minecraft:diamond 7");
         context.waitTicks(10);
         context.runOnClient(mc -> {
            net.minecraft.core.BlockPos chest = new net.minecraft.core.BlockPos(0, -59, 3);
            mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
               new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(chest), net.minecraft.core.Direction.UP, chest, false));
         });
         context.waitTicks(20);
         boolean chestOpen = context.computeOnClient(mc -> mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>);
         if (!chestOpen) {
            throw new AssertionError("the chest did not open");
         }

         context.setScreen(() -> null);
         context.waitTicks(5);
         int found = context.computeOnClient(mc -> org.blossomsuite.core.finder.ContainerFinder.INSTANCE.index().search("diamond", null).stream().mapToInt(h -> h.count()).sum());
         if (found != 12) {
            throw new AssertionError("the finder should remember 12 diamonds, remembered " + found);
         }

         context.getInput().lookAt(0.0F, 20.0F);
         context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
         context.runOnClient(mc -> mc.player.connection.sendCommand("buddy find diamond"));
         context.waitTicks(15);
         context.takeScreenshot("73-finder");

         // the session screen, with a session running
         context.setScreen(org.blossomsuite.core.ui.SessionScreen::new);
         context.waitTicks(10);
         context.takeScreenshot("74-session");
         context.setScreen(() -> null);

         // discord presence: with no Discord running it should say so and carry on
         context.runOnClient(mc -> {
            FeatureConfig.INSTANCE.discord.enabled = true;
            FeatureConfig.INSTANCE.discord.appId = "123456789012345678";
         });
         context.waitTicks(60);
         String discordStatus = context.computeOnClient(mc -> org.blossomsuite.core.discord.DiscordHook.INSTANCE.status());
         System.out.println("[SMOKE] discord status: " + discordStatus);
         // staff in vanish must not show on Discord
         String me = context.computeOnClient(mc -> mc.player.getName().getString());
         world.getServer().runCommand("tellraw @a \"Vanish for " + me + ": ENABLED\"");
         context.waitTicks(30);
         String vanishedStatus = context.computeOnClient(mc -> org.blossomsuite.core.discord.DiscordHook.INSTANCE.status());
         System.out.println("[SMOKE] discord status while vanished: " + vanishedStatus);
         if (!vanishedStatus.startsWith("Hidden while you are vanished")) {
            throw new AssertionError("discord presence should be hidden while vanished, status was: " + vanishedStatus);
         }

         world.getServer().runCommand("tellraw @a \"Vanish for " + me + ": DISABLED\"");
         context.waitTicks(30);
         context.runOnClient(mc -> FeatureConfig.INSTANCE.discord.enabled = false);

         // music controls: read whatever this computer is playing (only reads, never presses a button)
         context.runOnClient(mc -> {
            FeatureConfig.INSTANCE.music.enabled = true;
            FeatureConfig.INSTANCE.music.share = true;
         });
         context.waitTicks(200);
         String musicStatus = context.computeOnClient(mc -> org.blossomsuite.core.music.MusicService.INSTANCE.status());
         String musicShared = context.computeOnClient(mc -> org.blossomsuite.core.music.MusicService.INSTANCE.sharedText());
         System.out.println("[SMOKE] music status: " + musicStatus + " | would share: " + musicShared);
         context.takeScreenshot("75-music");
         context.runOnClient(mc -> {
            FeatureConfig.INSTANCE.music.enabled = false;
            FeatureConfig.INSTANCE.music.share = false;
         });
         context.waitTicks(25);

         // the profile screen with a level 55 profile exactly as the relay sends it (a dev script writes the file)
         String profileJson = System.getenv("BLOSSOMBUDDY_PROFILE_JSON");
         if (profileJson != null) {
            org.blossomsuite.core.board.ProfileModels.Profile preset = org.blossomsuite.core.util.JsonUtil.GSON.fromJson(
               readFile(profileJson), org.blossomsuite.core.board.ProfileModels.Profile.class);
            context.setScreen(() -> new org.blossomsuite.core.ui.ProfileScreen(preset));
            context.waitTicks(10);
            context.takeScreenshot("76-profile");
            context.getInput().setCursorPos(640.0, 420.0);
            context.waitTicks(3);
            context.takeScreenshot("77-profile-hover");
            context.setScreen(() -> null);
         }

         // the leaderboard window, reading from a throwaway local relay seeded with made-up players (started by the dev script)
         if (System.getenv("BLOSSOMBUDDY_SEEDED_RELAY") != null) {
            context.runOnClient(mc -> org.blossomsuite.core.state.SuiteState.INSTANCE.http.setBaseUrl("http://127.0.0.1:8799"));
            context.setScreen(org.blossomsuite.core.ui.LeaderboardScreen::new);
            context.waitTicks(40);
            context.takeScreenshot("60-leaderboard-money-all");
            context.runOnClient(mc -> choose(mc.screen, new org.blossomsuite.core.board.LeaderboardModels.Choice("overflow", "cherry", "week")));
            context.waitTicks(40);
            context.takeScreenshot("61-leaderboard-overflow-cherry-week");
            context.setScreen(() -> null);
         }

         // the screens that need a world
         screen(context, "50-playerlist", PlayerListScreen::new);
         screen(context, "50-emotewheel", EmoteWheelScreen::new);
         screen(context, "50-hudedit-in-world", HudEditScreen::new);
         context.setScreen(() -> null);
      }

      context.waitForScreen(TitleScreen.class);
      System.out.println("[SMOKE] done");
   }

   private static String readFile(String path) {
      try {
         return java.nio.file.Files.readString(java.nio.file.Path.of(path));
      } catch (java.io.IOException e) {
         throw new java.io.UncheckedIOException(e);
      }
   }

   private static void choose(Object screen, org.blossomsuite.core.board.LeaderboardModels.Choice choice) {
      try {
         java.lang.reflect.Method m = org.blossomsuite.core.ui.LeaderboardScreen.class.getDeclaredMethod("choose", org.blossomsuite.core.board.LeaderboardModels.Choice.class);
         m.setAccessible(true);
         m.invoke(screen, choice);
      } catch (ReflectiveOperationException e) {
         throw new IllegalStateException(e);
      }
   }

   private static void screen(ClientGameTestContext context, String name, Supplier<Screen> screen) {
      context.setScreen(screen);
      context.waitTicks(4);
      context.takeScreenshot(name);
   }

   @SuppressWarnings("unchecked")
   private static List<Object> tabs(Object screen) {
      try {
         Field f = SuiteSettingsScreen.class.getDeclaredField("tabs");
         f.setAccessible(true);
         return (List<Object>)f.get(screen);
      } catch (ReflectiveOperationException e) {
         throw new IllegalStateException(e);
      }
   }

   private static void selectTab(Object screen, int index) {
      try {
         Object tab = tabs(screen).get(index);
         for (Method m : SuiteSettingsScreen.class.getDeclaredMethods()) {
            if (m.getName().equals("selectTab") && m.getParameterCount() == 1) {
               m.setAccessible(true);
               m.invoke(screen, tab);
               return;
            }
         }

         throw new IllegalStateException("no selectTab");
      } catch (ReflectiveOperationException e) {
         throw new IllegalStateException(e);
      }
   }
}
