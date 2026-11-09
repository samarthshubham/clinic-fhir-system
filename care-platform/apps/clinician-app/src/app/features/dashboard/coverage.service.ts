import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CoverageRequest, CoverageResponse } from './dashboard.model';

@Injectable({ providedIn: 'root' })
export class CoverageService {
  private readonly http = inject(HttpClient);

  createCoverage(request: CoverageRequest): Observable<CoverageResponse> {
    return this.http.post<CoverageResponse>('/api/coverages', request);
  }

  updateCoverage(
    id: string,
    request: CoverageRequest,
  ): Observable<CoverageResponse> {
    return this.http.put<CoverageResponse>(`/api/coverages/${id}`, request);
  }
}
