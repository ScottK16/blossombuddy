package org.blossomsuite.core.mixin.client;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.blossomsuite.core.qol.locks.SlotProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops slot clicks that would drop or move an item out of a locked slot. The click is cancelled before it is
 * applied locally or sent, so the client and server stay in step.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class SlotClickProtectionMixin {
   @Inject(
      method = "handleContainerInput(IIILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void suitecore$lockedSlotClick(int syncId, int slotId, int button, ContainerInput actionType, Player player, CallbackInfo ci) {
      if (SlotProtection.blocksClick(player, slotId, button, actionType)) {
         SlotProtection.notifyBlocked(player);
         ci.cancel();
      }
   }
}
