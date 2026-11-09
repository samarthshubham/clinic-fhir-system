import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { CoverageService } from './coverage.service';
import { CoverageRequest } from './dashboard.model';

const request: CoverageRequest = {
  patientId: 'p1',
  payerId: 'praxis-health-plan',
  payerName: 'Praxis Health Plan',
  memberId: 'PHP-10001',
  planName: 'Praxis Gold PPO',
  status: 'active',
  periodStart: '2026-01-01',
  periodEnd: null,
  relationship: 'self',
};

describe('CoverageService', () => {
  let service: CoverageService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CoverageService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('should post a new coverage', () => {
    let result: unknown;

    service.createCoverage(request).subscribe((r) => (result = r));

    const req = http.expectOne('/api/coverages');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ ...request, id: 'cov1' });
    expect(result).toEqual({ ...request, id: 'cov1' });
  });

  it('should put an edited coverage by id', () => {
    service.updateCoverage('cov1', request).subscribe();

    const req = http.expectOne('/api/coverages/cov1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush({ ...request, id: 'cov1' });
  });
});
