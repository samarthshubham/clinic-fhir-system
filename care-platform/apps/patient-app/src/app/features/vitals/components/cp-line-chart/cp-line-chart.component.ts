import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import { DataPoint } from '@shared/models/patient-app.models';

@Component({
  selector: 'cp-line-chart',
  templateUrl: './cp-line-chart.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpLineChartComponent {
  readonly data = input<DataPoint[]>([]);
  readonly color = input<string>('var(--px-accent)');
  readonly yMin = input<number | undefined>(undefined);
  readonly yMax = input<number | undefined>(undefined);
  readonly dangerBelow = input<number | undefined>(undefined);
  readonly unitLabel = input<string>('');

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

  readonly hasData = computed(() => (this.data()?.length ?? 0) >= 2);

  private resolvedRange = computed(() => {
    const vs = (this.data() ?? []).map((p) => p.value);
    if (!vs.length) return { min: 0, max: 100 };
    const dataMin = Math.min(...vs);
    const dataMax = Math.max(...vs);
    const spread = dataMax - dataMin;
    return {
      min: this.yMin() ?? Math.floor(dataMin - spread * 0.2),
      max: this.yMax() ?? Math.ceil(dataMax + spread * 0.2),
    };
  });

  private sx(i: number): number {
    const len = this.data().length;
    return this.padL + (i / (len - 1)) * this.plotW;
  }

  private sy(v: number): number {
    const { min, max } = this.resolvedRange();
    return this.padT + this.plotH - ((v - min) / (max - min)) * this.plotH;
  }

  readonly gradId = computed(() => {
    const c = this.color().replace(/[^\w]/g, '');
    return `lg-${c}-${Math.round(this.resolvedRange().min)}`;
  });

  readonly lineD = computed(() => {
    const d = this.data();
    return d
      .map((p, i) => `${i === 0 ? 'M' : 'L'}${this.sx(i)},${this.sy(p.value)}`)
      .join(' ');
  });

  readonly areaD = computed(() => {
    const d = this.data();
    const bottom = this.padT + this.plotH;
    const line = this.lineD();
    return `${line} L${this.sx(d.length - 1)},${bottom} L${this.sx(0)},${bottom} Z`;
  });

  readonly dangerRect = computed(() => {
    const db = this.dangerBelow();
    const range = this.resolvedRange();
    if (db == null || db <= range.min) return null;
    const y = this.sy(db);
    return { y, h: this.padT + this.plotH - y };
  });

  readonly ticks = computed(() => {
    const { min, max } = this.resolvedRange();
    const count = 4;
    return Array.from({ length: count + 1 }, (_, i) =>
      Math.round(min + (i * (max - min)) / count),
    ).map((t) => ({ t, y: this.sy(t) }));
  });

  readonly xLabels = computed(() => {
    const d = this.data();
    if (!d.length) return [];
    const last = d.length - 1;
    const mid = Math.floor(last / 2);
    return [
      { i: 0, x: this.sx(0), label: this.shortDate(d[0].date) },
      { i: mid, x: this.sx(mid), label: this.shortDate(d[mid].date) },
      { i: last, x: this.sx(last), label: this.shortDate(d[last].date) },
    ];
  });

  readonly dots = computed(() => {
    const d = this.data();
    const last = d.length - 1;
    return d.map((p, i) => ({
      x: this.sx(i),
      y: this.sy(p.value),
      r: i === last ? 3.5 : 1.8,
      label: `${p.date}: ${p.value} ${this.unitLabel()}`,
    }));
  });

  readonly W_ = this.W;
  readonly H_ = this.H;
  readonly padL_ = this.padL;
  readonly padR_ = this.padR;
  readonly padT_ = this.padT;
  readonly plotH_ = computed(() => this.plotH);

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
