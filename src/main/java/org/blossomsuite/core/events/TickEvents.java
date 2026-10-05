package org.blossomsuite.core.events;

import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
public class TickEvents {
   public static void register(Consumer<Minecraft> tickProcessor) {
      if (tickProcessor != null) {
         ClientTickEvents.END_CLIENT_TICK.register(tickProcessor::accept);
      }
   }
}
