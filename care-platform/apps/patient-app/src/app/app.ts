import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
} from '@angular/core';
import { RouterModule, Router, NavigationEnd } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map, startWith } from 'rxjs';
import { BreakpointObserver } from '@angular/cdk/layout';
import { AuthService } from '@care-platform/auth';

@Component({
  selector: 'app-root',
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterModule],
})
export class App {
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      map((e) => (e as NavigationEnd).urlAfterRedirects),
      startWith(this.router.url),
    ),
    { initialValue: this.router.url },
  );

  readonly desktop = toSignal(
    inject(BreakpointObserver)
      .observe('(min-width: 900px)')
      .pipe(map((r) => r.matches)),
    { initialValue: false },
  );

  readonly showNav = computed(() => {
    const url = this.url();
    return (
      this.auth.isAuthenticated() &&
      !url.startsWith('/login') &&
      !url.startsWith('/vitals/record')
    );
  });

  readonly activeTab = computed(() => {
    const url = this.url();
    if (url.startsWith('/vitals')) return 'vitals';
    if (url.startsWith('/messages')) return 'messages';
    if (url.startsWith('/profile')) return 'profile';
    return 'home';
  });

  readonly patientName = computed(() => this.auth.user()?.name ?? '');

  readonly patientInitials = computed(() => {
    const parts = this.patientName().split(/\s+/).filter(Boolean);
    if (parts.length === 0) return '?';
    return parts
      .slice(0, 2)
      .map((part) => part[0])
      .join('')
      .toUpperCase();
  });

  navigate(route: string): void {
    this.router.navigate(['/' + route]);
  }

  signOut(): void {
    this.auth.logout();
  }
}
