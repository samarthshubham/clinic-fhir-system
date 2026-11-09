import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
} from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { EmptyStateComponent } from '@shared/empty-state/empty-state.component';
import { MemberService } from '@features/members/member.service';
import {
  MemberResponse,
  coverageTone,
  displayName,
} from '@features/members/member.models';

type DetailState =
  | { status: 'loading' }
  | { status: 'loaded'; member: MemberResponse; name: string }
  | { status: 'not-found' }
  | { status: 'error' };

@Component({
  selector: 'cp-member-detail',
  imports: [
    DatePipe,
    TitleCasePipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    EmptyStateComponent,
  ],
  templateUrl: './member-detail.component.html',
  styleUrl: './member-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MemberDetailComponent {
  private readonly members = inject(MemberService);

  readonly id = input.required<string>();
  readonly coverageTone = coverageTone;
  readonly priorAuthColumns = [
    'submitted',
    'service',
    'status',
    'outcome',
    'preAuthRef',
  ];

  readonly state = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) =>
        this.members.getMember(id).pipe(
          map(
            (member): DetailState => ({
              status: 'loaded',
              member,
              name: displayName(member),
            }),
          ),
          catchError((error: unknown) =>
            of<DetailState>(
              error instanceof HttpErrorResponse && error.status === 404
                ? { status: 'not-found' }
                : { status: 'error' },
            ),
          ),
          startWith<DetailState>({ status: 'loading' }),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as DetailState },
  );
}
