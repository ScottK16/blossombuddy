package org.blossomsuite.core.ui;

import org.blossomsuite.core.config.SuiteConfig;

import org.blossomsuite.core.config.VoteConfig;

import org.blossomsuite.core.emote.EmoteClient;
import org.blossomsuite.core.presence.PresenceClient;
import org.blossomsuite.core.stats.StatsClient;
import org.blossomsuite.core.xchat.XChatClient;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.blossomsuite.core.chat.SecondaryChat;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.cooldowns.CooldownRules;
import org.blossomsuite.core.hud.ScoreboardHud;
import org.blossomsuite.core.jobs.JobXpTracker;
import org.blossomsuite.core.state.SuiteState;

/** Options pages for the features added in BlossomBuddy. */
public final class BuddyTabs {
   private BuddyTabs() {
   }

   private abstract static class Page implements SuiteSubTab {
      /** Rows of content to size the scroll area from. Overridden where it depends on state. */
      abstract int height(SuiteSettingsScreen screen);

      abstract void rows(SuiteSettingsScreen screen, int x, int w, int y);

      @Override
      public final void build(SuiteSettingsScreen screen, int contentTopOffset) {
         int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
         this.rows(screen, screen.contentX(), screen.contentW(), y);
      }

      @Override
      public void removed() {
      }

      @Override
      public final int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
         return contentTopOffset + 8 + this.height(screen) + 24;
      }
   }

   // ------------------------------------------------------------------ scoreboard

   public static final class Scoreboard extends Page {
      @Override
      public String titleKey() {
         return "suitecore.tab.scoreboard";
      }

      @Override
      int height(SuiteSettingsScreen screen) {
         return UiRows.ROW * 8 + UiRows.SLIDER_ROW * 2 + 48;
      }

      @Override
      void rows(SuiteSettingsScreen screen, int x, int w, int y) {
         FeatureConfig.Scoreboard sb = FeatureConfig.INSTANCE.scoreboard;
         y = UiRows.toggle(screen, x, w, y, "Hide scoreboard", sb.hidden, "Hides the server's sidebar scoreboard. The HUD editor can still find it.", () -> {
            sb.hidden = !sb.hidden;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         });
         y = UiRows.slider(screen, x, w, y, "Size", "How big the scoreboard is drawn.", (sb.panel.scale - 0.3) / 1.7, v -> "Size: " + Math.round((0.3 + v * 1.7) * 100) + "%", v -> {
            sb.panel.scale = (float)(0.3 + v * 1.7);
            FeatureConfig.markDirty();
         });
         y = UiRows.toggle(screen, x, w, y, "Background", sb.background, "The dark panel behind the scoreboard.", () -> {
            sb.background = !sb.background;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         });
         if (sb.background) {
            double shown = sb.backgroundOpacity >= 0.0F ? sb.backgroundOpacity : 0.35;
            y = UiRows.slider(screen, x, w, y, "Background opacity", "How solid the background is.", shown, v -> "Opacity: " + Math.round(v * 100) + "%", v -> {
               sb.backgroundOpacity = (float)v;
               FeatureConfig.markDirty();
            });
         }

         y = UiRows.toggle(screen, x, w, y, "Show numbers", sb.showNumbers, "The score numbers on the right of each line.", () -> {
            sb.showNumbers = !sb.showNumbers;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         });
         y = UiRows.toggle(screen, x, w, y, "Text shadow", sb.textShadow, "Draws the text with a drop shadow.", () -> {
            sb.textShadow = !sb.textShadow;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         });
         y = UiRows.toggle(screen, x, w, y, "Border", sb.border, "A thin pink outline around the scoreboard.", () -> {
            sb.border = !sb.border;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         });
         y = UiRows.toggle(screen, x, w, y, "Rounded corners", sb.rounded, "Softens the corners of the background.", () -> {
            sb.rounded = !sb.rounded;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         });
         y = UiRows.button(screen, x, w, y, "Everything above", "Reset", "Back to the game's own look, position and size.", () -> {
            FeatureConfig.INSTANCE.scoreboard = new FeatureConfig.Scoreboard();
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         });
         y = UiRows.note(screen, x, w, y, "Drag or resize it in the HUD editor (/buddy editmode).");
         if (ScoreboardHud.foreign()) {
            String who = ScoreboardHud.foreignName();
            y = UiRows.note(screen, x, w, y, who + " is drawing its own scoreboard right now, so none of the settings above can change it.");
            UiRows.note(screen, x, w, y, "Use " + who + "'s own scoreboard settings, or turn its scoreboard off to use these.");
         }
      }
   }

   // ------------------------------------------------------------------ hands

   public static final class Hands extends Page {
      @Override
      public String titleKey() {
         return "suitecore.tab.hands";
      }

      @Override
      int height(SuiteSettingsScreen screen) {
         return UiRows.ROW * 3 + UiRows.SLIDER_ROW + 12;
      }

      @Override
      void rows(SuiteSettingsScreen screen, int x, int w, int y) {
         FeatureConfig.Hands h = FeatureConfig.INSTANCE.hands;
         y = UiRows.toggle(screen, x, w, y, "Small hands", h.smallHands, "Shrinks the first-person hand and held item.", () -> {
            h.smallHands = !h.smallHands;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         });
         if (h.smallHands) {
            y = UiRows.slider(screen, x, w, y, "Hand size", "How small the hand and item are drawn.", (h.smallHandsScale - 0.2) / 0.8, v -> "Hand size: " + Math.round((0.2 + v * 0.8) * 100) + "%", v -> {
               h.smallHandsScale = (float)(0.2 + v * 0.8);
               FeatureConfig.markDirty();
            });
         }

         y = UiRows.toggle(screen, x, w, y, "No swing movement", h.freezeSwing, "The held tool stays still while you swing or mine.", () -> {
            h.freezeSwing = !h.freezeSwing;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         });
         UiRows.toggle(screen, x, w, y, "No equip movement", h.freezeEquip, "The item doesn't slide up when you switch to it.", () -> {
            h.freezeEquip = !h.freezeEquip;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         });
      }
   }

   // ------------------------------------------------------------------ extra hotbar

   public static final class ExtraHotbar extends Page {
      @Override
      public String titleKey() {
         return "suitecore.tab.hotbar";
      }

      @Override
      int height(SuiteSettingsScreen screen) {
         return UiRows.ROW * 3 + 60;
      }

      @Override
      void rows(SuiteSettingsScreen screen, int x, int w, int y) {
         FeatureConfig.Hotbar hb = FeatureConfig.INSTANCE.hotbar;
         y = UiRows.toggle(screen, x, w, y, "Show extra hotbar", hb.show, "Shows the inventory rows above your hotbar.", () -> {
            hb.show = !hb.show;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         });
         y = UiRows.button(screen, x, w, y, "Rows", hb.extraRows == 1 ? "Double hotbar" : "Triple hotbar", "One or two extra rows above the hotbar.", () -> {
            hb.extraRows = hb.extraRows == 1 ? 2 : 1;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         });
         y = UiRows.note(screen, x, w, y, "Keys: Options > Controls > BlossomBuddy (all start unbound).");
         y = UiRows.note(screen, x, w, y, "\"Swap Hotbar With Row 1\" flips between two hotbars; press it again to come back.");
         y = UiRows.note(screen, x, w, y, "\"Hotbar Up / Down\" rotates through every row. Locked slots stay put.");
         UiRows.note(screen, x, w, y, "Drag the panel in the HUD editor (/buddy editmode).");
      }
   }

   // ------------------------------------------------------------------ slot locks

   public static final class SlotLocks extends Page {
      @Override
      public String titleKey() {
         return "suitecore.tab.slotlocks";
      }

      @Override
      int height(SuiteSettingsScreen screen) {
         return UiRows.ROW * 3 + 40;
      }

      @Override
      void rows(SuiteSettingsScreen screen, int x, int w, int y) {
         FeatureConfig.SlotLocks locks = FeatureConfig.INSTANCE.slotLocks;
         y = UiRows.button(screen, x, w, y, "Locked slots", "Edit", "Pick which inventory and hotbar slots are locked.", () -> MinecraftClient.getInstance().setScreen(new InventorySlotLocksScreen(screen)));
         y = UiRows.toggle(screen, x, w, y, "Block dropping (Q)", locks.blockDrop, "The drop key does nothing while a locked slot is selected.", () -> {
            locks.blockDrop = !locks.blockDrop;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         });
         y = UiRows.toggle(screen, x, w, y, "Block moving items", locks.blockMoves, "Locked items can't be dragged, shift-clicked or swapped with number keys.", () -> {
            locks.blockMoves = !locks.blockMoves;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         });
         UiRows.note(screen, x, w, y, "Locked slots are also skipped by Sort and Deposit, and by the extra hotbar.");
      }
   }

   // ------------------------------------------------------------------ vote sharing

   public static final class Sharing extends Page {
      @Override
      public String titleKey() {
         return "suitecore.tab.sharing";
      }

      @Override
      int height(SuiteSettingsScreen screen) {
         return UiRows.ROW * 5 + 18 * 5 + 56;
      }

      private static String notifyLabel(VoteConfig.NotifyWhen mode) {
         return switch (mode) {
            case OFF -> "Never";
            case COUNTDOWN -> "Countdown starts";
            case ONGOING -> "Party is running";
            case BOTH -> "Both";
         };
      }

      @Override
      void rows(SuiteSettingsScreen screen, int x, int w, int y) {
         FeatureConfig.Relay relay = FeatureConfig.INSTANCE.relay;
         y = UiRows.toggle(screen, x, w, y, "Share vote party", relay.share, "Shares your realm's vote-party count anonymously and gets the other realms' counts back.", () -> {
            relay.share = !relay.share;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         });
         y = UiRows.note(screen, x, w, y, "Automatic: the mod reads the vote party from your realm's scoreboard.");
         y = UiRows.note(screen, x, w, y, "Only the realm name and its vote count are sent. No name, no account.");
         VoteConfig vote = SuiteConfig.INSTANCE.VoteConfig;
         y = UiRows.button(screen, x, w, y, "Notify me when", notifyLabel(vote.notifyWhen), "Get an alert when another realm's vote party starts. Off unless you choose.", () -> {
            VoteConfig.NotifyWhen[] all = VoteConfig.NotifyWhen.values();
            vote.notifyWhen = all[(vote.notifyWhen.ordinal() + 1) % all.length];
            SuiteConfig.INSTANCE.markDirty();
            screen.rebuildPreserveScroll();
         });

         FeatureConfig.Stats stats = FeatureConfig.INSTANCE.stats;
         y = UiRows.toggle(screen, x, w, y, "Anonymous usage counts", stats.enabled, "Counts how many people use the mod, with a random ID and the mod version. No name, and no IP address is stored.", () -> {
            StatsClient.INSTANCE.setEnabled(!stats.enabled);
            screen.rebuildPreserveScroll();
         });
         y = UiRows.toggle(screen, x, w, y, "Share my username (optional)", stats.shareName, "Lets the developer see your Minecraft name in the list of users. Off unless you turn it on.", () -> {
            StatsClient.INSTANCE.setShareName(!stats.shareName);
            screen.rebuildPreserveScroll();
         });
         y = UiRows.note(screen, x, w, y, "Usage counting sends a random ID only. Your name is only stored if you turn the option above on.");

         screen.addContentWidget(new HoverLabelWidget(x, y + 4, w, 12, Text.literal("Relay address (advanced)"), Tooltip.of(Text.literal("Leave empty to use the built-in relay. Set this only to use your own."))));
         TextFieldWidget field = new TextFieldWidget(screen.getTextRenderer(), x, y + 20, w, 20, Text.literal("Relay address"));
         field.setMaxLength(200);
         field.setText(relay.url);
         field.setPlaceholder(Text.literal("empty = built-in"));
         field.setChangedListener(s -> {
            relay.url = s;
            FeatureConfig.markDirty();
            if (SuiteState.INSTANCE.http != null) {
               SuiteState.INSTANCE.http.setBaseUrl(FeatureConfig.effectiveRelayUrl());
            }
         });
         screen.addContentWidget(field);
         boolean on = SuiteState.INSTANCE.http != null && SuiteState.INSTANCE.http.enabled();
         UiRows.note(screen, x, w, y + 44, on ? "A relay is set." : "No relay is set yet, so nothing is being shared.");
      }
   }

   // ------------------------------------------------------------------ cross-realm chat

   public static final class CrossRealm extends Page {
      @Override
      public String titleKey() {
         return "suitecore.tab.xchat";
      }

      @Override
      int height(SuiteSettingsScreen screen) {
         return UiRows.ROW * 8 + 18 * 10 + 24;
      }

      @Override
      void rows(SuiteSettingsScreen screen, int x, int w, int y) {
         FeatureConfig.XChat xc = FeatureConfig.INSTANCE.xchat;
         y = UiRows.toggle(screen, x, w, y, "Cross-realm chat", xc.enabled, "Talk to BlossomBuddy players on every realm. Off until you turn it on.", () -> {
            xc.enabled = !xc.enabled;
            FeatureConfig.markDirty();
            if (xc.enabled) {
               XChatClient.INSTANCE.wake();
            }

            screen.rebuildPreserveScroll();
         });
         y = UiRows.note(screen, x, w, y, "Send with /xc <message>. Everyone using BlossomBuddy sees it, on any realm.");
         y = UiRows.note(screen, x, w, y, "It shows in the secondary chat's Realms tab (or the main chat if that window is off).");
         y = UiRows.note(screen, x, w, y, "Your name is proven with Mojang's login check, so nobody can pose as you.");
         y = UiRows.note(screen, x, w, y, "Mute someone: /buddy xchat mute <name>.");
         y = UiRows.note(screen, x, w, y, XChatClient.INSTANCE.status());

         FeatureConfig.Presence pl = FeatureConfig.INSTANCE.presence;
         y = UiRows.toggle(screen, x, w, y, "Appear in the player list", pl.enabled, "Shows your name and realm to other players who also chose to appear, and lets you see them. Off until you turn it on.", () -> {
            PresenceClient.INSTANCE.setEnabled(!pl.enabled);
            screen.rebuildPreserveScroll();
         });
         y = UiRows.toggle(screen, x, w, y, "Symbol in the tab list", pl.tabSymbol, "A small flower next to players in the tab list who are using BlossomBuddy.", () -> {
            PresenceClient.INSTANCE.setTabSymbol(!pl.tabSymbol);
            screen.rebuildPreserveScroll();
         });
         y = UiRows.note(screen, x, w, y, "Open the list with /buddy who (or bind a key: Options > Controls > BlossomBuddy).");
         y = UiRows.note(screen, x, w, y, PresenceClient.INSTANCE.status());

         FeatureConfig.Emotes em = FeatureConfig.INSTANCE.emotes;
         y = UiRows.toggle(screen, x, w, y, "Emotes", em.enabled, "Dances and waves. Open the wheel with /buddy emote or a key (Options > Controls > BlossomBuddy).", () -> {
            EmoteClient.INSTANCE.setEnabled(!em.enabled);
            screen.rebuildPreserveScroll();
         });
         y = UiRows.toggle(screen, x, w, y, "   Show other players' emotes", em.showOthers, "Plays other BlossomBuddy players' emotes on their characters.", () -> {
            EmoteClient.INSTANCE.setShowOthers(!em.showOthers);
            screen.rebuildPreserveScroll();
         });
         y = UiRows.toggle(screen, x, w, y, "   Let others see my emotes", em.share, "Tells the relay about your emotes so other BlossomBuddy players on your realm see them.", () -> {
            EmoteClient.INSTANCE.setShare(!em.share);
            screen.rebuildPreserveScroll();
         });
         y = UiRows.note(screen, x, w, y, "Emotes need the player list to be connected. Walking, jumping or attacking ends yours.");

         FeatureConfig.MapArt mapart = FeatureConfig.INSTANCE.mapart;
         y = UiRows.toggle(screen, x, w, y, "Show map art preview on screen", mapart.showPreview, "The picture of whatever design /buddy mapart <code> last found. Move it in /buddy editmode.", () -> {
            mapart.showPreview = !mapart.showPreview;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         });
         y = UiRows.toggle(screen, x, w, y, "Show map art block list on screen", mapart.showList, "The block list of whatever design /buddy mapart <code> last found. Move it in /buddy editmode.", () -> {
            mapart.showList = !mapart.showList;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         });
         UiRows.note(screen, x, w, y, "Look up a design first with /buddy mapart <code>, or from the map art website.");
      }
   }

   // ------------------------------------------------------------------ xp tracker

   public static final class XpTracker extends Page {
      @Override
      public String titleKey() {
         return "suitecore.tab.xp";
      }

      @Override
      int height(SuiteSettingsScreen screen) {
         return UiRows.ROW * 4 + UiRows.SLIDER_ROW + 24;
      }

      @Override
      void rows(SuiteSettingsScreen screen, int x, int w, int y) {
         FeatureConfig.Xp xp = FeatureConfig.INSTANCE.xp;
         y = UiRows.toggle(screen, x, w, y, "Show XP tracker", xp.show, "Session XP per job. Needs the Jobs Reborn boss bar.", () -> {
            xp.show = !xp.show;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         });
         y = UiRows.slider(screen, x, w, y, "Jobs shown", "How many jobs to list, most XP first.", (xp.maxRows - 1) / 7.0, v -> "Jobs shown: " + (1 + (int)Math.round(v * 7)), v -> {
            xp.maxRows = 1 + (int)Math.round(v * 7);
            FeatureConfig.markDirty();
         });
         y = UiRows.toggle(screen, x, w, y, "Show XP per hour", xp.showRates, "Adds an hourly rate to each job.", () -> {
            xp.showRates = !xp.showRates;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         });
         y = UiRows.toggle(screen, x, w, y, "Show time to next level", xp.showEta, "Estimated time to the next level at the current rate.", () -> {
            xp.showEta = !xp.showEta;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         });
         UiRows.button(screen, x, w, y, "Session", "Reset now", "Clears the tracked XP (same as /buddy xp reset).", () -> JobXpTracker.INSTANCE.reset());
      }
   }

   // ------------------------------------------------------------------ secondary chat

   public static final class SecondaryChatPage extends Page {
      private static final int FILTER_H = 56;

      @Override
      public String titleKey() {
         return "suitecore.tab.secondarychat";
      }

      @Override
      int height(SuiteSettingsScreen screen) {
         return UiRows.ROW * 2 + UiRows.SLIDER_ROW + FeatureConfig.INSTANCE.chat.filters.size() * FILTER_H + 30 + UiRows.ROW * 12 + 60;
      }

      @Override
      void rows(SuiteSettingsScreen screen, int x, int w, int y) {
         y = UiRows.note(screen, x, w, y, "Looking for something someone said, or a player's name? /buddy search (or a key: Options > Controls > BlossomBuddy) finds it in main chat and lets you copy it.");
         FeatureConfig.Chat chat = FeatureConfig.INSTANCE.chat;
         y = UiRows.toggle(screen, x, w, y, "Show secondary chat", chat.show, "A second chat window for the filters below.", () -> {
            chat.show = !chat.show;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         });
         y = UiRows.slider(screen, x, w, y, "Lines", "How many lines the window shows.", (chat.lines - 2) / 18.0, v -> "Lines: " + (2 + (int)Math.round(v * 18)), v -> {
            chat.lines = 2 + (int)Math.round(v * 18);
            FeatureConfig.markDirty();
         });
         String shown = SecondaryChat.INSTANCE.selectedName();
         y = UiRows.button(screen, x, w, y, "Showing", shown == null ? "All" : shown, "Which filter the window shows. Also cyclable with a key (Options > Controls).", () -> {
            SecondaryChat.INSTANCE.cycleFilter();
            screen.rebuildPreserveScroll();
         });

         y = UiRows.note(screen, x, w, y, "Extra windows: give a filter a window of its own, e.g. only Marry chat. Move them in /buddy editmode.");
         for (int i = 0; i < chat.windows.size(); i++) {
            FeatureConfig.ChatWindow win = chat.windows.get(i);
            y = UiRows.toggle(screen, x, w, y, "Window " + (i + 1), win.enabled, "Shows one filter in a window of its own. Works even if the filter's tab below is off.", () -> {
               win.enabled = !win.enabled;
               FeatureConfig.markDirty();
               screen.rebuildPreserveScroll();
            });
            y = UiRows.button(screen, x, w, y, "   Shows", win.filter == null || win.filter.isBlank() ? "(pick a filter)" : win.filter, "Which filter this window shows. Click to cycle through them.", () -> {
               win.filter = nextFilterName(chat, win.filter);
               FeatureConfig.markDirty();
               screen.rebuildPreserveScroll();
            });
            y = UiRows.toggle(screen, x, w, y, "   Only in this window", win.exclusive, "Leaves this filter's lines out of the main window's All view.", () -> {
               win.exclusive = !win.exclusive;
               FeatureConfig.markDirty();
               screen.rebuildPreserveScroll();
            });
         }

         for (int i = 0; i < chat.filters.size(); i++) {
            y = filterBlock(screen, x, w, y, chat, chat.filters.get(i));
         }

         UiRows.button(screen, x, w, y, "New filter", "Add", "Adds an empty filter. Type words separated by | (for example: marry|divorce).", () -> {
            chat.filters.add(new FeatureConfig.Filter("New filter", ""));
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         });
      }

      /** The next filter name after {@code current} (or the first one), for cycling an extra window through the filters. */
      static String nextFilterName(FeatureConfig.Chat chat, String current) {
         java.util.List<String> names = new java.util.ArrayList<>();
         for (FeatureConfig.Filter f : chat.filters) {
            if (f != null && f.name != null && !f.name.isBlank()) {
               names.add(f.name);
            }
         }

         if (names.isEmpty()) {
            return "";
         }

         for (int i = 0; i < names.size(); i++) {
            if (names.get(i).equalsIgnoreCase(current)) {
               return names.get((i + 1) % names.size());
            }
         }

         return names.get(0);
      }

      private static int filterBlock(SuiteSettingsScreen screen, int x, int w, int y, FeatureConfig.Chat chat, FeatureConfig.Filter f) {
         if (f.external) {
            screen.addContentWidget(new HoverLabelWidget(x, y + 6, Math.max(60, w - 130), 12, Text.literal("Realms (cross-realm chat)"), Tooltip.of(Text.literal("Where cross-realm chat shows up. Turn it on under Cross-Realm Chat."))));
            screen.addContentWidget(flag(f.enabled, "Show tab", x + w - 120, y, 120, () -> {
               f.enabled = !f.enabled;
               FeatureConfig.markDirty();
               screen.rebuildPreserveScroll();
            }));
            return y + 30;
         }

         int nameW = Math.max(70, w * 3 / 10);
         TextFieldWidget name = new TextFieldWidget(screen.getTextRenderer(), x, y, nameW, 20, Text.literal("Filter name"));
         name.setMaxLength(24);
         name.setText(f.name);
         name.setChangedListener(s -> {
            f.name = s;
            FeatureConfig.markDirty();
         });
         screen.addContentWidget(name);

         TextFieldWidget pattern = new TextFieldWidget(screen.getTextRenderer(), x + nameW + 6, y, w - nameW - 6, 20, Text.literal("Words to match"));
         pattern.setMaxLength(160);
         pattern.setText(f.pattern);
         pattern.setPlaceholder(Text.literal("words separated by |"));
         pattern.setChangedListener(s -> {
            f.pattern = s;
            FeatureConfig.markDirty();
         });
         screen.addContentWidget(pattern);

         int by = y + 24;
         int delW = 24;
         int each = (w - delW - 12) / 3;
         screen.addContentWidget(flag(f.enabled, "Enabled", x, by, each, () -> {
            f.enabled = !f.enabled;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         }));
         screen.addContentWidget(flag(f.hideFromMain, "Hide from main", x + each + 4, by, each, () -> {
            f.hideFromMain = !f.hideFromMain;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         }));
         screen.addContentWidget(flag(f.regex, "Regex", x + (each + 4) * 2, by, each, () -> {
            f.regex = !f.regex;
            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         }));
         screen.addContentWidget(StyledButton.of(Text.literal("x"), b -> {
            chat.filters.remove(f);
            if (chat.selected >= chat.filters.size()) {
               chat.selected = -1;
            }

            FeatureConfig.markDirty();
            screen.rebuildPreserveScroll();
         }).dimensions(x + w - delW, by, delW, 20).build());
         return y + FILTER_H;
      }

      private static StyledButton flag(boolean on, String label, int x, int y, int w, Runnable press) {
         return StyledButton.of(Text.literal(label + ": " + (on ? "ON" : "OFF")), b -> press.run()).dimensions(x, y, w, 20).build();
      }
   }

   // ------------------------------------------------------------------ cooldown items

   /** One on/off switch per item the cooldown rules know about. */
   public static final class CooldownItems extends Page {
      @Override
      public String titleKey() {
         return "suitecore.tab.cooldowns.items";
      }

      private static List<CooldownRules.RuleInfo> items() {
         return CooldownRules.allRuleInfo();
      }

      @Override
      int height(SuiteSettingsScreen screen) {
         List<CooldownRules.RuleInfo> items = items();
         return UiRows.ROW * (items.isEmpty() ? 3 : items.size() + 2);
      }

      @Override
      void rows(SuiteSettingsScreen screen, int x, int w, int y) {
         List<CooldownRules.RuleInfo> items = items();
         if (items.isEmpty()) {
            UiRows.note(screen, x, w, y, "No cooldown rules loaded. Put a cooldowns.json in the config folder, then run /buddy reload.");
            return;
         }

         List<String> disabled = FeatureConfig.INSTANCE.disabledCooldownItems;
         y = UiRows.pair(screen, x, w, y, "Track all", () -> {
            disabled.clear();
            changed(screen);
         }, "Track none", () -> {
            disabled.clear();
            for (CooldownRules.RuleInfo r : items) {
               disabled.add(r.key());
            }

            changed(screen);
         });
         for (CooldownRules.RuleInfo r : items) {
            boolean on = !disabled.contains(r.key());
            y = UiRows.toggle(screen, x, w, y, r.name(), on, r.detail(), () -> {
               if (on) {
                  disabled.add(r.key());
               } else {
                  disabled.remove(r.key());
               }

               changed(screen);
            });
         }
      }

      private static void changed(SuiteSettingsScreen screen) {
         FeatureConfig.markDirty();
         CooldownRules.reapply();
         screen.rebuildPreserveScroll();
      }
   }
}
