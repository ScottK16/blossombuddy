package org.blossomsuite.core.ui.QolSubTabs;

import org.blossomsuite.core.ui.StyledButton;

import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.config.AutoDropperConfigIO;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.GroupShareCodec;
import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.ui.HoverLabelWidget;
import org.blossomsuite.core.ui.ItemEntryButtonWidget;
import org.blossomsuite.core.ui.SuiteSettingsScreen;
import org.blossomsuite.core.ui.SuiteSubTab;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class AutoDropperSubTab implements SuiteSubTab {
   private static final List<AutoDropperSubTab.ItemChoice> ITEM_CHOICES = buildItemChoices();
   private static final int ITEM_EDITOR_ROWS = 6;
   private static final int ITEM_EDITOR_ROW_HEIGHT = 24;
   private static final int ITEM_EDITOR_SCROLL_EDGE_GUTTER = 36;
   private final boolean groupsOnly;
   private QolConfig.AutoDropGroupItem editingOriginal = null;
   private QolConfig.AutoDropGroupItem editingDraft = null;
   private String itemEditorSearch = "";
   private final Set<String> itemEditorSelectedIds = new LinkedHashSet<>();
   private int itemEditorScroll = 0;
   private int itemEditorListLeft = -1;
   private int itemEditorListRight = -1;
   private int itemEditorListTop = -1;
   private int itemEditorListBottom = -1;
   private boolean importingGroupCode = false;
   private String groupImportCode = "";
   private String groupShareNotice = "";
   private long groupShareNoticeUntilMs = 0L;

   public AutoDropperSubTab() {
      this(false);
   }

   public AutoDropperSubTab(boolean groupsOnly) {
      this.groupsOnly = groupsOnly;
   }

   @Override
   public String titleKey() {
      return this.groupsOnly ? "suitecore.tab.autodropper.custom_groups" : "suitecore.tab.autodropper.settings";
   }

   @Override
   public void build(SuiteSettingsScreen screen, int contentTopOffset) {
      QolConfig cfg = SuiteConfig.INSTANCE.QolConfig;
      int x = screen.contentX();
      int w = screen.contentW();
      int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
      int rowH = 20;
      int gapX = 6;
      if (this.groupsOnly) {
         this.addGroupEditor(screen, cfg, x, w, y, rowH, gapX);
      } else {
         y = this.addToggleRow(screen, x, w, y, "Enabled", cfg.autoDropperEnabled, "Master toggle for AutoDropper.", () -> {
            cfg.autoDropperEnabled = !cfg.autoDropperEnabled;
            SuiteConfig.INSTANCE.markDirty();
            ConfigIO.saveIfDirty();
            screen.rebuildPreserveScroll();
         });
         y = this.addToggleRow(
            screen,
            x,
            w,
            y,
            "Drop On Pickup",
            cfg.autoDropperOnPickup,
            "Queues AutoDropper when inventory contents change, usually from picked-up drops.",
            () -> {
               cfg.autoDropperOnPickup = !cfg.autoDropperOnPickup;
               SuiteConfig.INSTANCE.markDirty();
               ConfigIO.saveIfDirty();
               screen.rebuildPreserveScroll();
            }
         );
         y = this.addToggleRow(screen, x, w, y, "Drop On Sneak", cfg.autoDropperOnSneak, "Queues AutoDropper when you start sneaking.", () -> {
            cfg.autoDropperOnSneak = !cfg.autoDropperOnSneak;
            SuiteConfig.INSTANCE.markDirty();
            ConfigIO.saveIfDirty();
            screen.rebuildPreserveScroll();
         });
         y = this.addToggleRow(
            screen, x, w, y, "Include Hotbar", cfg.autoDropperIncludeHotbar, "Allows AutoDropper to drop matching items from the hotbar.", () -> {
               cfg.autoDropperIncludeHotbar = !cfg.autoDropperIncludeHotbar;
               SuiteConfig.INSTANCE.markDirty();
               ConfigIO.saveIfDirty();
               screen.rebuildPreserveScroll();
            }
         );
         y = this.addToggleRow(
            screen, x, w, y, "Protect Selected Slot", cfg.autoDropperProtectSelectedSlot, "Never drops from the currently selected hotbar slot.", () -> {
               cfg.autoDropperProtectSelectedSlot = !cfg.autoDropperProtectSelectedSlot;
               SuiteConfig.INSTANCE.markDirty();
               ConfigIO.saveIfDirty();
               screen.rebuildPreserveScroll();
            }
         );
         y = this.addToggleRow(
            screen,
            x,
            w,
            y,
            "Pause In Screens",
            cfg.autoDropperPauseWhileScreenOpen,
            "Prevents automatic dropping while an inventory, chest, or menu is open.",
            () -> {
               cfg.autoDropperPauseWhileScreenOpen = !cfg.autoDropperPauseWhileScreenOpen;
               SuiteConfig.INSTANCE.markDirty();
               ConfigIO.saveIfDirty();
               screen.rebuildPreserveScroll();
            }
         );
         y = this.addToggleRow(
            screen,
            x,
            w,
            y,
            "Pause While Sneaking",
            cfg.autoDropperPauseWhileSneaking,
            "Prevents AutoDropper from dropping or queueing drops while you are sneaking.",
            () -> {
               cfg.autoDropperPauseWhileSneaking = !cfg.autoDropperPauseWhileSneaking;
               SuiteConfig.INSTANCE.markDirty();
               ConfigIO.saveIfDirty();
               screen.rebuildPreserveScroll();
            }
         );
         y = this.addToggleRow(
            screen,
            x,
            w,
            y,
            "Pause on player attack",
            cfg.autoDropperPauseOnPlayerAttack,
            "Pauses AutoDropper briefly when another player damages you.",
            () -> {
               cfg.autoDropperPauseOnPlayerAttack = !cfg.autoDropperPauseOnPlayerAttack;
               SuiteConfig.INSTANCE.markDirty();
               ConfigIO.saveIfDirty();
               screen.rebuildPreserveScroll();
            }
         );
         y = this.addToggleRow(
            screen,
            x,
            w,
            y,
            "Pause on attacking player",
            cfg.autoDropperPauseOnAttackingPlayer,
            "Pauses AutoDropper briefly when you attack another player.",
            () -> {
               cfg.autoDropperPauseOnAttackingPlayer = !cfg.autoDropperPauseOnAttackingPlayer;
               SuiteConfig.INSTANCE.markDirty();
               ConfigIO.saveIfDirty();
               screen.rebuildPreserveScroll();
            }
         );
         y = this.addToggleRow(
            screen,
            x,
            w,
            y,
            "Pause on targeting player",
            cfg.autoDropperPauseOnTargetingPlayer,
            "Pauses AutoDropper while your crosshair is on another player.",
            () -> {
               cfg.autoDropperPauseOnTargetingPlayer = !cfg.autoDropperPauseOnTargetingPlayer;
               SuiteConfig.INSTANCE.markDirty();
               ConfigIO.saveIfDirty();
               screen.rebuildPreserveScroll();
            }
         );
         y = this.addIntField(
            screen,
            x,
            w,
            y,
            "Delay (ms)",
            cfg.autoDropperDelayMs,
            "Delay after pickup/sneak before dropping. 0 to 10000 ms.",
            value -> cfg.autoDropperDelayMs = Math.max(0, Math.min(10000, value))
         );
         y = this.addIntField(
            screen,
            x,
            w,
            y,
            "Burst Stacks / Tick",
            cfg.autoDropperMaxStacksPerTick,
            "Maximum stacks to throw per client tick. Lower values are gentler while mining. 1 to 64.",
            value -> cfg.autoDropperMaxStacksPerTick = Math.max(1, Math.min(64, value))
         );
         if (AutoDropperConfigIO.legacyAutoDropConfigExists()) {
            y = this.addButtonRow(
               screen, x, w, y, "Import Autodrop Config", "Import", "Imports config/autodrop.json from the old AutoDrop mod into AutoDropper groups.", () -> {
                  AutoDropperConfigIO.ImportResult result = AutoDropperConfigIO.importLegacyAutoDrop();
                  if (result.success()) {
                     ConfigIO.saveIfDirty();
                     ChatOutput.info("Imported AutoDrop: " + result.groups() + " groups, " + result.items() + " items.");
                  } else {
                     ChatOutput.info(result.message());
                  }

                  screen.rebuildPreserveScroll();
               }
            );
         }
      }
   }

   private void addGroupEditor(SuiteSettingsScreen screen, QolConfig cfg, int x, int w, int y, int rowH, int gapX) {
      QolConfig.AutoDropCustomGroup active = activeGroup(cfg);
      if (this.importingGroupCode) {
         this.addGroupImportEditor(screen, cfg, x, w, y, rowH);
      } else if (this.editingDraft != null && active != null) {
         this.addGroupItemEditor(screen, active, x, w, y, rowH, gapX);
      } else {
         int deleteW = 70;
         int addW = 70;
         int labelW = w - deleteW - addW - gapX * 2;
         screen.addContentWidget(
            new HoverLabelWidget(x, y + 4, labelW, 12, Text.literal("Groups"), Tooltip.of(Text.literal("Select the group you want to edit.")))
         );
         ButtonWidget add = StyledButton.of(Text.literal("+ New"), b -> {
            QolConfig.AutoDropCustomGroup groupx = new QolConfig.AutoDropCustomGroup();
            groupx.id = "drop-" + System.currentTimeMillis() + "-" + (cfg.autoDropperCustomGroups.size() + 1);
            groupx.name = "Drop Group " + (cfg.autoDropperCustomGroups.size() + 1);
            cfg.autoDropperCustomGroups.add(groupx);
            cfg.autoDropperActiveCustomGroup = cfg.autoDropperCustomGroups.size() - 1;
            this.closeItemEditor();
            SuiteConfig.INSTANCE.markDirty();
            ConfigIO.saveIfDirty();
            screen.rebuildPreserveScroll();
         }).dimensions(x + labelW + gapX, y, addW, rowH).build();
         screen.addContentWidget(add);
         ButtonWidget delete = StyledButton.of(Text.literal("Delete"), b -> {
            QolConfig.AutoDropCustomGroup groupx = activeGroup(cfg);
            if (groupx != null) {
               cfg.autoDropperCustomGroups.remove(groupx);
               cfg.autoDropperActiveCustomGroup = Math.max(0, Math.min(cfg.autoDropperActiveCustomGroup, cfg.autoDropperCustomGroups.size() - 1));
               this.closeItemEditor();
               SuiteConfig.INSTANCE.markDirty();
               ConfigIO.saveIfDirty();
               screen.rebuildPreserveScroll();
            }
         }).dimensions(x + labelW + gapX + addW + gapX, y, deleteW, rowH).build();
         delete.active = !cfg.autoDropperCustomGroups.isEmpty();
         screen.addContentWidget(delete);
         y += 24;
         int importW = 76;
         int exportW = 76;
         ButtonWidget importButton = StyledButton.of(Text.literal("Import"), b -> {
            this.importingGroupCode = true;
            this.groupImportCode = "";
            this.closeItemEditor();
            screen.rebuildFromTab();
         }).dimensions(x, y, importW, rowH).build();
         importButton.setTooltip(Tooltip.of(Text.literal("Paste an AutoDropper group share code.")));
         screen.addContentWidget(importButton);
         ButtonWidget exportButton = StyledButton.of(Text.literal("Export"), b -> {
            QolConfig.AutoDropCustomGroup groupx = activeGroup(cfg);
            if (groupx != null) {
               copyToClipboard(GroupShareCodec.exportAutoDropGroup(groupx));
               this.showGroupShareNotice("Code copied to clipboard.");
               screen.rebuildPreserveScroll();
            }
         }).dimensions(x + importW + gapX, y, exportW, rowH).build();
         exportButton.active = active != null;
         exportButton.setTooltip(Tooltip.of(Text.literal("Copies the selected AutoDropper group share code.")));
         screen.addContentWidget(exportButton);
         y += 28;
         if (this.hasGroupShareNotice()) {
            screen.addContentWidget(
               new HoverLabelWidget(x, y + 2, Math.min(w, 260), 12, Text.literal(this.groupShareNotice), Tooltip.of(Text.literal(this.groupShareNotice)))
            );
            y += 18;
         }

         for (int i = 0; i < cfg.autoDropperCustomGroups.size(); i++) {
            QolConfig.AutoDropCustomGroup group = cfg.autoDropperCustomGroups.get(i);
            int idx = i;
            int toggleW = 64;
            String name = group.name != null && !group.name.isBlank() ? group.name : "Custom Group";
            ButtonWidget groupRow = StyledButton.of(Text.literal(name), b -> {
               cfg.autoDropperActiveCustomGroup = idx;
               this.closeItemEditor();
               SuiteConfig.INSTANCE.markDirty();
               screen.rebuildPreserveScroll();
            }).dimensions(x, y, w - toggleW - gapX, rowH).build();
            groupRow.active = i != cfg.autoDropperActiveCustomGroup;
            screen.addContentWidget(groupRow);
            ButtonWidget enabled = StyledButton.of(Text.literal(group.enabled ? "ON" : "OFF"), b -> {
               group.enabled = !group.enabled;
               cfg.autoDropperActiveCustomGroup = idx;
               this.closeItemEditor();
               SuiteConfig.INSTANCE.markDirty();
               ConfigIO.saveIfDirty();
               screen.rebuildPreserveScroll();
            }).dimensions(x + w - toggleW, y, toggleW, rowH).build();
            enabled.setTooltip(Tooltip.of(Text.literal(group.enabled ? "This AutoDropper group is enabled." : "This AutoDropper group is disabled.")));
            screen.addContentWidget(enabled);
            y += 24;
         }

         y += 6;
         QolConfig.AutoDropCustomGroup group = active;
         if (group == null) {
            screen.addContentWidget(new HoverLabelWidget(x, y, Math.min(w, 340), 12, Text.literal("Create a group to add items."), null));
         } else {
            TextFieldWidget name = new TextFieldWidget(screen.getTextRenderer(), x, y, w, rowH, Text.empty());
            name.setMaxLength(48);
            name.setText(group.name == null ? "" : group.name);
            name.setChangedListener(s -> {
               group.name = s != null && !s.isBlank() ? s : "Custom Group";
               SuiteConfig.INSTANCE.markDirty();
            });
            screen.addContentWidget(name);
            y += 28;
            screen.addContentWidget(
               new HoverLabelWidget(
                  x,
                  y,
                  220,
                  12,
                  Text.literal("Group Items (" + group.items.size() + ")"),
                  Tooltip.of(Text.literal("Each item has its own drop threshold, keep settings, and optional advanced data filter."))
               )
            );
            ButtonWidget addItem = StyledButton.of(Text.literal("+ Add Item"), b -> {
               this.startAddingItem();
               screen.rebuildFromTab();
            }).dimensions(x + w - 100, y - 4, 100, rowH).build();
            screen.addContentWidget(addItem);
            y += 16;
            if (group.items.isEmpty()) {
               screen.addContentWidget(
                  new HoverLabelWidget(
                     x,
                     y + 4,
                     Math.min(w, 360),
                     12,
                     Text.literal("No items in this group yet."),
                     Tooltip.of(Text.literal("Use Add Item to choose an item and configure its drop settings."))
                  )
               );
            } else {
               for (int i = 0; i < group.items.size(); i++) {
                  QolConfig.AutoDropGroupItem entry = group.items.get(i);
                  String id = entry.itemId;
                  AutoDropperSubTab.ItemChoice choice = choiceById(id);
                  Item item = choice == null ? null : choice.item;
                  String label = compactItemLabel(entry, choice);
                  ItemEntryButtonWidget itemRow = new ItemEntryButtonWidget(x, y, w - 150, 22, item, Text.literal(label), () -> {
                     this.startEditingItem(entry);
                     screen.rebuildFromTab();
                  });
                  itemRow.setTooltip(Tooltip.of(Text.literal(itemTooltip(entry, choice))));
                  screen.addContentWidget(itemRow);
                  screen.addContentWidget(StyledButton.of(Text.literal("Edit"), b -> {
                     this.startEditingItem(entry);
                     screen.rebuildFromTab();
                  }).dimensions(x + w - 144, y + 1, 64, rowH).build());
                  QolConfig.AutoDropGroupItem removeEntry = entry;
                  screen.addContentWidget(StyledButton.of(Text.literal("Remove"), b -> {
                     group.items.remove(removeEntry);
                     SuiteConfig.INSTANCE.markDirty();
                     ConfigIO.saveIfDirty();
                     screen.rebuildPreserveScroll();
                  }).dimensions(x + w - 74, y + 1, 74, rowH).build());
                  y += 26;
               }
            }
         }
      }
   }

   private void addGroupImportEditor(SuiteSettingsScreen screen, QolConfig cfg, int x, int w, int y, int rowH) {
      screen.addContentWidget(
         new HoverLabelWidget(
            x, y + 4, 220, 12, Text.literal("Import AutoDropper Group"), Tooltip.of(Text.literal("Paste a BDROP1 share code from another player."))
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
      field.setMaxLength(131072);
      field.setText(this.groupImportCode);
      field.setPlaceholder(Text.literal("Paste share code..."));
      field.setChangedListener(s -> this.groupImportCode = s == null ? "" : s.trim());
      screen.addContentWidget(field);
      y += 28;
      if (this.hasGroupShareNotice()) {
         screen.addContentWidget(
            new HoverLabelWidget(x, y + 2, Math.min(w, 360), 12, Text.literal(this.groupShareNotice), Tooltip.of(Text.literal(this.groupShareNotice)))
         );
         y += 20;
      }

      ButtonWidget importButton = StyledButton.of(Text.literal("Import Group"), b -> {
         GroupShareCodec.ImportResult result = GroupShareCodec.importAutoDropGroup(cfg, this.groupImportCode);
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
   }

   private void addGroupItemEditor(SuiteSettingsScreen screen, QolConfig.AutoDropCustomGroup group, int x, int w, int y, int rowH, int gapX) {
      QolConfig.AutoDropGroupItem draft = this.editingDraft;
      if (draft != null) {
         screen.addContentWidget(
            new HoverLabelWidget(
               x,
               y + 4,
               180,
               12,
               Text.literal(this.editingOriginal == null ? "Add Item" : "Edit Item"),
               Tooltip.of(Text.literal("Choose the item, then set the AutoDropper rules for this group entry."))
            )
         );
         ButtonWidget back = StyledButton.of(Text.literal("Back"), b -> {
            this.closeItemEditor();
            screen.rebuildFromTab();
         }).dimensions(x + w - 74, y, 74, rowH).build();
         screen.addContentWidget(back);
         y += 28;
         TextFieldWidget search = new TextFieldWidget(screen.getTextRenderer(), x, y, w, rowH, Text.empty());
         search.setMaxLength(80);
         search.setText(this.itemEditorSearch);
         search.setPlaceholder(Text.literal("Search items..."));
         search.setChangedListener(s -> {
            String nextSearch = s == null ? "" : s;
            if (!nextSearch.equals(this.itemEditorSearch)) {
               this.itemEditorSearch = nextSearch;
               this.itemEditorScroll = 0;
               screen.rebuildPreserveScroll();
            }
         });
         screen.addContentWidget(search);
         y += 26;
         List<AutoDropperSubTab.ItemChoice> filtered = filteredItems(this.itemEditorSearch);
         int maxScroll = Math.max(0, filtered.size() - 6);
         this.itemEditorScroll = Math.max(0, Math.min(this.itemEditorScroll, maxScroll));
         int start = this.itemEditorScroll;
         int end = Math.min(filtered.size(), start + 6);
         screen.addContentWidget(
            new HoverLabelWidget(
               x,
               y + 4,
               Math.min(w, 260),
               12,
               Text.literal("Items " + (filtered.isEmpty() ? 0 : start + 1) + "-" + end + " of " + filtered.size() + this.selectedItemCountLabel()),
               Tooltip.of(
                  Text.literal("Scroll this item list with the mouse wheel while hovering over it. Use the open space to the right to scroll the main page.")
               )
            )
         );
         y += 24;
         this.itemEditorListLeft = x + editorScrollGutter(w, 36);
         this.itemEditorListRight = x + w - editorScrollGutter(w, 36);
         this.itemEditorListTop = y;
         this.itemEditorListBottom = y + 144;

         for (int i = start; i < end; i++) {
            AutoDropperSubTab.ItemChoice choice = filtered.get(i);
            boolean selectedChoice = this.editingOriginal == null ? this.itemEditorSelectedIds.contains(choice.id) : choice.id.equals(draft.itemId);
            ItemEntryButtonWidget row = new ItemEntryButtonWidget(x, y, w, 22, choice.item, Text.literal(choice.label), () -> {
               if (this.editingOriginal == null) {
                  this.toggleSelectedItem(choice.id);
                  draft.itemId = this.firstSelectedItemId();
               } else {
                  draft.itemId = choice.id;
               }

               SuiteConfig.INSTANCE.markDirty();
               screen.rebuildPreserveScroll();
            });
            row.setHighlighted(selectedChoice);
            row.setTooltip(Tooltip.of(Text.literal(choice.id)));
            screen.addContentWidget(row);
            y += 24;
         }

         if (filtered.isEmpty()) {
            screen.addContentWidget(new HoverLabelWidget(x, y + 4, Math.min(w, 280), 12, Text.literal("No matching items."), null));
            y += 24;
         }

         y = Math.max(y, this.itemEditorListBottom);
         y += 8;
         int third = (w - gapX * 2) / 3;
         int optionY = y;
         y = this.addIntField(
            screen,
            x,
            third,
            optionY,
            "Start At",
            draft.minimumAmount,
            "0 = always drop this item. Higher values wait until you have at least this many.",
            value -> draft.minimumAmount = Math.max(0, value)
         );
         this.addIntField(
            screen,
            x + third + gapX,
            third,
            optionY,
            "Keep Amount",
            draft.keepItems,
            "Leave this many of this item in your inventory.",
            value -> draft.keepItems = Math.max(0, value)
         );
         this.addIntField(
            screen,
            x + (third + gapX) * 2,
            third,
            optionY,
            "Keep Stacks",
            draft.keepStacks,
            "Leave this many stacks of this item in your inventory.",
            value -> draft.keepStacks = Math.max(0, value)
         );
         screen.addContentWidget(
            new HoverLabelWidget(
               x,
               y + 2,
               190,
               12,
               Text.literal("Advanced Item Data"),
               Tooltip.of(
                  Text.literal(
                     "Optional. Leave blank for normal items. Use only when you need this entry to match special item data, such as a custom name or component text."
                  )
               )
            )
         );
         y += 16;
         TextFieldWidget component = new TextFieldWidget(screen.getTextRenderer(), x, y, w, rowH, Text.empty());
         component.setMaxLength(160);
         component.setText(draft.componentFilter == null ? "" : draft.componentFilter);
         component.setPlaceholder(Text.literal("leave blank unless this item needs special matching"));
         component.setChangedListener(s -> {
            draft.componentFilter = s == null ? "" : s;
            SuiteConfig.INSTANCE.markDirty();
         });
         screen.addContentWidget(component);
         y += 30;
         int saveW = 124;
         int removeW = 86;
         ButtonWidget save = StyledButton.of(Text.literal(this.addItemSaveLabel()), b -> {
            this.saveItemEditor(group);
            SuiteConfig.INSTANCE.markDirty();
            ConfigIO.saveIfDirty();
            screen.rebuildPreserveScroll();
         }).dimensions(x, y, saveW, rowH).build();
         save.active = this.editingOriginal == null ? !this.itemEditorSelectedIds.isEmpty() : draft.itemId != null && !draft.itemId.isBlank();
         screen.addContentWidget(save);
         if (this.editingOriginal != null) {
            ButtonWidget remove = StyledButton.of(Text.literal("Remove"), b -> {
               group.items.remove(this.editingOriginal);
               this.closeItemEditor();
               SuiteConfig.INSTANCE.markDirty();
               ConfigIO.saveIfDirty();
               screen.rebuildPreserveScroll();
            }).dimensions(x + saveW + gapX, y, removeW, rowH).build();
            screen.addContentWidget(remove);
         }
      }
   }

   private void startAddingItem() {
      this.editingOriginal = null;
      this.editingDraft = new QolConfig.AutoDropGroupItem();
      this.itemEditorSearch = "";
      this.itemEditorSelectedIds.clear();
      this.itemEditorScroll = 0;
   }

   private void startEditingItem(QolConfig.AutoDropGroupItem entry) {
      this.editingOriginal = entry;
      this.editingDraft = copyItem(entry);
      this.itemEditorSearch = "";
      this.itemEditorSelectedIds.clear();
      this.itemEditorScroll = 0;
   }

   private void closeItemEditor() {
      this.editingOriginal = null;
      this.editingDraft = null;
      this.itemEditorSearch = "";
      this.itemEditorSelectedIds.clear();
      this.itemEditorScroll = 0;
      this.itemEditorListLeft = -1;
      this.itemEditorListRight = -1;
      this.itemEditorListTop = -1;
      this.itemEditorListBottom = -1;
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

   private void saveItemEditor(QolConfig.AutoDropCustomGroup group) {
      if (group != null && this.editingDraft != null) {
         if (this.editingOriginal != null) {
            copyInto(this.editingDraft, this.editingOriginal);
         } else {
            for (String itemId : this.itemEditorSelectedIds) {
               if (itemId != null && !itemId.isBlank()) {
                  this.editingDraft.itemId = itemId;
                  QolConfig.AutoDropGroupItem existing = groupItemById(group, itemId);
                  if (existing == null) {
                     group.items.add(copyItem(this.editingDraft));
                  } else {
                     copyInto(this.editingDraft, existing);
                  }
               }
            }
         }

         this.closeItemEditor();
      } else {
         this.closeItemEditor();
      }
   }

   private void toggleSelectedItem(String itemId) {
      if (itemId != null && !itemId.isBlank()) {
         if (!this.itemEditorSelectedIds.remove(itemId)) {
            this.itemEditorSelectedIds.add(itemId);
         }
      }
   }

   private String firstSelectedItemId() {
      return this.itemEditorSelectedIds.isEmpty() ? "" : this.itemEditorSelectedIds.iterator().next();
   }

   private String selectedItemCountLabel() {
      return this.editingOriginal == null && !this.itemEditorSelectedIds.isEmpty() ? " | Selected " + this.itemEditorSelectedIds.size() : "";
   }

   private String addItemSaveLabel() {
      if (this.editingOriginal != null) {
         return "Save";
      }

      int count = this.itemEditorSelectedIds.size();
      return count <= 1 ? "Add Item" : "Add " + count + " Items";
   }

   private static QolConfig.AutoDropGroupItem copyItem(QolConfig.AutoDropGroupItem source) {
      QolConfig.AutoDropGroupItem copy = new QolConfig.AutoDropGroupItem();
      if (source == null) {
         return copy;
      }

      copyInto(source, copy);
      return copy;
   }

   private static void copyInto(QolConfig.AutoDropGroupItem source, QolConfig.AutoDropGroupItem target) {
      if (source != null && target != null) {
         target.itemId = source.itemId == null ? "" : source.itemId;
         target.minimumAmount = Math.max(0, source.minimumAmount);
         target.keepItems = Math.max(0, source.keepItems);
         target.keepStacks = Math.max(0, source.keepStacks);
         target.componentFilter = source.componentFilter == null ? "" : source.componentFilter;
      }
   }

   private static String compactItemLabel(QolConfig.AutoDropGroupItem entry, AutoDropperSubTab.ItemChoice choice) {
      String label = choice == null ? entry.itemId : choice.label;
      return shorten(label, 34) + " | S " + Math.max(0, entry.minimumAmount) + " K " + Math.max(0, entry.keepItems) + " St " + Math.max(0, entry.keepStacks);
   }

   private static String itemTooltip(QolConfig.AutoDropGroupItem entry, AutoDropperSubTab.ItemChoice choice) {
      String label = choice == null ? entry.itemId : choice.label;
      String id = entry.itemId == null ? "" : entry.itemId;
      String components = entry.componentFilter != null && !entry.componentFilter.isBlank() ? entry.componentFilter : "none";
      return label
         + "\n"
         + id
         + "\nStart At: "
         + Math.max(0, entry.minimumAmount)
         + "\nKeep Amount: "
         + Math.max(0, entry.keepItems)
         + "\nKeep Stacks: "
         + Math.max(0, entry.keepStacks)
         + "\nAdvanced Data: "
         + components;
   }

   private static String shorten(String value, int maxLength) {
      if (value == null) {
         return "";
      } else {
         return value.length() <= maxLength ? value : value.substring(0, Math.max(0, maxLength - 3)) + "...";
      }
   }

   @Override
   public void removed() {
      ConfigIO.saveIfDirty();
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, int contentTopOffset) {
      if (!this.groupsOnly || this.editingDraft == null) {
         return false;
      }

      if (mouseX < this.itemEditorListLeft || mouseX > this.itemEditorListRight) {
         return false;
      }

      if (!(mouseY < this.itemEditorListTop) && !(mouseY > this.itemEditorListBottom)) {
         int maxScroll = Math.max(0, filteredItems(this.itemEditorSearch).size() - 6);
         if (maxScroll <= 0) {
            return false;
         }

         this.itemEditorScroll = this.itemEditorScroll - (int)Math.signum(verticalAmount);
         this.itemEditorScroll = Math.max(0, Math.min(this.itemEditorScroll, maxScroll));
         return true;
      } else {
         return false;
      }
   }

   @Override
   public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
      QolConfig cfg = SuiteConfig.INSTANCE.QolConfig;
      if (this.groupsOnly) {
         if (this.importingGroupCode) {
            return contentTopOffset + 28 + 28 + (this.hasGroupShareNotice() ? 20 : 0) + 32 + 40;
         } else {
            QolConfig.AutoDropCustomGroup group = activeGroup(cfg);
            if (group == null) {
               return contentTopOffset + 70;
            } else {
               return this.editingDraft != null
                  ? contentTopOffset + 28 + 26 + 24 + 144 + 8 + 72 + 16 + 30 + 40
                  : contentTopOffset
                     + 24
                     + 28
                     + (this.hasGroupShareNotice() ? 18 : 0)
                     + cfg.autoDropperCustomGroups.size() * 24
                     + 6
                     + 28
                     + 16
                     + Math.max(1, group.items.size()) * 26
                     + 80;
            }
         }
      } else {
         return contentTopOffset + 8 + 336 + 40;
      }
   }

   private int addToggleRow(SuiteSettingsScreen screen, int x, int w, int y, String label, boolean enabled, String tooltip, Runnable onPress) {
      screen.addContentWidget(new HoverLabelWidget(x, y + 6, 220, 12, Text.literal(label), Tooltip.of(Text.literal(tooltip))));
      ButtonWidget button = StyledButton.of(Text.literal(enabled ? "ON" : "OFF"), b -> onPress.run()).dimensions(x + w - 80, y, 80, 20).build();
      button.setTooltip(Tooltip.of(Text.literal(tooltip)));
      screen.addContentWidget(button);
      return y + 28;
   }

   private int addButtonRow(SuiteSettingsScreen screen, int x, int w, int y, String label, String buttonText, String tooltip, Runnable onPress) {
      screen.addContentWidget(new HoverLabelWidget(x, y + 6, 220, 12, Text.literal(label), Tooltip.of(Text.literal(tooltip))));
      ButtonWidget button = StyledButton.of(Text.literal(buttonText), b -> onPress.run()).dimensions(x + w - 100, y, 100, 20).build();
      button.setTooltip(Tooltip.of(Text.literal(tooltip)));
      screen.addContentWidget(button);
      return y + 28;
   }

   private int addIntField(SuiteSettingsScreen screen, int x, int w, int y, String label, int value, String tooltip, AutoDropperSubTab.IntSetter setter) {
      screen.addContentWidget(new HoverLabelWidget(x, y + 2, Math.min(w, 180), 12, Text.literal(label), Tooltip.of(Text.literal(tooltip))));
      y += 16;
      TextFieldWidget field = new TextFieldWidget(screen.getTextRenderer(), x, y, w, 20, Text.empty());
      field.setMaxLength(8);
      field.setText(String.valueOf(value));
      field.setChangedListener(s -> {
         try {
            setter.set(Integer.parseInt(s != null && !s.isBlank() ? s.trim() : "0"));
            SuiteConfig.INSTANCE.markDirty();
         } catch (Exception var3) {
         }
      });
      screen.addContentWidget(field);
      return y + 28;
   }

   private static QolConfig.AutoDropCustomGroup activeGroup(QolConfig cfg) {
      if (cfg.autoDropperCustomGroups.isEmpty()) {
         return null;
      }

      if (cfg.autoDropperActiveCustomGroup < 0) {
         cfg.autoDropperActiveCustomGroup = 0;
      }

      if (cfg.autoDropperActiveCustomGroup >= cfg.autoDropperCustomGroups.size()) {
         cfg.autoDropperActiveCustomGroup = cfg.autoDropperCustomGroups.size() - 1;
      }

      return cfg.autoDropperCustomGroups.get(cfg.autoDropperActiveCustomGroup);
   }

   private static int editorScrollGutter(int contentWidth, int preferredGutter) {
      return contentWidth <= 160 ? 0 : Math.min(preferredGutter, Math.max(0, (contentWidth - 120) / 2));
   }

   private static String activeGroupTitle(QolConfig cfg) {
      QolConfig.AutoDropCustomGroup group = activeGroup(cfg);
      if (group == null) {
         return "No Custom Groups";
      }

      String name = group.name != null && !group.name.isBlank() ? group.name : "Custom Group";
      return group.enabled ? name : name + " (OFF)";
   }

   private static QolConfig.AutoDropGroupItem groupItemById(QolConfig.AutoDropCustomGroup group, String itemId) {
      if (group != null && itemId != null) {
         for (QolConfig.AutoDropGroupItem item : group.items) {
            if (item != null && itemId.equals(item.itemId)) {
               return item;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static List<AutoDropperSubTab.ItemChoice> filteredItems(String raw) {
      String q = normalize(raw);
      if (q.isBlank()) {
         return ITEM_CHOICES;
      }

      List<AutoDropperSubTab.ItemChoice> out = new ArrayList<>();

      for (AutoDropperSubTab.ItemChoice choice : ITEM_CHOICES) {
         if (choice.normalizedId.contains(q) || choice.normalizedLabel.contains(q)) {
            out.add(choice);
         }
      }

      return out;
   }

   private static AutoDropperSubTab.ItemChoice choiceById(String id) {
      for (AutoDropperSubTab.ItemChoice choice : ITEM_CHOICES) {
         if (choice.id.equals(id)) {
            return choice;
         }
      }

      return null;
   }

   private static List<AutoDropperSubTab.ItemChoice> buildItemChoices() {
      List<AutoDropperSubTab.ItemChoice> out = new ArrayList<>();

      for (Item item : Registries.ITEM) {
         Identifier id = Registries.ITEM.getId(item);
         if (id != null) {
            ItemStack stack = new ItemStack(item);
            if (!stack.isEmpty()) {
               String label = stack.getName().getString();
               out.add(new AutoDropperSubTab.ItemChoice(item, id.toString(), label != null && !label.isBlank() ? label : id.toString()));
            }
         }
      }

      out.sort(Comparator.comparing(choice -> choice.label, String.CASE_INSENSITIVE_ORDER));
      return List.copyOf(out);
   }

   private static String normalize(String raw) {
      return raw == null ? "" : raw.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
   }

   private interface IntSetter {
      void set(int var1);
   }

   private record ItemChoice(Item item, String id, String label, String normalizedId, String normalizedLabel) {
      ItemChoice(Item item, String id, String label) {
         this(item, id, label, AutoDropperSubTab.normalize(id), AutoDropperSubTab.normalize(label));
      }
   }
}
