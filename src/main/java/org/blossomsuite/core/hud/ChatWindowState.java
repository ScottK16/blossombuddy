package org.blossomsuite.core.hud;

/**
 * The scrolling state of one extra chat window: how many of the newest lines the reader has scrolled back past. Kept free of
 * game objects so it can be tested.
 */
public final class ChatWindowState {
   private int scroll = 0;
   private int lastCount = -1;

   public int scroll() {
      return this.scroll;
   }

   /**
    * Call every frame with how many lines the window's filter has. A reader who has scrolled back keeps their place while
    * new lines arrive underneath; the position is kept between the oldest and the newest line.
    */
   public void update(int count) {
      if (this.lastCount >= 0 && this.scroll > 0 && count > this.lastCount) {
         this.scroll += count - this.lastCount;
      }

      this.lastCount = count;
      this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, count - 1)));
   }

   /** Back ({@code delta} > 0) or forward ({@code delta} < 0) through the lines; returns whether anything moved. */
   public boolean scrollBy(int delta, int count) {
      int before = this.scroll;
      this.scroll = Math.max(0, Math.min(this.scroll + delta, Math.max(0, count - 1)));
      return this.scroll != before;
   }

   public void set(int lines) {
      this.scroll = Math.max(0, lines);
   }

   public void reset() {
      this.scroll = 0;
   }
}
