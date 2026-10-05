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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
public class AutoSwapperSubTab implements SuiteSubTab {
   private static final TagKey<Block> C_ORES = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores"));
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
            new HoverLabelWidget(x, y + 6, 120, 12, Component.translatable("suitecore.option.enabled"), Tooltip.create(Component.literal("Master toggle for AutoSwapper.")))
         );
         int toggleW = 80;
         int toggleX = x + w - toggleW;
         Button enabledButton = StyledButton.of(Component.literal(cfg.QolConfig.autoSwapperEnabled ? "ON" : "OFF"), b -> {
            cfg.QolConfig.autoSwapperEnabled = !cfg.QolConfig.autoSwapperEnabled;
            cfg.markDirty();
            screen.rebuildPreserveScroll();
         }).dimensions(toggleX, y, toggleW, 20).build();
         enabledButton.setTooltip(Tooltip.create(Component.literal("Turns AutoSwapper on or off.")));
         screen.addContentWidget(enabledButton);
         y += 28;
         screen.addContentWidget(
            new HoverLabelWidget(
               x,
               y + 6,
               180,
               12,
               Component.literal("Status HUD"),
               Tooltip.create(Component.literal("Shows a draggable AutoSwapper status HUD.\nUse HUD Edit Mode to move or resize it."))
            )
         );
         Button hudButton = StyledButton.of(Component.literal(cfg.AutoSwapperHudConfig.showHud ? "ON" : "OFF"), b -> {
            cfg.AutoSwapperHudConfig.toggleShowHud();
            ConfigIO.saveIfDirty();
            screen.rebuildPreserveScroll();
         }).dimensions(toggleX, y, toggleW, 20).build();
         hudButton.setTooltip(Tooltip.create(Component.literal("Shows or hides the AutoSwapper status HUD.")));
         screen.addContentWidget(hudButton);
         y += 28;
         screen.addContentWidget(
            new HoverLabelWidget(
               x, y + 6, 180, 12, Component.literal("HUD Header"), Tooltip.create(Component.literal("Shows or hides the AutoSwapper title bar on its HUD."))
            )
         );
         Button hudHeaderButton = StyledButton.of(Component.literal(cfg.AutoSwapperHudConfig.showHeader ? "ON" : "OFF"), b -> {
            cfg.AutoSwapperHudConfig.toggleShowHeader();
            ConfigIO.saveIfDirty();
            screen.rebuildPreserveScroll();
         }).dimensions(toggleX, y, toggleW, 20).build();
         hudHeaderButton.setTooltip(Tooltip.create(Component.literal("Shows or hides the AutoSwapper HUD header.")));
         screen.addContentWidget(hudHeaderButton);
         if (!cfg.QolConfig.autoSwapperEnabled) {
            screen.addContentWidget(
               new HoverLabelWidget(
                  x,
                  y + 34,
                  260,
                  12,
                  Component.translatable("suitecore.option.disabled_hint"),
                  Tooltip.create(Component.literal("Enable AutoSwapper to configure profiles and rules."))
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
                     Component.literal("HolePuncher Tool"),
                     Tooltip.create(Component.literal("When HolePuncher is enabled, pressing its hotkey will switch to this hotbar slot first."))
                  )
               );
               Button hpSlotBtn = StyledButton.of(Component.literal(hpSlotText), b -> {
                  if (cfg.QolConfig.holePuncherEnabled) {
                     cfg.QolConfig.cycleHolePuncherAutoSwapSlot();
                     cfg.markDirty();
                     screen.rebuildPreserveScroll();
                  }
               }).dimensions(toggleX - 60, y, toggleW + 60, 20).build();
               hpSlotBtn.setTooltip(Tooltip.create(Component.literal(hpEnabled ? "Click to cycle slot 1..9." : "HolePuncher is OFF.")));
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
                  Component.literal("Debounce (ms)"),
                  Tooltip.create(
                     Component.literal(
                        "Adjusts the delay before AutoSwapper swaps tools.\nLower = faster response.\nHigher = safer on touchy servers.\nThis applies to all profiles.\nDefault: 100 ms"
                     )
                  )
               )
            );
            AbstractSliderButton debounceSlider = new StyledSlider(x, debounceSliderY, w, 20, Component.empty(), msToSlider(cfg.QolConfig.autoSwapperDebounceMs)) {
               {
                  this.updateMessage();
                  this.setTooltip(Tooltip.create(Component.literal("Current debounce delay in milliseconds.")));
               }

               @Override
               protected void updateMessage() {
                  int ms = AutoSwapperSubTab.sliderToMs(this.value);
                  this.setMessage(Component.literal(ms + " ms"));
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
                  Component.literal("Profile"),
                  Tooltip.create(Component.literal("AutoSwapper profiles let you keep separate setups for different tasks."))
               )
            );
            int deleteW = 70;
            int addW = 70;
            int profileW = w - deleteW - addW - 12;
            Button profileButton = StyledButton.of(Component.literal(currentProfileTitle(cfg.QolConfig)), b -> {
               if (!cfg.QolConfig.autoSwapperProfiles.isEmpty()) {
                  cfg.QolConfig.autoSwapperActiveProfile++;
                  if (cfg.QolConfig.autoSwapperActiveProfile >= cfg.QolConfig.autoSwapperProfiles.size()) {
                     cfg.QolConfig.autoSwapperActiveProfile = 0;
                  }

                  cfg.markDirty();
                  screen.rebuildPreserveScroll();
               }
            }).dimensions(x, y + 20, profileW, 20).build();
            profileButton.setTooltip(Tooltip.create(Component.literal("Cycles through your AutoSwapper profiles.")));
            screen.addContentWidget(profileButton);
            Button addProfileButton = StyledButton.of(Component.literal("+ Add"), b -> {
               QolConfig.AutoSwapperProfile p = new QolConfig.AutoSwapperProfile();
               p.name = nextProfileName(cfg.QolConfig);
               cfg.QolConfig.autoSwapperProfiles.add(p);
               cfg.QolConfig.autoSwapperActiveProfile = cfg.QolConfig.autoSwapperProfiles.size() - 1;
               cfg.markDirty();
               screen.rebuildPreserveScroll();
            }).dimensions(x + profileW + 6, y + 20, addW, 20).build();
            addProfileButton.setTooltip(Tooltip.create(Component.literal("Adds a new AutoSwapper profile.")));
            screen.addContentWidget(addProfileButton);
            Button deleteProfileButton = StyledButton.of(Component.literal("Delete"), b -> {
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
            deleteProfileButton.setTooltip(Tooltip.create(Component.literal("Deletes the current AutoSwapper profile.")));
            screen.addContentWidget(deleteProfileButton);
            QolConfig.AutoSwapperProfile profile = getActiveProfile(cfg.QolConfig);
            if (profile == null) {
               screen.addContentWidget(
                  new HoverLabelWidget(
                     x,
                     y + 52,
                     280,
                     12,
                     Component.literal("No profiles yet. Add one to start."),
                     Tooltip.create(Component.literal("Create a profile for mining, map art, or any other setup you want."))
                  )
               );
            } else {
               EditBox profileNameField = new EditBox(screen.getFont(), x, y + 46, w, 20, Component.empty());
               profileNameField.setMaxLength(32);
               profileNameField.setValue(profile.name == null ? "" : profile.name);
               profileNameField.setTooltip(Tooltip.create(Component.literal("Rename the current AutoSwapper profile.")));
               profileNameField.setResponder(newText -> {
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
                     Component.literal("Default Slot"),
                     Tooltip.create(Component.literal("When no rule matches, this profile can fall back to a default hotbar slot."))
                  )
               );
               int defaultToggleW = 120;
               int defaultToggleX = x + w - defaultToggleW;
               Button defaultToggle = StyledButton.of(Component.literal(profile.useDefaultTool ? "Use Default" : "No Default"), b -> {
                  profile.useDefaultTool = !profile.useDefaultTool;
                  cfg.markDirty();
                  screen.rebuildPreserveScroll();
               }).dimensions(defaultToggleX, y, defaultToggleW, 20).build();
               defaultToggle.setTooltip(Tooltip.create(Component.literal("Enable or disable use of the default slot when no rule applies for this profile.")));
               screen.addContentWidget(defaultToggle);
               if (profile.useDefaultTool) {
                  int slotW = 90;
                  int slotX = defaultToggleX - 6 - slotW;
                  Button slotButton = StyledButton.of(Component.literal("Slot " + profile.defaultSlot), b -> {
                     profile.defaultSlot = nextSlot(profile.defaultSlot);
                     cfg.markDirty();
                     screen.rebuildPreserveScroll();
                  }).dimensions(slotX, y, slotW, 20).build();
                  slotButton.setTooltip(Tooltip.create(Component.literal("Cycles the default hotbar slot used when no rule matches for this profile.")));
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
            Component.literal("Rules (Top rule has priority)"),
            Tooltip.create(Component.literal("Each rule matches a block group or exact block and picks a hotbar slot.\nHigher rules win if more than one matches."))
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
         Button typeButton = StyledButton.of(Component.literal(typeLabel(rule.type)), b -> {
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
         typeButton.setTooltip(Tooltip.create(Component.literal("Cycles between built-in groups, exact blocks, and custom block groups.")));
         screen.addContentWidget(typeButton);
         if (rule.type == QolConfig.AutoSwapTargetType.GROUP) {
            Button groupButton = StyledButton.of(Component.translatable(groupTitleKey(rule.group)), b -> {
               rule.group = nextGroup(rule.group);
               cfg.markDirty();
               screen.rebuildPreserveScroll();
            }).dimensions(fieldX, rowY, fieldW, rowH).build();
            groupButton.setTooltip(Tooltip.create(Component.literal("Cycles the block grouping used by this rule.")));
            screen.addContentWidget(groupButton);
         } else if (rule.type == QolConfig.AutoSwapTargetType.CUSTOM) {
            Button customButton = StyledButton.of(Component.literal(customRuleGroupTitle(cfg.QolConfig, rule)), b -> {
               cycleRuleCustomGroup(cfg.QolConfig, rule);
               cfg.markDirty();
               screen.rebuildPreserveScroll();
            }).dimensions(fieldX, rowY, fieldW, rowH).build();
            customButton.setTooltip(Tooltip.create(Component.literal("Cycles through your custom AutoSwapper block groups.")));
            customButton.active = !cfg.QolConfig.autoSwapperCustomGroups.isEmpty();
            screen.addContentWidget(customButton);
         } else {
            int actualFieldW = fieldW - gapX - 54;
            Button pickButton = StyledButton.of(Component.literal("Pick"), b -> {
               Minecraft mc = Minecraft.getInstance();
               if (mc.hitResult instanceof BlockHitResult bhr && mc.hitResult.getType() == Type.BLOCK && mc.level != null) {
                  BlockState state = mc.level.getBlockState(bhr.getBlockPos());
                  Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                  if (id != null) {
                     rule.blockId = id.toString();
                     cfg.markDirty();
                     screen.rebuildPreserveScroll();
                  }
               }
            }).dimensions(fieldX, rowY, 54, rowH).build();
            pickButton.setTooltip(Tooltip.create(Component.literal("Uses the block you are currently looking at.")));
            screen.addContentWidget(pickButton);
            EditBox field = new EditBox(screen.getFont(), fieldX + 54 + gapX, rowY, actualFieldW, rowH, Component.empty());
            field.setMaxLength(128);
            field.setValue(rule.blockId == null ? "" : rule.blockId);
            field.setTooltip(Tooltip.create(Component.literal("Enter a block id, such as minecraft:stone")));
            field.setResponder(newText -> {
               rule.blockId = newText;
               cfg.markDirty();
            });
            screen.addContentWidget(field);
         }

         Button slotButton = StyledButton.of(Component.literal("Slot " + rule.slot), b -> {
            rule.slot = nextSlot(rule.slot);
            cfg.markDirty();
            screen.rebuildPreserveScroll();
         }).dimensions(slotX, rowY, 70, rowH).build();
         slotButton.setTooltip(Tooltip.create(Component.literal("Selects the hotbar slot to swap to when this rule matches.")));
         screen.addContentWidget(slotButton);
         Button upButton = StyledButton.of(Component.literal("^"), b -> {
            int idx = profile.rules.indexOf(rule);
            if (idx > 0) {
               Collections.swap(profile.rules, idx, idx - 1);
               cfg.markDirty();
               screen.rebuildPreserveScroll();
            }
         }).dimensions(upX, rowY, 22, rowH).build();
         upButton.setTooltip(Tooltip.create(Component.literal("Moves this rule up. Higher rules have higher priority.")));
         screen.addContentWidget(upButton);
         Button downButton = StyledButton.of(Component.literal("v"), b -> {
            int idx = profile.rules.indexOf(rule);
            if (idx < profile.rules.size() - 1) {
               Collections.swap(profile.rules, idx, idx + 1);
               cfg.markDirty();
               screen.rebuildPreserveScroll();
            }
         }).dimensions(downX, rowY, 22, rowH).build();
         downButton.setTooltip(Tooltip.create(Component.literal("Moves this rule down.")));
         screen.addContentWidget(downButton);
         Button removeButton = StyledButton.of(Component.literal("X"), b -> {
            profile.rules.remove(rule);
            cfg.markDirty();
            screen.rebuildPreserveScroll();
         }).dimensions(removeX, rowY, 22, rowH).build();
         removeButton.setTooltip(Tooltip.create(Component.literal("Removes this rule.")));
         screen.addContentWidget(removeButton);
      }

      int addY = y + profile.rules.size() * gapY + 8;
      Button addRuleButton = StyledButton.of(Component.literal("+ Add Rule"), b -> {
         QolConfig.AutoSwapRule r = new QolConfig.AutoSwapRule();
         profile.rules.add(r);
         cfg.markDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x, addY, 120, rowH).build();
      addRuleButton.setTooltip(Tooltip.create(Component.literal("Adds a new AutoSwapper rule.")));
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
         new HoverLabelWidget(x, y, 220, 12, Component.literal("Custom Groups"), Tooltip.create(Component.literal("Create reusable block groups for AutoSwapper rules.")))
      );
      y += 18;
      int importW = 76;
      int exportW = 76;
      Button importButton = StyledButton.of(Component.literal("Import"), b -> {
         this.importingGroupCode = true;
         this.groupImportCode = "";
         this.closeBlockEditor();
         screen.rebuildFromTab();
      }).dimensions(x, y, importW, rowH).build();
      importButton.setTooltip(Tooltip.create(Component.literal("Paste an AutoSwapper group share code.")));
      screen.addContentWidget(importButton);
      Button exportButton = StyledButton.of(Component.literal("Export"), b -> {
         QolConfig.AutoSwapCustomGroup groupx = getActiveCustomGroup(cfg);
         if (groupx != null) {
            copyToClipboard(GroupShareCodec.exportAutoSwapGroup(groupx));
            this.showGroupShareNotice("Code copied to clipboard.");
            screen.rebuildPreserveScroll();
         }
      }).dimensions(x + importW + gapX, y, exportW, rowH).build();
      exportButton.active = active != null;
      exportButton.setTooltip(Tooltip.create(Component.literal("Copies the selected AutoSwapper group share code.")));
      screen.addContentWidget(exportButton);
      y += 28;
      if (this.hasGroupShareNotice()) {
         screen.addContentWidget(
            new HoverLabelWidget(x, y + 2, Math.min(w, 260), 12, Component.literal(this.groupShareNotice), Tooltip.create(Component.literal(this.groupShareNotice)))
         );
         y += 18;
      }

      int deleteW = 70;
      int addW = 70;
      int labelW = w - deleteW - addW - gapX * 2;
      screen.addContentWidget(
         new HoverLabelWidget(x, y + 4, labelW, 12, Component.literal("Groups"), Tooltip.create(Component.literal("Select the custom block group you want to edit.")))
      );
      Button addButton = StyledButton.of(Component.literal("+ New"), b -> {
         QolConfig.AutoSwapCustomGroup groupx = new QolConfig.AutoSwapCustomGroup();
         groupx.id = nextCustomGroupId(cfg);
         groupx.name = nextCustomGroupName(cfg);
         cfg.autoSwapperCustomGroups.add(groupx);
         cfg.autoSwapperActiveCustomGroup = cfg.autoSwapperCustomGroups.size() - 1;
         this.closeBlockEditor();
         SuiteConfig.INSTANCE.markDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + labelW + gapX, y, addW, rowH).build();
      addButton.setTooltip(Tooltip.create(Component.literal("Creates a new custom block group.")));
      screen.addContentWidget(addButton);
      Button deleteButton = StyledButton.of(Component.literal("Delete"), b -> {
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
      deleteButton.setTooltip(Tooltip.create(Component.literal("Deletes the selected custom group.")));
      screen.addContentWidget(deleteButton);
      y += 24;

      for (int i = 0; i < cfg.autoSwapperCustomGroups.size(); i++) {
         QolConfig.AutoSwapCustomGroup group = cfg.autoSwapperCustomGroups.get(i);
         int idx = i;
         String name = group.name != null && !group.name.isBlank() ? group.name : "Custom Group";
         Button groupRow = StyledButton.of(Component.literal(name), b -> {
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
               Component.literal("Create a group to add blocks and use it in rules."),
               Tooltip.create(Component.literal("Custom groups can be selected by rules using the Custom target type."))
            )
         );
         return y + 24;
      }

      screen.addContentWidget(new HoverLabelWidget(x, y + 2, 180, 12, Component.literal("Group Name"), Tooltip.create(Component.literal("Name shown in AutoSwapper rules."))));
      y += 16;
      EditBox nameField = new EditBox(screen.getFont(), x, y, w, rowH, Component.empty());
      nameField.setMaxLength(48);
      nameField.setValue(group.name == null ? "" : group.name);
      nameField.setResponder(s -> {
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
            Component.literal("Group Blocks (" + group.blockIds.size() + ")"),
            Tooltip.create(Component.literal("Blocks in this group can be selected by AutoSwapper rules using the Custom target type."))
         )
      );
      Button addBlock = StyledButton.of(Component.literal("+ Add Block"), b -> {
         this.startAddingBlock();
         screen.rebuildFromTab();
      }).dimensions(x + w - 104, y - 4, 104, rowH).build();
      screen.addContentWidget(addBlock);
      y += 16;
      if (group.blockIds.isEmpty()) {
         screen.addContentWidget(
            new HoverLabelWidget(
               x, y + 4, Math.min(w, 280), 12, Component.literal("No blocks added yet."), Tooltip.create(Component.literal("Use Add Block to search and add blocks."))
            )
         );
         return y + 24;
      }

      for (String id : group.blockIds) {
         AutoSwapperSubTab.BlockChoice choice = choiceById(id);
         String label = shorten(choice == null ? id : choice.label, 42);
         Block block = choice == null ? null : choice.block;
         BlockEntryButtonWidget row = new BlockEntryButtonWidget(x, y, w - 76, 22, block, Component.literal(label), () -> {});
         row.setTooltip(Tooltip.create(Component.literal(id)));
         screen.addContentWidget(row);
         String removeId = id;
         Button remove = StyledButton.of(Component.literal("Remove"), b -> {
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
            x, y + 4, 220, 12, Component.literal("Import AutoSwapper Group"), Tooltip.create(Component.literal("Paste a BSWAP1 share code from another player."))
         )
      );
      Button cancel = StyledButton.of(Component.literal("Cancel"), b -> {
         this.importingGroupCode = false;
         this.groupImportCode = "";
         screen.rebuildFromTab();
      }).dimensions(x + w - 80, y, 80, rowH).build();
      screen.addContentWidget(cancel);
      y += 28;
      EditBox field = new EditBox(screen.getFont(), x, y, w, rowH, Component.empty());
      field.setMaxLength(12000);
      field.setValue(this.groupImportCode);
      field.setHint(Component.literal("Paste share code..."));
      field.setResponder(s -> this.groupImportCode = s == null ? "" : s.trim());
      screen.addContentWidget(field);
      y += 28;
      Button importButton = StyledButton.of(Component.literal("Import Group"), b -> {
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
         new HoverLabelWidget(x, y + 4, 180, 12, Component.literal("Add Block"), Tooltip.create(Component.literal("Choose the block for this custom AutoSwapper group.")))
      );
      Button back = StyledButton.of(Component.literal("Back"), b -> {
         this.closeBlockEditor();
         screen.rebuildFromTab();
      }).dimensions(x + w - 74, y, 74, rowH).build();
      screen.addContentWidget(back);
      y += 28;
      EditBox searchField = new EditBox(screen.getFont(), x, y, w, rowH, Component.empty());
      searchField.setMaxLength(80);
      searchField.setValue(this.blockEditorSearch);
      searchField.setHint(Component.literal("Search blocks..."));
      searchField.setResponder(s -> {
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
            Component.literal("Blocks " + (filtered.isEmpty() ? 0 : start + 1) + "-" + end + " of " + filtered.size() + this.selectedBlockCountLabel()),
            Tooltip.create(
               Component.literal("Scroll this block list with the mouse wheel while hovering over it. Use the open space to the right to scroll the main page.")
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
         BlockEntryButtonWidget row = new BlockEntryButtonWidget(x, y, w, 22, choice.block, Component.literal(choice.label), () -> {
            this.toggleSelectedBlock(choice.id);
            this.editingDraftBlockId = this.firstSelectedBlockId();
            SuiteConfig.INSTANCE.markDirty();
            screen.rebuildPreserveScroll();
         });
         row.setHighlighted(selectedChoice);
         row.setTooltip(Tooltip.create(Component.literal(choice.id)));
         screen.addContentWidget(row);
         y += 24;
      }

      if (filtered.isEmpty()) {
         screen.addContentWidget(new HoverLabelWidget(x, y + 4, Math.min(w, 280), 12, Component.literal("No matching blocks."), null));
         y += 24;
      }

      y = Math.max(y, this.blockEditorListBottom);
      y += 8;
      Button save = StyledButton.of(Component.literal(this.addBlockSaveLabel()), b -> {
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
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.keyboardHandler != null && value != null) {
         client.keyboardHandler.setClipboard(value);
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

      for (Block block : BuiltInRegistries.BLOCK) {
         if (block != null) {
            if (block.asItem() != net.minecraft.world.item.Items.AIR) {
               Identifier id = BuiltInRegistries.BLOCK.getKey(block);
               if (id != null) {
                  String label = Component.translatable(block.asItem().getDescriptionId()).getString(); // not an ItemStack: those can't be built before a world is loaded
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
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.level != null) {
         if (client.hitResult instanceof BlockHitResult bhr) {
            BlockState state = client.level.getBlockState(bhr.getBlockPos());
            if (state == null) {
               return null;
            }

            Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
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
