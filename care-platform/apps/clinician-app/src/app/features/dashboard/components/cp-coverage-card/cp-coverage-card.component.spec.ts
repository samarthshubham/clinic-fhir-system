import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { AuthService } from '@care-platform/auth';
import { ToastService } from '@core/toast.service';
import { CoverageResponse } from '@features/dashboard/dashboard.model';
import { CpCoverageDialogComponent } from '@features/dashboard/components/cp-coverage-dialog/cp-coverage-dialog.component';
import { CpCoverageCardComponent } from './cp-coverage-card.component';

const coverage: CoverageResponse = {
  id: 'cov1',
  patientId: 'p1',
  payerOrganizationId: 'org1',
  payerId: 'praxis-health-plan',
  payerName: 'Praxis Health Plan',
  memberId: 'PHP-10001',
  planName: 'Praxis Gold PPO',
  status: 'active',
  periodStart: '2026-01-01',
  periodEnd: null,
  relationship: 'self',
};

describe('CpCoverageCardComponent', () => {
  let canWrite: boolean;
  let dialogResult: CoverageResponse | undefined;
  let dialog: { open: ReturnType<typeof vi.fn> };
  let toast: { show: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    canWrite = true;
    dialogResult = undefined;
    dialog = {
      open: vi.fn(() => ({ afterClosed: () => of(dialogResult) })),
    };
    toast = { show: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [CpCoverageCardComponent],
      providers: [
        { provide: AuthService, useValue: { hasRole: () => canWrite } },
        { provide: MatDialog, useValue: dialog },
        { provide: ToastService, useValue: toast },
      ],
    }).compileComponents();
  });

  async function render(value: CoverageResponse | null) {
    const fixture = TestBed.createComponent(CpCoverageCardComponent);
    fixture.componentRef.setInput('patientId', 'p1');
    fixture.componentRef.setInput('coverage', value);
    await fixture.whenStable();
    return fixture;
  }

  function text(el: HTMLElement, selector: string): string | undefined {
    return el.querySelector(selector)?.textContent?.trim();
  }

  it('should show payer, plan, member id and status', async () => {
    const el: HTMLElement = (await render(coverage)).nativeElement;

    expect(el.querySelector('section')?.getAttribute('aria-labelledby')).toBe(
      el.querySelector('.px-card-title')?.id,
    );
    expect(text(el, '.app-coverage-card__payer')).toBe('Praxis Health Plan');
    expect(text(el, '.app-coverage-card__plan')).toBe('Praxis Gold PPO');
    expect(text(el, '.app-coverage-card__member')).toBe('PHP-10001');
    expect(el.querySelector('.app-coverage-card__member')?.classList).toContain(
      'cp-font-mono',
    );
    const badge = el.querySelector('.cp-status-badge');
    expect(badge?.textContent?.trim()).toBe('active');
    expect(badge?.classList).toContain('cp-status-badge--success');
    expect(el.querySelector('button')?.getAttribute('aria-label')).toBe(
      'Edit coverage',
    );
  });

  it.each([
    ['draft', 'cp-status-badge--info'],
    ['cancelled', 'cp-status-badge--warning'],
    ['entered-in-error', 'cp-status-badge--critical'],
  ] as const)('should badge %s coverage as %s', async (status, badge) => {
    const el: HTMLElement = (await render({ ...coverage, status }))
      .nativeElement;

    expect(el.querySelector('.cp-status-badge')?.classList).toContain(badge);
  });

  it('should show dashes for a missing payer name, plan or member id', async () => {
    const el: HTMLElement = (
      await render({
        ...coverage,
        payerName: null,
        planName: null,
        memberId: null,
      })
    ).nativeElement;

    expect(text(el, '.app-coverage-card__payer')).toBe('–');
    expect(text(el, '.app-coverage-card__plan')).toBe('–');
    expect(text(el, '.app-coverage-card__member')).toBe('–');
  });

  it('should show the empty state with an add action', async () => {
    const el: HTMLElement = (await render(null)).nativeElement;

    expect(text(el, '.app-coverage-card__empty')).toBe(
      'No active coverage on file.',
    );
    expect(el.querySelector('dl')).toBeNull();
    expect(text(el, 'button')).toBe('Add coverage');
  });

  it('should hide the action from users who cannot write coverage', async () => {
    canWrite = false;
    const el: HTMLElement = (await render(null)).nativeElement;

    expect(el.querySelector('button')).toBeNull();
    expect(text(el, '.app-coverage-card__empty')).toBe(
      'No active coverage on file.',
    );
  });

  it('should open the dialog to add coverage and emit the saved one', async () => {
    dialogResult = coverage;
    const fixture = await render(null);
    const saved: CoverageResponse[] = [];
    fixture.componentInstance.saved.subscribe((c) => saved.push(c));

    fixture.nativeElement.querySelector('button').click();

    expect(dialog.open).toHaveBeenCalledWith(CpCoverageDialogComponent, {
      data: { patientId: 'p1', coverage: null },
      width: '560px',
    });
    expect(saved).toEqual([coverage]);
    expect(toast.show).toHaveBeenCalledWith('Coverage added');
  });

  it('should open the dialog with the current coverage to edit it', async () => {
    dialogResult = { ...coverage, memberId: 'PHP-20002' };
    const fixture = await render(coverage);

    fixture.nativeElement.querySelector('button').click();

    expect(dialog.open.mock.calls[0][1].data).toEqual({
      patientId: 'p1',
      coverage,
    });
    expect(toast.show).toHaveBeenCalledWith('Coverage updated');
  });

  it('should emit nothing when the dialog is cancelled', async () => {
    const fixture = await render(coverage);
    const saved: CoverageResponse[] = [];
    fixture.componentInstance.saved.subscribe((c) => saved.push(c));

    fixture.nativeElement.querySelector('button').click();

    expect(saved).toEqual([]);
    expect(toast.show).not.toHaveBeenCalled();
  });
});
