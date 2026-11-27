import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { EmptyStateComponent } from '@shared/empty-state/empty-state.component';
import { Subject, catchError, map, of, startWith, switchMap } from 'rxjs';

interface PriorAuthQueueItem {
  claimId: string;
  authorizationReference: string | null;
  memberId: string | null;
  memberName: string;
  service: string | null;
  expedited: boolean;
  decisionDeadline: string;
  secondsRemaining: number;
  breached: boolean;
}

function compareQueueItems(
  a: PriorAuthQueueItem,
  b: PriorAuthQueueItem,
): number {
  return (
    Number(b.expedited) - Number(a.expedited) ||
    Date.parse(a.decisionDeadline) - Date.parse(b.decisionDeadline) ||
    a.claimId.localeCompare(b.claimId)
  );
}

@Component({
  selector: 'cp-queue',
  imports: [
    DatePipe,
    EmptyStateComponent,
    MatButtonModule,
    MatProgressBarModule,
    MatTableModule,
  ],
  templateUrl: './queue.component.html',
  styleUrl: './queue.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QueueComponent {
  private readonly http = inject(HttpClient);
  private readonly reload$ = new Subject<void>();

  readonly columns = ['member', 'service', 'priority', 'deadline', 'time'];
  readonly queue = toSignal(
    this.reload$.pipe(
      startWith(undefined),
      switchMap(() =>
        this.http
          .get<{ items: PriorAuthQueueItem[] }>('/api/prior-auths', {
            params: { status: 'pended' },
          })
          .pipe(
            map(({ items }) => [...items].sort(compareQueueItems)),
            catchError(() => of('error' as const)),
            startWith(null),
          ),
      ),
    ),
    { initialValue: null },
  );

  reload(): void {
    this.reload$.next();
  }

  remaining(item: PriorAuthQueueItem): string {
    if (item.secondsRemaining === 0) return 'Due now';
    const minutes = Math.floor(Math.abs(item.secondsRemaining) / 60);
    const days = Math.floor(minutes / 1440);
    const hours = Math.floor((minutes % 1440) / 60);
    const duration =
      days > 0
        ? `${days}d ${hours}h`
        : hours > 0
          ? `${hours}h ${minutes % 60}m`
          : minutes > 0
            ? `${minutes} min`
            : 'Under 1 min';
    return `${duration} ${item.breached ? 'overdue' : 'left'}`;
  }
}
