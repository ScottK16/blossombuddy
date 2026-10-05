package org.blossomsuite.core.mixin.client;

import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.qol.toollock.ToolLock;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {
   @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
   private void suitecore$toolLockDoAttackHead(CallbackInfoReturnable<Boolean> cir) {
      Minecraft client = (Minecraft)(Object)this;
      if (client.player != null) {
         if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
            if (ToolLock.shouldBlockLeftClick(client.player)) {
               if (client.gameMode != null) {
                  client.gameMode.stopDestroyBlock();
               }

               ToolLock.reportBlocked();
               cir.setReturnValue(false);
               cir.cancel();
            }
         }
      }
   }

   @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
   private void suitecore$toolLockDoItemUseHead(CallbackInfo ci) {
      Minecraft client = (Minecraft)(Object)this;
      if (client.player != null) {
         if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
            boolean sneakPressed = false;

            try {
               if (client.options != null && client.options.keyShift != null) {
                  sneakPressed = client.options.keyShift.isDown();
               }
            } catch (Throwable var5) {
            }

            if (sneakPressed || client.player.isShiftKeyDown()) {
               ItemStack stack = client.player.getItemInHand(InteractionHand.MAIN_HAND);
               if (stack != null && !stack.isEmpty()) {
                  if (!(stack.getItem() instanceof BlockItem)) {
                     if (ToolLock.shouldBlockRightClick(client.player, InteractionHand.MAIN_HAND, stack, null)) {
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
