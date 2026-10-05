package org.blossomsuite.core.mixin.client;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemInHandRenderer.class)
public class HeldItemRendererMixin {
   @Inject(method = "shouldInstantlyReplaceVisibleItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
   private void suitecore$skipReequip(ItemStack from, ItemStack to, CallbackInfoReturnable<Boolean> cir) {
      if (ItemStack.isSameItem(from, to)) {
         cir.setReturnValue(true);
      }
   }
}
