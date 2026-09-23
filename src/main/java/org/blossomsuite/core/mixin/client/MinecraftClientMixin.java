package org.blossomsuite.core.mixin.client;

import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.qol.toollock.ToolLock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
   @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
   private void suitecore$toolLockDoAttackHead(CallbackInfoReturnable<Boolean> cir) {
      MinecraftClient client = (MinecraftClient)(Object)this;
      if (client.player != null) {
         if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
            if (ToolLock.shouldBlockLeftClick(client.player)) {
               if (client.interactionManager != null) {
                  client.interactionManager.cancelBlockBreaking();
               }

               ToolLock.reportBlocked();
               cir.setReturnValue(false);
               cir.cancel();
            }
         }
      }
   }

   @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
   private void suitecore$toolLockDoItemUseHead(CallbackInfo ci) {
      MinecraftClient client = (MinecraftClient)(Object)this;
      if (client.player != null) {
         if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
            boolean sneakPressed = false;

            try {
               if (client.options != null && client.options.sneakKey != null) {
                  sneakPressed = client.options.sneakKey.isPressed();
               }
            } catch (Throwable var5) {
            }

            if (sneakPressed || client.player.isSneaking()) {
               ItemStack stack = client.player.getStackInHand(Hand.MAIN_HAND);
               if (stack != null && !stack.isEmpty()) {
                  if (!(stack.getItem() instanceof BlockItem)) {
                     if (ToolLock.shouldBlockRightClick(client.player, Hand.MAIN_HAND, stack, null)) {
                        ToolLock.reportBlocked();
                        ci.cancel();
                     }
                  }
               }
            }
         }
      }
   }
}
