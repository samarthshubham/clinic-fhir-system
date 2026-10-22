import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import {
  catchError,
  distinctUntilChanged,
  filter,
  map,
  merge,
  Observable,
  of,
  pairwise,
  shareReplay,
  Subject,
  switchMap,
  tap,
  timer,
} from 'rxjs';
import { AlertPage, AlertResponse } from './alert.models';
import { AuthService } from './auth.service';

export const ALERT_POLL_MS = 30_000;

@Injectable({ providedIn: 'root' })
export class AlertService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly refresh$ = new Subject<void>();

  private readonly canSeeAlerts = computed(() =>
    this.auth.hasRole('ADMIN', 'CLINICIAN'),
  );

  readonly activeAlerts$: Observable<AlertPage | null> = toObservable(
    this.canSeeAlerts,
  ).pipe(
    distinctUntilChanged(),
    switchMap((allowed) =>
      allowed
        ? merge(timer(0, ALERT_POLL_MS), this.refresh$).pipe(
            switchMap(() => this.fetchActive()),
          )
        : of(null),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly newAlertCount$: Observable<number> = this.activeAlerts$.pipe(
    filter((page): page is AlertPage => page !== null),
    map((page) => new Set(page.items.map((alert) => alert.id))),
    pairwise(),
    map(([before, now]) => [...now].filter((id) => !before.has(id)).length),
    filter((count) => count > 0),
  );

  acknowledge(id: string): Observable<AlertResponse> {
    return this.http
      .post<AlertResponse>(`/api/alerts/${id}/acknowledge`, null)
      .pipe(tap(() => this.refresh$.next()));
  }

  private fetchActive(): Observable<AlertPage | null> {
    return this.http
      .get<AlertPage>('/api/alerts', { params: { status: 'active' } })
      .pipe(catchError(() => of(null)));
  }
}
