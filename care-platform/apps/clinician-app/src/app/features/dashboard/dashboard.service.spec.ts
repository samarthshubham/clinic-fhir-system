import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { DashboardService } from './dashboard.service';

describe('DashboardService', () => {
  let service: DashboardService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DashboardService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('should fetch the dashboard for a patient', () => {
    const body = { warnings: [] };
    let result: unknown;

    service.getDashboard('p1').subscribe((r) => (result = r));

    const req = http.expectOne('/api/dashboard/p1');
    expect(req.request.method).toBe('GET');
    req.flush(body);
    expect(result).toEqual(body);
  });

  it('should fetch the timeline with the default limit and no cursor', () => {
    service.getTimeline('p1').subscribe();

    const req = http.expectOne((r) => r.url === '/api/patients/p1/timeline');
    expect(req.request.params.get('limit')).toBe('20');
    expect(req.request.params.has('before')).toBe(false);
    req.flush([]);
  });

  it('should pass the limit and before cursor to the timeline', () => {
    service.getTimeline('p1', 5, '2024-01-01').subscribe();

    const req = http.expectOne((r) => r.url === '/api/patients/p1/timeline');
    expect(req.request.params.get('limit')).toBe('5');
    expect(req.request.params.get('before')).toBe('2024-01-01');
    req.flush([]);
  });

  it('should fetch trends with default types and period', () => {
    service.getTrends('p1').subscribe();

    const req = http.expectOne((r) => r.url === '/api/patients/p1/trends');
    expect(req.request.params.get('type')).toBe('bp,spo2,weight');
    expect(req.request.params.get('period')).toBe('30d');
    req.flush({});
  });

  it('should fetch trends with custom types and period', () => {
    service.getTrends('p1', 'bp', '7d').subscribe();

    const req = http.expectOne((r) => r.url === '/api/patients/p1/trends');
    expect(req.request.params.get('type')).toBe('bp');
    expect(req.request.params.get('period')).toBe('7d');
    req.flush({});
  });
});
