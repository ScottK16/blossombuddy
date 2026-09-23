package org.blossomsuite.core.remote;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.RemoteConfig;
import org.blossomsuite.core.config.SuiteConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class RemoteConfigClient {
   private static final Gson GSON = new Gson();
   private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(Redirect.NORMAL).build();
   private static final AtomicReference<CompletableFuture<RemoteConfigClient.DownloadResult>> IN_FLIGHT = new AtomicReference<>(null);
   private static Consumer<String> chatReporter = message -> {};
   private static Consumer<String> infoReporter = message -> {};
   private static BiConsumer<String, Throwable> warnReporter = (message, throwable) -> {};

   private RemoteConfigClient() {
   }

   public static void setChatReporter(Consumer<String> reporter) {
      chatReporter = reporter != null ? reporter : message -> {};
   }

   public static void setInfoReporter(Consumer<String> reporter) {
      infoReporter = reporter != null ? reporter : message -> {};
   }

   public static void setWarnReporter(BiConsumer<String, Throwable> reporter) {
      warnReporter = reporter != null ? reporter : (message, throwable) -> {};
   }

   private static Path cooldownsLocalPath() {
      return FabricLoader.getInstance().getConfigDir().resolve(SuiteRuntime.profile().modId()).resolve("cooldowns.json");
   }

   private static String joinUrl(String base, String path) {
      if (base.endsWith("/")) {
         base = base.substring(0, base.length() - 1);
      }

      if (!path.startsWith("/")) {
         path = "/" + path;
      }

      return base + path;
   }

   public static void refreshNow(MinecraftClient client) {
      SuiteConfig cfg = SuiteConfig.INSTANCE;
      if (!SuiteRuntime.profile().hasBackend()) {
         infoReporter.accept("[RemoteConfig] no update server configured; skipping");
      } else if (!cfg.RemoteConfig.enabled) {
         chatReporter.accept("Remote updates are disabled.");
      } else {
         CompletableFuture<RemoteConfigClient.DownloadResult> gate = new CompletableFuture<>();
         if (IN_FLIGHT.compareAndSet(null, gate)) {
            String manifestUrl = joinUrl(RemoteConfig.baseUrl(), "/v1/manifest");
            CompletableFuture.<RemoteManifest>supplyAsync(() -> fetchManifest(manifestUrl)).thenCompose(manifest -> {
               if (manifest != null && manifest.cooldowns != null && manifest.cooldowns.sha256 != null && manifest.cooldowns.url != null) {
                  String remoteSha = safe(manifest.cooldowns.sha256);
                  String version = safe(manifest.cooldowns.version);
                  String haveSha = safe(cfg.RemoteConfig.cooldownsSha256);
                  infoReporter.accept("[RemoteConfig] SENT: '" + remoteSha + "'");
                  infoReporter.accept("[RemoteConfig] HAVE: '" + haveSha + "'");
                  boolean same = !haveSha.isEmpty() && haveSha.equalsIgnoreCase(remoteSha);
                  warnReporter.accept("[RemoteConfig] compare same=" + same + " haveLen=" + haveSha.length() + " sentLen=" + remoteSha.length(), null);
                  if (!haveSha.isEmpty() && haveSha.equalsIgnoreCase(remoteSha)) {
                     return CompletableFuture.completedFuture(new RemoteConfigClient.DownloadResult(true, "Already up to date", remoteSha, version));
                  }

                  String fileUrl = manifest.cooldowns.url.startsWith("http") ? manifest.cooldowns.url : joinUrl(RemoteConfig.baseUrl(), manifest.cooldowns.url);
                  return CompletableFuture.supplyAsync(() -> downloadJson(fileUrl, remoteSha));
               } else {
                  return CompletableFuture.failedFuture(new RuntimeException("Bad manifest"));
               }
            }).whenComplete((res, ex) -> {
               IN_FLIGHT.set(null);
               if (ex != null) {
                  gate.completeExceptionally(ex);
               } else {
                  gate.complete(res);
               }
            });
            gate.thenAccept(result -> client.execute(() -> applyResult(client, result))).exceptionally(ex -> {
               warnReporter.accept("[RemoteConfig] refresh failed", ex);
               client.execute(() -> {
                  if (client.player != null) {
                     chatReporter.accept("Remote refresh failed (see log).");
                  }
               });
               return null;
            });
         }
      }
   }

   public static void checkIfUpdateAvailable(MinecraftClient client, long nowMs) {
      SuiteConfig cfg = SuiteConfig.INSTANCE;
      if (cfg.RemoteConfig.enabled) {
         if (nowMs - cfg.RemoteConfig.lastCheckMs >= 1800000L) {
            cfg.RemoteConfig.lastCheckMs = nowMs;
            cfg.markDirty();
            ConfigIO.saveIfDirty();
            refreshNow(client);
         }
      }
   }

   private static RemoteManifest fetchManifest(String url) {
      try {
         HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", SuiteRuntime.profile().displayName()).GET().build();
         HttpResponse<String> resp = HTTP.send(req, BodyHandlers.ofString());
         if (resp.statusCode() != 200) {
            warnReporter.accept("[RemoteConfig] manifest status " + resp.statusCode(), null);
            return null;
         } else {
            return (RemoteManifest)GSON.fromJson(resp.body(), RemoteManifest.class);
         }
      } catch (Exception e) {
         warnReporter.accept("[RemoteConfig] manifest fetch error", e);
         return null;
      }
   }

   private static RemoteConfigClient.DownloadResult downloadJson(String url, String sha256) {
      try {
         warnReporter.accept("[RemoteConfig] >>> ENTER downloadJson url=" + url + " sha=" + sha256, null);
         HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", SuiteRuntime.profile().displayName()).GET().build();
         HttpResponse<byte[]> resp = HTTP.send(req, BodyHandlers.ofByteArray());
         if (resp.statusCode() != 200) {
            return RemoteConfigClient.DownloadResult.fail("Download failed: HTTP " + resp.statusCode());
         }

         byte[] bytes = resp.body();
         String json = new String(bytes, StandardCharsets.UTF_8);
         GSON.fromJson(json, Object.class);
         JsonObject root = JsonParser.parseString(json).getAsJsonObject();
         String version = root.get("version").getAsString();
         Path file = cooldownsLocalPath();
         Files.createDirectories(file.getParent());
         Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
         SuiteConfig.INSTANCE.RemoteConfig.setCooldownsSha256(sha256);
         SuiteConfig.INSTANCE.RemoteConfig.setCooldownVersion(version);
         ConfigIO.saveIfDirty();
         return RemoteConfigClient.DownloadResult.ok("Downloaded", sha256, version);
      } catch (IOException e) {
         return RemoteConfigClient.DownloadResult.fail("IO error: " + e.getMessage());
      } catch (Exception e) {
         return RemoteConfigClient.DownloadResult.fail("Error: " + e.getMessage());
      }
   }

   private static void applyResult(MinecraftClient client, RemoteConfigClient.DownloadResult result) {
      SuiteConfig cfg = SuiteConfig.INSTANCE;
      if (!result.success) {
         if (client.player != null) {
            client.player.sendMessage(Text.literal(SuiteRuntime.profile().displayName() + ": " + result.message), false);
         }
      } else if (!"Already up to date".equals(result.message)) {
         chatReporter.accept("Cooldowns configuration has been updated to version v" + SuiteConfig.INSTANCE.RemoteConfig.cooldownVersion);
      }
   }

   public static String safe(String s) {
      return s == null ? "" : s.trim();
   }

   private record DownloadResult(boolean success, String message, String remoteSha256, String version) {
      static RemoteConfigClient.DownloadResult ok(String msg, String sha, String version) {
         return new RemoteConfigClient.DownloadResult(true, msg, sha, version);
      }

      static RemoteConfigClient.DownloadResult fail(String msg) {
         return new RemoteConfigClient.DownloadResult(false, msg, "", "0");
      }
   }
}
