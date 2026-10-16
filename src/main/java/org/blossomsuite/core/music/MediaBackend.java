package org.blossomsuite.core.music;

import java.io.Closeable;
import java.io.IOException;

/** A way of asking this computer what is playing and telling it to skip. One per operating system. */
public interface MediaBackend extends Closeable {
   /** What is playing now; {@link NowPlaying#NONE} when nothing is. */
   NowPlaying poll() throws IOException;

   /** Presses a button on the music app, then says what is playing afterwards. */
   NowPlaying command(MediaCommand command) throws IOException;

   @Override
   void close();
}
