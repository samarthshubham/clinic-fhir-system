import { HttpClient, HttpParams } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
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
interface DirectoryResult {
  planName: string | null;
  providers: DirectoryProvider[];
}

@Component({
  selector: 'cp-referral-directory',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatDialogModule,
  ],
  templateUrl: './cp-referral-directory.component.html',
  styleUrl: './cp-referral-directory.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpReferralDirectoryComponent {
  private readonly http = inject(HttpClient);
  private readonly patientId = inject<string>(MAT_DIALOG_DATA);
  private readonly search$ = new Subject<void>();

  protected readonly form = new FormGroup({
    specialty: new FormControl('', { nonNullable: true }),
    city: new FormControl('', { nonNullable: true }),
  });

  protected readonly result = toSignal(
    this.search$.pipe(
      switchMap(() => {
        const { specialty, city } = this.form.getRawValue();
        const params = new HttpParams()
          .set('patientId', this.patientId)
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
