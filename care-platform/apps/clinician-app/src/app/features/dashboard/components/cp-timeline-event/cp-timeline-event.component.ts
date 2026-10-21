import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  inject,
  Input,
  Output,
} from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { ToastService } from '../../../../toast.service';
import {
  BpSeries,
  DataPoint,
  GroupedObservation,
  SimpleSeries,
  TimelineEvent,
  TrendsResponse,
} from '../../dashboard.model';
import { CpObsSparklineComponent } from '../cp-obs-sparkline/cp-obs-sparkline.component';

const EVENT_TYPE_META: Record<
  string,
  { label: string; color: string; icon: string }
> = {
  encounter: {
    label: 'Encounter',
    color: 'var(--px-accent)',
    icon: 'calendar_today',
  },
  observation: {
    label: 'Observation',
    color: 'var(--px-obs-weight)',
    icon: 'monitoring',
  },
  report: {
    label: 'Lab Report',
    color: 'var(--px-obs-spo2)',
    icon: 'description',
  },
  'service-request': { label: 'Order', color: '#7a5af8', icon: 'checklist' },
  appointment: { label: 'Appointment', color: '#16a34a', icon: 'schedule' },
  condition: { label: 'Condition', color: '#d97706', icon: 'warning' },
};

// API GroupedObservation.type → CSS tint class suffix
const OBS_KIND: Record<string, string> = {
  systolic: 'bp',
  diastolic: 'bp',
  weight: 'weight',
  spo2: 'spo2',
  heartRate: 'hr',
  temperature: 'temp',
};

const OBS_KIND_COLOR: Record<string, string> = {
  bp: 'var(--px-accent)',
  hr: 'var(--px-obs-hr)',
  weight: 'var(--px-obs-weight)',
  spo2: 'var(--px-obs-spo2)',
  temp: 'var(--px-obs-temp)',
};

// Lower = better: bp, weight. Higher = better: spo2.
const LOWER_IS_BETTER = new Set(['bp', 'weight']);
const HIGHER_IS_BETTER = new Set(['spo2']);

export interface ObsRowData {
  obs: GroupedObservation;
  kind: string;
  color: string;
  history: DataPoint[];
  delta: {
    dir: 'up' | 'down' | 'flat';
    value: number;
    tone: 'good' | 'bad' | 'neutral';
  } | null;
  refMax: number | null;
  decimals: number;
}

export interface ActionButton {
  label: string;
  primary: boolean;
}

@Component({
  selector: 'cp-timeline-event',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule, CpObsSparklineComponent],
  templateUrl: './cp-timeline-event.component.html',
  styleUrl: './cp-timeline-event.component.scss',
})
export class CpTimelineEventComponent {
  @Input({ required: true }) ev!: TimelineEvent;
  @Input() trends: TrendsResponse | null = null;
  @Input() expanded = false;
  @Input() density: 'compact' | 'comfortable' = 'comfortable';
  @Output() eventSelect = new EventEmitter<string>();

  private readonly toastService = inject(ToastService);

  onAction(label: string): void {
    this.toastService.show(`${label} is a placeholder in this prototype`);
  }

  get meta(): { label: string; color: string; icon: string } {
    return (
      EVENT_TYPE_META[this.ev.type] ?? {
        label: this.ev.type,
        color: '#888',
        icon: 'help',
      }
    );
  }

  get isFuture(): boolean {
    return new Date(this.ev.date) > new Date();
  }

  get fmtDate(): string {
    return new Date(this.ev.date).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
    });
  }

  get provider(): string | null {
    return (this.ev.metadata?.['provider'] as string) ?? null;
  }

  get conclusion(): string | null {
    return (this.ev.metadata?.['conclusion'] as string) ?? null;
  }

  get showObsGrid(): boolean {
    return !!(
      this.ev.groupedObservations?.length &&
      !this.expanded &&
      this.density !== 'compact'
    );
  }

  get showConclusion(): boolean {
    return !!(this.conclusion && this.density !== 'compact' && !this.expanded);
  }

  obsKind(obs: GroupedObservation): string {
    return OBS_KIND[obs.type] ?? 'other';
  }

  get expandedObsRows(): ObsRowData[] {
    return (this.ev.groupedObservations ?? []).map((obs) => {
      const kind = this.obsKind(obs);
      const series = this.seriesFor(kind);
      const around = series
        ? this.seriesAround(series, this.ev.date)
        : { history: [], current: null, prior: null };
      const delta = this.computeDelta(around.current, around.prior, kind);
      const refMax = kind === 'bp' ? this.bpRefMax() : null;
      return {
        obs,
        kind,
        color: OBS_KIND_COLOR[kind] ?? 'var(--px-text-muted)',
        history: around.history,
        delta,
        refMax,
        decimals: kind === 'weight' ? 1 : 0,
      };
    });
  }

  get actions(): ActionButton[] {
    switch (this.ev.type) {
      case 'encounter':
        return [
          { label: 'Add note', primary: false },
          { label: 'Order follow-up', primary: false },
          { label: 'Schedule revisit', primary: true },
        ];
      case 'report':
        return [
          { label: 'Acknowledge', primary: false },
          { label: 'Compare to prior', primary: false },
          { label: 'Flag for review', primary: true },
        ];
      case 'service-request':
        return [
          { label: 'Cancel order', primary: false },
          { label: 'Change priority', primary: false },
          { label: 'Mark complete', primary: true },
        ];
      case 'appointment':
        return [
          { label: 'Reschedule', primary: false },
          { label: 'Cancel', primary: false },
          { label: 'Open visit prep', primary: true },
        ];
      case 'condition':
        return [
          { label: 'Edit diagnosis', primary: false },
          { label: 'Resolve', primary: false },
          { label: 'View related', primary: true },
        ];
      default:
        return [
          { label: 'Open in chart', primary: false },
          { label: 'View full record', primary: true },
        ];
    }
  }

  formatDelta(row: ObsRowData): string {
    if (!row.delta || row.delta.dir === 'flat') return '';
    return Math.abs(row.delta.value).toFixed(row.decimals);
  }

  private seriesFor(kind: string): DataPoint[] | null {
    const t = this.trends;
    if (!t) return null;
    if (kind === 'bp')
      return (t.series['bp'] as BpSeries | undefined)?.systolic ?? null;
    if (kind === 'weight')
      return (t.series['weight'] as SimpleSeries | undefined)?.values ?? null;
    if (kind === 'spo2')
      return (t.series['spo2'] as SimpleSeries | undefined)?.values ?? null;
    return null;
  }

  private seriesAround(
    series: DataPoint[],
    date: string,
  ): { history: DataPoint[]; current: number | null; prior: number | null } {
    if (!series.length) return { history: [], current: null, prior: null };
    let idx = series.findIndex((p) => p.date === date);
    if (idx < 0) idx = series.length - 1;
    const history = series.slice(Math.max(0, idx - 6), idx + 1);
    return {
      history,
      current: series[idx]?.value ?? null,
      prior: idx > 0 ? (series[idx - 1]?.value ?? null) : null,
    };
  }

  private computeDelta(
    current: number | null,
    prior: number | null,
    kind: string,
  ): ObsRowData['delta'] {
    if (current === null || prior === null) return null;
    const d = current - prior;
    if (Math.abs(d) < 0.05) return { dir: 'flat', value: 0, tone: 'neutral' };
    const dir: 'up' | 'down' = d > 0 ? 'up' : 'down';
    let tone: 'good' | 'bad' | 'neutral' = 'neutral';
    if (LOWER_IS_BETTER.has(kind)) tone = dir === 'down' ? 'good' : 'bad';
    else if (HIGHER_IS_BETTER.has(kind)) tone = dir === 'up' ? 'good' : 'bad';
    return { dir, value: d, tone };
  }

  private bpRefMax(): number | null {
    const bp = this.trends?.series?.['bp'] as BpSeries | undefined;
    return (bp?.referenceRange?.['systolicMax'] as number) ?? null;
  }
}
