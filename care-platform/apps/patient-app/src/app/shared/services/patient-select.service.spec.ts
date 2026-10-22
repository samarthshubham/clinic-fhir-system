import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { AuthService, AuthUser } from '@care-platform/auth';
import { PatientSelectService } from './patient-select.service';

describe('PatientSelectService', () => {
  it("should follow the signed-in patient's id", () => {
    const user = signal<AuthUser | null>(null);
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { user } }],
    });
    const service = TestBed.inject(PatientSelectService);

    expect(service.selectedId()).toBeNull();

    user.set({
      username: 'priya',
      name: 'Priya Nair',
      title: null,
      practitionerId: null,
      patientId: 'seed-priya-nair',
      roles: ['PATIENT'],
    });

    expect(service.selectedId()).toBe('seed-priya-nair');
  });
});
