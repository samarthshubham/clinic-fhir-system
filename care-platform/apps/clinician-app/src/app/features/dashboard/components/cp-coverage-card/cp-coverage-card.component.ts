import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { filter } from 'rxjs';
import { AuthService } from '@care-platform/auth';
import { ToastService } from '@core/toast.service';
import {
  CoverageResponse,
  CoverageStatus,
} from '@features/dashboard/dashboard.model';
import {
  CoverageDialogData,
  CpCoverageDialogComponent,
} from '@features/dashboard/components/cp-coverage-dialog/cp-coverage-dialog.component';

const STATUS_BADGE: Record<CoverageStatus, string> = {
  active: 'success',
  draft: 'info',
  cancelled: 'warning',
  'entered-in-error': 'critical',
};

@Component({
  selector: 'cp-coverage-card',
  imports: [MatButtonModule],
  templateUrl: './cp-coverage-card.component.html',
  styleUrl: './cp-coverage-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpCoverageCardComponent {
  private readonly dialog = inject(MatDialog);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly patientId = input.required<string>();
  readonly coverage = input<CoverageResponse | null>(null);
  readonly saved = output<CoverageResponse>();

  protected readonly canEdit = computed(() =>
    this.auth.hasRole('ADMIN', 'CLINICIAN', 'FRONT_DESK'),
  );

  protected readonly statusBadge = computed(() => {
    const status = this.coverage()?.status;
    return status ? `cp-status-badge--${STATUS_BADGE[status]}` : '';
  });

  openDialog(): void {
    const coverage = this.coverage();
    this.dialog
      .open<CpCoverageDialogComponent, CoverageDialogData, CoverageResponse>(
        CpCoverageDialogComponent,
        { data: { patientId: this.patientId(), coverage }, width: '560px' },
      )
      .afterClosed()
      .pipe(filter((result): result is CoverageResponse => !!result))
      .subscribe((result) => {
        this.toast.show(coverage ? 'Coverage updated' : 'Coverage added');
        this.saved.emit(result);
      });
  }
}
