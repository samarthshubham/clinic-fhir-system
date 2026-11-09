package io.github.samarthshubham.clinic.mapper;

final class UsCoreProfiles {

  private static final String BASE = "http://hl7.org/fhir/us/core/StructureDefinition/";

  static final String PATIENT = BASE + "us-core-patient";
  static final String PRACTITIONER = BASE + "us-core-practitioner";
  static final String ENCOUNTER = BASE + "us-core-encounter";
  static final String CONDITION_PROBLEMS = BASE + "us-core-condition-problems-health-concerns";
  static final String SERVICE_REQUEST = BASE + "us-core-servicerequest";
  static final String COVERAGE = BASE + "us-core-coverage";
  static final String ORGANIZATION = BASE + "us-core-organization";

  private UsCoreProfiles() {}
}
