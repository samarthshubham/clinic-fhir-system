import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  catchError,
  finalize,
  map,
  Observable,
  of,
  shareReplay,
  tap,
  throwError,
} from 'rxjs';
import { AuthUser, Role, TokenResponse } from './auth.models';

export const REFRESH_TOKEN_KEY = 'praxis.refreshToken';

interface Session {
  accessToken: string;
  user: AuthUser;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(null);
  private refreshInFlight$: Observable<string> | null = null;

  readonly user = computed(() => this.session()?.user ?? null);
  readonly isAuthenticated = computed(() => this.session() !== null);

  accessToken(): string | null {
    return this.session()?.accessToken ?? null;
  }

  hasRole(...roles: Role[]): boolean {
    const userRoles = this.user()?.roles ?? [];
    return roles.some((role) => userRoles.includes(role));
  }

  login(username: string, password: string): Observable<AuthUser> {
    return this.http
      .post<TokenResponse>('/api/auth/login', { username, password })
      .pipe(
        tap((tokens) => this.store(tokens)),
        map((tokens) => tokens.user),
      );
  }

  refresh(): Observable<string> {
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token'));
    }
    this.refreshInFlight$ ??= this.http
      .post<TokenResponse>('/api/auth/refresh', { refreshToken })
      .pipe(
        tap((tokens) => this.store(tokens)),
        map((tokens) => tokens.accessToken),
        catchError((error) => {
          this.clear();
          return throwError(() => error);
        }),
        finalize(() => (this.refreshInFlight$ = null)),
        shareReplay(1),
      );
    return this.refreshInFlight$;
  }

  restoreSession(): Observable<unknown> {
    if (!localStorage.getItem(REFRESH_TOKEN_KEY)) {
      return of(null);
    }
    return this.refresh().pipe(catchError(() => of(null)));
  }

  logout(): void {
    this.clear();
    this.router.navigate(['/login']);
  }

  private store(tokens: TokenResponse): void {
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
    this.session.set({ accessToken: tokens.accessToken, user: tokens.user });
  }

  private clear(): void {
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    this.session.set(null);
  }
}
