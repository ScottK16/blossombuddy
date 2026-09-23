package org.blossomsuite.core.cooldowns;

import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.services.RelayService;
import org.blossomsuite.core.services.models.RelayModels;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class CooldownRuntime {
   private static Supplier<String> realmSupplier = () -> "";
   private static Supplier<RelayService> relaySupplier = () -> null;
   private static Consumer<Text> chatReporter = text -> {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.player != null) {
         client.player.sendMessage(text, false);
      }
   };

   private CooldownRuntime() {
   }

   public static void setRealmSupplier(Supplier<String> supplier) {
      realmSupplier = supplier != null ? supplier : () -> "";
   }

   public static void setRelaySupplier(Supplier<RelayService> supplier) {
      relaySupplier = supplier != null ? supplier : () -> null;
   }

   public static void setChatReporter(Consumer<Text> reporter) {
      chatReporter = reporter != null ? reporter : text -> {};
   }

   public static String currentRealm() {
      String realm = realmSupplier.get();
      return realm == null ? "" : realm;
   }

   public static RelayModels.RelayFetchResponse relayState() {
      RelayService relay = relaySupplier.get();
      return relay == null ? null : relay.getLastState();
   }

   public static boolean publishCooldownSnapshot(List<RelayModels.RelayCooldownEntry> snapshot) {
      if (!SuiteConfig.INSTANCE.RelayConfig.publishCooldowns) {
         return false;
      }

      if (SuiteConfig.INSTANCE.RelayConfig.linkId != null && !SuiteConfig.INSTANCE.RelayConfig.linkId.isBlank()) {
         String realm = currentRealm();
         if (realm.isBlank()) {
            return false;
         } else {
            RelayService relay = relaySupplier.get();
            if (relay == null) {
               return false;
            } else {
               MinecraftClient client = MinecraftClient.getInstance();
               if (client != null && client.player != null) {
                  relay.publishSnapshotIfAllowed(true, SuiteConfig.INSTANCE.RelayConfig.linkId, realm, client.player.getName().getString(), snapshot);
                  return true;
               } else {
                  return false;
               }
            }
         }
      } else {
         return false;
      }
   }

   public static void sendChat(Text text) {
      if (text != null) {
         chatReporter.accept(text);
      }
   }
}
