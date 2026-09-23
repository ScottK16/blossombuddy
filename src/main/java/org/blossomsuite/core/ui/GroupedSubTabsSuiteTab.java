package org.blossomsuite.core.ui;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class GroupedSubTabsSuiteTab extends NestedSuiteTab {
   private final String titleKey;
   private final List<SuiteSubTab> children;

   public GroupedSubTabsSuiteTab(String titleKey, SuiteSubTab... children) {
      this.titleKey = titleKey;
      this.children = children == null ? List.of() : Arrays.stream(children).filter(Objects::nonNull).toList();
   }

   @Override
   public String titleKey() {
      return this.titleKey;
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
      this.subTabs.addAll(this.children);
   }
}
