package org.blossomsuite.core.mapart;

import com.google.gson.JsonParseException;
import java.util.Locale;
import java.util.function.Consumer;
import org.blossomsuite.core.state.SuiteScheduler;
import org.blossomsuite.core.util.JsonUtil;
import org.blossomsuite.core.util.SuiteLog;
import org.blossomsuite.core.xchat.XChatClient;

/**
 * Looks up a map art project by its short code (made on the public website, see site/mapart.html) so the mod can show
 * the picture and block list in-game with {@code /buddy mapart <code>}. Read-only: the mod never saves a design, only
 * the website does that.
 */
public final class MapArtClient {
   public static final MapArtClient INSTANCE = new MapArtClient(new XChatClient.HttpRelay());

   /** Either the project, or a message the player can be told. Never both. */
   public record Result(MapArtModels.Project project, String error) {
      static Result ok(MapArtModels.Project project) {
         return new Result(project, null);
      }

      static Result fail(String error) {
         return new Result(null, error);
      }

      public boolean ok() {
         return this.project != null;
      }
   }

   private final XChatClient.Relay relay;

   public MapArtClient(XChatClient.Relay relay) {
      this.relay = relay;
   }

   /** Runs on the mod's I/O thread; the callback is not marshalled back to the render thread, so screens must do that themselves. */
   public void fetchAsync(String code, Consumer<Result> callback) {
      SuiteScheduler.IO.execute(() -> {
         Result result;
         try {
            result = this.fetch(code);
         } catch (Throwable t) {
            SuiteLog.logger().debug("[mapart] fetch failed: {}", t.toString());
            result = Result.fail("Something went wrong looking that up.");
         }

         callback.accept(result);
      });
   }

   Result fetch(String code) {
      String normalized = normalize(code);
      if (normalized.isEmpty()) {
         return Result.fail("Type a code, like /buddy mapart 7K4P9M.");
      }

      XChatClient.Reply reply;
      try {
         reply = this.relay.post("/v1/mapart/get", JsonUtil.GSON.toJson(new MapArtModels.GetRequest(normalized)));
      } catch (XChatClient.ChatException e) {
         return Result.fail(e.getMessage());
      }

      if (reply.status() == 404) {
         return Result.fail("No map art found with that code.");
      }

      if (reply.status() != 200) {
         return Result.fail(errorOf(reply));
      }

      MapArtModels.Project project;
      try {
         project = JsonUtil.GSON.fromJson(reply.body(), MapArtModels.Project.class);
      } catch (JsonParseException e) {
         return Result.fail("The relay sent something unexpected.");
      }

      if (project == null || project.blocks == null || project.width <= 0 || project.height <= 0) {
         return Result.fail("The relay sent something unexpected.");
      }

      return Result.ok(project);
   }

   /** Codes are case-insensitive and don't mind surrounding whitespace, same as the site's own "load by code" box. */
   public static String normalize(String code) {
      return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
   }

   private static String errorOf(XChatClient.Reply reply) {
      try {
         MapArtModels.Error e = JsonUtil.GSON.fromJson(reply.body(), MapArtModels.Error.class);
         if (e != null && e.error != null && !e.error.isBlank()) {
            return e.error;
         }
      } catch (JsonParseException ignored) {
      }

      return "The relay said " + reply.status() + ".";
   }
}
