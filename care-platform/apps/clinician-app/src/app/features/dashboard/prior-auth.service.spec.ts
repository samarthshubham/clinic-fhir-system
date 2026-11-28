import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PriorAuthService } from './prior-auth.service';

describe('PriorAuthService', () => {
  it('gets authorization status by patient and submits by order', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);
    const service = TestBed.inject(PriorAuthService);
    service.list('p1').subscribe();
    const list = http.expectOne('/api/prior-auths?patientId=p1');
    expect(list.request.method).toBe('GET');
    list.flush([]);

    const body = {
      orderId: 'o1',
      service: null,
      placeOfService: '11',
      serviceCategory: '1',
      diagnoses: [],
    };
    service.submit(body).subscribe();
    const submit = http.expectOne('/api/prior-auths');
    expect(submit.request.method).toBe('POST');
    expect(submit.request.body).toEqual(body);
    submit.flush({ orderId: 'o1', status: 'PENDED' });
    http.verify();
  });
});
