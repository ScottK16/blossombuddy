package org.blossomsuite.core.util;

/**
 * What {@code /buddy rent} fills in for you, shared with the Rentals section of the options screen: the item is the one in your hand and
 * the place is the realm you are on, unless you type something else.
 */
public final class RentalDefaults {
   private RentalDefaults() {
   }

   /** The name for a rented item: its display name, else the item's own name, else "Rental". */
   public static String itemName(String hoverName, String baseName) {
      if (hoverName != null && !hoverName.isBlank()) {
         return hoverName.trim();
      }

      return baseName != null && !baseName.isBlank() ? baseName.trim() : "Rental";
   }

   /** The place typed in, else the realm the player is on, else "Unknown". */
   public static String place(String typed, String realm) {
      if (typed != null && !typed.isBlank()) {
         return typed.trim();
      }

      return realm != null && !realm.isBlank() ? realm.trim() : "Unknown";
   }

   /** The text for the Item Name box: what was typed, else the held item's name, else "Rental". */
   public static String itemOrHeld(String typed, String heldHoverName, String heldBaseName, boolean holding) {
      if (typed != null && !typed.isBlank()) {
         return typed.trim();
      }

      return holding ? itemName(heldHoverName, heldBaseName) : "Rental";
   }
}
