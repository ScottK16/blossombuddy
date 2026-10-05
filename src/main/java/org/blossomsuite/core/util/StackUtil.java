package org.blossomsuite.core.util;

import io.netty.buffer.Unpooled;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.HolderLookup.Provider;
public final class StackUtil {
   private StackUtil() {
   }

   /**
    * A one-item stack for drawing an icon, or {@link ItemStack#EMPTY} when the game can't build one yet. Since 26.1 an item's
    * components are only bound once registries are loaded from a world, so on the title screen this would throw.
    */
   public static ItemStack safeStack(net.minecraft.world.level.ItemLike item) {
      try {
         return item == null ? ItemStack.EMPTY : new ItemStack(item);
      } catch (RuntimeException notBoundYet) {
         return ItemStack.EMPTY;
      }
   }

   public static byte[] serializeStack(ItemStack stack, Provider lookup) {
      Tag element = (Tag)ItemStack.CODEC.encodeStart(lookup.createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
      if (element instanceof CompoundTag compound) {
         FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
         buf.writeNbt(compound);
         byte[] data = new byte[buf.readableBytes()];
         buf.getBytes(0, data);
         return data;
      } else {
         throw new IllegalStateException("ItemStack did not encode to NbtCompound");
      }
   }

   public static ItemStack deserializeStack(byte[] data, Provider lookup) {
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
      CompoundTag compound = buf.readNbt();
      return compound == null ? ItemStack.EMPTY : (ItemStack)ItemStack.CODEC.parse(lookup.createSerializationContext(NbtOps.INSTANCE), compound).getOrThrow();
   }
}
