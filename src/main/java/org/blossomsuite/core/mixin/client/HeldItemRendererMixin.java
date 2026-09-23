package org.blossomsuite.core.mixin.client;

import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
   @Inject(method = "shouldSkipHandAnimationOnSwap(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
   private void suitecore$skipReequip(ItemStack from, ItemStack to, CallbackInfoReturnable<Boolean> cir) {
      if (ItemStack.areItemsEqual(from, to)) {
         cir.setReturnValue(true);
      }
   }
}
