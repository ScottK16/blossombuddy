package org.blossomsuite.core.ui;

import net.minecraft.client.gui.tooltip.Tooltip;

/** A widget that remembers the tooltip it was given, so the settings search can read its text. */
interface TooltipHolder {
   Tooltip heldTooltip();
}
