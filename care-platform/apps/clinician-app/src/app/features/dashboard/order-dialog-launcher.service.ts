import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable, filter, tap } from 'rxjs';
import { AuthService } from '@care-platform/auth';
import { ToastService } from '@core/toast.service';
import {
  CpOrderDialogComponent,
  OrderDialogData,
} from '@features/dashboard/components/cp-order-dialog/cp-order-dialog.component';
import { ServiceRequestResponse } from './dashboard.model';

@Injectable({ providedIn: 'root' })
export class OrderDialogLauncher {
  private readonly dialog = inject(MatDialog);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  canOrder(): boolean {
    return this.auth.hasRole('ADMIN', 'CLINICIAN');
  }

  open(patientId: string): Observable<ServiceRequestResponse> {
    return this.dialog
      .open<CpOrderDialogComponent, OrderDialogData, ServiceRequestResponse>(
        CpOrderDialogComponent,
        {
          data: {
            patientId,
            practitionerId: this.auth.user()?.practitionerId ?? null,
          },
          width: '560px',
        },
      )
      .afterClosed()
      .pipe(
        filter((order): order is ServiceRequestResponse => !!order),
        tap(() => this.toast.show('Order placed')),
      );
  }
}
