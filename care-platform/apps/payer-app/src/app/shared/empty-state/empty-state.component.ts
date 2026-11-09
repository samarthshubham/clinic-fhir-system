import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'cp-empty-state',
  imports: [MatIconModule],
  template: `
    <mat-icon class="cp-empty-state__icon" aria-hidden="true">{{
      icon()
    }}</mat-icon>
    <p class="cp-empty-state__title">{{ title() }}</p>
    <p class="cp-empty-state__text">{{ text() }}</p>
  `,
  styleUrl: './empty-state.component.scss',
  host: { class: 'cp-empty-state', role: 'status' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmptyStateComponent {
  readonly icon = input.required<string>();
  readonly title = input.required<string>();
  readonly text = input.required<string>();
}
