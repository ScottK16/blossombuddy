package org.blossomsuite.core.keybinds;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;

public final class KeybindRuntime {
   private static Consumer<MinecraftClient> settingsOpener = client -> {};
   private static Consumer<MinecraftClient> hudEditorOpener = client -> {};
   private static Runnable rentalsPauseToggler = () -> {};
   private static Runnable expiredRentalsClearer = () -> {};
   private static BiConsumer<String, Throwable> warnReporter = (message, throwable) -> {};

   private KeybindRuntime() {
   }

   public static void setSettingsOpener(Consumer<MinecraftClient> opener) {
      settingsOpener = opener != null ? opener : client -> {};
   }

   public static void setHudEditorOpener(Consumer<MinecraftClient> opener) {
      hudEditorOpener = opener != null ? opener : client -> {};
   }

   public static void setRentalsPauseToggler(Runnable toggler) {
      rentalsPauseToggler = toggler != null ? toggler : () -> {};
   }

   public static void setExpiredRentalsClearer(Runnable clearer) {
      expiredRentalsClearer = clearer != null ? clearer : () -> {};
   }

   public static void setWarnReporter(BiConsumer<String, Throwable> reporter) {
      warnReporter = reporter != null ? reporter : (message, throwable) -> {};
   }

   static void openSettings(MinecraftClient client) {
      settingsOpener.accept(client);
   }

   static void openHudEditor(MinecraftClient client) {
      hudEditorOpener.accept(client);
   }

   static void toggleRentalsPause() {
      rentalsPauseToggler.run();
   }

   static void clearExpiredRentals() {
      expiredRentalsClearer.run();
   }

   static void warn(String message, Throwable throwable) {
      warnReporter.accept(message, throwable);
   }
}
