import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import {
  DocumentationForm,
  displayAnswer,
  fieldValue,
} from '@features/dashboard/documentation-form';

@Component({
  selector: 'cp-questionnaire-items',
  imports: [
    NgTemplateOutlet,
    ReactiveFormsModule,
    MatAutocompleteModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './cp-questionnaire-items.component.html',
  styleUrl: './cp-questionnaire-items.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpQuestionnaireItemsComponent {
  readonly model = input.required<DocumentationForm>();
  readonly evaluation =
    input.required<ReturnType<DocumentationForm['evaluate']>>();
  readonly formId = input.required<string>();
  readonly locked = input(false);

  protected readonly displayAnswer = displayAnswer;
  protected readonly fieldValue = fieldValue;

  protected fieldId(linkId: string): string {
    return this.formId() + '-' + encodeURIComponent(linkId);
  }
}
