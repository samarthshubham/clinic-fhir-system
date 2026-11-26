Feature: Seed payer — demo terminology

  PAS binds claim lines, diagnoses and attachment types to specific vocabularies.
  Load the demo billing-code fragments and the complete published code systems
  used by DTR and PAS into the payer HAPI's native terminology index.

  Scenario: Seed the demo code systems
    Given the payer HAPI has every CodeSystem in the terminology folder
