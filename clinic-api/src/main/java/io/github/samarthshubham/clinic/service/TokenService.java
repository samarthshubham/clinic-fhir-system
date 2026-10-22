package io.github.samarthshubham.clinic.service;

import io.github.samarthshubham.clinic.config.AuthProperties;
import io.github.samarthshubham.clinic.config.JwtConfig;
import io.github.samarthshubham.clinic.dto.AuthUser;
import io.github.samarthshubham.clinic.dto.TokenResponse;
import java.time.Instant;
import javax.crypto.SecretKey;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

  private final JwtEncoder jwtEncoder;
  private final JwtDecoder refreshTokenDecoder;
  private final AuthProperties authProperties;

  public TokenService(
      JwtEncoder jwtEncoder, SecretKey jwtSigningKey, AuthProperties authProperties) {
    this.jwtEncoder = jwtEncoder;
    this.refreshTokenDecoder =
        JwtConfig.decoderFor(jwtSigningKey, authProperties.issuer(), JwtConfig.REFRESH_TOKEN);
    this.authProperties = authProperties;
  }

  public TokenResponse issueTokens(AuthUser user) {
    Instant now = Instant.now();
    JwtClaimsSet.Builder access =
        JwtClaimsSet.builder()
            .issuer(authProperties.issuer())
            .subject(user.username())
            .issuedAt(now)
            .expiresAt(now.plus(authProperties.accessTokenTtl()))
            .claim(JwtConfig.TOKEN_TYPE_CLAIM, JwtConfig.ACCESS_TOKEN)
            .claim(JwtConfig.ROLES_CLAIM, user.roles().stream().map(Enum::name).toList())
            .claim("name", user.name());
    if (user.practitionerId() != null) {
      access.claim("practitioner_id", user.practitionerId());
    }
    JwtClaimsSet refresh =
        JwtClaimsSet.builder()
            .issuer(authProperties.issuer())
            .subject(user.username())
            .issuedAt(now)
            .expiresAt(now.plus(authProperties.refreshTokenTtl()))
            .claim(JwtConfig.TOKEN_TYPE_CLAIM, JwtConfig.REFRESH_TOKEN)
            .build();
    return new TokenResponse(
        encode(access.build()),
        encode(refresh),
        "Bearer",
        authProperties.accessTokenTtl().toSeconds(),
        user);
  }

  public String usernameFromRefreshToken(String refreshToken) {
    try {
      return refreshTokenDecoder.decode(refreshToken).getSubject();
    } catch (JwtException e) {
      throw new BadCredentialsException("Invalid or expired refresh token", e);
    }
  }

  private String encode(JwtClaimsSet claims) {
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
