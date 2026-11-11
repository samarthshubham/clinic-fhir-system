package io.github.samarthshubham.payer.support;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.github.samarthshubham.payer.config.AuthProperties;
import io.github.samarthshubham.payer.config.JwtConfig;
import io.github.samarthshubham.payer.dto.AuthUser;
import io.github.samarthshubham.payer.security.Role;
import io.github.samarthshubham.payer.service.TokenService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

public final class TestAuth {

  public static final String SECRET = "test-only-payer-jwt-secret-0123456789abcdef";
  public static final String ISSUER = "payer-api";
  public static final String PASSWORD = "Secret#123";
  public static final String CLIENT_ID = "clinic-api";
  public static final String CLIENT_SECRET = "test-client-secret";
  public static final List<String> CLIENT_SCOPES =
      List.of("system/Claim.c", "system/Coverage.r", "system/Questionnaire.r");
  public static final SecretKey KEY =
      new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

  private TestAuth() {}

  public static AuthProperties properties() {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    String hash = encoder.encode(PASSWORD);
    return new AuthProperties(
        SECRET,
        ISSUER,
        Duration.ofMinutes(15),
        Duration.ofDays(7),
        Duration.ofMinutes(5),
        List.of(
            new AuthProperties.User(
                "reviewer", hash, Role.UM_REVIEWER, "Dana", "Whitfield", "UM nurse"),
            new AuthProperties.User(
                "payeradmin", hash, Role.PAYER_ADMIN, "Sam", "Okoro", "Payer administrator")),
        List.of(
            new AuthProperties.Client(CLIENT_ID, encoder.encode(CLIENT_SECRET), CLIENT_SCOPES)));
  }

  public static TokenService tokenService(AuthProperties properties) {
    return new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(KEY)), KEY, properties);
  }

  public static JwtDecoder accessTokenDecoder() {
    return JwtConfig.decoderFor(KEY, ISSUER, JwtConfig.ACCESS_TOKEN);
  }

  public static AuthUser user(Role role) {
    return new AuthUser(role.name().toLowerCase(), "Test " + role, "Tester", List.of(role));
  }
}
