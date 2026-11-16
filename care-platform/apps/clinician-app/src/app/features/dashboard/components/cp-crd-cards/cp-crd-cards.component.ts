import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  CoverageInformation,
  CrdCard,
} from '@features/dashboard/dashboard.model';
import {
  coverageBadge,
  documentationNeeded,
} from '@features/dashboard/crd-coverage';

@Component({
  selector: 'cp-crd-cards',
  imports: [MatButtonModule, MatTooltipModule],
  templateUrl: './cp-crd-cards.component.html',
  styleUrl: './cp-crd-cards.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpCrdCardsComponent {
  readonly coverage = input<CoverageInformation | null | undefined>(null);
  readonly cards = input<CrdCard[] | null | undefined>(null);
  readonly compact = input(false);

  protected readonly badge = computed(() => coverageBadge(this.coverage()));

  protected readonly visibleCards = computed(() =>
    (this.cards() ?? []).filter((card) => card.summary || card.detail),
  );

  protected readonly showDocumentationAction = computed(() =>
    documentationNeeded(this.coverage()),
  );
}
