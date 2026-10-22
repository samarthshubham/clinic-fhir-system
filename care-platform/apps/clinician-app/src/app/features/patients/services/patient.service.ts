import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { PatientListItem } from '@features/patients/models/patient-list-item.model';
import {
  PageResponse,
  PatientResponse,
} from '@features/patients/models/patient-response.model';

@Injectable({ providedIn: 'root' })
export class PatientService {
  private readonly http = inject(HttpClient);

  getPatients(): Observable<PatientListItem[]> {
    return this.http
      .get<PageResponse<PatientResponse>>('/api/patients')
      .pipe(map((page) => page.items.map((p) => this.mapPatient(p))));
  }

  getPatient(id: string): Observable<PatientResponse> {
    return this.http.get<PatientResponse>(`/api/patients/${id}`);
  }

  private mapPatient(p: PatientResponse): PatientListItem {
    const name = [p.firstName, p.lastName].filter(Boolean).join(' ');
    return {
      id: p.id,
      name: name || 'Unknown',
      gender: (p.gender ?? 'unknown') as PatientListItem['gender'],
      dob: p.dateOfBirth ?? '',
      status: p.active === false ? 'inactive' : 'active',
      lastEncounter: undefined,
    };
  }
}
