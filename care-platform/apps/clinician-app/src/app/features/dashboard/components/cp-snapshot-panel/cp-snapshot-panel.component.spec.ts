import { TestBed } from '@angular/core/testing';
import { CpSnapshotPanelComponent } from './cp-snapshot-panel.component';
import {
  LatestVitals,
  SnapshotResponse,
  VitalReading,
} from '@features/dashboard/dashboard.model';

function reading(
  value: number,
  flagged = false,
  date = '2024-06-10',
): VitalReading {
  return { value, unit: '', date, flagged };
}

const vitals: LatestVitals = {
  systolicBp: reading(151.6, true),
  diastolicBp: reading(94.2),
  weightKg: reading(80.5),
  spo2Percent: reading(93, true),
  heartRate: reading(71.6, true),
  temperature: reading(37.25, true),
};

const snapshot: SnapshotResponse = {
  activeConditions: [
    {
      id: 'c1',
      display: 'Hypertension',
      onsetDate: '2020-03-01',
      status: 'active',
    },
    { id: 'c2', display: 'Asthma', onsetDate: null, status: 'active' },
  ],
  currentMedications: [
    { id: 'm1', name: 'Lisinopril', dosage: '10 mg daily', status: 'active' },
    { id: 'm2', name: 'Albuterol', dosage: null, status: 'active' },
  ],
  latestVitals: vitals,
  alerts: [
    { severity: 'critical', type: 'bp', message: 'BP high', resourceId: 'o1' },
  ],
};

describe('CpSnapshotPanelComponent', () => {
  beforeEach(async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2024-06-15T12:00:00'));
    await TestBed.configureTestingModule({
      imports: [CpSnapshotPanelComponent],
    }).compileComponents();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  async function render(s: SnapshotResponse) {
    const fixture = TestBed.createComponent(CpSnapshotPanelComponent);
    fixture.componentRef.setInput('snapshot', s);
    await fixture.whenStable();
    return fixture;
  }

  it('should format the latest vitals', async () => {
    const c = (await render(snapshot)).componentInstance;

    expect(c.bpValue).toBe('152/94');
    expect(c.bpFlagged).toBe(true);
    expect(c.weightValue).toBe('80.5');
    expect(c.spo2Value).toBe('93');
    expect(c.spo2Flagged).toBe(true);
    expect(c.hrValue).toBe('72');
    expect(c.hrFlagged).toBe(true);
    expect(c.tempValue).toBe('37.3');
    expect(c.tempFlagged).toBe(true);
    expect(c.latestVitalsDate).toBe('Jun 10');
    expect(c.daysAgoLabel).toBe('5d ago');
  });

  it('should flag BP when only the diastolic reading is flagged', async () => {
    const c = (
      await render({
        ...snapshot,
        latestVitals: {
          ...vitals,
          systolicBp: reading(120),
          diastolicBp: reading(95, true),
        },
      })
    ).componentInstance;

    expect(c.bpFlagged).toBe(true);
  });

  it('should show dashes when no vitals are available', async () => {
    const c = (await render({ ...snapshot, latestVitals: null }))
      .componentInstance;

    expect(c.bpValue).toBe('–');
    expect(c.bpFlagged).toBe(false);
    expect(c.weightValue).toBe('–');
    expect(c.spo2Value).toBe('–');
    expect(c.spo2Flagged).toBe(false);
    expect(c.hrValue).toBe('–');
    expect(c.hrFlagged).toBe(false);
    expect(c.tempValue).toBe('–');
    expect(c.tempFlagged).toBe(false);
    expect(c.latestVitalsDate).toBe('–');
    expect(c.daysAgoLabel).toBe('');
  });

  it('should show a dash for BP when only one side is present', async () => {
    const c = (
      await render({
        ...snapshot,
        latestVitals: { ...vitals, diastolicBp: null },
      })
    ).componentInstance;

    expect(c.bpValue).toBe('–');
  });

  it('should fall back to weight or heart rate for the vitals date', async () => {
    const c = (
      await render({
        ...snapshot,
        latestVitals: {
          ...vitals,
          systolicBp: null,
          weightKg: null,
          heartRate: reading(70, false, '2024-06-15T08:00:00'),
        },
      })
    ).componentInstance;

    expect(c.latestVitalsDate).toBe('Jun 15');
    expect(c.daysAgoLabel).toBe('today');
  });

  it('should render alerts, conditions and medications', async () => {
    const el: HTMLElement = (await render(snapshot)).nativeElement;

    expect(el.querySelectorAll('cp-vital-cell').length).toBe(6);
    expect(el.querySelector('.alert--critical .alert-msg')?.textContent).toBe(
      'BP high',
    );
    expect(el.querySelector('.alert-meta')?.textContent?.trim()).toBe(
      'CRITICAL',
    );
    expect(el.querySelectorAll('.cond-row').length).toBe(2);
    expect(el.querySelectorAll('.cond-meta').length).toBe(1);
    expect(el.querySelector('.cond-meta')?.textContent).toContain('Mar 20');
    expect(el.querySelectorAll('.med-row').length).toBe(2);
    expect(el.querySelectorAll('.med-dosage').length).toBe(1);
  });

  it('should hide the alerts card when there are no alerts', async () => {
    const el: HTMLElement = (await render({ ...snapshot, alerts: [] }))
      .nativeElement;

    expect(el.querySelector('.alerts-card')).toBeNull();
  });
});
