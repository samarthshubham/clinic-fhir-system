import { TestBed } from '@angular/core/testing';
import { CpTimelineEventComponent } from './cp-timeline-event.component';
import {
  DataPoint,
  GroupedObservation,
  TimelineEvent,
  TrendsResponse,
} from '@features/dashboard/dashboard.model';
import { ToastService } from '@core/toast.service';

function event(overrides: Partial<TimelineEvent> = {}): TimelineEvent {
  return {
    id: 'e1',
    type: 'encounter',
    date: '2024-06-08',
    title: 'Follow-up visit',
    subtitle: null,
    status: null,
    resourceId: 'Encounter/e1',
    metadata: {},
    chiefComplaint: null,
    note: null,
    groupedObservations: null,
    linkedTo: null,
    linkedFrom: null,
    ...overrides,
  };
}

function obs(type: string, value: number, flagged = false): GroupedObservation {
  return { type, display: type, value, unit: 'u', flagged };
}

function series(values: number[]): DataPoint[] {
  return values.map((value, i) => ({
    date: `2024-06-0${i + 1}`,
    value,
  }));
}

const trends: TrendsResponse = {
  period: '30d',
  from: '2024-06-01',
  to: '2024-06-08',
  series: {
    bp: {
      systolic: series([150, 148, 146, 144, 142, 140, 138, 130]),
      diastolic: [],
      referenceRange: { systolicMax: 135 },
    },
    weight: { values: series([80, 81.25]), unit: 'kg', referenceRange: {} },
    spo2: { values: series([95, 95.01]), unit: '%', referenceRange: {} },
  },
};

describe('CpTimelineEventComponent', () => {
  beforeEach(async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2024-06-10T12:00:00'));
    await TestBed.configureTestingModule({
      imports: [CpTimelineEventComponent],
    }).compileComponents();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  async function render(
    ev: TimelineEvent,
    inputs: Partial<{
      trends: TrendsResponse | null;
      expanded: boolean;
      density: 'compact' | 'comfortable';
    }> = {},
  ) {
    const fixture = TestBed.createComponent(CpTimelineEventComponent);
    fixture.componentRef.setInput('ev', ev);
    for (const [k, v] of Object.entries(inputs)) {
      fixture.componentRef.setInput(k, v);
    }
    await fixture.whenStable();
    return fixture;
  }

  it('should render the collapsed card', async () => {
    const fixture = await render(
      event({
        date: '2024-06-08T12:00:00',
        subtitle: 'Annual review',
        status: 'finished',
        metadata: { provider: 'Dr. Smith', conclusion: 'Stable' },
        linkedTo: 'r1',
        linkedFrom: 's1',
        groupedObservations: [obs('systolic', 130, true), obs('weight', 80)],
      }),
    );
    const el: HTMLElement = fixture.nativeElement;
    const root = el.querySelector('.tl-event');

    expect(root?.classList.contains('tl-encounter')).toBe(true);
    expect(root?.classList.contains('future')).toBe(false);
    expect(root?.classList.contains('expanded')).toBe(false);
    expect(el.querySelector('.tl-typetag')?.textContent).toBe('Encounter');
    expect(el.querySelector('.tl-title')?.textContent).toBe('Follow-up visit');
    expect(el.querySelector('.tl-status.status-finished')).not.toBeNull();
    expect(el.querySelector('.tl-card-meta span')?.textContent).toBe('Jun 8');
    expect(el.querySelector('.tl-subtitle')?.textContent).toBe('Annual review');
    expect(el.querySelector('.tl-provider')?.textContent).toContain(
      'Dr. Smith',
    );
    expect(el.querySelectorAll('.tl-obs').length).toBe(2);
    expect(
      el.querySelector('.tl-obs.obs-bp')?.classList.contains('flagged'),
    ).toBe(true);
    expect(el.querySelector('.tl-link:not(.from)')).not.toBeNull();
    expect(el.querySelector('.tl-link.from')).not.toBeNull();
    expect(el.querySelector('.tl-conclusion')?.textContent).toBe('Stable');
    expect(el.querySelector('.tl-expand')).toBeNull();
  });

  it('should hide observation grid and conclusion in compact density', async () => {
    const fixture = await render(
      event({
        metadata: { conclusion: 'Stable' },
        groupedObservations: [obs('weight', 80)],
      }),
      { density: 'compact' },
    );
    const c = fixture.componentInstance;

    expect(c.showObsGrid).toBe(false);
    expect(c.showConclusion).toBe(false);
    expect(fixture.nativeElement.querySelector('.tl-obs-grid')).toBeNull();
  });

  it('should fall back to generic metadata for unknown types', async () => {
    const c = (await render(event({ type: 'mystery' }))).componentInstance;

    expect(c.meta).toEqual({ label: 'mystery', color: '#888', icon: 'help' });
    expect(c.provider).toBeNull();
    expect(c.conclusion).toBeNull();
    expect(c.actions).toEqual([
      { label: 'Open in chart', primary: false },
      { label: 'View full record', primary: true },
    ]);
  });

  it('should mark future events', async () => {
    const fixture = await render(
      event({ type: 'appointment', date: '2024-07-01' }),
    );

    expect(fixture.componentInstance.isFuture).toBe(true);
    expect(
      fixture.nativeElement.querySelector('.tl-event').classList,
    ).toContain('future');
  });

  it.each([
    ['encounter', 'Schedule revisit'],
    ['report', 'Flag for review'],
    ['service-request', 'Mark complete'],
    ['appointment', 'Open visit prep'],
    ['condition', 'View related'],
  ])('should offer type-specific actions for %s', async (type, primary) => {
    const c = (await render(event({ type }))).componentInstance;

    expect(c.actions.length).toBe(3);
    expect(c.actions.find((a) => a.primary)?.label).toBe(primary);
  });

  it('should emit select with the event id when clicked', async () => {
    const fixture = await render(event());
    const select = vi.fn();
    fixture.componentInstance.eventSelect.subscribe(select);

    fixture.nativeElement.querySelector('.tl-event').click();

    expect(select).toHaveBeenCalledWith('e1');
  });

  it('should emit select from the keyboard with Enter or Space', async () => {
    const fixture = await render(event());
    const select = vi.fn();
    fixture.componentInstance.eventSelect.subscribe(select);
    const card: HTMLElement = fixture.nativeElement.querySelector('.tl-event');
    const space = new KeyboardEvent('keydown', { key: ' ', cancelable: true });

    card.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    card.dispatchEvent(space);

    expect(select).toHaveBeenCalledTimes(2);
    expect(space.defaultPrevented).toBe(true);
    expect(card.getAttribute('tabindex')).toBe('0');
  });

  it('should not emit select when pressing Enter on an action button', async () => {
    const fixture = await render(event(), { expanded: true });
    const select = vi.fn();
    fixture.componentInstance.eventSelect.subscribe(select);

    fixture.nativeElement
      .querySelector('.tl-btn-primary')
      .dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }),
      );

    expect(select).not.toHaveBeenCalled();
  });

  it('should compute expanded observation rows with history and deltas', async () => {
    const fixture = await render(
      event({
        groupedObservations: [
          obs('systolic', 130),
          obs('weight', 81.25),
          obs('spo2', 95.01),
          obs('heartRate', 70),
          obs('glucose', 5),
        ],
      }),
      { trends, expanded: true },
    );
    const rows = fixture.componentInstance.expandedObsRows;

    expect(rows.map((r) => r.kind)).toEqual([
      'bp',
      'weight',
      'spo2',
      'hr',
      'other',
    ]);
    expect(rows[0].history.map((p) => p.value)).toEqual([
      148, 146, 144, 142, 140, 138, 130,
    ]);
    expect(rows[0].delta).toEqual({ dir: 'down', value: -8, tone: 'good' });
    expect(rows[0].refMax).toBe(135);
    expect(rows[0].decimals).toBe(0);
    expect(rows[0].color).toBe('var(--px-accent)');
    expect(rows[1].delta).toEqual({ dir: 'up', value: 1.25, tone: 'bad' });
    expect(rows[1].decimals).toBe(1);
    expect(rows[1].refMax).toBeNull();
    expect(rows[2].delta).toEqual({ dir: 'flat', value: 0, tone: 'neutral' });
    expect(rows[3].delta).toBeNull();
    expect(rows[3].history).toEqual([]);
    expect(rows[4].color).toBe('var(--px-text-muted)');

    expect(fixture.componentInstance.formatDelta(rows[0])).toBe('8');
    expect(fixture.componentInstance.formatDelta(rows[1])).toBe('1.3');
    expect(fixture.componentInstance.formatDelta(rows[2])).toBe('');
    expect(fixture.componentInstance.formatDelta(rows[3])).toBe('');

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('.tl-obs-row').length).toBe(5);
    expect(el.querySelectorAll('cp-obs-sparkline').length).toBe(3);
    expect(
      el.querySelector('.tl-obs-row-delta.tone-good')?.textContent,
    ).toContain('arrow_downward');
    expect(
      el.querySelector('.tl-obs-row-delta.tone-bad')?.textContent,
    ).toContain('arrow_upward');
    expect(el.querySelector('.tl-obs-grid')).toBeNull();
  });

  it('should score spo2 as better when it rises and worse when it falls', async () => {
    const up: TrendsResponse = {
      ...trends,
      series: {
        spo2: { values: series([92, 96]), unit: '%', referenceRange: {} },
      },
    };
    const down: TrendsResponse = {
      ...trends,
      series: {
        spo2: { values: series([96, 92]), unit: '%', referenceRange: {} },
      },
    };
    const ev = event({
      date: '2024-06-02',
      groupedObservations: [obs('spo2', 96)],
    });

    const rising = (await render(ev, { trends: up })).componentInstance;
    const falling = (await render(ev, { trends: down })).componentInstance;

    expect(rising.expandedObsRows[0].delta?.tone).toBe('good');
    expect(falling.expandedObsRows[0].delta?.tone).toBe('bad');
  });

  it('should use the latest point when the event date is not in the series', async () => {
    const ev = event({
      date: '2024-05-01',
      groupedObservations: [obs('weight', 80)],
    });
    const row = (await render(ev, { trends })).componentInstance
      .expandedObsRows[0];

    expect(row.history.map((p) => p.value)).toEqual([80, 81.25]);
    expect(row.delta?.dir).toBe('up');
  });

  it('should have no history without trends or with an empty series', async () => {
    const ev = event({ groupedObservations: [obs('systolic', 130)] });

    const noTrends = (await render(ev)).componentInstance.expandedObsRows[0];
    expect(noTrends.history).toEqual([]);
    expect(noTrends.delta).toBeNull();
    expect(noTrends.refMax).toBeNull();

    const emptySeries: TrendsResponse = {
      ...trends,
      series: { bp: { systolic: [], diastolic: [], referenceRange: {} } },
    };
    const empty = (await render(ev, { trends: emptySeries })).componentInstance
      .expandedObsRows[0];
    expect(empty.history).toEqual([]);
    expect(empty.delta).toBeNull();
  });

  it('should have no prior reading for the first point in a series', async () => {
    const ev = event({
      date: '2024-06-01',
      groupedObservations: [obs('weight', 80)],
    });
    const row = (await render(ev, { trends })).componentInstance
      .expandedObsRows[0];

    expect(row.history.map((p) => p.value)).toEqual([80]);
    expect(row.delta).toBeNull();
  });

  it('should render notes, conclusion and actions when expanded', async () => {
    const fixture = await render(
      event({
        chiefComplaint: 'Headache',
        note: 'Rest advised',
        metadata: { conclusion: 'Stable' },
      }),
      { expanded: true },
    );
    const el: HTMLElement = fixture.nativeElement;
    const show = vi.spyOn(TestBed.inject(ToastService), 'show');
    const select = vi.fn();
    fixture.componentInstance.eventSelect.subscribe(select);

    expect(el.querySelector('.tl-event')?.classList).toContain('expanded');
    expect(
      Array.from(el.querySelectorAll('.tl-note-prose')).map(
        (p) => p.textContent,
      ),
    ).toEqual(['Headache', 'Rest advised']);
    expect(el.querySelector('.tl-expand-prose')?.textContent).toBe('Stable');
    expect(el.querySelector('.tl-conclusion')).toBeNull();
    expect(el.querySelector('.tl-chevron')?.textContent).toBe('expand_more');

    const primary = el.querySelector<HTMLButtonElement>('.tl-btn-primary');
    primary?.click();
    el.querySelector<HTMLElement>('.tl-expand')?.click();

    expect(show).toHaveBeenCalledWith(
      'Schedule revisit is a placeholder in this prototype',
    );
    expect(select).not.toHaveBeenCalled();
  });

  it('should render only the note block that has content', async () => {
    const el: HTMLElement = (
      await render(event({ note: 'Rest advised' }), { expanded: true })
    ).nativeElement;

    expect(el.querySelectorAll('.tl-note-block').length).toBe(1);
  });
});
