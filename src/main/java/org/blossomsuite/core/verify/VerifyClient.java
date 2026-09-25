package org.blossomsuite.core.verify;

import com.google.gson.JsonParseException;
import java.util.Map;
import java.util.function.Consumer;
import org.blossomsuite.core.state.SuiteScheduler;
import org.blossomsuite.core.util.JsonUtil;
import org.blossomsuite.core.util.SuiteLog;
import org.blossomsuite.core.xchat.XChatClient;
import org.blossomsuite.core.xchat.XChatModels;

/**
 * Links this player's Discord account to their Minecraft account ({@code /buddy verify}): the relay proves who they
 * are with Mojang's login check (the same one cross-realm chat uses, so the access token only ever goes to Mojang),
 * then hands back a one-time code to give the BlossomBuddy Discord bot. {@code /buddy verify remove} undoes it.
 */
public final class VerifyClient {
   public static final VerifyClient INSTANCE = new VerifyClient(new XChatClient.HttpRelay(), new XChatClient.MojangJoiner());

   /** What to tell the player. */
   public record Result(boolean ok, String message) {
   }

   static final class CodeReply {
      String code;
      Long expiresInSeconds;
   }

   static final class RemoveReply {
      boolean removed;
   }

   private final XChatClient.Relay relay;
   private final XChatClient.Joiner joiner;

   public VerifyClient(XChatClient.Relay relay, XChatClient.Joiner joiner) {
      this.relay = relay;
      this.joiner = joiner;
   }

   /** Runs on the mod's I/O thread; the callback is not marshalled back to the render thread. */
   public void startAsync(Consumer<Result> callback) {
      SuiteScheduler.IO.execute(() -> callback.accept(this.safely(this::start)));
   }

   public void removeAsync(Consumer<Result> callback) {
      SuiteScheduler.IO.execute(() -> callback.accept(this.safely(this::remove)));
   }

   private Result safely(java.util.concurrent.Callable<Result> work) {
      try {
         return work.call();
      } catch (XChatClient.ChatException e) {
         return new Result(false, e.getMessage());
      } catch (Throwable t) {
         SuiteLog.logger().debug("[verify] failed: {}", t.toString());
         return new Result(false, "Something went wrong.");
      }
   }

   Result start() throws XChatClient.ChatException {
      XChatClient.Reply reply = this.signedIn("/v1/verify/start");
      String failure = failureOf(reply);
      if (failure != null) {
         return new Result(false, failure);
      }

      CodeReply parsed = parse(reply.body(), CodeReply.class);
      if (parsed == null || parsed.code == null || parsed.code.isBlank()) {
         return new Result(false, "The relay sent something unexpected.");
      }

      long minutes = Math.max(1L, (parsed.expiresInSeconds == null ? 600L : parsed.expiresInSeconds) / 60L);
      return new Result(true, "Your code is " + parsed.code + " (good for " + minutes + " minutes). In the BlossomBuddy Discord, open the verify channel, click Verify and enter it. Linking saves your Discord account id next to your Minecraft name, so your Discord messages in the chat show as you. /buddy verify remove deletes it.");
   }

   Result remove() throws XChatClient.ChatException {
      XChatClient.Reply reply = this.signedIn("/v1/verify/remove");
      String failure = failureOf(reply);
      if (failure != null) {
         return new Result(false, failure);
      }

      RemoveReply parsed = parse(reply.body(), RemoveReply.class);
      return new Result(true, parsed != null && parsed.removed ? "Your Discord link is removed." : "You didn't have a Discord link.");
   }

   /** The relay's sign-in dance, then the request itself. */
   private XChatClient.Reply signedIn(String path) throws XChatClient.ChatException {
      XChatClient.Reply challengeReply = this.relay.post("/v1/chat/challenge", "{}");
      XChatModels.Challenge challenge = challengeReply.status() == 200 ? parse(challengeReply.body(), XChatModels.Challenge.class) : null;
      if (challenge == null || challenge.challengeId == null || challenge.serverId == null) {
         throw new XChatClient.ChatException("Couldn't reach the relay.", 60_000L);
      }

      String name = this.joiner.join(challenge.serverId);
      return this.relay.post(path, JsonUtil.GSON.toJson(Map.of("challengeId", challenge.challengeId, "name", name)));
   }

   /** A message for the player if the relay did not say yes, else null. */
   static String failureOf(XChatClient.Reply reply) {
      return switch (reply.status()) {
         case 200 -> null;
         case 401 -> "Couldn't verify your Minecraft account.";
         case 403 -> "You're blocked from this.";
         case 404 -> "Discord linking isn't switched on.";
         case 429 -> "Slow down a little, then try again.";
         default -> "The relay said " + reply.status() + ".";
      };
   }

   private static <T> T parse(String body, Class<T> type) {
      try {
         return JsonUtil.GSON.fromJson(body, type);
      } catch (JsonParseException e) {
         return null;
      }
   }
}
