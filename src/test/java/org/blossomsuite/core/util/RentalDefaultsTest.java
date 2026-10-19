package org.blossomsuite.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RentalDefaultsTest {
   @Test
   void theItemIsNamedAfterWhatYouHold() {
      assertEquals("Blossom Staff", RentalDefaults.itemName("  Blossom Staff ", "Stick"));
      assertEquals("Stick", RentalDefaults.itemName("", "Stick"));
      assertEquals("Stick", RentalDefaults.itemName(null, " Stick "));
      assertEquals("Rental", RentalDefaults.itemName(null, null));
      assertEquals("Rental", RentalDefaults.itemName(" ", " "));
   }

   @Test
   void thePlaceIsWhatWasTypedElseTheRealmYouAreOn() {
      assertEquals("PW Shop", RentalDefaults.place(" PW Shop ", "cherry"));
      assertEquals("cherry", RentalDefaults.place("", "cherry"));
      assertEquals("cherry", RentalDefaults.place(null, " cherry "));
      assertEquals("Unknown", RentalDefaults.place(null, null));
      assertEquals("Unknown", RentalDefaults.place("  ", ""));
   }

   @Test
   void aBlankItemNameInTheOptionsUsesTheItemInHandLikeTheCommandDoes() {
      assertEquals("My Pick", RentalDefaults.itemOrHeld("My Pick", "Held Thing", null, true), "typed text wins");
      assertEquals("Held Thing", RentalDefaults.itemOrHeld("", "Held Thing", "Stick", true));
      assertEquals("Stick", RentalDefaults.itemOrHeld("  ", null, "Stick", true));
      assertEquals("Rental", RentalDefaults.itemOrHeld("", "Held Thing", null, false), "nothing in hand");
   }
}
