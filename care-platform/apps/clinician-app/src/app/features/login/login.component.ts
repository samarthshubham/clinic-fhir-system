import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  isDevMode,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, exhaustMap, map, of, startWith, Subject, tap } from 'rxjs';
import { AuthService } from '@core/auth.service';

interface Credentials {
  username: string;
  password: string;
}

type LoginState =
  | { status: 'idle' }
  | { status: 'submitting' }
  | { status: 'success' }
  | { status: 'error'; message: string };

export const DEMO_ACCOUNTS: (Credentials & { label: string })[] = [
  { label: 'Admin', username: 'admin', password: 'Admin#2026' },
  { label: 'Clinician', username: 'clinician', password: 'Clinician#2026' },
  { label: 'Front desk', username: 'frontdesk', password: 'FrontDesk#2026' },
];

@Component({
  selector: 'cp-login',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly submit$ = new Subject<Credentials>();

  protected readonly demoAccounts = isDevMode() ? DEMO_ACCOUNTS : [];

  protected readonly form = new FormGroup({
    username: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  protected readonly state = toSignal(
    this.submit$.pipe(
      exhaustMap(({ username, password }) =>
        this.auth.login(username, password).pipe(
          tap(() => this.router.navigateByUrl(this.returnUrl())),
          map((): LoginState => ({ status: 'success' })),
          catchError((error) => of(this.failure(error))),
          startWith<LoginState>({ status: 'submitting' }),
        ),
      ),
    ),
    { initialValue: { status: 'idle' } as LoginState },
  );

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submit$.next(this.form.getRawValue());
  }

  signInAs(account: Credentials): void {
    this.form.setValue({
      username: account.username,
      password: account.password,
    });
    this.submit();
  }

  private returnUrl(): string {
    return this.route.snapshot.queryParamMap.get('returnUrl') ?? '/patients';
  }

  private failure(error: unknown): LoginState {
    const message =
      error instanceof HttpErrorResponse && error.status === 401
        ? 'Invalid username or password'
        : 'Could not reach the server. Try again.';
    return { status: 'error', message };
  }
}
