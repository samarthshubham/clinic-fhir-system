import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '@care-platform/auth';
import {
  Subject,
  catchError,
  exhaustMap,
  map,
  of,
  startWith,
  switchMap,
} from 'rxjs';

interface PriorAuthReview {
  claimId: string;
  authorizationReference: string | null;
  disposition: string | null;
  pended: boolean;
  lines: {
    sequence: number;
    service: string | null;
    code: string | null;
    rule: {
      name: string;
      requiresPriorAuth: boolean;
      criteria: {
        linkId: string;
        operator: string;
        expected: string;
        matched: boolean;
      }[];
    } | null;
  }[];
  answers: { linkId: string; question: string | null; values: string[] }[];
  attachments: {
    title: string | null;
    contentType: string | null;
    size: number | null;
  }[];
}

type Decision = 'APPROVE' | 'DENY' | 'PEND';

interface Submission {
  claimId: string;
  decision: Decision;
  reasonCode?: string;
  reasonText?: string;
}

function reviewUrl(claimId: string): string {
  return '/api/prior-auths/' + encodeURIComponent(claimId);
}

@Component({
  selector: 'cp-prior-auth-review',
  imports: [
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  templateUrl: './review.component.html',
  styleUrl: './review.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpPriorAuthReviewComponent {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly submissions = new Subject<Submission>();

  readonly canDecide = computed(() => {
    const roles: readonly string[] = this.auth.user()?.roles ?? [];
    return roles.includes('UM_REVIEWER');
  });
  readonly denialOpen = signal(false);
  readonly denial = new FormGroup({
    reasonCode: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.pattern(/^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$/),
      ],
    }),
    reasonText: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.pattern(/\S/),
        Validators.maxLength(1000),
      ],
    }),
  });

  readonly loaded = toSignal(
    this.route.paramMap.pipe(
      switchMap((params) =>
        this.http.get<PriorAuthReview>(reviewUrl(params.get('id') ?? '')).pipe(
          catchError(() => of('error' as const)),
          startWith(null),
        ),
      ),
    ),
    { initialValue: null },
  );

  readonly result = toSignal(
    this.submissions.pipe(
      exhaustMap(({ claimId, ...body }) =>
        this.http
          .post<PriorAuthReview>(reviewUrl(claimId) + '/decision', body)
          .pipe(
            map((review) => ({ status: 'saved' as const, claimId, review })),
            catchError((error) =>
              of({
                status: 'error' as const,
                claimId,
                message:
                  error.status === 409
                    ? 'This request is no longer available for this decision. Return to the queue to refresh it.'
                    : 'Could not save the decision. Try again.',
              }),
            ),
            startWith({ status: 'saving' as const, claimId }),
          ),
      ),
    ),
  );

  readonly review = computed(() => {
    const loaded = this.loaded();
    const result = this.result();
    return result?.status === 'saved' &&
      loaded &&
      loaded !== 'error' &&
      result.claimId === loaded.claimId
      ? result.review
      : loaded;
  });

  readonly currentResult = computed(() => {
    const review = this.review();
    const result = this.result();
    return review && review !== 'error' && result?.claimId === review.claimId
      ? result
      : null;
  });

  submit(decision: Decision): void {
    const review = this.review();
    if (!review || review === 'error' || !review.pended || !this.canDecide())
      return;
    if (decision === 'DENY') {
      this.denial.markAllAsTouched();
      if (this.denial.invalid) return;
      const { reasonCode, reasonText } = this.denial.getRawValue();
      this.submissions.next({
        claimId: review.claimId,
        decision,
        reasonCode,
        reasonText: reasonText.trim(),
      });
    } else {
      this.submissions.next({ claimId: review.claimId, decision });
    }
  }
}
