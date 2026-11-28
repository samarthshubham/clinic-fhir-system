import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { catchError, exhaustMap, map, of, startWith, Subject, tap } from 'rxjs';
import { PendingOrder } from '@features/dashboard/dashboard.model';
import {
  PriorAuthRequest,
  PriorAuthResponse,
  PriorAuthService,
} from '@features/dashboard/prior-auth.service';

type SubmitState =
  | { status: 'idle' | 'saving' }
  | { status: 'error'; message: string };

const X12_SERVICE_TYPES = 'https://codesystem.x12.org/005010/1365';

@Component({
  selector: 'cp-prior-auth-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
  ],
  templateUrl: './cp-prior-auth-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpPriorAuthDialogComponent {
  protected readonly order = inject<PendingOrder>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<CpPriorAuthDialogComponent, PriorAuthResponse>>(
      MatDialogRef,
    );
  private readonly priorAuths = inject(PriorAuthService);
  private readonly submit$ = new Subject<PriorAuthRequest>();

  protected readonly form = inject(NonNullableFormBuilder).group({
    serviceSystem: 'http://www.ama-assn.org/go/cpt',
    serviceCode: '',
    placeOfService: ['', [Validators.required, Validators.pattern(/^\d{2}$/)]],
    serviceCategory: [
      '1',
      [Validators.required, Validators.pattern(/^[A-Z0-9]{1,2}$/)],
    ],
    diagnosis: '',
  });

  protected readonly x12ServiceTypes = X12_SERVICE_TYPES;
  protected readonly state = toSignal(
    this.submit$.pipe(
      exhaustMap((request) =>
        this.priorAuths.submit(request).pipe(
          tap((result) => this.dialogRef.close(result)),
          map((): SubmitState => ({ status: 'idle' })),
          catchError((error: unknown) => of(this.failure(error))),
          startWith<SubmitState>({ status: 'saving' }),
        ),
      ),
    ),
    { initialValue: { status: 'idle' } as SubmitState },
  );

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const {
      serviceCode,
      serviceSystem,
      placeOfService,
      serviceCategory,
      diagnosis,
    } = this.form.getRawValue();
    this.submit$.next({
      orderId: this.order.id,
      service: serviceCode.trim()
        ? { system: serviceSystem, code: serviceCode.trim() }
        : null,
      placeOfService,
      serviceCategory,
      diagnoses: diagnosis.trim()
        ? [
            {
              system: 'http://hl7.org/fhir/sid/icd-10-cm',
              code: diagnosis.trim(),
            },
          ]
        : [],
    });
  }

  private failure(error: unknown): SubmitState {
    const message =
      error instanceof HttpErrorResponse ? error.error?.message : undefined;
    return {
      status: 'error',
      message:
        typeof message === 'string'
          ? message
          : 'Could not submit prior authorization. Try again.',
    };
  }
}
