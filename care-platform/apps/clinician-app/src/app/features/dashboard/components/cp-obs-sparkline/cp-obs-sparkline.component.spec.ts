import { TestBed } from '@angular/core/testing';
import { CpObsSparklineComponent } from './cp-obs-sparkline.component';
import { DataPoint } from '@features/dashboard/dashboard.model';

const points: DataPoint[] = [
  { date: '2024-01-01', value: 120 },
  { date: '2024-01-02', value: 140 },
  { date: '2024-01-03', value: 130 },
];

describe('CpObsSparklineComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CpObsSparklineComponent],
    }).compileComponents();
  });

  async function render(inputs: Record<string, unknown>) {
    const fixture = TestBed.createComponent(CpObsSparklineComponent);
    for (const [k, v] of Object.entries(inputs)) {
      fixture.componentRef.setInput(k, v);
    }
    await fixture.whenStable();
    return fixture;
  }

  it('should render nothing with fewer than two points', async () => {
    const fixture = await render({ points: [points[0]] });

    expect(fixture.componentInstance.svgData()).toBeNull();
    expect(fixture.nativeElement.querySelector('svg')).toBeNull();
  });

  it('should scale points into the default box', async () => {
    const fixture = await render({ points });
    const data = fixture.componentInstance.svgData();

    expect(data).toEqual({
      path: 'M1.0,17.0 L32.0,3.0 L63.0,10.0',
      dotX: 63,
      dotY: 10,
      refY: null,
      w: 64,
      h: 20,
    });
    expect(fixture.nativeElement.querySelector('path').getAttribute('d')).toBe(
      data?.path,
    );
    expect(fixture.nativeElement.querySelector('line')).toBeNull();
  });

  it('should draw the reference line only when it is within range', async () => {
    const inRange = await render({ points, refMax: 130 });
    expect(inRange.componentInstance.svgData()?.refY).toBe(10);
    expect(inRange.nativeElement.querySelector('line')).not.toBeNull();

    const outOfRange = await render({ points, refMax: 200 });
    expect(outOfRange.componentInstance.svgData()?.refY).toBeNull();
  });

  it('should handle a flat series and custom size and colour', async () => {
    const fixture = await render({
      points: [
        { date: 'a', value: 5 },
        { date: 'b', value: 5 },
      ],
      width: 10,
      height: 10,
      color: 'red',
    });

    expect(fixture.componentInstance.svgData()?.path).toBe('M1.0,7.0 L9.0,7.0');
    expect(
      fixture.nativeElement.querySelector('circle').getAttribute('fill'),
    ).toBe('red');
  });
});
