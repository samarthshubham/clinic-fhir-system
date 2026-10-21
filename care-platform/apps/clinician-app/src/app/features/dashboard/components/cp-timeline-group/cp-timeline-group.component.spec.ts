import { TestBed } from '@angular/core/testing';
import { CpTimelineGroupComponent } from './cp-timeline-group.component';
import { TimelineEvent } from '../../dashboard.model';

function event(id: string): TimelineEvent {
  return {
    id,
    type: 'encounter',
    date: '2024-06-08',
    title: `Event ${id}`,
    subtitle: null,
    status: null,
    resourceId: id,
    metadata: {},
    chiefComplaint: null,
    note: null,
    groupedObservations: null,
    linkedTo: null,
    linkedFrom: null,
  };
}

describe('CpTimelineGroupComponent', () => {
  beforeEach(async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2024-06-10T12:00:00'));
    await TestBed.configureTestingModule({
      imports: [CpTimelineGroupComponent],
    }).compileComponents();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  async function render(date: string, expandedId: string | null = null) {
    const fixture = TestBed.createComponent(CpTimelineGroupComponent);
    fixture.componentRef.setInput('date', date);
    fixture.componentRef.setInput('events', [event('a'), event('b')]);
    fixture.componentRef.setInput('expandedId', expandedId);
    await fixture.whenStable();
    return fixture;
  }

  it('should render the date stripe and one card per event', async () => {
    const fixture = await render('2024-06-08T09:00:00', 'b');
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('.tl-date-label')?.textContent).toBe(
      'Sat, Jun 8, 2024',
    );
    expect(el.querySelector('.tl-date-rel')?.textContent).toBe('2d ago');
    expect(el.querySelector('.tl-future-tag')).toBeNull();
    const cards = el.querySelectorAll('.tl-event');
    expect(cards.length).toBe(2);
    expect(cards[0].classList.contains('expanded')).toBe(false);
    expect(cards[1].classList.contains('expanded')).toBe(true);
  });

  it('should tag future dates as upcoming', async () => {
    const fixture = await render('2024-06-13T12:00:00');

    expect(fixture.componentInstance.isFuture).toBe(true);
    expect(fixture.componentInstance.daysAgo).toBe('in 3d');
    expect(
      fixture.nativeElement.querySelector('.tl-future-tag'),
    ).not.toBeNull();
  });

  it.each([
    ['2024-06-10T08:00:00', 'today'],
    ['2024-06-09T08:00:00', '1d ago'],
    ['2024-05-20T12:00:00', '21d ago'],
    ['2024-03-10T12:00:00', '3mo ago'],
    ['2022-06-01T12:00:00', '2y ago'],
  ])('should describe %s as %s', async (date, label) => {
    const fixture = await render(date);

    expect(fixture.componentInstance.daysAgo).toBe(label);
  });

  it('should re-emit select from its events', async () => {
    const fixture = await render('2024-06-08T09:00:00');
    const select = vi.fn();
    fixture.componentInstance.select.subscribe(select);

    fixture.nativeElement.querySelectorAll('.tl-event')[1].click();

    expect(select).toHaveBeenCalledWith('b');
  });
});
