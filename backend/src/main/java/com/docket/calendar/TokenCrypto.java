package com.docket.calendar;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM encryption for third-party refresh tokens at rest. The key comes from TOKEN_ENCRYPTION_KEY
 * (base64, exactly 32 bytes). Output: "v1." + base64url(iv || ciphertext+tag). A fresh random IV every time.
 * Pure logic, no Spring.
 */
public final class TokenCrypto {
  private static final SecureRandom RNG = new SecureRandom();
  private static final String PREFIX = "v1.";
  private final SecretKeySpec key;

  public TokenCrypto(String base64Key) {
    byte[] k;
    try { k = Base64.getDecoder().decode(base64Key == null ? "" : base64Key.trim()); }
    catch (IllegalArgumentException e) { throw new IllegalArgumentException("TOKEN_ENCRYPTION_KEY must be base64"); }
    if (k.length != 32) throw new IllegalArgumentException("TOKEN_ENCRYPTION_KEY must decode to exactly 32 bytes (openssl rand -base64 32)");
    this.key = new SecretKeySpec(k, "AES");
  }

  public static boolean isValidKey(String base64Key) {
    try { new TokenCrypto(base64Key); return true; } catch (RuntimeException e) { return false; }
  }

  public String encrypt(String plain) {
    try {
      byte[] iv = new byte[12];
      RNG.nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
      byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
    } catch (GeneralSecurityException e) { throw new IllegalStateException("encryption failed"); }
  }

  /** Throws IllegalArgumentException if the value was tampered with or encrypted under a different key. */
  public String decrypt(String stored) {
    try {
      if (stored == null || !stored.startsWith(PREFIX)) throw new IllegalArgumentException("unsupported format");
      byte[] all = Base64.getUrlDecoder().decode(stored.substring(PREFIX.length()));
      if (all.length < 12 + 16) throw new IllegalArgumentException("too short");
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
      return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) { throw new IllegalArgumentException("cannot decrypt"); }
  }
}
