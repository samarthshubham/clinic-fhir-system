import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { DashboardService } from './dashboard.service';
import {
  DashboardResponse,
  TimelineEvent,
  TrendsResponse,
} from './dashboard.model';

interface Call<T> {
  args: unknown[];
  response: Subject<T>;
}

function event(id: string, date: string): TimelineEvent {
  return {
    id,
    type: 'encounter',
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

const dashboard: DashboardResponse = {
  patient: {
    id: 'p1',
    firstName: 'Jane',
    lastName: 'Doe',
    dob: null,
    gender: 'female',
    mrn: 'MRN-1',
    phone: null,
    email: null,
  },
  careTeam: { primaryDoctor: { name: 'Dr. Smith' } },
  snapshot: {
    activeConditions: [],
    currentMedications: [],
    latestVitals: null,
    alerts: [],
  },
  upcomingAppointment: null,
  recentEncounters: [],
  pendingServiceRequests: [
    {
      id: 'o1',
      code: 'CBC',
      category: 'laboratory',
      orderedOn: null,
      priority: 'routine',
      status: 'active',
      orderedBy: null,
    },
  ],
  activeCarePlan: null,
  recentDiagnosticReport: null,
  warnings: [],
};

const trends: TrendsResponse = {
  period: '30d',
  from: '2024-06-01',
  to: '2024-06-08',
  series: {},
};

class FakeIntersectionObserver {
  observe = vi.fn();
  disconnect = vi.fn();
}

describe('DashboardComponent', () => {
  let params: BehaviorSubject<ParamMap>;
  let dashboardCalls: Call<DashboardResponse>[];
  let timelineCalls: Call<TimelineEvent[]>[];
  let trendsCalls: Call<TrendsResponse>[];

  function recorder<T>(calls: Call<T>[]) {
    return vi.fn((...args: unknown[]) => {
      const response = new Subject<T>();
      calls.push({ args, response });
      return response;
    });
  }

  beforeEach(async () => {
    localStorage.clear();
    vi.stubGlobal('IntersectionObserver', FakeIntersectionObserver);
    params = new BehaviorSubject(convertToParamMap({ id: 'p1' }));
    dashboardCalls = [];
    timelineCalls = [];
    trendsCalls = [];
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: ActivatedRoute, useValue: { paramMap: params } },
        {
          provide: DashboardService,
          useValue: {
            getDashboard: recorder(dashboardCalls),
            getTimeline: recorder(timelineCalls),
            getTrends: recorder(trendsCalls),
          },
        },
      ],
    }).compileComponents();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  async function render(): Promise<ComponentFixture<DashboardComponent>> {
    const fixture = TestBed.createComponent(DashboardComponent);
    await fixture.whenStable();
    return fixture;
  }

  function errors(fixture: ComponentFixture<DashboardComponent>): string[] {
    return Array.from(
      fixture.nativeElement.querySelectorAll('.cp-panel-error__message'),
    ).map((e) => (e as HTMLElement).textContent ?? '');
  }

  it('should request every stream for the routed patient and show skeletons', async () => {
    const fixture = await render();
    const el: HTMLElement = fixture.nativeElement;

    expect(fixture.componentInstance.patientId()).toBe('p1');
    expect(dashboardCalls.map((c) => c.args)).toEqual([['p1']]);
    expect(timelineCalls.map((c) => c.args)).toEqual([['p1', 20, undefined]]);
    expect(trendsCalls.map((c) => c.args)).toEqual([['p1']]);
    expect(el.querySelector('cp-patient-header-skeleton')).not.toBeNull();
    expect(el.querySelector('cp-snapshot-skeleton')).not.toBeNull();
    expect(el.querySelector('cp-timeline-skeleton')).not.toBeNull();
    expect(el.querySelector('cp-care-skeleton')).not.toBeNull();
  });

  it('should render patient panels once the dashboard loads', async () => {
    const fixture = await render();
    const c = fixture.componentInstance;

    dashboardCalls[0].response.next(dashboard);
    await fixture.whenStable();

    expect(c.isPatientLoading()).toBe(false);
    expect(c.patientHeader()?.displayName).toBe('Jane Doe');
    expect(c.patientHeader()?.pcp).toBe('Dr. Smith');
    expect(c.snapshotData()).toBe(dashboard.snapshot);
    expect(c.carePlan()).toBeNull();
    expect(c.pendingOrders()).toBe(dashboard.pendingServiceRequests);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('cp-patient-header')).not.toBeNull();
    expect(el.querySelector('cp-snapshot-panel')).not.toBeNull();
    expect(el.querySelector('cp-care-panel')).not.toBeNull();
    expect(el.querySelector('cp-timeline-skeleton')).not.toBeNull();
  });

  it('should isolate a dashboard failure from the timeline', async () => {
    const fixture = await render();
    const c = fixture.componentInstance;

    dashboardCalls[0].response.error(new Error('boom'));
    timelineCalls[0].response.next([event('a', '2024-06-08')]);
    await fixture.whenStable();

    expect(c.isPatientError()).toBe(true);
    expect(c.patientHeader()).toBeNull();
    expect(c.snapshotData()).toBeNull();
    expect(c.carePlan()).toBeNull();
    expect(c.pendingOrders()).toEqual([]);
    expect(errors(fixture)).toEqual([
      'Could not load patient data',
      'Snapshot unavailable',
      'Care plan unavailable',
    ]);
    expect(
      fixture.nativeElement.querySelector('cp-timeline-panel'),
    ).not.toBeNull();
  });

  it('should show a timeline error while patient panels still render', async () => {
    const fixture = await render();
    const c = fixture.componentInstance;

    dashboardCalls[0].response.next(dashboard);
    timelineCalls[0].response.error(new Error('boom'));
    await fixture.whenStable();

    expect(c.isTimelineError()).toBe(true);
    expect(c.timelineData()).toEqual([]);
    expect(errors(fixture)).toEqual(['Timeline unavailable']);
    expect(
      fixture.nativeElement.querySelector('cp-patient-header'),
    ).not.toBeNull();
  });

  it('should expose trends only when they load', async () => {
    const fixture = await render();
    const c = fixture.componentInstance;
    expect(c.trendsData()).toBeNull();

    trendsCalls[0].response.next(trends);
    expect(c.trendsData()).toBe(trends);

    c.reload();
    trendsCalls[1].response.error(new Error('boom'));
    expect(c.trendsData()).toBeNull();
  });

  it('should refetch every stream when retry is clicked', async () => {
    const fixture = await render();
    dashboardCalls[0].response.error(new Error('boom'));
    await fixture.whenStable();

    fixture.nativeElement.querySelector('cp-panel-error button').click();
    await fixture.whenStable();

    expect(dashboardCalls.length).toBe(2);
    expect(timelineCalls.length).toBe(2);
    expect(trendsCalls.length).toBe(2);
    expect(fixture.componentInstance.isPatientLoading()).toBe(true);

    dashboardCalls[1].response.next(dashboard);
    await fixture.whenStable();
    expect(fixture.componentInstance.isPatientError()).toBe(false);
  });

  it('should page the timeline using the last loaded date', async () => {
    const fixture = await render();
    const c = fixture.componentInstance;
    timelineCalls[0].response.next([
      event('a', '2024-06-08'),
      event('b', '2024-06-05'),
    ]);
    timelineCalls[0].response.complete();

    c.onLoadMore();

    expect(timelineCalls[1].args).toEqual(['p1', 20, '2024-06-05']);
    expect(c.isTimelineLoading()).toBe(false);
    expect(c.timelineData().map((e) => e.id)).toEqual(['a', 'b']);

    timelineCalls[1].response.next([event('c', '2024-06-01')]);
    expect(c.timelineData().map((e) => e.id)).toEqual(['a', 'b', 'c']);
  });

  it('should ignore load-more while a page is still loading', async () => {
    const fixture = await render();

    fixture.componentInstance.onLoadMore();

    expect(timelineCalls.length).toBe(1);
  });

  it('should keep loaded events when a later page fails', async () => {
    const fixture = await render();
    const c = fixture.componentInstance;
    timelineCalls[0].response.next([event('a', '2024-06-08')]);
    timelineCalls[0].response.complete();

    c.onLoadMore();
    timelineCalls[1].response.error(new Error('boom'));

    expect(c.isTimelineError()).toBe(false);
    expect(c.timelineData().map((e) => e.id)).toEqual(['a']);
  });

  it('should reset the timeline when the patient changes', async () => {
    const fixture = await render();
    timelineCalls[0].response.next([event('a', '2024-06-08')]);

    params.next(convertToParamMap({ id: 'p2' }));
    await fixture.whenStable();

    expect(fixture.componentInstance.patientId()).toBe('p2');
    expect(dashboardCalls.map((c) => c.args)).toEqual([['p1'], ['p2']]);
    expect(timelineCalls[1].args).toEqual(['p2', 20, undefined]);
    expect(fixture.componentInstance.isTimelineLoading()).toBe(true);
  });

  it('should not refetch when the same patient id is emitted again', async () => {
    await render();

    params.next(convertToParamMap({ id: 'p1' }));

    expect(dashboardCalls.length).toBe(1);
  });

  describe('resizer', () => {
    function grid(fixture: ComponentFixture<DashboardComponent>): HTMLElement {
      return fixture.nativeElement.querySelector('.dashboard__panels');
    }

    it('should restore a saved width within bounds', async () => {
      localStorage.setItem('cp-right-w', '450');
      const fixture = await render();

      expect(grid(fixture).style.getPropertyValue('--px-right-w')).toBe(
        '450px',
      );
    });

    it.each(['100', '900'])('should ignore a saved width of %s', async (w) => {
      localStorage.setItem('cp-right-w', w);
      const fixture = await render();

      expect(grid(fixture).style.getPropertyValue('--px-right-w')).toBe('');
    });

    it('should resize while dragging and persist the width on release', async () => {
      const fixture = await render();
      const c = fixture.componentInstance;
      grid(fixture).style.setProperty('--px-right-w', '400px');
      const resizer = fixture.nativeElement.querySelector(
        '.dashboard__resizer',
      );

      resizer.dispatchEvent(
        new MouseEvent('mousedown', { clientX: 500, cancelable: true }),
      );
      await fixture.whenStable();
      expect(c.isDragging()).toBe(true);
      expect(resizer.classList).toContain('dragging');

      document.dispatchEvent(new MouseEvent('mousemove', { clientX: 450 }));
      expect(grid(fixture).style.getPropertyValue('--px-right-w')).toBe(
        '450px',
      );

      document.dispatchEvent(new MouseEvent('mousemove', { clientX: 0 }));
      expect(grid(fixture).style.getPropertyValue('--px-right-w')).toBe(
        '600px',
      );

      document.dispatchEvent(new MouseEvent('mousemove', { clientX: 900 }));
      expect(grid(fixture).style.getPropertyValue('--px-right-w')).toBe(
        '280px',
      );

      document.dispatchEvent(new MouseEvent('mouseup'));
      expect(c.isDragging()).toBe(false);
      expect(localStorage.getItem('cp-right-w')).toBe('280');
    });

    it('should fall back to the default width when none is set', async () => {
      const fixture = await render();

      fixture.nativeElement
        .querySelector('.dashboard__resizer')
        .dispatchEvent(new MouseEvent('mousedown', { clientX: 500 }));
      document.dispatchEvent(new MouseEvent('mousemove', { clientX: 480 }));

      expect(grid(fixture).style.getPropertyValue('--px-right-w')).toBe(
        '420px',
      );
    });

    it('should ignore mouse moves and releases when not dragging', async () => {
      const fixture = await render();

      document.dispatchEvent(new MouseEvent('mousemove', { clientX: 10 }));
      document.dispatchEvent(new MouseEvent('mouseup'));

      expect(grid(fixture).style.getPropertyValue('--px-right-w')).toBe('');
      expect(localStorage.getItem('cp-right-w')).toBeNull();
    });
  });
});
