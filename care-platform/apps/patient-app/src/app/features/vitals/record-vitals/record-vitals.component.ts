import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { formatDate } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PatientAppService } from '../../../shared/services/patient-app.service';
import { PatientSelectService } from '../../../shared/services/patient-select.service';
import { PostVitalsRequest } from '../../../shared/models/patient-app.models';

@Component({
  selector: 'cp-record-vitals',
  templateUrl: './record-vitals.component.html',
  styleUrl: './record-vitals.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
})
export class RecordVitalsComponent {
  private readonly svc = inject(PatientAppService);
  private readonly selectSvc = inject(PatientSelectService);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder);

  readonly patientId = this.selectSvc.selectedId()!;
  readonly step = signal<0 | 1 | 2 | 3 | 4>(0);
  readonly saving = signal(false);

  readonly bpForm = this.fb.group({
    systolic: [
      120,
      [Validators.required, Validators.min(60), Validators.max(250)],
    ],
    diastolic: [
      80,
      [Validators.required, Validators.min(40), Validators.max(150)],
    ],
  });

  readonly weight = signal(70.0);
  readonly spo2 = signal(97);

  private readonly bpValue = toSignal(this.bpForm.valueChanges, {
    initialValue: this.bpForm.value,
  });
  private readonly bpStatus = toSignal(this.bpForm.statusChanges, {
    initialValue: this.bpForm.status,
  });

  readonly bpHigh = computed(() => {
    const s = this.bpValue().systolic ?? 0;
    const d = this.bpValue().diastolic ?? 0;
    return s >= 140 || d >= 90;
  });

  readonly canContinue = computed(
    () => this.step() !== 0 || this.bpStatus() === 'VALID',
  );

  readonly spo2Low = computed(() => this.spo2() < 94);

  readonly progressBars = computed(() =>
    [0, 1, 2, 3].map((i) => ({
      done: i < this.step(),
      active: i === this.step(),
    })),
  );

  goBack(): void {
    if (this.step() === 0) {
      this.router.navigate(['/home']);
    } else {
      this.step.update((s) => (s - 1) as 0 | 1 | 2 | 3 | 4);
    }
  }

  goNext(): void {
    if (!this.canContinue()) {
      return;
    }
    if (this.step() < 3) {
      this.step.update((s) => (s + 1) as 0 | 1 | 2 | 3 | 4);
    } else {
      this.save();
    }
  }

  adjustWeight(delta: number): void {
    this.weight.update((v) => Math.round((v + delta) * 10) / 10);
  }

  adjustSpo2(delta: number): void {
    this.spo2.update((v) => Math.max(70, Math.min(100, v + delta)));
  }

  private save(): void {
    this.saving.set(true);
    const readings: PostVitalsRequest = {
      patientId: this.patientId,
      effectiveDate: formatDate(new Date(), 'yyyy-MM-dd', 'en-US'),
      systolicBp: this.bpForm.value.systolic ?? undefined,
      diastolicBp: this.bpForm.value.diastolic ?? undefined,
      weightKg: this.weight(),
      spo2Percent: this.spo2(),
    };

    this.svc.postVitals(readings).subscribe({
      next: () => {
        this.saving.set(false);
        this.step.set(4);
        setTimeout(() => this.router.navigate(['/vitals']), 1800);
      },
      error: () => {
        this.saving.set(false);
        this.snack.open('Something went wrong. Please try again.', 'OK', {
          duration: 4000,
        });
      },
    });
  }
}
