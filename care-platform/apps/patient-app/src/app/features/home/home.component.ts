import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of, shareReplay, startWith } from 'rxjs';
import { PatientAppService } from '@shared/services/patient-app.service';
import { PatientSelectService } from '@shared/services/patient-select.service';
import { CpAvatarComponent } from '@shared/components/cp-avatar/cp-avatar.component';
import {
  CpVitalSnapshotCardComponent,
  VitalStatus,
} from './components/cp-vital-snapshot-card/cp-vital-snapshot-card.component';
import {
  DataPoint,
  DashboardResponse,
  AlertItem,
} from '@shared/models/patient-app.models';

@Component({
  selector: 'cp-home',
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CpAvatarComponent, CpVitalSnapshotCardComponent, DatePipe],
})
export class HomeComponent {
  private readonly svc = inject(PatientAppService);
  private readonly selectSvc = inject(PatientSelectService);
  private readonly router = inject(Router);

  readonly patientId = this.selectSvc.selectedId()!;

  private readonly dashboard$ = this.svc.getDashboard(this.patientId).pipe(
    catchError(() => of(null as DashboardResponse | null)),
    startWith(null),
    shareReplay(1),
  );

  private readonly trends$ = this.svc
    .getTrends(this.patientId, 'bp,spo2,weight', '30d')
    .pipe(
      catchError(() => of(null)),
      startWith(null),
      shareReplay(1),
    );

  private readonly appointments$ = this.svc
    .getAppointments(this.patientId)
    .pipe(
      catchError(() => of([])),
      startWith(null),
    );

  readonly dashboard = toSignal(this.dashboard$, { initialValue: null });
  readonly trends = toSignal(this.trends$, { initialValue: null });
  readonly appointments = toSignal(this.appointments$, { initialValue: null });

  readonly isLoading = computed(() => this.dashboard() === null);

  readonly patientInfo = computed(() => this.dashboard()?.patient ?? null);

  readonly snapshot = computed(() => this.dashboard()?.snapshot ?? null);

  readonly latestVitals = computed(() => this.snapshot()?.latestVitals ?? null);

  readonly alerts = computed(() => this.snapshot()?.alerts ?? []);

  readonly overallStatus = computed((): 'ok' | 'warn' | 'crit' => {
    const a = this.alerts();
    if (a.some((x: AlertItem) => x.severity === 'critical')) return 'crit';
    if (a.some((x: AlertItem) => x.severity === 'warning')) return 'warn';
    return 'ok';
  });

  readonly greeting = computed(() => {
    const h = new Date().getHours();
    if (h < 12) return 'Good morning';
    if (h < 18) return 'Good afternoon';
    return 'Good evening';
  });

  readonly avatarHue = computed(() => {
    const id = this.patientId;
    let h = 0;
    for (let i = 0; i < id.length; i++) {
      h = (Math.imul(31, h) + id.charCodeAt(i)) | 0;
    }
    return ((h % 360) + 360) % 360;
  });

  readonly bpStatus = computed((): VitalStatus => {
    const lv = this.latestVitals();
    const s = lv?.systolicBp?.value ?? 0;
    const d = lv?.diastolicBp?.value ?? 0;
    if (s >= 140 || d >= 90) return 'warn';
    if (s >= 130 || d >= 80) return 'elevated';
    return 'ok';
  });

  readonly spo2Status = computed((): VitalStatus => {
    const v = this.latestVitals()?.spo2Percent?.value ?? 100;
    return v < 94 ? 'warn' : 'ok';
  });

  readonly upcomingAppointment = computed(() => {
    const appts = this.appointments();
    if (!appts || appts.length === 0) return null;
    return appts[0];
  });

  readonly systolicSpark = computed(
    (): DataPoint[] => this.trends()?.series?.bp?.systolic ?? [],
  );

  readonly spo2Spark = computed(
    (): DataPoint[] => this.trends()?.series?.spo2?.values ?? [],
  );

  readonly weightSpark = computed(
    (): DataPoint[] => this.trends()?.series?.weight?.values ?? [],
  );

  readonly diastolicSpark = computed(
    (): DataPoint[] => this.trends()?.series?.bp?.diastolic ?? [],
  );

  formatApptDate(iso: string): {
    mo: string;
    day: number;
    weekday: string;
    time: string;
  } {
    const d = new Date(iso);
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
    const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    return {
      mo: months[d.getMonth()],
      day: d.getDate(),
      weekday: days[d.getDay()],
      time: d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }),
    };
  }

  goRecord(): void {
    this.router.navigate(['/vitals/record']);
  }
  goVitals(): void {
    this.router.navigate(['/vitals']);
  }
}
