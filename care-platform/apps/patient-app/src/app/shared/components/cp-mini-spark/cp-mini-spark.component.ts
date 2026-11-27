import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import { DataPoint } from '@shared/models/patient-app.models';

@Component({
  selector: 'cp-mini-spark',
  template: `
    <svg
      [attr.width]="width()"
      [attr.height]="height()"
      [attr.viewBox]="'0 0 ' + width() + ' ' + height()"
    >
      @if (path()) {
        <path
          [attr.d]="path()"
          [attr.stroke]="color()"
          stroke-width="1.5"
          fill="none"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
        <circle
          [attr.cx]="lastPt()[0]"
          [attr.cy]="lastPt()[1]"
          r="2"
          [attr.fill]="color()"
        />
      }
    </svg>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpMiniSparkComponent {
  readonly values = input<DataPoint[]>([]);
  readonly color = input<string>('var(--px-accent)');
  readonly width = input<number>(60);
  readonly height = input<number>(18);

  private readonly pts = computed(() => {
    const vs = (this.values() ?? []).map((v) => v.value);
    if (vs.length < 2) return null;
    const min = Math.min(...vs);
    const max = Math.max(...vs);
    const range = max - min || 1;
    const w = this.width();
    const h = this.height();
    const stepX = w / (vs.length - 1);
    return vs.map((v, i) => [i * stepX, h - 2 - ((v - min) / range) * (h - 4)]);
  });

  readonly path = computed(() => {
    const pts = this.pts();
    if (!pts) return null;
    return pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${p[0]},${p[1]}`).join(' ');
  });

  readonly lastPt = computed(() => {
    const pts = this.pts();
    return pts ? pts[pts.length - 1] : [0, 0];
  });
}
