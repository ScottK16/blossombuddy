package org.blossomsuite.core.tick;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

public final class TickRuntime {
   private static Predicate<Screen> hudEditScreenPredicate = screen -> false;
   private static Runnable settingsRequestClearer = () -> {};
   private static BooleanSupplier settingsRequestSupplier = () -> false;
   private static Consumer<MinecraftClient> settingsOpener = client -> {};
   private static BooleanSupplier hudEditOpenRequestSupplier = () -> false;
   private static Runnable hudEditOpenRequestClearer = () -> {};
   private static Runnable hudEditOpenRequestRequeuer = () -> {};
   private static Consumer<MinecraftClient> hudEditOpener = client -> {};
   private static BooleanSupplier hudEditCloseRequestSupplier = () -> false;
   private static Runnable hudEditCloseRequestClearer = () -> {};
   private static BiConsumer<String, Throwable> warnReporter = (message, throwable) -> {};

   private TickRuntime() {
   }

   public static void setHudEditScreenPredicate(Predicate<Screen> predicate) {
      hudEditScreenPredicate = predicate != null ? predicate : screen -> false;
   }

   public static void setSettingsRequestHandlers(BooleanSupplier supplier, Runnable clearer, Consumer<MinecraftClient> opener) {
      settingsRequestSupplier = supplier != null ? supplier : () -> false;
      settingsRequestClearer = clearer != null ? clearer : () -> {};
      settingsOpener = opener != null ? opener : client -> {};
   }

   public static void setHudEditRequestHandlers(
      BooleanSupplier openSupplier,
      Runnable openClearer,
      Runnable openRequeuer,
      Consumer<MinecraftClient> opener,
      BooleanSupplier closeSupplier,
      Runnable closeClearer
   ) {
      hudEditOpenRequestSupplier = openSupplier != null ? openSupplier : () -> false;
      hudEditOpenRequestClearer = openClearer != null ? openClearer : () -> {};
      hudEditOpenRequestRequeuer = openRequeuer != null ? openRequeuer : () -> {};
      hudEditOpener = opener != null ? opener : client -> {};
      hudEditCloseRequestSupplier = closeSupplier != null ? closeSupplier : () -> false;
      hudEditCloseRequestClearer = closeClearer != null ? closeClearer : () -> {};
   }

   public static void setWarnReporter(BiConsumer<String, Throwable> reporter) {
      warnReporter = reporter != null ? reporter : (message, throwable) -> {};
   }

   static boolean isHudEditScreen(Screen screen) {
      return hudEditScreenPredicate.test(screen);
   }

   static boolean consumeSettingsOpenRequest() {
      if (!settingsRequestSupplier.getAsBoolean()) {
         return false;
      }

      settingsRequestClearer.run();
      return true;
   }

   static void openSettings(MinecraftClient client) {
      settingsOpener.accept(client);
   }

   static boolean consumeHudEditOpenRequest() {
      if (!hudEditOpenRequestSupplier.getAsBoolean()) {
         return false;
      }

      hudEditOpenRequestClearer.run();
      return true;
   }

   static boolean hasHudEditOpenRequest() {
      return hudEditOpenRequestSupplier.getAsBoolean();
   }

   static void requeueHudEditOpenRequest() {
      hudEditOpenRequestRequeuer.run();
   }

   static void openHudEdit(MinecraftClient client) {
      hudEditOpener.accept(client);
   }

   static boolean consumeHudEditCloseRequest() {
      if (!hudEditCloseRequestSupplier.getAsBoolean()) {
         return false;
      }

      hudEditCloseRequestClearer.run();
      return true;
   }

   static void warn(String message, Throwable throwable) {
      warnReporter.accept(message, throwable);
   }
}
