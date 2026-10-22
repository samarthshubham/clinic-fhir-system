package io.github.samarthshubham.clinic.support;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.github.samarthshubham.clinic.config.AuthProperties;
import io.github.samarthshubham.clinic.config.JwtConfig;
import io.github.samarthshubham.clinic.dto.AuthUser;
import io.github.samarthshubham.clinic.security.Role;
import io.github.samarthshubham.clinic.service.TokenService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

public final class TestAuth {

  public static final String SECRET = "test-only-jwt-secret-0123456789abcdef";
  public static final String ISSUER = "clinic-api";
  public static final String PASSWORD = "Secret#123";
  public static final SecretKey KEY =
      new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

  private TestAuth() {}

  public static AuthProperties properties(Duration accessTtl, Duration refreshTtl) {
    String hash = new BCryptPasswordEncoder(4).encode(PASSWORD);
    return new AuthProperties(
        SECRET,
        ISSUER,
        accessTtl,
        refreshTtl,
        List.of(
            new AuthProperties.User("admin", hash, Role.ADMIN, "Alex", "Morgan", "Admin"),
            new AuthProperties.User(
                "clinician", hash, Role.CLINICIAN, "Anika", "Patel", "Internal Medicine"),
            new AuthProperties.User(
                "frontdesk", hash, Role.FRONT_DESK, "Jordan", "Lee", "Front desk")));
  }

  public static AuthProperties properties() {
    return properties(Duration.ofMinutes(15), Duration.ofDays(7));
  }

  public static TokenService tokenService(AuthProperties properties) {
    return new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(KEY)), KEY, properties);
  }

  public static JwtDecoder accessTokenDecoder() {
    return JwtConfig.decoderFor(KEY, ISSUER, JwtConfig.ACCESS_TOKEN);
  }

  public static AuthUser user(Role role) {
    return new AuthUser(role.name().toLowerCase(), "Test " + role, "user-" + role, List.of(role));
  }

  public static String signedToken(String tokenType, Instant issuedAt, Duration ttl) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject("admin")
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(ttl))
            .claim(JwtConfig.TOKEN_TYPE_CLAIM, tokenType)
            .claim(JwtConfig.ROLES_CLAIM, List.of(Role.ADMIN.name()))
            .build();
    return new NimbusJwtEncoder(new ImmutableSecret<>(KEY))
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }
}
