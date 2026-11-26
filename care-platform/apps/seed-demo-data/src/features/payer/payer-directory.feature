Feature: Seed payer — Plan-Net directory

  Publish the clinic's demo clinicians with their public practice location,
  specialty, service and network. Keep the payer and plan ids used by members.

  Scenario: Publish the Praxis provider directory
    Given payer organization "PHP01" named "Praxis Health Plan" is in the payer HAPI
    And the payer HAPI has these Plan-Net fixtures:
      | file                          |
      | organization-payer.json       |
      | organization-clinic.json      |
      | location-illinois.json        |
      | organization-network.json     |
      | endpoint.json                 |
      | location-clinic.json          |
      | healthcare-service.json       |
      | practitioner.json             |
      | practitioner-clinician.json   |
      | practitioner-role-aisha.json  |
      | practitioner-role-anika.json  |
      | organization-affiliation.json |
      | insurance-plan.json           |
      | insurance-plan-silver.json    |
    Then the public directory has these search matches:
      | type                    | query                                      | id                            |
      | Practitioner            | given=Aisha&family=Patel                   | aisha-patel                   |
      | Practitioner            | given=Anika&family=Patel                   | anika-patel                   |
      | PractitionerRole        | specialty=207R00000X&location.address-city=Springfield&network=Organization/praxis-standard-network | praxis-aisha-internal-medicine |
      | Location                | address-city=Springfield                   | praxis-clinic-location        |
      | HealthcareService       | coverage-area=Location/state-of-illinois  | praxis-primary-care           |
      | Organization            | coverage-area=Location/state-of-illinois  | praxis-standard-network       |
      | OrganizationAffiliation | participating-organization=Organization/praxis-clinic | praxis-clinic-affiliation |
      | Endpoint                | organization=Organization/praxis-clinic   | praxis-clinic-directory       |
