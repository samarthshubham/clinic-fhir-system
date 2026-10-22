import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import { DataPoint } from '../../../../shared/models/patient-app.models';

const DEFAULT_MIN = 60;
const DEFAULT_MAX = 170;

interface ChartPt {
  x: number;
  y: number;
  label: string;
}

@Component({
  selector: 'app-bp-chart',
  templateUrl: './cp-bp-chart.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpBpChartComponent {
  readonly systolic = input<DataPoint[]>([]);
  readonly diastolic = input<DataPoint[]>([]);

  private readonly W = 400;
  private readonly H = 140;
  private readonly padL = 36;
  private readonly padR = 12;
  private readonly padT = 14;
  private readonly padB = 26;

  private get plotW() {
    return this.W - this.padL - this.padR;
  }
  private get plotH() {
    return this.H - this.padT - this.padB;
  }

  readonly hasData = computed(() => (this.systolic()?.length ?? 0) >= 2);

  private sx(i: number, len: number): number {
    return this.padL + (i / (len - 1)) * this.plotW;
  }
  readonly range = computed(() => {
    const values = [...this.systolic(), ...this.diastolic()].map(
      (p) => p.value,
    );
    return {
      min: Math.min(
        DEFAULT_MIN,
        Math.floor((Math.min(...values) - 10) / 10) * 10,
      ),
      max: Math.max(
        DEFAULT_MAX,
        Math.ceil((Math.max(...values) + 10) / 10) * 10,
      ),
    };
  });

  private sy(v: number): number {
    const { min, max } = this.range();
    return this.padT + this.plotH - ((v - min) / (max - min)) * this.plotH;
  }

  readonly bands = computed(() => {
    const W = this.W;
    const padL = this.padL;
    const padR = this.padR;
    const plotW = this.plotW;
    const sy = (v: number) => this.sy(v);
    const yNT = sy(120);
    const yET = sy(130);
    const plotBottom = this.padT + this.plotH;
    return [
      {
        x: padL,
        y: this.padT,
        w: plotW,
        h: yET - this.padT,
        fill: 'color-mix(in srgb, var(--px-crit), transparent 92%)',
      },
      {
        x: padL,
        y: yET,
        w: plotW,
        h: yNT - yET,
        fill: 'color-mix(in srgb, var(--px-warn), transparent 92%)',
      },
      {
        x: padL,
        y: yNT,
        w: plotW,
        h: plotBottom - yNT,
        fill: 'color-mix(in srgb, var(--px-ok), transparent 90%)',
      },
    ];
  });

  readonly targetY = computed(() => this.sy(130));
  readonly gridLineY = computed(() => {
    const { min, max } = this.range();
    const ticks: number[] = [];
    for (let t = Math.floor(min / 20) * 20 + 20; t < max; t += 20) {
      ticks.push(t);
    }
    return ticks.map((t) => ({ t, y: this.sy(t) }));
  });

  readonly xLabels = computed(() => {
    const sys = this.systolic();
    if (!sys.length) return [];
    const last = sys.length - 1;
    const mid = Math.floor(last / 2);
    return [
      { i: 0, x: this.sx(0, sys.length), label: this.shortDate(sys[0].date) },
      {
        i: mid,
        x: this.sx(mid, sys.length),
        label: this.shortDate(sys[mid].date),
      },
      {
        i: last,
        x: this.sx(last, sys.length),
        label: this.shortDate(sys[last].date),
      },
    ];
  });

  readonly sysLine = computed(() => {
    const sys = this.systolic();
    if (!sys.length) return '';
    return sys
      .map(
        (p, i) =>
          `${i === 0 ? 'M' : 'L'}${this.sx(i, sys.length)},${this.sy(p.value)}`,
      )
      .join(' ');
  });

  readonly diaLine = computed(() => {
    const dia = this.diastolic();
    if (!dia.length) return '';
    return dia
      .map(
        (p, i) =>
          `${i === 0 ? 'M' : 'L'}${this.sx(i, dia.length)},${this.sy(p.value)}`,
      )
      .join(' ');
  });

  readonly sysDots = computed((): ChartPt[] => {
    const sys = this.systolic();
    const dia = this.diastolic();
    const last = sys.length - 1;
    return sys.map(
      (p, i) =>
        ({
          x: this.sx(i, sys.length),
          y: this.sy(p.value),
          label: `${p.date}: ${p.value}/${dia[i]?.value ?? '?'} mmHg`,
          r: i === last ? 3.5 : 2,
        }) as any,
    );
  });

  readonly diaDots = computed((): ChartPt[] => {
    const dia = this.diastolic();
    const last = dia.length - 1;
    return dia.map(
      (p, i) =>
        ({
          x: this.sx(i, dia.length),
          y: this.sy(p.value),
          label: `${p.date}: ${p.value} mmHg (dia)`,
          r: i === last ? 3 : 1.6,
        }) as any,
    );
  });

  readonly W_ = this.W;
  readonly H_ = this.H;
  readonly padL_ = this.padL;
  readonly padR_ = this.padR;

  private shortDate(iso: string): string {
    if (!iso) return '';
    const d = new Date(iso);
    const months = [
      'Jan',
      'Feb',
      'Mar',
      'Apr',
      'May',
      'Jun',
      'Jul',
      'Aug',
      'Sep',
      'Oct',
      'Nov',
      'Dec',
    ];
    return `${months[d.getMonth()]} ${d.getDate()}`;
  }
}
