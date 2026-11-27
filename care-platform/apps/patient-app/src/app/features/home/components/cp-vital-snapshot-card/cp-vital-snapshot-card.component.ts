import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import { NgClass } from '@angular/common';
import { DataPoint } from '@shared/models/patient-app.models';
import { CpMiniSparkComponent } from '@shared/components/cp-mini-spark/cp-mini-spark.component';

export type VitalStatus = 'ok' | 'elevated' | 'warn';

@Component({
  selector: 'cp-vital-snapshot-card',
  templateUrl: './cp-vital-snapshot-card.component.html',
  styleUrl: './cp-vital-snapshot-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CpMiniSparkComponent, NgClass],
})
export class CpVitalSnapshotCardComponent {
  readonly label = input.required<string>();
  readonly iconColor = input<string>('var(--px-accent)');
  readonly value = input<string | number | null>(null);
  readonly unit = input<string>('');
  readonly status = input<VitalStatus>('ok');
  readonly sparkData = input<DataPoint[]>([]);
  readonly sparkColor = input<string>('var(--px-accent)');

  readonly pill = computed(() => {
    switch (this.status()) {
      case 'warn':
        return { cls: 'pa-pill-warn', text: 'High' };
      case 'elevated':
        return { cls: 'pa-pill-info', text: 'Elevated' };
      default:
        return { cls: 'pa-pill-ok', text: 'In range' };
    }
  });

  readonly isFlagged = computed(() => this.status() === 'warn');
}
