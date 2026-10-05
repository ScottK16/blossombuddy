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

         // the screens that need a world
         screen(context, "50-playerlist", PlayerListScreen::new);
         screen(context, "50-emotewheel", EmoteWheelScreen::new);
         screen(context, "50-hudedit-in-world", HudEditScreen::new);
         context.setScreen(() -> null);
      }

      context.waitForScreen(TitleScreen.class);
      System.out.println("[SMOKE] done");
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
