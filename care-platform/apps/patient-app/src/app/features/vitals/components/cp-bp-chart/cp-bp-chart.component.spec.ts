import { TestBed } from '@angular/core/testing';
import { DataPoint } from '../../../../shared/models/patient-app.models';
import { CpBpChartComponent } from './cp-bp-chart.component';

function points(...values: number[]): DataPoint[] {
  return values.map((value, i) => ({
    date: `2026-10-${String(i + 1).padStart(2, '0')}`,
    value,
  })) as DataPoint[];
}

describe('CpBpChartComponent', () => {
  function render(systolic: number[], diastolic: number[]) {
    const fixture = TestBed.createComponent(CpBpChartComponent);
    fixture.componentRef.setInput('systolic', points(...systolic));
    fixture.componentRef.setInput('diastolic', points(...diastolic));
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('should keep the usual 60–170 scale for typical readings', () => {
    const chart = render([128, 135], [82, 88]);

    expect(chart.range()).toEqual({ min: 60, max: 170 });
    expect(chart.gridLineY().map((g) => g.t)).toEqual([80, 100, 120, 140, 160]);
  });

  it('should grow the scale so very high readings stay inside the plot', () => {
    const chart = render([150, 187], [95, 110]);

    expect(chart.range().max).toBe(200);
    for (const dot of chart.sysDots()) {
      expect(dot.y).toBeGreaterThanOrEqual(14);
    }
    expect(chart.gridLineY().map((g) => g.t)).toContain(180);
  });

  it('should colour high readings red and normal readings green', () => {
    const [top, , bottom] = render([128, 135], [82, 88]).bands();

    expect(top.fill).toContain('--px-crit');
    expect(bottom.fill).toContain('--px-ok');
  });
});
