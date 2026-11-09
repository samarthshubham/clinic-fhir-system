import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { MemberSearchComponent } from './member-search.component';
import { MemberService } from '@features/members/member.service';
import { MemberSummary, PageResponse } from '@features/members/member.models';

const priya: MemberSummary = {
  id: 'm1',
  memberId: 'PHP-10001',
  firstName: 'Priya',
  lastName: 'Nair',
  dateOfBirth: '1985-03-14',
  gender: 'female',
  planName: 'Praxis Gold PPO',
  coverageStatus: 'active',
};

const aisha: MemberSummary = {
  id: 'm8',
  memberId: 'PHP-10008',
  firstName: 'Aisha',
  lastName: 'Chen',
  dateOfBirth: null,
  gender: 'female',
  planName: null,
  coverageStatus: null,
};

function page(...items: MemberSummary[]): PageResponse<MemberSummary> {
  return { items, total: items.length };
}

describe('MemberSearchComponent', () => {
  let search: ReturnType<
    typeof vi.fn<
      (criteria: {
        name: string;
        memberId: string;
      }) => Observable<PageResponse<MemberSummary>>
    >
  >;

  beforeEach(async () => {
    search = vi.fn(() => of(page(priya, aisha)));
    await TestBed.configureTestingModule({
      imports: [MemberSearchComponent],
      providers: [
        provideRouter([]),
        { provide: MemberService, useValue: { search } },
      ],
    }).compileComponents();
  });

  afterEach(() => vi.useRealTimers());

  async function render(): Promise<ComponentFixture<MemberSearchComponent>> {
    const fixture = TestBed.createComponent(MemberSearchComponent);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  function rows(fixture: ComponentFixture<MemberSearchComponent>): string[] {
    return Array.from(
      fixture.nativeElement.querySelectorAll('tr.mat-mdc-row'),
      (row: Element) =>
        Array.from(row.querySelectorAll('td'), (cell) =>
          cell.textContent?.trim(),
        ).join(' '),
    );
  }

  function type(
    fixture: ComponentFixture<MemberSearchComponent>,
    name: string,
    value: string,
  ) {
    const input: HTMLInputElement = fixture.nativeElement.querySelector(
      `input[formControlName="${name}"]`,
    );
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('should list every member on load, sorted by name, with links to the detail', async () => {
    const fixture = await render();

    expect(search).toHaveBeenCalledExactlyOnceWith({ name: '', memberId: '' });
    expect(rows(fixture)).toEqual([
      'PHP-10008 Chen, Aisha — — —',
      'PHP-10001 Nair, Priya 14 Mar 1985 Praxis Gold PPO Active',
    ]);
    const link: HTMLAnchorElement = fixture.nativeElement.querySelector(
      'tr.mat-mdc-row:last-child a',
    );
    expect(link.getAttribute('href')).toBe('/m1');
    expect(
      fixture.nativeElement.querySelector('.cp-status-badge--success')
        ?.textContent,
    ).toContain('Active');
    expect(
      fixture.nativeElement.querySelector('td.cp-font-mono')?.textContent,
    ).toContain('PHP-10008');
    expect(fixture.nativeElement.textContent).toContain('2 members');
  });

  it('should sort by another column when its header is clicked', async () => {
    const fixture = await render();

    fixture.nativeElement
      .querySelector('th.mat-column-memberId .mat-sort-header-container')
      .click();
    fixture.detectChanges();

    expect(rows(fixture)[0]).toContain('PHP-10001');
  });

  it('should page the results', async () => {
    search.mockReturnValue(
      of(
        page(
          ...Array.from({ length: 30 }, (_, i) => ({
            ...priya,
            id: `m${i}`,
            lastName: `Member${String(i).padStart(2, '0')}`,
          })),
        ),
      ),
    );
    const fixture = await render();

    expect(rows(fixture)).toHaveLength(25);
    expect(fixture.nativeElement.textContent).toContain('30 members');
  });

  it('should search by trimmed name and member id once typing pauses', async () => {
    const fixture = await render();
    vi.useFakeTimers();
    search.mockClear();

    type(fixture, 'name', ' Nair ');
    type(fixture, 'memberId', 'PHP-10001');
    vi.advanceTimersByTime(249);
    expect(search).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);

    expect(search).toHaveBeenCalledExactlyOnceWith({
      name: 'Nair',
      memberId: 'PHP-10001',
    });
  });

  it('should not search again when only whitespace changed', async () => {
    const fixture = await render();
    vi.useFakeTimers();
    search.mockClear();

    type(fixture, 'name', '   ');
    vi.advanceTimersByTime(250);

    expect(search).not.toHaveBeenCalled();
  });

  it('should show an empty state when nothing matches and clear the search', async () => {
    search.mockReturnValue(of(page()));
    const fixture = await render();
    vi.useFakeTimers();

    expect(
      fixture.nativeElement.querySelector('[role="status"]')?.textContent,
    ).toContain('No members match');

    type(fixture, 'name', 'Zed');
    vi.advanceTimersByTime(250);
    search.mockClear();
    fixture.nativeElement
      .querySelector('form button[type="button"]')
      .dispatchEvent(new Event('click'));
    vi.advanceTimersByTime(250);

    expect(search).toHaveBeenCalledExactlyOnceWith({ name: '', memberId: '' });
  });

  it('should show a progress bar while loading', async () => {
    const pending = new Subject<PageResponse<MemberSummary>>();
    search.mockReturnValue(pending);
    const fixture = await render();

    expect(
      fixture.nativeElement.querySelector('mat-progress-bar'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('.app-member-search__table').hidden,
    ).toBe(true);
  });

  it('should show an error and retry', async () => {
    search.mockReturnValueOnce(throwError(() => new Error('boom')));
    const fixture = await render();

    const alert: HTMLElement =
      fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert.textContent).toContain('Could not load members');

    alert.querySelector('button')?.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(search).toHaveBeenCalledTimes(2);
    expect(rows(fixture)).toHaveLength(2);
  });
});
