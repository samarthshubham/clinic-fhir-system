import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  signal,
} from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import {
  BpSeries,
  DataPoint,
  SimpleSeries,
} from '@features/dashboard/dashboard.model';

const W = 340;
const H = 110;
const PAD_L = 8;
const PAD_R = 8;
const PAD_T = 12;
const PAD_B = 18;

function scaleX(i: number, n: number): number {
  return PAD_L + (i / (n - 1)) * (W - PAD_L - PAD_R);
}

function scaleY(v: number, min: number, span: number): number {
  return PAD_T + (1 - (v - min) / span) * (H - PAD_T - PAD_B);
}

function buildPath(
  pts: DataPoint[],
  min: number,
  span: number,
  n: number,
): string {
  return pts
    .map(
      (p, i) =>
        `${i === 0 ? 'M' : 'L'}${scaleX(i, n).toFixed(1)},${scaleY(p.value, min, span).toFixed(1)}`,
    )
    .join(' ');
}

function buildArea(
  pts: DataPoint[],
  min: number,
  span: number,
  n: number,
): string {
  const line = buildPath(pts, min, span, n);
  const x0 = scaleX(0, n).toFixed(1);
  const xN = scaleX(n - 1, n).toFixed(1);
  const base = (H - PAD_B).toFixed(1);
  return `${line} L${xN},${base} L${x0},${base} Z`;
}

interface ChartPoint {
  x: number;
  y: number;
  value: number;
  date: string;
}

function toDots(
  pts: DataPoint[],
  min: number,
  span: number,
  n: number,
): ChartPoint[] {
  return pts.map((p, i) => ({
    x: scaleX(i, n),
    y: scaleY(p.value, min, span),
    value: p.value,
    date: p.date,
  }));
}

// Flat interface — no discriminated union, so Angular SVG template access is unambiguous.
interface ChartData {
  isBp: boolean;
  primaryPath: string;
  primaryArea: string;
  primaryDots: ChartPoint[];
  secondaryPath: string | null;
  secondaryDots: ChartPoint[];
  refY: number | null;
  refY2: number | null;
  W: number;
  H: number;
  baselineY: number;
  n: number;
  startDate: string;
  endDate: string;
  unit: string;
  latestPrimary: number | null;
  latestSecondary: number | null;
}

@Component({
  selector: 'cp-trend-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe],
  templateUrl: './cp-trend-chart.component.html',
  styleUrl: './cp-trend-chart.component.scss',
})
export class CpTrendChartComponent {
  readonly series = input.required<BpSeries | SimpleSeries | null>();
  readonly label = input.required<string>();
  readonly primaryColor = input.required<string>();
  readonly secondaryColor = input<string | null>(null);

  readonly hoverIdx = signal<number | null>(null);

  readonly svgData = computed<ChartData | null>(() => {
    const s = this.series();
    if (!s) return null;

    if ('systolic' in s) {
      const bp = s as BpSeries;
      const n = Math.max(bp.systolic.length, bp.diastolic.length);
      if (n < 2) return null;
      const allVals = [
        ...bp.systolic.map((p) => p.value),
        ...bp.diastolic.map((p) => p.value),
      ];
      const refSys = bp.referenceRange['systolicMax'];
      const refDia = bp.referenceRange['diastolicMax'];
      if (refSys !== undefined) allVals.push(refSys);
      if (refDia !== undefined) allVals.push(refDia);
      const min = Math.min(...allVals) - 4;
      const max = Math.max(...allVals) + 4;
      const span = max - min || 1;
      const lastSys = bp.systolic.length
        ? bp.systolic[bp.systolic.length - 1]
        : null;
      const lastDia = bp.diastolic.length
        ? bp.diastolic[bp.diastolic.length - 1]
        : null;
      return {
        isBp: true,
        primaryPath: buildPath(bp.systolic, min, span, n),
        primaryArea: buildArea(bp.systolic, min, span, n),
        primaryDots: toDots(bp.systolic, min, span, n),
        secondaryPath: buildPath(bp.diastolic, min, span, n),
        secondaryDots: toDots(bp.diastolic, min, span, n),
        refY: refSys !== undefined ? scaleY(refSys, min, span) : null,
        refY2: refDia !== undefined ? scaleY(refDia, min, span) : null,
        W,
        H,
        baselineY: H - PAD_B,
        n,
        startDate: bp.systolic[0]?.date ?? '',
        endDate: lastSys?.date ?? '',
        unit: 'mmHg',
        latestPrimary: lastSys?.value ?? null,
        latestSecondary: lastDia?.value ?? null,
      };
    } else {
      const simple = s as SimpleSeries;
      const pts = simple.values;
      if (pts.length < 2) return null;
      const vals = pts.map((p) => p.value);
      const refRange = simple.referenceRange;
      const refMaxRaw = refRange['max'] ?? refRange['upper'] ?? null;
      const refMax = typeof refMaxRaw === 'number' ? refMaxRaw : null;
      if (refMax !== null) vals.push(refMax);
      const min = Math.min(...vals) - 0.5;
      const max = Math.max(...vals) + 0.5;
      const span = max - min || 1;
      const n = pts.length;
      const lastPt = pts[pts.length - 1];
      return {
        isBp: false,
        primaryPath: buildPath(pts, min, span, n),
        primaryArea: buildArea(pts, min, span, n),
        primaryDots: toDots(pts, min, span, n),
        secondaryPath: null,
        secondaryDots: [],
        refY: refMax !== null ? scaleY(refMax, min, span) : null,
        refY2: null,
        W,
        H,
        baselineY: H - PAD_B,
        n,
        startDate: pts[0]?.date ?? '',
        endDate: lastPt?.date ?? '',
        unit: simple.unit,
        latestPrimary: lastPt?.value ?? null,
        latestSecondary: null,
      };
    }
  });

  readonly hoverData = computed(() => {
    const idx = this.hoverIdx();
    const d = this.svgData();
    if (idx === null || !d || idx < 0 || idx >= d.n) return null;

    const primaryDot = d.primaryDots[idx] ?? null;
    const secondaryDot = d.isBp ? (d.secondaryDots[idx] ?? null) : null;
    return {
      primaryDot,
      secondaryDot,
      primary: primaryDot?.value ?? null,
      secondary: secondaryDot?.value ?? null,
      date: primaryDot?.date ?? null,
    };
  });

  onSvgMouseMove(event: MouseEvent, svgEl: Element): void {
    const d = this.svgData();
    if (!d) return;
    const rect = svgEl.getBoundingClientRect();
    const ratio = Math.max(
      0,
      Math.min(1, (event.clientX - rect.left) / rect.width),
    );
    this.hoverIdx.set(Math.round(ratio * (d.n - 1)));
  }

  onSvgMouseLeave(): void {
    this.hoverIdx.set(null);
  }
}
