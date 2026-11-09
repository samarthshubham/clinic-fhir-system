import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { EmptyStateComponent } from '@shared/empty-state/empty-state.component';

@Component({
  selector: 'cp-placeholder-page',
  imports: [EmptyStateComponent],
  template: `
    <h1>{{ heading() }}</h1>
    <cp-empty-state
      [icon]="icon()"
      title="Coming soon"
      [text]="description()"
    />
  `,
  styleUrl: './placeholder-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlaceholderPageComponent {
  readonly heading = input.required<string>();
  readonly icon = input.required<string>();
  readonly description = input.required<string>();
}
