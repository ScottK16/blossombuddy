package org.blossomsuite.core.jobs;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JobsParser {
   private static final Pattern MONEY_XP = Pattern.compile("You got:\\s*\\$(?<money>\\d+(?:\\.\\d+)?),\\s*and\\s*(?<exp>\\d+(?:\\.\\d+)?)\\s*exp", 2);

   private JobsParser() {
   }

   public static JobsParser.ParsedReward tryParse(String plainText) {
      Matcher m = MONEY_XP.matcher(plainText);
      if (!m.find()) {
         return null;
      }

      double money = Double.parseDouble(m.group("money"));
      double exp = Double.parseDouble(m.group("exp"));
      return new JobsParser.ParsedReward(money, exp);
   }

   public record ParsedReward(double money, double exp) {
   }
}
