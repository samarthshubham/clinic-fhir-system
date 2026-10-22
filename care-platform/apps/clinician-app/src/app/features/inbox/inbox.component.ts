import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import {
  catchError,
  forkJoin,
  map,
  mergeMap,
  Observable,
  of,
  Subject,
  switchMap,
} from 'rxjs';
import { AlertResponse } from '@core/alert.models';
import { AlertService } from '@core/alert.service';
import { PatientService } from '@features/patients/services/patient.service';

export interface InboxRow {
  alert: AlertResponse;
  patientName: string;
}

@Component({
  selector: 'cp-inbox',
  imports: [DatePipe, MatButtonModule, RouterLink],
  templateUrl: './inbox.component.html',
  styleUrl: './inbox.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InboxComponent {
  private readonly alerts = inject(AlertService);
  private readonly patients = inject(PatientService);
  private readonly acknowledge$ = new Subject<string>();

  protected readonly rows = toSignal<InboxRow[] | null | undefined>(
    this.alerts.activeAlerts$.pipe(
      switchMap((page) =>
        page === null ? of(null) : this.withNames(page.items),
      ),
    ),
    { initialValue: undefined },
  );

  constructor() {
    this.acknowledge$
      .pipe(
        mergeMap((id) =>
          this.alerts.acknowledge(id).pipe(catchError(() => of(null))),
        ),
        takeUntilDestroyed(),
      )
      .subscribe();
  }

  acknowledge(id: string): void {
    this.acknowledge$.next(id);
  }

  private withNames(alerts: AlertResponse[]): Observable<InboxRow[]> {
    const patientIds = [
      ...new Set(alerts.map((alert) => alert.patientId).filter(Boolean)),
    ] as string[];
    if (patientIds.length === 0) {
      return of(
        alerts.map((alert) => ({ alert, patientName: 'Unknown patient' })),
      );
    }
    return forkJoin(
      patientIds.map((id) =>
        this.patients.getPatient(id).pipe(
          map(
            (patient) =>
              [patient.firstName, patient.lastName].filter(Boolean).join(' ') ||
              `Patient ${id}`,
          ),
          catchError(() => of(`Patient ${id}`)),
          map((name) => [id, name] as const),
        ),
      ),
    ).pipe(
      map((entries) => new Map(entries)),
      map((names) =>
        alerts.map((alert) => ({
          alert,
          patientName: alert.patientId
            ? (names.get(alert.patientId) ?? `Patient ${alert.patientId}`)
            : 'Unknown patient',
        })),
      ),
    );
  }
}
