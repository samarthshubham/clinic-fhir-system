import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, of, shareReplay, startWith, switchMap } from 'rxjs';
import { PatientAppService } from '@shared/services/patient-app.service';
import { PatientSelectService } from '@shared/services/patient-select.service';
import { CpBpChartComponent } from './components/cp-bp-chart/cp-bp-chart.component';
import { CpLineChartComponent } from './components/cp-line-chart/cp-line-chart.component';
import {
  DataPoint,
  TrendsApiResponse,
} from '@shared/models/patient-app.models';

type Period = '7d' | '30d' | '90d';

@Component({
  selector: 'cp-vitals',
  templateUrl: './vitals.component.html',
  styleUrl: './vitals.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CpBpChartComponent, CpLineChartComponent],
})
export class VitalsComponent {
  private readonly svc = inject(PatientAppService);
  private readonly selectSvc = inject(PatientSelectService);
  private readonly router = inject(Router);

  readonly patientId = this.selectSvc.selectedId()!;
  readonly period = signal<Period>('30d');

  private readonly trends$ = toObservable(this.period).pipe(
    switchMap((p) =>
      this.svc.getTrends(this.patientId, 'bp,spo2,weight', p).pipe(
        catchError(() => of(null as TrendsApiResponse | null)),
        startWith(null),
      ),
    ),
    shareReplay(1),
  );

  readonly trends = toSignal(this.trends$, { initialValue: null });
  readonly isLoading = computed(() => this.trends() === null);

  private readonly snapshot$ = this.svc.getSnapshot(this.patientId).pipe(
    catchError(() => of(null)),
    startWith(null),
    shareReplay(1),
  );
  readonly snapshot = toSignal(this.snapshot$, { initialValue: null });

  readonly sysSeries = computed(
    (): DataPoint[] => this.trends()?.series?.bp?.systolic ?? [],
  );
  readonly diaSeries = computed(
    (): DataPoint[] => this.trends()?.series?.bp?.diastolic ?? [],
  );
  readonly spo2Series = computed(
    (): DataPoint[] => this.trends()?.series?.spo2?.values ?? [],
  );
  readonly weightSeries = computed(
    (): DataPoint[] => this.trends()?.series?.weight?.values ?? [],
  );

  readonly latestVitals = computed(() => this.snapshot()?.latestVitals ?? null);

  readonly periods: { value: Period; label: string }[] = [
    { value: '7d', label: '1 week' },
    { value: '30d', label: '1 month' },
    { value: '90d', label: '3 months' },
  ];

  goRecord(): void {
    this.router.navigate(['/vitals/record']);
  }

  relativeDate(iso: string | undefined): string {
    if (!iso) return '';
    const ref = new Date();
    const d = new Date(iso);
    const days = Math.floor((ref.getTime() - d.getTime()) / 86400000);
    if (days <= 0) return 'Today';
    if (days === 1) return 'Yesterday';
    if (days < 7) return `${days} days ago`;
    if (days < 30) return `${Math.floor(days / 7)} wk ago`;
    const months = [
      'Jan',
      'Feb',
      'Mar',
      'Apr',
      'May',
      'Jun',
      'Jul',
      'Aug',
      'Sep',
      'Oct',
      'Nov',
      'Dec',
    ];
    return `${months[d.getMonth()]} ${d.getDate()}`;
  }
}
