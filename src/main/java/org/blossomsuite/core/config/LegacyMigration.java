package org.blossomsuite.core.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import org.blossomsuite.core.SuiteProfile;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.util.SuiteLog;

/**
 * One-time carry-over of settings and data from the earlier names of this mod
 * ({@code blossomsuite} and the {@code blossomsuite2} dev builds). Existing files are never overwritten
 * and the old ones are left in place.
 */
public final class LegacyMigration {
   private static final String[] OLD_IDS = {"blossomsuite2", "blossomsuite"};

   private LegacyMigration() {
   }

   public static void run() {
      try {
         Path configDir = FabricLoader.getInstance().getConfigDir();
         SuiteProfile profile = SuiteRuntime.profile();

         Path newSettings = configDir.resolve(profile.configFileName());
         if (!Files.exists(newSettings)) {
            for (String old : OLD_IDS) {
               Path oldSettings = configDir.resolve(old + ".json");
               if (!oldSettings.equals(newSettings) && Files.isRegularFile(oldSettings)) {
                  Files.copy(oldSettings, newSettings);
                  SuiteLog.logger().info("[migrate] copied settings {} -> {}", oldSettings.getFileName(), newSettings.getFileName());
                  break;
               }
            }
         }

         Path newDir = configDir.resolve(profile.modId());
         Files.createDirectories(newDir);
         for (String old : OLD_IDS) {
            Path oldDir = configDir.resolve(old);
            if (!oldDir.equals(newDir) && Files.isDirectory(oldDir)) {
               copyMissingFiles(oldDir, newDir);
            }
         }
      } catch (Exception e) {
         SuiteLog.logger().warn("[migrate] could not carry over old settings: {}", e.toString());
      }
   }

   private static void copyMissingFiles(Path from, Path to) throws IOException {
      try (Stream<Path> files = Files.list(from)) {
         for (Path file : (Iterable<Path>)files::iterator) {
            Path target = to.resolve(file.getFileName().toString());
            if (Files.isRegularFile(file) && !Files.exists(target)) {
               Files.copy(file, target);
               SuiteLog.logger().info("[migrate] copied {}/{}", from.getFileName(), file.getFileName());
            }
         }
      }
   }
}
