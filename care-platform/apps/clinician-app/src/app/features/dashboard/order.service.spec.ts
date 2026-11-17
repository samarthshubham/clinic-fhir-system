import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { OrderService } from './order.service';
import { ServiceRequestRequest } from './dashboard.model';

const request: ServiceRequestRequest = {
  patientId: 'p1',
  encounterId: null,
  practitionerId: 'dr1',
  display: 'MRI lumbar spine without contrast',
  codeSystem: 'http://www.ama-assn.org/go/cpt',
  code: '72148',
  category: 'imaging',
  status: 'active',
  priority: 'routine',
  authoredOn: '2026-11-16',
};

describe('OrderService', () => {
  let service: OrderService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OrderService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('should post the order and return the saved order with its CRD answer', () => {
    let result: unknown;

    service.placeOrder(request).subscribe((r) => (result = r));

    const req = http.expectOne('/api/service-requests');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    const saved = { ...request, id: 'sr1', coverage: null, cards: [] };
    req.flush(saved);
    expect(result).toEqual(saved);
  });
});
