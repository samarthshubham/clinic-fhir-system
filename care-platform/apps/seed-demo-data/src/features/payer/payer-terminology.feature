Feature: Seed payer — demo terminology

  The payer HAPI has no CPT, HCPCS, ICD-10-CM or X12 code systems, so it cannot
  check the PAS required bindings on Claim/$submit. Each file in
  terminology/codesystems is a fragment CodeSystem with only the codes the demo
  uses. Each is PUT under its fixed id, so a rerun with unchanged files leaves
  the payer HAPI as it is, and a changed file becomes a new version.

  Scenario: Seed the demo code systems
    Given the payer HAPI has every CodeSystem in the terminology folder
