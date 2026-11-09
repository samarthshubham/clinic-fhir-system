package io.github.samarthshubham.payer.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.samarthshubham.payer.security.SmartScope.Context;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SmartScopeTest {

  @Test
  void parsesASystemScope() {
    assertThat(SmartScope.parse("system/Claim.c"))
        .contains(new SmartScope(Context.SYSTEM, "Claim", EnumSet.of(SmartPermission.CREATE)));
  }

  @Test
  void parsesAPatientWildcardScopeWithSeveralPermissions() {
    SmartScope scope = SmartScope.parse("patient/*.rs").orElseThrow();

    assertThat(scope.context()).isEqualTo(Context.PATIENT);
    assertThat(scope.anyResource()).isTrue();
    assertThat(scope.permissions())
        .containsExactlyInAnyOrder(SmartPermission.READ, SmartPermission.SEARCH);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "system/Claim.read",
        "system/Claim.",
        "system/Claim.rc",
        "system/claim.r",
        "launch/patient",
        "openid",
        "system/Claim.r?status=active"
      })
  void rejectsAnythingThatIsNotAPlainV2Scope(String scope) {
    assertThat(SmartScope.parse(scope)).isEmpty();
  }

  @Test
  void parseAllSkipsUnknownScopesAndToleratesNull() {
    assertThat(SmartScope.parseAll("openid  system/Claim.c system/Questionnaire.r"))
        .extracting(SmartScope::resourceType)
        .containsExactly("Claim", "Questionnaire");
    assertThat(SmartScope.parseAll(null)).isEmpty();
  }

  @Test
  void grantsOnlyTheMatchingTypeAndPermission() {
    SmartScope claimCreate = SmartScope.parse("system/Claim.c").orElseThrow();
    SmartScope anyRead = SmartScope.parse("system/*.r").orElseThrow();

    assertThat(claimCreate.grants("Claim", SmartPermission.CREATE)).isTrue();
    assertThat(claimCreate.grants("Claim", SmartPermission.READ)).isFalse();
    assertThat(claimCreate.grants("Questionnaire", SmartPermission.CREATE)).isFalse();
    assertThat(anyRead.grants("Questionnaire", SmartPermission.READ)).isTrue();
  }
}
