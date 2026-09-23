package org.blossomsuite.core.mixin.client;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import org.blossomsuite.core.qol.locks.SlotProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops slot clicks that would drop or move an item out of a locked slot. The click is cancelled before it is
 * applied locally or sent, so the client and server stay in step.
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class SlotClickProtectionMixin {
   @Inject(
      method = "clickSlot(IIILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void suitecore$lockedSlotClick(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
      if (SlotProtection.blocksClick(player, slotId, button, actionType)) {
         SlotProtection.notifyBlocked(player);
         ci.cancel();
      }
   }
}
