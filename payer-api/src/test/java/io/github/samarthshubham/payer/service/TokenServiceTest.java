package io.github.samarthshubham.payer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.samarthshubham.payer.config.JwtConfig;
import io.github.samarthshubham.payer.dto.OAuthTokenResponse;
import io.github.samarthshubham.payer.dto.TokenResponse;
import io.github.samarthshubham.payer.security.Role;
import io.github.samarthshubham.payer.support.TestAuth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class TokenServiceTest {

  private final TokenService tokenService = TestAuth.tokenService(TestAuth.properties());
  private final JwtDecoder accessDecoder = TestAuth.accessTokenDecoder();

  @Test
  void staffAccessTokenCarriesRolesAndNoScopes() {
    TokenResponse tokens = tokenService.issueStaffTokens(TestAuth.user(Role.UM_REVIEWER));

    Jwt access = accessDecoder.decode(tokens.accessToken());
    assertThat(access.getSubject()).isEqualTo("um_reviewer");
    assertThat(access.getClaimAsStringList(JwtConfig.ROLES_CLAIM)).containsExactly("UM_REVIEWER");
    assertThat(access.hasClaim(JwtConfig.SCOPE_CLAIM)).isFalse();
    assertThat(tokens.expiresIn()).isEqualTo(900);
  }

  @Test
  void refreshTokenGivesBackTheUsernameButIsNotAnAccessToken() {
    TokenResponse tokens = tokenService.issueStaffTokens(TestAuth.user(Role.PAYER_ADMIN));

    assertThat(tokenService.usernameFromRefreshToken(tokens.refreshToken()))
        .isEqualTo("payer_admin");
    assertThatThrownBy(() -> accessDecoder.decode(tokens.refreshToken()))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void accessTokenIsNotARefreshToken() {
    String access = tokenService.issueStaffTokens(TestAuth.user(Role.UM_REVIEWER)).accessToken();

    assertThatThrownBy(() -> tokenService.usernameFromRefreshToken(access))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void systemTokenCarriesTheGrantedSmartScopes() {
    OAuthTokenResponse token =
        tokenService.issueSystemToken(
            "clinic-api", List.of("system/Claim.c", "system/Questionnaire.r"));

    Jwt jwt = accessDecoder.decode(token.accessToken());
    assertThat(jwt.getSubject()).isEqualTo("clinic-api");
    assertThat(jwt.getClaimAsString(JwtConfig.CLIENT_ID_CLAIM)).isEqualTo("clinic-api");
    assertThat(jwt.getClaimAsString(JwtConfig.SCOPE_CLAIM))
        .isEqualTo("system/Claim.c system/Questionnaire.r");
    assertThat(jwt.hasClaim(JwtConfig.PATIENT_CLAIM)).isFalse();
    assertThat(token.tokenType()).isEqualTo("bearer");
    assertThat(token.scope()).isEqualTo("system/Claim.c system/Questionnaire.r");
    assertThat(token.expiresIn()).isEqualTo(300);
    assertThat(token.patient()).isNull();
  }

  @Test
  void memberTokenCarriesPatientScopesAndThePatientClaim() {
    OAuthTokenResponse token = tokenService.issueMemberToken("clinic-api", "member-a");

    Jwt jwt = accessDecoder.decode(token.accessToken());
    assertThat(jwt.getClaimAsString(JwtConfig.SCOPE_CLAIM)).isEqualTo("patient/*.r");
    assertThat(jwt.getClaimAsString(JwtConfig.PATIENT_CLAIM)).isEqualTo("member-a");
    assertThat(token.scope()).isEqualTo("patient/*.r");
    assertThat(token.patient()).isEqualTo("member-a");
  }

  @Test
  void tamperedTokenFailsSignatureCheck() {
    String token =
        tokenService.issueSystemToken("clinic-api", List.of("system/Claim.c")).accessToken();
    String[] parts = token.split("\\.");
    String tampered = parts[0] + "." + parts[1] + "x." + parts[2];

    assertThatThrownBy(() -> accessDecoder.decode(tampered)).isInstanceOf(JwtException.class);
  }
}
