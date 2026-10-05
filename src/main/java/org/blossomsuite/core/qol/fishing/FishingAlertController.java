package org.blossomsuite.core.qol.fishing;

import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
public final class FishingAlertController {
   private static long lastAlertAt = 0L;
   private static Supplier<FishingAlertController.Settings> settingsSupplier = () -> FishingAlertController.Settings.disabled();

   private FishingAlertController() {
   }

   public static void configure(Supplier<FishingAlertController.Settings> settingsSupplier) {
      FishingAlertController.settingsSupplier = settingsSupplier == null ? () -> FishingAlertController.Settings.disabled() : settingsSupplier;
   }

   public static boolean isEnabled() {
      return settings().enabled();
   }

   public static boolean shouldReplaceVanillaSplash(double soundX, double soundY, double soundZ) {
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.player != null && client.level != null) {
         FishingAlertController.Settings settings = settings();
         if (!settings.enabled()) {
            return false;
         }

         double r = 3.0;
         AABB search = new AABB(soundX - 3.0, soundY - 3.0, soundZ - 3.0, soundX + 3.0, soundY + 3.0, soundZ + 3.0);
         FishingHook nearest = null;
         double nearestDistSq = Double.POSITIVE_INFINITY;

         for (FishingHook bobber : client.level.getEntitiesOfClass(FishingHook.class, search, b -> true)) {
            double dx = bobber.getX() - soundX;
            double dy = bobber.getY() - soundY;
            double dz = bobber.getZ() - soundZ;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < nearestDistSq) {
               nearestDistSq = distSq;
               nearest = bobber;
            }
         }

         if (nearest == null) {
            return false;
         } else {
            return nearestDistSq > 4.0 ? false : nearest.getOwner() == client.player;
         }
      } else {
         return false;
      }
   }

   public static boolean tryHandleVanillaFishingSplash(double soundX, double soundY, double soundZ) {
      if (!shouldReplaceVanillaSplash(soundX, soundY, soundZ)) {
         return false;
      }

      long now = System.currentTimeMillis();
      int cooldownMs = settings().cooldownMs();
      if (now - lastAlertAt < cooldownMs) {
         return true;
      }

      lastAlertAt = now;
      playConfiguredAlert();
      return true;
   }

   public static void playPreview() {
      playConfiguredAlert();
   }

   public static void playConfiguredAlert() {
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.player != null && client.level != null) {
         FishingAlertController.Settings settings = settings();
         if (settings.enabled()) {
            Identifier id;
            try {
               id = Identifier.parse(settings.soundId());
            } catch (Exception ignored) {
               playFallback(client, settings);
               return;
            }

            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(id);
            if (sound != null && sound != SoundEvents.EMPTY) {
               client.level
                  .playSound(
                     client.player,
                     client.player.getX(),
                     client.player.getY(),
                     client.player.getZ(),
                     sound,
                     settings.soundCategory(),
                     settings.volume(),
                     settings.pitch()
                  );
            } else {
               playFallback(client, settings);
            }
         }
      }
   }

   private static FishingAlertController.Settings settings() {
      FishingAlertController.Settings settings = settingsSupplier.get();
      return settings == null ? FishingAlertController.Settings.disabled() : settings;
   }

   private static void playFallback(Minecraft client, FishingAlertController.Settings settings) {
      if (client != null && client.player != null && client.level != null) {
         client.level
            .playSound(
               client.player,
               client.player.getX(),
               client.player.getY(),
               client.player.getZ(),
               SoundEvents.EXPERIENCE_ORB_PICKUP,
               settings.soundCategory(),
               settings.volume(),
               settings.pitch()
            );
      }
   }

   public record Settings(boolean enabled, String soundId, float volume, float pitch, int cooldownMs, SoundSource soundCategory) {
      public Settings {
         soundId = soundId != null && !soundId.isBlank() ? soundId : "minecraft:entity.experience_orb.pickup";
         volume = Math.max(0.0F, volume);
         pitch = Math.max(0.0F, pitch);
         cooldownMs = Math.max(0, cooldownMs);
         soundCategory = soundCategory == null ? SoundSource.MASTER : soundCategory;
      }

      public static FishingAlertController.Settings disabled() {
         return new FishingAlertController.Settings(false, "minecraft:entity.experience_orb.pickup", 1.0F, 1.0F, 250, SoundSource.MASTER);
      }
   }
}
