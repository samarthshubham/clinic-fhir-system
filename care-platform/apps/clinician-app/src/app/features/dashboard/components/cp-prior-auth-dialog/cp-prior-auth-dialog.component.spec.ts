import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, Subject, throwError } from 'rxjs';
import {
  PriorAuthResponse,
  PriorAuthService,
} from '@features/dashboard/prior-auth.service';
import { CpPriorAuthDialogComponent } from './cp-prior-auth-dialog.component';

describe('CpPriorAuthDialogComponent', () => {
  const response: PriorAuthResponse = {
    orderId: 'order-1',
    status: 'PENDED',
    authorizationNumber: null,
    denialReason: null,
    denialReasonCode: null,
  };
  let fixture: ComponentFixture<CpPriorAuthDialogComponent>;
  let submit: ReturnType<typeof vi.fn>;
  let close: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    submit = vi.fn(() => of(response));
    close = vi.fn();
    await TestBed.configureTestingModule({
      imports: [CpPriorAuthDialogComponent],
      providers: [
        {
          provide: MAT_DIALOG_DATA,
          useValue: { id: 'order-1', code: 'Cardiac stress test' },
        },
        { provide: MatDialogRef, useValue: { close } },
        { provide: PriorAuthService, useValue: { submit } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CpPriorAuthDialogComponent);
    await fixture.whenStable();
  });

  function type(control: string, value: string): void {
    const field: HTMLInputElement = fixture.nativeElement.querySelector(
      `input[formcontrolname="${control}"]`,
    );
    field.value = value;
    field.dispatchEvent(new Event('input'));
  }

  it('requires place of service and attaches billing data to the selected order', () => {
    fixture.componentInstance.submit();
    expect(submit).not.toHaveBeenCalled();
    type('placeOfService', '11');
    type('serviceCode', '93015');
    type('diagnosis', 'R00.0');
    fixture.componentInstance.submit();

    expect(submit).toHaveBeenCalledWith({
      orderId: 'order-1',
      service: { system: 'http://www.ama-assn.org/go/cpt', code: '93015' },
      serviceCategory: '1',
      placeOfService: '11',
      diagnoses: [
        { system: 'http://hl7.org/fhir/sid/icd-10-cm', code: 'R00.0' },
      ],
    });
    expect(close).toHaveBeenCalledWith(response);
  });

  it('keeps one submission in flight and shows a payer error for retry', async () => {
    const pending = new Subject<PriorAuthResponse>();
    submit.mockReturnValueOnce(pending);
    type('placeOfService', '11');
    fixture.componentInstance.submit();
    fixture.componentInstance.submit();
    expect(submit).toHaveBeenCalledTimes(1);

    pending.error(
      new HttpErrorResponse({
        status: 400,
        error: { message: 'Check the billing codes.' },
      }),
    );
    await fixture.whenStable();
    expect(
      fixture.nativeElement.querySelector('[role="alert"]').textContent,
    ).toContain('Check the billing codes.');
    expect(close).not.toHaveBeenCalled();

    submit.mockReturnValueOnce(throwError(() => new Error('offline')));
    fixture.componentInstance.submit();
    await fixture.whenStable();
    expect(
      fixture.nativeElement.querySelector('[role="alert"]').textContent,
    ).toContain('Could not submit prior authorization.');
  });
});
