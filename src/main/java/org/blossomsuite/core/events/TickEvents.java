package org.blossomsuite.core.events;

import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

public class TickEvents {
   public static void register(Consumer<MinecraftClient> tickProcessor) {
      if (tickProcessor != null) {
         ClientTickEvents.END_CLIENT_TICK.register(tickProcessor::accept);
      }
   }
}
