package org.blossomsuite.core.music;

/** What the player can ask of their music app. The word is what the Windows helper script understands. */
public enum MediaCommand {
   NEXT("next"),
   PREVIOUS("prev"),
   PLAY_PAUSE("pause");

   private final String word;

   MediaCommand(String word) {
      this.word = word;
   }

   public String word() {
      return this.word;
   }
}
