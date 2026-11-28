import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import {
  takeUntilDestroyed,
  toObservable,
  toSignal,
} from '@angular/core/rxjs-interop';
import {
  catchError,
  combineLatest,
  distinctUntilChanged,
  filter,
  map,
  merge,
  of,
  startWith,
  Subject,
  switchMap,
  timer,
} from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { CpReferralDirectoryComponent } from '@features/dashboard/components/cp-referral-directory/cp-referral-directory.component';
import { MatIconModule } from '@angular/material/icon';
import { DashboardService } from '@features/dashboard/dashboard.service';
import {
  ActiveCarePlan,
  BpSeries,
  PendingOrder,
  ServiceRequestResponse,
  SimpleSeries,
  TrendsResponse,
} from '@features/dashboard/dashboard.model';
import { CpGoalRowComponent } from '@features/dashboard/components/cp-goal-row/cp-goal-row.component';
import { CpTrendChartComponent } from '@features/dashboard/components/cp-trend-chart/cp-trend-chart.component';
import { CpCrdCardsComponent } from '@features/dashboard/components/cp-crd-cards/cp-crd-cards.component';
import { OrderDialogLauncher } from '@features/dashboard/order-dialog-launcher.service';
import { CpPriorAuthDialogComponent } from '@features/dashboard/components/cp-prior-auth-dialog/cp-prior-auth-dialog.component';
import {
  PRIOR_AUTH_STATUS_LABEL,
  PriorAuthResponse,
  PriorAuthService,
} from '@features/dashboard/prior-auth.service';

type AsyncState<T> = T | null | 'error';

const PERIODS = ['7d', '30d', '90d'] as const;
type Period = (typeof PERIODS)[number];

function isBpSeries(s: BpSeries | SimpleSeries): s is BpSeries {
  return 'systolic' in s;
}

const CATEGORY_ICONS: Record<string, string> = {
  laboratory: 'biotech',
  imaging: 'radiology',
  procedure: 'medical_services',
  referral: 'person_add',
  medication: 'pill',
};

@Component({
  selector: 'cp-care-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CpGoalRowComponent,
    CpTrendChartComponent,
    CpCrdCardsComponent,
    MatButtonModule,
    MatIconModule,
    DatePipe,
  ],
  templateUrl: './cp-care-panel.component.html',
  styleUrl: './cp-care-panel.component.scss',
})
export class CpCarePanelComponent {
  private readonly svc = inject(DashboardService);
  private readonly dialog = inject(MatDialog);
  private readonly orderDialog = inject(OrderDialogLauncher);

  private readonly priorAuths = inject(PriorAuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly refreshPriorAuths$ = new Subject<void>();

  readonly patientId = input.required<string>();
  readonly carePlan = input.required<ActiveCarePlan | null>();
  readonly pending = input.required<PendingOrder[]>();
  readonly orderPlaced = output<ServiceRequestResponse>();

  protected readonly canOrder = computed(() => this.orderDialog.canOrder());

  protected readonly priorAuthLabels = PRIOR_AUTH_STATUS_LABEL;
  protected readonly priorAuthByOrder = toSignal(
    toObservable(this.patientId).pipe(
      distinctUntilChanged(),
      switchMap((id) =>
        this.canOrder() && id
          ? merge(
              of(undefined),
              timer(15_000, 15_000),
              this.refreshPriorAuths$,
            ).pipe(
              switchMap(() =>
                this.priorAuths.list(id).pipe(
                  map(
                    (results) =>
                      new Map(
                        results.map((result) => [result.orderId, result]),
                      ),
                  ),
                  catchError(() => of('error' as const)),
                ),
              ),
              startWith(null),
            )
          : of(null),
      ),
    ),
    { initialValue: null },
  );

  readonly periods = PERIODS;
  readonly period = signal<Period>('30d');
  readonly expandedGoalId = signal<string | null>(null);

  private readonly trends$ = combineLatest([
    toObservable(this.patientId).pipe(
      filter((id) => id !== ''),
      distinctUntilChanged(),
    ),
    toObservable(this.period),
  ]).pipe(
    switchMap(([id, p]) =>
      this.svc.getTrends(id, 'bp,spo2,weight', p).pipe(
        catchError(() => of('error' as const)),
        startWith(null),
      ),
    ),
  );

  private readonly trends = toSignal<AsyncState<TrendsResponse>>(this.trends$, {
    initialValue: null,
  });

  readonly isTrendsLoading = computed(() => this.trends() === null);

  readonly bpSeries = computed(() => {
    const t = this.trends();
    if (!t || t === 'error') return null;
    const s = t.series['bp'];
    return s && isBpSeries(s) ? s : null;
  });

  readonly weightSeries = computed(() => {
    const t = this.trends();
    if (!t || t === 'error') return null;
    const s = t.series['weight'];
    return s && !isBpSeries(s) ? (s as SimpleSeries) : null;
  });

  readonly spo2Series = computed(() => {
    const t = this.trends();
    if (!t || t === 'error') return null;
    const s = t.series['spo2'];
    return s && !isBpSeries(s) ? (s as SimpleSeries) : null;
  });

  readonly sortedPending = computed(() =>
    [...this.pending()].sort((a, b) => {
      const rank: Record<string, number> = {
        stat: 0,
        asap: 1,
        urgent: 2,
        routine: 3,
      };
      return (rank[a.priority] ?? 4) - (rank[b.priority] ?? 4);
    }),
  );

  getCategoryIcon(cat: string | null): string {
    return CATEGORY_ICONS[cat?.toLowerCase() ?? ''] ?? 'checklist';
  }

  openDirectory(): void {
    this.dialog.open(CpReferralDirectoryComponent, {
      data: this.patientId(),
      width: '640px',
    });
  }

  openOrderDialog(): void {
    this.orderDialog
      .open(this.patientId())
      .subscribe((order) => this.orderPlaced.emit(order));
  }

  openPriorAuth(order: PendingOrder): void {
    this.dialog
      .open<CpPriorAuthDialogComponent, PendingOrder, PriorAuthResponse>(
        CpPriorAuthDialogComponent,
        { data: order, width: '480px' },
      )
      .afterClosed()
      .pipe(
        filter((response) => response != null),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.refreshPriorAuths$.next());
  }

  setPeriod(p: Period): void {
    this.period.set(p);
  }

  toggleGoal(id: string): void {
    this.expandedGoalId.set(this.expandedGoalId() === id ? null : id);
  }
}
