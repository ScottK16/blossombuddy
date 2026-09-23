package org.blossomsuite.core.ui;

import org.blossomsuite.core.config.CooldownsConfig;
import org.blossomsuite.core.config.RelayConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.cooldowns.CooldownsMode;
import java.util.Locale;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class CooldownsTab extends NestedSuiteTab {
   @Override
   public String titleKey() {
      return "suitecore.tab.cooldowns";
   }

   @Override
   public boolean isEnabled() {
      return true;
   }

   @Override
   public void setEnabled(boolean enabled) {
   }

   @Override
   protected void initializeSubTabs() {
      this.subTabs.add(new CooldownsTab.UiSubTab());
      this.subTabs.add(new CooldownsTab.TrackingSubTab());
      this.subTabs.add(new CooldownsTab.NotificationsSubTab());
      this.subTabs.add(new CooldownsTab.AltCooldownsSubTab());
      this.subTabs.add(new BuddyTabs.CooldownItems());
   }

   private int addToggleRow(SuiteSettingsScreen screen, int x, int w, int y, int rowH, String label, boolean enabled, String tooltip, Runnable onPress) {
      screen.addContentWidget(new HoverLabelWidget(x, y + 6, 180, 12, Text.literal(label), Tooltip.of(Text.literal(tooltip))));
      ButtonWidget button = StyledButton.of(Text.literal(enabled ? "ON" : "OFF"), b -> onPress.run()).dimensions(x + w - 80, y, 80, rowH).build();
      button.setTooltip(Tooltip.of(Text.literal(tooltip)));
      screen.addContentWidget(button);
      return y + 24;
   }

   private static String generateRelayId() {
      String name = "relay";
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.getSession() != null) {
         String username = client.getSession().getUsername();
         if (username != null && !username.isBlank()) {
            name = username;
         }
      }

      Random r = new Random();
      int a = 100 + r.nextInt(900);
      int b = 100 + r.nextInt(900);
      int c = 100 + r.nextInt(900);
      return name + "-" + a + "-" + b + "-" + c;
   }

   private int addButtonRow(SuiteSettingsScreen screen, int x, int w, int y, int rowH, String label, String buttonText, String tooltip, Runnable onPress) {
      screen.addContentWidget(new HoverLabelWidget(x, y + 6, 180, 12, Text.literal(label), Tooltip.of(Text.literal(tooltip))));
      ButtonWidget button = StyledButton.of(Text.literal(buttonText), b -> onPress.run()).dimensions(x + w - 120, y, 120, rowH).build();
      button.setTooltip(Tooltip.of(Text.literal(tooltip)));
      screen.addContentWidget(button);
      return y + 24;
   }

   private static CooldownsMode nextMode(CooldownsMode current) {
      CooldownsMode[] all = CooldownsMode.values();
      return all[(current.ordinal() + 1) % all.length];
   }

   private static String prettyMode(CooldownsMode mode) {
      String raw = mode.name().toLowerCase(Locale.ROOT).replace('_', ' ');
      String[] parts = raw.split(" ");
      StringBuilder sb = new StringBuilder();

      for (String p : parts) {
         if (!p.isEmpty()) {
            if (sb.length() > 0) {
               sb.append(' ');
            }

            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
               sb.append(p.substring(1));
            }
         }
      }

      return sb.toString();
   }

   private static double thresholdToSlider(int secs) {
      int min = 0;
      int max = 1000;
      secs = Math.max(min, Math.min(max, secs));
      return (double)(secs - min) / (max - min);
   }

   private static int sliderToThreshold(double value) {
      int min = 0;
      int max = 1000;
      value = Math.max(0.0, Math.min(1.0, value));
      int secs = (int)Math.round(min + value * (max - min));
      return secs <= 2 ? 0 : (secs + 2) / 5 * 5;
   }

   private static String prettyInventoryHudMode(CooldownsConfig.InventoryHudMode mode) {
      if (mode == null) {
         return "Only Usable";
      }

      return switch (mode) {
         case ALL_INVENTORY -> "All Inventory";
         case ONLY_USABLE -> "Only Usable";
      };
   }

   private static String prettyHotbarIndicatorStyle(CooldownsConfig.HotbarIndicatorStyle style) {
      if (style == null) {
         return "Top Bar";
      }

      return switch (style) {
         case TOP_BAR -> "Top Bar";
         case VANILLA_SWIPE -> "Vanilla Swipe";
      };
   }

   private static String prettyCompleteMessageLocation(CooldownsConfig.CompleteMessageLocation location) {
      if (location == null) {
         return "Chat";
      }

      return switch (location) {
         case CHAT -> "Chat";
         case SCREEN -> "Screen";
      };
   }

   private final class AltCooldownsSubTab implements SuiteSubTab {
      @Override
      public String titleKey() {
         return "suitecore.tab.cooldowns.alts";
      }

      @Override
      public void build(SuiteSettingsScreen screen, int contentTopOffset) {
         RelayConfig relay = SuiteConfig.INSTANCE.RelayConfig;
         int x = screen.contentX();
         int w = screen.contentW();
         int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
         int rowH = 20;
         int gapX = 6;
         screen.addContentWidget(
            new HoverLabelWidget(x, y, 160, 12, Text.literal("Alt Cooldowns"), Tooltip.of(Text.literal("Share cooldown data between linked clients.")))
         );
         y += 14;
         screen.addContentWidget(
            new HoverLabelWidget(
               x, y + 6, 180, 12, Text.literal("Link ID"), Tooltip.of(Text.literal("Clients using the same Link ID can relay cooldown data."))
            )
         );
         int generateW = 80;
         int fieldW = 170;
         int fieldX = x + w - generateW - 6 - fieldW;
         int generateX = x + w - generateW;
         TextFieldWidget linkField = new TextFieldWidget(screen.getTextRenderer(), fieldX, y, fieldW, 20, Text.empty());
         linkField.setMaxLength(128);
         linkField.setText(relay.linkId == null ? "" : relay.linkId);
         linkField.setTooltip(Tooltip.of(Text.literal("Editable relay link id.")));
         linkField.setChangedListener(relay::setLinkId);
         screen.addContentWidget(linkField);
         ButtonWidget generateButton = StyledButton.of(Text.literal("Generate"), b -> {
            String generated = CooldownsTab.generateRelayId();
            relay.setLinkId(generated);
            screen.rebuildPreserveScroll();
         }).dimensions(generateX, y, generateW, 20).build();
         generateButton.setTooltip(Tooltip.of(Text.literal("Generates a new relay link id using your current Minecraft name.")));
         screen.addContentWidget(generateButton);
         y += 24;
         y = CooldownsTab.this.addToggleRow(
            screen, x, w, y, 20, "Publish Cooldowns", relay.publishCooldowns, "Publishes your cooldown data to the current relay link.", () -> {
               relay.togglePublishCooldowns();
               screen.rebuildPreserveScroll();
            }
         );
         CooldownsTab.this.addToggleRow(
            screen, x, w, y, 20, "Subscribe Cooldowns", relay.subscribeCooldowns, "Receives cooldown data from the current relay link.", () -> {
               relay.toggleSubscribeCooldowns();
               screen.rebuildPreserveScroll();
            }
         );
      }

      @Override
      public void removed() {
      }

      @Override
      public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
         return contentTopOffset + 14 + 24 + 24 + 24 + 30;
      }
   }

   private final class NotificationsSubTab implements SuiteSubTab {
      @Override
      public String titleKey() {
         return "suitecore.tab.cooldowns.notifications";
      }

      @Override
      public void build(SuiteSettingsScreen screen, int contentTopOffset) {
         final CooldownsConfig cfg = SuiteConfig.INSTANCE.CooldownsConfig;
         int x = screen.contentX();
         int w = screen.contentW();
         int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
         int rowH = 20;
         screen.addContentWidget(
            new HoverLabelWidget(x, y, 160, 12, Text.literal("Notifications"), Tooltip.of(Text.literal("Notification settings for completed cooldowns.")))
         );
         y += 14;
         y = CooldownsTab.this.addToggleRow(screen, x, w, y, 20, "Complete Sound", cfg.completeSound, "Plays a sound when a cooldown completes.", () -> {
            cfg.toggleCompleteSound();
            screen.rebuildPreserveScroll();
         });
         if (cfg.completeSound) {
            int labelY = y + 2;
            int sliderY = y + 16;
            screen.addContentWidget(
               new HoverLabelWidget(x, labelY, 180, 12, Text.literal("Sound Volume"), Tooltip.of(Text.literal("Volume for the cooldown-complete sound.")))
            );
            SliderWidget volumeSlider = new StyledSlider(x, sliderY, w, 20, Text.empty(), cfg.completeSoundVolume) {
               {
                  this.updateMessage();
                  this.setTooltip(Tooltip.of(Text.literal("Current completion sound volume.")));
               }

               @Override
               protected void updateMessage() {
                  int pct = Math.round((float)this.value * 100.0F);
                  this.setMessage(Text.literal(pct + "%"));
               }

               @Override
               protected void applyValue() {
                  cfg.setCompleteSoundVolume((float)this.value);
               }
            };
            screen.addContentWidget(volumeSlider);
            y = sliderY + 28;
         }

         y = CooldownsTab.this.addToggleRow(screen, x, w, y, 20, "Complete Message", cfg.completeMessage, "Shows a message when a cooldown completes.", () -> {
            cfg.toggleCompleteMessage();
            screen.rebuildPreserveScroll();
         });
         if (cfg.completeMessage) {
            y = CooldownsTab.this.addButtonRow(
               screen,
               x,
               w,
               y,
               20,
               "Message Location",
               CooldownsTab.prettyCompleteMessageLocation(cfg.completeMessageLocation),
               "Chooses whether completed cooldown messages appear in chat or in the screen notice spot.",
               () -> {
                  cfg.cycleCompleteMessageLocation();
                  screen.rebuildPreserveScroll();
               }
            );
         }

         int thresholdLabelY = y + 2;
         int thresholdSliderY = y + 16;
         screen.addContentWidget(
            new HoverLabelWidget(
               x,
               thresholdLabelY,
               220,
               12,
               Text.literal("Cooldown Completion Threshold"),
               Tooltip.of(Text.literal("Only notify for cooldowns at or above this length.\n0 = no threshold."))
            )
         );
         SliderWidget thresholdSlider = new StyledSlider(x, thresholdSliderY, w, 20, Text.empty(), CooldownsTab.thresholdToSlider(cfg.completeThresholdInSecs)) {
            {
               this.updateMessage();
               this.setTooltip(Tooltip.of(Text.literal("Minimum cooldown length required before completion alerts are shown.")));
            }

            @Override
            protected void updateMessage() {
               int secs = CooldownsTab.sliderToThreshold(this.value);
               this.setMessage(Text.literal(secs == 0 ? "Off" : secs + " sec"));
            }

            @Override
            protected void applyValue() {
               cfg.setCompleteThresholdInSecs(CooldownsTab.sliderToThreshold(this.value));
            }
         };
         screen.addContentWidget(thresholdSlider);
      }

      @Override
      public void removed() {
      }

      @Override
      public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
         CooldownsConfig cfg = SuiteConfig.INSTANCE.CooldownsConfig;
         return contentTopOffset + 14 + 24 + (cfg.completeSound ? 44 : 0) + 24 + (cfg.completeMessage ? 24 : 0) + 44 + 30;
      }
   }

   private final class TrackingSubTab implements SuiteSubTab {
      @Override
      public String titleKey() {
         return "suitecore.tab.cooldowns.tracking";
      }

      @Override
      public void build(SuiteSettingsScreen screen, int contentTopOffset) {
         CooldownsConfig cfg = SuiteConfig.INSTANCE.CooldownsConfig;
         int x = screen.contentX();
         int w = screen.contentW();
         int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
         int rowH = 20;
         screen.addContentWidget(
            new HoverLabelWidget(
               x,
               y,
               140,
               12,
               Text.literal("Tracking"),
               Tooltip.of(Text.literal("Choose which equipment and inventory slots participate in cooldown tracking."))
            )
         );
         y += 14;
         y = CooldownsTab.this.addToggleRow(
            screen,
            x,
            w,
            y,
            20,
            "Track Inventory",
            cfg.trackInventory,
            "Tracks cooldown rules that require an item to be in inventory slots (beyond the hotbar).",
            () -> {
               cfg.toggleTrackInventory();
               screen.rebuildPreserveScroll();
            }
         );
         if (cfg.trackInventory) {
            y = CooldownsTab.this.addButtonRow(
               screen,
               x,
               w,
               y,
               20,
               "Inventory HUD",
               CooldownsTab.prettyInventoryHudMode(cfg.inventoryHudMode),
               "Controls whether the cooldown HUD shows all inventory cooldown items, or only the ones that are currently usable.",
               () -> {
                  cfg.cycleInventoryHudMode();
                  screen.rebuildPreserveScroll();
               }
            );
         }

         y = CooldownsTab.this.addToggleRow(
            screen, x, w, y, 20, "Track Offhand", cfg.trackOffhand, "Tracks cooldown rules and usage that involve the offhand item.", () -> {
               cfg.toggleTrackOffhand();
               screen.rebuildPreserveScroll();
            }
         );
         CooldownsTab.this.addToggleRow(
            screen, x, w, y, 20, "Track Armor", cfg.trackArmor, "Tracks cooldown rules that are bound to armor slots (head/chest/legs/feet).", () -> {
               cfg.toggleTrackArmor();
               screen.rebuildPreserveScroll();
            }
         );
      }

      @Override
      public void removed() {
      }

      @Override
      public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
         CooldownsConfig cfg = SuiteConfig.INSTANCE.CooldownsConfig;
         return contentTopOffset + 14 + 72 + (cfg.trackInventory ? 24 : 0) + 30;
      }
   }

   private final class UiSubTab implements SuiteSubTab {
      @Override
      public String titleKey() {
         return "suitecore.tab.cooldowns.ui";
      }

      @Override
      public void build(SuiteSettingsScreen screen, int contentTopOffset) {
         CooldownsConfig cfg = SuiteConfig.INSTANCE.CooldownsConfig;
         int x = screen.contentX();
         int w = screen.contentW();
         int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
         int rowH = 20;
         screen.addContentWidget(
            new HoverLabelWidget(x, y, 120, 12, Text.literal("HUD Options"), Tooltip.of(Text.literal("Controls how cooldowns are shown on screen.")))
         );
         y += 14;
         y = CooldownsTab.this.addToggleRow(screen, x, w, y, 20, "Show HUD", cfg.showHud, "Shows or hides the cooldown HUD overlay.", () -> {
            cfg.toggleShowHud();
            screen.rebuildPreserveScroll();
         });
         y = CooldownsTab.this.addToggleRow(
            screen, x, w, y, 20, "Show Hotbar", cfg.showHotbar, "Shows cooldown indicators on the hotbar when available.", () -> {
               cfg.toggleShowHotbar();
               screen.rebuildPreserveScroll();
            }
         );
         if (cfg.showHotbar) {
            y = CooldownsTab.this.addButtonRow(
               screen,
               x,
               w,
               y,
               20,
               "Hotbar Indicator",
               CooldownsTab.prettyHotbarIndicatorStyle(cfg.hotbarIndicatorStyle),
               "Switches between the current top bar and a vanilla-style cooldown swipe over the item.",
               () -> {
                  cfg.cycleHotbarIndicatorStyle();
                  screen.rebuildPreserveScroll();
               }
            );
         }

         y = CooldownsTab.this.addButtonRow(screen, x, w, y, 20, "Mode", CooldownsTab.prettyMode(cfg.mode), "Cycles through cooldown display modes.", () -> {
            cfg.setCooldownsHudMode(CooldownsTab.nextMode(cfg.mode));
            screen.rebuildPreserveScroll();
         });
         CooldownsTab.this.addToggleRow(
            screen,
            x,
            w,
            y,
            20,
            "Legacy HUD",
            cfg.legacyActiveOnly,
            "When enabled, only shows items while they are actively on cooldown (hides READY rows).",
            () -> {
               cfg.toggleLegacyActiveOnly();
               screen.rebuildPreserveScroll();
            }
         );
      }

      @Override
      public void removed() {
      }

      @Override
      public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
         CooldownsConfig cfg = SuiteConfig.INSTANCE.CooldownsConfig;
         return contentTopOffset + 14 + (4 + (cfg.showHotbar ? 1 : 0)) * 24 + 30;
      }
   }
}
