import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  Injector,
  signal,
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {
  catchError,
  exhaustMap,
  finalize,
  map,
  merge,
  of,
  startWith,
  Subject,
  switchMap,
} from 'rxjs';
import { DocumentationForm } from '@features/dashboard/documentation-form';
import { DocumentationRequest } from '@features/dashboard/documentation.model';
import { DocumentationService } from '@features/dashboard/documentation.service';
import { CpQuestionnaireItemsComponent } from '@features/dashboard/components/cp-questionnaire-items/cp-questionnaire-items.component';

export interface DocumentationDialogData {
  orderId: string;
}

type Action = 'save' | 'complete';
type ReadyState = {
  status: 'ready';
  model: DocumentationForm;
  pending: Action | null;
  message: string | null;
  error?: boolean;
};
type State =
  | ReadyState
  | { status: 'loading' }
  | { status: 'error'; message: string };

@Component({
  selector: 'cp-documentation-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    CpQuestionnaireItemsComponent,
  ],
  templateUrl: './cp-documentation-dialog.component.html',
  styleUrl: './cp-documentation-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpDocumentationDialogComponent {
  private readonly orderId =
    inject<DocumentationDialogData>(MAT_DIALOG_DATA).orderId;
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly service = inject(DocumentationService);
  private readonly dialogRef = inject(
    MatDialogRef<CpDocumentationDialogComponent>,
  );
  private readonly reload$ = new Subject<void>();
  private readonly submit$ = new Subject<{
    action: Action;
    model: DocumentationForm;
    request: DocumentationRequest;
  }>();
  private readonly attempted = signal<Action | null>(null);

  protected readonly formId = this.dialogRef.id + '-form';

  protected readonly state = toSignal(
    merge(
      this.reload$.pipe(
        startWith(undefined),
        switchMap(() =>
          this.service.get(this.orderId).pipe(
            map(
              (response): State => ({
                status: 'ready',
                model: new DocumentationForm(response),
                pending: null,
                message: null,
              }),
            ),
            startWith<State>({ status: 'loading' }),
            catchError((error: unknown) =>
              of<State>({ status: 'error', message: this.errorMessage(error) }),
            ),
          ),
        ),
      ),
      this.submit$.pipe(
        exhaustMap(({ action, model, request }) => {
          this.dialogRef.disableClose = true;
          model.form.disable();
          const response$ =
            action === 'complete'
              ? this.service.complete(this.orderId, request)
              : this.service.save(this.orderId, request);
          return response$.pipe(
            map((response): State => {
              this.attempted.set(null);
              return {
                status: 'ready',
                model: new DocumentationForm(response),
                pending: null,
                message: action === 'save' ? 'Draft saved.' : null,
              };
            }),
            catchError((error: unknown) =>
              of<State>({
                status: 'ready',
                model,
                pending: null,
                message: this.errorMessage(error),
                error: true,
              }),
            ),
            startWith<State>({
              status: 'ready',
              model,
              pending: action,
              message: null,
            }),
            finalize(() => {
              model.form.enable();
              this.dialogRef.disableClose = false;
            }),
          );
        }),
      ),
    ),
    { initialValue: { status: 'loading' } as State },
  );

  private readonly formChanges = toSignal(
    toObservable(this.state).pipe(
      switchMap((state) =>
        state.status === 'ready' ? state.model.form.valueChanges : of(null),
      ),
    ),
  );

  protected readonly evaluation = computed(() => {
    this.formChanges();
    const state = this.state();
    if (state.status !== 'ready') return null;
    const attempted = this.attempted();
    const evaluation = state.model.evaluate(attempted === 'complete');
    if (!attempted) evaluation.errors.clear();
    return evaluation;
  });

  protected retry(): void {
    this.reload$.next();
  }

  protected submit(action: Action): void {
    const state = this.state();
    if (state.status !== 'ready' || state.pending || state.model.completed)
      return;
    this.attempted.set(action);
    const { errors, request } = state.model.evaluate(action === 'complete');
    const firstError = errors.keys().next().value;
    if (firstError !== undefined) {
      afterNextRender(
        () => {
          const items =
            this.element.nativeElement.querySelectorAll<HTMLElement>(
              '[data-link-id]',
            );
          const item = Array.from(items).find(
            (entry) => entry.dataset['linkId'] === firstError,
          );
          item
            ?.querySelector<HTMLElement>(
              'input, textarea, mat-select, [role="alert"]',
            )
            ?.focus();
        },
        { injector: this.injector },
      );
      return;
    }
    this.submit$.next({ action, model: state.model, request });
  }

  private errorMessage(error: unknown): string {
    const message =
      error instanceof HttpErrorResponse ? error.error?.message : null;
    return typeof message === 'string'
      ? message
      : 'Could not load or save documentation. Try again.';
  }
}
