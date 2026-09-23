package org.blossomsuite.core.jobs.overflow;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Decrypts the original standalone Jobs Overflow XP mod's save file, so a player's existing data can be carried over
 * instead of silently failing to parse as plain JSON. The scheme (and the key itself) came straight from that mod's
 * own author: the whole file is one base64 blob of a 12-byte GCM nonce followed by AES/GCM ciphertext (Java's GCM
 * cipher appends the 16-byte auth tag to the ciphertext itself, so there is nothing else to strip).
 */
final class LegacyOverflowCrypto {
   private static final byte[] KEY_BYTES = "Jf7kQ2zP9mX4nR6w".getBytes(StandardCharsets.UTF_8);
   private static final int IV_LENGTH = 12;
   private static final int TAG_BITS = 128;

   private LegacyOverflowCrypto() {
   }

   /** @throws Exception whatever the underlying cipher throws - too short, wrong key, corrupted, not actually encrypted, ... */
   static String decrypt(String base64) throws Exception {
      byte[] combined = Base64.getDecoder().decode(base64);
      if (combined.length <= IV_LENGTH) {
         throw new IllegalArgumentException("too short to contain a GCM nonce");
      }

      byte[] iv = new byte[IV_LENGTH];
      byte[] cipherText = new byte[combined.length - IV_LENGTH];
      System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
      System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY_BYTES, "AES"), new GCMParameterSpec(TAG_BITS, iv));
      return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
   }
}
