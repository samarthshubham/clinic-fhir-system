import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { PatientService } from './patient.service';
import { PatientListItem } from '@features/patients/models/patient-list-item.model';
import {
  PageResponse,
  PatientResponse,
} from '@features/patients/models/patient-response.model';

function patient(overrides: Partial<PatientResponse> = {}): PatientResponse {
  return {
    id: 'p1',
    firstName: 'Jane Q',
    lastName: 'Doe',
    dateOfBirth: '1980-01-02',
    gender: 'female',
    phone: null,
    email: null,
    active: true,
    ...overrides,
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
    function load(page: PageResponse<PatientResponse>): PatientListItem[] {
      let result: PatientListItem[] = [];
      service.getPatients().subscribe((r) => (result = r));
      http.expectOne('/api/patients').flush(page);
      return result;
    }

    it('should map each patient response to a list item', () => {
      const result = load({ items: [patient()], total: 1 });

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

    it('should mark inactive patients', () => {
      const [item] = load({ items: [patient({ active: false })], total: 1 });

      expect(item.status).toBe('inactive');
    });

    it('should treat a missing active flag as active', () => {
      const [item] = load({ items: [patient({ active: null })], total: 1 });

      expect(item.status).toBe('active');
    });

    it('should fall back to defaults when fields are missing', () => {
      const [item] = load({
        items: [
          patient({
            firstName: null,
            lastName: null,
            dateOfBirth: null,
            gender: null,
          }),
        ],
        total: 1,
      });

      expect(item).toEqual({
        id: 'p1',
        name: 'Unknown',
        gender: 'unknown',
        dob: '',
        status: 'active',
        lastEncounter: undefined,
      });
    });

    it('should use whichever name part is present', () => {
      const [item] = load({
        items: [patient({ firstName: null, lastName: 'Roe' })],
        total: 1,
      });

      expect(item.name).toBe('Roe');
    });

    it('should return an empty list for an empty page', () => {
      expect(load({ items: [], total: 0 })).toEqual([]);
    });
  });
});
