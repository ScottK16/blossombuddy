package org.blossomsuite.core.xchat;

import java.util.ArrayList;
import java.util.List;

/** The JSON the mod exchanges with the relay's /v1/chat/* endpoints (see relay/chat.js). */
public final class XChatModels {
   private XChatModels() {
   }

   public static final class Challenge {
      public String challengeId;
      public String serverId;
   }

   public static final class AuthRequest {
      public String challengeId;
      public String name;

      public AuthRequest(String challengeId, String name) {
         this.challengeId = challengeId;
         this.name = name;
      }
   }

   public static final class Auth {
      public String token;
      public String name;
      public String uuid;
      public Integer expiresInSeconds;
   }

   public static final class SendRequest {
      public String token;
      public String message;
      public String realm;

      public SendRequest(String token, String message, String realm) {
         this.token = token;
         this.message = message;
         this.realm = realm;
      }
   }

   public static final class PollRequest {
      public String token;
      /** Null (left out of the JSON) on the first poll: "just tell me where the chat is now". */
      public Long since;

      public PollRequest(String token, Long since) {
         this.token = token;
         this.since = since;
      }
   }

   public static final class Message {
      public long id;
      public long ts;
      public String uuid;
      public String name;
      public String realm;
      public String text;
      /** 2-5 "#rrggbb" stops for the sender's name, only present when an admin has granted one (see relay/cosmetics.js). */
      public String[] gradient;
   }

   public static final class Poll {
      public List<Message> messages = new ArrayList<>();
      public long latestId;
   }

   public static final class Error {
      public String error;
   }
}
