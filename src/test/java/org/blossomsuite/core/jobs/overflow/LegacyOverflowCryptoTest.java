package org.blossomsuite.core.jobs.overflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * The old standalone Jobs Overflow XP mod's own encrypt method was never itself given to us, only the key and the
 * decrypt side (see LegacyOverflowCrypto) - so these tests build ciphertext with the exact same
 * AES/GCM/NoPadding + 12-byte-nonce-prefix scheme independently, and check our decrypt reverses it correctly.
 */
class LegacyOverflowCryptoTest {
   private static final byte[] KEY_BYTES = "Jf7kQ2zP9mX4nR6w".getBytes(StandardCharsets.UTF_8);

   private static String encrypt(String plaintext) throws Exception {
      byte[] iv = new byte[12];
      new SecureRandom().nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY_BYTES, "AES"), new GCMParameterSpec(128, iv));
      byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] combined = new byte[iv.length + cipherText.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
      return Base64.getEncoder().encodeToString(combined);
   }

   @Test
   void decryptsBackToTheExactOriginalTextRoundTrip() throws Exception {
      String plain = "{\"jobs\":{\"Miner\":1234.5,\"Builder\":6789.0}}";
      assertEquals(plain, LegacyOverflowCrypto.decrypt(encrypt(plain)));
   }

   @Test
   void handlesTextWithUnicodeAndSpecialCharactersCorrectly() throws Exception {
      String plain = "{\"realm\":\"Cherry 🌸\",\"note\":\"café \\\"quoted\\\"\"}";
      assertEquals(plain, LegacyOverflowCrypto.decrypt(encrypt(plain)));
   }

   @Test
   void anEmptyPlaintextRoundTripsToAnEmptyString() throws Exception {
      assertEquals("", LegacyOverflowCrypto.decrypt(encrypt("")));
   }

   @Test
   void aTamperedCiphertextFailsRatherThanReturningGarbageSilently() throws Exception {
      String encoded = encrypt("{\"jobs\":{}}");
      byte[] bytes = Base64.getDecoder().decode(encoded);
      bytes[bytes.length - 1] ^= 0x01; // flip a bit inside the GCM tag/ciphertext
      String tampered = Base64.getEncoder().encodeToString(bytes);
      assertThrows(Exception.class, () -> LegacyOverflowCrypto.decrypt(tampered));
   }

   @Test
   void inputTooShortToEvenHoldANonceIsRejectedCleanly() {
      String tooShort = Base64.getEncoder().encodeToString(new byte[5]);
      assertThrows(Exception.class, () -> LegacyOverflowCrypto.decrypt(tooShort));
   }

   @Test
   void notActuallyBase64AtAllFailsRatherThanCrashingTheWholeImport() {
      assertThrows(Exception.class, () -> LegacyOverflowCrypto.decrypt("not valid base64!!!"));
   }

   @Test
   void theWrongKeyWouldNeverDecryptThisCorrectly_confirmsWeAreUsingTheRealKey() throws Exception {
      // encrypt with a DIFFERENT key entirely, and confirm our decrypt (hardcoded to the real key) refuses it
      byte[] wrongKey = "0000000000000000".getBytes(StandardCharsets.UTF_8);
      byte[] iv = new byte[12];
      new SecureRandom().nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(wrongKey, "AES"), new GCMParameterSpec(128, iv));
      byte[] cipherText = cipher.doFinal("{}".getBytes(StandardCharsets.UTF_8));
      byte[] combined = new byte[iv.length + cipherText.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
      String encoded = Base64.getEncoder().encodeToString(combined);

      assertThrows(Exception.class, () -> LegacyOverflowCrypto.decrypt(encoded));
   }
}
