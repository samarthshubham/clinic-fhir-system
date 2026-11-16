import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ServiceRequestRequest,
  ServiceRequestResponse,
} from './dashboard.model';

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly http = inject(HttpClient);

  placeOrder(
    request: ServiceRequestRequest,
  ): Observable<ServiceRequestResponse> {
    return this.http.post<ServiceRequestResponse>(
      '/api/service-requests',
      request,
    );
  }
}
