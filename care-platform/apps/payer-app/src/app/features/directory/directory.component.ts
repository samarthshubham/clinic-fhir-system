import { HttpClient, HttpParams } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';

import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Subject, catchError, of, startWith, switchMap } from 'rxjs';

interface DirectoryProvider {
  id: string;
  name: string;
  specialties: string[];
  location: string | null;
  address: string;
  organization: string | null;
}
type DirectoryResult = DirectoryProvider[];

@Component({
  selector: 'cp-directory',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './directory.component.html',
  styleUrl: './directory.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpDirectoryComponent {
  private readonly http = inject(HttpClient);

  private readonly search$ = new Subject<void>();

  protected readonly form = new FormGroup({
    specialty: new FormControl('', { nonNullable: true }),
    city: new FormControl('', { nonNullable: true }),
  });

  protected readonly result = toSignal(
    this.search$.pipe(
      startWith(undefined),
      switchMap(() => {
        const { specialty, city } = this.form.getRawValue();
        const params = new HttpParams()

          .set('specialty', specialty.trim())
          .set('city', city.trim());
        return this.http
          .get<DirectoryResult>('/api/directory', { params })
          .pipe(
            startWith('loading' as const),
            catchError(() => of('error' as const)),
          );
      }),
    ),
  );

  protected search(): void {
    this.search$.next();
  }
}
