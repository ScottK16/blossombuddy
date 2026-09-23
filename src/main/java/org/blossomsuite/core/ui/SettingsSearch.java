package org.blossomsuite.core.ui;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

/**
 * Finds settings by name. Every tab builds its widgets through {@link SuiteSettingsScreen}, so search asks each
 * tab to build once into a throw-away capture, groups the widgets into rows (a label plus the controls beside
 * it), and keeps the rows whose label, tooltip or section matches the query.
 */
final class SettingsSearch {
   private static final String SEP = " > ";

   private SettingsSearch() {
   }

   /** One page of settings: a top-level tab, or one sub-tab of it. */
   record Leaf(SuiteTab tab, NestedSuiteTab nested, SuiteSubTab group, SuiteSubTab sub, String path, int offset) {
   }

   /** A label and the controls that belong to it, at the position they were built. */
   record Row(List<ClickableWidget> widgets, String text, int top, int bottom) {
      int height() {
         return this.bottom - this.top;
      }
   }

   static List<Leaf> leaves(List<SuiteTab> tabs, SuiteSettingsScreen screen) {
      List<Leaf> out = new ArrayList<>();
      for (SuiteTab tab : tabs) {
         String tabTitle = title(tab.titleKey());
         if (tab instanceof NestedSuiteTab nested) {
            int offset = nested.contentOffset(screen);
            for (SuiteSubTab sub : nested.sidebarSubTabs()) {
               String subTitle = tabTitle + SEP + title(sub.titleKey());
               if (sub instanceof SuiteSubTabGroup group) {
                  for (SuiteSubTab child : group.children()) {
                     out.add(new Leaf(tab, nested, group, child, subTitle + SEP + title(child.titleKey()), offset));
                  }
               } else {
                  out.add(new Leaf(tab, nested, null, sub, subTitle, offset));
               }
            }
         } else if (tab instanceof SubTabSuiteTab single) {
            out.add(new Leaf(tab, null, null, single.subTab(), tabTitle, 0));
         } else {
            out.add(new Leaf(tab, null, null, null, tabTitle, 0));
         }
      }

      return out;
   }

   /** Builds the leaf's widgets (into the screen's capture list). */
   static void build(Leaf leaf, SuiteSettingsScreen screen) {
      if (leaf.sub() != null) {
         leaf.sub().build(screen, leaf.offset());
      } else {
         leaf.tab().build(screen);
      }
   }

   static List<Row> group(List<ClickableWidget> widgets) {
      List<ClickableWidget> anchors = new ArrayList<>();
      for (ClickableWidget w : widgets) {
         if (w.visible && (w instanceof HoverLabelWidget || w instanceof LabelWidget)) {
            anchors.add(w);
         }
      }

      Map<ClickableWidget, List<ClickableWidget>> attached = new IdentityHashMap<>();
      List<ClickableWidget> orphans = new ArrayList<>();
      for (ClickableWidget w : widgets) {
         if (!w.visible || anchors.contains(w)) {
            continue;
         }

         ClickableWidget best = null;
         int bestDist = Integer.MAX_VALUE;
         for (ClickableWidget a : anchors) {
            // a control sits beside its label: level with it, or a little below (sliders)
            if (w.getY() >= a.getY() - 8 && w.getY() < a.getY() + 16) {
               int dist = Math.abs(centerY(w) - centerY(a));
               if (dist < bestDist) {
                  bestDist = dist;
                  best = a;
               }
            }
         }

         if (best == null) {
            orphans.add(w);
         } else {
            attached.computeIfAbsent(best, k -> new ArrayList<>()).add(w);
         }
      }

      List<Row> rows = new ArrayList<>();
      for (ClickableWidget a : anchors) {
         List<ClickableWidget> members = new ArrayList<>();
         members.add(a);
         members.addAll(attached.getOrDefault(a, List.of()));
         rows.add(row(members));
      }

      for (ClickableWidget o : orphans) {
         rows.add(row(List.of(o)));
      }

      rows.sort((a, b) -> Integer.compare(a.top(), b.top()));
      return rows;
   }

   private static Row row(List<ClickableWidget> members) {
      int top = Integer.MAX_VALUE;
      int bottom = Integer.MIN_VALUE;
      StringBuilder text = new StringBuilder();
      for (ClickableWidget w : members) {
         top = Math.min(top, w.getY());
         bottom = Math.max(bottom, w.getY() + w.getHeight());
         text.append(w.getMessage().getString()).append(' ').append(tooltipText(w)).append(' ');
      }

      return new Row(List.copyOf(members), text.toString().toLowerCase(Locale.ROOT), top, bottom);
   }

   static String[] tokens(String query) {
      String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
      return q.isEmpty() ? new String[0] : q.split("\\s+");
   }

   /** Every word of the query must appear in the row's text or in the name of the section it lives in. */
   static boolean matches(Row row, Leaf leaf, String[] tokens) {
      if (tokens.length == 0 || row.text().isBlank()) {
         return false;
      }

      String path = leaf.path().toLowerCase(Locale.ROOT);
      for (String token : tokens) {
         if (!row.text().contains(token) && !path.contains(token)) {
            return false;
         }
      }

      return true;
   }

   private static int centerY(ClickableWidget w) {
      return w.getY() + w.getHeight() / 2;
   }

   private static String title(String translationKey) {
      return Text.translatable(translationKey).getString();
   }

   private static String tooltipText(ClickableWidget w) {
      try {
         Tooltip tooltip = w instanceof TooltipHolder holder ? holder.heldTooltip() : null;
         if (tooltip == null) {
            return "";
         }

         StringBuilder sb = new StringBuilder();
         for (OrderedText line : tooltip.getLines(MinecraftClient.getInstance())) {
            line.accept((index, style, codePoint) -> {
               sb.appendCodePoint(codePoint);
               return true;
            });
            sb.append(' ');
         }

         return sb.toString();
      } catch (RuntimeException e) {
         return "";
      }
   }
}
