import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Subject } from 'rxjs';
import { CoverageService } from '@features/dashboard/coverage.service';
import { CoverageResponse } from '@features/dashboard/dashboard.model';
import {
  CoverageDialogData,
  CpCoverageDialogComponent,
} from './cp-coverage-dialog.component';

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

describe('CpCoverageDialogComponent', () => {
  let saves: Subject<CoverageResponse>[];
  let coverageService: {
    createCoverage: ReturnType<typeof vi.fn>;
    updateCoverage: ReturnType<typeof vi.fn>;
  };
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  function respond() {
    const response = new Subject<CoverageResponse>();
    saves.push(response);
    return response;
  }

  async function render(
    data: CoverageDialogData,
  ): Promise<ComponentFixture<CpCoverageDialogComponent>> {
    saves = [];
    coverageService = {
      createCoverage: vi.fn(respond),
      updateCoverage: vi.fn(respond),
    };
    dialogRef = { close: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [CpCoverageDialogComponent],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: CoverageService, useValue: coverageService },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(CpCoverageDialogComponent);
    await fixture.whenStable();
    return fixture;
  }

  function input(
    fixture: ComponentFixture<CpCoverageDialogComponent>,
    name: string,
  ): HTMLInputElement {
    return fixture.nativeElement.querySelector(
      `input[formcontrolname="${name}"]`,
    );
  }

  async function type(
    fixture: ComponentFixture<CpCoverageDialogComponent>,
    name: string,
    value: string,
  ): Promise<void> {
    const el = input(fixture, name);
    el.value = value;
    el.dispatchEvent(new Event('input'));
    el.dispatchEvent(new Event('blur'));
    await fixture.whenStable();
  }

  async function save(
    fixture: ComponentFixture<CpCoverageDialogComponent>,
  ): Promise<void> {
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  function errors(
    fixture: ComponentFixture<CpCoverageDialogComponent>,
  ): string[] {
    return Array.from(fixture.nativeElement.querySelectorAll('mat-error')).map(
      (e) => (e as HTMLElement).textContent?.trim() ?? '',
    );
  }

  async function fillRequired(
    fixture: ComponentFixture<CpCoverageDialogComponent>,
  ): Promise<void> {
    await type(fixture, 'payerName', 'Praxis Health Plan');
    await type(fixture, 'payerId', 'praxis-health-plan');
    await type(fixture, 'memberId', 'PHP-10001');
  }

  it('should open in add mode with sensible defaults', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-03-15T12:00:00'));
    const fixture = await render({ patientId: 'p1', coverage: null });
    vi.useRealTimers();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('h2')?.textContent?.trim()).toBe('Add coverage');
    expect(input(fixture, 'periodStart').value).toBe('2026-03-15');
    expect(input(fixture, 'payerName').value).toBe('');
    expect(el.querySelectorAll('mat-select').length).toBe(2);
    expect(el.querySelectorAll('mat-label').length).toBe(8);
  });

  it('should validate a field only when it loses focus', async () => {
    const fixture = await render({ patientId: 'p1', coverage: null });
    const el = input(fixture, 'payerId');

    el.value = 'not valid!';
    el.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(errors(fixture)).toEqual([]);

    el.dispatchEvent(new Event('blur'));
    await fixture.whenStable();
    expect(errors(fixture)).toEqual([
      "Use only letters, digits, '.', '_' and '-'",
    ]);
  });

  it('should show required errors and not save an empty form', async () => {
    const fixture = await render({ patientId: 'p1', coverage: null });
    await type(fixture, 'periodStart', '');

    await save(fixture);

    expect(errors(fixture)).toEqual([
      'Payer name is required',
      'Payer ID is required',
      'Member ID is required',
      'Coverage start is required',
    ]);
    expect(coverageService.createCoverage).not.toHaveBeenCalled();
  });

  it('should reject a coverage end before the start', async () => {
    const fixture = await render({ patientId: 'p1', coverage: null });
    await fillRequired(fixture);
    await type(fixture, 'periodStart', '2026-02-01');
    await type(fixture, 'periodEnd', '2026-01-01');

    expect(errors(fixture)).toEqual(["Coverage end can't be before the start"]);

    await save(fixture);
    expect(coverageService.createCoverage).not.toHaveBeenCalled();
  });

  it('should create coverage for the patient and close with the result', async () => {
    const fixture = await render({ patientId: 'p1', coverage: null });
    await fillRequired(fixture);
    await type(fixture, 'planName', '  ');
    await type(fixture, 'periodStart', '2026-01-01');

    await save(fixture);

    expect(coverageService.createCoverage).toHaveBeenCalledWith({
      patientId: 'p1',
      payerId: 'praxis-health-plan',
      payerName: 'Praxis Health Plan',
      memberId: 'PHP-10001',
      planName: null,
      status: 'active',
      periodStart: '2026-01-01',
      periodEnd: null,
      relationship: 'self',
    });
    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    );
    expect(button.disabled).toBe(true);
    expect(button.textContent?.trim()).toBe('Saving…');

    saves[0].next(coverage);
    expect(dialogRef.close).toHaveBeenCalledWith(coverage);
  });

  it('should edit an existing coverage by id', async () => {
    const fixture = await render({ patientId: 'p1', coverage });
    expect(fixture.nativeElement.querySelector('h2')?.textContent?.trim()).toBe(
      'Edit coverage',
    );
    expect(input(fixture, 'memberId').value).toBe('PHP-10001');

    await type(fixture, 'memberId', 'PHP-20002');
    await type(fixture, 'periodEnd', '2026-12-31');
    await save(fixture);

    expect(coverageService.updateCoverage).toHaveBeenCalledWith('cov1', {
      patientId: 'p1',
      payerId: 'praxis-health-plan',
      payerName: 'Praxis Health Plan',
      memberId: 'PHP-20002',
      planName: 'Praxis Gold PPO',
      status: 'active',
      periodStart: '2026-01-01',
      periodEnd: '2026-12-31',
      relationship: 'self',
    });
    expect(coverageService.createCoverage).not.toHaveBeenCalled();
  });

  it('should fall back to defaults for missing stored values', async () => {
    const fixture = await render({
      patientId: 'p1',
      coverage: {
        ...coverage,
        payerName: null,
        payerId: null,
        memberId: null,
        planName: null,
        status: null,
        periodStart: null,
        relationship: null,
      },
    });

    expect(input(fixture, 'payerName').value).toBe('');
    expect(input(fixture, 'payerId').value).toBe('');
    expect(input(fixture, 'memberId').value).toBe('');
    expect(input(fixture, 'planName').value).toBe('');
    expect(input(fixture, 'periodStart').value).not.toBe('');
  });

  it('should show the server message when saving fails', async () => {
    const fixture = await render({ patientId: 'p1', coverage });

    await save(fixture);
    saves[0].error(
      new HttpErrorResponse({
        status: 422,
        error: { status: 422, message: 'does not belong to Patient' },
      }),
    );
    await fixture.whenStable();

    expect(
      fixture.nativeElement.querySelector('[role="alert"]')?.textContent,
    ).toBe('does not belong to Patient');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('should show a generic message when the server gives none', async () => {
    const fixture = await render({ patientId: 'p1', coverage });

    await save(fixture);
    saves[0].error(new HttpErrorResponse({ status: 0 }));
    await fixture.whenStable();

    expect(
      fixture.nativeElement.querySelector('[role="alert"]')?.textContent,
    ).toBe('Could not save coverage. Try again.');
  });
});
