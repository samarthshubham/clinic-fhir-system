import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  SnapshotResponse,
  DashboardResponse,
  TrendsApiResponse,
  AppointmentItem,
  PostVitalsRequest,
  AppointmentResponse,
  PageResponse,
} from '../models/patient-app.models';

@Injectable({ providedIn: 'root' })
export class PatientAppService {
  private readonly http = inject(HttpClient);

  getSnapshot(patientId: string): Observable<SnapshotResponse> {
    return this.http.get<SnapshotResponse>(
      `/api/patients/${patientId}/snapshot`,
    );
  }

  getDashboard(patientId: string): Observable<DashboardResponse> {
    return this.http.get<DashboardResponse>(`/api/dashboard/${patientId}`);
  }

  getTrends(
    patientId: string,
    types: string,
    period: string,
  ): Observable<TrendsApiResponse> {
    return this.http.get<TrendsApiResponse>(
      `/api/patients/${patientId}/trends?type=${types}&period=${period}`,
    );
  }

  postVitals(body: PostVitalsRequest): Observable<unknown> {
    return this.http.post('/api/vitals', body);
  }

  getAppointments(patientId: string): Observable<AppointmentItem[]> {
    return this.http
      .get<
        PageResponse<AppointmentResponse>
      >(`/api/appointments?patientId=${patientId}`)
      .pipe(
        map((page) => {
          const now = Date.now();
          return page.items
            .filter((a) => a.start && new Date(a.start).getTime() > now)
            .map((a) => ({
              id: a.id,
              start: a.start as string,
              description: a.description ?? '',
              status: a.status ?? '',
            }))
            .sort(
              (a, b) =>
                new Date(a.start).getTime() - new Date(b.start).getTime(),
            );
        }),
      );
  }
}
