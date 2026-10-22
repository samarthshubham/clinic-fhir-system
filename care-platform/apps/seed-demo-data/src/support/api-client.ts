const SPRING_API = 'http://localhost:9090';
const HAPI_FHIR = 'http://localhost:8080';
const DEFAULT_SEED_USERNAME = 'clinician';
const DEFAULT_SEED_PASSWORD = 'Clinician#2026';

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
  code: string;
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

interface FhirSearchBundle {
  entry?: Array<{ resource: { id: string } }>;
}

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

  async cascadeDeletePatient(id: string): Promise<void> {
    // Order matters: delete referencing resources before the resources they reference.
    // CarePlan → Goal, Condition; DiagnosticReport → ServiceRequest, Observation;
    // ServiceRequest, Observation → Encounter; everything → Patient.

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
}

export const apiClient = new ApiClient();
