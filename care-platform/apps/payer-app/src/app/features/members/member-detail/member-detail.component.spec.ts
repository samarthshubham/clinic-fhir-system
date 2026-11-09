import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { NEVER, Observable, of, throwError } from 'rxjs';
import { MemberDetailComponent } from './member-detail.component';
import { MemberService } from '@features/members/member.service';
import { MemberResponse } from '@features/members/member.models';

const priya: MemberResponse = {
  id: 'm1',
  memberId: 'PHP-10001',
  firstName: 'Priya',
  lastName: 'Nair',
  dateOfBirth: '1985-03-14',
  gender: 'female',
  coverages: [
    {
      id: 'c1',
      status: 'active',
      periodStart: '2025-11-09',
      periodEnd: null,
      payerName: 'Praxis Health Plan',
      relationship: 'self',
      plan: {
        id: 'plan-gold',
        planId: 'praxis-gold-ppo',
        name: 'Praxis Gold PPO',
        type: 'PPO',
      },
    },
  ],
  priorAuths: [],
};

describe('MemberDetailComponent', () => {
  let getMember: ReturnType<
    typeof vi.fn<(id: string) => Observable<MemberResponse>>
  >;

  beforeEach(async () => {
    getMember = vi.fn(() => of(priya));
    await TestBed.configureTestingModule({
      imports: [MemberDetailComponent],
      providers: [
        provideRouter([]),
        { provide: MemberService, useValue: { getMember } },
      ],
    }).compileComponents();
  });

  async function render(
    id = 'm1',
  ): Promise<ComponentFixture<MemberDetailComponent>> {
    const fixture = TestBed.createComponent(MemberDetailComponent);
    fixture.componentRef.setInput('id', id);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<MemberDetailComponent>): string {
    return fixture.nativeElement.textContent.replace(/\s+/g, ' ');
  }

  it('should show the member, coverage and plan, with an empty prior auth history', async () => {
    const fixture = await render();

    expect(getMember).toHaveBeenCalledWith('m1');
    expect(fixture.nativeElement.querySelector('h1').textContent).toBe(
      'Nair, Priya',
    );
    expect(text(fixture)).toContain('Member IDPHP-10001');
    expect(text(fixture)).toContain('14 Mar 1985');
    expect(text(fixture)).toContain('Praxis Gold PPO');
    expect(text(fixture)).toContain('Praxis Health Plan · PPO');
    expect(text(fixture)).toContain('09 Nov 2025 – ongoing');
    expect(
      fixture.nativeElement.querySelector('.cp-status-badge--success')
        .textContent,
    ).toBe('Active');
    expect(
      fixture.nativeElement.querySelector(
        'section[aria-labelledby="prior-auth-heading"] [role="status"]',
      ).textContent,
    ).toContain('No prior authorizations');
    expect(
      fixture.nativeElement.querySelector('a[href="/members"]'),
    ).toBeTruthy();
  });

  it('should list prior authorizations when there are some', async () => {
    getMember.mockReturnValue(
      of({
        ...priya,
        gender: null,
        dateOfBirth: null,
        coverages: [
          {
            ...priya.coverages[0],
            status: 'cancelled',
            periodStart: null,
            periodEnd: '2026-10-10',
            payerName: null,
            relationship: null,
            plan: null,
          },
        ],
        priorAuths: [
          {
            id: 'pa1',
            status: 'active',
            submitted: '2026-11-01',
            service: 'Echocardiography',
            outcome: 'complete',
            preAuthRef: 'PA-0001',
          },
          {
            id: 'pa2',
            status: null,
            submitted: null,
            service: null,
            outcome: null,
            preAuthRef: null,
          },
        ],
      }),
    );
    const fixture = await render();

    const rows = Array.from(
      fixture.nativeElement.querySelectorAll('tr.mat-mdc-row'),
      (row: Element) => row.textContent?.replace(/\s+/g, ' ').trim(),
    );
    expect(rows).toEqual([
      '01 Nov 2026 Echocardiography Active Complete PA-0001',
      '— — — Pending —',
    ]);
    expect(text(fixture)).toContain('Unknown plan');
    expect(text(fixture)).toContain('— – 10 Oct 2026');
    expect(
      fixture.nativeElement.querySelector('.cp-status-badge--warning'),
    ).toBeTruthy();
  });

  it('should say when a member has no coverage', async () => {
    getMember.mockReturnValue(of({ ...priya, coverages: [] }));
    const fixture = await render();

    expect(text(fixture)).toContain('No coverage on file');
  });

  it('should show a progress bar while loading', async () => {
    getMember.mockReturnValue(NEVER);
    const fixture = await render();

    expect(
      fixture.nativeElement.querySelector('mat-progress-bar'),
    ).toBeTruthy();
  });

  it('should say when the member does not exist', async () => {
    getMember.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404 })),
    );
    const fixture = await render('nope');

    expect(text(fixture)).toContain('Member not found');
  });

  it('should show an error when loading fails', async () => {
    getMember.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 503 })),
    );
    const fixture = await render();

    expect(
      fixture.nativeElement.querySelector('[role="alert"]').textContent,
    ).toContain('Could not load this member');
  });
});
