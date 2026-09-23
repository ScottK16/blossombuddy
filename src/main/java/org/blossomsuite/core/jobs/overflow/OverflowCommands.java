package org.blossomsuite.core.jobs.overflow;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.config.SuiteConfig;

/** {@code /jobsoverflow}: same command name and subcommands as the standalone Jobs Overflow XP mod. */
public final class OverflowCommands {
   private OverflowCommands() {
   }

   public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
      dispatcher.register(
         ClientCommandManager.literal("jobsoverflow")
            .executes(ctx -> {
               showCurrent();
               return 1;
            })
            .then(ClientCommandManager.literal("all").executes(ctx -> {
               showAll();
               return 1;
            }))
            .then(
               ClientCommandManager.literal("display")
                  .then(ClientCommandManager.literal("xp").executes(ctx -> setDisplay(OverflowDisplayMode.XP, "raw overflow XP")))
                  .then(ClientCommandManager.literal("levels").executes(ctx -> setDisplay(OverflowDisplayMode.LEVELS, "cosmetic levels")))
                  .then(ClientCommandManager.literal("off").executes(ctx -> setDisplay(OverflowDisplayMode.OFF, "off (still tracking)")))
            )
            .then(ClientCommandManager.literal("reset").executes(ctx -> {
               OverflowTracker.INSTANCE.resetCurrentRealm();
               ChatOutput.info("Reset all overflow XP for '" + OverflowTracker.INSTANCE.realmLabel() + "'.");
               return 1;
            }))
            .then(ClientCommandManager.literal("resetall").executes(ctx -> {
               OverflowTracker.INSTANCE.resetAll();
               ChatOutput.info("Reset all overflow XP for every realm.");
               return 1;
            }))
            .then(
               ClientCommandManager.literal("resetjob")
                  .then(ClientCommandManager.argument("job", StringArgumentType.greedyString()).executes(ctx -> {
                     String job = StringArgumentType.getString(ctx, "job");
                     OverflowTracker.INSTANCE.resetJob(job);
                     ChatOutput.info("Reset " + job + " on '" + OverflowTracker.INSTANCE.realmLabel() + "'.");
                     return 1;
                  }))
            )
      );
   }

   private static int setDisplay(OverflowDisplayMode mode, String label) {
      SuiteConfig.INSTANCE.JobsConfig.overflowDisplay = mode;
      SuiteConfig.INSTANCE.markDirty();
      ChatOutput.info("Overflow display: " + label + ".");
      return 1;
   }

   private static void showCurrent() {
      String realm = OverflowTracker.INSTANCE.realmLabel();
      Map<String, Double> jobs = OverflowTracker.INSTANCE.snapshotCurrentRealm();
      if (jobs.isEmpty()) {
         ChatOutput.info("No overflow XP tracked yet on '" + realm + "'.");
         return;
      }

      ChatOutput.info("Overflow XP on '" + realm + "':");
      jobs.forEach((job, xp) -> ChatOutput.info("  " + pretty(job) + ": " + OverflowTracker.formatNumber(xp) + " XP"));
   }

   private static void showAll() {
      Map<String, Map<String, Double>> all = OverflowTracker.INSTANCE.snapshotAll();
      if (all.isEmpty()) {
         ChatOutput.info("No overflow XP tracked yet.");
         return;
      }

      all.forEach((realm, jobs) -> {
         ChatOutput.info("Realm '" + realm + "':");
         jobs.forEach((job, xp) -> ChatOutput.info("  " + pretty(job) + ": " + OverflowTracker.formatNumber(xp) + " XP"));
      });
   }

   private static String pretty(String job) {
      return job.isEmpty() ? job : job.substring(0, 1).toUpperCase(Locale.ROOT) + job.substring(1);
   }
}
