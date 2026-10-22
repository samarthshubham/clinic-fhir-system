import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, filter, of, throwError } from 'rxjs';
import { AlertPage, AlertResponse } from '@core/alert.models';
import { AlertService } from '@core/alert.service';
import { PatientService } from '@features/patients/services/patient.service';
import { InboxComponent } from './inbox.component';

function alert(id: string, patientId: string | null): AlertResponse {
  return {
    id,
    patientId,
    observationId: `obs-${id}`,
    reason: 'Systolic BP above 180 mmHg',
    raisedAt: '2026-10-22T10:15:00Z',
    status: 'active',
  };
}

describe('InboxComponent', () => {
  let alerts: BehaviorSubject<AlertPage | null | undefined>;
  let acknowledge: ReturnType<typeof vi.fn>;
  let getPatient: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    alerts = new BehaviorSubject<AlertPage | null | undefined>(undefined);
    acknowledge = vi.fn(() => of(alert('a1', 'p1')));
    getPatient = vi.fn((id: string) =>
      id === 'p1'
        ? of({ id, firstName: 'Tony', lastName: 'Stark' })
        : throwError(() => new Error('not found')),
    );
    await TestBed.configureTestingModule({
      imports: [InboxComponent],
      providers: [
        provideRouter([]),
        {
          provide: AlertService,
          useValue: {
            activeAlerts$: alerts.pipe(filter((page) => page !== undefined)),
            acknowledge,
          },
        },
        { provide: PatientService, useValue: { getPatient } },
      ],
    }).compileComponents();
  });

  async function render(): Promise<ComponentFixture<InboxComponent>> {
    const fixture = TestBed.createComponent(InboxComponent);
    await fixture.whenStable();
    return fixture;
  }

  function text(fixture: ComponentFixture<InboxComponent>): string {
    return fixture.nativeElement.textContent.replace(/\s+/g, ' ');
  }

  it('should show the loading state until the first poll', async () => {
    const fixture = await render();

    expect(text(fixture)).toContain('Loading alerts');
  });

  it('should show the error state when alerts could not be loaded', async () => {
    alerts.next(null);
    const fixture = await render();

    expect(text(fixture)).toContain('Could not load alerts');
  });

  it('should show the empty state', async () => {
    alerts.next({ items: [], total: 0 });
    const fixture = await render();

    expect(text(fixture)).toContain('No active alerts.');
  });

  it('should list alerts with patient names linking to the dashboard', async () => {
    alerts.next({
      items: [alert('a2', 'p1'), alert('a1', 'p1')],
      total: 2,
    });
    const fixture = await render();
    const links: HTMLAnchorElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('.inbox__patient'),
    );

    expect(links.map((a) => a.textContent?.trim())).toEqual([
      'Tony Stark',
      'Tony Stark',
    ]);
    expect(links[0].getAttribute('href')).toBe('/patients/p1');
    expect(getPatient).toHaveBeenCalledTimes(1);
    expect(text(fixture)).toContain('Systolic BP above 180 mmHg');
  });

  it('should fall back to the patient id when the name cannot be loaded', async () => {
    alerts.next({ items: [alert('a3', 'p9')], total: 1 });
    const fixture = await render();

    expect(
      fixture.nativeElement.querySelector('.inbox__patient').textContent.trim(),
    ).toBe('Patient p9');
  });

  it('should label alerts without a patient reference', async () => {
    alerts.next({ items: [alert('a4', null)], total: 1 });
    const fixture = await render();

    expect(
      fixture.nativeElement.querySelector('.inbox__patient').textContent.trim(),
    ).toBe('Unknown patient');
    expect(getPatient).not.toHaveBeenCalled();
  });

  it('should acknowledge an alert', async () => {
    alerts.next({ items: [alert('a1', 'p1')], total: 1 });
    const fixture = await render();

    fixture.nativeElement.querySelector('button').click();

    expect(acknowledge).toHaveBeenCalledWith('a1');
  });

  it('should keep working when acknowledging fails', async () => {
    acknowledge.mockReturnValueOnce(throwError(() => new Error('down')));
    alerts.next({ items: [alert('a1', 'p1')], total: 1 });
    const fixture = await render();
    const button: HTMLButtonElement =
      fixture.nativeElement.querySelector('button');

    button.click();
    button.click();

    expect(acknowledge).toHaveBeenCalledTimes(2);
  });
});
