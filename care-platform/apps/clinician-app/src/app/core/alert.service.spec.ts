import { TestBed } from '@angular/core/testing';
import { computed, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Subscription } from 'rxjs';
import { ALERT_POLL_MS, AlertService } from './alert.service';
import { AlertPage, AlertResponse } from './alert.models';
import { AuthService, Role } from '@care-platform/auth';

function alert(id: string): AlertResponse {
  return {
    id,
    patientId: 'p1',
    observationId: `obs-${id}`,
    reason: 'Systolic BP above 180 mmHg',
    raisedAt: '2026-10-22T10:00:00Z',
    status: 'active',
  };
}

function page(...ids: string[]): AlertPage {
  return { items: ids.map(alert), total: ids.length };
}

describe('AlertService', () => {
  let service: AlertService;
  let http: HttpTestingController;
  let roles: ReturnType<typeof signal<Role[]>>;
  let subscriptions: Subscription;

  beforeEach(() => {
    vi.useFakeTimers();
    roles = signal<Role[]>(['CLINICIAN']);
    const userRoles = computed(() => roles());
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: {
            hasRole: (...wanted: Role[]) =>
              wanted.some((role) => userRoles().includes(role)),
          },
        },
      ],
    });
    service = TestBed.inject(AlertService);
    http = TestBed.inject(HttpTestingController);
    subscriptions = new Subscription();
  });

  afterEach(() => {
    subscriptions.unsubscribe();
    http.verify();
    vi.useRealTimers();
  });

  function collect<T>(source: {
    subscribe: (fn: (v: T) => void) => Subscription;
  }) {
    const values: T[] = [];
    subscriptions.add(source.subscribe((v) => values.push(v)));
    TestBed.tick();
    vi.advanceTimersByTime(0);
    return values;
  }

  function expectPoll() {
    return http.expectOne(
      (req) =>
        req.url === '/api/alerts' && req.params.get('status') === 'active',
    );
  }

  it('should poll active alerts now and every interval for clinical roles', () => {
    const pages = collect(service.activeAlerts$);

    expectPoll().flush(page('a1'));
    vi.advanceTimersByTime(ALERT_POLL_MS);
    expectPoll().flush(page('a1', 'a2'));

    expect(pages.map((p) => p?.total)).toEqual([1, 2]);
  });

  it('should share one poll between subscribers', () => {
    collect(service.activeAlerts$);
    collect(service.activeAlerts$);

    expectPoll().flush(page());
  });

  it('should not poll for the front desk', () => {
    roles.set(['FRONT_DESK']);

    const pages = collect(service.activeAlerts$);

    http.expectNone('/api/alerts');
    expect(pages).toEqual([null]);
  });

  it('should emit null when a poll fails and recover on the next one', () => {
    const pages = collect(service.activeAlerts$);

    expectPoll().flush(null, { status: 500, statusText: 'Server Error' });
    vi.advanceTimersByTime(ALERT_POLL_MS);
    expectPoll().flush(page('a1'));

    expect(pages.map((p) => p?.total ?? null)).toEqual([null, 1]);
  });

  it('should refresh right after acknowledging', () => {
    const pages = collect(service.activeAlerts$);
    expectPoll().flush(page('a1'));

    let acknowledged: AlertResponse | undefined;
    service.acknowledge('a1').subscribe((a) => (acknowledged = a));
    const req = http.expectOne('/api/alerts/a1/acknowledge');
    expect(req.request.method).toBe('POST');
    req.flush({ ...alert('a1'), status: 'inactive' });
    expectPoll().flush(page());

    expect(acknowledged?.status).toBe('inactive');
    expect(pages.map((p) => p?.total)).toEqual([1, 0]);
  });

  it('should count alerts that were not there on the previous poll', () => {
    const counts = collect(service.newAlertCount$);

    expectPoll().flush(page('a1'));
    vi.advanceTimersByTime(ALERT_POLL_MS);
    expectPoll().flush(page('a1'));
    vi.advanceTimersByTime(ALERT_POLL_MS);
    expectPoll().flush(page('a1', 'a2', 'a3'));

    expect(counts).toEqual([2]);
  });
});
