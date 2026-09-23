package org.blossomsuite.core.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public abstract class NestedSuiteTab implements SuiteTab {
   protected final List<SuiteSubTab> subTabs = new ArrayList<>();
   protected SuiteSubTab current;
   protected int subTabScrollIndex = 0;

   protected abstract void initializeSubTabs();

   protected int topControlsHeight() {
      return 0;
   }

   protected int subTabBarHeight() {
      return 30;
   }

   protected int subTabGap() {
      return 2;
   }

   protected int subTabContentOffset() {
      return this.topControlsHeight() + this.subTabBarHeight() + 12;
   }

   protected int subTabContentOffset(SuiteSettingsScreen screen) {
      return screen.usesSidebarNavigation() ? this.topControlsHeight() + 12 : this.subTabContentOffset();
   }

   /** Vertical offset sub-tab content is built at (used by the settings search). */
   public int contentOffset(SuiteSettingsScreen screen) {
      return this.subTabContentOffset(screen);
   }

   protected void ensureInitialized() {
      if (this.subTabs.isEmpty()) {
         this.initializeSubTabs();
         if (!this.subTabs.isEmpty() && this.current == null) {
            this.current = this.subTabs.get(0);
         }
      }
   }

   @Override
   public void build(SuiteSettingsScreen screen) {
      this.ensureInitialized();
      if (this.current != null) {
         this.current.build(screen, this.subTabContentOffset(screen));
      }

      this.buildSubTabBar(screen);
   }

   @Override
   public void renderText(SuiteSettingsScreen screen, DrawContext ctx, int mouseX, int mouseY, float delta) {
      this.ensureInitialized();
      if (this.current != null) {
         this.current.renderText(screen, ctx, mouseX, mouseY, delta, this.subTabContentOffset(screen));
      }
   }

   @Override
   public void removed() {
      if (this.current != null) {
         this.current.removed();
      }
   }

   protected void buildSubTabBar(SuiteSettingsScreen screen) {
      if (!screen.usesSidebarNavigation()) {
         int x = screen.contentX();
         int y = screen.contentY() + this.topControlsHeight();
         int w = screen.contentW();
         int gap = this.subTabGap();
         int baseH = 18;
         int count = this.subTabs.size();
         if (count > 0) {
            int minButtonW = 92;
            int arrowW = 18;
            boolean needsScroll = count * 92 + (count - 1) * gap > w;
            int availableW = needsScroll ? w - (36 + gap * 2) : w;
            int maxVisible = Math.max(1, (availableW + gap) / (92 + gap));
            int visible = Math.min(count, maxVisible);
            int maxStart = Math.max(0, count - visible);
            if (this.subTabScrollIndex < 0) {
               this.subTabScrollIndex = 0;
            }

            if (this.subTabScrollIndex > maxStart) {
               this.subTabScrollIndex = maxStart;
            }

            int buttonW = (availableW - (visible - 1) * gap) / visible;
            if (buttonW < 92) {
               buttonW = 92;
            }

            int drawX = x;
            if (needsScroll) {
               boolean canLeft = this.subTabScrollIndex > 0;
               boolean canRight = this.subTabScrollIndex < maxStart;
               ButtonWidget left = StyledButton.of(Text.literal("<"), b -> {
                  this.subTabScrollIndex = Math.max(0, this.subTabScrollIndex - 1);
                  int idx = this.current == null ? -1 : this.subTabs.indexOf(this.current);
                  if (idx >= 0) {
                     if (idx < this.subTabScrollIndex) {
                        if (this.current != null) {
                           this.current.removed();
                        }

                        this.current = this.subTabs.get(this.subTabScrollIndex);
                     } else if (idx >= this.subTabScrollIndex + visible) {
                        int pick = Math.min(this.subTabs.size() - 1, this.subTabScrollIndex + visible - 1);
                        if (this.current != null) {
                           this.current.removed();
                        }

                        this.current = this.subTabs.get(pick);
                     }
                  }

                  screen.rebuildPreserveScroll();
               }).dimensions(drawX, y, 18, baseH).build();
               left.active = canLeft;
               screen.addWidget(left);
               drawX += 18 + gap;
               int rightArrowX = x + w - 18;
               ButtonWidget right = StyledButton.of(Text.literal(">"), b -> {
                  this.subTabScrollIndex = Math.min(maxStart, this.subTabScrollIndex + 1);
                  int idx = this.current == null ? -1 : this.subTabs.indexOf(this.current);
                  if (idx >= 0) {
                     if (idx < this.subTabScrollIndex) {
                        if (this.current != null) {
                           this.current.removed();
                        }

                        this.current = this.subTabs.get(this.subTabScrollIndex);
                     } else if (idx >= this.subTabScrollIndex + visible) {
                        int pick = Math.min(this.subTabs.size() - 1, this.subTabScrollIndex + visible - 1);
                        if (this.current != null) {
                           this.current.removed();
                        }

                        this.current = this.subTabs.get(pick);
                     }
                  }

                  screen.rebuildPreserveScroll();
               }).dimensions(rightArrowX, y, 18, baseH).build();
               right.active = canRight;
               screen.addWidget(right);
               int trackX = x + 18 + gap;
               int trackW = w - (36 + gap * 2);
               int trackY = y + baseH + 2;
               screen.addWidget(new SubTabScrollBarWidget(trackX, trackY, trackW, 10, count, visible, this.subTabScrollIndex, idx -> {
                  this.subTabScrollIndex = Math.max(0, Math.min(maxStart, idx));
                  int curIdx = this.current == null ? -1 : this.subTabs.indexOf(this.current);
                  if (curIdx >= 0) {
                     if (curIdx < this.subTabScrollIndex) {
                        if (this.current != null) {
                           this.current.removed();
                        }

                        this.current = this.subTabs.get(this.subTabScrollIndex);
                     } else if (curIdx >= this.subTabScrollIndex + visible) {
                        int pick = Math.min(this.subTabs.size() - 1, this.subTabScrollIndex + visible - 1);
                        if (this.current != null) {
                           this.current.removed();
                        }

                        this.current = this.subTabs.get(pick);
                     }
                  }

                  screen.rebuildPreserveScroll();
               }));
            }

            int start = this.subTabScrollIndex;
            int end = Math.min(count, start + visible);

            for (int i = start; i < end; i++) {
               SuiteSubTab tab = this.subTabs.get(i);
               boolean selected = tab == this.current;
               int h = selected ? baseH + 2 : baseH;
               int yPos = selected ? y - 2 : y;
               screen.addWidget(new TabButtonWidget(drawX, yPos, buttonW, h, Text.translatable(tab.titleKey()), selected, () -> {
                  if (this.current != tab) {
                     if (this.current != null) {
                        this.current.removed();
                     }

                     this.current = tab;
                     screen.rebuildFromTab();
                  }
               }));
               drawX += buttonW + gap;
            }
         }
      }
   }

   public int currentSubTabContentHeight(SuiteSettingsScreen screen) {
      return this.current == null ? 0 : this.current.contentHeight(screen, this.subTabContentOffset(screen));
   }

   public List<SuiteSubTab> sidebarSubTabs() {
      this.ensureInitialized();
      return List.copyOf(this.subTabs);
   }

   public SuiteSubTab currentSubTab() {
      this.ensureInitialized();
      return this.current;
   }

   public void selectSubTab(SuiteSubTab tab) {
      this.ensureInitialized();
      if (tab != null && tab != this.current && this.subTabs.contains(tab)) {
         if (this.current != null) {
            this.current.removed();
         }

         this.current = tab;
      }
   }

   @Override
   public int contentHeight(SuiteSettingsScreen screen) {
      this.ensureInitialized();
      return this.current == null ? 0 : this.current.contentHeight(screen, this.subTabContentOffset(screen));
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      this.ensureInitialized();
      return this.current != null && this.current.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount, 0);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      this.ensureInitialized();
      return this.current != null && this.current.mouseClicked(mouseX, mouseY, button, 0);
   }
}
