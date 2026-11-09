package io.github.samarthshubham.payer.service;

import io.github.samarthshubham.payer.config.AuthProperties;
import io.github.samarthshubham.payer.config.JwtConfig;
import io.github.samarthshubham.payer.dto.AuthUser;
import io.github.samarthshubham.payer.dto.OAuthTokenResponse;
import io.github.samarthshubham.payer.dto.TokenResponse;
import java.time.Instant;
import java.util.List;
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

  public static final String MEMBER_SCOPE = "patient/*.r";
  private static final String OAUTH_TOKEN_TYPE = "bearer";

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

  public TokenResponse issueStaffTokens(AuthUser user) {
    Instant now = Instant.now();
    JwtClaimsSet access =
        JwtClaimsSet.builder()
            .issuer(authProperties.issuer())
            .subject(user.username())
            .issuedAt(now)
            .expiresAt(now.plus(authProperties.accessTokenTtl()))
            .claim(JwtConfig.TOKEN_TYPE_CLAIM, JwtConfig.ACCESS_TOKEN)
            .claim(JwtConfig.ROLES_CLAIM, user.roles().stream().map(Enum::name).toList())
            .claim("name", user.name())
            .build();
    JwtClaimsSet refresh =
        JwtClaimsSet.builder()
            .issuer(authProperties.issuer())
            .subject(user.username())
            .issuedAt(now)
            .expiresAt(now.plus(authProperties.refreshTokenTtl()))
            .claim(JwtConfig.TOKEN_TYPE_CLAIM, JwtConfig.REFRESH_TOKEN)
            .build();
    return new TokenResponse(
        encode(access),
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

  public OAuthTokenResponse issueSystemToken(String clientId, List<String> scopes) {
    String scope = String.join(" ", scopes);
    return new OAuthTokenResponse(
        encode(clientToken(clientId, scope).build()),
        OAUTH_TOKEN_TYPE,
        authProperties.clientTokenTtl().toSeconds(),
        scope,
        null);
  }

  public OAuthTokenResponse issueMemberToken(String clientId, String patientId) {
    JwtClaimsSet claims =
        clientToken(clientId, MEMBER_SCOPE).claim(JwtConfig.PATIENT_CLAIM, patientId).build();
    return new OAuthTokenResponse(
        encode(claims),
        OAUTH_TOKEN_TYPE,
        authProperties.clientTokenTtl().toSeconds(),
        MEMBER_SCOPE,
        patientId);
  }

  private JwtClaimsSet.Builder clientToken(String clientId, String scope) {
    Instant now = Instant.now();
    return JwtClaimsSet.builder()
        .issuer(authProperties.issuer())
        .subject(clientId)
        .issuedAt(now)
        .expiresAt(now.plus(authProperties.clientTokenTtl()))
        .claim(JwtConfig.TOKEN_TYPE_CLAIM, JwtConfig.ACCESS_TOKEN)
        .claim(JwtConfig.CLIENT_ID_CLAIM, clientId)
        .claim(JwtConfig.SCOPE_CLAIM, scope);
  }

  private String encode(JwtClaimsSet claims) {
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
