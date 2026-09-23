package org.blossomsuite.core.qol.fishing;

import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

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
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.player != null && client.world != null) {
         FishingAlertController.Settings settings = settings();
         if (!settings.enabled()) {
            return false;
         }

         double r = 3.0;
         Box search = new Box(soundX - 3.0, soundY - 3.0, soundZ - 3.0, soundX + 3.0, soundY + 3.0, soundZ + 3.0);
         FishingBobberEntity nearest = null;
         double nearestDistSq = Double.POSITIVE_INFINITY;

         for (FishingBobberEntity bobber : client.world.getEntitiesByClass(FishingBobberEntity.class, search, b -> true)) {
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
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.player != null && client.world != null) {
         FishingAlertController.Settings settings = settings();
         if (settings.enabled()) {
            Identifier id;
            try {
               id = Identifier.of(settings.soundId());
            } catch (Exception ignored) {
               playFallback(client, settings);
               return;
            }

            SoundEvent sound = Registries.SOUND_EVENT.get(id);
            if (sound != null && sound != SoundEvents.INTENTIONALLY_EMPTY) {
               client.world
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

   private static void playFallback(MinecraftClient client, FishingAlertController.Settings settings) {
      if (client != null && client.player != null && client.world != null) {
         client.world
            .playSound(
               client.player,
               client.player.getX(),
               client.player.getY(),
               client.player.getZ(),
               SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
               settings.soundCategory(),
               settings.volume(),
               settings.pitch()
            );
      }
   }

   public record Settings(boolean enabled, String soundId, float volume, float pitch, int cooldownMs, SoundCategory soundCategory) {
      public Settings {
         soundId = soundId != null && !soundId.isBlank() ? soundId : "minecraft:entity.experience_orb.pickup";
         volume = Math.max(0.0F, volume);
         pitch = Math.max(0.0F, pitch);
         cooldownMs = Math.max(0, cooldownMs);
         soundCategory = soundCategory == null ? SoundCategory.MASTER : soundCategory;
      }

      public static FishingAlertController.Settings disabled() {
         return new FishingAlertController.Settings(false, "minecraft:entity.experience_orb.pickup", 1.0F, 1.0F, 250, SoundCategory.MASTER);
      }
   }
}
