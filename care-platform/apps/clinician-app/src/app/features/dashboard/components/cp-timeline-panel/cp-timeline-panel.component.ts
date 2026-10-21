import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import {
  TimelineEvent,
  TrendsResponse,
} from '@features/dashboard/dashboard.model';
import { CpTimelineGroupComponent } from '@features/dashboard/components/cp-timeline-group/cp-timeline-group.component';

interface TimelineGroup {
  date: string;
  items: TimelineEvent[];
}

const FILTERS = [
  { id: 'all', label: 'All' },
  { id: 'encounter', label: 'Visits' },
  { id: 'observation', label: 'Vitals' },
  { id: 'report', label: 'Labs' },
  { id: 'service-request', label: 'Orders' },
  { id: 'appointment', label: 'Appts' },
] as const;

@Component({
  selector: 'cp-timeline-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CpTimelineGroupComponent],
  templateUrl: './cp-timeline-panel.component.html',
  styleUrl: './cp-timeline-panel.component.scss',
})
export class CpTimelinePanelComponent implements AfterViewInit, OnChanges {
  @Input({ required: true }) events!: TimelineEvent[];
  @Input() trends: TrendsResponse | null = null;
  @Output() loadMore = new EventEmitter<void>();

  private readonly destroyRef = inject(DestroyRef);
  private readonly sentinelEl = viewChild<ElementRef<HTMLElement>>('sentinel');
  private intersectionObs?: IntersectionObserver;

  readonly filters = FILTERS;

  // Synchronous UI state
  readonly activeFilter = signal<string>('all');
  readonly expandedId = signal<string | null>(null);

  // events is @Input — use a signal to bridge it into computed()
  private readonly events$ = signal<TimelineEvent[]>([]);

  ngOnChanges(): void {
    this.events$.set(this.events ?? []);
  }

  readonly filtered = computed(() => {
    const f = this.activeFilter();
    const evs = this.events$();
    return f === 'all' ? evs : evs.filter((e) => e.type === f);
  });

  readonly groups = computed<TimelineGroup[]>(() => {
    const result: TimelineGroup[] = [];
    let cur: TimelineGroup | null = null;
    for (const e of this.filtered()) {
      if (!cur || cur.date !== e.date) {
        cur = { date: e.date, items: [] };
        result.push(cur);
      }
      cur.items.push(e);
    }
    return result;
  });

  toggleExpanded(id: string): void {
    this.expandedId.set(this.expandedId() === id ? null : id);
  }

  setFilter(id: string): void {
    this.activeFilter.set(id);
    this.expandedId.set(null);
  }

  ngAfterViewInit(): void {
    const sentinel = this.sentinelEl()?.nativeElement;
    if (!sentinel) return;

    this.intersectionObs = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) this.loadMore.emit();
      },
      { rootMargin: '120px' },
    );
    this.intersectionObs.observe(sentinel);
    this.destroyRef.onDestroy(() => this.intersectionObs?.disconnect());
  }
}
