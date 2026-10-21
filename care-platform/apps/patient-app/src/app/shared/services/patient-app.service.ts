import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  PatientListItem,
  SnapshotResponse,
  DashboardResponse,
  TrendsApiResponse,
  AppointmentItem,
  PostVitalsRequest,
  PageResponse,
  PatientResponse,
} from '../models/patient-app.models';

function hashHue(str: string): number {
  let h = 0;
  for (let i = 0; i < str.length; i++) {
    h = (Math.imul(31, h) + str.charCodeAt(i)) | 0;
  }
  return ((h % 360) + 360) % 360;
}

@Injectable({ providedIn: 'root' })
export class PatientAppService {
  private readonly http = inject(HttpClient);

  getPatients(): Observable<PatientListItem[]> {
    return this.http.get<PageResponse<PatientResponse>>('/api/patients').pipe(
      map((page) =>
        page.items.map(
          (p) =>
            ({
              id: p.id,
              firstName: p.firstName ?? '',
              lastName: p.lastName ?? '',
              dob: p.dateOfBirth ?? '',
              gender: p.gender ?? '',
              avatarHue: hashHue(p.id),
            }) satisfies PatientListItem,
        ),
      ),
    );
  }

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
    return this.http.get<any>(`/api/appointments?patientId=${patientId}`).pipe(
      map((bundle) => {
        const entries: any[] = bundle?.entry ?? [];
        const now = new Date().toISOString();
        return entries
          .map((e: any) => e.resource)
          .filter(
            (r: any) => r?.resourceType === 'Appointment' && r.start > now,
          )
          .map((a: any) => ({
            id: a.id,
            start: a.start,
            description: a.description ?? '',
            status: a.status ?? '',
          }))
          .sort((a, b) => a.start.localeCompare(b.start));
      }),
    );
  }
}
