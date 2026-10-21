import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import { DataPoint } from '@features/dashboard/dashboard.model';

@Component({
  selector: 'cp-obs-sparkline',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './cp-obs-sparkline.component.html',
  styleUrl: './cp-obs-sparkline.component.scss',
})
export class CpObsSparklineComponent {
  readonly points = input.required<DataPoint[]>();
  readonly width = input(64);
  readonly height = input(20);
  readonly color = input('currentColor');
  readonly refMax = input<number | null>(null);

  readonly svgData = computed(() => {
    const pts = this.points();
    const w = this.width();
    const h = this.height();
    const rm = this.refMax();
    const PAD = 3;

    if (pts.length < 2) return null;

    const vals = pts.map((p) => p.value);
    const min = Math.min(...vals);
    const max = Math.max(...vals);
    const span = max - min || 1;
    const xs = (i: number) => (i / (pts.length - 1)) * (w - 2) + 1;
    const ys = (v: number) => h - PAD - ((v - min) / span) * (h - PAD * 2);

    const path = pts
      .map(
        (p, i) =>
          `${i === 0 ? 'M' : 'L'}${xs(i).toFixed(1)},${ys(p.value).toFixed(1)}`,
      )
      .join(' ');
    const dotX = xs(pts.length - 1);
    const dotY = ys(pts[pts.length - 1].value);
    const refY = rm !== null && rm >= min && rm <= max ? ys(rm) : null;

    return { path, dotX, dotY, refY, w, h };
  });
}
