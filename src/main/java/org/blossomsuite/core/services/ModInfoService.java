package org.blossomsuite.core.services;

import org.blossomsuite.core.net.EtagCache;
import org.blossomsuite.core.net.SuiteHttp;
import org.blossomsuite.core.services.models.ModInfo;
import org.blossomsuite.core.util.JsonUtil;
import java.util.function.Consumer;

public final class ModInfoService {
   private final SuiteHttp http;
   private final EtagCache etags;
   private final String currentVersion;
   private final Consumer<ModInfoService.UpdateNotice> updateNoticeSink;

   public ModInfoService(SuiteHttp http, EtagCache etags, String currentVersion, Consumer<ModInfoService.UpdateNotice> updateNoticeSink) {
      this.http = http;
      this.etags = etags;
      this.currentVersion = currentVersion;
      this.updateNoticeSink = updateNoticeSink == null ? notice -> {} : updateNoticeSink;
   }

   public void pollOnce() {
      String key = "mod_info";
      String inm = this.etags.get(key);
      this.http.get("/v1/mod/info", inm).thenAccept(resp -> {
         int code = resp.statusCode();
         if (code != 304) {
            if (code == 200) {
               String etag = resp.headers().firstValue("etag").orElse(null);
               this.etags.set(key, etag);
               ModInfo info = (ModInfo)JsonUtil.GSON.fromJson(resp.body(), ModInfo.class);
               if (info != null && info.latestVersion != null && info.requiredVersion != null) {
                  String current = this.currentVersion;
                  String required = info.requiredVersion;
                  String latest = info.latestVersion;
                  boolean belowRequired = isNewer(required, current);
                  boolean belowLatest = isNewer(latest, current);
                  if (belowRequired) {
                     this.updateNoticeSink.accept(ModInfoService.UpdateNotice.required(current, required, latest));
                  } else if (belowLatest) {
                     this.updateNoticeSink.accept(ModInfoService.UpdateNotice.optional(current, latest));
                  }
               }
            }
         }
      }).exceptionally(ex -> null);
   }

   private static boolean isNewer(String latest, String current) {
      int[] a = parseVer(latest);
      int[] b = parseVer(current);

      for (int i = 0; i < 3; i++) {
         if (a[i] > b[i]) {
            return true;
         }

         if (a[i] < b[i]) {
            return false;
         }
      }

      return false;
   }

   private static int[] parseVer(String s) {
      int[] out = new int[]{0, 0, 0};
      if (s == null) {
         return out;
      }

      String[] parts = s.split("\\.");

      for (int i = 0; i < Math.min(3, parts.length); i++) {
         try {
            out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
         } catch (Exception var5) {
         }
      }

      return out;
   }

   public enum Type {
      REQUIRED,
      OPTIONAL;
   }

   public record UpdateNotice(ModInfoService.Type type, String currentVersion, String requiredVersion, String latestVersion) {
      public static ModInfoService.UpdateNotice required(String currentVersion, String requiredVersion, String latestVersion) {
         return new ModInfoService.UpdateNotice(ModInfoService.Type.REQUIRED, currentVersion, requiredVersion, latestVersion);
      }

      public static ModInfoService.UpdateNotice optional(String currentVersion, String latestVersion) {
         return new ModInfoService.UpdateNotice(ModInfoService.Type.OPTIONAL, currentVersion, null, latestVersion);
      }
   }
}
