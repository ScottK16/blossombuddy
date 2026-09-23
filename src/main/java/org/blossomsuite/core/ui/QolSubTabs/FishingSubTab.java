package org.blossomsuite.core.ui.QolSubTabs;

import org.blossomsuite.core.ui.StyledButton;
import org.blossomsuite.core.ui.StyledSlider;

import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.qol.fishing.FishingAlertController;
import org.blossomsuite.core.ui.HoverLabelWidget;
import org.blossomsuite.core.ui.SuiteSettingsScreen;
import org.blossomsuite.core.ui.SuiteSubTab;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class FishingSubTab implements SuiteSubTab {
   private static final List<String> SOUND_IDS = new ArrayList<>();
   private static final int VISIBLE_SOUND_ROWS = 8;
   private String searchText = "";
   private int listScroll = 0;

   @Override
   public String titleKey() {
      return "suitecore.tab.fishing";
   }

   @Override
   public void build(SuiteSettingsScreen screen, int contentTopOffset) {
      final SuiteConfig cfg = SuiteConfig.INSTANCE;
      final QolConfig qol = cfg.QolConfig;
      int x = screen.contentX();
      int w = screen.contentW();
      int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
      int rowH = 20;
      int gapX = 6;
      int rowGap = 24;
      screen.addContentWidget(
         new HoverLabelWidget(
            x, y + 6, 120, 12, Text.translatable("suitecore.option.enabled"), Tooltip.of(Text.literal("Master toggle for Fishing bite alerts."))
         )
      );
      int toggleW = 80;
      int toggleX = x + w - toggleW;
      ButtonWidget enabledButton = StyledButton.of(Text.literal(qol.fishingEnabled ? "ON" : "OFF"), b -> {
         qol.fishingEnabled = !qol.fishingEnabled;
         cfg.markDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(toggleX, y, toggleW, 20).build();
      enabledButton.setTooltip(Tooltip.of(Text.literal("Turns Fishing bite alerts on or off.")));
      screen.addContentWidget(enabledButton);
      if (!qol.fishingEnabled) {
         screen.addContentWidget(
            new HoverLabelWidget(
               x,
               y + 40,
               300,
               12,
               Text.translatable("suitecore.option.disabled_hint"),
               Tooltip.of(Text.literal("Enable Fishing to choose an alert sound, volume, and pitch."))
            )
         );
      } else {
         y += 44;
         screen.addContentWidget(
            new HoverLabelWidget(
               x, y, 160, 12, Text.literal("Selected Alert Sound"), Tooltip.of(Text.literal("This is the sound that will play when a fish bites."))
            )
         );
         int playW = 60;
         int selectedW = w - playW - 6;
         ButtonWidget selectedSoundButton = StyledButton.of(Text.literal(shortButtonLabel(prettySoundName(qol.fishingSoundId))), b -> {})
            .dimensions(x, y + 16, selectedW, 20)
            .build();
         selectedSoundButton.active = false;
         selectedSoundButton.setTooltip(Tooltip.of(Text.literal(qol.fishingSoundId)));
         screen.addContentWidget(selectedSoundButton);
         ButtonWidget playButton = StyledButton.of(Text.literal("Play"), b -> FishingAlertController.playPreview())
            .dimensions(x + selectedW + 6, y + 16, playW, 20)
            .build();
         playButton.setTooltip(Tooltip.of(Text.literal("Play the selected sound using the current volume and pitch.")));
         screen.addContentWidget(playButton);
         y += 50;
         screen.addContentWidget(
            new HoverLabelWidget(
               x,
               y,
               140,
               12,
               Text.literal("Search Sounds"),
               Tooltip.of(Text.literal("Type part of a sound name or id.\nExamples: pling, orb, pickup, bell, note"))
            )
         );
         TextFieldWidget searchBox = new TextFieldWidget(screen.getTextRenderer(), x, y + 16, w, 20, Text.literal("Search Sounds"));
         searchBox.setMaxLength(120);
         searchBox.setText(this.searchText);
         searchBox.setPlaceholder(Text.literal("Search sounds..."));
         searchBox.setTooltip(Tooltip.of(Text.literal("Filters the sound list below.")));
         searchBox.setChangedListener(text -> {
            this.searchText = text == null ? "" : text;
            this.listScroll = 0;
            screen.rebuildPreserveScroll();
         });
         screen.addContentWidget(searchBox);
         y += 48;
         List<String> filtered = filteredSounds(this.searchText);
         this.clampListScroll(filtered);
         screen.addContentWidget(
            new HoverLabelWidget(
               x,
               y,
               220,
               12,
               Text.literal("Matching Sounds (" + filtered.size() + ")"),
               Tooltip.of(Text.literal("Click a sound to select it.\nHover a row to see the full id."))
            )
         );
         y += 16;
         int upDownW = 26;
         int rowButtonW = w - upDownW - 6;
         int maxRows = Math.min(8, filtered.size());

         for (int i = 0; i < maxRows; i++) {
            int actualIndex = this.listScroll + i;
            if (actualIndex >= filtered.size()) {
               break;
            }

            String soundId = filtered.get(actualIndex);
            String pretty = prettySoundName(soundId);
            boolean selected = soundId.equals(qol.fishingSoundId);
            String prefix = selected ? "> " : "";
            int rowY = y + i * 24;
            ButtonWidget rowButton = StyledButton.of(Text.literal(prefix + shortButtonLabel(pretty)), b -> {
               qol.fishingSoundId = soundId;
               cfg.markDirty();
               screen.rebuildPreserveScroll();
            }).dimensions(x, rowY, rowButtonW, 20).build();
            rowButton.setTooltip(Tooltip.of(Text.literal(soundId)));
            screen.addContentWidget(rowButton);
         }

         int listHeight = 188;
         int scrollX = x + rowButtonW + 6;
         ButtonWidget upButton = StyledButton.of(Text.literal("^"), b -> {
            if (this.listScroll > 0) {
               this.listScroll--;
               screen.rebuildPreserveScroll();
            }
         }).dimensions(scrollX, y, upDownW, 20).build();
         upButton.active = this.listScroll > 0;
         upButton.setTooltip(Tooltip.of(Text.literal("Scroll up.")));
         screen.addContentWidget(upButton);
         ButtonWidget downButton = StyledButton.of(Text.literal("v"), b -> {
            int maxScroll = Math.max(0, filtered.size() - 8);
            if (this.listScroll < maxScroll) {
               this.listScroll++;
               screen.rebuildPreserveScroll();
            }
         }).dimensions(scrollX, y + listHeight - 20, upDownW, 20).build();
         downButton.active = this.listScroll < Math.max(0, filtered.size() - 8);
         downButton.setTooltip(Tooltip.of(Text.literal("Scroll down.")));
         screen.addContentWidget(downButton);
         y += listHeight + 18;
         screen.addContentWidget(
            new HoverLabelWidget(x, y, 120, 12, Text.literal("Volume"), Tooltip.of(Text.literal("How loud the fishing alert sound plays.")))
         );
         SliderWidget volumeSlider = new StyledSlider(x, y + 16, w, 20, Text.empty(), floatToSlider(qol.fishingVolume, 0.0F, 1.0F)) {
            {
               this.updateMessage();
               this.setTooltip(Tooltip.of(Text.literal("Current fishing alert volume.")));
            }

            @Override
            protected void updateMessage() {
               float volume = FishingSubTab.sliderToFloat(this.value, 0.0F, 1.0F);
               this.setMessage(Text.literal("Volume: " + String.format(Locale.ROOT, "%.2f", volume)));
            }

            @Override
            protected void applyValue() {
               qol.fishingVolume = FishingSubTab.sliderToFloat(this.value, 0.0F, 1.0F);
               cfg.markDirty();
            }
         };
         screen.addContentWidget(volumeSlider);
         y += 50;
         screen.addContentWidget(
            new HoverLabelWidget(x, y, 120, 12, Text.literal("Pitch"), Tooltip.of(Text.literal("Changes the pitch of the fishing alert sound.")))
         );
         SliderWidget pitchSlider = new StyledSlider(x, y + 16, w, 20, Text.empty(), floatToSlider(qol.fishingPitch, 0.5F, 2.0F)) {
            {
               this.updateMessage();
               this.setTooltip(Tooltip.of(Text.literal("Current fishing alert pitch.")));
            }

            @Override
            protected void updateMessage() {
               float pitch = FishingSubTab.sliderToFloat(this.value, 0.5F, 2.0F);
               this.setMessage(Text.literal("Pitch: " + String.format(Locale.ROOT, "%.2f", pitch)));
            }

            @Override
            protected void applyValue() {
               qol.fishingPitch = FishingSubTab.sliderToFloat(this.value, 0.5F, 2.0F);
               cfg.markDirty();
            }
         };
         screen.addContentWidget(pitchSlider);
      }
   }

   @Override
   public void renderText(SuiteSettingsScreen screen, DrawContext ctx, int mouseX, int mouseY, float delta, int contentTopOffset) {
   }

   @Override
   public void removed() {
   }

   @Override
   public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
      SuiteConfig cfg = SuiteConfig.INSTANCE;
      int h = contentTopOffset + 30;
      if (!cfg.QolConfig.fishingEnabled) {
         return h + 50;
      }

      h += 50;
      h += 48;
      h += 16;
      h += 188;
      h += 18;
      h += 50;
      h += 50;
      return h + 20;
   }

   private void clampListScroll(List<String> filtered) {
      int maxScroll = Math.max(0, filtered.size() - 8);
      if (this.listScroll < 0) {
         this.listScroll = 0;
      }

      if (this.listScroll > maxScroll) {
         this.listScroll = maxScroll;
      }
   }

   private static List<String> filteredSounds(String query) {
      if (query != null && !query.isBlank()) {
         String q = normalize(query);
         List<String> out = new ArrayList<>();

         for (String id : SOUND_IDS) {
            String pretty = prettySoundName(id);
            if (normalize(id).contains(q) || normalize(pretty).contains(q)) {
               out.add(id);
            }
         }

         return out;
      } else {
         return SOUND_IDS;
      }
   }

   private static String prettySoundName(String soundId) {
      if (soundId != null && !soundId.isBlank()) {
         String path = soundId;
         int colon = path.indexOf(58);
         if (colon >= 0 && colon + 1 < path.length()) {
            path = path.substring(colon + 1);
         }

         String[] parts = path.split("\\.");
         String raw;
         if (parts.length >= 2) {
            raw = parts[parts.length - 2] + " " + parts[parts.length - 1];
         } else {
            raw = parts[parts.length - 1];
         }

         raw = raw.replace('_', ' ').trim();
         return toTitleCase(raw);
      } else {
         return "Unknown Sound";
      }
   }

   private static String shortButtonLabel(String text) {
      if (text != null && !text.isBlank()) {
         return text.length() <= 30 ? text : text.substring(0, 27) + "...";
      } else {
         return "Unknown";
      }
   }

   private static String normalize(String s) {
      return s == null ? "" : s.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
   }

   private static String toTitleCase(String input) {
      if (input != null && !input.isBlank()) {
         String[] words = input.split("\\s+");
         StringBuilder sb = new StringBuilder();

         for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (!word.isEmpty()) {
               if (i > 0) {
                  sb.append(' ');
               }

               if (word.length() == 1) {
                  sb.append(Character.toUpperCase(word.charAt(0)));
               } else {
                  sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
               }
            }
         }

         return sb.toString();
      } else {
         return "";
      }
   }

   private static double floatToSlider(float value, float min, float max) {
      value = Math.max(min, Math.min(max, value));
      return (double)(value - min) / (max - min);
   }

   private static float sliderToFloat(double value, float min, float max) {
      value = Math.max(0.0, Math.min(1.0, value));
      return (float)(min + value * (max - min));
   }

   static {
      Registries.SOUND_EVENT
         .stream()
         .map(Registries.SOUND_EVENT::getId)
         .filter(id -> id != null)
         .map(Identifier::toString)
         .sorted(Comparator.naturalOrder())
         .forEach(SOUND_IDS::add);
      if (SOUND_IDS.isEmpty()) {
         SOUND_IDS.add("minecraft:entity.experience_orb.pickup");
      }
   }
}
