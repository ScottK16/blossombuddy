package org.blossomsuite.core.ui;

import org.blossomsuite.core.chat.ChatModeProbe;
import org.blossomsuite.core.chat.StaffChatState;
import org.blossomsuite.core.config.ChatConfig;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.SuiteConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class ChatTab extends NestedSuiteTab {
   private TextFieldWidget advertisementMessageField;
   private TextFieldWidget welcomeMessageField;
   private TextFieldWidget advertiserProfileNameField;
   private TextFieldWidget pinataBalanceThresholdField;

   @Override
   public String titleKey() {
      return "suitecore.tab.chat";
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
      this.subTabs.add(new ChatTab.HudSubTab());
      this.subTabs.add(new ChatTab.FiltersSubTab());
      this.subTabs.add(new ChatTab.AdvertisementsSubTab());
      this.subTabs.add(new ChatTab.StaffSubTab());
   }

   @Override
   protected void ensureInitialized() {
      super.ensureInitialized();
      this.syncStaffSubTab();
   }

   private void syncStaffSubTab() {
      ChatTab.StaffSubTab existing = null;

      for (SuiteSubTab tab : this.subTabs) {
         if (tab instanceof ChatTab.StaffSubTab staffSubTab) {
            existing = staffSubTab;
            break;
         }
      }

      if (existing == null) {
         this.subTabs.add(new ChatTab.StaffSubTab());
      }
   }

   @Override
   public void removed() {
      super.removed();
      this.advertisementMessageField = null;
      this.welcomeMessageField = null;
      this.advertiserProfileNameField = null;
   }

   private static boolean staffHudActive(ChatConfig cfg) {
      return cfg.trackStaffChat && cfg.showStaffHud && StaffChatState.isStaffMember;
   }

   private void clearTextFields() {
      this.advertisementMessageField = null;
      this.welcomeMessageField = null;
      this.advertiserProfileNameField = null;
      this.pinataBalanceThresholdField = null;
   }

   private void renderCounter(SuiteSettingsScreen screen, DrawContext ctx, TextFieldWidget field, String helperText, int maxLength) {
      if (field != null) {
         int baseX = field.getX();
         int baseY = field.getY() + field.getHeight() + 4;
         int width = field.getWidth();
         ctx.drawTextWithShadow(screen.getTextRenderer(), Text.literal(helperText), baseX, baseY, -7829368);
         String counterStr = field.getText().length() + " / " + maxLength;
         int counterWidth = screen.getTextRenderer().getWidth(counterStr);
         ctx.drawTextWithShadow(screen.getTextRenderer(), Text.literal(counterStr), baseX + width - counterWidth, baseY, -5592406);
      }
   }

   private int addToggleRow(SuiteSettingsScreen screen, int x, int w, int y, int rowH, String label, boolean enabled, String tooltip, Runnable onPress) {
      screen.addContentWidget(new HoverLabelWidget(x, y + 6, 220, 12, Text.literal(label), Tooltip.of(Text.literal(tooltip))));
      ButtonWidget button = StyledButton.of(Text.literal(enabled ? "ON" : "OFF"), b -> onPress.run()).dimensions(x + w - 80, y, 80, rowH).build();
      button.setTooltip(Tooltip.of(Text.literal(tooltip)));
      screen.addContentWidget(button);
      return y + 24;
   }

   private int addButtonRow(SuiteSettingsScreen screen, int x, int w, int y, int rowH, String label, String buttonText, String tooltip, Runnable onPress) {
      screen.addContentWidget(new HoverLabelWidget(x, y + 6, 180, 12, Text.literal(label), Tooltip.of(Text.literal(tooltip))));
      ButtonWidget button = StyledButton.of(Text.literal(buttonText), b -> onPress.run()).dimensions(x + w - 120, y, 120, rowH).build();
      button.setTooltip(Tooltip.of(Text.literal(tooltip)));
      screen.addContentWidget(button);
      return y + 24;
   }

   private int addIntField(SuiteSettingsScreen screen, int x, int w, int y, String label, int value, String tooltip, ChatTab.IntSetter setter) {
      screen.addContentWidget(new HoverLabelWidget(x, y + 2, 220, 12, Text.literal(label), Tooltip.of(Text.literal(tooltip))));
      y += 16;
      this.pinataBalanceThresholdField = new TextFieldWidget(screen.getTextRenderer(), x, y, Math.min(120, w), 20, Text.empty());
      this.pinataBalanceThresholdField.setMaxLength(8);
      this.pinataBalanceThresholdField.setText(String.valueOf(value));
      this.pinataBalanceThresholdField.setTooltip(Tooltip.of(Text.literal(tooltip)));
      this.pinataBalanceThresholdField.setChangedListener(s -> {
         try {
            setter.set(Integer.parseInt(s != null && !s.isBlank() ? s.trim() : "0"));
            SuiteConfig.INSTANCE.markDirty();
            ConfigIO.saveIfDirty();
         } catch (NumberFormatException var3) {
         }
      });
      screen.addContentWidget(this.pinataBalanceThresholdField);
      return y + 24;
   }

   private String crateMessageModeLabel(ChatConfig.CrateMessageMode mode) {
      if (mode == ChatConfig.CrateMessageMode.NORMAL) {
         return "Normal";
      } else {
         return mode == ChatConfig.CrateMessageMode.HIDE ? "Hide" : "Updated";
      }
   }

   private final class AdvertisementsSubTab implements SuiteSubTab {
      @Override
      public String titleKey() {
         return "suitecore.tab.chat.advertisements";
      }

      @Override
      public void build(SuiteSettingsScreen screen, int contentTopOffset) {
         ChatTab.this.welcomeMessageField = null;
         ChatConfig cfg = SuiteConfig.INSTANCE.ChatConfig;
         cfg.ensureAdvertiserProfilesInitialized();
         int x = screen.contentX();
         int w = screen.contentW();
         int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
         y = ChatTab.this.addButtonRow(
            screen,
            x,
            w,
            y,
            20,
            "Active Profile",
            cfg.getActiveAdvertiserProfileName(),
            "Cycles between advertiser profiles. This controls which message is sent by the Advertiser keybind.",
            () -> {
               cfg.cycleAdvertiserProfile();
               ConfigIO.saveIfDirty();
               screen.rebuildPreserveScroll();
            }
         );
         screen.addContentWidget(
            new HoverLabelWidget(
               x,
               y + 2,
               260,
               12,
               Text.literal("Profile Name"),
               Tooltip.of(Text.literal("Name shown in the HUD and settings for the active advertiser profile."))
            )
         );
         y += 16;
         ChatTab.this.advertiserProfileNameField = new TextFieldWidget(screen.getTextRenderer(), x, y, w, 20, Text.empty());
         ChatTab.this.advertiserProfileNameField.setMaxLength(64);
         ChatTab.this.advertiserProfileNameField.setText(cfg.getActiveAdvertiserProfile().name == null ? "" : cfg.getActiveAdvertiserProfile().name);
         ChatTab.this.advertiserProfileNameField.setTooltip(Tooltip.of(Text.literal("Rename the active advertiser profile.")));
         ChatTab.this.advertiserProfileNameField.setChangedListener(s -> {
            cfg.getActiveAdvertiserProfile().name = s == null ? "" : s;
            SuiteConfig.INSTANCE.markDirty();
            ConfigIO.saveIfDirty();
         });
         screen.addContentWidget(ChatTab.this.advertiserProfileNameField);
         y += 24;
         y = ChatTab.this.addButtonRow(screen, x, w, y, 20, "Profiles", "New", "Creates a new advertiser profile and selects it.", () -> {
            cfg.addAdvertiserProfile();
            ConfigIO.saveIfDirty();
            screen.rebuildPreserveScroll();
         });
         if (cfg.advertiserProfiles != null && cfg.advertiserProfiles.size() > 1) {
            y = ChatTab.this.addButtonRow(screen, x, w, y, 20, "Delete", "Delete", "Deletes the active advertiser profile.", () -> {
               cfg.deleteActiveAdvertiserProfile();
               ConfigIO.saveIfDirty();
               screen.rebuildPreserveScroll();
            });
         }

         screen.addContentWidget(
            new HoverLabelWidget(
               x, y + 2, 260, 12, Text.literal("Advertisement Message"), Tooltip.of(Text.literal("Message sent when the advertiser hotkey is used."))
            )
         );
         y += 16;
         ChatTab.this.advertisementMessageField = new TextFieldWidget(screen.getTextRenderer(), x, y, w, 20, Text.empty());
         ChatTab.this.advertisementMessageField.setMaxLength(256);
         ChatTab.this.advertisementMessageField.setText(cfg.getActiveAdvertisementMessage());
         ChatTab.this.advertisementMessageField.setTooltip(Tooltip.of(Text.literal("Enter the message used by the advertiser hotkey.")));
         ChatTab.this.advertisementMessageField.setChangedListener(s -> {
            cfg.setAutomatedAdvertisementMessage(s);
            ConfigIO.saveIfDirty();
         });
         screen.addContentWidget(ChatTab.this.advertisementMessageField);
      }

      @Override
      public void renderText(SuiteSettingsScreen screen, DrawContext ctx, int mouseX, int mouseY, float delta, int contentTopOffset) {
         ChatTab.this.renderCounter(screen, ctx, ChatTab.this.advertisementMessageField, "Used when your advertiser hotkey is pressed.", 256);
      }

      @Override
      public void removed() {
      }

      @Override
      public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
         ChatConfig cfg = SuiteConfig.INSTANCE.ChatConfig;
         int h = contentTopOffset + 8;
         h += 24;
         h += 36;
         h += 24;
         if (cfg.advertiserProfiles != null && cfg.advertiserProfiles.size() > 1) {
            h += 24;
         }

         h += 60;
         return h + 24;
      }
   }

   private final class FiltersSubTab implements SuiteSubTab {
      @Override
      public String titleKey() {
         return "suitecore.tab.chat.filters";
      }

      @Override
      public void build(SuiteSettingsScreen screen, int contentTopOffset) {
         ChatTab.this.clearTextFields();
         ChatConfig cfg = SuiteConfig.INSTANCE.ChatConfig;
         int x = screen.contentX();
         int w = screen.contentW();
         int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
         y = ChatTab.this.addButtonRow(
            screen,
            x,
            w,
            y,
            20,
            "Crate Messages",
            ChatTab.this.crateMessageModeLabel(cfg.crateMessageMode),
            "Controls crate messages: normal, updated formatting, or hidden completely.",
            () -> {
               cfg.cycleCrateMessageMode();
               ConfigIO.saveIfDirty();
               screen.rebuildPreserveScroll();
            }
         );
         y = ChatTab.this.addToggleRow(
            screen,
            x,
            w,
            y,
            20,
            "Hide Voucher Full-Inv Messages",
            cfg.hideInventoryFullVoucherMessages,
            "Hides system messages like:\n[ ITEMS ] Your inventory is full ... Voucher ... around your feet",
            () -> {
               cfg.hideInventoryFullVoucherMessages = !cfg.hideInventoryFullVoucherMessages;
               SuiteConfig.INSTANCE.markDirty();
               screen.rebuildPreserveScroll();
            }
         );
         y = ChatTab.this.addToggleRow(
            screen,
            x,
            w,
            y,
            20,
            "Hide Server Cooldown Messages",
            cfg.hideCooldownMessages,
            "Hides system messages like:\nYou are in cooldown ! (0H 0M 19S)",
            () -> {
               cfg.hideCooldownMessages = !cfg.hideCooldownMessages;
               SuiteConfig.INSTANCE.markDirty();
               screen.rebuildPreserveScroll();
            }
         );
         y = ChatTab.this.addToggleRow(
            screen,
            x,
            w,
            y,
            20,
            "Hide Inventory Compact Messages",
            cfg.hideInventoryCompactMessages,
            "Hides inventory system messages like:\nInventory: You have no items that can be converted into blocks.\nInventory: Converted all items into blocks.",
            () -> {
               cfg.hideInventoryCompactMessages = !cfg.hideInventoryCompactMessages;
               SuiteConfig.INSTANCE.markDirty();
               screen.rebuildPreserveScroll();
            }
         );
         y = ChatTab.this.addToggleRow(
            screen,
            x,
            w,
            y,
            20,
            "Hide Pinata Balance Messages",
            cfg.hidePinataBalanceMessages,
            "Hides balance-added reward messages below the configured dollar amount.\nExample: with 100, $10 is hidden and $100 is shown.",
            () -> {
               cfg.hidePinataBalanceMessages = !cfg.hidePinataBalanceMessages;
               SuiteConfig.INSTANCE.markDirty();
               screen.rebuildPreserveScroll();
            }
         );
         if (cfg.hidePinataBalanceMessages) {
            ChatTab.this.addIntField(
               screen,
               x,
               w,
               y,
               "Pinata Balance Minimum ($)",
               cfg.pinataBalanceHideBelowDollars,
               "Only balance-added messages below this amount are hidden.",
               value -> cfg.pinataBalanceHideBelowDollars = Math.max(0, value)
            );
         }
      }

      @Override
      public void removed() {
      }

      @Override
      public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
         ChatConfig cfg = SuiteConfig.INSTANCE.ChatConfig;
         int h = contentTopOffset + 8 + 120 + 24;
         if (cfg.hidePinataBalanceMessages) {
            h += 44;
         }

         return h;
      }
   }

   private final class HudSubTab implements SuiteSubTab {
      @Override
      public String titleKey() {
         return "suitecore.tab.chat.hud";
      }

      @Override
      public void build(SuiteSettingsScreen screen, int contentTopOffset) {
         ChatTab.this.clearTextFields();
         ChatConfig cfg = SuiteConfig.INSTANCE.ChatConfig;
         int x = screen.contentX();
         int w = screen.contentW();
         int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
         y = ChatTab.this.addToggleRow(
            screen, x, w, y, 20, "Show Tracked Channel", cfg.showTrackedChannelHud, "Shows Main/Marry/XC chat status in the chat HUD - green means whatever you type goes there right now.", () -> {
               cfg.showTrackedChannelHud = !cfg.showTrackedChannelHud;
               SuiteConfig.INSTANCE.markDirty();
               if (cfg.showTrackedChannelHud) {
                  ChatModeProbe.requestProbe();
               }

               ConfigIO.saveIfDirty();
               screen.rebuildPreserveScroll();
            }
         );
         y = ChatTab.this.addToggleRow(
            screen, x, w, y, 20, "Show Advertisement", cfg.showAdvertisementHud, "Shows advertisement cooldown/ready state in the chat HUD.", () -> {
               cfg.showAdvertisementHud = !cfg.showAdvertisementHud;
               SuiteConfig.INSTANCE.markDirty();
               screen.rebuildPreserveScroll();
            }
         );
         if (cfg.showTrackedChannelHud || cfg.showAdvertisementHud || ChatTab.staffHudActive(cfg)) {
            ChatTab.this.addToggleRow(screen, x, w, y, 20, "Compact", cfg.compact, "Uses the compact chat HUD layout.", () -> {
               cfg.compact = !cfg.compact;
               SuiteConfig.INSTANCE.markDirty();
               screen.rebuildPreserveScroll();
            });
         }
      }

      @Override
      public void removed() {
      }

      @Override
      public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
         ChatConfig cfg = SuiteConfig.INSTANCE.ChatConfig;
         int h = contentTopOffset + 8 + 48;
         if (cfg.showTrackedChannelHud || cfg.showAdvertisementHud || ChatTab.staffHudActive(cfg)) {
            h += 24;
         }

         return h + 24;
      }
   }

   private interface IntSetter {
      void set(int var1);
   }

   private final class StaffSubTab implements SuiteSubTab {
      @Override
      public String titleKey() {
         return "suitecore.tab.chat.staff";
      }

      @Override
      public void build(SuiteSettingsScreen screen, int contentTopOffset) {
         ChatTab.this.advertisementMessageField = null;
         ChatTab.this.advertiserProfileNameField = null;
         int x = screen.contentX();
         int w = screen.contentW();
         int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
         ChatConfig cfg = SuiteConfig.INSTANCE.ChatConfig;
         y = ChatTab.this.addToggleRow(
            screen,
            x,
            w,
            y,
            20,
            "Track Staff Chat",
            cfg.trackStaffChat,
            "When OFF, staff chat state, staff probes, staff scoreboard lines, and the staff chat toggle keybind are ignored.",
            () -> {
               cfg.toggleTrackStaffChat();
               if (!cfg.trackStaffChat) {
                  StaffChatState.resetForJoin();
                  ChatModeProbe.resetForJoin();
               } else if (cfg.showTrackedChannelHud) {
                  ChatModeProbe.requestProbe();
               }

               ConfigIO.saveIfDirty();
               screen.rebuildPreserveScroll();
            }
         );
         if (!cfg.trackStaffChat) {
            ChatTab.this.welcomeMessageField = null;
            screen.addContentWidget(
               new HoverLabelWidget(
                  x,
                  y,
                  Math.min(w, 320),
                  24,
                  Text.literal("Staff chat tracking is disabled."),
                  Tooltip.of(Text.literal("SuiteCore will ignore staff chat state until Track Staff Chat is turned back on."))
               )
            );
         } else if (!StaffChatState.isStaffMember) {
            ChatTab.this.welcomeMessageField = null;
            screen.addContentWidget(
               new HoverLabelWidget(
                  x,
                  y,
                  Math.min(w, 320),
                  24,
                  Text.literal("Staff options appear after staff chat is detected."),
                  Tooltip.of(Text.literal("Join a server where staff chat is available to configure these options."))
               )
            );
         } else {
            y = ChatTab.this.addToggleRow(screen, x, w, y, 20, "Show Staff HUD", cfg.showStaffHud, "Shows whether staff chat is toggled on.", () -> {
               cfg.showStaffHud = !cfg.showStaffHud;
               SuiteConfig.INSTANCE.markDirty();
               screen.rebuildPreserveScroll();
            });
            screen.addContentWidget(
               new HoverLabelWidget(
                  x, y + 2, 260, 12, Text.literal("Welcome Message"), Tooltip.of(Text.literal("Message sent when you press Send Welcome Message."))
               )
            );
            y += 16;
            ChatTab.this.welcomeMessageField = new TextFieldWidget(screen.getTextRenderer(), x, y, w, 20, Text.empty());
            ChatTab.this.welcomeMessageField.setMaxLength(256);
            ChatTab.this.welcomeMessageField.setText(cfg.welcomeMessage == null ? "" : cfg.welcomeMessage);
            ChatTab.this.welcomeMessageField.setTooltip(Tooltip.of(Text.literal("Enter a message used by the staff-only Welcome Message keybind.")));
            ChatTab.this.welcomeMessageField.setChangedListener(cfg::setWelcomeMessage);
            screen.addContentWidget(ChatTab.this.welcomeMessageField);
         }
      }

      @Override
      public void renderText(SuiteSettingsScreen screen, DrawContext ctx, int mouseX, int mouseY, float delta, int contentTopOffset) {
         ChatTab.this.renderCounter(screen, ctx, ChatTab.this.welcomeMessageField, "Used when your Welcome Message keybind is pressed.", 256);
      }

      @Override
      public void removed() {
      }

      @Override
      public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
         ChatConfig cfg = SuiteConfig.INSTANCE.ChatConfig;
         return cfg.trackStaffChat && StaffChatState.isStaffMember ? contentTopOffset + 8 + 24 + 24 + 16 + 20 + 24 + 24 : contentTopOffset + 8 + 24 + 48;
      }
   }
}
