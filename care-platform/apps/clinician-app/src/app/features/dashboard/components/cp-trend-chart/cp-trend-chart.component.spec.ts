import { TestBed } from '@angular/core/testing';
import { CpTrendChartComponent } from './cp-trend-chart.component';
import { BpSeries, SimpleSeries } from '@features/dashboard/dashboard.model';

const weight: SimpleSeries = {
  values: [
    { date: '2024-06-01', value: 10 },
    { date: '2024-06-08', value: 12 },
  ],
  unit: 'kg',
  referenceRange: {},
};

const bp: BpSeries = {
  systolic: [
    { date: '2024-06-01', value: 120 },
    { date: '2024-06-08', value: 140 },
  ],
  diastolic: [
    { date: '2024-06-01', value: 80 },
    { date: '2024-06-08', value: 90 },
  ],
  referenceRange: { systolicMax: 130, diastolicMax: 85 },
};

describe('CpTrendChartComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CpTrendChartComponent],
    }).compileComponents();
  });

  async function render(
    series: BpSeries | SimpleSeries | null,
    secondaryColor: string | null = null,
  ) {
    const fixture = TestBed.createComponent(CpTrendChartComponent);
    fixture.componentRef.setInput('series', series);
    fixture.componentRef.setInput('label', 'Trend');
    fixture.componentRef.setInput('primaryColor', 'blue');
    fixture.componentRef.setInput('secondaryColor', secondaryColor);
    await fixture.whenStable();
    return fixture;
  }

  it('should show the no-data placeholder without a series', async () => {
    const fixture = await render(null);

    expect(fixture.componentInstance.svgData()).toBeNull();
    expect(
      fixture.nativeElement.querySelector('.trend-label').textContent,
    ).toBe('Trend');
    expect(
      fixture.nativeElement.querySelector('.trend-no-data'),
    ).not.toBeNull();
    expect(fixture.nativeElement.querySelector('svg')).toBeNull();
  });

  it('should show the placeholder when a simple series has fewer than two points', async () => {
    const fixture = await render({ ...weight, values: [weight.values[0]] });

    expect(fixture.componentInstance.svgData()).toBeNull();
  });

  it('should show the placeholder when a BP series has fewer than two points', async () => {
    const fixture = await render({
      ...bp,
      systolic: [bp.systolic[0]],
      diastolic: [],
    });

    expect(fixture.componentInstance.svgData()).toBeNull();
  });

  it('should build paths for a simple series', async () => {
    const fixture = await render(weight);
    const d = fixture.componentInstance.svgData();

    expect(d).toMatchObject({
      isBp: false,
      primaryPath: 'M8.0,78.7 L332.0,25.3',
      primaryArea: 'M8.0,78.7 L332.0,25.3 L332.0,92.0 L8.0,92.0 Z',
      secondaryPath: null,
      secondaryDots: [],
      refY: null,
      refY2: null,
      baselineY: 92,
      n: 2,
      startDate: '2024-06-01',
      endDate: '2024-06-08',
      unit: 'kg',
      latestPrimary: 12,
      latestSecondary: null,
    });
    expect(d?.primaryDots.map((p) => p.x)).toEqual([8, 332]);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.trend-val')?.textContent).toBe('12');
    expect(el.querySelector('.trend-unit')?.textContent).toBe('kg');
    expect(el.querySelector('.trend-legend')).toBeNull();
  });

  it('should include a numeric max or upper reference in the scale', async () => {
    const withMax = await render({ ...weight, referenceRange: { max: 13 } });
    expect(withMax.componentInstance.svgData()?.refY).toBe(22);

    const withUpper = await render({
      ...weight,
      referenceRange: { upper: 13 },
    });
    expect(withUpper.componentInstance.svgData()?.refY).toBe(22);

    const nonNumeric = await render({
      ...weight,
      referenceRange: { max: 'x' },
    });
    expect(nonNumeric.componentInstance.svgData()?.refY).toBeNull();
  });

  it('should build systolic and diastolic paths with reference lines for BP', async () => {
    const fixture = await render(bp, 'red');
    const d = fixture.componentInstance.svgData();

    expect(d).toMatchObject({
      isBp: true,
      n: 2,
      unit: 'mmHg',
      latestPrimary: 140,
      latestSecondary: 90,
      startDate: '2024-06-01',
      endDate: '2024-06-08',
    });
    expect(d?.secondaryPath).toMatch(/^M8\.0,[\d.]+ L332\.0,[\d.]+$/);
    expect(d?.secondaryDots.length).toBe(2);
    expect(d?.refY).toBeCloseTo(12 + (14 / 68) * 80);
    expect(d?.refY2).toBeCloseTo(12 + (59 / 68) * 80);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.trend-val--sec')?.textContent).toBe('90');
    expect(el.querySelector('.trend-legend')).not.toBeNull();
    expect(el.querySelectorAll('svg line').length).toBe(3);
  });

  it('should leave reference lines out when BP has no reference range', async () => {
    const fixture = await render({ ...bp, referenceRange: {} });
    const d = fixture.componentInstance.svgData();

    expect(d?.refY).toBeNull();
    expect(d?.refY2).toBeNull();
    expect(fixture.nativeElement.querySelectorAll('svg line').length).toBe(1);
  });

  it('should handle a BP series where one side is empty', async () => {
    const fixture = await render({ ...bp, systolic: [] });
    const d = fixture.componentInstance.svgData();

    expect(d?.latestPrimary).toBeNull();
    expect(d?.latestSecondary).toBe(90);
    expect(d?.startDate).toBe('');
    expect(d?.endDate).toBe('');
  });

  it('should track the hovered point from the mouse position', async () => {
    const fixture = await render(bp, 'red');
    const c = fixture.componentInstance;
    const svg = {
      getBoundingClientRect: () => ({ left: 100, width: 200 }),
    } as Element;

    c.onSvgMouseMove({ clientX: 290 } as MouseEvent, svg);
    expect(c.hoverIdx()).toBe(1);
    expect(c.hoverData()).toMatchObject({
      primary: 140,
      secondary: 90,
      date: '2024-06-08',
    });

    c.onSvgMouseMove({ clientX: 0 } as MouseEvent, svg);
    expect(c.hoverIdx()).toBe(0);

    c.onSvgMouseMove({ clientX: 999 } as MouseEvent, svg);
    expect(c.hoverIdx()).toBe(1);

    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.trend-val')?.textContent).toBe('140');
    expect(el.querySelector('.trend-val--sec')?.textContent).toBe('90');
    expect(el.querySelector('.trend-date')?.textContent).toBe('Jun 8');

    c.onSvgMouseLeave();
    expect(c.hoverData()).toBeNull();
  });

  it('should ignore hover without data or with an out-of-range index', async () => {
    const empty = await render(null);
    empty.componentInstance.onSvgMouseMove(
      { clientX: 10 } as MouseEvent,
      { getBoundingClientRect: () => ({ left: 0, width: 10 }) } as Element,
    );
    expect(empty.componentInstance.hoverIdx()).toBeNull();

    const fixture = await render(weight);
    fixture.componentInstance.hoverIdx.set(5);
    expect(fixture.componentInstance.hoverData()).toBeNull();

    fixture.componentInstance.hoverIdx.set(0);
    expect(fixture.componentInstance.hoverData()).toMatchObject({
      primary: 10,
      secondary: null,
      secondaryDot: null,
    });
  });
});
