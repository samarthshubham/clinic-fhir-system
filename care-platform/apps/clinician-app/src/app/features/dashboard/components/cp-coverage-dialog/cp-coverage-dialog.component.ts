import { formatDate } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ErrorStateMatcher } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import {
  Observable,
  Subject,
  catchError,
  exhaustMap,
  map,
  of,
  startWith,
  tap,
} from 'rxjs';
import { CoverageService } from '@features/dashboard/coverage.service';
import {
  CoverageRelationship,
  CoverageRequest,
  CoverageResponse,
  CoverageStatus,
} from '@features/dashboard/dashboard.model';

export interface CoverageDialogData {
  patientId: string;
  coverage: CoverageResponse | null;
}

type SaveState =
  | { status: 'idle' }
  | { status: 'saving' }
  | { status: 'error'; message: string };

const PERIOD_ORDER_ERROR = 'periodEndBeforeStart';

function periodEndNotBeforeStart(
  group: AbstractControl,
): ValidationErrors | null {
  const start = group.get('periodStart')?.value as string;
  const end = group.get('periodEnd')?.value as string;
  return start && end && end < start ? { [PERIOD_ORDER_ERROR]: true } : null;
}

@Component({
  selector: 'cp-coverage-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
  ],
  templateUrl: './cp-coverage-dialog.component.html',
  styleUrl: './cp-coverage-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpCoverageDialogComponent {
  private readonly data = inject<CoverageDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<CpCoverageDialogComponent, CoverageResponse>>(
      MatDialogRef,
    );
  private readonly coverageService = inject(CoverageService);
  private readonly submit$ = new Subject<CoverageRequest>();

  protected readonly isEdit = this.data.coverage !== null;

  protected readonly statuses: { value: CoverageStatus; label: string }[] = [
    { value: 'active', label: 'Active' },
    { value: 'draft', label: 'Draft' },
    { value: 'cancelled', label: 'Cancelled' },
    { value: 'entered-in-error', label: 'Entered in error' },
  ];

  protected readonly relationships: {
    value: CoverageRelationship;
    label: string;
  }[] = [
    { value: 'self', label: 'Self' },
    { value: 'spouse', label: 'Spouse' },
    { value: 'child', label: 'Child' },
    { value: 'parent', label: 'Parent' },
    { value: 'common', label: 'Common law spouse' },
    { value: 'injured', label: 'Injured party' },
    { value: 'other', label: 'Other' },
  ];

  protected readonly form = this.buildForm(this.data.coverage);

  protected readonly periodEndMatcher: ErrorStateMatcher = {
    isErrorState: (control, form) =>
      !!control &&
      (control.touched || !!form?.submitted) &&
      (control.invalid || this.form.hasError(PERIOD_ORDER_ERROR)),
  };

  protected readonly state = toSignal(
    this.submit$.pipe(
      exhaustMap((request) =>
        this.save(request).pipe(
          tap((coverage) => this.dialogRef.close(coverage)),
          map((): SaveState => ({ status: 'idle' })),
          catchError((error) => of(this.failure(error))),
          startWith<SaveState>({ status: 'saving' }),
        ),
      ),
    ),
    { initialValue: { status: 'idle' } as SaveState },
  );

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.submit$.next({
      ...value,
      patientId: this.data.patientId,
      planName: value.planName.trim() || null,
      periodEnd: value.periodEnd || null,
    });
  }

  private save(request: CoverageRequest): Observable<CoverageResponse> {
    const existing = this.data.coverage;
    return existing
      ? this.coverageService.updateCoverage(existing.id, request)
      : this.coverageService.createCoverage(request);
  }

  private buildForm(coverage: CoverageResponse | null) {
    const required = {
      nonNullable: true as const,
      validators: [Validators.required],
    };
    return new FormGroup(
      {
        payerName: new FormControl(coverage?.payerName ?? '', required),
        payerId: new FormControl(coverage?.payerId ?? '', {
          nonNullable: true,
          validators: [
            Validators.required,
            Validators.pattern(/^[A-Za-z0-9._-]+$/),
          ],
        }),
        planName: new FormControl(coverage?.planName ?? '', {
          nonNullable: true,
        }),
        memberId: new FormControl(coverage?.memberId ?? '', required),
        status: new FormControl<CoverageStatus>(
          coverage?.status ?? 'active',
          required,
        ),
        periodStart: new FormControl(
          coverage?.periodStart ??
            formatDate(new Date(), 'yyyy-MM-dd', 'en-US'),
          required,
        ),
        periodEnd: new FormControl(coverage?.periodEnd ?? '', {
          nonNullable: true,
        }),
        relationship: new FormControl<CoverageRelationship>(
          coverage?.relationship ?? 'self',
          required,
        ),
      },
      { updateOn: 'blur', validators: periodEndNotBeforeStart },
    );
  }

  private failure(error: unknown): SaveState {
    const serverMessage =
      error instanceof HttpErrorResponse ? error.error?.message : undefined;
    return {
      status: 'error',
      message:
        typeof serverMessage === 'string'
          ? serverMessage
          : 'Could not save coverage. Try again.',
    };
  }
}
