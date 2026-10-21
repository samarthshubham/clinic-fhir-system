package io.github.samarthshubham.clinic.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.TimeZone;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.InstantType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FhirValuesTest {

  private TimeZone originalZone;

  @BeforeEach
  void useAZoneFarFromTheStoredOffsets() {
    originalZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
  }

  @AfterEach
  void restoreZone() {
    TimeZone.setDefault(originalZone);
  }

  @ParameterizedTest
  @CsvSource({
    "2026-05-01T22:00:00-05:00, 2026-05-01",
    "2026-05-01T23:30:00-05:00, 2026-05-01",
    "2026-05-01T02:00:00+05:30, 2026-05-01",
    "2026-05-01T00:00:00Z, 2026-05-01",
    "2026-05-01, 2026-05-01"
  })
  void localDateKeepsTheStoredCalendarDate(String stored, LocalDate expected) {
    assertThat(FhirValues.localDate(new DateTimeType(stored))).isEqualTo(expected);
  }

  @Test
  void localDateReadsInstantsAndDatesAsStored() {
    assertThat(FhirValues.localDate(new InstantType("2026-04-12T23:30:00.000-05:00")))
        .isEqualTo(LocalDate.of(2026, 4, 12));
    assertThat(FhirValues.localDate(new DateType("1980-02-03")))
        .isEqualTo(LocalDate.of(1980, 2, 3));
  }

  @Test
  void localDateIsNullWithoutADay() {
    assertThat(FhirValues.localDate(new DateType("1980"))).isNull();
    assertThat(FhirValues.localDate(new DateType("1980-02"))).isNull();
    assertThat(FhirValues.localDate(new DateTimeType())).isNull();
    assertThat(FhirValues.localDate(null)).isNull();
  }

  @ParameterizedTest
  @CsvSource({"2026-11-20T09:00:00-05:00", "2026-11-20T09:00:00+05:30", "2026-11-20T09:00:00Z"})
  void offsetDateTimeKeepsTheStoredOffset(String stored) {
    assertThat(FhirValues.offsetDateTime(new InstantType(stored)))
        .isEqualTo(OffsetDateTime.parse(stored));
  }

  @Test
  void offsetDateTimeIsNullWhenAbsent() {
    assertThat(FhirValues.offsetDateTime(new InstantType())).isNull();
    assertThat(FhirValues.offsetDateTime(null)).isNull();
  }
}
