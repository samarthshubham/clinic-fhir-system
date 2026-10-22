import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { PatientRosterComponent } from './patient-roster.component';
import { PatientService } from '@features/patients/services/patient.service';
import { PatientListItem } from '@features/patients/models/patient-list-item.model';
import { computed, signal } from '@angular/core';
import { AuthService } from '@core/auth.service';
import { AuthUser, Role } from '@core/auth.models';

function fakeAuth(...roles: Role[]) {
  const user = signal<AuthUser | null>(
    roles.length
      ? {
          username: 'clinician',
          name: 'Anika Patel',
          title: 'Internal Medicine',
          practitionerId: 'user-clinician',
          roles,
        }
      : null,
  );
  return {
    user,
    isAuthenticated: computed(() => user() !== null),
    hasRole: (...wanted: Role[]) =>
      wanted.some((role) => user()?.roles.includes(role) ?? false),
    logout: vi.fn(),
  };
}

const patients: PatientListItem[] = [
  {
    id: 'p1',
    name: 'Jane Doe',
    gender: 'female',
    dob: '1980-06-15',
    status: 'active',
  },
  {
    id: 'p2',
    name: 'John Smith',
    gender: 'male',
    dob: '1972-01-03',
    status: 'inactive',
    lastEncounter: '2024-05-01',
  },
  {
    id: 'p3',
    name: 'Ana Maria Lopez',
    gender: 'female',
    dob: '',
    status: 'active',
  },
];

describe('PatientRosterComponent', () => {
  let calls: Subject<PatientListItem[]>[];
  let navigate: ReturnType<typeof vi.fn>;
  let auth: ReturnType<typeof fakeAuth>;

  beforeEach(async () => {
    vi.useFakeTimers();
    calls = [];
    auth = fakeAuth('CLINICIAN');
    await TestBed.configureTestingModule({
      imports: [PatientRosterComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        {
          provide: PatientService,
          useValue: {
            getPatients: vi.fn(() => {
              const response = new Subject<PatientListItem[]>();
              calls.push(response);
              return response;
            }),
          },
        },
      ],
    }).compileComponents();
    navigate = vi
      .spyOn(TestBed.inject(Router), 'navigate')
      .mockResolvedValue(true) as unknown as ReturnType<typeof vi.fn>;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function render(): ComponentFixture<PatientRosterComponent> {
    const fixture = TestBed.createComponent(PatientRosterComponent);
    fixture.detectChanges();
    return fixture;
  }

  async function settle(fixture: ComponentFixture<PatientRosterComponent>) {
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(250);
    fixture.detectChanges();
  }

  async function loaded() {
    const fixture = render();
    calls[0].next(patients);
    await settle(fixture);
    return fixture;
  }

  async function search(
    fixture: ComponentFixture<PatientRosterComponent>,
    term: string,
  ) {
    const input: HTMLInputElement = fixture.nativeElement.querySelector(
      '.roster__search-input',
    );
    input.value = term;
    input.dispatchEvent(new Event('input'));
    await settle(fixture);
  }

  function rowNames(fixture: ComponentFixture<PatientRosterComponent>) {
    return Array.from(
      fixture.nativeElement.querySelectorAll('.roster__name'),
    ).map((n) => (n as HTMLElement).textContent);
  }

  function summary(fixture: ComponentFixture<PatientRosterComponent>) {
    return fixture.nativeElement
      .querySelector('.roster__summary')
      .textContent.replace(/\s+/g, ' ')
      .trim();
  }

  it('should show the loading state until patients arrive', () => {
    const fixture = render();
    const el: HTMLElement = fixture.nativeElement;

    expect(fixture.componentInstance.isLoading()).toBe(true);
    expect(el.querySelector('[aria-label="Loading patients"]')).not.toBeNull();
    expect(el.querySelector<HTMLElement>('.roster__table-wrap')?.hidden).toBe(
      true,
    );
  });

  it('should render patients sorted by name with counts', async () => {
    const fixture = await loaded();
    const el: HTMLElement = fixture.nativeElement;

    expect(fixture.componentInstance.isLoading()).toBe(false);
    expect(el.querySelector<HTMLElement>('.roster__table-wrap')?.hidden).toBe(
      false,
    );
    expect(rowNames(fixture)).toEqual([
      'Ana Maria Lopez',
      'Jane Doe',
      'John Smith',
    ]);
    expect(
      Array.from(el.querySelectorAll('.roster__mini-avatar')).map((a) =>
        a.textContent?.trim(),
      ),
    ).toEqual(['AM', 'JD', 'JS']);
    expect(el.querySelectorAll('.px-chip.is-inactive').length).toBe(1);
    expect(summary(fixture)).toBe('3 patients');
  });

  it('should show an error and retry on demand', async () => {
    const fixture = render();
    calls[0].error(new Error('down'));
    await settle(fixture);
    const el: HTMLElement = fixture.nativeElement;

    expect(fixture.componentInstance.hasError()).toBe(true);
    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      'Could not load patients',
    );

    el.querySelector<HTMLButtonElement>('.roster__btn-stroked')?.click();
    await settle(fixture);

    expect(calls.length).toBe(2);
    expect(fixture.componentInstance.isLoading()).toBe(true);

    calls[1].next(patients);
    await settle(fixture);
    expect(fixture.componentInstance.hasError()).toBe(false);
    expect(rowNames(fixture).length).toBe(3);
  });

  it('should filter by name case-insensitively', async () => {
    const fixture = await loaded();

    await search(fixture, '  JANE ');

    expect(fixture.componentInstance.searchTerm()).toBe('JANE');
    expect(rowNames(fixture)).toEqual(['Jane Doe']);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.roster__summary-num')?.textContent?.trim()).toBe(
      '1',
    );
    expect(el.querySelector('.roster__summary-of')?.textContent).toBe('of 3');
  });

  it('should filter by date of birth', async () => {
    const fixture = await loaded();

    await search(fixture, '1972');

    expect(rowNames(fixture)).toEqual(['John Smith']);
  });

  it('should show the no-match state and clear the search', async () => {
    const fixture = await loaded();
    await search(fixture, 'zzz');
    const el: HTMLElement = fixture.nativeElement;

    expect(el.textContent).toContain('No patients match your search');
    expect(el.querySelector<HTMLElement>('.roster__table-wrap')?.hidden).toBe(
      true,
    );

    el.querySelector<HTMLButtonElement>('.roster__btn-text')?.click();
    await settle(fixture);

    expect(fixture.componentInstance.searchTerm()).toBe('');
    expect(
      el.querySelector<HTMLInputElement>('.roster__search-input')?.value,
    ).toBe('');
    expect(rowNames(fixture).length).toBe(3);
  });

  it('should clear the search from the clear button', async () => {
    const fixture = await loaded();
    await search(fixture, 'jane');

    fixture.nativeElement.querySelector('.roster__search-clear').click();
    await settle(fixture);

    expect(
      fixture.nativeElement.querySelector('.roster__search-clear'),
    ).toBeNull();
    expect(rowNames(fixture).length).toBe(3);
  });

  it('should open a patient from a row click or keyboard', async () => {
    const fixture = await loaded();
    const row: HTMLElement =
      fixture.nativeElement.querySelector('.roster__row');

    row.click();
    row.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    const space = new KeyboardEvent('keydown', { key: ' ', cancelable: true });
    row.dispatchEvent(space);

    expect(navigate).toHaveBeenCalledTimes(3);
    expect(navigate).toHaveBeenCalledWith(['/patients', 'p3']);
    expect(space.defaultPrevented).toBe(true);
  });

  it('should open a patient once from the row action button', async () => {
    const fixture = await loaded();

    fixture.nativeElement.querySelector('.roster__chev').click();

    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith(['/patients', 'p3']);
  });

  it('should not open charts for the front desk', async () => {
    auth.user.set({
      username: 'frontdesk',
      name: 'Jordan Lee',
      title: null,
      practitionerId: null,
      roles: ['FRONT_DESK'],
    });
    const fixture = await loaded();
    const row: HTMLElement =
      fixture.nativeElement.querySelector('.roster__row');

    row.click();

    expect(fixture.nativeElement.querySelector('.roster__chev')).toBeNull();
    expect(row.classList).toContain('roster__row--static');
    expect(row.hasAttribute('tabindex')).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('should do nothing yet for the new patient button', async () => {
    const fixture = await loaded();

    fixture.nativeElement.querySelector('.roster__btn-new').click();

    expect(navigate).not.toHaveBeenCalled();
  });

  it('should build initials from at most two name parts', () => {
    const c = render().componentInstance;

    expect(c.initials('ana maria lopez')).toBe('AM');
    expect(c.initials('Cher')).toBe('C');
  });
});
