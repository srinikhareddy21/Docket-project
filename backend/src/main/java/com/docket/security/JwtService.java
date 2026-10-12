package com.docket.security;

import com.docket.config.AppProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Short-lived access tokens (HS256). The secret comes from JWT_SECRET and is validated at startup. */
@Service
public class JwtService {
  private final byte[] secret;
  private final AppProperties props;

  public JwtService(AppProperties props) {
    this.props = props;
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(props.jwt().secret().trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("JWT_SECRET must be Base64. Generate one with: openssl rand -base64 48");
    }
    if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes.");
    this.secret = bytes;
  }

  public String issue(long userId, String sid) {
    Instant now = Instant.now();
    try {
      JWTClaimsSet claims = new JWTClaimsSet.Builder()
          .issuer(props.jwt().issuer())
          .subject(Long.toString(userId))
          .claim("sid", sid)
          .issueTime(Date.from(now))
          .expirationTime(Date.from(now.plus(props.auth().accessTtl())))
          .build();
      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      jwt.sign(new MACSigner(secret));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException("Could not sign token", e);
    }
  }

  /** Returns the principal only if signature, issuer and expiry are all valid. */
  public Optional<AuthUser> verify(String token) {
    try {
      SignedJWT jwt = SignedJWT.parse(token);
      if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) return Optional.empty();
      if (!jwt.verify(new MACVerifier(secret))) return Optional.empty();
      JWTClaimsSet c = jwt.getJWTClaimsSet();
      if (!props.jwt().issuer().equals(c.getIssuer())) return Optional.empty();
      if (c.getExpirationTime() == null || c.getExpirationTime().toInstant().isBefore(Instant.now())) return Optional.empty();
      String sid = c.getStringClaim("sid");
      if (sid == null || c.getSubject() == null) return Optional.empty();
      return Optional.of(new AuthUser(Long.parseLong(c.getSubject()), sid));
    } catch (ParseException | JOSEException | NumberFormatException e) {
      return Optional.empty();
    }
  }
}
