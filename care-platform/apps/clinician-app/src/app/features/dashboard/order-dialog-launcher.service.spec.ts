import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { AuthService } from '@care-platform/auth';
import { ToastService } from '@core/toast.service';
import { CpOrderDialogComponent } from '@features/dashboard/components/cp-order-dialog/cp-order-dialog.component';
import { ServiceRequestResponse } from './dashboard.model';
import { OrderDialogLauncher } from './order-dialog-launcher.service';

const placed: ServiceRequestResponse = {
  id: 'sr1',
  patientId: 'p1',
  encounterId: null,
  practitionerId: 'dr1',
  display: 'MRI lumbar spine',
  codeSystem: 'http://www.ama-assn.org/go/cpt',
  code: '72148',
  category: 'imaging',
  status: 'active',
  priority: 'routine',
  authoredOn: '2026-11-17',
  coverage: null,
  cards: [],
};

describe('OrderDialogLauncher', () => {
  let dialogResult: ServiceRequestResponse | undefined;
  let dialog: { open: ReturnType<typeof vi.fn> };
  let toast: { show: ReturnType<typeof vi.fn> };
  let roles: string[];

  beforeEach(() => {
    dialogResult = undefined;
    roles = ['CLINICIAN'];
    dialog = {
      open: vi.fn(() => ({ afterClosed: () => of(dialogResult) })),
    };
    toast = { show: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: MatDialog, useValue: dialog },
        { provide: ToastService, useValue: toast },
        {
          provide: AuthService,
          useValue: {
            user: () => ({ practitionerId: 'dr1' }),
            hasRole: (...r: string[]) => r.some((x) => roles.includes(x)),
          },
        },
      ],
    });
  });

  it('should open the order dialog for the patient and emit the placed order', () => {
    dialogResult = placed;
    const emitted: ServiceRequestResponse[] = [];

    TestBed.inject(OrderDialogLauncher)
      .open('p1')
      .subscribe((o) => emitted.push(o));

    expect(dialog.open).toHaveBeenCalledWith(CpOrderDialogComponent, {
      data: { patientId: 'p1', practitionerId: 'dr1' },
      width: '560px',
    });
    expect(toast.show).toHaveBeenCalledWith('Order placed');
    expect(emitted).toEqual([placed]);
  });

  it('should emit nothing when the dialog is cancelled', () => {
    const emitted: ServiceRequestResponse[] = [];

    TestBed.inject(OrderDialogLauncher)
      .open('p1')
      .subscribe((o) => emitted.push(o));

    expect(toast.show).not.toHaveBeenCalled();
    expect(emitted).toEqual([]);
  });

  it('should let only clinicians and admins order', () => {
    const launcher = TestBed.inject(OrderDialogLauncher);
    expect(launcher.canOrder()).toBe(true);

    roles = ['FRONT_DESK'];
    expect(launcher.canOrder()).toBe(false);
  });
});
