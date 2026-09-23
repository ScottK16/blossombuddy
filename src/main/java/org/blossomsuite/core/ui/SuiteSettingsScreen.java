package org.blossomsuite.core.ui;

import org.blossomsuite.core.commands.BuddyCommands;

import java.net.URI;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.blossomsuite.core.SuiteFeature;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.ui.QolSubTabs.AutoDropperSubTab;
import org.blossomsuite.core.ui.QolSubTabs.AutoFlySubTab;
import org.blossomsuite.core.ui.QolSubTabs.AutoSwapperSubTab;
import org.blossomsuite.core.ui.QolSubTabs.BiomeSubTab;
import org.blossomsuite.core.ui.QolSubTabs.BlockHighlightSubTab;
import org.blossomsuite.core.ui.QolSubTabs.CoordsSubTab;
import org.blossomsuite.core.ui.QolSubTabs.CrosshairSubTab;
import org.blossomsuite.core.ui.QolSubTabs.FishingSubTab;
import org.blossomsuite.core.ui.QolSubTabs.HolePuncherSubTab;
import org.blossomsuite.core.ui.QolSubTabs.InventorySortSubTab;
import org.blossomsuite.core.ui.QolSubTabs.MiningSubTab;
import org.blossomsuite.core.ui.QolSubTabs.RentalsSubTab;
import org.blossomsuite.core.ui.QolSubTabs.ToolLockSubTab;
import org.blossomsuite.core.ui.QolSubTabs.VoteSubTab;
import org.blossomsuite.core.util.SuiteLog;
import org.blossomsuite.core.util.SuiteModInfo;
import org.lwjgl.glfw.GLFW;

public class SuiteSettingsScreen extends Screen {
   public static boolean requestOpenOptionsScreen = false;

   private static final int HEADER_H = 46;
   private static final int NAV_LIST_INSET = 8;
   private static final int SEARCH_H = 24;
   private static final int RESULT_HEADER_H = 20;
   private static final int RESULT_ROW_GAP = 6;
   private static final int RESULT_SECTION_GAP = 10;

   private final Screen parent;
   private final List<SuiteTab> tabs = new ArrayList<>();
   private SuiteTab current;
   private int navLeft;
   private int navTop;
   private int navWidth;
   private int navBottom;
   private int contentLeft;
   private int contentTop;
   private int contentWidth;
   private int contentBottom;
   private int scrollOffset = 0;
   private int maxScroll = 0;
   private int navScrollOffset = 0;
   private int maxNavScroll = 0;
   private int navContentHeight = 0;
   private final Map<SuiteTab, TabButtonWidget> tabButtons = new IdentityHashMap<>();
   private final List<Drawable> contentDrawables = new ArrayList<>();
   private final List<Element> contentElements = new ArrayList<>();

   // search
   private TextFieldWidget searchField;
   private String query = "";
   private int searchX;
   private int searchW;
   private int resultCount = 0;
   /** Non-null only while a tab is being built for search; widgets go here instead of onto the screen. */
   private List<ClickableWidget> capture;

   private record Section(SettingsSearch.Leaf leaf, List<SettingsSearch.Row> rows) {
   }

   private record SearchLayout(List<Section> sections, int height, int count) {
   }

   public SuiteSettingsScreen(Screen parent) {
      super(Text.literal(SuiteRuntime.profile().displayName() + " Settings"));
      this.parent = parent;
      if (feature(SuiteFeature.AUTO_FLY)) {
         this.tabs.add(new SubTabSuiteTab(new AutoFlySubTab()));
      }

      if (feature(SuiteFeature.AUTO_DROPPER)) {
         this.tabs.add(new GroupedSubTabsSuiteTab("suitecore.tab.autodropper", new AutoDropperSubTab(), new AutoDropperSubTab(true)));
      }

      if (feature(SuiteFeature.AUTO_SWAPPER)) {
         this.tabs.add(new GroupedSubTabsSuiteTab("suitecore.tab.autoswapper", new AutoSwapperSubTab(), new AutoSwapperSubTab(true)));
      }

      if (feature(SuiteFeature.BIOME)) {
         this.tabs.add(new SubTabSuiteTab(new BiomeSubTab()));
      }

      if (feature(SuiteFeature.CHAT_TOOLS)) {
         this.tabs.add(new ChatTab());
      }

      if (feature(SuiteFeature.COOLDOWNS)) {
         this.tabs.add(new CooldownsTab());
      }

      if (feature(SuiteFeature.COORDS)) {
         this.tabs.add(new SubTabSuiteTab(new CoordsSubTab()));
      }

      if (feature(SuiteFeature.FISHING)) {
         this.tabs.add(new SubTabSuiteTab(new FishingSubTab()));
      }

      this.tabs.add(new GeneralTab());
      if (feature(SuiteFeature.HOLE_PUNCHER)) {
         this.tabs.add(new SubTabSuiteTab(new HolePuncherSubTab()));
      }

      if (feature(SuiteFeature.INVENTORY_SORT)) {
         this.tabs.add(new SubTabSuiteTab(new InventorySortSubTab()));
      }

      if (feature(SuiteFeature.JOBS)) {
         this.tabs.add(new JobsTab());
      }

      this.tabs.add(new SubTabSuiteTab(new BuddyTabs.Scoreboard()));
      this.tabs.add(new SubTabSuiteTab(new BuddyTabs.Hands()));
      this.tabs.add(new SubTabSuiteTab(new BuddyTabs.ExtraHotbar()));
      this.tabs.add(new SubTabSuiteTab(new BuddyTabs.SlotLocks()));
      this.tabs.add(new SubTabSuiteTab(new BuddyTabs.Sharing()));
      this.tabs.add(new SubTabSuiteTab(new BuddyTabs.CrossRealm()));
      this.tabs.add(new SubTabSuiteTab(new BuddyTabs.XpTracker()));
      this.tabs.add(new SubTabSuiteTab(new BuddyTabs.SecondaryChatPage()));
      this.tabs.add(new KeybindsTab());
      if (feature(SuiteFeature.MINING)) {
         this.tabs.add(new SubTabSuiteTab(new MiningSubTab()));
      }

      if (feature(SuiteFeature.RENTALS)) {
         this.tabs.add(new SubTabSuiteTab(new RentalsSubTab()));
      }

      if (feature(SuiteFeature.TOOL_LOCK)) {
         this.tabs.add(new SubTabSuiteTab(new ToolLockSubTab()));
      }

      if (feature(SuiteFeature.VISUALS)) {
         this.tabs.add(new GroupedSubTabsSuiteTab("suitecore.tab.visuals", new CrosshairSubTab(), new BlockHighlightSubTab()));
      }

      if (feature(SuiteFeature.VOTE)) {
         this.tabs.add(new SubTabSuiteTab(new VoteSubTab()));
      }

      this.current = this.tabs.get(0);
   }

   private static boolean feature(SuiteFeature feature) {
      return SuiteRuntime.isEnabled(feature);
   }

   private boolean searching() {
      return !this.query.isBlank();
   }

   public int contentX() {
      return this.contentLeft + 16;
   }

   public int contentW() {
      return this.contentWidth - 32;
   }

   public int contentY() {
      return this.contentTop;
   }

   public int headerControlY() {
      return this.contentTop + 6;
   }

   public int dividerY() {
      return this.contentTop + 28;
   }

   public int bodyContentY() {
      return this.dividerY() + 10;
   }

   public int contentViewportTop() {
      return this.dividerY() + 2;
   }

   public int contentViewportBottom() {
      return this.contentBottom - 4;
   }

   public int contentViewportHeight() {
      return Math.max(0, this.contentViewportBottom() - this.contentViewportTop());
   }

   /** Zero while capturing for search, so widgets are built at their unscrolled positions. */
   public int scrollOffset() {
      return this.capture != null ? 0 : this.scrollOffset;
   }

   public boolean usesSidebarNavigation() {
      return true;
   }

   @Override
   protected void init() {
      this.navLeft = 16;
      this.navTop = HEADER_H;
      this.navWidth = Math.max(132, Math.min(176, this.width / 4));
      this.navBottom = this.height - 42;
      this.contentLeft = this.navLeft + this.navWidth + 16;
      this.contentWidth = Math.max(260, this.width - this.contentLeft - 16);
      this.contentTop = 74;
      this.contentBottom = this.height - 42;
      this.searchW = Math.max(160, Math.min(320, this.contentWidth));
      this.searchX = this.contentLeft + this.contentWidth - this.searchW;
      if (this.searchField == null) {
         this.searchField = new TextFieldWidget(this.textRenderer, 0, 0, 10, 10, Text.literal("Search settings"));
         this.searchField.setDrawsBackground(false);
         this.searchField.setMaxLength(48);
         this.searchField.setPlaceholder(Text.literal("Search settings...").formatted(Formatting.DARK_GRAY));
         this.searchField.setChangedListener(this::onSearchChanged);
      }

      this.searchField.setX(this.searchX + 24);
      this.searchField.setY(12 + (SEARCH_H - 8) / 2);
      this.searchField.setWidth(this.searchW - 24 - 28);
      this.searchField.setHeight(10);
      this.rebuild();
   }

   @Override
   public void resize(MinecraftClient client, int width, int height) {
      super.resize(client, width, height);
      this.rebuild();
   }

   private void onSearchChanged(String text) {
      if (!text.equals(this.query)) {
         this.query = text;
         this.scrollOffset = 0;
         this.rebuild();
      }
   }

   private void clearSearch() {
      this.query = "";
      this.searchField.setText("");
      this.scrollOffset = 0;
      this.rebuild();
   }

   private void rebuild() {
      this.clearChildren();
      this.tabButtons.clear();
      this.contentDrawables.clear();
      this.contentElements.clear();
      if (this.searching()) {
         SearchLayout layout = this.computeSearchLayout();
         this.resultCount = layout.count();
         this.maxScroll = Math.max(0, layout.height() + 8 - this.contentViewportHeight());
         this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, this.maxScroll));
         this.addNavRail();
         this.addSearchBox();
         this.placeSearchLayout(layout);
      } else {
         this.resultCount = 0;
         this.maxScroll = Math.max(0, this.current.contentHeight(this) - this.contentViewportHeight());
         this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, this.maxScroll));
         this.addNavRail();
         this.addSearchBox();
         this.addHeaderControls();
         this.addContent();
      }

      this.addFooter();
   }

   // ---------------------------------------------------------------- search

   private void addSearchBox() {
      this.addDrawableChild(this.searchField);
      if (this.searching()) {
         this.addDrawableChild(
            StyledButton.of(Text.literal("x"), b -> this.clearSearch()).dimensions(this.searchX + this.searchW - 22, 12 + 4, 16, SEARCH_H - 8).build()
         );
      }
   }

   private SearchLayout computeSearchLayout() {
      String[] tokens = SettingsSearch.tokens(this.query);
      List<Section> sections = new ArrayList<>();
      int height = 0;
      int count = 0;
      this.capture = new ArrayList<>();
      try {
         for (SettingsSearch.Leaf leaf : SettingsSearch.leaves(this.tabs, this)) {
            this.capture.clear();
            try {
               SettingsSearch.build(leaf, this);
            } catch (RuntimeException e) {
               SuiteLog.logger().warn("[search] could not index {}: {}", leaf.path(), e.toString());
               continue;
            }

            List<SettingsSearch.Row> rows = new ArrayList<>();
            for (SettingsSearch.Row row : SettingsSearch.group(new ArrayList<>(this.capture))) {
               if (SettingsSearch.matches(row, leaf, tokens)) {
                  rows.add(row);
               }
            }

            if (!rows.isEmpty()) {
               sections.add(new Section(leaf, rows));
               height += RESULT_HEADER_H;
               for (SettingsSearch.Row row : rows) {
                  height += row.height() + RESULT_ROW_GAP;
                  count++;
               }

               height += RESULT_SECTION_GAP;
            }
         }
      } finally {
         this.capture = null;
      }

      return new SearchLayout(sections, height, count);
   }

   private void placeSearchLayout(SearchLayout layout) {
      int x = this.contentX();
      int w = this.contentW();
      int base = this.bodyContentY() - this.scrollOffset;
      if (layout.sections().isEmpty()) {
         this.addContentWidget(new LabelWidget(x, base + 4, w, 14, Text.literal("No settings match \"" + this.query.trim() + "\"."), Theme.TEXT_DIM));
         this.addContentWidget(new LabelWidget(x, base + 20, w, 14, Text.literal("Try a shorter word, like \"cooldown\" or \"hud\"."), Theme.TEXT_MUTED));
         return;
      }

      int y = 0;
      for (Section section : layout.sections()) {
         this.addContentWidget(new LabelWidget(x, base + y + 4, w - 64, 12, Text.literal(section.leaf().path()), Theme.ACCENT));
         this.addContentWidget(
            StyledButton.of(Text.literal("Open"), b -> this.openLeaf(section.leaf())).dimensions(x + w - 56, base + y, 56, 16).build()
         );
         y += RESULT_HEADER_H;
         for (SettingsSearch.Row row : section.rows()) {
            int shift = base + y - row.top();
            for (ClickableWidget widget : row.widgets()) {
               widget.setY(widget.getY() + shift);
               this.addContentWidget(widget);
            }

            y += row.height() + RESULT_ROW_GAP;
         }

         y += RESULT_SECTION_GAP;
      }
   }

   private void openLeaf(SettingsSearch.Leaf leaf) {
      this.query = "";
      this.searchField.setText("");
      if (this.current != leaf.tab()) {
         if (this.current != null) {
            this.current.removed();
         }

         this.current = leaf.tab();
      }

      if (leaf.nested() != null) {
         SuiteSubTab target = leaf.group() != null ? leaf.group() : leaf.sub();
         leaf.nested().selectSubTab(target);
         if (leaf.group() instanceof SuiteSubTabGroup group) {
            group.selectChild(leaf.sub());
         }
      }

      this.rebuildFromTab();
   }

   private void selectTab(SuiteTab tab) {
      boolean wasSearching = this.searching();
      if (wasSearching) {
         this.query = "";
         this.searchField.setText("");
      }

      if (this.current != tab) {
         this.current.removed();
         this.current = tab;
         this.rebuildFromTab();
      } else if (wasSearching) {
         this.rebuildFromTab();
      }
   }

   // ---------------------------------------------------------------- navigation rail

   private int navListTop() {
      return this.navTop + NAV_LIST_INSET;
   }

   private void addNavRail() {
      int x = this.navLeft + 8;
      int navListTop = this.navListTop();
      int navListBottom = this.navBottom - 8;
      int rowH = 22;
      int gap = 4;
      int w = this.navWidth - 16;
      boolean searching = this.searching();
      this.navContentHeight = this.computeNavContentHeight(rowH, gap);
      this.maxNavScroll = Math.max(0, this.navContentHeight - Math.max(0, navListBottom - navListTop));
      this.navScrollOffset = Math.max(0, Math.min(this.navScrollOffset, this.maxNavScroll));
      int y = navListTop - this.navScrollOffset;

      for (SuiteTab tab : this.tabs) {
         boolean selected = tab == this.current && !searching;
         TabButtonWidget btn = new TabButtonWidget(x, y, w, rowH, Text.translatable(tab.titleKey()), selected, () -> this.selectTab(tab));
         this.tabButtons.put(tab, btn);
         btn.visible = fullyVisible(y, rowH, navListTop, navListBottom);
         this.addHeaderWidget(btn);
         y += rowH + gap;
         if (tab == this.current && !searching && tab instanceof NestedSuiteTab nested) {
            for (SuiteSubTab subTab : nested.sidebarSubTabs()) {
               boolean subSelected = subTab == nested.currentSubTab();
               TabButtonWidget subBtn = new TabButtonWidget(x + 12, y, w - 12, 18, Text.translatable(subTab.titleKey()), subSelected, () -> {
                  nested.selectSubTab(subTab);
                  this.rebuildFromTab();
               });
               subBtn.visible = fullyVisible(y, 18, navListTop, navListBottom);
               this.addHeaderWidget(subBtn);
               y += 20;
               if (subTab instanceof SuiteSubTabGroup group && subSelected) {
                  for (SuiteSubTab child : group.children()) {
                     boolean childSelected = child == group.currentChild();
                     TabButtonWidget childBtn = new TabButtonWidget(x + 24, y, w - 24, 18, Text.translatable(child.titleKey()), childSelected, () -> {
                        nested.selectSubTab(group);
                        group.selectChild(child);
                        this.rebuildFromTab();
                     });
                     childBtn.visible = fullyVisible(y, 18, navListTop, navListBottom);
                     this.addHeaderWidget(childBtn);
                     y += 20;
                  }
               }
            }

            y += 4;
         }
      }
   }

   private int computeNavContentHeight(int rowH, int gap) {
      int height = 0;
      boolean searching = this.searching();

      for (SuiteTab tab : this.tabs) {
         height += rowH + gap;
         if (tab == this.current && !searching && tab instanceof NestedSuiteTab nested) {
            for (SuiteSubTab subTab : nested.sidebarSubTabs()) {
               height += 20;
               if (subTab instanceof SuiteSubTabGroup group && subTab == nested.currentSubTab()) {
                  height += group.children().size() * 20;
               }
            }

            height += 4;
         }
      }

      return Math.max(0, height);
   }

   private void addHeaderControls() {
      this.current.buildHeaderControls(this);
   }

   private void addContent() {
      this.current.build(this);
   }

   private void addFooter() {
      int y = this.height - 30;
      this.addDrawableChild(
         StyledButton.of(Text.literal("Discord"), ConfirmLinkScreen.opening(this, URI.create(BuddyCommands.DISCORD_INVITE)))
            .dimensions(this.contentLeft, y, 90, 22)
            .build()
      );
      this.addDrawableChild(
         StyledButton.of(Text.literal("Privacy"), ConfirmLinkScreen.opening(this, URI.create(BuddyCommands.PRIVACY_URL)))
            .dimensions(this.contentLeft + 96, y, 90, 22)
            .build()
      );
      this.addDrawableChild(StyledButton.of(Text.translatable("gui.done"), b -> {
         this.current.removed();
         ConfigIO.save();
         this.client.setScreen(this.parent);
      }).dimensions(this.contentLeft + this.contentWidth - 126, y, 120, 22).build().accent());
   }

   @Override
   public void close() {
      if (this.current != null) {
         this.current.removed();
      }

      ConfigIO.save();
      if (this.client != null) {
         this.client.setScreen(this.parent);
      }
   }

   public void rebuildPreserveScroll() {
      this.rebuild();
   }

   public void rebuildFromTab() {
      this.scrollOffset = 0;
      this.rebuild();
   }

   // ---------------------------------------------------------------- input

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      boolean insideNav = mouseX >= this.navLeft
         && mouseX <= this.navLeft + this.navWidth
         && mouseY >= this.navListTop()
         && mouseY <= this.navBottom - 8;
      if (insideNav && this.maxNavScroll > 0) {
         this.navScrollOffset -= (int)(verticalAmount * 20.0);
         this.navScrollOffset = Math.max(0, Math.min(this.navScrollOffset, this.maxNavScroll));
         this.rebuildPreserveScroll();
         return true;
      } else {
         boolean inside = mouseX >= this.contentLeft
            && mouseX <= this.contentLeft + this.contentWidth
            && this.contentViewportBottom() > this.contentViewportTop()
            && mouseY >= this.contentViewportTop()
            && mouseY <= this.contentViewportBottom();
         if (inside && !this.searching() && this.current != null && this.current.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            this.rebuildPreserveScroll();
            return true;
         } else if (inside && this.maxScroll > 0) {
            this.scrollOffset -= (int)(verticalAmount * 20.0);
            this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, this.maxScroll));
            this.rebuildPreserveScroll();
            return true;
         } else {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
         }
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      boolean inSearchPill = mouseX >= this.searchX && mouseX <= this.searchX + this.searchW && mouseY >= 12 && mouseY <= 12 + SEARCH_H;
      if (inSearchPill && button == 0) {
         boolean onClear = this.searching() && mouseX >= this.searchX + this.searchW - 24;
         if (!onClear) {
            this.setFocused(this.searchField);
            return true;
         }
      }

      if (!this.searching() && this.current != null && this.current.mouseClicked(mouseX, mouseY, button)) {
         this.rebuildPreserveScroll();
         return true;
      } else {
         return !(mouseY < this.contentViewportTop()) && !(mouseY > this.contentViewportBottom())
            ? super.mouseClicked(mouseX, mouseY, button)
            : this.mouseClickedNonContent(mouseX, mouseY, button);
      }
   }

   private boolean mouseClickedNonContent(double mouseX, double mouseY, int button) {
      for (Element child : this.children()) {
         if (!this.contentElements.contains(child) && child.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(child);
            if (button == 0) {
               this.setDragging(true);
            }

            return true;
         }
      }

      return false;
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_F) {
         this.setFocused(this.searchField);
         return true;
      }

      if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.searching()) {
         this.clearSearch();
         return true;
      }

      if (!this.searching() && this.current != null && this.current.keyPressed(keyCode, scanCode, modifiers)) {
         this.rebuildPreserveScroll();
         return true;
      } else {
         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   @Override
   public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
      if (!this.searching() && this.current != null && this.current.keyReleased(keyCode, scanCode, modifiers)) {
         this.rebuildPreserveScroll();
         return true;
      } else {
         return super.keyReleased(keyCode, scanCode, modifiers);
      }
   }

   // ---------------------------------------------------------------- drawing

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      TextRenderer tr = this.textRenderer;
      context.fillGradient(0, 0, this.width, this.height, Theme.BG_TOP, Theme.BG_BOTTOM);
      this.renderWordmark(context, tr);
      this.renderSearchPill(context);

      Theme.roundBox(context, this.navLeft, this.navTop, this.navLeft + this.navWidth, this.navBottom, 5, Theme.PANEL_EDGE, Theme.PANEL);
      this.renderNavScrollbar(context);

      int panelX1 = this.contentLeft;
      int panelY1 = HEADER_H;
      int panelX2 = this.contentLeft + this.contentWidth;
      int panelY2 = this.contentBottom;
      Theme.roundBox(context, panelX1, panelY1, panelX2, panelY2, 5, Theme.PANEL_EDGE, Theme.PANEL);

      Text title;
      if (this.searching()) {
         title = Text.literal("Search results").formatted(Formatting.BOLD)
            .append(Text.literal("  " + this.resultCount + (this.resultCount == 1 ? " setting" : " settings")).formatted(Formatting.RESET));
      } else {
         title = Text.translatable(this.current.titleKey()).formatted(Formatting.BOLD);
      }

      context.drawTextWithShadow(tr, title, this.contentLeft + 16, panelY1 + 10, Theme.TEXT);
      context.drawHorizontalLine(this.contentLeft + 16, this.contentLeft + this.contentWidth - 16, this.dividerY(), Theme.PANEL_EDGE);
      context.fill(this.contentLeft + 16, this.dividerY(), this.contentLeft + 16 + 36, this.dividerY() + 1, Theme.ACCENT);

      String hint = this.searching() ? "Esc  clear search" : "Ctrl+F  search settings";
      context.drawTextWithShadow(tr, Text.literal(hint), this.contentLeft + 2, this.height - 24, Theme.TEXT_MUTED);

      super.render(context, mouseX, mouseY, delta);
      if (this.contentViewportBottom() > this.contentViewportTop() && this.contentWidth > 2) {
         context.enableScissor(this.contentLeft + 1, this.contentViewportTop(), this.contentLeft + this.contentWidth - 1, this.contentViewportBottom());

         for (Drawable drawable : this.contentDrawables) {
            drawable.render(context, mouseX, mouseY, delta);
         }

         if (!this.searching()) {
            this.current.renderText(this, context, mouseX, mouseY, delta);
         }

         context.disableScissor();
      }
   }

   private void renderWordmark(DrawContext context, TextRenderer tr) {
      String name = SuiteRuntime.profile().displayName();
      int split = -1;
      for (int i = 1; i < name.length(); i++) {
         if (Character.isUpperCase(name.charAt(i))) {
            split = i;
            break;
         }
      }

      String first = split < 0 ? name : name.substring(0, split);
      String second = split < 0 ? "" : name.substring(split);
      Text a = Text.literal(first).formatted(Formatting.BOLD);
      int x = this.navLeft + 2;
      context.drawTextWithShadow(tr, a, x, 7, Theme.ACCENT);
      if (!second.isEmpty()) {
         context.drawTextWithShadow(tr, Text.literal(second).formatted(Formatting.BOLD), x + tr.getWidth(a), 7, Theme.LAVENDER);
      }

      context.drawTextWithShadow(tr, Text.literal("v" + SuiteModInfo.getVersion()), x, 19, Theme.TEXT_MUTED);
      context.drawTextWithShadow(tr, Text.literal("Developed by " + BuddyCommands.DEVELOPER), x, 31, Theme.ACCENT_DIM);
   }

   private void renderSearchPill(DrawContext context) {
      boolean focused = this.searchField != null && this.searchField.isFocused();
      Theme.roundBox(
         context, this.searchX, 12, this.searchX + this.searchW, 12 + SEARCH_H, 5, focused ? Theme.ACCENT_DIM : Theme.CONTROL_EDGE, Theme.CONTROL_OFF
      );
      Theme.magnifier(context, this.searchX + 8, 12 + (SEARCH_H - 11) / 2, focused ? Theme.ACCENT : Theme.TEXT_MUTED);
   }

   @Override
   public TextRenderer getTextRenderer() {
      return this.textRenderer;
   }

   private static boolean fullyVisible(int y, int height, int top, int bottom) {
      return y >= top && y + height <= bottom;
   }

   private void renderNavScrollbar(DrawContext context) {
      if (this.maxNavScroll > 0) {
         int trackX = this.navLeft + this.navWidth - 5;
         int trackY = this.navListTop();
         int trackH = Math.max(1, this.navBottom - 8 - trackY);
         int thumbH = Math.max(18, (int)((double)trackH * trackH / Math.max(trackH, this.navContentHeight)));
         thumbH = Math.min(trackH, thumbH);
         int movable = Math.max(1, trackH - thumbH);
         int thumbY = trackY + (int)Math.round((double)this.navScrollOffset / this.maxNavScroll * movable);
         context.fill(trackX, trackY, trackX + 2, trackY + trackH, Theme.CONTROL);
         context.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbH, Theme.ACCENT_DIM);
      }
   }

   // ---------------------------------------------------------------- widget registration used by the tabs

   public <T extends Element & Drawable & Selectable> T addHeaderWidget(T widget) {
      if (this.capture != null) {
         if (widget instanceof ClickableWidget clickable) {
            this.capture.add(clickable);
         }

         return widget;
      }

      return this.addDrawableChild(widget);
   }

   public <T extends Element & Drawable & Selectable> T addWidget(T widget) {
      return this.addHeaderWidget(widget);
   }

   public <T extends Element & Drawable & Selectable> T addContentWidget(T widget) {
      if (this.capture != null) {
         if (widget instanceof ClickableWidget clickable) {
            this.capture.add(clickable);
         }

         return widget;
      }

      this.addSelectableChild(widget);
      this.contentDrawables.add(widget);
      this.contentElements.add(widget);
      return widget;
   }
}
