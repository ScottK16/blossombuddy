package org.blossomsuite.core.util;

import org.blossomsuite.core.SuiteRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SuiteLog {
   private static final Logger FALLBACK = LoggerFactory.getLogger("suite-core");

   private SuiteLog() {
   }

   public static Logger logger() {
      try {
         return LoggerFactory.getLogger(SuiteRuntime.profile().modId());
      } catch (IllegalStateException ignored) {
         return FALLBACK;
      }
   }
}
