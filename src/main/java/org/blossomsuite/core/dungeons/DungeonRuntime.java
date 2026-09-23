package org.blossomsuite.core.dungeons;

import org.blossomsuite.core.config.DungeonConfig;
import org.blossomsuite.core.services.DungeonReportService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class DungeonRuntime {
   private static Supplier<String> serverSupplier = () -> "";
   private static Supplier<Boolean> activeWorldSupplier = () -> true;
   private static Supplier<DungeonReportService> reportServiceSupplier = () -> null;
   private static Consumer<Text> chatReporter = text -> {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.player != null) {
         client.player.sendMessage(text, false);
      }
   };

   private DungeonRuntime() {
   }

   public static void setServerSupplier(Supplier<String> supplier) {
      serverSupplier = supplier != null ? supplier : () -> "";
   }

   public static void setActiveWorldSupplier(Supplier<Boolean> supplier) {
      activeWorldSupplier = supplier != null ? supplier : () -> true;
   }

   public static void setReportServiceSupplier(Supplier<DungeonReportService> supplier) {
      reportServiceSupplier = supplier != null ? supplier : () -> null;
   }

   public static void setChatReporter(Consumer<Text> reporter) {
      chatReporter = reporter != null ? reporter : text -> {};
   }

   public static boolean isActiveWorld() {
      Boolean active = activeWorldSupplier.get();
      return active == null || active;
   }

   public static String currentServer() {
      String server = serverSupplier.get();
      return DungeonConfig.serverKey(server);
   }

   public static DungeonReportService reportService() {
      return reportServiceSupplier.get();
   }

   public static void sendChat(Text text) {
      if (text != null) {
         chatReporter.accept(text);
      }
   }
}
