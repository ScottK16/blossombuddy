package org.blossomsuite.core.net;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class SuiteHttp {
   private final HttpClient client;
   private volatile String baseUrl;
   private static final String VOTE_PREFIX = "/v1/vote/";
   private static final String CHAT_PREFIX = "/v1/chat/";
   private static final String STATS_PREFIX = "/v1/stats/";
   private static final String PRESENCE_PREFIX = "/v1/presence/";
   private static final String EMOTE_PREFIX = "/v1/emote/";
   private static final String MAPART_PREFIX = "/v1/mapart/";
   private final String ingestKey;
   private final String ingestHeaderName;

   public SuiteHttp(String baseUrl, String ingestKey, String ingestHeaderName) {
      this.baseUrl = stripTrailingSlash(baseUrl == null ? "" : baseUrl.trim());
      this.ingestKey = ingestKey == null ? "" : ingestKey;
      this.ingestHeaderName = ingestHeaderName == null ? "" : ingestHeaderName;
      this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).version(Version.HTTP_1_1).build();
   }

   /** False when no backend URL is configured; every request then fails locally without touching the network. */
   public boolean enabled() {
      return !this.baseUrl.isEmpty();
   }

   /** Points the client at a relay (an empty string switches it off). Takes effect on the next request. */
   public void setBaseUrl(String url) {
      this.baseUrl = stripTrailingSlash(url == null ? "" : url.trim());
   }

   /**
    * The only requests this mod will ever send to a relay: vote-party sync, cross-realm chat, the usage counts, the player list, emotes,
    * and looking up a map art project by its code (the mod only ever reads one by code; saving a design happens on the website, never
    * from the mod). Everything else the original mod talked to its server about (donations, dungeon runs, cooldown sharing, ...) is
    * refused here, so it cannot leak to a relay even by mistake.
    */
   public static boolean isAllowedPath(String path) {
      return path != null && (path.startsWith(VOTE_PREFIX) || path.startsWith(CHAT_PREFIX) || path.startsWith(STATS_PREFIX) || path.startsWith(PRESENCE_PREFIX) || path.startsWith(EMOTE_PREFIX) || path.startsWith(MAPART_PREFIX));
   }

   private static CompletableFuture<HttpResponse<String>> disabled() {
      return CompletableFuture.failedFuture(new IllegalStateException("No backend configured"));
   }

   public CompletableFuture<HttpResponse<String>> get(String path, String ifNoneMatchEtag) {
      if (!this.enabled() || !isAllowedPath(path)) {
         return disabled();
      }

      Builder b = HttpRequest.newBuilder().uri(URI.create(this.baseUrl + path)).timeout(Duration.ofSeconds(8L)).GET().header("Accept", "application/json");
      this.addAuthHeader(b);
      if (ifNoneMatchEtag != null && !ifNoneMatchEtag.isEmpty()) {
         b.header("If-None-Match", ifNoneMatchEtag);
      }

      return this.client.sendAsync(b.build(), BodyHandlers.ofString());
   }

   public CompletableFuture<HttpResponse<String>> postJson(String path, String jsonBody) {
      if (!this.enabled() || !isAllowedPath(path)) {
         return disabled();
      }

      Builder b = HttpRequest.newBuilder()
         .uri(URI.create(this.baseUrl + path))
         .timeout(Duration.ofSeconds(8L))
         .POST(BodyPublishers.ofString(jsonBody))
         .header("Content-Type", "application/json")
         .header("Accept", "application/json");
      this.addAuthHeader(b);
      return this.client.sendAsync(b.build(), BodyHandlers.ofString());
   }

   private void addAuthHeader(Builder b) {
      if (!this.ingestKey.isEmpty() && !this.ingestHeaderName.isEmpty()) {
         b.header(this.ingestHeaderName, this.ingestKey);
      }
   }

   private static String stripTrailingSlash(String s) {
      if (s == null) {
         return "";
      } else {
         return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
      }
   }
}
