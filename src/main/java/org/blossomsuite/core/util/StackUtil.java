package org.blossomsuite.core.util;

import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryWrapper.WrapperLookup;

public final class StackUtil {
   private StackUtil() {
   }

   public static byte[] serializeStack(ItemStack stack, WrapperLookup lookup) {
      NbtElement element = (NbtElement)ItemStack.CODEC.encodeStart(lookup.getOps(NbtOps.INSTANCE), stack).getOrThrow();
      if (element instanceof NbtCompound compound) {
         PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
         buf.writeNbt(compound);
         byte[] data = new byte[buf.readableBytes()];
         buf.getBytes(0, data);
         return data;
      } else {
         throw new IllegalStateException("ItemStack did not encode to NbtCompound");
      }
   }

   public static ItemStack deserializeStack(byte[] data, WrapperLookup lookup) {
      PacketByteBuf buf = new PacketByteBuf(Unpooled.wrappedBuffer(data));
      NbtCompound compound = buf.readNbt();
      return compound == null ? ItemStack.EMPTY : (ItemStack)ItemStack.CODEC.parse(lookup.getOps(NbtOps.INSTANCE), compound).getOrThrow();
   }
}
