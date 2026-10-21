import { TestBed } from '@angular/core/testing';
import { CpTimelinePanelComponent } from './cp-timeline-panel.component';
import { TimelineEvent } from '../../dashboard.model';

function event(id: string, type: string, date: string): TimelineEvent {
  return {
    id,
    type,
    date,
    title: id,
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

const events = [
  event('a', 'encounter', '2024-06-08'),
  event('b', 'observation', '2024-06-08'),
  event('c', 'report', '2024-06-05'),
  event('d', 'encounter', '2024-06-01'),
];

class FakeIntersectionObserver {
  static instances: FakeIntersectionObserver[] = [];
  readonly observe = vi.fn();
  readonly disconnect = vi.fn();

  constructor(
    readonly callback: IntersectionObserverCallback,
    readonly options?: IntersectionObserverInit,
  ) {
    FakeIntersectionObserver.instances.push(this);
  }

  trigger(isIntersecting: boolean): void {
    this.callback(
      [{ isIntersecting } as IntersectionObserverEntry],
      this as unknown as IntersectionObserver,
    );
  }
}

describe('CpTimelinePanelComponent', () => {
  beforeEach(async () => {
    FakeIntersectionObserver.instances = [];
    vi.stubGlobal('IntersectionObserver', FakeIntersectionObserver);
    await TestBed.configureTestingModule({
      imports: [CpTimelinePanelComponent],
    }).compileComponents();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  async function render(evs: TimelineEvent[] = events) {
    const fixture = TestBed.createComponent(CpTimelinePanelComponent);
    fixture.componentRef.setInput('events', evs);
    await fixture.whenStable();
    return fixture;
  }

  it('should group consecutive events by date', async () => {
    const fixture = await render();
    const c = fixture.componentInstance;

    expect(c.groups().map((g) => [g.date, g.items.map((e) => e.id)])).toEqual([
      ['2024-06-08', ['a', 'b']],
      ['2024-06-05', ['c']],
      ['2024-06-01', ['d']],
    ]);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.panel-count')?.textContent).toBe('4 events');
    expect(el.querySelectorAll('cp-timeline-group').length).toBe(3);
    expect(el.querySelector('.tl-end')).not.toBeNull();
    expect(el.querySelector('.tl-empty')).toBeNull();
  });

  it('should filter by type and collapse the expanded event', async () => {
    const fixture = await render();
    const c = fixture.componentInstance;
    c.toggleExpanded('a');

    c.setFilter('encounter');

    expect(c.filtered().map((e) => e.id)).toEqual(['a', 'd']);
    expect(c.expandedId()).toBeNull();
  });

  it('should apply a filter when its pill is clicked', async () => {
    const fixture = await render();
    const pills: HTMLButtonElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('.pill'),
    );

    pills.find((p) => p.textContent?.trim() === 'Labs')?.click();
    await fixture.whenStable();

    expect(fixture.componentInstance.activeFilter()).toBe('report');
    expect(
      fixture.nativeElement.querySelector('.pill.active').textContent.trim(),
    ).toBe('Labs');
    expect(
      fixture.nativeElement.querySelector('.panel-count').textContent,
    ).toBe('1 events');
  });

  it('should show the empty state when nothing matches', async () => {
    const fixture = await render();
    fixture.componentInstance.setFilter('appointment');
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('.tl-empty')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.tl-end')).toBeNull();
  });

  it('should toggle the expanded event from a card click', async () => {
    const fixture = await render();
    const c = fixture.componentInstance;

    fixture.nativeElement.querySelector('.tl-event').click();
    expect(c.expandedId()).toBe('a');

    c.toggleExpanded('a');
    expect(c.expandedId()).toBeNull();
  });

  it('should pick up new events when the input changes', async () => {
    const fixture = await render([]);
    expect(fixture.componentInstance.filtered()).toEqual([]);

    fixture.componentRef.setInput('events', events.slice(0, 1));
    await fixture.whenStable();

    expect(fixture.componentInstance.filtered().map((e) => e.id)).toEqual([
      'a',
    ]);
  });

  it('should emit loadMore when the sentinel scrolls into view', async () => {
    const fixture = await render();
    const loadMore = vi.fn();
    fixture.componentInstance.loadMore.subscribe(loadMore);
    const [observer] = FakeIntersectionObserver.instances;

    expect(observer.observe).toHaveBeenCalledWith(
      fixture.nativeElement.querySelector('.tl-sentinel'),
    );
    expect(observer.options).toEqual({ rootMargin: '120px' });

    observer.trigger(false);
    expect(loadMore).not.toHaveBeenCalled();

    observer.trigger(true);
    expect(loadMore).toHaveBeenCalledTimes(1);
  });

  it('should disconnect the observer on destroy', async () => {
    const fixture = await render();
    const [observer] = FakeIntersectionObserver.instances;

    fixture.destroy();

    expect(observer.disconnect).toHaveBeenCalled();
  });
});
