package org.blossomsuite.core.state;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class SuiteScheduler {
   public static final ScheduledExecutorService IO = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "SuiteCore-IO");
      t.setDaemon(true);
      return t;
   });
}
