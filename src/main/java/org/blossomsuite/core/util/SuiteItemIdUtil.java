package org.blossomsuite.core.util;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

public final class SuiteItemIdUtil {
   public static boolean ALLOW_RAW_COMPONENTS_EI_SCAN = true;
   private static final WeakHashMap<ItemStack, SuiteItemIdUtil.CacheEntry> BEST_ID_CACHE = new WeakHashMap<>();
   private static final Map<Class<?>, Optional<Method>> CUSTOM_DATA_NBT_METHOD_CACHE = new HashMap<>();
   private static final Pattern EI_ID_PATTERN = Pattern.compile("\"[^\"]*:ei-id\"\\s*:\\s*\"([^\"]+)\"");

   private SuiteItemIdUtil() {
   }

   public static String getBestId(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         SuiteItemIdUtil.CacheEntry ce = BEST_ID_CACHE.get(stack);
         int componentsHash = safeComponentsHash(stack);
         if (ce != null && ce.componentsHash == componentsHash) {
            return ce.bestId;
         }

         Optional<String> ei = findEiId(stack);
         String best = ei.orElseGet(() -> TextUtil.foldName(stack.getName().getString()));
         BEST_ID_CACHE.put(stack, new SuiteItemIdUtil.CacheEntry(componentsHash, best));
         return best;
      } else {
         return "<empty>";
      }
   }

   public static Optional<String> findEiId(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         Optional<String> structured = findEiIdFromCustomDataComponent(stack);
         if (structured.isPresent()) {
            return structured;
         } else {
            return ALLOW_RAW_COMPONENTS_EI_SCAN ? findEiIdFromRawComponentsString(stack) : Optional.empty();
         }
      } else {
         return Optional.empty();
      }
   }

   private static int safeComponentsHash(ItemStack stack) {
      try {
         Object comps = stack.getComponents();
         return comps != null ? comps.hashCode() : 0;
      } catch (Throwable t) {
         return 0;
      }
   }

   private static Optional<String> findEiIdFromCustomDataComponent(ItemStack stack) {
      Optional<NbtCompound> pbv = getPublicBukkitValues(stack);
      if (pbv.isEmpty()) {
         return Optional.empty();
      }

      for (String key : pbv.get().getKeys()) {
         if (key != null && key.endsWith(":ei-id")) {
            Optional<String> valOpt = pbv.get().getString(key);
            if (valOpt.isPresent()) {
               String v = valOpt.get();
               if (v != null && !v.isBlank()) {
                  return Optional.of(v);
               }
            }
         }
      }

      return Optional.empty();
   }

   private static Optional<NbtCompound> getPublicBukkitValues(ItemStack stack) {
      Object customData = stack.get(DataComponentTypes.CUSTOM_DATA);
      if (customData == null) {
         return Optional.empty();
      }

      Optional<NbtCompound> rootOpt = extractCustomDataNbt(customData);
      return rootOpt.isEmpty() ? Optional.empty() : rootOpt.get().getCompound("PublicBukkitValues");
   }

   private static Optional<NbtCompound> extractCustomDataNbt(Object customDataComponent) {
      if (customDataComponent == null) {
         return Optional.empty();
      }

      Class<?> cls = customDataComponent.getClass();
      Optional<Method> mOpt = CUSTOM_DATA_NBT_METHOD_CACHE.get(cls);
      if (mOpt == null) {
         mOpt = resolveCustomDataNbtMethod(cls);
         CUSTOM_DATA_NBT_METHOD_CACHE.put(cls, mOpt);
      }

      if (mOpt.isEmpty()) {
         return Optional.empty();
      }

      try {
         Object result = mOpt.get().invoke(customDataComponent);
         if (result instanceof NbtCompound c) {
            return Optional.of(c);
         }

         if (result instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof NbtCompound c2) {
            return Optional.of(c2);
         }
      } catch (Throwable var7) {
      }

      return Optional.empty();
   }

   private static Optional<Method> resolveCustomDataNbtMethod(Class<?> cls) {
      String[] candidates = new String[]{"copyNbt", "getNbt", "nbt", "asNbt", "getValue", "value", "toNbt", "getNbtCopy", "copy"};

      for (String name : candidates) {
         try {
            Method m = cls.getMethod(name);
            m.setAccessible(true);
            return Optional.of(m);
         } catch (Throwable var8) {
         }
      }

      for (String name : candidates) {
         try {
            Method m = cls.getDeclaredMethod(name);
            m.setAccessible(true);
            return Optional.of(m);
         } catch (Throwable var7) {
         }
      }

      return Optional.empty();
   }

   private static Optional<String> findEiIdFromRawComponentsString(ItemStack stack) {
      String raw = String.valueOf(stack.getComponents());
      Matcher m = EI_ID_PATTERN.matcher(raw);
      if (m.find()) {
         String v = m.group(1);
         if (v != null && !v.isBlank()) {
            return Optional.of(v);
         }
      }

      return Optional.empty();
   }

   private static final class CacheEntry {
      final int componentsHash;
      final String bestId;

      CacheEntry(int componentsHash, String bestId) {
         this.componentsHash = componentsHash;
         this.bestId = bestId;
      }
   }
}
