import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { PatientAppService } from '../../../shared/services/patient-app.service';
import { PatientSelectService } from '../../../shared/services/patient-select.service';
import { RecordVitalsComponent } from './record-vitals.component';

describe('RecordVitalsComponent', () => {
  let postVitals: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    postVitals = vi.fn(() => of([]));
    await TestBed.configureTestingModule({
      imports: [RecordVitalsComponent],
      providers: [
        provideRouter([]),
        { provide: PatientAppService, useValue: { postVitals } },
        { provide: PatientSelectService, useValue: { selectedId: () => 'p1' } },
        { provide: MatSnackBar, useValue: { open: vi.fn() } },
      ],
    }).compileComponents();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllEnvs();
  });

  async function render(): Promise<ComponentFixture<RecordVitalsComponent>> {
    const fixture = TestBed.createComponent(RecordVitalsComponent);
    await fixture.whenStable();
    return fixture;
  }

  function primaryButton(fixture: ComponentFixture<RecordVitalsComponent>) {
    return fixture.nativeElement.querySelector(
      '.pa-btn-primary',
    ) as HTMLButtonElement;
  }

  async function typeInto(
    fixture: ComponentFixture<RecordVitalsComponent>,
    id: string,
    value: string,
  ) {
    const input: HTMLInputElement = fixture.nativeElement.querySelector(
      `#${id}`,
    );
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function clickThroughAllSteps(
    fixture: ComponentFixture<RecordVitalsComponent>,
  ) {
    for (let i = 0; i < 4; i++) {
      primaryButton(fixture).click();
      await fixture.whenStable();
    }
  }

  it('saves every reading in one request under the local date', async () => {
    vi.stubEnv('TZ', 'Asia/Kolkata');
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-16T00:30:00+05:30'));
    const fixture = await render();

    await clickThroughAllSteps(fixture);

    expect(postVitals).toHaveBeenCalledTimes(1);
    expect(postVitals).toHaveBeenCalledWith({
      patientId: 'p1',
      effectiveDate: '2026-11-16',
      systolicBp: 120,
      diastolicBp: 80,
      weightKg: 70,
      spo2Percent: 97,
    });
  });

  it('does not leave the blood pressure step while a value is missing', async () => {
    const fixture = await render();
    await typeInto(fixture, 'systolic', '');

    primaryButton(fixture).click();
    await fixture.whenStable();

    expect(fixture.componentInstance.step()).toBe(0);
    expect(primaryButton(fixture).disabled).toBe(true);
  });

  it('warns when the blood pressure typed is high', async () => {
    const fixture = await render();
    expect(fixture.nativeElement.querySelector('.pa-step-warn')).toBeNull();

    await typeInto(fixture, 'systolic', '150');

    expect(
      fixture.nativeElement.querySelector('.pa-step-warn')?.textContent,
    ).toContain("That's a bit high");
  });
});
