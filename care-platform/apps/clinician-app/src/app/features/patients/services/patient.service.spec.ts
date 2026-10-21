import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { PatientService } from './patient.service';
import { FhirBundle } from '../models/fhir-bundle.model';
import { PatientListItem } from '../models/patient-list-item.model';
import { PatientSummary } from '../models/patient-detail.model';

function observation(
  id: string,
  code: string,
  date: string | undefined,
  value: number | undefined,
  unit = 'u',
) {
  return {
    resourceType: 'Observation',
    id,
    code: { coding: [{ system: 'http://loinc.org', code }] },
    effectiveDateTime: date,
    valueQuantity: value === undefined ? {} : { value, unit },
  };
}

describe('PatientService', () => {
  let service: PatientService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PatientService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  describe('getPatients', () => {
    function load(bundle: FhirBundle): PatientListItem[] {
      let result: PatientListItem[] = [];
      service.getPatients().subscribe((r) => (result = r));
      http.expectOne('/api/patients').flush(bundle);
      return result;
    }

    it('should map patients and skip other resources', () => {
      const result = load({
        resourceType: 'Bundle',
        entry: [
          {
            resource: {
              resourceType: 'Patient',
              id: 'p1',
              name: [{ given: ['Jane', 'Q'], family: 'Doe' }],
              gender: 'female',
              birthDate: '1980-01-02',
            } as never,
          },
          { resource: { resourceType: 'Encounter' } },
          {},
        ],
      });

      expect(result).toEqual([
        {
          id: 'p1',
          name: 'Jane Q Doe',
          gender: 'female',
          dob: '1980-01-02',
          status: 'active',
          lastEncounter: undefined,
        },
      ]);
    });

    it('should prefer the name text and mark inactive patients', () => {
      const [patient] = load({
        resourceType: 'Bundle',
        entry: [
          {
            resource: {
              resourceType: 'Patient',
              id: 'p2',
              active: false,
              name: [{ text: 'Dr. John Roe', given: ['John'], family: 'Roe' }],
            } as never,
          },
        ],
      });

      expect(patient.name).toBe('Dr. John Roe');
      expect(patient.status).toBe('inactive');
    });

    it('should fall back to defaults when fields are missing', () => {
      const [patient] = load({
        resourceType: 'Bundle',
        entry: [{ resource: { resourceType: 'Patient' } }],
      });

      expect(patient).toEqual({
        id: '',
        name: 'Unknown',
        gender: 'unknown',
        dob: '',
        status: 'active',
        lastEncounter: undefined,
      });
    });

    it('should return an empty list for a bundle without entries', () => {
      expect(load({ resourceType: 'Bundle' })).toEqual([]);
    });
  });

  describe('getPatientSummary', () => {
    function load(bundle: FhirBundle): PatientSummary {
      let result: PatientSummary | undefined;
      service.getPatientSummary('p1').subscribe((r) => (result = r));
      http.expectOne('/api/patients/p1/summary').flush(bundle);
      if (!result) throw new Error('no summary emitted');
      return result;
    }

    it('should map demographics, encounters, vitals and observations', () => {
      const summary = load({
        resourceType: 'Bundle',
        entry: [
          {
            resource: {
              resourceType: 'Patient',
              id: 'p1',
              name: [{ given: ['Jane'], family: 'Doe' }],
              birthDate: '1980-01-02',
              gender: 'female',
              telecom: [
                { system: 'email', value: 'jane@example.com' },
                { system: 'phone', value: '555-0100' },
              ],
              identifier: [
                { type: { coding: [{ code: 'SS' }] }, value: '000-00' },
                { type: { coding: [{ code: 'MR' }] }, value: 'MRN-9' },
              ],
              address: [
                {
                  line: ['1 Main St', 'Apt 2'],
                  city: 'Springfield',
                  state: 'IL',
                  postalCode: '62701',
                },
              ],
              maritalStatus: { coding: [{ display: 'Married' }] },
              communication: [{ language: { text: 'English' } }],
            } as never,
          },
          {
            resource: {
              resourceType: 'Encounter',
              id: 'e1',
              status: 'finished',
              period: { start: '2024-01-01' },
              reasonCode: [{ text: 'Checkup' }],
            } as never,
          },
          {
            resource: {
              resourceType: 'Encounter',
              id: 'e2',
              period: { start: '2024-03-01' },
            } as never,
          },
          {
            resource: observation('o1', '8480-6', '2024-01-01', 120) as never,
          },
          {
            resource: observation('o2', '8480-6', '2024-03-01', 135) as never,
          },
          {
            resource: observation('o3', '8462-4', '2024-03-01', 85) as never,
          },
          {
            resource: observation(
              'o4',
              '29463-7',
              '2024-02-01',
              70.5,
              'kg',
            ) as never,
          },
          {
            resource: observation(
              'o5',
              '59408-5',
              '2024-02-01',
              undefined,
            ) as never,
          },
          { resource: observation('o6', '9999-9', '2024-02-01', 1) as never },
        ],
      });

      expect(summary).toMatchObject({
        id: 'p1',
        name: 'Jane Doe',
        dob: '1980-01-02',
        gender: 'female',
        mrn: 'MRN-9',
        phone: '555-0100',
        email: 'jane@example.com',
        address: {
          line: '1 Main St, Apt 2',
          city: 'Springfield',
          state: 'IL',
          postalCode: '62701',
        },
        maritalStatus: 'Married',
        language: 'English',
        status: 'active',
      });

      expect(summary.encounters).toEqual([
        { id: 'e2', date: '2024-03-01', reason: '—', status: 'unknown' },
        { id: 'e1', date: '2024-01-01', reason: 'Checkup', status: 'finished' },
      ]);

      expect(summary.vitals).toEqual({
        systolic: { value: 135, unit: 'u', date: '2024-03-01' },
        diastolic: { value: 85, unit: 'u', date: '2024-03-01' },
        weight: { value: 70.5, unit: 'kg', date: '2024-02-01' },
        spo2: undefined,
      });

      expect(summary.allObservations.map((o) => o.id)).toEqual([
        'o1',
        'o4',
        'o2',
        'o3',
      ]);
      expect(summary.allObservations[1]).toEqual({
        id: 'o4',
        loincCode: '29463-7',
        vitalType: 'weight',
        label: 'Weight',
        value: 70.5,
        unit: 'kg',
        date: '2024-02-01',
      });
    });

    it('should prefer coded text for marital status and language when present', () => {
      const summary = load({
        resourceType: 'Bundle',
        entry: [
          {
            resource: {
              resourceType: 'Patient',
              name: [{ text: 'Jane Doe' }],
              active: false,
              maritalStatus: { text: 'Single', coding: [{ display: 'S' }] },
              communication: [
                { language: { coding: [{ display: 'Spanish' }] } },
              ],
            } as never,
          },
        ],
      });

      expect(summary.name).toBe('Jane Doe');
      expect(summary.maritalStatus).toBe('Single');
      expect(summary.language).toBe('Spanish');
      expect(summary.status).toBe('inactive');
    });

    it('should return safe defaults for an empty bundle', () => {
      const summary = load({ resourceType: 'Bundle' });

      expect(summary).toEqual({
        id: '',
        name: 'Unknown',
        dob: '',
        gender: 'unknown',
        mrn: undefined,
        phone: undefined,
        email: undefined,
        address: undefined,
        maritalStatus: undefined,
        language: undefined,
        status: 'active',
        encounters: [],
        vitals: {
          systolic: undefined,
          diastolic: undefined,
          weight: undefined,
          spo2: undefined,
        },
        allObservations: [],
      });
    });

    it('should skip observations without a date', () => {
      const summary = load({
        resourceType: 'Bundle',
        entry: [
          { resource: observation('o1', '59408-5', undefined, 97) as never },
        ],
      });

      expect(summary.allObservations).toEqual([]);
      expect(summary.vitals.spo2).toEqual({ value: 97, unit: 'u', date: '' });
    });
  });
});
