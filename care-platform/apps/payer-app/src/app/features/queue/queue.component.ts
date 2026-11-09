import { ChangeDetectionStrategy, Component } from '@angular/core';
import { EmptyStateComponent } from '@shared/empty-state/empty-state.component';

@Component({
  selector: 'cp-queue',
  imports: [EmptyStateComponent],
  templateUrl: './queue.component.html',
  styleUrl: './queue.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QueueComponent {}
