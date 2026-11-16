import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Subject } from 'rxjs';
import { OrderService } from '@features/dashboard/order.service';
import { ServiceRequestResponse } from '@features/dashboard/dashboard.model';
import { CpOrderDialogComponent } from './cp-order-dialog.component';

const CPT = 'http://www.ama-assn.org/go/cpt';

const mri: ServiceRequestResponse = {
  id: 'sr1',
  patientId: 'p1',
  encounterId: null,
  practitionerId: 'dr1',
  display: 'MRI lumbar spine without contrast',
  codeSystem: CPT,
  code: '72148',
  category: 'imaging',
  status: 'active',
  priority: 'routine',
  authoredOn: '2026-11-16',
  coverage: {
    coverageId: 'cov1',
    covered: 'covered',
    paNeeded: 'auth-needed',
    docNeeded: 'clinical',
    reason: null,
    questionnaire: null,
    date: '2026-11-16',
    coverageAssertionId: 'a1',
  },
  cards: [
    {
      summary: 'Prior authorization needed: MRI lumbar spine without contrast',
      indicator: 'warning',
      detail: 'Complete the documentation questionnaire.',
      source: 'Praxis Health Plan',
    },
  ],
};

describe('CpOrderDialogComponent', () => {
  let saves: Subject<ServiceRequestResponse>[];
  let orderService: { placeOrder: ReturnType<typeof vi.fn> };
  let dialogRef: { close: ReturnType<typeof vi.fn>; disableClose: boolean };

  async function render(): Promise<ComponentFixture<CpOrderDialogComponent>> {
    saves = [];
    orderService = {
      placeOrder: vi.fn(() => {
        const response = new Subject<ServiceRequestResponse>();
        saves.push(response);
        return response;
      }),
    };
    dialogRef = { close: vi.fn(), disableClose: false };
    await TestBed.configureTestingModule({
      imports: [CpOrderDialogComponent],
      providers: [
        {
          provide: MAT_DIALOG_DATA,
          useValue: { patientId: 'p1', practitionerId: 'dr1' },
        },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: OrderService, useValue: orderService },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(CpOrderDialogComponent);
    await fixture.whenStable();
    return fixture;
  }

  function form(fixture: ComponentFixture<CpOrderDialogComponent>) {
    return (
      fixture.componentInstance as unknown as {
        form: CpOrderDialogComponent['form'];
      }
    ).form;
  }

  async function type(
    fixture: ComponentFixture<CpOrderDialogComponent>,
    name: string,
    value: string,
  ): Promise<void> {
    const el: HTMLInputElement = fixture.nativeElement.querySelector(
      `input[formcontrolname="${name}"]`,
    );
    el.value = value;
    el.dispatchEvent(new Event('input'));
    el.dispatchEvent(new Event('blur'));
    await fixture.whenStable();
  }

  async function submit(
    fixture: ComponentFixture<CpOrderDialogComponent>,
  ): Promise<void> {
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  function errors(fixture: ComponentFixture<CpOrderDialogComponent>): string[] {
    return Array.from(fixture.nativeElement.querySelectorAll('mat-error')).map(
      (e) => (e as HTMLElement).textContent?.trim() ?? '',
    );
  }

  async function fillMri(
    fixture: ComponentFixture<CpOrderDialogComponent>,
  ): Promise<void> {
    await type(fixture, 'display', ' MRI lumbar spine without contrast ');
    form(fixture).patchValue({ category: 'imaging', codeSystem: CPT });
    await type(fixture, 'code', '72148');
  }

  it('should open as a new order form with defaults', async () => {
    const el: HTMLElement = (await render()).nativeElement;

    expect(el.querySelector('h2')?.textContent?.trim()).toBe('New order');
    expect(el.querySelectorAll('mat-select').length).toBe(3);
    expect(el.querySelectorAll('mat-label').length).toBe(5);
  });

  it('should require the order text', async () => {
    const fixture = await render();

    await submit(fixture);

    expect(errors(fixture)).toEqual(['Order is required']);
    expect(orderService.placeOrder).not.toHaveBeenCalled();
  });

  it('should reject a code without a code system', async () => {
    const fixture = await render();
    await type(fixture, 'display', 'MRI lumbar spine');
    await type(fixture, 'code', '72148');

    await submit(fixture);

    expect(errors(fixture)).toEqual([
      'Give both a code system and a code, or neither',
    ]);
    expect(orderService.placeOrder).not.toHaveBeenCalled();
  });

  it('should place the order and show the coverage badge, card and documentation action', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-16T12:00:00'));
    const fixture = await render();
    await fillMri(fixture);
    await submit(fixture);
    vi.useRealTimers();

    expect(orderService.placeOrder).toHaveBeenCalledWith({
      patientId: 'p1',
      encounterId: null,
      practitionerId: 'dr1',
      display: 'MRI lumbar spine without contrast',
      codeSystem: CPT,
      code: '72148',
      category: 'imaging',
      status: 'active',
      priority: 'routine',
      authoredOn: '2026-11-16',
    });
    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    );
    expect(button.disabled).toBe(true);
    expect(button.textContent?.trim()).toBe('Placing…');

    saves[0].next(mri);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('h2')?.textContent?.trim()).toBe('Order placed');
    expect(el.querySelector('.cp-status-badge')?.textContent?.trim()).toBe(
      'PA needed',
    );
    expect(el.querySelector('.app-crd-card__summary')?.textContent).toBe(
      'Prior authorization needed: MRI lumbar spine without contrast',
    );
    expect(el.textContent).toContain('Complete documentation');
    expect(dialogRef.disableClose).toBe(true);
    expect(dialogRef.close).not.toHaveBeenCalled();

    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Done')
      ?.click();
    expect(dialogRef.close).toHaveBeenCalledWith(mri);
  });

  it('should send an uncoded order without a coding', async () => {
    const fixture = await render();
    await type(fixture, 'display', 'Chest X-ray');

    await submit(fixture);

    expect(orderService.placeOrder).toHaveBeenCalledWith(
      expect.objectContaining({ codeSystem: null, code: null }),
    );
  });

  it('should show the server message when the order fails', async () => {
    const fixture = await render();
    await type(fixture, 'display', 'Chest X-ray');
    await submit(fixture);

    saves[0].error(
      new HttpErrorResponse({
        status: 400,
        error: { status: 400, message: 'priority is required' },
      }),
    );
    await fixture.whenStable();

    expect(
      fixture.nativeElement
        .querySelector('[role="alert"]')
        ?.textContent?.trim(),
    ).toBe('priority is required');
    expect(dialogRef.disableClose).toBe(false);
  });

  it('should fall back to a generic message for other errors', async () => {
    const fixture = await render();
    await type(fixture, 'display', 'Chest X-ray');
    await submit(fixture);

    saves[0].error(new Error('offline'));
    await fixture.whenStable();

    expect(
      fixture.nativeElement
        .querySelector('[role="alert"]')
        ?.textContent?.trim(),
    ).toBe('Could not place the order. Try again.');
  });
});
