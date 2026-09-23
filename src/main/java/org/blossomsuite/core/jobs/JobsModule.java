package org.blossomsuite.core.jobs;

import org.blossomsuite.core.config.SuiteConfig;
import net.minecraft.client.MinecraftClient;

public final class JobsModule {
   private static JobsTracker tracker;

   private JobsModule() {
   }

   public static void init() {
      tracker = new JobsTracker();
   }

   public static JobsTracker tracker() {
      return tracker;
   }

   public static void tick(MinecraftClient client) {
   }

   public static void addLifeTime(double amt) {
      if (JobsChattextSetup.realmName != null && !JobsChattextSetup.realmName.equals("")) {
         SuiteConfig.INSTANCE.JobsConfig.addToLifetimeForServer(JobsChattextSetup.realmName, amt);
      }
   }
}
