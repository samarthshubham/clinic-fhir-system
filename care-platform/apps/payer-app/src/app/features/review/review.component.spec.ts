import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRoute,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import { AuthService } from '@care-platform/auth';
import { of } from 'rxjs';
import { CpPriorAuthReviewComponent } from './review.component';

describe('CpPriorAuthReviewComponent', () => {
  const url = '/api/prior-auths/claim-1';
  const detail = {
    claimId: 'claim-1',
    authorizationReference: 'PHP-PA-1',
    disposition: 'Pended for clinical review',
    pended: true,
    lines: [
      {
        sequence: 1,
        service: 'Cardiac stress test',
        code: '93015',
        rule: {
          name: 'Cardiac stress test',
          requiresPriorAuth: true,
          criteria: [
            {
              linkId: 'exercise',
              operator: 'EQUALS',
              expected: 'true',
              matched: true,
            },
          ],
        },
      },
    ],
    answers: [
      { linkId: 'exercise', question: 'Exercise attempted', values: ['true'] },
    ],
    attachments: [
      { title: 'Stress report', contentType: 'application/pdf', size: 123 },
    ],
  };
  let roles: string[];

  beforeEach(() => {
    roles = ['UM_REVIEWER'];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ id: 'claim-1' })) },
        },
        { provide: AuthService, useValue: { user: () => ({ roles }) } },
      ],
    });
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  function load() {
    const fixture = TestBed.createComponent(CpPriorAuthReviewComponent);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(url).flush(detail);
    fixture.detectChanges();
    return { fixture, http, el: fixture.nativeElement as HTMLElement };
  }

  function button(el: HTMLElement, label: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find(
      (button) => button.textContent.trim() === label,
    )!;
  }

  it('shows the claim, rule match, answers and attachments, and approves a pended request', () => {
    const { fixture, http, el } = load();
    expect(el.textContent).toContain('Cardiac stress test');
    expect(el.textContent).toContain('Matched');
    expect(el.textContent).toContain('Exercise attempted');
    expect(el.textContent).toContain('Stress report');

    button(el, 'Approve').click();
    const request = http.expectOne(url + '/decision');
    expect(request.request.body).toEqual({ decision: 'APPROVE' });
    request.flush({ ...detail, disposition: 'Approved', pended: false });
    fixture.detectChanges();
    expect(el.querySelector('[role=status]')?.textContent).toContain(
      'Decision saved.',
    );
    expect(button(el, 'Approve')).toBeUndefined();
  });

  it('requires both denial fields and sends the reason for a valid denial', () => {
    const { fixture, http, el } = load();
    button(el, 'Deny').click();
    fixture.detectChanges();
    expect(button(el, 'Confirm denial').disabled).toBe(true);
    fixture.componentInstance.submit('DENY');
    http.expectNone(url + '/decision');

    fixture.componentInstance.denial.setValue({
      reasonCode: 'criteria',
      reasonText: '  Missing criteria  ',
    });
    fixture.detectChanges();
    expect(button(el, 'Confirm denial').disabled).toBe(false);
    fixture.componentInstance.submit('DENY');
    const request = http.expectOne(url + '/decision');
    expect(request.request.body).toEqual({
      decision: 'DENY',
      reasonCode: 'criteria',
      reasonText: 'Missing criteria',
    });
    request.flush({ ...detail, disposition: 'Denied', pended: false });
  });

  it('keeps a pended request available and handles a conflict', () => {
    const { fixture, http, el } = load();
    button(el, 'Pend').click();
    const request = http.expectOne(url + '/decision');
    expect(request.request.body).toEqual({ decision: 'PEND' });
    request.flush(detail);
    fixture.detectChanges();
    expect(button(el, 'Approve')).toBeTruthy();

    button(el, 'Approve').click();
    http
      .expectOne(url + '/decision')
      .flush({}, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(el.querySelector('[role=alert]')?.textContent).toContain(
      'Return to the queue',
    );
  });

  it('hides decision actions from read-only staff', () => {
    roles = ['PAYER_ADMIN'];
    const { fixture, http, el } = load();
    expect(button(el, 'Approve')).toBeUndefined();
    fixture.componentInstance.submit('APPROVE');
    http.expectNone(url + '/decision');
  });

  it('shows a loading state followed by a read error', () => {
    const fixture = TestBed.createComponent(CpPriorAuthReviewComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(
      el.querySelector('[aria-label="Loading authorization"]'),
    ).toBeTruthy();
    TestBed.inject(HttpTestingController)
      .expectOne(url)
      .flush({}, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(el.querySelector('[role=alert]')?.textContent).toContain(
      'Could not load',
    );
  });
});
