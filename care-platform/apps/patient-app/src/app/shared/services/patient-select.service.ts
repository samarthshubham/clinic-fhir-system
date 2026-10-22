import { Injectable, computed, inject } from '@angular/core';
import { AuthService } from '@care-platform/auth';

@Injectable({ providedIn: 'root' })
export class PatientSelectService {
  private readonly auth = inject(AuthService);

  readonly selectedId = computed(() => this.auth.user()?.patientId ?? null);
}
