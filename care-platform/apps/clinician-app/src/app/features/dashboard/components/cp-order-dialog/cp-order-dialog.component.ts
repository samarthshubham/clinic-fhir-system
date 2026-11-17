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
import { Subject, catchError, exhaustMap, map, of, startWith } from 'rxjs';
import { OrderService } from '@features/dashboard/order.service';
import {
  OrderCategory,
  OrderPriority,
  ServiceRequestRequest,
  ServiceRequestResponse,
} from '@features/dashboard/dashboard.model';
import { CpCrdCardsComponent } from '@features/dashboard/components/cp-crd-cards/cp-crd-cards.component';

export interface OrderDialogData {
  patientId: string;
  practitionerId: string | null;
}

type SaveState =
  | { status: 'idle' }
  | { status: 'saving' }
  | { status: 'error'; message: string }
  | { status: 'saved'; order: ServiceRequestResponse };

const CODING_INCOMPLETE_ERROR = 'codingIncomplete';

function codeAndSystemTogether(
  group: AbstractControl,
): ValidationErrors | null {
  const system = group.get('codeSystem')?.value as string;
  const code = (group.get('code')?.value as string).trim();
  return !system !== !code ? { [CODING_INCOMPLETE_ERROR]: true } : null;
}

@Component({
  selector: 'cp-order-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    CpCrdCardsComponent,
  ],
  templateUrl: './cp-order-dialog.component.html',
  styleUrl: './cp-order-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpOrderDialogComponent {
  private readonly data = inject<OrderDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<CpOrderDialogComponent, ServiceRequestResponse>>(
      MatDialogRef,
    );
  private readonly orderService = inject(OrderService);
  private readonly submit$ = new Subject<ServiceRequestRequest>();

  protected readonly categories: { value: OrderCategory; label: string }[] = [
    { value: 'laboratory', label: 'Laboratory' },
    { value: 'imaging', label: 'Imaging' },
    { value: 'procedure', label: 'Procedure' },
    { value: 'referral', label: 'Referral' },
  ];

  protected readonly priorities: { value: OrderPriority; label: string }[] = [
    { value: 'routine', label: 'Routine' },
    { value: 'urgent', label: 'Urgent' },
    { value: 'asap', label: 'ASAP' },
    { value: 'stat', label: 'Stat' },
  ];

  protected readonly codeSystems: { value: string; label: string }[] = [
    { value: '', label: 'None' },
    { value: 'http://www.ama-assn.org/go/cpt', label: 'CPT' },
    {
      value: 'http://www.cms.gov/Medicare/Coding/HCPCSReleaseCodeSets',
      label: 'HCPCS',
    },
    { value: 'http://loinc.org', label: 'LOINC' },
    { value: 'http://snomed.info/sct', label: 'SNOMED CT' },
  ];

  protected readonly form = new FormGroup(
    {
      display: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required],
      }),
      category: new FormControl<OrderCategory>('laboratory', {
        nonNullable: true,
        validators: [Validators.required],
      }),
      priority: new FormControl<OrderPriority>('routine', {
        nonNullable: true,
        validators: [Validators.required],
      }),
      codeSystem: new FormControl('', { nonNullable: true }),
      code: new FormControl('', { nonNullable: true }),
    },
    { updateOn: 'blur', validators: codeAndSystemTogether },
  );

  protected readonly codeMatcher: ErrorStateMatcher = {
    isErrorState: (control, form) =>
      !!control &&
      (control.touched || !!form?.submitted) &&
      this.form.hasError(CODING_INCOMPLETE_ERROR),
  };

  protected readonly state = toSignal(
    this.submit$.pipe(
      exhaustMap((request) => {
        this.dialogRef.disableClose = true;
        return this.orderService.placeOrder(request).pipe(
          map((order): SaveState => ({ status: 'saved', order })),
          catchError((error) => {
            this.dialogRef.disableClose = false;
            return of(this.failure(error));
          }),
          startWith<SaveState>({ status: 'saving' }),
        );
      }),
    ),
    { initialValue: { status: 'idle' } as SaveState },
  );

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const code = value.code.trim();
    this.submit$.next({
      patientId: this.data.patientId,
      encounterId: null,
      practitionerId: this.data.practitionerId,
      display: value.display.trim(),
      codeSystem: value.codeSystem || null,
      code: code || null,
      category: value.category,
      status: 'active',
      priority: value.priority,
      authoredOn: formatDate(new Date(), 'yyyy-MM-dd', 'en-US'),
    });
  }

  done(order: ServiceRequestResponse): void {
    this.dialogRef.close(order);
  }

  private failure(error: unknown): SaveState {
    const serverMessage =
      error instanceof HttpErrorResponse ? error.error?.message : undefined;
    return {
      status: 'error',
      message:
        typeof serverMessage === 'string'
          ? serverMessage
          : 'Could not place the order. Try again.',
    };
  }
}
