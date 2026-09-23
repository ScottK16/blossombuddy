package org.blossomsuite.core.keybinds;

import org.blossomsuite.core.config.KeybindsConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.InputUtil.Key;
import net.minecraft.client.util.InputUtil.Type;
import org.lwjgl.glfw.GLFW;

public final class KeybindUtil {
   private KeybindUtil() {
   }

   public static int modsFromGlfwMask(int glfwMods) {
      int out = 0;
      if ((glfwMods & 2) != 0) {
         out |= 1;
      }

      if ((glfwMods & 1) != 0) {
         out |= 2;
      }

      if ((glfwMods & 4) != 0) {
         out |= 4;
      }

      return out;
   }

   public static boolean requiredModsHeld(long windowHandle, int requiredMods) {
      requiredMods = normalizeMods(requiredMods);
      if ((requiredMods & 1) != 0 && !isCtrlDown(windowHandle)) {
         return false;
      } else if ((requiredMods & 2) != 0 && !isShiftDown(windowHandle)) {
         return false;
      } else if ((requiredMods & 4) != 0 && !isAltDown(windowHandle)) {
         return false;
      } else if ((requiredMods & 8) != 0 && !InputUtil.isKeyPressed(windowHandle, 341)) {
         return false;
      } else if ((requiredMods & 16) != 0 && !InputUtil.isKeyPressed(windowHandle, 345)) {
         return false;
      } else if ((requiredMods & 32) != 0 && !InputUtil.isKeyPressed(windowHandle, 340)) {
         return false;
      } else if ((requiredMods & 64) != 0 && !InputUtil.isKeyPressed(windowHandle, 344)) {
         return false;
      } else {
         return (requiredMods & 128) != 0 && !InputUtil.isKeyPressed(windowHandle, 342)
            ? false
            : (requiredMods & 256) == 0 || InputUtil.isKeyPressed(windowHandle, 346);
      }
   }

   public static boolean isCtrlDown(long windowHandle) {
      return InputUtil.isKeyPressed(windowHandle, 341) || InputUtil.isKeyPressed(windowHandle, 345);
   }

   public static boolean isShiftDown(long windowHandle) {
      return InputUtil.isKeyPressed(windowHandle, 340) || InputUtil.isKeyPressed(windowHandle, 344);
   }

   public static boolean isAltDown(long windowHandle) {
      return InputUtil.isKeyPressed(windowHandle, 342) || InputUtil.isKeyPressed(windowHandle, 346);
   }

   public static int heldMods(long windowHandle) {
      int held = 0;
      if (InputUtil.isKeyPressed(windowHandle, 341)) {
         held |= 8;
      }

      if (InputUtil.isKeyPressed(windowHandle, 345)) {
         held |= 16;
      }

      if (InputUtil.isKeyPressed(windowHandle, 340)) {
         held |= 32;
      }

      if (InputUtil.isKeyPressed(windowHandle, 344)) {
         held |= 64;
      }

      if (InputUtil.isKeyPressed(windowHandle, 342)) {
         held |= 128;
      }

      if (InputUtil.isKeyPressed(windowHandle, 346)) {
         held |= 256;
      }

      return held;
   }

   public static int normalizeMods(int mods) {
      return mods & 511;
   }

   public static boolean isModifierOnlyChord(KeybindsConfig.Chord chord) {
      return chord != null && isModifierOnlyKey(chord.key) && normalizeMods(chord.mods) != 0;
   }

   public static boolean isModifierOnlyKey(String key) {
      return "suitecore.modifier_only".equals(key) || "blossomsuite.modifier_only".equals(key) || "mysticsuite.modifier_only".equals(key);
   }

   public static int modFromKeyCode(int keyCode) {
      return switch (keyCode) {
         case 340 -> 32;
         case 341 -> 8;
         case 342 -> 128;
         default -> 0;
         case 344 -> 64;
         case 345 -> 16;
         case 346 -> 256;
      };
   }

   public static boolean isModifierTranslationKey(String translationKey) {
      return translationKey == null
         ? false
         : "key.keyboard.left.shift".equals(translationKey)
            || "key.keyboard.right.shift".equals(translationKey)
            || "key.keyboard.left.control".equals(translationKey)
            || "key.keyboard.right.control".equals(translationKey)
            || "key.keyboard.left.alt".equals(translationKey)
            || "key.keyboard.right.alt".equals(translationKey);
   }

   public static String formatChord(KeybindsConfig.Chord chord) {
      if (chord == null || chord.key == null || chord.key.isBlank()) {
         return "Unbound";
      }

      if (isModifierOnlyChord(chord)) {
         return formatModsOnly(chord.mods);
      }

      StringBuilder sb = new StringBuilder();
      int mods = normalizeMods(chord.mods);
      appendMods(sb, mods, " + ");
      Key k = InputUtil.fromTranslationKey(chord.key);
      String keyName = formatKeyName(k, chord.key);
      sb.append(keyName);
      if (chord.extraKey != null && !chord.extraKey.isBlank()) {
         Key extra = InputUtil.fromTranslationKey(chord.extraKey);
         sb.append(" + ").append(formatKeyName(extra, chord.extraKey));
      }

      return sb.toString();
   }

   public static String formatModsOnly(int mods) {
      mods = normalizeMods(mods);
      StringBuilder sb = new StringBuilder();
      appendMods(sb, mods, " + ");
      return sb.length() == 0 ? "Unbound" : sb.toString();
   }

   private static void appendMods(StringBuilder sb, int mods, String separator) {
      appendMod(sb, mods, 1, "Ctrl", separator);
      appendMod(sb, mods, 8, "Left Ctrl", separator);
      appendMod(sb, mods, 16, "Right Ctrl", separator);
      appendMod(sb, mods, 2, "Shift", separator);
      appendMod(sb, mods, 32, "Left Shift", separator);
      appendMod(sb, mods, 64, "Right Shift", separator);
      appendMod(sb, mods, 4, "Alt", separator);
      appendMod(sb, mods, 128, "Left Alt", separator);
      appendMod(sb, mods, 256, "Right Alt", separator);
   }

   private static void appendMod(StringBuilder sb, int mods, int bit, String label, String separator) {
      if ((mods & bit) != 0) {
         if (sb.length() > 0) {
            sb.append(separator);
         }

         sb.append(label);
      }
   }

   public static String formatKeyName(Key key, String fallback) {
      if (key == null) {
         return fallback;
      } else {
         return key.getCategory() == Type.MOUSE ? formatMouseButton(key.getCode()) : key.getLocalizedText().getString();
      }
   }

   public static String formatMouseButton(int button) {
      return switch (button) {
         case 0 -> "Mouse Left";
         case 1 -> "Mouse Right";
         case 2 -> "Mouse Middle";
         default -> "Mouse Button " + (button + 1);
      };
   }

   public static boolean exactModsDown(long windowHandle, int requiredMods) {
      int required = normalizeMods(requiredMods);
      int held = heldMods(windowHandle);
      return !requiredModsHeld(windowHandle, required)
         ? false
         : noExtraModGroup(required, held, 1, 8, 16) && noExtraModGroup(required, held, 2, 32, 64) && noExtraModGroup(required, held, 4, 128, 256);
   }

   private static boolean noExtraModGroup(int required, int held, int generic, int left, int right) {
      int heldGroup = held & (left | right);
      if ((required & generic) != 0) {
         return heldGroup != 0;
      }

      int requiredSides = required & (left | right);
      return heldGroup == requiredSides;
   }

   public static boolean isMouseChord(KeybindsConfig.Chord chord) {
      if (chord != null && chord.key != null && !chord.key.isBlank()) {
         Key k = InputUtil.fromTranslationKey(chord.key);
         if (k != null && k.getCategory() == Type.MOUSE) {
            return true;
         } else if (chord.extraKey != null && !chord.extraKey.isBlank()) {
            Key extra = InputUtil.fromTranslationKey(chord.extraKey);
            return extra != null && extra.getCategory() == Type.MOUSE;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean matchesKeyPressed(KeybindsConfig.Chord chord, int keyCode, int glfwMods) {
      if (chord == null || chord.key == null || chord.key.isBlank()) {
         return false;
      }

      if (isModifierOnlyChord(chord)) {
         int pressedMod = modFromKeyCode(keyCode);
         if (pressedMod == 0) {
            return false;
         }

         int chordMods = normalizeMods(chord.mods);
         long window = windowHandle();
         return (chordMods & pressedMod) != 0 && exactModsDown(window, chordMods);
      } else {
         if (isModifierTranslationKey(chord.key)) {
            return false;
         }

         Key k = InputUtil.fromTranslationKey(chord.key);
         if (k == null) {
            return false;
         }

         if (k.getCategory() != Type.KEYSYM) {
            return false;
         }

         boolean pressedPrimary = k.getCode() == keyCode;
         boolean pressedExtra = false;
         if (chord.extraKey != null && !chord.extraKey.isBlank()) {
            Key extra = InputUtil.fromTranslationKey(chord.extraKey);
            if (extra == null || extra.getCategory() != Type.KEYSYM) {
               return false;
            }

            pressedExtra = extra.getCode() == keyCode;
            if (!isKeyLikeDown(windowHandle(), extra)) {
               return false;
            }
         }

         if (!pressedPrimary && !pressedExtra) {
            return false;
         } else {
            return !isKeyLikeDown(windowHandle(), k) ? false : requiredModsHeld(windowHandle(), chord.mods);
         }
      }
   }

   public static boolean isKeyLikeDown(long windowHandle, Key key) {
      if (key == null) {
         return false;
      } else if (key.getCategory() == Type.KEYSYM) {
         return InputUtil.isKeyPressed(windowHandle, key.getCode());
      } else {
         return key.getCategory() == Type.MOUSE ? GLFW.glfwGetMouseButton(windowHandle, key.getCode()) == 1 : false;
      }
   }

   public static long windowHandle() {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc != null && mc.getWindow() != null ? mc.getWindow().getHandle() : 0L;
   }
}
