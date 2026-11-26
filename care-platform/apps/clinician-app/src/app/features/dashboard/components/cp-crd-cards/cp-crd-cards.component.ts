import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import {
  CpDocumentationDialogComponent,
  DocumentationDialogData,
} from '@features/dashboard/components/cp-documentation-dialog/cp-documentation-dialog.component';
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
  imports: [MatButtonModule],
  templateUrl: './cp-crd-cards.component.html',
  styleUrl: './cp-crd-cards.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpCrdCardsComponent {
  private readonly dialog = inject(MatDialog);
  readonly orderId = input<string | null>(null);
  readonly coverage = input<CoverageInformation | null | undefined>(null);
  readonly cards = input<CrdCard[] | null | undefined>(null);
  readonly compact = input(false);

  protected readonly badge = computed(() => coverageBadge(this.coverage()));

  protected readonly visibleCards = computed(() =>
    (this.cards() ?? []).filter((card) => card.summary || card.detail),
  );

  protected readonly documentationOrderId = computed(() =>
    documentationNeeded(this.coverage()) ? this.orderId() : null,
  );

  protected openDocumentation(orderId: string): void {
    this.dialog.open<CpDocumentationDialogComponent, DocumentationDialogData>(
      CpDocumentationDialogComponent,
      { data: { orderId }, width: '680px' },
    );
  }
}
