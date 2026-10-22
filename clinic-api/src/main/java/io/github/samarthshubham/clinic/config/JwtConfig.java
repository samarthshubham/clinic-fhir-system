package io.github.samarthshubham.clinic.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.github.samarthshubham.clinic.security.Role;
import io.github.samarthshubham.clinic.security.RolePermissions;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class JwtConfig {

  public static final String TOKEN_TYPE_CLAIM = "token_type";
  public static final String ROLES_CLAIM = "roles";
  public static final String ACCESS_TOKEN = "access";
  public static final String REFRESH_TOKEN = "refresh";

  private static final int MIN_HS256_KEY_BYTES = 32;

  @Bean
  SecretKey jwtSigningKey(AuthProperties authProperties) {
    byte[] secret = authProperties.jwtSecret().getBytes(StandardCharsets.UTF_8);
    if (secret.length < MIN_HS256_KEY_BYTES) {
      throw new IllegalStateException(
          "clinic.auth.jwt-secret (env CLINIC_JWT_SECRET) must be set to at least "
              + MIN_HS256_KEY_BYTES
              + " bytes for HS256");
    }
    return new SecretKeySpec(secret, "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKey jwtSigningKey, AuthProperties authProperties) {
    return decoderFor(jwtSigningKey, authProperties.issuer(), ACCESS_TOKEN);
  }

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> new ArrayList<>(RolePermissions.authoritiesFor(rolesOf(jwt))));
    return converter;
  }

  public static JwtDecoder decoderFor(SecretKey key, String issuer, String expectedTokenType) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer),
            new JwtClaimValidator<String>(TOKEN_TYPE_CLAIM, expectedTokenType::equals)));
    return decoder;
  }

  private static List<Role> rolesOf(Jwt jwt) {
    List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
    return roles == null ? List.of() : roles.stream().map(Role::valueOf).toList();
  }
}
