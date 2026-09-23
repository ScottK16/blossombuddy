package org.blossomsuite.core.ui.QolSubTabs;

import org.blossomsuite.core.ui.StyledButton;
import org.blossomsuite.core.ui.StyledSlider;

import org.blossomsuite.core.SuiteFeature;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.GroupShareCodec;
import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.ui.BlockEntryButtonWidget;
import org.blossomsuite.core.ui.HoverLabelWidget;
import org.blossomsuite.core.ui.SuiteSettingsScreen;
import org.blossomsuite.core.ui.SuiteSubTab;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;

public class AutoSwapperSubTab implements SuiteSubTab {
   private static final TagKey<Block> C_ORES = TagKey.of(RegistryKeys.BLOCK, Identifier.of("c", "ores"));
   private static final int BLOCK_EDITOR_ROWS = 6;
   private static final int BLOCK_EDITOR_ROW_HEIGHT = 24;
   private static final int BLOCK_EDITOR_SCROLL_EDGE_GUTTER = 36;
   private static final List<AutoSwapperSubTab.BlockChoice> BLOCK_CHOICES = buildBlockChoices();
   private final boolean customGroupsOnly;
   private String editingDraftBlockId = null;
   private final Set<String> blockEditorSelectedIds = new LinkedHashSet<>();
   private String blockEditorSearch = "";
   private int blockEditorScroll = 0;
   private int blockEditorListLeft = -1;
   private int blockEditorListRight = -1;
   private int blockEditorListTop = -1;
   private int blockEditorListBottom = -1;
   private boolean importingGroupCode = false;
   private String groupImportCode = "";
   private String groupShareNotice = "";
   private long groupShareNoticeUntilMs = 0L;

   public AutoSwapperSubTab() {
      this(false);
   }

   public AutoSwapperSubTab(boolean customGroupsOnly) {
      this.customGroupsOnly = customGroupsOnly;
   }

   @Override
   public String titleKey() {
      return this.customGroupsOnly ? "suitecore.tab.autoswapper.custom_groups" : "suitecore.tab.autoswapper.settings";
   }

   @Override
   public void build(SuiteSettingsScreen screen, int contentTopOffset) {
      final SuiteConfig cfg = SuiteConfig.INSTANCE;
      int x = screen.contentX();
      int w = screen.contentW();
      int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
      int rowH = 20;
      int gapY = 26;
      int gapX = 6;
      if (this.customGroupsOnly) {
         this.addCustomGroupSection(screen, cfg.QolConfig, x, w, y, 20, 6);
      } else {
         screen.addContentWidget(
            new HoverLabelWidget(x, y + 6, 120, 12, Text.translatable("suitecore.option.enabled"), Tooltip.of(Text.literal("Master toggle for AutoSwapper.")))
         );
         int toggleW = 80;
         int toggleX = x + w - toggleW;
         ButtonWidget enabledButton = StyledButton.of(Text.literal(cfg.QolConfig.autoSwapperEnabled ? "ON" : "OFF"), b -> {
            cfg.QolConfig.autoSwapperEnabled = !cfg.QolConfig.autoSwapperEnabled;
            cfg.markDirty();
            screen.rebuildPreserveScroll();
         }).dimensions(toggleX, y, toggleW, 20).build();
         enabledButton.setTooltip(Tooltip.of(Text.literal("Turns AutoSwapper on or off.")));
         screen.addContentWidget(enabledButton);
         y += 28;
         screen.addContentWidget(
            new HoverLabelWidget(
               x,
               y + 6,
               180,
               12,
               Text.literal("Status HUD"),
               Tooltip.of(Text.literal("Shows a draggable AutoSwapper status HUD.\nUse HUD Edit Mode to move or resize it."))
            )
         );
         ButtonWidget hudButton = StyledButton.of(Text.literal(cfg.AutoSwapperHudConfig.showHud ? "ON" : "OFF"), b -> {
            cfg.AutoSwapperHudConfig.toggleShowHud();
            ConfigIO.saveIfDirty();
            screen.rebuildPreserveScroll();
         }).dimensions(toggleX, y, toggleW, 20).build();
         hudButton.setTooltip(Tooltip.of(Text.literal("Shows or hides the AutoSwapper status HUD.")));
         screen.addContentWidget(hudButton);
         y += 28;
         screen.addContentWidget(
            new HoverLabelWidget(
               x, y + 6, 180, 12, Text.literal("HUD Header"), Tooltip.of(Text.literal("Shows or hides the AutoSwapper title bar on its HUD."))
            )
         );
         ButtonWidget hudHeaderButton = StyledButton.of(Text.literal(cfg.AutoSwapperHudConfig.showHeader ? "ON" : "OFF"), b -> {
            cfg.AutoSwapperHudConfig.toggleShowHeader();
            ConfigIO.saveIfDirty();
            screen.rebuildPreserveScroll();
         }).dimensions(toggleX, y, toggleW, 20).build();
         hudHeaderButton.setTooltip(Tooltip.of(Text.literal("Shows or hides the AutoSwapper HUD header.")));
         screen.addContentWidget(hudHeaderButton);
         if (!cfg.QolConfig.autoSwapperEnabled) {
            screen.addContentWidget(
               new HoverLabelWidget(
                  x,
                  y + 34,
                  260,
                  12,
                  Text.translatable("suitecore.option.disabled_hint"),
                  Tooltip.of(Text.literal("Enable AutoSwapper to configure profiles and rules."))
               )
            );
         } else {
            if (SuiteRuntime.isEnabled(SuiteFeature.HOLE_PUNCHER)) {
               y += 28;
               boolean hpEnabled = cfg.QolConfig.holePuncherEnabled;
               String hpSlotText;
               if (!hpEnabled) {
                  hpSlotText = "OFF";
               } else {
                  int slot = cfg.QolConfig.holePuncherAutoSwapSlot;
                  if (slot < 1) {
                     slot = 1;
                  }

                  if (slot > 9) {
                     slot = 9;
                  }

                  hpSlotText = "Slot " + slot;
               }

               screen.addContentWidget(
                  new HoverLabelWidget(
                     x,
                     y + 6,
                     220,
                     12,
                     Text.literal("HolePuncher Tool"),
                     Tooltip.of(Text.literal("When HolePuncher is enabled, pressing its hotkey will switch to this hotbar slot first."))
                  )
               );
               ButtonWidget hpSlotBtn = StyledButton.of(Text.literal(hpSlotText), b -> {
                  if (cfg.QolConfig.holePuncherEnabled) {
                     cfg.QolConfig.cycleHolePuncherAutoSwapSlot();
                     cfg.markDirty();
                     screen.rebuildPreserveScroll();
                  }
               }).dimensions(toggleX - 60, y, toggleW + 60, 20).build();
               hpSlotBtn.setTooltip(Tooltip.of(Text.literal(hpEnabled ? "Click to cycle slot 1..9." : "HolePuncher is OFF.")));
               screen.addContentWidget(hpSlotBtn);
            }

            y += 45;
            int debounceLabelY = y;
            int debounceSliderY = debounceLabelY + 16;
            screen.addContentWidget(
               new HoverLabelWidget(
                  x,
                  debounceLabelY,
                  140,
                  12,
                  Text.literal("Debounce (ms)"),
                  Tooltip.of(
                     Text.literal(
                        "Adjusts the delay before AutoSwapper swaps tools.\nLower = faster response.\nHigher = safer on touchy servers.\nThis applies to all profiles.\nDefault: 100 ms"
                     )
                  )
               )
            );
            SliderWidget debounceSlider = new StyledSlider(x, debounceSliderY, w, 20, Text.empty(), msToSlider(cfg.QolConfig.autoSwapperDebounceMs)) {
               {
                  this.updateMessage();
                  this.setTooltip(Tooltip.of(Text.literal("Current debounce delay in milliseconds.")));
               }

               @Override
               protected void updateMessage() {
                  int ms = AutoSwapperSubTab.sliderToMs(this.value);
                  this.setMessage(Text.literal(ms + " ms"));
               }

               @Override
               protected void applyValue() {
                  cfg.QolConfig.autoSwapperDebounceMs = AutoSwapperSubTab.sliderToMs(this.value);
                  cfg.markDirty();
               }
            };
            screen.addContentWidget(debounceSlider);
            y = debounceSliderY + 50;
            screen.addContentWidget(
               new HoverLabelWidget(
                  x,
                  y + 6,
                  120,
                  12,
                  Text.literal("Profile"),
                  Tooltip.of(Text.literal("AutoSwapper profiles let you keep separate setups for different tasks."))
               )
            );
            int deleteW = 70;
            int addW = 70;
            int profileW = w - deleteW - addW - 12;
            ButtonWidget profileButton = StyledButton.of(Text.literal(currentProfileTitle(cfg.QolConfig)), b -> {
               if (!cfg.QolConfig.autoSwapperProfiles.isEmpty()) {
                  cfg.QolConfig.autoSwapperActiveProfile++;
                  if (cfg.QolConfig.autoSwapperActiveProfile >= cfg.QolConfig.autoSwapperProfiles.size()) {
                     cfg.QolConfig.autoSwapperActiveProfile = 0;
                  }

                  cfg.markDirty();
                  screen.rebuildPreserveScroll();
               }
            }).dimensions(x, y + 20, profileW, 20).build();
            profileButton.setTooltip(Tooltip.of(Text.literal("Cycles through your AutoSwapper profiles.")));
            screen.addContentWidget(profileButton);
            ButtonWidget addProfileButton = StyledButton.of(Text.literal("+ Add"), b -> {
               QolConfig.AutoSwapperProfile p = new QolConfig.AutoSwapperProfile();
               p.name = nextProfileName(cfg.QolConfig);
               cfg.QolConfig.autoSwapperProfiles.add(p);
               cfg.QolConfig.autoSwapperActiveProfile = cfg.QolConfig.autoSwapperProfiles.size() - 1;
               cfg.markDirty();
               screen.rebuildPreserveScroll();
            }).dimensions(x + profileW + 6, y + 20, addW, 20).build();
            addProfileButton.setTooltip(Tooltip.of(Text.literal("Adds a new AutoSwapper profile.")));
            screen.addContentWidget(addProfileButton);
            ButtonWidget deleteProfileButton = StyledButton.of(Text.literal("Delete"), b -> {
               if (!cfg.QolConfig.autoSwapperProfiles.isEmpty()) {
                  int idx = clampActiveProfileIndex(cfg.QolConfig);
                  cfg.QolConfig.autoSwapperProfiles.remove(idx);
                  if (cfg.QolConfig.autoSwapperActiveProfile >= cfg.QolConfig.autoSwapperProfiles.size()) {
                     cfg.QolConfig.autoSwapperActiveProfile = Math.max(0, cfg.QolConfig.autoSwapperProfiles.size() - 1);
                  }

                  cfg.markDirty();
                  screen.rebuildPreserveScroll();
               }
            }).dimensions(x + profileW + 6 + addW + 6, y + 20, deleteW, 20).build();
            deleteProfileButton.setTooltip(Tooltip.of(Text.literal("Deletes the current AutoSwapper profile.")));
            screen.addContentWidget(deleteProfileButton);
            QolConfig.AutoSwapperProfile profile = getActiveProfile(cfg.QolConfig);
            if (profile == null) {
               screen.addContentWidget(
                  new HoverLabelWidget(
                     x,
                     y + 52,
                     280,
                     12,
                     Text.literal("No profiles yet. Add one to start."),
                     Tooltip.of(Text.literal("Create a profile for mining, map art, or any other setup you want."))
                  )
               );
            } else {
               TextFieldWidget profileNameField = new TextFieldWidget(screen.getTextRenderer(), x, y + 46, w, 20, Text.empty());
               profileNameField.setMaxLength(32);
               profileNameField.setText(profile.name == null ? "" : profile.name);
               profileNameField.setTooltip(Tooltip.of(Text.literal("Rename the current AutoSwapper profile.")));
               profileNameField.setChangedListener(newText -> {
                  profile.name = newText != null && !newText.isBlank() ? newText : "Profile";
                  cfg.markDirty();
               });
               screen.addContentWidget(profileNameField);
               y += 76;
               screen.addContentWidget(
                  new HoverLabelWidget(
                     x,
                     y + 6,
                     140,
                     12,
                     Text.literal("Default Slot"),
                     Tooltip.of(Text.literal("When no rule matches, this profile can fall back to a default hotbar slot."))
                  )
               );
               int defaultToggleW = 120;
               int defaultToggleX = x + w - defaultToggleW;
               ButtonWidget defaultToggle = StyledButton.of(Text.literal(profile.useDefaultTool ? "Use Default" : "No Default"), b -> {
                  profile.useDefaultTool = !profile.useDefaultTool;
                  cfg.markDirty();
                  screen.rebuildPreserveScroll();
               }).dimensions(defaultToggleX, y, defaultToggleW, 20).build();
               defaultToggle.setTooltip(Tooltip.of(Text.literal("Enable or disable use of the default slot when no rule applies for this profile.")));
               screen.addContentWidget(defaultToggle);
               if (profile.useDefaultTool) {
                  int slotW = 90;
                  int slotX = defaultToggleX - 6 - slotW;
                  ButtonWidget slotButton = StyledButton.of(Text.literal("Slot " + profile.defaultSlot), b -> {
                     profile.defaultSlot = nextSlot(profile.defaultSlot);
                     cfg.markDirty();
                     screen.rebuildPreserveScroll();
                  }).dimensions(slotX, y, slotW, 20).build();
                  slotButton.setTooltip(Tooltip.of(Text.literal("Cycles the default hotbar slot used when no rule matches for this profile.")));
                  screen.addContentWidget(slotButton);
               }

               this.addRulesSection(screen, cfg, profile, x, w, y, 20, 26, 6);
            }
         }
      }
   }

   private int addRulesSection(
      SuiteSettingsScreen screen, SuiteConfig cfg, QolConfig.AutoSwapperProfile profile, int x, int w, int y, int rowH, int gapY, int gapX
   ) {
      y += 45;
      screen.addContentWidget(
         new HoverLabelWidget(
            x,
            y,
            240,
            12,
            Text.literal("Rules (Top rule has priority)"),
            Tooltip.of(Text.literal("Each rule matches a block group or exact block and picks a hotbar slot.\nHigher rules win if more than one matches."))
         )
      );
      y += 18;
      int typeW = 90;
      int slotW = 70;
      int moveW = 22;
      int removeW = 22;
      int pickW = 54;
      int slotX = x + w - 136;
      int upX = x + w - 66;
      int downX = x + w - 44;
      int removeX = x + w - 22;
      int fieldX = x + 90 + gapX;
      int fieldW = slotX - fieldX - gapX;

      for (int i = 0; i < profile.rules.size(); i++) {
         QolConfig.AutoSwapRule rule = profile.rules.get(i);
         int rowY = y + i * gapY;
         ButtonWidget typeButton = StyledButton.of(Text.literal(typeLabel(rule.type)), b -> {
            rule.type = nextTargetType(rule.type);
            if (rule.type == QolConfig.AutoSwapTargetType.CUSTOM) {
               QolConfig.AutoSwapCustomGroup group = getActiveCustomGroup(cfg.QolConfig);
               if (group != null && (rule.customGroupId == null || rule.customGroupId.isBlank())) {
                  rule.customGroupId = group.id;
               }
            }

            cfg.markDirty();
            screen.rebuildPreserveScroll();
         }).dimensions(x, rowY, 90, rowH).build();
         typeButton.setTooltip(Tooltip.of(Text.literal("Cycles between built-in groups, exact blocks, and custom block groups.")));
         screen.addContentWidget(typeButton);
         if (rule.type == QolConfig.AutoSwapTargetType.GROUP) {
            ButtonWidget groupButton = StyledButton.of(Text.translatable(groupTitleKey(rule.group)), b -> {
               rule.group = nextGroup(rule.group);
               cfg.markDirty();
               screen.rebuildPreserveScroll();
            }).dimensions(fieldX, rowY, fieldW, rowH).build();
            groupButton.setTooltip(Tooltip.of(Text.literal("Cycles the block grouping used by this rule.")));
            screen.addContentWidget(groupButton);
         } else if (rule.type == QolConfig.AutoSwapTargetType.CUSTOM) {
            ButtonWidget customButton = StyledButton.of(Text.literal(customRuleGroupTitle(cfg.QolConfig, rule)), b -> {
               cycleRuleCustomGroup(cfg.QolConfig, rule);
               cfg.markDirty();
               screen.rebuildPreserveScroll();
            }).dimensions(fieldX, rowY, fieldW, rowH).build();
            customButton.setTooltip(Tooltip.of(Text.literal("Cycles through your custom AutoSwapper block groups.")));
            customButton.active = !cfg.QolConfig.autoSwapperCustomGroups.isEmpty();
            screen.addContentWidget(customButton);
         } else {
            int actualFieldW = fieldW - gapX - 54;
            ButtonWidget pickButton = StyledButton.of(Text.literal("Pick"), b -> {
               MinecraftClient mc = MinecraftClient.getInstance();
               if (mc.crosshairTarget instanceof BlockHitResult bhr && mc.crosshairTarget.getType() == Type.BLOCK && mc.world != null) {
                  BlockState state = mc.world.getBlockState(bhr.getBlockPos());
                  Identifier id = Registries.BLOCK.getId(state.getBlock());
                  if (id != null) {
                     rule.blockId = id.toString();
                     cfg.markDirty();
                     screen.rebuildPreserveScroll();
                  }
               }
            }).dimensions(fieldX, rowY, 54, rowH).build();
            pickButton.setTooltip(Tooltip.of(Text.literal("Uses the block you are currently looking at.")));
            screen.addContentWidget(pickButton);
            TextFieldWidget field = new TextFieldWidget(screen.getTextRenderer(), fieldX + 54 + gapX, rowY, actualFieldW, rowH, Text.empty());
            field.setMaxLength(128);
            field.setText(rule.blockId == null ? "" : rule.blockId);
            field.setTooltip(Tooltip.of(Text.literal("Enter a block id, such as minecraft:stone")));
            field.setChangedListener(newText -> {
               rule.blockId = newText;
               cfg.markDirty();
            });
            screen.addContentWidget(field);
         }

         ButtonWidget slotButton = StyledButton.of(Text.literal("Slot " + rule.slot), b -> {
            rule.slot = nextSlot(rule.slot);
            cfg.markDirty();
            screen.rebuildPreserveScroll();
         }).dimensions(slotX, rowY, 70, rowH).build();
         slotButton.setTooltip(Tooltip.of(Text.literal("Selects the hotbar slot to swap to when this rule matches.")));
         screen.addContentWidget(slotButton);
         ButtonWidget upButton = StyledButton.of(Text.literal("^"), b -> {
            int idx = profile.rules.indexOf(rule);
            if (idx > 0) {
               Collections.swap(profile.rules, idx, idx - 1);
               cfg.markDirty();
               screen.rebuildPreserveScroll();
            }
         }).dimensions(upX, rowY, 22, rowH).build();
         upButton.setTooltip(Tooltip.of(Text.literal("Moves this rule up. Higher rules have higher priority.")));
         screen.addContentWidget(upButton);
         ButtonWidget downButton = StyledButton.of(Text.literal("v"), b -> {
            int idx = profile.rules.indexOf(rule);
            if (idx < profile.rules.size() - 1) {
               Collections.swap(profile.rules, idx, idx + 1);
               cfg.markDirty();
               screen.rebuildPreserveScroll();
            }
         }).dimensions(downX, rowY, 22, rowH).build();
         downButton.setTooltip(Tooltip.of(Text.literal("Moves this rule down.")));
         screen.addContentWidget(downButton);
         ButtonWidget removeButton = StyledButton.of(Text.literal("X"), b -> {
            profile.rules.remove(rule);
            cfg.markDirty();
            screen.rebuildPreserveScroll();
         }).dimensions(removeX, rowY, 22, rowH).build();
         removeButton.setTooltip(Tooltip.of(Text.literal("Removes this rule.")));
         screen.addContentWidget(removeButton);
      }

      int addY = y + profile.rules.size() * gapY + 8;
      ButtonWidget addRuleButton = StyledButton.of(Text.literal("+ Add Rule"), b -> {
         QolConfig.AutoSwapRule r = new QolConfig.AutoSwapRule();
         profile.rules.add(r);
         cfg.markDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x, addY, 120, rowH).build();
      addRuleButton.setTooltip(Tooltip.of(Text.literal("Adds a new AutoSwapper rule.")));
      screen.addContentWidget(addRuleButton);
      return addY + rowH + 12;
   }

   @Override
   public void removed() {
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, int contentTopOffset) {
      if (!this.customGroupsOnly || this.editingDraftBlockId == null) {
         return false;
      }

      if (mouseX < this.blockEditorListLeft || mouseX > this.blockEditorListRight) {
         return false;
      }

      if (!(mouseY < this.blockEditorListTop) && !(mouseY > this.blockEditorListBottom)) {
         int maxScroll = Math.max(0, filteredBlockChoices(this.blockEditorSearch).size() - 6);
         if (maxScroll <= 0) {
            return false;
         }

         this.blockEditorScroll = this.blockEditorScroll - (int)Math.signum(verticalAmount);
         this.blockEditorScroll = Math.max(0, Math.min(this.blockEditorScroll, maxScroll));
         return true;
      } else {
         return false;
      }
   }

   private int addCustomGroupSection(SuiteSettingsScreen screen, QolConfig cfg, int x, int w, int y, int rowH, int gapX) {
      QolConfig.AutoSwapCustomGroup active = getActiveCustomGroup(cfg);
      if (this.importingGroupCode) {
         return this.addCustomGroupImportEditor(screen, cfg, x, w, y, rowH, gapX);
      }

      if (this.editingDraftBlockId != null && active != null) {
         return this.addCustomGroupBlockEditor(screen, active, x, w, y, rowH, gapX);
      }

      screen.addContentWidget(
         new HoverLabelWidget(x, y, 220, 12, Text.literal("Custom Groups"), Tooltip.of(Text.literal("Create reusable block groups for AutoSwapper rules.")))
      );
      y += 18;
      int importW = 76;
      int exportW = 76;
      ButtonWidget importButton = StyledButton.of(Text.literal("Import"), b -> {
         this.importingGroupCode = true;
         this.groupImportCode = "";
         this.closeBlockEditor();
         screen.rebuildFromTab();
      }).dimensions(x, y, importW, rowH).build();
      importButton.setTooltip(Tooltip.of(Text.literal("Paste an AutoSwapper group share code.")));
      screen.addContentWidget(importButton);
      ButtonWidget exportButton = StyledButton.of(Text.literal("Export"), b -> {
         QolConfig.AutoSwapCustomGroup groupx = getActiveCustomGroup(cfg);
         if (groupx != null) {
            copyToClipboard(GroupShareCodec.exportAutoSwapGroup(groupx));
            this.showGroupShareNotice("Code copied to clipboard.");
            screen.rebuildPreserveScroll();
         }
      }).dimensions(x + importW + gapX, y, exportW, rowH).build();
      exportButton.active = active != null;
      exportButton.setTooltip(Tooltip.of(Text.literal("Copies the selected AutoSwapper group share code.")));
      screen.addContentWidget(exportButton);
      y += 28;
      if (this.hasGroupShareNotice()) {
         screen.addContentWidget(
            new HoverLabelWidget(x, y + 2, Math.min(w, 260), 12, Text.literal(this.groupShareNotice), Tooltip.of(Text.literal(this.groupShareNotice)))
         );
         y += 18;
      }

      int deleteW = 70;
      int addW = 70;
      int labelW = w - deleteW - addW - gapX * 2;
      screen.addContentWidget(
         new HoverLabelWidget(x, y + 4, labelW, 12, Text.literal("Groups"), Tooltip.of(Text.literal("Select the custom block group you want to edit.")))
      );
      ButtonWidget addButton = StyledButton.of(Text.literal("+ New"), b -> {
         QolConfig.AutoSwapCustomGroup groupx = new QolConfig.AutoSwapCustomGroup();
         groupx.id = nextCustomGroupId(cfg);
         groupx.name = nextCustomGroupName(cfg);
         cfg.autoSwapperCustomGroups.add(groupx);
         cfg.autoSwapperActiveCustomGroup = cfg.autoSwapperCustomGroups.size() - 1;
         this.closeBlockEditor();
         SuiteConfig.INSTANCE.markDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + labelW + gapX, y, addW, rowH).build();
      addButton.setTooltip(Tooltip.of(Text.literal("Creates a new custom block group.")));
      screen.addContentWidget(addButton);
      ButtonWidget deleteButton = StyledButton.of(Text.literal("Delete"), b -> {
         QolConfig.AutoSwapCustomGroup groupx = getActiveCustomGroup(cfg);
         if (groupx != null) {
            String removedId = groupx.id;
            cfg.autoSwapperCustomGroups.remove(groupx);
            if (cfg.autoSwapperActiveCustomGroup >= cfg.autoSwapperCustomGroups.size()) {
               cfg.autoSwapperActiveCustomGroup = Math.max(0, cfg.autoSwapperCustomGroups.size() - 1);
            }

            for (QolConfig.AutoSwapperProfile profile : cfg.autoSwapperProfiles) {
               for (QolConfig.AutoSwapRule rule : profile.rules) {
                  if (rule.type == QolConfig.AutoSwapTargetType.CUSTOM && removedId.equals(rule.customGroupId)) {
                     rule.customGroupId = "";
                  }
               }
            }

            SuiteConfig.INSTANCE.markDirty();
            screen.rebuildPreserveScroll();
         }
      }).dimensions(x + labelW + gapX + addW + gapX, y, deleteW, rowH).build();
      deleteButton.active = !cfg.autoSwapperCustomGroups.isEmpty();
      deleteButton.setTooltip(Tooltip.of(Text.literal("Deletes the selected custom group.")));
      screen.addContentWidget(deleteButton);
      y += 24;

      for (int i = 0; i < cfg.autoSwapperCustomGroups.size(); i++) {
         QolConfig.AutoSwapCustomGroup group = cfg.autoSwapperCustomGroups.get(i);
         int idx = i;
         String name = group.name != null && !group.name.isBlank() ? group.name : "Custom Group";
         ButtonWidget groupRow = StyledButton.of(Text.literal(name), b -> {
            cfg.autoSwapperActiveCustomGroup = idx;
            this.closeBlockEditor();
            SuiteConfig.INSTANCE.markDirty();
            screen.rebuildPreserveScroll();
         }).dimensions(x, y, w, rowH).build();
         groupRow.active = i != cfg.autoSwapperActiveCustomGroup;
         screen.addContentWidget(groupRow);
         y += 24;
      }

      y += 6;
      QolConfig.AutoSwapCustomGroup group = active;
      if (group == null) {
         screen.addContentWidget(
            new HoverLabelWidget(
               x,
               y,
               Math.min(w, 340),
               12,
               Text.literal("Create a group to add blocks and use it in rules."),
               Tooltip.of(Text.literal("Custom groups can be selected by rules using the Custom target type."))
            )
         );
         return y + 24;
      }

      screen.addContentWidget(new HoverLabelWidget(x, y + 2, 180, 12, Text.literal("Group Name"), Tooltip.of(Text.literal("Name shown in AutoSwapper rules."))));
      y += 16;
      TextFieldWidget nameField = new TextFieldWidget(screen.getTextRenderer(), x, y, w, rowH, Text.empty());
      nameField.setMaxLength(48);
      nameField.setText(group.name == null ? "" : group.name);
      nameField.setChangedListener(s -> {
         group.name = s != null && !s.isBlank() ? s : "Custom Group";
         SuiteConfig.INSTANCE.markDirty();
      });
      screen.addContentWidget(nameField);
      y += 28;
      screen.addContentWidget(
         new HoverLabelWidget(
            x,
            y,
            180,
            12,
            Text.literal("Group Blocks (" + group.blockIds.size() + ")"),
            Tooltip.of(Text.literal("Blocks in this group can be selected by AutoSwapper rules using the Custom target type."))
         )
      );
      ButtonWidget addBlock = StyledButton.of(Text.literal("+ Add Block"), b -> {
         this.startAddingBlock();
         screen.rebuildFromTab();
      }).dimensions(x + w - 104, y - 4, 104, rowH).build();
      screen.addContentWidget(addBlock);
      y += 16;
      if (group.blockIds.isEmpty()) {
         screen.addContentWidget(
            new HoverLabelWidget(
               x, y + 4, Math.min(w, 280), 12, Text.literal("No blocks added yet."), Tooltip.of(Text.literal("Use Add Block to search and add blocks."))
            )
         );
         return y + 24;
      }

      for (String id : group.blockIds) {
         AutoSwapperSubTab.BlockChoice choice = choiceById(id);
         String label = shorten(choice == null ? id : choice.label, 42);
         Block block = choice == null ? null : choice.block;
         BlockEntryButtonWidget row = new BlockEntryButtonWidget(x, y, w - 76, 22, block, Text.literal(label), () -> {});
         row.setTooltip(Tooltip.of(Text.literal(id)));
         screen.addContentWidget(row);
         String removeId = id;
         ButtonWidget remove = StyledButton.of(Text.literal("Remove"), b -> {
            group.blockIds.remove(removeId);
            SuiteConfig.INSTANCE.markDirty();
            screen.rebuildPreserveScroll();
         }).dimensions(x + w - 74, y + 1, 74, rowH).build();
         screen.addContentWidget(remove);
         y += 24;
      }

      return y;
   }

   private int addCustomGroupImportEditor(SuiteSettingsScreen screen, QolConfig cfg, int x, int w, int y, int rowH, int gapX) {
      screen.addContentWidget(
         new HoverLabelWidget(
            x, y + 4, 220, 12, Text.literal("Import AutoSwapper Group"), Tooltip.of(Text.literal("Paste a BSWAP1 share code from another player."))
         )
      );
      ButtonWidget cancel = StyledButton.of(Text.literal("Cancel"), b -> {
         this.importingGroupCode = false;
         this.groupImportCode = "";
         screen.rebuildFromTab();
      }).dimensions(x + w - 80, y, 80, rowH).build();
      screen.addContentWidget(cancel);
      y += 28;
      TextFieldWidget field = new TextFieldWidget(screen.getTextRenderer(), x, y, w, rowH, Text.empty());
      field.setMaxLength(12000);
      field.setText(this.groupImportCode);
      field.setPlaceholder(Text.literal("Paste share code..."));
      field.setChangedListener(s -> this.groupImportCode = s == null ? "" : s.trim());
      screen.addContentWidget(field);
      y += 28;
      ButtonWidget importButton = StyledButton.of(Text.literal("Import Group"), b -> {
         GroupShareCodec.ImportResult result = GroupShareCodec.importAutoSwapGroup(cfg, this.groupImportCode);
         if (result.success()) {
            this.importingGroupCode = false;
            this.groupImportCode = "";
            ConfigIO.saveIfDirty();
            this.showGroupShareNotice("Imported group: " + result.name());
            screen.rebuildFromTab();
         } else {
            this.showGroupShareNotice(result.message());
            screen.rebuildPreserveScroll();
         }
      }).dimensions(x, y, 120, rowH).build();
      screen.addContentWidget(importButton);
      return y + 32;
   }

   private int addCustomGroupBlockEditor(SuiteSettingsScreen screen, QolConfig.AutoSwapCustomGroup group, int x, int w, int y, int rowH, int gapX) {
      screen.addContentWidget(
         new HoverLabelWidget(x, y + 4, 180, 12, Text.literal("Add Block"), Tooltip.of(Text.literal("Choose the block for this custom AutoSwapper group.")))
      );
      ButtonWidget back = StyledButton.of(Text.literal("Back"), b -> {
         this.closeBlockEditor();
         screen.rebuildFromTab();
      }).dimensions(x + w - 74, y, 74, rowH).build();
      screen.addContentWidget(back);
      y += 28;
      TextFieldWidget searchField = new TextFieldWidget(screen.getTextRenderer(), x, y, w, rowH, Text.empty());
      searchField.setMaxLength(80);
      searchField.setText(this.blockEditorSearch);
      searchField.setPlaceholder(Text.literal("Search blocks..."));
      searchField.setChangedListener(s -> {
         String nextSearch = s == null ? "" : s;
         if (!nextSearch.equals(this.blockEditorSearch)) {
            this.blockEditorSearch = nextSearch;
            this.blockEditorScroll = 0;
            screen.rebuildPreserveScroll();
         }
      });
      screen.addContentWidget(searchField);
      y += 26;
      List<AutoSwapperSubTab.BlockChoice> filtered = filteredBlockChoices(this.blockEditorSearch);
      int maxScroll = Math.max(0, filtered.size() - 6);
      this.blockEditorScroll = Math.max(0, Math.min(this.blockEditorScroll, maxScroll));
      int start = this.blockEditorScroll;
      int end = Math.min(filtered.size(), start + 6);
      screen.addContentWidget(
         new HoverLabelWidget(
            x,
            y + 4,
            Math.min(w, 260),
            12,
            Text.literal("Blocks " + (filtered.isEmpty() ? 0 : start + 1) + "-" + end + " of " + filtered.size() + this.selectedBlockCountLabel()),
            Tooltip.of(
               Text.literal("Scroll this block list with the mouse wheel while hovering over it. Use the open space to the right to scroll the main page.")
            )
         )
      );
      y += 24;
      this.blockEditorListLeft = x + editorScrollGutter(w, 36);
      this.blockEditorListRight = x + w - editorScrollGutter(w, 36);
      this.blockEditorListTop = y;
      this.blockEditorListBottom = y + 144;

      for (int i = start; i < end; i++) {
         AutoSwapperSubTab.BlockChoice choice = filtered.get(i);
         boolean selectedChoice = this.blockEditorSelectedIds.contains(choice.id);
         BlockEntryButtonWidget row = new BlockEntryButtonWidget(x, y, w, 22, choice.block, Text.literal(choice.label), () -> {
            this.toggleSelectedBlock(choice.id);
            this.editingDraftBlockId = this.firstSelectedBlockId();
            SuiteConfig.INSTANCE.markDirty();
            screen.rebuildPreserveScroll();
         });
         row.setHighlighted(selectedChoice);
         row.setTooltip(Tooltip.of(Text.literal(choice.id)));
         screen.addContentWidget(row);
         y += 24;
      }

      if (filtered.isEmpty()) {
         screen.addContentWidget(new HoverLabelWidget(x, y + 4, Math.min(w, 280), 12, Text.literal("No matching blocks."), null));
         y += 24;
      }

      y = Math.max(y, this.blockEditorListBottom);
      y += 8;
      ButtonWidget save = StyledButton.of(Text.literal(this.addBlockSaveLabel()), b -> {
         this.saveBlockEditor(group);
         SuiteConfig.INSTANCE.markDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x, y, 128, rowH).build();
      save.active = !this.blockEditorSelectedIds.isEmpty();
      screen.addContentWidget(save);
      return y + 28;
   }

   private void startAddingBlock() {
      this.editingDraftBlockId = "";
      this.blockEditorSelectedIds.clear();
      this.blockEditorSearch = "";
      this.blockEditorScroll = 0;
   }

   private void closeBlockEditor() {
      this.editingDraftBlockId = null;
      this.blockEditorSelectedIds.clear();
      this.blockEditorSearch = "";
      this.blockEditorScroll = 0;
      this.blockEditorListLeft = -1;
      this.blockEditorListRight = -1;
      this.blockEditorListTop = -1;
      this.blockEditorListBottom = -1;
   }

   private static void copyToClipboard(String value) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.keyboard != null && value != null) {
         client.keyboard.setClipboard(value);
      }
   }

   private void showGroupShareNotice(String message) {
      this.groupShareNotice = message == null ? "" : message;
      this.groupShareNoticeUntilMs = System.currentTimeMillis() + 3000L;
   }

   private boolean hasGroupShareNotice() {
      if (this.groupShareNotice != null && !this.groupShareNotice.isBlank()) {
         if (System.currentTimeMillis() <= this.groupShareNoticeUntilMs) {
            return true;
         }

         this.groupShareNotice = "";
         this.groupShareNoticeUntilMs = 0L;
         return false;
      } else {
         return false;
      }
   }

   private void saveBlockEditor(QolConfig.AutoSwapCustomGroup group) {
      if (group != null && !this.blockEditorSelectedIds.isEmpty()) {
         for (String blockId : this.blockEditorSelectedIds) {
            if (blockId != null && !blockId.isBlank() && !group.blockIds.contains(blockId)) {
               group.blockIds.add(blockId);
            }
         }

         this.closeBlockEditor();
      } else {
         this.closeBlockEditor();
      }
   }

   private void toggleSelectedBlock(String blockId) {
      if (blockId != null && !blockId.isBlank()) {
         if (!this.blockEditorSelectedIds.remove(blockId)) {
            this.blockEditorSelectedIds.add(blockId);
         }
      }
   }

   private String firstSelectedBlockId() {
      return this.blockEditorSelectedIds.isEmpty() ? "" : this.blockEditorSelectedIds.iterator().next();
   }

   private String selectedBlockCountLabel() {
      return this.blockEditorSelectedIds.isEmpty() ? "" : " | Selected " + this.blockEditorSelectedIds.size();
   }

   private String addBlockSaveLabel() {
      int count = this.blockEditorSelectedIds.size();
      return count <= 1 ? "Add Block" : "Add " + count + " Blocks";
   }

   private static QolConfig.AutoSwapTargetType nextTargetType(QolConfig.AutoSwapTargetType type) {
      if (type == QolConfig.AutoSwapTargetType.GROUP) {
         return QolConfig.AutoSwapTargetType.BLOCK;
      } else {
         return type == QolConfig.AutoSwapTargetType.BLOCK ? QolConfig.AutoSwapTargetType.CUSTOM : QolConfig.AutoSwapTargetType.GROUP;
      }
   }

   private static String typeLabel(QolConfig.AutoSwapTargetType type) {
      if (type == QolConfig.AutoSwapTargetType.BLOCK) {
         return "Block";
      } else {
         return type == QolConfig.AutoSwapTargetType.CUSTOM ? "Custom" : "Group";
      }
   }

   private static QolConfig.AutoSwapCustomGroup getActiveCustomGroup(QolConfig cfg) {
      if (cfg != null && !cfg.autoSwapperCustomGroups.isEmpty()) {
         if (cfg.autoSwapperActiveCustomGroup < 0) {
            cfg.autoSwapperActiveCustomGroup = 0;
         }

         if (cfg.autoSwapperActiveCustomGroup >= cfg.autoSwapperCustomGroups.size()) {
            cfg.autoSwapperActiveCustomGroup = cfg.autoSwapperCustomGroups.size() - 1;
         }

         return cfg.autoSwapperCustomGroups.get(cfg.autoSwapperActiveCustomGroup);
      } else {
         return null;
      }
   }

   private static String currentCustomGroupTitle(QolConfig cfg) {
      QolConfig.AutoSwapCustomGroup group = getActiveCustomGroup(cfg);
      if (group == null) {
         return "No Custom Groups";
      } else {
         return group.name != null && !group.name.isBlank() ? group.name : "Custom Group";
      }
   }

   private static String nextCustomGroupId(QolConfig cfg) {
      long now = System.currentTimeMillis();
      int n = cfg == null ? 1 : cfg.autoSwapperCustomGroups.size() + 1;
      return "custom-" + now + "-" + n;
   }

   private static String nextCustomGroupName(QolConfig cfg) {
      int n = cfg == null ? 1 : cfg.autoSwapperCustomGroups.size() + 1;
      return "Custom Group " + n;
   }

   private static int editorScrollGutter(int contentWidth, int preferredGutter) {
      return contentWidth <= 160 ? 0 : Math.min(preferredGutter, Math.max(0, (contentWidth - 120) / 2));
   }

   private static String customRuleGroupTitle(QolConfig cfg, QolConfig.AutoSwapRule rule) {
      if (cfg != null && !cfg.autoSwapperCustomGroups.isEmpty()) {
         if (rule.customGroupId != null && !rule.customGroupId.isBlank()) {
            for (QolConfig.AutoSwapCustomGroup group : cfg.autoSwapperCustomGroups) {
               if (group != null && rule.customGroupId.equals(group.id)) {
                  return safeGroupName(group);
               }
            }

            return "Missing Group";
         } else {
            QolConfig.AutoSwapCustomGroup active = getActiveCustomGroup(cfg);
            return active == null ? "No Custom Groups" : safeGroupName(active);
         }
      } else {
         return "No Custom Groups";
      }
   }

   private static void cycleRuleCustomGroup(QolConfig cfg, QolConfig.AutoSwapRule rule) {
      if (cfg != null && rule != null && !cfg.autoSwapperCustomGroups.isEmpty()) {
         int idx = -1;

         for (int i = 0; i < cfg.autoSwapperCustomGroups.size(); i++) {
            QolConfig.AutoSwapCustomGroup group = cfg.autoSwapperCustomGroups.get(i);
            if (group != null && group.id != null && group.id.equals(rule.customGroupId)) {
               idx = i;
               break;
            }
         }

         int next = (idx + 1) % cfg.autoSwapperCustomGroups.size();
         QolConfig.AutoSwapCustomGroup group = cfg.autoSwapperCustomGroups.get(next);
         rule.customGroupId = group == null ? "" : group.id;
      }
   }

   private static String safeGroupName(QolConfig.AutoSwapCustomGroup group) {
      return group != null && group.name != null && !group.name.isBlank() ? group.name : "Custom Group";
   }

   private static List<AutoSwapperSubTab.BlockChoice> filteredBlockChoices(String search) {
      String q = normalize(search);
      if (q.isBlank()) {
         return BLOCK_CHOICES;
      }

      List<AutoSwapperSubTab.BlockChoice> out = new ArrayList<>();

      for (AutoSwapperSubTab.BlockChoice choice : BLOCK_CHOICES) {
         if (choice.normalizedLabel.contains(q) || choice.normalizedId.contains(q)) {
            out.add(choice);
         }
      }

      return out;
   }

   private static AutoSwapperSubTab.BlockChoice choiceById(String id) {
      if (id != null && !id.isBlank()) {
         for (AutoSwapperSubTab.BlockChoice choice : BLOCK_CHOICES) {
            if (id.equals(choice.id)) {
               return choice;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static String defaultBlockId() {
      return BLOCK_CHOICES.isEmpty() ? "minecraft:stone" : BLOCK_CHOICES.get(0).id;
   }

   private static String shorten(String value, int maxLength) {
      if (value == null) {
         return "";
      } else {
         return value.length() <= maxLength ? value : value.substring(0, Math.max(0, maxLength - 3)) + "...";
      }
   }

   private static List<AutoSwapperSubTab.BlockChoice> buildBlockChoices() {
      List<AutoSwapperSubTab.BlockChoice> out = new ArrayList<>();

      for (Block block : Registries.BLOCK) {
         if (block != null) {
            ItemStack stack = new ItemStack(block.asItem());
            if (!stack.isEmpty()) {
               Identifier id = Registries.BLOCK.getId(block);
               if (id != null) {
                  String label = stack.getName().getString();
                  if (label == null || label.isBlank()) {
                     label = prettyBlockName(id.toString());
                  }

                  out.add(new AutoSwapperSubTab.BlockChoice(block, id.toString(), label));
               }
            }
         }
      }

      out.sort(Comparator.comparing(choice -> choice.label, String.CASE_INSENSITIVE_ORDER));
      return List.copyOf(out);
   }

   private static String prettyBlockName(String blockId) {
      String path = blockId == null ? "" : blockId;
      int colon = path.indexOf(58);
      if (colon >= 0 && colon + 1 < path.length()) {
         path = path.substring(colon + 1);
      }

      String[] parts = path.replace('_', ' ').split("\\s+");
      StringBuilder sb = new StringBuilder();

      for (String part : parts) {
         if (!part.isBlank()) {
            if (sb.length() > 0) {
               sb.append(' ');
            }

            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
               sb.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
         }
      }

      return sb.length() == 0 ? "Unknown Block" : sb.toString();
   }

   private static String normalize(String raw) {
      return raw == null ? "" : raw.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
   }

   private static int nextSlot(int slot) {
      int s = slot + 1;
      return s > 9 ? 1 : s;
   }

   private static QolConfig.AutoSwapGrouping nextGroup(QolConfig.AutoSwapGrouping g) {
      QolConfig.AutoSwapGrouping[] all = QolConfig.AutoSwapGrouping.values();
      return all[(g.ordinal() + 1) % all.length];
   }

   private static String groupTitleKey(QolConfig.AutoSwapGrouping g) {
      return switch (g) {
         case ORES -> "suitecore.autoswap.group.ores";
         case LOGS -> "suitecore.autoswap.group.logs";
         case PLANKS -> "suitecore.autoswap.group.planks";
         case SHOVEL_MINEABLE -> "suitecore.autoswap.group.shovel";
         case PICKAXE_MINEABLE -> "suitecore.autoswap.group.pickaxe";
         case AXE_MINEABLE -> "suitecore.autoswap.group.axe";
         case HOE_MINEABLE -> "suitecore.autoswap.group.hoe";
         case SWORD_EFFICIENT -> "suitecore.autoswap.group.sword";
         case LEAVES -> "suitecore.autoswap.group.leaves";
         case MUSHROOMS -> "suitecore.autoswap.group.mushrooms";
         case SPAWNERS -> "suitecore.autoswap.group.spawners";
         case DIRT_LIKE -> "suitecore.autoswap.group.dirt";
         case SAND_LIKE -> "suitecore.autoswap.group.sand";
         case GLASS -> "suitecore.autoswap.group.glass";
         case WOOL -> "suitecore.autoswap.group.wool";
         case SAPLINGS -> "suitecore.autoswap.group.saplings";
         case CROPS -> "suitecore.autoswap.group.crops";
         case FLOWERS -> "suitecore.autoswap.group.flowers";
         case SMALL_FLOWERS -> "suitecore.autoswap.group.small_flowers";
         case AMETHYST -> "suitecore.autoswap.group.amethyst";
         case LIGHT_EMITTING -> "suitecore.autoswap.group.light_emitting";
         case DECORATIVE_LIGHTS -> "suitecore.autoswap.group.decorative_lights";
         case TERRACOTTA -> "suitecore.autoswap.group.terracotta";
         case CONCRETE -> "suitecore.autoswap.group.concrete";
         case ICE -> "suitecore.autoswap.group.ice";
         case RAILS -> "suitecore.autoswap.group.rails";
         case REDSTONE_COMPONENTS -> "suitecore.autoswap.group.redstone";
         case SHEARS_MINEABLE -> "suitecore.autoswap.group.shears";
      };
   }

   private static String pickLookedAtBlockId() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.world != null) {
         if (client.crosshairTarget instanceof BlockHitResult bhr) {
            BlockState state = client.world.getBlockState(bhr.getBlockPos());
            if (state == null) {
               return null;
            }

            Identifier id = Registries.BLOCK.getId(state.getBlock());
            return id == null ? null : id.toString();
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static double msToSlider(int ms) {
      ms = Math.max(0, Math.min(1000, ms));
      return (ms - 0) / 1000.0;
   }

   private static int sliderToMs(double value) {
      value = Math.max(0.0, Math.min(1.0, value));
      return (int)Math.round(0.0 + value * 1000.0);
   }

   private static QolConfig.AutoSwapperProfile getActiveProfile(QolConfig cfg) {
      if (cfg.autoSwapperProfiles.isEmpty()) {
         return null;
      }

      int idx = clampActiveProfileIndex(cfg);
      return cfg.autoSwapperProfiles.get(idx);
   }

   private static int clampActiveProfileIndex(QolConfig cfg) {
      if (cfg.autoSwapperProfiles.isEmpty()) {
         cfg.autoSwapperActiveProfile = 0;
         return 0;
      }

      if (cfg.autoSwapperActiveProfile < 0) {
         cfg.autoSwapperActiveProfile = 0;
      }

      if (cfg.autoSwapperActiveProfile >= cfg.autoSwapperProfiles.size()) {
         cfg.autoSwapperActiveProfile = cfg.autoSwapperProfiles.size() - 1;
      }

      return cfg.autoSwapperActiveProfile;
   }

   private static String currentProfileTitle(QolConfig cfg) {
      QolConfig.AutoSwapperProfile profile = getActiveProfile(cfg);
      if (profile == null) {
         return "No Profile";
      } else {
         return profile.name != null && !profile.name.isBlank() ? profile.name : "Profile";
      }
   }

   private static String nextProfileName(QolConfig cfg) {
      int n = cfg.autoSwapperProfiles.size() + 1;
      return "Profile " + n;
   }

   @Override
   public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
      SuiteConfig cfg = SuiteConfig.INSTANCE;
      if (this.customGroupsOnly) {
         return contentTopOffset + this.customGroupSectionHeight(cfg.QolConfig) + 24;
      }

      int h = contentTopOffset + 86;
      if (!cfg.QolConfig.autoSwapperEnabled) {
         return h + 50;
      }

      if (SuiteRuntime.isEnabled(SuiteFeature.HOLE_PUNCHER)) {
         h += 28;
      }

      h += 50;
      h += 76;
      QolConfig.AutoSwapperProfile profile = getActiveProfile(cfg.QolConfig);
      if (profile == null) {
         return h + 50;
      }

      h += 52;
      h += 68;
      h += profile.rules.size() * 26;
      return h + 40;
   }

   private int customGroupSectionHeight(QolConfig cfg) {
      if (this.importingGroupCode) {
         return 88;
      }

      int h = 74;
      if (this.hasGroupShareNotice()) {
         h += 18;
      }

      QolConfig.AutoSwapCustomGroup group = getActiveCustomGroup(cfg);
      if (group == null) {
         return h + 24;
      }

      if (this.editingDraftBlockId != null) {
         return 258;
      }

      h += cfg.autoSwapperCustomGroups.size() * 24 + 6;
      h += 44;
      h += 16;
      if (group.blockIds.isEmpty()) {
         h += 24;
      } else {
         h += group.blockIds.size() * 24;
      }

      return h + 60;
   }

   private record BlockChoice(Block block, String id, String label, String normalizedId, String normalizedLabel) {
      BlockChoice(Block block, String id, String label) {
         this(block, id, label, AutoSwapperSubTab.normalize(id), AutoSwapperSubTab.normalize(label));
      }
   }
}
