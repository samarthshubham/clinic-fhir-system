import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { TimelineEvent, TrendsResponse } from '../../dashboard.model';
import { CpTimelineEventComponent } from '../cp-timeline-event/cp-timeline-event.component';

@Component({
  selector: 'cp-timeline-group',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CpTimelineEventComponent],
  templateUrl: './cp-timeline-group.component.html',
  styleUrl: './cp-timeline-group.component.scss',
})
export class CpTimelineGroupComponent {
  @Input({ required: true }) date!: string;
  @Input({ required: true }) events!: TimelineEvent[];
  @Input() trends: TrendsResponse | null = null;
  @Input() expandedId: string | null = null;
  @Input() density: 'compact' | 'comfortable' = 'comfortable';
  @Output() eventSelect = new EventEmitter<string>();

  get isFuture(): boolean {
    return new Date(this.date) > new Date();
  }

  get fmtDateLong(): string {
    return new Date(this.date).toLocaleDateString('en-US', {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  }

  get daysAgo(): string {
    const ms = Date.now() - new Date(this.date).getTime();
    const d = Math.floor(ms / 86_400_000);
    if (d === 0) return 'today';
    if (d < 0) return `in ${-d}d`;
    if (d === 1) return '1d ago';
    if (d < 30) return `${d}d ago`;
    if (d < 365) return `${Math.floor(d / 30)}mo ago`;
    return `${Math.floor(d / 365)}y ago`;
  }
}
