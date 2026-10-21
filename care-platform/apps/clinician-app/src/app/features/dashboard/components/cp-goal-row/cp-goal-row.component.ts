import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
} from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { GoalSummary } from '@features/dashboard/dashboard.model';

@Component({
  selector: 'cp-goal-row',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe, MatIconModule],
  templateUrl: './cp-goal-row.component.html',
  styleUrl: './cp-goal-row.component.scss',
})
export class CpGoalRowComponent {
  readonly goal = input.required<GoalSummary>();
  readonly expanded = input(false);
  readonly goalToggle = output<string>();

  readonly percent = computed(() => {
    const p = this.goal().progress.percentToGoal;
    return p !== null ? Math.min(100, Math.max(0, p)) : null;
  });
}
