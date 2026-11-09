const SPRING_API = 'http://localhost:9090';
const HAPI_FHIR = 'http://localhost:8080';
const DEFAULT_SEED_USERNAME = 'clinician';
const DEFAULT_SEED_PASSWORD = 'Clinician#2026';
const MRN_SYSTEM = 'http://praxis.clinic/fhir/identifier/mrn';
const PAYER_FHIR =
  process.env['PAYER_FHIR_URL'] ?? 'http://localhost:8081/fhir';
const PAYER_API = process.env['PAYER_API_URL'] ?? 'http://localhost:9095';
const DEFAULT_PAYER_CLIENT_ID = 'clinic-api';
const DEFAULT_PAYER_CLIENT_SECRET = 'dev-only-clinic-api-client-secret';
const MEMBER_ID_SYSTEM =
  'http://praxishealthplan.example/fhir/identifier/member-id';
const PAYER_ORGANIZATION_SYSTEM =
  'http://praxishealthplan.example/fhir/identifier/organization';
const INSURANCE_PLAN_SYSTEM =
  'http://praxishealthplan.example/fhir/identifier/insurance-plan';
const IDENTIFIER_TYPE_SYSTEM = 'http://terminology.hl7.org/CodeSystem/v2-0203';
const US_CORE = 'http://hl7.org/fhir/us/core/StructureDefinition';

export interface CreatedResource {
  id: string;
}

export interface CreateEncounterBody {
  patientId: string;
  visitDate: string;
  reason?: string;
  status: string;
  practitionerId?: string;
  note?: string;
}

export interface CreateVitalsBody {
  patientId: string;
  encounterId?: string;
  effectiveDate: string;
  systolicBp?: number;
  diastolicBp?: number;
  weightKg?: number;
  spo2Percent?: number;
  heartRateBpm?: number;
  temperatureCelsius?: number;
}

export interface PatientFields {
  id: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: string;
  phone?: string;
  email?: string;
  mrn?: string;
}

export interface CreateConditionBody {
  patientId: string;
  encounterId?: string;
  code: string;
  display: string;
  clinicalStatus: string;
  onsetDate: string;
}

export interface CreateMedicationBody {
  patientId: string;
  medicationName: string;
  status: string;
  dosageText?: string;
  startDate: string;
}

export interface CreateAppointmentBody {
  patientId: string;
  practitionerId?: string;
  start: string;
  end: string;
  description?: string;
  status: string;
}

export interface CreateServiceRequestBody {
  patientId: string;
  encounterId?: string;
  practitionerId?: string;
  display: string;
  codeSystem?: string;
  code?: string;
  category: string;
  status: string;
  priority: string;
  authoredOn: string;
}

export interface CreateDiagnosticReportBody {
  patientId: string;
  encounterId?: string;
  serviceRequestId?: string;
  title: string;
  status: string;
  issued: string;
  conclusion?: string;
  resultIds?: string[];
}

export interface CreateCarePlanBody {
  patientId: string;
  conditionIds?: string[];
  title: string;
  status: string;
  periodStart: string;
  goalIds?: string[];
}

export interface CreateGoalBody {
  patientId: string;
  description: string;
  status: string;
  targetMeasureCode?: string;
  targetMeasureDisplay?: string;
  targetValue?: number;
  targetUnit?: string;
  targetDate?: string;
}

export interface CreateCoverageBody {
  patientId: string;
  payerId: string;
  payerName: string;
  memberId: string;
  planName?: string;
  status: string;
  periodStart: string;
  periodEnd?: string;
  relationship?: string;
}

export interface PayerOrganizationFields {
  organizationId: string;
  name: string;
}

export interface InsurancePlanFields {
  planId: string;
  name: string;
  planType: string;
  organizationId: string;
  periodStart: string;
}

export interface InsurancePlan {
  id: string;
  planId: string;
  name: string;
}

export interface ProviderPatient {
  id: string;
  name?: Array<{ family?: string; given?: string[] }>;
  birthDate?: string;
  gender?: string;
  telecom?: Array<{ system?: string; value?: string }>;
}

export interface MemberCoverageFields {
  memberPatientId: string;
  organizationId: string;
  payerName: string;
  memberId: string;
  plan: InsurancePlan;
  status: string;
  periodStart: string;
  periodEnd?: string;
}

interface FhirIdentifier {
  system: string;
  value: string;
}

interface FhirResource {
  resourceType: string;
  [element: string]: unknown;
}

interface FhirSearchBundle {
  entry?: Array<{ resource: { id: string } }>;
}

interface InsurancePlanBundle {
  entry?: Array<{
    resource: { id: string; identifier?: FhirIdentifier[] };
  }>;
}

const memberIdentifier = (memberId: string) => ({
  type: {
    coding: [
      { system: IDENTIFIER_TYPE_SYSTEM, code: 'MB', display: 'Member Number' },
    ],
  },
  system: MEMBER_ID_SYSTEM,
  value: memberId,
});

class ApiClient {
  private accessToken: string | null = null;

  // Node's fetch reuses pooled connections that the server may have already
  // closed, throwing "TypeError: fetch failed". A retry opens a fresh one.
  private async fetchWithRetry(
    url: string,
    init: RequestInit,
    retries = 2,
  ): Promise<Response> {
    for (let attempt = 0; ; attempt++) {
      try {
        return await fetch(url, init);
      } catch (e) {
        if (attempt >= retries || !(e instanceof TypeError)) throw e;
      }
    }
  }

  private async post<T>(
    url: string,
    body: unknown,
    contentType = 'application/json',
  ): Promise<T> {
    const headers: Record<string, string> = { 'Content-Type': contentType };
    if (this.accessToken) {
      headers['Authorization'] = `Bearer ${this.accessToken}`;
    }
    const response = await this.fetchWithRetry(url, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(
        `POST ${url} failed [${response.status}]: ${text.slice(0, 300)}`,
      );
    }
    return response.json() as Promise<T>;
  }

  private async put(url: string, body: object): Promise<void> {
    const response = await this.fetchWithRetry(url, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/fhir+json' },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(
        `PUT ${url} failed [${response.status}]: ${text.slice(0, 300)}`,
      );
    }
  }

  private async searchFhirIds(
    resourceType: string,
    query?: string,
  ): Promise<string[]> {
    const qs = query ? `&${query}` : '';
    // _count=1000 silently returns zero entries for several resource types when
    // combined with the synthetic `patient` search param (HAPI FHIR v7.4.0 quirk,
    // confirmed broken at exactly 1000, fine at 999 and below). Seed patients
    // never have anywhere near 100 resources of one type.
    const response = await this.fetchWithRetry(
      `${HAPI_FHIR}/fhir/${resourceType}?_count=100&_elements=id${qs}`,
      {
        headers: { Accept: 'application/fhir+json' },
      },
    );
    if (!response.ok)
      throw new Error(`GET /fhir/${resourceType} failed [${response.status}]`);
    const bundle = (await response.json()) as FhirSearchBundle;
    return bundle.entry?.map((e) => e.resource.id).filter(Boolean) ?? [];
  }

  private async deleteFhirById(
    resourceType: string,
    id: string,
  ): Promise<void> {
    const response = await this.fetchWithRetry(
      `${HAPI_FHIR}/fhir/${resourceType}/${id}`,
      { method: 'DELETE' },
    );
    if (!response.ok && response.status !== 404) {
      throw new Error(
        `DELETE /fhir/${resourceType}/${id} failed [${response.status}]`,
      );
    }
  }

  async login(
    username = process.env['SEED_USERNAME'] ?? DEFAULT_SEED_USERNAME,
    password = process.env['SEED_PASSWORD'] ?? DEFAULT_SEED_PASSWORD,
  ): Promise<void> {
    const url = `${SPRING_API}/api/auth/login`;
    const response = await this.fetchWithRetry(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(
        `Login as ${username} failed [${response.status}]: ${text.slice(0, 300)}`,
      );
    }
    const tokens = (await response.json()) as { accessToken: string };
    this.accessToken = tokens.accessToken;
  }

  async payerApiToken(
    clientId = process.env['PAYER_CLIENT_ID'] ?? DEFAULT_PAYER_CLIENT_ID,
    clientSecret = process.env['PAYER_CLIENT_SECRET'] ??
      DEFAULT_PAYER_CLIENT_SECRET,
  ): Promise<string> {
    const url = `${PAYER_API}/oauth/token`;
    const basic = Buffer.from(`${clientId}:${clientSecret}`).toString('base64');
    const response = await this.fetchWithRetry(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basic}`,
      },
      body: 'grant_type=client_credentials',
    });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(
        `Token request for ${clientId} failed [${response.status}]: ${text.slice(0, 300)}`,
      );
    }
    const token = (await response.json()) as { access_token: string };
    return token.access_token;
  }

  private async ensurePayerResource(
    resource: FhirResource,
    identifier: FhirIdentifier,
  ): Promise<CreatedResource> {
    const url = `${PAYER_FHIR}/${resource.resourceType}`;
    const response = await this.fetchWithRetry(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/fhir+json',
        Accept: 'application/fhir+json',
        'If-None-Exist': `identifier=${encodeURIComponent(`${identifier.system}|${identifier.value}`)}`,
      },
      body: JSON.stringify(resource),
    });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(
        `POST ${url} failed [${response.status}]: ${text.slice(0, 300)}`,
      );
    }
    const created = (await response.json()) as CreatedResource;
    return { id: created.id };
  }

  async getProviderPatient(id: string): Promise<ProviderPatient> {
    const response = await this.fetchWithRetry(
      `${HAPI_FHIR}/fhir/Patient/${id}`,
      { headers: { Accept: 'application/fhir+json' } },
    );
    if (!response.ok)
      throw new Error(`GET /fhir/Patient/${id} failed [${response.status}]`);
    return response.json() as Promise<ProviderPatient>;
  }

  ensurePayerOrganization(
    fields: PayerOrganizationFields,
  ): Promise<CreatedResource> {
    const identifier = {
      system: PAYER_ORGANIZATION_SYSTEM,
      value: fields.organizationId,
    };
    return this.ensurePayerResource(
      {
        resourceType: 'Organization',
        meta: { profile: [`${US_CORE}/us-core-organization`] },
        identifier: [identifier],
        active: true,
        type: [
          {
            coding: [
              {
                system:
                  'http://terminology.hl7.org/CodeSystem/organization-type',
                code: 'pay',
                display: 'Payer',
              },
            ],
          },
        ],
        name: fields.name,
      },
      identifier,
    );
  }

  ensureInsurancePlan(fields: InsurancePlanFields): Promise<CreatedResource> {
    const identifier = { system: INSURANCE_PLAN_SYSTEM, value: fields.planId };
    const payer = { reference: `Organization/${fields.organizationId}` };
    return this.ensurePayerResource(
      {
        resourceType: 'InsurancePlan',
        identifier: [identifier],
        status: 'active',
        type: [
          {
            coding: [
              {
                system:
                  'http://terminology.hl7.org/CodeSystem/insurance-plan-type',
                code: 'medical',
                display: 'Medical',
              },
            ],
          },
        ],
        name: fields.name,
        period: { start: fields.periodStart },
        ownedBy: payer,
        administeredBy: payer,
        plan: [{ type: { text: fields.planType } }],
      },
      identifier,
    );
  }

  async findInsurancePlan(name: string): Promise<InsurancePlan> {
    const query = `name:exact=${encodeURIComponent(name)}`;
    const response = await this.fetchWithRetry(
      `${PAYER_FHIR}/InsurancePlan?${query}`,
      { headers: { Accept: 'application/fhir+json' } },
    );
    if (!response.ok)
      throw new Error(`GET /fhir/InsurancePlan failed [${response.status}]`);
    const bundle = (await response.json()) as InsurancePlanBundle;
    for (const { resource } of bundle.entry ?? []) {
      const planId = resource.identifier?.find(
        (identifier) => identifier.system === INSURANCE_PLAN_SYSTEM,
      )?.value;
      if (planId) return { id: resource.id, planId, name };
    }
    throw new Error(
      `Insurance plan "${name}" is not in the payer HAPI; run payer-plans.feature first`,
    );
  }

  ensureMemberPatient(
    memberId: string,
    patient: ProviderPatient,
  ): Promise<CreatedResource> {
    const identifier = memberIdentifier(memberId);
    return this.ensurePayerResource(
      {
        resourceType: 'Patient',
        meta: { profile: [`${US_CORE}/us-core-patient`] },
        identifier: [identifier],
        active: true,
        name: patient.name,
        birthDate: patient.birthDate,
        gender: patient.gender,
        ...(patient.telecom && { telecom: patient.telecom }),
      },
      identifier,
    );
  }

  ensureMemberCoverage(fields: MemberCoverageFields): Promise<CreatedResource> {
    const identifier = memberIdentifier(fields.memberId);
    return this.ensurePayerResource(
      {
        resourceType: 'Coverage',
        meta: { profile: [`${US_CORE}/us-core-coverage`] },
        identifier: [identifier],
        status: fields.status,
        subscriberId: fields.memberId,
        beneficiary: { reference: `Patient/${fields.memberPatientId}` },
        relationship: {
          coding: [
            {
              system:
                'http://terminology.hl7.org/CodeSystem/subscriber-relationship',
              code: 'self',
            },
          ],
        },
        period: {
          start: fields.periodStart,
          ...(fields.periodEnd && { end: fields.periodEnd }),
        },
        payor: [
          {
            reference: `Organization/${fields.organizationId}`,
            display: fields.payerName,
          },
        ],
        class: [
          {
            type: {
              coding: [
                {
                  system:
                    'http://terminology.hl7.org/CodeSystem/coverage-class',
                  code: 'plan',
                },
              ],
            },
            value: fields.plan.planId,
            name: fields.plan.name,
          },
        ],
      },
      identifier,
    );
  }

  async cascadeDeletePatient(id: string): Promise<void> {
    // Order matters: delete referencing resources before the resources they reference.
    // Coverage → Patient;
    // Flag (clinician alert) → Observation; CarePlan → Goal, Condition;
    // DiagnosticReport → ServiceRequest, Observation;
    // ServiceRequest, Observation → Encounter; everything → Patient.

    const coverageIds = await this.searchFhirIds('Coverage', `patient=${id}`);
    await Promise.all(
      coverageIds.map((cid) => this.deleteFhirById('Coverage', cid)),
    );

    const flagIds = await this.searchFhirIds('Flag', `patient=${id}`);
    await Promise.all(flagIds.map((fid) => this.deleteFhirById('Flag', fid)));

    const cpIds = await this.searchFhirIds('CarePlan', `patient=${id}`);
    await Promise.all(cpIds.map((cid) => this.deleteFhirById('CarePlan', cid)));

    const goalIds = await this.searchFhirIds('Goal', `patient=${id}`);
    await Promise.all(goalIds.map((gid) => this.deleteFhirById('Goal', gid)));

    const rptIds = await this.searchFhirIds(
      'DiagnosticReport',
      `patient=${id}`,
    );
    await Promise.all(
      rptIds.map((rid) => this.deleteFhirById('DiagnosticReport', rid)),
    );

    const srIds = await this.searchFhirIds('ServiceRequest', `patient=${id}`);
    await Promise.all(
      srIds.map((sid) => this.deleteFhirById('ServiceRequest', sid)),
    );

    const apptIds = await this.searchFhirIds('Appointment', `patient=${id}`);
    await Promise.all(
      apptIds.map((aid) => this.deleteFhirById('Appointment', aid)),
    );

    const medIds = await this.searchFhirIds(
      'MedicationStatement',
      `patient=${id}`,
    );
    await Promise.all(
      medIds.map((mid) => this.deleteFhirById('MedicationStatement', mid)),
    );

    const condIds = await this.searchFhirIds('Condition', `patient=${id}`);
    await Promise.all(
      condIds.map((cid) => this.deleteFhirById('Condition', cid)),
    );

    const obsIds = await this.searchFhirIds('Observation', `patient=${id}`);
    await Promise.all(
      obsIds.map((oid) => this.deleteFhirById('Observation', oid)),
    );

    const encIds = await this.searchFhirIds('Encounter', `patient=${id}`);
    await Promise.all(
      encIds.map((eid) => this.deleteFhirById('Encounter', eid)),
    );

    await this.deleteFhirById('Patient', id);
  }

  async upsertPatient(fields: PatientFields): Promise<void> {
    const telecom = [];
    if (fields.phone) telecom.push({ system: 'phone', value: fields.phone });
    if (fields.email) telecom.push({ system: 'email', value: fields.email });

    await this.put(`${HAPI_FHIR}/fhir/Patient/${fields.id}`, {
      resourceType: 'Patient',
      id: fields.id,
      active: true,
      name: [{ family: fields.lastName, given: [fields.firstName] }],
      birthDate: fields.dateOfBirth,
      gender: fields.gender,
      ...(fields.mrn && {
        identifier: [
          {
            use: 'usual',
            type: {
              coding: [
                {
                  system: 'http://terminology.hl7.org/CodeSystem/v2-0203',
                  code: 'MR',
                },
              ],
            },
            system: MRN_SYSTEM,
            value: fields.mrn,
          },
        ],
      }),
      ...(telecom.length > 0 && { telecom }),
    });
  }

  async upsertPractitioner(id: string): Promise<void> {
    await this.put(`${HAPI_FHIR}/fhir/Practitioner/${id}`, {
      resourceType: 'Practitioner',
      id,
      active: true,
      name: [{ family: 'Patel', given: ['Aisha'], prefix: ['Dr.'] }],
      gender: 'female',
      qualification: [{ code: { text: 'Internal Medicine' } }],
    });
  }

  async upsertOrganization(id: string): Promise<void> {
    await this.put(`${HAPI_FHIR}/fhir/Organization/${id}`, {
      resourceType: 'Organization',
      id,
      active: true,
      name: 'Seed Dev Clinic',
      type: [
        {
          coding: [
            {
              system: 'http://terminology.hl7.org/CodeSystem/organization-type',
              code: 'prov',
              display: 'Healthcare Provider',
            },
          ],
        },
      ],
    });
  }

  createEncounter(body: CreateEncounterBody): Promise<CreatedResource> {
    return this.post<CreatedResource>(`${SPRING_API}/api/encounters`, body);
  }

  recordVitals(body: CreateVitalsBody): Promise<CreatedResource[]> {
    return this.post<CreatedResource[]>(`${SPRING_API}/api/vitals`, body);
  }

  createCondition(body: CreateConditionBody): Promise<CreatedResource> {
    return this.post<CreatedResource>(`${SPRING_API}/api/conditions`, body);
  }

  createMedication(body: CreateMedicationBody): Promise<CreatedResource> {
    return this.post<CreatedResource>(`${SPRING_API}/api/medications`, body);
  }

  createAppointment(body: CreateAppointmentBody): Promise<CreatedResource> {
    return this.post<CreatedResource>(`${SPRING_API}/api/appointments`, body);
  }

  createServiceRequest(
    body: CreateServiceRequestBody,
  ): Promise<CreatedResource> {
    return this.post<CreatedResource>(
      `${SPRING_API}/api/service-requests`,
      body,
    );
  }

  createDiagnosticReport(
    body: CreateDiagnosticReportBody,
  ): Promise<CreatedResource> {
    return this.post<CreatedResource>(
      `${SPRING_API}/api/diagnostic-reports`,
      body,
    );
  }

  createCarePlan(body: CreateCarePlanBody): Promise<CreatedResource> {
    return this.post<CreatedResource>(`${SPRING_API}/api/care-plans`, body);
  }

  createGoal(body: CreateGoalBody): Promise<CreatedResource> {
    return this.post<CreatedResource>(`${SPRING_API}/api/goals`, body);
  }

  async ensureCoverage(body: CreateCoverageBody): Promise<CreatedResource> {
    const [existingId] = await this.searchFhirIds(
      'Coverage',
      `identifier=${encodeURIComponent(`${MEMBER_ID_SYSTEM}|${body.memberId}`)}`,
    );
    if (existingId) return { id: existingId };
    return this.post<CreatedResource>(`${SPRING_API}/api/coverages`, body);
  }
}

export const apiClient = new ApiClient();
