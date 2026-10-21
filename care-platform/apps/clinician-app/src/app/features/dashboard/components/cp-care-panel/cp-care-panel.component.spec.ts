import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { CpCarePanelComponent } from './cp-care-panel.component';
import { DashboardService } from '@features/dashboard/dashboard.service';
import {
  ActiveCarePlan,
  PendingOrder,
  TrendsResponse,
} from '@features/dashboard/dashboard.model';

function order(
  id: string,
  priority: string,
  category: string | null,
): PendingOrder {
  return {
    id,
    code: `Order ${id}`,
    category,
    orderedOn: '2024-06-01',
    priority,
    status: 'active',
    orderedBy: null,
  };
}

const plan: ActiveCarePlan = {
  id: 'cp1',
  title: 'Hypertension plan',
  status: 'active',
  goals: [
    {
      id: 'g1',
      description: 'Lower BP',
      status: 'active',
      targetValue: 130,
      targetDate: null,
      progress: {
        currentValue: 140,
        targetValue: 130,
        onTrack: true,
        percentToGoal: 50,
        message: null,
        baselineValue: 150,
      },
    },
  ],
};

const trends: TrendsResponse = {
  period: '30d',
  from: '2024-06-01',
  to: '2024-06-08',
  series: {
    bp: {
      systolic: [
        { date: '2024-06-01', value: 140 },
        { date: '2024-06-08', value: 130 },
      ],
      diastolic: [
        { date: '2024-06-01', value: 90 },
        { date: '2024-06-08', value: 85 },
      ],
      referenceRange: {},
    },
    weight: {
      values: [
        { date: '2024-06-01', value: 80 },
        { date: '2024-06-08', value: 79 },
      ],
      unit: 'kg',
      referenceRange: {},
    },
    spo2: {
      values: [
        { date: '2024-06-01', value: 96 },
        { date: '2024-06-08', value: 97 },
      ],
      unit: '%',
      referenceRange: {},
    },
  },
};

describe('CpCarePanelComponent', () => {
  let requests: {
    id: string;
    types: string;
    period: string;
    response: Subject<TrendsResponse>;
  }[];

  beforeEach(async () => {
    requests = [];
    const svc = {
      getTrends: vi.fn((id: string, types: string, period: string) => {
        const response = new Subject<TrendsResponse>();
        requests.push({ id, types, period, response });
        return response;
      }),
    };
    await TestBed.configureTestingModule({
      imports: [CpCarePanelComponent],
      providers: [{ provide: DashboardService, useValue: svc }],
    }).compileComponents();
  });

  async function render(
    patientId = 'p1',
    carePlan: ActiveCarePlan | null = plan,
    pending: PendingOrder[] = [],
  ) {
    const fixture = TestBed.createComponent(CpCarePanelComponent);
    fixture.componentRef.setInput('patientId', patientId);
    fixture.componentRef.setInput('carePlan', carePlan);
    fixture.componentRef.setInput('pending', pending);
    await fixture.whenStable();
    return fixture;
  }

  it('should render the care plan and its goals', async () => {
    const el: HTMLElement = (await render()).nativeElement;

    expect(el.querySelector('.care-plan-title')?.textContent).toBe(
      'Hypertension plan',
    );
    expect(
      el.querySelector('.care-plan-status.care-plan-status--active'),
    ).not.toBeNull();
    expect(el.querySelector('.care-plan-sub')?.textContent?.trim()).toBe(
      '1 goal',
    );
    expect(el.querySelectorAll('cp-goal-row').length).toBe(1);
  });

  it('should show empty states without a plan, goals or orders', async () => {
    const noPlan: HTMLElement = (await render('p1', null)).nativeElement;
    const empties = Array.from(noPlan.querySelectorAll('.care-empty')).map(
      (e) => e.textContent?.trim(),
    );
    expect(empties).toEqual(['No active care plan', 'No pending orders']);

    const noGoals: HTMLElement = (await render('p1', { ...plan, goals: [] }))
      .nativeElement;
    expect(noGoals.querySelector('.care-plan-sub')?.textContent?.trim()).toBe(
      '0 goals',
    );
    expect(noGoals.querySelector('.care-empty')?.textContent?.trim()).toBe(
      'No goals defined',
    );
  });

  it('should toggle the expanded goal', async () => {
    const fixture = await render();
    const c = fixture.componentInstance;

    fixture.nativeElement.querySelector('.goal-row').click();
    expect(c.expandedGoalId()).toBe('g1');

    c.toggleGoal('g1');
    expect(c.expandedGoalId()).toBeNull();
  });

  it('should sort pending orders by priority', async () => {
    const fixture = await render('p1', plan, [
      order('a', 'routine', 'laboratory'),
      order('b', 'unknown', null),
      order('c', 'stat', 'Imaging'),
      order('d', 'urgent', 'referral'),
      order('e', 'asap', 'procedure'),
    ]);
    const el: HTMLElement = fixture.nativeElement;

    expect(fixture.componentInstance.sortedPending().map((o) => o.id)).toEqual([
      'c',
      'e',
      'd',
      'a',
      'b',
    ]);
    expect(el.querySelectorAll('.order-row').length).toBe(5);
    expect(el.querySelectorAll('.order-icon--imaging').length).toBe(1);
    expect(
      el
        .querySelector('.order-priority')
        ?.classList.contains('order-priority--stat'),
    ).toBe(true);
  });

  it('should map order categories to icons', async () => {
    const c = (await render()).componentInstance;

    expect(c.getCategoryIcon('Laboratory')).toBe('biotech');
    expect(c.getCategoryIcon('imaging')).toBe('radiology');
    expect(c.getCategoryIcon('medication')).toBe('pill');
    expect(c.getCategoryIcon('other')).toBe('checklist');
    expect(c.getCategoryIcon(null)).toBe('checklist');
  });

  it('should load trends for the patient and split the series', async () => {
    const fixture = await render();
    const c = fixture.componentInstance;

    expect(requests.map((r) => [r.id, r.types, r.period])).toEqual([
      ['p1', 'bp,spo2,weight', '30d'],
    ]);
    expect(c.isTrendsLoading()).toBe(true);
    expect(
      fixture.nativeElement.querySelector('.trends-loading'),
    ).not.toBeNull();

    requests[0].response.next(trends);
    await fixture.whenStable();

    expect(c.isTrendsLoading()).toBe(false);
    expect(c.bpSeries()).toBe(trends.series['bp']);
    expect(c.weightSeries()).toBe(trends.series['weight']);
    expect(c.spo2Series()).toBe(trends.series['spo2']);
    expect(
      fixture.nativeElement.querySelectorAll('cp-trend-chart svg').length,
    ).toBe(3);
  });

  it('should treat missing or mismatched series as empty', async () => {
    const fixture = await render();
    const c = fixture.componentInstance;

    requests[0].response.next({
      ...trends,
      series: { bp: trends.series['weight'], weight: trends.series['bp'] },
    });

    expect(c.bpSeries()).toBeNull();
    expect(c.weightSeries()).toBeNull();
    expect(c.spo2Series()).toBeNull();
  });

  it('should isolate trend errors to empty charts', async () => {
    const fixture = await render();
    const c = fixture.componentInstance;

    requests[0].response.error(new Error('boom'));
    await fixture.whenStable();

    expect(c.isTrendsLoading()).toBe(false);
    expect(c.bpSeries()).toBeNull();
    expect(c.weightSeries()).toBeNull();
    expect(c.spo2Series()).toBeNull();
    expect(
      fixture.nativeElement.querySelectorAll('.trend-no-data').length,
    ).toBe(3);
  });

  it('should refetch trends when the period changes', async () => {
    const fixture = await render();
    requests[0].response.next(trends);
    await fixture.whenStable();

    const pills: HTMLButtonElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('.period-pill'),
    );
    pills.find((p) => p.textContent?.trim() === '7d')?.click();
    await fixture.whenStable();

    expect(fixture.componentInstance.period()).toBe('7d');
    expect(requests.map((r) => r.period)).toEqual(['30d', '7d']);
    expect(fixture.componentInstance.isTrendsLoading()).toBe(true);
    expect(
      fixture.nativeElement
        .querySelector('.period-pill.active')
        .textContent.trim(),
    ).toBe('7d');
  });

  it('should not fetch trends without a patient id', async () => {
    await render('');

    expect(requests).toEqual([]);
  });
});
